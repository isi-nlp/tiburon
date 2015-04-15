package edu.isi.tiburon;

import java.util.Vector;

// node in a transducerRightTree masquerading as a GrammarRule 
// not quite as awesome as we want -- still make new state symbols for elements
// no weights here
public class TransducerAsGrammarRule extends GrammarRule {

	private TransducerRightTree node;
	private Symbol state;
	private Vector<Symbol> children;
	private static final int PERIOD=100000;
	private static int counter=0;
	public TransducerAsGrammarRule(TransducerRightTree n, Symbol s, Vector<Symbol> c) {
		boolean debug = false;
		if (debug) Debug.debug(debug, "building with "+n+", "+s+", "+c);
		state = s;
		node = n;
		children = c;
		counter++;
		if (counter % PERIOD == 0) {
			Debug.debug(true, "Built "+counter+" ConcreteGrammarRules");
		}
	}
	@Override
	public Symbol getChild(int i) {
		if (children.size() > i)
			return children.get(i);
		return null;
	}

	@Override
	public Vector<Symbol> getChildren() {
		return children;
	}

	@Override
	public Symbol getLabel() {
		return node.getLabel();
	}

	@Override
	public Symbol getState() {
		return state;
	}

	@Override
	public double getWeight() {
		// no weight in TAGR
		return 0;
	}
	// what node are we covering?
	public TransducerRightTree getNode() {
		return node;
	}
	
	public String toString() {
		StringBuffer str = new StringBuffer(state+" -> ");
		if (node.getLabel() != null)
			str.append(node.getLabel());
		if (children.size() > 0)
			str.append(children);
		return str.toString();
	}
	
}
