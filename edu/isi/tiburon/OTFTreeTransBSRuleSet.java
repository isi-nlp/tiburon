package edu.isi.tiburon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;

// BS rule set that is the result of forward application of a tree on a tree
// transducer. does it lazily ("otf"). but generates all rules for a state
public class OTFTreeTransBSRuleSet implements BSRuleSet {

	
	private StateTreePair startState;
	
	private CascadeTransducer trs;
	// memorize the already-generated rules
	private HashMap<Symbol, Vector<Rule>> memo;
	// rule set just to have something to index rules
	// TODO: get rid of this!
	private RuleSet rs;
	private boolean isNorm;
	private int ruleCount;
	public int getRuleCount() { return ruleCount; }
	public void reportRules() { 
		Debug.debug(true, "Created "+getRuleCount()+" rules");
	//	gram.reportRules();
	}
	
	public OTFTreeTransBSRuleSet(TreeItem tree, CascadeTransducer trans, boolean isnorm) throws UnusualConditionException {
		startState = SymbolFactory.getStateTreePair(trans.getStartState(), tree, 1, tree.getLeaves().length);
		isNorm = isnorm;
		trs = trans;
		if (trans instanceof TreeTransducerRuleSet) {
			rs = new RTGRuleSet(trans.getSemiring());
		}
		else {
			rs = new CFGRuleSet(trans.getSemiring());
		}
			
		memo = new HashMap<Symbol, Vector<Rule>>();
	}
	
	
	// generate the rules for this state now
	private void buildRules(StateTreePair state) {
		// TODO: string handling!!
		// TODO: build rules without the old code -- avoid memory issues?
		// match symbol. If needed, single-symbol lookahead on tree okay
		Symbol treeSym = state.getTree().getLabel();
		int rank = state.getTree().getNumChildren();
		// regular matching rules
		Iterator iter = trs.getBackwardRules(state.getState(), treeSym, rank).iterator();
		while (iter.hasNext()) {
			TreeTransducerRule ttr = (TreeTransducerRule)iter.next();
			// single-symbol lookahead
			if (ttr.isLookahead()) {
				boolean isOK = true;
				for (int i = 0; i < ttr.getLHS().getNumChildren(); i++) {
					if (ttr.getLHS().getChild(i).hasLabel() &&
							!state.getTree().getChild(i).getLabel().equals(ttr.getLHS().getChild(i).getLabel())) {
						isOK = false;
						break;
					}
				}
				if (!isOK)
					continue;
			}
			// build the rule
			// this is needed
			//HashSet<StateTreePair> states = new HashSet<StateTreePair> ();
			HashMap<TransducerLeftTree, TreeItem> h = ttr.getLHS().getInverseTreeMatchPairs(state.getTree());
			if (isNorm) {
				// TODO: make norm-form rules!!
			}
			else {
				Rule rule = ttr.buildItem(state, h, rs, null, ttr.getWeight(), rs.getSemiring());
				ruleCount++;
				if (!memo.containsKey(state))
					memo.put(state, new Vector<Rule>());
				memo.get(state).add(rule);
			}
		}
		// eps rules
		iter = trs.getBackwardRules(state.getState(), Symbol.getEpsilon(), 1).iterator();
		while (iter.hasNext()) {
			TreeTransducerRule ttr = (TreeTransducerRule)iter.next();
			// lookahead checks current symbol
			if (ttr.isLookahead()) {
				if (!treeSym.equals(ttr.getLHS().getLabel()))
					continue;
			}
			// build the rule
			// this is needed
			//HashSet<StateTreePair> states = new HashSet<StateTreePair> ();
			HashMap<TransducerLeftTree, TreeItem> h = ttr.getLHS().getInverseTreeMatchPairs(state.getTree());
			if (isNorm) {
				// TODO: make norm-form rules!!
			}
			else {
				Rule rule = ttr.buildItem(state, h, rs, null, ttr.getWeight(), rs.getSemiring());
				ruleCount++;
				if (!memo.containsKey(state))
					memo.put(state, new Vector<Rule>());
				memo.get(state).add(rule);
			}
		}
		
	}
	public PIterator<Rule> getBSIter(Symbol s) throws UnusualConditionException {
		if (!memo.containsKey(s)) {
			if (s instanceof StateTreePair)
				buildRules((StateTreePair)s);
			else
				throw new UnusualConditionException("tried to build rules from invalid symbol "+s);
		}
		return new WrappedPIterator<Rule>(memo.get(s).iterator());
	}

	public Symbol getStartState() {
		return startState;
	}

	// only a weak check
	public boolean isState(Symbol s) {
		return s instanceof StateTreePair;
	}
}
