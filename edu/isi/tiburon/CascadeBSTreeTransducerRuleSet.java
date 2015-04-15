package edu.isi.tiburon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import edu.stanford.nlp.util.FixedPrioritiesPriorityQueue;


// just for getting bs from a tree transducer in cascade training
// don't succumb to features!!

public class CascadeBSTreeTransducerRuleSet implements CascadeTransducer {
	private Symbol startState;
		// filtered backward
	private PMap<Symbol, PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>>> labelbrules;

	private Semiring semiring;
	
	public Symbol getStartState() { return startState; }
	public Semiring getSemiring() { return semiring; }
	
	private static Vector<TreeTransducerRule> empty;
	static {
		empty = new Vector<TreeTransducerRule>();
	}
	
	public Iterable<TreeTransducerRule> getBackwardRules(Symbol state, Symbol label, int rank) {
		if (labelbrules.goc(state).goc(label).containsKey(rank))
			return labelbrules.get(state).get(label).get(rank);
		return empty;
	}
	
	public CascadeBSTreeTransducerRuleSet(TreeTransducerRuleSet trans) throws UnusualConditionException {
		startState = trans.getStartState();
		
		boolean debug = false;
		labelbrules = new PMap<Symbol, PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>>>();
		
		for (Symbol state : trans.getStates()) {
			PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>> sortingLabelBsrules = new PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>>();
		
			for (TransducerRule rawr : trans.getRulesOfType(state)) {
				TreeTransducerRule r = (TreeTransducerRule)rawr;
				Symbol s = r.isInEps() ? Symbol.getEpsilon() : r.getLHS().getLabel();
				int rank = r.isInEps() ? 1 : r.getLHS().getNumChildren();

				// add to backward rule map

				if (!sortingLabelBsrules.goc(s).containsKey(rank)) {
					sortingLabelBsrules.get(s).put(rank, new FixedPrioritiesPriorityQueue<TreeTransducerRule>());
				}
				sortingLabelBsrules.get(s).get(rank).add(r, -r.getWeight());
				
	
			}
			
			// convert to labelbrules
			// add to backward rule map
			for (Symbol rsym : sortingLabelBsrules.keySet()) {
				for (Integer i : sortingLabelBsrules.get(rsym).keySet()) {
					labelbrules.goc(state).goc(rsym).put(i, new Vector<TreeTransducerRule>(sortingLabelBsrules.get(rsym).get(i).toSortedList()));	
				}
			}
		}
	}
}
