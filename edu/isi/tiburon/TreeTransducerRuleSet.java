package edu.isi.tiburon;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TreeTransducerRuleSet extends TransducerRuleSet {

	// loopableSet constructed by makeComposeSafe
	private HashSet loopableStates = null;

	// inverse rbs constructed on demand
	private HashMap<Symbol, ArrayList<TreeTransducerRule>> inverseRulesByState = null;

	// dummy constructor for testing file format
	public TreeTransducerRuleSet() { }

	// empty spaces or comments regions
	private static Pattern commentPat = Pattern.compile("\\s*(%.*)?");

	// something that can be a start state -- no spaces, no parens, no colons, no periods
	// can be followed by whitespace and comment
	private static Pattern startStatePat = Pattern.compile("\\s*([^\\s\\(\\):\\.%]+)\\s*(%.*)?");

	// strip comments off
	private static Pattern commentStripPat = Pattern.compile("\\s*(.*?[^\\s%])(\\s*(%.*)?)?");

	public TreeTransducerRuleSet(String filename, String encoding, Semiring s) throws FileNotFoundException, IOException, DataFormatException  {
		this(new BufferedReader(new InputStreamReader(new FileInputStream(filename), encoding)), s);
	}
	// TODO: re-enable comma warning?
	public TreeTransducerRuleSet(BufferedReader br, Semiring s) throws  IOException, DataFormatException {
		super(s);
		boolean debug = false;
		String line = br.readLine();
		// 1) ignore all comments fields and blank lines in the header
		Matcher commentMatch = commentPat.matcher(line);
		while (commentMatch.matches()) {
			if (debug) Debug.debug(debug, "Ignoring comment/whitespace: "+line);
			try {
				line = br.readLine();
			}
			catch (IOException e) {
				throw new IOException("Couldn't read start state");
			}
			commentMatch = commentPat.matcher(line);
		}
		// 2) get start state
		Matcher startStateMatch = startStatePat.matcher(line);
		if (debug) Debug.debug(debug, "Trying to get a start state out of "+line);
		if (!startStateMatch.matches())
			throw new IOException("Could not find start state in "+line);

		startState = SymbolFactory.getSymbol(startStateMatch.group(1));

		// 3) get rules, skipping white space and comments

		states = new HashSet();
		states.add(startState);

		// 3) get rules, skipping white space and comments
		int rulecounter = 0;
		Date readTime = new Date();
		boolean didPrintWarning = false;
		while (br.ready()) {
			line = br.readLine();
			if (debug) Debug.debug(debug, "Trying to get a rule out of "+line);
			commentMatch = commentPat.matcher(line);
			if (commentMatch.matches()) {
				if (debug) Debug.debug(debug, "Ignoring comment/whitespace: "+line);
				continue;
			}
			Matcher commentStripMatch = commentStripPat.matcher(line);
			if (!commentStripMatch.matches())
				throw new DataFormatException("Couldn't strip comments off of "+line);
			String ruleText = commentStripMatch.group(1);

			if (debug) Debug.debug(debug, "Isolated "+ruleText+" for rule");
			TreeTransducerRule r = null;
			try {
				r = new TreeTransducerRule(this, states, ruleText, semiring);
			}
			catch (DataFormatException e) {
				throw new DataFormatException(ruleText+", "+e.getMessage(), e);
			}
			if (debug) Debug.debug(debug, "Made rule "+r.toString());
			if (debug) Debug.debug(debug, "Rule's tie id is "+r.getTie());
//			Debug.prettyDebug("WARNING: not adding rule");
			rules.add(r);
			if (!rulesByState.containsKey(r.getState()))
				rulesByState.put(r.getState(), new ArrayList<TransducerRule>());
			rulesByState.get(r.getState()).add(r);
			rulesByIndex.put(r.getIndex(), r);
			rulecounter++;
			if (!isExtendedRHS && r.isExtendedRHS())
				isExtendedRHS = true;
			if (rulecounter >= 10000 && rulecounter % 10000 == 0) {
				Date pause = new Date();
				long lapse = pause.getTime() - readTime.getTime();
				readTime = pause;
				if (!didPrintWarning){
					Debug.prettyDebug("File is large (>10,000 rules) so time to read in will be reported below");
					didPrintWarning = true;
				}
				Debug.prettyDebug("Read "+rulecounter+" rules: "+lapse+" ms");
			}
			if (r.getTie() > 0) {
				if (!rulesByTie.containsKey(r.getTie()))
					rulesByTie.put(r.getTie(), new ArrayList<TransducerRule>());
				((ArrayList<TransducerRule>)rulesByTie.get(r.getTie())).add(r);
			}
		}
		isExtendedRHSset = true;
	}


	public TreeTransducerRuleSet(TreeTransducerRuleSet trs) {
		super(trs);
	}





	// for conversion of RTG
	// start state the same
	// rhs of rule mirrored on lhs with variables replacing states
	// rhs copied, with variables inserted at states
	// state-to-state 
	public TreeTransducerRuleSet(RTGRuleSet rtg) {
		super();
		boolean debug = false;
		applicableSymbols = new HashSet<Symbol>();
		if (debug) Debug.debug(debug, "initialized applicableSymbols");
		startState = rtg.getStartState();
		// can just recreate whole states set
		states = new HashSet(rtg.getStates());
		semiring = rtg.getSemiring();
		Iterator it = rtg.getRules().iterator();
		while (it.hasNext()) {
			RTGRule oldrule = (RTGRule)it.next();
			TreeTransducerRule newrule = new TreeTransducerRule(this, oldrule, applicableSymbols, rtg.getStates());
			if (!isExtendedRHS && newrule.isExtendedRHS())
				isExtendedRHS = true;
			if (debug) Debug.debug(debug, "Formed "+newrule+" from "+oldrule);
			rules.add(newrule);
			if (!rulesByState.containsKey(newrule.getState())) {
				rulesByState.put(newrule.getState(), new ArrayList<TransducerRule>());
			}
			rulesByState.get(newrule.getState()).add(newrule);
			// add it to the index, too
			rulesByIndex.put(newrule.getIndex(), newrule);
			if (newrule.getTie() > 0) {
				if (!rulesByTie.containsKey(newrule.getTie()))
					rulesByTie.put(newrule.getTie(), new ArrayList<TransducerRule>());
				((ArrayList)rulesByTie.get(newrule.getTie())).add(newrule);
			}
		}
		// don't keep star state around if we haven't seen it
		if (!rulesByState.containsKey(Symbol.getStar()) && states.contains(Symbol.getStar()))
			states.remove(Symbol.getStar());
		if (debug) Debug.debug(debug, "RTG->RLN is "+toString());
		isExtendedRHSset = true;
		if (debug) Debug.debug(debug, "Applicable symbols has "+applicableSymbols);
	}

//	// temporary 2-d table used for pairwise state creation
//	private Hashtable<Symbol, Hashtable<Symbol, Symbol>> pairstatetable = null;
//	// used to get states from table more quickly than with symbolfactory
//	public Symbol getPairState(Symbol a, Symbol b) {
//		if (pairstatetable == null)
//			pairstatetable = new Hashtable<Symbol, Hashtable<Symbol, Symbol>>();
//		if (!pairstatetable.containsKey(a))
//			pairstatetable.put(a, new Hashtable<Symbol, Symbol>());
//		if (!pairstatetable.get(a).containsKey(b)) {
//			pairstatetable.get(a).put(b, SymbolFactory.getStateSymbol(a+"_"+b));
//			// 	    HashSet<Symbol> h = new HashSet<Symbol>();
//			// 	    h.add(a);
//			// 	    h.add(b);
//			// 	    pairstatetable.get(a).put(b, SymbolFactory.getSymbol(h));
//		}
//		return pairstatetable.get(a).get(b);
//	}
//	private void resetPairStateTable() {
//		pairstatetable = null;
//	}


	private class TransducerRuleComp implements Comparator<TransducerRule> {
		public int compare(TransducerRule o1, TransducerRule o2) {
			if (o1.semiring.better(o1.weight, o2.weight))
				return 1;
			else if (o1.semiring.better(o2.weight, o1.weight))
				return -1;
			else
				return 0;
		}
	}
	
	

	
	
	// composition via forward or backward application
	// beam is max number of rules added from each state
	// all potential rules are inspected, but only highest weighted are kept,
	// thanks to a priority queue
	// beam of 0 means all rules are added

	// new way is "lazy"
	public TreeTransducerRuleSet(TreeTransducerRuleSet a, TreeTransducerRuleSet b, int beam) throws ImproperConversionException, UnusualConditionException {
		super();

		boolean debug = false;
		if (debug) Debug.debug(debug, "Composing "+a.toString()+" and "+b.toString()+" with beam of "+0);
//		debug = true;
		if (a.getSemiring() != b.getSemiring())
			throw new ImproperConversionException("Transducers must have same semiring to be composed");
		semiring = a.getSemiring();
		// in general input epsilon doesn't mix with extended rhs, but we can't catch the times when it does yet
		if (a.isExtendedRHS() && b.isInEps())
			throw new ImproperConversionException("Cannot compose an extended rhs transducer with an input-epsilon transducer");

		// determine order of composition when adding "normal" rules. 
		// leftside (default or when a is extended rhs) = given the rhs of rules from a,
		//          find the rtg of rules from b
		// rightside (when b is extended lhs) = given the lhs of rules from b, find the rtg
		//          of rules from a.
		boolean isleftside = true;
		if (b.isExtended() || b.isInEps()) {
			if (debug) Debug.debug(debug, "Choosing righside composition");
			isleftside = false;
		}

		// index a rules by rhs sym, including eps
		Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>> rulesByRhs = new Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>>();
		Date preIndexTime = new Date();
		int statecount = 0;
		for (Symbol s : a.states) {
			statecount++;
			if (statecount % 10000 == 0)
				if (debug) Debug.debug(debug, "indexing rhs of rules of "+statecount+" states");
			rulesByRhs.put(s, new Hashtable<Symbol, ArrayList<TransducerRule>>());
			for (TransducerRule r : a.getRulesOfType(s)) {
				Symbol rhssym = null;
				if (r.rhs.isEpsilon())
					rhssym = Symbol.getEpsilon();
				else
					rhssym = r.rhs.getLabel();
				if (!rulesByRhs.get(s).containsKey(rhssym))
					rulesByRhs.get(s).put(rhssym, new ArrayList<TransducerRule>());
				rulesByRhs.get(s).get(rhssym).add(r);
			}
		}
		Date postIndexTime = new Date();
		//Debug.dbtime(1, 1, preIndexTime, postIndexTime,  "index rhs of transducer a");

		// index b rules by lhs sym, including eps
		Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>> rulesByLhs = new Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>>();
		preIndexTime = new Date();
		statecount = 0;
		for (Symbol s : b.states) {
			statecount++;
			if (statecount % 10000 == 0)
				if (debug) Debug.debug(debug, "indexing lhs of rules of "+statecount+" states");
			rulesByLhs.put(s, new Hashtable<Symbol, ArrayList<TransducerRule>>());
			for (TransducerRule r : b.getRulesOfType(s)) {
				Symbol lhssym = null;
				if (r.getLHS().isEpsilon())
					lhssym = Symbol.getEpsilon();
				else
					lhssym = r.getLHS().getLabel();
				if (!rulesByLhs.get(s).containsKey(lhssym))
					rulesByLhs.get(s).put(lhssym, new ArrayList<TransducerRule>());
				rulesByLhs.get(s).get(lhssym).add(r);
			}
		}
		postIndexTime = new Date();
		//Debug.dbtime(1, 1, preIndexTime, postIndexTime,  "index lhs of transducer b");



		Date readTime = new Date();
		// timers and counters for analyzing
		int rulecount = 0;
		int symcount = 0;
		int prunecount = 0;
		int per_rulecount = 0;
		int newrulecount = 0;
		int per_newrulecount = 0;
		long applytime = 0;
		long kbesttime = 0;
		long preptime = 0;
		long addtime = 0;
		long stmaketime = 0;
		long maketime = 0;

		//		startState = getPairState(a.getStartState(), b.getStartState());

		// maintain a list of new states to be processed
		ArrayList<VecSymbol> new_states = new ArrayList<VecSymbol>();

		// add the new start state to the list
		Vector<Symbol> startStateVec = new Vector<Symbol>();
		startStateVec.add(a.getStartState());
		startStateVec.add(b.getStartState());

		// maintain a map of states already processed
		// map the pair representation to symbol representation
		HashSet<VecSymbol> al_states = new HashSet<VecSymbol>();
		// add the new start state to the list
		startState = SymbolFactory.getVecSymbol(startStateVec);
		new_states.add((VecSymbol)startState);
		al_states.add((VecSymbol)startState);
		
		HashMap<Symbol, TransducerRightSide> amap = new HashMap<Symbol, TransducerRightSide>();
		HashMap<Symbol, StateTreePair> bmap = new HashMap<Symbol, StateTreePair>();
		HashMap<StateTreePair, Symbol> invBmap = new HashMap<StateTreePair, Symbol>();
		while (new_states.size() > 0) {
			// pop off the next state
			VecSymbol newState = new_states.remove(0);
			Symbol aState = newState.getVec().get(0);
			Symbol bState = newState.getVec().get(1);
			int rulesPerState = 0;


			// create a priority queue. not quite so useful if not beaming, but no biggee.
			PriorityQueue<TransducerRule> heap=new PriorityQueue<TransducerRule>(beam>0 ? beam : 50, new TransducerRuleComp());;

			// get rules based on b being input epsilon
			// if the state from a is q, for each rule in b r.x0: -> RHS, add q_r -> RHS
			// adjoin q to states in RHS

			if (b.isInEps() && rulesByLhs.get(bState).containsKey(Symbol.getEpsilon())) {
				// get all rules leading from transducer 1
				if (debug) Debug.debug(debug, "getting input epsilon rules for "+newState);
				for (TransducerRule br : rulesByLhs.get(bState).get(Symbol.getEpsilon())) {
					// if we're beaming and over the limit, short-circuit construction here
					if (beam>0 && 
							rulesPerState >= beam && 
							semiring.betteroreq(heap.peek().weight, br.getWeight())) {
						prunecount++;
						if (debug) Debug.debug(debug, "Beamed out potential epsilon rule without creating");
					}
					// create and add the new rule. potentially remove worst rule
					else {
						Date premake = new Date();
						TreeTransducerRule newrule = new TreeTransducerRule(this, newState, aState, 
								((TreeTransducerRule)br).getRHS(), br.getWeight());
						Date postmake = new Date();
						maketime += postmake.getTime()-premake.getTime();
						Date preadd = new Date();
						if (debug) Debug.debug(debug, "Added new composition epsilon rule "+newrule);
						heap.add(newrule);
						newrulecount++;
						per_newrulecount++;
						// remove worst rule
						if (beam>0 && rulesPerState >= beam) {
							prunecount++;
							TransducerRule reject = heap.poll();
							if (debug) Debug.debug(debug, "Removed least rule "+reject);
						}
						else
							rulesPerState++;
						Date postadd = new Date();
						addtime += postadd.getTime()-preadd.getTime();
					}
				}
			}
			// get all rules leading from transducer 1 with appropriate state vec
			if (debug) Debug.debug(debug, "getting normal rules for "+newState+" = "+newState.toInternalString());
			// out eps rules
			// if arule is q.LHS -> r.x0: and b state is s
			// add q_s.LHS -> r_s.x0:
			if (rulesByRhs.get(aState).containsKey(Symbol.getEpsilon())) {
				for (TransducerRule arule : rulesByRhs.get(aState).get(Symbol.getEpsilon())) {
					rulecount++;
					per_rulecount++;
					// if we're beaming and over the limit, short-circuit construction here
					if (beam>0 && 
							rulesPerState >= beam && 
							semiring.betteroreq(heap.peek().weight, arule.getWeight())) {
						prunecount++;
						if (debug) Debug.debug(debug, "Beamed out potential output epsilon rule without creating");
					}
					// create and add the new rule. potentially remove worst rule
					else {					
						if (debug) Debug.debug(debug, "doing output epsilon adds");
						Vector<Symbol> dststate = new Vector<Symbol>();
						dststate.add(((TreeTransducerRule)arule).getRHS().getState());
						dststate.add(bState);

						Date premake = new Date();
						TreeTransducerRule newrule =  new TreeTransducerRule(this, arule.getLHS(), ((TreeTransducerRule)arule).getRHS().getVariable(), 
								newState, SymbolFactory.getVecSymbol(dststate), arule.getWeight());
						Date postmake = new Date();
						maketime += postmake.getTime()-premake.getTime();
						Date preadd = new Date();
						if (debug) Debug.debug(debug, "Adding outEps rule "+newrule);
						heap.add(newrule);
						newrulecount++;
						per_newrulecount++;
						// remove worst rule
						if (beam>0 && rulesPerState >= beam) { 
							prunecount++;
							TransducerRule reject = heap.poll();
							if (debug) Debug.debug(debug, "Removed least rule "+reject);
						}
						else
							rulesPerState++;
						Date postadd = new Date();
						addtime += postadd.getTime()-preadd.getTime();
					}
				}
			}
			// now do rules that match asym(rhs) and bsym(lhs) -- either leftside or rightside
			if (isleftside) {
				for (Symbol asym : rulesByRhs.get(aState).keySet()) {
					symcount++;
					if (asym == Symbol.getEpsilon())
						continue;
					if (!rulesByLhs.get(bState).containsKey(asym))
						continue;
					for (TransducerRule arule : rulesByRhs.get(aState).get(asym)) {
						rulecount++;
						per_rulecount++;
						// form a mapped tree from the rule
						if (debug) Debug.debug(debug, "starting with "+arule);
						//										if (debug) Debug.debug(debug, "doing normal adds");
						Date preapply = new Date();
						TreeItem aTree = ((TreeTransducerRule)arule).getRightComposableTree(amap);
						// form an rtg from the domain of transducer b that starts with the appropriate b
						// start state
						RTGRuleSet outItems = new RTGRuleSet(aTree, 
								bState, 
								b, 
								amap.keySet(), 
								bmap, 
								invBmap);
						Date postapply = new Date();
						applytime += postapply.getTime()-preapply.getTime();
						Date prek = new Date();
						// TODO: beam these too!
						if (outItems.getNumRules() < 1) {
							Date postk = new Date();
							kbesttime += postk.getTime()-prek.getTime();
							continue;
						}
						if (!outItems.isFinite(false))
							throw new UnusualConditionException("Created infinite RTG by applying "+aTree+"; perhaps epsilon rules are involved?");

						// get each possible set of rules from b that matches the rhs of a
						String num = outItems.getNumberOfDerivations();
						//if (debug) Debug.debug(debug, num+" derivations");
						int numint = Integer.parseInt(num);
						if (numint < 1) {
							Date postk = new Date();
							kbesttime += postk.getTime()-prek.getTime();
							if (debug) Debug.debug(debug, "Couldn't make rule from "+aTree);
						}
						else {
							KBest k = new KBest(outItems);
							TreeItem[] list = (TreeItem[]) k.getKBestItems(numint);
							Date postk = new Date();
							kbesttime += postk.getTime()-prek.getTime();
							for (int i = 0; i < list.length; i++) {
								// if we're beaming and over the limit, short-circuit construction here
								// since the list is in order, short-circuit the rest of the adds, too.
								if (beam>0 && 
										rulesPerState >= beam && 
										semiring.betteroreq(heap.peek().weight, 
												semiring.times(arule.getWeight(), list[i].getWeight()))) {
									prunecount += (list.length - i);
									if (debug) Debug.debug(debug, "Beamed out potential normal rule (and all others from "+i+" to "+list.length+") without creating");
									break;
								}
								// make rule. potentially remove worst rule
								Date prestmake = new Date();
								if (debug) Debug.debug(debug, "Making rule out of "+list[i]+" with state "+newState);
								Date poststmake = new Date();
								stmaketime += poststmake.getTime()-prestmake.getTime();
								Date premake = new Date();
								TreeTransducerRule newrule = null;
								try {
									newrule = new TreeTransducerRule(this, 
											arule.getLHS(), 
											newState, 
											list[i], 
											semiring.times(arule.getWeight(), list[i].getWeight()), 
											amap, 
											bmap);		
								}
								catch (ImproperConversionException e) {
									throw new ImproperConversionException("Couldn't make rule from "+arule+": "+e.getMessage());
								}
								if (!isExtendedRHS && newrule.isExtendedRHS())
									isExtendedRHS = true;
								Date postmake = new Date();
								maketime += postmake.getTime()-premake.getTime();
								Date preadd = new Date();
								if (debug) Debug.debug(debug, "Adding normal rule "+newrule);
								heap.add(newrule);
								newrulecount++;
								per_newrulecount++;
								if (beam>0 && rulesPerState >= beam) { 
									prunecount++;
									TransducerRule reject = heap.poll();
									if (debug) Debug.debug(debug, "Removed least rule "+reject);
								}
								else 
									rulesPerState++;
								Date postadd = new Date();
								addtime += postadd.getTime()-preadd.getTime();
							}
						}
					}
				}
			}
			// rightside comp
			else {
				for (Symbol bsym : rulesByLhs.get(bState).keySet()) {
					symcount++;
					if (bsym == Symbol.getEpsilon())
						continue;
					if (!rulesByRhs.get(aState).containsKey(bsym))
						continue;
					for (TransducerRule brule : rulesByLhs.get(bState).get(bsym)) {
						rulecount++;
						per_rulecount++;
						// form a mapped tree from the rule
						if (debug) Debug.debug(debug, "starting with "+brule);
						//										if (debug) Debug.debug(debug, "doing normal adds");
						Date preapply = new Date();
						TreeItem bTree = brule.getLeftComposableTree(amap);
						// form an rtg from the range of transducer a that starts 
						// with the appropriate a start state
						RTGRuleSet outItems = new RTGRuleSet(a, 
								aState, 
								bTree, 
								amap.keySet(), 
								bmap, 
								invBmap);
						Date postapply = new Date();
						applytime += postapply.getTime()-preapply.getTime();
						Date prek = new Date();
						// TODO: beam these too!
						if (outItems.getNumRules() < 1) {
							Date postk = new Date();
							kbesttime += postk.getTime()-prek.getTime();
							continue;
						}
						if (!outItems.isFinite(false))
							throw new UnusualConditionException("Created infinite RTG by applying "+bTree+"; perhaps epsilon rules are involved?");

						// get each possible set of rules from b that matches the rhs of a
						String num = outItems.getNumberOfDerivations();
						//if (debug) Debug.debug(debug, num+" derivations");
						int numint = Integer.parseInt(num);
						if (numint < 1) {
							Date postk = new Date();
							kbesttime += postk.getTime()-prek.getTime();
							if (debug) Debug.debug(debug, "Couldn't make rule from "+bTree);
						}
						else {
							KBest k = new KBest(outItems);
							TreeItem[] list = (TreeItem[]) k.getKBestItems(numint);
							Date postk = new Date();
							kbesttime += postk.getTime()-prek.getTime();
							for (int i = 0; i < list.length; i++) {
								// if we're beaming and over the limit, short-circuit construction here
								// since the list is in order, short-circuit the rest of the adds, too.
								if (beam>0 && 
										rulesPerState >= beam && 
										semiring.betteroreq(heap.peek().weight, 
												semiring.times(brule.getWeight(), list[i].getWeight()))) {
									prunecount += (list.length - i);
									if (debug) Debug.debug(debug, "Beamed out potential normal rule (and all others from "+i+" to "+list.length+") without creating");
									break;
								}
								// make rule. potentially remove worst rule
								Date prestmake = new Date();
								if (debug) Debug.debug(debug, "Making rule out of "+list[i]+" with state "+newState);
								Date poststmake = new Date();
								stmaketime += poststmake.getTime()-prestmake.getTime();
								Date premake = new Date();
								TreeTransducerRule newrule = null;
								try {
									newrule = new TreeTransducerRule(this, 
											list[i], 
											newState, 
											((TreeTransducerRule)brule).getRHS(), 
											semiring.times(brule.getWeight(), list[i].getWeight()), 
											amap, 
											bmap);		
								}
								catch (ImproperConversionException e) {
									throw new ImproperConversionException("Couldn't make rule from "+brule+": "+e.getMessage());
								}
								if (!isExtendedRHS && newrule.isExtendedRHS())
									isExtendedRHS = true;
								Date postmake = new Date();
								maketime += postmake.getTime()-premake.getTime();
								Date preadd = new Date();
								if (debug) Debug.debug(debug, "Adding normal rule "+newrule);
								heap.add(newrule);
								newrulecount++;
								per_newrulecount++;
								if (beam>0 && rulesPerState >= beam) { 
									prunecount++;
									TransducerRule reject = heap.poll();
									if (debug) Debug.debug(debug, "Removed least rule "+reject);
								}
								else 
									rulesPerState++;
								Date postadd = new Date();
								addtime += postadd.getTime()-preadd.getTime();
							}
						}
					}
				}
			}

			if (debug) {
				//				if (rulecount % 500 == 0) {
				Date pause = new Date();
				long lapse = pause.getTime() - readTime.getTime();
				readTime = pause;
				String timeperrule = Rounding.round((lapse+0.0)/per_newrulecount, 2);
				String preprat = Rounding.round(100*(preptime+0.0)/lapse, 2);
				String applyrat = Rounding.round(100*(applytime+0.0)/lapse, 2);
				String krat = Rounding.round(100*(kbesttime+0.0)/lapse, 2);
				String makerat = Rounding.round(100*(maketime+0.0)/lapse, 2);
				String stmakerat = Rounding.round(100*(stmaketime+0.0)/lapse, 2);
				String addrat = Rounding.round(100*(addtime+0.0)/lapse, 2);
				Debug.prettyDebug("Composition processed "+per_rulecount+" rules, pruned "+prunecount+", and added "+per_newrulecount+" rules: "+lapse+" ms");
				Debug.prettyDebug(timeperrule+" ms per new rule. Processed "+rulecount+", "+symcount+" intermediate symbols;  added "+newrulecount+" rules; "+new_states.size()+" states left");
				Debug.prettyDebug(
						"Prep: "+preptime+"("+preprat+
						"%) Apply: "+applytime+"("+applyrat+
						"%) Kbest: "+kbesttime+"("+krat+
						"%) Make: "+maketime+"("+makerat+
						"%) STMake: "+stmaketime+"("+stmakerat+
						"%) Add: "+addtime+"("+addrat+"%)\n");
				preptime = applytime = kbesttime = stmaketime = maketime = addtime = 0;
				per_rulecount = 0;
				per_newrulecount = 0;
				prunecount = 0;
			}

			// add heap rules to the actual rule set
			while (!heap.isEmpty()) {
				TransducerRule r = heap.poll();
				if (debug) Debug.debug(debug, "Truly adding "+r);
				rules.add(r);
				// also add potential new states from newly added rules
				for (TransducerRightSide rhs : r.getTRVM().getRHSInOrder()) {
					if (rhs.hasState()) {
						VecSymbol nextst = (VecSymbol)rhs.getState();
						if (!al_states.contains(nextst)) {
							if (debug) Debug.debug(debug, "Adding "+nextst+" = "+nextst.toInternalString()+" to next state queue");
							al_states.add(nextst);
							new_states.add(nextst);
						}
					}
				}
			}
//			Debug.prettyDebug(new_states.size()+" states left to process");
		}
		// add all rulesByX
		for (TransducerRule newrule : rules) {
			if (!rulesByState.containsKey(newrule.getState())) {
				rulesByState.put(newrule.getState(), new ArrayList<TransducerRule>());
			}
			rulesByState.get(newrule.getState()).add(newrule);
			// add it to the index, too
			rulesByIndex.put(newrule.getIndex(), newrule);
			if (newrule.getTie() > 0) {
				if (!rulesByTie.containsKey(newrule.getTie()))
					rulesByTie.put(newrule.getTie(), new ArrayList<TransducerRule>());
				((ArrayList)rulesByTie.get(newrule.getTie())).add(newrule);
			}
		}
		// al_states is the states list
		states = new HashSet<Symbol>(al_states);
	}

	
	
	
	
	
	// n-way composition via forward or backward application
	// beam is max number of rules added from each state
	// all potential rules are inspected, but only highest weighted are kept,
	// thanks to a priority queue
	// beam of 0 means all rules are added

	

	public TreeTransducerRuleSet(Vector<TreeTransducerRuleSet> trslist, boolean isLeftside, int beam) throws ImproperConversionException, UnusualConditionException {
		super();

		boolean debug = false;
		boolean memoize = false;
		if (debug) Debug.debug(debug, "Composing "+trslist.size()+" transducers");

		semiring = trslist.get(0).getSemiring();

		// determine order of composition when adding "normal" rules. 
		// leftside (default or when a is extended rhs) = given the rhs of rules from a,
		//          find the rtg of rules from b
		// rightside (when b is extended lhs) = given the lhs of rules from b, find the rtg
		//          of rules from a.
	
		// leftside: index all by lhs except first. index first by rhs
		// rightside: index all by rhs except last. index last by lhs
		

	
		Vector<Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>>> rulesByRhsVec = new Vector<Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>>>();
		Vector<Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>>> rulesByLhsVec = new Vector<Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>>>();

		for (int i = 0; i < trslist.size(); i++) {
			TreeTransducerRuleSet trs = trslist.get(i);
			Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>> rulesByRhs = new Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>>();
			Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>> rulesByLhs = new Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>>();

			Date preIndexTime = new Date();
			int statecount = 0;
			for (Symbol s : trs.states) {
				statecount++;
				if (statecount % 10000 == 0)
					if (debug) Debug.debug(debug, "indexing rules of "+statecount+" states");
				if ((i < trslist.size()-1 && !isLeftside) || (i == 0 && isLeftside)) 
					rulesByRhs.put(s, new Hashtable<Symbol, ArrayList<TransducerRule>>());
				if ((i > 0 && isLeftside) || (i == trslist.size()-1 && !isLeftside))
					rulesByLhs.put(s, new Hashtable<Symbol, ArrayList<TransducerRule>>());
				for (TransducerRule r : trs.getRulesOfType(s)) {
					// rhs indexing
					if ((i < trslist.size()-1 && !isLeftside) || (i == 0 && isLeftside))  {
						Symbol rhssym = null;
						if (r.rhs.isEpsilon())
							rhssym = Symbol.getEpsilon();
						else
							rhssym = r.rhs.getLabel();
						if (!rulesByRhs.get(s).containsKey(rhssym))
							rulesByRhs.get(s).put(rhssym, new ArrayList<TransducerRule>());
						rulesByRhs.get(s).get(rhssym).add(r);
					}
					// lhs indexing
					if ((i > 0 && isLeftside) || (i == trslist.size()-1 && !isLeftside)){
						Symbol lhssym = null;
						if (r.getLHS().isEpsilon())
							lhssym = Symbol.getEpsilon();
						else
							lhssym = r.getLHS().getLabel();
						if (!rulesByLhs.get(s).containsKey(lhssym))
							rulesByLhs.get(s).put(lhssym, new ArrayList<TransducerRule>());
						rulesByLhs.get(s).get(lhssym).add(r);
					}
				}
			}
			Date postIndexTime = new Date();
			rulesByRhsVec.add(rulesByRhs);
			rulesByLhsVec.add(rulesByLhs);

//			Debug.dbtime(1, 1, preIndexTime, postIndexTime,  "index transducer "+i);
		}
		

		// maintain a list of new states to be processed
		ArrayList<VecSymbol> new_states = new ArrayList<VecSymbol>();

		// add the new start state to the list
		Vector<Symbol> startStateVec = new Vector<Symbol>();
		for (TreeTransducerRuleSet trs: trslist) {
			startStateVec.add(trs.getStartState());
		}
	

		// maintain a map of states already processed
		// map the pair representation to symbol representation
		HashSet<VecSymbol> al_states = new HashSet<VecSymbol>();
		// add the new start state to the list
		startState = SymbolFactory.getVecSymbol(startStateVec);
		new_states.add((VecSymbol)startState);
		al_states.add((VecSymbol)startState);
		
		
		// archive the partial rules to avoid having to re-compose
		HashMap<VecSymbol, PriorityQueue<LeftTransducerRuleElements>> leftMemo = new HashMap<VecSymbol, PriorityQueue<LeftTransducerRuleElements>>();
		HashMap<VecSymbol, PriorityQueue<RightTransducerRuleElements>> rightMemo = new HashMap<VecSymbol, PriorityQueue<RightTransducerRuleElements>>();


		while (new_states.size() > 0) {
			//Debug.prettyDebug(new_states.size()+" states left");
			// pop off the next state
			VecSymbol newState = new_states.remove(0);
			// build lhs, rhs pairs with first/last two transducers, just like in classical composition.
			// then use each successive transducer to modify, augment, or eliminate rules. at end,
			// join the lhs and rhs to form new rules and recover new states

			int aNum=0;
			int bNum=0;
			if (isLeftside) {
				aNum = 0;
				bNum = 1;
			}
			else {
				aNum = trslist.size()-2;
				bNum = trslist.size()-1;
			}
			Symbol aState = newState.getVec().get(aNum);
			Symbol bState = newState.getVec().get(bNum);
			TreeTransducerRuleSet a = trslist.get(aNum);
			TreeTransducerRuleSet b = trslist.get(bNum);
			Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>> rulesByRhs = rulesByRhsVec.get(aNum);
			Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>> rulesByLhs = rulesByLhsVec.get(bNum);

	
			// left and right branches. Essentially identical except for the methods called and
			// the constructing of rules
			if (isLeftside) {
				Vector<Symbol> vec = new Vector<Symbol>();
				vec.add(aState);
				vec.add(bState);
				VecSymbol memovec = SymbolFactory.getVecSymbol(vec);
				PriorityQueue<LeftTransducerRuleElements> ruletable = null;
				if (memoize && leftMemo.containsKey(memovec)) {
					ruletable = leftMemo.get(memovec);
				}
				else {
					// get the rule table from the first two transducers
					ruletable = initLeftComposition(aState, bState, b, rulesByRhs, rulesByLhs, beam);
					if (memoize)
						leftMemo.put(memovec, ruletable);
				}
				//Debug.prettyDebug(ruletable.size()+" in table");

				// now, use each successive transducer to modify the ruletable by passing all TRTs through.
				for (int j = 2; j < trslist.size(); j++) {

					TreeTransducerRuleSet trs = trslist.get(j);
					Symbol state = newState.getVec().get(j);
					memovec = SymbolFactory.getVecSymbol(memovec,state);
					rulesByLhs = rulesByLhsVec.get(j);
					if (memoize && leftMemo.containsKey(memovec))
						ruletable = leftMemo.get(memovec);
					else {
						ruletable = getLeftComposition(ruletable, state, trs, rulesByLhs, beam);
						if (memoize)
							leftMemo.put(memovec, ruletable);
					}
					//Debug.prettyDebug(ruletable.size()+" in table");

				}
				// now, turn each of the elements in the rule table into a real rule, add that rule, and figure out new
				// states
				// do beaming here!
				
				int rulesAdded = 0;
				while ((rulesAdded < beam || beam == 0) && !ruletable.isEmpty()) {
					LeftTransducerRuleElements tre = ruletable.poll();
					Date premake = new Date();
					TreeTransducerRule newrule = null;
					try {
						newrule = new TreeTransducerRule(this, 
								tre.lhs, 
								newState, 
								(TreeItem)tre.rhs, 
								tre.weight);		
					}
					catch (ImproperConversionException e) {
						throw new ImproperConversionException("Couldn't make rule from "+tre.lhs+", "+tre.rhs+": "+e.getMessage());
					}
					rules.add(newrule);
					rulesAdded++;
					if (!isExtendedRHS && newrule.isExtendedRHS())
						isExtendedRHS = true;
					Date postmake = new Date();
					// also add potential new states from newly added rules
					for (TransducerRightSide rhs : newrule.getTRVM().getRHSInOrder()) {
						if (rhs.hasState()) {
							VecSymbol nextst = (VecSymbol)rhs.getState();
							if (!al_states.contains(nextst)) {
								if (debug) Debug.debug(debug, "Adding "+nextst+" = "+nextst.toInternalString()+" to next state queue");
								al_states.add(nextst);
								new_states.add(nextst);
							}
						}
					}
				}
				if (debug) Debug.debug(debug, "Added "+rulesAdded+" rules; left "+ruletable.size());
				ruletable = null;
			}
			else {
				Vector<Symbol> vec = new Vector<Symbol>();
				// rest are pushed!
				vec.add(aState);
				vec.add(bState);
				VecSymbol memovec = SymbolFactory.getVecSymbol(vec);
				// get the rule table from the last two transducers
				PriorityQueue<RightTransducerRuleElements> ruletable = null;
				if (memoize && rightMemo.containsKey(memovec)) {
//					Debug.prettyDebug("Using memoization for right table "+memovec);
					ruletable = rightMemo.get(memovec);
				}
				else {
//					Debug.prettyDebug("Not Using memoization for right table "+memovec);

					// get the rule table from the first two transducers
					ruletable = initRightComposition(aState, bState, a, rulesByRhs, rulesByLhs, beam);
					if (memoize)
						rightMemo.put(memovec, ruletable);
				}
//				Debug.prettyDebug(ruletable.size()+" in table");

				// now, use each successive transducer to modify the ruletable by passing all TRTs through.
				for (int j = trslist.size()-3; j >= 0; j--) {

					TreeTransducerRuleSet trs = trslist.get(j);
					Symbol state = newState.getVec().get(j);
					memovec = SymbolFactory.getVecSymbol(state,memovec);
					rulesByRhs = rulesByRhsVec.get(j);
					if (memoize && rightMemo.containsKey(memovec)) {
//						Debug.prettyDebug("Using memoization for right table "+memovec);
						ruletable = rightMemo.get(memovec);
					}
					else {
//						Debug.prettyDebug("Not Using memoization for right table "+memovec);
						ruletable = getRightComposition(ruletable, state, trs, rulesByRhs, beam);
						if (memoize)
							rightMemo.put(memovec, ruletable);
					}
//					Debug.prettyDebug(ruletable.size()+" in table");
				}
				// now, turn each of the elements in the rule table into a real rule, add that rule, and figure out new
				// states
				// do beaming here
				int rulesAdded = 0;
				while ((rulesAdded < beam || beam == 0) && !ruletable.isEmpty()) {
					RightTransducerRuleElements tre = ruletable.poll();
			
					Date premake = new Date();
					TreeTransducerRule newrule = null;
					try {
						newrule = new TreeTransducerRule(this, 
								tre.lhs, 
								newState, 
								(TransducerRightTree)tre.rhs, 
								tre.weight);		
					}
					catch (ImproperConversionException e) {
						throw new ImproperConversionException("Couldn't make rule from "+tre.lhs+", "+tre.rhs+": "+e.getMessage());
					}
					if (debug) Debug.debug(debug, "Added rule "+newrule);
					rules.add(newrule);
					rulesAdded++;
					if (!isExtendedRHS && newrule.isExtendedRHS())
						isExtendedRHS = true;
					Date postmake = new Date();
					// also add potential new states from newly added rules
					for (TransducerRightSide rhs : newrule.getTRVM().getRHSInOrder()) {
						if (rhs.hasState()) {
							VecSymbol nextst = (VecSymbol)rhs.getState();
							if (!al_states.contains(nextst)) {
								if (debug) Debug.debug(debug, "Adding "+nextst+" = "+nextst.toInternalString()+" to next state queue");
								al_states.add(nextst);
								new_states.add(nextst);
							}
						}
					}
				}
				if (debug) Debug.debug(debug, "Added "+rulesAdded+" rules; left "+ruletable.size());
				ruletable = null;
			}
				
			
			//			Debug.prettyDebug(new_states.size()+" states left to process");
		}
		// add all rulesByX
		for (TransducerRule newrule : rules) {
			if (!rulesByState.containsKey(newrule.getState())) {
				rulesByState.put(newrule.getState(), new ArrayList<TransducerRule>());
			}
			rulesByState.get(newrule.getState()).add(newrule);
			// add it to the index, too
			rulesByIndex.put(newrule.getIndex(), newrule);
			if (newrule.getTie() > 0) {
				if (!rulesByTie.containsKey(newrule.getTie()))
					rulesByTie.put(newrule.getTie(), new ArrayList<TransducerRule>());
				((ArrayList)rulesByTie.get(newrule.getTie())).add(newrule);
			}
		}
		// al_states is the states list
		states = new HashSet<Symbol>(al_states);
	}

	
	// leftside composition of the first two tree transducers in a chain.
	static PriorityQueue<LeftTransducerRuleElements> initLeftComposition(	
			Symbol aState,
			Symbol bState,
			TreeTransducerRuleSet b,
			Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>> rulesByRhs,
			Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>> rulesByLhs,
			int beam) throws UnusualConditionException{
		boolean debug=false;
		
		// track actions
		int prunecount=0;
		
		long maketime=0;
		long addtime=0;
		long applytime=0;
		long kbesttime=0;
		
		Date startTime = new Date();
		// vector of "processed" transducer states for epsilon input handling
		Vector<Symbol> inEpsVec = new Vector<Symbol>();
		inEpsVec.add(aState);
		
		int rulesPerState = 0;
		//Debug.prettyDebug("Setting beam to 0 in inner composition (remove me!)");
		beam = 0;

		// create a priority queue. not quite so useful if not beaming, but no biggee.
		PriorityQueue<LeftTransducerRuleElements> heap=LeftTransducerRuleElements.getHeap(beam);

		
		// THIS SHOULD BE HANDLED BY initRight!
		
//		// get rules based on b being input epsilon
//		// if the state from a is q, for each rule in b r.x0: -> RHS, add q_r -> RHS
//		// adjoin q to states in RHS
//
//		if (rulesByLhs.get(bState).containsKey(Symbol.getEpsilon())) {
//			// get all rules leading from transducer 1
//			if (debug) Debug.debug(debug, "getting input epsilon rules from "+bState);
//			for (TransducerRule br : rulesByLhs.get(bState).get(Symbol.getEpsilon())) {
//				// if we're beaming and over the limit, short-circuit construction here
//				if (beam>0 && 
//						rulesPerState >= beam && 
//						semiring.betteroreq(heap.peek().weight, br.getWeight())) {
//					prunecount++;
//					if (debug) Debug.debug(debug, "Beamed out potential epsilon rule without creating");
//				}
//				// create and add the new rule. potentially remove worst rule
//				else {
//					Date premake = new Date();
//					// store the lhs, rhs, and weight
//					LeftTransducerRuleElements newrule = new LeftTransducerRuleElements(new TransducerLeftTree(0), 
//																				((TreeTransducerRule)br).getEpsInputRightComposableTree(inEpsVec), 
//																				br.getWeight(),
//																				semiring);
//					Date postmake = new Date();
//					maketime += postmake.getTime()-premake.getTime();
//					Date preadd = new Date();
//					if (debug) Debug.debug(debug, "Added new composition epsilon rule "+newrule);
//					heap.add(newrule);
//
//					// remove worst rule
//					if (beam>0 && rulesPerState >= beam) {
//						prunecount++;
//						LeftTransducerRuleElements reject = heap.poll();
//						if (debug) Debug.debug(debug, "Removed least rule "+reject);
//					}
//					else
//						rulesPerState++;
//					Date postadd = new Date();
//					addtime += postadd.getTime()-preadd.getTime();
//				}
//			}
//		}
		

		// get all rules leading from transducer 1 with appropriate state vec
		if (debug) Debug.debug(debug, "getting normal rules from "+aState+" to "+bState);

		// out eps rules
		// if arule is q.LHS -> r.x0: and b state is s
		// add q_s.LHS -> r_s.x0:
		if (rulesByRhs.get(aState).containsKey(Symbol.getEpsilon())) {
			for (TransducerRule arule : rulesByRhs.get(aState).get(Symbol.getEpsilon())) {
				// if we're beaming and over the limit, short-circuit construction here
				if (beam>0 && 
						rulesPerState >= beam && 
						arule.getSemiring().betteroreq(heap.peek().weight, arule.getWeight())) {
					prunecount++;
					if (debug) Debug.debug(debug, "Beamed out potential output epsilon rule without creating");
				}
				// create and add the new rule. potentially remove worst rule
				else {					
					if (debug) Debug.debug(debug, "doing output epsilon adds");

					Date premake = new Date();
					// get variable name from original rule but for state ordering pass the b state
					// then adjoin in the a state
					LeftTransducerRuleElements newrule =  new LeftTransducerRuleElements(arule.getLHS(),
																				((TreeTransducerRule)arule).getRHS().getEpsOutputVecVarImageTree(bState),
																				arule.getWeight(),
																				arule.getSemiring());
					Date postmake = new Date();
					maketime += postmake.getTime()-premake.getTime();
					Date preadd = new Date();
					if (debug) Debug.debug(debug, "Adding outEps rule "+newrule);
					heap.add(newrule);
					// remove worst rule
					if (beam>0 && rulesPerState >= beam) { 
						prunecount++;
						LeftTransducerRuleElements reject = heap.poll();
						if (debug) Debug.debug(debug, "Removed least rule "+reject);
					}
					else
						rulesPerState++;
					Date postadd = new Date();
					addtime += postadd.getTime()-preadd.getTime();
				}
			}
		}
		
		// now do rules that match asym(rhs) and bsym(lhs) 

		for (Symbol asym : rulesByRhs.get(aState).keySet()) {
			if (asym == Symbol.getEpsilon())
				continue;
			if (!rulesByLhs.get(bState).containsKey(asym))
				continue;
			for (TransducerRule arule : rulesByRhs.get(aState).get(asym)) {
				// form a mapped tree from the rule
				if (debug) Debug.debug(debug, "starting with "+arule);
				//										if (debug) Debug.debug(debug, "doing normal adds");
				Date preapply = new Date();
				// tree has special leaf symbols with variable, state vectors
				TreeItem aTree = ((TreeTransducerRule)arule).getRightComposableTree();
				// form an rtg from the domain of transducer b that starts with the appropriate b
				// start state
				// aTree has special leaves, and so do the trees in this rtg 
				RTGRuleSet outItems = new RTGRuleSet(aTree, bState, b);
				
				Date postapply = new Date();
				applytime += postapply.getTime()-preapply.getTime();
				Date prek = new Date();
				// TODO: beam these too!
				if (outItems.getNumRules() < 1) {
					Date postk = new Date();
					kbesttime += postk.getTime()-prek.getTime();
					continue;
				}
				if (!outItems.isFinite(false))
					throw new UnusualConditionException("Created infinite RTG by applying "+aTree+"; perhaps epsilon rules are involved?");

				// get each possible set of rules from b that matches the rhs of a
				String num = outItems.getNumberOfDerivations();
				//if (debug) Debug.debug(debug, num+" derivations");
				int numint = Integer.parseInt(num);
				if (numint < 1) {
					Date postk = new Date();
					kbesttime += postk.getTime()-prek.getTime();
					if (debug) Debug.debug(debug, "Couldn't make rule from "+aTree);
				}
				else {
					KBest k = new KBest(outItems);
					TreeItem[] list = (TreeItem[]) k.getKBestItems(numint);
					Date postk = new Date();
					kbesttime += postk.getTime()-prek.getTime();
					for (int i = 0; i < list.length; i++) {
						// if we're beaming and over the limit, short-circuit construction here
						// since the list is in order, short-circuit the rest of the adds, too.
						if (beam>0 && 
								rulesPerState >= beam && 
								arule.getSemiring().betteroreq(heap.peek().weight, 
										arule.getSemiring().times(arule.getWeight(), list[i].getWeight()))) {
							prunecount += (list.length - i);
							if (debug) Debug.debug(debug, "Beamed out potential normal rule (and all others from "+i+" to "+list.length+") without creating");
							break;
						}
						// make rule. potentially remove worst rule
						if (debug) Debug.debug(debug, "Making rule out of "+list[i]);

						Date premake = new Date();
						
						// store the lhs, rhs, and weight
						LeftTransducerRuleElements newrule = new LeftTransducerRuleElements(arule.getLHS(), 
																					list[i], 
																					arule.getSemiring().times(arule.getWeight(), list[i].getWeight()),
																					arule.getSemiring());
						


						Date postmake = new Date();
						maketime += postmake.getTime()-premake.getTime();
						Date preadd = new Date();
						if (debug) Debug.debug(debug, "Adding normal rule "+newrule);
						heap.add(newrule);
						if (beam>0 && rulesPerState >= beam) { 
							prunecount++;
							LeftTransducerRuleElements reject = heap.poll();
						}
						else 
							rulesPerState++;
						Date postadd = new Date();
						addtime += postadd.getTime()-preadd.getTime();
					}
				}
			}
		}


		if (debug) {
			//				if (rulecount % 500 == 0) {
			Date pause = new Date();
			long lapse = pause.getTime() - startTime.getTime();
			String applyrat = Rounding.round(100*(applytime+0.0)/lapse, 2);
			String krat = Rounding.round(100*(kbesttime+0.0)/lapse, 2);
			String makerat = Rounding.round(100*(maketime+0.0)/lapse, 2);
			String addrat = Rounding.round(100*(addtime+0.0)/lapse, 2);
			Debug.prettyDebug(
					" Apply: "+applytime+"("+applyrat+
					"%) Kbest: "+kbesttime+"("+krat+
					"%) Make: "+maketime+"("+makerat+
					"%) Add: "+addtime+"("+addrat+"%)\n");
			applytime = kbesttime = maketime = addtime = 0;
			
			
			prunecount = 0;
		}

		// hold onto the elements
		return heap;
//		return new Vector<LeftTransducerRuleElements>(heap);		
	}
	
	
	// leftside composition of the next tree transducer in the middle of a chain
	// assume each rule has size-one lhs
	static PriorityQueue<LeftTransducerRuleElements> getLeftComposition(
			PriorityQueue<LeftTransducerRuleElements> ruletable,
			Symbol state,
			TreeTransducerRuleSet trs,
			Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>> rulesByLhs,
			int beam) throws UnusualConditionException{
		boolean debug = false;
		int rulesPerState = 0;

		// track actions
		int prunecount=0;

		long maketime=0;
		long addtime=0;
		long applytime=0;
		long kbesttime=0;

		Date startTime = new Date();
	//	Debug.prettyDebug("Setting beam to 0 in inner composition (remove me!)");
		beam = 0;
		PriorityQueue<LeftTransducerRuleElements> heap=LeftTransducerRuleElements.getHeap(beam);

		// INPUT EPSILON SHOULD BE HANDLED BY RIGHTSIDE
		
		// get rules based on b being input epsilon
		// if the state from a is q, for each rule in b r.x0: -> RHS, add q_r.x0: -> RHS
		// adjoin q to states in RHS

//		if (trs.isInEps() && rulesByLhs.get(state).containsKey(Symbol.getEpsilon())) {
//			// get all rules that are thus far still viable
//			if (debug) Debug.debug(debug, "getting input epsilon rules for "+state);
//			for (TransducerRule rule : rulesByLhs.get(state).get(Symbol.getEpsilon())) {
//				// if we're beaming and over the limit, short-circuit construction here
//				if (beam>0 && 
//						rulesPerState >= beam && 
//						semiring.betteroreq(heap.peek().weight, rule.getWeight())) {
//					prunecount++;
//					if (debug) Debug.debug(debug, "Beamed out potential epsilon rule without creating");
//				}
//				// create and add the new rule. potentially remove worst rule
//				else {
//					Date premake = new Date();
//					// store the lhs, rhs, and weight
//					LeftTransducerRuleElements newrule = new LeftTransducerRuleElements(new TransducerLeftTree(0), 
//							((TreeTransducerRule)rule).getEpsInputRightComposableTree(inEpsVec), 
//							rule.getWeight(),
//							semiring);
//					Date postmake = new Date();
//					maketime += postmake.getTime()-premake.getTime();
//					Date preadd = new Date();
//					if (debug) Debug.debug(debug, "Added new composition epsilon rule "+newrule);
//					heap.add(newrule);
//
//					// remove worst rule
//					if (beam>0 && rulesPerState >= beam) {
//						prunecount++;
//						LeftTransducerRuleElements reject = heap.poll();
//						if (debug) Debug.debug(debug, "Removed least rule "+reject);
//					}
//					else
//						rulesPerState++;
//					Date postadd = new Date();
//					addtime += postadd.getTime()-preadd.getTime();
//				}
//			}
//		}
		
			// get all rules leading from transducer 1

		// get all rules that match the rhs set we have
		for (LeftTransducerRuleElements tre : ruletable) {
			TreeItem trt = (TreeItem)tre.rhs;
			// out eps rules
			// if trt is variable, add in the next state
			// add q_s.LHS -> r_s.x0:
			if (trt.label instanceof VecSymbol) {
				// if we're beaming and over the limit, short-circuit construction here
				if (beam>0 && 
						rulesPerState >= beam && 
						trs.getSemiring().betteroreq(heap.peek().weight, tre.weight)) {
					prunecount++;
					if (debug) Debug.debug(debug, "Beamed out potential output epsilon rule without creating");
				}
				// create and add the new rule. potentially remove worst rule
				else {
					// get variable name from original rule but for state ordering pass the b state
					// then adjoin in the a state
					Date premake = new Date();
					LeftTransducerRuleElements newrule =  new LeftTransducerRuleElements(tre.lhs,
																				new TreeItem(SymbolFactory.getVecSymbol((VecSymbol)trt.label, state)),
																				tre.weight,
																				tre.semiring);
					Date postmake = new Date();
					maketime += postmake.getTime()-premake.getTime();
					Date preadd = new Date();
					if (debug) Debug.debug(debug, "Adding outEps rule "+newrule);
					heap.add(newrule);

					// remove worst rule
					if (beam>0 && rulesPerState >= beam) { 
						prunecount++;
						LeftTransducerRuleElements reject = heap.poll();
						if (debug) Debug.debug(debug, "Removed least rule "+reject);
					}
					else
						rulesPerState++;
					Date postadd = new Date();
					addtime += postadd.getTime()-preadd.getTime();
				}
			}
			else {

				// if we can't match the output so far, this partial rule dies
				if (!rulesByLhs.get(state).containsKey(trt.getLabel())) 
					continue;
				if (debug) Debug.debug(debug, "Trying to match "+trt);
				// now figure out where we can go with this transducer
				Date preapply = new Date();
				RTGRuleSet outItems = new RTGRuleSet(trt, state, trs);
				if (outItems.getNumRules() < 1) {
					continue;
				}
				if (!outItems.isFinite(false))
					throw new UnusualConditionException("Created infinite RTG by applying "+trt+"; perhaps epsilon rules are involved?");
				String num = outItems.getNumberOfDerivations();

				int numint = Integer.parseInt(num);
				
				Date postapply = new Date();
				applytime += postapply.getTime()-preapply.getTime();
				if (numint < 1) {
					if (debug) Debug.debug(debug, "Couldn't make rule from "+trt);
				}
				else {
					KBest k = new KBest(outItems);
					TreeItem[] list = (TreeItem[]) k.getKBestItems(numint);
					for (int i = 0; i < list.length; i++) {
						// if we're beaming and over the limit, short-circuit construction here
						// since the list is in order, short-circuit the rest of the adds, too.
						if (beam>0 && 
								rulesPerState >= beam && 
								trs.getSemiring().betteroreq(heap.peek().weight, 
										trs.getSemiring().times(tre.weight, list[i].getWeight()))) {
							prunecount += (list.length - i);
							if (debug) Debug.debug(debug, "Beamed out potential normal rule (and all others from "+i+" to "+list.length+") without creating");
							break;
						}
						// make rule. potentially remove worst rule
						if (debug) Debug.debug(debug, "Making rule out of "+list[i]);
						// store the lhs, rhs, and weight
						LeftTransducerRuleElements newrule = new LeftTransducerRuleElements(tre.lhs, 
								list[i], 
								trs.getSemiring().times(tre.weight, list[i].getWeight()),
								trs.getSemiring());

						heap.add(newrule);
	
						if (beam>0 && rulesPerState >= beam) { 
							prunecount++;
							heap.poll();
						}
						else 
							rulesPerState++;
					}
				}
			}
		}
		if (debug) {
			//				if (rulecount % 500 == 0) {
			Date pause = new Date();
			long lapse = pause.getTime() - startTime.getTime();
			String applyrat = Rounding.round(100*(applytime+0.0)/lapse, 2);
			String krat = Rounding.round(100*(kbesttime+0.0)/lapse, 2);
			String makerat = Rounding.round(100*(maketime+0.0)/lapse, 2);
			String addrat = Rounding.round(100*(addtime+0.0)/lapse, 2);
			Debug.prettyDebug(
					" Apply: "+applytime+"("+applyrat+
					"%) Kbest: "+kbesttime+"("+krat+
					"%) Make: "+maketime+"("+makerat+
					"%) Add: "+addtime+"("+addrat+"%)\n");
			applytime = kbesttime = maketime = addtime = 0;
			
			
			prunecount = 0;
		}
		// hold on to the elements
		return heap;
//		return new Vector<LeftTransducerRuleElements>(heap);

	}

	
	// rightside composition of the last two tree transducers in a chain
	
	static PriorityQueue<RightTransducerRuleElements> initRightComposition(	
			Symbol aState,
			Symbol bState,
			TreeTransducerRuleSet a,
			Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>> rulesByRhs,
			Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>> rulesByLhs,
			int beam) throws UnusualConditionException{
		boolean debug=false;
		
		// track actions
		int prunecount=0;
		
		long maketime=0;
		long addtime=0;
		long applytime=0;
		long kbesttime=0;
		
		Date startTime = new Date();

		
		int rulesPerState = 0;

		//Debug.prettyDebug("Setting beam to 0 in inner composition (remove me!)");
		beam = 0;

		// create a priority queue. not quite so useful if not beaming, but no biggee.
		PriorityQueue<RightTransducerRuleElements> heap=RightTransducerRuleElements.getHeap(beam);

		
		
		// get rules based on b being input epsilon
		// if the state from a is q, for each rule in b r.x0: -> RHS, add q_r.x0: -> RHS
		// adjoin q to states in RHS

		if (rulesByLhs.get(bState).containsKey(Symbol.getEpsilon())) {
			// get all rules leading from transducer 1
			if (debug) Debug.debug(debug, "getting input epsilon rules from "+bState);
			for (TransducerRule br : rulesByLhs.get(bState).get(Symbol.getEpsilon())) {
				// if we're beaming and over the limit, short-circuit construction here
				if (beam>0 && 
						rulesPerState >= beam && 
						a.getSemiring().betteroreq(heap.peek().weight, br.getWeight())) {
					prunecount++;
					if (debug) Debug.debug(debug, "Beamed out potential epsilon rule without creating");
				}
				// create and add the new rule. potentially remove worst rule
				else {
					Date premake = new Date();
					// store the lhs, rhs, and weight
					RightTransducerRuleElements newrule = new RightTransducerRuleElements(
							br.getLHS().toTree(),
							new TransducerRightTree(((TreeTransducerRule)br).getRHS(), aState),
							br.getWeight(),
							a.getSemiring());
					Date postmake = new Date();
					maketime += postmake.getTime()-premake.getTime();
					Date preadd = new Date();
					if (debug) Debug.debug(debug, "Added new composition epsilon rule "+newrule);
					heap.add(newrule);

					// remove worst rule
					if (beam>0 && rulesPerState >= beam) {
						prunecount++;
						RightTransducerRuleElements reject = heap.poll();
						if (debug) Debug.debug(debug, "Removed least rule "+reject);
					}
					else
						rulesPerState++;
					Date postadd = new Date();
					addtime += postadd.getTime()-preadd.getTime();
				}
			}
		}
		

		// get all rules leading from transducer 1 with appropriate state vec
		if (debug) Debug.debug(debug, "getting normal rules from "+aState+" to "+bState);


		// output epsilon composition not allowed for rightside comp

		// except for eps-eps, which are handled below
		// out eps rules
		// if arule is q.x0: -> r.x0 and b state is s
		// add q_s.x0: -> r_s.x0:
		if (rulesByRhs.get(aState).containsKey(Symbol.getEpsilon())) {
			for (TransducerRule arule : rulesByRhs.get(aState).get(Symbol.getEpsilon())) {
				if (!arule.isInEps())
					continue;
				TreeTransducerRule ar = (TreeTransducerRule)arule;
				// if we're beaming and over the limit, short-circuit construction here
				if (beam>0 && 
						rulesPerState >= beam && 
						arule.getSemiring().betteroreq(heap.peek().weight, arule.getWeight())) {
					prunecount++;
					if (debug) Debug.debug(debug, "Beamed out potential output epsilon rule without creating");
				}
				// create and add the new rule. potentially remove worst rule
				else {					
					if (debug) Debug.debug(debug, "doing output epsilon adds");

					Date premake = new Date();
					// pass bState into rhs 
					Vector<Symbol> vec = new Vector<Symbol>();
					vec.add(ar.getRHS().getState());
					vec.add(bState);
					RightTransducerRuleElements newrule =  new RightTransducerRuleElements(arule.getLHS().toTree(),
							new TransducerRightTree(ar.getRHS().getVariable(), SymbolFactory.getVecSymbol(vec)),
							arule.getWeight(),
							arule.getSemiring());
					Date postmake = new Date();
					maketime += postmake.getTime()-premake.getTime();
					Date preadd = new Date();
					if (debug) Debug.debug(debug, "Adding outEps rule "+newrule);
					heap.add(newrule);
					// remove worst rule
					if (beam>0 && rulesPerState >= beam) { 
						prunecount++;
						RightTransducerRuleElements reject = heap.poll();
						if (debug) Debug.debug(debug, "Removed least rule "+reject);
					}
					else
						rulesPerState++;
					Date postadd = new Date();
					addtime += postadd.getTime()-preadd.getTime();
				}
			}
		}
		// now do rules that match asym(rhs) and bsym(lhs) 
		// assume the lhs of b is big and must be matched to several rhs of a
		
		for (Symbol bsym : rulesByLhs.get(bState).keySet()) {
			if (bsym == Symbol.getEpsilon())
				continue;
			if (!rulesByRhs.get(aState).containsKey(bsym))
				continue;
			for (TransducerRule brule : rulesByLhs.get(bState).get(bsym)) {
				// form a mapped tree from the rule
				if (debug) Debug.debug(debug, "starting with "+brule);
				//										if (debug) Debug.debug(debug, "doing normal adds");
				Date preapply = new Date();
				
				
				// form an rtg from the range of transducer a that starts with the appropriate a
				// start state
				// match the brule lhs trees in this rtg 
				TreeItem bTree = brule.getLHS().toTree();
				RTGRuleSet outItems = new RTGRuleSet(a, aState, bTree);
				
				Date postapply = new Date();
				applytime += postapply.getTime()-preapply.getTime();
				Date prek = new Date();
				// TODO: beam these too!
				if (outItems.getNumRules() < 1) {
					Date postk = new Date();
					kbesttime += postk.getTime()-prek.getTime();
					continue;
				}
				if (!outItems.isFinite(false))
					throw new UnusualConditionException("Created infinite RTG by applying "+bTree+"; perhaps epsilon rules are involved?");

				// get each possible set of rules from b that matches the rhs of a
				String num = outItems.getNumberOfDerivations();
				//if (debug) Debug.debug(debug, num+" derivations");
				int numint = Integer.parseInt(num);
				if (numint < 1) {
					Date postk = new Date();
					kbesttime += postk.getTime()-prek.getTime();
					if (debug) Debug.debug(debug, "Couldn't make rule from "+bTree);
				}
				else {
					KBest k = new KBest(outItems);
					TreeItem[] list = (TreeItem[]) k.getKBestItems(numint);
					Date postk = new Date();
					kbesttime += postk.getTime()-prek.getTime();
					for (int i = 0; i < list.length; i++) {
						// if we're beaming and over the limit, short-circuit construction here
						// since the list is in order, short-circuit the rest of the adds, too.
						if (beam>0 && 
								rulesPerState >= beam && 
								a.getSemiring().betteroreq(heap.peek().weight, 
										a.getSemiring().times(brule.getWeight(), list[i].getWeight()))) {
							prunecount += (list.length - i);
							if (debug) Debug.debug(debug, "Beamed out potential normal rule (and all others from "+i+" to "+list.length+") without creating");
							break;
						}
						// make rule. potentially remove worst rule
						if (debug) Debug.debug(debug, "Making rule out of "+list[i]);

						Date premake = new Date();
						
						// pass the state at the leaves of the tree to the rhs, and 
						// remove it from the tree, setting us up for next composition
						if (debug) Debug.debug(debug, "Passing states from "+list[i]+" to "+((TreeTransducerRule)brule).getRHS());
						TransducerRightTree newrhs = new TransducerRightTree(list[i], ((TreeTransducerRule)brule).getRHS());
						if (debug) Debug.debug(debug, "After pass we have "+list[i]+" and "+newrhs);

						// store the lhs, rhs, and weight
						RightTransducerRuleElements newrule = new RightTransducerRuleElements(list[i], 
																					newrhs, 
																					a.getSemiring().times(brule.getWeight(), list[i].getWeight()),
																					a.getSemiring());
						


						Date postmake = new Date();
						maketime += postmake.getTime()-premake.getTime();
						Date preadd = new Date();
						if (debug) Debug.debug(debug, "Adding normal rule "+newrule);
						heap.add(newrule);
						if (beam>0 && rulesPerState >= beam) { 
							prunecount++;
							RightTransducerRuleElements reject = heap.poll();
						}
						else 
							rulesPerState++;
						Date postadd = new Date();
						addtime += postadd.getTime()-preadd.getTime();
					}
				}
			}
		}


		if (debug) {
			//				if (rulecount % 500 == 0) {
			Date pause = new Date();
			long lapse = pause.getTime() - startTime.getTime();
			String applyrat = Rounding.round(100*(applytime+0.0)/lapse, 2);
			String krat = Rounding.round(100*(kbesttime+0.0)/lapse, 2);
			String makerat = Rounding.round(100*(maketime+0.0)/lapse, 2);
			String addrat = Rounding.round(100*(addtime+0.0)/lapse, 2);
			Debug.prettyDebug(
					" Apply: "+applytime+"("+applyrat+
					"%) Kbest: "+kbesttime+"("+krat+
					"%) Make: "+maketime+"("+makerat+
					"%) Add: "+addtime+"("+addrat+"%)\n");
			applytime = kbesttime = maketime = addtime = 0;
			
			
			prunecount = 0;
		}

		// hold onto the elements
		return heap;
//		return new Vector<RightTransducerRuleElements>(heap);		
	}
	
	// rightside composition of the next tree transducer in the middle of a chain
	static PriorityQueue<RightTransducerRuleElements> getRightComposition(
			PriorityQueue<RightTransducerRuleElements> ruletable,
			Symbol state,
			TreeTransducerRuleSet trs,
			Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>> rulesByRhs,
			int beam) throws UnusualConditionException{
		boolean debug = false;
		int rulesPerState = 0;

		// track actions
		int prunecount=0;

		long maketime=0;
		long addtime=0;
		long applytime=0;
		long kbesttime=0;

		Date startTime = new Date();

//		Debug.prettyDebug("Setting beam to 0 in inner composition (remove me!)");
		beam = 0;

		PriorityQueue<RightTransducerRuleElements> heap=RightTransducerRuleElements.getHeap(beam);

		// if an input epsilon was added before, add the current state to those rules


		// output epsilon rules shouldn't be allowed
		// except for eps-eps, which are handled below
		// out eps rules
		// if arule is q.x0: -> r.x0 and b state is s
		// add q_s.x0: -> r_s.x0:
		if (rulesByRhs.get(state).containsKey(Symbol.getEpsilon())) {
			for (TransducerRule arule : rulesByRhs.get(state).get(Symbol.getEpsilon())) {
				if (!arule.isInEps())
					continue;
				TreeTransducerRule ar = (TreeTransducerRule)arule;
				// if we're beaming and over the limit, short-circuit construction here
				if (beam>0 && 
						rulesPerState >= beam && 
						arule.getSemiring().betteroreq(heap.peek().weight, arule.getWeight())) {
					prunecount++;
					if (debug) Debug.debug(debug, "Beamed out potential output epsilon rule without creating");
				}
				// create and add the new rule. potentially remove worst rule
				else {					
					if (debug) Debug.debug(debug, "doing output epsilon adds");

					Date premake = new Date();
					// pass bState into rhs 
					Vector<Symbol> vec = new Vector<Symbol>();
					vec.add(ar.getRHS().getState());
					vec.add(state);
					RightTransducerRuleElements newrule =  new RightTransducerRuleElements(arule.getLHS().toTree(),
							new TransducerRightTree(ar.getRHS().getVariable(), SymbolFactory.getVecSymbol(vec)),
							arule.getWeight(),
							arule.getSemiring());
					Date postmake = new Date();
					maketime += postmake.getTime()-premake.getTime();
					Date preadd = new Date();
					if (debug) Debug.debug(debug, "Adding outEps rule "+newrule);
					heap.add(newrule);
					// remove worst rule
					if (beam>0 && rulesPerState >= beam) { 
						prunecount++;
						RightTransducerRuleElements reject = heap.poll();
						if (debug) Debug.debug(debug, "Removed least rule "+reject);
					}
					else
						rulesPerState++;
					Date postadd = new Date();
					addtime += postadd.getTime()-preadd.getTime();
				}
			}
		}
		

		// get all rules that match the rhs set we have
		for (RightTransducerRuleElements tre : ruletable) {
			TreeItem tlt = tre.lhs;

			// in eps rules
			// if tlt is variable, add in the next state
			// x0: -> RHS  gets aState added to leaves of RHS
			if (tlt.label instanceof VecSymbol) {
				// if we're beaming and over the limit, short-circuit construction here
				if (beam>0 && 
						rulesPerState >= beam && 
						trs.getSemiring().betteroreq(heap.peek().weight, tre.weight)) {
					prunecount++;
					if (debug) Debug.debug(debug, "Beamed out potential input epsilon rule without creating");
				}
				// create and add the new rule. potentially remove worst rule
				// lhs is the same.
				// rhs has state cast in

				else {
					// insert the a state vector into what we have
					Date premake = new Date();
					TransducerRightTree newrhs = new TransducerRightTree((TransducerRightTree)tre.rhs, state);
					RightTransducerRuleElements newrule =  new RightTransducerRuleElements(
							tlt,
							newrhs,
							tre.weight,
							tre.semiring);
					Date postmake = new Date();
					maketime += postmake.getTime()-premake.getTime();
					Date preadd = new Date();
					if (debug) Debug.debug(debug, "Adding inEps rule "+newrule);
					heap.add(newrule);

					// remove worst rule
					if (beam>0 && rulesPerState >= beam) { 
						prunecount++;
						RightTransducerRuleElements reject = heap.poll();
						if (debug) Debug.debug(debug, "Removed least rule "+reject);
					}
					else
						rulesPerState++;
					Date postadd = new Date();
					addtime += postadd.getTime()-preadd.getTime();
				}
			}
			else {

				// if we can't match the input so far, this partial rule dies
				if (!rulesByRhs.get(state).containsKey(tlt.getLabel())) 
					continue;
				if (debug) Debug.debug(debug, "Trying to match "+tlt);
				// now figure out where we can go with this transducer
				// form an rtg from the range of transducer a that starts 
				// with the appropriate a start state
				Date preapply = new Date();
				RTGRuleSet outItems = new RTGRuleSet(trs, state, tlt); 

				if (outItems.getNumRules() < 1) {
					continue;
				}
				if (!outItems.isFinite(false))
					throw new UnusualConditionException("Created infinite RTG by applying "+tlt+"; perhaps epsilon rules are involved?");
				String num = outItems.getNumberOfDerivations();

				int numint = Integer.parseInt(num);
				Date postapply = new Date();
				applytime += postapply.getTime()-preapply.getTime();
				if (numint < 1) {
					if (debug) Debug.debug(debug, "Couldn't make rule from "+tlt);
				}
				else {
					KBest k = new KBest(outItems);
					TreeItem[] list = (TreeItem[]) k.getKBestItems(numint);
					for (int i = 0; i < list.length; i++) {
						// if we're beaming and over the limit, short-circuit construction here
						// since the list is in order, short-circuit the rest of the adds, too.
						if (beam>0 && 
								rulesPerState >= beam && 
								trs.getSemiring().betteroreq(heap.peek().weight, 
										trs.getSemiring().times(tre.weight, list[i].getWeight()))) {
							prunecount += (list.length - i);
							if (debug) Debug.debug(debug, "Beamed out potential normal rule (and all others from "+i+" to "+list.length+") without creating");
							break;
						}
						// make rule. potentially remove worst rule
						if (debug) Debug.debug(debug, "Making rule out of "+list[i]);
						// store the lhs, rhs, and weight
						// pass the state at the leaves of the tree to the rhs, and 
						// remove it from the tree, setting us up for next composition
						if (debug) Debug.debug(debug, "Passing states from "+list[i]+" to "+tre.rhs);
						TransducerRightTree newrhs = new TransducerRightTree(list[i], (TransducerRightTree)tre.rhs);
						if (debug) Debug.debug(debug, "After pass we have "+list[i]+" and "+newrhs);

						// TODO: this isn't right! Need to adjoin the states into tre.rhs!
						RightTransducerRuleElements newrule = new RightTransducerRuleElements(
								list[i],
								newrhs,
								trs.getSemiring().times(tre.weight, list[i].getWeight()),
								trs.getSemiring());

						heap.add(newrule);
	
						if (beam>0 && rulesPerState >= beam) { 
							prunecount++;
							heap.poll();
						}
						else 
							rulesPerState++;
					}
				}
			}
		}
		if (debug) {
			//				if (rulecount % 500 == 0) {
			Date pause = new Date();
			long lapse = pause.getTime() - startTime.getTime();
			String applyrat = Rounding.round(100*(applytime+0.0)/lapse, 2);
			String krat = Rounding.round(100*(kbesttime+0.0)/lapse, 2);
			String makerat = Rounding.round(100*(maketime+0.0)/lapse, 2);
			String addrat = Rounding.round(100*(addtime+0.0)/lapse, 2);
			Debug.prettyDebug(
					" Apply: "+applytime+"("+applyrat+
					"%) Kbest: "+kbesttime+"("+krat+
					"%) Make: "+maketime+"("+makerat+
					"%) Add: "+addtime+"("+addrat+"%)\n");
			applytime = kbesttime = maketime = addtime = 0;
			
			
			prunecount = 0;
		}
		// hold on to the elements
		return heap;
//		return new Vector<RightTransducerRuleElements>(heap);

	}
	
	
	// gets rtg rules for backward application of a tree through this transducer
	// just like forward application, returns stps that could be now viable. adds to rules and states inline.
	// takes care of identity rules from application/composition so we don't have to add all of them
	// all at once.
	public ArrayList<RTGRule> getBackwardGrammarRules(RTGRuleSet rs, 
										   StateTreePair stp, 
										   ArrayList<StateTreePair> stateSet, 
										   Set<Symbol> varSymSet, 
										   HashMap<Symbol, StateTreePair> varSymSTP, 
										   HashMap<StateTreePair, Symbol> STPvarSym, 
										   boolean epsAllowed) {
		boolean debug = false;
		ArrayList<RTGRule> rh = new ArrayList<RTGRule>();
		// if the tree is just a variable, treat it specially with a "virtual" rule
		if (stp.getTree().numChildren == 0 && varSymSet.contains(stp.getTree().label)) {
			if (debug) Debug.debug(debug, "Saw special variable "+stp.getTree()+"; getting rtg rules from virtual transducer rule");
			if (!STPvarSym.containsKey(stp)) {
				Symbol dstsym = SymbolFactory.getSymbol(stp.getTree().label+"_"+stp.getState());
				varSymSTP.put(dstsym, stp);
				STPvarSym.put(stp, dstsym);
			}
			rh.add(new RTGRule(rs, stp.getSymbol(), STPvarSym.get(stp), semiring.ONE(), semiring));
		}
		else {
			ArrayList<TransducerRule> currRules = getRulesOfType(stp.getState());
			if (currRules == null)
				return rh;
			for (TransducerRule rabs : currRules) {
				TreeTransducerRule r = (TreeTransducerRule)rabs;
				if (!epsAllowed && r.isInEps()) {
					if (debug) Debug.debug(debug, "Skipping "+r+": input epsilon prohibited");
					continue;
				}
				if (debug) Debug.debug(debug, "Checking rule "+r+" with state pair "+stp.toInternalString());
				// the actual stps
				RTGRule newRule = r.getBackwardGrammarRule(rs, stp);

				try {
					if (newRule != null) {
						if (debug) Debug.debug(debug, "Adding grammar rule "+newRule.toString()+" to list with "+rh.size()+" members");
						rh.add(newRule);
						if (debug) Debug.debug(debug, "Getting grammar states for "+stp.toInternalString());
						HashSet<StateTreePair> newstates = r.getBackwardGrammarStates(stp);
						if (debug) Debug.debug(debug, "About to try to add new states");
						if (newstates != null) {
							if (debug) Debug.debug(debug, "Adding "+newstates.size()+" states");
							stateSet.addAll(newstates);
						}		
						else {
							if (debug) Debug.debug(debug, "No new states");			
						}
					}
				}
				catch (DataFormatException e) {
					System.err.println("DataFormatException on grammar states from "+r.toString()+" for "+stp.toString()+": "+e.getMessage());
				}
			}
		}
		return rh;

	}

	// gets rtg rules for backward application of a tree through this transducer
	// just like forward application, returns stps that could be now viable. adds to rules and states inline.
	// takes care of identity rules from application/composition so we don't have to add all of them
	// all at once.
	// this version doesn't use external maps
	public ArrayList<RTGRule> getBackwardGrammarRules(RTGRuleSet rs, 
										   StateTreePair stp, 
										   ArrayList<StateTreePair> stateSet, 
										   boolean epsAllowed) {
		boolean debug = false;
		ArrayList<RTGRule> rh = new ArrayList<RTGRule>();
		// if the tree is just a variable, treat it specially with a "virtual" rule
		if (stp.getTree().numChildren == 0 && stp.getTree().label instanceof VecSymbol) {
			if (debug) Debug.debug(debug, "Saw special variable "+stp.getTree()+"; getting rtg rules from virtual transducer rule");
			// insert the state after the variable 
			VecSymbol dstsym = SymbolFactory.getVecSymbol((VecSymbol)stp.getTree().label, stp.getState());
			rh.add(new RTGRule(rs, stp.getSymbol(), dstsym, semiring.ONE(), semiring));
		}
		else {
			ArrayList<TransducerRule> currRules = getRulesOfType(stp.getState());
			if (currRules == null)
				return rh;
			for (TransducerRule rabs : currRules) {
				TreeTransducerRule r = (TreeTransducerRule)rabs;
				if (!epsAllowed && r.isInEps()) {
					if (debug) Debug.debug(debug, "Skipping "+r+": input epsilon prohibited");
					continue;
				}
				if (debug) Debug.debug(debug, "Checking rule "+r+" with state pair "+stp.toInternalString());
				// the actual stps
				RTGRule newRule = r.getBackwardGrammarRule(rs, stp);

				try {
					if (newRule != null) {
						if (debug) Debug.debug(debug, "Adding grammar rule "+newRule.toString()+" to list with "+rh.size()+" members");
						rh.add(newRule);
						if (debug) Debug.debug(debug, "Getting grammar states for "+stp.toInternalString());
						HashSet<StateTreePair> newstates = r.getBackwardGrammarStates(stp);
						if (debug) Debug.debug(debug, "About to try to add new states");
						if (newstates != null) {
							if (debug) Debug.debug(debug, "Adding "+newstates.size()+" states");
							stateSet.addAll(newstates);
						}		
						else {
							if (debug) Debug.debug(debug, "No new states");			
						}
					}
				}
				catch (DataFormatException e) {
					System.err.println("DataFormatException on grammar states from "+r.toString()+" for "+stp.toString()+": "+e.getMessage());
				}
			}
		}
		return rh;

	}

	
	

	// are RHS single-level or less trees? if not, we're "extended RHS"
	boolean isExtendedRHSset=false;
	boolean isExtendedRHS=false;
	public boolean isExtendedRHS() {
		if (isExtendedRHSset)
			return isExtendedRHS;
		for (TransducerRule r : rules) {
			if (((TreeTransducerRule)r).isExtendedRHS()) {
				isExtendedRHS = true;
				break;
			}
		}
		isExtendedRHSset = true;
		return isExtendedRHS;
	}


	
	
	// TODO: option to print 0-scoring rules
	public String toString() {
		StringBuffer l = new StringBuffer(getStartState().toString()+"\n");
		ArrayList<TransducerRule> initSet = getRulesOfType(getStartState());
		if (initSet != null) {
			Iterator it = initSet.iterator();
			while (it.hasNext()) {
				TreeTransducerRule r = (TreeTransducerRule)it.next();
				if (semiring.betteroreq(semiring.ZERO(), r.getWeight()))
					continue;
				l.append(r.toString());
				// check if we have to print the tie
				if (r.getTie() > 0) {
					boolean seenMatch = false;
					Iterator tieit = getTiedRules(r.getTie()).iterator();
					while (tieit.hasNext()) {
						TreeTransducerRule tierule = (TreeTransducerRule)tieit.next();
						if (tierule != r && semiring.better(tierule.getWeight(), semiring.ZERO())) {
							seenMatch = true;
							break;
						}
					}
					if (seenMatch)
						l.append(" @ "+r.getTie());
				}		    
				l.append("\n");
			}
		}
		Iterator lhsit = rulesByState.keySet().iterator();
		while (lhsit.hasNext()) {
			Symbol left = (Symbol)lhsit.next();
			if (left.equals(getStartState()))
				continue;
			ArrayList rhsSet = getRulesOfType(left);
			Iterator it = rhsSet.iterator();
			while (it.hasNext()) {
				TreeTransducerRule r = (TreeTransducerRule)it.next();
				if (semiring.betteroreq(semiring.ZERO(), r.getWeight()))
					continue;
				l.append(r.toString());
				// check if we have to print the tie
				if (r.getTie() > 0) {
					boolean seenMatch = false;
					Iterator tieit = getTiedRules(r.getTie()).iterator();
					while (tieit.hasNext()) {
						TreeTransducerRule tierule = (TreeTransducerRule)tieit.next();
						if (tierule != r && semiring.better(tierule.getWeight(), semiring.ZERO())) {
							seenMatch = true;
							break;
						}
					}
					if (seenMatch)
						l.append(" @ "+r.getTie());
				}		    
				l.append("\n");
			}
		}   
		return l.toString();
	}

	// TODO: memoize!!
	public ArrayList<TreeTransducerRule> getTrainingRules(Symbol s, Item iitem, Item oitem) throws UnexpectedCaseException{
		if (! (iitem instanceof TreeItem))
			throw new UnexpectedCaseException("Expected Tree for input item, got "+iitem.toString());
		if (! (oitem instanceof TreeItem))
			throw new UnexpectedCaseException("Expected Tree for output item, got "+oitem.toString());
		TreeItem itree = (TreeItem)iitem;
		TreeItem otree = (TreeItem)oitem;	
		boolean debug = false;
		ArrayList<TreeTransducerRule> retSet = new ArrayList<TreeTransducerRule>();
		ArrayList currRules = getRulesOfType(s);
		if (currRules == null)
			throw new UnexpectedCaseException("No rules for "+s.toString());
		Iterator rit = currRules.iterator();
		while (rit.hasNext()) {
			TreeTransducerRule r = (TreeTransducerRule)rit.next();
			if (debug) Debug.debug(debug, "Checking "+r.toString()+" for "+s.toString()+", "+itree.toString()+", "+otree.toString());
			if (r.isTreeMatch(s, itree, otree)) {
				if (debug) Debug.debug(debug, "Success!");
				retSet.add(r);
			}
		}
		return retSet;
	}

	//     // inverse lookup. initializes on demand
	//     // all the rules that have in their rhs a particular state
	//     public ArrayList<TreeTransducerRule> getInverseRulesOfType(Symbol s) {
	// 	if (inverseRulesByState == null)
	// 	    initializeInverseRulesByState();
	// 	return inverseRulesByState.get(s);
	//     }

	//     public void initializeInverseRulesByState() {
	// 	inverseRulesByState = new HashMap<Symbol, ArrayList<TreeTransducerRule>>();
	// 	for (TransducerRule r : rules) {
	// 	    Iterator<Symbol> rsit = r.getStates().iterator();
	// 	    while (rsit.hasNext()) {
	// 		Symbol s = rsit.next();
	// 		if (!inverseRulesByState.containsKey(s))
	// 		    inverseRulesByState.put(s, new ArrayList<TreeTransducerRule>());
	// 		inverseRulesByState.get(s).add((TreeTransducerRule)r);
	// 	    }
	// 	}
	//     }


	// CURRENTLY NOT USED AND COMMENTED OUT: getStates() in TRVM must be re-enabled to use!

	// to implement the pereira/riley algorithm virtually on the first of a pair of composable transducers, 
	// all states must either be loopable or not loopable.
	// loopable states do not have any incoming arcs with epsilon output. non-loopable states have only incoming arcs
	// with epsilon output. if a state is neither loopable nor non-loopable, it must be cloned and things must be
	// changed. This should not change the transducer's functionality
	//     public void makeComposeSafe() {
	// 	makeComposeSafe(false);
	//     }
	//     public void makeComposeSafe(boolean debug) {
	// 	isCopyingSet = false;
	// 	if (loopableStates != null) {
	// 	    if (debug) Debug.debug(debug, "Already made compose safe");
	// 	    return;
	// 	}
	// 	loopableStates = new HashSet();
	// 	// since states may change, make a copy
	// 	HashSet localStates = new HashSet(states);
	// 	Iterator stit = localStates.iterator();
	// 	while (stit.hasNext()) {
	// 	    Symbol state = (Symbol)stit.next();
	// 	    if (state.equals(startState)) {
	// 		if (debug) Debug.debug(debug, "Adding start state to loopable");
	// 		loopableStates.add(state);
	// 		continue;
	// 	    }
	// 	    ArrayList<TreeTransducerRule> invRules = getInverseRulesOfType(state);
	// 	    if (invRules == null) {
	// 		Debug.debug(true, "WARNING: "+state.toString()+" has no incoming edges and is not the start state");
	// 		continue;
	// 	    }
	// 	    boolean seennorm = false;
	// 	    boolean seeneps = false;
	// 	    for (TreeTransducerRule r : invRules) {
	// 		if (r.isRightEpsilon())
	// 		    seeneps = true;
	// 		else
	// 		    seennorm = true;
	// 		if (seeneps && seennorm)
	// 		    break;
	// 	    }
	// 	    if (debug) Debug.debug(debug, "Norm, eps status of "+state.toString()+" is "+seennorm+", "+seeneps);
	// 	    if (seennorm ^ seeneps) {
	// 		if (seennorm)
	// 		    loopableStates.add(state);
	// 		continue;
	// 	    }

	// 	    else {
	// 		// actually do the cloning:
	// 		// 1) create the clone state
	// 		Symbol cloneState = SymbolFactory.getSymbol(state.toString()+"_clone");
	// 		states.add(cloneState);
	// 		// 2) for each outgoing rule, make a new rule that is a clone except for the start state. Put them
	// 		// in the proper places (rulesbystate done en masse at the end)

	// 		// local hashset again.
	// 		ArrayList<TransducerRule> outRules = new ArrayList<TransducerRule>(getRulesOfType(state));
	// 		ArrayList<TransducerRule> newRules = new ArrayList<TransducerRule>();
	// 		Iterator orit = outRules.iterator();
	// 		while (orit.hasNext()) {
	// 		    TreeTransducerRule or = (TreeTransducerRule)orit.next();
	// 		    // TODO: check that this type of creation is actually okay!
	// 		    // TODO: these rules may need to point to clone rules!!
	// 		    TreeTransducerRule newor = new TreeTransducerRule(cloneState, or.getLHS(), or.getRHS(), 
	// 								      or.getTRVM(), or.getWeight(), or.getSemiring(), this);
	// 		    newRules.add(newor);
	// 		    rules.add(newor);
	// 		    rulesByIndex.put(newor.getIndex(), newor);
	// 		    // no need to add states...there's nothing new
	// 		    // but we do have to add to the inverse
	// 		    Iterator<Symbol> rsit = newor.getStates().iterator();
	// 		    while (rsit.hasNext()) {
	// 			Symbol s = rsit.next();
	// 			if (!inverseRulesByState.containsKey(s))
	// 			    inverseRulesByState.put(s, new ArrayList<TreeTransducerRule>());
	// 			inverseRulesByState.get(s).add(newor);
	// 		    }
	// 		}
	// 		// en masse add to regular rules by state
	// 		rulesByState.put(cloneState, newRules);
	// 		// 3) change all right-eps rules pointing at the state to point at the clone state
	// 		// note that invRules is refreshed since we may have added some things to it
	// 		// also note that it's local
	// 		if (!inverseRulesByState.containsKey(cloneState))
	// 		    inverseRulesByState.put(cloneState, new ArrayList<TreeTransducerRule>());

	// 		invRules = new ArrayList<TreeTransducerRule>(getInverseRulesOfType(state));
	// 		for (TreeTransducerRule r : invRules) {
	// 		    // TODO: maybe make leaf children instead of assuming the rhs has the state?
	// 		    // TODO: shouldn't do a direct change? toString() could be affected!!!
	// 		    if (r.isRightEpsilon()) {
	// 			((ArrayList<TreeTransducerRule>)inverseRulesByState.get(state)).remove(r);
	// 			r.getRHS().state = cloneState;
	// 			((ArrayList<TreeTransducerRule>)inverseRulesByState.get(cloneState)).add(r);
	// 		    }
	// 		}
	// 	    }
	// 	}
	//     }
	//     public boolean isLoopable(Symbol st) {
	// 	if (loopableStates == null) {
	// 	    Debug.debug(true, "isLoopable run before makeComposeSafe!");
	// 	    System.exit(-1);
	// 	}
	// 	return loopableStates.contains(st);
	//     }


	// test code
	public static void main(String argv[]) {
		try {
			RealSemiring s = new RealSemiring();
			String encoding = "utf-8";
			TreeTransducerRuleSet rs1 = new TreeTransducerRuleSet(new BufferedReader(new InputStreamReader(new FileInputStream(argv[0]), "utf-8")), s);
			System.out.println(rs1.toString());
			TreeTransducerRuleSet rs2 = new TreeTransducerRuleSet(new BufferedReader(new InputStreamReader(new FileInputStream(argv[1]), "utf-8")), s);
			System.out.println(rs2.toString());
			TreeTransducerRuleSet rs3 = new TreeTransducerRuleSet(new BufferedReader(new InputStreamReader(new FileInputStream(argv[2]), "utf-8")), s);
			System.out.println(rs3.toString());
			Vector<TreeTransducerRuleSet> vec = new Vector<TreeTransducerRuleSet>();
			vec.add(rs1);
			vec.add(rs2);
			vec.add(rs3);
			Date preVecTime = new Date();
			TreeTransducerRuleSet compvec = new TreeTransducerRuleSet(vec, false, 0);
			Date postVecTime = new Date();
			Debug.dbtime(1, 1, preVecTime, postVecTime, "vector compose");
			System.out.println(compvec);
			Date preClassicTime = new Date();
			TreeTransducerRuleSet classic = new TreeTransducerRuleSet(rs1, new TreeTransducerRuleSet(rs2, rs3, 0), 0);
			Date postClassicTime = new Date();
			Debug.dbtime(1, 1, preClassicTime, postClassicTime, "classic compose");
			System.out.println(classic);


			// 	    RTGRuleSet rsright = trs2.getLeftImage(trs2.getStartState());
			// 	    Debug.debug(true, "Right rule set:");
			// 	    Debug.debug(true, rsright.toString());
			//  	    Iterator i = trs1.rules.iterator();
			//  	    while (i.hasNext()) {
			//  		TreeTransducerRule r = (TreeTransducerRule)i.next();
			//  		Debug.debug(true, r.toString());
			//  		RTGRuleSet rsleft = r.getRightImage();
			// 		Debug.debug(true, "Left rule set:");
			// 		Debug.debug(true, rsleft.toString());
			// 		RTGRuleSet rsis = Intersect.intersectRuleSets(rsleft, rsright);
			// 		Debug.debug(true, "Isect rule set:");
			// 		Debug.debug(true, rsis.toString());
			// 		String num = rsis.getNumberOfDerivations();
			// 		Debug.debug(true, num+" derivations");
			// 		int numint = Integer.parseInt(num);
			// 		KBest k = new KBest(rsis);
			// 		TransducerRightTree[] list = k.getKBestTransducerRightTrees(numint);
			// 		for (int x = 0; x < list.length; x++) {
			// 		    Debug.debug(true, "\t"+x+": "+list[x].toString());
			// 		}
			// 	    }


		} 
		catch (Exception e) {
			System.err.println(e.getMessage());
		}
	}
}
