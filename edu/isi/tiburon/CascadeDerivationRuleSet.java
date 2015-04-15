package edu.isi.tiburon;

import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntDoubleIterator;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;
import gnu.trove.TIntStack;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;

import mjr.heap.Heap;
import mjr.heap.Heapable;

public class CascadeDerivationRuleSet implements Serializable{

	// hack enable and replace id when you recompile accidentally!
	//    static final long serialVersionUID = 4055010875998859110L;

	private int startState;
	private HashSet<CascadeDerivationRule> rules;
	private static final int minState = 1;
	private int maxState;
//	private TIntHashSet states;
	private transient Semiring semiring;

	// "regular" state to rule map - given a state, which rules is it in on the lhs?
	private transient HashMap<Integer, Vector<CascadeDerivationRule>> rulesByLHS=null;
	// "backwards" state to rule map - given a state, which rules is it in on the rhs?
	private transient HashMap<Integer, Vector<CascadeDerivationRule>> adj = null;

	// alphas and betas, which may be recalculated, so don't get comfortable.
	// all are based on nonterms, i.e. states
	private transient TIntDoubleHashMap alpha=null;
	private transient TIntDoubleHashMap beta=null;

	// state to rule table: best rule according to betas. this gets reset whenever betas change
	private transient TIntObjectHashMap bestRules = null;
	// numerical state index - set for the purpose of writing to graehl format
	// not needed now that states are just ints
	//     private Hashtable stateIndex = null;
	//     private int nextStateIndex = 1;

	// used in calculating alphas and betas
	private static final int OMEGA=-1;
	//     static {
	// 	OMEGA = SymbolFactory.getSymbol("**OMEGA**");
	//     }

	// inner class for getting alphas and betas
	class HeapKey implements Heapable {
		public double val;
		public int state;
		public HeapKey(int s, double v) {
			state = s;
			val = v;
		}
		public String toString() {
			return "("+state+", "+val+")";
		}
		public boolean equalTo(Object o) {
			return (val == ((HeapKey)o).val);
		}
		public boolean greaterThan(Object o) {
			return (val > ((HeapKey)o).val);
		}
		public boolean lessThan(Object o) {
			return (val < ((HeapKey)o).val);
		}

		// these don't do anything
		public void setPos(int i) {
			Debug.debug(true, "WARNING! HeapKey::setPos called");
		}
		public int getPos() {
			Debug.debug(true, "WARNING! HeapKey::getPos called");
			return -1;
		}

		// equals, not to be confused with equalTo, is for
		// hashset equality
		public boolean equals(Object o) {
			if (!o.getClass().equals(this.getClass()))
				return false;
			HeapKey k = (HeapKey)o;
			if (val != k.val)
				return false;
			if (state != k.state)
				return false;
			return true;
		}
		private void setHashCode() {
			int hsh = (new Double(val)).hashCode();
			hsh += (new Integer(state)).hashCode();
			hshcode = hsh;
		}
		private int hshcode = -1;
		public int hashCode() {
			if (hshcode == -1)
				setHashCode();
			return hshcode;
		}

	}

	// inner class for getting best rule for a state
	class RuleHeapKey implements Heapable {
		public double val;
		public CascadeDerivationRule rule;
		public RuleHeapKey(CascadeDerivationRule r, double v) {
			rule = r;
			val = v;
		}
		public String toString() {
			return "("+rule.toString()+", "+val+")";
		}
		public boolean equalTo(Object o) {
			return (val == ((RuleHeapKey)o).val);
		}
		public boolean greaterThan(Object o) {
			return (val > ((RuleHeapKey)o).val);
		}
		public boolean lessThan(Object o) {
			return (val < ((RuleHeapKey)o).val);
		}

		// 	// these don't do anything
		public void setPos(int i) {
			// 	    Debug.debug(true, "WARNING! HeapKey::setPos called");
		}
		public int getPos() {
			Debug.debug(true, "WARNING! HeapKey::getPos called");
			return -1;
		}


	}

//
//	public CascadeDerivationRuleSet(int start, TIntHashSet sth, HashSet<CascadeDerivationRule> ruh, Semiring s) {
//		startState = start;
//		states = sth;
//		rules = ruh;
//		semiring = s;
//	}
	
	public CascadeDerivationRuleSet(int start, int inmaxState, HashSet<CascadeDerivationRule> ruh, Semiring s) {
		startState = start;
		
		maxState = inmaxState;
//		states = new TIntHashSet();
//		for (int i = 1; i < maxState; i++)
//			states.add(i);
		rules = ruh;
		semiring = s;
	}


	public String toString() {
		StringBuffer l = new StringBuffer(startState+"\n");
		Vector<CascadeDerivationRule> initSet = getRulesByLHS(startState);
		if (initSet != null) {
			for (CascadeDerivationRule r : initSet) {
				l.append(r.toString());
				l.append("\n");
			}
		}
		for (int key : rulesByLHS.keySet()) {
			if (key == startState)
				continue;
			for (CascadeDerivationRule r : rulesByLHS.get(key)) {
				l.append(r.toString());
				l.append("\n");
			}
		}   
		return l.toString();
	}

	// toForest is for printing the DRS in a format recognized by forest-em.
	// each state is an OR-node and a possibly repeatable subtree. each RHS is an AND-node
	// (just a node) and could be repeatable...so this must be tracked. 

	// note: toForest isn't so useful unless the internal integers used to track rules are made public
	public String toForest() {
		TIntHashSet usedStates = new TIntHashSet();
		return forestFromState(startState, usedStates, 0).toString();
	}

	// recursively called function doing the work of toForest
	private StringBuffer forestFromState(int st, TIntHashSet used, int indent) {
		boolean debug = false;
		if (debug) Debug.debug(debug, indent, "adding ref to state "+st);
		StringBuffer retval = new StringBuffer("");
		if (used.contains(st)) {
			if (debug) Debug.debug(debug, indent, "already seen "+st+", so backreferencing");
			retval.append("#"+st+" ");
			return retval;
		}
		// if it's just a single number, don't bother with the hash thing
		Vector<CascadeDerivationRule> ruleSet = getRulesByLHS(st);
		if (ruleSet.size() == 1) {
			CascadeDerivationRule r = (CascadeDerivationRule)(ruleSet.iterator().next());
			if (r.getNumChildren() == 0) {
				retval.append(r.getLabelIndex()+" ");
				return retval;
			}
		}
		// otherwise, we do the hash thing
		retval.append("#"+st);
		used.add(st);

		if (ruleSet.size() > 1)
			retval.append("(OR ");
		if (debug) Debug.debug(debug, indent, "defining "+st+"; "+ruleSet.size()+" members of OR node");
		int ruleCounter = 0;
		Iterator it = ruleSet.iterator();
		while (it.hasNext()) {
			CascadeDerivationRule r = (CascadeDerivationRule)it.next();
			ruleCounter++;
			if (debug) Debug.debug(debug, indent, ruleCounter+": "+r.toString()+" has a label of "+r.getLabelIndex());
			// TODO: check right side of r for and-node duplication!
			if (r.getNumChildren() > 0) {
				if (debug) Debug.debug(debug, indent, r.getNumChildren()+" children");
				retval.append("("+r.getLabelIndex()+" ");
				int [] kids = r.getRHS();
				for (int i = 0; i < r.getNumChildren(); i++) {
					retval.append(forestFromState(kids[i], used, indent+1));
				}
				retval.append(")");
			}
			else {
				if (debug) Debug.debug(debug, indent, "no children");
				retval.append(r.getLabelIndex()+" ");
			}
		}
		if (ruleSet.size() > 1)
			retval.append(")");
		return retval;
	}


	// one-time rulesByLHS initializer
	private void setRulesByLHS() {
		rulesByLHS = new HashMap<Integer, Vector<CascadeDerivationRule>>();
		for (CascadeDerivationRule r : rules) {
			if (!rulesByLHS.containsKey(r.getLHS()))
				rulesByLHS.put(r.getLHS(), new Vector<CascadeDerivationRule>());
			rulesByLHS.get(r.getLHS()).add(r);
		}
	}

	// one-time adj initializer
	private void setAdj(boolean debug) {
		adj = new HashMap<Integer, Vector<CascadeDerivationRule>>();
		Iterator it = rules.iterator(); 
		while (it.hasNext()) {
			CascadeDerivationRule currRule = (CascadeDerivationRule)it.next();
			if (debug) Debug.debug(debug, "Derivation Rule is "+currRule.toString());

			int[] leafChildren = getLeafChildren(currRule);
			// either omega or belongs to more than one
			if (leafChildren.length == 0) {
				if (debug) Debug.debug(debug, "Adding "+currRule.toString()+" to omega adj - no states in rhs");
				if (!adj.containsKey(OMEGA))
					adj.put(OMEGA, new Vector<CascadeDerivationRule>());
				adj.get(OMEGA).add(currRule);
			}
			else {
				for (int i = 0; i < leafChildren.length; i++) {
					int state = leafChildren[i];
					if (debug) Debug.debug(debug, "Adding "+currRule.toString()+" to "+state+" adj");
					if (!adj.containsKey(state)) 
						adj.put(state, new Vector<CascadeDerivationRule>());
					adj.get(state).add(currRule);
				}
			}
		}
	}

	public void calculateWeights() {
		calculateBetas();
		calculateAlphas();
	}



	// nothing like the k-best derivation - this just is a recursive definition
	public void calculateBetas() {
		boolean debug = false;
		if (rulesByLHS == null)
			setRulesByLHS();
		beta = new TIntDoubleHashMap();
//		TIntIterator stit = states.iterator();
		// bestRules is no longer reliable
		bestRules = new  TIntObjectHashMap();
		for (int state = minState; state < maxState; state++) {
//		while (stit.hasNext()) {
//			int state = stit.next();
			//	    if (state == startState)
			//		calculateBeta(state, 0, true);
			//	    else
			calculateBeta(state, 0);
		}
		// sanity check
		if (debug) {
			Debug.prettyDebug("Betas: ");
			for (TIntDoubleIterator kit = beta.iterator(); kit.hasNext();) {
				kit.advance();
				Debug.prettyDebug("\t"+kit.key()+"("+rulesByLHS.get(kit.key())+"): "+kit.value());
			}
		}
	}

	// recursive function does the work
	private void calculateBeta(int state, int indent) {
		boolean debug = false;
		// already done
		if (beta.containsKey(state)) {
			if (debug) Debug.debug(debug, indent, "Beta for "+state+" already determined");
			return;
		}
		double newval = semiring.ZERO();
		Vector<CascadeDerivationRule> ruleSet = rulesByLHS.get(state);
		if (ruleSet == null) {
			if (debug) Debug.debug(debug, indent, "No rules for "+state+", beta is "+newval);
		}
		else {
			Heap Q = null;
			if (semiring.ZERO() > semiring.ONE())
				Q = new Heap(false);
			else 
				Q = new Heap(true);
			for (CascadeDerivationRule currule : ruleSet) {
			
				// beta for a rule is its weight times the beta of its children
				// if no rule weight (ie it is a virtual) it's just the children
				
				if (debug) Debug.debug(debug, indent, "Getting beta for Rule "+currule.toString());
				double partialVal = (currule.isVirtual() ? semiring.ONE() : currule.getWeight());
				int[] kids = getLeafChildren(currule);
				for (int i = 0; i < kids.length; i++) {
					int kid = kids[i];
					calculateBeta(kid, indent+1);
					partialVal = semiring.times(partialVal, beta.get(kid));
					if (debug) Debug.debug(debug, indent, "partial Beta for rule "+currule.toString()+" to "+partialVal+" : added "+beta.get(kid));
				}
				RuleHeapKey key = new RuleHeapKey(currule, partialVal);
				Q.insert(key);
				// each partial val added in
				if (debug) Debug.debug(debug, indent, "adding "+newval+" to "+partialVal);
				newval = semiring.plus(newval, partialVal);
				if (debug) Debug.debug(debug, indent, "beta for "+state+" to "+newval);
			}
			RuleHeapKey bestkey = (RuleHeapKey)Q.remove();
			bestRules.put(state, bestkey.rule);
		}

		beta.put(state, newval);
	}


	// nothing like the k-best derivation - this just is a recursive definition
	public void calculateAlphas() {
		boolean debug = false;
		if (adj == null)
			setAdj(debug);
		alpha = new TIntDoubleHashMap();
		//TIntIterator stit = states.iterator();
		for(int state = minState; state < maxState; state++) {
//		while (stit.hasNext()) {
//			int state = stit.next();
			calculateAlpha(state);
		}
		// sanity check
		if (debug) {
			System.err.println("Alphas: ");
			for (TIntDoubleIterator kit = alpha.iterator(); kit.hasNext();) {
				kit.advance();
				System.err.println("\t"+kit.key()+": "+kit.value());
			}
		}
	}

	// recursive function does the work
	private void calculateAlpha(int state) {
		boolean debug = false;
		// already done
		if (alpha.containsKey(state)) {
			if (debug) Debug.debug(debug, "Alpha for "+state+" already determined");
			return;
		}
		if (state == startState) {
			alpha.put(state, semiring.ONE());
			return;
		}
		if (debug) Debug.debug(debug, "Alpha for "+state+":");
		double newval = semiring.ZERO();
		Vector<CascadeDerivationRule> ruleSet = adj.get(state);
		if (ruleSet == null) {
			if (debug) Debug.debug(debug, "No rules for "+state+", alpha is "+newval);
		}
		else {
			Iterator rsit = ruleSet.iterator();
			for (CascadeDerivationRule currule : ruleSet) {
				// alpha component for a given rule is alpha of the lhs times weight times beta of each sibling except self
				if (debug) Debug.debug(debug, "Getting alpha component for "+state+" from "+currule.toString());
				double partialVal = currule.isVirtual() ? semiring.ONE() : currule.getWeight();
				if (debug) Debug.debug(debug, "rule weight brings partial Alpha to "+partialVal);
				calculateAlpha(currule.getLHS());
				partialVal = semiring.times(partialVal, alpha.get(currule.getLHS()));
				if (debug) Debug.debug(debug, "lhs alpha brings partial Alpha to "+partialVal);
				int[] kids = getLeafChildren(currule);
				for (int i = 0; i < kids.length; i++) {
					int kid = kids[i];
					if (kid == state)
						continue;
					partialVal = semiring.times(partialVal, beta.get(kid));
					//		    if (debug) Debug.debug(debug, "child beta of "+beta.get(kid)+" brings partial Alpha to "+partialVal);
					if (debug) Debug.debug(debug, "\t\t beta of "+kid+"("+beta.get(kid)+")*");
				}
				if (debug) Debug.debug(debug, "\t="+partialVal+"+");
				// each partial val added in
				newval = semiring.plus(newval, partialVal);
				//		if (debug) Debug.debug(debug, "alpha for "+state+" to "+newval);
			}
			if (debug) Debug.debug(debug, "="+newval);
		}
		alpha.put(state, newval);
	}


	// prune useless - two-pass algorithm, based on knuth. First go bottom up and mark all states
	// that can be used to reach terminal symbols. Then go top-down and mark all states that can
	// be reached from the start symbol. Only keep states that satisfy both criteria, and throw away any rules that have discarded states.
	// this is taken from RuleSet
	public void pruneUseless() {

		boolean debug = false;
		if (adj == null)
			setAdj(false);
		if (rulesByLHS == null)
			setRulesByLHS();

		TIntHashSet bottomReachable = new TIntHashSet();
		// phase 1: bottom up
		// for each state not already reachable, try and add it

		int brSize = 0;
		do {
			brSize = bottomReachable.size();
			for (int currState = minState; currState < maxState; currState++) {
//			TIntIterator stit = states.iterator();
//			while (stit.hasNext()) {
	//			int currState = stit.next();
				//		Debug.debug(true, "BU: Considering state "+currState.toString());
				if (bottomReachable.contains(currState))
					continue;
				Vector<CascadeDerivationRule> currRules = getRulesByLHS(currState);
				if (currRules == null)
					continue;
				//		Debug.debug(true, "\t"+currRules.size()+" rules");
				
				// look for at least one valid rule
				for (CascadeDerivationRule currRule : currRules) {
					
					// sometimes this is run when ruleset is in an uncertain state. trust rules list above rulesoftype
					// list
					if (!rules.contains(currRule))
						continue;
					int[] leaves = getLeafChildren(currRule);
					boolean isOkay = true;
					// check that all leaves are already seen
					for (int i = 0; i < leaves.length; i++) {
						if (!bottomReachable.contains(leaves[i])) {
							isOkay = false;
							break;
						}
					}
					if (isOkay) {
						//			Debug.debug(true, "BU: "+currRule.getLHS().toString());
						//			Debug.debug(true, "\t thanks to "+currRule.toString());
						bottomReachable.add(currRule.getLHS());
						break;
					}
				}
			}
			//	    Debug.debug(true, "Gone from "+brSize+" to "+bottomReachable.size());
		} while (brSize < bottomReachable.size());

		// phase 2: top down
		// starting with the start state (if it's bottom-reachable), 
		// find each state that can be reached in a downward direction
		// more specifically, find each rule that applies
		HashSet<CascadeDerivationRule> checkedRules = new HashSet<CascadeDerivationRule>();
		TIntHashSet checkedStates = new TIntHashSet();
		TIntStack readyStates = new TIntStack();
		TIntHashSet readyStatesList = new TIntHashSet();
		if (bottomReachable.contains(startState)) {
			readyStates.push(startState);
			readyStatesList.add(startState);
		}
		while (readyStates.size() > 0) {
			int currState = readyStates.pop();
			//	    Debug.debug(true, "TD: "+currState.toString());
			checkedStates.add(currState);
			Vector<CascadeDerivationRule> currRules = getRulesByLHS(currState);
			if (currRules == null)
				continue;
			Iterator rit = currRules.iterator();
			// look for at least one valid rule
			while (rit.hasNext()) {
				CascadeDerivationRule currRule = (CascadeDerivationRule)rit.next();
				// sometimes this is run when ruleset is in an uncertain state. trust rules list above rulesoftype
				// list
				if (!rules.contains(currRule))
					continue;
				int[] leaves = getLeafChildren(currRule);
				boolean isOkay = true;
				// check that all leaves are already seen
				for (int i = 0; i < leaves.length; i++) {
					if (!bottomReachable.contains(leaves[i])) {
						isOkay = false;
						break;
					}
				}
				// valid rules inspire other states to check
				if (isOkay) {
					checkedRules.add(currRule);
					for (int i = 0; i < leaves.length; i++) {
						if (!checkedStates.contains(leaves[i]) &&
								!readyStatesList.contains(leaves[i])) {
							readyStates.push(leaves[i]);
							readyStatesList.add(leaves[i]);
						}
					}
				}
			}
		}
		// 	Debug.debug(true, "Going from "+rules.size()+" to "+checkedRules.size()+" rules");
		// 	Debug.debug(true, "Going from "+states.size()+" to "+checkedStates.size()+" states");
		rules = checkedRules;
//		states = checkedStates;
		rulesByLHS = null;
		adj = null;
		alpha = null;
		beta = null;
	}

	// stricter conditions than above pruneUseless; this is the same algorithm but also prunes a rule if the rule
	// within it has a zero weight
	public void pruneUselessAndZero() {		
		boolean debug = false;
		if (adj == null)
			setAdj(false);
		if (rulesByLHS == null)
			setRulesByLHS();

		TIntHashSet bottomReachable = new TIntHashSet();
		// phase 1: bottom up
		// for each state not already reachable, try and add it

		int brSize = 0;
		do {
			brSize = bottomReachable.size();
			for (int currState = minState; currState < maxState; currState++) {
				if (debug) Debug.debug(debug, "BU: Considering state "+currState);
				if (bottomReachable.contains(currState))
					continue;
				Vector<CascadeDerivationRule> currRules = getRulesByLHS(currState);
				if (currRules == null)
					continue;
				//		Debug.debug(true, "\t"+currRules.size()+" rules");
				Iterator rit = currRules.iterator();
				// look for at least one valid rule
				while (rit.hasNext()) {
					CascadeDerivationRule currRule = (CascadeDerivationRule)rit.next();
					// sometimes this is run when ruleset is in an uncertain state. trust rules list above rulesoftype
					// list
					if (!rules.contains(currRule))
						continue;
					if (!currRule.isVirtual() && semiring.betteroreq(semiring.ZERO(),currRule.getWeight())) {
						if (debug) Debug.debug(debug, "BU: Not considering "+currRule.toString()+" because of zero label");
						continue;
					}
					int[] leaves = getLeafChildren(currRule);
					boolean isOkay = true;
					// check that all leaves are already seen
					for (int i = 0; i < leaves.length; i++) {
						if (!bottomReachable.contains(leaves[i])) {
							isOkay = false;
							break;
						}
					}
					if (isOkay) {
						if (debug) Debug.debug(debug, "BU: "+currRule.getLHS());
						if (debug) Debug.debug(debug, "\t thanks to "+currRule.toString());
						bottomReachable.add(currRule.getLHS());
						break;
					}
				}
			}
			//	    Debug.debug(true, "Gone from "+brSize+" to "+bottomReachable.size());
		} while (brSize < bottomReachable.size());

		// phase 2: top down
		// starting with the start state (if it's bottom-reachable), 
		// find each state that can be reached in a downward direction
		// more specifically, find each rule that applies
		HashSet checkedRules = new HashSet();
		TIntHashSet checkedStates = new TIntHashSet();
		TIntStack readyStates = new TIntStack();
		TIntHashSet readyStatesList = new TIntHashSet();
		if (bottomReachable.contains(startState)) {
			readyStates.push(startState);
			readyStatesList.add(startState);
		}
		while (readyStates.size() > 0) {
			int currState = readyStates.pop();
			if (debug)  Debug.debug(debug, "TD: "+currState);
			checkedStates.add(currState);
			Vector<CascadeDerivationRule> currRules = getRulesByLHS(currState);
			if (currRules == null)
				continue;
//			Iterator rit = currRules.iterator();
			// look for at least one valid rule
			for (CascadeDerivationRule currRule : currRules) {
				// sometimes this is run when ruleset is in an uncertain state. trust rules list above rulesoftype
				// list
				if (!rules.contains(currRule))
					continue;
				if (!currRule.isVirtual() && semiring.betteroreq(semiring.ZERO(), currRule.getWeight())) {
					if (debug) Debug.debug(debug, "TD: Not considering "+currRule.toString()+" because of zero label");
					continue;
				}
				int[] leaves = getLeafChildren(currRule);
				boolean isOkay = true;
				// check that all leaves are already seen
				for (int i = 0; i < leaves.length; i++) {
					if (!bottomReachable.contains(leaves[i])) {
						isOkay = false;
						break;
					}
				}
				// valid rules inspire other states to check
				if (isOkay) {
					if (debug) Debug.debug(debug, currRule+" is okay");
					checkedRules.add(currRule);
					for (int i = 0; i < leaves.length; i++) {
						if (!checkedStates.contains(leaves[i]) &&
								!readyStatesList.contains(leaves[i])) {
							readyStates.push(leaves[i]);
							readyStatesList.add(leaves[i]);
						}
					}
				}
			}
		}
		// 	Debug.debug(true, "Going from "+rules.size()+" to "+checkedRules.size()+" rules");
		// 	Debug.debug(true, "Going from "+states.size()+" to "+checkedStates.size()+" states");
		rules = checkedRules;
	//	states = checkedStates;
		rulesByLHS = null;
		adj = null;
		alpha = null;
		beta = null;
	}



	// unlike regular rules, derivation rules are always cnf, so no getLeafChildren is needed
	// if this is untrue or changes, that modification should be made here.
	// in the meantime, getLeafChildren merely gets the children
	public int[] getLeafChildren(CascadeDerivationRule r) {
		int [] rhs = r.getRHS();
		int [] ret = new int[r.getNumChildren()];
		for (int i = 0; i < ret.length; i++)
			ret[i] = rhs[i];
		return ret;
	}

	// accessors for alpha and beta
	double getAlpha(int s) {
		if (alpha == null) {
			Debug.debug(true, "Warning: alpha requested before alphas set");
			return semiring.ZERO();
		}
		if (!alpha.containsKey(s)) {
			Debug.debug(true, "Warning: alpha value of "+s+" not set...returning incorrect value");
			return semiring.ZERO();
		}
		return alpha.get(s);
	}
	double getBeta(int s) throws UnusualConditionException {
		if (beta == null) {
			throw new UnusualConditionException("Warning: beta "+s+" requested before betas set");
			//	    return semiring.ZERO();
		}
		if (!beta.containsKey(s)) {
			throw new UnusualConditionException("Warning: beta value of "+s+" not set...returning incorrect value");
			//	    return semiring.ZERO();
		}
		return beta.get(s);
	}

	// other accessors
	int getStartState() { return startState; }
	HashSet<CascadeDerivationRule> getRules() { return rules; }
	int getMinState() { return minState; }
	int getMaxState() { return maxState; }
//	TIntHashSet getStates() { return states; }
	public Vector<CascadeDerivationRule> getRulesByLHS(int state) {
		if (rulesByLHS == null)
			setRulesByLHS();
		return rulesByLHS.get(state);
	}
	public int getNumRules() { return rules.size(); }
	public int getNumStates() { 
//		return states.size(); 
		return maxState - (minState-1);
	}
	public Semiring getSemiring() { return semiring; }


	// given a state, return the best rule. Assume calculateBetas has been set
	public CascadeDerivationRule getBestRule(int s) {
		boolean debug = false;
		if (bestRules.containsKey(s)) {
			Debug.debug(debug, "Using memoized value for state "+s);
			return (CascadeDerivationRule)bestRules.get(s);
		}
		// target is the viterbi score of this state. If we have a rule with that score, we return it.
		Vector<CascadeDerivationRule> cands = getRulesByLHS(s);
		double bestScore = semiring.ZERO();
		CascadeDerivationRule currRule = null;
		for (CascadeDerivationRule r : cands) {
			double score = r.isVirtual() ? semiring.ONE() : r.getWeight();
			int[] kids = getLeafChildren(r);
			for (int i = 0; i < kids.length; i++) {
				try {
					score = semiring.times(score, getBeta(kids[i]));
				}
				catch (UnusualConditionException e) {
					Debug.debug(true, "Unusual Condition getting beta "+i+" for rule "+r.toString()+": "+e.getMessage());
				}
			}
			if (currRule == null || semiring.better(score, bestScore)) {
				currRule = r;
				bestScore = score;
			}
		}
		if (currRule == null) {
			Debug.debug(true, "ERROR: no rule has viterbi score better than "+semiring.ZERO()+" for "+s);
		}
		bestRules.put(s, currRule);
		return currRule;
	}


	// getViterbiAlignments: maybe a little too special-case for tiburon?
	// Set betas and conduct a best
	// pass through this DRS. Along the way we catch alignments for the words. when done we return what
	// we have
	public String getViterbiAlignments() {
		boolean debug = false;
		Date prebeta = new Date();
		calculateBetas();
		Date postbeta = new Date();
		Debug.dbtime(1, 1, prebeta, postbeta, "get betas ");
		CascadeDerivationRule currRule = getBestRule(getStartState());
		HashSet rulesUsed = new HashSet();
		double total = recursiveGetViterbi(currRule, rulesUsed, 0, debug);
		if (debug) Debug.debug(debug, "Total weight of viterbi deriv is "+total);
		Iterator ruit = rulesUsed.iterator();
		StringBuffer completeAlign = new StringBuffer();
		while (ruit.hasNext()) {
			CascadeDerivationRule r = (CascadeDerivationRule)ruit.next();
			StringBuffer as = r.getAlignString();
			if (as != null)
				completeAlign.append(as);
		}
		completeAlign.append("\n");
		return completeAlign.toString();
	}

	private double recursiveGetViterbi(CascadeDerivationRule r, HashSet set, int depth, boolean debug) {
		if (r == null) {
			if (debug) Debug.debug(debug, depth, "null derivation rule");
			return semiring.ZERO();
		}
		double weight = r.getWeight();
	//	if (debug) Debug.debug(debug, depth, (r.isVirtual() ? "" : r.getRuleIndex()+" = ")+r.toString());
		set.add(r);
		int[] kids = getLeafChildren(r);
		for (int i = 0; i < kids.length; i++) {
			CascadeDerivationRule nextRule = getBestRule(kids[i]);
			weight = semiring.times(weight, recursiveGetViterbi(nextRule, set, depth+1, debug));
		}
		return weight;
	}

	// state index - used for printing graehl format

	// no longer needed - state index is the state itself, which is an int
	// has the index been set?
	//     public boolean hasStateIndex(int state) {
	// 	if (stateIndex == null || !stateIndex.containsKey(state))
	// 	    return false;
	// 	return true;
	//     }

	//     // get the state index. it's assumed to exist, otherwise null will be returned
	//     public String getStateIndex(Symbol state) {
	// 	return (String)stateIndex.get(state);
	//     }




	//     // get a new state index (the state index is assumed to not exist and no check is done)
	//     public String getNewStateIndex(Symbol state) {
	// 	if (stateIndex == null)
	// 	    stateIndex = new Hashtable();
	// 	stateIndex.put(state, new String(""+(nextStateIndex++)));
	// 	return (String)stateIndex.get(state);
	//     }


	// isFinite returns true if there are not an infinite number of trees that can be produced
	// from this set. this code taken from RuleSet
	boolean isFinite() {
		// TODO: memoize this, releasing memoization when change to set occurs
		// maintain master set (states we will process), then for each state popped off,
		// get the set of states it reaches until this is unchanging. If one of the members of the set
		// is the state from the master, we're in a loop. Union the reached states 
		// minus the processed states with the master set
		// and continue. When the master set is empty, we're done.

		TIntHashSet masterStates = new TIntHashSet();
		TIntHashSet processedStates = new TIntHashSet();
		masterStates.add(startState);
		int stateCounter = 0;
		while(!masterStates.isEmpty()) {
			// the state we will look for a loop to
			int currState = masterStates.iterator().next();
			stateCounter++;
			processedStates.add(currState);
			masterStates.remove(currState);
			TIntHashSet reachedStates = new TIntHashSet();

			// the things that could possibly increase the size of reached states
			// initialized with the rules produced from the currstate
			HashSet currRules = new HashSet(getRulesByLHS(currState));
			int size = reachedStates.size();

			// in the loop: 
			//    1) add all the states from currRules to a mini set. die if the set has the currstate
			//    2) empty out currRules and refill it with the expansions from the miniset
			//    3) union reachedstates with the miniset. check size and break if static
			// stop when the reached states size stops changing
			while (true) {
				TIntHashSet miniSet = new TIntHashSet();
				//    1) add all the states from currRules to a mini set. die if the set has the currstate
				Iterator it = currRules.iterator();
				while (it.hasNext()) {
					int[] leaves = getLeafChildren((CascadeDerivationRule)it.next());
					for (int i = 0; i < leaves.length; i++) {
						if (leaves[i] >= minState && leaves[i] < maxState) {
//						if (states.contains(leaves[i])) {
							if (currState == leaves[i]) {
								//Debug.debug(true, "Can reach "+currState.toString()+" from itself");
								return false;
							}
							miniSet.add(leaves[i]);
						}
					}
				}
				//    2) empty out currRules and refill it with the expansions from the miniset
				currRules.clear();
				TIntIterator miniit = miniSet.iterator();
				while (miniit.hasNext())
					currRules.addAll(getRulesByLHS(miniit.next()));
				//    3) union reachedstates with the miniset. check size and break if static
				reachedStates.addAll(miniSet.toArray());
				if (reachedStates.size() == size)
					break;
				size = reachedStates.size();
			}
			// we've established that currState does not generate rules to reach itself.
			// if there are new states here, add them to the masterstates list
			reachedStates.removeAll(processedStates.toArray());
			masterStates.addAll(reachedStates.toArray());
		}
		return true;
	}

	// taken from RuleSet
	public String getNumberOfDerivations() {
		TIntObjectHashMap scores = new TIntObjectHashMap();
		TIntHashSet okStates = new TIntHashSet();
		TIntHashSet leftStates = new TIntHashSet();
		for (int i = minState; i < maxState; i++)
			leftStates.add(i);

		// copy cause we change the set
		while (!scores.containsKey(startState)) {
			TIntHashSet leftStatesCopy = new TIntHashSet(leftStates.toArray());
			TIntIterator it = leftStatesCopy.iterator();
			while (it.hasNext()) {
				//		Debug.debug(true, "Size of left states = "+leftStates.size());
				//		Debug.debug(true, "Size of left states copy = "+leftStatesCopy.size());
				int currState = it.next();
				//		Debug.debug(true, "Considering state "+currState);
				Vector<CascadeDerivationRule> currRules = getRulesByLHS(currState);
				if (currRules == null) {
					Debug.debug(true, "ERROR: "+currState+" has no rules!");
					System.exit(0);
				}
				BigInteger derivCount = new BigInteger("0");
				boolean hasBadState = false;
				for (CascadeDerivationRule rule : currRules) {
					BigInteger ruleDeriv = new BigInteger("1");
					int[] leaves = getLeafChildren(rule);
					//		    System.err.print("\t");
					for (int i = 0; i < leaves.length; i++) {
						//			System.err.print(leaves.get(i)+"...");
						// acceptable states multiply the derivations. unacceptables
						// get the can. terminals don't affect the number
//						if (states.contains(leaves[i])) {
						if (leaves[i] >= minState && leaves[i] < maxState) {
							if (okStates.contains(leaves[i])) {
								BigInteger contrib = (BigInteger)scores.get(leaves[i]);
								ruleDeriv = ruleDeriv.multiply(contrib);
								//				System.err.print("("+contrib+") ");
							}
							else {
								//				Debug.debug(true, "BAD!");
								hasBadState = true;
								break;
							}
						}
						//			else
						//			    System.err.print("(1t) ");
					}
					// one bad apple spoils the bunch
					if (hasBadState)
						break;
					//		    Debug.debug(true, "="+ruleDeriv);
					derivCount = derivCount.add(ruleDeriv);
				}
				if (hasBadState)
					continue;
				//		Debug.debug(true, "\tTOTAL: "+derivCount.toString());
				// now we know this state, so it's no longer bad. we can remove it from 
				// consideration and make it okay
				scores.put(currState, derivCount);
				okStates.add(currState);
				leftStates.remove(currState);
			}
		}
		return ((BigInteger)scores.get(startState)).toString();
	}

	// revive - a very weird function that allows rules to restore their label if they've been written
	// restores semiring too, so it can change
	public void revive(Vector<TransducerRuleSet> trs, Semiring s) {
		semiring = s;
		//	Debug.debug(true, "Reviving "+rules.size()+" derivation rules with "+trs.getNumRules()+" transducer rules");
		for (CascadeDerivationRule r : rules) {
			r.revive(trs);
			r.setSemiring(s);
		}
		//	Debug.debug(true, "Done Reviving; processing rules by lhs and adj");
		setRulesByLHS();
		setAdj(false);
		//	Debug.debug(true, "Done resetting");
	}

	// revive for rule set-based DRS
	public void revive(RuleSet rs, Semiring s) {
		semiring = s;
		//	Debug.debug(true, "Reviving "+rules.size()+" derivation rules with "+rs.getNumRules()+" grammar rules");
		for (CascadeDerivationRule r : rules) {
			r.revive(rs);
			r.setSemiring(s);
		}
		//	Debug.debug(true, "Done Reviving; processing rules by lhs and adj");
		setRulesByLHS();
		setAdj(false);
		//	Debug.debug(true, "Done resetting");
	}
}
