package edu.isi.tiburon;

import java.util.Vector;

// integer-only slim tree class with only what is needed
// also holds states when parsing it
public class IPTree {
	private int label;
	private Vector<IPTree> children;
	private IPTree parent;
	private Vector<IPTreeState> states;
	// terminal construction
	public IPTree(int l) {
		label = l;
		children = new Vector<IPTree>();
	}
	// nonterminal construction including delayed parent set
	public IPTree(int l, Vector<IPTree> c) {
		label = l;
		children = c;
		for (IPTree k : children)
			k.setParent(this);
	}
	private void setParent(IPTree p) {
		parent = p;
	}
	public void addState(IPTreeState s) {
		if (states == null)
			states = new Vector<IPTreeState>();
		states.add(s);
	}
	public int getNumChildren() { return children.size(); }
	public Vector<IPTree> getChildren() { return children; }
	public int getLabel() { return label; }
	public IPTree getParent() { return parent; }
	public Vector<IPTreeState> getStates() { return states; }
	public String toString() {
	
	StringBuffer ret = new StringBuffer(label+"");
	if (children.size() > 0) {
		ret.append("("+children.get(0).toString());
		for (int i = 1; i < children.size(); i++) {
			ret.append(" "+children.get(i).toString());
		}
		ret.append(")");
	}
	//	else
	//	    ret.append(":"+label.hashCode());
	return ret.toString();
}

}
