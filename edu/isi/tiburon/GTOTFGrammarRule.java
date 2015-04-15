package edu.isi.tiburon;

import java.util.Vector;

// slim representation of a rule: state, symbol, state vector, weight
// for building derivation space, also keep track of rule origin

public class GTOTFGrammarRule extends GrammarRule{
	private Symbol state;
	private Symbol label;
	private Vector<Symbol> children;
	private double weight;

	private Vector<TreeTransducerRule> rules;
	
	public Vector<TreeTransducerRule> getRules() { return rules; }
	public Symbol getState() { return state; }
	public Symbol getLabel() { return label; }
	public Vector<Symbol> getChildren() { return children; }
	// ordering is for dotted rules
	public Symbol getChild(int i) {
		if (children.size() == 0)
			return null;
		return children.get(i);
	}
	
	public double getWeight() { return weight; }
	
	private static final int PERIOD=100000;
	private static int counter=0;
	// built via GTOTFGrammar construction from rtg rule and transducer rule
	public GTOTFGrammarRule(Symbol st, Symbol l, Vector<Symbol> c, double w, Vector<TreeTransducerRule> oldRules, TreeTransducerRule newRule) {
		super();
		state = st;
		label = l;
		children = c;
		weight = w;
		if (oldRules != null) {
			rules = new Vector<TreeTransducerRule>(oldRules);
			rules.add(newRule);
		}
		else if (newRule != null) {
			rules = new Vector<TreeTransducerRule>();
			rules.add(newRule);
		}
		else
			rules = null;
		counter++;
		if (counter % PERIOD == 0) {
			Debug.debug(true, "Built "+counter+" GTOTFGrammarRules");
		}
	}
	
	public String toString() {
		StringBuffer str = new StringBuffer(state+" -> ");
		if (label != null)
			str.append(label);
		if (children.size() > 0)
			str.append(children);
		str.append(" # "+weight);
		return str.toString();
	}

}
