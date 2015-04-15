package edu.isi.tiburon;

import java.util.Vector;

// slim representation of a rule used in covering transducer rule: state, state vector, weight
public class RGOTFGrammarRule extends GrammarRule{
	private Symbol state;
	private Vector<Symbol> children;
	private double weight;
	
	public Symbol getState() { return state; }
	public Symbol getLabel() { return null; }
	public Vector<Symbol> getChildren() { return children; }
	// ordering is for dotted rules
	public Symbol getChild(int i) {
		if (i >= children.size())
			return null;
		return children.get(i);
	}
	
	public double getWeight() { return weight; }
	
	private static final int PERIOD=100000;
	private static int counter=0;
	// built via GTOTFGrammar construction from rtg rule and transducer rule
	public RGOTFGrammarRule(Symbol st, Vector<Symbol> c, double w) {
		super();
		state = st;
		children = c;
		weight = w;
		counter++;
		if (counter % PERIOD == 0) {
			Debug.debug(true, "Built "+counter+" RGOTFGrammarRules");
		}
	}
	public String toString() {
		StringBuffer str = new StringBuffer(state+" -> ");
		
		if (children.size() > 0)
			str.append(children);
		else
			str.append("*e*");
		str.append(" # "+weight);
		return str.toString();
	}

}
