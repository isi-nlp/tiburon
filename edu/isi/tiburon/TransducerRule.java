package edu.isi.tiburon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;


public abstract class TransducerRule implements Derivable{

private	TransducerLeftTree lhs;
	TransducerRightSide rhs;

	TransducerRuleVariableMap trvm;
	private Symbol state;

	// internal value, of course
	double weight;
	Semiring semiring;
	// memoized string value
	String sval=null;

	// for tied rules
	int tieid=0;

	// ref back to including set for setting rule index for purposes of setting numerical index
	TransducerRuleSet parent;
	int ruleIndex=-1;

	Hash hsh=null;

	// force the lower equals to run
	abstract public boolean equals(Object o);

	TransducerRule() { }
	// inherited private members
	TransducerRule(TransducerRuleSet p, TransducerLeftTree l, Symbol s, double w, Semiring sem, TransducerRuleVariableMap t) {
		parent = p;
		lhs = l;
		state = s;
		weight = w;
		semiring = sem;
		trvm = t;
	}
	

	// for conversion purposes
	// state is the same. rule rhs is used by both lhs and rhs. In lhs, just variables.
	// In rhs, state-variable match.
	// feed available syms
	TransducerRule(TransducerRuleSet trs, RTGRule rule, HashSet<Symbol> availableSyms, HashSet rtgstates) {
		parent = trs;
		trvm = new TransducerRuleVariableMap();
		weight = rule.getWeight();
		semiring = rule.getSemiring();
		state = rule.getLHS();
		tieid = rule.getTie();
		ruleIndex = parent.getNextRuleIndex();
		lhs = new TransducerLeftTree((TreeItem)rule.getRHS(), trvm, availableSyms, rtgstates);
		// rhs should be set by instantiating constructor
	}

	// accessors
	public Symbol getState() { return state; }
	void setState(Symbol s) { state = s; }
	// should only be used by constructor
	void setLHS(TransducerLeftTree l) { lhs = l; } 
	public double getWeight() { return weight; }
	public TransducerRuleVariableMap getTRVM() { 
		if (trvm == null)
			trvm = new TransducerRuleVariableMap();
		return trvm; 
	}
	public Semiring getSemiring() { return semiring; }
	public TransducerLeftTree getLHS() { return lhs; }
	
	public int getTie() { return tieid; }
	public boolean isCopying() { 
		boolean debug = false;
		if (trvm == null)
			return false;
		boolean ret = getTRVM().isCopying(); 
		if (debug) Debug.debug(debug, toString()+" copying status is "+ret);
		return ret;
	}
	public boolean isDeleting() { 
		boolean debug = false;
		if (trvm == null)
			return false;
		boolean ret = getTRVM().isDeleting(); 
		if (debug) Debug.debug(debug, toString()+" deleting status is "+ret);
		return ret;
	}

	public boolean isExtended() {
		boolean debug = false;
		if (lhs == null)
			return false;
		boolean ret = lhs.isExtended();
		if (debug) Debug.debug(debug, toString()+" extended status is "+ret);
		return ret;
	}
	public boolean isLookahead() {
		boolean debug = false;
		if (lhs == null)
			return false;
		boolean ret = lhs.isLookahead();
		if (debug) Debug.debug(debug, toString()+" lookahead status is "+ret);
		return ret;
	}
	public boolean isInEps() {
		boolean debug = false;
		if (lhs == null)
			return false;
		boolean ret = lhs.isEpsilon();
		if (debug) Debug.debug(debug, toString()+" in eps status is "+ret);	
		return ret;
	}
	public boolean isOutEps() {
		boolean debug = false;
		if (rhs == null)
			return false;
		boolean ret = rhs.isEpsilon();
		if (debug) Debug.debug(debug, toString()+" out eps status is "+ret);	
		return ret;
	}

	private Symbol lhsSym = null;
	private Symbol lhsCondSym = null;
	// memoized accessor needed for normalization
	// note: it's only the state and the root, NOT the whole lhs!
	// TODO: hashify this to avoid adding symbols!
	public Symbol getLHSSym() {
		if (lhsSym == null) {
			if (lhs.hasLabel())
				lhsSym = SymbolFactory.getStateSymbol(state.toString()+"."+lhs.getLabel().toString());
			else
				lhsSym = SymbolFactory.getStateSymbol(state.toString()+".");
		}
		return lhsSym;
	}

	// this one IS the whole lhs and state
	public Symbol getLHSCondSym() {
		if (lhsCondSym == null) {
			lhsCondSym = SymbolFactory.getStateSymbol(state.toString()+"."+lhs.toString());
		}
		return lhsCondSym;
	}


	// settor needed for training
	// TODO: does this muck up the hash sets holding the object??
	public void setWeight(double w) { 
		weight = w; 
		resetStringVal();
		//	setHashCode();
	}

	// filled on demand with mappings from a tree to the rtg rule produced
	// and the members of that rule that are states
	HashMap<TreeItem, Rule> grammarRules;
	HashMap<Rule, HashSet<StateTreePair>> grammarStates;


	// equality stuff
	public int hashCode() { 
		if (hsh == null)
			setHashCode();
		return  hsh.bag(Integer.MAX_VALUE);
	}
	public Hash getHash() {
		if (hsh == null)
			setHashCode();
		return hsh;
	}
	abstract void setHashCode();

	// to undo memoized tostring. set the memoized value to null.
	void resetStringVal() {
		sval = null;
	}

	abstract public String toString();

	// get all the states represented in this rule (from trvm)
	//     public  HashSet<Symbol> getStates() {
	// 	return trvm.getStates();
	//     }

	//     // get the sequence of states represented in this rule (from trvm)
	//     public Vector<Symbol> getStateSeq() {
	// 	return trvm.getStateSeq();
	//     }

	// TODO: maybe make this more complex. probably change the name
	public boolean isLHSTreeMatch(StateTreePair st) {
		return isLHSTreeMatch(st.getState(), st.getTree());
	}
	public boolean isLHSTreeMatch(Symbol st, TreeItem t) {
		if (!st.equals(state))
			return false;
		return lhs.isTreeMatch(t);
	}

	

	// TODO: again, the doubleness - assume isTreeMatch
	// getTreeMatchPairs for TLT gets a tree->variable mapping. We really care
	// about a tree->states mapping
	public HashMap getTreeMatchPairs(Symbol st, TreeItem t) {
		//	Debug.debug(true, "TransducerRule: getting match pairs on "+st.toString()+" and "+t.toString());
		HashMap h1 = lhs.getTreeMatchPairs(t);
		HashMap h2 = new HashMap();
		Iterator it = h1.keySet().iterator();
		while (it.hasNext()) {
			TreeItem subtree = (TreeItem)it.next();
			//	    Debug.debug(true, "Got subtree "+subtree.toString());
			h2.put(subtree, getTRVM().getNextStates((TransducerLeftTree)h1.get(subtree)));
		}
		return h2;
	}

	// create a cfg or RTG rule from a state, tree pair, and memoize it. Memoize states too, that are returned by getGrammarStates
	// only the actual rule state would apply, but this is a double check
	// added option to memoize or not
	// fill hash set here
	
	public Rule getForwardGrammarRule(RuleSet rs, StateTreePair st, HashSet<StateTreePair> newStates) {
		boolean debug = false;
		boolean doMemoize = false;
		if (doMemoize) {
			if (grammarRules == null) {
				grammarRules = new HashMap<TreeItem, Rule>();
				grammarStates = new HashMap<Rule, HashSet<StateTreePair>>();
			}
			//	Debug.debug(true, "Attempting to match "+st.toString()+" to "+toString());
			if (grammarRules.containsKey(st.getTree())) {
				Rule r = grammarRules.get(st.getTree());
				newStates.addAll(grammarStates.get(r));
					    Debug.debug(true, "Memoized!");
				return r;
			}
		}
		// store if there's no tree match
		if (!isLHSTreeMatch(st)) {
			if (doMemoize) {
				grammarRules.put(st.getTree(), null);
			}
			return null;
		}
		if (debug) Debug.debug(debug, "matching "+st.toString()+" to "+toString());
		// how to build an rtg rule:
		// lhs is the state, tree pair as a state
		// rhs is the rhs of the transducer rule with variables substituted
		HashMap<TransducerLeftTree, TreeItem> h = lhs.getInverseTreeMatchPairs(st.getTree());

		// other states added by buildTree
		//	if (grammarRules.keySet().size() % 100 == 0 ) {
		//	    Debug.debug(true, grammarRules.keySet().size()+" rules memoized at "+toString());
		//	}

		// build the rhs and create a rule out of it. Different for Tree and String
	
		
		Rule r = buildItem(st, h, rs, newStates, weight, semiring);
		newStates.add(st);
			if (doMemoize) {				
				grammarStates.put(r, newStates);
				grammarRules.put(st.getTree(), r);
			}
			
		if (debug) Debug.debug(debug, "Built "+r.toString());
		return r;	
	}


	// get left tree as "normal" tree, mapping variable trees to constants in the provided map
	// use TRVM to get right side
	public TreeItem getLeftComposableTree(HashMap<Symbol, TransducerRightSide> map) {
		boolean debug = false;
		// first get the variable children
		Iterator<TransducerLeftTree> varkids = getLHS().getVariableChildren().iterator();
		// map each one to a symbol, first reusing existing symbols from the map, then creating
		// new ones
		int varnum = 0;
		HashMap<TransducerLeftTree, Symbol> backmap = new HashMap<TransducerLeftTree, Symbol>();
		for (Symbol sym: map.keySet()) {
			if (varkids.hasNext()) {
				TransducerLeftTree val = varkids.next();
				TransducerRightSide[] arr = new TransducerRightSide[1];
				arr = getTRVM().getRHS(val).toArray(arr);
				// TODO: handle the multiple state issue!
				map.put(sym, arr[0]);
				backmap.put(val, sym);
				if (debug) Debug.debug(debug, "Re-using "+sym+" to map to "+arr[0]);
				varnum++;
			}
			// uninitialize if we run out of variables
			else {
				map.put(sym, null);
				if (debug) Debug.debug(debug, "Uninitializing "+sym);
			}
		}
		// create new symbols for the additional variables
		while (varkids.hasNext()) {
			Symbol newsym = SymbolFactory.getSymbol("TEMP"+varnum);
			TransducerLeftTree val = varkids.next();
			TransducerRightSide[] arr = new TransducerRightSide[1];
			arr = getTRVM().getRHS(val).toArray(arr);
			// TODO: handle the multiple state issue!
			map.put(newsym, arr[0]);
			backmap.put(val, newsym);
			if (debug) Debug.debug(debug, "Creating "+newsym+" to map to "+arr[0]);	    
			varnum++;
		}
		// now form the tree, substituting the new symbols where appropriate
		TreeItem ret = getLHS().getImageTree(backmap);
		if (debug) Debug.debug(debug, "Created "+ret.toString()+" out of "+getLHS().toString());
		return ret;
	}


	
	// local methods include a right side
	abstract Rule buildItem(StateTreePair st, 
			HashMap<TransducerLeftTree, TreeItem> h, 
			RuleSet rs, 
			HashSet<StateTreePair> states, 
			double weight, Semiring semiring);


	// for getting graehl-style deriv forests
	public int getIndex() {
		if (ruleIndex == -1) {
			ruleIndex = parent.getNextRuleIndex();
		}
		return ruleIndex;
	}


	// uniform array of leaf symbols (which might be all symbols if rhs is a string) used by pruneUseless
	public Symbol [] getRHSLeaves() {
		if (this instanceof TreeTransducerRule)
			return ((TreeTransducerRule)this).getRHS().getLeaves();
		else if (this instanceof StringTransducerRule)
			return ((StringTransducerRule)this).getRHS().getLeaves();
		else {
			Debug.debug(true, "ERROR: this transducer rule neither tree nor string, but "+this.getClass().getName()+"!");
			System.exit(1);
		}
		return null;
	}

	// given an extended rule, make a set of rules from it by traversing down the rule one level at a time,
	// copying variables where found, and extracting others.
	abstract public void makeNonExtended(ArrayList<TransducerRule> newrules);
	
	// given a rule with lookahead, convert it to a rule without lookahead and, if necessary, 
	// create subset of rules from some state, now starting with the subsetted symbol
	public boolean makeNonLookahead(ArrayList<TransducerRule> newrules, 
						HashMap<Symbol, HashMap<Symbol, Symbol>> map) {
		boolean debug = false;
		HashMap<Symbol, HashSet<Symbol>> newadds = new HashMap<Symbol, HashSet<Symbol>>();
		// convert the rule
		if (debug) Debug.debug(debug, "About to map lhs of "+this);

		if (!lhs.makeNonLookahead(getTRVM(), map, newadds, this)) {
			if (debug) Debug.debug(debug, "Unable to map lhs; returning");
			return false;
		}
		this.resetStringVal();
		if (debug) Debug.debug(debug, "Successfully mapped lhs: "+this);
		
		ArrayList<TransducerRule> tempnewrules = new ArrayList<TransducerRule>();
		tempnewrules.add(this);
		// for each newly added state, symbol pair add subsetted rules by copying existing rules and changing state
		boolean allAddOkay = true;
		for (Symbol st : newadds.keySet()) {
			for (Symbol sym : newadds.get(st)) {
				boolean didAdd = false;
				for (TransducerRule r : parent.getRulesOfType(st)) {
					// we don't care about a rule with the WRONG label
					if (r.getLHS().hasLabel() && r.getLHS().getLabel() != sym)
						continue;
					TransducerRule subrule = null;
					TransducerRuleVariableMap subruletrvm = new TransducerRuleVariableMap();
					TransducerLeftTree subrulelhs = new TransducerLeftTree(r.lhs, subruletrvm);
					
						
					// if rule has no label we actually have to ADD it, then attempt to remove it
					// by passing symbol down to descendants
					if (!r.getLHS().hasLabel()) { 
						subrulelhs.setLabel(sym);
					}
					
					// if rule has the right label, just change the state
					if (r instanceof TreeTransducerRule) {
						TransducerRightTree subrulerhs = new TransducerRightTree(((TreeTransducerRule)r).getRHS());
						subruletrvm.traverse(subrulerhs);
						subrule = new TreeTransducerRule(map.get(st).get(sym), subrulelhs, subrulerhs, subruletrvm, r.weight, r.semiring, (TreeTransducerRuleSet)parent);
					}
					else {
						TransducerRightString subrulerhs = new TransducerRightString(((StringTransducerRule)r).getRHS());
						subruletrvm.traverse(subrulerhs);
						subrule = new StringTransducerRule(map.get(st).get(sym), subrulelhs, subrulerhs, subruletrvm, r.weight, r.semiring, (StringTransducerRuleSet)parent);

					}
					
					if (debug) Debug.debug(debug, "Successfully made subrule "+subrule);
					if (subrule.isLookahead()) {
						if (!subrule.makeNonLookahead(newrules, map))
							continue;
					}
					else {
						// subrule is added in makeNonLookahead. If we didn't do that, add it here
						tempnewrules.add(subrule);
					}
					didAdd = true;

				}
				if (!didAdd) {
					if (debug) Debug.debug(debug, "Unable to find anything for "+st+"-"+sym+" so declaring failure");
					// but don't declare failure yet -- even though this rule is bad, newly added children could be okay, so process them
					map.get(st).put(sym, Symbol.getStar());
					allAddOkay = false;
//					break;
				}
			}
//			if (!allAddOkay) {
//				break;
//			}
		}
		// BUGFIX: rules weren't added if there was a failure, but this left some good rules never added. Now they're all added, but the rejection is noted.
		newrules.addAll(tempnewrules);
		return allAddOkay;
	}

	
}
