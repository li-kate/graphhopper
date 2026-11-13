/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing;

import com.carrotsearch.hppc.IntSet;
import com.carrotsearch.hppc.predicates.IntObjectPredicate;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.graphhopper.util.Parameters.Algorithms.AltRoute.*;

/**
 * This class implements the alternative paths search using the "plateau" and partially the
 * "penalty" method described in the following papers.
 * <p>
 * <ul>
 * <li>Choice Routing Explanation - Camvit 2009:
 * http://www.camvit.com/camvit-technical-english/Camvit-Choice-Routing-Explanation-english.pdf</li>
 * <li>and refined in: Alternative Routes in Road Networks 2010:
 * http://www.cs.princeton.edu/~rwerneck/papers/ADGW10-alternatives-sea.pdf</li>
 * <li>other ideas 'Improved Alternative Route Planning', 2013:
 * https://hal.inria.fr/hal-00871739/document</li>
 * <li>via point 'storage' idea 'Candidate Sets for Alternative Routes in Road Networks', 2013:
 * https://algo2.iti.kit.edu/download/s-csarrn-12.pdf</li>
 * <li>Alternative route graph construction 2011:
 * http://algo2.iti.kit.edu/download/altgraph_tapas_extended.pdf
 * </li>
 * </ul>
 * <p>
 * Note: This algorithm can be slow for longer routes and alternatives are only really practical in combination with CH, see #2566
 *
 * @author Peter Karich
 */
public class AlternativeRoute extends AStarBidirection implements RoutingAlgorithm {
    private static final Comparator<AlternativeInfo> ALT_COMPARATOR = Comparator.comparingDouble(o -> o.sortBy);

    private final int maxPaths;
    /**
     * This variable influences the graph exploration for alternative paths. Specify a higher value than the default to
     * potentially get more alternatives and a lower value to improve query time but reduces chance to find alternatives.
     */
    private final double explorationFactor;
    /**
     * Decreasing this factor filters found alternatives and increases quality. E.g. if the factor is 2 than
     * all alternatives with a weight 2 times longer than the optimal weight are return.
     */
    private final double maxWeightFactor;
    /**
     * Decreasing this factor filters found alternatives and might increase quality. This parameter is used to avoid
     * alternatives too similar to the best path. Specify 0.2 to ensure maximum 20% of the best path are on the same roads.
     * The unit is also the 'weight'.
     */
    private final double maxShareFactor;
    /**
     * Increasing this factor filters found alternatives and might increase quality. This specifies the minimum plateau
     * portion of every alternative path that is required. Keep in mind that a plateau is often not complete especially
     * when the explorationFactor is low (and for performance reasons the explorationFactor should be as low as possible).
     * This is the reason we cannot require a too big plateau portion here as default.
     */
    private final double minPlateauFactor;
    /**
     * Maximum allowed distance (in meters) for alternative paths.
     * Alternatives with distance exceeding this value are rejected.
     * A value <= 0 disables this filter (default: -1, disabled).
     */
    private final double maxDistance;
    /**
     * Maximum ratio of exactly-shared edges (measured by their distance) between any two accepted
     * alternatives. E.g. if set to 0.7, a candidate that shares more than 70% of the longer path's
     * distance in common edges with any already-accepted alternative is rejected.
     * A value <= 0 or >= 1 disables this filter (default: -1, disabled).
     */
    private final double maxDistanceShareFactor;

    /**
     * Key used to stash the resolved per-edge {@link DecimalEncodedValue} into the hints PMap so
     * AlternativeRoute can read it without changing any public factory signature. See
     * Router.FlexSolver.getAlgoOpts where it is injected.
     */
    public static final String EV_FILTER_HINT = "alternative_route.ev_encoded_value";

    /**
     * Per-edge encoded value used to compute a distance-weighted route average and filter
     * alternatives against it. Null disables the filter (the common case).
     */
    private final DecimalEncodedValue evFilter;
    /**
     * Reject an alternative if its distance-weighted average of {@link #evFilter} exceeds this.
     * Use for "lower is better" metrics such as heat exposure (UTCI). NaN disables this bound.
     */
    private final double evMax;
    /**
     * Reject an alternative if its distance-weighted average of {@link #evFilter} is below this.
     * Use for "higher is better" metrics such as greenery. NaN disables this bound.
     */
    private final double evMin;

    /**
     * Hint carrying the edges of a route returned by a previous GraphHopper call (e.g. the shortest
     * route) that candidate alternatives should be compared against. Format: a comma-separated list
     * of edge ids, e.g. "12,45,998". The comparison uses the same metric and threshold
     * ({@link #maxDistanceShareFactor}) as {@link #isTooSimilarByDistance}. The edge ids must come
     * from the same graph as this query. Empty/absent disables the comparison.
     */
    public static final String REFERENCE_EDGES = "alternative_route.reference_edges";

    /**
     * The reference route (from a previous call) as an edge-id -> distance map plus its total
     * distance, built once in the constructor. Null when no reference route was supplied.
     */
    private final EdgeSet referenceEdgeSet;

    public AlternativeRoute(Graph graph, Weighting weighting, TraversalMode traversalMode, PMap hints) {
        super(graph, weighting, traversalMode);
        if (weighting.hasTurnCosts() && !traversalMode.isEdgeBased())
            throw new IllegalStateException("Weightings supporting turn costs cannot be used with node-based traversal mode");

        this.maxPaths = hints.getInt(MAX_PATHS, 2);
        if (this.maxPaths < 2)
            throw new IllegalArgumentException("Use normal algorithm with less overhead instead if no alternatives are required");

        this.explorationFactor = hints.getDouble("alternative_route.max_exploration_factor", 1.12);
        this.maxWeightFactor = hints.getDouble(MAX_WEIGHT, 1.25);
        this.maxShareFactor = hints.getDouble(MAX_SHARE, 0.6);
        this.minPlateauFactor = hints.getDouble("alternative_route.min_plateau_factor", 0.1);
        this.maxDistance = hints.getDouble("alternative_route.max_distance", -1);
        this.maxDistanceShareFactor = hints.getDouble("alternative_route.max_distance_share_factor", -1);

        this.evFilter = (DecimalEncodedValue) hints.getObject(EV_FILTER_HINT, null);
        this.evMax = hints.getDouble("alternative_route.ev_max", Double.NaN);
        this.evMin = hints.getDouble("alternative_route.ev_min", Double.NaN);

        this.referenceEdgeSet = parseReferenceEdgeSet(graph, hints.getString(REFERENCE_EDGES, ""));
    }

    /**
     * Distance-weighted average of the {@link #evFilter} encoded value over a path:
     * Sum(ev_edge * metres_edge) / Sum(metres_edge). Weighting by metre length (not by number of
     * geometry points) avoids biasing toward curvy, point-dense segments. Returns NaN for an
     * empty/zero-length path.
     */
    private double routeAverageEv(Path path) {
        double weighted = 0, dist = 0;
        for (EdgeIteratorState edge : path.calcEdges()) {
            double meters = edge.getDistance();
            if (meters <= 0)
                continue;
            weighted += edge.get(evFilter) * meters;
            dist += meters;
        }
        return dist > 0 ? weighted / dist : Double.NaN;
    }

    /**
     * True if the path's distance-weighted average EV violates the configured bound(s):
     * above ev_max (lower-is-better metrics, e.g. heat) or below ev_min (higher-is-better, e.g.
     * greenery). A path whose average is NaN (no measurable length) is rejected when a filter is active.
     */
    private boolean failsEvThreshold(Path path) {
        if (evFilter == null || (Double.isNaN(evMax) && Double.isNaN(evMin)))
            return false;
        double avg = routeAverageEv(path);
        if (Double.isNaN(avg))
            return true;
        if (!Double.isNaN(evMax) && avg > evMax)
            return true;
        if (!Double.isNaN(evMin) && avg < evMin)
            return true;
        return false;
    }

    static List<String> getAltNames(Graph graph, SPTEntry ee) {
        if (ee == null || !EdgeIterator.Edge.isValid(ee.edge))
            return Collections.emptyList();

        EdgeIteratorState iter = graph.getEdgeIteratorState(ee.edge, Integer.MIN_VALUE);
        if (iter == null)
            return Collections.emptyList();

        String str = iter.getName();
        if (str.isEmpty())
            return Collections.emptyList();

        return Collections.singletonList(str);
    }

    static double calcSortBy(double weightInfluence, double weight,
                             double shareInfluence, double shareWeight,
                             double plateauInfluence, double plateauWeight) {
        return weightInfluence * weight + shareInfluence * shareWeight + plateauInfluence * plateauWeight;
    }

    /**
     * A route reduced to the only data the exact-edge share check needs: an edge-id -> distance
     * (meters) map and the route's total distance. This gives already-accepted alternatives (built
     * from a {@link Path} on the fly) and the reference route from a previous call (built once from
     * edge ids, see {@link #REFERENCE_EDGES}) the same shape, so a single comparison method handles both.
     */
    private static final class EdgeSet {
        final Map<Integer, Double> edgeDistances;
        final double total;

        EdgeSet(Map<Integer, Double> edgeDistances, double total) {
            this.edgeDistances = edgeDistances;
            this.total = total;
        }
    }

    /**
     * Build an {@link EdgeSet} from a path's edges.
     */
    private EdgeSet toEdgeSet(Path path) {
        Map<Integer, Double> edgeDistances = new HashMap<>();
        for (EdgeIteratorState edge : path.calcEdges()) {
            edgeDistances.put(edge.getEdge(), edge.getDistance());
        }
        return new EdgeSet(edgeDistances, path.getDistance());
    }

    /**
     * Parse the {@link #REFERENCE_EDGES} hint (comma-separated edge ids) into an {@link EdgeSet},
     * looking up each edge's distance from the graph. Returns null when no reference route was given.
     */
    private static EdgeSet parseReferenceEdgeSet(Graph graph, String refEdgesStr) {
        if (refEdgesStr.isEmpty())
            return null;
        Map<Integer, Double> edgeDistances = new HashMap<>();
        double total = 0;
        int edgeCount = graph.getEdges();
        for (String token : refEdgesStr.split(",")) {
            token = token.trim();
            if (token.isEmpty())
                continue;
            int edgeId;
            try {
                edgeId = Integer.parseInt(token);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid edge id in " + REFERENCE_EDGES + ": '" + token + "'");
            }
            // Ignore ids that are not real base-graph edges for this query. Virtual edges created
            // for the previous call's snapped points are not guaranteed to exist here, and a stale
            // id would otherwise throw from getEdgeIteratorState.
            if (edgeId < 0 || edgeId >= edgeCount)
                continue;
            if (edgeDistances.containsKey(edgeId))
                continue;
            double dist = graph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE).getDistance();
            edgeDistances.put(edgeId, dist);
            total += dist;
        }
        return edgeDistances.isEmpty() ? null : new EdgeSet(edgeDistances, total);
    }

    /**
     * Summed distance of the edges the candidate and {@code other} have in common (matched by edge
     * id) divided by the longer route's total distance. Distance is only the unit, not spatial
     * proximity. Returns true if that ratio exceeds {@link #maxDistanceShareFactor}, i.e. the
     * candidate is too similar to {@code other} and should be rejected.
     */
    private boolean isTooSimilar(EdgeSet candidate, EdgeSet other) {
        if (candidate.total <= 0)
            return false;
        double sharedDistance = 0;
        for (Map.Entry<Integer, Double> entry : candidate.edgeDistances.entrySet()) {
            if (other.edgeDistances.containsKey(entry.getKey())) {
                // same edge id -> same distance, so count the shared edge once
                sharedDistance += entry.getValue();
            }
        }
        double maxTotal = Math.max(candidate.total, other.total);
        return maxTotal > 0 && sharedDistance / maxTotal > maxDistanceShareFactor;
    }

    /**
     * Check if a candidate path shares too many exact edges with any already-accepted alternative
     * or with the reference route from a previous call (see {@link #REFERENCE_EDGES}). Both use the
     * same metric and the same {@link #maxDistanceShareFactor} threshold. Returns true if the
     * candidate should be rejected.
     */
    private boolean isTooSimilarByDistance(Path candidate, List<AlternativeInfo> accepted) {
        EdgeSet candidateSet = toEdgeSet(candidate);
        for (AlternativeInfo alt : accepted) {
            if (isTooSimilar(candidateSet, toEdgeSet(alt.getPath())))
                return true;
        }
        if (referenceEdgeSet != null && isTooSimilar(candidateSet, referenceEdgeSet))
            return true;
        return false;
    }

    public List<AlternativeInfo> calcAlternatives(int from, int to) {
        Path bestPath = searchBest(from, to);
        List<AlternativeInfo> alternatives = calcAlternatives(bestPath, maxPaths,
                maxWeightFactor, 7,
                maxShareFactor, 0.8,
                minPlateauFactor, -0.2);

        // The best path is seeded into the list before the SPT loop (as bestAlt) and is therefore
        // exempt from every in-loop filter (steps 5 and 6). Candidates found inside the loop are
        // already filtered there, so only index 0 (the best path) can reach this point in violation
        // of a bound. Apply the same two filters to it here so a best path that is too similar to the
        // reference route, or that violates the EV threshold, is dropped just like the alternatives.
        if (!alternatives.isEmpty()) {
            Path best = alternatives.get(0).getPath();
            boolean dropBest = false;
            if (maxDistanceShareFactor > 0 && maxDistanceShareFactor < 1
                    && referenceEdgeSet != null && isTooSimilar(toEdgeSet(best), referenceEdgeSet))
                dropBest = true;
            if (evFilter != null && failsEvThreshold(best))
                dropBest = true;
            if (dropBest)
                alternatives.remove(0);
        }

        return alternatives;
    }

    @Override
    public List<Path> calcPaths(int from, int to) {
        checkAlreadyRun();
        setupFinishTime();
        List<AlternativeInfo> alternatives = calcAlternatives(from, to);
        List<Path> paths = new ArrayList<>(alternatives.size());
        for (AlternativeInfo a : alternatives) {
            paths.add(a.getPath());
        }
        return paths;
    }

    @Override
    public String getName() {
        return Parameters.Algorithms.ALT_ROUTE;
    }

    public static class AlternativeInfo {
        private final double sortBy;
        private final Path path;
        private final double shareWeight;
        private final List<String> names;

        public AlternativeInfo(double sortBy, Path path, double shareWeight, List<String> altNames) {
            this.names = altNames;
            this.sortBy = sortBy;
            this.path = path;
            this.path.setDescription(names);
            this.shareWeight = shareWeight;
        }

        public Path getPath() {
            return path;
        }

        public double getShareWeight() {
            return shareWeight;
        }

        public double getSortBy() {
            return sortBy;
        }

        @Override
        public String toString() {
            return names + ", sortBy:" + sortBy + ", shareWeight:" + shareWeight + ", " + path;
        }
    }

    @Override
    public boolean finished() {
        // we need to finish BOTH searches identical to CH
        if (finishedFrom && finishedTo)
            return true;

        if (isMaxVisitedNodesExceeded() || isTimeoutExceeded())
            return true;

        // The following condition is necessary to avoid traversing the full graph if areas are disconnected
        // but it is only valid for non-CH e.g. for CH it can happen that finishedTo is true but the from-SPT could still reach 'to'
        if (finishedFrom || finishedTo)
            return true;

        // increase overlap of both searches:
        return currFrom.weight + currTo.weight > explorationFactor * (bestWeight + stoppingCriterionOffset);
        // This is more precise but takes roughly 20% longer: return currFrom.weight > bestWeight && currTo.weight > bestWeight;
        // For bidir A* and AStarEdge.getWeightOfVisitedPath see comment in AStarBidirection.finished
    }

    public Path searchBest(int from, int to) {
        init(from, 0, to, 0);
        // init collections and bestPath.getWeight properly
        runAlgo();
        return extractPath();
    }

    /**
     * @return the information necessary to handle alternative paths. Note that the paths are
     * not yet extracted.
     */
    public List<AlternativeInfo> calcAlternatives(final Path bestPath, final int maxPaths,
                                                  double maxWeightFactor, final double weightInfluence,
                                                  final double maxShareFactor, final double shareInfluence,
                                                  final double minPlateauFactor, final double plateauInfluence) {
        if (!bestPath.isFound()) {
            List<AlternativeInfo> notFound = new ArrayList<>(1);
            notFound.add(new AlternativeInfo(0, bestPath, 0, Collections.emptyList()));
            return notFound;
        }
        final double maxWeight = maxWeightFactor * bestWeight;
        // Edge IDs of the best path - used to compute share by counting actual shared
        // edges on each candidate (analogous to AlternativeRouteCH.sharedDistance).
        final IntSet bestPathEdges = new GHIntHashSet(bestPath.getEdges());

        // For edge-based this is the first edge id of the best path, for node-based the from node.
        final int startEdgeOrNode = traversalMode.isEdgeBased()
                ? (bestPath.getEdges().isEmpty() ? -1 : bestPath.getEdges().get(0))
                : bestPath.getFromNode();

        // find all 'good' alternatives from forward-SPT matching the backward-SPT and optimize by
        // small total weight (1), small share and big plateau (3a+b) and do these expensive calculations
        // only for plateau start candidates (2)
        final List<AlternativeInfo> alternatives = new ArrayList<>(maxPaths);

        double bestPlateau = bestWeight;
        double bestShare = 0;
        double sortBy = calcSortBy(weightInfluence, bestWeight,
                shareInfluence, bestShare,
                plateauInfluence, bestPlateau);

        final AlternativeInfo bestAlt = new AlternativeInfo(sortBy, bestPath, bestShare, getAltNames(graph, bestFwdEntry));
        alternatives.add(bestAlt);
        AtomicReference<SPTEntry> bestEntry = new AtomicReference<>();

        bestWeightMapFrom.forEach(new IntObjectPredicate<SPTEntry>() {
            @Override
            public boolean apply(final int traversalId, final SPTEntry fromSPTEntry) {
                SPTEntry toSPTEntry = bestWeightMapTo.get(traversalId);
                if (toSPTEntry == null)
                    return true;

                // Using the parent is required to avoid duplicate edge in Path.
                // TODO we miss the turn cost weight (but at least we not duplicate the current edge weight)
                if (traversalMode.isEdgeBased() && toSPTEntry.parent != null)
                    toSPTEntry = toSPTEntry.parent;

                // The alternative path is suboptimal if U-turn (after fromSPTEntry)
                if (fromSPTEntry.edge == toSPTEntry.edge)
                    return true;

                // (1) skip too long paths
                final double weight = fromSPTEntry.getWeightOfVisitedPath() + toSPTEntry.getWeightOfVisitedPath()
                        + weighting.calcTurnWeight(fromSPTEntry.edge, fromSPTEntry.adjNode, toSPTEntry.edge);
                if (weight > maxWeight)
                    return true;

                // (2a)
                if (isStartOfFwdSPT(fromSPTEntry))
                    return true;

                // (2b) For edge based traversal we need the next entry to find out the plateau start
                SPTEntry tmpFromEntry = traversalMode.isEdgeBased() ? fromSPTEntry.parent : fromSPTEntry;
                if (tmpFromEntry == null || tmpFromEntry.parent == null) {
                    // we can be here only if edge based and only if entry is not part of the best path
                    // e.g. when starting point has two edges and one is part of the best path the other edge is path of an alternative
                    assert traversalMode.isEdgeBased();
                } else {
                    int nextToTraversalId = traversalMode.createTraversalId(graph.getEdgeIteratorState(tmpFromEntry.edge, tmpFromEntry.parent.adjNode), true);
                    SPTEntry correspondingToEntry = bestWeightMapTo.get(nextToTraversalId);
                    if (correspondingToEntry != null) {
                        if (traversalMode.isEdgeBased())
                            correspondingToEntry = correspondingToEntry.parent;
                        if (correspondingToEntry.edge == fromSPTEntry.edge)
                            return true;
                    }
                }

                // (3a) calculate plateau, we know we are at the beginning of the 'from'-side of
                // the plateau A-B-C and go further to B
                // where B is the next-'from' of A and B is also the previous-'to' of A.
                //
                //      *<-A-B-C->*
                //        /    \
                //    start    end
                //
                // extend plateau in only one direction necessary (A to B to ...) as we know
                // that the from-SPTEntry is the start of the plateau or there is no plateau at all
                //
                double plateauWeight = 0;
                SPTEntry prevToSPTEntry = toSPTEntry, prevFrom = fromSPTEntry;
                while (prevToSPTEntry.parent != null) {
                    int nextFromTraversalId = traversalMode.createTraversalId(graph.getEdgeIteratorState(prevToSPTEntry.edge, prevToSPTEntry.parent.adjNode), false);
                    SPTEntry otherFromEntry = bestWeightMapFrom.get(nextFromTraversalId);
                    // end of a plateau
                    if (otherFromEntry == null ||
                            otherFromEntry.parent != prevFrom ||
                            otherFromEntry.edge != prevToSPTEntry.edge)
                        break;

                    prevFrom = otherFromEntry;
                    plateauWeight += (prevToSPTEntry.getWeightOfVisitedPath() - prevToSPTEntry.parent.getWeightOfVisitedPath());
                    prevToSPTEntry = prevToSPTEntry.parent;
                }

                if (plateauWeight <= 0 || plateauWeight / weight < minPlateauFactor)
                    return true;

                if (fromSPTEntry.parent == null)
                    throw new IllegalStateException("not implemented yet. in case of an edge based traversal the parent of fromSPTEntry could be null");

                // (3b) Calculate share by walking the candidate's parent chains and summing the
                // weight of every edge that is also on the best path. This catches duplicates of
                // the best path naturally (share == bestWeight) and gives the true share for
                // partial overlaps - unlike the prior heuristic which only considered the first
                // shared edge on each side and could miss heavy-weight shared edge in the middle.
                double shareWeight = 0;
                for (SPTEntry e = fromSPTEntry; e.parent != null; e = e.parent)
                    if (bestPathEdges.contains(e.edge))
                        shareWeight += e.getWeightOfVisitedPath() - e.parent.getWeightOfVisitedPath();
                for (SPTEntry e = toSPTEntry; e.parent != null; e = e.parent)
                    if (bestPathEdges.contains(e.edge))
                        shareWeight += e.getWeightOfVisitedPath() - e.parent.getWeightOfVisitedPath();
                boolean smallShare = shareWeight / bestWeight < maxShareFactor;
                if (smallShare) {
                    List<String> altNames = getAltNames(graph, fromSPTEntry);

                    double sortBy = calcSortBy(weightInfluence, weight, shareInfluence, shareWeight, plateauInfluence, plateauWeight);
                    double worstSortBy = getWorstSortBy();

                    if (sortBy < worstSortBy || alternatives.size() < maxPaths) {
                        Path path = DefaultBidirPathExtractor.extractPath(graph, weighting, fromSPTEntry, toSPTEntry, weight);

                        // (4) Distance-based filtering: reject if path exceeds max allowed distance
                        if (maxDistance > 0) {
                            if (path.getDistance() > maxDistance) {
                                return true;  // skip this alternative, too long by distance
                            }
                        }

                        // (5) Exact-edge share filtering: reject if path shares too many of the exact
                        // same edges (measured by their distance) with any already-accepted alternative
                        // or with the reference route from a previous call (alternative_route.reference_edges).
                        // Both use the same maxDistanceShareFactor threshold.
                        if (maxDistanceShareFactor > 0 && maxDistanceShareFactor < 1) {
                            if (isTooSimilarByDistance(path, alternatives)) {
                                return true;  // skip this alternative, too similar to an accepted one or the reference route
                            }
                        }

                        // (6) EV threshold filtering: reject if the distance-weighted average of the
                        // configured encoded value violates ev_max (lower-is-better, e.g. heat) or
                        // ev_min (higher-is-better, e.g. greenery). Rejecting here - rather than
                        // trimming the final list - lets the search keep looking so we still return
                        // up to maxPaths routes that all satisfy the threshold.
                        if (failsEvThreshold(path)) {
                            return true;  // skip this alternative, violates EV threshold
                        }

                        alternatives.add(new AlternativeInfo(sortBy, path, shareWeight, altNames));

                        Collections.sort(alternatives, ALT_COMPARATOR);
                        if (alternatives.get(0) != bestAlt)
                            throw new IllegalStateException("best path should be always first entry " + bestAlt.path.getWeight() + " vs " + alternatives.get(0).path.getWeight());

                        if (alternatives.size() > maxPaths)
                            alternatives.subList(maxPaths, alternatives.size()).clear();
                    }
                }

                return true;
            }

            /**
             * Return the current worst weight for all alternatives
             */
            double getWorstSortBy() {
                if (alternatives.isEmpty())
                    throw new IllegalStateException("Empty alternative list cannot happen");
                return alternatives.get(alternatives.size() - 1).sortBy;
            }

            // returns true if fromSPTEntry is the root of the forward SPT (the start of the best path)
            boolean isStartOfFwdSPT(SPTEntry fromSPTEntry) {
                if (traversalMode.isEdgeBased()) {
                    if (startEdgeOrNode == fromSPTEntry.edge) {
                        if (fromSPTEntry.parent == null)
                            throw new IllegalStateException("best path entry must have a parent but was null: " + fromSPTEntry);
                        if (bestEntry.get() != null && bestEntry.get().edge != fromSPTEntry.edge)
                            throw new IllegalStateException("there can be only one best entry but was " + fromSPTEntry + " vs old: " + bestEntry.get()
                                    + " " + graph.getEdgeIteratorState(fromSPTEntry.edge, fromSPTEntry.adjNode).fetchWayGeometry(FetchMode.ALL));
                        bestEntry.set(fromSPTEntry);
                        return true;
                    }

                } else if (fromSPTEntry.parent == null) {
                    if (startEdgeOrNode != fromSPTEntry.adjNode)
                        throw new IllegalStateException("Start node has to be identical to root edge entry "
                                + "which is the plateau start of the best path but was: " + startEdgeOrNode + " vs. adjNode: " + fromSPTEntry.adjNode);
                    if (bestEntry.get() != null)
                        throw new IllegalStateException("there can be only one best entry but was " + fromSPTEntry + " vs old: " + bestEntry.get()
                                + " " + graph.getEdgeIteratorState(fromSPTEntry.edge, fromSPTEntry.adjNode).fetchWayGeometry(FetchMode.ALL));
                    bestEntry.set(fromSPTEntry);
                    return true;
                }

                return false;
            }
        });

        return alternatives;
    }
}
