package edu.isi.tiburon;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectDoubleHashMap;

public abstract class TransducerRuleSet {
	Symbol startState;
	ArrayList<TransducerRule> rules;
	HashSet<Symbol> states;
	HashMap<Symbol, ArrayList<TransducerRule>> rulesByState;
	HashMap<Integer, TransducerRule> rulesByIndex;
	HashMap<Integer, ArrayList<TransducerRule>> rulesByTie;

	// experimental: track which symbols are available so we can skip some composition processing
	// for now, only activated when converting an rtg to transducer
	HashSet<Symbol> applicableSymbols = null;

	Semiring semiring;

	// memoization holder: map state x tree to set of rules that are useful
	// TODO: link to children rules instead of copying!!
	HashMap stateTreeRules;


	// used by transducerRule for setting ruleIndex, which is used for getting graehl-style deriv forests
	// and for serialization
	int nextRuleIndex = 1;

	// used by transducerRule for creating new states if necessary
	int nextNewState = 0;

	// memoize copying, deleting, extended, ineps, outeps status
	boolean isCopying = false;
	boolean isCopyingSet = false;

	boolean isDeleting = false;
	boolean isDeletingSet = false;

	boolean isExtended = false;
	boolean isExtendedSet = false;

	boolean isInEps = false;
	boolean isInEpsSet = false;

	boolean isOutEps = false;
	boolean isOutEpsSet = false;
	
	boolean isLookahead = false;
	boolean isLookaheadSet = false;

	// vector position id -- used to identify rule source in vector-based DR when reviving
	private int positionID;
	public void setID(int id) { positionID = id; }
	public int getID() { return positionID; }
	
	
	// dummy constructor for testing file formats
	public TransducerRuleSet() {
		rules = new ArrayList<TransducerRule>();
		states = new HashSet<Symbol>();
		rulesByState = new HashMap<Symbol, ArrayList<TransducerRule>>();
		rulesByIndex = new HashMap<Integer, TransducerRule>();
		rulesByTie = new HashMap<Integer, ArrayList<TransducerRule>>();
		// stateTreeRules not filled in until demand
		stateTreeRules = new HashMap();
		// one comma check 
		TransducerLeftTree.setCommaTrip();
		isCopyingSet = false;
		isDeletingSet = false;
		isExtendedSet = false;
		isLookaheadSet = false;
		isInEpsSet = false;
		isOutEpsSet = false;

	}
	public TransducerRuleSet(Semiring s) {
		this();
		semiring = s;
	}


	// copy constructor. no memoization is copied
	public TransducerRuleSet(TransducerRuleSet trs) {
		startState = trs.startState;
		rules = new ArrayList<TransducerRule>(trs.rules);
		states = new HashSet<Symbol>(trs.states);
		rulesByState = new HashMap<Symbol, ArrayList<TransducerRule>>(trs.rulesByState);
		rulesByIndex = new HashMap<Integer, TransducerRule>(trs.rulesByIndex);
		rulesByTie = new HashMap<Integer, ArrayList<TransducerRule>>(trs.rulesByTie);
		semiring = trs.semiring;
		nextRuleIndex = trs.nextRuleIndex;
		isCopyingSet = trs.isCopyingSet;
		isCopying = trs.isCopying;
		isDeletingSet = trs.isDeletingSet;
		isDeleting = trs.isDeleting;
		isExtendedSet = trs.isExtendedSet;
		isExtended = trs.isExtended;
		isLookaheadSet = trs.isLookaheadSet;
		isLookahead = trs.isLookahead;
		isInEpsSet = trs.isInEpsSet;
		isInEps = trs.isInEps;
		isOutEpsSet = trs.isOutEpsSet;
		isOutEps = trs.isOutEps;
	}

	abstract public String toString();
	// better than writing the whole thing to a single memory object
	public void print(OutputStreamWriter w) throws IOException {
		w.write(getStartState()+"\n");
		if (getRulesOfType(getStartState()) != null) {
			for (TransducerRule r : getRulesOfType(getStartState())) {
				if (semiring.betteroreq(semiring.ZERO(), r.getWeight()))
					continue;
				w.write(r.toString());
				// check if we have to print the tie
				if (r.getTie() > 0) {
					boolean seenMatch = false;
					for (TransducerRule tierule : getTiedRules(r.getTie())) {
						if (tierule != r && semiring.better(tierule.getWeight(), semiring.ZERO())) {
							seenMatch = true;
							break;
						}
					}
					if (seenMatch)
						w.write(" @ "+r.getTie());
				}
				w.write("\n");
			}
		}
		for (Symbol left : rulesByState.keySet()) {
			if (left.equals(getStartState()))
				continue;
			for (TransducerRule r : getRulesOfType(left)) {
				if (semiring.betteroreq(semiring.ZERO(), r.getWeight()))
					continue;
				w.write(r.toString());
				// check if we have to print the tie
				if (r.getTie() > 0) {
					boolean seenMatch = false;
					for (TransducerRule tierule : getTiedRules(r.getTie())) {
						if (tierule != r && semiring.better(tierule.getWeight(), semiring.ZERO())) {
							seenMatch = true;
							break;
						}
					}
					if (seenMatch)
						w.write(" @ "+r.getTie());
				}
				w.write("\n");
			}
		}
	}

	// accessors
	public Symbol getStartState() { return startState; }
	public Semiring getSemiring() { return semiring; }
	public HashSet<Symbol> getStates() { return states; }

	// useful for indexing rules by common state
	public ArrayList<TransducerRule> getRulesOfType(Symbol s) {
		if (rulesByState.containsKey(s))
			return rulesByState.get(s);	
		return new ArrayList<TransducerRule>();
	}

	// for each state in the state set, find at least one rule with the same lhs, and add all of these
	// together into a vector, so each vector should have exactly |stateset| elements
	// e.g. R1 = q.A-> ... R2 = q.A-> ... R3 = q.B -> R4 = r.A->
	// vectors added are <R1, R4>, <R2, R4>
	// do this by 1) sectioning all rules by state, lhs
	//            2) for each lhs, getting cross-product, pairwise

	// simultaneously figure out the sets of states led to by combining these rules
	public Vector<Vector<TransducerRule>> getRelevantRules(HashSet<Symbol> stateset, Vector<Vector<HashSet<Symbol>>> nextStates) {
		boolean debug = false;
		// index first by symbol, then by rank, then by state
		HashMap<Symbol, HashMap<Integer, HashMap<Symbol, Vector<TransducerRule>>>> chart = 
			new HashMap<Symbol, HashMap<Integer, HashMap<Symbol, Vector<TransducerRule>>>>();
		// input epsilon rules are indexed by state only and kept in a separate chart
		HashMap<Symbol, Vector<TransducerRule>> inEpsChart = new HashMap<Symbol, Vector<TransducerRule>>();
		// seed the chart
		for (Symbol state : stateset) {
			if (debug) Debug.debug(debug, "adding rules for "+state);
			ArrayList<TransducerRule> rots = getRulesOfType(state);
			if (rots == null)
				continue;
			for (TransducerRule rule : rots) {
				if (rule.getLHS().hasLabel()) {
					Symbol label = rule.getLHS().getLabel();
					Integer rank = new Integer(rule.getLHS().getNumChildren());
					if (!chart.containsKey(label))
						chart.put(label, new HashMap<Integer, HashMap<Symbol, Vector<TransducerRule>>>());
					if (!chart.get(label).containsKey(rank))
						chart.get(label).put(rank, new HashMap<Symbol, Vector<TransducerRule>>());
					if (!chart.get(label).get(rank).containsKey(state))
						chart.get(label).get(rank).put(state, new Vector<TransducerRule>());
					chart.get(label).get(rank).get(state).add(rule);
					if (debug) Debug.debug(debug, "Adding "+rule+" to "+label+"->"+rank+"->"+state);
				}
				// input epsilon case
				else {
					if (!inEpsChart.containsKey(state))
						inEpsChart.put(state, new Vector<TransducerRule>());
					inEpsChart.get(state).add(rule);
					if (debug) Debug.debug(debug, "Adding input epsilon rule "+rule+" to "+state);
				}
			}
		}
		// retrieve rules
		Vector<Vector<TransducerRule>> ret = new Vector<Vector<TransducerRule>>();

		// if epsilon rules exist, try to get just them
		// for loop to make breaking out easy
		if (inEpsChart.keySet().size() > 0) {
			for (int i = 0; i < 1; i++) {
				if (debug) Debug.debug(debug, "Considering epsilon rules");
				if (inEpsChart.keySet().size() < stateset.size()) {
					if (debug) Debug.debug(debug, "Not all states have input epsilon");
					break;
				}
				// cross-product the rules, adding in one more state at a time.
				Vector<Vector<TransducerRule>> crossprod = new Vector<Vector<TransducerRule>>();
				crossprod.add(new Vector<TransducerRule>());
				for (Symbol key : stateset) {
					if (debug) Debug.debug(debug, "About to build cross-product for "+key);
					crossprod = getCrossProduct(crossprod, null, inEpsChart.get(key));
				}
				if (crossprod != null) {
					if (debug) Debug.debug(debug, "Formed cross product "+crossprod);
					// get the state combinations and add them to the nextStates set
					for (Vector<TransducerRule> vec : crossprod) {
						Vector<HashSet<Symbol>> nsvec = new Vector<HashSet<Symbol>>();
						// no need to worry about a mix of input epsilon and non here.
						// add all rhs
						HashSet<Symbol> symset = new HashSet<Symbol>();
						for (TransducerRule rule : vec) {
							try {
								// if there's a deletion, add star
								if (rule.getTRVM().getRHSByVariable(rule.getTRVM().getVariableByIndex(0)).size() == 0) {
									Debug.debug(debug, rule+" has a deletion. Adding star");
									symset.add(Symbol.getStar());
								}
								else
									for (TransducerRightSide right : rule.getTRVM().getRHSByVariable(rule.getTRVM().getVariableByIndex(0)))
										symset.add(right.getState());
							}
							catch (UnusualConditionException e) {
								Debug.debug(true, "Unusual condition adding states of "+rule+": "+e.getMessage());
							}
						}
						if (debug) Debug.debug(debug, "Adding "+symset+" to nextStates");
						nsvec.add(symset);
						nextStates.add(nsvec);
					}
					ret.addAll(crossprod);
				}
			}
		}
		// label-based rules (though epsilons can apply here)
		for (Symbol label : chart.keySet()) {
			if (debug) Debug.debug(debug, "Considering rules starting with "+label);
			for (Integer rank : chart.get(label).keySet()) {
				if (debug) Debug.debug(debug, "and rank "+rank);
				if (chart.get(label).get(rank).keySet().size() < stateset.size()) {
					// check if the input-epsilon rules make up the difference
					if (inEpsChart.keySet().size() > 0) {
						HashSet<Symbol> setkeys = new HashSet<Symbol>(chart.get(label).get(rank).keySet());
						setkeys.addAll(inEpsChart.keySet());
						if (setkeys.size() < stateset.size()) {
							if (debug) Debug.debug(debug, "Not all states have rule or input epsilon");
							continue;
						}
					}
					else {
						if (debug) Debug.debug(debug, "Not all states have rule");
						continue;
					}
				}
				// cross-product the rules, adding in one more state at a time.
				Vector<Vector<TransducerRule>> crossprod = new Vector<Vector<TransducerRule>>();
				crossprod.add(new Vector<TransducerRule>());
				for (Symbol key : stateset) {
					if (debug) Debug.debug(debug, "About to build cross-product for "+key);
					crossprod = getCrossProduct(crossprod, chart.get(label).get(rank).get(key), inEpsChart.get(key));
				}
				if (crossprod != null) {
					if (debug) Debug.debug(debug, "Formed cross product "+crossprod);
					// get the state combinations and add them to the nextStates set
					for (Vector<TransducerRule> vec : crossprod) {

						// do we have a mix of input epsilon and non-input epsilon?
						boolean seenie = false;
						boolean seennonie = false;
						for (TransducerRule tr : vec) {
							if (tr.isInEps())
								seenie = true;
							else
								seennonie = true;
							if (seenie && seennonie)
								break;
						}
						Vector<HashSet<Symbol>> nsvec = new Vector<HashSet<Symbol>>();

						// If we've seen both input epsilon and non-input epsilon,
						// combine lhs states from non-input epsilon with rhs states from 
						// input epsilon

						if (seenie && seennonie) {
							HashSet<Symbol> symset = new HashSet<Symbol>();
							for (TransducerRule rule : vec) {
								// add all rhs states of epsilon rules 
								if (rule.isInEps()) {
									try {
										// if there's a deletion, add star
										if (rule.getTRVM().getRHSByVariable(rule.getTRVM().getVariableByIndex(0)).size() == 0) {
											Debug.debug(debug, rule+" has a deletion. Adding star");
											symset.add(Symbol.getStar());
										}
										else
											for (TransducerRightSide right : rule.getTRVM().getRHSByVariable(rule.getTRVM().getVariableByIndex(0)))
												symset.add(right.getState());
									}
									catch (UnusualConditionException e) {
										Debug.debug(true, "Unusual condition adding states of "+rule+": "+e.getMessage());
									}
								}
								// add lhs state of non-epsilon rules
								else {
									symset.add(rule.getState());
								}
							}
							if (debug) Debug.debug(debug, "Adding "+symset+" to nextStates");
							nsvec.add(symset);
						}
						// if we've seen only non-input epsilon,
						// combine rhs states together to find the next states.
						else if (seennonie) {			
							// union of all copying variables and all matching variables from paired rules
							for (int i = 0; i < rank.intValue(); i++) {
								HashSet<Symbol> symset = new HashSet<Symbol>();
								for (TransducerRule rule : vec) {
									// add all states on the rhs of variable i to symset
									try {
										// if there's a deletion, add star
										if (rule.getTRVM().getRHSByVariable(rule.getTRVM().getVariableByIndex(i)).size() == 0)
											symset.add(Symbol.getStar());
										else
											for (TransducerRightSide right : rule.getTRVM().getRHSByVariable(rule.getTRVM().getVariableByIndex(i)))
												symset.add(right.getState());
									}
									catch (UnusualConditionException e) {
										Debug.debug(true, "Unusual condition adding states of "+rule+" variable position "+i+": "+e.getMessage());
									}
								}
								if (debug) Debug.debug(debug, "Adding "+symset+" to nextStates");
								nsvec.add(symset);
							}
						}
						// if we've seen only ie, ignore these (they should have been covered above) and don't add 
						// the vector to the return value!
						else if (seenie) {
							if (debug) Debug.debug(debug, vec+" is a repeat element, so skipping it");
							continue;
						}
						nextStates.add(nsvec);
						ret.add(vec);
					}
					//		    ret.addAll(crossprod);
				}
			}
		}

		return ret;
	}

	// utility function for taking cross product of rules: 
	// A = ((1, 2) (4, 5)), B = (7, 8): A x B = ((1, 2, 7) (1, 2, 8) (4, 5, 7) (4, 5, 8))
	// Two B-sets to account for epsilon rules being stored separately from non-epsilon rules
	private static Vector<Vector<TransducerRule>> getCrossProduct(Vector<Vector<TransducerRule>> aset,
			Vector<TransducerRule> bset1,
			Vector<TransducerRule> bset2) {
		boolean debug = false;
		Vector<Vector<TransducerRule>> ret = new Vector<Vector<TransducerRule>>();
		for (Vector<TransducerRule> item : aset) {
			if (bset1 != null) {
				if (debug) Debug.debug(debug, "Inspecting normal rule set for cross product");
				for (TransducerRule rule : bset1) {
					if (debug) Debug.debug(debug, "Found "+rule);
					Vector<TransducerRule> newitem = new Vector<TransducerRule>(item);
					newitem.add(rule);
					ret.add(newitem);
				}
			}
			if (bset2 != null) {
				if (debug) Debug.debug(debug, "Inspecting epsilon rule set for cross product");
				for (TransducerRule rule : bset2) {
					if (debug) Debug.debug(debug, "Found "+rule);
					Vector<TransducerRule> newitem = new Vector<TransducerRule>(item);
					newitem.add(rule);
					ret.add(newitem);
				}
			}
		}
		return ret;
	}

	// useful if applicableSymbols table exists: check the tree to make sure all symbols are present
	boolean isApplicable(TreeItem t) {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Checking "+t);
		if (applicableSymbols == null) {
			if (debug) Debug.debug(debug, "Hashset is null");
			return true;
		}
		if (!applicableSymbols.contains(t.getLabel())) {
			if (debug) Debug.debug(debug, "label is not found");
			return false;
		}
		if (t.getNumChildren() == 0) {
			if (debug) Debug.debug(debug, "No more children");
			return true;
		}
		for (int i = 0; i < t.getNumChildren(); i++)
			if (!isApplicable(t.getChild(i)))
				return false;
		if (debug) Debug.debug(debug, "Done with "+t);
		return true;
	}


	// useful for indexing rules by common tie
	public ArrayList<TransducerRule> getTiedRules(int i) {
		return rulesByTie.get(i);
	}
	// the set of ties
	public Iterable<Integer> getTies() {
		//	Debug.debug(true, "RulesByTie has "+rulesByTie.keys().length+" keys");
		return rulesByTie.keySet();
	}

	public ArrayList<TransducerRule> getRules() {
		return rules;
	}
	public TransducerRule getRuleByIndex(int index) {
		return (TransducerRule)rulesByIndex.get(index);
	}
	// stats
	public int getNumStates() { return states.size(); }
	public int getNumRules() { return rules.size(); }

	public boolean isCopying() {
		if (isCopyingSet)
			return isCopying;
		isCopyingSet = true;
		isCopying = false;
		for (TransducerRule r : rules) {
			if (r.isCopying()) {
				isCopying = true;
				break;
			}
		}
		return isCopying;
	}
	public boolean isDeleting() {
		if (isDeletingSet)
			return isDeleting;
		isDeletingSet = true;
		isDeleting = false;
		for (TransducerRule r : rules) {
			if (r.isDeleting()) {
				isDeleting = true;
				break;
			}
		}
		return isDeleting;
	}
	public boolean isExtended() {
		if (isExtendedSet)
			return isExtended;
		isExtendedSet = true;
		isExtended = false;
		for (TransducerRule r : rules) {
			if (r.isExtended()) {
				isExtended = true;
				break;
			}
		}
		return isExtended;
	}
	public boolean isLookahead() {
		if (isLookaheadSet)
			return isLookahead;
		isLookaheadSet = true;
		isLookahead = false;
		for (TransducerRule r : rules) {
			if (r.isLookahead()) {
				isLookahead = true;
				break;
			}
		}
		return isLookahead;
	}
	
	public boolean isInEps() {
		if (isInEpsSet)
			return isInEps;
		isInEpsSet = true;
		isInEps = false;
		for (TransducerRule r : rules) {
			if (r.isInEps()) {
				isInEps = true;
				break;
			}
		}
		return isInEps;
	}

	public boolean isOutEps() {
		if (isOutEpsSet)
			return isOutEps;
		isOutEpsSet = true;
		isOutEps = false;
		for (TransducerRule r : rules) {
			if (r.isOutEps()) {
				isOutEps = true;
				break;
			}
		}
		return isOutEps;
	}


	// gets grammar rules for forward application of a tree through this transducer
	// better version - returns stps that could be now viable. adds to rules and states inline.
	// takes care of identity rules from application/composition so we don't have to add all of them
	// all at once.
	public ArrayList<Rule> getForwardGrammarRules(RuleSet rs, 
										   StateTreePair stp, 
										   ArrayList<StateTreePair> stateSet, 
										   Set<Symbol> varSymSet, 
										   HashMap<Symbol, StateTreePair> varSymSTP, 
										   HashMap<StateTreePair, Symbol> STPvarSym, 
										   boolean epsAllowed) {
		boolean debug = false;
		ArrayList<Rule> rh = new ArrayList<Rule>();
		// if the tree is just a variable, treat it specially with a "virtual" rule
		if (stp.getTree().numChildren == 0 && varSymSet.contains(stp.getTree().label)) {
			if (debug) Debug.debug(debug, "Saw special variable "+stp.getTree()+"; getting rtg rules from virtual transducer rule");
			if (!STPvarSym.containsKey(stp)) {
				Symbol dstsym = SymbolFactory.getSymbol(stp.getTree().label+"_"+stp.getState());
				varSymSTP.put(dstsym, stp);
				STPvarSym.put(stp, dstsym);
			}
			if (this instanceof TreeTransducerRuleSet) {
				rh.add(new RTGRule((RTGRuleSet)rs, stp.getSymbol(), STPvarSym.get(stp), semiring.ONE(), semiring));
			}
			else {
				rh.add(new CFGRule((CFGRuleSet)rs, stp.getSymbol(), new StringItem(STPvarSym.get(stp)), semiring.ONE(), semiring));
			}			
		}
		else {
			ArrayList<TransducerRule> currRules = getRulesOfType(stp.getState());
			if (currRules == null)
				return rh;
			for (TransducerRule r : currRules) {
				if (!epsAllowed && r.isInEps()) {
					if (debug) Debug.debug(debug, "Skipping "+r+": input epsilon prohibited");
					continue;
				}
				if (debug) Debug.debug(debug, "Checking rule "+r+" with state pair "+stp.toInternalString());
				// the actual stps
				HashSet<StateTreePair> newstates = new HashSet<StateTreePair>();
				Rule newRule = r.getForwardGrammarRule(rs, stp, newstates);


				if (newRule != null) {
					if (debug) Debug.debug(debug, "Adding grammar rule "+newRule.toString()+" to list with "+rh.size()+" members");
					rh.add(newRule);
					if (debug) Debug.debug(debug, "Getting grammar states for "+stp.toInternalString());
					if (debug) Debug.debug(debug, "About to try to add new states");
					if (newstates != null) {
						if (debug) Debug.debug(debug, "Adding "+newstates.size()+" states");
						stateSet.addAll(newstates);
					}		
					else {
						if (debug) Debug.debug(debug, "No new states");			
					}
				}

			}
		}
		return rh;

	}

	
	
	// gets grammar rules for forward application of a tree through this transducer
	// better version - returns stps that could be now viable. adds to rules and states inline.
	// takes care of identity rules from application/composition so we don't have to add all of them
	// all at once.
	public ArrayList<Rule> getForwardGrammarRules(RuleSet rs, 
										   StateTreePair stp, 
										   ArrayList<StateTreePair> stateSet,
										   boolean epsAllowed) {
		boolean debug = false;
		ArrayList<Rule> rh = new ArrayList<Rule>();
		// if the tree is just a variable, treat it specially with a "virtual" rule
		if (stp.getTree().numChildren == 0 && stp.getTree().label instanceof VecSymbol) {
			if (debug) Debug.debug(debug, "Saw special variable "+stp.getTree()+"; getting rtg rules from virtual transducer rule");

			VecSymbol dstsym = SymbolFactory.getVecSymbol((VecSymbol)stp.getTree().label, stp.getState());
			if (this instanceof TreeTransducerRuleSet) {
				rh.add(new RTGRule((RTGRuleSet)rs, stp.getSymbol(), dstsym, semiring.ONE(), semiring));
			}
			else {
				rh.add(new CFGRule((CFGRuleSet)rs, stp.getSymbol(), new StringItem(dstsym), semiring.ONE(), semiring));
			}			
		}
		else {
			ArrayList<TransducerRule> currRules = getRulesOfType(stp.getState());
			if (currRules == null)
				return rh;
			for (TransducerRule r : currRules) {
				
				if (!epsAllowed && r.isInEps()) {
					if (debug) Debug.debug(debug, "Skipping "+r+": input epsilon prohibited");
					continue;
				}
				if (debug) Debug.debug(debug, "Checking rule "+r+" with state pair "+stp.toInternalString());
				// the actual stps. also get new rules
				HashSet<StateTreePair> newstates = new HashSet<StateTreePair>();
				Rule newRule = r.getForwardGrammarRule(rs, stp, newstates);


				if (newRule != null) {
					if (debug) Debug.debug(debug, "Adding grammar rule "+newRule.toString()+" to list with "+rh.size()+" members");
					rh.add(newRule);
					if (debug) Debug.debug(debug, "Getting grammar states for "+stp.toInternalString());
					if (debug) Debug.debug(debug, "About to try to add new states");
					if (newstates != null) {
						if (debug) Debug.debug(debug, "Adding "+newstates.size()+" states");
						stateSet.addAll(newstates);
					}		
					else {
						if (debug) Debug.debug(debug, "No new states");			
					}
				}

			}
		}
		return rh;

	}

	// for getting graehl-style derivation trees, but also useful for serialization
	public int getNextRuleIndex() {
		return nextRuleIndex++;
	}

	// for creating new states when transforming to a non-extended corrupting version
	public int getNextNewState() {
		return nextNewState++;
	}

	// initialization of lookup tables is done in constructors, but this re-sets them when rules and states change
	// TODO: treetransducerruleset has a memoized piece that must be reset!!
	protected void reinitialize() {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Reinitializing with "+rules.size()+" rules");
		int reinitNum = rules.size();
		states = new HashSet<Symbol>(reinitNum);
		rulesByState = new HashMap<Symbol, ArrayList<TransducerRule>>(reinitNum);
		rulesByIndex = new HashMap<Integer, TransducerRule>(reinitNum);
		rulesByTie = new HashMap<Integer, ArrayList<TransducerRule>>(reinitNum);
		states.add(startState);
		for (TransducerRule r : rules) {
			states.add(r.getState());
			if (!rulesByState.containsKey(r.getState()))
				rulesByState.put(r.getState(), new ArrayList<TransducerRule>());
			rulesByState.get(r.getState()).add(r);
			if (!rulesByTie.containsKey(r.getTie()))
				rulesByTie.put(r.getTie(), new ArrayList());
			((ArrayList)rulesByTie.get(r.getTie())).add(r);

			rulesByIndex.put(r.getIndex(), r);
		}
		isCopyingSet = false;
		isDeletingSet = false;
		isExtendedSet = false;
		isLookaheadSet = false;
		isInEpsSet = false;
		isOutEpsSet = false;

	}

	// domain-preserving, range-corrupting transformation that makes an extended transducer non-extended
	// do this by turning each extended rule into potentially many rules to break down the extension

	// creating those rules inserts tree elements into the rhs and changes variable order so the range
	// should not be used. However, nothing stops that from happening
	public void makeNonExtended() {
		boolean debug = false;
		if (!isExtended())
			return;
		ArrayList<TransducerRule> newrules = new ArrayList<TransducerRule>();
		for (TransducerRule r : rules) {
			if (r.isExtended()) {
				if (debug) Debug.debug(debug, "About to unextend "+r);
				r.makeNonExtended(newrules);
				if (debug) Debug.debug(debug, "We now have "+newrules.size()+" new rules");
			}
			else {
				if (debug) Debug.debug(debug, "Not unextending "+r);
				newrules.add(r);
				if (debug) Debug.debug(debug, "We now have "+newrules.size()+" new rules");
			}
		}
		rules = newrules;
		reinitialize();
	}

	// remove lookahead rules and replace with equivalent rules that lead to states
	// that separate by lookahead symbol
	public void makeNonLookahead() {
		boolean debug = false;
		if (!isLookahead())
			return;
		ArrayList<TransducerRule> newrules = new ArrayList<TransducerRule>();
		HashMap<Symbol, HashMap<Symbol, Symbol>> nonLookaheadMap = new HashMap<Symbol, HashMap<Symbol, Symbol>>();
		for (TransducerRule r : rules) {
			if (r.isLookahead()) {
				if (debug) Debug.debug(debug, "About to remove lookahead from "+r);
				if (r.isCopying()) {
					int x = 0;
					x++;
				}
				if (r.makeNonLookahead(newrules, nonLookaheadMap)) {
					if (debug) Debug.debug(debug, "Success!");
				}
				else
					if (debug) Debug.debug(debug, "Fail!");
				if (debug) Debug.debug(debug, "We now have "+newrules.size()+" new rules");
			}
			else {
				if (debug) Debug.debug(debug, "Not unlookaheading "+r);
				newrules.add(r);
				if (debug) Debug.debug(debug, "We now have "+newrules.size()+" new rules");
			}
		}
		rules = newrules;
		
		reinitialize();
		pruneUseless();
	}
	
	
	// the -n flag in carmel: normalize weights
	// for trees, normalization groups in JOINT mode are based on the state, left symbol, not just state, and not whole left side
	// in CONDITIONAL mode they are based on whole left side!!
	public void normalizeWeights() {
		boolean debug = false;
		isCopyingSet = false;
		isDeletingSet = false;
		isExtendedSet = false;
		isLookaheadSet = false;
		isInEpsSet = false;
		isOutEpsSet = false;

		Debug.debug(debug, "WARNING: haven't changed normalizeWeights w/r/t tied rules!");
		// as we iterate over all the rules
		// calculate the weight for each rule starting with a unique state, symbol
		TObjectDoubleHashMap map = new TObjectDoubleHashMap();
		for (TransducerRule r : rules) {
			Symbol sym;
			if (TransducerTraining.ISCONDITIONAL)
				sym = r.getLHSCondSym();
			else
				sym = r.getLHSSym();
			if (map.containsKey(sym))
				map.put(sym, semiring.plus(map.get(sym), r.getWeight()));
			else
				map.put(sym, r.getWeight());
		}
		// now normalize
		for (TransducerRule r : rules) {
			Symbol sym;
			if (TransducerTraining.ISCONDITIONAL)
				sym = r.getLHSCondSym();
			else
				sym = r.getLHSSym();
			r.setWeight(semiring.times(r.getWeight(), semiring.inverse(map.get(sym))));
		}
	}

	// mostly useful for training, this method chooses a random probability between 0.2 and 0.8 for each rule
	public void randomizeRuleWeights() {
		for (TransducerRule r : rules) {
			double val = 0.2+ (Math.random()*0.6);
			r.setWeight(semiring.convertFromReal(val));
		}
	}

	// prune useless - two-pass algorithm, based on knuth. First go bottom up and mark all states
	// that can be used to reach terminal symbols. Then go top-down and mark all states that can
	// be reached from the start symbol. Only keep states that satisfy both criteria.

	// This is a "strong" prune useless, in that a rule with weight of semiring.ZERO is considered
	// not to exist.

	public void pruneUseless() {
		boolean debug = false;
		isCopyingSet = false;
		isDeletingSet = false;
		isExtendedSet = false;
		isLookaheadSet = false;
		isInEpsSet = false;
		isOutEpsSet = false;

		Debug.debug(debug, "Pruning useless from transducer");
		HashSet bottomReachable = new HashSet();
		// phase 1: bottom up
		// for each state not already reachable, try and add it

		int brSize = 0;
		do {
			brSize = bottomReachable.size();
			for (Symbol currState : states) {
				if (debug) Debug.debug(debug, "BU: Considering state "+currState.toString());
				if (currState == Symbol.getStar())
					continue;
				if (bottomReachable.contains(currState))
					continue;
				ArrayList<TransducerRule> currRules = getRulesOfType(currState);
				if (currRules == null)
					continue;
				if (debug) Debug.debug(debug, "\t"+currRules.size()+" rules");
				// look for at least one valid rule
				for (TransducerRule currRule : currRules) {
					if (debug) Debug.debug(debug, "\t\t"+currRule.toString());
					// sometimes this is run when ruleset is in an uncertain state. trust rules list above rulesoftype
					// list
					// DISABLED because it is very slow...and probably unnecessary

					// 		    if (!rules.contains(currRule)) {
					// 			if (debug) Debug.debug(debug, "\t\t Not okay -- Not in true rule set!");
					// 			continue;
					// 		    }

					// rule doesn't count if it has zero weight
					if (semiring.betteroreq(semiring.ZERO(), currRule.getWeight())) {
						continue;
					}
					Symbol[] leaves = currRule.getRHSLeaves();
					boolean isOkay = true;
					// check that all leaves are either terms, star, or already seen
					for (int i = 0; i < leaves.length; i++) {
						if (states.contains(leaves[i]) && leaves[i] != Symbol.getStar() && !bottomReachable.contains(leaves[i])) {
							if (debug) Debug.debug(debug, "\t\t Not okay because of "+leaves[i].toString());
							isOkay = false;
							break;
						}
					}
					if (isOkay) {
						if (debug) Debug.debug(debug, "\t\t Okay!");
						if (debug) Debug.debug(debug, "\t\t thanks to "+currRule.toString());
						bottomReachable.add(currRule.getState());
						break;
					}
				}
			}
			if (debug) Debug.debug(debug, "Gone from "+brSize+" to "+bottomReachable.size());
		} while (brSize < bottomReachable.size());

		// phase 2: top down
		// starting with the start state (if it's bottom-reachable), 
		// find each state that can be reached in a downward direction
		// more specifically, find each rule that applies
		ArrayList<TransducerRule> checkedRules = new ArrayList<TransducerRule>();
		HashSet checkedStates = new HashSet();
		Stack readyStates = new Stack();
		if (bottomReachable.contains(startState))
			readyStates.push(startState);
		while (readyStates.size() > 0) {
			Symbol currState = (Symbol)readyStates.pop();
			if (debug) Debug.debug(debug, "TD: "+currState.toString());
			if (currState == Symbol.getStar())
				continue;
			checkedStates.add(currState);
			ArrayList<TransducerRule> currRules = getRulesOfType(currState);
			if (currRules == null) 
				continue;
			if (debug) Debug.debug(debug, "\t"+currRules.size()+" rules");
			// look for at least one valid rule
			for (TransducerRule currRule : currRules) {
				if (debug) Debug.debug(debug, "\t\t"+currRule.toString());
				// sometimes this is run when ruleset is in an uncertain state. trust rules list above rulesoftype
				// list
				// DISABLED because it is very slow...and probably unnecessary
				// 		if (!rules.contains(currRule)) {
				// 		    if (debug) Debug.debug(debug, "\t\t Not okay -- Not in true rule set!");
				// 		    continue;
				// 		}

				// rule doesn't count if it has zero weight
				if (semiring.betteroreq(semiring.ZERO(), currRule.getWeight())) {
					if (debug) Debug.debug(debug, "\t\t Not okay -- Weight is zero!");
					continue;
				}
				//		if (debug) Debug.debug(debug, "TD: Getting RHS leaves of "+currRule.toString());
				Symbol[] leaves = currRule.getRHSLeaves();
				boolean isOkay = true;
				// check that all leaves are either terms, star, or already seen
				for (int i = 0; i < leaves.length; i++) {
					if (states.contains(leaves[i]) && leaves[i] != Symbol.getStar() && !bottomReachable.contains(leaves[i])) {
						if (debug) Debug.debug(debug, "\t\t Not okay -- Leaf "+i+", "+leaves[i].toString()+" not reachable!");
						isOkay = false;
						break;
					}
				}
				// valid rules inspire other states to check
				if (isOkay) {
					if (debug) Debug.debug(debug, "\t\t Okay! Adding rule "+currRule.toString()+" which has non-zero weight "+currRule.getWeight());
					checkedRules.add(currRule);
					for (int i = 0; i < leaves.length; i++) {
						if (states.contains(leaves[i]) && 
								leaves[i] != Symbol.getStar() &&
								!checkedStates.contains(leaves[i]) &&
								!readyStates.contains(leaves[i])) {
							readyStates.push(leaves[i]);
						}
					}
				}
			}
		}
		if (debug) Debug.debug(debug, "New transducer has "+checkedRules.size()+" rules");
		rules = checkedRules;
		reinitialize();
	}


	// generate star rules for domain projection: for each symbol and rank in the input rules, create 
	// weight-1 star rule
	public Vector<RTGRule> generateStarRules(RTGRuleSet master) {
		HashMap<Symbol, HashSet<Integer>> vocab = new HashMap<Symbol, HashSet<Integer>>();
		Vector<TransducerLeftTree> treesLeft = new Vector<TransducerLeftTree>();
		for (TransducerRule r : rules)
			treesLeft.add(r.getLHS());
		while (treesLeft.size() > 0) {
			TransducerLeftTree tree = treesLeft.remove(0);
			if (tree.hasLabel() && !tree.hasVariable()) {
				Symbol label = tree.getLabel();
				int rank = tree.getNumChildren();
				if (!vocab.containsKey(label))
					vocab.put(label, new HashSet<Integer>());
				if (!vocab.get(label).contains(rank))
					vocab.get(label).add(rank);
				for (int i = 0; i < rank; i++)
					treesLeft.add(tree.getChild(i));
			}
		}
		Vector<RTGRule> starRules = new Vector<RTGRule>();
		for (Symbol label : vocab.keySet()) {
			for (int rank : vocab.get(label)) {
				if (rank > 0) {
					Vector<Symbol> starvec = new Vector<Symbol>();
					for (int i = 0; i < rank; i++)
						starvec.add(Symbol.getStar());
					TreeItem rhs = new TreeItem(label, starvec);
					starRules.add(new RTGRule(master, Symbol.getStar(), rhs, getSemiring().ONE(), getSemiring()));
				}
				else {
					starRules.add(new RTGRule(master, Symbol.getStar(), label, getSemiring().ONE(), getSemiring()));
				}
			}
		}
		return starRules;
	}
	
	// add a set of rules that allow transduction of the symbol set. Only add rules for symbols not yet added
	// map the created symbols back to the lhs so I can recall them later

	private HashSet<Symbol> usedIdentityRules = new HashSet<Symbol>();

	void addIdentityRules(Set<Symbol> set, HashMap<Symbol, TransducerRule> map) {
		boolean debug = false;
		for (Symbol sym : set) {
			if (usedIdentityRules.contains(sym))
				continue;
			usedIdentityRules.add(sym);
			if (applicableSymbols != null)
				applicableSymbols.add(sym);
			for (Symbol st : states) {
				if (st == Symbol.getStar())
					continue;
				Symbol dstsym = SymbolFactory.getSymbol(sym.toString()+"_"+st.toString());
				TransducerRule newrule = null;
				if (this instanceof TreeTransducerRuleSet) {
					newrule = new TreeTransducerRule(st, new TransducerLeftTree(sym), 
							new TransducerRightTree(dstsym), 
							new TransducerRuleVariableMap(),
							semiring.ONE(), semiring, (TreeTransducerRuleSet)this);
					map.put(((TreeTransducerRule)newrule).getRHS().getLabel(), newrule);
				}
				else {
					newrule = new StringTransducerRule(st, new TransducerLeftTree(sym), 
							new TransducerRightString(dstsym), 
							new TransducerRuleVariableMap(),
							semiring.ONE(), semiring, (StringTransducerRuleSet)this);
					map.put(((StringTransducerRule)newrule).getRHS().getLabel(), newrule);
				}

				if (debug) Debug.debug(debug, "Adding identity rule "+newrule);
				rules.add(newrule);
				if (!rulesByState.containsKey(newrule.getState()))
					rulesByState.put(newrule.getState(), new ArrayList<TransducerRule>());
				rulesByState.get(newrule.getState()).add(newrule);
				if (!rulesByTie.containsKey(newrule.getTie()))
					rulesByTie.put(newrule.getTie(), new ArrayList<TransducerRule>());
				((ArrayList<TransducerRule>)rulesByTie.get(newrule.getTie())).add(newrule);

				rulesByIndex.put(newrule.getIndex(), newrule);	    
			}
		}
	}



}
