package edu.isi.tiburon;




import edu.stanford.nlp.util.*;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO: meant to be a new approach to grammars -- for now, just testing a bottom-up 1-best 
// and lazy-admissible approach

// TODO: make ConcreteGrammar for testing
// TODO: make LazyGrammar if this all works and saves rule accesses!
// TODO: ultimately make LazyAppliedGrammar!!!
public abstract class Grammar {


	// given a state and position (and optionally a labelR+ank), get rules 
	// that have that state in that position (with that label on top)
	abstract public Iterable<GrammarRule> getForwardRules(Symbol s, int pos) throws UnusualConditionException;
	abstract public Iterable<GrammarRule> getForwardRules(Symbol s, int pos, Symbol l, int r) throws UnusualConditionException;
	
	// backward star: given a state, get rules that have that state as a parent
	abstract public Iterable<GrammarRule> getBackwardRules(Symbol s) throws UnusualConditionException ;
	// more specific backward star: restrict by label and rank
	abstract public Iterable<GrammarRule> getBackwardRules(Symbol s, Symbol l, int r) throws UnusualConditionException;
	
	// for lazy methods: given a state, get an iterator over rules in that state
	abstract public PIterator<GrammarRule> getBSIter(Symbol s) throws UnusualConditionException;
	// filtered version: given a state, label, rank, get an iterator over rules that apply
	abstract public PIterator<GrammarRule> getBSIter(Symbol s, Symbol label, int rank) throws UnusualConditionException;

	// lazy lexical unfiltered forward
	abstract public PIterator<GrammarRule> getLexFSIter() throws UnusualConditionException;

	// lazy lexical filtered forward
	abstract public PIterator<GrammarRule> getLexFSIter(Symbol label, int rank) throws UnusualConditionException;
	
	// more general lazy unfiltered forward (assumes other states are in place)
	abstract public PIterator<GrammarRule> getFSIter(Symbol s) throws UnusualConditionException;
	
	// lazy unfiltered forward
	abstract public PIterator<GrammarRule> getFSIter(Symbol s, int pos) throws UnusualConditionException;

	// lazy filtered forward
	abstract public PIterator<GrammarRule> getFSIter(Symbol s, int pos, Symbol l, int r) throws UnusualConditionException;
	
	// get terminal rules
	abstract public Iterable<GrammarRule> getTerminalRules();
	// start state of the grammar
	abstract public Symbol getStartState() throws UnsupportedOperationException;
	abstract public boolean isStartState(Symbol s);
	
	// note completed states for bottom-up backward app
	abstract boolean injectState(Symbol s, double wgt) throws UnusualConditionException;
	
	// report on rules created
	abstract void reportRules();
	
	private Semiring semiring;
	private int injectCap;
	int getInjectCap() { return injectCap; }
	
	// just to set semiring and cap
	public Grammar(Semiring semi, int c) { semiring = semi; injectCap = c; lastDeriv = 0;}

	public Semiring getSemiring() { return semiring; }
	
	// tables and queues used for k-best
	private BinaryHeapPriorityQueue<AgendaItem> pq;
	private HashMap<Symbol, Double> bounds;
	private HashMap<Symbol, HState> hstates;
	private HashMap<Symbol, OState> ostates;
	// items as they're built -- lets us change the back pointers
	private HashMap<Symbol, BackPointerState> items;
	
	// waiting dotted rules, indexed by what they're waiting for
	private HashMap<Symbol, Vector<DottedRule>> dottedRules;
	
	// potential rules for building outside cost that didn't have an inside yet
	// indexed by their next inside
	private HashMap<Symbol, Vector<GrammarRule>> incompleteOutsideRules;
	// potential outside rules that have all their children but not their true outside yet
	// indexed by top state
	HashMap<Symbol, Vector<GrammarRule>> waitingOutsideRules;

	// potential rules for building deriv that didn't have the member yet
	private HashMap<Symbol, Vector<Deriv>> incompleteDerivs;
	
	// one-best scores
	private HashMap<Symbol, Double> obscores;
	// true outside scores
	private HashMap<Symbol, Double> outsidescores;
	// one-best rules
	private HashMap<Symbol, GrammarRule> obrules;
	// explored adjacencies for each rule
	private HashMap<GrammarRule, HashSet<Vector<Integer>>> adjacencies;
	// derivs list
	private HashMap<Symbol, Vector<Deriv>> derivs;
	
	// FS RGOTF can produce the same rule more than once. Keep track of them here to prevent duplicate processing
	//	we track them from top state
	// this is not a great tool to use
	private HashMap<Symbol, HashSet<GrammarRule>> duplicateRules;
	
	// strings formed from derivs in certain subclasses
	// replaced by more principled indexed string iterator
	//	private Vector<Pair<Vector<Symbol>, Double>> strings;
	// monotonicity checker
	private double stopBound;
	// creation checker
	private int touchcount;
	
	// track dottedRule persistence from initial creation, existence on queue, existence in holding
	private int dottedRulesBuiltCount;
	private int dottedRulesQueuedCount;
	private int dottedRulesWaitingCount;
	
	// keep track of derivations we've returned
	private int lastDeriv;
	
	// keep track of dead rules found in kbas
	private static int deadrulecount = 0;
	public int getDeadRuleCount() { return deadrulecount; }
	// keep track of pushes and pops
	private int hspush, hspop, ipush, ipop, opush, opop, dpush, dpop, rdisc;
	public void getPushPop() { 
		Debug.prettyDebug("Heuristic state: "+hspush+" push, "+hspop+" pop");
		Debug.prettyDebug("Inside state: "+ipush+" push, "+ipop+" pop");
		Debug.prettyDebug("Outside state: "+opush+" push, "+opop+" pop");
		Debug.prettyDebug("Deriv state: "+dpush+" push, "+dpop+" pop");
		Debug.prettyDebug("Rules discovered: "+rdisc);

	}
	
	// just a cast of symbol as an agenda item
	// heuristic outside cost
	private interface AgendaItem { }
	private class HState extends Symbol implements AgendaItem { 
		private Symbol s;
		public boolean equals(Object o) { 
			if (o instanceof HState) {
				HState b = (HState)o;
				return s.equals(b.s);
			}
			return false;
		}
		public int hashCode() { return s.hashCode();}
		public Hash getHash() { return s.getHash(); }
		public Symbol getSym() { return s; }
		public String toString() { return s.toString(); }
		public HState(Symbol sym) { s = sym; } 
	}
	
	// just a cast of symbol as an agenda item
	// exact outside cost
	private class OState extends Symbol implements AgendaItem { 
		private Symbol s;
		private double score;
		public boolean equals(Object o) { 
			if (o instanceof OState) {
				OState b = (OState)o;
				return s.equals(b.s);
			}
			return false;
		}
		public int hashCode() { return s.hashCode();}
		public Hash getHash() { return s.getHash(); }
		public double getScore() { return score; }
		public void setScore(double sc) { score = sc; }
		public Symbol getSym() { return s; }
		public String toString() { return s+":"+score; }
		public OState(Symbol sym, double sc) { s = sym; score = sc; } 
	}
	
	// just a cast of a PIterator for HState propagation as an agenda item
	// for hashing purposes, keyed on its state
	private class HStateRuleIterator implements AgendaItem {
		private PIterator<GrammarRule> it;
		private Symbol s;
		public boolean equals(Object o) { 
			if (o instanceof HStateRuleIterator) {
				HStateRuleIterator b = (HStateRuleIterator)o;
				return s.equals(b.s);
			}
			return false;
		}
		public int hashCode() { return s.hashCode();}
		public Hash getHash() { return s.getHash(); }
		public HStateRuleIterator(Symbol sym, PIterator<GrammarRule> iterator) { 
			s = sym; it = iterator; 
		}
		// is this iterator done?
		public boolean hasNext() {
			return it.hasNext();
		}
		// peeking just returns the awaiting value
		public double peek() { return it.peek().getWeight(); }
		public GrammarRule next() {
			return it.next();
		}
		public Symbol getSym() { return s; }
	}
	
	// just a cast of a PIterator for IState propagation as an agenda item
	// for hashing purposes, keyed on its state (use epsilon for terminal state)
	private class IStateRuleIterator implements AgendaItem {
		private PIterator<GrammarRule> it;
		private Symbol s;
		public boolean equals(Object o) { 
			if (o instanceof HStateRuleIterator) {
				HStateRuleIterator b = (HStateRuleIterator)o;
				return s.equals(b.s);
			}
			return false;
		}
		public int hashCode() { return s.hashCode();}
		public Hash getHash() { return s.getHash(); }
		public IStateRuleIterator(Symbol sym, PIterator<GrammarRule> iterator) { 
			s = sym; it = iterator; 
		}
		public IStateRuleIterator(PIterator<GrammarRule> iterator) { 
			s = Symbol.getEpsilon(); it = iterator; 
		}
		// is this iterator done?
		public boolean hasNext() {
			return it.hasNext();
		}
		// peeking just returns the awaiting value
		public double peek() { return it.peek().getWeight(); }
		public GrammarRule next() {
			return it.next();
		}
		public Symbol getSym() { return s; }
	}

	
	// inside 1-best cost
	private class BackPointerState extends Symbol implements AgendaItem {
		private Symbol s;
		private GrammarRule r;
		private double score;
		public boolean equals(Object o) { 
			if (o instanceof BackPointerState) {
				BackPointerState b = (BackPointerState)o;
				return s.equals(b.s);
			}
			return false;
		}
		public int hashCode() { return s.hashCode();}
		public Hash getHash() { return s.getHash(); }
		public Symbol getSym() { return s; }
		public GrammarRule getRule() { return r; }
		public double getScore() { return score; }
		public BackPointerState(Symbol sym, GrammarRule rule, double sc) { s = sym; r = rule; score=sc;}
		public void setRule(GrammarRule rule) { r = rule; }
		public void setScore(double sc) { score = sc; }
		public String toString() { return s+":"+r+"="+score; }
	}
	
	// for RuleIntegratedOneBest
	// symbol subclass just to fit in
	private class DottedRule extends Symbol implements AgendaItem {
		private Hash hsh;
		private GrammarRule r;
		private int dot;
		double score;
		public boolean equals(Object o) { 
			if (o instanceof DottedRule) {
				DottedRule dr = (DottedRule)o;
				return r.equals(dr.r) && dot == dr.dot;
			}
				return false;
		}
		public Hash getHash() { return hsh; }
		public String toString() { return r+":"+dot+":"+score; }
		public int hashCode() { return hsh.bag(Integer.MAX_VALUE); }
		public boolean isDone() { return (dot >= r.getChildren().size()); }
		public GrammarRule getRule() { return r; }
		public Symbol getNext() throws UnusualConditionException { 
			if (isDone())
				throw new UnusualConditionException("Tried to get Next state from done rule "+toString());
			return r.getChild(dot);
		}
		public double getScore() { return score; }
		public int getDot() { return dot; }
		
		// TODO: make this a generic times!
		// true initial
		public DottedRule(GrammarRule rule) throws UnusualConditionException {
			if (rule.getChildren() == null || rule.getChildren().size() == 0)
				throw new UnusualConditionException("Tried to create terminal dotted rule in wrong constructor: "+rule);
			dot = 0;
			hsh = new Hash(rule.hashCode());
			hsh = hsh.sag(new Hash(dot));
			r = rule;
			// rule weight has wrong sign
			score = -rule.getWeight();
			
		}
		// initial (non terminal) rule
		public DottedRule(GrammarRule rule, double statescore) throws UnusualConditionException {
			if (rule.getChildren() == null || rule.getChildren().size() == 0)
				throw new UnusualConditionException("Tried to create terminal dotted rule in wrong constructor: "+rule);
			dot = 1;
			hsh = new Hash(rule.hashCode());
			hsh = hsh.sag(new Hash(dot));
			r = rule;
			// rule weight has wrong sign
			score = statescore-rule.getWeight();
			
		}
		// TODO: make this a generic times!
		// rule from previous rule and state
		public DottedRule(DottedRule last, double statescore) throws UnusualConditionException {
			if (last.isDone())
				throw new UnusualConditionException("Tried to advance done rule "+last);
			r = last.r;
			score = last.score + statescore;
			dot = last.dot+1;
			hsh = last.getHash().sag(new Hash(dot));
		}
	}

	private class Deriv implements AgendaItem {
		private GrammarRule r;
		private Vector<Integer> d;
		private double score;
		private Hash hsh;
		Deriv(GrammarRule rule, Vector<Integer> deriv,  double s) { 
			r= rule; d = deriv; 
			score = s;
			hsh = new Hash();
			hsh = hsh.sag(new Hash(r.hashCode()));
			for (int i: d)
				hsh = hsh.sag(new Hash(i));
		}


		public String toString() { return r+":"+d+":"+score;}
		public boolean equals(Object o) {
			if (o instanceof Deriv) {
				Deriv e = (Deriv)o;
				return (r.equals(e.r) && d.equals(e.d));
			}
			return false;	
		}
		public int hashCode() {
			return  hsh.bag(Integer.MAX_VALUE);
		}
		public double getScore() { return score; }
		public Vector<Integer> getPositions() { return d; }
		public void setScore(double s) { score = s; }
		public GrammarRule getRule() { return r; }
	}
	// relies on BS iterator returning sorted rules
	// no for loops on rules!
	// fs-free!
	// k = upper bound for returning -- don't make more derivs per state than this
	// goal = state we want a deriv for
	// parseOnly = if true, don't add outsides or derivs
	private Deriv lazyKbas(int k, Symbol goal, boolean parseOnly) throws UnusualConditionException {
		boolean debug = false;
		boolean popdebug = false;
		boolean tempDebug = false;
		// should we use td heuristic?
		boolean usetd = true;
		// should we use external heuristic?
		boolean useExternal = false;
		// should we report monotonicity violations?
		boolean reportMono = false;
		
		// did we empty the queue?
		boolean didEmpty = false;
		if (debug) Debug.debug(debug, "Goal is "+goal);
		// do-once initialization
		if (pq == null) {
			pq = new BinaryHeapPriorityQueue<AgendaItem>();
			bounds = new HashMap<Symbol, Double>();
			hstates = new HashMap<Symbol,HState>();
			ostates = new HashMap<Symbol,OState>();
			// items as they're built -- lets us change the back pointers
			items = new HashMap<Symbol, BackPointerState>();

			
			// waiting dotted rules, indexed by what they're waiting for
			dottedRules = new HashMap<Symbol, Vector<DottedRule>>();
			

			// potential outside rules that haven't had all their children's insides yet 
			// indexed by what they're waiting for
			incompleteOutsideRules = new HashMap<Symbol, Vector<GrammarRule>> ();
			// potential outside rules that have all their children but not their true outside yet
			// indexed by top state
			waitingOutsideRules = new HashMap<Symbol, Vector<GrammarRule>> ();

			incompleteDerivs = new HashMap<Symbol, Vector<Deriv>>();
			// one-best scores
			obscores = new HashMap<Symbol, Double>();
			outsidescores = new HashMap<Symbol, Double>() ;
			obrules = new HashMap<Symbol, GrammarRule>();
			adjacencies = new HashMap<GrammarRule, HashSet<Vector<Integer>>>();
			derivs = new HashMap<Symbol, Vector<Deriv>>(); 
			
			// initialize agen with top hstate
			if (debug) Debug.debug(debug, "About to create a new HState out of "+getStartState());
			hstates.put(getStartState(), new HState(getStartState()));
			hspush=hspop=ipush=ipop=opush=opop=dpush=dpop=0;
			double initheur = useExternal ? getHeuristics(getStartState()) : 0.0;
			pq.add(hstates.get(getStartState()), initheur);
			hspush++;
			stopBound = 0;
			touchcount = 0;
		}
	
		
		
		while (!pq.isEmpty()) {
			double ph = pq.getPriority();
			
			AgendaItem theitem = pq.removeFirst();
			// monotonicity check
			double thresh = 1E-12;
			if (reportMono && ph - stopBound > thresh)
				Debug.prettyDebug("Monotonicity in one-best heap violated: saw "+ph+" after "+stopBound);
			stopBound = ph;
			
			// top-down heuristic case
			if (theitem instanceof HState) {
				hspop++;
				double p = ph;
				HState tds = (HState)theitem;
				Symbol s = tds.getSym();
				if (debug) Debug.debug(popdebug || debug, "Popped t-d heuristic "+tds+" with priority "+p);
				bounds.put(s, usetd ? p : 0);
	
				// we shouldn't pop t-d heuristic after b-u one best
				if (obscores.containsKey(s))
					throw new UnusualConditionException("Got t-d heuristic for "+s+" after one-best");
				// add in the HSTateIterator for this state
				HStateRuleIterator hsri = new HStateRuleIterator(s, getBSIter(s));
				// priority is heuristic cost of above plus rule weight (which is negative)
				if (hsri.hasNext()) {
					double hsripri = p-hsri.peek();
					if (hsripri > Double.NEGATIVE_INFINITY) {
						if (debug) Debug.debug(debug, "Relaxing t-d heuristic iterator with priority "+hsripri);
						if (!pq.relaxPriority(hsri, hsripri))
							throw new UnusualConditionException("Couldn't add t-d iterator for "+s+" to queue");
					}
				}
				else {
					if (debug) Debug.debug(debug, "No next element for "+s+" so discarding");
				}
			}
			// next rule in top-down heuristic search
			else if (theitem instanceof HStateRuleIterator) {
				HStateRuleIterator hsri = (HStateRuleIterator) theitem;
				Symbol s= hsri.getSym();
//				if (debug) Debug.debug(debug, "Popping t-d heuristic rule for "+s);
				GrammarRule g = hsri.next();
				rdisc++;
				if (debug) Debug.debug(debug, "Popped t-d heuristic rule for "+s+": "+g);
				// put the iterator back if it still has rules
				if (hsri.hasNext()) {
					// priority is heuristic cost of above plus rule weight (which is negative)
					double hsripri = bounds.get(s)-hsri.peek();
					if (debug) Debug.debug(debug, "Relaxing t-d heuristic iterator with priority "+hsripri);
					if (!pq.relaxPriority(hsri, hsripri))
						throw new UnusualConditionException("Couldn't add t-d iterator for "+s+" to queue");
				}
				// process the rule
				

				if (g.touch())
					touchcount++;
				// add in single b-u element if we're at terminus
				
				if (g.getChildren().size() == 0) {
					//if (debug) Debug.debug(debug, "After "+touchcount+" rules, initializing bottom-up");
					//buinit = true;
					if (!obscores.containsKey(g.getState())) {
						BackPointerState item = null;
						double cost = -g.getWeight();
						if (items.containsKey(s)) {
							item = items.get(s);
							//								if (debug) Debug.debug(debug, "Getting already seen item for "+g+": "+item);
						}
						else {
							item = new BackPointerState(s, g, cost);
							items.put(s, item);
							//								if (debug) Debug.debug(debug, "Created new item for "+g+": "+item);
						}
						double priority = cost;
						// we always have the outside cost of s -- we added it to get to this iterator
						// terminal items should be real -- but they might have previously been fake, so get 'em out!
						priority+= bounds.get(s);

						ipush++;
						if (pq.relaxPriority(item, priority)) {
							if (debug) Debug.debug(debug, "Relaxed b-u item "+item+" with priority "+priority);
							item.setRule(g);
							item.setScore(cost);
						}
					}
					// propagate new outside states and derivs -- no inside as this is term
					if (outsidescores.containsKey(s)) {
						propagateOutsideAndSeedDeriv(g, -g.getWeight(), k);
					}
					// also make the waiting rule for outside propagation if need be
					else {
						if (!waitingOutsideRules.containsKey(s))
							waitingOutsideRules.put(s, new Vector<GrammarRule>());
						waitingOutsideRules.get(s).add(g);					
					}
					
				}
				else {
					
					// avoid using this rule if it has dead-state descendents
					// checking for a dead state can be costly up front (may have to find a whole path
					// for each state) but prevents too many rules from existing
					
//					if (!hasPath(g)) {
//						deadrulecount++;
//						continue;
//					}
					
					// start to build b-u object that may need to wait for some inside costs				
					// build dotted rule with as many children as have been seen
					// also propagate heuristic outside cost downward
					DottedRule dr = new DottedRule(g);
					boolean hitIncomplete = false;
					
					for (Symbol c : g.getChildren()) {
						if (!hitIncomplete && obscores.containsKey(c)) {
							if (debug) Debug.debug(debug, "Seen "+c+" so advancing "+dr);
							dr = new DottedRule(dr, obscores.get(c));
						}
						else
							hitIncomplete = true;
						

						if (bounds.containsKey(c)) {
							//						if (debug) Debug.debug(debug, "Already popped "+c+" so not re-considering");
							continue;
						}
						double weight = usetd ? bounds.get(g.getState())-g.getWeight() : 0;
						if (useExternal) {
							double newWeight = getHeuristics(c);
//							if (tempDebug) Debug.debug(tempDebug, "Replacing heuristic of "+weight+" for "+c+" with external "+newWeight);
							weight = newWeight;
						}
						
						if (!hstates.containsKey(c))
							hstates.put(c, new HState(c));

						hspush++;
						if (pq.relaxPriority(hstates.get(c), weight)) {
							if (debug) Debug.debug(debug, "Relaxed t-d heuristic priority of "+c+" to "+weight+" using "+g);						
						}
					}

					// either add the b-u state if all children have been covered and state is unseen
					// add the outside prop if state is seen and outside of state is seen
					// add the dotted rule if not all children have been covered.
					double drpriority = dr.getScore()+bounds.get(s);
					if (dr.isDone()) {
						if (debug) Debug.debug(debug, "Completed "+dr+" so making item");
						if (!obscores.containsKey(s)) {
							BackPointerState item = null;
							double cost = dr.getScore();
							if (items.containsKey(s)) {
								item = items.get(s);
								//								if (debug) Debug.debug(debug, "Getting already seen item for "+g+": "+item);
							}
							else {
								item = new BackPointerState(s, g, cost);
								items.put(s, item);
								//								if (debug) Debug.debug(debug, "Created new item for "+g+": "+item);
							}
							ipush++;
							if (pq.relaxPriority(item, drpriority)) {
								if (debug) Debug.debug(debug, "Relaxed b-u item "+item+" with priority "+drpriority);
								item.setRule(g);
								item.setScore(cost);
							}
						}
						// propagate new outside states and derivs
						if (outsidescores.containsKey(s)) {
							propagateOutsideAndSeedDeriv(g, dr.getScore(), k);
						}
						// also make the waiting rule for outside propagation if need be
						else {
							if (!waitingOutsideRules.containsKey(s))
								waitingOutsideRules.put(s, new Vector<GrammarRule>());
							waitingOutsideRules.get(s).add(g);					
						}
					}
					else {
						if (debug) Debug.debug(debug, "Relaxing b-u rule "+dr+" with priority "+drpriority);
						if (!pq.relaxPriority(dr, drpriority))
							throw new UnusualConditionException("Weird: couldn't relax "+dr+" with "+drpriority);
					}
				}
			}
			// bottom-up rule case (subcase of bottom-up state)
			else if (theitem instanceof DottedRule) {
				DottedRule olddr = (DottedRule)theitem;
				Symbol s = olddr.getNext();
				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped b-u rule "+olddr+" with priority "+ph);
				// used to not bother if we've already locked the state
				// but now we might need this rule for exploring exact outside costs
//				if (obscores.containsKey(olddr.getRule().getState()))
//					continue;
				
				// If the rule's state exists, advance it. 
				if (obscores.containsKey(s)) {
					if (debug) Debug.debug(debug, "Combining "+olddr+" with extant state "+s);
					DottedRule dr = new DottedRule(olddr, obscores.get(s));
					// if it's complete and we haven't locked the state, add a new b-u state
					// if it's complete, propagate new outside states (as needed) or save to wait for exact outside
					if (dr.isDone()) {
						GrammarRule g = dr.getRule();
						if (!obscores.containsKey(g.getState())) {
							double cost = dr.getScore();;
							double priority = cost+bounds.get(g.getState());
							// adding new b-u inside states
							BackPointerState item = null;
							if (items.containsKey(g.getState())) {
								item = items.get(g.getState());
								//							if (debug) Debug.debug(debug, "Getting already seen item for "+g+": "+item);
							}
							else {
								item = new BackPointerState(g.getState(), g, cost);
								items.put(g.getState(), item);
								//							if (debug) Debug.debug(debug, "Created new item for "+g+": "+item);
							}
							ipush++;
							if (pq.relaxPriority(item, priority)) {
								item.setRule(g);
								item.setScore(cost);
								if (debug) Debug.debug(debug, "Relaxed  b-u item "+g.getState()+" with priority "+priority);
							}
						}
						// propagate new outside states and derivs
						if (outsidescores.containsKey(g.getState())) {
							propagateOutsideAndSeedDeriv(g, dr.getScore(), k);
						}
						// save in waiting rules
						else {
							if (debug) Debug.debug(debug, "No true outside for "+g.getState()+" yet, so archiving "+g);
							// also make the waiting rule for outside propagation
							if (!waitingOutsideRules.containsKey(g.getState()))
								waitingOutsideRules.put(g.getState(), new Vector<GrammarRule>());
							waitingOutsideRules.get(g.getState()).add(g);			
						}
					}
					// if it's not complete, add the dotted rule to the pq (we know it's got an outside cost)
					else {
						if (debug) Debug.debug(debug, "Adding incomplete dotted rule "+dr);
						if (!pq.relaxPriority(dr, dr.getScore()+bounds.get(dr.getRule().getState()))) {
							throw new UnusualConditionException("Couldn't add "+dr+" to pq!");
						}
					}
				}
				// If the rule's state doesn't exist, put it in the waiting list
				else {
					if (!dottedRules.containsKey(s)) {
						dottedRules.put(s, new Vector<DottedRule>());
					}
					if (debug) Debug.debug(debug, "Archiving "+olddr);
					dottedRules.get(s).add(olddr);
				}
			}
			
			// bottom-up state case
			else if (theitem instanceof BackPointerState) {
				ipop++;
				BackPointerState bps = (BackPointerState)theitem;
				Symbol s = bps.getSym();
				double p = bps.getScore();

				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped b-u state "+bps+" with priority "+ph+" and inside cost "+p);

				// instead of derivs, add in one bests
				obrules.put(s, bps.getRule());
				obscores.put(s, p);
				// once the end is reached, enqueue the first OState
				if (!parseOnly && s == getStartState()) {
					if (!ostates.containsKey(s))
						ostates.put(s, new OState(s, 0));
					opush++;
					if (pq.relaxPriority(ostates.get(s), p)) {
						if (debug) Debug.debug(debug, "Relaxed outside priority of top state "+s+" to priority "+p);								
					}
				}
				// possibly build outside states
				if (!parseOnly && incompleteOutsideRules.containsKey(s)) {
					Vector<GrammarRule> incRules = incompleteOutsideRules.remove(s);
					for (GrammarRule g : incRules) {
						
						boolean isGood = true;
						double baseCost = -g.getWeight();
						if (debug) Debug.debug(debug, "Attempting to build outside cost for deferred rule "+g);
						for (Symbol c : g.getChildren()) {
							if (!obscores.containsKey(c)) {
								if (debug) Debug.debug(debug, "No inside cost for "+c+" so saving for later");
								isGood = false;
								if (!incompleteOutsideRules.containsKey(c)) {
									incompleteOutsideRules.put(c, new Vector<GrammarRule>());
								}
								incompleteOutsideRules.get(c).add(g);
								break;
							}
							baseCost += obscores.get(c);
						}
						if (isGood)
							propagateOutsideAndSeedDeriv(g, baseCost, k);
						
					}
				}
				
				// get rules from the waiting list and remove them
				if (dottedRules.containsKey(s)) {
					if (debug) Debug.debug(debug, dottedRules.get(s).size()+" waiting rules");
					for (DottedRule olddr : dottedRules.remove(s)) {
						
						DottedRule dr = new DottedRule(olddr, obscores.get(s));
						// if it's complete, add a new b-u state
						// if outside of top is done, propagate outsides
						// otherwise, it's a waiting rule
						if (dr.isDone()) {
							GrammarRule g = dr.getRule();
							// don't bother if we've already locked the state
							if (!obscores.containsKey(g.getState())) {

								double cost = dr.getScore();;
								
								double priority = cost+bounds.get(g.getState());

								BackPointerState item = null;
								if (items.containsKey(g.getState())) {
									item = items.get(g.getState());
									//							if (debug) Debug.debug(debug, "Getting already seen item for "+g+": "+item);
								}
								else {
									item = new BackPointerState(g.getState(), g, cost);
									items.put(g.getState(), item);
									//							if (debug) Debug.debug(debug, "Created new item for "+g+": "+item);
								}
								ipush++;
								if (pq.relaxPriority(item, priority)) {
									item.setRule(g);
									item.setScore(cost);
									if (debug) Debug.debug(debug, "Relaxed  b-u item "+g.getState()+" with priority "+priority);
								}
							}
							// propagate new outside states
							if (!parseOnly) {
								if (outsidescores.containsKey(g.getState())) {
									propagateOutsideAndSeedDeriv(g, dr.getScore(), k);
								}
								// save in waiting rules
								else {
									// also make the waiting rule for outside propagation
									if (!waitingOutsideRules.containsKey(g.getState()))
										waitingOutsideRules.put(g.getState(), new Vector<GrammarRule>());
									waitingOutsideRules.get(g.getState()).add(g);			
								}
							}
							
						}
						// if it's not complete, add the dotted rule to the pq 
						else {
							if (debug) Debug.debug(debug, "Adding incomplete dotted rule "+dr);
							if (!pq.relaxPriority(dr, dr.getScore()+bounds.get(dr.getRule().getState()))) {
								throw new UnusualConditionException("Couldn't add "+dr+" to pq!");
							}

						}
					}
				}
			}
			
			// top-down REAL outside scores -- clear out all waiting rules
			else if (theitem instanceof OState) {
				opop++;
				OState os = (OState)theitem;
				Symbol s = os.getSym();
				double p = os.getScore();
				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped outside state "+s+" with priority "+ph+" and cost "+p);

				outsidescores.put(s, p);
				if (waitingOutsideRules.containsKey(s)) {
					for (GrammarRule g : waitingOutsideRules.remove(s)) {
						double priority = p-g.getWeight();
						if (g.getChildren().size() == 0) {
							Deriv d = new Deriv(g, new Vector<Integer>(), -g.getWeight());
							if (debug) Debug.debug(debug, "Adding base deriv object "+d+" with priority"+priority);
							dpush++;
							pq.relaxPriority(d, priority);
						}
						else {
							double baseCost = -g.getWeight();
							
							for (Symbol c : g.getChildren()) {
								baseCost += obscores.get(c);
							}
							propagateOutsideAndSeedDeriv(g, baseCost, k);
						}
					}
				}
			}
			// deriv case
			// this is the next best derivation of the rule's state
			// save the derivation, then:
			// if this is the FIRST derivation of a state, use FS and start building a deriv
			// check the incomplete derivs queue for derivs seeking this deriv. advance them and
			// enqueue if finished
			// create all the +1 versions of this deriv (check to avoid double-building). If possible,
			// add them. Otherwise, save them
			else if (theitem instanceof Deriv) {
				dpop++;
				Deriv d = (Deriv)theitem;
				double p = d.getScore();
				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped deriv "+d+" with priority "+ph+" and cost "+p);
				Symbol state = d.getRule().getState();
				if (!derivs.containsKey(state)) {
					if (debug) Debug.debug(debug, "Popped FIRST deriv for "+state);
					derivs.put(state, new Vector<Deriv>());
				}

				derivs.get(state).add(d);
			
	
					
				// check incomplete derivs of this state
				// move them or add them
				if (incompleteDerivs.containsKey(state)) {
					Vector<Deriv> incDerivs = incompleteDerivs.remove(state);
					for (Deriv newd : incDerivs) {
						// cycle avoidance -- no more than k derivs per state
						if (derivs.containsKey(newd.getRule().getState()) && derivs.get(newd.getRule().getState()).size() >= k)
							continue;
						if (debug) Debug.debug(debug, "Attempting to complete deferred deriv "+newd);
						boolean isGood = true;
						double cost = -newd.getRule().getWeight();
						Vector<Integer> positions = newd.getPositions();
						Vector<Symbol> children = newd.getRule().getChildren();
						for (int i = 0; i < children.size(); i++) {
							Symbol c = children.get(i);
							int pos = positions.get(i);
							if (!derivs.containsKey(c) || derivs.get(c).size() <= pos) {
								if (debug) Debug.debug(debug, "No "+pos+" deriv for "+c+" so saving for later");
								isGood = false;
								if (c.equals(state))
									throw new UnusualConditionException("Couldn't complete "+c+" while in its completion frame!");
								if (!incompleteDerivs.containsKey(c)) {
									incompleteDerivs.put(c, new Vector<Deriv>());
								}
								incompleteDerivs.get(c).add(newd);
								break;
							}
							cost += derivs.get(c).get(pos).getScore();
						}
						if (!isGood)
							continue;
						// add deriv to queue
						newd.setScore(cost);

						double priority = cost + outsidescores.get(newd.getRule().getState());
						dpush++;
						if (pq.relaxPriority(newd, priority))
							if (debug) Debug.debug(debug, "Relaxed deriv priority of deferred deriv "+newd+" to "+priority+"(outside of "+newd.getRule().getState()+", which is "+outsidescores.get(newd.getRule().getState())+") and cost to "+cost);														

					}
				}
				
				// cycle avoidance -- no more than k derivs per state
				if (derivs.containsKey(d.getRule().getState()) && derivs.get(d.getRule().getState()).size() >= k)
					continue;
				
				// create +1 versions of this deriv
				// enqueue them if possible or archive them if not
				// we only need to check the element we change to know if it's deferred or not
				Vector<Integer> positions = d.getPositions();
				Vector<Symbol> children = d.getRule().getChildren();
				for (int i = 0; i < children.size(); i++) {
					Symbol c = children.get(i);
					
					Vector<Integer> newpos = new Vector<Integer>(positions);
					newpos.set(i, newpos.get(i)+1);
					int pos = newpos.get(i);
					// check adjacencies
					if (adjacencies.get(d.getRule()).contains(newpos)) {
						if (debug) Debug.debug(debug, "Already saw "+newpos+" for "+d.getRule());														
						continue;
					}
					adjacencies.get(d.getRule()).add(newpos);
					Deriv newd = new Deriv(d.getRule(), newpos, 0);
					if (!derivs.containsKey(c) || derivs.get(c).size() <= pos) {
						if (debug) Debug.debug(debug, "No "+pos+" deriv for "+c+" so saving for later");
						if (!incompleteDerivs.containsKey(c)) {
							incompleteDerivs.put(c, new Vector<Deriv>());
						}
						incompleteDerivs.get(c).add(newd);
						continue;
					}
					else {
						double cost = d.getScore()-derivs.get(c).get(pos-1).getScore()+derivs.get(c).get(pos).getScore();
						double priority = cost + outsidescores.get(newd.getRule().getState());
						newd.setScore(cost);
						if (debug) Debug.debug(debug, "We have "+pos+" deriv for "+c+" so enqueuing adajcency now with score "+cost+" and priority "+priority);
						dpush++;
						if (pq.relaxPriority(newd, priority))
							if (debug) Debug.debug(debug, "Relaxed priority");
							
					}
					
				}
				// stop if deriv of goal is popped
				if (state.equals(goal)) {
					if (debug) Debug.debug(debug, "Returning goal deriv");
					return d;
				}
				
			}
			
		}
		// empty queue -- no derivations left
		if (tempDebug || debug) Debug.debug(tempDebug || debug, "Empty queue");
		// reclaim objects
		if (!didEmpty) {
			didEmpty = true;
			bounds = null;
			hstates = null;
			ostates = null;
			items = null;
			dottedRules = null;
			incompleteOutsideRules = null;
			waitingOutsideRules = null;
			incompleteDerivs = null;
			obscores = null;
			outsidescores = null;
			obrules = null;
			adjacencies = null;
			derivs = null;
			heurMap = null;
		}
		return null;

	}


	
	// relies on FS iterator returning sorted rules
	// and externally provided heuristics
	// no for loops on rules!
	// k = upper bound for returning -- don't make more derivs per state than this
	// goal = state we want a deriv for
	// special check for RGOTF Grammar -- goal state is interpreted as left of a pair,
	// and when checking for goal we only check for the left match
	// other special things for RGOTF grammar -- wildcard state combinations added at discovery time
	// duplicate rule creation squashed
	private Deriv bsFreeLazyKbas(int k, Symbol goal, boolean isRGOTF) throws UnusualConditionException {
		final boolean debug = false;
		final boolean popdebug = false;
		final boolean tempDebug = false;
		final boolean drDebug = true;
		
		// should we use external heuristic?
		final boolean useExternal = false;
		// should we report monotonicity violations?
		final boolean reportMono = false;
		

		// do-once initialization
		if (pq == null) {
			pq = new BinaryHeapPriorityQueue<AgendaItem>();
			
			ostates = new HashMap<Symbol,OState>();
			// items as they're built -- lets us change the back pointers
			items = new HashMap<Symbol, BackPointerState>();


			// waiting dotted rules, indexed by what they're waiting for
			dottedRules = new HashMap<Symbol, Vector<DottedRule>>();


			// potential outside rules that haven't had all their children's insides yet 
			// indexed by what they're waiting for
			incompleteOutsideRules = new HashMap<Symbol, Vector<GrammarRule>> ();
			// potential outside rules that have all their children but not their true outside yet
			// indexed by top state
			waitingOutsideRules = new HashMap<Symbol, Vector<GrammarRule>> ();

			incompleteDerivs = new HashMap<Symbol, Vector<Deriv>>();
			// one-best scores
			obscores = new HashMap<Symbol, Double>();
			outsidescores = new HashMap<Symbol, Double>() ;
			obrules = new HashMap<Symbol, GrammarRule>();
			adjacencies = new HashMap<GrammarRule, HashSet<Vector<Integer>>>();
			derivs = new HashMap<Symbol, Vector<Deriv>>(); 
			dottedRulesBuiltCount = dottedRulesQueuedCount = dottedRulesWaitingCount = 0;
			if (isRGOTF)
				duplicateRules = new HashMap<Symbol, HashSet<GrammarRule>>();

			// initialize agen with lexical fs iterator

			IStateRuleIterator isri = new IStateRuleIterator(getLexFSIter());
			if (!isri.hasNext()) {
				if (!isRGOTF)
					throw new UnusualConditionException("No rules for non-RGOTF lexical fs iterator");
			}
			else {
				hspush=hspop=ipush=ipop=opush=opop=dpush=dpop=0;

				pq.add(isri, 0.0);
				hspush++;
				stopBound = 0;
				touchcount = 0;
			}
		}


		
		while (!pq.isEmpty()) {
			double ph = pq.getPriority();
			
			AgendaItem theitem = pq.removeFirst();
			// monotonicity check
			double thresh = 1E-12;
			if (reportMono && ph - stopBound > thresh)
				Debug.prettyDebug("Monotonicity in one-best heap violated: saw "+ph+" after "+stopBound);
			stopBound = ph;
			
			
			// next rule in bottom-up heuristic search
			if (theitem instanceof IStateRuleIterator) {
				IStateRuleIterator isri = (IStateRuleIterator) theitem;
				Symbol leafs= isri.getSym();
				
//				if (debug) Debug.debug(debug, "Popping b-u heuristic rule for "+s);
				GrammarRule g = isri.next();
				rdisc++;
				Symbol parent = g.getState();
				if (debug || tempDebug) Debug.debug(debug || tempDebug, "Popped b-u heuristic rule for "+leafs+": "+g);
			
				// process the rule
				if (g.touch())
					touchcount++;
				else {
					if (isRGOTF)
						if (debug) Debug.debug(debug, "Already seen "+g+" in RGOTFGrammar");
				}
				// put the iterator back if it still has rules
				if (isri.hasNext()) {
					// priority is inside cost of below (if available) plus rule weight (which is negative)
					double isripri = -isri.peek();
					if (!leafs.equals(Symbol.getEpsilon()))
						isripri += obscores.get(leafs);
					if (debug) Debug.debug(debug, "Relaxing b-u heuristic iterator with priority "+isripri);
					if (!pq.relaxPriority(isri, isripri))
						throw new UnusualConditionException("Couldn't add b-u iterator for "+leafs+" to queue");
				}
				// ignore it if it's RGOTF and duplicate
				if (isRGOTF) {
					if (!duplicateRules.containsKey(parent))
						duplicateRules.put(parent, new HashSet<GrammarRule>());
					if (duplicateRules.get(parent).contains(g)) {
						if (debug) Debug.debug(debug, "Skipping duplicate RGOTF rule "+g);
						continue;
					}
					else {
						if (debug) Debug.debug(debug, "Adding RGOTF rule "+g+" in "+this);
					duplicateRules.get(parent).add(g);
					}
				}
			
				// add in single b-u element if we're at terminus
				
				if (g.getChildren().size() == 0) {
					//if (debug) Debug.debug(debug, "After "+touchcount+" rules, initializing bottom-up");
					//buinit = true;
					
					if (!obscores.containsKey(parent)) {
						BackPointerState item = null;
						double cost = -g.getWeight();
						if (items.containsKey(parent)) {
							item = items.get(parent);
							//								if (debug) Debug.debug(debug, "Getting already seen item for "+g+": "+item);
						}
						else {
							item = new BackPointerState(parent, g, cost);
							items.put(parent, item);
							//								if (debug) Debug.debug(debug, "Created new item for "+g+": "+item);
						}
						double priority = cost;
						if (useExternal)
							priority+= getHeuristics(parent);

						ipush++;
						if (pq.relaxPriority(item, priority)) {
							if (debug) Debug.debug(debug, "Relaxed b-u item "+item+" with priority "+priority);
							item.setRule(g);
							item.setScore(cost);
						}
					}
					// propagate new outside states and derivs -- no inside as this is term
					if (outsidescores.containsKey(parent)) {
						propagateOutsideAndSeedDeriv(g, -g.getWeight(), k);
					}
					// also make the waiting rule for outside propagation if need be
					else {
						if (!waitingOutsideRules.containsKey(parent))
							waitingOutsideRules.put(parent, new Vector<GrammarRule>());
						waitingOutsideRules.get(parent).add(g);					
					}
					
				}
				else {
					
					// avoid using this rule if it has dead-state descendents
					// checking for a dead state can be costly up front (may have to find a whole path
					// for each state) but prevents too many rules from existing
					
//					if (!hasPath(g)) {
//						deadrulecount++;
//						continue;
//					}
					
					// start to build b-u object that may need to wait for some inside costs				
					// build dotted rule with as many children as have been seen
					
					// once we're done priority will be added, the heuristic of the top state
					// early exit now if the heuristic is infinite
					
					double drpriority = useExternal ? getHeuristics(g.getState()) : 0;
					if (drpriority <= Double.NEGATIVE_INFINITY) {
						if (debug) Debug.debug(debug, "Heuristic is infinite, so abandoning "+g);
						continue;
					}
					
					DottedRule dr = new DottedRule(g);
					dottedRulesBuiltCount++;
					if (drDebug && dottedRulesBuiltCount % 10000 == 0) {
						Debug.debug(drDebug, dottedRulesBuiltCount+" built, "+dottedRulesQueuedCount+" queued, "+dottedRulesWaitingCount+" waiting dotted rules");
					}
					
					boolean hitIncomplete = false;
					
					for (Symbol c : g.getChildren()) {
						// possibly discover special lex rules
						if (isRGOTF) {
							if (!obscores.containsKey(c)) {
								GrammarRule lexRule = ((RGOTFGrammar)this).getLexRule(c);
								if (lexRule != null) {
									if (debug) Debug.debug(debug, "Discovered special lex rules "+lexRule);
									obscores.put(c, 0.0);
									items.put(c, new BackPointerState(c, lexRule, 0));
									waitingOutsideRules.put(c, new Vector<GrammarRule>());
									waitingOutsideRules.get(c).add(lexRule);
								}
							}
						}
						if (!hitIncomplete && obscores.containsKey(c)) {
							if (debug) Debug.debug(debug, "Seen "+c+" so advancing "+dr);
							dr = new DottedRule(dr, obscores.get(c));
						}
						else
							hitIncomplete = true;
					}

					// either add the b-u state if all children have been covered and state is unseen
					// add the outside prop if state is seen and outside of state is seen
					// add the dotted rule if not all children have been covered.
					
					drpriority += dr.getScore();
					if (dr.isDone()) {
						if (debug) Debug.debug(debug, "Completed "+dr+" so making item");
						if (!obscores.containsKey(g.getState())) {
							BackPointerState item = null;
							double cost = dr.getScore();
							if (items.containsKey(parent)) {
								item = items.get(parent);
								//								if (debug) Debug.debug(debug, "Getting already seen item for "+g+": "+item);
							}
							else {
								item = new BackPointerState(parent, g, cost);
								items.put(parent, item);
								//								if (debug) Debug.debug(debug, "Created new item for "+g+": "+item);
							}
							ipush++;
							if (pq.relaxPriority(item, drpriority)) {
								if (debug) Debug.debug(debug, "Relaxed b-u item "+item+" with priority "+drpriority);
								item.setRule(g);
								item.setScore(cost);
							}
						}
						// propagate new outside states and derivs
						if (outsidescores.containsKey(parent)) {
							propagateOutsideAndSeedDeriv(g, dr.getScore(), k);
						}
						// also make the waiting rule for outside propagation if need be
						else {
							if (!waitingOutsideRules.containsKey(parent))
								waitingOutsideRules.put(parent, new Vector<GrammarRule>());
							waitingOutsideRules.get(parent).add(g);					
						}
					}
					else {
						if (debug) Debug.debug(debug, "Relaxing b-u rule "+dr+" with priority "+drpriority);
						if (!pq.relaxPriority(dr, drpriority))
							throw new UnusualConditionException("Weird: couldn't relax "+dr+" with "+drpriority);
						dottedRulesQueuedCount++;
					}
				}
			}
			// bottom-up rule case (subcase of bottom-up state)
			else if (theitem instanceof DottedRule) {
				DottedRule olddr = (DottedRule)theitem;
				dottedRulesQueuedCount--;
				Symbol s = olddr.getNext();
				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped b-u rule "+olddr+" with priority "+ph);
				// used to not bother if we've already locked the state
				// but now we might need this rule for exploring exact outside costs
//				if (obscores.containsKey(olddr.getRule().getState()))
//					continue;
				
				if (isRGOTF) {
					if (!obscores.containsKey(s)) {
						GrammarRule lexRule = ((RGOTFGrammar)this).getLexRule(s);
						if (lexRule != null) {
							if (debug) Debug.debug(debug, "Discovered special lex rules "+lexRule);
							obscores.put(s, 0.0);
							items.put(s, new BackPointerState(s, lexRule, 0));
							waitingOutsideRules.put(s, new Vector<GrammarRule>());
							waitingOutsideRules.get(s).add(lexRule);
						}
					}
				}
				// If the rule's state exists, advance it. 
				if (obscores.containsKey(s)) {
					if (debug) Debug.debug(debug, "Combining "+olddr+" with extant state "+s);
					DottedRule dr = new DottedRule(olddr, obscores.get(s));
					// if it's complete and we haven't locked the state, add a new b-u state
					// if it's complete, propagate new outside states (as needed) or save to wait for exact outside
					if (dr.isDone()) {
						GrammarRule g = dr.getRule();
						if (!obscores.containsKey(g.getState())) {
							double cost = dr.getScore();;
							double priority = cost;
							if (useExternal)
								priority += getHeuristics(g.getState());
							// adding new b-u inside states
							BackPointerState item = null;
							if (items.containsKey(g.getState())) {
								item = items.get(g.getState());
								//							if (debug) Debug.debug(debug, "Getting already seen item for "+g+": "+item);
							}
							else {
								item = new BackPointerState(g.getState(), g, cost);
								items.put(g.getState(), item);
								//							if (debug) Debug.debug(debug, "Created new item for "+g+": "+item);
							}
							ipush++;
							if (pq.relaxPriority(item, priority)) {
								item.setRule(g);
								item.setScore(cost);
								if (debug) Debug.debug(debug, "Relaxed  b-u item "+g.getState()+" with priority "+priority);
							}
						}
						// propagate new outside states and derivs
						if (outsidescores.containsKey(g.getState())) {
							propagateOutsideAndSeedDeriv(g, dr.getScore(), k);
						}
						// save in waiting rules
						else {
							if (debug) Debug.debug(debug, "No true outside for "+g.getState()+" yet, so archiving "+g);
							// also make the waiting rule for outside propagation
							if (!waitingOutsideRules.containsKey(g.getState()))
								waitingOutsideRules.put(g.getState(), new Vector<GrammarRule>());
							waitingOutsideRules.get(g.getState()).add(g);			
						}
					}
					// if it's not complete, add the dotted rule to the pq (we know it's got an outside cost)
					else {
						if (debug) Debug.debug(debug, "Adding incomplete dotted rule "+dr);
						double drpriority = dr.getScore();
						if (useExternal)
							drpriority += getHeuristics(dr.getRule().getState());
						if (!pq.relaxPriority(dr, drpriority)) {
							throw new UnusualConditionException("Couldn't add "+dr+" to pq!");
						}
						dottedRulesQueuedCount++;
					}
				}
				// If the rule's state doesn't exist, put it in the waiting list
				else {
					if (!dottedRules.containsKey(s)) {
						dottedRules.put(s, new Vector<DottedRule>());
					}
					if (debug) Debug.debug(debug, "Archiving "+olddr);
					dottedRules.get(s).add(olddr);
					dottedRulesWaitingCount++;
				}
			}
			
			// bottom-up state case -- set off a left-corner iterator
			else if (theitem instanceof BackPointerState) {
				ipop++;
				BackPointerState bps = (BackPointerState)theitem;
				Symbol s = bps.getSym();
				double p = bps.getScore();

				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped b-u state "+bps+" with priority "+ph+" and inside cost "+p);

				// instead of derivs, add in one bests
				obrules.put(s, bps.getRule());
				obscores.put(s, p);
				
				IStateRuleIterator isri;
				if (isRGOTF) {
					if (s instanceof PairSymbol) {
						PairSymbol pairS = (PairSymbol)s;
						// only ready to move up if we're a proper filtering symbol. otherwise it's eps
						if (pairS.getRight() instanceof FilteringPairSymbol && ((FilteringPairSymbol)pairS.getRight()).getFilter() != FilteringPairSymbol.FILTER.REAL) {
							Debug.prettyDebug("WARNING: in Grammar, stuff depending on REAL filter");
							if (debug) Debug.debug(debug, "Getting RGOTF rules for eps rule");
							isri = new IStateRuleIterator(s, getFSIter(s, 0));
						}
						else {
							// figure out the appropriate position for this state by matching to the original tree
							Symbol checkState = pairS.getLeft();
							TransducerRightTree node = ((RGOTFGrammar)this).state2Node(checkState);
							TransducerRightTree parent = node.parent;
							int pos = -1;
							if (parent == null)
								pos = 0;
							else {
								for (int i = 0; i < parent.getNumChildren(); i++) {
									if (parent.getChild(i).equals(node)) {
										pos = i;
										break;
									}
								}
							}
							if (debug) Debug.debug(debug, "Getting RGOTF rules for "+s+" in pos "+pos);
							isri = new IStateRuleIterator(s, getFSIter(s, pos));
						}
					}
					else {
						throw new UnusualConditionException("Tried to get rules of non-pair state "+s+" in RGOTF");
					}
				}
				else {
					isri = new IStateRuleIterator(s, getFSIter(s, 0));
				}
				// priority is heuristic cost of above plus rule weight (which is negative)
				if (isri.hasNext()) {
					double isripri = p-isri.peek();
					if (isripri > Double.NEGATIVE_INFINITY) {
						if (debug) Debug.debug(debug, "Relaxing b-u heuristic iterator with priority "+isripri);
						if (!pq.relaxPriority(isri, isripri))
							throw new UnusualConditionException("Couldn't add b-u iterator for "+s+" to queue");
					}
				}
				else {
					if (debug) Debug.debug(debug, "No next element for "+s+" so discarding");
				}
				
				// once the end is reached, enqueue the first OState
				// special top checking in RGOTF-land
				boolean isAtTop = false;
				if (isRGOTF) {
					if (s instanceof PairSymbol) {
						Symbol checkState = ((PairSymbol)s).getLeft();
						if (checkState.equals(goal))
							isAtTop = true;
					}
				}
				else {
					if (s == getStartState())
						isAtTop = true;
				}
				if (isAtTop)
				{
					if (!ostates.containsKey(s))
						ostates.put(s, new OState(s, 0));
					opush++;
					if (pq.relaxPriority(ostates.get(s), p)) {
						if (debug) Debug.debug(debug, "Relaxed outside priority of top state "+s+" to priority "+p);								
					}
				}
				// possibly build outside states
				if (incompleteOutsideRules.containsKey(s)) {
					Vector<GrammarRule> incRules = incompleteOutsideRules.remove(s);
					for (GrammarRule g : incRules) {
						
						boolean isGood = true;
						double baseCost = -g.getWeight();
						if (debug) Debug.debug(debug, "Attempting to build outside cost for deferred rule "+g);
						for (Symbol c : g.getChildren()) {
							// possibly discover special lex rules
							if (isRGOTF) {
								if (!obscores.containsKey(c)) {
									GrammarRule lexRule = ((RGOTFGrammar)this).getLexRule(c);
									if (lexRule != null) {
										if (debug) Debug.debug(debug, "Discovered special lex rules "+lexRule);
										obscores.put(c, 0.0);
										items.put(c, new BackPointerState(c, lexRule, 0));
										waitingOutsideRules.put(c, new Vector<GrammarRule>());
										waitingOutsideRules.get(c).add(lexRule);
									}
								}
							}
							if (!obscores.containsKey(c)) {
								if (debug) Debug.debug(debug, "No inside cost for "+c+" so saving for later");
								isGood = false;
								if (!incompleteOutsideRules.containsKey(c)) {
									incompleteOutsideRules.put(c, new Vector<GrammarRule>());
								}
								incompleteOutsideRules.get(c).add(g);
								break;
							}
							baseCost += obscores.get(c);
						}
						if (isGood)
							propagateOutsideAndSeedDeriv(g, baseCost, k);
						
					}
				}
				
				// get rules from the waiting list and remove them
				if (dottedRules.containsKey(s)) {
					if (debug) Debug.debug(debug, dottedRules.get(s).size()+" waiting rules");
					for (DottedRule olddr : dottedRules.remove(s)) {
						dottedRulesWaitingCount--;
						DottedRule dr = new DottedRule(olddr, obscores.get(s));
						// if it's complete, add a new b-u state
						// if outside of top is done, propagate outsides
						// otherwise, it's a waiting rule
						if (dr.isDone()) {
							GrammarRule g = dr.getRule();
							// don't bother if we've already locked the state
							if (!obscores.containsKey(g.getState())) {

								double cost = dr.getScore();;
								
								double priority = cost;
								if (useExternal)
									priority += getHeuristics(g.getState());
								BackPointerState item = null;
								if (items.containsKey(g.getState())) {
									item = items.get(g.getState());
									//							if (debug) Debug.debug(debug, "Getting already seen item for "+g+": "+item);
								}
								else {
									item = new BackPointerState(g.getState(), g, cost);
									items.put(g.getState(), item);
									//							if (debug) Debug.debug(debug, "Created new item for "+g+": "+item);
								}
								ipush++;
								if (pq.relaxPriority(item, priority)) {
									item.setRule(g);
									item.setScore(cost);
									if (debug) Debug.debug(debug, "Relaxed  b-u item "+g.getState()+" with priority "+priority);
								}
							}
							// propagate new outside states
							if (outsidescores.containsKey(g.getState())) {
								propagateOutsideAndSeedDeriv(g, dr.getScore(), k);
							}
							// save in waiting rules
							else {
								// also make the waiting rule for outside propagation
								if (!waitingOutsideRules.containsKey(g.getState()))
									waitingOutsideRules.put(g.getState(), new Vector<GrammarRule>());
								waitingOutsideRules.get(g.getState()).add(g);			
							}
							
						}
						// if it's not complete, add the dotted rule to the pq 
						else {
							if (debug) Debug.debug(debug, "Adding incomplete dotted rule "+dr);
							double priority = dr.getScore();
							if (useExternal)
								priority += getHeuristics(dr.getRule().getState());
							if (!pq.relaxPriority(dr, priority)) {
								throw new UnusualConditionException("Couldn't add "+dr+" to pq!");
							}
							dottedRulesQueuedCount++;
							
						}
					}
				}
			}
			
			// top-down REAL outside scores -- clear out all waiting rules
			else if (theitem instanceof OState) {
				opop++;
				OState os = (OState)theitem;
				Symbol s = os.getSym();
				double p = os.getScore();
				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped outside state "+s+" with priority "+ph+" and cost "+p);

				outsidescores.put(s, p);
				if (waitingOutsideRules.containsKey(s)) {
					for (GrammarRule g : waitingOutsideRules.remove(s)) {
						double priority = p-g.getWeight();
						if (g.getChildren().size() == 0) {
							Deriv d = new Deriv(g, new Vector<Integer>(), -g.getWeight());
							if (debug) Debug.debug(debug, "Adding base deriv object "+d+" with priority"+priority);
							dpush++;
							pq.relaxPriority(d, priority);
						}
						else {
							double baseCost = -g.getWeight();
							
							for (Symbol c : g.getChildren()) {
								baseCost += obscores.get(c);
							}
							propagateOutsideAndSeedDeriv(g, baseCost, k);
						}
					}
				}
			}
			// deriv case
			// this is the next best derivation of the rule's state
			// save the derivation, then:
			// if this is the FIRST derivation of a state, use FS and start building a deriv
			// check the incomplete derivs queue for derivs seeking this deriv. advance them and
			// enqueue if finished
			// create all the +1 versions of this deriv (check to avoid double-building). If possible,
			// add them. Otherwise, save them
			else if (theitem instanceof Deriv) {
				dpop++;
				Deriv d = (Deriv)theitem;
				double p = d.getScore();
				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped deriv "+d+" with priority "+ph+" and cost "+p);
				Symbol state = d.getRule().getState();
				if (!derivs.containsKey(state)) {
					if (debug) Debug.debug(debug, "Popped FIRST deriv for "+state);
					derivs.put(state, new Vector<Deriv>());
				}

				derivs.get(state).add(d);
			
	
					
				// check incomplete derivs of this state
				// move them or add them
				if (incompleteDerivs.containsKey(state)) {
					Vector<Deriv> incDerivs = incompleteDerivs.remove(state);
					for (Deriv newd : incDerivs) {
						// cycle avoidance -- no more than k derivs per state
						if (derivs.containsKey(newd.getRule().getState()) && derivs.get(newd.getRule().getState()).size() >= k)
							continue;
						if (debug) Debug.debug(debug, "Attempting to complete deferred deriv "+newd);
						boolean isGood = true;
						double cost = -newd.getRule().getWeight();
						Vector<Integer> positions = newd.getPositions();
						Vector<Symbol> children = newd.getRule().getChildren();
						for (int i = 0; i < children.size(); i++) {
							Symbol c = children.get(i);
							int pos = positions.get(i);
							if (!derivs.containsKey(c) || derivs.get(c).size() <= pos) {
								if (debug) Debug.debug(debug, "No "+pos+" deriv for "+c+" so saving for later");
								isGood = false;
								if (c.equals(state))
									throw new UnusualConditionException("Couldn't complete "+c+" while in its completion frame!");
								if (!incompleteDerivs.containsKey(c)) {
									incompleteDerivs.put(c, new Vector<Deriv>());
								}
								incompleteDerivs.get(c).add(newd);
								break;
							}
							cost += derivs.get(c).get(pos).getScore();
						}
						if (!isGood)
							continue;
						// add deriv to queue
						newd.setScore(cost);

						double priority = cost + outsidescores.get(newd.getRule().getState());
						dpush++;
						if (pq.relaxPriority(newd, priority))
							if (debug) Debug.debug(debug, "Relaxed deriv priority of deferred deriv "+newd+" to "+priority+"(outside of "+newd.getRule().getState()+", which is "+outsidescores.get(newd.getRule().getState())+") and cost to "+cost);														

					}
				}
				
				// cycle avoidance -- no more than k derivs per state
				if (derivs.containsKey(d.getRule().getState()) && derivs.get(d.getRule().getState()).size() >= k)
					continue;
				
				// create +1 versions of this deriv
				// enqueue them if possible or archive them if not
				// we only need to check the element we change to know if it's deferred or not
				Vector<Integer> positions = d.getPositions();
				Vector<Symbol> children = d.getRule().getChildren();
				for (int i = 0; i < children.size(); i++) {
					Symbol c = children.get(i);
					
					Vector<Integer> newpos = new Vector<Integer>(positions);
					newpos.set(i, newpos.get(i)+1);
					int pos = newpos.get(i);
					// check adjacencies
					if (adjacencies.get(d.getRule()).contains(newpos)) {
						if (debug) Debug.debug(debug, "Already saw "+newpos+" for "+d.getRule());														
						continue;
					}
					adjacencies.get(d.getRule()).add(newpos);
					Deriv newd = new Deriv(d.getRule(), newpos, 0);
					if (!derivs.containsKey(c) || derivs.get(c).size() <= pos) {
						if (debug) Debug.debug(debug, "No "+pos+" deriv for "+c+" so saving for later");
						if (!incompleteDerivs.containsKey(c)) {
							incompleteDerivs.put(c, new Vector<Deriv>());
						}
						incompleteDerivs.get(c).add(newd);
						continue;
					}
					else {
						double cost = d.getScore()-derivs.get(c).get(pos-1).getScore()+derivs.get(c).get(pos).getScore();
						double priority = cost + outsidescores.get(newd.getRule().getState());
						newd.setScore(cost);
						if (debug) Debug.debug(debug, "We have "+pos+" deriv for "+c+" so enqueuing adajcency now with score "+cost+" and priority "+priority);
						dpush++;
						if (pq.relaxPriority(newd, priority))
							if (debug) Debug.debug(debug, "Relaxed priority");
							
					}
					
				}
				// stop if deriv of goal is popped
				// if special RGOTF case, just check left of state
				if (isRGOTF) {
					if (state instanceof PairSymbol) {
						Symbol checkState = ((PairSymbol)state).getLeft();
						if (debug) Debug.debug(debug, state+" is check State "+checkState+" and "+((PairSymbol)state).getRight()+"; checking against "+goal);
						if (checkState.equals(goal))
							return d;
						else {
							if (debug) Debug.debug(debug, checkState+" not equal to "+goal);
						}
					}
					else {
						if (debug) Debug.debug(debug, state+" is not PairSymbol");
					}
				}
				else {
					if (state.equals(goal)) {
						Debug.prettyDebug("Touch count at return time: "+touchcount);
						return d;
					}
				}
				
			}
			else {
				throw new UnusualConditionException("Weird type "+theitem.getClass().getCanonicalName());
			}
			
		}
		// empty queue -- no derivations left
		if (tempDebug || debug) Debug.debug(tempDebug || debug, "Empty queue");
		return null;

	}

	
	
	// specific kbas for binarized parsing -- check both sides of a state

	// relies on FS iterator returning sorted rules
	// and externally provided heuristics
	// no for loops on rules!
	// k = upper bound for returning -- don't make more derivs per state than this
	// goal = state we want a deriv for
	// and when checking for goal we only check for the left match
	// if parseOnly, no derivs or outsides calculated
	private Deriv bsFreeBinarizedLazyKbas(int k, boolean parseOnly) throws UnusualConditionException {
		final boolean debug = false;
		final boolean popdebug = false;
		final boolean tempDebug = false;
		final boolean drDebug = false;
		
		// should we use external heuristic?
		final boolean useExternal = false;
		// should we report monotonicity violations?
		final boolean reportMono = false;
		

		
		// do-once initialization
		if (pq == null) {
			pq = new BinaryHeapPriorityQueue<AgendaItem>();
			
			ostates = new HashMap<Symbol,OState>();
			// items as they're built -- lets us change the back pointers
			items = new HashMap<Symbol, BackPointerState>();


			// waiting dotted rules, indexed by what they're waiting for
			dottedRules = new HashMap<Symbol, Vector<DottedRule>>();


			// potential outside rules that haven't had all their children's insides yet 
			// indexed by what they're waiting for
			incompleteOutsideRules = new HashMap<Symbol, Vector<GrammarRule>> ();
			// potential outside rules that have all their children but not their true outside yet
			// indexed by top state
			waitingOutsideRules = new HashMap<Symbol, Vector<GrammarRule>> ();

			incompleteDerivs = new HashMap<Symbol, Vector<Deriv>>();
			// one-best scores
			obscores = new HashMap<Symbol, Double>();
			outsidescores = new HashMap<Symbol, Double>() ;
			obrules = new HashMap<Symbol, GrammarRule>();
			adjacencies = new HashMap<GrammarRule, HashSet<Vector<Integer>>>();
			derivs = new HashMap<Symbol, Vector<Deriv>>(); 
			dottedRulesBuiltCount = dottedRulesQueuedCount = dottedRulesWaitingCount = 0;
			

			// initialize agen with lexical fs iterator

			IStateRuleIterator isri = new IStateRuleIterator(getLexFSIter());
			if (!isri.hasNext()) {
					throw new UnusualConditionException("No rules for lexical fs iterator");
			}
			else {
				hspush=hspop=ipush=ipop=opush=opop=dpush=dpop=0;

				pq.add(isri, 0.0);
				hspush++;
				stopBound = 0;
				touchcount = 0;
			}
		}


		
		while (!pq.isEmpty()) {
			double ph = pq.getPriority();
			
			AgendaItem theitem = pq.removeFirst();
			// monotonicity check
			double thresh = 1E-12;
			if (reportMono && ph - stopBound > thresh)
				Debug.prettyDebug("Monotonicity in one-best heap violated: saw "+ph+" after "+stopBound);
			stopBound = ph;
			
			
			// next rule in bottom-up heuristic search
			if (theitem instanceof IStateRuleIterator) {
				IStateRuleIterator isri = (IStateRuleIterator) theitem;
				Symbol leafs= isri.getSym();
				
//				if (debug) Debug.debug(debug, "Popping b-u heuristic rule for "+s);
				GrammarRule g = isri.next();
				rdisc++;
				Symbol parent = g.getState();
				if (debug || tempDebug) Debug.debug(debug || tempDebug, "Popped b-u heuristic rule for "+leafs+": "+g);
			
				
				
				// process the rule
				if (g.touch())
					touchcount++;
				
				// put the iterator back if it still has rules
				if (isri.hasNext()) {
					// priority is inside cost of below (if available) plus rule weight (which is negative)
					double isripri = -isri.peek();
					if (!leafs.equals(Symbol.getEpsilon()))
						isripri += obscores.get(leafs);
					if (debug) Debug.debug(debug, "Relaxing b-u heuristic iterator with priority "+isripri);
					pq.relaxPriority(isri, isripri);
//					if (!pq.relaxPriority(isri, isripri))
//						throw new UnusualConditionException("Couldn't add b-u iterator for "+leafs+" to queue");
				}
				
				
			
				// add in single b-u element if we're at terminus
				
				if (g.getChildren().size() == 0) {
					//if (debug) Debug.debug(debug, "After "+touchcount+" rules, initializing bottom-up");
					//buinit = true;
					
					if (!obscores.containsKey(parent)) {
						BackPointerState item = null;
						double cost = -g.getWeight();
						if (items.containsKey(parent)) {
							item = items.get(parent);
							//								if (debug) Debug.debug(debug, "Getting already seen item for "+g+": "+item);
						}
						else {
							item = new BackPointerState(parent, g, cost);
							items.put(parent, item);
							//								if (debug) Debug.debug(debug, "Created new item for "+g+": "+item);
						}
						double priority = cost;
						if (useExternal)
							priority+= getHeuristics(parent);

						ipush++;
						if (pq.relaxPriority(item, priority)) {
							if (debug) Debug.debug(debug, "Relaxed b-u item "+item+" with priority "+priority);
							item.setRule(g);
							item.setScore(cost);
						}
					}
					if (!parseOnly) {
						// propagate new outside states and derivs -- no inside as this is term
						if (outsidescores.containsKey(parent)) {
							propagateOutsideAndSeedDeriv(g, -g.getWeight(), k);
						}
						// also make the waiting rule for outside propagation if need be
						else {
							if (!waitingOutsideRules.containsKey(parent))
								waitingOutsideRules.put(parent, new Vector<GrammarRule>());
							waitingOutsideRules.get(parent).add(g);					
						}
					}

				}
				else {
					
					// avoid using this rule if it has dead-state descendents
					// checking for a dead state can be costly up front (may have to find a whole path
					// for each state) but prevents too many rules from existing
					
//					if (!hasPath(g)) {
//						deadrulecount++;
//						continue;
//					}
					
					// start to build b-u object that may need to wait for some inside costs				
					// build dotted rule with as many children as have been seen
					
					// once we're done priority will be added, the heuristic of the top state
					// early exit now if the heuristic is infinite
					
					double drpriority = useExternal ? getHeuristics(g.getState()) : 0;
					if (drpriority <= Double.NEGATIVE_INFINITY) {
						if (debug) Debug.debug(debug, "Heuristic is infinite, so abandoning "+g);
						continue;
					}
					
					DottedRule dr = new DottedRule(g);
					dottedRulesBuiltCount++;
					if (drDebug && dottedRulesBuiltCount % 10000 == 0) {
						Debug.debug(drDebug, dottedRulesBuiltCount+" built, "+dottedRulesQueuedCount+" queued, "+dottedRulesWaitingCount+" waiting dotted rules");
					}
					
					boolean hitIncomplete = false;
					
					for (Symbol c : g.getChildren()) {
						if (!hitIncomplete && obscores.containsKey(c)) {
							if (debug) Debug.debug(debug, "Seen "+c+" so advancing "+dr);
							dr = new DottedRule(dr, obscores.get(c));
						}
						else
							hitIncomplete = true;
					}

					// either add the b-u state if all children have been covered and state is unseen
					// add the outside prop if state is seen and outside of state is seen
					// add the dotted rule if not all children have been covered.
					
					drpriority += dr.getScore();
					if (dr.isDone()) {
						if (debug) Debug.debug(debug, "Completed "+dr+" so making item");
						if (!obscores.containsKey(g.getState())) {
							BackPointerState item = null;
							double cost = dr.getScore();
							if (items.containsKey(parent)) {
								item = items.get(parent);
								//								if (debug) Debug.debug(debug, "Getting already seen item for "+g+": "+item);
							}
							else {
								item = new BackPointerState(parent, g, cost);
								items.put(parent, item);
								//								if (debug) Debug.debug(debug, "Created new item for "+g+": "+item);
							}
							ipush++;
							if (pq.relaxPriority(item, drpriority)) {
								if (debug) Debug.debug(debug, "Relaxed b-u item "+item+" with priority "+drpriority);
								item.setRule(g);
								item.setScore(cost);
							}
						}
						if (!parseOnly) {
							// propagate new outside states and derivs
							if (outsidescores.containsKey(parent)) {
								propagateOutsideAndSeedDeriv(g, dr.getScore(), k);
							}
							// also make the waiting rule for outside propagation if need be
							else {
								if (!waitingOutsideRules.containsKey(parent))
									waitingOutsideRules.put(parent, new Vector<GrammarRule>());
								waitingOutsideRules.get(parent).add(g);					
							}
						}
					}
					else {
						if (debug) Debug.debug(debug, "Relaxing incomplete b-u rule "+dr+" with priority "+drpriority);
						if (!pq.relaxPriority(dr, drpriority))
							throw new UnusualConditionException("Weird: couldn't relax "+dr+" with "+drpriority);
						dottedRulesQueuedCount++;
					}
				}
			}
			// bottom-up rule case (subcase of bottom-up state)
			else if (theitem instanceof DottedRule) {
				Debug.debug(true, "WARNING: Shouldn't see dotted rules in binarized TSOTF");
				DottedRule olddr = (DottedRule)theitem;
				dottedRulesQueuedCount--;
				Symbol s = olddr.getNext();
				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped b-u rule "+olddr+" with priority "+ph);
				// used to not bother if we've already locked the state
				// but now we might need this rule for exploring exact outside costs
//				if (obscores.containsKey(olddr.getRule().getState()))
//					continue;
				
				
				// If the rule's state exists, advance it. 
				if (obscores.containsKey(s)) {
					if (debug) Debug.debug(debug, "Combining "+olddr+" with extant state "+s);
					DottedRule dr = new DottedRule(olddr, obscores.get(s));
					// if it's complete and we haven't locked the state, add a new b-u state
					// if it's complete, propagate new outside states (as needed) or save to wait for exact outside
					if (dr.isDone()) {
						GrammarRule g = dr.getRule();
						if (!obscores.containsKey(g.getState())) {
							double cost = dr.getScore();;
							double priority = cost;
							if (useExternal)
								priority += getHeuristics(g.getState());
							// adding new b-u inside states
							BackPointerState item = null;
							if (items.containsKey(g.getState())) {
								item = items.get(g.getState());
								//							if (debug) Debug.debug(debug, "Getting already seen item for "+g+": "+item);
							}
							else {
								item = new BackPointerState(g.getState(), g, cost);
								items.put(g.getState(), item);
								//							if (debug) Debug.debug(debug, "Created new item for "+g+": "+item);
							}
							ipush++;
							if (pq.relaxPriority(item, priority)) {
								item.setRule(g);
								item.setScore(cost);
								if (debug) Debug.debug(debug, "Relaxed  b-u item "+g.getState()+" with priority "+priority);
							}
						}
						// propagate new outside states and derivs
						if (outsidescores.containsKey(g.getState())) {
							propagateOutsideAndSeedDeriv(g, dr.getScore(), k);
						}
						// save in waiting rules
						else {
							if (debug) Debug.debug(debug, "No true outside for "+g.getState()+" yet, so archiving "+g);
							// also make the waiting rule for outside propagation
							if (!waitingOutsideRules.containsKey(g.getState()))
								waitingOutsideRules.put(g.getState(), new Vector<GrammarRule>());
							waitingOutsideRules.get(g.getState()).add(g);			
						}
					}
					// if it's not complete, add the dotted rule to the pq (we know it's got an outside cost)
					else {
						if (debug) Debug.debug(debug, "Adding incomplete dotted rule "+dr);
						double drpriority = dr.getScore();
						if (useExternal)
							drpriority += getHeuristics(dr.getRule().getState());
						if (!pq.relaxPriority(dr, drpriority)) {
							throw new UnusualConditionException("Couldn't add "+dr+" to pq!");
						}
						dottedRulesQueuedCount++;
					}
				}
				// If the rule's state doesn't exist, put it in the waiting list
				else {
					if (!dottedRules.containsKey(s)) {
						dottedRules.put(s, new Vector<DottedRule>());
					}
					if (debug) Debug.debug(debug, "Archiving "+olddr);
					dottedRules.get(s).add(olddr);
					dottedRulesWaitingCount++;
				}
			}
			
			// bottom-up state case -- set off a rule iterator
			else if (theitem instanceof BackPointerState) {
				ipop++;
				BackPointerState bps = (BackPointerState)theitem;
				Symbol s = bps.getSym();
				double p = bps.getScore();

				
				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped b-u state "+bps+" with priority "+ph+" and inside cost "+p);
				
				
				
				if (!injectState(s, p)) {
					if (popdebug || debug) Debug.debug(popdebug || debug, "Eliminated b-u state "+bps);
					continue;
				}
				// instead of derivs, add in one bests
				obrules.put(s, bps.getRule());
				obscores.put(s, p);
				
				IStateRuleIterator isri;
				
				isri = new IStateRuleIterator(s, getFSIter(s));
				
				// priority is heuristic cost of above plus rule weight (which is negative)
				if (isri.hasNext()) {
					double isripri = p-isri.peek();
					if (isripri > Double.NEGATIVE_INFINITY) {
						if (debug) Debug.debug(debug, "Relaxing b-u heuristic iterator with priority "+isripri);
						if (!pq.relaxPriority(isri, isripri))
							throw new UnusualConditionException("Couldn't add b-u iterator for "+s+" to queue");
					}
				}
				else {
					if (debug) Debug.debug(debug, "No next element for "+s+" so discarding");
				}
				
				// once the end is reached, enqueue the first OState
				
				if (!parseOnly && isStartState(s))
				{
					if (!ostates.containsKey(s))
						ostates.put(s, new OState(s, 0));
					opush++;
					if (pq.relaxPriority(ostates.get(s), p)) {
						if (debug) Debug.debug(debug, "Relaxed outside priority of top state "+s+" to priority "+p);								
					}
				}
				// possibly build outside states
				if (incompleteOutsideRules.containsKey(s)) {
					Vector<GrammarRule> incRules = incompleteOutsideRules.remove(s);
					for (GrammarRule g : incRules) {
						
						boolean isGood = true;
						double baseCost = -g.getWeight();
						if (debug) Debug.debug(debug, "Attempting to build outside cost for deferred rule "+g);
						for (Symbol c : g.getChildren()) {
							
							if (!obscores.containsKey(c)) {
								if (debug) Debug.debug(debug, "No inside cost for "+c+" so saving for later");
								isGood = false;
								if (!incompleteOutsideRules.containsKey(c)) {
									incompleteOutsideRules.put(c, new Vector<GrammarRule>());
								}
								incompleteOutsideRules.get(c).add(g);
								break;
							}
							baseCost += obscores.get(c);
						}
						if (isGood)
							propagateOutsideAndSeedDeriv(g, baseCost, k);
						
					}
				}
				
				// get rules from the waiting list and remove them
				if (dottedRules.containsKey(s)) {
					if (debug) Debug.debug(debug, dottedRules.get(s).size()+" waiting rules");
					for (DottedRule olddr : dottedRules.remove(s)) {
						dottedRulesWaitingCount--;
						DottedRule dr = new DottedRule(olddr, obscores.get(s));
						// if it's complete, add a new b-u state
						// if outside of top is done, propagate outsides
						// otherwise, it's a waiting rule
						if (dr.isDone()) {
							GrammarRule g = dr.getRule();
							// don't bother if we've already locked the state
							if (!obscores.containsKey(g.getState())) {

								double cost = dr.getScore();;
								
								double priority = cost;
								if (useExternal)
									priority += getHeuristics(g.getState());
								BackPointerState item = null;
								if (items.containsKey(g.getState())) {
									item = items.get(g.getState());
									//							if (debug) Debug.debug(debug, "Getting already seen item for "+g+": "+item);
								}
								else {
									item = new BackPointerState(g.getState(), g, cost);
									items.put(g.getState(), item);
									//							if (debug) Debug.debug(debug, "Created new item for "+g+": "+item);
								}
								ipush++;
								if (pq.relaxPriority(item, priority)) {
									item.setRule(g);
									item.setScore(cost);
									if (debug) Debug.debug(debug, "Relaxed  b-u item "+g.getState()+" with priority "+priority);
								}
							}
							// propagate new outside states
							if (outsidescores.containsKey(g.getState())) {
								propagateOutsideAndSeedDeriv(g, dr.getScore(), k);
							}
							// save in waiting rules
							else {
								// also make the waiting rule for outside propagation
								if (!waitingOutsideRules.containsKey(g.getState()))
									waitingOutsideRules.put(g.getState(), new Vector<GrammarRule>());
								waitingOutsideRules.get(g.getState()).add(g);			
							}
							
						}
						// if it's not complete, add the dotted rule to the pq 
						else {
							if (debug) Debug.debug(debug, "Adding incomplete dotted rule "+dr);
							double priority = dr.getScore();
							if (useExternal)
								priority += getHeuristics(dr.getRule().getState());
							if (!pq.relaxPriority(dr, priority)) {
								throw new UnusualConditionException("Couldn't add "+dr+" to pq!");
							}
							dottedRulesQueuedCount++;
							
						}
					}
				}
			}
			
			// top-down REAL outside scores -- clear out all waiting rules
			else if (theitem instanceof OState) {
				opop++;
				OState os = (OState)theitem;
				Symbol s = os.getSym();
				double p = os.getScore();
				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped outside state "+s+" with priority "+ph+" and cost "+p);

				outsidescores.put(s, p);
				if (waitingOutsideRules.containsKey(s)) {
					for (GrammarRule g : waitingOutsideRules.remove(s)) {
						double priority = p-g.getWeight();
						if (g.getChildren().size() == 0) {
							Deriv d = new Deriv(g, new Vector<Integer>(), -g.getWeight());
							if (debug) Debug.debug(debug, "Adding base deriv object "+d+" with priority"+priority);
							dpush++;
							pq.relaxPriority(d, priority);
						}
						else {
							double baseCost = -g.getWeight();
							
							for (Symbol c : g.getChildren()) {
								baseCost += obscores.get(c);
							}
							propagateOutsideAndSeedDeriv(g, baseCost, k);
						}
					}
				}
			}
			// deriv case
			// this is the next best derivation of the rule's state
			// save the derivation, then:
			// if this is the FIRST derivation of a state, use FS and start building a deriv
			// check the incomplete derivs queue for derivs seeking this deriv. advance them and
			// enqueue if finished
			// create all the +1 versions of this deriv (check to avoid double-building). If possible,
			// add them. Otherwise, save them
			else if (theitem instanceof Deriv) {
				dpop++;
				Deriv d = (Deriv)theitem;
				double p = d.getScore();
				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped deriv "+d+" with priority "+ph+" and cost "+p);
				Symbol state = d.getRule().getState();
				if (!derivs.containsKey(state)) {
					if (debug) Debug.debug(debug, "Popped FIRST deriv for "+state);
					derivs.put(state, new Vector<Deriv>());
				}

				derivs.get(state).add(d);
			
	
					
				// check incomplete derivs of this state
				// move them or add them
				if (incompleteDerivs.containsKey(state)) {
					Vector<Deriv> incDerivs = incompleteDerivs.remove(state);
					for (Deriv newd : incDerivs) {
						// cycle avoidance -- no more than k derivs per state
						if (derivs.containsKey(newd.getRule().getState()) && derivs.get(newd.getRule().getState()).size() >= k)
							continue;
						if (debug) Debug.debug(debug, "Attempting to complete deferred deriv "+newd);
						boolean isGood = true;
						double cost = -newd.getRule().getWeight();
						Vector<Integer> positions = newd.getPositions();
						Vector<Symbol> children = newd.getRule().getChildren();
						for (int i = 0; i < children.size(); i++) {
							Symbol c = children.get(i);
							int pos = positions.get(i);
							if (!derivs.containsKey(c) || derivs.get(c).size() <= pos) {
								if (debug) Debug.debug(debug, "No "+pos+" deriv for "+c+" so saving for later");
								isGood = false;
								if (c.equals(state))
									throw new UnusualConditionException("Couldn't complete "+c+" while in its completion frame!");
								if (!incompleteDerivs.containsKey(c)) {
									incompleteDerivs.put(c, new Vector<Deriv>());
								}
								incompleteDerivs.get(c).add(newd);
								break;
							}
							cost += derivs.get(c).get(pos).getScore();
						}
						if (!isGood)
							continue;
						// add deriv to queue
						newd.setScore(cost);

						double priority = cost + outsidescores.get(newd.getRule().getState());
						dpush++;
						if (pq.relaxPriority(newd, priority))
							if (debug) Debug.debug(debug, "Relaxed deriv priority of deferred deriv "+newd+" to "+priority+"(outside of "+newd.getRule().getState()+", which is "+outsidescores.get(newd.getRule().getState())+") and cost to "+cost);														

					}
				}
				
				// cycle avoidance -- no more than k derivs per state
				if (derivs.containsKey(d.getRule().getState()) && derivs.get(d.getRule().getState()).size() >= k)
					continue;
				
				// create +1 versions of this deriv
				// enqueue them if possible or archive them if not
				// we only need to check the element we change to know if it's deferred or not
				Vector<Integer> positions = d.getPositions();
				Vector<Symbol> children = d.getRule().getChildren();
				for (int i = 0; i < children.size(); i++) {
					Symbol c = children.get(i);
					
					Vector<Integer> newpos = new Vector<Integer>(positions);
					newpos.set(i, newpos.get(i)+1);
					int pos = newpos.get(i);
					// check adjacencies
					if (adjacencies.get(d.getRule()).contains(newpos)) {
						if (debug) Debug.debug(debug, "Already saw "+newpos+" for "+d.getRule());														
						continue;
					}
					adjacencies.get(d.getRule()).add(newpos);
					Deriv newd = new Deriv(d.getRule(), newpos, 0);
					if (!derivs.containsKey(c) || derivs.get(c).size() <= pos) {
						if (debug) Debug.debug(debug, "No "+pos+" deriv for "+c+" so saving for later");
						if (!incompleteDerivs.containsKey(c)) {
							incompleteDerivs.put(c, new Vector<Deriv>());
						}
						incompleteDerivs.get(c).add(newd);
						continue;
					}
					else {
						double cost = d.getScore()-derivs.get(c).get(pos-1).getScore()+derivs.get(c).get(pos).getScore();
						double priority = cost + outsidescores.get(newd.getRule().getState());
						newd.setScore(cost);
						if (debug) Debug.debug(debug, "We have "+pos+" deriv for "+c+" so enqueuing adajcency now with score "+cost+" and priority "+priority);
						dpush++;
						if (pq.relaxPriority(newd, priority))
							if (debug) Debug.debug(debug, "Relaxed priority");
							
					}
					
				}
				// stop if deriv of start state  is popped
				if (isStartState(state)) {
//					Debug.prettyDebug("Touch count at return time: "+touchcount);
					return d;
				}


			}
			else {
				throw new UnusualConditionException("Weird type "+theitem.getClass().getCanonicalName());
			}
			
		}
		// empty queue -- no derivations left
		if (tempDebug || debug) Debug.debug(tempDebug || debug, "Empty queue");
		return null;

	}
	
	// convenience method that is done several times in lazyKbas
	// given a rule that has the outside of the top and the inside of all bottoms
	// 1) propagate outside for each of the bottoms, if not yet found
	// 2) seed the initial (all 1-best) deriv of that rule as either incomplete (if not all lower derivs have been found)
	// or in the queue (if they have)
	// base cost is inside of all children plus rule weight. it gets modified for outside propagation
	// outside base cost adds in outside of top but subtracts inside of specific propagated child
	// deriv cost is strictly speaking deriv cost of children plus rule weight, but in the base case
	// this is equal to base cost
	private void propagateOutsideAndSeedDeriv(GrammarRule g, double baseCost, int k) throws UnusualConditionException{
		boolean debug = false;
		// add in outside item for each child
		// outside rule is complete so seed the deriv object here
		// deriv object's children might be complete
		
		// priority is inside of all children plus rule plus outside of top -- same for everything here
		double priority = baseCost+outsidescores.get(g.getState());
		// subtract inside of relevant state from base outside
		double baseOutside = priority;
		// baseCost is deriv cost
		
		Vector<Integer> derivvec = new Vector<Integer>();		
		Symbol lastsym = null;
		for (Symbol c : g.getChildren()) {
			derivvec.add(0);
			if (lastsym == null) {
				// find first deriv that doesn't exist
				if (!derivs.containsKey(c))
					lastsym = c;
			}

			if (outsidescores.containsKey(c))
				continue;
			double cost = baseOutside - obscores.get(c);

			if (!ostates.containsKey(c))
				ostates.put(c, new OState(c, cost));
			opush++;
			if (pq.relaxPriority(ostates.get(c), priority)) {
				ostates.get(c).setScore(cost);
				if (debug) Debug.debug(debug, "Relaxed outside priority of "+c+" to "+priority+" and cost to "+cost+" using "+g);								
			}
			else {
				if (debug) Debug.debug(debug, "Couldn't relax outside priority of "+c+" to "+priority);								
			}
		}
		// seed new deriv:
		// cycle avoidance -- no more than k derivs per state
		if (derivs.containsKey(g.getState()) && derivs.get(g.getState()).size() >= k)
			return;
		// set adjacencies
		if (!adjacencies.containsKey(g))
			adjacencies.put(g, new HashSet<Vector<Integer>>());
		if (adjacencies.get(g).contains(derivvec))
			return;
		adjacencies.get(g).add(derivvec);

		Deriv newDeriv = new Deriv(g, derivvec, baseCost);
		if (debug) Debug.debug(debug, "Created deriv "+newDeriv+" with "+g+"("+g.hashCode()+")");
		// either add the incomplete deriv to the appropriate spot
		// or add the complete deriv to the agenda
		if (lastsym == null) {			
			if (debug) Debug.debug(debug, "Adding completed deriv "+newDeriv+" with priority "+priority);
			dpush++;
			if (!pq.relaxPriority(newDeriv, priority))
				throw new UnusualConditionException("Weird: couldn't relax priority "+priority+" of "+newDeriv);
		}
		else {
			if (debug) Debug.debug(debug, "No 1 best for "+lastsym+" so saving top deriv of "+g+" for later");
			if (!incompleteDerivs.containsKey(lastsym))
				incompleteDerivs.put(lastsym, new Vector<Deriv>());
			incompleteDerivs.get(lastsym).add(newDeriv);
		}
	}
	
	
	// check a rule for having some path
	// a rule has a path if it is terminal or if its children all have paths
	private boolean hasPath(GrammarRule g) throws UnusualConditionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "checking hasPath for "+g);
		if (g.getChildren().size() == 0)
			return true;
		for (Symbol c : g.getChildren()) {
			if (bounds.containsKey(c))
				continue;
			if (debug) Debug.debug(debug, "Checking hasPath for "+c);
			if (!hasPath(c))
				return false;
		}
		if (debug) Debug.debug(debug, g+" is okay");
		return true;
	}
	// check a state for having some path
	// a state has a path if it has a terminal rule
	// a state does not have a path if it has no rule
	// a state has a path if it has a rule with a path
	// TODO: this might mess up in RGOTF!!!
	private static HashMap<Symbol, Boolean> pathTable;
	static {
		pathTable = new	HashMap<Symbol, Boolean> ();
	}
	private boolean hasPath(Symbol c) throws UnusualConditionException {
		if (!pathTable.containsKey(c)) {
			PIterator<GrammarRule> it = getBSIter(c);
			if (!it.hasNext()) 
				pathTable.put(c, false);
			else {
				while (true) {
					GrammarRule g = it.next();
					if (hasPath(g)) {
						pathTable.put(c, true);
						break;
					}
					if (!it.hasNext()) {
						pathTable.put(c, false);
						break;
					}
				}
			}
		}
		return pathTable.get(c);
	}
	
	// write an item as a tree
	private TreeItem getTree(Deriv d) {
		// get through epsilon rules
		while (d.getRule().getLabel() == null) {
//			System.out.println("Using "+d);
			//			Debug.prettyDebug("Skipping "+d);
			d = derivs.get(d.getRule().getChildren().get(0)).get(d.getPositions().get(0));
		}
//		System.out.println("Using "+d);
		TreeItem t = new TreeItem(d.getRule().getLabel());
	//	Debug.prettyDebug("Processing "+d);
		for (int i = 0; i < d.getPositions().size(); i++) {
			t.addChild(getTree(derivs.get(d.getRule().getChildren().get(i)).get(d.getPositions().get(i))));
		}
		return t;
	}
	
	// write an item as a vector of strings from a cfg deriv -- ignore labels when there are children
	//, allow epsilon, etc.
	
	private Vector<Symbol> getString(Deriv d) {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Using "+d);
		Vector<Symbol> v = new Vector<Symbol>();
		if (d.getRule().getChildren().size() > 0) {
			for (int i = 0; i < d.getPositions().size(); i++) {
				Symbol s = d.getRule().getChildren().get(i);
				if (derivs.containsKey(s))
					v.addAll(getString(derivs.get(s).get(d.getPositions().get(i))));
				else {
					Debug.prettyDebug("Warning: Treating presumed state "+s+" as a literal");
					v.add(s);
				}
			}
		}
		else {
			Symbol label = d.getRule().getLabel();
			if (label != null)
				v.add(label);
		}
		return v;
	}
	
	// get the next-best tree in the grammar
	public Pair<TreeItem, Pair<Double, Integer>> getNextTree(boolean td) throws UnusualConditionException {
		int lookahead = lastDeriv+5;
		Deriv d;
		if (td)
			d = lazyKbas(lookahead, getStartState(), false);
		else {
//			d = bsFreeLazyKbas(lookahead, getStartState(), false);
			d = bsFreeBinarizedLazyKbas(lookahead, false);
		}
//		Deriv d = bsFreeLazyKbas(lookahead, getStartState());
		if (d != null) {
			TreeItem t = getTree(d);
			lastDeriv++;
			return new Pair<TreeItem, Pair<Double, Integer>>(t, new Pair<Double, Integer>(-d.getScore(), touchcount));
		}
		else
			return null;
	}
	
	// generate everything from the heap
	public void exhaust(boolean td) throws UnusualConditionException {
		int lookahead = 5;
		while (true) {
			Deriv d;
			if (td) {
				d = lazyKbas(lookahead, getStartState(), true);
			}
			else {
				d = bsFreeBinarizedLazyKbas(lookahead, true);
			}
			if (d == null)
				break;
			lookahead++;
		}
	}
	
	// get next-best string in the grammar
	// first symbol in the vector is start state of the derivation -- this is used mostly by RGOTF
	public Pair<Vector<Symbol>, Double> getNextString(Symbol goal, boolean td, boolean isRGOTF) throws UnusualConditionException {
		int lookahead = lastDeriv+5;
//		Debug.prettyDebug("Getting next string of "+this);
		Deriv d;
		if (td)
			d = lazyKbas(lookahead, goal, false);
		else
			d = bsFreeLazyKbas(lookahead, goal, isRGOTF);
		
		if (d != null) {
			Vector<Symbol> v = getString(d);
			// ignore the tree index component if isRGOTF
			Symbol topState = d.getRule().getState();
			if (isRGOTF && topState instanceof PairSymbol)
				v.add(0, ((PairSymbol)topState).getRight());
			else
				v.add(0, topState);
			lastDeriv++;
			return new Pair<Vector<Symbol>, Double>(v, -d.getScore());
		}
		else
			return null;
	}
	
//	// get kth best string derivation of a particular symbol
//	// if it's been made or try to make up to k otherwise
//	// first symbol in the vector is start state of the derivation -- this is used mostly by RGOTF
//
//	public Pair<Vector<Symbol>, Double> getIndexedString(Symbol goal, int k, boolean td, boolean isRGOTF) throws UnusualConditionException {
//		boolean debug = false;
//		// initialize the table
//		if (strings == null)
//			strings = new Vector<Pair<Vector<Symbol>, Double>>();
//		// catch-up : make strings for each extant derivation not yet in a string
//		if (derivs != null && derivs.containsKey(getStartState())) {
//			int startIndex = strings.size();
//			int finishIndex = derivs.get(getStartState()).size();
//			if (finishIndex - startIndex > 0) {
//				if (debug) Debug.debug(debug, "Playing catch up for "+(finishIndex-startIndex)+" items from "+startIndex);
//				for (int i = startIndex; i < finishIndex; i++) {
//					Deriv d = derivs.get(getStartState()).get(i);
//					Vector<Symbol> v = getString(d);
//					Symbol topState = d.getRule().getState();
//					if (isRGOTF && topState instanceof PairSymbol)
//						v.add(0, ((PairSymbol)topState).getRight());
//					else
//						v.add(0, topState);
//					Pair<Vector<Symbol>, Double> ns = new Pair<Vector<Symbol>, Double>(v, -d.getScore());
//					if (debug )Debug.debug(debug, "Caught up to "+i+"th; Got "+ns.l()+" from "+d);
//					strings.add(ns);
//				}
//			}
//		}
//		int lookahead = k+5;
//		int buildcount = 0;
//		// always attempt to get one more
//		while (strings.size() <= k+1) {
//			buildcount++;
//			if (debug) Debug.debug(debug, "Attempting to derive "+strings.size()+"th");
//			Deriv d;
//			if (td)
//				d = lazyKbas(lookahead, getStartState());
//			else
//				d = bsFreeLazyKbas(lookahead, getStartState(), isRGOTF);
//			if (d == null) {
//				if (debug) Debug.debug(debug, "No more derivations");
//				break;
//			}
//			else {
//				Vector<Symbol> v = getString(d);
//				Symbol topState = d.getRule().getState();
//				if (isRGOTF && topState instanceof PairSymbol)
//					v.add(0, ((PairSymbol)topState).getRight());
//				else
//					v.add(0, topState);
//				Pair<Vector<Symbol>, Double> ns = new Pair<Vector<Symbol>, Double>(v, -d.getScore());
//				if (debug )Debug.debug(debug, "Built "+strings.size()+"th of "+this+"; Got "+ns.l()+" from "+d);
//				strings.add(ns);
//			}
//		}
//		if (debug) Debug.debug(debug, hashCode()+":Got "+k+" with buildcount of "+buildcount);
//
//		if (strings.size() <= k)
//			return null;
//		return strings.get(k);
//	}
//	
	// treat grammar as a string iterator -- with indexed string iterator, can revisit
	// strings without redoing work!
	public Iterator<Pair<Vector<Symbol>, Double>> stringIterator(Symbol goal, boolean td, boolean isRGOTF) throws UnusualConditionException {
		return new IndexedStringIterator(goal, td, isRGOTF);
//		return new StringIterator(goal, td, isRGOTF);
	}
	
	private class StringIterator implements Iterator<Pair<Vector<Symbol>, Double>> {
		private Pair<Vector<Symbol>, Double> next;
		private boolean topDown;
		private boolean isRGOTF;
		private Symbol goal;
		public StringIterator(Symbol g, boolean td, boolean isr) throws UnusualConditionException {
			topDown = td;
			isRGOTF = isr;
			goal = g;
			next = getNextString(goal, topDown, isRGOTF);
		}
		public boolean hasNext() {
			return next != null;
		}
		public Pair<Vector<Symbol>, Double> next() throws NoSuchElementException{
			if (next == null)
				throw new NoSuchElementException("Asked for next on empty StringIterator");
			Pair<Vector<Symbol>, Double> ret = next;
			try {
				next = getNextString(goal, topDown, isRGOTF);
			}
			catch (UnusualConditionException e) {
				throw new NoSuchElementException("While getting next string: "+e.getMessage());
			}
			return ret;
		}
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for StringIterator");
		}
	}
	
	private HashMap<Symbol, Vector<Pair<Vector<Symbol>, Double>>> indexedStrings;
	private HashMap<Symbol, StringIterator> stringIterators;
	private class IndexedStringIterator implements Iterator<Pair<Vector<Symbol>, Double>> {
		private boolean topDown;
		private boolean isRGOTF;
		private Symbol goal;
		private int next;
		private StringIterator iterator;
		private Vector<Pair<Vector<Symbol>, Double>> results;
		public IndexedStringIterator(Symbol g, boolean td, boolean isr) throws UnusualConditionException {
			next = 0;
			goal = g;
			topDown = td;
			isRGOTF = isr;
			boolean debug = false;
			if (stringIterators == null)
				stringIterators = new HashMap<Symbol, StringIterator> ();
			if (indexedStrings == null)
				indexedStrings = new HashMap<Symbol, Vector<Pair<Vector<Symbol>, Double>>> ();
			if (!stringIterators.containsKey(goal))
				stringIterators.put(goal, new StringIterator(goal, topDown, isRGOTF));
			else {
				if (debug) Debug.debug(debug, "Reusing string iterator for "+goal);
			}
			if (!indexedStrings.containsKey(goal))
				indexedStrings.put(goal, new Vector<Pair<Vector<Symbol>, Double>>());
			results = indexedStrings.get(goal);
			iterator = stringIterators.get(goal);
		}
		public boolean hasNext() {
			boolean ret;
			ret = (results.size() > next || (iterator != null && iterator.hasNext()));
			// kill iterator if it is still around
			if (!ret && iterator != null) {
				iterator = null;
				stringIterators.put(goal, null);
			}
			return ret;
		}
		public Pair<Vector<Symbol>, Double> next() throws NoSuchElementException {
			boolean debug = false;
			if (!hasNext())
				throw new NoSuchElementException("Asked for peek on empty Iterator");
			if (debug) Debug.debug(debug, "Looking for "+next+" of "+goal);
			if (results.size() <= next) {
				if (debug) Debug.debug(debug, "Results only has "+results.size());				
				results.add(iterator.next());
			}
			int nextnum = next;
			Pair<Vector<Symbol>, Double> res = results.get(next++);
			if (debug) Debug.debug(debug, "Returning result "+nextnum+" of "+(this.hashCode())+"; "+res);
			return res;
		}
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException("Didn't bother with remove for BsIndexedIterator");
		}
	}

	
	// TODO: revamp this more like the IndexedBSIter, etc.
	
	// works with parent strings table to return string form 
	// of a particular deriv for a particular state
//	private class IndexedStringIterator implements Iterator<Pair<Vector<Symbol>, Double>> {
//		private int next;
//		private boolean topDown;
//		private boolean isRGOTF;
//
//		public IndexedStringIterator(boolean td, boolean isr) throws UnusualConditionException {
//			topDown = td;
//			isRGOTF = isr;
//			next = 0;
//			getIndexedString(0, topDown, isRGOTF);			
//		}
//		public boolean hasNext() {
//			if (strings == null)
//				return false;
//			return strings.size() >= next+1;
//		}
//		public Pair<Vector<Symbol>, Double> next() throws NoSuchElementException{
//			if (strings.size() < next+1)
//				throw new NoSuchElementException("Asked for next on empty StringIterator");
//			Pair<Vector<Symbol>, Double> ret = null;
//			try {
//				ret = getIndexedString(next++, topDown, isRGOTF);
//			}
//			catch (UnusualConditionException e) {
//				throw new NoSuchElementException("While getting next string: "+e.getMessage());
//			}
//			return ret;
//		}
//		public void remove() throws UnsupportedOperationException {
//			throw new UnsupportedOperationException("Didn't bother with remove for StringIterator");
//		}
//	}

	
	// load in outside heuristics that may be FilteringPairSymbols or ProdSymbols
	// not very resilient -- just store as string->wgt
//	private HashMap<String, Double> heurMap;
//	private static Pattern heurPat = Pattern.compile("^(.*\\S)\\s*#\\s*(\\S.*\\S)\\s*$");
	
//	void loadHeuristics(String file) throws FileNotFoundException, IOException {
//		boolean debug = false;
//		heurMap = new HashMap<String, Double> ();
//		BufferedReader br = new BufferedReader(new FileReader(file));
//		while (br.ready()) {
//			String line = br.readLine();
//			Matcher match = heurPat.matcher(line);
//			if (!match.matches())
//				throw new IOException("Bad line: "+line);
//			String lhs = match.group(1);
//			double rhs = Double.parseDouble(match.group(2));
//			if (debug) Debug.debug(debug, "Mapping ["+lhs+"] to "+rhs);
//			heurMap.put(lhs, rhs);
//
//		}
//	}
//
//	// form appropriate string value of s and get heuristics from map
//	public double getHeuristics(Symbol s) throws UnusualConditionException {
//		boolean debug = false;
//		String str;
//		if (s instanceof FilteringPairSymbol) {
//			str = FilteringPairSymbol.unrollChain((FilteringPairSymbol)s);
//		}
//		else if (s instanceof ProdSymbol)
//			str = ProdSymbol.unrollChain((ProdSymbol)s);
//		else
//			str = s.toString();
//		if (!heurMap.containsKey(str))
//			throw new UnusualConditionException("No heuristics loaded for ["+str+"]");
////			return Double.NEGATIVE_INFINITY;
//		if (debug) Debug.debug(debug, "Got heuristics for "+str);
//		return heurMap.get(str);
//		
//	}
	private HashMap<Symbol, Double> heurMap;
	public void setHeuristics(HashMap<Symbol, Double> hm) {
		heurMap = new HashMap<Symbol, Double>();
		for (Symbol s : hm.keySet()) {
			Symbol key = null;
			if (s instanceof ProdSymbol) {
				ProdSymbol ps = (ProdSymbol)s;
				key = SymbolFactory.getSymbol(ProdSymbol.unrollChain(ps));
			}
			else if (s instanceof FilteringPairSymbol) {
				FilteringPairSymbol ps = (FilteringPairSymbol)s;
				key = SymbolFactory.getSymbol(FilteringPairSymbol.unrollChain(ps));
			}
			else
				key = s;
			if (!heurMap.containsKey(key) || hm.get(s) > heurMap.get(key))
				heurMap.put(key, hm.get(s));
		}
//		heurMap = hm;
	}
	public double getHeuristics(Symbol inputs) throws UnusualConditionException {
		boolean debug = false;
		Symbol s = null;
		if (inputs instanceof ProdSymbol) {
			ProdSymbol ps = (ProdSymbol)inputs;
			s = SymbolFactory.getSymbol(ProdSymbol.unrollChain(ps));
		}
		else if (inputs instanceof FilteringPairSymbol) {
			FilteringPairSymbol ps = (FilteringPairSymbol)inputs;
			s = SymbolFactory.getSymbol(FilteringPairSymbol.unrollChain(ps));
		}
		else
			s = inputs;
		if (!heurMap.containsKey(s)) {
			if (debug) Debug.debug(debug, "NO heuristics for "+s);
			return Double.NEGATIVE_INFINITY;
		}
		if (debug) Debug.debug(debug, "Got heuristics for "+s);
		return heurMap.get(s);
	}
	
}
