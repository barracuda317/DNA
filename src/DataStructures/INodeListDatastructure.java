package DataStructures;

import Graph.Nodes.Node;

public interface INodeListDatastructure extends IDataStructure {
	public boolean add(Node element);
	public Node get(int element);
	public boolean remove(Node element);
	public int getMaxNodeIndex();
}
