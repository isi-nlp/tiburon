package edu.isi.tiburon;

import java.util.Vector;

// pretending a transducer rule is a concrete grammar
public class TransducerAsGrammar extends Grammar {

	private static Vector<GrammarRule> empty;
	static {
		empty = new Vector<GrammarRule>();
	}
	private Symbol startState;
	
//	private PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer,Vector<TransducerAsGrammarRule>>>>> frules;
	// rules by dst state and position of that state (better way to hold these?)
	private PMap<Symbol, PMap<Integer, Vector<GrammarRule>>> allfrules;
	
	// rules by head state
	private PMap<Symbol, Vector<GrammarRule>> bsrules;
	// rules by head state, label, rank
	private PMap<Symbol, PMap<Symbol, PMap<Integer,Vector<GrammarRule>>>> bslabelrules;

	// terminal rules by label and rank
//	private PMap<Symbol, PMap<Integer, Vector<TransducerAsGrammarRule>>> trules;
	// all terminal rules
	private Vector<GrammarRule> alltrules;

	// mapping of states to TRT nodes in this grammar
	private PMap<Symbol, TransducerRightTree> state2Node;
	
	// used by  constructors
	private void initialize() {
	//	trules = new PMap<Symbol, PMap<Integer, Vector<TransducerAsGrammarRule>>>();
		alltrules = new Vector<GrammarRule>();
	//	frules = new PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer,Vector<TransducerAsGrammarRule>>>>>(); 
		allfrules = new PMap<Symbol, PMap<Integer, Vector<GrammarRule>>>();
		bsrules = new PMap<Symbol, Vector<GrammarRule>>();
		bslabelrules = new PMap<Symbol, PMap<Symbol, PMap<Integer,Vector<GrammarRule>>>> ();
		state2Node = new PMap<Symbol, TransducerRightTree> ();

	}
	// form ConcreteGrammar from single TreeTransducerRule RHS by breaking it down into a 
	// single-derivation RTG. 
	public TransducerAsGrammar(TreeTransducerRule ttr) {
		super(ttr.getSemiring(), 0);
		initialize();
		boolean debug = false;
		startState = SymbolFactory.getStateSymbol();
		state2Node.put(startState, ttr.getRHS());
		// TODO: fill in forward rules!!
		for (TransducerAsGrammarRule r : tree2Rules(ttr.getRHS(), startState)) {
			if (debug) Debug.debug(debug, "Built "+r);
			Vector<GrammarRule> v = new Vector<GrammarRule>();
			v.add(r);
			bsrules.put(r.getState(), v);
			bslabelrules.goc(r.getState()).goc(r.getLabel()).put(r.getChildren().size(), v);
			// rule is terminal if it has no children or if all its nodes children have variables
			TransducerRightTree node = r.getNode();
			boolean isTerm = false;
			if (node.getNumChildren() == 0)
				isTerm = true;
			if (!isTerm) {
				isTerm = true;
				for (int i = 0; i < node.getNumChildren(); i++) {
					TransducerRightTree child = node.getChild(i);
					if (!child.hasVariable()) {
						isTerm = false;
						break;
					}
				}
			}
			if (isTerm) {
				if (debug) Debug.debug(debug, r+" is considered terminal");
				alltrules.add(r);
			}
			else {
				for (int i = 0; i < r.getChildren().size(); i++) {
					if (!allfrules.goc(r.getChild(i)).containsKey(i))
						allfrules.get(r.getChild(i)).put(i, new Vector<GrammarRule>());
					allfrules.get(r.getChild(i)).get(i).add(r);
				}
			}
		}
		if (debug) Debug.debug(debug, "Done building grammar "+this+" from "+ttr+". Start state is "+getStartState());
	}
	
	// walk down the tree and build rules
	
	private Vector<TransducerAsGrammarRule> tree2Rules(TransducerRightTree tree, Symbol state) {
		Vector<TransducerAsGrammarRule> ret = new Vector<TransducerAsGrammarRule>();
		Vector<Symbol> children = new Vector<Symbol>();
		// normal case
		if (tree.hasLabel()) {
			for (int i = 0; i < tree.getNumChildren(); i++) {
				TransducerRightTree child = tree.getChild(i);
				if (child.hasVariable())
					children.add(PairSymbol.get(child.getState(), child.getVariable()));
				else {
					Symbol nextstate = SymbolFactory.getStateSymbol();
					state2Node.put(nextstate, child);
					children.add(nextstate);
					ret.addAll(tree2Rules(child, nextstate));
				}
			}
			TransducerAsGrammarRule rule = new TransducerAsGrammarRule(tree, state, children);
			ret.add(rule);
		}
		// eps-output case -- convert to grammar with eps label and single child
		else {
			children.add(PairSymbol.get(tree.getState(), tree.getVariable()));
			TransducerAsGrammarRule rule = new TransducerAsGrammarRule(tree, state, children);
			ret.add(rule);
		}
		
		return ret;
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

	@Override
	public Iterable<GrammarRule> getBackwardRules(Symbol s)
			throws UnusualConditionException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<GrammarRule> getBackwardRules(Symbol s, Symbol l, int r)
			throws UnusualConditionException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PIterator<GrammarRule> getFSIter(Symbol s)
			throws UnusualConditionException {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public PIterator<GrammarRule> getFSIter(Symbol s, int pos)
			throws UnusualConditionException {
		if (
				allfrules.containsKey(s) &&
				allfrules.get(s).containsKey(pos)
		)
			return new WrappedPIterator<GrammarRule> (allfrules.get(s).get(pos).iterator());

		return new WrappedPIterator<GrammarRule>(empty.iterator());
	}
	@Override
	public PIterator<GrammarRule> getFSIter(Symbol s, int pos, Symbol l, int r)
			throws UnusualConditionException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<GrammarRule> getForwardRules(Symbol s, int pos)
			throws UnusualConditionException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<GrammarRule> getForwardRules(Symbol s, int pos, Symbol l,
			int r) throws UnusualConditionException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PIterator<GrammarRule> getLexFSIter()
			throws UnusualConditionException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PIterator<GrammarRule> getLexFSIter(Symbol label, int rank)
			throws UnusualConditionException {
		// TODO Auto-generated method stub
		return null;
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

	@Override
	boolean injectState(Symbol s, double wgt) throws UnusualConditionException {
		return true;
	}
	void reportRules() {
		Debug.prettyDebug("TransducerAsGrammar shouldn't report rules");
	}
	
	public TransducerRightTree state2Node(Symbol state) {
		return state2Node.get(state);
	}
}
