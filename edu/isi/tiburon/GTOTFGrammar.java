package edu.isi.tiburon;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Vector;

import edu.stanford.nlp.util.FixedPrioritiesPriorityQueue;

// Grammar + Tree Transducer on-the-fly grammar
public class GTOTFGrammar extends Grammar {

	// match two rules and form rtg rules. then add all the results
	// lazy bs assumptions: only doing BS formation. return the top rule
	private GrammarRule lazyBsFormAndAddRules(GrammarRule g, TreeTransducerRule r, FilteringPairSymbol.FILTER f) throws UnusualConditionException {

		HashMap<TransducerRightSide, FilteringPairSymbol> varmap = new HashMap<TransducerRightSide, FilteringPairSymbol>();
		
		boolean debug = false;
		
		// only allow eps-eps matches (or label-label matches) here
		if (g.getLabel() == null && !r.isInEps())
			throw new UnusualConditionException("Tried to map epsilon grammar rule "+g+" to transducer rule "+r);
		if (g.getLabel() != null && r.isInEps())
			throw new UnusualConditionException("Tried to map grammar rule "+g+" to in-eps transducer rule "+r);
		if (debug) Debug.debug(debug, "Trying to form rules with grammar rule "+g+" and transducer rule "+r+" and filter "+f);

		// check to avoid repeats -- this supersedes individual checks above!
		if (
				filteringRulePairs.containsKey(g) && 
				filteringRulePairs.get(g).containsKey(r) && 
				filteringRulePairs.get(g).get(r).containsKey(f)) {
			if (debug) Debug.debug(debug, "Returning extant rule");
			return filteringRulePairs.get(g).get(r).get(f);
		}

		if (!filteringRulePairs.containsKey(g))
			filteringRulePairs.put(g, new HashMap<TreeTransducerRule, HashMap<FilteringPairSymbol.FILTER, GrammarRule>>());
		if (!filteringRulePairs.get(g).containsKey(r))
			filteringRulePairs.get(g).put(r, new HashMap<FilteringPairSymbol.FILTER, GrammarRule>());

		
		// map variables to join of rtg rule state and ttr rule state

		// eps-eps case: don't allow rules from anything other than NOEPS.
		if (g.getLabel() == null && r.isInEps()) {
			if (!f.equals(FilteringPairSymbol.FILTER.NOEPS))
				throw new UnusualConditionException("Tried to build rule out of eps-eps pair "+g+" and "+r+" with filter "+f);
			for (TransducerRightSide trt : r.getTRVM().getRHS(r.getLHS())) {
				FilteringPairSymbol childState = FilteringPairSymbol.get(g.getChildren().get(0), trt.getState(), FilteringPairSymbol.FILTER.NOEPS);
				if (varmap.containsKey(trt))
					throw new UnusualConditionException("Shouldn't have more than one mapping for variable: "+r+" and "+g);
				varmap.put(trt, childState);
				if(debug) Debug.debug(debug, "Mapped "+trt.getVariable()+" to "+childState.toInternalString());
			}
		}
		// normal case
		else {
			for (int i = 0; i < g.getChildren().size(); i++) {
				for (TransducerRightSide trt : r.getTRVM().getRHS(r.getLHS().getChild(i))) {
					FilteringPairSymbol childState = FilteringPairSymbol.get(g.getChildren().get(i), trt.getState(), FilteringPairSymbol.FILTER.NOEPS);
					if (varmap.containsKey(trt))
						throw new UnusualConditionException("Shouldn't have more than one mapping for variable: "+r+" and "+g);
					varmap.put(trt, childState);
					if(debug) Debug.debug(debug, "Mapped "+trt.getVariable()+" to "+childState.toInternalString());
				}
			}
		}
		
		FilteringPairSymbol state = FilteringPairSymbol.get(g.getState(), r.getState(), f);
		
		GrammarRule ret = null;
		for (GrammarRule newg : getProductions(state, 
				g.hashCode(), r.hashCode(),
				(TransducerRightTree)r.getRHS(), 
				varmap, 
				semiring.times(g.getWeight(), r.getWeight()), 
				g, r,
				true)) {
			if (debug) Debug.debug(debug, "Formed "+newg);
			ruleCount++;
			Symbol newgst = newg.getState();

			// the rule we were looking for
			if (state.equals(newgst)) {
				if (ret != null)
					throw new UnusualConditionException("Already found "+ret+" but also found "+newg);
				ret = newg;
			}
			// remainder rules -- should actually only be one per state
			else {
//				if (!bsrules.containsKey(newgst))
//					bsrules.put(newgst, new Vector<GrammarRule>());
				auxBsRules.put(newgst, newg);
			}
		}
		if (ret == null)
			throw new UnusualConditionException("Didn't make rule with "+state);
		filteringRulePairs.get(g).get(r).put(f, ret);
		return ret;
	}
	
	// match epsilon (State change) grammar rule with state from transducer and form rtg rule. then
	// return the result
	private GrammarRule lazyBsFormAndAddRules(GrammarRule g, FilteringPairSymbol fs) throws UnusualConditionException {
		boolean debug = false;
		Symbol s = fs.getRight();
		FilteringPairSymbol.FILTER f = fs.getFilter();
		if (g.getLabel() != null || g.getChildren().size() != 1)
			throw new UnusualConditionException("Tried to map non-epsilon grammar rule "+g+" to transducer state "+s);
		if (debug) Debug.debug(debug, "Trying to form rules with grammar rule "+g+" and transducer state "+s+" and filter "+f);

		// check to avoid repeats
		// check to avoid repeats -- this supersedes individual checks above!
		if (ruleStatePairs.containsKey(g) && ruleStatePairs.get(g).containsKey(fs)) {
	
			return ruleStatePairs.get(g).get(fs);
		}
		
		// instate can't be righteps
		if (f.equals(FilteringPairSymbol.FILTER.RIGHTEPS))
			return null;
		//  if filtering, outstate must be "LEFT". instate can be LEFT or NONE
		FilteringPairSymbol outstate = FilteringPairSymbol.get(g.getChildren().get(0), s, 
				(ALLOWINEPS ? FilteringPairSymbol.FILTER.LEFTEPS : FilteringPairSymbol.FILTER.NOEPS));
		Vector<Symbol> c = new Vector<Symbol>();
		c.add(outstate);
		


		FilteringPairSymbol instate = FilteringPairSymbol.get(g.getState(), s, f);


		GTOTFGrammarRule newg;
		if (g instanceof GTOTFGrammarRule)
			newg = new GTOTFGrammarRule(instate, null, c, g.getWeight(), ((GTOTFGrammarRule)g).getRules(), null);
		else
			newg = new GTOTFGrammarRule(instate, null, c, g.getWeight(), null, null);
			
		ruleCount++;
		if (debug) Debug.debug(debug, "Formed "+newg);
		ruleStatePairs.goc(g).put(fs, newg);
		return newg;
		
	}
	// match grammar state with epsilon transducer rule
	// add the result
	private GrammarRule lazyBsFormAndAddRules(FilteringPairSymbol fs, TreeTransducerRule r) throws UnusualConditionException {
		boolean debug = false;
		Symbol s = fs.getLeft();
		FilteringPairSymbol.FILTER f = fs.getFilter();
		if (debug) Debug.debug(debug, "Trying to form rules with grammar state "+s+" and in-eps rule "+r+" and filter "+f);
		HashMap<TransducerRightSide, FilteringPairSymbol> varmap = new HashMap<TransducerRightSide, FilteringPairSymbol>();
		if (!r.isInEps())
			throw new UnusualConditionException("Tried to map non-epsilon transducer rule "+r+" to grammar state "+s);

		// check to avoid repeats
		// check to avoid repeats -- this supersedes individual checks above!
		if (stateRulePairs.containsKey(fs) && stateRulePairs.get(fs).containsKey(r)) {
			if (debug) Debug.debug(debug, "Returning extant rule");
			return stateRulePairs.get(fs).get(r);
		}
		if (!stateRulePairs.containsKey(fs))
			stateRulePairs.put(fs, new HashMap<TreeTransducerRule, GrammarRule>());
		
		// if filtering, outstate must be "RIGHT". instate is whatever came in
		for (TransducerRightSide trt : r.getTRVM().getRHS(r.getLHS())) {
			FilteringPairSymbol childState = FilteringPairSymbol.get(s, trt.getState(), 
					(ALLOWINEPS ? FilteringPairSymbol.FILTER.RIGHTEPS : FilteringPairSymbol.FILTER.NOEPS));
			if (varmap.containsKey(trt))
				throw new UnusualConditionException("Shouldn't have more than one mapping for variable: "+r+" and "+s);
			varmap.put(trt, childState);
			if(debug) Debug.debug(debug, "Mapped "+trt.getVariable()+" to "+childState.toInternalString());
		}
		if (f.equals(FilteringPairSymbol.FILTER.LEFTEPS))
			throw new UnusualConditionException("Got left filter in right-eps rule constructor! "+s);
		FilteringPairSymbol instate = FilteringPairSymbol.get(s, r.getState(), f);
		GrammarRule ret = null;
		for (GrammarRule newg : getProductions(instate, 
				0, r.hashCode(),
				(TransducerRightTree)r.getRHS(), 
				varmap, 
				r.getWeight(),
				null, r, true)) { 
				
			ruleCount++;
			if (debug) Debug.debug(debug, "Formed "+newg);
			Symbol newgst = newg.getState();
			// the rule we were looking for
			if (instate.equals(newgst)) {
				if (ret != null)
					throw new UnusualConditionException("Already found "+ret+" but also found "+newg);
				ret = newg;
			}
			// remainder rules -- should actually only be one per state
			else {
//				if (!bsrules.containsKey(newgst))
//					bsrules.put(newgst, new Vector<GrammarRule>());
				auxBsRules.put(newgst, newg);
			}
		}
		if (ret == null)
			throw new UnusualConditionException("Didn't make rule with "+instate);
		stateRulePairs.get(fs).put(r, ret);
		return ret;
	}
	
	// match two rules and form rtg rules. then add all the results
	// lazy fs assumptions: only doing FS formation. return the rule keyed to the given tree node
	private GrammarRule lazyFsFormAndAddRules(GrammarRule g, TreeTransducerRule r, FilteringPairSymbol.FILTER f, TransducerRightTree node) throws UnusualConditionException {

		boolean debug = false;
		
		// only allow eps-eps matches (or label-label matches) here
		if (g.getLabel() == null && !r.isInEps())
			throw new UnusualConditionException("Tried to map epsilon grammar rule "+g+" to transducer rule "+r);
		if (g.getLabel() != null && r.isInEps())
			throw new UnusualConditionException("Tried to map grammar rule "+g+" to in-eps transducer rule "+r);
		if (debug) Debug.debug(debug, "Trying to form rules with grammar rule "+g+" and transducer rule "+r+" and filter "+f);

		// check to avoid repeats -- this supersedes individual checks above!
		if (
				forwardFilteringRulePairs.containsKey(g) && 
				forwardFilteringRulePairs.get(g).containsKey(r) && 
				forwardFilteringRulePairs.get(g).get(r).containsKey(f)) {
			if (debug) Debug.debug(debug, "Returning extant rule at "+g+", "+r+", "+f+", "+node);
			return forwardFilteringRulePairs.get(g).get(r).get(f).get(node);
		}

		if (!forwardFilteringRulePairs.containsKey(g))
			forwardFilteringRulePairs.put(g, new HashMap<TreeTransducerRule, HashMap<FilteringPairSymbol.FILTER, HashMap<TransducerRightTree, GrammarRule>>>());
		if (!forwardFilteringRulePairs.get(g).containsKey(r))
			forwardFilteringRulePairs.get(g).put(r, new HashMap<FilteringPairSymbol.FILTER,  HashMap<TransducerRightTree, GrammarRule>>());
		if (!forwardFilteringRulePairs.get(g).get(r).containsKey(f))
			forwardFilteringRulePairs.get(g).get(r).put(f, new HashMap<TransducerRightTree, GrammarRule>());

		HashMap<TransducerRightSide, FilteringPairSymbol> varmap = new HashMap<TransducerRightSide, FilteringPairSymbol>();

		// map variables to join of rtg rule state and ttr rule state

		// eps-eps case: don't allow rules from anything other than NOEPS.
		if (g.getLabel() == null && r.isInEps()) {
			if (!f.equals(FilteringPairSymbol.FILTER.NOEPS))
				throw new UnusualConditionException("Tried to build rule out of eps-eps pair "+g+" and "+r+" with filter "+f);
			for (TransducerRightSide trt : r.getTRVM().getRHS(r.getLHS())) {
				FilteringPairSymbol childState = FilteringPairSymbol.get(g.getChildren().get(0), trt.getState(), FilteringPairSymbol.FILTER.NOEPS);
				if (varmap.containsKey(trt))
					throw new UnusualConditionException("Shouldn't have more than one mapping for variable: "+r+" and "+g);
				varmap.put(trt, childState);
				if(debug) Debug.debug(debug, "Mapped "+trt.getVariable()+" to "+childState.toInternalString());
			}
		}
		// TODO: get filtering right!!
		
		// normal case
		else {
			for (int i = 0; i < g.getChildren().size(); i++) {
				for (TransducerRightSide trt : r.getTRVM().getRHS(r.getLHS().getChild(i))) {
				
					FilteringPairSymbol childState = FilteringPairSymbol.get(g.getChildren().get(i), trt.getState(), FilteringPairSymbol.FILTER.NOEPS);
					if (varmap.containsKey(trt.getVariable()))
						throw new UnusualConditionException("Shouldn't have more than one mapping for variable: "+r+" and "+g);
					varmap.put(trt, childState);
					if(debug) Debug.debug(debug, "Mapped "+trt.getVariable()+" to "+childState.toInternalString());
				}
			}
		}
		
		FilteringPairSymbol state = FilteringPairSymbol.get(g.getState(), r.getState(), f);

		GrammarRule ret = null;
		for (Map.Entry<TransducerRightTree, Vector<GrammarRule>> entry : getFSProductions(state, 
				g.hashCode(), r.hashCode(),
				(TransducerRightTree)r.getRHS(), 
				varmap, 
				semiring.times(g.getWeight(), r.getWeight()), 
				false).entrySet()) {
			TransducerRightTree loc = entry.getKey();
			for (GrammarRule newg : entry.getValue()) {
				ruleCount++;
				if (debug) Debug.debug(debug, "Formed "+newg+" mapped to "+loc);

				// the rule we were looking for -- should only conflict when it's a terminal
				// rule, so put the one with smaller rank in
				if (loc.equals(node)) {
					if (ret == null || ret.getChildren().size() > newg.getChildren().size())
						ret = newg;
				}

				
				// treat rule as an aux rule
				// if we've matched in the middle of a rule, store the covered aux state
				if (loc.hasLabel() && newg.getChildren().size() > 0) {
					// ugly: figure out the label, rank, and position
					TransducerRightTree parent = loc.parent;
					if (parent == null)
						throw new UnusualConditionException("Parent of "+loc+" shouldn't be null -- we formed "+newg);
					Symbol label = parent.getLabel();
					int rank = parent.getNumChildren();
					int pos = -1;
					for (int i = 0; i < rank; i++) {
						if (parent.getChild(i).equals(loc)) {
							pos = i;
							break;
						}
					}
					if (pos < 0)
						throw new UnusualConditionException("Couldn't find "+loc+" in "+parent);
					Symbol auxst = newg.getChild(pos);
					if (debug) Debug.debug(debug, "Storing "+newg+" in aux as "+auxst+"->"+pos+"->"+label+"->"+rank);
					// index by aux state/pos (and label/rank) remainder rules -- should actually only be one per state
					auxFsRules.goc(auxst).goc(pos).goc(label).put(rank, newg);
					allAuxFsRules.goc(auxst).put(pos, newg);
					
					// also store in parent's main rule slot for lexicals
					if (debug) Debug.debug(debug, "Storing "+newg+" in main as "+g+"->"+r+"->"+f+"->"+parent);
					forwardFilteringRulePairs.get(g).get(r).get(f).put(parent, newg);
					
					
				}
				// treat rule as a main rule
				else {
					// store all rules by the node they're keyed to
					if (debug) Debug.debug(debug, "Storing "+newg+" in main as "+g+"->"+r+"->"+f+"->"+loc);

					forwardFilteringRulePairs.get(g).get(r).get(f).put(loc, newg);
				}
			}
		}
		if (ret == null)
			throw new UnusualConditionException("Didn't make rule with "+state);
		return ret;
	}

	// do simple state mapping on an epsilon grammar rule (cf. formAndAddRules in TGOTF)
// given a symbol and grammar rule, 
	
	// match state from transducer with epsilon (State change) grammar rule and form rtg rule. then
	// return the result
	private GrammarRule lazyFsFormAndAddRules(GrammarRule g, FilteringPairSymbol fs) throws UnusualConditionException {
		boolean debug = false;
		Symbol s = fs.getRight();
		FilteringPairSymbol.FILTER f = fs.getFilter();
		if (g.getLabel() != null || g.getChildren().size() != 1)
			throw new UnusualConditionException("Tried to map transducer state "+s+" to non-epsilon grammar rule "+g);
		if (debug) Debug.debug(debug, "Trying to form rules with transducer state "+s+" and filter "+f+" and grammar rule "+g);

		// check to avoid repeats
		// check to avoid repeats -- this supersedes individual checks above!
		if (ruleStatePairs.containsKey(g) && ruleStatePairs.get(g).containsKey(fs)) {
			if (debug) Debug.debug(debug, "Returning extant rule");

			return ruleStatePairs.get(g).get(fs);
		}
		
		// outstate can't be righteps
		if (f.equals(FilteringPairSymbol.FILTER.RIGHTEPS))
			return null;
		//  if filtering, instate must be "LEFT". outstate is what it is
		FilteringPairSymbol instate = FilteringPairSymbol.get(g.getState(), s,  
				(ALLOWINEPS ? FilteringPairSymbol.FILTER.LEFTEPS : FilteringPairSymbol.FILTER.NOEPS));
		Vector<Symbol> c = new Vector<Symbol>();

		FilteringPairSymbol outstate = FilteringPairSymbol.get(g.getChild(0), s, f);
		c.add(outstate);
		

		ConcreteGrammarRule newg = new ConcreteGrammarRule(instate, null, c, g.getWeight());
		ruleCount++;
		if (debug) Debug.debug(debug, "Formed "+newg);
		ruleStatePairs.goc(g).put(fs, newg);
		return newg;	
	}
	
	
	
	
	// match two rules and form rtg rules. then add all the results
	private void formAndAddRules(GrammarRule g, TreeTransducerRule r) throws UnusualConditionException {
		HashMap<TransducerRightSide, FilteringPairSymbol> varmap = new HashMap<TransducerRightSide, FilteringPairSymbol>();
		
		boolean debug = false;

		// only allow eps-eps matches (or label-label matches) here
		if (g.getLabel() == null && !r.isInEps())
			throw new UnusualConditionException("Tried to map epsilon grammar rule "+g+" to transducer rule "+r);
		if (g.getLabel() != null && r.isInEps())
			throw new UnusualConditionException("Tried to map grammar rule "+g+" to in-eps transducer rule "+r);

		// check to avoid repeats -- this supersedes individual checks above!
		if (rulePairs.containsKey(g) && rulePairs.get(g).containsKey(r)) {
			if (debug) Debug.debug(debug, "Already paired "+g+" and "+r+"; not repeating");
			return;
		}
		if (debug) Debug.debug(debug, "Trying to form rules with grammar rule "+g+" and transducer rule "+r);

		if (!rulePairs.containsKey(g))
			rulePairs.put(g, new HashMap<TreeTransducerRule, GrammarRule>());
		rulePairs.get(g).put(r, null);
		
		// map variables to join of rtg rule state and ttr rule state

		// eps-eps case
		boolean isLabelLabel=true;
		if (g.getLabel() == null && r.isInEps()) {
			isLabelLabel=false;
			for (TransducerRightSide trt : r.getTRVM().getRHS(r.getLHS())) {
				FilteringPairSymbol childState = FilteringPairSymbol.get(g.getChildren().get(0), trt.getState(), FilteringPairSymbol.FILTER.NOEPS);
				if (varmap.containsKey(trt))
					throw new UnusualConditionException("Shouldn't have more than one mapping for variable: "+r+" and "+g);
				varmap.put(trt, childState);
				if(debug) Debug.debug(debug, "Mapped "+trt.getVariable()+" to "+childState.toInternalString());
			}
		}
		// normal case
		else {
			for (int i = 0; i < g.getChildren().size(); i++) {
				for (TransducerRightSide trt : r.getTRVM().getRHS(r.getLHS().getChild(i))) {
					FilteringPairSymbol childState = FilteringPairSymbol.get(g.getChildren().get(i), trt.getState(), FilteringPairSymbol.FILTER.NOEPS);
					if (varmap.containsKey(trt))
						throw new UnusualConditionException("Shouldn't have more than one mapping for variable: "+r+" and "+g);
					varmap.put(trt, childState);
					if(debug) Debug.debug(debug, "Mapped "+trt.getVariable()+" to "+childState.toInternalString());
				}
			}
		}
		// if label-label, start state can be any type here. if eps-eps, it can only be noeps
		for (FilteringPairSymbol.FILTER f : (ALLOWINEPS && isLabelLabel ? FilteringPairSymbol.FILTER.values() : nofilterarr)) {
			FilteringPairSymbol state = FilteringPairSymbol.get(g.getState(), r.getState(), f);
			

			for (GrammarRule newg : getProductions(state, 
					g.hashCode(), r.hashCode(),
					(TransducerRightTree)r.getRHS(), 
					varmap, 
					semiring.times(g.getWeight(), r.getWeight()), 
					g, r,
					true)) {
				ruleCount++;
				if (debug) Debug.debug(debug, "Formed "+newg);
				Symbol newgst = newg.getState();
				if (!bsrules.containsKey(newgst))
					bsrules.put(newgst, new Vector<GrammarRule>());
				bsrules.get(newgst).add(newg);
	
				Symbol label = newg.getLabel();
				int rank = newg.getChildren().size();
				if (label == null) {
					label = Symbol.getEpsilon();
					//				Debug.prettyDebug("Will be storing eps rule "+newg);
				}
				for (int i = 0; i < newg.getChildren().size(); i++) {
					Symbol c = newg.getChildren().get(i);
			

					// categorize by state, pos, label (might be eps)

					if (!posfrules.containsKey(c)) {
						posfrules.put(c, new HashMap<Integer, HashMap<Symbol, HashMap<Integer, Vector<GrammarRule>>>>());
						allposfrules.put(c, new HashMap<Integer, Vector<GrammarRule>>());
					}
					if (!posfrules.get(c).containsKey(i)) {
						posfrules.get(c).put(i, new HashMap<Symbol, HashMap<Integer, Vector<GrammarRule>>>());
						allposfrules.get(c).put(i, new Vector<GrammarRule>());
					}
					if (!posfrules.get(c).get(i).containsKey(label))
						posfrules.get(c).get(i).put(label, new HashMap<Integer, Vector<GrammarRule>>());
					if (!posfrules.get(c).get(i).get(label).containsKey(rank))
						posfrules.get(c).get(i).get(label).put(rank, new Vector<GrammarRule>());
					allposfrules.get(c).get(i).add(newg);
					posfrules.get(c).get(i).get(label).get(rank).add(newg);
				}
			}
		}
	}
	
	// match epsilon (State change) grammar rule with state from transducer and form rtg rule. then
	// add the result
	private void formAndAddRules(GrammarRule g, Symbol s) throws UnusualConditionException {
		boolean debug = false;
		if (g.getLabel() != null || g.getChildren().size() != 1)
			throw new UnusualConditionException("Tried to map non-epsilon grammar rule "+g+" to transducer state "+s);

		// check to avoid repeats
		// check to avoid repeats -- this supersedes individual checks above!
		if (ruleStatePairs.containsKey(g) && ruleStatePairs.get(g).containsKey(s)) {
			if (debug) Debug.debug(debug, "Already paired "+g+" and "+s+"; not repeating");
			return;
		}
		
		ruleStatePairs.goc(g).put(s, null);
		
		// if filtering, outstate must be "LEFT". instate can be LEFT or NONE
		FilteringPairSymbol outstate = FilteringPairSymbol.get(g.getChildren().get(0), s, 
				(ALLOWINEPS ? FilteringPairSymbol.FILTER.LEFTEPS : FilteringPairSymbol.FILTER.NOEPS));
		Vector<Symbol> c = new Vector<Symbol>();
		c.add(outstate);
		for (FilteringPairSymbol.FILTER f : (ALLOWINEPS ? FilteringPairSymbol.FILTER.values() : nofilterarr)) {
			if (f.equals(FilteringPairSymbol.FILTER.RIGHTEPS))
				continue;

			FilteringPairSymbol instate = FilteringPairSymbol.get(g.getState(), s, f);


			GTOTFGrammarRule newg;
			if (g instanceof GTOTFGrammarRule)
				newg = new GTOTFGrammarRule(instate, null, c, g.getWeight(), ((GTOTFGrammarRule) g).getRules(), null);
			else
				newg = new GTOTFGrammarRule(instate, null, c, g.getWeight(), null, null);

			ruleCount++;
			if (debug) Debug.debug(debug, "Formed "+newg);
			if (!bsrules.containsKey(instate))
				bsrules.put(instate, new Vector<GrammarRule>());
			bsrules.get(instate).add(newg);


			// categorize by state, pos, label 

			if (!posfrules.containsKey(outstate)) {
				posfrules.put(outstate, new HashMap<Integer, HashMap<Symbol, HashMap<Integer, Vector<GrammarRule>>>>());
				allposfrules.put(outstate, new HashMap<Integer, Vector<GrammarRule>>());
			}
			if (!posfrules.get(outstate).containsKey(0)) {
				posfrules.get(outstate).put(0, new HashMap<Symbol, HashMap<Integer, Vector<GrammarRule>>>());
				allposfrules.get(outstate).put(0, new Vector<GrammarRule>());
			}
			if (!posfrules.get(outstate).get(0).containsKey(Symbol.getEpsilon()))
				posfrules.get(outstate).get(0).put(Symbol.getEpsilon(), new HashMap<Integer, Vector<GrammarRule>>());
			if (!posfrules.get(outstate).get(0).get(Symbol.getEpsilon()).containsKey(1))
				posfrules.get(outstate).get(0).get(Symbol.getEpsilon()).put(1, new Vector<GrammarRule>());
			allposfrules.get(outstate).get(0).add(newg);
			posfrules.get(outstate).get(0).get(Symbol.getEpsilon()).get(1).add(newg);
		}
	}
	
	
	// match state from grammar rule with input epsilon transducer rule and form rtg rule. then
	// add the result
	private void formAndAddRules(Symbol s, TreeTransducerRule r) throws UnusualConditionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Trying to form rules with grammar state "+s+" and in-eps rule "+r);
		HashMap<TransducerRightSide, FilteringPairSymbol> varmap = new HashMap<TransducerRightSide, FilteringPairSymbol>();
		if (!r.isInEps())
			throw new UnusualConditionException("Tried to map non-epsilon transducer rule "+r+" to grammar state "+s);

		// check to avoid repeats
		// check to avoid repeats -- this supersedes individual checks above!
		if (stateRulePairs.containsKey(s) && stateRulePairs.get(s).containsKey(r)) {
			if (debug) Debug.debug(debug, "Already paired "+s+" and "+r+"; not repeating");
			return;
		}
		if (!stateRulePairs.containsKey(s))
			stateRulePairs.put(s, new HashMap<TreeTransducerRule, GrammarRule>());
		stateRulePairs.get(s).put(r, null);
		// if filtering, outstate must be "RIGHT". instate can be RIGHT or NONE
		for (TransducerRightSide trt : r.getTRVM().getRHS(r.getLHS())) {
			FilteringPairSymbol childState = FilteringPairSymbol.get(s, trt.getState(), 
					(ALLOWINEPS ? FilteringPairSymbol.FILTER.RIGHTEPS : FilteringPairSymbol.FILTER.NOEPS));
			if (varmap.containsKey(trt))
				throw new UnusualConditionException("Shouldn't have more than one mapping for variable: "+r+" and "+s);
			varmap.put(trt, childState);
			if(debug) Debug.debug(debug, "Mapped "+trt.getVariable()+" to "+childState.toInternalString());
		}
		for (FilteringPairSymbol.FILTER f : (ALLOWINEPS ? FilteringPairSymbol.FILTER.values() : nofilterarr)) {
			if (f.equals(FilteringPairSymbol.FILTER.LEFTEPS))
				continue;
			FilteringPairSymbol instate = FilteringPairSymbol.get(s, r.getState(), f);

			for (GrammarRule newg : getProductions(instate, 
					0, r.hashCode(),
					(TransducerRightTree)r.getRHS(), 
					varmap, 
					r.getWeight(),
					null,
					r,
					true)) {
				ruleCount++;
				if (debug) Debug.debug(debug, "Formed "+newg);
				Symbol newgst = newg.getState();
				if (!bsrules.containsKey(newgst))
					bsrules.put(newgst, new Vector<GrammarRule>());
				bsrules.get(newgst).add(newg);
	
				Symbol label = newg.getLabel();
				int rank = newg.getChildren().size();
				if (label == null) {
					label = Symbol.getEpsilon();
					//				Debug.prettyDebug("Will be storing eps rule "+newg);
				}
				for (int i = 0; i < newg.getChildren().size(); i++) {
					Symbol c = newg.getChildren().get(i);
		

					// categorize by state, pos, label (might be eps)

					if (!posfrules.containsKey(c)) {
						posfrules.put(c, new HashMap<Integer, HashMap<Symbol, HashMap<Integer, Vector<GrammarRule>>>>());
						allposfrules.put(c, new HashMap<Integer, Vector<GrammarRule>>());
					}
					if (!posfrules.get(c).containsKey(i)) {
						posfrules.get(c).put(i, new HashMap<Symbol, HashMap<Integer, Vector<GrammarRule>>>());
						allposfrules.get(c).put(i, new Vector<GrammarRule>());
					}
					if (!posfrules.get(c).get(i).containsKey(label))
						posfrules.get(c).get(i).put(label, new HashMap<Integer, Vector<GrammarRule>>());
					if (!posfrules.get(c).get(i).get(label).containsKey(rank))
						posfrules.get(c).get(i).get(label).put(rank, new Vector<GrammarRule>());
					allposfrules.get(c).get(i).add(newg);
					posfrules.get(c).get(i).get(label).get(rank).add(newg);
				}
			}
		}
	}
	// form of forward that can handle deletion and reordering. returns based on position in rule!!

	@Override
	public Iterable<GrammarRule> getForwardRules(Symbol s, int pos) throws UnusualConditionException {
		boolean debug = false;
		if (!fposdone.containsKey(s) || !fposdone.get(s).contains(pos)) {
			if (s instanceof FilteringPairSymbol) {
				Symbol a = ((FilteringPairSymbol)s).getLeft();
				Symbol b = ((FilteringPairSymbol)s).getRight();
				FilteringPairSymbol.FILTER f = ((FilteringPairSymbol)s).getFilter();
				if (debug) Debug.debug(debug, "Getting position "+pos+" forward star of "+s+"("+a+", "+b+", "+f+")");
				if (!fposdone.containsKey(s))
					fposdone.put(s, new HashSet<Integer>());
				fposdone.get(s).add(pos);
				// special handling of epsilon rules: they get propagated!
				// if filtering, left eps only allowed if s is left eps
				if ((!ALLOWINEPS || f == FilteringPairSymbol.FILTER.LEFTEPS) && pos == 0) {
					for (GrammarRule g : grammar.getForwardRules(a, 0, Symbol.getEpsilon(), 1)) {
						if (debug) Debug.debug(debug, "Matching "+g+" with state "+b);
						formAndAddRules(g, b);

					}

				}
				// get transducer rules that match the pos and state in their rhs
				// nothing to add here if s is left eps
				if (f != FilteringPairSymbol.FILTER.LEFTEPS) {
					if (debug) Debug.debug(debug, "Checking rules with "+b+" at pos "+pos);
					for (TreeTransducerRule r : trans.getRelPosForwardRules(b, pos)) {
						// handling for in-eps rules: find grammar eps and also handle on own
						// only allowed when filtering is allowed
						if (ALLOWINEPS && r.isInEps()) {
//							if (debug) Debug.debug(debug, "Got in-eps rule "+r);
							// right eps only allowed if s is right eps
							if (f == FilteringPairSymbol.FILTER.RIGHTEPS)
								formAndAddRules(a, r);
							// non-eps only allowed if s is non eps
							else if (f == FilteringPairSymbol.FILTER.NOEPS) {
								for (GrammarRule g : grammar.getForwardRules(a, 0, Symbol.getEpsilon(), 1)) {
									//							if (debug) Debug.debug(debug, "Matching eps rules "+g+" and "+r);
									formAndAddRules(g, r);
								}
							}
						}
						else if (f == FilteringPairSymbol.FILTER.NOEPS) {
							// figure out which symbol and state pos the retrieved state has moved to 
							// -- could be multiple pos
							Symbol lhssym = r.getLHS().getLabel();
							int rank = r.getLHS().getNumChildren();
							for (int rtgpos = 0; rtgpos < r.getLHS().getNumChildren(); rtgpos++) {
								HashSet<TransducerRightSide> rhscheck = r.getTRVM().getRHS(r.getLHS().getChild(rtgpos));
								if (rhscheck.size() > 1)
									throw new UnusualConditionException("Copying rule "+r);
								// rhs should match state and relative pos (or, if pos is 0, empty parent is okay)
								for (TransducerRightSide rawrhsitem : rhscheck) {
									TransducerRightTree rhsitem = (TransducerRightTree)rawrhsitem;
									if (rhsitem.getState().equals(b)) {
										if((pos == 0 && rhsitem.parent == null) || rhsitem.parent.getChild(pos).equals(rhsitem)) {
											//		if (debug) Debug.debug(debug, r+" makes us look for "+a+" , "+rtgpos+" , "+lhssym);
											for (GrammarRule g : grammar.getForwardRules(a, rtgpos, lhssym, rank)) {
												if (debug) Debug.debug(debug, "Matching "+g+" with "+r);
												formAndAddRules(g, r);
											}
										}
									}
								}
							}
						}
					}
				}

			}
			else 
				throw new UnusualConditionException("Tried to do lf operation with "+s+" which is not a pair symbol");
		}
		
		if (allposfrules.containsKey(s) && allposfrules.get(s).containsKey(pos)) {
			if (debug) Debug.debug(debug, "Got "+allposfrules.get(s).get(pos).size()+" for FS of "+s+" in pos "+pos);
			return allposfrules.get(s).get(pos);
		}
		if (debug) Debug.debug(debug, "Got nothing for FS of "+s+" in pos "+pos);

		return empty;
	}
	
	// do the more general operation above (TODO: maybe don't do it like this?)
	@Override
	public Iterable<GrammarRule> getForwardRules(Symbol s, int pos, Symbol l, int r) throws UnusualConditionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Getting position "+pos+" forward star of "+s+" with symbol "+l+" and rank "+r);
		getForwardRules(s, pos);
		if (
				posfrules.containsKey(s) && 
				posfrules.get(s).containsKey(pos) &&
				posfrules.get(s).get(pos).containsKey(l) &&
				posfrules.get(s).get(pos).get(l).containsKey(r)
		)
			return posfrules.get(s).get(pos).get(l).get(r);
		return empty;
	}
	
	

	
	@Override
	public Iterable<GrammarRule> getBackwardRules(Symbol s)
	throws UnusualConditionException {
		boolean debug = false;
		Vector<GrammarRule> vec = new Vector<GrammarRule>();
		PIterator<GrammarRule> it = getBSIter(s);
		while (it.hasNext())
			vec.add(it.next());
		return vec;

//		if (!bsdone.contains(s)) {
//			if (s instanceof FilteringPairSymbol) {
//				Symbol a = ((FilteringPairSymbol)s).getLeft();
//				Symbol b = ((FilteringPairSymbol)s).getRight();
//				FilteringPairSymbol.FILTER f = ((FilteringPairSymbol)s).getFilter();
//				if (debug) Debug.debug(debug, "Getting backward star of "+s+"("+a+", "+b+")");
//				bsdone.add(s);
//				// backward star: for each rule in bs from a, get match to state and symbol in b. combine
//				// to build a new grammarRule
//				if (!bsrules.containsKey(s))
//					bsrules.put(s, new Vector<GrammarRule>());
//				
//				for (GrammarRule g : grammar.getBackwardRules(a)) {
//				//	if (debug) Debug.debug(debug, "Considering "+g);
//					// epsilon rules
//					if (g.getLabel() == null) {
//					//	if (debug) Debug.debug(debug, "Matching "+g+" with state "+b);
//						// can't do left eps if in right eps
//						// only an issue if filtering
//						if (f != FilteringPairSymbol.FILTER.RIGHTEPS)
//							formAndAddRules(g, b);
//						// if filtering, and in NOEPS, can attempt epsilon-epsilon case
//						if (ALLOWINEPS && f.equals(FilteringPairSymbol.FILTER.NOEPS)) {
//							for (TreeTransducerRule r : trans.getBackwardRules(b, Symbol.getEpsilon(), 1))
//								formAndAddRules(g, r, true, !KBAS.useIndexForward, debug);
//						}
//					}
//					// normal rules
//					else {
//						for (TreeTransducerRule r : trans.getBackwardRules(b, g.getLabel(), g.getChildren().size())) {
//						//	if (debug) Debug.debug(debug, "Matching "+g+" with "+r);
//							formAndAddRules(g, r, true, !KBAS.useIndexForward, debug);
//						}
//						
//					}
//				}
//				// if filtering,
//				// attempt trans-epsilon case
//				// can't do right eps if in left eps
//				if (ALLOWINEPS) {
//					if (f != FilteringPairSymbol.FILTER.LEFTEPS) {
//						for (TreeTransducerRule r : trans.getBackwardRules(b, Symbol.getEpsilon(), 1))
//							formAndAddRules(a, r, true, !KBAS.useIndexForward);
//					}
//				}
//			}
//			else 
//				throw new UnusualConditionException("Tried to do bs operation with "+s+" which is not a pair symbol");
//		}
//		if (debug) Debug.debug(debug, "Got "+bsrules.get(s).size()+" for BS of "+s);
//		if (debug) Debug.debug(debug, "Got "+bsrules.get(s));
//
//		return bsrules.get(s);
	}
	
	// don't actually need the label-specific backward rules yet
	public Iterable<GrammarRule> getBackwardRules(Symbol s, Symbol l, int r) throws UnusualConditionException {
		throw new UnusualConditionException("specific getBackwardRules disabled for GTOTF!");
	}

	
	// generally make a lazy iterator and return it
	// if the state is artificially made, just return the already-created list
	public PIterator<GrammarRule> getBSIter(Symbol s) throws UnusualConditionException {
			boolean debug = false;
		if (s instanceof FilteringPairSymbol) {
			if (debug) Debug.debug(debug, "Getting lazy iterator for filtering pair symbol "+s);
//			return new BSLazyIterator(grammar, trans, (FilteringPairSymbol)s, maxheap);
			return new BSIndexedIterator(grammar, trans, (FilteringPairSymbol)s, maxheap);

		}
		else {
			if (debug) Debug.debug(debug, "Getting lazy iterator for 'normal' symbol "+s);
			if (!auxBsRules.containsKey(s))
				throw new UnusualConditionException("Asked for rules from unknown state "+s);
			return new WrappedPIterator<GrammarRule> (auxBsRules.get(s));
		}
	}
	
	
	// filtered version of BSIter
	public PIterator<GrammarRule> getBSIter(Symbol s, Symbol l, int r) throws UnusualConditionException {
		throw new UnusualConditionException("specific getBSIter disabled for GTOTF!");
	}
	
	// lazy forward star calls
	// unfiltered lexical
	public PIterator<GrammarRule> getLexFSIter() throws UnusualConditionException {
		return new FSLazyIterator(grammar, trans, maxheap);
	}
	
	// lazy forward star calls
	// filtered lexical
	public PIterator<GrammarRule> getLexFSIter(Symbol label, int rank) throws UnusualConditionException {
		return new FSLazyIterator(grammar, trans, label, rank, maxheap);
	}
	
	@Override
	public PIterator<GrammarRule> getFSIter(Symbol s)
			throws UnusualConditionException {
		// TODO Auto-generated method stub
		return null;
	}
	
	public PIterator<GrammarRule> getFSIter(Symbol s, int pos) throws UnusualConditionException {
			boolean debug = false;
		if (s instanceof FilteringPairSymbol) {
			if (debug) Debug.debug(debug, "Getting lazy FS iterator for filtering pair symbol "+s);
			return new FSLazyIterator(grammar, trans, (FilteringPairSymbol)s, pos, maxheap);
		}
		
		else {
			if (debug) Debug.debug(debug, "Getting lazy iterator for 'normal' symbol "+s+" at pos "+pos);
			if (!allAuxFsRules.containsKey(s))
				throw new UnusualConditionException("Asked for rules from unknown state "+s);
			return new WrappedPIterator<GrammarRule> (allAuxFsRules.get(s).get(pos));
		}
	}
	
	
	// filtered version of BSIter
	public PIterator<GrammarRule> getFSIter(Symbol s, int pos, Symbol l, int r) throws UnusualConditionException {
		boolean debug = false;
		if (s instanceof FilteringPairSymbol) {
			if (debug) Debug.debug(debug, "Getting lazy FS iterator for filtering pair symbol "+s+", label "+l+" and rank "+r);
			return new FSLazyIterator(grammar, trans, (FilteringPairSymbol)s, pos, l, r, maxheap);
		}
		
		else {
			if (debug) Debug.debug(debug, "Getting lazy iterator for 'normal' symbol "+s+" at pos "+pos+" with label "+l+" and rank "+r);
			if (
					auxFsRules.containsKey(s) &&
					auxFsRules.get(s).containsKey(pos) &&
					auxFsRules.get(s).get(pos).containsKey(l) &&
					auxFsRules.get(s).get(pos).get(l).containsKey(r)
			)
				return new WrappedPIterator<GrammarRule> (auxFsRules.get(s).get(pos).get(l).get(r));
			return  new WrappedPIterator<GrammarRule> (empty.iterator());
		}
	}
	
	@Override
	boolean injectState(Symbol s, double wgt) throws UnusualConditionException {
		return true;
	}
	
	void reportRules() {
		Debug.prettyDebug("GTOTF Grammar has "+ruleCount+" rules");
		grammar.reportRules();
	}
	// allows memoization of BS members and multiple simultaneous access of the same list
	private HashMap<Symbol, Vector<GrammarRule>> bsResultTable;
	private HashMap<Symbol, BSLazyIterator> bsIterTable;
	private class BSIndexedIterator implements PIterator<GrammarRule> {
		private int next;
		private Symbol state;
		private Vector<GrammarRule> results;
		private BSLazyIterator iterator;
		public BSIndexedIterator(Grammar gram, OTFTreeTransducerRuleSet trans, FilteringPairSymbol s, int mh) throws UnusualConditionException {
			next = 0;
			state = s;
			if (!bsIterTable.containsKey(state))
				bsIterTable.put(state, new BSLazyIterator(gram, trans, s, mh));
			if (!bsResultTable.containsKey(state))
				bsResultTable.put(state, new Vector<GrammarRule>());
			results = bsResultTable.get(state);
			iterator = bsIterTable.get(state);
		}
		public boolean hasNext() {
			return results.size() > next || iterator.hasNext();
		}
		public GrammarRule peek() throws NoSuchElementException {
			if (!hasNext())
				throw new NoSuchElementException("Asked for peek on empty PIterator");
			if (results.size() > next)
				return results.get(next);
			else
				return iterator.peek();
		}
		public GrammarRule next() throws NoSuchElementException {
			boolean debug = false;
			if (!hasNext())
				throw new NoSuchElementException("Asked for peek on empty PIterator");
			if (debug) Debug.debug(debug, "Looking for "+next+" of "+state);
			if (results.size() <= next) {
				if (debug) Debug.debug(debug, "Results only has "+results.size());				
				results.add(iterator.next());
			}
			return results.get(next++);
		}
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BsIndexedIterator");
		}
	}
	
	
	// implementation of pervasive laziness
	private class BSLazyIterator implements PIterator<GrammarRule> {
		// rtg iter
		private PIterator<GrammarRule> aiter;
		// trans, which makes iters
		private OTFTreeTransducerRuleSet btrans;
		// filtering pair symbol, which contains trans state b and filter state f
		private FilteringPairSymbol fs;
		// main queue
		private FixedPrioritiesPriorityQueue<PIterator<GrammarRule>> q;
		// wait queue
		private FixedPrioritiesPriorityQueue<PIterator<GrammarRule>> w;
		// next items
		private FixedPrioritiesPriorityQueue<GrammarRule> next;
		// for checking monotonicity
		private double lastcost;
		// how big a wait heap?
		private int maxheap;
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;
		
		public BSLazyIterator(Grammar gram, OTFTreeTransducerRuleSet trans, FilteringPairSymbol s, int mh) throws UnusualConditionException {
			boolean debug = false;
			// initialize elements
			q = new FixedPrioritiesPriorityQueue<PIterator<GrammarRule>>();
			// wait queue
			w = new FixedPrioritiesPriorityQueue<PIterator<GrammarRule>>();
			next = new FixedPrioritiesPriorityQueue<GrammarRule>();
			maxheap = mh;
			fs = s;
			Symbol a = s.getLeft();
			Symbol b = s.getRight();
			FilteringPairSymbol.FILTER f = s.getFilter();
			if (debug) Debug.debug(debug, "Looking for matches for "+s+": between "+a+" and "+b+" with filter "+f);
			aiter = gram.getBSIter(a);
			btrans = trans;
			if (!aiter.hasNext()) {
				deadcount++;
				return;
			}
			while (aiter.hasNext() && q.isEmpty()) {
				if (debug) Debug.debug(debug, "trying to seed with "+aiter.peek()+" and "+b);
				BSBaseLazyIterator l = new BSBaseLazyIterator(aiter.next(), fs, btrans, maxheap);
				if (!l.hasNext())
					continue;
				double cost = -l.peek().getWeight();
				if (!q.add(l, cost))
					throw new UnusualConditionException("Couldn't add new iterator for "+s+" with cost "+cost);
			}
			// throw in the input-epsilon iterator if possible
			InputEpsilonLazyIterator ieit = new InputEpsilonLazyIterator(s, trans, mh);
			if (ieit.hasNext())
				q.add(ieit, -ieit.peek().getWeight());
			// do first step
			fillNext();
			if (debug) Debug.debug(debug, "Initial list of rules for "+a+" and "+b+" with filter "+f+" is "+next);
			if (hasNext())
				livecount++;
			else
				deadcount++;
		}
		// the meat of the class
		private void fillNext() throws UnusualConditionException {
			boolean debug = false;
			if (q.isEmpty()) {
				if (debug) Debug.debug(debug, "Main queue is empty, so no move made");
				return;
			}
			
			while (
					(maxheap == 0 ? true : next.size() < maxheap) && 
					!q.isEmpty()
					) {
				PIterator<GrammarRule> current = q.removeFirst();
				GrammarRule g  = current.next();
				next.add(g, -g.getWeight());
				if (debug) Debug.debug(debug,  "Next rule is "+g);
				if (current.hasNext()) {
					double nextcost = -current.peek().getWeight();
					if (debug )Debug.debug(debug, "Readding current to main queue with cost "+nextcost);
					if (!q.add(current, nextcost))
						throw new UnusualConditionException("Couldn't add next step iterator with cost "+nextcost);
				}
				else {
					if (debug )Debug.debug(debug, "Current queue is empty so not re-adding");
				}
				// add to wait queue until wait queue is not empty or we run out of lists
				while (w.isEmpty() && aiter.hasNext()) {
					BSBaseLazyIterator l2 = new BSBaseLazyIterator(aiter.next(), fs, btrans, maxheap);
					if (!l2.hasNext())
						continue;
					double waitcost = -l2.peek().getWeight();
					if (!w.add(l2, waitcost))
						throw new UnusualConditionException("Couldn't add new waiting iterator with cost "+waitcost);
				}
				// migrate lead from wait queue over to main queue
				if (!w.isEmpty()) {
					double waitcost = w.getPriority();
					if (q.isEmpty() || waitcost > q.getPriority()) {
						if (debug) Debug.debug(debug, "Moving list from wait to queue");
						if (!q.add(w.removeFirst(), waitcost))
							throw new UnusualConditionException("Couldn't migrate waiting iterator to main queue");
					}
				}
			}
			if (REPORTMONO && lastcost < next.getPriority())
				Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
				//throw new UnusualConditionException("Monotonicity of lazy iterator violated: got "+next+" after "+lastcost);
			lastcost = next.getPriority();
		}
		public boolean hasNext() {
			return !next.isEmpty();
		}
		public GrammarRule peek() throws NoSuchElementException {
			if (next.isEmpty())
				throw new NoSuchElementException("Asked for peek on empty PIterator");
			return next.getFirst();
		}
		public GrammarRule next() throws NoSuchElementException {
			boolean debug = false;
			if (next.isEmpty())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			GrammarRule ret = next.removeFirst();
			if (debug) Debug.debug(debug, "Going to return "+ret);
			try {
				fillNext();
			}
			catch (UnusualConditionException e) {
				throw new NoSuchElementException("Masking UnusualConditionException: "+e.getMessage());
			}

			return ret;
		}
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
		}

	}

	
	// base lazy iterator -- one grammar rule matching with transducer
	// must match epsilon grammar rule if relevant first
	// then must match actual matches
	// input-epsilon transduer rules are handled by InputEpsilonLazyIterator
	// next pops off next member
	private class BSBaseLazyIterator implements PIterator<GrammarRule> {
		private FixedPrioritiesPriorityQueue<GrammarRule> next;
		private GrammarRule base;
		private FilteringPairSymbol.FILTER filter;
		private Iterator<TreeTransducerRule> iterator;
		private double lastcost = 0;
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;
		public BSBaseLazyIterator(GrammarRule r, FilteringPairSymbol fs, 
				 OTFTreeTransducerRuleSet trans, int maxheap) throws UnusualConditionException {
			Symbol b = fs.getRight();
			FilteringPairSymbol.FILTER f = fs.getFilter();
			boolean debug = false;
			if (debug) Debug.debug(debug, "Looking for matches between "+r+" and "+b);
			base = r;
			filter = f;
			next = new FixedPrioritiesPriorityQueue<GrammarRule> ();
			// epsilon rules
			if (r.getLabel() == null) {
				GrammarRule epsrule = lazyBsFormAndAddRules(base, fs);
				if (epsrule != null)
					next.add(epsrule, -epsrule.getWeight());
				// eps-eps rules only allowed from NOEPS
				if (f.equals(FilteringPairSymbol.FILTER.NOEPS))
					iterator = trans.getBackwardRules(b, Symbol.getEpsilon(), 1).iterator();
				else
					iterator = transempty.iterator();
			}
			else {
				iterator = trans.getBackwardRules(b, r.getLabel(), r.getChildren().size()).iterator();
			}
			while (
					(maxheap == 0 ? true : next.size() < maxheap) && 
					iterator.hasNext()
					) {
				TreeTransducerRule t = iterator.next();		
				if (debug) Debug.debug(debug, "Matching "+base+" to "+t);
				GrammarRule g = lazyBsFormAndAddRules(base, t, filter);
				next.add(g, -g.getWeight());
				if (debug) Debug.debug(debug,  "Built rule for "+base+"; "+g);
			}
			if (!next.isEmpty()) {
				if (REPORTMONO && lastcost < next.getPriority())
					Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
				//					throw new UnusualConditionException("Monotonicity of lazy iterator violated: got "+next+" after "+lastcost);
				lastcost = next.getPriority();
			}
		}
		public boolean hasNext() {
			return !next.isEmpty();
		}
		public GrammarRule peek() throws NoSuchElementException {
			if (next.isEmpty())
				throw new NoSuchElementException("Asked for peek on empty PIterator");
			return next.getFirst();
		}
		public GrammarRule next() throws NoSuchElementException {
			boolean debug = false;
			if (next.isEmpty())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			GrammarRule ret = next.removeFirst();
			if (debug) Debug.debug(debug, "Next returns "+ret);
			if (iterator.hasNext()) {
				TreeTransducerRule t = iterator.next();	
				if (debug) Debug.debug(debug, "Matching "+base+" to "+t);
				try {
					GrammarRule g = lazyBsFormAndAddRules(base, t, filter);
					if (debug) Debug.debug(debug, "Built next rule for "+base+"; "+g);
					next.add(g, -g.getWeight());
				}
				catch (UnusualConditionException e) {
					throw new NoSuchElementException("Masking UnusualConditionException: "+e.getMessage());
				}
				if (REPORTMONO && lastcost < next.getPriority())
					Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
				lastcost = next.getPriority();
			}
			
			return ret;
		}
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
		}
	}
	
	
	
	// match a symbol with input-epsilon transducer rules
	private class InputEpsilonLazyIterator implements PIterator<GrammarRule> {
		private Iterator<TreeTransducerRule> iterator;
		private FilteringPairSymbol base;
		private FixedPrioritiesPriorityQueue<GrammarRule> next;
		private double lastcost = 0;
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;
		public InputEpsilonLazyIterator(FilteringPairSymbol s, 
				OTFTreeTransducerRuleSet trans, int maxheap) throws UnusualConditionException {
			boolean debug = false;
			base = s;
			FilteringPairSymbol.FILTER filter = s.getFilter();
			// not allowed to get these rules if we're lefteps
			if (filter.equals(FilteringPairSymbol.FILTER.LEFTEPS))
				return;
			iterator = trans.getBackwardRules(s.getRight(), Symbol.getEpsilon(), 1).iterator();
			next = new FixedPrioritiesPriorityQueue<GrammarRule> ();
			while (
					(maxheap == 0 ? true : next.size() < maxheap) && 
					iterator.hasNext()
					) {
				TreeTransducerRule t = iterator.next();					
				GrammarRule g = lazyBsFormAndAddRules(base, t);
				next.add(g, -g.getWeight());
				if (debug) Debug.debug(debug,  "Built rule for "+t+"; "+g);
			}
			if (!next.isEmpty()) {
				if (REPORTMONO && lastcost < next.getPriority())
					Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
				//					throw new UnusualConditionException("Monotonicity of lazy iterator violated: got "+next+" after "+lastcost);
				lastcost = next.getPriority();
			}
		}
		public boolean hasNext() {
			// if filtering makes this illegal, could have null objects
			if (next == null)
				return false;
			return  !next.isEmpty();
		}
		public GrammarRule peek() throws NoSuchElementException {
			if (next == null || next.isEmpty())
				throw new NoSuchElementException("Asked for peek on empty PIterator");
			return next.getFirst();
		}
		
		public GrammarRule next() throws NoSuchElementException {
			boolean debug = false;
			if (next == null || next.isEmpty())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			GrammarRule ret = next.removeFirst();
			if (iterator.hasNext()) {
				TreeTransducerRule t = iterator.next();	
				try {
					GrammarRule g = lazyBsFormAndAddRules(base, t);
					if (debug) Debug.debug(debug, "Built next rule for "+t+"; "+g);
					next.add(g, -g.getWeight());
				}
				catch (UnusualConditionException e) {
					throw new NoSuchElementException("Masking UnusualConditionException: "+e.getMessage());
				}
				if (REPORTMONO && lastcost < next.getPriority())
					Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
				lastcost = next.getPriority();
			}			
			return ret;
		}
		
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
		}
	}
	
	
	
	// implementation of pervasive laziness for forwardstar
	private class FSLazyIterator implements PIterator<GrammarRule> {
		// gram, which makes iters
		private Grammar agram;
		// trans iter
		private PIterator<TreeTransducerRule> biter;
		// filtering pair symbol, which contains trans state b and filter state f
		private FilteringPairSymbol fs;
		// rhs pos
		private int pos;
		// rhs label and rank, which could be null
		private int rrank;
		private Symbol rlabel;
		
		// main queue
		private FixedPrioritiesPriorityQueue<PIterator<GrammarRule>> q;
		// wait queue
		private FixedPrioritiesPriorityQueue<PIterator<GrammarRule>> w;
		// next items
		private FixedPrioritiesPriorityQueue<GrammarRule> next;
		// for checking monotonicity
		private double lastcost;
		// how big a wait heap?
		private int maxheap;
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;

		// lexical, unfiltered
		// strictly gets 0-rank lexical rhs rules
		public FSLazyIterator(Grammar gram, OTFTreeTransducerRuleSet trans, int mh) throws UnusualConditionException {
			boolean debug = false;
			// label not used in unfiltered
			// rank 0 to signify lexical term
			rrank = 0;
			rlabel = null;
			// initialize elements
			q = new FixedPrioritiesPriorityQueue<PIterator<GrammarRule>>();
			// wait queue
			w = new FixedPrioritiesPriorityQueue<PIterator<GrammarRule>>();
			next = new FixedPrioritiesPriorityQueue<GrammarRule>();
			maxheap = mh;
			// fs and pos not used in lex
			fs = null;
			pos = -1;
			agram = gram;
			biter = new WrappedPIterator<TreeTransducerRule> (trans.getRelPosLexRules().iterator());
			if (!biter.hasNext()) {
				deadcount++;
				return;
			}
			while (biter.hasNext() && q.isEmpty()) {
				if (debug) Debug.debug(debug, "trying to seed with "+biter.peek());
				TreeTransducerRule brule = biter.next();
				// find the lexical nodes. this is extra work that could have been done before.
				for (TransducerRightTree node : brule.getRHS().getNonVariableChildren()) {
					if (debug) Debug.debug(debug, "Seeding "+brule+" based on "+node);

					FSLexBaseLazyIterator l = new FSLexBaseLazyIterator(agram, brule, node, maxheap);
					if (!l.hasNext())
						continue;
					double cost = -l.peek().getWeight();
					if (!q.add(l, cost))
						throw new UnusualConditionException("Couldn't add new lex iterator with cost "+cost);
				}
			}
	
			// do first step
			fillNext();
			if (hasNext())
				livecount++;
			else
				deadcount++;
		}
		
		// lexical, filtered
		// includes nonlexical rules used in the service of getting lexical rules
		public FSLazyIterator(Grammar gram, OTFTreeTransducerRuleSet trans, Symbol label, int rank, int mh) throws UnusualConditionException {
			boolean debug = false;
			rrank = rank;
			rlabel = label;
			// initialize elements
			q = new FixedPrioritiesPriorityQueue<PIterator<GrammarRule>>();
			// wait queue
			w = new FixedPrioritiesPriorityQueue<PIterator<GrammarRule>>();
			next = new FixedPrioritiesPriorityQueue<GrammarRule>();
			maxheap = mh;
			// fs and pos not used in lex
			fs = null;
			pos = -1;
			agram = gram;
			biter = new WrappedPIterator<TreeTransducerRule> (trans.getRelPosLexRules(rlabel, rrank).iterator());
			if (!biter.hasNext()) {
				deadcount++;
				return;
			}
			while (biter.hasNext() && q.isEmpty()) {
				if (debug) Debug.debug(debug, "trying to seed with "+biter.peek());
				TreeTransducerRule brule = biter.next();
				// find the appropriate nodes. this is extra work that could have been done before.
				// true lexical (rank 0)
				if (rrank == 0) {
					for (TransducerRightTree node : brule.getRHS().getNonVariableChildren()) {
						if (debug) Debug.debug(debug, "Seeding "+brule+" based on "+node);
						FSLexBaseLazyIterator l = new FSLexBaseLazyIterator(agram, brule, node, maxheap);
						if (!l.hasNext())
							continue;
						double cost = -l.peek().getWeight();
						if (!q.add(l, cost))
							throw new UnusualConditionException("Couldn't add new lex iterator with cost "+cost);
					}
				}
				else {
					// fake lexical (rank > 0) -- scrape for requested label/rank
					
					for (TransducerRightTree node : findMatches(brule.getRHS(), label, rrank)) {
						if (debug) Debug.debug(debug, "Seeding "+brule+" based on "+node);
						FSLexBaseLazyIterator l = new FSLexBaseLazyIterator(agram, brule, node, maxheap);
						if (!l.hasNext())
							continue;
						double cost = -l.peek().getWeight();
						if (!q.add(l, cost))
							throw new UnusualConditionException("Couldn't add new lex iterator with cost "+cost);
					}
				}
			}


			// do first step
			fillNext();
			if (hasNext())
				livecount++;
			else
				deadcount++;
		}

		// non-lexical, unfiltered
		// specific position is requested
		public FSLazyIterator(Grammar gram, OTFTreeTransducerRuleSet trans, FilteringPairSymbol s, int p, int mh) throws UnusualConditionException {
			boolean debug = false;
			// label and rank not used in unfiltered
			rrank = -1;
			rlabel = null;
			// initialize elements
			q = new FixedPrioritiesPriorityQueue<PIterator<GrammarRule>>();
			// wait queue
			w = new FixedPrioritiesPriorityQueue<PIterator<GrammarRule>>();
			next = new FixedPrioritiesPriorityQueue<GrammarRule>();
			maxheap = mh;
			fs = s;
			pos = p;
			Symbol a = s.getLeft();
			Symbol b = s.getRight();
			FilteringPairSymbol.FILTER f = s.getFilter();
			if (debug) Debug.debug(debug, "Looking for matches for "+s+": between "+a+" and "+b+" with filter "+f);
			agram = gram;
			biter = new WrappedPIterator<TreeTransducerRule> (trans.getRelPosForwardRules(b, pos).iterator());
			if (!biter.hasNext()) {
				deadcount++;
				return;
			}
			while (biter.hasNext() && q.isEmpty()) {
				if (debug) Debug.debug(debug, "trying to seed with "+biter.peek()+" and "+a);
				TreeTransducerRule brule = biter.next();
				// find the right node or nodes. this is extra work that could have been done before.
				// we also get the pos, which is nice
				for (int i = 0; i < brule.getLHS().getNumChildren(); i++) {
					HashSet<TransducerRightSide> trsset = brule.getTRVM().getRHSByVariable(brule.getTRVM().getVariableByIndex(i));
					if (trsset == null || trsset.size() == 0)
						continue;
				// if this variable matches the specified pos and state on the rhs, make an iterator
					for (TransducerRightSide gentrt : trsset) {
						TransducerRightTree trt = (TransducerRightTree)gentrt;
						if (trt.getState().equals(b) && 
								trt.parent != null &&
								trt.parent.getNumChildren() > pos &&
								trt.parent.getChild(pos).equals(trt)) {
							FSBaseLazyIterator l = new FSBaseLazyIterator(agram, fs, brule, trt, i, maxheap);
							if (!l.hasNext())
								continue;
							double cost = -l.peek().getWeight();
							if (!q.add(l, cost))
								throw new UnusualConditionException("Couldn't add new iterator for "+s+" with cost "+cost);

						}
					}
				}
			}
			// if pos is 0 we can form eps rules using only eps rules from the grammar
			if (p == 0) {
				EpsilonGrammarLazyIterator egli = new EpsilonGrammarLazyIterator(agram, fs, mh);
				if (egli.hasNext())
					q.add(egli, -egli.peek().getWeight());
			}

			fillNext();
			if (debug) Debug.debug(debug, "Initial list of rules for "+a+" and "+b+" with filter "+f+" is "+next);
			if (hasNext())
				livecount++;
			else
				deadcount++;
		}
		
		// non-lexical, filtered 
		public FSLazyIterator(Grammar gram, OTFTreeTransducerRuleSet trans, FilteringPairSymbol s, int p, Symbol label, int rank, int mh) throws UnusualConditionException {
			boolean debug = false;
			// initialize elements
			q = new FixedPrioritiesPriorityQueue<PIterator<GrammarRule>>();
			// wait queue
			w = new FixedPrioritiesPriorityQueue<PIterator<GrammarRule>>();
			next = new FixedPrioritiesPriorityQueue<GrammarRule>();
			maxheap = mh;
			fs = s;
			pos = p;
			rrank = rank;
			rlabel = label;
			Symbol a = s.getLeft();
			Symbol b = s.getRight();
			FilteringPairSymbol.FILTER f = s.getFilter();
			if (debug) Debug.debug(debug, "Looking for matches for "+s+": between "+a+" and "+b+" with filter "+f);
			agram = gram;
			biter = new WrappedPIterator<TreeTransducerRule> (trans.getRelPosForwardRules(b, pos, rlabel, rrank).iterator());
			if (!biter.hasNext()) {
				deadcount++;
				return;
			}
			while (biter.hasNext() && q.isEmpty()) {
				if (debug) Debug.debug(debug, "trying to seed with "+biter.peek()+" and "+a);
				TreeTransducerRule brule = biter.next();
				// find the right node or nodes. this is extra work that could have been done before.
				// we also get the pos, which is nice
				for (int i = 0; i < brule.getLHS().getNumChildren(); i++) {
					HashSet<TransducerRightSide> trsset = brule.getTRVM().getRHSByVariable(brule.getTRVM().getVariableByIndex(i));
					if (trsset == null || trsset.size() == 0)
						continue;
					// if this variable matches the specified pos and state on the rhs, make an iterator
					for (TransducerRightSide gentrt : trsset) {
						TransducerRightTree trt = (TransducerRightTree)gentrt;
						if (trt.getState().equals(b) && 
								trt.parent != null &&
								trt.parent.getLabel().equals(rlabel) &&
								trt.parent.getNumChildren() == rrank &&
								trt.parent.getChild(pos).equals(trt)) {
							FSBaseLazyIterator l = new FSBaseLazyIterator(agram, fs, brule, trt, i, maxheap);
							if (!l.hasNext())
								continue;
							double cost = -l.peek().getWeight();
							if (!q.add(l, cost))
								throw new UnusualConditionException("Couldn't add new iterator for "+s+" with cost "+cost);

						}
					}
				}
			}
			// if pos is 0 and symbol/rank is appropriate we can form eps rules using only eps rules from the grammar
			if (p == 0 && label.equals(Symbol.getEpsilon()) && rank == 1) {
				EpsilonGrammarLazyIterator egli = new EpsilonGrammarLazyIterator(agram, fs, mh);
				if (egli.hasNext())
					q.add(egli, -egli.peek().getWeight());
			}
			// TODO: appropriate epsilon!
			// throw in the input-epsilon iterator if possible
			//			InputEpsilonLazyIterator ieit = new InputEpsilonLazyIterator(s, trans, mh);
			//			if (ieit.hasNext())
			//				q.add(ieit, -ieit.peek().getWeight());
			// do first step
			fillNext();
			if (debug) Debug.debug(debug, "Initial list of rules for "+a+" and "+b+" with filter "+f+" is "+next);
			if (hasNext())
				livecount++;
			else
				deadcount++;
		}
		// utility for scraping transducer right trees for nodes matching a particular label and rank
		private Vector<TransducerRightTree> findMatches(TransducerRightTree input, Symbol label, int rank) {
			Vector<TransducerRightTree> procVec = new Vector<TransducerRightTree>();
			Vector<TransducerRightTree> foundVec = new Vector<TransducerRightTree>();
			procVec.add(input);
			while (!procVec.isEmpty()) {
				TransducerRightTree trt = procVec.remove(0);
				if (trt.hasLabel() && trt.getLabel().equals(label) && trt.getNumChildren() == rank)
					foundVec.add(trt);
				for (int i = 0; i < trt.getNumChildren(); i++)
					procVec.add(trt.getChild(i));
			}
			return foundVec;
		}
		
		// the meat of the class
		private void fillNext() throws UnusualConditionException {
			boolean debug = false;
			if (q.isEmpty()) {
				if (debug) Debug.debug(debug, "Main queue is empty, so no move made");
				return;
			}

			while (
					(maxheap == 0 ? true : next.size() < maxheap) && 
					!q.isEmpty()
			) {
				PIterator<GrammarRule> current = q.removeFirst();
				GrammarRule g  = current.next();
				next.add(g, -g.getWeight());
				if (debug) Debug.debug(debug,  "Next rule is "+g);
				if (current.hasNext()) {
					double nextcost = -current.peek().getWeight();
					if (!q.add(current, nextcost))
						throw new UnusualConditionException("Couldn't add next step iterator with cost "+nextcost);
				}
				// add to wait queue until wait queue is not empty or we run out of lists
				while (w.isEmpty() && biter.hasNext()) {
					TreeTransducerRule brule = biter.next();
					if (debug) Debug.debug(debug, "Trying to seed with "+brule+" with pos of "+pos+" and rank of "+rrank);
					// find the right node or nodes. this is extra work that could have been done before.
					if (pos < 0) {
						if (debug) Debug.debug(debug, "This is lexical");
						if (rrank == 0) {
							if (debug) Debug.debug(debug, "This is terminal");
							for (TransducerRightTree node : brule.getRHS().getNonVariableChildren()) {
								FSLexBaseLazyIterator l2 = new FSLexBaseLazyIterator(agram, brule, node, maxheap);
								if (!l2.hasNext())
									continue;
								double cost = -l2.peek().getWeight();
								if (!q.add(l2, cost))
									throw new UnusualConditionException("Couldn't add new lex iterator with cost "+cost);
							}
						}

						else {
							if (debug) Debug.debug(debug, "This is non-terminal: label of "+rlabel+" and rank of "+rrank+" desired");
							// fake lexical (rank > 0) -- scrape for requested label/rank

							for (TransducerRightTree node : findMatches(brule.getRHS(), rlabel, rrank)) {
								FSLexBaseLazyIterator l2 = new FSLexBaseLazyIterator(agram, brule, node, maxheap);
								if (!l2.hasNext())
									continue;
								double cost = -l2.peek().getWeight();
								if (!q.add(l2, cost))
									throw new UnusualConditionException("Couldn't add new lex iterator with cost "+cost);
							}
						}

					}
					// non-lexical case
					else {
						if (debug) Debug.debug(debug, "This is non-lexical");
						// we also get the pos, which is nice
						for (int i = 0; i < brule.getLHS().getNumChildren(); i++) {
							HashSet<TransducerRightSide> trsset = brule.getTRVM().getRHSByVariable(brule.getTRVM().getVariableByIndex(i));
							// kludge: should be zero or one members
							if (trsset == null || trsset.size() == 0)
								continue;
							if (trsset.size() > 1)
								throw new UnusualConditionException(trsset.size()+" mappings for variable "+i+" in "+brule);
							// if this variable matches the specified pos and state on the rhs, make an iterator
							for (TransducerRightSide gentrt : trsset) {
								TransducerRightTree trt = (TransducerRightTree)gentrt;
								if (trt.getState().equals(fs.getRight()) && 
										trt.parent != null &&
										trt.parent.getNumChildren() > pos &&
										trt.parent.getChild(pos).equals(trt)) {
									// additional check if filtered
									if (rrank >= 0) {
										if (trt.parent.getNumChildren() != rrank || !trt.parent.getLabel().equals(rlabel))
											continue;
									}
									FSBaseLazyIterator l2 = new FSBaseLazyIterator(agram, fs, brule, trt, i, maxheap);
									if (!l2.hasNext())
										continue;
									double waitcost = -l2.peek().getWeight();
									if (!w.add(l2, waitcost))
										throw new UnusualConditionException("Couldn't addnew waiting iterator with cost "+waitcost);

								}
							}
						}
					}
				}
				// migrate lead from wait queue over to main queue
				if (!w.isEmpty()) {
					double waitcost = w.getPriority();
					if (q.isEmpty() || waitcost > q.getPriority()) {
						if (debug) Debug.debug(debug, "Moving list from wait to queue");
						if (!q.add(w.removeFirst(), waitcost))
							throw new UnusualConditionException("Couldn't migrate waiting iterator to main queue");
					}
				}
			}
			if (REPORTMONO && lastcost < next.getPriority())
				Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
			//throw new UnusualConditionException("Monotonicity of lazy iterator violated: got "+next+" after "+lastcost);
			lastcost = next.getPriority();
			if (debug) Debug.debug(debug, "Queue is "+next);
		}
		public boolean hasNext() {
			return !next.isEmpty();
		}
		public GrammarRule peek() throws NoSuchElementException {
			if (next.isEmpty())
				throw new NoSuchElementException("Asked for peek on empty PIterator");
			return next.getFirst();
		}
		public GrammarRule next() throws NoSuchElementException {
			if (next.isEmpty())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			GrammarRule ret = next.removeFirst();
			try {
				fillNext();
			}
			catch (UnusualConditionException e) {
				throw new NoSuchElementException("Masking UnusualConditionException: "+e.getMessage());
			}

			return ret;
		}
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
		}

	}

	
	// base lazy iterator for forward star -- one transducer rule matching with grammar rule at a particular
	// node
	// track the appropriate FS call to make to grammar

	// must match epsilon grammar rule if relevant first
	// then must match actual matches
	// input-epsilon transduer rules are handled by InputEpsilonLazyIterator
	// next pops off next member
	private class FSBaseLazyIterator implements PIterator<GrammarRule> {
		private FixedPrioritiesPriorityQueue<GrammarRule> next;
		private TreeTransducerRule base;
		private TransducerRightTree node;
		private FilteringPairSymbol.FILTER filter;
		private Iterator<GrammarRule> iterator;
		private double lastcost = 0;
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;
		public FSBaseLazyIterator(Grammar g, FilteringPairSymbol fs, 
				TreeTransducerRule rule, TransducerRightTree n, int pos, int maxheap) throws UnusualConditionException {
			
			boolean debug = false;
			Symbol a = fs.getLeft();
			Symbol b = fs.getRight();
			FilteringPairSymbol.FILTER f = fs.getFilter();
			node = n;
			base = rule;
			filter = f;
			
			if (debug) Debug.debug(debug, "Looking for matches between "+rule+" at "+b+
					" and "+node+" and grammar at "+a+" with match at "+pos);
			
			next = new FixedPrioritiesPriorityQueue<GrammarRule> ();
			// TODO: Epsilons!
			//			// epsilon rules
			//			if (r.getLabel() == null) {
			//				GrammarRule epsrule = lazyFsFormAndAddRules(base, fs);
			//				if (epsrule != null)
			//					next.add(epsrule, -epsrule.getWeight());
			//				// eps-eps rules only allowed from NOEPS
			//				if (f.equals(FilteringPairSymbol.FILTER.NOEPS))
			//					iterator = trans.getBackwardRules(b, Symbol.getEpsilon(), 1).iterator();
			//				else
			//					iterator = transempty.iterator();
			//			}
			//			else {
			//				iterator = trans.getBackwardRules(b, r.getLabel(), r.getChildren().size()).iterator();
			//			}
			iterator = g.getFSIter(a, pos, base.getLHS().getLabel(), base.getLHS().getNumChildren());
			while (
					(maxheap == 0 ? true : next.size() < maxheap) && 
					iterator.hasNext()
			) {
				GrammarRule r = iterator.next();		
				if (debug) Debug.debug(debug, "Matching "+r+" to "+base);
				GrammarRule newg = lazyFsFormAndAddRules(r, base, filter, node);
				next.add(newg, -newg.getWeight());
				if (debug) Debug.debug(debug,  "Built rule for "+base+"; "+newg);
			}
			if (!next.isEmpty()) {
				if (REPORTMONO && lastcost < next.getPriority())
					Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
				//					throw new UnusualConditionException("Monotonicity of lazy iterator violated: got "+next+" after "+lastcost);
				lastcost = next.getPriority();
			}
		}
		public boolean hasNext() {
			return !next.isEmpty();
		}
		public GrammarRule peek() throws NoSuchElementException {
			if (next.isEmpty())
				throw new NoSuchElementException("Asked for peek on empty PIterator");
			return next.getFirst();
		}
		public GrammarRule next() throws NoSuchElementException {
			boolean debug = false;
			if (next.isEmpty())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			GrammarRule ret = next.removeFirst();
			if (debug) Debug.debug(debug, "Next returns "+ret);
			if (iterator.hasNext()) {
				GrammarRule r = iterator.next();	
				if (debug) Debug.debug(debug, "Matching "+base+" to "+r);
				try {
					GrammarRule g = lazyFsFormAndAddRules(r, base, filter, node);
					if (debug) Debug.debug(debug, "Built next rule for "+base+"; "+g);
					next.add(g, -g.getWeight());
				}
				catch (UnusualConditionException e) {
					throw new NoSuchElementException("Masking UnusualConditionException: "+e.getMessage());
				}
				if (REPORTMONO && lastcost < next.getPriority())
					Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
				lastcost = next.getPriority();
			}

			return ret;
		}
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
		}
	}


	// base lazy iterator for lexical rules from forward star -- one transducer rule matching with grammar rule at a particular
	// node
	// track the appropriate FS call to make to grammar

	// no epsilon rules here
	private class FSLexBaseLazyIterator implements PIterator<GrammarRule> {
		private FixedPrioritiesPriorityQueue<GrammarRule> next;
		private TreeTransducerRule base;
		private TransducerRightTree node;
		
		private Iterator<GrammarRule> iterator;
		private double lastcost = 0;
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;
		public FSLexBaseLazyIterator(Grammar g,  
				TreeTransducerRule rule, TransducerRightTree n, int maxheap) throws UnusualConditionException {
			
			boolean debug = false;
		
			node = n;
			base = rule;
			
			if (debug) Debug.debug(debug, "Looking for matches between lexical rule "+rule+
					" at "+node+" and grammar ");
			
			next = new FixedPrioritiesPriorityQueue<GrammarRule> ();
		
			iterator = g.getLexFSIter(base.getLHS().getLabel(), base.getLHS().getNumChildren());
			while (
					(maxheap == 0 ? true : next.size() < maxheap) && 
					iterator.hasNext()
			) {
				GrammarRule r = iterator.next();		
				if (debug) Debug.debug(debug, "Matching "+r+" to "+base);
				GrammarRule newg = lazyFsFormAndAddRules(r, base, FilteringPairSymbol.FILTER.NOEPS, node);
				if (debug) Debug.debug(debug,  "Built rule for "+base+" at "+node+"; "+newg);
				next.add(newg, -newg.getWeight());
				
			}
			if (!next.isEmpty()) {
				if (REPORTMONO && lastcost < next.getPriority())
					Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
				//					throw new UnusualConditionException("Monotonicity of lazy iterator violated: got "+next+" after "+lastcost);
				lastcost = next.getPriority();
			}
		}
		public boolean hasNext() {
			return !next.isEmpty();
		}
		public GrammarRule peek() throws NoSuchElementException {
			if (next.isEmpty())
				throw new NoSuchElementException("Asked for peek on empty PIterator");
			return next.getFirst();
		}
		public GrammarRule next() throws NoSuchElementException {
			boolean debug = false;
			if (next.isEmpty())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			GrammarRule ret = next.removeFirst();
			if (debug) Debug.debug(debug, "Next returns "+ret);
			if (iterator.hasNext()) {
				GrammarRule r = iterator.next();	
				if (debug) Debug.debug(debug, "Matching "+base+" to "+r);
				try {
					GrammarRule g = lazyFsFormAndAddRules(r, base, FilteringPairSymbol.FILTER.NOEPS, node);
					if (debug) Debug.debug(debug, "Built next rule for "+base+" at "+node+"; "+g);
					next.add(g, -g.getWeight());
				}
				catch (UnusualConditionException e) {
					throw new NoSuchElementException("Masking UnusualConditionException: "+e.getMessage());
				}
				if (REPORTMONO && lastcost < next.getPriority())
					Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
				lastcost = next.getPriority();
			}

			return ret;
		}
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
		}
	}
	// match a symbol with epsilon grammar rules
	// NOTE: this is a FORWARD STAR oriented iterator
	// so grammar is searched from FS perspective and filter is passed in that way too
	private class EpsilonGrammarLazyIterator implements PIterator<GrammarRule> {
		private Iterator<GrammarRule> iterator;
		private FilteringPairSymbol base;
		private FixedPrioritiesPriorityQueue<GrammarRule> next;
		private double lastcost = 0;
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;
		public EpsilonGrammarLazyIterator(Grammar gram, 
				FilteringPairSymbol s, int maxheap) throws UnusualConditionException {
			boolean debug = false;
			base = s;
			FilteringPairSymbol.FILTER filter = s.getFilter();
			// not allowed to get these rules if we're rightteps
			if (filter.equals(FilteringPairSymbol.FILTER.RIGHTEPS))
				return;
			iterator = gram.getFSIter(s.getLeft(), 0, Symbol.getEpsilon(), 1);
			next = new FixedPrioritiesPriorityQueue<GrammarRule> ();
			while (
					(maxheap == 0 ? true : next.size() < maxheap) && 
					iterator.hasNext()
					) {
				GrammarRule t = iterator.next();					
				GrammarRule g = lazyFsFormAndAddRules(t, base);
				next.add(g, -g.getWeight());
				if (debug) Debug.debug(debug,  "Built rule for "+t+"; "+g);
			}
			if (!next.isEmpty()) {
				if (REPORTMONO && lastcost < next.getPriority())
					Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
				//					throw new UnusualConditionException("Monotonicity of lazy iterator violated: got "+next+" after "+lastcost);
				lastcost = next.getPriority();
			}
		}
		public boolean hasNext() {
			// if filtering makes this illegal, could have null objects
			if (next == null)
				return false;
			return  !next.isEmpty();
		}
		public GrammarRule peek() throws NoSuchElementException {
			if (next == null || next.isEmpty())
				throw new NoSuchElementException("Asked for peek on empty PIterator");
			return next.getFirst();
		}
		
		public GrammarRule next() throws NoSuchElementException {
			boolean debug = false;
			if (next == null || next.isEmpty())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			GrammarRule ret = next.removeFirst();
			if (iterator.hasNext()) {
				GrammarRule t = iterator.next();					
				try {
					GrammarRule g = lazyFsFormAndAddRules(t, base);
					if (debug) Debug.debug(debug, "Built next rule for "+t+"; "+g);
					next.add(g, -g.getWeight());
				}
				catch (UnusualConditionException e) {
					throw new NoSuchElementException("Masking UnusualConditionException: "+e.getMessage());
				}
				if (REPORTMONO && lastcost < next.getPriority())
					Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
				lastcost = next.getPriority();
			}			
			return ret;
		}
		
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
		}
	}

	// isTop -- is this the initial call or the recursive call? 
	// determines whether external rule memorization happens
	private Vector<GrammarRule> getProductions(
			Symbol state,
			int gramid,
			int transid,
			TransducerRightTree oldTree, 
			HashMap<TransducerRightSide, FilteringPairSymbol> varmap, 
			double weight, 
			GrammarRule srcGram,
			TreeTransducerRule srcTrans,
			boolean isTop) {
		// traverse down tree, building rules
		boolean debug = false;
		Vector<GrammarRule> ret = new Vector<GrammarRule>();
		Vector<Symbol> children = new Vector<Symbol>();
	
		if (debug) Debug.debug(debug, "Making productions from "+state+" and "+oldTree);
		// output eps case
		if (oldTree.hasVariable()) {
			if (debug) Debug.debug(debug, "Making production on output-eps rule");
			children.add(varmap.get(oldTree));
		}
		else {
			for (int i = 0; i < oldTree.getNumChildren(); i++) {
				// new state is created! must update bs and fs done components
				if (oldTree.getChild(i).hasLabel()) {
					ProdSymbol newState = ProdSymbol.get(state, gramid, transid, oldTree.getChild(i).hashCode());
//					Symbol newState = SymbolFactory.getStateSymbol();
					bsdone.add(newState);
					lfdone.add(newState);
					fdone.add(newState);
					if (!fposdone.containsKey(newState))
						fposdone.put(newState, new HashSet<Integer>());
					fposdone.get(newState).add(i);
					children.add(newState);
					
					ret.addAll(getProductions(newState, gramid, transid, oldTree.getChild(i), varmap, semiring.ONE(), null, null, false));
				}
				else {
					children.add(varmap.get(oldTree.getChild(i)));
				}
			}
		}
		
		
		if (debug && children.size() > 0) Debug.debug(debug, "Got child vector of "+children);
		
		
		Vector<TreeTransducerRule> leftArg = null;
		TreeTransducerRule rightArg = null;
		if (isTop) {
			if (srcGram != null && srcGram instanceof GTOTFGrammarRule)
				leftArg = ((GTOTFGrammarRule)srcGram).getRules();
			if (srcTrans != null)
				rightArg = srcTrans;
		}
		GTOTFGrammarRule ggr = new GTOTFGrammarRule(state, oldTree.getLabel(), children, weight, leftArg, rightArg);

		if (debug) Debug.debug(debug, "Made "+ggr);
		ret.add(ggr);
		return ret;
		
	}


	// lazy FS version returns a map from each matched trt node in the leaf to the created rule
	// so some rules are indexed more than once
	// TODO: integrate this with the above?
	private HashMap<TransducerRightTree, Vector<GrammarRule>> getFSProductions(
			Symbol state, 
			int gramid,
			int transid,
			TransducerRightTree oldTree, 
			HashMap<TransducerRightSide, FilteringPairSymbol> varmap, 
			double weight, 
			boolean isTop) {
		// traverse down tree, building rules
		boolean debug = false;
		HashMap<TransducerRightTree, Vector<GrammarRule>> ret = new HashMap<TransducerRightTree, Vector<GrammarRule>>();
		Vector<Symbol> children = new Vector<Symbol>();
	
		Vector<TransducerRightTree> keys = new Vector<TransducerRightTree>();
		// term/out eps
		if (oldTree.getNumChildren() == 0) {
			keys.add(oldTree);
			// output eps case
			if (oldTree.hasVariable()) {
				if (debug) Debug.debug(debug, "Making production on output-eps rule");
				children.add(varmap.get(oldTree));
			}
		}
		else {
			for (int i = 0; i < oldTree.getNumChildren(); i++) {
				keys.add(oldTree.getChild(i));
				// new state is created! must update bs and fs done components
				if (oldTree.getChild(i).hasLabel()) {
					ProdSymbol newState = ProdSymbol.get(state, gramid, transid, oldTree.getChild(i).hashCode());
//					Symbol newState = SymbolFactory.getStateSymbol();
					bsdone.add(newState);
					lfdone.add(newState);
					fdone.add(newState);
					if (!fposdone.containsKey(newState))
						fposdone.put(newState, new HashSet<Integer>());
					fposdone.get(newState).add(i);
					children.add(newState);
					
					ret.putAll(getFSProductions(newState, gramid, transid, oldTree.getChild(i), varmap, semiring.ONE(), isTop));
				}
				else {
					children.add(varmap.get(oldTree.getChild(i)));
				}
			}
		}
		

		if (debug && children.size() > 0) Debug.debug(debug, "Got child vector of "+children);
		GTOTFGrammarRule rule = new GTOTFGrammarRule(state, oldTree.getLabel(), children, weight, null, null);
		if (debug) Debug.debug(debug, "Formed "+rule);
		for (TransducerRightTree trt : keys) {
			if (!ret.containsKey(trt))
				ret.put(trt, new Vector<GrammarRule>());
			if (debug) Debug.debug(debug, "Mapped "+trt+" to "+rule);
			ret.get(trt).add(rule);

		}
		return ret;		
	}
	

	@Override
	public Symbol getStartState() {
		return startState;
	}
	
	@Override
	public boolean isStartState(Symbol s) {
		return s.equals(startState);
	}

	@Override
	public Iterable<GrammarRule> getTerminalRules() {
		Debug.prettyDebug("Terminal rules not implemented!");
		return null;
	}
	// grammar is made up of grammar and transducer
	private Grammar grammar;
	private OTFTreeTransducerRuleSet trans;
	
	
	// rules indexed by children and pos and label
	private HashMap<Symbol, HashMap<Integer, HashMap<Symbol, HashMap<Integer,Vector<GrammarRule>>>>> posfrules;
	// rules by dst state and position of that state (better way to hold these?)
	private HashMap<Symbol, HashMap<Integer, Vector<GrammarRule>>> allposfrules;
	
	// bs rules indexed by internally created head state
	private HashMap<Symbol, Vector<GrammarRule>> bsrules;
	
	// bs rules indexed by internally created head state
	private HashMap<Symbol, GrammarRule> auxBsRules;

	// fs rules indexed by internally created tail state and position, as well as parent label/rank
	private PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, GrammarRule>>>> auxFsRules;
	// fs rules indexed by internally created tail state and position only
	private PMap<Symbol, PMap<Integer, GrammarRule>> allAuxFsRules;
	
	
	// terminal rules
//	private HashSet<GrammarRule> trules;
	// the start state
	private FilteringPairSymbol startState;
	// sets of inquiries that have been made
	private HashSet<Symbol> bsdone;
	private HashSet<Symbol> lfdone;
	private HashSet<Symbol> fdone;
	private HashMap<Symbol, HashSet<Integer>> fposdone;
	// pairings in combination to avoid repeats (last map now no longer needed since we have filter-based ones below)
	private HashMap<GrammarRule, HashMap<TreeTransducerRule, GrammarRule>> rulePairs;
	// filter based version of rule pairs
	private HashMap<GrammarRule, HashMap<TreeTransducerRule, HashMap<FilteringPairSymbol.FILTER, GrammarRule>>> filteringRulePairs;

	// for forward star: index of rules by original pairing, filter, and specific anchor from tree transducer rule
	private HashMap<GrammarRule, HashMap<TreeTransducerRule, HashMap<FilteringPairSymbol.FILTER, HashMap<TransducerRightTree, GrammarRule>>>> forwardFilteringRulePairs;

	
	// for eps (State change) grammar rules
	private PMap<GrammarRule, PMap<Symbol, GrammarRule>> ruleStatePairs;
	// for in-eps  grammar rules
	private HashMap<Symbol, HashMap<TreeTransducerRule, GrammarRule>> stateRulePairs;
	
	private int maxheap;
	private Semiring semiring;
	
	// just count the rules that get built
	private int ruleCount;
	
	// turns filtering on, which is needed if input-epsilon rules are allowed
	private static boolean ALLOWINEPS = true;
	
	// count iterators with and without content (calculate dead state creation)
	private static int deadcount = 0;
	private static int livecount = 0;
	
	public GTOTFGrammar(Grammar g, OTFTreeTransducerRuleSet t, Semiring s, int mh) {
		super(s, 0);
		semiring = s;
		grammar = g;
		maxheap = mh < 0 ? 0 : mh;
		trans = t;
		ruleCount = 0;
		startState = FilteringPairSymbol.get(grammar.getStartState(), trans.getStartState(), FilteringPairSymbol.FILTER.NOEPS);

		bsrules = new HashMap<Symbol, Vector<GrammarRule>>();
		// bs rules indexed by internally created head state
		auxBsRules = new HashMap<Symbol, GrammarRule>();

		// fs rules indexed by internally created tail state and position, as well as parent label/rank
		auxFsRules = new PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, GrammarRule>>>> ();
		// fs rules indexed by internally created tail state and position only
		allAuxFsRules = new PMap<Symbol, PMap<Integer, GrammarRule>> ();
		
		
		posfrules = new HashMap<Symbol, HashMap<Integer, HashMap<Symbol, HashMap<Integer,Vector<GrammarRule>>>>>(); 
		allposfrules = new HashMap<Symbol, HashMap<Integer, Vector<GrammarRule>>>();
		rulePairs = new HashMap<GrammarRule, HashMap<TreeTransducerRule, GrammarRule>>(); 
		filteringRulePairs = new HashMap<GrammarRule, HashMap<TreeTransducerRule, HashMap<FilteringPairSymbol.FILTER, GrammarRule>>> ();
		forwardFilteringRulePairs = new HashMap<GrammarRule, HashMap<TreeTransducerRule, HashMap<FilteringPairSymbol.FILTER, HashMap<TransducerRightTree, GrammarRule>>>> ();

		ruleStatePairs = new PMap<GrammarRule, PMap<Symbol, GrammarRule>>(); 
		stateRulePairs = new HashMap<Symbol, HashMap<TreeTransducerRule, GrammarRule>> ();
		bsResultTable = new HashMap<Symbol, Vector<GrammarRule>>(); 
		bsIterTable = new HashMap<Symbol, BSLazyIterator> ();
		bsdone = new HashSet<Symbol>();
		lfdone = new HashSet<Symbol>();
		fdone = new HashSet<Symbol>();
		fposdone = new HashMap<Symbol, HashSet<Integer>>();
	//	trules = new HashSet<GrammarRule> ();
	}

	
	
	private void getTopReachableRules() throws UnusualConditionException {
		HashSet<Symbol> usedBackwardStates = new HashSet<Symbol>();
		Vector<Symbol> pendingBackwardStates = new Vector<Symbol>();

		pendingBackwardStates.add(getStartState());
		usedBackwardStates.add(getStartState());
		int trrRuleCount = 0;


		// go down the forest. we will check each state for forward next
		while (!pendingBackwardStates.isEmpty()) {
			Symbol st = pendingBackwardStates.remove(0);

			for (GrammarRule g : getBackwardRules(st)) {


				//				Debug.prettyDebug("Saw "+g);
				trrRuleCount++;
				for (Symbol c : g.getChildren()) {
					if (!usedBackwardStates.contains(c)) {
						usedBackwardStates.add(c);
						pendingBackwardStates.add(c);
					}
				}
			}
		}

		Debug.prettyDebug("Saw "+trrRuleCount+" rules from top");
	}
	
	private static Vector<GrammarRule> empty;
	private static Vector<TreeTransducerRule> transempty;
	private static FilteringPairSymbol.FILTER[] nofilterarr;
	static {
		empty = new Vector<GrammarRule>();
		transempty = new Vector<TreeTransducerRule> ();
		nofilterarr = new FilteringPairSymbol.FILTER[1];
		nofilterarr[0] = FilteringPairSymbol.FILTER.NOEPS;
	}
	// 1-best experiment
	public static void main(String argv[]) {
		TropicalSemiring semiring = new TropicalSemiring();
		boolean runTopDown = true;
		Debug.debug(true, "Running "+(runTopDown ? "top-down" : "bottom-up"));
		try {
////			String choice = argv[3];
//			
//			if (!choice.equals("new") && !choice.equals("newer") && !choice.equals("old"))
//				throw new UnusualConditionException("Need to specify new, newer or old");
			
			int mh = Integer.parseInt(argv[0]);
			int k = Integer.parseInt(argv[1]);
			RTGRuleSet rtg = new RTGRuleSet(argv[2], "utf-8", semiring);
			Debug.prettyDebug("Lazy beam of "+mh);
			Debug.prettyDebug("Done loading rtg");
			ConcreteGrammar gr = new ConcreteGrammar(rtg);
			Grammar g = gr;
			Debug.prettyDebug("Done forming concreteGrammar");
			for (int i = 3; i < argv.length; i++) {
				TreeTransducerRuleSet trs = new TreeTransducerRuleSet(argv[i], "utf-8", semiring);
				if (trs.isCopying())
					throw new UnusualConditionException("Copying doesn't preserve recognizability");
				Debug.prettyDebug("Done loading "+argv[i]);
				OTFTreeTransducerRuleSet ottrs = new OTFTreeTransducerRuleSet(trs);
				Debug.prettyDebug("Done converting transducer to on-the-fly form");
				Debug.prettyDebug("Done building on-the-fly grammar");
				GTOTFGrammar otfg = new GTOTFGrammar(g, ottrs, semiring, mh);
				g = otfg;
			}
			if (mh < 0) {
				gr = new ConcreteGrammar(g);
				g = gr;
			}
			// get outsides as perfect heuristic
//			KBAS kbas = new KBAS(g);
			
			// load heuristics
//			g.loadHeuristics(argv[argv.length-1]);
//			Date preOutside = new Date();
//			HashMap<Symbol, Double> outsides = kbas.getOutsides();

//			Debug.prettyDebug("Got "+outsides.size()+" outsides");
//			g.setHeuristics(outsides);
			
			Date postOutside = new Date();

			
			for (int i = 0; i < k; i++) {
				Pair<TreeItem, Pair<Double, Integer>> tt = g.getNextTree(runTopDown);
				if (tt == null)
					System.out.println("EOL");
				else
					System.out.println((i+1)+":"+tt.l()+" # "+tt.r().l()+" # "+tt.r().r());
			}
			Date postTrees = new Date();
//			Debug.dbtime(1, 1, preOutside, postOutside, "gather heuristics");
			Debug.dbtime(1, 1, postOutside, postTrees, "get trees");

			Debug.prettyDebug("Globally created "+deadcount+" dead states and "+livecount+" locally live ones");
			Debug.prettyDebug("Avoided processing "+g.getDeadRuleCount()+" dead rules");
			g.getPushPop();
			
		}
		catch (DataFormatException e) {
			System.err.println("Bad data format reading rtg "+argv[0]);
			StackTraceElement elements[] = e.getStackTrace();
			int n = elements.length;
			for (int i = 0; i < n; i++) {       
				System.err.println(e.toString()+": "+elements[i].getFileName() + ":" 
						+ elements[i].getLineNumber() 
						+ ">> " 
						+ elements[i].getMethodName() + "()");
			}
			System.exit(-1);
		}
		catch (IOException e) {
			System.err.println("IO error reading rtg "+argv[0]);
			StackTraceElement elements[] = e.getStackTrace();
			int n = elements.length;
			for (int i = 0; i < n; i++) {       
				System.err.println(e.toString()+": "+elements[i].getFileName() + ":" 
						+ elements[i].getLineNumber() 
						+ ">> " 
						+ elements[i].getMethodName() + "()");
			}
			System.exit(-1);
		}
		catch (ImproperConversionException e) {
			System.err.println("Couldn't convert grammar -- probably not in normal form");
			StackTraceElement elements[] = e.getStackTrace();
			int n = elements.length;
			for (int i = 0; i < n; i++) {       
				System.err.println(e.toString()+": "+elements[i].getFileName() + ":" 
						+ elements[i].getLineNumber() 
						+ ">> " 
						+ elements[i].getMethodName() + "()");
			}
			System.exit(-1);
		}
		catch (UnusualConditionException e) {
			System.err.println("Unusual Condition!");
			StackTraceElement elements[] = e.getStackTrace();
			int n = elements.length;
			for (int i = 0; i < n; i++) {       
				System.err.println(e.toString()+": "+elements[i].getFileName() + ":" 
						+ elements[i].getLineNumber() 
						+ ">> " 
						+ elements[i].getMethodName() + "()");
			}
			System.exit(-1);
		}
	}
	
}
