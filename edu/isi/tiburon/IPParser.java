package edu.isi.tiburon;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Vector;


// playground class (for now) with static methods

// process transducer rules into something that can be traversed, do parsing, etc.
public class IPParser {
	
	// map all literals and at least original states into integers and back
	// (virtual states from multi-height transducer rules will come 
	
	private static int nextID=0;
	private static int firstState=0;
	private static HashMap<Symbol, Integer> s2i ;
	private static HashMap<Integer, Symbol> i2s ;
	
	// conversion of string and tree transducer rules, indexed by lhs and rhs sequences
	// slot 0 is the string, and the rest are the tree
	private static IPBasicAccess[] trsRules;
	
	
	// bottom-up tree (cascade) parser
	public static void parse(
			TreeItem tree,
			Vector<TreeTransducerRuleSet> chain) {
		boolean debug = true;
		nextID=0;
		s2i = new HashMap<Symbol, Integer>();
		i2s = new HashMap<Integer, Symbol>();
		trsRules = new IPBasicAccess[chain.size()];
		//mapping literals and creating IPTree
		IPTree iptree = mapTree(tree);
		// just mapping literals
		for (TreeTransducerRuleSet tt: chain) {
			mapTransducerLiterals(tt);
		}
		// last "natural" state (other states come from the chain and aren't used in parsing)
		int lastState = nextID-1;
		int i = 0;
		for (TreeTransducerRuleSet tt: chain) {
			trsRules[i] = new IPBasicAccess();
			mapAndSeedTransducer(tt, i++);
		}
		// leaves of trees for processing
		Vector<IPTree> todoList = new Vector<IPTree>();
		getLeaves(iptree, todoList);
		
		// process from bottom up, adding parent when all children are finished
		while (!todoList.isEmpty()) {
			IPTree node = todoList.remove(0);
			// TODO: match label and rank of this node
			// TODO: if rule is special match sibling pattern too
			calculateStates(node);
			// add parent if this is the last child of parent
			// TODO: and if there is something for all previous children!
			if (node.getParent() != null) {
				IPTree parent = node.getParent();
				if (parent.getChildren().get(parent.getNumChildren()-1) == node)
					todoList.add(parent);
			}
		}
	}
	
	// TODO: this is a reimplementation of stuff in IPFinState: unify them!
	private static class DstStructure {
		Vector<Vector<Integer>> seq;
		Vector<Vector<HashMap<Integer, Vector<IPTreeState>>>> map;
		public String toString() {
			return seq+"\n"+map;
		}
	}
	// recursive descent through tree transducer cascade
	// level 0: for each rule with RHS label matching this node label and rank matching
	// this node rank, get all possible children in each position that match the RHS 
	// child sequence.
	
	// how many calls to a level?
	private static int[] callMap;
	// how many successful returns from a level?
	private static int[] succMap;
	// see also: finish in IPFinState
	private static void calculateStates(IPTree node) {
		boolean debug = true;
		int rank = node.getNumChildren();
		int level = 0;
		int match = node.getLabel();
		int createdCount = 0;
		callMap = new int[trsRules.length];
		succMap = new int[trsRules.length];

		
		for (Vector<Integer> seq : trsRules[level].getTemplates(match)) {
			if (seq.size() != rank)
				continue;
			// sequences of states that the dst of these rules can go onto
			DstStructure partials = new DstStructure();
			partials.map = new Vector<Vector<HashMap<Integer, Vector<IPTreeState>>>>();
			partials.seq = new Vector<Vector<Integer>>();
			for (IPRule rule : trsRules[level].getTemplates(match, seq)) {
				//if (debug) Debug.debug(debug, "Considering "+rule);
				// look for seq in node's children at the appropriate level
				// for each child, get all states with the appropriate integer at that level
				Vector<Vector<IPTreeState>> possibleChildren = null;
				if (rank > 0) {
					possibleChildren = new Vector<Vector<IPTreeState>>();
					for (int i = 0; i < rank; i++) {
						Vector<IPTreeState> possibleChild = new Vector<IPTreeState>();
						for (IPTreeState state : node.getChildren().get(i).getStates()) {
							if (state.getState(level) == seq.get(i))
								possibleChild.add(state);
						}
						if (possibleChild.size() == 0)
							continue;
						possibleChildren.add(possibleChild);
					}
//					if (debug) {
//						StringBuffer kidcount = new StringBuffer(possibleChildren.get(0).size()+"");
//						for (int i = 1; i < rank; i++)
//							kidcount.append("x"+possibleChildren.get(i).size());
//						Debug.debug(debug, "Allowed "+kidcount+" for "+node+" at level "+level);
//					}
				}
				
				
				// if this is getting passed on to next transducer, use semantics to
				// get the matching label and ordering. Get partial state sequences				 
				if (trsRules.length > level+1) {
					for (IPRule.Semantics sem : rule.getSemantics()) {
						// reorder possible children
						Vector<Vector<IPTreeState>> reorderedChildren = reorderStateSeq(possibleChildren, sem.getSemChildren(), sem.getMap());
						callMap[level+1]++;
						DstStructure recurseDst = calculateStates(sem, level+1, reorderedChildren);
						if (recurseDst == null || recurseDst.seq.size() == 0)
							continue;
						succMap[level+1]++;
						int i = 0;
						for (Vector<Integer> partial : recurseDst.seq) {
							Vector<Integer> complete = new Vector<Integer>(partial);
							// add in this level's state to top
							complete.add(0, rule.getState());
							partials.seq.add(complete);
							// pass map along, adding in an empty level
							Vector<HashMap<Integer, Vector<IPTreeState>>> map = new Vector<HashMap<Integer, Vector<IPTreeState>>>(recurseDst.map.get(i));
							map.add(0, new HashMap<Integer, Vector<IPTreeState>>());
							partials.map.add(map);
						}						
					}
				}
				else {
					// create base items
					Vector<Integer> base = new Vector<Integer>();
					base.add(0, rule.getState());
					partials.seq.add(base);
					Vector<HashMap<Integer, Vector<IPTreeState>>> mapvec = new Vector<HashMap<Integer, Vector<IPTreeState>>>();
					mapvec.add(new HashMap<Integer, Vector<IPTreeState>>());
					partials.map.add(mapvec);

				}
			}
			int i = 0;
			for (Vector<Integer> resultseq : partials.seq) {
				Vector<HashMap<Integer, Vector<IPTreeState>>> resultmap = partials.map.get(i);
				IPTreeState state = new IPTreeState(node, resultseq, resultmap);
				// only counting full-length states
				if (resultseq.size() == trsRules.length) {
					createdCount++;
					//if (debug) Debug.debug(debug, "Created "+state);
				}
				node.addState(state);
			}
		}
		if (debug) Debug.debug(debug, "Created "+createdCount+" for "+node);
		if (debug) {
			for (int i = 1; i < trsRules.length; i++) {
				Debug.debug(debug, "Called for level "+i+" "+callMap[i]+"; succeeded "+succMap[i]);
			}
		}
	}
	
	// using the lhs label and rank in sem, search the rule set at level for something that matches
	// in each position of possibles. for each such matching rule, descend if possible , then add dst
	// states and/or map info to the dst structure and return
	private static DstStructure calculateStates(IPRule.Semantics sem, int level, Vector<Vector<IPTreeState>> prevPossibleChildren) {
		boolean debug = false;
		int rank = sem.getSemChildren();
		int match = sem.getLabel();
		// sequences of states that the dst of these rules can go onto
		DstStructure partials = new DstStructure();
		partials.map = new Vector<Vector<HashMap<Integer, Vector<IPTreeState>>>>();
		partials.seq = new Vector<Vector<Integer>>();
		for (Vector<Integer> seq : trsRules[level].getTemplates(match)) {
			if (seq.size() != rank)
				continue;
			for (IPRule rule : trsRules[level].getTemplates(match, seq)) {
			//	if (debug) Debug.debug(debug, "Considering "+rule);
				// look for seq in passed in possible children at the appropriate level
				Vector<Vector<IPTreeState>> possibleChildren = null;
				
				if (rank > 0) {
					boolean sawEmpty = false;
					possibleChildren = new Vector<Vector<IPTreeState>>();
					for (int i = 0; i < rank; i++) {
						Vector<IPTreeState> possibleChild = new Vector<IPTreeState>();
						for (IPTreeState state : prevPossibleChildren.get(i)) {
							
							// TODO: check that either state matches desired seq
							// or that state has in its map a match for desired seq
							if (state.getState(level) == seq.get(i))
								possibleChild.add(state);
						}
						if (possibleChild.size() == 0) {
							sawEmpty = true;
							break;
						}
						possibleChildren.add(possibleChild);
					}
					if (sawEmpty)
						continue;
					if (debug) {
						StringBuffer kidcount = new StringBuffer(possibleChildren.get(0).size()+"");
						for (int i = 1; i < rank; i++)
							kidcount.append("x"+possibleChildren.get(i).size());
						Debug.debug(debug, "Allowed "+kidcount+" at level "+level);
					}
				}
				// if this is getting passed on to next transducer, use semantics to
				// get the matching label and ordering. Get partial state sequences				 
				if (trsRules.length > level+1) {
					for (IPRule.Semantics nextsem : rule.getSemantics()) {
						if (nextsem.getLabel() >= 0) {

							// reorder possible children
							Vector<Vector<IPTreeState>> reorderedChildren = reorderStateSeq(possibleChildren, nextsem.getSemChildren(), nextsem.getMap());
							// TODO: better map promotion strategy: this is invalid if we're promoting more than one thing
							boolean didMap = false;
							if (possibleChildren != null) {
								for (Vector<IPTreeState> posvec : possibleChildren) {
									for (IPTreeState state : posvec) {
										if (state.getMap(level) != null) {
											if (state.getMap(level).keySet().size() > 1)
												Debug.debug(true, "WARNING: unrolling map with more than one element and not preserving dependence: "+state);
											for (int key : state.getMap(level).keySet()) {
												didMap = true;
											//	if (debug) Debug.debug(debug, level, state+" has mapping "+key+" -> "+state.getMap(level).get(key));
												if (reorderedChildren.get(key) == null)
													reorderedChildren.set(key, state.getMap(level).get(key));
												else
													reorderedChildren.get(key).addAll(state.getMap(level).get(key));
											}
										}
									}
								}
							}
							//if (didMap && debug) Debug.debug(debug, level, "after bringing up insertions: "+reorderedChildren);
							
							callMap[level+1]++;
							DstStructure recurseDst = calculateStates(nextsem, level+1, reorderedChildren);
							if (recurseDst.seq.size() > 0)
								succMap[level+1]++;
							int i = 0;
							for (Vector<Integer> partial : recurseDst.seq) {
								Vector<Integer> complete = new Vector<Integer>(partial);
								// add in this level's state to top
								complete.add(0, rule.getState());
								partials.seq.add(complete);
								// pass map along, adding in an empty level
								Vector<HashMap<Integer, Vector<IPTreeState>>> map = new Vector<HashMap<Integer, Vector<IPTreeState>>>(recurseDst.map.get(i));
								map.add(0, new HashMap<Integer, Vector<IPTreeState>>());
								partials.map.add(map);
							}
						}
						// in middle of virtual rule. add mapping
						else {
							//if (debug) Debug.debug(debug, level, "Stopping at level "+level+" and saving map "+sem.getMap());
							Vector<Integer> base = new Vector<Integer>();
							base.add(0, rule.getState());
							partials.seq.add(base);
							HashMap<Integer, Vector<IPTreeState>> map = new HashMap<Integer, Vector<IPTreeState>>();
							for (int i = 0; i < seq.size(); i++) {
								// declared in this map
								if (sem.getMap().containsKey(i))
									map.put(sem.getMap().get(i), prevPossibleChildren.get(i));
								// TODO: promoted up from virtual -- can we use the tree structure?
								//else
								//	seq.get(i).promoteMapMembers(map, level);
							}

							Vector<HashMap<Integer, Vector<IPTreeState>>> mapvec = new Vector<HashMap<Integer, Vector<IPTreeState>>>();
							mapvec.add(map);
							partials.map.add(mapvec);	
						}

					}
				}
				// if not descending, just add base
				else {
					// create base items
					Vector<Integer> base = new Vector<Integer>();
					base.add(0, rule.getState());
					partials.seq.add(base);
					Vector<HashMap<Integer, Vector<IPTreeState>>> mapvec = new Vector<HashMap<Integer, Vector<IPTreeState>>>();
					mapvec.add(new HashMap<Integer, Vector<IPTreeState>>());
					partials.map.add(mapvec);
				}
			}
		}
		return partials;				
	}

	
	public static RTGRuleSet parse(
			StringItem string, 
			Vector<TreeTransducerRuleSet> chain, 
			StringTransducerRuleSet trs) {
		boolean debug = false;
		boolean altdebug = true;
		Semiring semiring = trs.semiring;
		nextID=0;
		s2i = new HashMap<Symbol, Integer>();
		i2s = new HashMap<Integer, Symbol>();
		trsRules = new IPBasicAccess[chain.size()+1];
	
		// map epsilon just in case
		s2i.put(Symbol.getEpsilon(), -1);
		i2s.put(-1, Symbol.getEpsilon());
		IPUnfState.setEpsilon(-1);

		
		// just mapping literals
		Vector<Integer> stringVec = mapString(string);
		mapTransducerLiterals(trs);
	
		for (TreeTransducerRuleSet tt: chain) {
			mapTransducerLiterals(tt);
		}
		
		// should be no more literals, so save int (last id)
		int lastLiteral = nextID-1;
		IPUnfState.setLastLiteral(lastLiteral);
		// so that we can keep things keyed on states in arrays, save first state
		// id, and subtract to get cell address
		firstState = nextID;
		
		trsRules[0] = new IPBasicAccess();
		mapAndSeedTransducer(trs);
		
		// last "natural" state (other states come from the chain and aren't used in parsing)
		int lastState = nextID-1;
		int i = 1;
		for (TreeTransducerRuleSet tt: chain) {
			trsRules[i] = new IPBasicAccess();
			mapAndSeedTransducer(tt, i++);
		}

		// start state for building rtg later
		Vector<Integer> startVec = new Vector<Integer>();
		for (TreeTransducerRuleSet tt: chain) {
			startVec.add(s2i.get(tt.startState));
		}
		startVec.add(0, s2i.get(trs.startState));
		
		
		
		// already-built states
		HashSet<IPFinState> builtStates = new HashSet<IPFinState>();

		// where we store candidate built rules
		HashMap<IPFinState, HashMap<Vector<IPFinState>, HashMap<Integer, Double>>> builtRules = new HashMap<IPFinState, HashMap<Vector<IPFinState>, HashMap<Integer, Double>>>();

		
		// vector of states for building rules later
		Vector<IPFinState> todoList= new Vector<IPFinState>();
		
		// now do the parsing
		
		// change queuing strategy by replacing this agenda
//		IPVectorAgenda agenda = new IPVectorAgenda();
		IPBinnedAgenda agenda = new IPBinnedAgenda();

		
		// track whether prediction items are handled
		// index by reduced symbol id and string position
		boolean[][] preds = new boolean[state2slot(lastState)+1][stringVec.size()+1];
		
		// categorize chart nodes by finished and unfinished variants
		// index by state id and string pos
		// additionally index unfinished chart by rule position and use a sorted map to
		// ensure earleir parts of a rule are covered before later parts (important for nodes with zero width)
		HashSet<IPFinState>[][] finishedChart = new HashSet[state2slot(lastState)+1][stringVec.size()+1];
		TreeMap<Integer, HashSet<IPUnfState>>[][] unfinishedChart = new TreeMap[state2slot(lastState)+1][stringVec.size()+1];

		
		int start = s2i.get(trs.getStartState());
		agenda.addAll(IPUnfState.init(start, trsRules[0]));
		preds[state2slot(start)][0] = true;
	
		while (!agenda.isEmpty()) {
			IPState item = agenda.getState();
			if (altdebug || debug) Debug.debug(altdebug ||  debug, "Processing "+item);

			// if it's unfinished, try to predict, shift, and finish with it
			if (item instanceof IPUnfState) {
				IPUnfState usItem = (IPUnfState) item;

				if (!usItem.isNextLit() && !preds[state2slot(usItem.getNext())][usItem.getEnd()]) {
					Vector<IPUnfState> predRules = usItem.predict(trsRules[0]);
					if (predRules.size() > 0) {
						if (debug) Debug.debug(debug, "Predicting "+predRules);
						preds[state2slot(predRules.get(0).getBaseState())][usItem.getEnd()] = true;
						agenda.addAll(predRules);
					}
				}
				if (usItem.getEnd() < stringVec.size()) {
					IPUnfState shift = usItem.shift(stringVec.get(usItem.getEnd()));
					if (shift != null) {
						if (debug) Debug.debug(debug, "Shifting "+shift);
						// unfinished states at the end should try to become finished and then add
						// to the agenda
						if (shift.isFinished()) {
							Vector<IPFinState> fins = IPFinState.finish(shift, trsRules, builtStates, semiring);
							if (debug) Debug.debug(debug, "Finished "+fins);

							agenda.addAll(fins);
						}
						else
							agenda.addState(shift);
					}
				}
				else if (usItem.getEnd() == stringVec.size()) {
					IPUnfState shift = usItem.shift(s2i.get(Symbol.getEpsilon()));
					if (shift != null) {
						if (debug) Debug.debug(debug, "Shifting "+shift);
						// unfinished states at the end should try to become finished and then add
						// to the agenda
						if (shift.isFinished()) {
							Vector<IPFinState> fins = IPFinState.finish(shift, trsRules, builtStates, semiring);
							if (debug) Debug.debug(debug, "Finished "+fins);

							agenda.addAll(fins);
						}
						else
							agenda.addState(shift);
					}
				}
				// if item points at a state,
				// add to chart (based on the state its pointing to)
				// try to complete with a finished state that starts where this ends
				if (!usItem.isNextLit()) {
					int nextstate = state2slot(usItem.getNext());
					int idx = usItem.getEnd();
					if (unfinishedChart[nextstate][idx] == null)
						unfinishedChart[nextstate][idx] = new TreeMap<Integer,HashSet<IPUnfState>>();
					if (!unfinishedChart[nextstate][idx].containsKey(usItem.getPos()))
						unfinishedChart[nextstate][idx].put(usItem.getPos(), new HashSet<IPUnfState>());
					unfinishedChart[nextstate][idx].get(usItem.getPos()).add(usItem);
					if (finishedChart[nextstate][idx] != null) {
						if (debug || altdebug) Debug.debug(debug || altdebug, "About to check "+finishedChart[nextstate][idx].size()+" possible completions");
						for (IPFinState fin : finishedChart[nextstate][idx]) {
							IPUnfState comp = usItem.complete(fin);
							if (debug) Debug.debug(debug, "Completing "+comp+" = "+usItem+" + "+fin);
							// unfinished states at the end should try to become finished and then add
							// to the agenda
							if (comp.isFinished()) {
								Vector<IPFinState> fins = IPFinState.finish(comp, trsRules, builtStates, semiring);
								if (debug) Debug.debug(debug, "Finished "+fins);

								agenda.addAll(fins);
							}
							else
								agenda.addState(comp);
						}
						if (debug || altdebug) Debug.debug(debug || altdebug, "Done with completions");
						
					}
				}

			}
			// finished states should add to the chart and try to complete
			else {
				IPFinState finItem = (IPFinState) item;
				// add to chart
				int state = state2slot(finItem.getBaseState());
				int idx = finItem.getStart();
				if (finishedChart[state][idx] == null)
					finishedChart[state][idx] = new HashSet<IPFinState>();
				finishedChart[state][idx].add(finItem);
				// if it's truly a start state and spans the whole string, add it to todoList
				if (!finItem.isDone() && 
						finItem.getSeq().equals(startVec) && 
						idx == 0 && 
						finItem.getEnd() == stringVec.size()) {
					if (debug) Debug.debug(debug, "Adding goal state "+finItem);
					finItem.setDone();
					todoList.add(finItem);
				}
				// try to complete with an unfinished state that ends where this starts
				if (unfinishedChart[state][idx] != null) {
					for (int pos : unfinishedChart[state][idx].keySet()) {
						for (IPUnfState unf : unfinishedChart[state][idx].get(pos)) {
							IPUnfState comp = unf.complete(finItem);
							if (debug) Debug.debug(debug, "Completing "+comp+" = "+unf+" + "+finItem);
							// unfinished states at the end should try to become finished and then add
							// to the agenda
							if (comp.isFinished()) {

								Vector<IPFinState> fins = IPFinState.finish(comp, trsRules, builtStates, semiring);
								if (debug) Debug.debug(debug, "Finished "+fins);

								agenda.addAll(fins);
							}
							else
								agenda.addState(comp);
						}
					}
				}

			}

		}
	

		while (!todoList.isEmpty()) {
			todoList.remove(0).getRuleElements(trsRules, semiring, builtRules, todoList);
		}
		
		IPFinState startSym = IPFinState.get(startVec, 0, stringVec.size());
		// builtRules has the elements of RTG rules we need
		
		RTGRuleSet rtg = new RTGRuleSet(builtRules, i2s, startSym, semiring);

		return rtg;
	
	}
	
	
	private static int state2slot(int state) {
		return state-firstState;
	}
	private static int slot2state(int slot) {
		return slot+firstState;
	}
		
	// turn all symbols we will work with into integers 
	private static Vector<Integer> mapString(StringItem s) {
		Vector<Integer> ret = new Vector<Integer>();
		for (int i = 0; i < s.getSize(); i++) {
			Symbol sym = s.getSym(i);
			if (!s2i.containsKey(sym)) {
				s2i.put(sym, nextID);
				i2s.put(nextID++, sym);
			}
			ret.add(s2i.get(sym));
		}
		return ret;
	}
	
	// turn all symbols we will work with into integers and create the new tree
	private static IPTree mapTree(TreeItem t) {
		Symbol sym = t.getLabel();
		int num = -1;
		if (!s2i.containsKey(sym)) {
			num = nextID;
			s2i.put(sym, nextID);
			i2s.put(nextID++, sym);
		}
		else
			num = s2i.get(sym);
		if (t.getNumChildren() == 0) {
			return new IPTree(num);
		}
		else {
			Vector<IPTree> kids = new Vector<IPTree>();
			for (int i = 0; i < t.getNumChildren(); i++) {
				kids.add(mapTree(t.getChild(i)));
			}
			return new IPTree(num, kids);
		}
	}
	
	// build a list of leaves for 
	private static void getLeaves(IPTree iptree, Vector<IPTree>todoList) {
		if (iptree.getNumChildren() > 0) {
			for (IPTree child : iptree.getChildren()) {
				getLeaves(child, todoList);
			}
		}
		else {
			todoList.add(iptree);
		}
	}
	
	// just map the literals of the transducer rules
	private static void mapTransducerLiterals(StringTransducerRuleSet trs) {
		boolean debug = false;
		for (TransducerRule raw_r : trs.getRules()) {
			StringTransducerRule r = (StringTransducerRule)raw_r;
			TransducerRightString rhs = r.getRHS();
			if (!rhs.hasLabel())
				rhs = rhs.nextTerminal();
			while (rhs != null) {
				Symbol sym = rhs.getLabel();
				
				if (!s2i.containsKey(sym)) {
					
					if (debug) Debug.debug(debug, "Mapping "+sym+" to "+nextID);
					s2i.put(sym, nextID);
					i2s.put(nextID++, sym);
				}
				rhs = rhs.nextTerminal();
			}
			TransducerLeftTree lhs = r.getLHS();
			mapTransducerTreeLiterals(lhs);
		}
		
	}
	// just map the literals of the transducer rules
	
	private static void mapTransducerLiterals(TreeTransducerRuleSet trs) {
		for (TransducerRule raw_r : trs.getRules()) {
			TreeTransducerRule r = (TreeTransducerRule)raw_r;
			TransducerRightTree rhs = r.getRHS();
			mapTransducerTreeLiterals(rhs);
			TransducerLeftTree lhs = r.getLHS();
			mapTransducerTreeLiterals(lhs);
		}
	}
	private static void mapTransducerTreeLiterals(TransducerRightTree t) {
		boolean debug = false;
		if (t.getNumChildren() == 0) {
			if (t.hasLabel()) {
				Symbol sym = t.getLabel();
				if (!s2i.containsKey(sym)) {
					if (debug) Debug.debug(debug, "Mapping "+sym+" to "+nextID);
					s2i.put(sym, nextID);
					i2s.put(nextID++, sym);
				}
			}
		}
		else {
			Symbol sym = t.getLabel();
			if (!s2i.containsKey(sym)) {
				if (debug) Debug.debug(debug, "Mapping "+sym+" to "+nextID);
				s2i.put(sym, nextID);
				i2s.put(nextID++, sym);
			}
			for (int i = 0; i < t.getNumChildren(); i++)
				mapTransducerTreeLiterals(t.getChild(i));
		}
	}
	
	private static void mapTransducerTreeLiterals(TransducerLeftTree t) {
		boolean debug = false;
		if (t.getNumChildren() == 0) {
			if (t.hasLabel()) {
				Symbol sym = t.getLabel();
				if (!s2i.containsKey(sym)) {
					if (debug) Debug.debug(debug, "Mapping "+sym+" to "+nextID);
					s2i.put(sym, nextID);
					i2s.put(nextID++, sym);
				}
			}
		}
		else {
			Symbol sym = t.getLabel();
			if (!s2i.containsKey(sym)) {
				if (debug) Debug.debug(debug, "Mapping "+sym+" to "+nextID);
				s2i.put(sym, nextID);
				i2s.put(nextID++, sym);
			}
			for (int i = 0; i < t.getNumChildren(); i++)
				mapTransducerTreeLiterals(t.getChild(i));
		}
	}
	
	
	// run makeStringIPRule on each rule, transforming it for processing
	// also get ids for the states
	private static void mapAndSeedTransducer(StringTransducerRuleSet trs) {
		boolean debug = false;
		for (TransducerRule raw_r : trs.getRules()) {
			StringTransducerRule r = (StringTransducerRule)raw_r;
			if (debug) Debug.debug(debug, "Converting "+r);
			makeStringIPRule(r);
		}
		
	}
	// get a IPRule representation for a String Transducer Rule and add it to a structure, indexed on 
	// (state and rhs) template (could be multiple lhs)
	
	// does state symbol mapping, too
	
	private static void makeStringIPRule(StringTransducerRule r) {
		boolean debug = true;
		Symbol statesym = r.getState();
		if (!s2i.containsKey(statesym)) {
			if (debug) Debug.debug(debug, "Mapping state "+statesym+" to "+nextID);
			s2i.put(statesym, nextID);
			i2s.put(nextID++, statesym);
		}
		int state = s2i.get(statesym);
		int lhs = s2i.get(r.getLHS().getLabel());
		int numkids = r.getLHS().getNumChildren();
		Vector<Integer> rhskids = new Vector<Integer>();
		TransducerRightString rhs = r.getRHS();
		
		// build integer rhs
		
		// also,
		// mapping from rhs order to lhs order
		// x0 x1 x2 -> Z x2 x0 x1  = 1->2, 2->0, 3->1 (literals on right are NOT ignored)
		// hokey way to do this, but it only happens once: since lhs is height 1
		// get rhs in order and then check which child, if any, it is
		HashMap<Integer, Integer> varmap = new HashMap<Integer, Integer>();
		int rhspos = 0;
		while (rhs != null) {
			if (rhs.hasState()) {
				Symbol sym = rhs.getState();
				if (!s2i.containsKey(sym)) {
					if (debug) Debug.debug(debug, "Mapping state "+sym+" to "+nextID);
					s2i.put(sym, nextID);
					i2s.put(nextID++, sym);
				}
				rhskids.add(s2i.get(sym));
				TransducerLeftTree tltmap = r.getTRVM().getLHS(rhs);
				if (tltmap != null) {
					for (int i = 0; i < numkids; i++) {
						if (r.getLHS().getChild(i) == tltmap) {
							varmap.put(rhspos, i);
							if (debug) Debug.debug(debug, "Mapped rhs pos "+rhspos+" to lhs var "+i);
							break;
						}
					}
				}
			}
			else
				rhskids.add(s2i.get(rhs.getLabel()));
			rhs = rhs.next();
			rhspos++;
		}
		
		IPRule ret = IPRule.get(state, -1, rhskids, lhs, numkids, varmap, r.getWeight(), r.getWeight(), r.semiring);
		if (debug) Debug.debug(debug, "Created "+ret);
		trsRules[0].addItem(ret);
		
		
		
		
	}
	
	// run makeTreeIPRule on each rule, transforming it for processing
	// also get ids for the states
	private static void mapAndSeedTransducer(TreeTransducerRuleSet trs, int level) {
		boolean debug = false;
		for (TransducerRule raw_r : trs.getRules()) {
			TreeTransducerRule r = (TreeTransducerRule)raw_r;
			if (debug) Debug.debug(debug, "Converting "+r);
			makeTreeIPRule(r, level);
		}
		
	}
	
	// make one or more IPRules from tree transducer rule, mapping states
	// and creating new states as need be
	private static void makeTreeIPRule(TreeTransducerRule r, int level) {
		boolean debug = false;
		Symbol statesym = r.getState();
		if (!s2i.containsKey(statesym)) {
			if (debug) Debug.debug(debug, "Mapping state "+statesym+" to "+nextID);
			s2i.put(statesym, nextID);
			i2s.put(nextID++, statesym);
		}
		int state = s2i.get(statesym);
		int lhs = s2i.get(r.getLHS().getLabel());
		int numkids = r.getLHS().getNumChildren();
		Vector<Integer> rhskids = new Vector<Integer>();
		TransducerRightTree rhs = r.getRHS();
		int rhslabel = s2i.get(rhs.getLabel());
		
		// build integer rhs
		
		// also,
		// mapping from rhs order to lhs order
		// x0 x1 x2 -> Z x2 x0 x1  = 1->2, 2->0, 3->1 (literals on right are NOT ignored)
		// hokey way to do this, but it only happens once: since lhs is height 1
		// get rhs in order and then check which child, if any, it is
		HashMap<Integer, Integer> varmap = new HashMap<Integer, Integer>();
		int rhspos = 0;
		boolean hadSubrule = false;
		for (int i = 0; i < rhs.getNumChildren(); i++) {
			if (rhs.getChild(i).hasState()) {
				Symbol sym = rhs.getChild(i).getState();
				if (!s2i.containsKey(sym)) {
					if (debug) Debug.debug(debug, "Mapping state "+sym+" to "+nextID);
					s2i.put(sym, nextID);
					i2s.put(nextID++, sym);
				}
				rhskids.add(s2i.get(sym));
				TransducerLeftTree tltmap = r.getTRVM().getLHS(rhs.getChild(i));
				if (tltmap != null) {
					for (int j = 0; j < numkids; j++) {
						if (r.getLHS().getChild(j) == tltmap) {
							varmap.put(rhspos, j);
							if (debug) Debug.debug(debug, "Mapped rhs pos "+rhspos+" to lhs var "+j);
							break;
						}
					}
				}
			}
			// otherwise we get a new one-time state for this subtree and recurse down 
			else {
				int childid = nextID++;
				rhskids.add(childid);
				hadSubrule = true;
				makeSubTreeIPRule(childid, r, rhs.getChild(i), level, i==0);
			}
			rhspos++;
		}
		double vitweight = r.semiring.ONE();
		if (!hadSubrule)
			vitweight = r.getWeight();
		
		// TODO: must link in sub-rules somehow!
		IPRule ret = IPRule.get(state, rhslabel, rhskids, lhs, numkids, varmap, r.getWeight(), vitweight, r.semiring);
		if (debug) Debug.debug(debug, "Created "+ret);
		trsRules[level].addItem(ret);
		
		
	}
	
	// recursively add IPRules from part of a tree transducer rule
	// no lhs info. at best, do some mapping of variables and recurse along the way
	// if leftmost and no children, viterbi weight goes here
	private static void makeSubTreeIPRule(int state, TreeTransducerRule r, TransducerRightTree tree, int level, boolean isLeftmost) {
		boolean debug = false;
		int rhslabel = s2i.get(tree.getLabel());
		Vector<Integer> rhskids = new Vector<Integer>();
		int rhspos = 0;
		boolean hadSubrule = false;
		HashMap<Integer, Integer> varmap = new HashMap<Integer, Integer>();
		for (int i = 0; i < tree.getNumChildren(); i++) {
			if (tree.getChild(i).hasState()) {
				Symbol sym = tree.getChild(i).getState();
				if (!s2i.containsKey(sym)) {
					if (debug) Debug.debug(debug, "Mapping state "+sym+" to "+nextID);
					s2i.put(sym, nextID);
					i2s.put(nextID++, sym);
				}
				rhskids.add(s2i.get(sym));
				TransducerLeftTree tltmap = r.getTRVM().getLHS(tree.getChild(i));
				if (tltmap != null) {
					for (int j = 0; j < r.getLHS().getNumChildren(); j++) {
						if (r.getLHS().getChild(j) == tltmap) {
							varmap.put(rhspos, j);
							if (debug) Debug.debug(debug, "Mapped rhs pos "+rhspos+" to lhs var "+j);
							break;
						}
					}
				}
			}
			// otherwise we get a new one-time state for this subtree and recurse down 
			else {
				int childid = nextID++;
				rhskids.add(childid);
				hadSubrule = true;
				makeSubTreeIPRule(childid, r, tree.getChild(i), level, i==0);
			}
			rhspos++;
		}
		double vitweight = r.semiring.ONE();
		if (!hadSubrule && isLeftmost)
			vitweight = r.getWeight();
		// TODO: must link in sub-rules somehow!
		IPRule ret = IPRule.get(state, rhslabel, rhskids, -1, 0, varmap, r.semiring.ONE(), vitweight, r.semiring);
		if (debug) Debug.debug(debug, "Created "+ret);

		trsRules[level].addItem(ret);
		
		
	}
	
	// given a mapping of current pos -> new pos and a vector of anything, map things to new pos and return
	private static <T> Vector<T> reorderStateSeq(Vector<T> invec, int outsize, HashMap<Integer, Integer> map)  {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Reordering "+invec+" on "+map);
		Vector<T> outvec = new Vector<T>(outsize);
		outvec.setSize(outsize);
		for (int key : map.keySet()) {
			outvec.set(map.get(key), invec.get(key));
		}
		return outvec;
	}


	
	public static void main(String[] argv) {
//		try {
//			TrueRealSemiring s = new TrueRealSemiring();
//
//
//			String encoding = "euc-jp";
////			int beam = 0;
//			Vector<TreeTransducerRuleSet> chain = new Vector<TreeTransducerRuleSet>();
//			for (int i = 0; i < argv.length-2; i++)
//				chain.add(0, new TreeTransducerRuleSet(new BufferedReader(new InputStreamReader(new FileInputStream(argv[i]), encoding)), s));
//			StringTransducerRuleSet strs = new StringTransducerRuleSet(new BufferedReader(new InputStreamReader(new FileInputStream(argv[argv.length-2]), encoding)), s);
//			StringItem string = new StringItem(new BufferedReader(new InputStreamReader(new FileInputStream(argv[argv.length-1]), encoding)));
//
//			RTGRuleSet rtg = parse(string, chain, strs);
//			//RTGRuleSet rts = new RTGRuleSet(chain, strs, string, beam, 1);
//
//			System.out.println(rtg.toString());
//		} 
		
		try {
			TrueRealSemiring s = new TrueRealSemiring();


			String encoding = "euc-jp";
//			int beam = 0;
			Vector<TreeTransducerRuleSet> chain = new Vector<TreeTransducerRuleSet>();
			for (int i = 0; i < argv.length-1; i++)
				chain.add(0, new TreeTransducerRuleSet(new BufferedReader(new InputStreamReader(new FileInputStream(argv[i]), encoding)), s));
			TreeItem tree = new TreeItem(new BufferedReader(new InputStreamReader(new FileInputStream(argv[argv.length-1]), encoding)));

			parse(tree, chain);
			//RTGRuleSet rts = new RTGRuleSet(chain, strs, string, beam, 1);

		} 
		
		catch (Exception e) {
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
