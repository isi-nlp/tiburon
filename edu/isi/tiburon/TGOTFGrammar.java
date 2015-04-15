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




import edu.stanford.nlp.util.FixedPrioritiesPriorityQueue;

public class TGOTFGrammar extends Grammar {

	private static int bsLazyCreateCount = 0;
	private static int bsBaseLazyCreateCount = 0;
	private static int extRHSCount = 0;
	private static int olRHSCount = 0;
	
	private static int bsIndexCreateCount = 0;
	private static int bsIndexReuseCount = 0;
	private static long fillNextTime = 0;
	private static long labelVerTime = 0;
	private static long transItTime = 0;
	private static long getItemTime = 0;
	private static long buildBBITime = 0;
	private static long getGramTime = 0;
	private static long migrateTime = 0;
	private static long bsItTime = 0;
	
	private static int fillNextCalls = 0;
	
	public static void resetTimers() {
		bsLazyCreateCount = bsBaseLazyCreateCount = 
			extRHSCount = olRHSCount = fillNextCalls = 
				bsIndexCreateCount = bsIndexReuseCount = 0;
		fillNextTime = labelVerTime = transItTime = 
			getItemTime = buildBBITime =getGramTime =migrateTime = bsItTime = 0;
	}
	public static void printTimers() {
		System.out.println(bsLazyCreateCount+" bs main lazy items created");
		System.out.println(bsBaseLazyCreateCount+" bs base lazy items created");
		System.out.println(bsIndexCreateCount+" items created for the index");
		System.out.println(bsIndexReuseCount+" items reused in the index");
		System.out.println(bsIndexReuseCount+" items reused in the index");
		System.out.println(fillNextCalls+" calls to fillNext");
		System.out.println(fillNextTime+" time spent on fillNext");
		System.out.println("\t"+labelVerTime+" time spent on label verification of fillNext");
		System.out.println("\t"+transItTime+" time spent on creating transducer iterators in fillNext");
		System.out.println("\t"+getItemTime+" time spent getting next item in fillNextt");
		System.out.println("\t"+buildBBITime+" time spent building base iterator in fillNextt");
		System.out.println("\t"+getGramTime+" time spent getting new grammar rules in fillNextt");
		System.out.println("\t"+migrateTime+" time spent migrating wait to main queue in fillNextt");
		System.out.println(bsItTime+" time spent on BS iterator init WITHOUT fillNext");

		
		
	}
	
	public TGOTFGrammar(BinaryTreeTransducer t, Grammar g, Semiring s, int mh, int c, boolean istd) {
		super(s, c);
		isTD = istd;
		initialize();
		trans = t;
		grammar = g;
		
		if (t instanceof OTFTreeTransducerRuleSet) {
			startState = FilteringPairSymbol.get(((OTFTreeTransducerRuleSet)trans).getStartState(), 
					grammar.getStartState(),  
					isTD || !ALLOWINEPS ? FilteringPairSymbol.FILTER.NOEPS : FilteringPairSymbol.FILTER.REAL);
		}
		else
			startState = null;
		maxheap = mh < 0 ? 0 : mh;

		cap = 0;
		//cap = c;
	}


	// vector of symbols and double below requires uniquification
	private static HashMap<String, Pair<Vector<Symbol>, Double>> strUniqueMap;
	private static Pair<Vector<Symbol>, Double> uniquifyString(Pair<Vector<Symbol>, Double> in) {
		// maybe we don't want this uniqued after all!
		return in;
//		String str = in.toString();
//		if (!strUniqueMap.containsKey(str))
//			strUniqueMap.put(str, in);
//		return strUniqueMap.get(str);
	}
	// take a tree transducer rule and (state, state)->variable map
	// form the lhs of the rule, with the variables replaced by (state, state) into
	// a grammarRule rhs
	// get top state of the rule from the front of the vector
	// if TD, bottom states are NOEPS. 
	// if BU, for out-eps r, do as above. Otherwise, they're REAL (experimental)
	private GrammarRule formAndAddRules(
			TreeTransducerRule r, 
			Pair<Vector<Symbol>, Double> s) throws UnusualConditionException {
		Pair<Vector<Symbol>, Double> us = uniquifyString(s);
		boolean debug = false;
		boolean tempdbg = false;
		Vector<Symbol> str = s.l();
		Symbol b = str.remove(0);
		FilteringPairSymbol fs = FilteringPairSymbol.get(r.getState(), b, FilteringPairSymbol.FILTER.NOEPS);
		if (
				rulePairs.containsKey(r) && 
				rulePairs.get(r).containsKey(us) && 
				rulePairs.get(r).get(us).containsKey(fs)) {
			GrammarRule ret = rulePairs.get(r).get(us).get(fs);
			if (debug||tempdbg) Debug.debug(debug||tempdbg, "Returning extant rule "+ret);
			return ret;
		}

		if (!rulePairs.containsKey(r))
			rulePairs.put(r, new HashMap<Pair<Vector<Symbol>, Double>, HashMap<FilteringPairSymbol, GrammarRule>>());
		if (!rulePairs.get(r).containsKey(us))
			rulePairs.get(r).put(us, new HashMap<FilteringPairSymbol, GrammarRule>());

		// var map: each member of the input string is ((statea, stateb), var).
		// map var -> (statea, stateb, NOEPS)
		
		double w2 = s.r();
		HashMap<Symbol, FilteringPairSymbol> varMap = new HashMap<Symbol, FilteringPairSymbol>();
		
		for (Symbol m : str) {
			if (!(m instanceof PairSymbol) || !(((PairSymbol)m).getLeft() instanceof PairSymbol ))
				throw new UnusualConditionException("Bad form for "+m);
			PairSymbol mp = (PairSymbol)m;
			PairSymbol mpl = (PairSymbol)mp.getLeft();
			
			varMap.put(mp.getRight(), 
					FilteringPairSymbol.get(mpl.getLeft(), mpl.getRight(), 
							isTD || r.isOutEps() || !ALLOWINEPS ? FilteringPairSymbol.FILTER.NOEPS : FilteringPairSymbol.FILTER.REAL));
			 
		}
		
		Vector<Symbol> rhs = new Vector<Symbol>();
		// in-eps case: map to the label, not the children
		Symbol label = null;
		if (r.isInEps()) {
			if (!varMap.containsKey(r.getLHS().getVariable()))
				throw new UnusualConditionException("No mapping for variable in "+r.getLHS()+" in "+varMap);
			rhs.add(varMap.get(r.getLHS().getVariable()));
			//label = Symbol.getEpsilon();
		}
		else {
			for (int i = 0; i < r.getLHS().getNumChildren(); i++) {
				if (!varMap.containsKey(r.getLHS().getChild(i).getVariable()))
					throw new UnusualConditionException("No mapping for variable in "+r.getLHS().getChild(i)+" in "+varMap);
				rhs.add(varMap.get(r.getLHS().getChild(i).getVariable()));
			}
			label = r.getLHS().getLabel();
		}
		ConcreteGrammarRule rule = new ConcreteGrammarRule(fs, label, rhs, getSemiring().times(r.getWeight(), w2));
		incRuleCount();
		if (debug) Debug.debug(debug, "Built rule "+rule+" from "+r+" and "+us+"("+us.hashCode()+") and "+fs);
		rulePairs.get(r).get(us).put(fs, rule);
		return rule;
	}
	
	
	// take a single-level tree transducer rule and single grammar rule,
	// form the lhs of the trans rule, with the variables replaced by (state, state) into
	// a grammarRule rhs
	
	// TD or eps-eps: leaf states have NOEPS filter
	// BU: leaf states have REAL filter
	private GrammarRule formAndAddRules(
			TreeTransducerRule r, 
			GrammarRule g,
			FilteringPairSymbol fs) throws UnusualConditionException {

		FilteringPairSymbol.FILTER f = fs.getFilter();
		boolean debug = false;
		boolean tempdbg = false;
		
		// don't allow eps-eps from a filter other than NOEPS
		if (r.isOutEps() && !fs.getFilter().equals(FilteringPairSymbol.FILTER.NOEPS))
			throw new UnusualConditionException("Tried to do eps-eps adding of "+r+" with filter "+fs.getFilter());
		if (oneLevelRulePairs.goc(r).goc(g).containsKey(fs)) {
			GrammarRule ret = oneLevelRulePairs.get(r).get(g).get(fs);
//			if (debug||tempdbg) Debug.debug(debug||tempdbg, "Returning extant rule "+ret);
			
			return ret;
		}
		

		// var map: map var -> (statea, stateb, NOEPS)
		
		HashMap<Symbol, FilteringPairSymbol> varmap = new HashMap<Symbol, FilteringPairSymbol>();
		// eps-eps case: don't allow rules from anything other than NOEPS.
		// out-state is NOEPS in TD or BU
		if (g.getLabel() == null && r.isOutEps()) {
			if (!f.equals(FilteringPairSymbol.FILTER.NOEPS))
				throw new UnusualConditionException("Tried to build rule out of eps-eps pair "+g+" and "+r+" with filter "+f);
			FilteringPairSymbol childState = FilteringPairSymbol.get(r.getRHS().getState(), g.getChild(0), FilteringPairSymbol.FILTER.NOEPS);
			varmap.put(r.getRHS().getVariable(), childState);
		}
		// normal case. out-state filter depends on isTD
		else {
			for (int i = 0; i < g.getChildren().size(); i++) {
				TransducerRightTree trt = r.getRHS().getChild(i);
				if (!trt.hasVariable())
					throw new UnusualConditionException("Tried to do special case mapping with non-normal transducer rule "+r);
				FilteringPairSymbol childState = FilteringPairSymbol.get(trt.getState(), 
						g.getChild(i), 
						isTD || !ALLOWINEPS ? FilteringPairSymbol.FILTER.NOEPS : FilteringPairSymbol.FILTER.REAL);
				if (varmap.containsKey(trt.getVariable()))
					throw new UnusualConditionException("Shouldn't have more than one mapping for variable: "+r+" and "+g);
				varmap.put(trt.getVariable(), childState);
//				if(debug) Debug.debug(debug, "Mapped "+trt.getVariable()+" to "+childState.toInternalString());
			}
		}
		Vector<Symbol> rhs = new Vector<Symbol>();
		// in-eps case: map to the label, not the children
		Symbol label = null;
		if (r.isInEps()) {
			if (!varmap.containsKey(r.getLHS().getVariable()))
				throw new UnusualConditionException("No mapping for variable in "+r.getLHS()+" in "+varmap);
			rhs.add(varmap.get(r.getLHS().getVariable()));
			//label = Symbol.getEpsilon();
		}
		else {
			for (int i = 0; i < r.getLHS().getNumChildren(); i++) {
				if (!varmap.containsKey(r.getLHS().getChild(i).getVariable()))
					throw new UnusualConditionException("No mapping for variable in "+r.getLHS().getChild(i)+" in "+varmap);
				rhs.add(varmap.get(r.getLHS().getChild(i).getVariable()));
			}
			label = r.getLHS().getLabel();
		}
		ConcreteGrammarRule rule = new ConcreteGrammarRule(fs, label, rhs, getSemiring().times(r.getWeight(), g.getWeight()));
		incRuleCount();
		if (debug) Debug.debug(debug, "Built rule "+rule+" from "+r+" and "+g);
		oneLevelRulePairs.get(r).get(g).put(fs, rule);
		return rule;
	}
	
	
	
	
	// given a symbol and grammar rule, 
	
	// match state from transducer with epsilon (State change) grammar rule and form rtg rule. then
	// return the result
	// slightly different variants for TD and BU
	// BS(td) and FS(bu) variants are very similar except for filter position

	private GrammarRule formAndAddRules(FilteringPairSymbol fs, GrammarRule g) throws UnusualConditionException {
		boolean debug = false;
		Symbol s = fs.getLeft();
		FilteringPairSymbol.FILTER f = fs.getFilter();
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
		
		// incoming state can't be lefteps or REAL
		if (f.equals(FilteringPairSymbol.FILTER.LEFTEPS) || f.equals(FilteringPairSymbol.FILTER.REAL))
			return null;
		FilteringPairSymbol instate;
		FilteringPairSymbol outstate;
		if (isTD) {
			//  if filtering, outstate must be "RIGHT". instate is what it is
			outstate = FilteringPairSymbol.get(s, g.getChildren().get(0), 
					(ALLOWINEPS ? FilteringPairSymbol.FILTER.RIGHTEPS : FilteringPairSymbol.FILTER.NOEPS));
			instate = FilteringPairSymbol.get(s, g.getState(), f);
		}
		else {
			//  if filtering, instate must be "RIGHT". outstate is what it is
			outstate = FilteringPairSymbol.get(s, g.getChildren().get(0), f);
			instate = FilteringPairSymbol.get(s, g.getState(), 
					(ALLOWINEPS ? FilteringPairSymbol.FILTER.RIGHTEPS : FilteringPairSymbol.FILTER.NOEPS));
		}
		Vector<Symbol> c = new Vector<Symbol>();
		
		c.add(outstate);

		ConcreteGrammarRule newg = new ConcreteGrammarRule(instate, null, c, g.getWeight());
		incRuleCount();
		if (debug) Debug.debug(debug, "Formed "+newg+" from "+fs+" and "+g);
		stateRulePairs.get(fs).put(g, newg);
		return newg;	
	}
	
	// given an output-eps transducer rule and a grammar state, form a rtg rule 
	// match epsilon (State change) grammar rule with state from transducer and form rtg rule. then
	// return the result
	
	// BS(td) and FS(bu) variants are very similar except for filter position
	private GrammarRule formAndAddRules(TreeTransducerRule g, FilteringPairSymbol fs) throws UnusualConditionException {
		boolean debug = false;
		Symbol s = fs.getRight();
		FilteringPairSymbol.FILTER f = fs.getFilter();
		if (!g.isOutEps())
			throw new UnusualConditionException("Tried to map non-out-epsilon transducer rule "+g+" to grammar state "+s);
//		if (debug) Debug.debug(debug, "Trying to form rules with out-eps transducer rule "+g+" and grammar state "+s+" and filter "+f);

		// check to avoid repeats
		// check to avoid repeats -- this supersedes individual checks above!
		if (ruleStatePairs.containsKey(g) && ruleStatePairs.get(g).containsKey(fs)) {
			if (debug) Debug.debug(debug, "Returning extant rule");

			return ruleStatePairs.get(g).get(fs);
		}
		if (!ruleStatePairs.containsKey(g))
			ruleStatePairs.put(g, new HashMap<Symbol, GrammarRule>());
		
		// incoming state can't be righteps or REAL
		if (f.equals(FilteringPairSymbol.FILTER.RIGHTEPS) || f.equals(FilteringPairSymbol.FILTER.REAL))
			return null;
		FilteringPairSymbol instate;
		FilteringPairSymbol outstate;
		
		// td and bu mirror conditions
		if (isTD) {
			//  if filtering, outstate must be "LEFT". instate is what it is
			outstate = FilteringPairSymbol.get(g.getRHS().getState(), s, 
					(ALLOWINEPS ? FilteringPairSymbol.FILTER.LEFTEPS : FilteringPairSymbol.FILTER.NOEPS));
			instate = fs;
		}
		else {
		//  if filtering, instate must be "LEFT". outstate is what it is
			instate = FilteringPairSymbol.get(g.getState(), s, 
					(ALLOWINEPS ? FilteringPairSymbol.FILTER.LEFTEPS : FilteringPairSymbol.FILTER.NOEPS));
			outstate = fs;
		}
		Vector<Symbol> rhs = new Vector<Symbol>();
		rhs.add(outstate);
		ConcreteGrammarRule rule = new ConcreteGrammarRule(instate, g.getLHS().getLabel(), rhs, g.getWeight());
		incRuleCount();
		ruleStatePairs.get(g).put(fs, rule);
		if (debug) Debug.debug(debug, "Formed "+rule+" from "+g+" and "+fs);
		return rule;		
	}
		
	
	// given a filter state, convert from REAL to that filter state
	// used to unify FS construction
	private GrammarRule formRealFilterRule(FilteringPairSymbol fs) throws UnusualConditionException {
		boolean debug = false;
		if (realRules.containsKey(fs))
			return realRules.get(fs);
		FilteringPairSymbol.FILTER f = fs.getFilter();
		if (f == FilteringPairSymbol.FILTER.REAL)
			throw new UnusualConditionException("Tried to make RealFilterRule from filter state REAL");
		Vector<Symbol> rhs = new Vector<Symbol>();
		rhs.add(fs);
		FilteringPairSymbol instate = FilteringPairSymbol.get(fs.getLeft(), fs.getRight(), FilteringPairSymbol.FILTER.REAL);
		ConcreteGrammarRule rule = new ConcreteGrammarRule(instate, null, rhs, 0);
		incRuleCount();
		if (debug) Debug.debug(debug, "Formed unfiltering rule "+rule);

		realRules.put(fs, rule);
		return rule;

	}
	
	
	// allows memoization of BS members and multiple simultaneous access of the same list
	private HashMap<Symbol, Vector<GrammarRule>> bsUnfilteredResultTable;
	private HashMap<Symbol, PIterator<GrammarRule>> bsUnfilteredIterTable;
	// state->label->rank->thing
	private PMap<Symbol, PMap<Symbol, PMap<Integer, Vector<GrammarRule>>>> bsFilteredResultTable;
	private PMap<Symbol, PMap<Symbol, PMap<Integer, PIterator<GrammarRule>>>> bsFilteredIterTable;
	
	private class BSIndexedIterator implements PIterator<GrammarRule> {
		private int next;
		private Symbol state;
		private Symbol label;
		private int rank;
		private Vector<GrammarRule> results;
		private PIterator<GrammarRule> iterator;
		public BSIndexedIterator(OTFTreeTransducerRuleSet trans, Grammar gram,  FilteringPairSymbol s, int mh) throws UnusualConditionException {
			next = 0;
			state = s;
			boolean debug = false;
			if (!bsUnfilteredIterTable.containsKey(state)) {
				bsIndexCreateCount++;
				bsUnfilteredIterTable.put(state, new BSLazyIterator(trans, gram, s, mh));
//				bsUnfilteredIterTable.put(state, new BSInvertLazyIterator(trans, gram, s, mh));

			}
			else {
				bsIndexReuseCount++;
				if (debug )Debug.debug(debug, "Reuse of "+s);
			}
			if (!bsUnfilteredResultTable.containsKey(state))
				bsUnfilteredResultTable.put(state, new Vector<GrammarRule>());
			results = bsUnfilteredResultTable.get(state);
			iterator = bsUnfilteredIterTable.get(state);
		}
		public BSIndexedIterator(OTFTreeTransducerRuleSet trans, Symbol l, int r, Grammar gram, FilteringPairSymbol s, int mh) throws UnusualConditionException {
			next = 0;
			state = s;
			label = l;
			rank = r;
			boolean debug = false;

			if (!bsFilteredIterTable.goc(state).goc(label).containsKey(rank)) {
				bsIndexCreateCount++;
				bsFilteredIterTable.get(state).get(label).put(rank, new BSLazyIterator(trans, label, rank, gram, s, mh));
//				bsFilteredIterTable.get(state).get(label).put(rank, new BSInvertLazyIterator(trans, label, rank, gram, s, mh));

			}
			else {
				bsIndexReuseCount++;
				if (debug )Debug.debug(debug, "Reuse of "+s+", "+label+", "+rank);
			}
			if (!bsFilteredResultTable.goc(state).goc(label).containsKey(rank))
				bsFilteredResultTable.get(state).get(label).put(rank, new Vector<GrammarRule>());
			results = bsFilteredResultTable.get(state).get(label).get(rank);
			iterator = bsFilteredIterTable.get(state).get(label).get(rank);					
		}
		
		public boolean hasNext() {
			boolean ret;
			if (cap > 0 && next >= cap)
				ret = false;
			else
				ret = (results.size() > next || (iterator != null && iterator.hasNext()));
			// kill iterator if it is still around
			if (!ret && iterator != null) {
				iterator = null;
				if (label == null)
					bsUnfilteredIterTable.put(state, null);
				else
					bsFilteredIterTable.get(state).get(label).put(rank, null);
			}
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
		// transducer rule iterator
		private PIterator<TreeTransducerRule> aiter;
		// grammar, which makes iters
		private Grammar bgram;
		// filtering pair symbol, which contains gram state b and filter state f
		private FilteringPairSymbol fs;
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
		// transducer rules filtered by lhs label and rank 
		public BSLazyIterator(OTFTreeTransducerRuleSet trans, Symbol label, int rank, Grammar gram, FilteringPairSymbol s, int mh) throws UnusualConditionException {
			boolean debug = false;
			Date preIT = new Date();
			bsLazyCreateCount++;
			initialize();
			maxheap = mh;
			fs = s;
			Symbol a = s.getLeft();
			Symbol b = s.getRight();
			FilteringPairSymbol.FILTER f = s.getFilter();
			if (debug) Debug.debug(debug, "Looking for matches for "+s+": between "+a+" and "+b+" with filter "+f);
			aiter = new WrappedPIterator<TreeTransducerRule>(trans.getBackwardRules(a, label, rank).iterator());
			bgram = gram;
//			if (!aiter.hasNext())
//				return;
			// throw in the input-epsilon iterator if relevant and possible
			if (label.equals(Symbol.getEpsilon())) {
				EpsilonGrammarLazyIterator egit = new EpsilonGrammarLazyIterator(s, gram, mh);
				if (egit.hasNext())
					q.add(egit, -egit.peek().getWeight());
			}
			while (aiter.hasNext() && q.isEmpty()) {
				TreeTransducerRule next = aiter.next();
				if (debug) Debug.debug(debug, "trying to seed with "+next+" and "+b);
				Date preBBI = new Date();
				BSBaseLazyIterator l = new BSBaseLazyIterator(next, fs, bgram, maxheap, true);
				Date postBBI = new Date();
//				Debug.dbtime(1, 1, preBBI, postBBI, "Make BBI out of "+next+" and "+b);	
//				buildBBITime += postBBI.getTime()-preBBI.getTime();
				if (!l.hasNext())
					continue;
				double cost = -l.peek().getWeight();
				if (!q.add(l, cost))
					throw new UnusualConditionException("Couldn't add new iterator for "+s+" with cost "+cost);
			}
			Date postIT = new Date();
			bsItTime += postIT.getTime()-preIT.getTime();
			// do first step
			Date preFillNext = new Date();
			fillNext();
			Date postFillNext = new Date();
			fillNextTime += postFillNext.getTime()-preFillNext.getTime();
			fillNextCalls++;
			if (debug) Debug.debug(debug, "Initial list of rules for "+a+" and "+b+" with filter "+f+" is "+next);
		}
		// unfiltered transducer rules 
		public BSLazyIterator(OTFTreeTransducerRuleSet trans, Grammar gram, FilteringPairSymbol s, int mh) throws UnusualConditionException {
			boolean debug = false;
			Date preIT = new Date();

			initialize();
			bsLazyCreateCount++;

			maxheap = mh;
			fs = s;
			Symbol a = s.getLeft();
			Symbol b = s.getRight();
			FilteringPairSymbol.FILTER f = s.getFilter();
			if (debug) Debug.debug(debug, "Looking for matches for "+s+": between "+a+" and "+b+" with filter "+f);
			aiter = new WrappedPIterator<TreeTransducerRule>(trans.getBackwardRules(a).iterator());
			bgram = gram;
			EpsilonGrammarLazyIterator egit = new EpsilonGrammarLazyIterator(s, gram, mh);
			if (egit.hasNext())
				q.add(egit, -egit.peek().getWeight());
//			if (!aiter.hasNext())
//				return;
			while (aiter.hasNext() && q.isEmpty()) {
				TreeTransducerRule next = aiter.next();
				if (debug) Debug.debug(debug, "trying to seed with "+next+" and "+b);
				Date preBBI = new Date();
				BSBaseLazyIterator l = new BSBaseLazyIterator(next, fs, bgram, maxheap, true);
				Date postBBI = new Date();
//				Debug.dbtime(1, 1, preBBI, postBBI, "Make BBI out of "+next+" and "+b);	
//				buildBBITime += postBBI.getTime()-preBBI.getTime();

				if (!l.hasNext())
					continue;
				double cost = -l.peek().getWeight();
				if (!q.add(l, cost))
					throw new UnusualConditionException("Couldn't add new iterator for "+s+" with cost "+cost);
			}
			Date postIT = new Date();
			bsItTime += postIT.getTime()-preIT.getTime();
			// do first step
			Date preFillNext = new Date();
			fillNext();
			Date postFillNext = new Date();
			fillNextTime += postFillNext.getTime()-preFillNext.getTime();
			fillNextCalls++;
			if (debug) Debug.debug(debug, "Initial list of rules for "+a+" and "+b+" with filter "+f+" is "+next);
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
				Date preGetItem = new Date();

				PIterator<GrammarRule> current = q.removeFirst();
				GrammarRule g  = current.next();
				next.add(g, -g.getWeight());
				if (debug) Debug.debug(debug,  "Next rule is "+g);
				if (current.hasNext()) {
					double nextcost = -current.peek().getWeight();
					if (!q.add(current, nextcost))
						throw new UnusualConditionException("Couldn't add next step iterator with cost "+nextcost);
				}
				Date postGetItem = new Date();
				getItemTime+= postGetItem.getTime()-preGetItem.getTime();
				// add to wait queue until wait queue is not empty or we run out of lists
				while (w.isEmpty() && aiter.hasNext()) {
					TreeTransducerRule next = aiter.next();
					if (debug) Debug.debug(debug, "trying to seed with "+next+" and "+fs.getRight());
					Date preBBI = new Date();
					BSBaseLazyIterator l2 = new BSBaseLazyIterator(next, fs, bgram, maxheap, true);
					Date postBBI = new Date();
		//			Debug.dbtime(1, 1, preBBI, postBBI, "Make BBI out of "+next+" and "+fs);	
					buildBBITime+= postBBI.getTime()-preBBI.getTime();
					if (!l2.hasNext()){
						continue;
					}
					double waitcost = -l2.peek().getWeight();
					if (!w.add(l2, waitcost))
						throw new UnusualConditionException("Couldn't add new waiting iterator with cost "+waitcost);
				}
				// migrate lead from wait queue over to main queue
				Date preMigrate = new Date();
				if (!w.isEmpty()) {
					double waitcost = w.getPriority();
					if (q.isEmpty() || waitcost > q.getPriority()) {
						if (debug) Debug.debug(debug, "Moving list from wait to queue");
						if (!q.add(w.removeFirst(), waitcost))
							throw new UnusualConditionException("Couldn't migrate waiting iterator to main queue");
					}
				}
				Date postMigrate = new Date();
				migrateTime += postMigrate.getTime()-preMigrate.getTime();
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
			boolean debug = false;
			if (next.isEmpty())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			GrammarRule ret = next.removeFirst();
			try {
				Date preFillNext = new Date();
				fillNext();
				Date postFillNext = new Date();
				fillNextTime += postFillNext.getTime()-preFillNext.getTime();
				fillNextCalls++;
			}
			catch (UnusualConditionException e) {
				throw new NoSuchElementException("Masking UnusualConditionException: "+e.getMessage());
			}
			if (debug) Debug.debug(debug, "Returning "+ret+"; next is "+next);
			return ret;
		}
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
		}

	}

	// inverted implementation of pervasive laziness -- checks with grammar on right first
	private class BSInvertLazyIterator implements PIterator<GrammarRule> {
		// transducer, which provides iterators to match off
		private OTFTreeTransducerRuleSet atrans;
		// transducer rule iterator, matched to a particular initial rhs
		private PIterator<TreeTransducerRule> aiter;
		// grammar iterator, to dictate what we match with
		private PIterator<GrammarRule> biter;
		// list of unique label/ranks that have come out of biter
		private HashMap<Symbol, HashSet<Integer>> seenLabels;
		// grammar, for forming iterators
		private Grammar bgram;
		// filtered lhs label and rank, if this is desired
		private Symbol lhsLabel;
		private int lhsRank;
		// filtering pair symbol, which contains gram state b and filter state f
		private FilteringPairSymbol fs;
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
			seenLabels = new HashMap<Symbol, HashSet<Integer>>();
		}
		// transducer rules filtered by lhs label and rank 
		public BSInvertLazyIterator(OTFTreeTransducerRuleSet trans, Symbol label, int rank, Grammar gram, FilteringPairSymbol s, int mh) throws UnusualConditionException {
			boolean debug = false;
			Date preIT = new Date();

			bsLazyCreateCount++;

			initialize();
			maxheap = mh;
			fs = s;
			Symbol a = s.getLeft();
			Symbol b = s.getRight();
			FilteringPairSymbol.FILTER f = s.getFilter();
			lhsLabel = label;
			lhsRank = rank;
			if (debug) Debug.debug(debug, "Looking for matches for "+s+": between "+a+" and "+b+" with label/rank "+label+"/"+rank+" and filter "+f);
			atrans = trans;
			biter = gram.getBSIter(b);
			bgram = gram;
			seenLabels = new HashMap<Symbol, HashSet<Integer>>();
			// add out eps as a separate iterator
			Iterator<TreeTransducerRule> oeit = trans.getBackwardRulesByRHS(a,  label, rank, Symbol.getEpsilon(), 1).iterator();
			if (oeit.hasNext()) {
				if (debug) Debug.debug(debug, "Adding out-eps iterator");
				OutEpsGrammarLazyIterator oegli = new OutEpsGrammarLazyIterator(fs, oeit, maxheap);
				if (oegli.hasNext()) {
					double cost = -oegli.peek().getWeight();
					if (!q.add(oegli, cost))
						throw new UnusualConditionException("Couldn't add new o-e iterator for "+s+" with cost "+cost);
				}
			}
			if (!biter.hasNext()) {
				Date postIT = new Date();
				bsItTime += postIT.getTime()-preIT.getTime();
				return;
			}
			do {
				if (debug) Debug.debug(debug, "trying to seed with something that will match "+biter.peek());
				GrammarRule g = biter.next();
				
				Symbol rhslabel = g.getLabel();
				if (rhslabel == null)
					rhslabel = Symbol.getEpsilon();
				int rhsrank = g.getChildren().size();
				if (seenLabels.containsKey(rhslabel) && seenLabels.get(rhslabel).contains(rhsrank)) {
					if (debug )Debug.debug(debug, "Already seen "+rhslabel+":"+rhsrank+" as intermediate, so continuing");
					continue;
				}
				if (!seenLabels.containsKey(rhslabel))
					seenLabels.put(rhslabel, new HashSet<Integer>());
				seenLabels.get(rhslabel).add(rhsrank);
				aiter = new WrappedPIterator<TreeTransducerRule>(atrans.getBackwardRulesByRHS(a, label, rank, rhslabel, rhsrank).iterator());
				if (!aiter.hasNext()) {
					if (debug) Debug.debug(debug, "No transducer rule matches "+a+":"+label+":"+rank+":"+rhslabel+":"+rhsrank);
					continue;
				}
				// get the first in
				while (aiter.hasNext() && q.isEmpty()) {
					TreeTransducerRule next = aiter.next();
					if (debug) Debug.debug(debug, "trying to seed with "+next+" and "+b);
					Date preBBI = new Date();
					// NOTE: non-invert passes true below!
					BSBaseLazyIterator l = new BSBaseLazyIterator(next, fs, bgram, maxheap, false);
					Date postBBI = new Date();
		//			buildBBITime += postBBI.getTime()-preBBI.getTime();
		//			Debug.dbtime(1, 1, preBBI, postBBI, "Make BBI out of "+next+" and "+b);	

					
					if (!l.hasNext())
						continue;
					double cost = -l.peek().getWeight();
					if (!q.add(l, cost))
						throw new UnusualConditionException("Couldn't add new iterator for "+s+" with cost "+cost);
				}
			} while (biter.hasNext() && q.isEmpty());
			// throw in the input-epsilon iterator if relevant and possible
			if (label.equals(Symbol.getEpsilon())) {
				EpsilonGrammarLazyIterator egit = new EpsilonGrammarLazyIterator(s, gram, mh);
				if (egit.hasNext())
					q.add(egit, -egit.peek().getWeight());
			}
			Date postIT = new Date();
			bsItTime += postIT.getTime()-preIT.getTime();
			// do first step
			Date preFillNext = new Date();
			fillNext();
			Date postFillNext = new Date();
			fillNextTime += postFillNext.getTime()-preFillNext.getTime();
			fillNextCalls++;
			if (debug) Debug.debug(debug, "Initial list of rules for "+a+" and "+b+" with filter "+f+" is "+next);
		}
		// unfiltered transducer rules 
		public BSInvertLazyIterator(OTFTreeTransducerRuleSet trans, Grammar gram, FilteringPairSymbol s, int mh) throws UnusualConditionException {
			boolean debug = false;
			Date preIT = new Date();

			bsLazyCreateCount++;
			initialize();
			maxheap = mh;
			fs = s;
			Symbol a = s.getLeft();
			Symbol b = s.getRight();
			FilteringPairSymbol.FILTER f = s.getFilter();
			lhsLabel = null;
			lhsRank = -1;
			if (debug) Debug.debug(debug, "Looking for matches for "+s+": between "+a+" and "+b+" with filter "+f);
			atrans = trans;
			biter = gram.getBSIter(b);
			bgram = gram;
			// add out eps as a separate iterator
			Iterator<TreeTransducerRule> oeit = trans.getBackwardRulesByRHS(a, Symbol.getEpsilon(), 1).iterator();
			if (oeit.hasNext()) {
				if (debug) Debug.debug(debug, "Adding out-eps iterator");
				OutEpsGrammarLazyIterator oegli = new OutEpsGrammarLazyIterator(fs, oeit, maxheap);
				if (oegli.hasNext()) {
					double cost = -oegli.peek().getWeight();
					if (!q.add(oegli, cost))
						throw new UnusualConditionException("Couldn't add new o-e iterator for "+s+" with cost "+cost);
				}
			}
			if (!biter.hasNext()) {
				Date postIT = new Date();
				bsItTime += postIT.getTime()-preIT.getTime();
				return;
			}
			do {
				if (debug) Debug.debug(debug, "trying to seed with something that will match "+biter.peek());
				GrammarRule g = biter.next();
				Symbol rhslabel = g.getLabel();
				if (rhslabel == null)
					rhslabel = Symbol.getEpsilon();
				int rhsrank = g.getChildren().size();
				if (seenLabels.containsKey(rhslabel) && seenLabels.get(rhslabel).contains(rhsrank)) {
					if (debug )Debug.debug(debug, "Already seen "+rhslabel+":"+rhsrank+" as intermediate, so continuing");
					continue;
				}
				if (!seenLabels.containsKey(rhslabel))
					seenLabels.put(rhslabel, new HashSet<Integer>());
				seenLabels.get(rhslabel).add(rhsrank);
				aiter = new WrappedPIterator<TreeTransducerRule>(trans.getBackwardRulesByRHS(a, rhslabel, rhsrank).iterator());
				if (!aiter.hasNext()) {
					if (debug) Debug.debug(debug, "No transducer rule matches "+a+":"+rhslabel+":"+rhsrank);
					continue;
				}
				// get the first in
				while (aiter.hasNext() && q.isEmpty()) {
					TreeTransducerRule next = aiter.next();
					if (debug) Debug.debug(debug, "trying to seed with "+next+" and "+b);
					Date preBBI = new Date();
					// NOTE: non-invert passes true below!
					BSBaseLazyIterator l = new BSBaseLazyIterator(next, fs, bgram, maxheap, false);
					Date postBBI = new Date();
//					buildBBITime += postBBI.getTime()-preBBI.getTime();
//					Debug.dbtime(1, 1, preBBI, postBBI, "Make BBI out of "+next+" and "+b);	
					if (!l.hasNext())
						continue;
					double cost = -l.peek().getWeight();
					if (!q.add(l, cost))
						throw new UnusualConditionException("Couldn't add new iterator for "+s+" with cost "+cost);
				}
			} while (biter.hasNext() && q.isEmpty());
			// throw in the input-epsilon iterator if possible
			EpsilonGrammarLazyIterator egit = new EpsilonGrammarLazyIterator(s, gram, mh);
			if (egit.hasNext())
				q.add(egit, -egit.peek().getWeight());
			Date postIT = new Date();
			bsItTime += postIT.getTime()-preIT.getTime();
			// do first step
			Date preFillNext = new Date();
			fillNext();
			Date postFillNext = new Date();
			fillNextTime += postFillNext.getTime()-preFillNext.getTime();
			fillNextCalls++;
			if (debug) Debug.debug(debug, "Initial list of rules for "+a+" and "+b+" with filter "+f+" is "+next);
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
				Date preGetItem = new Date();
				PIterator<GrammarRule> current = q.removeFirst();
				GrammarRule g  = current.next();
				next.add(g, -g.getWeight());
				if (debug) Debug.debug(debug,  "Next rule is "+g);
				if (current.hasNext()) {
					double nextcost = -current.peek().getWeight();
					if (!q.add(current, nextcost))
						throw new UnusualConditionException("Couldn't add next step iterator with cost "+nextcost);
				}
				Date postGetItem = new Date();
				getItemTime+= postGetItem.getTime()-preGetItem.getTime();
				// add to wait queue until wait queue is not empty or we run out of lists
				while (w.isEmpty() && (aiter.hasNext() || biter.hasNext())) {
					// use up aiter if there's stuff left
					
					BSBaseLazyIterator l2 = null;
					if (aiter.hasNext()) {
						TreeTransducerRule next = aiter.next();
						if (debug) Debug.debug(debug, "trying to seed with "+next+" and "+fs.getRight());
						Date preBBI = new Date();
						// NOTE: non-invert passes true below!
						l2 = new BSBaseLazyIterator(next, fs, bgram, maxheap, false);
						Date postBBI = new Date();
						buildBBITime += postBBI.getTime()-preBBI.getTime();
	//					Debug.dbtime(1, 1, preBBI, postBBI, "Make BBI out of "+next+" and "+fs.getRight());	
						if (!l2.hasNext()) {							
							continue;
						}
					}
					// otherwise make a new one from biter and try again
					else {
						Date preGetGram = new Date();
						GrammarRule nextb = biter.next();
						Date postGetGram = new Date();
						getGramTime += postGetGram.getTime()-preGetGram.getTime();
						Date preLabelVer = new Date();
						Symbol rhslabel = nextb.getLabel();
						if (rhslabel == null)
							rhslabel = Symbol.getEpsilon();
						int rhsrank = nextb.getChildren().size();
						if (seenLabels.containsKey(rhslabel) && seenLabels.get(rhslabel).contains(rhsrank)) {
							if (debug )Debug.debug(debug, "Already seen "+rhslabel+":"+rhsrank+" as intermediate, so continuing");
							Date postLabelVer = new Date();
							labelVerTime += postLabelVer.getTime()-preLabelVer.getTime();
							continue;
						}
						if (!seenLabels.containsKey(rhslabel))
							seenLabels.put(rhslabel, new HashSet<Integer>());
						seenLabels.get(rhslabel).add(rhsrank);
						Date postLabelVer = new Date();
						labelVerTime += postLabelVer.getTime()-preLabelVer.getTime();
						if(debug) Debug.debug(debug, "Making new trans iterator based on intermediate "+rhslabel+":"+rhsrank);
						Date preTransIt = new Date();
						if (lhsRank >= 0) 
							aiter = new WrappedPIterator<TreeTransducerRule>(atrans.getBackwardRulesByRHS(fs.getLeft(), lhsLabel, lhsRank, rhslabel, rhsrank).iterator());
						else
							aiter = new WrappedPIterator<TreeTransducerRule>(atrans.getBackwardRulesByRHS(fs.getLeft(), rhslabel, rhsrank).iterator());
						Date postTransIt = new Date();
						transItTime += postTransIt.getTime()-preTransIt.getTime();
						continue;						
					}
					double waitcost = -l2.peek().getWeight();
					if (!w.add(l2, waitcost))
						throw new UnusualConditionException("Couldn't add new waiting iterator with cost "+waitcost);
				}
				// migrate lead from wait queue over to main queue
				Date preMigrate = new Date();
				if (!w.isEmpty()) {
					double waitcost = w.getPriority();
					if (q.isEmpty() || waitcost > q.getPriority()) {
						if (debug) Debug.debug(debug, "Moving list from wait to queue");
						if (!q.add(w.removeFirst(), waitcost))
							throw new UnusualConditionException("Couldn't migrate waiting iterator to main queue");
					}
				}
				Date postMigrate = new Date();
				migrateTime += postMigrate.getTime()-preMigrate.getTime();
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
			boolean debug = false;
			if (next.isEmpty())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			GrammarRule ret = next.removeFirst();
			try {
				Date preFillNext = new Date();
				fillNext();
				Date postFillNext = new Date();
				fillNextTime += postFillNext.getTime()-preFillNext.getTime();
				fillNextCalls++;
				}
			catch (UnusualConditionException e) {
				throw new NoSuchElementException("Masking UnusualConditionException: "+e.getMessage());
			}
			if (debug) Debug.debug(debug, "Returning "+ret+"; next is "+next);
			return ret;
		}
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
		}

	}

	
	
	
	
	// backward star base lazy iterator -- one transducer rule matching a grammar at a state
	// if transducer rule is out-epsilon and filter is okay, 
	// if allowed, build epsilon grammar rule first
	// then must match actual matches
	// epsilon grammar rules are handled by EpsilonGrammarLazyIterator
	// next pops off next member
	// general case: big rhs transducer rule combines with some possible covering from grammar
	// and state sequence plus product of rules is mapped on.
	
	// height-one (and possibly very common) case: single-level rhs transducer rule combines
	// with exactly one grammar rule
	
	private class BSBaseLazyIterator implements PIterator<GrammarRule> {
		private FixedPrioritiesPriorityQueue<GrammarRule> next;
		private TreeTransducerRule base;
		private FilteringPairSymbol sym;
		private final boolean useSpecialIterator = true;
		// stringIterator for general case
		private Iterator<Pair<Vector<Symbol>, Double>> stringIterator;
		// ruleIterator for height-one case
		private Iterator<GrammarRule> ruleIterator;
		private double lastcost = 0;
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;
		public BSBaseLazyIterator(TreeTransducerRule r, FilteringPairSymbol fs, 
				 Grammar gram, int maxheap, boolean doEps) throws UnusualConditionException {
			bsBaseLazyCreateCount++;
			Symbol b = fs.getRight();
			boolean debug = false;
//			boolean tempdbg = true;
//			if (tempdbg) Debug.debug(tempdbg, r+":"+fs+":"+gram.hashCode());
			if (debug) Debug.debug(debug, "Looking for matches between "+r+" and grammar starting with "+b);
			base = r;
			sym = fs;
			next = new FixedPrioritiesPriorityQueue<GrammarRule> ();
			// epsilon rules
			if (doEps && r.isOutEps()) {
				if (debug) Debug.debug(debug, "Finding out-eps rules for "+r);
				GrammarRule epsrule = formAndAddRules(base, sym);
				if (epsrule != null) {
					if (debug) Debug.debug(debug, "Found eps rule "+epsrule);
					next.add(epsrule, -epsrule.getWeight());
				}
			}
			if (useSpecialIterator && !r.isExtendedRHS()) {
				stringIterator = null;
				if (r.isOutEps()) {
					if (fs.getFilter().equals(FilteringPairSymbol.FILTER.NOEPS))
						ruleIterator = gram.getBSIter(b, Symbol.getEpsilon(), 1);
					else
						ruleIterator = empty.iterator();
				}
				else {
					ruleIterator = gram.getBSIter(b, r.getRHS().getLabel(), r.getRHS().getNumChildren());
				}
				while (
						(maxheap == 0 ? true : next.size() < maxheap) && 
						ruleIterator.hasNext()
				) {
					GrammarRule rule = ruleIterator.next();
					GrammarRule g = formAndAddRules(base, rule, sym);
					next.add(g, -g.getWeight());
					if (debug) Debug.debug(debug,  "Built rule for "+base+"; "+g);
				}
			}
			else {
				ruleIterator = null;

				// out eps not in NOEP can't build
				if (r.isOutEps() && !fs.getFilter().equals(FilteringPairSymbol.FILTER.NOEPS))
					stringIterator = stringempty.iterator();
				else {
					// if this is a known bad construction, constructor will yield null
					RGOTFGrammar rggram = new RGOTFGrammar(r, gram, b, r.getSemiring(), maxheap);
					if (rggram == null)
						stringIterator = stringempty.iterator();
					else
						stringIterator = rggram.stringIterator(rggram.getStartState(), true, true);
				}			
				while (
						(maxheap == 0 ? true : next.size() < maxheap) && 
						stringIterator.hasNext()
				) {
					Pair<Vector<Symbol>, Double> string = stringIterator.next();
					GrammarRule g = formAndAddRules(base, string);
					next.add(g, -g.getWeight());
					if (debug) Debug.debug(debug,  "Built rule for "+base+"; "+g);
				}
			}
			if (!next.isEmpty()) {
				if (REPORTMONO && lastcost < next.getPriority())
					Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
				//					throw new UnusualConditionException("Monotonicity of lazy iterator violated: got "+next+" after "+lastcost);
				lastcost = next.getPriority();
			}
			if (debug) Debug.debug(debug, "Done initializing matches between "+r+" and grammar starting with "+b+"; initial list is "+next);

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
			if (useSpecialIterator && ruleIterator != null) {
				if (ruleIterator.hasNext()) {
					GrammarRule rule = ruleIterator.next();		
					try {
						GrammarRule g = formAndAddRules(base, rule, sym);
						if (debug) Debug.debug(debug, "Built next rule for "+base+" with "+rule+"; "+g);
						next.add(g, -g.getWeight());
					}
					catch (UnusualConditionException e) {
						throw new NoSuchElementException("Masking UnusualConditionException: "+e.getMessage());
					}
					if (REPORTMONO && lastcost < next.getPriority())
						Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
					lastcost = next.getPriority();
				}
			}
			else {
				if (stringIterator.hasNext()) {
					Pair<Vector<Symbol>, Double> string = stringIterator.next();		
					try {
						GrammarRule g = formAndAddRules(base, string);
						if (debug) Debug.debug(debug, "Built next rule for "+base+" with "+string.l()+"; "+g);
						next.add(g, -g.getWeight());
					}
					catch (UnusualConditionException e) {
						throw new NoSuchElementException("Masking UnusualConditionException: "+e.getMessage());
					}
					if (REPORTMONO && lastcost < next.getPriority())
						Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
					lastcost = next.getPriority();
				}
			}
			

			return ret;
		}
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
		}
	}

	
	// out eps lazy iterator -- output epsilon transducer rules passing a state through
	// this was handled/can be handled by base lazy iterator, but for invert purpsoes, it's best
	// to do it separately.
	private class OutEpsGrammarLazyIterator implements PIterator<GrammarRule> {
		private Iterator<TreeTransducerRule> iterator;
		private FilteringPairSymbol base;
		private FixedPrioritiesPriorityQueue<GrammarRule> next;
		private double lastcost = 0;
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;
		public OutEpsGrammarLazyIterator(FilteringPairSymbol s, 
				Iterator<TreeTransducerRule> it, int maxheap) throws UnusualConditionException {
			boolean debug = false;
			iterator = it;
			base = s;
			next = new FixedPrioritiesPriorityQueue<GrammarRule> ();
			while (
					(maxheap == 0 ? true : next.size() < maxheap) && 
					iterator.hasNext()
					) {
				TreeTransducerRule t = iterator.next();
				if (!t.isOutEps())
					throw new UnusualConditionException("saw non-outeps rule "+t+" in outeps iterator");
				if (debug) Debug.debug(debug, "Making rule out of "+t+" and "+base);
				GrammarRule epsrule = formAndAddRules(t, base);
				if (epsrule != null)
					next.add(epsrule, -epsrule.getWeight());
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
				TreeTransducerRule t = iterator.next();					
				try {
					GrammarRule g = formAndAddRules(t, base);
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
			throw new UnsupportedOperationException("Didn't bother with remove for OutEpsGrammarLazyIterator");
		}
	}
	
	
	
	// match a symbol with epsilon grammar rules
	// BS and FS callings vary in the placement of filters and the way gram is queried but otherwise
	// should be the same
	private class EpsilonGrammarLazyIterator implements PIterator<GrammarRule> {
		private Iterator<GrammarRule> iterator;
		private FilteringPairSymbol base;
		private FixedPrioritiesPriorityQueue<GrammarRule> next;
		private double lastcost = 0;
		
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;
		public EpsilonGrammarLazyIterator(FilteringPairSymbol s, 
				Grammar gram, int maxheap) throws UnusualConditionException {
			boolean debug = false;
			base = s;
			FilteringPairSymbol.FILTER filter = s.getFilter();
			// not allowed to get these rules if we're lefteps
			if (filter.equals(FilteringPairSymbol.FILTER.LEFTEPS))
				return;
			if (debug) Debug.debug(debug, "Looking for epsilon rules from "+s+" which is really "+s.getRight());
			if (isTD)
				iterator = gram.getBSIter(s.getRight(), Symbol.getEpsilon(), 1);
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
	
	
	// allows memoization of FS members and multiple simultaneous access of the same list
	
	// lex only
	private Vector<GrammarRule> fsBaseResultTable;
	private FSLazyIterator fsBaseIterTable;
	
	// state->pos
	private PMap<Symbol, PMap<Integer,Vector<GrammarRule>>> fsStatePosResultTable;
	private PMap<Symbol, PMap<Integer, FSLazyIterator>> fsStatePosIterTable;
	
	// label->rank
	private PMap<Symbol, PMap<Integer,Vector<GrammarRule>>> fsLabelRankResultTable;
	private PMap<Symbol, PMap<Integer, FSLazyIterator>> fsLabelRankIterTable;
	
	// state->pos->label->rank
	private PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, Vector<GrammarRule>>>>> fsStatePosLabelRankResultTable;
	private PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, FSLazyIterator>>>> fsStatePosLabelRankIterTable;
	
	private class FSIndexedIterator implements PIterator<GrammarRule> {
		private int next;
		private FilteringPairSymbol state;
		private int pos;
		private Symbol label;
		private int rank;
		
		private Vector<GrammarRule> results;
		private FSLazyIterator iterator;
		public FSIndexedIterator(OTFTreeTransducerRuleSet trans, Grammar gram, int mh) throws UnusualConditionException {
			next = 0;
			state = null;
			label = null;
			pos = rank = -1;
			boolean debug = false;
			if (fsBaseIterTable == null) {
				fsBaseIterTable = new FSLazyIterator(trans, gram, mh);
				if (debug )Debug.debug(debug, "No Reuse of base");

			}
			else {
				if (debug )Debug.debug(debug, "Reuse of base");
			}
			if (fsBaseResultTable == null)
			fsBaseResultTable = new Vector<GrammarRule>();
			
			results = fsBaseResultTable;
			iterator = fsBaseIterTable;
		}
		// for state/pos
		public FSIndexedIterator(OTFTreeTransducerRuleSet trans, Grammar gram, FilteringPairSymbol s, int p, int mh) throws UnusualConditionException {
			next = 0;
			state = s;
			pos = p;
			label = null;
			rank = -1;
			boolean debug = false;

			if (!fsStatePosIterTable.goc(state).containsKey(pos)){
				fsStatePosIterTable.get(state).put(pos, new FSLazyIterator(trans, gram, state, pos, mh));
				if (debug )Debug.debug(debug, "No Reuse of "+state+", "+pos);

			}
			else {
				if (debug )Debug.debug(debug, "Reuse of "+state+", "+pos);
			}
			if (!fsStatePosResultTable.goc(state).containsKey(pos))
				fsStatePosResultTable.get(state).put(pos, new Vector<GrammarRule>());
			results = fsStatePosResultTable.get(state).get(pos);
			iterator = fsStatePosIterTable.get(state).get(pos);					
		}
		// for label/rank/
		public FSIndexedIterator(OTFTreeTransducerRuleSet trans, Grammar gram, Symbol l, int r, int mh) throws UnusualConditionException {
			next = 0;
			state = null;
			pos = -1;
			label = l;
			rank = r;
			boolean debug = false;

			if (!fsLabelRankIterTable.goc(label).containsKey(rank)){
				fsLabelRankIterTable.get(label).put(rank, new FSLazyIterator(trans, gram, label, rank, mh));
				if (debug )Debug.debug(debug, "No Reuse of "+label+", "+rank);

			}
			else {
				if (debug )Debug.debug(debug, "Reuse of "+label+", "+rank);
			}
			if (!fsLabelRankResultTable.goc(label).containsKey(rank))
				fsLabelRankResultTable.get(label).put(rank, new Vector<GrammarRule>());
			results = fsLabelRankResultTable.get(label).get(rank);
			iterator = fsLabelRankIterTable.get(label).get(rank);					
		}
		// for label/rank/state/pos
		public FSIndexedIterator(OTFTreeTransducerRuleSet trans, Grammar gram, FilteringPairSymbol s, int p,  Symbol l, int r, int mh) throws UnusualConditionException {
			next = 0;
			state = s;
			pos = p;
			label = l;
			rank = r;
			boolean debug = false;

			if (!fsStatePosLabelRankIterTable.goc(state).goc(pos).goc(label).containsKey(rank)) {
				fsStatePosLabelRankIterTable.get(state).get(pos).get(label).put(rank, new FSLazyIterator(trans, gram, state, pos, label, rank, mh));
				if (debug )Debug.debug(debug, "No Reuse of "+state+", "+pos+", "+label+", "+rank);

			}
			else {
				if (debug )Debug.debug(debug, "Reuse of "+state+", "+pos+", "+label+", "+rank);
			}
			if (!fsStatePosLabelRankResultTable.goc(state).goc(pos).goc(label).containsKey(rank))
				fsStatePosLabelRankResultTable.get(state).get(pos).get(label).put(rank, new Vector<GrammarRule>());
			results = fsStatePosLabelRankResultTable.get(state).get(pos).get(label).get(rank);
			iterator = fsStatePosLabelRankIterTable.get(state).get(pos).get(label).get(rank);					
		}
		
		
		
		public boolean hasNext() {
			boolean ret;
			if (cap > 0 && next >= cap)
				ret = false;
			else
				ret = (results.size() > next || (iterator != null && iterator.hasNext()));
			// kill iterator if it is still around
			if (!ret && iterator != null) {
				iterator = null;
				if (state == null && label == null && rank == -1 && pos == -1)
					fsBaseIterTable = null;
				else if (state != null && label == null && rank == -1 && pos != -1)
					fsStatePosIterTable.get(state).put(pos, null);
				else if (state == null && label != null && rank != -1 && pos == -1)
					fsLabelRankIterTable.get(label).put(rank, null);
				else
					fsStatePosLabelRankIterTable.get(state).get(pos).get(label).put(rank, null);
			}
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
	// implementation of forward-star pervasive laziness
	// 
	private class FSLazyIterator implements PIterator<GrammarRule> {
		// transducer rule iterator
		private PIterator<TreeTransducerRule> aiter;
		// grammar, which makes iters
		private Grammar bgram;
		// filtering pair symbol, which contains gram state b and filter state f
		// sometimes null if we're behaving lexically
		private FilteringPairSymbol fs;
		
		// lhs pos
		private int pos;
		
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
		// lexical, unfiltered
		// strictly gets 0-rank lexical lhs rules
		public FSLazyIterator(OTFTreeTransducerRuleSet trans, Grammar gram, int mh) throws UnusualConditionException {
			boolean debug = false;
			initialize();
			pos = -1;
			fs = null;

			maxheap = mh;
			
			aiter = new WrappedPIterator<TreeTransducerRule>(trans.getLexRules().iterator());
			bgram = gram;
			if (!aiter.hasNext())
				return;
			while (aiter.hasNext() && q.isEmpty()) {
				if (debug) Debug.debug(debug, "trying to seed with "+aiter.peek());
				if (debug) Debug.debug(debug, "Creating new lexical unfiltered base lazy iterator for "+trans.hashCode()+" and "+bgram.hashCode());
				FSBaseLazyIterator l = new FSBaseLazyIterator(aiter.next(), null, null, bgram, maxheap, true);
				if (!l.hasNext())
					continue;
				double cost = -l.peek().getWeight();
				if (!q.add(l, cost))
					throw new UnusualConditionException("Couldn't add new lexical iterator with cost "+cost);
			}
			// transducer lex rules won't have epsilon, so no special handling
			
			fillNext();
			if (debug) Debug.debug(debug, "Initial list of unfiltered lexical rules is "+next);


		}
		// lexical, filtered
		// keyed on lhs label and rank but no state -- anything goes
		public FSLazyIterator(OTFTreeTransducerRuleSet trans, Grammar gram, Symbol label, int rank, int mh) throws UnusualConditionException {
			boolean debug = false;
			initialize();
			pos = -1;
			fs = null;

			maxheap = mh;
			
			aiter = new WrappedPIterator<TreeTransducerRule>(trans.getLexRules(label, rank).iterator());
			bgram = gram;
			if (!aiter.hasNext())
				return;
			while (aiter.hasNext() && q.isEmpty()) {
				if (debug) Debug.debug(debug, "trying to seed with "+aiter.peek());
				if (debug) Debug.debug(debug, "Creating new lexical base lazy iterator filtered by "+label+" for "+trans.hashCode()+" and "+bgram.hashCode());
				FSBaseLazyIterator l = new FSBaseLazyIterator(aiter.next(), null, null, bgram, maxheap, true);
				if (!l.hasNext())
					continue;
				double cost = -l.peek().getWeight();
				if (!q.add(l, cost))
					throw new UnusualConditionException("Couldn't add new lexical iterator with cost "+cost);
			}
			
			// TODO: epsilon?
			// throw in the input-epsilon iterator if possible
//			EpsilonGrammarLazyIterator egit = new EpsilonGrammarLazyIterator(s, gram, mh);
//			if (egit.hasNext())
//				q.add(egit, -egit.peek().getWeight());
			// do first step
			fillNext();
			if (debug) Debug.debug(debug, "Initial list of filtered lexical rules is "+next);

		}
		// nonlexical, unfiltered
		// keyed on a particular state and lhs position but no symbol
		// REAL filtering
		public FSLazyIterator(OTFTreeTransducerRuleSet trans, Grammar gram,  FilteringPairSymbol s, int p, int mh) throws UnusualConditionException {
			boolean debug = false;
			initialize();
			pos = p;
			fs = s;
			FilteringPairSymbol.FILTER f = s.getFilter();
			maxheap = mh;
			if (debug) Debug.debug(debug, "Looking for matches rooted at "+s+", in position "+p+" with filter "+f);
			
			// if not REAL, throw in the un-REALing rule
			if (ALLOWINEPS && f != FilteringPairSymbol.FILTER.REAL) {
				WrappedPIterator<GrammarRule> realit = new WrappedPIterator<GrammarRule>(formRealFilterRule(s));
				q.add(realit, -realit.peek().getWeight());
			}
			// throw in the input-epsilon iterator if possible (pos must be 0)
			if (p == 0 && !f.equals(FilteringPairSymbol.FILTER.REAL)) {
				EpsilonGrammarLazyIterator egit = new EpsilonGrammarLazyIterator(s, gram, mh);
				if (egit.hasNext())
					q.add(egit, -egit.peek().getWeight());
			}
			aiter = new WrappedPIterator<TreeTransducerRule>(trans.getForwardRules(fs.getLeft(), pos).iterator());
			bgram = gram;
//			if (!aiter.hasNext())
//				return;
			while (aiter.hasNext() && q.isEmpty()) {
				if (debug) Debug.debug(debug, "trying to seed with "+aiter.peek());
				TreeTransducerRule arule = aiter.next();
				HashSet<TransducerRightSide> trsset = arule.getTRVM().getRHS(arule.getLHS().getChild(pos));
				if (trsset.size() != 1)
					throw new UnusualConditionException("Non-single mappings for variable in "+arule+"; can only do LN with backward app");
				for (TransducerRightSide gentrt : trsset) {
					TransducerRightTree trt = (TransducerRightTree)gentrt;
					FSBaseLazyIterator l = new FSBaseLazyIterator(arule, trt, fs, bgram, maxheap, true);
					if (!l.hasNext())
						continue;
					double cost = -l.peek().getWeight();
					if (!q.add(l, cost))
						throw new UnusualConditionException("Couldn't add new lexical iterator with cost "+cost);
				}
			}
	
			// do first step
			fillNext();
			if (debug) Debug.debug(debug, "Initial list of unfiltered nonlexical rules is "+next);
		}
		
		// nonlexical, filtered
		// keyed on a particular state and lhs position as well as a lhs label and rank
		public FSLazyIterator(OTFTreeTransducerRuleSet trans, Grammar gram,  FilteringPairSymbol s, int p, Symbol label, int rank, int mh) throws UnusualConditionException {
			boolean debug = false;
			initialize();
			pos = p;
			fs = s;
			FilteringPairSymbol.FILTER f = s.getFilter();
			maxheap = mh;
			if (debug) Debug.debug(debug, "Looking for matches rooted at "+s+", in position "+p+" with filter "+f+" and label "+label+" and rank "+rank);

			// if not REAL and we're looking for eps, throw in the un-REALing rule
			if (ALLOWINEPS && f != FilteringPairSymbol.FILTER.REAL && label.equals(Symbol.getEpsilon())) {
				WrappedPIterator<GrammarRule> realit = new WrappedPIterator<GrammarRule>(formRealFilterRule(s));
				q.add(realit, -realit.peek().getWeight());
			}
			// throw in the input-epsilon iterator if possible (label must be eps, pos must be 0 and rank must be 1)
			if (label.equals(Symbol.getEpsilon()) && p == 0 && rank == 1 && !f.equals(FilteringPairSymbol.FILTER.REAL)) {
				EpsilonGrammarLazyIterator egit = new EpsilonGrammarLazyIterator(s, gram, mh);
				if (egit.hasNext())
					q.add(egit, -egit.peek().getWeight());
			}
			aiter = new WrappedPIterator<TreeTransducerRule>(trans.getForwardRules(fs.getLeft(), pos, label, rank).iterator());
			bgram = gram;
//			if (!aiter.hasNext())
//				return;
			while (aiter.hasNext() && q.isEmpty()) {
				if (debug) Debug.debug(debug, "trying to seed with "+aiter.peek());
				TreeTransducerRule arule = aiter.next();
				HashSet<TransducerRightSide> trsset = arule.getTRVM().getRHS(arule.getLHS().getChild(pos));
				if (trsset.size() != 1)
					throw new UnusualConditionException("Non-single mappings for variable in "+arule+"; can only do LN with backward app");
				for (TransducerRightSide gentrt : trsset) {
					TransducerRightTree trt = (TransducerRightTree)gentrt;
					FSBaseLazyIterator l = new FSBaseLazyIterator(arule, trt, fs, bgram, maxheap, true);
					if (!l.hasNext())
						continue;
					double cost = -l.peek().getWeight();
					if (!q.add(l, cost))
						throw new UnusualConditionException("Couldn't add new lexical iterator with cost "+cost);
				}
			}
		

			// do first step
			fillNext();
			if (debug) Debug.debug(debug, "Initial list of filtered nonlexical rules is "+next);
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
					TreeTransducerRule arule = aiter.next();
					// non-lexical case: find the node that matches the requested pos
					if (pos >= 0) {
						HashSet<TransducerRightSide> trsset = arule.getTRVM().getRHS(arule.getLHS().getChild(pos));
						if (trsset.size() != 1)
							throw new UnusualConditionException("Non-single mappings for variable in "+arule+"; can only do LN with backward app");
						for (TransducerRightSide gentrt : trsset) {
							TransducerRightTree trt = (TransducerRightTree)gentrt;
							FSBaseLazyIterator l2 = new FSBaseLazyIterator(arule, trt, fs, bgram, maxheap, true);
							if (!l2.hasNext())
								continue;
							double waitcost = -l2.peek().getWeight();
							if (!w.add(l2, waitcost))
								throw new UnusualConditionException("Couldn't addnew waiting iterator with cost "+waitcost);							
						}
					}
					// lexical case -- don't provide a node
					else {
						FSBaseLazyIterator l2 = new FSBaseLazyIterator(arule, null, null, bgram, maxheap, true);
						if (!l2.hasNext())
							continue;
						double waitcost = -l2.peek().getWeight();
						if (!w.add(l2, waitcost))
							throw new UnusualConditionException("Couldn't addnew waiting iterator with cost "+waitcost);							
			
					}

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
	
	
	// forward star base lazy iterator -- one transducer rule matching a grammar at a state
	// and position
	// if transducer rule is out-epsilon and filter is okay, 
	// if allowed, build epsilon grammar rule first
	// then must match actual matches
	// epsilon grammar rules are handled by EpsilonGrammarLazyIterator
	// next pops off next member
	// general case: big rhs transducer rule combines with some possible covering from grammar
	// and state sequence plus product of rules is mapped on.
	
	// height-one (and possibly very common) case: single-level rhs transducer rule combines
	// with exactly one grammar rule
	
	private class FSBaseLazyIterator implements PIterator<GrammarRule> {
		private FixedPrioritiesPriorityQueue<GrammarRule> next;
		private TreeTransducerRule base;
		private FilteringPairSymbol sym;
		private final boolean useSpecialIterator = true;
		// stringIterator for general case
		private Iterator<Pair<Vector<Symbol>, Double>> stringIterator;
		// ruleIterator for height-one case
		private Iterator<GrammarRule> ruleIterator;
		
		private double lastcost = 0;
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;
		
		// build grammar rules by combining r with rules from gram such that the variable at n
		// matches something with fs.right()
		public FSBaseLazyIterator(TreeTransducerRule r, TransducerRightTree n, FilteringPairSymbol fs, 
				 Grammar gram, int maxheap, boolean doEps) throws UnusualConditionException {
			Symbol b = null;
			if (fs != null)
				b = fs.getRight();
			boolean debug = false;
			if (debug) Debug.debug(debug, "Looking for matches between "+r+" and grammar starting with "+b+", rooted at "+n);
			if (debug && fs != null) Debug.debug(debug, "State is "+fs+" which is "+fs.getLeft()+", "+fs.getRight()+", "+fs.getFilter());
			base = r;
			sym = fs;
			next = new FixedPrioritiesPriorityQueue<GrammarRule> ();
			// epsilon rules -- REAL can't participate
			if (doEps && r.isOutEps() && !fs.getFilter().equals(FilteringPairSymbol.FILTER.REAL)) {
				if (debug) Debug.debug(debug, "Finding out-eps rules for "+r);
				GrammarRule epsrule = formAndAddRules(base, sym);
				if (epsrule != null)
					next.add(epsrule, -epsrule.getWeight());
			}
		
			// special case is faster than parsing rhs
			if (useSpecialIterator && !r.isExtendedRHS()) {
				stringIterator = null;
				if (r.isOutEps()) {
					if (fs.getFilter().equals(FilteringPairSymbol.FILTER.NOEPS))
						ruleIterator = gram.getFSIter(b, 0, Symbol.getEpsilon(), 1);
					else
						ruleIterator = empty.iterator();
				}
				else if (fs != null && !r.isOutEps() && ALLOWINEPS && !fs.getFilter().equals(FilteringPairSymbol.FILTER.REAL))
					ruleIterator = empty.iterator();
				// normal and not REAL can't build
				else {
					// figure out label, rank
					Symbol label = r.getRHS().getLabel();
					int rank = r.getRHS().getNumChildren();
					// figure out pos if need be (if non-lexical)
					if (b != null) {
						int pos = -1;
						for (int i = 0; i < rank; i++) {
							if (r.getRHS().getChild(i).equals(n)) {
								pos = i;
								break;
							}
						}
						ruleIterator = gram.getFSIter(b, pos, label, rank);
					}
					else {
						ruleIterator = gram.getLexFSIter(label, rank);
					}
				}
				while (
						(maxheap == 0 ? true : next.size() < maxheap) && 
						ruleIterator.hasNext()
				) {
					GrammarRule rule = ruleIterator.next();
					// manually form top state here!
					FilteringPairSymbol topState = FilteringPairSymbol.get(base.getState(), rule.getState(), FilteringPairSymbol.FILTER.NOEPS);
					GrammarRule g = formAndAddRules(base, rule, topState);
					next.add(g, -g.getWeight());
					if (debug) Debug.debug(debug,  "Built rule for "+base+" and "+rule+"; "+g);
				}
			}
			else {
				ruleIterator = null;

				// out eps not in NOEP can't build
				if (r.isOutEps() && !fs.getFilter().equals(FilteringPairSymbol.FILTER.NOEPS))
					stringIterator = stringempty.iterator();
				// normal and not REAL can't build
				else if (fs != null && !r.isOutEps() && ALLOWINEPS && !fs.getFilter().equals(FilteringPairSymbol.FILTER.REAL))
					stringIterator = stringempty.iterator();
				else {
					// if this is a known bad construction, constructor will yield null
					if (debug) Debug.debug(debug, "Building new grammar for "+r+", "+gram.hashCode()+", "+n+", "+b);
					RGOTFGrammar rggram = new RGOTFGrammar(r, gram, n, b, r.getSemiring(), maxheap);
					if (rggram == null)
						stringIterator = stringempty.iterator();
					else
						stringIterator = rggram.stringIterator(rggram.top(), false, true);
				}
				while (
						(maxheap == 0 ? true : next.size() < maxheap) && 
						stringIterator.hasNext()
				) {
					Pair<Vector<Symbol>, Double> string = stringIterator.next();
					GrammarRule g = formAndAddRules(base, string);
					next.add(g, -g.getWeight());
					if (debug) Debug.debug(debug,  "Built rule for "+base+", "+n+", "+fs+"; "+g);
				}
			}
			if (!next.isEmpty()) {
				if (REPORTMONO && lastcost < next.getPriority())
					Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
				//					throw new UnusualConditionException("Monotonicity of lazy iterator violated: got "+next+" after "+lastcost);
				lastcost = next.getPriority();
			}
			if (debug) Debug.debug(debug, "Done initializing matches between "+r+" and grammar starting with "+b+"; initial list is "+next);			
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
			// TODO: height-one case
			if (useSpecialIterator && ruleIterator != null) {
				if (ruleIterator.hasNext()) {
					GrammarRule rule = ruleIterator.next();		
					try {
						FilteringPairSymbol topState = FilteringPairSymbol.get(base.getState(), rule.getState(), FilteringPairSymbol.FILTER.NOEPS);
						GrammarRule g = formAndAddRules(base, rule, topState);
						if (debug) Debug.debug(debug, "Built next rule for "+base+" with "+rule+"; "+g);
						next.add(g, -g.getWeight());
					}
					catch (UnusualConditionException e) {
						throw new NoSuchElementException("Masking UnusualConditionException: "+e.getMessage());
					}
					if (REPORTMONO && lastcost < next.getPriority())
						Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
					lastcost = next.getPriority();
				}
			}
			else {
				if (stringIterator.hasNext()) {
					Pair<Vector<Symbol>, Double> string = stringIterator.next();		
					try {
						GrammarRule g = formAndAddRules(base, string);
						if (debug) Debug.debug(debug, "Built next rule for "+base+" with "+string.l()+"; "+g);
						next.add(g, -g.getWeight());
					}
					catch (UnusualConditionException e) {
						throw new NoSuchElementException("Masking UnusualConditionException: "+e.getMessage());
					}
					if (REPORTMONO && lastcost < next.getPriority())
						Debug.prettyDebug("Monotonicity of lazy iterator violated: got "+next.getPriority()+" after "+lastcost);
					lastcost = next.getPriority();
				}
			}


			return ret;
		}

		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
		}

	}
	
	// indexes for binary iterators
	// lex only
	private Vector<GrammarRule> fsBinaryBaseResultTable;
	private FSBinaryLazyIterator fsBinaryBaseIterTable;
	
	// by state
	private PMap<Symbol,Vector<GrammarRule>> fsBinaryStateResultTable;
	private PMap<Symbol, FSBinaryLazyIterator> fsBinaryStateIterTable;
	
	// wraps FSLazyIterator -- allows multiple access, arbitrary cap
	private class FSBinaryIndexedIterator implements PIterator<GrammarRule> {
		private int next;		
		private Vector<GrammarRule> results;
		private FSBinaryLazyIterator iterator;
		FilteringPairSymbol keyst;
		// for state
		public FSBinaryIndexedIterator(BinaryTreeTransducer trans, Grammar gram, FilteringPairSymbol state, int mh) throws UnusualConditionException {
			next = 0;


			boolean debug = false;
			keyst = state;
			if (!fsBinaryStateIterTable.containsKey(state)){
				fsBinaryStateIterTable.put(state, new FSBinaryLazyIterator(trans, gram, state, mh));
				if (debug )Debug.debug(debug, "No Reuse of "+state);

			}
			else {
				if (debug )Debug.debug(debug, "Reuse of "+state);
			}
			if (!fsBinaryStateResultTable.containsKey(state))
				fsBinaryStateResultTable.put(state, new Vector<GrammarRule>());
			results = fsBinaryStateResultTable.get(state);
			iterator = fsBinaryStateIterTable.get(state);					
		}
		// for lex
		public FSBinaryIndexedIterator(BinaryTreeTransducer trans, Grammar gram, int mh) throws UnusualConditionException {
			next = 0;


			boolean debug = false;

			if (fsBinaryBaseIterTable == null){
				fsBinaryBaseIterTable = new FSBinaryLazyIterator(trans, gram, mh);
				if (debug )Debug.debug(debug, "No Reuse of lex");

			}
			else {
				if (debug )Debug.debug(debug, "Reuse of lex");
			}
			if (fsBinaryBaseResultTable == null)
				fsBinaryBaseResultTable = new Vector<GrammarRule>();
			results = fsBinaryBaseResultTable;
			iterator = fsBinaryBaseIterTable;					
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
					if (!c.equals(keyst) && fsBinaryStateResultTable.containsKey(c)) {
						if (debug) Debug.debug(debug, "Adding "+newrule+" to list of "+c+" when in "+keyst+" iterator");
						fsBinaryStateResultTable.get(c).add(newrule);
					}
				}
			}
			return results.get(next++);
		}
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BsIndexedIterator");
		}
	}
	
	
	// top level pervasive laziness -- given a state, choose a rule from rhs and feed this with the transducer 
	// to a mid level iterator
	private class FSBinaryLazyIterator implements PIterator<GrammarRule> {
		// main queue
		private FixedPrioritiesPriorityQueue<PIterator<GrammarRule>> q;
		// wait queue
		private FixedPrioritiesPriorityQueue<PIterator<GrammarRule>> w;
		// next items
		private FixedPrioritiesPriorityQueue<GrammarRule> next;

		private PIterator<GrammarRule> biter;
		// key state for nonlex. null for lex
		private FilteringPairSymbol state;
		private BinaryTreeTransducer atrans;
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
		// non-lexical version
		public FSBinaryLazyIterator(BinaryTreeTransducer trans, Grammar gram, FilteringPairSymbol st, int mh) 
		throws UnusualConditionException {
			boolean debug =false;
			initialize();
			atrans = trans;
			state = st;
			maxheap= mh;
			biter = gram.getFSIter(state.getRight());
	
			if (!biter.hasNext())
				return;
			// prime q
			while (biter.hasNext() && q.isEmpty()) {
//				if (debug) Debug.debug(debug, "trying to seed with "+biter.peek());
				GrammarRule rule = biter.next();
				
				FSBinaryMidLazyIterator l = new FSBinaryMidLazyIterator(atrans, state, rule, maxheap);
				
				if (!l.hasNext())
					continue;
				double cost = -l.peek().getWeight();
				if (!q.add(l, cost))
					throw new UnusualConditionException("Couldn't add new lexical iterator with cost "+cost);
			}
			fillNext();
			if (debug) Debug.debug(debug, "Initial list for "+st+" is "+next);
		
		}
		// lexical version
		public FSBinaryLazyIterator(BinaryTreeTransducer trans, Grammar gram, int mh) 
		throws UnusualConditionException {
			boolean debug =false;
			initialize();
			atrans = trans;
			maxheap= mh;
			biter = gram.getLexFSIter();
			if (!biter.hasNext())
				return;
			// prime q
			while (biter.hasNext() && q.isEmpty()) {
				if (debug) Debug.debug(debug, "trying to seed with "+biter.peek());
				FSBinaryLexBaseLazyIterator l = new FSBinaryLexBaseLazyIterator(atrans, biter.next());
				if (!l.hasNext())
					continue;
				double cost = -l.peek().getWeight();
				if (!q.add(l, cost))
					throw new UnusualConditionException("Couldn't add new lexical iterator with cost "+cost);
			}
			fillNext();
			if (debug) Debug.debug(debug, "Initial lexical list is "+next);

		}
		
		// the meat of the class
		private void fillNext() throws UnusualConditionException {
			boolean debug = false;
			if (q.isEmpty()) {
				if (debug) Debug.debug(debug, "Main queue is empty, so no move made");
				return;
			}
			
			// move items into the next list
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
				while (w.isEmpty() && biter.hasNext()) {
					PIterator<GrammarRule> l;
					GrammarRule rule = biter.next();
					if (state != null) {
						l = new FSBinaryMidLazyIterator(atrans, state, rule, maxheap);
					}
					else {
						l = new FSBinaryLexBaseLazyIterator(atrans, rule);
					}
					if (!l.hasNext())
						continue;
					double waitcost = -l.peek().getWeight();
					if (!w.add(l, waitcost))
						throw new UnusualConditionException("Couldn't addnew waiting iterator with cost "+waitcost);							
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
			throw new UnsupportedOperationException("Didn't bother with remove for LazyIterator");
		}

	}
	
	// mid level pervasive laziness -- given a rule and key state, choose a state sequence (from an iterator) and feed the two with
	// the transducer to a base iterator
	// non-lexical only!
	private class FSBinaryMidLazyIterator implements PIterator<GrammarRule> {
		// main queue
		private FixedPrioritiesPriorityQueue<PIterator<GrammarRule>> q;
		// wait queue
		private FixedPrioritiesPriorityQueue<PIterator<GrammarRule>> w;
		// next items
		private FixedPrioritiesPriorityQueue<GrammarRule> next;

		private PIterator<StateSeq> biter;
		private GrammarRule brule;
		private BinaryTreeTransducer atrans;
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
		public FSBinaryMidLazyIterator(BinaryTreeTransducer trans, FilteringPairSymbol state, GrammarRule r, int mh) 
		throws UnusualConditionException {
			boolean debug =false;
			initialize();
			brule = r;
			atrans = trans;
			maxheap= mh;
			
			// if rule is unary, solo iterator
			if (brule.getChildren().size() == 1) {
				// add in single-state case, too
				StateSeq solo = getStateSeq(state);
				
				biter = new WrappedPIterator<StateSeq>(solo);
			}
			else					
				biter = new PairIterator(state, atrans, brule, maxheap);
			if (!biter.hasNext())
				return;
			// prime q
			while (biter.hasNext() && q.isEmpty()) {
				if (debug) Debug.debug(debug, "trying to seed with "+biter.peek());
				FSBinaryBaseLazyIterator l = new FSBinaryBaseLazyIterator(atrans, brule, biter.next(), maxheap);
				if (!l.hasNext())
					continue;
				double cost = -l.peek().getWeight();
				if (!q.add(l, cost))
					throw new UnusualConditionException("Couldn't add new iterator with cost "+cost);
			}
			fillNext();
		}
		// the meat of the class
		private void fillNext() throws UnusualConditionException {
			boolean debug = false;
			if (q.isEmpty()) {
				if (debug) Debug.debug(debug, "Main queue is empty, so no move made");
				return;
			}
			
			// move items into the next list
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
				while (w.isEmpty() && biter.hasNext()) {
					FSBinaryBaseLazyIterator l = new FSBinaryBaseLazyIterator(atrans, brule, biter.next(), maxheap);
					if (!l.hasNext())
						continue;
					double waitcost = -l.peek().getWeight();
					if (!w.add(l, waitcost))
						throw new UnusualConditionException("Couldn't addnew waiting iterator with cost "+waitcost);							
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

	
	// match given valid state sequence (vector of states) to all matching rules, one at a time
	// that forms a grammar rule!
	// binary only, height-one only, non-lexical only
	private class FSBinaryBaseLazyIterator implements PIterator<GrammarRule> {
		private FixedPrioritiesPriorityQueue<GrammarRule> next;
		//private StateSeq base;
		private GrammarRule rule;
		private Iterator<TreeTransducerRule> iterator;
		
		private double lastcost = 0;
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;
		public FSBinaryBaseLazyIterator(BinaryTreeTransducer trans, GrammarRule r, StateSeq b, int maxheap) throws UnusualConditionException {
			next = new FixedPrioritiesPriorityQueue<GrammarRule> ();
			
			Vector<Symbol> transRHS = new Vector<Symbol>();
			//base = b;
			rule = r;
			transRHS.add(b.getLeft().getLeft());
			if (b.getRight() != null)
				transRHS.add(b.getRight().getLeft());
			
//			VecSymbol vs = SymbolFactory.getVecSymbol(transRHS);
			iterator = trans.getForwardRules(transRHS, r.getLabel()).iterator();
			fillNext();
			
		}
		private void fillNext() throws UnusualConditionException {
			boolean debug =false;
			while (
					(maxheap == 0 ? true : next.size() < maxheap) && 
					iterator.hasNext()
			) {
				TreeTransducerRule ttr = iterator.next();		
				if (debug) Debug.debug(debug, "Matching "+ttr+" to "+rule);
				FilteringPairSymbol topState = FilteringPairSymbol.get(ttr.getState(), rule.getState(), FilteringPairSymbol.FILTER.NOEPS);
				GrammarRule newg = formAndAddRules(ttr, rule, topState);
				if (debug) Debug.debug(debug,  "Built rule for "+rule+"; "+newg);
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
	
	
	//struct for storing vector of index symbols with weight = sum of inside costs
	private PMap<FilteringPairSymbol, PMap<FilteringPairSymbol, StateSeq>> binarySeqMemo;
	private HashMap<FilteringPairSymbol, StateSeq> unarySeqMemo;
	private int seqStateCreateCount = 0;
	private StateSeq getStateSeq(FilteringPairSymbol l, FilteringPairSymbol r, double w) {
		boolean useMemo = true;
		if (useMemo) {
		if (!binarySeqMemo.goc(l).containsKey(r))
			binarySeqMemo.get(l).put(r, new StateSeq(l, r, w));
		
		return binarySeqMemo.get(l).get(r);
		}
		else return new StateSeq(l, r, w);
	}
	private StateSeq getStateSeq(FilteringPairSymbol l) {
		boolean useMemo = true;
		if (useMemo) {
		if (!unarySeqMemo.containsKey(l))
			unarySeqMemo.put(l, new StateSeq(l));
		
		return unarySeqMemo.get(l);
		}
		else return new StateSeq(l);
	}
	private class StateSeq {
		public StateSeq(FilteringPairSymbol l, FilteringPairSymbol r, double w) {
			seqStateCreateCount++;
			_left = l;
			_right = r;
			_weight = w;
//			if (seqStateCreateCount % 10000 == 0) { Debug.debug(true, seqStateCreateCount+" seq states created"); }
		}
		public StateSeq(FilteringPairSymbol l) {
			seqStateCreateCount++;

			_left = l;
			_right = null;
			_weight = 0;
//			if (seqStateCreateCount % 10000 == 0) { Debug.debug(true, seqStateCreateCount+" seq states created"); }
		}
		public FilteringPairSymbol getLeft() { return _left; }
		public FilteringPairSymbol getRight() { return _right; }

		public String toString() {
			if (_right == null)
				return "["+_left+"]:"+_weight;
			return  "["+_left+","+_right+"]:"+_weight;

		}
		public double getWeight() { return _weight; }
		private FilteringPairSymbol _left;
		private FilteringPairSymbol _right;
		private double _weight;
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
		
		// make left and/or right iterators and put them into the heap
		public PairIterator(FilteringPairSymbol state, BinaryTreeTransducer ttrs, GrammarRule rule, int mh) throws UnusualConditionException {
			boolean debug = false;
			if (debug) Debug.debug(debug, "Making Pair Iterator for "+state+" on "+rule);
			initialize();
			maxheap = mh;
			if (rule.getChildren().size() != 2)
				throw new UnusualConditionException("Tried to make a pair iterator on a non-binary rule "+rule);
			Symbol stateBase = state.getRight();
			// left child matches base, so iterate to the right
			// check that there are some rules with left child, rule symbol
			if (rule.getChild(0).equals(stateBase)) {
				// check for valid rules
				if (ttrs.hasLeftStateRules(state.getLeft(), rule.getLabel())) {
					RightPairIterator rpi = new RightPairIterator(state, rule);
					if (rpi.hasNext()) {
						double rightcost = rpi.peek().getWeight();
						if (!q.add(rpi, rightcost))
							throw new UnusualConditionException("Couldn't add right iterator for "+state);
					}
				}
			}
			// right child matches base, so iterate to the left 
			// check that there are some rules with right child, rule symbol
			if (rule.getChild(1).equals(stateBase)) {
				if (ttrs.hasRightStateRules(state.getLeft(), rule.getLabel())) {
					LeftPairIterator lpi = new LeftPairIterator(state,rule);
					if (lpi.hasNext()) {
						double leftcost = lpi.peek().getWeight();
						if (!q.add(lpi, leftcost))
							throw new UnusualConditionException("Couldn't add left iterator for "+state);
					}
				}
			}
			fillNext();
			if (debug) Debug.debug(debug, "Initial list of state seqs is "+next);
		}
		// just pop off top and put it back in -- no two-queue strategy here
		private void fillNext() throws UnusualConditionException {
			boolean debug = false;
			if (q.isEmpty()) {
	//			if (debug) Debug.debug(debug, "Main queue is empty, so no move made");
				return;
			}

			while (
					(maxheap == 0 ? true : next.size() < maxheap) && 
					!q.isEmpty()
			) {
				PIterator<StateSeq> current = q.removeFirst();
				StateSeq g  = current.next();
				next.add(g, g.getWeight());
				if (debug) Debug.debug(debug,  "Next state seq is "+g);
				if (current.hasNext()) {
					double nextcost = current.peek().getWeight();
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
	
	// use index into states for this iterator over pairs (right state given)
	private class LeftPairIterator implements PIterator<StateSeq> {
		private FilteringPairSymbol right;
		private int nextItem;
		private Vector<Pair<FilteringPairSymbol, Double>> vec;
		private StateSeq next;
		public LeftPairIterator(FilteringPairSymbol r, GrammarRule rule) throws UnusualConditionException {
			nextItem = 0;
			right = r;
			Symbol leftBase = rule.getChild(0);
			if (leftBase == null)
				throw new UnusualConditionException("No left child in "+rule);
			vec = states.get(leftBase);
			fillNext();
		}
		public boolean hasNext() {
			return next != null;
		}
		private void fillNext() {
			if (vec != null && vec.size() > nextItem) {
				double wgt = vec.get(nextItem).r();
				next = getStateSeq(vec.get(nextItem++).l(), right, wgt);
				
			}
			else
				next = null;
		}
		public StateSeq next() throws NoSuchElementException {
			if (!hasNext())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			StateSeq ret = next;
			fillNext();
			return ret;
		}
		public StateSeq peek() throws NoSuchElementException {
			if (!hasNext())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			return next;
		}
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
		}
	}
	
	// use index into states for this iterator over pairs (left state given)
	private class RightPairIterator implements PIterator<StateSeq> {
		private FilteringPairSymbol left;
		private int nextItem;
		private Vector<Pair<FilteringPairSymbol, Double>> vec;
		private StateSeq next;
		public RightPairIterator(FilteringPairSymbol l, GrammarRule rule) throws UnusualConditionException {
			nextItem = 0;
			left = l;
			Symbol rightBase = rule.getChild(1);
			if (rightBase == null)
				throw new UnusualConditionException("No right child in "+rule);
			vec = states.get(rightBase);
			fillNext();
		}
		public boolean hasNext() {
			return next != null;
		}
		private void fillNext() {
			if (vec != null && vec.size() > nextItem) {
				double wgt = vec.get(nextItem).r();
				next = getStateSeq(left, vec.get(nextItem++).l(), wgt);
			}
			else
				next = null;
		}
		public StateSeq next() throws NoSuchElementException {
			if (!hasNext())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			StateSeq ret = next;
			fillNext();
			return ret;
		}
		public StateSeq peek() throws NoSuchElementException {
			if (!hasNext())
				throw new NoSuchElementException("Asked for next on empty PIterator");
			return next;
		}
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BasLazyIterator");
		}
	}
	
	
	// TODO: base lazy iterator for non-lexical rules here!!!
	
	
	
	
	// forward star grammar-driven lexical base lazy iterator
	// used by binary-only top-level
	// given a lexical rule from grammar, match it to an iterator over matching transducer rules
	private class FSBinaryLexBaseLazyIterator implements PIterator<GrammarRule> {
		private FixedPrioritiesPriorityQueue<GrammarRule> next;
		private GrammarRule base;
		private Iterator<TreeTransducerRule> iterator;		
		private double lastcost = 0;
		// should we report monotonicity errors?
		private static final boolean REPORTMONO = false;
		
		public FSBinaryLexBaseLazyIterator(BinaryTreeTransducer trans, GrammarRule g)  throws UnusualConditionException {
			next = new FixedPrioritiesPriorityQueue<GrammarRule> ();
			base = g;
			iterator = trans.getRelPosLexRules(g.getLabel(), 0).iterator();
			fillNext();
			
		}
		private void fillNext() throws UnusualConditionException {
			boolean debug =false;
			while (
					(maxheap == 0 ? true : next.size() < maxheap) && 
					iterator.hasNext()
			) {
				TreeTransducerRule r = iterator.next();		
				if (debug) Debug.debug(debug, "Matching "+r+" to "+base);
				FilteringPairSymbol topState = FilteringPairSymbol.get(r.getState(), base.getState(), FilteringPairSymbol.FILTER.NOEPS);
				GrammarRule newg = formAndAddRules(r, base, topState);
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
	
	
	
	// generally make a lazy iterator and return it
	// should be no artificially made states here
	public PIterator<GrammarRule> getBSIter(Symbol s, Symbol label, int rank) throws UnusualConditionException {
			boolean debug = false;
		if (s instanceof FilteringPairSymbol) {
			return new BSIndexedIterator((OTFTreeTransducerRuleSet)trans, label, rank, grammar, (FilteringPairSymbol)s, maxheap);
//			return new BSLazyIterator((OTFTreeTransducerRuleSet)trans, label, rank, grammar, (FilteringPairSymbol)s, maxheap);
//			return new BSInvertLazyIterator((OTFTreeTransducerRuleSet)trans, label, rank, grammar, (FilteringPairSymbol)s, maxheap);

		}
		else {
				throw new UnusualConditionException("Asked for rules from non-filtering symbol "+s);
		}
	}
	// generally make a lazy iterator and return it
	// should be no artificially made states here
	public PIterator<GrammarRule> getBSIter(Symbol s) throws UnusualConditionException {
			boolean debug = false;
		if (s instanceof FilteringPairSymbol) {
			return new BSIndexedIterator((OTFTreeTransducerRuleSet)trans, grammar, (FilteringPairSymbol)s, maxheap);
//			return new BSLazyIterator((OTFTreeTransducerRuleSet)trans, grammar, (FilteringPairSymbol)s, maxheap);
//			return new BSInvertLazyIterator((OTFTreeTransducerRuleSet)trans, grammar, (FilteringPairSymbol)s, maxheap);

		}
		else {
				throw new UnusualConditionException("Asked for rules from non-filtering symbol "+s);
		}
	}
	
	// lazy lexical unfiltered forward
	// NOTE: taken over by gramar-first stuff!
	public PIterator<GrammarRule> getLexFSIter() throws UnusualConditionException {
		//return new FSIndexedIterator(trans, grammar,  maxheap);
		//return new FSLazyIterator(trans, grammar, maxheap);
		return new FSBinaryLazyIterator(trans, grammar, maxheap);
		//return new FSBinaryIndexedIterator(trans, grammar,  maxheap);

	}

	// lazy lexical filtered forward
	public PIterator<GrammarRule> getLexFSIter(Symbol label, int rank) throws UnusualConditionException {
		return new FSIndexedIterator((OTFTreeTransducerRuleSet)trans, grammar, label, rank, maxheap);
		//return new FSLazyIterator(trans, grammar, label, rank, maxheap);
	}
	
	// lazy forward with no specified position (binary only)
	@Override
	public PIterator<GrammarRule> getFSIter(Symbol s)
			throws UnusualConditionException {
		if (s instanceof FilteringPairSymbol) {
//			return new FSBinaryLazyIterator(trans, grammar,(FilteringPairSymbol)s, maxheap);
			return new FSBinaryIndexedIterator(trans, grammar,(FilteringPairSymbol)s, maxheap);

		}
		else {
			throw new UnusualConditionException("Asked for rules from non-filtering symbol "+s);
		}
	}
	
	// lazy unfiltered forward
	public PIterator<GrammarRule> getFSIter(Symbol s, int pos) throws UnusualConditionException {
		if (s instanceof FilteringPairSymbol) {
			return new FSIndexedIterator((OTFTreeTransducerRuleSet)trans, grammar, (FilteringPairSymbol)s, pos, maxheap);
			//return new FSLazyIterator(trans, grammar, (FilteringPairSymbol)s, pos, maxheap);
		}
		else {
			throw new UnusualConditionException("Asked for rules from non-filtering symbol "+s);
		}
	}

	// lazy filtered forward
	public PIterator<GrammarRule> getFSIter(Symbol s, int pos, Symbol l, int r) throws UnusualConditionException {
		if (s instanceof FilteringPairSymbol) {
			return new FSIndexedIterator((OTFTreeTransducerRuleSet)trans, grammar, (FilteringPairSymbol)s, pos, l, r, maxheap);
			//return new FSLazyIterator(trans, grammar, (FilteringPairSymbol)s, pos, l, r, maxheap);
		}
		else {
			throw new UnusualConditionException("Asked for rules from non-filtering symbol "+s);
		}
	}

	
	@Override
	public Iterable<GrammarRule> getBackwardRules(Symbol s)
			throws UnusualConditionException {
		Vector<GrammarRule> vec = new Vector<GrammarRule>();
		PIterator<GrammarRule> it = getBSIter(s);
		while (it.hasNext())
			vec.add(it.next());
		return vec;
	}
	
	// TODO: this should just iterate through the BSIter rather than having separate
	// code
	
	@Override
	public Iterable<GrammarRule> getBackwardRules(Symbol s, Symbol l, int r) 
			throws UnusualConditionException {
		throw new UnusualConditionException("Get Backward rules not currently on for TGOTFGrammar");
//		boolean debug = false;
//		if (
//				!bslabeldone.containsKey(s) || 
//				!bslabeldone.get(s).containsKey(l) || 
//				!bslabeldone.get(s).get(l).contains(r)
//		)
//		{
//			if (s instanceof FilteringPairSymbol) {
//				Symbol a = ((FilteringPairSymbol)s).getLeft();
//				Symbol b = ((FilteringPairSymbol)s).getRight();
//				FilteringPairSymbol.FILTER f = ((FilteringPairSymbol)s).getFilter();
//				if (!bslabeldone.containsKey(s))
//					bslabeldone.put(s, new HashMap<Symbol, HashSet<Integer>>());
//				if (!bslabeldone.get(s).containsKey(l))
//					bslabeldone.get(s).put(l, new HashSet<Integer>());
//				bslabeldone.get(s).get(l).add(r);
//				// for each transducer rule, parse that rule and get all possible state strings
//				for (TreeTransducerRule t : trans.getBackwardRules(a, l, r)) {
//					RGOTFGrammar transGram = RGOTFGrammar.get(t, grammar, b, t.getSemiring(), maxheap);
//					if (debug) Debug.debug(debug, "Just built transgram "+transGram+"; getting next string");
//					// for each string in the transgram, form a rule
//					Pair<Vector<Symbol>, Double> string = transGram.getNextString();
//					while (string != null) {
//						Vector<Symbol> str = string.l();
//						double cost = string.r();
//						StringBuffer display = new StringBuffer("[");
//						for (Symbol m : str) {
//							if (!(m instanceof PairSymbol) || !(((PairSymbol)m).getLeft() instanceof PairSymbol ))
//								throw new UnusualConditionException("Bad form for "+m);
//							PairSymbol mp = (PairSymbol)m;
//							PairSymbol mpl = (PairSymbol)mp.getLeft();
//							display.append("("+mpl.getLeft()+","+mpl.getRight()+") -> "+mp.getRight()+" ");
//						}
//						display.append("] # "+cost);
//						Debug.prettyDebug("Will map "+t+" to "+display);
//						string = transGram.getNextString();
//						// TODO: form and add the rule
//					}					
//					
////					TDItem axiom = new TDItem(getTreeStateSym(t.getRHS(), b));
////					Vector<ParseRule> parseRules = parseRHS(grammar, axiom);
//				}
//				// then map those state strings to the lhs to form the rule 
//			}
//			else 
//				throw new UnusualConditionException("Tried to do bs operation with "+s+" which is not a pair symbol");
//		}
//		if (
//				!bslabelrules.containsKey(s) || 
//				!bslabelrules.get(s).containsKey(l) || 
//				!bslabelrules.get(s).get(l).containsKey(r)
//		)
//			return bslabelrules.get(s).get(l).get(r);
//		return empty;
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
	public Symbol getStartState() throws UnsupportedOperationException {
		if (startState != null)
			return startState;
		else
			throw new UnsupportedOperationException("No saved start state in TGOTFGrammar");
	}
	
	@Override
	public boolean isStartState(Symbol s) {
		if (startState != null)
			return s.equals(startState);
		if (s instanceof FilteringPairSymbol) {
			FilteringPairSymbol state = (FilteringPairSymbol)s;
			return trans.isStartState(state.getLeft()) && grammar.isStartState(state.getRight());
		}
		else
			return false;
	}
	
	@Override
	public Iterable<GrammarRule> getTerminalRules() {
		// TODO Auto-generated method stub
		return null;
	}
	// inject states as their inside cost is found. important for building new rules
	// only care whether we were able to inject into this grammar
	// beams aren't passed down
	@Override
	boolean injectState(Symbol s, double wgt) throws UnusualConditionException {
		boolean debug = false;
		int injectBeam = getInjectCap();
		if (debug) Debug.debug(debug, "Injecting "+s+"("+s.hashCode()+") with beam of "+injectBeam);

		if (!(s instanceof FilteringPairSymbol))
			throw new UnusualConditionException("Tried to inject non-filtering pair state "+s);
		FilteringPairSymbol state = (FilteringPairSymbol)s;
		if (allStates.contains(state)) {
			if (debug) Debug.debug(debug, state+" already exists");
			return true;
		}
		allStates.add(state);
	
		Symbol rightState = state.getRight();
		if (!grammar.injectState(rightState, wgt)) {
			//if(debug) Debug.debug(debug, "Rejecting "+s+"; descendent "+rightState+" rejected");
			
			return false;
		}
		if (!states.containsKey(rightState))
			states.put(rightState, new Vector<Pair<FilteringPairSymbol, Double>>());
		if (injectBeam == 0 || states.get(rightState).size() < injectBeam) {
			Pair<FilteringPairSymbol, Double> newPair = new Pair<FilteringPairSymbol, Double>(state, wgt); 
			states.get(rightState).add(newPair);
			return true;
		}
		else {
			if(debug) Debug.debug(debug, "Rejecting "+s+"; too many ="+rightState+"("+injectBeam+")"+":"+states.get(rightState));
			
		
			return false;
		}
	}
	void reportRules() {
		Debug.prettyDebug("TGOTF Grammar has "+ruleCount+" rules");
//		printAllFoundRules();
		grammar.reportRules();
	}
	
	// actually display every rule in every hashmap
	private void printAllFoundRules() {
		Debug.debug(true, "rulePairs");
		for (TreeTransducerRule a : rulePairs.keySet()) {
			for (Pair<Vector<Symbol>, Double> b : rulePairs.get(a).keySet()) {
				for (FilteringPairSymbol c : rulePairs.get(a).get(b).keySet()) {
					Debug.debug(true, ""+rulePairs.get(a).get(b).get(c));
				}
			}
		}
		Debug.debug(true, "one level rule pairs");
		for (TreeTransducerRule a : oneLevelRulePairs.keySet()) {
			for (GrammarRule b : oneLevelRulePairs.get(a).keySet()) {
				for (FilteringPairSymbol c : oneLevelRulePairs.get(a).get(b).keySet()) {
					Debug.debug(true, ""+oneLevelRulePairs.get(a).get(b).get(c));
				}
			}
		}
		Debug.debug(true, "state rule pairs");
		for (Symbol a : stateRulePairs.keySet()) {
			for (GrammarRule b : stateRulePairs.get(a).keySet()) {
				Debug.debug(true, ""+stateRulePairs.get(a).get(b));
			}
		}
		Debug.debug(true, "rule state pairs");
		for (TreeTransducerRule a : ruleStatePairs.keySet()) {
			for (Symbol b : ruleStatePairs.get(a).keySet()) {
				Debug.debug(true, ""+ruleStatePairs.get(a).get(b));
			}
		}
	}
	// states we can use -- validated by the chart, and in order by weight
	// indexed by left and right sides
	private HashMap<Symbol, Vector<Pair<FilteringPairSymbol, Double>>> states;
	// just track which states have been added so duplicates aren't added
	private HashSet<FilteringPairSymbol> allStates;
	
	// initialize storage maps
	private void initialize() {
		realRules = new HashMap<Symbol, GrammarRule> ();
		
		bsrules = new HashMap<Symbol, Vector<GrammarRule>> ();
		bslabelrules = new HashMap<Symbol, HashMap<Symbol, HashMap<Integer, Vector<GrammarRule>>>> ();
		bsdone = new HashSet<Symbol> ();
		bslabeldone = new HashMap<Symbol, HashMap<Symbol, HashSet<Integer>>>();
		rulePairs = new HashMap<TreeTransducerRule, HashMap<Pair<Vector<Symbol>, Double>, HashMap<FilteringPairSymbol, GrammarRule>>>();
		oneLevelRulePairs = new  PMap<TreeTransducerRule, PMap<GrammarRule, PMap<FilteringPairSymbol, GrammarRule>>>();

		stateRulePairs = new HashMap<Symbol, HashMap<GrammarRule, GrammarRule>> ();
		ruleStatePairs = new HashMap<TreeTransducerRule, HashMap<Symbol, GrammarRule>> ();
		bsFilteredResultTable = new PMap<Symbol, PMap<Symbol, PMap<Integer, Vector<GrammarRule>>>>(); 
		bsFilteredIterTable = new PMap<Symbol, PMap<Symbol, PMap<Integer, PIterator<GrammarRule>>>>() ;
		bsUnfilteredResultTable = new HashMap<Symbol, Vector<GrammarRule>> ();
		bsUnfilteredIterTable = new HashMap<Symbol, PIterator<GrammarRule>> ();
		
		fsStatePosResultTable	= new  PMap<Symbol, PMap<Integer,Vector<GrammarRule>>> ();
		fsStatePosIterTable		= new  PMap<Symbol, PMap<Integer, FSLazyIterator>> ();
		
		// label->rank
		fsLabelRankResultTable		= new  PMap<Symbol, PMap<Integer,Vector<GrammarRule>>> ();
		fsLabelRankIterTable		= new  PMap<Symbol, PMap<Integer, FSLazyIterator>> ();
		
		// state->pos->label->rank
		fsStatePosLabelRankResultTable = new  PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, Vector<GrammarRule>>>>> ();
		fsStatePosLabelRankIterTable	= new  PMap<Symbol, PMap<Integer, PMap<Symbol, PMap<Integer, FSLazyIterator>>>> ();
		states = new HashMap<Symbol, Vector<Pair<FilteringPairSymbol, Double>>> ();
		allStates = new HashSet<FilteringPairSymbol> ();
		
		// binary by state
		fsBinaryStateResultTable = new PMap<Symbol,Vector<GrammarRule>> ();
		fsBinaryStateIterTable = new PMap<Symbol, FSBinaryLazyIterator> ();
		
		binarySeqMemo = new PMap<FilteringPairSymbol, PMap<FilteringPairSymbol, StateSeq>> ();
		unarySeqMemo = new HashMap<FilteringPairSymbol, StateSeq>(); 
		
		ruleCount = 0;

	}
	// grammar is made up of transducer and grammar
	private BinaryTreeTransducer trans;
	private Grammar grammar;
	private int maxheap;
	// rule-per-state cap (enforced by indexed iterators)
	private int cap;
	// rules indexed by head state
	private HashMap<Symbol, Vector<GrammarRule>> bsrules;
	// rules indexed by head state, label, rank
	private HashMap<Symbol, HashMap<Symbol, HashMap<Integer, Vector<GrammarRule>>>> bslabelrules;
	// inquiries that have been made
	private HashSet<Symbol> bsdone;
	private HashMap<Symbol, HashMap<Symbol, HashSet<Integer>>> bslabeldone;
	
	// rules that change the filter from REAL to whatever is input for unifying states that are at bottom
	// of "real" rules
	private HashMap<Symbol, GrammarRule> realRules;
	
	// (default) multi-level rule pairs (with filter)
	private HashMap<
	TreeTransducerRule, 
	HashMap<Pair<Vector<Symbol>, Double>, 
	HashMap<FilteringPairSymbol, GrammarRule>>> rulePairs;

	// one-level rule pairs (like in GTOTFGrammar)
	private PMap<TreeTransducerRule, PMap<GrammarRule, PMap<FilteringPairSymbol, GrammarRule>>> oneLevelRulePairs;
	
	private HashMap<Symbol, HashMap<GrammarRule, GrammarRule>> stateRulePairs;
	private HashMap<TreeTransducerRule, HashMap<Symbol, GrammarRule>> ruleStatePairs;
	// do we process td or bu?
	private boolean isTD;
	// the start state
	private FilteringPairSymbol startState;
	private static Vector<GrammarRule> empty;
	private static Vector<Pair<Vector<Symbol>, Double>> stringempty;
	// count rules as they're formed
	private int ruleCount;
	private void incRuleCount() {
		ruleCount++;
		boolean debug = false;
		final int inc = 1000;
		if (debug && ruleCount % inc == 0) {
			Debug.prettyDebug("TGOTF has "+ruleCount+" rules");
		}
	}
	
	// turns filtering on, which is needed if input-epsilon rules are allowed
	private static final boolean ALLOWINEPS = false;
	static {
		empty = new Vector<GrammarRule>();
		stringempty = new Vector<Pair<Vector<Symbol>, Double>> ();
		strUniqueMap = new HashMap<String, Pair<Vector<Symbol>, Double>> ();
	}
	public static void main(String argv[]) {
		boolean runTopDown = true;
		TropicalSemiring semiring = new TropicalSemiring();
//		Debug.debug(true, Runtime.getRuntime().totalMemory()+" total");
//		Debug.debug(true, Runtime.getRuntime().freeMemory()+" free");
//		Debug.debug(true, "Running "+(runTopDown ? "top-down" : "bottom-up"));
		try {
			String charset = "euc-jp";
			int mh = Integer.parseInt(argv[0]);
			int c = Integer.parseInt(argv[1]);
			int k = Integer.parseInt(argv[2]);
			int endPoint = 2;
			Debug.debug(true, "Max heap of "+mh+", injection cap of "+c+" and getting "+k+" answers");
			
			// if not a batch, last file is an RTG. Otherwise, it's either strings or trees
			boolean isBatch = true;
			// exhaustively parse strings to make forest, or convert trees into simple RTG?
			boolean isString = false;
			// where the end of the chain of tree transducers is. 
			int startPoint;
			if (isString && isBatch)
				startPoint = argv.length-3;
			else
				startPoint = argv.length-2;
			// load all the tree transducers into a vector from back to front
			Vector<OTFTreeTransducerRuleSet> tts = new Vector<OTFTreeTransducerRuleSet>();
			for (int i = startPoint; i > endPoint; i--) {
				TreeTransducerRuleSet trs = new TreeTransducerRuleSet(argv[i], charset, semiring);
				//			Debug.prettyDebug("Done loading "+argv[i]);
				OTFTreeTransducerRuleSet ottrs = new OTFTreeTransducerRuleSet(trs);
				tts.add(ottrs);
			}
			
			// if batch, read last file as strings or trees
			if (isBatch) {
				
				Vector itemvec;
				// parser for string case. parses each string into rtg
				StringTransducerRuleSet parser;
				if (isString) {
					itemvec = CFGTraining.readItemSet(
							new BufferedReader(new InputStreamReader(new FileInputStream(argv[argv.length-1]), charset)), 
							false, semiring);
					parser = new StringTransducerRuleSet(argv[argv.length-2], charset, semiring);
				}
				else {
					parser = null;
					itemvec = RuleSetTraining.readItemSet(
							new BufferedReader(new InputStreamReader(new FileInputStream(argv[argv.length-1]), charset)), 
							false, true, semiring);
				}
				
				File itemfile = (File)itemvec.get(0);
				int itemcount = ((Integer)itemvec.get(1)).intValue();
				ObjectInputStream ois = new ObjectInputStream(new FileInputStream(itemfile));
				
				// for each item, either parse it or convert it to rtg, wrap in cascade, and do operation
				for (int itemnum = 0; itemnum < itemcount; itemnum++) {
					
					FilteringPairSymbol.clear();
					PairSymbol.clear();
					System.gc(); System.gc(); System.gc();
					Date startTime = new Date();
					RTGRuleSet rtg;
					if (isString) {
						StringItem str = (StringItem)ois.readObject();
						Date preForestTime = new Date();
						rtg = new RTGRuleSet(parser, str, 0, 1);
						Date postForestTime = new Date();
						Debug.dbtime(1, 1, preForestTime, postForestTime, "time to build forest by parsing string");
					}
					else {
						TreeItem tree = (TreeItem)ois.readObject();
						Date preForestTime = new Date();
						rtg = new RTGRuleSet(tree, semiring);
						Date postForestTime = new Date();
						Debug.dbtime(1, 1, preForestTime, postForestTime, "time to build forest from tree");
					}
					Date preAssembleTime = new Date();

//					Debug.prettyDebug("Done loading rtg");
					ConcreteGrammar gr = new ConcreteGrammar(rtg);
					Grammar g = gr;
					for (OTFTreeTransducerRuleSet ottrs : tts) {
						Date preSubAssembleTime = new Date();
						TGOTFGrammar otfg = new TGOTFGrammar(ottrs, g, semiring, mh, c, runTopDown);
						if (mh < 0)
							g = new ConcreteGrammar(otfg);
						else
							g = otfg;
						Date postSubAssembleTime = new Date();
						if (mh < 0) {
							Debug.dbtime(1, 1, preSubAssembleTime, postSubAssembleTime, "time to assemble part of cascade");
						}
					}
					Date postAssembleTime = new Date();
					Debug.dbtime(1, 1, preAssembleTime, postAssembleTime, "time to assemble cascade");
					Date preGenTime = new Date();
					for (int i = 0; i < k; i++) {
						Pair<TreeItem, Pair<Double, Integer>> tt = g.getNextTree(runTopDown);
						if (tt == null)
							System.out.println("EOL");
						else
							System.out.println((i+1)+":"+tt.l()+" # "+tt.r().l()+" # "+tt.r().r());
				
					}
					Date postGenTime = new Date();			
					Debug.dbtime(1, 1, preGenTime, postGenTime, "time to generate");
					Date endTime = new Date();
					Debug.dbtime(1, 1, startTime, endTime, "total time");
					

//					TGOTFGrammar.printTimers();

//					g.reportRules();
//					TGOTFGrammar.resetTimers();

//					System.out.println(Runtime.getRuntime().totalMemory()+" total");
//					System.out.println(Runtime.getRuntime().freeMemory()+" free");
				}
			}
			// not batch -- last item is an rtg. use that in the cascade
			else {
				Date preAssembleTime = new Date();
				Date startTime = new Date();
				RTGRuleSet rtg = new RTGRuleSet(argv[argv.length-1], charset, semiring);
				ConcreteGrammar gr = new ConcreteGrammar(rtg);
				Grammar g = gr;
				for (OTFTreeTransducerRuleSet ottrs : tts) {
					TGOTFGrammar otfg = new TGOTFGrammar(ottrs, g, semiring, mh, c, runTopDown);
					if (mh < 0)
						g = new ConcreteGrammar(otfg);
					else
						g = otfg;
				}
				Date postAssembleTime = new Date();
				Debug.dbtime(1, 1, preAssembleTime, postAssembleTime, "time to assemble cascade");
				Date preGenTime = new Date();
				for (int i = 0; i < k; i++) {
					Pair<TreeItem, Pair<Double, Integer>> tt = g.getNextTree(runTopDown);
					if (tt == null)
						System.out.println("EOL");
					else
						System.out.println((i+1)+":"+tt.l()+" # "+tt.r().l()+" # "+tt.r().r());
			
				}
				Date postGenTime = new Date();			
				Debug.dbtime(1, 1, preGenTime, postGenTime, "time to generate");
				Date endTime = new Date();
				Debug.dbtime(1, 1, startTime, endTime, "total time");
				TGOTFGrammar.printTimers();
				
				g.reportRules();
				TGOTFGrammar.resetTimers();
				System.out.println(Runtime.getRuntime().totalMemory()+" total");
				System.out.println(Runtime.getRuntime().freeMemory()+" free");
			}
		}
		catch (ClassNotFoundException e) {
			System.err.println("Couldn't find class while reading batch");
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
			System.err.println("Bad data format reading rtg "+argv[0]);
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
			System.err.println("IO error reading rtg "+argv[0]);
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
