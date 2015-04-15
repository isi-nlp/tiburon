package edu.isi.tiburon;

import java.util.HashMap;
import java.util.Vector;

// BS rule set that is the result of forward application of a tree on a tree
// transducer. does it non-lazily ("bucket").
public class BucketTreeTransBSRuleSet implements BSRuleSet {

	// store the rules
	private RuleSet rules;
	public int getRuleCount() { return rules.getNumRules(); }
	public void reportRules() { 
		Debug.debug(true, "Created "+getRuleCount()+" rules");
//		Debug.debug(true, rules.toString());
	}
	public BucketTreeTransBSRuleSet(TreeItem tree, TransducerRuleSet trans, boolean isNorm) {
		boolean debug = false;
		// do forward application here
		if (trans instanceof TreeTransducerRuleSet) {
			RTGRuleSet rtg = new RTGRuleSet(tree, (TreeTransducerRuleSet)trans);
			if (debug) Debug.debug(debug, "Before making norm: "+rtg.getNumRules());
			if (isNorm)
				rtg.makeNormal();
			if (debug) Debug.debug(debug, "After making norm: "+rtg.getNumRules());

			rules = rtg;
			if (debug) {
				Debug.debug(debug, "Applied tree to transducer:");
				for (Rule rule : rtg.getRules()) {
					Debug.debug(debug, rule+"");
				}
			}
		}
		else {
			CFGRuleSet cfg = new CFGRuleSet(tree, (StringTransducerRuleSet)trans);
			rules = cfg;
		}
	}
	public PIterator<Rule> getBSIter(Symbol s){
		if (rules.getRulesOfType(s) == null) {
			Debug.debug(true, "No rules for "+s);
			return null;
		}
		return new WrappedPIterator<Rule>(rules.getRulesOfType(s).iterator());
	}

	public Symbol getStartState() {
		return rules.getStartState();
	}

	public boolean isState(Symbol s) {
		return rules.getStates().contains(s);
	}
}
