package edu.isi.tiburon;

import java.util.Vector;

// slim representation of a rule: state, symbol, state vector, weight
public class ConcreteGrammarRule extends GrammarRule{
	private Symbol state;
	private Symbol label;
	private Vector<Symbol> children;
	private double weight;
	
	public Symbol getState() { return state; }
	public Symbol getLabel() { return label; }
	public Vector<Symbol> getChildren() { return children; }
	
	public Symbol getChild(int i) {
		if (children.size() > i)
			return children.get(i);
		return null;
	}
	public double getWeight() { return weight; }
	
	// TODO: serialize-oriented constructor and writer
	public ConcreteGrammarRule(Rule r, RuleSet rs) throws ImproperConversionException {
		super();
		state = r.getLHS();
		weight = r.getWeight();
		children = new Vector<Symbol>();
		if (r.getRHS().numChildren == 0) {
			// special handling for state-change rules
			if (rs.states.contains(r.getRHS().getLabel())) {
				label = null;
				children.add(r.getRHS().getLabel());
			}
			else {
				label = r.getRHS().getLabel();
			}
		}
		else {
			label = r.getRHS().getLabel();
			for (int i = 0; i < r.getRHS().numChildren; i++) {
				if (r.getRHS().children[i].numChildren > 0)
					throw new ImproperConversionException("Can only convert normal-form rules!");
				children.add(r.getRHS().children[i].getLabel());
			}
		}
	}
	private static final int PERIOD=100000;
	private static int counter=0;
	// build rule by hand
	public ConcreteGrammarRule(Symbol s, Symbol l, Vector<Symbol> c, double w) {
		state = s;
		label = l;
		children = c;
		weight = w;
		counter++;
//		if (counter % PERIOD == 0) {
//			Debug.debug(true, "Built "+counter+" ConcreteGrammarRules");
//		}
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
