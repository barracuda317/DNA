package dna.metrics.similarityMeasures.matching;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

import dna.graph.Graph;
import dna.graph.IElement;
import dna.graph.edges.DirectedEdge;
import dna.graph.edges.DirectedWeightedEdge;
import dna.graph.edges.UndirectedEdge;
import dna.graph.edges.UndirectedWeightedEdge;
import dna.graph.nodes.DirectedNode;
import dna.graph.nodes.Node;
import dna.graph.nodes.UndirectedNode;
import dna.graph.weights.DoubleWeight;
import dna.graph.weights.IntWeight;
import dna.graph.weights.Weight;
import dna.metrics.IMetric;
import dna.metrics.Metric;
import dna.metrics.similarityMeasures.Matrix;
import dna.series.data.BinnedDistributionLong;
import dna.series.data.Distribution;
import dna.series.data.NodeNodeValueList;
import dna.series.data.NodeValueList;
import dna.series.data.Value;
import dna.updates.batch.Batch;
import dna.util.parameters.Parameter;
import dna.util.parameters.StringParameter;

/**
 * Computes the similarity matching measure for graphs. The similarity of two
 * nodes <i>n</i>, <i>m</i> is defined as the number of elements in the
 * intersection of <i>neighbors(n)</i> and <i>neighbors(m)</i>.
 * <p>
 * To control the usage of edge weights in weighted graphs and to determine
 * whether compute coefficient for indegree or outdegree in directed graphs, use
 * Parameters.
 * </p>
 * 
 * @see MatchingR
 * @see MatchingU
 */
public abstract class Matching extends Metric implements IMetric {
	/**
	 * Setting for {@link Parameter} "directedDegreeType".
	 */
	public static enum DirectedDegreeType {
		IN("in"), OUT("out");

		private final StringParameter param;

		DirectedDegreeType(String value) {
			this.param = new StringParameter("directedDegreeType", value);
		}

		public StringParameter StringParameter() {
			return this.param;
		}
	}

	/**
	 * Setting for {@link Parameter} "edgeWeightType".
	 */
	public static enum EdgeWeightType {
		IGNORE_WEIGHTS("unweighted"), USE_WEIGHTS("weighted");

		private final StringParameter param;

		EdgeWeightType(String value) {
			this.param = new StringParameter("edgeWeightType", value);
		}

		public StringParameter StringParameter() {
			return this.param;
		}
	}

	/**
	 * Is either "out" (default) or "in", depending on the {@link Parameter} in
	 * {@link #Assortativity(String, DirectedDegreeType, EdgeWeightType)}. This
	 * value determines whether nodes in directed graphs are compared by there
	 * in- or outdegree and is ignored for undirected graphs.
	 */
	DirectedDegreeType directedDegreeType;

	/**
	 * To check equality of metrics in {@link #equals(IMetric)}, the
	 * assortativity coefficient {@link #r} is compared. This value is the
	 * allowed difference of two values to still accept them as equal.
	 */
	public static final double ACCEPTED_ERROR_FOR_EQUALITY = 1.0E-4;

	/**
	 * Is either "unweighted" (default) or "weighted", depending on the
	 * {@link Parameter} in
	 * {@link #Assortativity(String, DirectedDegreeType, EdgeWeightType)} . This
	 * value determines whether edge weights in weighted graphs are ignored not
	 * (will always be ignored for weighted graphs).
	 */
	EdgeWeightType edgeWeightType;

	/** Contains the result for each matching. */
	protected Matrix matching;

	/** Average per Node Distribution */
	protected BinnedDistributionLong binnedDistributionEveryNodeToOtherNodes;

	protected BinnedDistributionLong matchingD;

	/**
	 * Initializes {@link Matching}. Implicitly sets degree type for directed
	 * graphs to outdegree ang ignore weights.
	 * 
	 * @param name
	 *            The name of the metric.
	 */
	public Matching(String name) {
		this(name, DirectedDegreeType.OUT, EdgeWeightType.IGNORE_WEIGHTS);
	}

	/**
	 * Initializes {@link Matching}.
	 * 
	 * @param name
	 *            The name of the metric, e.g. <i>AssortativityR</i> for the
	 *            Matching Recomputation and <i>MatchingU</i> for the Matching
	 *            Updates.
	 * @param directedDegreeType
	 *            <i>in</i> or <i>out</i>, determining whether to use in- or
	 *            outdegree for directed graphs. Will be ignored for undirected
	 *            graphs.
	 * @param edgeWeightType
	 *            <i>weighted</i> or <i>unweighted</i>, determining whether to
	 *            use edge weights in weighted graphs or not. Will be ignored
	 *            for unweighted graphs.
	 */
	public Matching(String name, DirectedDegreeType directedDegreeType,
			EdgeWeightType edgeWeightType) {
		super(name, IMetric.MetricType.exact, directedDegreeType
				.StringParameter(), edgeWeightType.StringParameter());

		this.directedDegreeType = directedDegreeType;
		this.edgeWeightType = edgeWeightType;
	}

	/**
	 * Static computation of the matching similarity.
	 * 
	 * @return True if something was computed and false if no computation was
	 *         done because graph does not fit.
	 */
	boolean compute() {
		if (DirectedWeightedEdge.class.isAssignableFrom(this.g
				.getGraphDatastructures().getEdgeType())) {

			// directed weighted graph

			if (this.edgeWeightType.equals(EdgeWeightType.USE_WEIGHTS))
				return this.computeForDirectedWeightedGraph();
			else if (this.edgeWeightType.equals(EdgeWeightType.IGNORE_WEIGHTS))
				return this.computeForDirectedUnweightedGraph();

		} else if (UndirectedWeightedEdge.class.isAssignableFrom(this.g
				.getGraphDatastructures().getEdgeType())) {

			// undirected weighted graph

			if (this.edgeWeightType.equals(EdgeWeightType.USE_WEIGHTS))
				return this.computeForUndirectedWeightedGraph();
			else if (this.edgeWeightType.equals(EdgeWeightType.IGNORE_WEIGHTS))
				return this.computeForUndirectedUnweightedGraph();

		} else if (DirectedNode.class.isAssignableFrom(this.g
				.getGraphDatastructures().getNodeType())) {

			// directed unweighted graph
			return this.computeForDirectedUnweightedGraph();

		} else if (UndirectedNode.class.isAssignableFrom(this.g
				.getGraphDatastructures().getNodeType())) {

			// undirected unweighted graph
			return this.computeForUndirectedUnweightedGraph();

		}
		System.err.println("Fehler!");
		return false;
	}

	/**
	 * Computing for graphs with directed edges based only on current snapshot.
	 */
	private boolean computeForDirectedUnweightedGraph() {
		final Iterable<IElement> nodesOfGraph = this.g.getNodes();

		DirectedNode node1, node2;
		// neighbors for node1, node2:
		HashSet<Node> neighbors1, neighbors2;
		// indices for both for-loops to save some time with using matching(1,2)
		// = matching(2,1)
		int nodeIndex1 = 0, nodeIndex2;

		for (IElement iElement1 : nodesOfGraph) {
			node1 = (DirectedNode) iElement1;
			neighbors1 = this.getNeighborNodesDirectedUnweighted(node1);
			nodeIndex2 = 0;
			for (IElement iElement2 : nodesOfGraph) {
				if (nodeIndex2 < nodeIndex1) {
					// matching is equal to equivalent calculated before
					// (matching(1,2) = matching(2,1))

					nodeIndex2++;
					continue;
				}

				node2 = (DirectedNode) iElement2;
				neighbors2 = this.getNeighborNodesDirectedUnweighted(node2);

				// intersection
				neighbors2.retainAll(neighbors1);
				this.matching.put(node1, node2, (double) neighbors2.size());
				this.matchingD.incr((double) neighbors2.size());
				nodeIndex2++;
			}

			nodeIndex1++;
		}

		return true;
	}

	/**
	 * Computing for graphs with directed weighted edges based only on current
	 * snapshot.
	 */
	private boolean computeForDirectedWeightedGraph() {
		final Iterable<IElement> nodesOfGraph = this.g.getNodes();

		DirectedNode node1, node2;
		// neighbors for node1, node2:
		HashMap<Node, Double> neighbors1, neighbors2;
		// indices for both for-loops to save some time with using matching(1,2)
		// = matching(2,1)
		int nodeIndex1 = 0, nodeIndex2;

		for (IElement iElement1 : nodesOfGraph) {
			node1 = (DirectedNode) iElement1;
			neighbors1 = this.getNeighborNodesDirectedWeighted(node1);

			nodeIndex2 = 0;
			for (IElement iElement2 : nodesOfGraph) {
				if (nodeIndex2 < nodeIndex1) {
					// matching is equal to equivalent calculated before
					// (matching(1,2) = matching(2,1))
					nodeIndex2++;
					continue;
				}

				node2 = (DirectedNode) iElement2;
				neighbors2 = this.getNeighborNodesDirectedWeighted(node2);

				// #intersection
				double sum = getMapValueSum(getMatching(neighbors1, neighbors2));

				this.matching.put(node1, node2, sum);
				this.matchingD.incr(sum);

				nodeIndex2++;
			}

			nodeIndex1++;
		}

		return true;
	}

	/**
	 * Computing for graphs with undirected edges based only on current
	 * snapshot.
	 */
	private boolean computeForUndirectedUnweightedGraph() {
		final Iterable<IElement> nodesOfGraph = this.g.getNodes();

		UndirectedNode node1, node2;

		// neighbors for node1, node2:
		HashSet<Node> neighbors1, neighbors2;

		// indices for both for-loops to save some time with using matching(1,2)
		// = matching(2,1)
		int nodeIndex1 = 0, nodeIndex2;

		for (IElement iElement1 : nodesOfGraph) {
			node1 = (UndirectedNode) iElement1;
			neighbors1 = this.getNeighborNodesUndirectedUnweighted(node1);
			nodeIndex2 = 0;
			for (IElement iElement2 : nodesOfGraph) {
				if (nodeIndex2 < nodeIndex1) {
					// matching is equal to equivalent calculated before
					// (matching(1,2) = matching(2,1))
					nodeIndex2++;
					continue;
				}
				node2 = (UndirectedNode) iElement2;
				neighbors2 = this.getNeighborNodesUndirectedUnweighted(node2);

				// intersection
				neighbors2.retainAll(neighbors1);
				this.matching.put(node1, node2, (double) neighbors2.size());
				this.matchingD.incr((double) neighbors2.size());
				nodeIndex2++;
			}
			nodeIndex1++;
		}
		return true;
	}

	/**
	 * Computing for graphs with undirected weighted edges based only on current
	 * snapshot.
	 */
	private boolean computeForUndirectedWeightedGraph() {
		final Iterable<IElement> nodesOfGraph = this.g.getNodes();

		UndirectedNode node1, node2;
		// neighbors for node1, node2:
		HashMap<Node, Double> neighbors1, neighbors2;
		// indices for both for-loops to save some time with using matching(1,2)
		// = matching(2,1)
		int nodeIndex1 = 0, nodeIndex2;

		for (IElement iElement1 : nodesOfGraph) {
			node1 = (UndirectedNode) iElement1;
			neighbors1 = this.getNeighborNodesUndirectedWeighted(node1);
			nodeIndex2 = 0;
			for (IElement iElement2 : nodesOfGraph) {
				if (nodeIndex2 < nodeIndex1) {
					// matching is equal to equivalent calculated before
					// (matching(1,2) = matching(2,1))

					nodeIndex2++;
					continue;
				}

				node2 = (UndirectedNode) iElement2;
				neighbors2 = this.getNeighborNodesUndirectedWeighted(node2);

				// intersection
				double sum = getMapValueSum(getMatching(neighbors1, neighbors2));

				this.matching.put(node1, node2, sum);
				this.matchingD.incr(sum);

				nodeIndex2++;
			}

			nodeIndex1++;
		}

		return true;
	}

	@Override
	public boolean equals(IMetric m) {
		return this.isComparableTo(m)
				&& ((Matching) m).matching.equals(this.matching,
						ACCEPTED_ERROR_FOR_EQUALITY);
	}

	@Override
	public Distribution[] getDistributions() {
		this.binnedDistributionEveryNodeToOtherNodes = new BinnedDistributionLong(
				"BinnedDistributionEveryNodeToOtherNodes", 1, new long[] {}, 0);

		for (IElement iterable_element : this.g.getNodes()) {

			double index = this.matching.getRowSum((Node) iterable_element)
					/ this.g.getNodeCount();
			this.binnedDistributionEveryNodeToOtherNodes.incr(index);
		}
		this.matchingD.truncate();
		this.binnedDistributionEveryNodeToOtherNodes.truncate();

		return new Distribution[] { this.matchingD,
				this.binnedDistributionEveryNodeToOtherNodes };
	}

	/**
	 * Sums the values of a Map.
	 * 
	 * @param neighbors
	 *            A {@link Map} containing the neighbors with their frequency.
	 * @return The sums of values.
	 */
	private double getMapValueSum(HashMap<Node, Double> neighbors) {
		double sum = 0;
		for (Entry<Node, Double> e : neighbors.entrySet())
			sum = sum + e.getValue();
		return sum;
	}

	/**
	 * Computes the intersection between the neighbors of two nodes of an
	 * directed weighted graph.
	 * 
	 * @param neighbors1
	 *            A {@link Map} includes the neighbors of the first node with
	 *            their frequency.
	 * @param neighbors2
	 *            A {@link Map} includes the neighbors of the second node with
	 *            their frequency.
	 * @return A {@link Map} containing the intersection of neighbors1 and
	 *         neighbors2.
	 */
	private HashMap<Node, Double> getMatching(HashMap<Node, Double> neighbors1,
			HashMap<Node, Double> neighbors2) {
		final HashMap<Node, Double> neighbors = new HashMap<Node, Double>();
		for (Entry<Node, Double> e : neighbors1.entrySet()) {
			if (neighbors2.containsKey(e.getKey())) {
				if (neighbors2.get(e.getKey()) <= e.getValue()) {
					neighbors.put(e.getKey(), neighbors2.get(e.getKey()));
				} else {
					neighbors.put(e.getKey(), e.getValue());
				}
			}
		}
		return neighbors;
	}

	/**
	 * Get neighbors of a node for an directed unweighted graph.
	 * 
	 * @param node
	 *            The {@link Node} which neighbors are wanted.
	 * @return A {@link Map} containing all neighbors of given node with their
	 *         frequency.
	 */
	private HashSet<Node> getNeighborNodesDirectedUnweighted(DirectedNode node) {
		final HashSet<Node> neighbors = new HashSet<Node>();

		DirectedEdge edge;
		if (isOutgoingMatching())
			for (IElement iEdge : node.getOutgoingEdges()) {
				edge = (DirectedEdge) iEdge;
				neighbors.add(edge.getDst());
			}
		else
			for (IElement iEdge : node.getIncomingEdges()) {
				edge = (DirectedEdge) iEdge;
				neighbors.add(edge.getSrc());
			}

		return neighbors;
	}

	/**
	 * Get neighbors of a node for an directed weighted graph.
	 * 
	 * @param node
	 *            The {@link Node} which neighbors are wanted.
	 * @return A {@link Map} containing all neighbors of given node with their
	 *         frequency.
	 */
	protected HashMap<Node, Double> getNeighborNodesDirectedWeighted(
			DirectedNode node) {
		final HashMap<Node, Double> neighbors = new HashMap<Node, Double>();

		if (isOutgoingMatching())
			for (IElement iEdge : node.getOutgoingEdges()) {
				DirectedWeightedEdge edgeD = (DirectedWeightedEdge) iEdge;
				neighbors.put(edgeD.getDst(), weight(edgeD.getWeight()));

			}
		else
			for (IElement iEdge : node.getIncomingEdges()) {

				DirectedWeightedEdge edgeD = (DirectedWeightedEdge) iEdge;
				neighbors.put(edgeD.getSrc(), weight(edgeD.getWeight()));

			}
		return neighbors;
	}

	/**
	 * Get neighbors of a node for an undirected unweighted graph.
	 * 
	 * @param node
	 *            The {@link Node} which neighbors are wanted.
	 * @return A {@link Map} containing all neighbors of given node with their
	 *         frequency.
	 */
	protected HashSet<Node> getNeighborNodesUndirectedUnweighted(Node node) {
		final HashSet<Node> neighbors = new HashSet<Node>();
		UndirectedEdge edge;
		// iterate over all edges and ...
		for (IElement iEdge : node.getEdges()) {
			edge = (UndirectedEdge) iEdge;
			// ... add the node which is not the given one to the neighbors
			if (edge.getNode1().equals(node))
				neighbors.add(edge.getNode2());
			else
				neighbors.add(edge.getNode1());
		}
		return neighbors;
	}

	/**
	 * Get neighbors of a node for an undirected weighted graph.
	 * 
	 * @param node
	 *            The {@link Node} which neighbors are wanted.
	 * @return A {@link Map} containing all neighbors of given node with their
	 *         frequency.
	 */
	protected HashMap<Node, Double> getNeighborNodesUndirectedWeighted(
			UndirectedNode node) {
		final HashMap<Node, Double> neighbors = new HashMap<Node, Double>();

		UndirectedWeightedEdge edge;
		// iterate over all edges and ...
		for (IElement iEdge : node.getEdges()) {
			edge = (UndirectedWeightedEdge) iEdge;

			// ... add the node which is not the given one to the neighbors
			if (edge.getNode1().equals(node))
				neighbors.put(edge.getNode2(), weight(edge.getWeight()));
			else
				neighbors.put(edge.getNode1(), weight(edge.getWeight()));
		}

		return neighbors;
	}

	@Override
	public NodeNodeValueList[] getNodeNodeValueLists() {
		return new NodeNodeValueList[] {};
	}

	@Override
	public NodeValueList[] getNodeValueLists() {
		return new NodeValueList[] {};
	}

	@Override
	public Value[] getValues() {
		Value v1 = new Value("avarage", this.matchingD.computeAverage());
		return new Value[] { v1 };
	}

	public void init_() {
		this.matching = new Matrix();
		this.matchingD = new BinnedDistributionLong("MatchingD", 1,
				new long[] {}, 0);
		this.binnedDistributionEveryNodeToOtherNodes = new BinnedDistributionLong(
				"BinnedDistributionEveryNodeToOtherNodes", 1, new long[] {}, 0);
	}

	@Override
	public boolean isApplicable(Batch b) {
		return true;
	}

	@Override
	public boolean isApplicable(Graph g) {
		return true;
	}

	@Override
	public boolean isComparableTo(IMetric m) {
		return m != null
				&& m instanceof Matching
				&& ((Matching) m).directedDegreeType
						.equals(this.directedDegreeType)
				&& ((Matching) m).edgeWeightType.equals(this.edgeWeightType);
	}

	/**
	 * Returns for which type of directed edges the matching is.
	 * 
	 * @return true, if the matching is for outgoing edges; false for incoming
	 */
	protected boolean isOutgoingMatching() {
		if (this.directedDegreeType.equals(DirectedDegreeType.OUT))
			return true;
		return false;
	}

	public void reset_() {
		this.matching = new Matrix();
		this.matchingD = new BinnedDistributionLong("MatchingD", 1,
				new long[] {}, 0);
		this.binnedDistributionEveryNodeToOtherNodes = new BinnedDistributionLong(
				"BinnedDistributionEveryNodeToOtherNodes", 1, new long[] {}, 0);
	}

	/**
	 * @param w
	 *            Any {@link Weight}.
	 * @return Given w as double value.
	 */
	double weight(Weight w) {
		if (w instanceof IntWeight)
			return (double) ((IntWeight) w).getWeight();
		else if (w instanceof DoubleWeight)
			return ((DoubleWeight) w).getWeight();
		else
			return Double.NaN;
	}

}
