package edu.isi.tiburon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Vector;

import edu.stanford.nlp.util.BinaryHeapPriorityQueue;
import edu.stanford.nlp.util.FixedPrioritiesPriorityQueue;

public class TSOTFGrammar extends Grammar {

	
	private void initialize() {
		ruleIndexPairs = new PMap<StringTransducerRule, PMap<Index, GrammarRule>> ();
		rulePairs = new	PMap<StringTransducerRule, PMap<Vector<Index>, GrammarRule>> ();
		leftStates = new PMap<Integer, PMap<Symbol,Vector<Pair<IndexSymbol, Double>>>> ();
		rightStates = new PMap<Integer, PMap<Symbol, Vector<Pair<IndexSymbol, Double>>>> ();
		allStates = new HashSet<IndexSymbol> ();
		leftStateSyms = new PMap<Integer, Vector<Pair<Symbol, Double>>> ();
		rightStateSyms = new PMap<Integer, Vector<Pair<Symbol, Double>>> ();
		fsStateResultTable = new PMap<Symbol, Vector<GrammarRule>> ();
		fsStateIterTable = new PMap<Symbol, FSLazyIterator>(); 
		ruleCount = 0;

	}
	
	// form lexical grammar rules -- assume a match!
	private GrammarRule formAndAddRules(StringTransducerRule r, Index index) {
		boolean debug = true;
		if (ruleIndexPairs.goc(r).containsKey(index))
			return ruleIndexPairs.get(r).get(index);
		IndexSymbol st = IndexSymbol.get(r.getState(), index);
		Vector<Symbol> rhs = new Vector<Symbol>();
		ConcreteGrammarRule ret = new ConcreteGrammarRule(st, r.getLHS().getLabel(), rhs, r.getWeight());
		if (debug) Debug.debug(debug, "Formed "+ret);
		incRuleCount();
		ruleIndexPairs.get(r).put(index, ret);
		return ret;
	}
	
	// form nonlexical grammar rules -- match each index to variables on rhs and transfer them to lhs
	private GrammarRule formAndAddRules(StringTransducerRule r, Vector<Index> index) throws UnusualConditionException {
		boolean debug = true;
		// TODO: need to uniquify index vector?
		if (rulePairs.goc(r).containsKey(index)) {
			GrammarRule ret =  rulePairs.get(r).get(index);
//			if (debug) Debug.debug(debug, "Returning already formed "+ret+" from "+r+" and "+index);
			return ret;
		}
		
		Vector<TransducerRightString> inrhs = r.getRHS().getItemLeaves();
		HashMap<Symbol, IndexSymbol> varMap = new HashMap<Symbol, IndexSymbol>();
		for (int i = 0; i < inrhs.size(); i++) {
			varMap.put(inrhs.get(i).getVariable(), IndexSymbol.get(inrhs.get(i).getState(), index.get(i)));
		}
		Vector<Symbol> outrhs = new Vector<Symbol>();
		for (int i = 0; i < r.getLHS().getNumChildren(); i++) {
			if (!varMap.containsKey(r.getLHS().getChild(i).getVariable()))
				throw new UnusualConditionException("No mapping for variable in "+r.getLHS().getChild(i)+" in "+varMap);
			outrhs.add(varMap.get(r.getLHS().getChild(i).getVariable()));
		}
		// extremes form top state
		int left = index.get(0).left();
		int right = index.get(inrhs.size()-1).right();
		IndexSymbol st = IndexSymbol.get(r.getState(), Index.get(left, right));
		Symbol label = r.getLHS().getLabel();
		ConcreteGrammarRule ret = new ConcreteGrammarRule(st, label, outrhs, r.getWeight());
		if (debug) Debug.debug(debug, "Formed "+ret+" from "+r+" and "+index);
		incRuleCount();
		rulePairs.get(r).put(index, ret);
		return ret;
	}
	
	// assuming all rules have already been formed, dump them, in no particular order
	public Iterable<ConcreteGrammarRule> getRules() {
		Vector<ConcreteGrammarRule> allRules = new Vector<ConcreteGrammarRule>();
		for (PMap<Index, GrammarRule> map : ruleIndexPairs.values()) {
			for (GrammarRule rule : map.values())
				allRules.add((ConcreteGrammarRule)rule);
		}
		for (PMap<Vector<Index>, GrammarRule> map : rulePairs.values()) {
			for (GrammarRule rule : map.values())
				allRules.add((ConcreteGrammarRule)rule);
		}
		return allRules;
	}
	
	// memoization tables by state
	private PMap<Symbol, Vector<GrammarRule>> fsStateResultTable;
	private PMap<Symbol, FSLazyIterator> fsStateIterTable;
	
	// wraps FSLazyIterator -- allows multiple access, arbitrary cap
	private class FSIndexedIterator implements PIterator<GrammarRule> {
		private int next;		
		private Vector<GrammarRule> results;
		private FSLazyIterator iterator;
		private IndexSymbol keyst;

		// for state
		public FSIndexedIterator(OTFStringTransducerRuleSet trans, IndexSymbol state, int mh) throws UnusualConditionException {
			next = 0;
			keyst = state;

			boolean debug = false;

			if (!fsStateIterTable.containsKey(state)){
				fsStateIterTable.put(state, new FSLazyIterator(trans, state, mh));
				if (debug )Debug.debug(debug, "No Reuse of "+state);

			}
			else {
				if (debug )Debug.debug(debug, "Reuse of "+state);
			}
			if (!fsStateResultTable.containsKey(state))
				fsStateResultTable.put(state, new Vector<GrammarRule>());
			results = fsStateResultTable.get(state);
			iterator = fsStateIterTable.get(state);					
		}
		public boolean hasNext() {
			boolean ret;
			boolean debug = false;
			if (cap > 0 && next >= cap) {
				if (debug) Debug.debug(debug, "Reached cap of "+cap+" after "+results.get(next-1));
				ret = false;
			}
			else
				ret = (results.size() > next || (iterator != null && iterator.hasNext()));
			return ret;
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
			if (results.size() <= next) {
				if (debug) Debug.debug(debug, "Results only has "+results.size());
				GrammarRule newrule = iterator.next();
				if (debug) Debug.debug(debug, "Got "+newrule+" for "+next+" of "+keyst);
				results.add(newrule);
				for (Symbol c : newrule.getChildren()) {
					if (!c.equals(keyst) && fsStateResultTable.containsKey(c)) {
						if (debug) Debug.debug(debug, "Adding "+newrule+" to list of "+c+" when in "+keyst+" iterator");
						fsStateResultTable.get(c).add(newrule);
					}
				}
			}
			return results.get(next++);
		}
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BsIndexedIterator");
		}
	}
	
	

	// implementation of pervasive laziness -- combine transducer rules with indexed states
	// or labeled spans to form grammar rules
	private class FSLazyIterator implements PIterator<GrammarRule> {
		// state sequence iterator -- pairs of states and inside cost 
		private PIterator<StateSeq> biter;
		

		// transducer, which makes iters
		private OTFStringTransducerRuleSet atrans;


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

		private void initialize() {
			// initialize elements
			q = new FixedPrioritiesPriorityQueue<PIterator<GrammarRule>>();
			// wait queue
			w = new FixedPrioritiesPriorityQueue<PIterator<GrammarRule>>();
			next = new FixedPrioritiesPriorityQueue<GrammarRule>();

		}

		// lexical rule generator -- no states needed
		// queues for each position are added, as we assume we need to cover the string
		// no need to be that lazy here!
		public FSLazyIterator(OTFStringTransducerRuleSet strs, OTFString str, int mh) throws UnusualConditionException {
			boolean debug = false;
			initialize();
			atrans = strs;
			biter = new WrappedPIterator<StateSeq>(stateempty.iterator());
			maxheap = mh;
			for (Symbol label : str.getLabels()) {
				for (Index index: str.getMatches(label)) {
					FSLexBaseLazyIterator l = new FSLexBaseLazyIterator(atrans, label, index);
					if (!l.hasNext())
						continue;
					double cost = -l.peek().getWeight();
					if (!q.add(l, cost))
						throw new UnusualConditionException("Couldn't add new lex iterator for "+label+", "+index+" with cost "+cost);
				}
			}
			fillNext();
			if (debug) Debug.debug(debug, "Initial list of lexical rules is "+next);
		}
		
		// nonlexical lazy iterator
		// given a state, iterate over possible vectors including that state (to left and to right)
		// for each of these, create a base iterator that matches with rules
		public FSLazyIterator(OTFStringTransducerRuleSet strs, IndexSymbol state, int mh) throws UnusualConditionException {
			boolean debug = false;
			initialize();
			atrans = strs;
			// check whether this state can exist in *any* rules solo, to left, or to right
			// create PairIterator to reflect this
			Symbol sym = state.getSym();
			boolean isSolo = atrans.isSolo(sym);
			boolean isLeft = atrans.isLeft(sym);
			boolean isRight = atrans.isRight(sym);
			
			biter = new PairIterator(state, atrans, isSolo, isLeft, isRight, mh);
			maxheap = mh;
			if (!biter.hasNext())
				return;
			while (biter.hasNext() && q.isEmpty()) {
				if (debug) Debug.debug(debug, "trying to seed with "+biter.peek());
				StateSeq base = biter.next();
				FSBaseLazyIterator l = new FSBaseLazyIterator(atrans, base.states);
				if (!l.hasNext())
					continue;
				double cost = -l.peek().getWeight();
				if (!q.add(l, cost))
					throw new UnusualConditionException("Couldn't add new nonlexical iterator with cost "+cost);		
			}
			fillNext();
			if (debug) Debug.debug(debug, "Initial list of nonlexical rules is "+next);

		}
		
		
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
				// if this was lexical, it would have already been handled above in q
				while (w.isEmpty() && biter.hasNext()) {
					if (debug) Debug.debug(debug, "proposing new list based on "+biter.peek());
					StateSeq base = biter.next();
					FSBaseLazyIterator l = new FSBaseLazyIterator(atrans, base.states);
					if (!l.hasNext())
						continue;
					double cost = -l.peek().getWeight();
					if (!w.add(l, cost))
						throw new UnusualConditionException("Couldn't add new nonlexical iterator with cost "+cost);
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


	
	// lex version
	// given a position in the string and a label on that position, find matching lexical rules
	// epsilon should also be passed here from above
	private class FSLexBaseLazyIterator implements PIterator<GrammarRule> {
	private FixedPrioritiesPriorityQueue<GrammarRule> next;
	private Index base;
	private Iterator<StringTransducerRule> iterator;		
	private double lastcost = 0;
	// should we report monotonicity errors?
	private static final boolean REPORTMONO = false;
	public FSLexBaseLazyIterator(OTFStringTransducerRuleSet trans, Symbol label, Index index) {
		boolean debug =false;
		next = new FixedPrioritiesPriorityQueue<GrammarRule> ();
		base = index;
		iterator = trans.getLexRules(label).iterator();
		while (
				(maxheap == 0 ? true : next.size() < maxheap) && 
				iterator.hasNext()
		) {
			StringTransducerRule r = iterator.next();		
			if (debug) Debug.debug(debug, "Matching "+r+" to "+base);
			GrammarRule newg = formAndAddRules(r, base);
			if (debug) Debug.debug(debug,  "Built rule for "+base+"; "+newg);
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
			StringTransducerRule r = iterator.next();	
			if (debug) Debug.debug(debug, "Matching "+base+" to "+r);

			GrammarRule g = formAndAddRules(r, base);
			if (debug) Debug.debug(debug, "Built next rule for "+base+"; "+g);
			next.add(g, -g.getWeight());

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
	// non-lex version
	// given a fixed state sequence, iterate through matching transducer rules and form the result

	private class FSBaseLazyIterator implements PIterator<GrammarRule> {
		private FixedPrioritiesPriorityQueue<GrammarRule> next;
		private Vector<Index> base;
		private Iterator<StringTransducerRule> iterator;		
		private double lastcost = 0;
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;
		public FSBaseLazyIterator(OTFStringTransducerRuleSet trans, Vector<IndexSymbol> isbase) throws UnusualConditionException {
			boolean debug =false;
			next = new FixedPrioritiesPriorityQueue<GrammarRule> ();
			Vector<Symbol> transRHS = new Vector<Symbol>();
			base = new Vector<Index>();
			for (IndexSymbol is : isbase) {
				transRHS.add(is.getSym());
				base.add(is.getIndex());
			}
//			VecSymbol vs = SymbolFactory.getVecSymbol(transRHS);
			iterator = trans.getForwardRules(transRHS).iterator();
			while (
					(maxheap == 0 ? true : next.size() < maxheap) && 
					iterator.hasNext()
			) {
				StringTransducerRule r = iterator.next();		
				if (debug) Debug.debug(debug, "Matching "+r+" to "+base);
				GrammarRule newg = formAndAddRules(r, base);
				if (debug) Debug.debug(debug,  "Built rule for "+base+"; "+newg);
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
				StringTransducerRule r = iterator.next();	
				if (debug) Debug.debug(debug, "Matching "+base+" to "+r);
				try {
					GrammarRule g = formAndAddRules(r, base);
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
	
	// struct for storing vector of index symbols with weight = sum of inside costs
	private class StateSeq {
		Vector<IndexSymbol> states;
		double weight;
		public String toString() { return states.toString()+":"+weight; }
	}
	
	// lazily step through left and right pair iterators for a state
	private class PairIterator implements PIterator<StateSeq> {
		// main queue
		private FixedPrioritiesPriorityQueue<PIterator<StateSeq>> q;
		// next items
		private FixedPrioritiesPriorityQueue<StateSeq> next;
		// for checking monotonicity
		private double lastcost;
		// how big a wait heap?
		private int maxheap;
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;

		private void initialize() {
			// initialize elements
			q = new FixedPrioritiesPriorityQueue<PIterator<StateSeq>>();
			// wait queue
			next = new FixedPrioritiesPriorityQueue<StateSeq>();
		}
		
		// make left and right iterators and put them into the heap
		public PairIterator(IndexSymbol state, OTFStringTransducerRuleSet strs, boolean isSolo, boolean isLeft, boolean isRight, int mh) throws UnusualConditionException {
			boolean debug = false;
			initialize();
			maxheap = mh;
			// add in single-state case, too
			if (isSolo) {
				StateSeq solo = new StateSeq();
				solo.states = new Vector<IndexSymbol>();
				solo.states.add(state);

				if (!q.add(new WrappedPIterator<StateSeq>(solo), 0))
					throw new UnusualConditionException("Couldn't add solo-state iterator for "+state);
			}
			if (isRight) {
				LeftPairIterator lpi = new LeftPairIterator(state, strs, mh);
//				LeftPairIterator lpi = new LeftPairIterator(state);

				if (lpi.hasNext()) {
					double leftcost = lpi.peek().weight;
					if (!q.add(lpi, leftcost))
						throw new UnusualConditionException("Couldn't add left iterator for "+state);
				}
			}
			if (isLeft) {
				RightPairIterator rpi = new RightPairIterator(state, strs, mh);
//				RightPairIterator rpi = new RightPairIterator(state);

				if (rpi.hasNext()) {
					double rightcost = rpi.peek().weight;
					if (!q.add(rpi, rightcost))
						throw new UnusualConditionException("Couldn't add right iterator for "+state);
				}
			}
			fillNext();
			if (debug) Debug.debug(debug, "Initial list of state seqs is "+next);
		}
		// just pop off top and put it back in -- no two-queue strategy here
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
				PIterator<StateSeq> current = q.removeFirst();
				StateSeq g  = current.next();
				next.add(g, g.weight);
				if (debug) Debug.debug(debug,  "Next state seq is "+g);
				if (current.hasNext()) {
					double nextcost = current.peek().weight;
					if (!q.add(current, nextcost))
						throw new UnusualConditionException("Couldn't add next step iterator with cost "+nextcost);
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
		public StateSeq peek() throws NoSuchElementException {
			if (next.isEmpty())
				throw new NoSuchElementException("Asked for peek on empty PIterator");
			return next.getFirst();
		}
		public StateSeq next() throws NoSuchElementException {
			if (next.isEmpty())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			StateSeq ret = next.removeFirst();
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
	// use index into rightStates for this iterator over pairs (left state given)
	// propose type of 
	private class RightPairIterator implements PIterator<StateSeq> {
		// main queue
		private FixedPrioritiesPriorityQueue<PIterator<Pair<IndexSymbol, Double>>> q;
		// wait queue
		private FixedPrioritiesPriorityQueue<PIterator<Pair<IndexSymbol, Double>>> w;
		// next items
		private FixedPrioritiesPriorityQueue<StateSeq> next;
		// for checking monotonicity
		private double lastcost;
		// how big a wait heap?
		private int maxheap;
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;

		private void initialize() {
			// initialize elements
			q = new FixedPrioritiesPriorityQueue<PIterator<Pair<IndexSymbol, Double>>>();
			// wait queue
			w = new FixedPrioritiesPriorityQueue<PIterator<Pair<IndexSymbol, Double>>>();
			next = new FixedPrioritiesPriorityQueue<StateSeq>();

		}
		// validates combination
		private OTFStringTransducerRuleSet atrans;
		private IndexSymbol left;
		// symbol of proposed right state -- not the state itself!
		private int nextSym = 0;
		private Vector<Pair<Symbol, Double>> vec;

		public RightPairIterator(IndexSymbol l, OTFStringTransducerRuleSet strs, int mh)  throws UnusualConditionException  {
			boolean debug = false;
			nextSym = 0;
			left = l;
			atrans = strs;
			vec = leftStateSyms.get(left.getIndex().right());
			maxheap = mh;
			if (vec == null)
				return;
			initialize();
			while (vec.size() > nextSym && q.isEmpty()) {
				if (debug) Debug.debug(debug, "proposing right states starting with "+vec.get(nextSym).l());
				// TODO: make this part more efficient!
				Symbol rightSym = vec.get(nextSym++).l();
				Vector<Symbol> tempVec = new Vector<Symbol>();
				tempVec.add(left.getSym());
				tempVec.add(rightSym);					
			//	VecSymbol vs = SymbolFactory.getVecSymbol(tempVec);
				// validate this symbol pairing
				if (!atrans.getForwardRules(tempVec).iterator().hasNext())
					continue;
				RightPairBaseIterator rpbi = new RightPairBaseIterator(left, rightSym);
				
				double cost = rpbi.peek().r();
				
				if (!q.add(rpbi, cost))
					throw new UnusualConditionException("Couldn't add new nonlexical iterator with cost "+cost);		
			}
			fillNext();
		}
		public boolean hasNext() {
			return next != null && !next.isEmpty();
		}
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
				PIterator<Pair<IndexSymbol, Double>> current = q.removeFirst();
				Pair<IndexSymbol, Double> rightPair  = current.next();
				StateSeq ss = new StateSeq();
				ss.states = new Vector<IndexSymbol>();
				ss.states.add(left);
				ss.weight = rightPair.r();
				ss.states.add(rightPair.l());
				next.add(ss, ss.weight);
				if (debug) Debug.debug(debug,  "Next state seq is "+ss);
				if (current.hasNext()) {
					double nextcost = current.peek().r();
					if (!q.add(current, nextcost))
						throw new UnusualConditionException("Couldn't add next step iterator with cost "+nextcost);
				}
				// add to wait queue until wait queue is not empty or we run out of lists
				// if this was lexical, it would have already been handled above in q
				while (w.isEmpty() && vec.size() > nextSym) {
					if (debug) Debug.debug(debug, "proposing right states starting with "+vec.get(nextSym).l());
					// TODO: make this part more efficient!
					Symbol rightSym = vec.get(nextSym++).l();
					Vector<Symbol> tempVec = new Vector<Symbol>();
					tempVec.add(left.getSym());
					tempVec.add(rightSym);					
					//VecSymbol vs = SymbolFactory.getVecSymbol(tempVec);
					// validate this symbol pairing
					if (!atrans.getForwardRules(tempVec).iterator().hasNext())
						continue;
					RightPairBaseIterator l = new RightPairBaseIterator(left, rightSym);
					
					double cost = l.peek().r();
					if (!w.add(l, cost))
						throw new UnusualConditionException("Couldn't add new nonlexical iterator with cost "+cost);
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
		public StateSeq peek() throws NoSuchElementException {
			if (!hasNext())
				throw new NoSuchElementException("Asked for peek on empty PIterator");
			return next.getFirst();
		}
		public StateSeq next() throws NoSuchElementException {
			if (!hasNext())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			StateSeq ret = next.removeFirst();
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
	
	//given left state and right state signature, iterate over right states
	private class RightPairBaseIterator implements PIterator<Pair<IndexSymbol, Double>> {
		private int nextItem;
		private Vector<Pair<IndexSymbol, Double>> vec;
		private Pair<IndexSymbol, Double> next;
		public RightPairBaseIterator(IndexSymbol l, Symbol rSym) {
			nextItem = 0;
			vec = leftStates.get(l.getIndex().right()).get(rSym);
			fillNext();
		}
		public boolean hasNext() {
			return next != null;
		}
		private void fillNext() {
			if (vec != null && vec.size() > nextItem) {
				next = vec.get(nextItem++);
			}
			else
				next = null;
		}
		public Pair<IndexSymbol, Double> next() throws NoSuchElementException {
			if (!hasNext())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			Pair<IndexSymbol, Double> ret = next;
			fillNext();
			return ret;
		}
		public Pair<IndexSymbol, Double> peek() throws NoSuchElementException {
			if (!hasNext())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			return next;
		}
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
		}
	}
	
//	// use index into rightStates for this iterator over pairs (left state given)
//	private class RightPairIterator implements PIterator<StateSeq> {
//		private IndexSymbol left;
//		private int nextItem;
//		private Vector<Pair<IndexSymbol, Double>> vec;
//		private StateSeq next;
//		public RightPairIterator(IndexSymbol l) {
//			nextItem = 0;
//			left = l;
//			vec = leftStates.get(left.getIndex().right());
//			fillNext();
//		}
//		public boolean hasNext() {
//			return next != null;
//		}
//		private void fillNext() {
//			if (vec != null && vec.size() > nextItem) {
//				next = new StateSeq();
//				next.states = new Vector<IndexSymbol>();
//				next.states.add(left);
//				next.weight = vec.get(nextItem).r();
//				next.states.add(vec.get(nextItem++).l());
//			}
//			else
//				next = null;
//		}
//		public StateSeq next() throws NoSuchElementException {
//			if (!hasNext())
//				throw new NoSuchElementException("Asked for next on empty PIterator");
//			StateSeq ret = next;
//			fillNext();
//			return ret;
//		}
//		public StateSeq peek() throws NoSuchElementException {
//			if (!hasNext())
//				throw new NoSuchElementException("Asked for next on empty PIterator");
//			return next;
//		}
//		public void remove() throws UnsupportedOperationException {
//			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
//		}
//	}

	
	// use index into leftStates for this iterator over pairs (rightt state given)
	
	private class LeftPairIterator implements PIterator<StateSeq> {
		// main queue
		private FixedPrioritiesPriorityQueue<PIterator<Pair<IndexSymbol, Double>>> q;
		// wait queue
		private FixedPrioritiesPriorityQueue<PIterator<Pair<IndexSymbol, Double>>> w;
		// next items
		private FixedPrioritiesPriorityQueue<StateSeq> next;
		// for checking monotonicity
		private double lastcost;
		// how big a wait heap?
		private int maxheap;
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;

		private void initialize() {
			// initialize elements
			q = new FixedPrioritiesPriorityQueue<PIterator<Pair<IndexSymbol, Double>>>();
			// wait queue
			w = new FixedPrioritiesPriorityQueue<PIterator<Pair<IndexSymbol, Double>>>();
			next = new FixedPrioritiesPriorityQueue<StateSeq>();

		}
		// validates combination
		private OTFStringTransducerRuleSet atrans;
		private IndexSymbol right;
		// symbol of proposed right state -- not the state itself!
		private int nextSym = 0;
		private Vector<Pair<Symbol, Double>> vec;

		public LeftPairIterator(IndexSymbol r, OTFStringTransducerRuleSet strs, int mh)  throws UnusualConditionException  {
			boolean debug = false;
			nextSym = 0;
			right = r;
			atrans = strs;
			vec = rightStateSyms.get(right.getIndex().left());
			maxheap = mh;
			if (vec == null)
				return;
			initialize();
			while (vec.size() > nextSym && q.isEmpty()) {
				if (debug) Debug.debug(debug, "proposing left states starting with "+vec.get(nextSym).l());
				// TODO: make this part more efficient!
				Symbol leftSym = vec.get(nextSym++).l();
				Vector<Symbol> tempVec = new Vector<Symbol>();
				tempVec.add(leftSym);			
				tempVec.add(right.getSym());
			//	VecSymbol vs = SymbolFactory.getVecSymbol(tempVec);
				// validate this symbol pairing
				if (!atrans.getForwardRules(tempVec).iterator().hasNext())
					continue;
				LeftPairBaseIterator rpbi = new LeftPairBaseIterator(leftSym, right);
				
				double cost = rpbi.peek().r();
				
				if (!q.add(rpbi, cost))
					throw new UnusualConditionException("Couldn't add new nonlexical iterator with cost "+cost);		
			}
			fillNext();
		}
		public boolean hasNext() {
			return next != null && !next.isEmpty();
		}
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
				PIterator<Pair<IndexSymbol, Double>> current = q.removeFirst();
				Pair<IndexSymbol, Double> leftPair  = current.next();
				StateSeq ss = new StateSeq();
				ss.states = new Vector<IndexSymbol>();
				ss.states.add(leftPair.l());
				ss.states.add(right);
				ss.weight = leftPair.r();
				next.add(ss, ss.weight);
				if (debug) Debug.debug(debug,  "Next state seq is "+ss);
				if (current.hasNext()) {
					double nextcost = current.peek().r();
					if (!q.add(current, nextcost))
						throw new UnusualConditionException("Couldn't add next step iterator with cost "+nextcost);
				}
				// add to wait queue until wait queue is not empty or we run out of lists
				// if this was lexical, it would have already been handled above in q
				while (w.isEmpty() && vec.size() > nextSym) {
					if (debug) Debug.debug(debug, "proposing left states starting with "+vec.get(nextSym).l());
					// TODO: make this part more efficient!
					Symbol leftSym = vec.get(nextSym++).l();
					Vector<Symbol> tempVec = new Vector<Symbol>();
					tempVec.add(leftSym);
					tempVec.add(right.getSym());					
//					VecSymbol vs = SymbolFactory.getVecSymbol(tempVec);
					// validate this symbol pairing
					if (!atrans.getForwardRules(tempVec).iterator().hasNext())
						continue;
					LeftPairBaseIterator l = new LeftPairBaseIterator(leftSym, right);
					
					double cost = l.peek().r();
					if (!w.add(l, cost))
						throw new UnusualConditionException("Couldn't add new nonlexical iterator with cost "+cost);
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
		public StateSeq peek() throws NoSuchElementException {
			if (!hasNext())
				throw new NoSuchElementException("Asked for peek on empty PIterator");
			return next.getFirst();
		}
		public StateSeq next() throws NoSuchElementException {
			if (!hasNext())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			StateSeq ret = next.removeFirst();
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
	
	//given left state signature and right state, iterate over left states
	private class LeftPairBaseIterator implements PIterator<Pair<IndexSymbol, Double>> {
		private int nextItem;
		private Vector<Pair<IndexSymbol, Double>> vec;
		private Pair<IndexSymbol, Double> next;
		public LeftPairBaseIterator(Symbol lSym, IndexSymbol r) {
			nextItem = 0;
			vec = rightStates.get(r.getIndex().left()).get(lSym);
			fillNext();
		}
		public boolean hasNext() {
			return next != null;
		}
		private void fillNext() {
			if (vec != null && vec.size() > nextItem) {
				next = vec.get(nextItem++);
			}
			else
				next = null;
		}
		public Pair<IndexSymbol, Double> next() throws NoSuchElementException {
			if (!hasNext())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			Pair<IndexSymbol, Double> ret = next;
			fillNext();
			return ret;
		}
		public Pair<IndexSymbol, Double> peek() throws NoSuchElementException {
			if (!hasNext())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			return next;
		}
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
		}
	}
	
//	// use index into leftStates for this iterator over pairs (right state given)
//	private class LeftPairIterator implements PIterator<StateSeq> {
//		private IndexSymbol right;
//		private int nextItem;
//		private Vector<Pair<IndexSymbol, Double>> vec;
//		private StateSeq next;
//		public LeftPairIterator(IndexSymbol l) {
//			nextItem = 0;
//			right = l;
//			vec = rightStates.get(right.getIndex().left());
//			fillNext();
//		}
//		public boolean hasNext() {
//			return next != null;
//		}
//		private void fillNext() {
//			if (vec != null && vec.size() > nextItem) {
//				next = new StateSeq();
//				next.states = new Vector<IndexSymbol>();
//				next.weight = vec.get(nextItem).r();
//				next.states.add(vec.get(nextItem++).l());
//				next.states.add(right);
//			}
//			else
//				next = null;
//		}
//		public StateSeq next() throws NoSuchElementException {
//			if (!hasNext())
//				throw new NoSuchElementException("Asked for next on empty PIterator");
//			StateSeq ret = next;
//			fillNext();
//			return ret;
//		}
//		public StateSeq peek() throws NoSuchElementException {
//			if (!hasNext())
//				throw new NoSuchElementException("Asked for next on empty PIterator");
//			return next;
//		}
//		public void remove() throws UnsupportedOperationException {
//			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
//		}
//	}
//	
//	
	
	
//	// alternate combination order: lexical is the same
//	// given a state, choose a rule that could cover it, then choose a state that could fit
//	private class AltFSLazyIterator implements PIterator<GrammarRule> {
//		// rule iterator -- rules that fit the base state on a particular side (left or right) 
//		
//		private Iterator<Pair<StringTransducerRule, Boolean>> biter;
//		// base state
//		private IndexSymbol base;
//
//
//		// main queue
//		private FixedPrioritiesPriorityQueue<PIterator<GrammarRule>> q;
//		// wait queue
//		private FixedPrioritiesPriorityQueue<PIterator<GrammarRule>> w;
//		// next items
//		private FixedPrioritiesPriorityQueue<GrammarRule> next;
//		// for checking monotonicity
//		private double lastcost;
//		// how big a wait heap?
//		private int maxheap;
//		// should we report monotonicity errors?
//		private static final boolean REPORTMONO = false;
//
//		private void initialize() {
//			// initialize elements
//			q = new FixedPrioritiesPriorityQueue<PIterator<GrammarRule>>();
//			// wait queue
//			w = new FixedPrioritiesPriorityQueue<PIterator<GrammarRule>>();
//			next = new FixedPrioritiesPriorityQueue<GrammarRule>();
//
//		}
//
//		// lexical rule generator
//		// queues for each position are added, as we assume we need to cover the string
//		// no need to be that lazy here!
//		public AltFSLazyIterator(OTFStringTransducerRuleSet trans, OTFString str, int mh) throws UnusualConditionException {
//			boolean debug = false;
//			initialize();
//			biter = ruleBoolempty.iterator();
//			maxheap = mh;
//			for (Symbol label : str.getLabels()) {
//				for (Index index: str.getMatches(label)) {
//					FSLexBaseLazyIterator l = new FSLexBaseLazyIterator(trans, label, index);
//					if (!l.hasNext())
//						continue;
//					double cost = -l.peek().getWeight();
//					if (!q.add(l, cost))
//						throw new UnusualConditionException("Couldn't add new lex iterator for "+label+", "+index+" with cost "+cost);
//				}
//			}
//			fillNext();
//			if (debug) Debug.debug(debug, "Initial list of lexical rules is "+next);
//		}
//		
//		// nonlexical lazy iterator
//		// given a state, iterate over rules that include that state (to left and to right)
//		// for each of these, create a base iterator that tries to find a matching state to fit in
//		
//		public AltFSLazyIterator(OTFStringTransducerRuleSet trans, IndexSymbol state, int mh) throws UnusualConditionException {
//			boolean debug = false;
//			initialize();
//			base = state;
//			biter = trans.getBinaryForwardRules(base.getSym()).iterator();
//			maxheap = mh;
//			if (!biter.hasNext())
//				return;
//			while (biter.hasNext() && q.isEmpty()) {
//				Pair<StringTransducerRule, Boolean> rw = biter.next();
//				AltFSBaseLazyIterator l = new AltFSBaseLazyIterator(base, rw.l(), rw.r());
//				if (!l.hasNext())
//					continue;
//				double cost = -l.peek().getWeight();
//				if (!q.add(l, cost))
//					throw new UnusualConditionException("Couldn't add new nonlexical iterator with cost "+cost);		
//			}
//			fillNext();
//			if (debug) Debug.debug(debug, "Initial list of nonlexical rules is "+next);
//
//		}
//		
//		
//		private void fillNext() throws UnusualConditionException {
//			boolean debug = false;
//			if (q.isEmpty()) {
//				if (debug) Debug.debug(debug, "Main queue is empty, so no move made");
//				return;
//			}
//
//			while (
//					(maxheap == 0 ? true : next.size() < maxheap) && 
//					!q.isEmpty()
//			) {
//				PIterator<GrammarRule> current = q.removeFirst();
//				GrammarRule g  = current.next();
//				next.add(g, -g.getWeight());
//				if (debug) Debug.debug(debug,  "Next rule is "+g);
//				if (current.hasNext()) {
//					double nextcost = -current.peek().getWeight();
//					if (!q.add(current, nextcost))
//						throw new UnusualConditionException("Couldn't add next step iterator with cost "+nextcost);
//				}
//				// add to wait queue until wait queue is not empty or we run out of lists
//				// if this was lexical, it would have already been handled above in q
//				while (w.isEmpty() && biter.hasNext()) {
//					Pair<StringTransducerRule, Boolean> rw = biter.next();
//					AltFSBaseLazyIterator l = new AltFSBaseLazyIterator(base, rw.l(), rw.r());
//					if (!l.hasNext())
//						continue;
//					double cost = -l.peek().getWeight();
//					if (!w.add(l, cost))
//						throw new UnusualConditionException("Couldn't add new nonlexical iterator with cost "+cost);
//				}
//
//				// migrate lead from wait queue over to main queue
//				if (!w.isEmpty()) {
//					double waitcost = w.getPriority();
//					if (q.isEmpty() || waitcost > q.getPriority()) {
//						if (debug) Debug.debug(debug, "Moving list from wait to queue");
//						if (!q.add(w.removeFirst(), waitcost))
//							throw new UnusualConditionException("Couldn't migrate waiting iterator to main queue");
//					}
//				}
//			}
//			if (REPORTMONO && lastcost < next.getPriority())
//				Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
//			//throw new UnusualConditionException("Monotonicity of lazy iterator violated: got "+next+" after "+lastcost);
//			lastcost = next.getPriority();
//			if (debug) Debug.debug(debug, "Queue is "+next);
//		}
//		public boolean hasNext() {
//			return !next.isEmpty();
//		}
//		public GrammarRule peek() throws NoSuchElementException {
//			if (next.isEmpty())
//				throw new NoSuchElementException("Asked for peek on empty PIterator");
//			return next.getFirst();
//		}
//		public GrammarRule next() throws NoSuchElementException {
//			if (next.isEmpty())
//				throw new NoSuchElementException("Asked for next on empty PIterator");
//			GrammarRule ret = next.removeFirst();
//			try {
//				fillNext();
//			}
//			catch (UnusualConditionException e) {
//				throw new NoSuchElementException("Masking UnusualConditionException: "+e.getMessage());
//			}
//
//			return ret;
//		}
//		public void remove() throws UnsupportedOperationException {
//			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
//		}
//
//
//	}
//	
//	
//	
//	// non-lex version
//	// given a state, a rule, and a location for that state,
//	// iterate through matching available states, combine, and form the result
//
//	private class AltFSBaseLazyIterator implements PIterator<GrammarRule> {
//		private GrammarRule next;
//		private IndexSymbol baseState;
//		private StringTransducerRule baseRule;
//		private boolean isBaseLeft;
//		private int nextItem = 0;
//		private Vector<Pair<IndexSymbol, Double>> vec;
//		private double lastcost = 0;
//		// should we report monotonicity errors?
//		private static final boolean REPORTMONO = false;
//		public AltFSBaseLazyIterator(IndexSymbol bState, StringTransducerRule bRule, boolean isLeft) throws UnusualConditionException {
//			next = null;
//			baseState = bState;
//			baseRule = bRule;
//			isBaseLeft = isLeft;
//			// may not have to do any matching if rule is solo -- and there will be just one rule
//			if (baseRule.getRHSLeaves().length == 1) {
//				Vector<Index> base = new Vector<Index>();
//				base.add(baseState.getIndex());
//				next = formAndAddRules(baseRule, base);
//			}
//			else {
//				// what index and what state do we have to match?
//				if (isBaseLeft) {
//					int rightStateLeftIndex = bState.getIndex().right();
//					Symbol rightStateSym = baseRule.getRHS().getItemLeaves().get(1).getState();
//					if (leftStates.goc(rightStateLeftIndex).containsKey(rightStateSym))
//						vec = leftStates.get(rightStateLeftIndex).get(rightStateSym);
//				}
//				else {
//					int leftStateRightIndex = bState.getIndex().left();
//					Symbol leftStateSym = baseRule.getRHS().getItemLeaves().get(0).getState();
//					if (rightStates.goc(leftStateRightIndex).containsKey(leftStateSym))
//						vec = rightStates.get(leftStateRightIndex).get(leftStateSym);
//				}
//			}
//			fillNext();
//		}
//		private void fillNext() throws UnusualConditionException {
//			boolean debug =false;
//			if (vec == null || vec.size() <= nextItem)
//				return;
//			Vector<Index> base = new Vector<Index>();
//			if (isBaseLeft) {
//				base.add(baseState.getIndex());
//				Pair<IndexSymbol, Double> rightState = vec.get(nextItem++);
//				base.add(rightState.l().getIndex());
//			}
//			else {
//				Pair<IndexSymbol, Double> leftState = vec.get(nextItem++);
//				base.add(leftState.l().getIndex());
//				base.add(baseState.getIndex());
//			}
//			if (debug) Debug.debug(debug, "Matching "+baseRule+" to "+base);
//			GrammarRule newg = formAndAddRules(baseRule, base);
//			if (debug) Debug.debug(debug,  "Built rule for "+base+"; "+newg);
//			next = newg;		
//		}
//		public boolean hasNext() {
//			return next != null;
//		}
//		public GrammarRule peek() throws NoSuchElementException {
//			if (!hasNext())
//				throw new NoSuchElementException("Asked for peek on empty PIterator");
//			return next;
//		}
//		public GrammarRule next() throws NoSuchElementException {
//			boolean debug = false;
//			if (!hasNext())
//				throw new NoSuchElementException("Asked for next on empty PIterator");
//			GrammarRule ret = next;
//			try {
//				fillNext();
//			}
//			catch (UnusualConditionException e) {
//				throw new NoSuchElementException("Masking UnusualConditionException: "+e.getMessage());
//			}
//			return ret;
//		}
//		public void remove() throws UnsupportedOperationException {
//			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
//		}
//	}
//	
	public TSOTFGrammar(OTFStringTransducerRuleSet t, OTFString si, Semiring s, int mh, int c) {
		super(s, c);
		initialize();
		trans = t;
		string = si;
		startState = IndexSymbol.get(t.getStartState(), si.getSpan());
		maxheap = mh < 0 ? 0 : mh;
		cap = 0;
//		cap = c;
	}
	
	@Override
	public PIterator<GrammarRule> getBSIter(Symbol s)
			throws UnusualConditionException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PIterator<GrammarRule> getBSIter(Symbol s, Symbol label, int rank)
			throws UnusualConditionException {
		// TODO Auto-generated method stub
		return null;
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
	public PIterator<GrammarRule> getFSIter(Symbol s, int pos)
			throws UnusualConditionException {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public PIterator<GrammarRule> getFSIter(Symbol s)
			throws UnusualConditionException {
		if (s instanceof IndexSymbol)
			return new FSIndexedIterator(trans, (IndexSymbol)s, maxheap);
//			return new FSLazyIterator(trans, (IndexSymbol)s, maxheap);
//		return new AltFSLazyIterator(trans, (IndexSymbol)s, maxheap);
		else
			throw new UnusualConditionException("Asked for rules from non-index symbol "+s);
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
		return new FSLazyIterator(trans, string, maxheap);
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
		// TODO Auto-generated method stub
		return null;
	}

	// inject states as their inside cost is found. important for building new rules
	// ignore cap
	@Override
	boolean injectState(Symbol s, double wgt) throws UnusualConditionException {
		boolean debug = false;
		
		if (!(s instanceof IndexSymbol))
			throw new UnusualConditionException("Tried to inject non-indexed state "+s);
		IndexSymbol state = (IndexSymbol)s;
		if (allStates.contains(state))
			return true;
		allStates.add(state);
		if (debug) Debug.debug(debug, "Injecting "+state+", "+wgt);
		int left = state.getIndex().left();
		int right = state.getIndex().right();
		Symbol sym = state.getSym();
		
		if (!leftStateSyms.containsKey(left)) {
			leftStateSyms.put(left, new Vector<Pair<Symbol, Double>>());
		}
		if (!rightStateSyms.containsKey(right)) {
			rightStateSyms.put(right, new Vector<Pair<Symbol, Double>>());
		}
		// left and right state sym is for first appearance 
		// of particular left/right/states with this nonterm
		boolean doLeftStateSym = false;
		boolean doRightStateSym = false;
		if (!leftStates.goc(left).containsKey(sym)) {
			leftStates.get(left).put(sym, new Vector<Pair<IndexSymbol, Double>>());
			doLeftStateSym = true;
		}
		if (!rightStates.goc(right).containsKey(sym)) {
			rightStates.get(right).put(sym, new Vector<Pair<IndexSymbol, Double>>());
			doRightStateSym = true;
		}
		Pair<IndexSymbol, Double> newPair = new Pair<IndexSymbol, Double>(state, wgt); 
		if (doLeftStateSym || doRightStateSym) {
			Pair<Symbol, Double> newSymPair = new Pair<Symbol, Double>(sym, wgt);
			if (doLeftStateSym) 
				leftStateSyms.get(left).add(newSymPair);
			if (doRightStateSym)
				rightStateSyms.get(right).add(newSymPair);
		}
		leftStates.get(left).get(sym).add(newPair);
		rightStates.get(right).get(sym).add(newPair);
		return true;

	}
	void reportRules() {
		Debug.prettyDebug("TSOTF Grammar has "+ruleCount+" rules");
	}
	// states we can use -- validated by the chart, and in order by weight
	// indexed by left and right sides and by Symbols
	private PMap<Integer, PMap<Symbol, Vector<Pair<IndexSymbol, Double>>>> leftStates;
	private PMap<Integer, PMap<Symbol, Vector<Pair<IndexSymbol, Double>>>> rightStates;
	
	// ordered index into the below
	private PMap<Integer, Vector<Pair<Symbol, Double>>> leftStateSyms;
	private PMap<Integer, Vector<Pair<Symbol, Double>>> rightStateSyms;


	
	// just track which states have been added so duplicates aren't added
	private HashSet<IndexSymbol> allStates;
	
	// grammar is made up of transducer and string
	private OTFStringTransducerRuleSet trans;
	private OTFString string;
	private IndexSymbol startState;
	private int maxheap;
	// rule-per-state cap (enforced by indexed iterators)
	private int cap;
	
	
	
	
	
	// lex rules -- transducer rule and indicies
	private PMap<StringTransducerRule, PMap<Index, GrammarRule>> ruleIndexPairs;
	// non-lex rules -- transducer rule and vector of spans
	private PMap<StringTransducerRule, PMap<Vector<Index>, GrammarRule>> rulePairs;
	
	// count rules as we form them
	private int ruleCount;
	private void incRuleCount() {
		ruleCount++;
		boolean debug = false;
		final int inc = 1000;
		if (debug && ruleCount % inc == 0) {
			Debug.prettyDebug("TSOTF has "+ruleCount+" rules");
		}
	}
	private static Vector<StateSeq> stateempty;
	private static Vector<Pair<StringTransducerRule, Boolean>> ruleBoolempty;
	static {
		stateempty = new Vector<StateSeq> ();
		ruleBoolempty = new Vector<Pair<StringTransducerRule, Boolean>> ();
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		TropicalSemiring semiring = new TropicalSemiring();
		try {
			boolean tryExhaust = Boolean.parseBoolean(args[0]);
			int mh = Integer.parseInt(args[1]);
//			int c = Integer.parseInt(args[2]);
			int k = Integer.parseInt(args[2]);

			
			if (tryExhaust)
				Debug.debug(true, "Exhaustively parsing each string!");
			int length = args.length;
			Vector strvec = CFGTraining.readItemSet(
					new BufferedReader(new InputStreamReader(new FileInputStream(args[length-1]), "utf-8")), 
					false, semiring);
			
			File strfile = (File)strvec.get(0);
			int strcount = ((Integer)strvec.get(1)).intValue();
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(strfile));
			// read in chain of binary ottrs, and save in backward order
			Vector<BinaryTreeTransducer> tts = new Vector<BinaryTreeTransducer>();
			Vector<Integer> ttsCaps = new Vector<Integer>();
			OTFStringTransducerRuleSet ostrs=null;
			int ostrscap = 0;
			int nextarg = 3;
			int argsize = args.length;
			Debug.debug(true, "Max heap of "+mh+" and getting "+k+" answers");
			while (nextarg < argsize-1) {
				int c = Integer.parseInt(args[nextarg++]);
				String filename = args[nextarg++];
				// special ngram transducer
				if (filename.equals("NG")) {
					filename = args[nextarg++];
					Debug.debug(true, "cap of "+c+" for lm "+filename);
					OTFLMTreeTransducerRuleSet lm = new OTFLMTreeTransducerRuleSet(filename, "utf-8", semiring);
					tts.add(0, lm);
					ttsCaps.add(0, c);
				}
				// normal tree transducer
				else if (nextarg < argsize-2) {
					Debug.debug(true, "cap of "+c+" for tt "+filename);
					TreeTransducerRuleSet ttrs = new TreeTransducerRuleSet(filename, "utf-8", semiring);
					OTFTreeTransducerRuleSet ottrs = new OTFTreeTransducerRuleSet(ttrs);
					tts.add(0, ottrs);
					ttsCaps.add(0, c);
				}
				// end of cascade string transducer
				else {
					Debug.debug(true, "cap of "+c+" for ts "+filename);
					StringTransducerRuleSet trs = new StringTransducerRuleSet(filename, "utf-8", semiring);
					ostrs = new OTFStringTransducerRuleSet(trs);
					ostrscap = c;
				}
			}
			
			Date wholeStartTime = new Date();
			
			int oneBestEOLCount = 0;
			double totalOneBestScore = 0;
			for (int strnum = 0; strnum < strcount; strnum++) {
				IndexSymbol.clear();
				FilteringPairSymbol.clear();
				PairSymbol.clear();
				Index.clear();
				System.gc(); System.gc(); System.gc();
				StringItem str = (StringItem)ois.readObject();
				OTFString ostr = new OTFString(str);
				Date startTime = new Date();

				Grammar g;
				TSOTFGrammar tsg = new TSOTFGrammar(ostrs, ostr, semiring, mh, ostrscap);
				if (tryExhaust) {
					Date preExTime = new Date();
					tsg.exhaust(false);
					ConcreteGrammar cg = new ConcreteGrammar(tsg);
					Date postExTime = new Date();
					Debug.dbtime(1, 1, preExTime, postExTime, "time to exhaustively parse and convert");
					g = cg;
				}
				else {
					g = tsg;
				}
				// TODO: move this leftover stuff for LM TTRS!
				//			if (length >= 6) {
				//				OTFLMTreeTransducerRuleSet lm = new OTFLMTreeTransducerRuleSet(args[length-3], "utf-8", semiring);
				//				TGOTFGrammar otfg = new TGOTFGrammar(lm, g, semiring, mh, c, false);
				//				g = otfg;
				//			}
				// TODO: change below to length-4	
				int transNum = 0;
				for (BinaryTreeTransducer ottrs : tts) {
					int c = ttsCaps.get(transNum++);
					//			Debug.prettyDebug("Done converting transducer to on-the-fly form");
					//			Debug.prettyDebug("Done building on-the-fly grammar");
					TGOTFGrammar otfg = new TGOTFGrammar(ottrs, g, semiring, mh, c, false);
					g = otfg;
				}
				if (mh < 0) {
					ConcreteGrammar gr = new ConcreteGrammar(g);
					g = gr;
				}
				Date preGenTime = new Date();
				for (int i = 0; i < k; i++) {
					Pair<TreeItem, Pair<Double, Integer>> tt = g.getNextTree(false);
					if (tt == null) {
						System.out.println(strnum+":EOL");
						if (i == 0)
							oneBestEOLCount++;
					}
					else {
						System.out.println(strnum+":"+(i+1)+":"+tt.l()+" # "+tt.r().l()+" # "+tt.r().r());
						if (i == 0)
							totalOneBestScore+=tt.r().l();
					}

				}
//				for (int i = 0; i < tts.size(); i++)
//					tts.get(i).getNullCount();
//				ostrs.getNullCount();
				Date postGenTime = new Date();
				Debug.dbtime(1, 1, preGenTime, postGenTime, "time to generate");
				Date endTime = new Date();
				Debug.dbtime(1, 1, startTime, endTime, "total time");

				g.reportRules();

			}
			Date wholeEndTime = new Date();
			Debug.dbtime(1, 1, wholeStartTime, wholeEndTime, "grand total time over "+strcount);
			System.out.println(oneBestEOLCount+" had no one-best");
			System.out.println(totalOneBestScore+" total one-best score");
			System.out.println(Runtime.getRuntime().totalMemory()+" total");
			
			System.out.println(Runtime.getRuntime().freeMemory()+" free");

		}
		catch (ClassNotFoundException e) {
			System.err.println("Class not found reading string batch ");
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
		catch (DataFormatException e) {
			System.err.println("Bad data format reading rtg "+args[0]);
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
			System.err.println("IO error reading rtg "+args[0]);
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
