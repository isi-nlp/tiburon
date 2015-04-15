package edu.isi.tiburon;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;


import edu.stanford.nlp.util.BinaryHeapPriorityQueue;




// an implementation of k-best a star. 
// TODO: merge into grammar if this works
public class KBAS {
	
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

	private Grammar gr;
	private BinaryHeapPriorityQueue<AgendaItem> pq;
	private HashMap<Symbol, Double> bounds;
	private HashMap<Symbol, HState> hstates;
	private HashMap<Symbol, OState> ostates;
	// items as they're built -- lets us change the back pointers
	private HashMap<Symbol, BackPointerState> items;
	// potential bottom-up items that haven't had an outside yet
	private BinaryHeapPriorityQueue<BackPointerState> incompletes;
	// waiting dotted rules, indexed by what they're waiting for
	private HashMap<Symbol, Vector<DottedRule>> dottedRules;
	// potential bottom-up dotted rules that haven't had an outside yet,
	// indexed by their outside
	private HashMap<Symbol, HashSet<DottedRule>> incompleteDottedRules;
	
	// potential rules for building outside cost that didn't have an inside yet
	// indexed by their next inside
	private HashMap<Symbol, Vector<GrammarRule>> incompleteOutsideRules;
	// potential rules for building deriv that didn't have the member yet
	private HashMap<Symbol, Vector<Deriv>> incompleteDerivs;
	// derivs that don't have outsides
	private HashMap<Symbol, Vector<Deriv>> noOutsideDerivs;
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
	public static final boolean useIndexForward = true;
	public void kbas(int k) throws UnusualConditionException {
		boolean debug = false;
		boolean popdebug = false;
		// should we use td heuristic?
		boolean usetd = true;
		// should we try fs-free approach?
		boolean fsfree = true;
		
		pq = new BinaryHeapPriorityQueue<AgendaItem>();
		bounds = new HashMap<Symbol, Double>();
		hstates = new HashMap<Symbol,HState>();
		ostates = new HashMap<Symbol,OState>();
		// items as they're built -- lets us change the back pointers
		items = new HashMap<Symbol, BackPointerState>();
		// potential bottom-up items that haven't had an outside yet
		incompletes = new BinaryHeapPriorityQueue<BackPointerState>();
		// waiting dotted rules, indexed by what they're waiting for
		dottedRules = new HashMap<Symbol, Vector<DottedRule>>();
		// potential bottom-up dotted rules that haven't had an outside yet,
		// indexed by their outside
		incompleteDottedRules = new HashMap<Symbol, HashSet<DottedRule>>();
		incompleteOutsideRules = new HashMap<Symbol, Vector<GrammarRule>> ();
		incompleteDerivs = new HashMap<Symbol, Vector<Deriv>>();
		// one-best scores
		obscores = new HashMap<Symbol, Double>();
		outsidescores = new HashMap<Symbol, Double>() ;
		obrules = new HashMap<Symbol, GrammarRule>();
		adjacencies = new HashMap<GrammarRule, HashSet<Vector<Integer>>>();
		derivs = new HashMap<Symbol, Vector<Deriv>>(); 
		noOutsideDerivs = new HashMap<Symbol, Vector<Deriv>>();
		hstates.put(gr.getStartState(), new HState(gr.getStartState()));
		double stopBound = 0;
		int touchcount = 0;
		// initialize agen with top hstate
		pq.add(hstates.get(gr.getStartState()), 0.0);
		while (!pq.isEmpty()) {
			double ph = pq.getPriority();
			
			AgendaItem theitem = pq.removeFirst();
			// monotonicity check
			double thresh = 1E-12;
			if (ph - stopBound > thresh)
				throw new UnusualConditionException("Monotonicity in one-best heap violated: saw "+ph+" after "+stopBound);
			stopBound = ph;
			
			// top-down heuristic case
			if (theitem instanceof HState) {
				double p = ph;
				HState tds = (HState)theitem;
				Symbol s = tds.getSym();
				if (debug) Debug.debug(popdebug || debug, "Popped t-d heuristic "+tds+" with priority "+p);
				bounds.put(s, p);
				// if this symbol is formerly incomplete (b-u item waiting for fake outside cost), move it to main pq
				// shouldn't exist in fs-free
				if (items.containsKey(s)) {
					if (fsfree)
						throw new UnusualConditionException("Shouldn't be awaiting bounds for "+items.get(s));
					BackPointerState item = items.get(s);
					if (incompletes.contains(item)) {
						incompletes.remove(item);
						if (debug) Debug.debug(debug, "Removing "+item+" from fake b-u pq and moving to real");
						pq.relaxPriority(item, item.getScore()+bounds.get(s));
					}
				}
				// if there were incomplete dotted rules (incomplete b-u item waiting for fake outside AND more b-u items), clear them out
				// shouldn't exist in fs-free
				if (incompleteDottedRules.containsKey(s)) {
					if (fsfree)
						throw new UnusualConditionException("Shouldn't be awaiting bounds for "+incompleteDottedRules.get(s));
					if(debug) Debug.debug(debug, "Clearing out "+incompleteDottedRules.get(s).size()+" incomplete dotted rules");
					for (DottedRule dr : incompleteDottedRules.get(s)) {
						if (debug) Debug.debug(debug, "Relaxing incomplete dotted rule "+dr);
						if (!pq.relaxPriority(dr, dr.getScore()+bounds.get(dr.getRule().getState()))) {
							throw new UnusualConditionException("Couldn't add "+dr+" to pq!");
						}
					}
					incompleteDottedRules.remove(s);
				}
				// we shouldn't pop t-d heuristic after b-u one best
				if (obscores.containsKey(s))
					throw new UnusualConditionException("Got t-d heuristic for "+s+" after one-best");
				for (GrammarRule g : gr.getBackwardRules(s)) {
					
					if (g.touch())
						touchcount++;
					// add in single b-u element
					if (g.getChildren().size() == 0) {
						//if (debug) Debug.debug(debug, "After "+touchcount+" rules, initializing bottom-up");
						//buinit = true;

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
						// we always have the outside cost of s -- we just added it!
						// terminal items should be real -- but they might have previously been fake, so get 'em out!
						priority+= bounds.get(s);

						if (pq.relaxPriority(item, priority)) {
							if (debug) Debug.debug(debug, "Relaxed b-u item "+item+" with priority "+priority);
							item.setRule(g);
							item.setScore(cost);
							if (incompletes.contains(item)) {
								if (incompletes.remove(item)) {
									if (debug) Debug.debug(debug, "Removed fake b-u upon seeing real outside cost");
								}
								else {
									if (debug) Debug.debug(debug, "Couldn't remove fake b-u "+item);
								}
							}
							if (debug) Debug.debug(debug, "Relaxed term real b-u "+s+" to "+priority+" and changed item back pointer: "+item);
						}
					}
					else {
						// in fsfree, start to build b-u object that may need to wait for some inside costs				
						// for fsfree: build dotted rule with as many children as have been seen
						DottedRule dr = fsfree ? new DottedRule(g) : null;
						boolean hitIncomplete = false;
						for (Symbol c : g.getChildren()) {
							if (fsfree) {
								if (!hitIncomplete && obscores.containsKey(c)) {
									if (debug) Debug.debug(debug, "Seen "+c+" so advancing "+dr);
									dr = new DottedRule(dr, obscores.get(c));
								}
								else
									hitIncomplete = true;
							}
								
							if (bounds.containsKey(c)) {
								//						if (debug) Debug.debug(debug, "Already popped "+c+" so not re-considering");
								continue;
							}
							double weight = usetd ? bounds.get(g.getState())-g.getWeight() : 0;
							if (!hstates.containsKey(c))
								hstates.put(c, new HState(c));

							if (pq.relaxPriority(hstates.get(c), weight)) {
								if (debug) Debug.debug(debug, "Relaxed t-d heuristic priority of "+c+" to "+weight+" using "+g);						
							}
						}
						if (fsfree) {
							double drpriority = dr.getScore()+bounds.get(s);
							if (dr.isDone()) {
								if (debug) Debug.debug(debug, "Completed "+dr+" so making item");
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
								if (pq.relaxPriority(item, drpriority)) {
									if (debug) Debug.debug(debug, "Relaxed b-u item "+item+" with priority "+drpriority);
									item.setRule(g);
									item.setScore(cost);
								}

							}
							else {
								if (debug) Debug.debug(debug, "Relaxing b-u rule "+dr+" with priority "+drpriority);
								if (!pq.relaxPriority(dr, drpriority))
									throw new UnusualConditionException("Weird: couldn't relax "+dr+" with "+drpriority);
							}
						}
					}
				}
			}
			// bottom-up rule case (subcase of bottom-up state)
			else if (theitem instanceof DottedRule) {
				DottedRule olddr = (DottedRule)theitem;
				Symbol s = olddr.getNext();
				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped b-u rule "+olddr+" with priority "+ph);
				// don't bother if we've already locked the state
				if (obscores.containsKey(olddr.getRule().getState()))
					continue;
				// If the rule's state exists, advance it. 
				if (obscores.containsKey(s)) {
					if (debug) Debug.debug(debug, "Combining "+olddr+" with extant state "+s);
					DottedRule dr = new DottedRule(olddr, obscores.get(s));
					// if it's complete, add a new b-u state
					// fsfree check
					if (dr.isDone()) {
						if (!addStateToBUAgenda(dr.getRule(), dr.getScore()) && fsfree)
							throw new UnusualConditionException("Added "+dr+" to bounds-free agenda!");
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

				BackPointerState bps = (BackPointerState)theitem;
				Symbol s = bps.getSym();
				double p = bps.getScore();

				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped b-u state "+bps+" with priority "+ph+" and inside cost "+p);

				// instead of derivs, add in one bests
				obrules.put(s, bps.getRule());
				obscores.put(s, p);
				// once the end is reached, enqueue the first OState
				if (s == gr.getStartState()) {
					if (!ostates.containsKey(s))
						ostates.put(s, new OState(s, 0));
					
					if (pq.relaxPriority(ostates.get(s), p)) {
						if (debug) Debug.debug(debug, "Relaxed outside priority of top state "+s+" to priority "+p);								
					}
				}
				// possibly build outside states
				if (incompleteOutsideRules.containsKey(s)) {
					Vector<GrammarRule> incRules = incompleteOutsideRules.remove(s);
					for (GrammarRule g : incRules) {
						
						boolean isGood = true;
						double priority = outsidescores.get(g.getState())-g.getWeight();
						if (debug) Debug.debug(debug, "Attempting to build outside cost for deferred rule "+g+"; rule and outside make "+priority+" so far");
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
							priority += obscores.get(c);
						}
						if (!isGood)
							continue;
						// if fsfree, outside rule is complete so seed the deriv object here
						// TODO: if stuff is missed, do this all when outside rules are initially explored
						Vector<Integer> derivvec = fsfree ? new Vector<Integer>() : null;
						// add in outside item for each child
						for (Symbol c : g.getChildren()) {
							if (fsfree)
								derivvec.add(0);
							if (outsidescores.containsKey(c))
								continue;
							double cost = priority - obscores.get(c);

							if (!ostates.containsKey(c))
								ostates.put(c, new OState(c, cost));

							if (pq.relaxPriority(ostates.get(c), priority)) {
								ostates.get(c).setScore(cost);
								if (debug) Debug.debug(debug, "Relaxed outside priority of "+c+" to "+priority+" and cost to "+cost+" using "+g);								
							}
						}
						if (fsfree) {
							if (!adjacencies.containsKey(g))
								adjacencies.put(g, new HashSet<Vector<Integer>>());
							if (adjacencies.get(g).contains(derivvec))
								continue;
							adjacencies.get(g).add(derivvec);
							double cost = -g.getWeight();
							// derivs might have cost
							Deriv newDeriv = new Deriv(g, derivvec, cost);
							
							boolean isOk = true;
							// does 0th of each deriv exist? if not, deriv is incomplete
							for (Symbol c : g.getChildren()) {
								if (derivs.containsKey(c)) {
									cost += derivs.get(c).get(0).getScore();
								}
								else {
									isOk = false;
									if (debug) Debug.debug(debug, "No 1 best for "+c+" so saving top deriv of "+g+" for later");
									if (!incompleteDerivs.containsKey(c))
										incompleteDerivs.put(c, new Vector<Deriv>());
									incompleteDerivs.get(c).add(newDeriv);
									break;
								}
							}
							// for derivs with complete substructure. does outside exist? if not, save them
							// if so, add to priority
							if (isOk) {

								newDeriv.setScore(cost);
								if (outsidescores.containsKey(g.getState())) {
									double derivpriority = cost + outsidescores.get(g.getState());
									if (pq.relaxPriority(newDeriv, derivpriority))
										if (debug) Debug.debug(debug, "Relaxed deriv priority of "+newDeriv+" to "+derivpriority+"(outside of "+g.getState()+", which is "+outsidescores.get(g.getState())+") and cost to "+cost);														
								}
								else {
									if (debug) Debug.debug(debug, "Outside not found for "+newDeriv+" so saving");														
									if (!noOutsideDerivs.containsKey(g.getState()))
										noOutsideDerivs.put(g.getState(), new Vector<Deriv>());
									noOutsideDerivs.get(g.getState()).add(newDeriv);
								}
							}
						}
					}
				}
				
				// get rules with left corner that match the state
				// this is handled by heuristic states and dotted rules if fsfree
				if (!fsfree) {
					for (GrammarRule g :  gr.getForwardRules(s, 0) ) {
						if (g.touch())
							touchcount++;
						// don't bother if we've already locked the state
						if (obscores.containsKey(g.getState()))
							continue;
						// make a dotted rule out of this
						DottedRule dr = new DottedRule(g, obscores.get(s));
						// if it's complete, add a new b-u state
						if (dr.isDone()) {
							addStateToBUAgenda(dr.getRule(), dr.getScore());
						}
						// if it's not complete, add the dotted rule to the pq or the incomplete list
						else {
							if (debug) Debug.debug(debug, "Adding incomplete dotted rule "+dr);
							if (bounds != null && bounds.containsKey(dr.getRule().getState())) {
								if (!pq.relaxPriority(dr, dr.getScore()+bounds.get(dr.getRule().getState()))) {
									throw new UnusualConditionException("Couldn't add "+dr+" to pq!");
								}
							}
							else {
								if (debug) Debug.debug(debug, "Waiting for outside cost for incomplete dotted rule "+dr);
								if (!incompleteDottedRules.containsKey(dr.getRule().getState()))
									incompleteDottedRules.put(dr.getRule().getState(), new HashSet<DottedRule>());
								incompleteDottedRules.get(dr.getRule().getState()).add(dr);
							}
						}

					}
				}
				// get rules from the waiting list and remove them
				if (dottedRules.containsKey(s)) {
					if (debug) Debug.debug(debug, dottedRules.get(s).size()+" waiting rules");
					for (DottedRule olddr : dottedRules.remove(s)) {
						// don't bother if we've already locked the state
						if (obscores.containsKey(olddr.getRule().getState()))
							continue;
						DottedRule dr = new DottedRule(olddr, obscores.get(s));
						// if it's complete, add a new b-u state
						if (dr.isDone()) {
							if (!addStateToBUAgenda(dr.getRule(), dr.getScore()) && fsfree)
								throw new UnusualConditionException("Added "+dr+" to bounds-free agenda!");
						}
						// if it's not complete, add the dotted rule to the pq or the incomplete list
						else {
							if (debug) Debug.debug(debug, "Adding incomplete dotted rule "+dr);
							if (bounds != null && bounds.containsKey(dr.getRule().getState())) {
								if (!pq.relaxPriority(dr, dr.getScore()+bounds.get(dr.getRule().getState()))) {
									throw new UnusualConditionException("Couldn't add "+dr+" to pq!");
								}
							}
							else {
								if (fsfree)
									throw new UnusualConditionException("No bounds for "+dr);
								if (debug) Debug.debug(debug, "Waiting for outside cost for incomplete dotted rule "+dr);
								if (!incompleteDottedRules.containsKey(dr.getRule().getState()))
									incompleteDottedRules.put(dr.getRule().getState(), new HashSet<DottedRule>());
								incompleteDottedRules.get(dr.getRule().getState()).add(dr);
							}
						}
					}
				}
			}
			
			// top-down REAL outside scores
			else if (theitem instanceof OState) {
				OState os = (OState)theitem;
				Symbol s = os.getSym();
				double p = os.getScore();
				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped outside state "+s+" with priority "+ph+" and cost "+p);

				outsidescores.put(s, p);
				// look for waiting derivs
				if (noOutsideDerivs.containsKey(s)) {
					if (fsfree)
						throw new UnusualConditionException("Found derivs with no outside: "+noOutsideDerivs.get(s));
					for (Deriv d : noOutsideDerivs.remove(s)) {
						double priority = p+d.getScore();
						// cycle avoidance -- no more than k derivs per state
						if (derivs.containsKey(d.getRule().getState()) && derivs.get(d.getRule().getState()).size() >= k)
							continue;
						if (pq.relaxPriority(d, priority)) {
							if (debug) Debug.debug(debug, "Adding waiting deriv "+d+" with priority "+priority);
						}
						else {
							if (debug) Debug.debug(debug, "Couldn't add waiting deriv "+d+" with priority "+priority);
						}
						
					}
				}
				
				// propagate down
				for (GrammarRule g : gr.getBackwardRules(s)) {	
					if (g.touch()) {
						Debug.prettyDebug("Weird: re-touched rule "+g+" in Ostate propagation");
						touchcount++;
					}
					// add in base case deriv element
					if (g.getChildren().size() == 0) {
						
						Deriv d = new Deriv(g, new Vector<Integer>(), -g.getWeight());
						double priority = outsidescores.get(s)-g.getWeight();
						if (debug) Debug.debug(debug, "Adding base deriv object "+d+" with priority"+priority);
						pq.relaxPriority(d, priority);
					}
					else {
						// if not all children have cost, don't bother adding in outsides
						boolean isGood = true;
						double priority = p-g.getWeight();
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
							priority += obscores.get(c);
						}
						if (!isGood)
							continue;
						// add in outside item for each child
						// if fsfree, outside rule is complete so seed the deriv object here
						// deriv object's children might be complete
						Vector<Integer> derivvec = fsfree ? new Vector<Integer>() : null;
						double derivcost = -g.getWeight();
						Symbol lastsym = null;
						for (Symbol c : g.getChildren()) {
							if (fsfree) {
								derivvec.add(0);
								if (lastsym == null) {
									if (derivs.containsKey(c))
										derivcost += derivs.get(c).get(0).getScore();
									else
										lastsym = c;
								}
							}
							if (outsidescores.containsKey(c))
								continue;
							double cost = priority - obscores.get(c);

							if (!ostates.containsKey(c))
								ostates.put(c, new OState(c, cost));

							if (pq.relaxPriority(ostates.get(c), priority)) {
								ostates.get(c).setScore(cost);
								if (debug) Debug.debug(debug, "Relaxed outside priority of "+c+" to "+priority+" and cost to "+cost+" using "+g);								
							}
						}
						// if fsfree, handle new deriv:
						
						if (fsfree) {
							// cycle avoidance -- no more than k derivs per state
							if (derivs.containsKey(g.getState()) && derivs.get(g.getState()).size() >= k)
								continue;
							// set adjacencies
							if (!adjacencies.containsKey(g))
								adjacencies.put(g, new HashSet<Vector<Integer>>());
							if (adjacencies.get(g).contains(derivvec))
								continue;
							adjacencies.get(g).add(derivvec);
						
							Deriv newDeriv = new Deriv(g, derivvec, derivcost);
							// either add the incomplete deriv to the appropriate spot
							// or add the complete deriv to the agenda
							if (lastsym == null) {
								double derivpriority = derivcost+p;
								if (debug) Debug.debug(debug, "Adding completed deriv "+newDeriv+" with priority "+derivpriority);
								if (!pq.relaxPriority(newDeriv, derivpriority))
									throw new UnusualConditionException("Weird: couldn't relax priority "+derivpriority+" of "+newDeriv);
							}
							else {
								if (debug) Debug.debug(debug, "No 1 best for "+lastsym+" so saving top deriv of "+g+" for later");
								if (!incompleteDerivs.containsKey(lastsym))
									incompleteDerivs.put(lastsym, new Vector<Deriv>());
								incompleteDerivs.get(lastsym).add(newDeriv);
							}
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
				Deriv d = (Deriv)theitem;
				double p = d.getScore();
				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped deriv "+d+" with priority "+ph+" and cost "+p);
				Symbol state = d.getRule().getState();
				if (!derivs.containsKey(state)) {
					if (debug) Debug.debug(debug, "Popped FIRST deriv for "+state);
					derivs.put(state, new Vector<Deriv>());
					derivs.get(state).add(d);
					if (!fsfree) {
						if (debug) Debug.debug(debug, "using FS to build derivs");
						for (GrammarRule g : gr.getForwardRules(state, 0)) {

							if (g.touch()) {
								Debug.prettyDebug("Weird: re-touched rule "+g+" in Deriv propagation");
								touchcount++;
							}

							// cycle avoidance -- no more than k derivs per state
							if (derivs.containsKey(g.getState()) && derivs.get(g.getState()).size() >= k)
								continue;

							Vector<Integer> newd = new Vector<Integer>();
							for (Symbol c : g.getChildren()) {
								newd.add(0);
							}
							if (!adjacencies.containsKey(g))
								adjacencies.put(g, new HashSet<Vector<Integer>>());
							if (adjacencies.get(g).contains(newd))
								continue;
							if (debug) Debug.debug(debug, "Adding new adjacency: "+g+" and "+newd);
							adjacencies.get(g).add(newd);
							double cost = -g.getWeight();
							// TODO: shouldn't have any cost yet!
							Deriv newDeriv = new Deriv(g, newd, cost);
							boolean isOk = true;
							// does 0th of each deriv exist?
							for (Symbol c : g.getChildren()) {
								if (derivs.containsKey(c)) {
									cost += derivs.get(c).get(0).getScore();
								}
								else {
									isOk = false;
									if (debug) Debug.debug(debug, "No 1 best for "+c+" so saving top deriv of "+g+" for later");
									if (!incompleteDerivs.containsKey(c))
										incompleteDerivs.put(c, new Vector<Deriv>());
									incompleteDerivs.get(c).add(newDeriv);
									break;
								}
							}
							if (!isOk)
								continue;
							newDeriv.setScore(cost);
							if (outsidescores.containsKey(g.getState())) {
								double priority = cost + outsidescores.get(g.getState());
								if (pq.relaxPriority(newDeriv, priority))
									if (debug) Debug.debug(debug, "Relaxed deriv priority of "+newDeriv+" to "+priority+"(outside of "+g.getState()+", which is "+outsidescores.get(g.getState())+") and cost to "+cost);														
							}
							else {
								if (debug) Debug.debug(debug, "Outside not found for "+newDeriv+" so saving");														
								if (!noOutsideDerivs.containsKey(g.getState()))
									noOutsideDerivs.put(g.getState(), new Vector<Deriv>());
								noOutsideDerivs.get(g.getState()).add(newDeriv);
							}
						}
					}
				}
				else {
					derivs.get(state).add(d);
				}
				// stop if kth deriv of top is popped
				if (state.equals(gr.getStartState())) {
					System.out.println(derivs.get(state).size()+":"+getTree(d)+" # "+d.getScore()+" # "+touchcount);
					if (derivs.get(state).size() == k) 
					return;
				}
					
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
						if (outsidescores.containsKey(newd.getRule().getState())) {
							double priority = cost + outsidescores.get(newd.getRule().getState());
							if (pq.relaxPriority(newd, priority)) {
								if (debug) Debug.debug(debug, "Relaxed deriv priority of deferred deriv "+newd+" to "+priority+"(outside of "+newd.getRule().getState()+", which is "+outsidescores.get(newd.getRule().getState())+") and cost to "+cost);	
							}
							else {
								if (debug) Debug.debug(debug, "Couldn't relax priority of deferred deriv "+newd+" to "+priority);
							}
						}
						else {
							if (fsfree)
								throw new UnusualConditionException("Found deriv with no outside: "+newd);
							if (debug) Debug.debug(debug, "Outside not found for "+newd+" so saving");														
							if (!noOutsideDerivs.containsKey(newd.getRule().getState()))
								noOutsideDerivs.put(newd.getRule().getState(), new Vector<Deriv>());
							noOutsideDerivs.get(newd.getRule().getState()).add(newd);
						}
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
						if (pq.relaxPriority(newd, priority)) {
							if (debug) Debug.debug(debug, "Relaxed priority of deriv "+newd+" to "+priority);
						}
						else
							if (debug) Debug.debug(debug, "Couldn't relax priority of deriv "+newd+" to "+priority);
							
							
					}
					
				}
				
			}
			
		}

	}

	// adding of new state to bottom-up agenda -- convenience bit!
	// return value tells us where it went
	private boolean addStateToBUAgenda(GrammarRule g, double c) {
		double cost = c;
		boolean debug = false;
		double priority = cost;
		//					if (debug) Debug.debug(debug, "Cost is "+cost);
		boolean isReal = true;
		if (bounds != null && bounds.containsKey(g.getState())) {
			priority += bounds.get(g.getState());
			//						if (debug) Debug.debug(debug, "Known bounds of "+bounds.get(g.getState())+" puts priority at "+priority);
		}
		else {
			// don't actually put the stop bound in here -- it gets added when this item comes off the fake
			// agenda
			
			isReal = false;
		}
		//						if (debug) Debug.debug(debug, "Trying "+g+" with cost "+cost);
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
		// choose agenda based on source of outside. If we now have a real item that was fake, remove the fake one
		BinaryHeapPriorityQueue agen = isReal ? pq : incompletes;
		if (agen.relaxPriority(item, priority)) {
			item.setRule(g);
			item.setScore(cost);
			if (debug) Debug.debug(debug, "Relaxed "+(isReal ? "real" : "fake")+" b-u "+g.getState()+" to "+priority+" and changed item back pointer: "+item);
			if (isReal && incompletes.contains(item)) {
				if(incompletes.remove(item)) {
					if (debug) Debug.debug(debug, "Removed fake b-u upon seeing real outside cost");
				}
				else {
					if (debug) Debug.debug(debug, "Couldn't remove fake b-u upon seeing real outside cost!");
				}
			}

		}
		else {
			if (debug) Debug.debug(debug, "Couldn't relax "+(isReal ? "real" : "fake")+" b-u "+g.getState()+" because "+priority+" exceeds current value of "+agen.getPriority(item));
		}
		return isReal;
	}
	
	
	// relies on BS iterator returning sorted rules
	// no for loops on rules!
	// fs-free!
	public HashMap<Symbol, Double> getOutsides() throws UnusualConditionException {
		boolean debug = false;
		boolean popdebug = false;
		// should we use td heuristic?
		boolean usetd = true;
		// should we report monotonicity violations?
		boolean reportMono = false;
		
		
		pq = new BinaryHeapPriorityQueue<AgendaItem>();
		bounds = new HashMap<Symbol, Double>();
		hstates = new HashMap<Symbol,HState>();
		ostates = new HashMap<Symbol,OState>();
		// items as they're built -- lets us change the back pointers
		items = new HashMap<Symbol, BackPointerState>();
		
		
		// waiting dotted rules, indexed by what they're waiting for
		dottedRules = new HashMap<Symbol, Vector<DottedRule>>();
		// banned in fsfree land
		// completed bottom-up dotted rules that haven't had a *true* outside yet,
		// indexed by their outside
		// incompleteDottedRules = new HashMap<Symbol, HashSet<DottedRule>>();
		
		// potential outside rules that haven't had all their children's insides yet 
		// indexed by what they're waiting for
		incompleteOutsideRules = new HashMap<Symbol, Vector<GrammarRule>> ();
		// potential outside rules that have all their children but not their true outside yet
		// indexed by top state
		HashMap<Symbol, Vector<GrammarRule>> waitingOutsideRules = new HashMap<Symbol, Vector<GrammarRule>> ();
		
		
		// one-best scores
		obscores = new HashMap<Symbol, Double>();
		outsidescores = new HashMap<Symbol, Double>() ;
		obrules = new HashMap<Symbol, GrammarRule>();
		
		
		hstates.put(gr.getStartState(), new HState(gr.getStartState()));
		double stopBound = 0;
		int touchcount = 0;
		// initialize agen with top hstate
		pq.add(hstates.get(gr.getStartState()), 0.0);
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
				double p = ph;
				HState tds = (HState)theitem;
				Symbol s = tds.getSym();
				if (debug) Debug.debug(popdebug || debug, "Popped t-d heuristic "+tds+" with priority "+p);
				bounds.put(s, p);
	
				// we shouldn't pop t-d heuristic after b-u one best
				if (obscores.containsKey(s))
					throw new UnusualConditionException("Got t-d heuristic for "+s+" after one-best");
				// add in the HSTateIterator for this state
				HStateRuleIterator hsri = new HStateRuleIterator(s, gr.getBSIter(s));
				// priority is heuristic cost of above plus rule weight (which is negative)
				if (hsri.hasNext()) {
					double hsripri = p-hsri.peek();
					if (debug) Debug.debug(debug, "Relaxing t-d heuristic iterator with priority "+hsripri);
					if (!pq.relaxPriority(hsri, hsripri))
						throw new UnusualConditionException("Couldn't add t-d iterator for "+s+" to queue");
				}
				else {
					if (debug) Debug.debug(debug, "No next element for "+s+" so discarding");
				}
			}
			// next rule in top-down heuristic search
			else if (theitem instanceof HStateRuleIterator) {
				HStateRuleIterator hsri = (HStateRuleIterator) theitem;
				Symbol s= hsri.getSym();
				GrammarRule g = hsri.next();
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

						if (pq.relaxPriority(item, priority)) {
							if (debug) Debug.debug(debug, "Relaxed b-u item "+item+" with priority "+priority);
							item.setRule(g);
							item.setScore(cost);
						}
					}
					// terminal rules are waiting outside cost
					if (!waitingOutsideRules.containsKey(s))
						waitingOutsideRules.put(s, new Vector<GrammarRule>());
					waitingOutsideRules.get(s).add(g);		
				}
				else {
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
						if (!hstates.containsKey(c))
							hstates.put(c, new HState(c));

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
							if (pq.relaxPriority(item, drpriority)) {
								if (debug) Debug.debug(debug, "Relaxed b-u item "+item+" with priority "+drpriority);
								item.setRule(g);
								item.setScore(cost);
							}
						}
						// propagate new outside states and derivs
						if (outsidescores.containsKey(s)) {
							propagateOutside(g, dr.getScore());
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
							if (pq.relaxPriority(item, priority)) {
								item.setRule(g);
								item.setScore(cost);
								if (debug) Debug.debug(debug, "Relaxed  b-u item "+g.getState()+" with priority "+priority);
							}
						}
						// propagate new outside states and derivs
						if (outsidescores.containsKey(g.getState())) {
							propagateOutside(g, dr.getScore());
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

				BackPointerState bps = (BackPointerState)theitem;
				Symbol s = bps.getSym();
				double p = bps.getScore();

				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped b-u state "+bps+" with priority "+ph+" and inside cost "+p);

				// instead of derivs, add in one bests
				obrules.put(s, bps.getRule());
				obscores.put(s, p);
				// once the end is reached, enqueue the first OState
				if (s == gr.getStartState()) {
					if (!ostates.containsKey(s))
						ostates.put(s, new OState(s, 0));
					
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
							propagateOutside(g, baseCost);
						
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
								if (pq.relaxPriority(item, priority)) {
									item.setRule(g);
									item.setScore(cost);
									if (debug) Debug.debug(debug, "Relaxed  b-u item "+g.getState()+" with priority "+priority);
								}
							}
							// propagate new outside states
							if (outsidescores.containsKey(g.getState())) {
								propagateOutside(g, dr.getScore());
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
							if (!pq.relaxPriority(dr, dr.getScore()+bounds.get(dr.getRule().getState()))) {
								throw new UnusualConditionException("Couldn't add "+dr+" to pq!");
							}

						}
					}
				}
			}
			
			// top-down REAL outside scores -- clear out all waiting rules
			else if (theitem instanceof OState) {
				OState os = (OState)theitem;
				Symbol s = os.getSym();
				double p = os.getScore();
				printOutside(s, p);
				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped outside state "+s+" with priority "+ph+" and cost "+p);

				outsidescores.put(s, p);
				if (waitingOutsideRules.containsKey(s)) {
					for (GrammarRule g : waitingOutsideRules.remove(s)) {
						double priority = p-g.getWeight();
						if (g.getChildren().size() > 0) {

							double baseCost = -g.getWeight();

							for (Symbol c : g.getChildren()) {
								baseCost += obscores.get(c);
							}
							propagateOutside(g, baseCost);
						}
					}
				}
			}
			// deriv case
		
			else {
				throw new UnusualConditionException("Saw item "+theitem+" in getOutsides");
			}

		}
		return outsidescores;

	}

	// given a state that might be composed of other states and a weight,
	// print the chain of states and then the weight
	private void printOutside(Symbol s, double w) {
		StringBuffer buf = new StringBuffer();
		buf.append(s+" = ");
		if (s instanceof FilteringPairSymbol) {
			buf.append(FilteringPairSymbol.unrollChain((FilteringPairSymbol)s));
		}
		else if (s instanceof ProdSymbol)
			buf.append(ProdSymbol.unrollChain((ProdSymbol)s));
		else
			buf.append(s);
		buf.append(" # "+w);
		Debug.prettyDebug(buf.toString());
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
		// either add the incomplete deriv to the appropriate spot
		// or add the complete deriv to the agenda
		if (lastsym == null) {			
			if (debug) Debug.debug(debug, "Adding completed deriv "+newDeriv+" with priority "+priority);
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
	
	

	// convenience method that is done several times in getOutside
	// given a rule that has the outside of the top and the inside of all bottoms
	// 1) propagate outside for each of the bottoms, if not yet found
	
	// base cost is inside of all children plus rule weight. it gets modified for outside propagation
	// outside base cost adds in outside of top but subtracts inside of specific propagated child
	
	private void propagateOutside(GrammarRule g, double baseCost) throws UnusualConditionException{
		boolean debug = false;
		// add in outside item for each child
		// outside rule is complete so seed the deriv object here
		// deriv object's children might be complete
		
		// priority is inside of all children plus rule plus outside of top -- same for everything here
		double priority = baseCost+outsidescores.get(g.getState());
		// subtract inside of relevant state from base outside
		double baseOutside = priority;
		// baseCost is deriv cost
			
		for (Symbol c : g.getChildren()) {
			if (outsidescores.containsKey(c))
				continue;
			double cost = baseOutside - obscores.get(c);

			if (!ostates.containsKey(c))
				ostates.put(c, new OState(c, cost));

			if (pq.relaxPriority(ostates.get(c), priority)) {
				ostates.get(c).setScore(cost);
				if (debug) Debug.debug(debug, "Relaxed outside priority of "+c+" to "+priority+" and cost to "+cost+" using "+g);								
			}
			else {
				if (debug) Debug.debug(debug, "Couldn't relax outside priority of "+c+" to "+priority);								
			}
		}
	}
	
	
	
	// like lazyKbas but only used for getting exact outside costs of states, so no derivs.
	// uses same structures, though
	public void lazyKbas(int k) throws UnusualConditionException {
		boolean debug = false;
		boolean popdebug = false;
		// should we use td heuristic?
		boolean usetd = true;
		// should we report monotonicity violations?
		boolean reportMono = false;
		
		
		pq = new BinaryHeapPriorityQueue<AgendaItem>();
		bounds = new HashMap<Symbol, Double>();
		hstates = new HashMap<Symbol,HState>();
		ostates = new HashMap<Symbol,OState>();
		// items as they're built -- lets us change the back pointers
		items = new HashMap<Symbol, BackPointerState>();
		
		// banned in fsfree land
		// potential bottom-up items that haven't had an outside yet
		//incompletes = new BinaryHeapPriorityQueue<BackPointerState>();
		// waiting dotted rules, indexed by what they're waiting for
		dottedRules = new HashMap<Symbol, Vector<DottedRule>>();
		// banned in fsfree land
		// completed bottom-up dotted rules that haven't had a *true* outside yet,
		// indexed by their outside
		// incompleteDottedRules = new HashMap<Symbol, HashSet<DottedRule>>();
		
		// potential outside rules that haven't had all their children's insides yet 
		// indexed by what they're waiting for
		incompleteOutsideRules = new HashMap<Symbol, Vector<GrammarRule>> ();
		// potential outside rules that have all their children but not their true outside yet
		// indexed by top state
		HashMap<Symbol, Vector<GrammarRule>> waitingOutsideRules = new HashMap<Symbol, Vector<GrammarRule>> ();
		
		incompleteDerivs = new HashMap<Symbol, Vector<Deriv>>();
		// one-best scores
		obscores = new HashMap<Symbol, Double>();
		outsidescores = new HashMap<Symbol, Double>() ;
		obrules = new HashMap<Symbol, GrammarRule>();
		adjacencies = new HashMap<GrammarRule, HashSet<Vector<Integer>>>();
		derivs = new HashMap<Symbol, Vector<Deriv>>(); 
		// banned in fsfree land
		//noOutsideDerivs = new HashMap<Symbol, Vector<Deriv>>();
		hstates.put(gr.getStartState(), new HState(gr.getStartState()));
		double stopBound = 0;
		int touchcount = 0;
		// initialize agen with top hstate
		pq.add(hstates.get(gr.getStartState()), 0.0);
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
				double p = ph;
				HState tds = (HState)theitem;
				Symbol s = tds.getSym();
				if (debug) Debug.debug(popdebug || debug, "Popped t-d heuristic "+tds+" with priority "+p);
				bounds.put(s, p);
	
				// we shouldn't pop t-d heuristic after b-u one best
				if (obscores.containsKey(s))
					throw new UnusualConditionException("Got t-d heuristic for "+s+" after one-best");
				// add in the HSTateIterator for this state
				HStateRuleIterator hsri = new HStateRuleIterator(s, gr.getBSIter(s));
				// priority is heuristic cost of above plus rule weight (which is negative)
				if (hsri.hasNext()) {
					double hsripri = p-hsri.peek();
					if (debug) Debug.debug(debug, "Relaxing t-d heuristic iterator with priority "+hsripri);
					if (!pq.relaxPriority(hsri, hsripri))
						throw new UnusualConditionException("Couldn't add t-d iterator for "+s+" to queue");
				}
				else {
					if (debug) Debug.debug(debug, "No next element for "+s+" so discarding");
				}
			}
			// next rule in top-down heuristic search
			else if (theitem instanceof HStateRuleIterator) {
				HStateRuleIterator hsri = (HStateRuleIterator) theitem;
				Symbol s= hsri.getSym();
				GrammarRule g = hsri.next();
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

						if (pq.relaxPriority(item, priority)) {
							if (debug) Debug.debug(debug, "Relaxed b-u item "+item+" with priority "+priority);
							item.setRule(g);
							item.setScore(cost);
						}
					}
					// terminal rules are waiting outside cost
					if (!waitingOutsideRules.containsKey(s))
						waitingOutsideRules.put(s, new Vector<GrammarRule>());
					waitingOutsideRules.get(s).add(g);		
				}
				else {
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
						if (!hstates.containsKey(c))
							hstates.put(c, new HState(c));

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

				BackPointerState bps = (BackPointerState)theitem;
				Symbol s = bps.getSym();
				double p = bps.getScore();

				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped b-u state "+bps+" with priority "+ph+" and inside cost "+p);

				// instead of derivs, add in one bests
				obrules.put(s, bps.getRule());
				obscores.put(s, p);
				// once the end is reached, enqueue the first OState
				if (s == gr.getStartState()) {
					if (!ostates.containsKey(s))
						ostates.put(s, new OState(s, 0));
					
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
							if (!pq.relaxPriority(dr, dr.getScore()+bounds.get(dr.getRule().getState()))) {
								throw new UnusualConditionException("Couldn't add "+dr+" to pq!");
							}

						}
					}
				}
			}
			
			// top-down REAL outside scores -- clear out all waiting rules
			else if (theitem instanceof OState) {
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
				Deriv d = (Deriv)theitem;
				double p = d.getScore();
				if (popdebug || debug) Debug.debug(popdebug || debug, "Popped deriv "+d+" with priority "+ph+" and cost "+p);
				Symbol state = d.getRule().getState();
				if (!derivs.containsKey(state)) {
					if (debug) Debug.debug(debug, "Popped FIRST deriv for "+state);
					derivs.put(state, new Vector<Deriv>());
				}

				derivs.get(state).add(d);
			
				// stop if kth deriv of top is popped
				if (state.equals(gr.getStartState())) {
					System.out.println(derivs.get(state).size()+":"+getTree(d)+" # "+d.getScore()+" # "+touchcount);
					if (derivs.get(state).size() == k) 
					return;
				}
					
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
						if (pq.relaxPriority(newd, priority))
							if (debug) Debug.debug(debug, "Relaxed priority");
							
					}
					
				}
				
			}
			
		}

	}

	
	
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
	
	public KBAS(Grammar gram) throws UnusualConditionException {
		gr = gram;
//		getOutsides();
	}
	
	public KBAS(Grammar gram, int k) throws UnusualConditionException {
		gr = gram;
		lazyKbas(k);
//		kbas(k);
	}

	// non-otf k-best experiment
	public static void main(String argv[]) {
		TropicalSemiring semiring = new TropicalSemiring();
		int mh = 1;
		try {
			int choice = Integer.parseInt(argv[0]);
			if (choice > 1)
				throw new UnusualConditionException("Expected 0 (bucket) or 1 (integrated)");
			boolean doOTFBucket = (choice == 1);
			
			int k = Integer.parseInt(argv[1]);
			RTGRuleSet rtg = new RTGRuleSet(argv[2], "utf-8", semiring);
			
			//			rtg.removeEpsilons();
			//			System.out.println(rtg.toString());
			Debug.prettyDebug("Done loading grammar");
			ConcreteGrammar rtggr = new ConcreteGrammar(rtg);
			ConcreteGrammar endgr = null;
			
			
			
			// otf-style bucket brigade
			if (doOTFBucket) {
				Grammar g = rtggr;
				Debug.prettyDebug("Done forming rtg concreteGrammar");
				for (int i = 3; i < argv.length; i++) {
					TreeTransducerRuleSet trs = new TreeTransducerRuleSet(argv[i], "utf-8", semiring);
					Debug.prettyDebug("Done loading "+argv[i]);
					OTFTreeTransducerRuleSet ottrs = new OTFTreeTransducerRuleSet(trs);
					Debug.prettyDebug("Done converting transducer to on-the-fly form");
					Debug.prettyDebug("Done building on-the-fly grammar");
					GTOTFGrammar otfg = new GTOTFGrammar(g, ottrs, semiring, mh);
					g = otfg;
				}

				if (argv.length <= 3)
					endgr = (ConcreteGrammar)g;
				else {
					endgr = new ConcreteGrammar((GTOTFGrammar)g);
					Debug.prettyDebug("Done converting otfgrammar into concreteGrammar");
				}
			}
			
			// real style bucket brigade
			else {
				endgr = rtggr;
				for (int i = 3; i < argv.length; i++) {
					TreeTransducerRuleSet trs = new TreeTransducerRuleSet(argv[i], "utf-8", semiring);
					Debug.prettyDebug("Done loading "+argv[i]);
					OTFTreeTransducerRuleSet ottrs = new OTFTreeTransducerRuleSet(trs);
					Debug.prettyDebug("Done converting transducer to on-the-fly form");
					Debug.prettyDebug("Done building on-the-fly grammar");
					GTOTFGrammar otfg = new GTOTFGrammar(endgr, ottrs, semiring, mh);
					endgr = new ConcreteGrammar(otfg);
					Debug.prettyDebug("Done converting otfgrammar into concreteGrammar");
				}
			}
			KBAS kbas = new KBAS(endgr, k);

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
