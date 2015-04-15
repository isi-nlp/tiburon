package edu.isi.tiburon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import edu.stanford.nlp.util.FixedPrioritiesPriorityQueue;


// tree transducer rule set that only has what we need for otf operations
// rules indexed by state,symbol,rank
// (LT) rules indexed by left state,symbol,rank
// (general forward) rules indexed by state, input variable position (x number), symbol, rank

public class OTFTreeTransducerRuleSet implements BinaryTreeTransducer {
	private Symbol startState;
		// filtered backward
	private PMap<Symbol, PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>>> labelbrules;
	// filtered backward by lhs label/rank AND THEN rhs label/rank
//	private PMap<Symbol, PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>>>>> labelbrulesByRHS;
	private PMap<Symbol, PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>>>>> labelbrulesByRHS;
	
	// unfiltered backward
	private PMap<Symbol, Vector<TreeTransducerRule>> brules;
	// initially unfiltered backward, then filtered by initial rhs label/rank
	private PMap<Symbol, PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>>> brulesByRHS;

	// all rhs terminal
	private Vector<TreeTransducerRule> allrelpostrules;

	// rhs terminal by symbol
	private PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>> relpostrules;

	// all lhs terminal
	private Vector<TreeTransducerRule> alltrules;

	// lhs terminal by symbol
	private PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>> trules;
	// relative position f-rules
	private PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>> allrelposfrules;
	private PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>>>> relposfrules;

	// f-rules by lhs position
	private PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>> allfrules;
	private PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>>>> frules;
	// f-rules by entire rhs string vector
	// TODO: make VecSymbol more efficient!
	private PMap<VecSymbol, PMap<Symbol,Vector<TreeTransducerRule>>> oldWholeFrules;
	// tree structure indexed by label and then, for each child level, state.
	private PMap<Symbol, Pair<Vector<TreeTransducerRule>, PMap<Symbol, Pair>>> wholeFrules;
	
	
	public Symbol getStartState() { return startState; }
	
	// validate start state
	public boolean isStartState(Symbol s) {
		return startState.equals(s);
	}
	
	private static Vector<TreeTransducerRule> empty;
	static {
		empty = new Vector<TreeTransducerRule>();
	}
	public Iterable<TreeTransducerRule> getBackwardRules(Symbol state) {
		if (brules.containsKey(state))
			return brules.get(state);
		return empty;
	}
	public Iterable<TreeTransducerRule> getBackwardRulesByRHS(Symbol state, Symbol rhslabel, int rank) {
		if (brulesByRHS.goc(state).goc(rhslabel).containsKey(rank))
			return brulesByRHS.get(state).get(rhslabel).get(rank);
		return empty;
	}
	public Iterable<TreeTransducerRule> getBackwardRules(Symbol state, Symbol label, int rank) {
		if (labelbrules.goc(state).goc(label).containsKey(rank))
			return labelbrules.get(state).get(label).get(rank);
		return empty;
	}
	public Iterable<TreeTransducerRule> getBackwardRulesByRHS(
			Symbol state, 
			Symbol lhslabel, int lhsrank,
			Symbol rhslabel, int rhsrank) {
		if (labelbrulesByRHS.goc(state).goc(lhslabel).goc(lhsrank).goc(rhslabel).containsKey(rhsrank))
			return labelbrulesByRHS.get(state).get(lhslabel).get(lhsrank).get(rhslabel).get(rhsrank);
		return empty;
	}

	// forward (rhs) lexical rules with no index
	public Iterable<TreeTransducerRule> getRelPosLexRules() { 
		boolean debug = false;
		if (debug) Debug.debug(debug, "Lex rules are "+allrelpostrules);
		return allrelpostrules;
	}

	// forward (rhs) lexical rules indexed by label
	public Iterable<TreeTransducerRule> getRelPosLexRules(Symbol s, int i) {
		if(relpostrules.containsKey(s) && relpostrules.get(s).containsKey(i))
			return relpostrules.get(s).get(i);
		return empty;
	}
	
	// backward (lhs) lexical rules with no index
	public Iterable<TreeTransducerRule> getLexRules() { 
		boolean debug = false;
		if (debug) Debug.debug(debug, "Lex rules are "+alltrules);
		return alltrules;
	}

	// forward lexical rules indexed by label
	public Iterable<TreeTransducerRule> getLexRules(Symbol s, int i) {
		if(trules.containsKey(s) && trules.get(s).containsKey(i))
			return trules.get(s).get(i);
		return empty;
	}

	// forward rules, indexed by state and position relative to its parent in the RHS
	public Iterable<TreeTransducerRule> getRelPosForwardRules(Symbol state, int pos) {
		boolean debug = false;
		if (allrelposfrules.goc(state).containsKey(pos)) {
			if (debug) Debug.debug(debug, "Returning "+allrelposfrules.get(state).get(pos).size()+" rules"+" for "+state+"->"+pos);
			return allrelposfrules.get(state).get(pos);
		}
		if (debug) Debug.debug(debug, "No transducer rules with state "+state+" in pos "+pos);

		return empty;
	}
	// forward rules, indexed by state, position relative to its parent in the RHS,
	// parent label, and rank
	public Iterable<TreeTransducerRule> getRelPosForwardRules(Symbol state, int pos, Symbol label, int rank) {
		if (relposfrules.goc(state).goc(pos).goc(label).containsKey(rank))
			return relposfrules.get(state).get(pos).get(label).get(rank);
		return empty;
	}
	// boolean check that this state is in left position with this label
	public boolean hasLeftStateRules(Symbol state, Symbol label) {
		return (relposfrules.containsKey(state) &&
				relposfrules.get(state).containsKey(0) &&
				relposfrules.get(state).get(0).containsKey(label) &&
				relposfrules.get(state).get(0).get(label).containsKey(2));
	}
	// boolean check that this state is in right position with this label
	public boolean hasRightStateRules(Symbol state, Symbol label) {
		return (relposfrules.containsKey(state) &&
				relposfrules.get(state).containsKey(1) &&
				relposfrules.get(state).get(1).containsKey(label) &&
				relposfrules.get(state).get(1).get(label).containsKey(2));
	}
	
	
	// forward rules, indexed by lhs state and position 
	public Iterable<TreeTransducerRule> getForwardRules(Symbol state, int pos) {
		boolean debug = false;
		if (allfrules.goc(state).containsKey(pos)) {
			if (debug) Debug.debug(debug, "Returning "+allfrules.get(state).get(pos).size()+" rules"+" for "+state+"->"+pos);
			return allfrules.get(state).get(pos);
		}
		if (debug) Debug.debug(debug, "No transducer rules with state "+state+" in pos "+pos);

		return empty;
	}
	// forward rules, indexed by state, position relative to its parent in the RHS,
	// parent label, and rank
	public Iterable<TreeTransducerRule> getForwardRules(Symbol state, int pos, Symbol label, int rank) {
		if (frules.goc(state).goc(pos).goc(label).containsKey(rank))
			return frules.get(state).get(pos).get(label).get(rank);
		return empty;
	}

//	// forward rules, indexed by rhs state sequence and rhs (single) label
//	public Iterable<TreeTransducerRule> getForwardRules(VecSymbol vs, Symbol label) { 
//		if(oldWholeFrules.goc(vs).containsKey(label))
//			return oldWholeFrules.get(vs).get(label);
//		nullreqCount++;
//		boolean debug = false;
//		if (debug && nullreqCount % 1000 == 0)
//			Debug.debug(debug, "Made "+nullreqCount+" empty requests");
//		return empty;
//	}
	
	// forward rules, indexed by rhs state sequence and rhs (single) label
	// use nested structure of symbols
	public Iterable<TreeTransducerRule> getForwardRules(Vector<Symbol> vs, Symbol label) { 
		if (!wholeFrules.containsKey(label))
			return empty;
		Pair<Vector<TreeTransducerRule>, PMap<Symbol, Pair>> currPair = wholeFrules.get(label);
		int currChild = 0;
		while (currChild < vs.size()) {
			if (!currPair.r().containsKey(vs.get(currChild)))
				return empty;
			currPair = currPair.r().get(vs.get(currChild++));
		}
		return currPair.l();
	}

	
	private int nullreqCount;
	public void getNullCount() {
		Debug.debug(true, "Made "+nullreqCount+" empty requests");
	}
	public OTFTreeTransducerRuleSet(TreeTransducerRuleSet trans) throws UnusualConditionException {
		startState = trans.getStartState();
		nullreqCount = 0;
		boolean debug = false;
		labelbrules = new PMap<Symbol, PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>>>();
		brules = new PMap<Symbol, Vector<TreeTransducerRule>>();
		brulesByRHS = new PMap<Symbol, PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>>>();
		//labelbrulesByRHS = new PMap<Symbol, PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>>>>> ();
		labelbrulesByRHS = new PMap<Symbol, PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>>>>> ();

		
		allrelposfrules = new PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>> ();
		relposfrules = new PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>>>> ();
		allfrules = new  PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>> ();
		frules = new PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>>>> ();

		relpostrules = new PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>>();
		trules = new PMap<Symbol, PMap<Integer, Vector<TreeTransducerRule>>>();
		oldWholeFrules = new PMap<VecSymbol, PMap<Symbol, Vector<TreeTransducerRule>>> ();
		wholeFrules = new PMap<Symbol, Pair<Vector<TreeTransducerRule>, PMap<Symbol, Pair>>> ();
		
		PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>> sortingRelpostrules = new PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>>();
		FixedPrioritiesPriorityQueue<TreeTransducerRule> sortingAllrelpostrules = new FixedPrioritiesPriorityQueue<TreeTransducerRule>();
		PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>> sortingTrules = new PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>>();
		FixedPrioritiesPriorityQueue<TreeTransducerRule> sortingAlltrules = new FixedPrioritiesPriorityQueue<TreeTransducerRule>();
	
		
		PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>> sortingAllrelposfrules 
		= new PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>> ();
		PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>>>> sortingRelposfrules 
		= new PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>>>> ();

		PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>> sortingAllfrules 
		= new PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>> ();
		PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>>>> sortingFrules 
		= new PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>>>> ();
		
		PMap<Symbol, FixedPrioritiesPriorityQueue<TreeTransducerRule>> sortingWholeFrules = new PMap<Symbol, FixedPrioritiesPriorityQueue<TreeTransducerRule>>();
//		PMap<VecSymbol, PMap<Symbol, FixedPrioritiesPriorityQueue<TreeTransducerRule>>> sortingWholeFrules 
//		= new PMap<VecSymbol, PMap<Symbol, FixedPrioritiesPriorityQueue<TreeTransducerRule>>>();

		
		
		
		for (Symbol state : trans.getStates()) {
			PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>> sortingLabelBsrules = new PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>>();
			PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>>>> sortingLabelBsrulesByRHS = new PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>>>>();
			FixedPrioritiesPriorityQueue<TreeTransducerRule> sortingBsrules = new FixedPrioritiesPriorityQueue<TreeTransducerRule>();
			PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>> sortingBsrulesByRHS = new PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>>();

			for (TransducerRule rawr : trans.getRulesOfType(state)) {
				TreeTransducerRule r = (TreeTransducerRule)rawr;
				Symbol s = r.isInEps() ? Symbol.getEpsilon() : r.getLHS().getLabel();
				int rank = r.isInEps() ? 1 : r.getLHS().getNumChildren();

				// add to backward rule map

				if (!sortingLabelBsrules.goc(s).containsKey(rank)) {
					sortingLabelBsrules.get(s).put(rank, new FixedPrioritiesPriorityQueue<TreeTransducerRule>());
		//			sortingLabelBsrulesByRHS.get(s).put(rank, new HashMap<Symbol, HashMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>>());
		//			labelbrulesByRHS.get(state).get(s).put(rank, new HashMap<Symbol, HashMap<Integer, Vector<TreeTransducerRule>>>());

				}
				sortingLabelBsrules.get(s).get(rank).add(r, -r.getWeight());
				sortingBsrules.add(r, -r.getWeight());
				// add to lhs lex map
				if (rank == 0)
					sortingAlltrules.add(r, -r.getWeight());
				if (!sortingTrules.goc(s).containsKey(rank))
					sortingTrules.get(s).put(rank, new FixedPrioritiesPriorityQueue<TreeTransducerRule>());
				sortingTrules.get(s).get(rank).add(r, -r.getWeight());
				
				// rhs rank: must add indices for byRHS vectors AND queues
				Symbol rhsSym = r.isOutEps() ? Symbol.getEpsilon() : r.getRHS().getLabel();
				int rhsRank = r.isOutEps() ? 1 : r.getRHS().getNumChildren();

//				if (!sortingLabelBsrulesByRHS.get(s).get(rank).containsKey(rhsSym)) {
//					sortingLabelBsrulesByRHS.get(s).get(rank).put(rhsSym, new HashMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>());
//					//labelbrulesByRHS.get(state).get(s).get(rank).put(rhsSym, new HashMap<Integer, Vector<TreeTransducerRule>>());
//
//				}
				if (!sortingLabelBsrulesByRHS.goc(s).goc(rank).goc(rhsSym).containsKey(rhsRank)) {
					sortingLabelBsrulesByRHS.get(s).get(rank).get(rhsSym).put(rhsRank, new FixedPrioritiesPriorityQueue<TreeTransducerRule>());
				}
//				if (!sortingBsrulesByRHS.containsKey(rhsSym)) {
//					brulesByRHS.get(state).put(rhsSym, new HashMap<Integer, Vector<TreeTransducerRule>>());
//					sortingBsrulesByRHS.put(rhsSym, new HashMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>>());
//				}
				if (!sortingBsrulesByRHS.goc(rhsSym).containsKey(rhsRank)) {
					sortingBsrulesByRHS.get(rhsSym).put(rhsRank, new FixedPrioritiesPriorityQueue<TreeTransducerRule>());
				}
				sortingLabelBsrulesByRHS.get(s).get(rank).get(rhsSym).get(rhsRank).add(r, -r.getWeight());
				sortingBsrulesByRHS.get(rhsSym).get(rhsRank).add(r, -r.getWeight());


				// check all nodes in rhs. store based on internal and lexical terminals in relpostrules
				// store based on variable terminals in frules
				Vector<TransducerRightTree> tq = new Vector<TransducerRightTree>();
				tq.add(r.getRHS());
				while (!tq.isEmpty()) {
					TransducerRightTree node = tq.remove(0);
					int localRank = node.getNumChildren();
					for (int i = 0; i < localRank; i++)
						tq.add(node.getChild(i));
					if (node.hasVariable()) {
						// for relPos (rhs) storing
						TransducerRightTree parent = node.parent;
						Symbol currst = node.getState();
						int c = -1;
						Symbol psym = null;
						int prank = 0;
						// eps case
						if (parent == null) {
							c = 0;
							psym = Symbol.getEpsilon();
							prank = 1;
						}
						else {
							psym = parent.getLabel();
							prank = parent.getNumChildren();
							for (int i = 0; i < parent.getNumChildren(); i++) {
								if (parent.getChild(i) == node) {
									c = i;
									break;
								}
							}
						}
						// for lhs storing
						// s (lhs symbol) and rank (lhs rank) already set, as well as currst
						int lpos = -1;
						TransducerLeftTree map = r.getTRVM().getLHS(node);
						// eps case
						if (r.getLHS().equals(map)) {
							lpos = 0;
						}
						else {
							for (int i = 0; i < rank; i++) {
								if (r.getLHS().getChild(i).equals(map)) {
									lpos = i;
									break;
								}
							}
						}
						
						if (currst == null || psym == null || c < 0 || lpos < 0) {
							throw new UnusualConditionException("Couldn't get symbol and/or parent of "+node+" in "+r);
						}
						if (!sortingAllrelposfrules.goc(currst).containsKey(c)) {
							sortingAllrelposfrules.get(currst).put(c, new FixedPrioritiesPriorityQueue<TreeTransducerRule>());

						}
						if (!sortingRelposfrules.goc(currst).goc(c).goc(psym).containsKey(prank)) {
							sortingRelposfrules.get(currst).get(c).get(psym).put(prank, new FixedPrioritiesPriorityQueue<TreeTransducerRule>());

						}
						if (!sortingAllfrules.goc(currst).containsKey(lpos)) {
							sortingAllfrules.get(currst).put(lpos, new FixedPrioritiesPriorityQueue<TreeTransducerRule>());
						}
						if (!sortingFrules.goc(currst).goc(lpos).goc(s).containsKey(rank)) {
							sortingFrules.get(currst).get(lpos).get(s).put(rank, new FixedPrioritiesPriorityQueue<TreeTransducerRule>());

						}
						if (debug) Debug.debug(debug, "Adding "+r+" to rightside forward element "+currst+"->"+c+"->"+psym+"->"+prank);
						sortingRelposfrules.get(currst).get(c).get(psym).get(prank).add(r, -r.getWeight());
						sortingAllrelposfrules.get(currst).get(c).add(r, -r.getWeight());
						if (debug) Debug.debug(debug, "Adding "+r+" to leftside forward element "+currst+"->"+lpos+"->"+s+"->"+rank);
						sortingFrules.get(currst).get(lpos).get(s).get(rank).add(r, -r.getWeight());
						sortingAllfrules.get(currst).get(lpos).add(r, -r.getWeight());

						
						//						if (debug) Debug.debug(debug, "There are "+relposfrules.get(currst).get(c).get(psym).get(prank).size()+" now in relpos and "+
						//								allrelposfrules.get(currst).get(c).size()+" in allrelpos");
						//						if (r.isInEps())
						//							Debug.prettyDebug("Added "+r+" to "+currst+", "+c+", "+psym);

					}

					else {
						Symbol c = node.getLabel();
//						if (!relpostrules.containsKey(c)) {
////							sortingTrules.put(c, new HashMap<Integer, FixedPrioritiesPriorityQueue<TreeTransducerRule>> ());
//							relpostrules.put(c, new HashMap<Integer, Vector<TreeTransducerRule>> ());
//						}
						if (!relpostrules.goc(c).containsKey(localRank)) {
							relpostrules.get(c).put(localRank, new Vector<TreeTransducerRule> ());
						}
						if (!sortingRelpostrules.goc(c).containsKey(localRank)) {
							sortingRelpostrules.get(c).put(localRank, new FixedPrioritiesPriorityQueue<TreeTransducerRule>());
						}
						if (localRank == 0) {
							sortingAllrelpostrules.add(r, -r.getWeight());
							if (debug) Debug.debug(debug, "sorting lexical rules are "+sortingAllrelpostrules);
						}
						sortingRelpostrules.get(c).get(localRank).add(r, -r.getWeight());
						if (debug) Debug.debug(debug, "Adding "+r+" to lexical forward element "+c+" and rank "+localRank);

					}

				}
				// get entire yield of rhs and map rule by the vector of states and rhs label
				Vector<Symbol> states = new Vector<Symbol>();
				for (TransducerRightTree c : r.getRHS().getVariableChildren()) {
					states.add(c.getState());
				}
				if (states.size() > 0) {
//					// binary constraint for now
//					if (states.size() > 2)
//						throw new UnusualConditionException("Can only handle binary rules: "+r);
//					VecSymbol vs = SymbolFactory.getVecSymbol(states);
//					if (!sortingWholeFrules.goc(vs).containsKey(rhsSym))
//						sortingWholeFrules.get(vs).put(rhsSym, new FixedPrioritiesPriorityQueue<TreeTransducerRule>());
//					sortingWholeFrules.get(vs).get(rhsSym).add(r, -r.getWeight());
					if (!sortingWholeFrules.containsKey(rhsSym))
						sortingWholeFrules.put(rhsSym, new FixedPrioritiesPriorityQueue<TreeTransducerRule>());
					sortingWholeFrules.get(rhsSym).add(r, -r.getWeight());
					
				}
				
				
			}
			brules.put(state,  new Vector<TreeTransducerRule>(sortingBsrules.toSortedList()));
			for (Symbol rsym : sortingBsrulesByRHS.keySet()) {
				for (Integer i : sortingBsrulesByRHS.get(rsym).keySet())
					brulesByRHS.goc(state).goc(rsym).put(i, new Vector<TreeTransducerRule>(sortingBsrulesByRHS.get(rsym).get(i).toSortedList()));
			}
			// convert to labelbrules
			// add to backward rule map
			for (Symbol rsym : sortingLabelBsrules.keySet()) {
				for (Integer i : sortingLabelBsrules.get(rsym).keySet()) {
					labelbrules.goc(state).goc(rsym).put(i, new Vector<TreeTransducerRule>(sortingLabelBsrules.get(rsym).get(i).toSortedList()));	
					for (Symbol srhs : sortingLabelBsrulesByRHS.get(rsym).get(i).keySet()) {
						for (Integer irhs : sortingLabelBsrulesByRHS.get(rsym).get(i).get(srhs).keySet())
							labelbrulesByRHS.goc(state).goc(rsym).goc(i).goc(srhs).put(irhs, new Vector<TreeTransducerRule>(sortingLabelBsrulesByRHS.get(rsym).get(i).get(srhs).get(irhs).toSortedList()));
					}
				}
			}
		}
			// terminal rules
		allrelpostrules = new Vector<TreeTransducerRule>(sortingAllrelpostrules.toSortedList());
		if (debug) Debug.debug(debug, "Lexical rules are "+allrelpostrules);
		for (Symbol c : sortingRelpostrules.keySet())
			for (int localRank : sortingRelpostrules.get(c).keySet()) 
				relpostrules.goc(c).put(localRank, new Vector<TreeTransducerRule>(sortingRelpostrules.get(c).get(localRank).toSortedList()));
		
		alltrules = new Vector<TreeTransducerRule>(sortingAlltrules.toSortedList());
		for (Symbol c : sortingTrules.keySet())
			for (int localRank : sortingTrules.get(c).keySet()) 
				trules.goc(c).put(localRank, new Vector<TreeTransducerRule>(sortingTrules.get(c).get(localRank).toSortedList()));
		
		// convert to relative forward rules bits
		for (Symbol currst : sortingRelposfrules.keySet()) {
			for (int c : sortingRelposfrules.get(currst).keySet()) {
				allrelposfrules.goc(currst).put(c, new Vector<TreeTransducerRule>(sortingAllrelposfrules.get(currst).get(c).toSortedList()));
				for (Symbol psym : sortingRelposfrules.get(currst).get(c).keySet()) {
					for (int prank : sortingRelposfrules.get(currst).get(c).get(psym).keySet()) {
						relposfrules.goc(currst).goc(c).goc(psym).put(prank, new Vector<TreeTransducerRule>(sortingRelposfrules.get(currst).get(c).get(psym).get(prank).toSortedList()));
					}
				}
			}
		}
		// convert to forward rules bits
		for (Symbol currst : sortingFrules.keySet()) {
			for (int c : sortingFrules.get(currst).keySet()) {
				allfrules.goc(currst).put(c, new Vector<TreeTransducerRule>(sortingAllfrules.get(currst).get(c).toSortedList()));
				for (Symbol psym : sortingFrules.get(currst).get(c).keySet()) {
					for (int prank : sortingFrules.get(currst).get(c).get(psym).keySet()) {
						frules.goc(currst).goc(c).goc(psym).put(prank, new Vector<TreeTransducerRule>(sortingFrules.get(currst).get(c).get(psym).get(prank).toSortedList()));
					}
				}
			}
		}
		// convert to whole forward rules
		for (Symbol label : sortingWholeFrules.keySet()) {
			FixedPrioritiesPriorityQueue<TreeTransducerRule> q = sortingWholeFrules.get(label);
			while (!q.isEmpty()) {
				TreeTransducerRule rule = q.next();
				if (!wholeFrules.containsKey(label))
					wholeFrules.put(label, new Pair<Vector<TreeTransducerRule>, PMap<Symbol, Pair>>(new Vector<TreeTransducerRule>(), new PMap<Symbol, Pair>()));
				Pair<Vector<TreeTransducerRule>, PMap<Symbol, Pair>> currPair = wholeFrules.get(label);
				for (int i = 0; i < rule.getRHS().getNumChildren(); i++) {
					Symbol c = rule.getRHS().getChild(i).getState();
					if (!currPair.r().containsKey(c))
						currPair.r().put(c, new Pair<Vector<TreeTransducerRule>, PMap<Symbol, Pair>>(new Vector<TreeTransducerRule>(), new PMap<Symbol, Pair>()));
					currPair = currPair.r().get(c);
				}
				currPair.l().add(rule);
			}
		}
//		for (VecSymbol vs : sortingWholeFrules.keySet()) {
//			for (Symbol psym : sortingWholeFrules.get(vs).keySet()) {
//				oldWholeFrules.goc(vs).put(psym, new Vector<TreeTransducerRule>(sortingWholeFrules.get(vs).get(psym).toSortedList()));
//			}
//		}
	}
}
