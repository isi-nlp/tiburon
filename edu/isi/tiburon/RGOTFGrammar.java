package edu.isi.tiburon;

import java.util.HashSet;
import java.util.Iterator;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Vector;

import edu.stanford.nlp.util.FixedPrioritiesPriorityQueue;

// on-the-fly grammar for building rules from a tree transducer rule and a grammar
// accessing methods specify the grammar's start state

// from a particular state. Used by TGOTFGrammar and avoids any explicit parsing algorithms!
public class RGOTFGrammar extends Grammar {
	// tree transducer rule represented as a grammar
	private TransducerAsGrammar rulegram;
	// grammar treated like a tree transducer rule
	private Grammar gram; 
	// which symbol to start from in BS case?
	private Symbol startState;
	// which TransducerNode to start from in FS case?
	private TransducerRightTree startNode;
	
	// already mapped combinations
	private static HashMap<GrammarRule, HashMap<GrammarRule, GrammarRule>> rulePairs;
	// epsilon combinations
	private static HashMap<Symbol, HashMap<GrammarRule, GrammarRule>> stateRulePairs;

	// special lexical rules
	private static HashMap<Symbol, GrammarRule> lexRules;
	// index of lexical rules by their original symbol
	private static HashMap<Symbol, GrammarRule> lexRulesByTriple;
	// mappings of transducer rules to grammars
	private static HashMap<TreeTransducerRule, TransducerAsGrammar> transGramMap;
	private static Vector<GrammarRule> empty;

	// in FS, a single rule is made for the key state
	private GrammarRule keyRule;
	
	
	static {
		transGramMap = new HashMap<TreeTransducerRule, TransducerAsGrammar> ();
		stateRulePairs = new HashMap<Symbol, HashMap<GrammarRule, GrammarRule>> ();

		rulePairs = new HashMap<GrammarRule, HashMap<GrammarRule, GrammarRule>> ();
		lexRules = new HashMap<Symbol, GrammarRule> ();
		lexRulesByTriple = new HashMap<Symbol, GrammarRule> ();
		empty = new Vector<GrammarRule>();
//		bsmemomap = new PMap<TreeTransducerRule, PMap<Grammar, PMap<Symbol, RGOTFGrammar>>>();
//		bsemptymap = new PMap<TreeTransducerRule, PMap<Grammar, HashSet<Symbol>>>();
//		// memoization of good fs-accessed RGOTFGrammars
//		fsmemomap = new PMap<TreeTransducerRule, PMap<TransducerRightTree, PMap<Grammar, PMap<Symbol, RGOTFGrammar>>>> ();
//		// memoization of bad bs-accessed RGOTFGrammars (without actually storing the grammars
//		fsemptymap = new PMap<TreeTransducerRule, PMap<TransducerRightTree, PMap<Grammar, HashSet<Symbol>>>> ();

	}
//	// memoization of good bs-accessed RGOTFGrammars
//	private static PMap<TreeTransducerRule, PMap<Grammar, PMap<Symbol, RGOTFGrammar>>> bsmemomap;
//	// memoization of bad bs-accessed RGOTFGrammars (without actually storing the grammars
//	private static PMap<TreeTransducerRule, PMap<Grammar, HashSet<Symbol>>> bsemptymap;
//
//	// memoization of good fs-accessed RGOTFGrammars
//	private static PMap<TreeTransducerRule, PMap<TransducerRightTree, PMap<Grammar, PMap<Symbol, RGOTFGrammar>>>> fsmemomap;
//	// memoization of bad bs-accessed RGOTFGrammars (without actually storing the grammars
//	private static PMap<TreeTransducerRule, PMap<TransducerRightTree, PMap<Grammar, HashSet<Symbol>>>> fsemptymap;
//	
	// size of lookahead heap
	private int maxheap;
	// turn a transducer rule into a grammar or just retrieve the previously stored one
	private TransducerAsGrammar ruleToGrammar(TreeTransducerRule ttr) {
		boolean debug = false;
		if (!transGramMap.containsKey(ttr))
			transGramMap.put(ttr, new TransducerAsGrammar(ttr));
//			transGramMap.put(ttr, new ConcreteGrammar(ttr));
		else
			Debug.debug(debug, "Already broke down "+ttr+"; not repeating");
		return transGramMap.get(ttr);
	}
	
	// given grammar rule (breakdown of a transducer rule rhs) and a "transducer rule"
	// (grammar rule masquerading as tree-string transducer rule), return cfg grammar
	// rule that can be used by KBAS
	private GrammarRule formAndAddRules(GrammarRule r, GrammarRule t) throws UnusualConditionException {
		boolean debug = false;
		// check to avoid repeats -- this supersedes individual checks above!
		if (
				rulePairs.containsKey(r) && 
				rulePairs.get(r).containsKey(t)
		)
		{
			GrammarRule ret = rulePairs.get(r).get(t);
			if (debug) Debug.debug(debug, "Returning extant rule for "+r+" and "+t+"; "+ret);
			return ret;
		}
		
		// avoid eps with anything but eps
		if (t.getLabel() == null && r.getLabel() != null || t.getLabel() != null && r.getLabel() == null)
			throw new UnusualConditionException("Tried to map grammar rule "+r+" to in-eps \"transducer\" rule "+t);
		if (debug) Debug.debug(debug, "Trying to form rules with grammar rule "+r+" and \"transducer\" rule "+t);
		if (!rulePairs.containsKey(r))
			rulePairs.put(r, new HashMap<GrammarRule, GrammarRule>());
		
		PairSymbol lhs = PairSymbol.get(r.getState(), t.getState());
		Vector<Symbol> rhs = new Vector<Symbol>();
		for (int i = 0; i < r.getChildren().size(); i++) {
			// pair symbol in r signifies state, variable pair. turn it into a triple
			// create a lexical rule with the triple as the label and store it for later
			
			Symbol rsym = r.getChild(i);
			Symbol tsym = t.getChild(i);
			if (rsym instanceof PairSymbol) {
				PairSymbol rpair = (PairSymbol)rsym;
				PairSymbol triple = PairSymbol.get(PairSymbol.get(rpair.getLeft(), tsym), rpair.getRight());
				if (lexRulesByTriple.containsKey(triple)) {
					if (debug) Debug.debug(debug, "Retrieving previous lex rule");
					rhs.add(lexRulesByTriple.get(triple).getState());
				}
				else {
					Symbol newState = SymbolFactory.getStateSymbol();
										rhs.add(newState);
					ConcreteGrammarRule lexRule = new ConcreteGrammarRule(newState, triple, new Vector<Symbol>(), getSemiring().ONE());
					if (debug) Debug.debug(debug, "Formed lex rule "+lexRule+" where rhs is "+rpair.getLeft()+", "+tsym+", "+rpair.getRight());
					lexRules.put(newState, lexRule);
					lexRulesByTriple.put(triple, lexRule);
				}
			}
			else
				rhs.add(PairSymbol.get(rsym, tsym));
		}
		// r rule doesn't have weight
		RGOTFGrammarRule rule = new RGOTFGrammarRule(lhs, rhs, t.getWeight());
		if (debug) Debug.debug(debug, "Formed "+rule+" from "+r+"("+r.hashCode()+") and "+t+"("+t.hashCode()+")");	
		rulePairs.get(r).put(t, rule);
		return rule;				
	}
	
	// given left state and right epsilon "transducer rule"
	// (grammar rule masquerading as tree-string transducer rule), return cfg grammar
	// rule that can be used by KBAS
	//
	
	private GrammarRule formAndAddRules(PairSymbol fs, GrammarRule g) throws UnusualConditionException {
		boolean debug = false;
		Symbol s = fs.getLeft();
		
		if (g.getLabel() != null || g.getChildren().size() != 1)
			throw new UnusualConditionException("Tried to map transducer state "+s+" to non-epsilon grammar rule "+g);
//		if (debug) Debug.debug(debug, "Trying to form rules with transducer state "+s+" and filter "+f+" and grammar rule "+g);

		// check to avoid repeats
		// check to avoid repeats -- this supersedes individual checks above!
		if (stateRulePairs.containsKey(fs) && stateRulePairs.get(fs).containsKey(g)) {
			GrammarRule ret = stateRulePairs.get(fs).get(g);
			if (debug) Debug.debug(debug, "Returning extant rule "+ret);
			return ret;
		}
		if (!stateRulePairs.containsKey(fs))
			stateRulePairs.put(fs, new HashMap<GrammarRule, GrammarRule>());
		
		
		PairSymbol instate;
		PairSymbol outstate;
		
		outstate = PairSymbol.get(s, g.getChildren().get(0));
		instate = PairSymbol.get(s, g.getState()); 
		
		Vector<Symbol> c = new Vector<Symbol>();
		
		c.add(outstate);

		RGOTFGrammarRule newg = new RGOTFGrammarRule(instate, c, g.getWeight());
		if (debug) Debug.debug(debug, "Formed "+newg+" from "+fs+" and "+g);
		stateRulePairs.get(fs).put(g, newg);
		return newg;	
	}
	
	private void initialize() {
		bsResultTable = new HashMap<Symbol, Vector<GrammarRule>>(); 
		bsIterTable = new HashMap<Symbol, BSLazyIterator>();
//		fsResultTable = new HashMap<Symbol, Vector<GrammarRule>>(); 
//		fsIterTable = new HashMap<Symbol, BSLazyIterator>();
		
	}
//	// bs RGOTFGrammar accessor
//	public static RGOTFGrammar get(TreeTransducerRule t, Grammar g, Symbol gs, Semiring s, int mh) throws UnusualConditionException {
//		boolean useMemo = false;
//		boolean debug = false;
//		if (useMemo) {
//			if (!bsemptymap.goc(t).containsKey(g))				
//				bsemptymap.get(t).put(g, new HashSet<Symbol>());
//			
//			if (bsemptymap.get(t).get(g).contains(gs)) {
//				if (debug) Debug.debug(debug, "Skipping bad RGOTF "+t+":"+g.hashCode()+":"+gs);
//				return null;
//			}
//			if (!bsmemomap.goc(t).goc(g).containsKey(gs)) { 
//				if (debug) Debug.debug(debug, "Building RGOTF for "+t+":"+g.hashCode()+":"+gs);
//				RGOTFGrammar gram = new RGOTFGrammar(t, g, gs, s, mh);
//				if (!gram.stringIterator().hasNext()) {
//					if (debug) Debug.debug(debug, "New RGOTF "+gram.hashCode()+" is no good");
//					bsemptymap.get(t).get(g).add(gs);
//					return null;
//				}
//				bsmemomap.get(t).get(g).put(gs, gram);
//			}
//			else {
//				if (debug) Debug.debug(debug, "Recalling RGOTF for "+t+":"+g.hashCode()+":"+gs+":"+bsmemomap.get(t).get(g).get(gs).hashCode());
//			}
//			return bsmemomap.get(t).get(g).get(gs);		
//		}
//		else
//			return new RGOTFGrammar(t, g, gs, s, mh);
//	}
	
	
	// for BS traversal
	public RGOTFGrammar(TreeTransducerRule t, Grammar g, Symbol gs, Semiring s, int mh) {
		super(s, 0);
		boolean debug = false;
		if (debug) Debug.debug(debug, "BS RGOTFGrammar for "+t+":"+g.hashCode()+":"+gs);
		initialize();
		rulegram = ruleToGrammar(t);
		gram = g;	
		startState = PairSymbol.get(rulegram.getStartState(), gs);
		maxheap = mh < 0 ? 0 : mh;
	}
	
	
	// for FS traversal -- no start state, but a keyed end state
	public RGOTFGrammar(TreeTransducerRule t, Grammar g, TransducerRightTree tnode, Symbol gs, Semiring s, int mh) {
		super(s, 0);
		boolean debug = false;
		if (debug) Debug.debug(debug, "FS RGOTFGrammar for "+t+":"+g.hashCode()+":"+tnode+":"+gs);
		initialize();
		rulegram = ruleToGrammar(t);
		gram = g;
		startNode = tnode;
		// dangerous -- "start state" is the grammar-side bottom-up start state!
		startState = gs;
		
//		// add the rule for the keyed end state. no other rules for this node should be made
//		if (tnode != null) {
//			Symbol newState = SymbolFactory.getStateSymbol();
//			PairSymbol triple = PairSymbol.get(PairSymbol.get(tnode.getState(), gs), tnode.getVariable());
//			ConcreteGrammarRule lexRule = new ConcreteGrammarRule(newState, triple, new Vector<Symbol>(), getSemiring().ONE());
//			lexRules.put(newState, lexRule);
//			lexRulesByTriple.put(triple, lexRule);
//			keyRule = lexRule;
//		}
		maxheap = mh < 0 ? 0 : mh;
	}
	
	// allows memoization of BS members and multiple simultaneous access of the same list
	private HashMap<Symbol, Vector<GrammarRule>> bsResultTable;
	private HashMap<Symbol, BSLazyIterator> bsIterTable;
	private class BSIndexedIterator implements PIterator<GrammarRule> {
		private int next;
		private int cap;
		private Symbol state;
		private Vector<GrammarRule> results;
		private BSLazyIterator iterator;
		public BSIndexedIterator(Grammar gram, Grammar trans, PairSymbol s, int mh, int c) throws UnusualConditionException {
			next = 0;
			cap = c;
			state = s;
			if (!bsIterTable.containsKey(state))
				bsIterTable.put(state, new BSLazyIterator(gram, trans, s, mh));
			if (!bsResultTable.containsKey(state))
				bsResultTable.put(state, new Vector<GrammarRule>());
			results = bsResultTable.get(state);
			iterator = bsIterTable.get(state);
		}
		public boolean hasNext() {
			if (cap > 0 && next >= cap)
				return false;
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
		// rtg iter -- rules that cover a transducer right tree (so not very complex)
		private PIterator<GrammarRule> aiter;
		// "trans", which makes iters
		private Grammar btrans;
		// start symbol for trans 
		private PairSymbol fs;
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
		
		
		
		public BSLazyIterator(Grammar gram, Grammar trans, PairSymbol s, int mh) throws UnusualConditionException {
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
			
			if (debug) Debug.debug(debug, "Looking for matches for "+s+": between "+a+" and "+b);
			// add epsilon rules right here
			BSEpsilonGrammarLazyIterator egit = new BSEpsilonGrammarLazyIterator(s, trans, mh);
			if (egit.hasNext())
				q.add(egit, -egit.peek().getWeight());		
			
			aiter = gram.getBSIter(a);
			btrans = trans;
			if (!aiter.hasNext())
				return;
			while (aiter.hasNext() && q.isEmpty()) {
				if (debug) Debug.debug(debug, "trying to seed with "+aiter.peek()+" and "+b);
				BSBaseLazyIterator l = new BSBaseLazyIterator(aiter.next(), fs, btrans, maxheap);
				if (!l.hasNext())
					continue;
				double cost = -l.peek().getWeight();
				if (!q.add(l, cost))
					throw new UnusualConditionException("Couldn't add new iterator for "+s+" with cost "+cost);
			}
			
			// do first step
			fillNext();
			if (debug) Debug.debug(debug, "Initial list of rules for "+a+" and "+b+" is "+next);
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
	
	// base lazy iterator -- one grammar rule matching with "transducer" (really a grammar
	// but treated as tree-string transducer)
	
	// no epsilons from grammar in this world
	
	// input-epsilon "transducer" rules are handled by InputEpsilonLazyIterator
	// next pops off next member
	private  class BSBaseLazyIterator implements PIterator<GrammarRule> {
		private FixedPrioritiesPriorityQueue<GrammarRule> next;
		private GrammarRule base;
		
		// "transducer" rules
		private Iterator<GrammarRule> iterator;
		private double lastcost = 0;
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;
		public BSBaseLazyIterator(GrammarRule r, PairSymbol fs, 
				 Grammar trans, int maxheap) throws UnusualConditionException {
			Symbol b = fs.getRight();
			
			boolean debug = false;
			if (debug) Debug.debug(debug, "Looking for matches between "+r+" and "+b);
			base = r;
			Symbol label = r.getLabel();
			if (label == null)
				label = Symbol.getEpsilon();
			next = new FixedPrioritiesPriorityQueue<GrammarRule> ();
			iterator = trans.getBSIter(b, label, r.getChildren().size());
			while (
					(maxheap == 0 ? true : next.size() < maxheap) && 
					iterator.hasNext()
					) {
				GrammarRule t = iterator.next();
				if (debug) Debug.debug(debug, "Forming rules from "+base+" and "+t);
				GrammarRule g = formAndAddRules(base, t);
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
			if (iterator.hasNext()) {
				GrammarRule t = iterator.next();	
				try {
					GrammarRule g = formAndAddRules(base, t);
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

	
//	// allows memoization of BS members and multiple simultaneous access of the same list
//	private HashMap<Symbol, Vector<GrammarRule>> fsResultTable;
//	private HashMap<Symbol, FSLazyIterator> fsIterTable;
//	private class FSIndexedIterator implements PIterator<GrammarRule> {
//		private int next;
//		private int cap;
//		private Symbol state;
//		private Vector<GrammarRule> results;
//		private FSLazyIterator iterator;
//		public FSIndexedIterator(Grammar gram, Grammar trans, PairSymbol s, int mh, int c) throws UnusualConditionException {
//			next = 0;
//			cap = c;
//			state = s;
//			if (!fsIterTable.containsKey(state))
//				fsIterTable.put(state, new FSLazyIterator(gram, trans, s, mh));
//			if (!fsResultTable.containsKey(state))
//				fsResultTable.put(state, new Vector<GrammarRule>());
//			results = fsResultTable.get(state);
//			iterator = fsIterTable.get(state);
//		}
//		public boolean hasNext() {
//			if (cap > 0 && next >= cap)
//				return false;
//			return results.size() > next || iterator.hasNext();
//		}
//		public GrammarRule peek() throws NoSuchElementException {
//			if (!hasNext())
//				throw new NoSuchElementException("Asked for peek on empty PIterator");
//			if (results.size() > next)
//				return results.get(next);
//			else
//				return iterator.peek();
//		}
//		public GrammarRule next() throws NoSuchElementException {
//			boolean debug = false;
//			if (!hasNext())
//				throw new NoSuchElementException("Asked for peek on empty PIterator");
//			if (debug) Debug.debug(debug, "Looking for "+next+" of "+state);
//			if (results.size() <= next) {
//				if (debug) Debug.debug(debug, "Results only has "+results.size());				
//				results.add(iterator.next());
//			}
//			return results.get(next++);
//		}
//		public void remove() throws UnsupportedOperationException {
//			throw new UnsupportedOperationException("Didn't bother with remove for BsIndexedIterator");
//		}
//	}
	
	// implementation of pervasive laziness for FS covering of a transducer rule by a grammar
	
	// implementation of pervasive laziness for forwardstar
	private class FSLazyIterator implements PIterator<GrammarRule> {
		// rtg iter -- rules that cover a transducer right tree (so not very complex)
		private PIterator<GrammarRule> aiter;
		// "trans", which makes iters
		private Grammar btrans;
		
		// grounded state, for nonlex
		private PairSymbol state;
		// pos of grounded state, for nonlex
		private int pos;
		
		// main queue
		private FixedPrioritiesPriorityQueue<PIterator<GrammarRule>> q;
		// wait queue
		private FixedPrioritiesPriorityQueue<PIterator<GrammarRule>> w;
		// next items
		private FixedPrioritiesPriorityQueue<GrammarRule> next;
		// are we a lexical iterator?
		boolean isLex;
		// for checking monotonicity
		private double lastcost;
		// how big a wait heap?
		private int maxheap;
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;
		
		
		// lexical, unfiltered
		// gets base of the transducer rule's rules composed with appropriate
		// elements from the grammar
		public FSLazyIterator(TransducerAsGrammar gram, Grammar trans, int mh) throws UnusualConditionException {
			boolean debug = false;
			isLex = true;
			// initialize elements
			q = new FixedPrioritiesPriorityQueue<PIterator<GrammarRule>>();
			// wait queue
			w = new FixedPrioritiesPriorityQueue<PIterator<GrammarRule>>();
			next = new FixedPrioritiesPriorityQueue<GrammarRule>();
			maxheap = mh;
	
			btrans = trans;
			
			aiter = new WrappedPIterator<GrammarRule> (gram.getTerminalRules().iterator());

			
			
			if (!aiter.hasNext()) {
				return;
			}

			
			while (aiter.hasNext() && q.isEmpty()) {
				if (debug) Debug.debug(debug, "trying to seed with "+aiter.peek());
				TransducerAsGrammarRule arule = (TransducerAsGrammarRule)aiter.next();
				FSBaseLazyIterator l = new FSBaseLazyIterator(arule, btrans, maxheap);				
				if (!l.hasNext())
					continue;
				double cost = -l.peek().getWeight();
				if (!q.add(l, cost))
					throw new UnusualConditionException("Couldn't add new lex iterator with cost "+cost);
			}
//			// key rule also added in
//			if (keyRule != null)
//				q.add(new WrappedPIterator<GrammarRule>(keyRule), 0);
			// do first step
			fillNext();
		}
		
		// nonlexical, keyed to a particular state and pos 
		// could combine with eps (state-change) rule from "trans"
		public FSLazyIterator(TransducerAsGrammar gram, Grammar trans,  PairSymbol ps, int p, int mh) throws UnusualConditionException {
			boolean debug = false;
			isLex = false;
			// initialize elements
			q = new FixedPrioritiesPriorityQueue<PIterator<GrammarRule>>();
			// wait queue
			w = new FixedPrioritiesPriorityQueue<PIterator<GrammarRule>>();
			state = ps;
			pos = p;
			next = new FixedPrioritiesPriorityQueue<GrammarRule>();
			maxheap = mh;
	
			btrans = trans;
			aiter = gram.getFSIter(ps.getLeft(), pos);

			if (p == 0) {
				FSEpsilonGrammarLazyIterator egit = new FSEpsilonGrammarLazyIterator(ps, trans, mh);
				if (egit.hasNext())
					q.add(egit, -egit.peek().getWeight());			
			}
//			if (!aiter.hasNext()) {
//				return;
//			}
			while (aiter.hasNext() && q.isEmpty()) {
				if (debug) Debug.debug(debug, "trying to seed with "+aiter.peek());
				TransducerAsGrammarRule arule = (TransducerAsGrammarRule)aiter.next();
				FSBaseLazyIterator l = new FSBaseLazyIterator(arule, btrans, state, pos, maxheap);				
				if (!l.hasNext())
					continue;
				double cost = -l.peek().getWeight();
				if (!q.add(l, cost))
					throw new UnusualConditionException("Couldn't add new lex iterator with cost "+cost);
			}
			// do first step
			fillNext();
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
				while (w.isEmpty() && aiter.hasNext()) {
					FSBaseLazyIterator l2;
					if (isLex)
						l2 = new FSBaseLazyIterator((TransducerAsGrammarRule)aiter.next(), btrans, maxheap);
					else
						l2 = new FSBaseLazyIterator((TransducerAsGrammarRule)aiter.next(), btrans, state, pos, maxheap);

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
	
	// filter iterator that wraps another iterator constraining it to produce rules with a certain state in
	// a certain pos
	// this is not terribly efficient as the internal iterator can produce many things that get filtered out
	private class FSStatePosFilterIterator implements Iterator<GrammarRule> {
		private Iterator<GrammarRule> iterator;
		private int pos;
		private Symbol state;
		private GrammarRule next;
		public FSStatePosFilterIterator(Iterator<GrammarRule> i, Symbol s, int p) {
			iterator = i;
			pos = p;
			state = s;
			fillNext();
		}
		public boolean hasNext() {
			return next != null;
		}
		public GrammarRule next() throws NoSuchElementException {
			if (next == null)
				throw new NoSuchElementException("Asked for next on empty Iterator");
			GrammarRule ret = next;
			fillNext();
			return ret;
		}
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
		}
		// where the work is done
		private void fillNext() {
			boolean debug = false;
			next = null;
			while (next == null && iterator.hasNext()) {
				GrammarRule cand = iterator.next();
				if (debug) Debug.debug(debug, "Candidate is "+cand+"; filtering "+state+", "+pos);
				if (cand.getChildren().size() <= pos)
					continue;
				if (!cand.getChild(pos).equals(state))
					continue;
				if (debug) Debug.debug(debug, cand+" passes filter");
				next = cand;
			}
		}
	}
	
	// base lazy iterator for rules from forward star -- one transducer rule-as-grammar-rule
	// matching with grammar rule at a particular node
	// special treatment for parents of key node
	// 
	// no epsilon rules here
	private class FSBaseLazyIterator implements PIterator<GrammarRule> {
		private FixedPrioritiesPriorityQueue<GrammarRule> next;
		private GrammarRule base;
		
		private Iterator<GrammarRule> iterator;
		private double lastcost = 0;
		
		
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;
		
		// get "lexical" rules -- tails are unconstrained unless a tail is the key node 
		public FSBaseLazyIterator(TransducerAsGrammarRule rule, Grammar g,  
				int maxheap) throws UnusualConditionException {

			boolean debug = false;


			base = rule;

			if (debug) Debug.debug(debug, "Looking for matches between lexical rule "+rule+
			" and grammar ");

			next = new FixedPrioritiesPriorityQueue<GrammarRule> ();

			// if a child of rule is the key node, iterator must match
			TransducerRightTree ruleNode = rule.getNode();
			int pos = -1;
			boolean foundKey = false;
			
			boolean isEps = false;
			if (startNode != null) {
				// epsilon node case
				if (ruleNode.getNumChildren() == 0 && ruleNode.equals(startNode)) {
					pos = 0;
					foundKey = true;
					isEps = true;
				}
				else {
					for (int i = 0; i < ruleNode.getNumChildren(); i++) {
						if (ruleNode.getChild(i).equals(startNode)) {
							pos = i;
							foundKey = true;
							break;
						}
					}
				}
			}
			if (foundKey) {
				if (debug) Debug.debug(debug, "Found key node at "+pos);
				iterator = g.getFSIter(startState, pos, isEps ? Symbol.getEpsilon() : rule.getLabel(), rule.getChildren().size());
			}
			else {
				// otherwise, anything matching this rule's rank and node is okay			
				iterator = g.getLexFSIter(rule.getLabel(), rule.getChildren().size());
			}
			while (
					(maxheap == 0 ? true : next.size() < maxheap) && 
					iterator.hasNext()
			) {
				GrammarRule r = iterator.next();		
				if (debug) Debug.debug(debug, "Matching "+r+" to "+base);
				GrammarRule newg = formAndAddRules(base, r);
				if (debug) Debug.debug(debug,  "Built rule for "+base+" and "+r+"; "+newg);
				next.add(newg, -newg.getWeight());

			}
			if (!next.isEmpty()) {
				if (REPORTMONO && lastcost < next.getPriority())
					Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
				//					throw new UnusualConditionException("Monotonicity of lazy iterator violated: got "+next+" after "+lastcost);
				lastcost = next.getPriority();
			}
		}
		// get rules constrained by a specified state
		// get "nonlexical" rules -- tails are constrained by specified symbol 
		// if base rule has key and this is not the constrained symbol, we have double constraint
		
		public FSBaseLazyIterator(TransducerAsGrammarRule rule, Grammar g,  PairSymbol ps, int pos,
				int maxheap) throws UnusualConditionException {

			boolean debug = false;
			base = rule;
			if (debug) Debug.debug(debug, "Looking for matches between nonlexical rule "+rule+
			" and grammar where child "+pos+" is "+ps.getRight());
			
			next = new FixedPrioritiesPriorityQueue<GrammarRule> ();
			// if a child of rule is the key node, iterator must match
			TransducerRightTree ruleNode = rule.getNode();
			int keypos = -1;
			boolean foundKey = false;
			if (startNode != null) {
				for (int i = 0; i < ruleNode.getNumChildren(); i++) {
					if (ruleNode.getChild(i).equals(startNode)) {
						keypos = i;
						foundKey = true;
						break;
					}
				}
			}
			if (foundKey && keypos != pos) {
				if (debug) Debug.debug(debug, "Doubly-constrained non-lexical rule: must check for both "+pos+" and "+keypos);
				iterator = new FSStatePosFilterIterator(g.getFSIter(ps.getRight(), pos, rule.getLabel(), rule.getChildren().size()), startState, keypos);
			}
			else {
				iterator = g.getFSIter(ps.getRight(), pos, rule.getLabel(), rule.getChildren().size());
			}
			
			while (
					(maxheap == 0 ? true : next.size() < maxheap) && 
					iterator.hasNext()
			) {
				GrammarRule r = iterator.next();		
				if (debug) Debug.debug(debug, "Matching "+r+" to "+base);
				GrammarRule newg = formAndAddRules(base, r);
				if (debug) Debug.debug(debug,  "Built rule for "+base+" and "+r+"; "+newg);
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
					GrammarRule g = formAndAddRules(base, r);
					if (debug) Debug.debug(debug, "Built next rule for "+base+" and "+r+"; "+g);
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
	
	// match a symbol with epsilon grammar "transducer" rules
	// FS variant
	
	private class FSEpsilonGrammarLazyIterator implements PIterator<GrammarRule> {
		private Iterator<GrammarRule> iterator;
		private PairSymbol base;
		private FixedPrioritiesPriorityQueue<GrammarRule> next;
		private double lastcost = 0;
		
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;
		public FSEpsilonGrammarLazyIterator(PairSymbol s, 
				Grammar gram, int maxheap) throws UnusualConditionException {
			boolean debug = false;
			base = s;			
			if (debug) Debug.debug(debug, "Looking for epsilon rules from "+s+" which is really "+s.getLeft()+" and "+s.getRight());
			// don't allow epsilon productions when the top is start state
			// this prevents redundant epsilon rules from forming
			if (s.getLeft().equals(top()))
				iterator = new WrappedPIterator<GrammarRule>(empty.iterator());
			else 
				iterator = gram.getFSIter(s.getRight(), 0, Symbol.getEpsilon(), 1);
			next = new FixedPrioritiesPriorityQueue<GrammarRule> ();
			while (
					(maxheap == 0 ? true : next.size() < maxheap) && 
					iterator.hasNext()
					) {
				GrammarRule t = iterator.next();					
				GrammarRule g = formAndAddRules(base, t);
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
					GrammarRule g = formAndAddRules(base, t);
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
	
	// BS epsilon inserter
	// similar to FS but no checking top state and uses bs call to gram

	private class BSEpsilonGrammarLazyIterator implements PIterator<GrammarRule> {
		private Iterator<GrammarRule> iterator;
		private PairSymbol base;
		private FixedPrioritiesPriorityQueue<GrammarRule> next;
		private double lastcost = 0;
		
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;
		public BSEpsilonGrammarLazyIterator(PairSymbol s, 
				Grammar gram, int maxheap) throws UnusualConditionException {
			boolean debug = false;
			base = s;			
			if (debug) Debug.debug(debug, "Looking for epsilon rules from "+s+" which is really "+s.getLeft()+" and "+s.getRight());
			
			iterator = gram.getBSIter(s.getRight(), Symbol.getEpsilon(), 1);
			next = new FixedPrioritiesPriorityQueue<GrammarRule> ();
			while (
					(maxheap == 0 ? true : next.size() < maxheap) && 
					iterator.hasNext()
					) {
				GrammarRule t = iterator.next();					
				GrammarRule g = formAndAddRules(base, t);
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
					GrammarRule g = formAndAddRules(base, t);
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
	
	
	@Override
	public PIterator<GrammarRule> getBSIter(Symbol s)
	throws UnusualConditionException {
		int cap = 0;
		if (s instanceof PairSymbol) {
			return new BSIndexedIterator(rulegram, gram, (PairSymbol)s, maxheap, cap);
//			return new BSLazyIterator(rulegram, gram, (PairSymbol)s, maxheap);
		}
		else {
			if (!lexRules.containsKey(s))
				throw new UnusualConditionException("Asked for rules from unknown state "+s);
			return new WrappedPIterator<GrammarRule> (lexRules.get(s));
		}
	}

	@Override
	public PIterator<GrammarRule> getBSIter(Symbol s, Symbol label, int rank)
	throws UnusualConditionException {
		throw new UnusualConditionException("Label-specific BSIter for RGOTF doesn't make sense!");
	}

	// lazy lexical unfiltered forward
	public PIterator<GrammarRule> getLexFSIter() throws UnusualConditionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Getting new lex FS iterator from "+rulegram.hashCode()+" and "+gram.hashCode());
		return new FSLazyIterator(rulegram, gram, maxheap);
	}

	// lazy lexical filtered forward
	public PIterator<GrammarRule> getLexFSIter(Symbol label, int rank) throws UnusualConditionException {
		throw new UnusualConditionException("Not yet implemented");
	}
	
	@Override
	public PIterator<GrammarRule> getFSIter(Symbol s)
			throws UnusualConditionException {
		// TODO Auto-generated method stub
		return null;
	}
	
	// lazy unfiltered forward
	public PIterator<GrammarRule> getFSIter(Symbol s, int pos) throws UnusualConditionException {
		int cap = 0;
		if (s instanceof PairSymbol) {
//			return new FSIndexedIterator(rulegram, gram, (PairSymbol)s, pos, maxheap);
			boolean debug = false;
			if (debug) Debug.debug(debug, "Getting new nonlex FS iterator from "+rulegram.hashCode()+" and "+gram.hashCode()+", "+s+" and "+pos);
			return new FSLazyIterator(rulegram, gram, (PairSymbol)s, pos, maxheap);
		}
		else {
			throw new UnusualConditionException("Asked for rules from non-pair symbol "+s);
		}
		
	}

	// lazy filtered forward
	public PIterator<GrammarRule> getFSIter(Symbol s, int pos, Symbol l, int r) throws UnusualConditionException {
		throw new UnusualConditionException("Not yet implemented");
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
	public Symbol getStartState() {
			return startState;
	}
	@Override
	public boolean isStartState(Symbol s) {
		return s.equals(startState);
	}

	@Override
	public Iterable<GrammarRule> getTerminalRules() {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	boolean injectState(Symbol s, double wgt) throws UnusualConditionException {
		return true;
	}
	void reportRules() {
		Debug.prettyDebug("RGOTFGrammar shouldn't report rules");
	}
	
	// special feed to the left side top
	public Symbol top() {
		return rulegram.getStartState();
	}
	// special access to lex rules used by FS on RGOTF 
	public GrammarRule getLexRule(Symbol s) {
		if (lexRules.containsKey(s))
			return lexRules.get(s);
		return null;
	}
	// passthrough to rulegram
	public TransducerRightTree state2Node(Symbol s) {
		return rulegram.state2Node(s);
	}

}
