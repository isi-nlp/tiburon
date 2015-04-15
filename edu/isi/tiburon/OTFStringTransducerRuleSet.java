package edu.isi.tiburon;

import java.util.HashSet;
import java.util.Vector;

import edu.stanford.nlp.util.FixedPrioritiesPriorityQueue;

// tree-string transducer rule set containing what we need for otf operations
// (to begin with, just backward app, as forward app isn't that interesting)

// to begin with, binary rules only
// TODO: increase complexity beyond binary?

public class OTFStringTransducerRuleSet {
	// rhs lexical rules by label
	private PMap<Symbol, Vector<StringTransducerRule>> trules;
	// rhs nonlexical rules by state sequence
	// TODO: make VecSymbol more efficient!
	private PMap<VecSymbol, Vector<StringTransducerRule>> oldFrules;
	// tree structure indexed by, for each child level, state.
	private PMap<Symbol, Pair<Vector<StringTransducerRule>, PMap<Symbol, Pair>>> frules;
	// nonlexical rules by a state in the rhs. boolean indicates if it is the left state
	private PMap<Symbol, Vector<Pair<StringTransducerRule, Boolean>>> binaryFrules;
	
	// track whether states can be left, right, or solo
	private HashSet<Symbol> soloStates;
	private HashSet<Symbol> leftStates;
	private HashSet<Symbol> rightStates;
	
	private Symbol startState;
	// backward app, forward-star lexical rules with no index
	public Iterable<StringTransducerRule> getLexRules(Symbol s) { 
		if(trules.containsKey(s))
			return trules.get(s);
		return empty;
	}
	// backward app, forward-star nonlexical rules based on state sequence
//	public Iterable<StringTransducerRule> getForwardRules(VecSymbol vs) { 
//		if(oldFrules.containsKey(vs))
//			return oldFrules.get(vs);
//		nullreqCount++;
//		boolean debug = false;
//		if (debug && nullreqCount % 1000 == 0)
//			Debug.debug(debug, "Made "+nullreqCount+" empty requests");
//		return empty;
//	}
	public Iterable<StringTransducerRule> getForwardRules(Vector<Symbol> vs) { 
		Pair<Vector<StringTransducerRule>, PMap<Symbol, Pair>> currPair = frules.get(vs.get(0));
		if (currPair == null)
			return empty;
		int currChild = 1;
		while (currChild < vs.size()) {
			if (!currPair.r().containsKey(vs.get(currChild)))
				return empty;
			currPair = currPair.r().get(vs.get(currChild++));
		}
		return currPair.l();
	}
	
	
	
	public void getNullCount() {
		Debug.debug(true, "Made "+nullreqCount+" empty requests");
	}
	private static Vector<StringTransducerRule> empty;
	static {
		empty = new Vector<StringTransducerRule>();
	}
	private int nullreqCount;
	private void initialize() {
		trules = new PMap<Symbol, Vector<StringTransducerRule>>();
		frules = new PMap<Symbol, Pair<Vector<StringTransducerRule>, PMap<Symbol, Pair>>>();
		binaryFrules = new PMap<Symbol, Vector<Pair<StringTransducerRule, Boolean>>>(); 
		soloStates = new HashSet<Symbol> ();
		leftStates = new HashSet<Symbol> ();
		rightStates = new HashSet<Symbol>();
		nullreqCount = 0;
	}
	public Symbol getStartState() { return startState; }
	public OTFStringTransducerRuleSet(StringTransducerRuleSet trans) throws UnusualConditionException {
		initialize();
		startState = trans.getStartState();
		
		PMap<Symbol, FixedPrioritiesPriorityQueue<StringTransducerRule>> sortingTrules = 
			new PMap<Symbol, FixedPrioritiesPriorityQueue<StringTransducerRule>>();
//		PMap<VecSymbol, FixedPrioritiesPriorityQueue<StringTransducerRule>> sortingFrules = 
//			new PMap<VecSymbol, FixedPrioritiesPriorityQueue<StringTransducerRule>>();
		FixedPrioritiesPriorityQueue<StringTransducerRule> sortingFrules = new FixedPrioritiesPriorityQueue<StringTransducerRule>();

		PMap<Symbol, FixedPrioritiesPriorityQueue<StringTransducerRule>> sortingBinaryFrules = 
			new PMap<Symbol, FixedPrioritiesPriorityQueue<StringTransducerRule>>();

		
		for (Symbol state : trans.getStates()) {
			for (TransducerRule rawr : trans.getRulesOfType(state)) {
				StringTransducerRule r = (StringTransducerRule)rawr;
				
				// get the state signature or label -- normal form required!
				Symbol label = null;
				Vector<Symbol> states = new Vector<Symbol>();
				for (TransducerRightString c : r.getRHS().getItemLeaves()) {
					if (c.hasState())
						states.add(c.getState());
					else if (c.hasLabel()) {
						if (label != null)
							throw new UnusualConditionException("More than one label in "+r);
						label = c.getLabel();
					}
					else {
						throw new UnusualConditionException("Not sure what to do with child in "+r);
					}
				}
				if (label != null && states.size() != 0)
					throw new UnusualConditionException("Saw label and states in "+r);
				if (label != null) {
					if (!sortingTrules.containsKey(label))
						sortingTrules.put(label, new FixedPrioritiesPriorityQueue<StringTransducerRule>());
					sortingTrules.get(label).add(r, -r.getWeight());
				}
				else if (states.size() > 0) {
					// binary constraint for now
					if (states.size() > 2)
						throw new UnusualConditionException("Can only handle binary rules: "+r);
					if (states.size() == 1)
						soloStates.add(states.get(0));
					else {
						leftStates.add(states.get(0));
						rightStates.add(states.get(1));
					}
					// indexed one state at a time
					for (Symbol c : states) {
						if (!sortingBinaryFrules.containsKey(c))
							sortingBinaryFrules.put(c, new FixedPrioritiesPriorityQueue<StringTransducerRule>());
						sortingBinaryFrules.get(c).add(r, -r.getWeight());
					}
//					VecSymbol vs = SymbolFactory.getVecSymbol(states);
//					if (!sortingFrules.containsKey(vs))
//						sortingFrules.put(vs, new FixedPrioritiesPriorityQueue<StringTransducerRule>());
//					sortingFrules.get(vs).add(r, -r.getWeight());
					sortingFrules.add(r, -r.getWeight());
					
				}
				else {
					throw new UnusualConditionException("Saw neither label nor states in "+r);
				}
			}

		}
		for (Symbol c : sortingTrules.keySet())
				trules.put(c, new Vector<StringTransducerRule>(sortingTrules.get(c).toSortedList()));
		
		while (!sortingFrules.isEmpty()) {
			StringTransducerRule rule = sortingFrules.next();
			Vector<TransducerRightString> rhs = rule.getRHS().getItemLeaves();
			Symbol state = rhs.get(0).getState();
			if (!frules.containsKey(state))
				frules.put(state, new Pair<Vector<StringTransducerRule>, PMap<Symbol, Pair>>(new Vector<StringTransducerRule>(), new PMap<Symbol, Pair>()));
			Pair<Vector<StringTransducerRule>, PMap<Symbol, Pair>> currPair = frules.get(state);
			for (int i = 1; i < rhs.size(); i++) {
				Symbol c = rhs.get(i).getState();
				if (!currPair.r().containsKey(c))
					currPair.r().put(c, new Pair<Vector<StringTransducerRule>, PMap<Symbol, Pair>>(new Vector<StringTransducerRule>(), new PMap<Symbol, Pair>()));
				currPair = currPair.r().get(c);
			}
			currPair.l().add(rule);
		}
//		for (VecSymbol vs : sortingFrules.keySet())
//			frules.put(vs, new Vector<StringTransducerRule>(sortingFrules.get(vs).toSortedList()));
		for (Symbol c : sortingBinaryFrules.keySet()) {
			binaryFrules.put(c, new Vector<Pair<StringTransducerRule, Boolean>>());
			for (StringTransducerRule r : sortingBinaryFrules.get(c).toSortedList()) {
				Vector<TransducerRightString> rhs = r.getRHS().getItemLeaves();
				if (rhs.get(0).getState().equals(c))
					binaryFrules.get(c).add(new Pair<StringTransducerRule, Boolean>(r, true));
				if (rhs.size() > 1 && rhs.get(1).getState().equals(c))
					binaryFrules.get(c).add(new Pair<StringTransducerRule, Boolean>(r, false));
			}
		}
		
	}
	// determine if a state appears solo, left, or right
	public boolean isSolo(Symbol s) {
		return soloStates.contains(s);
	}
	public boolean isLeft(Symbol s) {
		return leftStates.contains(s);
	}
	public boolean isRight(Symbol s) {
		return rightStates.contains(s);
	}
	
	// return rules that have the specified state in rhs, with an indication if that state is to the left 
	// (or solo)
	public Iterable<Pair<StringTransducerRule, Boolean>> getBinaryForwardRules(Symbol s) {
		return binaryFrules.get(s);
	}
}
