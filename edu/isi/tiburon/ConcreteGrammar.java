package edu.isi.tiburon;

import java.io.IOException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Vector;

import edu.stanford.nlp.util.*;
// simple conversion of normal-form RuleSet
// all rules and states are saved here
public class ConcreteGrammar extends Grammar {

	private static Vector<GrammarRule> empty;
	static {
		empty = new Vector<GrammarRule>();
	}
	

	@Override
	public Iterable<GrammarRule> getForwardRules(Symbol s, int pos) throws UnusualConditionException {
		if (!allfrules.containsKey(s) || !allfrules.get(s).containsKey(pos))
			return empty;
		return allfrules.get(s).get(pos);
	}
	@Override
	public Iterable<GrammarRule> getForwardRules(Symbol s, int pos, Symbol l, int r ) throws UnusualConditionException {
		if (
				!frules.containsKey(s) || 
				!frules.get(s).containsKey(pos) || 
				!frules.get(s).get(pos).containsKey(l) ||
				!frules.get(s).get(pos).get(l).containsKey(r)
		)
			return empty;
		return frules.get(s).get(pos).get(l).get(r);
	}
	
	@Override
	public Iterable<GrammarRule> getBackwardRules(Symbol s, Symbol l, int r) throws UnusualConditionException {
		if (
				!bslabelrules.containsKey(s) || 
				!bslabelrules.get(s).containsKey(l) || 
				!bslabelrules.get(s).get(l).containsKey(r) 
		)
			return empty;
		return bslabelrules.get(s).get(l).get(r);
	}
	
	@Override
	public Iterable<GrammarRule> getBackwardRules(Symbol s) throws UnusualConditionException {
		boolean debug = false;
		if (!bsrules.containsKey(s)) {
			throw new UnusualConditionException("No rules start in "+s);
		}
		if (debug) Debug.debug(debug, "Got "+bsrules.get(s).size()+" for BS of "+s);
		return bsrules.get(s);
	}
	
	public PIterator<GrammarRule> getBSIter(Symbol s) throws UnusualConditionException {
		boolean debug = false;
		if (!bsrules.containsKey(s)) {
			throw new UnusualConditionException("No rules start in "+s);
		}
		if (debug) Debug.debug(debug, "getting new iterator for "+s);
		return new WrappedPIterator<GrammarRule> (bsrules.get(s).iterator());
	}
	
	public PIterator<GrammarRule> getBSIter(Symbol s, Symbol label, int rank) throws UnusualConditionException {
		boolean debug = false;
		if (
				bslabelrules.containsKey(s) &&
				bslabelrules.get(s).containsKey(label) &&
				bslabelrules.get(s).get(label).containsKey(rank)
		) {
			if (debug) Debug.debug(debug, "getting new iterator for "+s+", "+label+", "+rank);
			return new WrappedPIterator<GrammarRule> (bslabelrules.get(s).get(label).get(rank).iterator());
		}
		return new WrappedPIterator<GrammarRule>(empty.iterator());
		
	}
	
	// lazy lexical unfiltered forward
	public PIterator<GrammarRule> getLexFSIter() throws UnusualConditionException {
		return new WrappedPIterator<GrammarRule> (alltrules.iterator());
	}

	// lazy lexical filtered forward
	public PIterator<GrammarRule> getLexFSIter(Symbol label, int rank) throws UnusualConditionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Looking for rules with label "+label+" and rank "+rank);
		if (trules.containsKey(label) &&
				trules.get(label).containsKey(rank))
			return new WrappedPIterator<GrammarRule> (trules.get(label).get(rank).iterator());
		if (debug) Debug.debug(debug, "None found");
		return new WrappedPIterator<GrammarRule>(empty.iterator());
	}
	
	// gets all matching rules that have this state and an injected state
	@Override
	public PIterator<GrammarRule> getFSIter(Symbol s)
			throws UnusualConditionException {
		return new FSBinaryIterator(s);
	}
	
	// lazy unfiltered forward
	public PIterator<GrammarRule> getFSIter(Symbol s, int pos) throws UnusualConditionException {
		if (
				allfrules.containsKey(s) &&
				allfrules.get(s).containsKey(pos)
		)
			return new WrappedPIterator<GrammarRule> (allfrules.get(s).get(pos).iterator());

		return new WrappedPIterator<GrammarRule>(empty.iterator());
	}

	// lazy filtered forward
	public PIterator<GrammarRule> getFSIter(Symbol s, int pos, Symbol l, int r) throws UnusualConditionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "looking for rule with state "+s+" in pos "+pos+" "+" with label "+l+" and rank "+r);
		if (frules.goc(s).goc(pos).goc(l).containsKey(r)) {
			if (debug) Debug.debug(debug, "Found "+frules.get(s).get(pos).get(l).get(r).size()+" rules");
			return new WrappedPIterator<GrammarRule> (frules.get(s).get(pos).get(l).get(r).iterator());
		}
		if (debug )Debug.debug(debug, "None found");
		return new WrappedPIterator<GrammarRule>(empty.iterator());

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
		return alltrules;
	}

	
	
	// inject states as their inside cost is found. important for building new rules
	@Override
	boolean injectState(Symbol s, double wgt) throws UnusualConditionException {
		
		if (!states.containsKey(s))
			states.put(s, new Pair<Symbol, Double>(s, wgt));
		return true;
	}
	void reportRules() {
		Debug.prettyDebug("Concrete grammar has "+ruleCount+" rules");
		//printOutRules();
	}
	
	private void printOutRules() {
		Debug.debug(true, "bs rules");
		for (Symbol a : bsrules.keySet()) {
			for (GrammarRule b : bsrules.get(a)) {
				Debug.debug(true, b+"");
			}
		}
	}
	// states we can use -- validated by the chart, and in order by weight
	private HashMap<Symbol, Pair<Symbol, Double>> states;
	
	
	
	// non-lazy iterator over rules coming from a specified state and only using
	// injected states in the tail
	
	// list is built at initialization based on available states
	private class FSBinaryIterator implements PIterator<GrammarRule> {
		Vector<GrammarRule> rules;
		int next = 0;
		public FSBinaryIterator(Symbol state) throws UnusualConditionException {
			FixedPrioritiesPriorityQueue<GrammarRule> newrules = new FixedPrioritiesPriorityQueue<GrammarRule>();
			PIterator<GrammarRule> leftruleit = getFSIter(state, 0);
			while (leftruleit.hasNext()) {
				GrammarRule g = leftruleit.next();
				if (g.getChildren().size() == 1)
					newrules.add(g, -g.getWeight());
				else if (g.getChildren().size() == 2) {
					if (states.containsKey(g.getChild(1))) {
						newrules.add(g, states.get(g.getChild(1)).r()-g.getWeight());
					}
				}
				else {
					throw new UnusualConditionException("Non-binary or unary rule "+g);
				}
			}
			PIterator<GrammarRule> rightruleit = getFSIter(state, 1);
			while (rightruleit.hasNext()) {
				GrammarRule g = rightruleit.next();
				if (g.getChildren().size() == 2) {
					if (states.containsKey(g.getChild(0))) {
						newrules.add(g, states.get(g.getChild(0)).r()-g.getWeight());
					}
				}
				else {
					throw new UnusualConditionException("Non-binary or unary rule "+g);
				}
			}
			rules = new Vector<GrammarRule>(newrules.toSortedList());
		}
		public boolean hasNext() {
			return rules.size() > next;
		}
		public GrammarRule peek() throws NoSuchElementException {
			if (!hasNext())
				throw new NoSuchElementException("Asked for peek on empty PIterator");
			return rules.get(next);
		}
		public GrammarRule next() throws NoSuchElementException {
			if (!hasNext())
				throw new NoSuchElementException("Asked for peek on empty PIterator");
			return rules.get(next++);
		}
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for LazyIterator");
		}
		
	}
	
	// used by  constructors
	private void initialize() {
		trules = new PMap<Symbol, PMap<Integer, Vector<GrammarRule>>>();
		alltrules = new Vector<GrammarRule>();
		frules = new PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer,Vector<GrammarRule>>>>>(); 
		allfrules = new PMap<Symbol, PMap<Integer, Vector<GrammarRule>>>();
		bsrules = new PMap<Symbol, Vector<GrammarRule>>();
		bslabelrules = new PMap<Symbol, PMap<Symbol, PMap<Integer,Vector<GrammarRule>>>> ();
		states = new HashMap<Symbol, Pair<Symbol, Double>>(); 
		ruleCount = 0;
	}
	public ConcreteGrammar(RuleSet rs) throws ImproperConversionException {
		super(rs.getSemiring(), 0);
		startState = rs.getStartState();
		initialize();
		
		PMap<Symbol, FixedPrioritiesPriorityQueue<GrammarRule>> sortingBsrules = new PMap<Symbol, FixedPrioritiesPriorityQueue<GrammarRule>>();
		PMap<Symbol, PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>>> sortingBslabelrules = new PMap<Symbol, PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>>>();
		PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>> sortingTrules = new PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>>();
		FixedPrioritiesPriorityQueue<GrammarRule> sortingAlltrules = new FixedPrioritiesPriorityQueue<GrammarRule>();
		
		// build temp structure to prune on
		HashMap<Symbol, Vector<GrammarRule>> ruleSet = new HashMap<Symbol, Vector<GrammarRule>>();
		for (Rule r : rs.getRules()) {
			ConcreteGrammarRule g = new ConcreteGrammarRule(r, rs);
			ruleCount++;
			// build temp structure to prune on
			if (!ruleSet.containsKey(g.getState())) {
				ruleSet.put(g.getState(), new Vector<GrammarRule>());
			}
			ruleSet.get(g.getState()).add(g);
		}
		ruleSet = pruneUseless(startState, ruleSet);
		for (Symbol state : ruleSet.keySet()) {
			for (GrammarRule g : ruleSet.get(state)) {
				if (!sortingBsrules.containsKey(state)) {
					sortingBsrules.put(state, new FixedPrioritiesPriorityQueue<GrammarRule>());
					//		sortingBslabelrules.put(state, new HashMap<Symbol, HashMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>>());
					//		bslabelrules.put(state, new HashMap<Symbol, HashMap<Integer, Vector<GrammarRule>>>());
				}
				sortingBsrules.get(state).add(g, -g.getWeight());
				Symbol label = g.getLabel();
				int rank = -1;
				if (label == null) {
					label = Symbol.getEpsilon();
					rank = 1;
				}
				else
					rank = g.getChildren().size();
				//			if (!sortingBslabelrules.get(state).containsKey(label)) {
				//				sortingBslabelrules.get(state).put(label, new HashMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>());
				//				bslabelrules.get(state).put(label, new HashMap<Integer, Vector<GrammarRule>>());
				//			}
				if (!sortingBslabelrules.goc(state).goc(label).containsKey(rank)) 
					sortingBslabelrules.get(state).get(label).put(rank, new FixedPrioritiesPriorityQueue<GrammarRule>());
				sortingBslabelrules.get(state).get(label).get(rank).add(g, -g.getWeight());
				//			if (!trules.containsKey(label))
				//				trules.put(label, new HashMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>());
				if (!sortingTrules.goc(label).containsKey(rank))
					sortingTrules.get(label).put(rank, new FixedPrioritiesPriorityQueue<GrammarRule>());

				sortingTrules.get(label).get(rank).add(g, -g.getWeight());
				if (rank == 0)
					sortingAlltrules.add(g, -g.getWeight());
				for (int i = 0; i < g.getChildren().size(); i++) {
					Symbol c = g.getChildren().get(i);				

					// categorize by state, pos, label (might be eps
					//
					//				if (!frules.containsKey(c)) {
					//					frules.put(c, new PMap<Integer, PMap<Symbol, PMap<Integer,Vector<GrammarRule>>>>());
					//					allfrules.put(c, new PMap<Integer, Vector<GrammarRule>>());
					//				}
					if (!allfrules.goc(c).containsKey(i)) {
						//				frules.get(c).put(i, new PMap<Symbol, PMap<Integer,Vector<GrammarRule>>>());
						allfrules.get(c).put(i, new Vector<GrammarRule>());
					}
					//				if (!frules.get(c).get(i).containsKey(label))
					//					frules.get(c).get(i).put(label, new PMap<Integer,Vector<GrammarRule>>());
					if (!frules.goc(c).goc(i).goc(label).containsKey(rank))
						frules.get(c).get(i).get(label).put(rank, new Vector<GrammarRule>());
					allfrules.get(c).get(i).add(g);
					frules.get(c).get(i).get(label).get(rank).add(g);
				}

			}
		}
		alltrules = new Vector<GrammarRule>(sortingAlltrules.toSortedList());
		for (Symbol s : sortingTrules.keySet()) {
			for (int r : sortingTrules.get(s).keySet()) {
				trules.goc(s).put(r, new Vector<GrammarRule>(sortingTrules.get(s).get(r).toSortedList()));
			}
		}
		for (Symbol s : sortingBsrules.keySet()) {
			bsrules.put(s, new Vector<GrammarRule>(sortingBsrules.get(s).toSortedList()));
			for (Symbol l : sortingBslabelrules.get(s).keySet()) {
				for (Integer i : sortingBslabelrules.get(s).get(l).keySet())
					bslabelrules.goc(s).goc(l).put(i, new Vector<GrammarRule>(sortingBslabelrules.get(s).get(l).get(i).toSortedList()));
			}
		}

	}
	
	// form concrete grammar from TS transducer grammar that has been exhaustively created

	public ConcreteGrammar(TSOTFGrammar tsotf) throws ImproperConversionException {
		super(tsotf.getSemiring(), 0);
		boolean debug = true;
		startState = tsotf.getStartState();
		initialize();
		
		PMap<Symbol, FixedPrioritiesPriorityQueue<GrammarRule>> sortingBsrules = new PMap<Symbol, FixedPrioritiesPriorityQueue<GrammarRule>>();
		PMap<Symbol, PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>>> sortingBslabelrules = new PMap<Symbol, PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>>>();
		PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>> sortingTrules = new PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>>();
		FixedPrioritiesPriorityQueue<GrammarRule> sortingAlltrules = new FixedPrioritiesPriorityQueue<GrammarRule>();
		
		for (ConcreteGrammarRule g : tsotf.getRules()) {
			ruleCount++;
			if (!sortingBsrules.containsKey(g.getState())) {
				sortingBsrules.put(g.getState(), new FixedPrioritiesPriorityQueue<GrammarRule>());
		//		sortingBslabelrules.put(g.getState(), new HashMap<Symbol, HashMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>>());
		//		bslabelrules.put(g.getState(), new HashMap<Symbol, HashMap<Integer, Vector<GrammarRule>>>());
			}
			sortingBsrules.get(g.getState()).add(g, -g.getWeight());
			Symbol label = g.getLabel();
			int rank = -1;
			if (label == null) {
				label = Symbol.getEpsilon();
				rank = 1;
			}
			else
				rank = g.getChildren().size();
//			if (!sortingBslabelrules.get(g.getState()).containsKey(label)) {
//				sortingBslabelrules.get(g.getState()).put(label, new HashMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>());
//				bslabelrules.get(g.getState()).put(label, new HashMap<Integer, Vector<GrammarRule>>());
//			}
			if (!sortingBslabelrules.goc(g.getState()).goc(label).containsKey(rank)) 
				sortingBslabelrules.get(g.getState()).get(label).put(rank, new FixedPrioritiesPriorityQueue<GrammarRule>());
			sortingBslabelrules.get(g.getState()).get(label).get(rank).add(g, -g.getWeight());
//			if (!trules.containsKey(label))
//				trules.put(label, new HashMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>());
			if (!sortingTrules.goc(label).containsKey(rank))
				sortingTrules.get(label).put(rank, new FixedPrioritiesPriorityQueue<GrammarRule>());

			sortingTrules.get(label).get(rank).add(g, -g.getWeight());
			if (rank == 0)
				sortingAlltrules.add(g, -g.getWeight());
			for (int i = 0; i < g.getChildren().size(); i++) {
				Symbol c = g.getChildren().get(i);				

				// categorize by state, pos, label (might be eps
//
//				if (!frules.containsKey(c)) {
//					frules.put(c, new PMap<Integer, PMap<Symbol, PMap<Integer,Vector<GrammarRule>>>>());
//					allfrules.put(c, new PMap<Integer, Vector<GrammarRule>>());
//				}
				if (!allfrules.goc(c).containsKey(i)) {
	//				frules.get(c).put(i, new PMap<Symbol, PMap<Integer,Vector<GrammarRule>>>());
					allfrules.get(c).put(i, new Vector<GrammarRule>());
				}
//				if (!frules.get(c).get(i).containsKey(label))
//					frules.get(c).get(i).put(label, new PMap<Integer,Vector<GrammarRule>>());
				if (!frules.goc(c).goc(i).goc(label).containsKey(rank))
					frules.get(c).get(i).get(label).put(rank, new Vector<GrammarRule>());
				allfrules.get(c).get(i).add(g);
				frules.get(c).get(i).get(label).get(rank).add(g);
			}

		}
		alltrules = new Vector<GrammarRule>(sortingAlltrules.toSortedList());
		for (Symbol s : sortingTrules.keySet()) {
			for (int r : sortingTrules.get(s).keySet()) {
				trules.goc(s).put(r, new Vector<GrammarRule>(sortingTrules.get(s).get(r).toSortedList()));
			}
		}
		for (Symbol s : sortingBsrules.keySet()) {
			bsrules.put(s, new Vector<GrammarRule>(sortingBsrules.get(s).toSortedList()));
			for (Symbol l : sortingBslabelrules.get(s).keySet()) {
				for (Integer i : sortingBslabelrules.get(s).get(l).keySet())
					bslabelrules.goc(s).goc(l).put(i, new Vector<GrammarRule>(sortingBslabelrules.get(s).get(l).get(i).toSortedList()));
			}
		}
	}
	
	// form concrete grammar from on-the-fly grammar by traversing through states
	public ConcreteGrammar(Grammar otfg) throws ImproperConversionException, UnusualConditionException {
		super(otfg.getSemiring(), 0);
		boolean debug = false;
		startState = otfg.getStartState();
		initialize();
		PMap<Symbol, FixedPrioritiesPriorityQueue<GrammarRule>> sortingBsrules = new PMap<Symbol, FixedPrioritiesPriorityQueue<GrammarRule>>();
		PMap<Symbol, PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>>> sortingBslabelrules = new PMap<Symbol, PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>>>();
		PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>> sortingTrules = new PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>>();
		FixedPrioritiesPriorityQueue<GrammarRule> sortingAlltrules = new FixedPrioritiesPriorityQueue<GrammarRule>();
	
		HashSet<Symbol> usedBackwardStates = new HashSet<Symbol>();
		Vector<Symbol> pendingBackwardStates = new Vector<Symbol>();

		pendingBackwardStates.add(otfg.getStartState());
		usedBackwardStates.add(otfg.getStartState());

		
		// go down the forest. 
		while (!pendingBackwardStates.isEmpty()) {
			Symbol st = pendingBackwardStates.remove(0);
			if (debug) Debug.debug(debug, "Processing "+st);
			if (!sortingBsrules.containsKey(st)) {
				sortingBsrules.put(st, new FixedPrioritiesPriorityQueue<GrammarRule>());
//				sortingBslabelrules.put(st, new PMap<Symbol, PMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>>());
//				bslabelrules.put(st, new PMap<Symbol, PMap<Integer, Vector<GrammarRule>>>());

			}
			for (GrammarRule g : otfg.getBackwardRules(st)) {
				//Debug.prettyDebug("Saw "+g);
				ruleCount++;
				for (Symbol c : g.getChildren()) {
					if (!usedBackwardStates.contains(c)) {
						usedBackwardStates.add(c);
						pendingBackwardStates.add(c);
					}
				}

				sortingBsrules.get(g.getState()).add(g, -g.getWeight());
				Symbol label = g.getLabel();
				int rank = -1;
				if (label == null) {
					label = Symbol.getEpsilon();
					rank = 0;
				}
				else
					rank = g.getChildren().size();
//				if (!sortingBslabelrules.get(g.getState()).containsKey(label)) {
//					sortingBslabelrules.get(g.getState()).put(label, new HashMap<Integer, FixedPrioritiesPriorityQueue<GrammarRule>>());
//					bslabelrules.get(g.getState()).put(label, new HashMap<Integer, Vector<GrammarRule>>());
//				}
				if (!sortingBslabelrules.goc(g.getState()).goc(label).containsKey(rank)) 
					sortingBslabelrules.get(g.getState()).get(label).put(rank, new FixedPrioritiesPriorityQueue<GrammarRule>());
				sortingBslabelrules.get(g.getState()).get(label).get(rank).add(g, -g.getWeight());


				if (!sortingTrules.goc(label).containsKey(rank))
					sortingTrules.get(label).put(rank, new FixedPrioritiesPriorityQueue<GrammarRule>());

				sortingTrules.get(label).get(rank).add(g, -g.getWeight());
				if (rank == 0)
					sortingAlltrules.add(g, -g.getWeight());
				
				for (int i = 0; i < g.getChildren().size(); i++) {
					Symbol c = g.getChildren().get(i);				

					// categorize by state, pos, label (might be eps

//					if (!frules.containsKey(c)) {
//						frules.put(c, new HashMap<Integer, HashMap<Symbol, HashMap<Integer,Vector<GrammarRule>>>>());
//						allfrules.put(c, new HashMap<Integer, Vector<GrammarRule>>());
//					}
					if (!allfrules.goc(c).containsKey(i)) {
						allfrules.get(c).put(i, new Vector<GrammarRule>());
					}
//					if (!frules.get(c).get(i).containsKey(label))
//						frules.get(c).get(i).put(label, new HashMap<Integer,Vector<GrammarRule>>());
					if (!frules.goc(c).goc(i).goc(label).containsKey(rank))
						frules.get(c).get(i).get(label).put(rank, new Vector<GrammarRule>());
					allfrules.get(c).get(i).add(g);
					frules.get(c).get(i).get(label).get(rank).add(g);
				}

			}
		}
		alltrules = new Vector<GrammarRule>(sortingAlltrules.toSortedList());
		for (Symbol s : sortingTrules.keySet()) {
			for (int r : sortingTrules.get(s).keySet()) {
				trules.goc(s).put(r, new Vector<GrammarRule>(sortingTrules.get(s).get(r).toSortedList()));
			}
		}
		for (Symbol s : sortingBsrules.keySet()) {
			bsrules.put(s, new Vector<GrammarRule>(sortingBsrules.get(s).toSortedList()));
			if (!sortingBslabelrules.containsKey(s))
				continue;
			for (Symbol l : sortingBslabelrules.get(s).keySet()) {
				for (Integer i : sortingBslabelrules.get(s).get(l).keySet())
					bslabelrules.goc(s).goc(l).put(i, new Vector<GrammarRule>(sortingBslabelrules.get(s).get(l).get(i).toSortedList()));
			}
		}
		if (debug) Debug.debug(debug, "Converted "+ruleCount+" rules");
	}
	
	// form ConcreteGrammar from single TreeTransducerRule RHS by breaking it down into a 
	// single-derivation RTG. 
	public ConcreteGrammar(TreeTransducerRule ttr) {
		super(ttr.getSemiring(), 0);
		boolean debug = false;
		startState = SymbolFactory.getStateSymbol();
		
		initialize();
		// TODO: fill in forward rules!!
		for (ConcreteGrammarRule r : tree2Rules(ttr.getRHS(), startState)) {
			ruleCount++;
			if (debug) Debug.debug(debug, "Built "+r);
			Vector<GrammarRule> v = new Vector<GrammarRule>();
			v.add(r);
			bsrules.put(r.getState(), v);
			bslabelrules.put(r.getState(), new PMap<Symbol, PMap<Integer,Vector<GrammarRule>>>());
			bslabelrules.get(r.getState()).put(r.getLabel(), new PMap<Integer,Vector<GrammarRule>>());
			bslabelrules.get(r.getState()).get(r.getLabel()).put(r.getChildren().size(), v);
		}
		if (debug) Debug.debug(debug, "Done building grammar "+this+" from "+ttr+". Start state is "+getStartState());
	}
	
	// walk down the tree and build rules
	
	private Vector<ConcreteGrammarRule> tree2Rules(TransducerRightTree tree, Symbol state) {
		Vector<ConcreteGrammarRule> ret = new Vector<ConcreteGrammarRule>();
		Vector<Symbol> children = new Vector<Symbol>();
		// normal case
		if (tree.hasLabel()) {
			for (int i = 0; i < tree.getNumChildren(); i++) {
				TransducerRightTree child = tree.getChild(i);
				if (child.hasVariable())
					children.add(PairSymbol.get(child.getState(), child.getVariable()));
				else {
					Symbol nextstate = SymbolFactory.getStateSymbol();
					children.add(nextstate);
					ret.addAll(tree2Rules(child, nextstate));
				}
			}
			ConcreteGrammarRule rule = new ConcreteGrammarRule(state, tree.getLabel(), children, getSemiring().ONE());
			ret.add(rule);
		}
		// eps-output case -- convert to grammar with eps label and single child
		else {
			children.add(PairSymbol.get(tree.getState(), tree.getVariable()));
			ConcreteGrammarRule rule = new ConcreteGrammarRule(state, null, children, getSemiring().ONE());
			ret.add(rule);
		}
		
		return ret;
	}
	
	
	
	// remove unreachable rules
	// based on RTGRuleSet's pruneUseless, but operating only from a BS perspective -- I don't think
	// it needs two passes
	// TODO: get citation!!
	public HashMap<Symbol, Vector<GrammarRule>> pruneUseless(Symbol start, HashMap<Symbol, Vector<GrammarRule>> orig) {
		boolean debug = false;
		HashMap<Symbol, Vector<GrammarRule>> ret = new HashMap<Symbol, Vector<GrammarRule>>();
		HashSet<Symbol> okStates = new HashSet<Symbol>();
		Vector<Symbol> todoStates = new Vector<Symbol>();
		HashSet<Symbol> seenStates = new HashSet<Symbol>();
		HashMap<Symbol, Vector<GrammarRule>> left = new HashMap<Symbol, Vector<GrammarRule>>(orig);
		HashMap<Symbol, Vector<GrammarRule>> next = new HashMap<Symbol, Vector<GrammarRule>>();
		int lastWork = 0;
		ruleCount = 0;
		// repeat until no more progress is made:
		do {
			lastWork = 0;
			todoStates.clear();
			seenStates.clear();
			next.clear();
			todoStates.add(start);
			seenStates.add(startState);
			while (!todoStates.isEmpty()) {
				Symbol state = todoStates.remove(0);
				if (debug) Debug.debug(debug, "Traversing "+state);
				if (!left.containsKey(state) && state.equals(start))
					break;
				for (GrammarRule r : left.get(state)) {
						boolean isOkay = true;
						// check that all leaves are already seen (assumed normal form)
						// propagate down unclosed states
						for (Symbol c : r.getChildren()) {
							if (!okStates.contains(c)) {
								isOkay = false;
								if (!seenStates.contains(c)) {
									seenStates.add(c);
									todoStates.add(c);
								}
							}
						}
						// if okay, add it and don't look at it again
						// okay also sets the state as okay
						// if not, we might want it next time
						if (isOkay) {
							ruleCount++;
							lastWork++;
							okStates.add(state);
							if (!ret.containsKey(state))
								ret.put(state, new Vector<GrammarRule>());
							ret.get(state).add(r);
						}
						else {
							if (debug) Debug.debug(debug, r+" for next time");
							if (!next.containsKey(state))
								next.put(state, new Vector<GrammarRule>());
							next.get(state).add(r);
						}
					
				}
			}
			left = new HashMap<Symbol, Vector<GrammarRule>>(next);
			if (debug) Debug.debug(debug, "Added "+lastWork+" rules");
		} while (lastWork > 0);
		
		return ret;
	}
	
	private Symbol startState;
	
	
	// rules by dst state, position of that state, and label
	
	private PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer,Vector<GrammarRule>>>>> frules;
	// rules by dst state and position of that state (better way to hold these?)
	private PMap<Symbol, PMap<Integer, Vector<GrammarRule>>> allfrules;
	
	// rules by head state
	private PMap<Symbol, Vector<GrammarRule>> bsrules;
	// rules by head state, label, rank
	private PMap<Symbol, PMap<Symbol, PMap<Integer,Vector<GrammarRule>>>> bslabelrules;

	// terminal rules by label and rank
	private PMap<Symbol, PMap<Integer, Vector<GrammarRule>>> trules;
	// all terminal rules
	private Vector<GrammarRule> alltrules;

	// count the rules as we form them
	private int ruleCount;
	// all terminal rules
	
	// 1-best experiment
	public static void main(String argv[]) {
		TropicalSemiring semiring = new TropicalSemiring();
//		try {
//			String choice = argv[2];
//			
//			if (!choice.equals("new") && !choice.equals("newer") && !choice.equals("old"))
//				throw new UnusualConditionException("Need to specify new, newer or old");
//			RTGRuleSet rtg = new RTGRuleSet(argv[0], "utf-8", semiring);
//			int k = Integer.parseInt(argv[1]);
//			
//			
////			rtg.removeEpsilons();
////			System.out.println(rtg.toString());
//			ConcreteGrammar gr = new ConcreteGrammar(rtg);
//			Symbol start = rtg.getStartState();
//			rtg = null;
//			if (choice.equals("new"))
//				gr.printDeriv(gr.getIntegratedOneBest(start));
//			else if (choice.equals("newer"))
//				gr.printDeriv(gr.getRuleIntegratedOneBest(start,0));
//			else if (choice.equals("old")) {
//				int bound = Integer.parseInt(argv[3]);
//				gr.printDeriv(gr.getOneBest(start, bound));
//			}
//			else throw new UnusualConditionException("Need to specify new or old");
////			
//			for (int i = 2; i <= k; i++) {
//				//Debug.prettyDebug("Printing "+i);
//				gr.printDeriv(gr.getKthBest(start, i));
//			}
			
//		}
//		catch (DataFormatException e) {
//			System.err.println("Bad data format reading rtg "+argv[0]);
//			StackTraceElement elements[] = e.getStackTrace();
//			int n = elements.length;
//			for (int i = 0; i < n; i++) {       
//				System.err.println(e.toString()+": "+elements[i].getFileName() + ":" 
//						+ elements[i].getLineNumber() 
//						+ ">> " 
//						+ elements[i].getMethodName() + "()");
//			}
//			System.exit(-1);
//		}
//		catch (IOException e) {
//			System.err.println("IO error reading rtg "+argv[0]);
//			StackTraceElement elements[] = e.getStackTrace();
//			int n = elements.length;
//			for (int i = 0; i < n; i++) {       
//				System.err.println(e.toString()+": "+elements[i].getFileName() + ":" 
//						+ elements[i].getLineNumber() 
//						+ ">> " 
//						+ elements[i].getMethodName() + "()");
//			}
//			System.exit(-1);
//		}
//		catch (ImproperConversionException e) {
//			System.err.println("Couldn't convert grammar -- probably not in normal form");
//			StackTraceElement elements[] = e.getStackTrace();
//			int n = elements.length;
//			for (int i = 0; i < n; i++) {       
//				System.err.println(e.toString()+": "+elements[i].getFileName() + ":" 
//						+ elements[i].getLineNumber() 
//						+ ">> " 
//						+ elements[i].getMethodName() + "()");
//			}
//			System.exit(-1);
//		}
//		catch (UnusualConditionException e) {
//			System.err.println("Unusual Condition!");
//			StackTraceElement elements[] = e.getStackTrace();
//			int n = elements.length;
//			for (int i = 0; i < n; i++) {       
//				System.err.println(e.toString()+": "+elements[i].getFileName() + ":" 
//						+ elements[i].getLineNumber() 
//						+ ">> " 
//						+ elements[i].getMethodName() + "()");
//			}
//			System.exit(-1);
//		}
	}
}
