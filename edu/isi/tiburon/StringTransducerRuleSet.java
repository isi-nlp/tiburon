package edu.isi.tiburon;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Stack;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;




public class StringTransducerRuleSet extends TransducerRuleSet {

	// dummy constructor for testing file format
	public StringTransducerRuleSet() { }

	// empty spaces or comments regions
	private static Pattern commentPat = Pattern.compile("\\s*(%.*)?");

	// something that can be a start state -- no spaces, no parens, no colons, no periods
	// can be followed by whitespace and comment
	private static Pattern startStatePat = Pattern.compile("\\s*([^\\s\\(\\):\\.%]+)\\s*(%.*)?");
	
	// strip comments off
	private static Pattern commentStripPat = Pattern.compile("\\s*(.*?[^\\s%])(\\s*(%.*)?)?");
	
	private Hashtable<Symbol, Integer> s2i;
	private Hashtable<Integer, Symbol> i2s;
	
	protected void reinitialize() {
		super.reinitialize();
		initI2S();
	}
	public int s2i(Symbol s) {
		try {
			return s2i.get(s);
		}
		catch (NullPointerException e) {
			throw new NullPointerException("Null pointer translating "+s);
		}
	}
	
	public Symbol i2s(short i) {
		return i2s.get(i);
	}
	// set up i2s
	private int nextState;
	private void initI2S() {
		s2i = new Hashtable<Symbol, Integer>();
		i2s = new Hashtable<Integer, Symbol>();
		// integer mapping to and from xrs states used by backwards application
		nextState=0;
		for (Symbol s : states) {
			s2i.put(s, nextState);
			i2s.put(nextState++, s);
		}
	}
//	public void addToI2S(Symbol s) {
//		if (!s2i.containsKey(s)) {
//			s2i.put(s, nextState);
//			i2s.put(nextState++, s);
//		}
//	}
	public StringTransducerRuleSet(String filename, String encoding, Semiring s) throws FileNotFoundException, IOException, DataFormatException  {
		this(new BufferedReader(new InputStreamReader(new FileInputStream(filename), encoding)), s);
	}
	// TODO: re-enable comma warning?
	public StringTransducerRuleSet(BufferedReader br, Semiring s) throws  IOException, DataFormatException {
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
			StringTransducerRule r = null;
			try {
				r = new StringTransducerRule(this, states, ruleText, semiring);
			}
			catch (DataFormatException e) {
				throw new DataFormatException(ruleText+", "+e.getMessage(), e);
			}
			if (debug) Debug.debug(debug, "Made rule "+r.toString());
			if (debug) Debug.debug(debug, "Rule's tie id is "+r.getTie());
			rules.add(r);
			if (!rulesByState.containsKey(r.getState()))
				rulesByState.put(r.getState(), new ArrayList<TransducerRule>());
			rulesByState.get(r.getState()).add(r);
			rulesByIndex.put(r.getIndex(), r);
			rulecounter++;
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
		initI2S();
	}

	// for conversion of RTG
	// start state the same
	// rhs of rule mirrored on lhs with variables replacing states
	// rhs the yield of rule rhs, with variables inserted at states
	// state-to-state 
	public StringTransducerRuleSet(RTGRuleSet rtg) {
		super();
		applicableSymbols = new HashSet<Symbol>();
		startState = rtg.getStartState();
		// can just recreate whole states set
		states = new HashSet(rtg.getStates());
		semiring = rtg.getSemiring();
		for (Rule r : rtg.getRules()) {
			StringTransducerRule newrule = new StringTransducerRule(this, (RTGRule)r, applicableSymbols, rtg.getStates());
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
				((ArrayList<TransducerRule>)rulesByTie.get(newrule.getTie())).add(newrule);
			}
		}
		initI2S();
	}

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
	// by-state composition with beaming via forward algorithm
	// old full cross-composition via forward application
	public StringTransducerRuleSet(TreeTransducerRuleSet a, StringTransducerRuleSet b, int beam) throws ImproperConversionException, UnusualConditionException {
		super();
		boolean debug = false;
		if (debug) Debug.debug(debug, "Composing "+a.toString()+" and "+b.toString()+" with beam of "+0);
//		debug = true;
		if (a.getSemiring() != b.getSemiring())
			throw new ImproperConversionException("Transducers must have same semiring to be composed");
		semiring = a.getSemiring();
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
		Debug.dbtime(1, 1, preIndexTime, postIndexTime,  "index rhs of transducer a");

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
		Debug.dbtime(1, 1, preIndexTime, postIndexTime,  "index lhs of transducer b");



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
						StringTransducerRule newrule = new StringTransducerRule(this, newState, aState, 
								((StringTransducerRule)br).getRHS(), br.getWeight());
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
						StringTransducerRule newrule =  new StringTransducerRule(this, 
								arule.getLHS(), 
								((TreeTransducerRule)arule).getRHS().getVariable(), 
								newState, 
								SymbolFactory.getVecSymbol(dststate), 
								arule.getWeight());
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
			// now do rules that match asym(rhs) and bsym(lhs)
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
					CFGRuleSet outItems = new CFGRuleSet(aTree, 
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
						StringItem[] list = (StringItem[]) k.getKBestItems(numint);
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
							StringTransducerRule newrule = null;
							try {
								newrule = new StringTransducerRule(this, 
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
				StringTransducerRule r = (StringTransducerRule)heap.poll();
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
		initI2S();
	}


	
	// container class and accompanying comparator
	
	private class TransducerRuleElements {
		public TransducerLeftTree lhs;
		public Item rhs;
		public double weight;
		public Semiring semiring;
		public TransducerRuleElements(TransducerLeftTree tlt, Item trt, double wgt, Semiring s) {
			lhs=tlt;
			rhs=trt;
			weight=wgt;
			semiring=s;
		}
		public String toString() {
			return lhs+":"+rhs+":"+weight;
		}
	}
	
	private class TransducerRuleElementsComp implements Comparator<TransducerRuleElements> {
		public int compare(TransducerRuleElements o1, TransducerRuleElements o2) {
			if (o1.semiring.better(o1.weight, o2.weight))
				return 1;
			else if (o1.semiring.better(o2.weight, o1.weight))
				return -1;
			else
				return 0;
		}
	}
	
	// n-way composition via forward or backward application
	// last transducer is string; the rest are tree
	// beam is max number of rules added from each state
	// all potential rules are inspected, but only highest weighted are kept,
	// thanks to a priority queue
	// beam of 0 means all rules are added
	// uses TreeTransducerRuleSet composition methods when appropriate
	// TODO: right-side composition
	

	public StringTransducerRuleSet(Vector<TransducerRuleSet> trslist, boolean isLeftside, int beam) throws ImproperConversionException, UnusualConditionException {
		super();

		boolean debug = false;
		if (debug) Debug.debug(debug, "Composing "+trslist.size()+" transducers");

		semiring = trslist.get(0).getSemiring();

		// determine order of composition when adding "normal" rules. 
		// leftside (default or when a is extended rhs) = given the rhs of rules from a,
		//          find the rtg of rules from b
		// rightside (when b is extended lhs) = given the lhs of rules from b, find the rtg
		//          of rules from a.


		// index all rulesets except last by rhs sym
		// index all rulesets except first by lhs sym
	
		Vector<Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>>> rulesByRhsVec = new Vector<Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>>>();
		Vector<Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>>> rulesByLhsVec = new Vector<Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>>>();

		for (int i = 0; i < trslist.size(); i++) {
			TransducerRuleSet trs = trslist.get(i);
			if (debug) Debug.debug(debug, trs.getNumStates()+" states");
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
		for (TransducerRuleSet trs: trslist) {
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

		int stateCount = 0;
		while (new_states.size() > 0) {
			//Debug.prettyDebug(new_states.size()+" states left");
			// pop off the next state
			VecSymbol newState = new_states.remove(0);
			stateCount++;
			if (stateCount % 10 == 0)
				if (debug) Debug.debug(debug, "indexing rules of "+stateCount+" states");
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
			TreeTransducerRuleSet a = (TreeTransducerRuleSet)trslist.get(aNum);
			TransducerRuleSet b = trslist.get(bNum);
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
				if (leftMemo.containsKey(memovec)) {
					ruletable = leftMemo.get(memovec);
				}
				else {
					// get the rule table from the first two transducers
					if (b instanceof TreeTransducerRuleSet)
						ruletable = TreeTransducerRuleSet.initLeftComposition(aState, bState, (TreeTransducerRuleSet)b, rulesByRhs, rulesByLhs, beam);
					else
						ruletable = initLeftComposition(aState, bState, (StringTransducerRuleSet)b, rulesByRhs, rulesByLhs, beam);

					leftMemo.put(memovec, ruletable);
				}
//				Debug.prettyDebug(ruletable.size()+" in initial table");
//				Debug.prettyDebug(ruletable.toString());


				// now, use each successive transducer to modify the ruletable by passing all TRTs through.
				for (int j = 2; j < trslist.size(); j++) {

					TransducerRuleSet trs = trslist.get(j);
					Symbol state = newState.getVec().get(j);
					memovec = SymbolFactory.getVecSymbol(memovec,state);
					rulesByLhs = rulesByLhsVec.get(j);
					if (leftMemo.containsKey(memovec))
						ruletable = leftMemo.get(memovec);
					else {
						if (trs instanceof TreeTransducerRuleSet)
							ruletable = TreeTransducerRuleSet.getLeftComposition(ruletable, state, (TreeTransducerRuleSet)trs, rulesByLhs, beam);
						else
							ruletable = getLeftComposition(ruletable, state, (StringTransducerRuleSet)trs, rulesByLhs, beam);

						leftMemo.put(memovec, ruletable);
					}
//					Debug.prettyDebug(ruletable.size()+" in table");
//					Debug.prettyDebug(ruletable.toString());

				}
				// now, turn each of the elements in the rule table into a real rule, add that rule, and figure out new
				// states
				// do beaming here!
				
				int rulesAdded = 0;
				while ((rulesAdded < beam || beam == 0) && !ruletable.isEmpty()) {
					LeftTransducerRuleElements tre = ruletable.poll();
					Date premake = new Date();
					StringTransducerRule newrule = null;
					try {
						newrule = new StringTransducerRule(this, 
								tre.lhs, 
								newState, 
								(StringItem)tre.rhs, 
								tre.weight);		
					}
					catch (ImproperConversionException e) {
						throw new ImproperConversionException("Couldn't make rule from "+tre.lhs+", "+tre.rhs+": "+e.getMessage());
					}
					rules.add(newrule);
					rulesAdded++;
					Date postmake = new Date();
					// also add potential new states from newly added rules
					for (TransducerRightSide rhs : newrule.getTRVM().getRHSInOrder()) {
						if (rhs.hasState()) {
							VecSymbol nextst = (VecSymbol)rhs.getState();
							if (!al_states.contains(nextst)) {
//								if (debug) Debug.debug(debug, "Adding "+nextst+" = "+nextst.toInternalString()+" to next state queue");
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
				if (rightMemo.containsKey(memovec)) {
//					Debug.prettyDebug("Using memoization for right table "+memovec);
					ruletable = rightMemo.get(memovec);
				}
				else {
	//				Debug.prettyDebug("Not Using memoization for right table "+memovec);

					// get the rule table from the last two transducers -- last is always going to be string,
					// so no use of TTRS init as in the leftward case
					ruletable = initRightComposition(aState, bState, a, rulesByRhs, rulesByLhs, beam);
					rightMemo.put(memovec, ruletable);
				}
				

				// now, use each successive transducer to modify the ruletable by passing all TRTs through.
				// all instances should be tree transducer rule sets here but make trs on right
				for (int j = trslist.size()-3; j >= 0; j--) {

					TreeTransducerRuleSet trs = (TreeTransducerRuleSet)trslist.get(j);
					Symbol state = newState.getVec().get(j);
					memovec = SymbolFactory.getVecSymbol(state,memovec);
					rulesByRhs = rulesByRhsVec.get(j);
					if (rightMemo.containsKey(memovec)) {
				//		Debug.prettyDebug("Using memoization for right table "+memovec);
						ruletable = rightMemo.get(memovec);
					}
					else {
					//	Debug.prettyDebug("Not Using memoization for right table "+memovec);
						ruletable = getRightComposition(ruletable, state, trs, rulesByRhs, beam);
						rightMemo.put(memovec, ruletable);
					}
				}
				// now, turn each of the elements in the rule table into a real rule, add that rule, and figure out new
				// states
				// do beaming here
				int rulesAdded = 0;
				while ((rulesAdded < beam || beam == 0) && !ruletable.isEmpty()) {
					RightTransducerRuleElements tre = ruletable.poll();
					Date premake = new Date();
					StringTransducerRule newrule = null;
					try {
						newrule = new StringTransducerRule(this, 
								tre.lhs, 
								newState, 
								(TransducerRightString)tre.rhs, 
								tre.weight);		
					}
					catch (ImproperConversionException e) {
						throw new ImproperConversionException("Couldn't make rule from "+tre.lhs+", "+tre.rhs+": "+e.getMessage());
					}
					if (debug) Debug.debug(debug, "Added rule "+newrule);
					rules.add(newrule);
					rulesAdded++;
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
		// al_stat			es is the states list
		states = new HashSet<Symbol>(al_states);
		initI2S();
	}
		
	

	
	
	
	// leftside composition of the first tree transducer and the second (string) transducer in a chain.
	static PriorityQueue<LeftTransducerRuleElements> initLeftComposition(	
			Symbol aState,
			Symbol bState,
			StringTransducerRuleSet b,
			Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>> rulesByRhs,
			Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>> rulesByLhs,
			int beam) throws UnusualConditionException{
		boolean debug=false;
		boolean lightdebug = false;
		
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

		

		
//		// b as input epsilon only allowed for eps-eps rules,
//		// a state is q and b has r.x0: -> s.x0 := add q_r.x0: -> q_s.x0
//		if (rulesByLhs.get(bState).containsKey(Symbol.getEpsilon())) {
//			for (TransducerRule br : rulesByLhs.get(bState).get(Symbol.getEpsilon())) {
//				if (!br.isOutEps())
//					continue;
//				// if we're beaming and over the limit, short-circuit construction here
//				if (beam>0 && 
//						rulesPerState >= beam && 
//						br.getSemiring().betteroreq(heap.peek().weight, br.getWeight())) {
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
//																				br.getSemiring());
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
//		if (debug) Debug.debug(debug, "getting input epsilon rules from "+bState);
		
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
		if (debug||lightdebug) Debug.debug(debug||lightdebug, "getting normal rules from "+aState+" to "+bState);

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
																				new StringItem(((TreeTransducerRule)arule).getRHS().getEpsOutputVecVarImageTree(bState).getLabel()),
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
				CFGRuleSet outItems = new CFGRuleSet(aTree, bState, b);
				
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
					StringItem[] list = (StringItem[]) k.getKBestItems(numint);
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
		if (debug||lightdebug) Debug.debug(debug||lightdebug, "got "+heap.size()+" rules");

		// hold onto the elements
		return heap;
//		return new Vector<LeftTransducerRuleElements>(heap);		
	}



	private static final int RULE_RPT = 10000;
	// leftside composition of the next tree transducer in the middle of a chain
	// assume each rule has size-one lhs
	static PriorityQueue<LeftTransducerRuleElements> getLeftComposition(
			PriorityQueue<LeftTransducerRuleElements> ruletable,
			Symbol state,
			StringTransducerRuleSet trs,
			Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>> rulesByLhs,
			int beam) throws UnusualConditionException{
		boolean debug = false;
		boolean heapdbg = true;
		int rulesPerState = 0;

		// track actions
		int prunecount=0;

		long maketime=0;
		long addtime=0;
		long applytime=0;
		long kbesttime=0;

		Date startTime = new Date();
		//Debug.prettyDebug("Setting beam to 0 in inner composition (remove me!)");
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

		int discard=0;
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
																				new StringItem(SymbolFactory.getVecSymbol((VecSymbol)trt.label, state)),
																				tre.weight,
																				tre.semiring);
					Date postmake = new Date();
					maketime += postmake.getTime()-premake.getTime();
					Date preadd = new Date();
					if (debug) Debug.debug(debug, "Adding outEps rule "+newrule);
					heap.add(newrule);

					if (heapdbg && heap.size() % RULE_RPT == 0) {
						Debug.debug(heapdbg, "at eps, Heap at "+heap.size());
					}
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
				if (!rulesByLhs.get(state).containsKey(trt.getLabel())) {
					discard++;
					continue;
				}
				if (debug) Debug.debug(debug, "Trying to match "+trt);
				// now figure out where we can go with this transducer
				Date preapply = new Date();
				CFGRuleSet outItems = new CFGRuleSet(trt, state, trs);
				if (outItems.getNumRules() < 1) {
					discard++;
					continue;
				}
				if (!outItems.isFinite(false))
					throw new UnusualConditionException("Created infinite RTG by applying "+trt+"; perhaps epsilon rules are involved?");
				String num = outItems.getNumberOfDerivations();

				int numint = Integer.parseInt(num);
				
				Date postapply = new Date();
				applytime += postapply.getTime()-preapply.getTime();
				if (numint < 1) {
					discard++;
					if (debug) Debug.debug(debug, "Couldn't make rule from "+trt);
				}
				else {
					KBest k = new KBest(outItems);
					StringItem[] list = (StringItem[]) k.getKBestItems(numint);
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

						if (heapdbg && heap.size() % RULE_RPT == 0) {
							Debug.debug(heapdbg, "Heap at "+heap.size());
						}
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
//		Debug.prettyDebug(discard+" discarded");
		// hold on to the elements
		return heap;
//		return new Vector<LeftTransducerRuleElements>(heap);

	}
	
	
	// rightside composition of the second-to-last tree transducer and last string transducer in a chain
	
	static PriorityQueue<RightTransducerRuleElements> initRightComposition(	
			Symbol aState,
			Symbol bState,
			TreeTransducerRuleSet a,
			Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>> rulesByRhs,
			Hashtable<Symbol, Hashtable<Symbol, ArrayList<TransducerRule>>> rulesByLhs,
			int beam) throws UnusualConditionException{
		boolean debug=true;
		
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
		if (debug) Debug.debug(debug, "Combining "+aState+" and "+bState);

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
							new TransducerRightString(((StringTransducerRule)br).getRHS(), aState),
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
							new TransducerRightString(ar.getRHS().getVariable(), SymbolFactory.getVecSymbol(vec)),
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
						if (debug) Debug.debug(debug, "Passing states from "+list[i]+" to "+((StringTransducerRule)brule).getRHS());
						TransducerRightString newrhs = new TransducerRightString(list[i], ((StringTransducerRule)brule).getRHS());
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
		//Debug.prettyDebug("Setting beam to 0 in inner composition (remove me!)");
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
							new TransducerRightString(ar.getRHS().getVariable(), SymbolFactory.getVecSymbol(vec)),
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
					TransducerRightString newrhs = new TransducerRightString((TransducerRightString)tre.rhs, state);
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
						TransducerRightString newrhs = new TransducerRightString(list[i], (TransducerRightString)tre.rhs);
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
	public StringTransducerRuleSet(StringTransducerRuleSet trs) {
		super(trs);
		initI2S();
	}

	// TODO: option to print 0-scoring rules	
	public String toString() {
		StringBuffer l = new StringBuffer(getStartState().toString()+"\n");
		ArrayList<TransducerRule> initSet = getRulesOfType(getStartState());
		if (initSet != null) {
			Iterator it = initSet.iterator();
			while (it.hasNext()) {
				StringTransducerRule r = (StringTransducerRule)it.next();
				if (r.getWeight() == semiring.ZERO())
					continue;
				l.append(r.toString());
				// check if we have to print the tie
				if (r.getTie() > 0) {
					boolean seenMatch = false;
					Iterator tieit = getTiedRules(r.getTie()).iterator();
					while (tieit.hasNext()) {
						StringTransducerRule tierule = (StringTransducerRule)tieit.next();
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
			ArrayList<TransducerRule> rhsSet = getRulesOfType(left);
			Iterator it = rhsSet.iterator();
			while (it.hasNext()) {
				StringTransducerRule r = (StringTransducerRule)it.next();
				if (r.getWeight() == semiring.ZERO())
					continue;
				l.append(r.toString());
				// check if we have to print the tie
				if (r.getTie() > 0) {
					boolean seenMatch = false;
					Iterator tieit = getTiedRules(r.getTie()).iterator();
					while (tieit.hasNext()) {
						StringTransducerRule tierule = (StringTransducerRule)tieit.next();
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

	public ArrayList<StringTransducerRule> getTrainingRules(Symbol s, TreeItem itree, TrainingString ostring) throws UnexpectedCaseException {
		boolean debug = false;
		ArrayList<StringTransducerRule> retSet = new ArrayList<StringTransducerRule>();
		ArrayList<TransducerRule> currRules = getRulesOfType(s);
		if (currRules == null)
			throw new UnexpectedCaseException("No rules for "+s.toString());
		if (debug) Debug.debug(debug, "Retrieved "+currRules.size()+" rules matching "+s.toString());
		Iterator rit = currRules.iterator();
		while (rit.hasNext()) {
			StringTransducerRule r = (StringTransducerRule)rit.next();
			if (r.isTreeMatch(s, itree, ostring, debug))
				retSet.add(r);
		}
		return retSet;
	}



	//     // create rules for an rtg by applying this tree to the rule set. Save those rules off
	//     // also create states, that can be returned by getRTGStates
	//     public HashSet getRTGRules(Tree t) {
	// 	if (rtgRules.containsKey(t))
	// 	    return (HashSet)rtgRules.get(t);
	// 	Iterator sit = states.iterator();
	// 	HashSet rh = new HashSet();
	// 	HashSet sh = new HashSet();
	// 	try {
	// 	    while (sit.hasNext()) {
	// 		Symbol s = (Symbol)sit.next();
	// 		//		Debug.debug(true, "Checking state "+s.toString());
	// 		StateTreePair st = new StateTreePair(s, t);
	// 		Iterator rit = rules.iterator();
	// 		while (rit.hasNext()) {
	// 		    StringTransducerRule r = (StringTransducerRule)rit.next();
	// 		    //		    Debug.debug(true, "Checking rule "+r.toString());
	// 		    RTGRule newRule = r.getRTGRule(st);
	// 		    if (newRule != null) {
	// 			rh.add(newRule);
	// 			sh.addAll(r.getRTGStates(st));
	// 		    }
	// 		}
	// 	    }
	// 	} catch (Exception e) {
	// 	    System.err.println(e.getMessage());
	// 	}
	// 	// sanity check
	// // 	Debug.debug(true, "StringTransducerRuleSet: "+rh.size()+" rules");
	// //  	Iterator rhit = rh.iterator();
	// //  	Debug.debug(true, "StringTransducerRuleSet: obtained ");
	// //  	while (rhit.hasNext()) {
	// //  	    Debug.debug(true, "\t"+((RTGRule)rhit.next()).toString());
	// //   }
	// 	rtgRules.put(t, rh);
	// 	rtgStates.put(t, sh);
	// 	return rh;
	//     }


	//     // get a hash set of indexed rules. no need to memoize this
	//     public HashSet getIndexedRules() {
	// 	HashSet hs = new HashSet();
	// 	Iterator it = rules.iterator();
	// 	while (it.hasNext()) {
	// 	    StringTransducerRule rule = (StringTransducerRule)it.next();
	// 	    if (rule.isIndexed())
	// 		hs.add(rule);
	// 	}
	// 	return hs;
	//     }


	// array of rules is sometimes convenient, but only when called for
	private StringTransducerRule[] rulearr = null;
	public StringTransducerRule[] getRuleArr() {
		boolean debug = false;
		if (rulearr != null)
			return rulearr;
		// could be mostly empty, but we've already created at least this many rules so it
		// shouldn't be that problematic
		rulearr = new StringTransducerRule[nextRuleIndex];
		if (debug) Debug.debug(debug, " building rule array with up to "+nextRuleIndex+" rules");
		for (Map.Entry<Integer, TransducerRule> map : rulesByIndex.entrySet()) {
			if (debug) Debug.debug(debug, "Filling slot "+map.getKey());
			rulearr[map.getKey()] = (StringTransducerRule)map.getValue();
		}
		return rulearr;
	}
	// just parsing. used by training and backwards app.
	// given the xrs (i.e. "this") and string, return the finished chart
	// cf the equivalent method in CFGRuleSet
	
	public HashSet<EarleyState>[][] parse(StringItem string, int beam, int timeLevel) {
		boolean debug = false;

		boolean minidebug = false;
		// initialize the earley state factory
//		EarleyStateFactory.init(this, string.getSize());
		EarleyStateFactory.clear();
		EarleyState.ESPair.resetEdgeCount();
		Date preParseTime = new Date();
//		Debug.prettyDebug("About to parse "+string+" in cfg with "+states.size()+" states and "+rules.size()+" rules");
		Stack<EarleyState> agenda = new Stack<EarleyState>();
		// track all states that have been added to an agenda. map from the state with no next pointers
		// to the state with all next pointers (which is the state actually added into the agenda) and update
		// the latter. 
		
//		HashMap<EarleyState, EarleyState> addedStates = new HashMap<EarleyState,EarleyState>();
		
		// categorize chart nodes by finished and unfinished variants
		// index by state id and string pos
		HashSet<EarleyState>[][] finishedChart = new HashSet[states.size()+1][string.getSize()+1];
		
		HashSet<EarleyState>[][] unfinishedChart = new HashSet[states.size()+1][string.getSize()+1];
		// track whether prediction items are handled
		// index by symbol id and string position
		boolean[][] preds = new boolean[states.size()+1][string.getSize()+1];
//		HashMap<Integer, HashSet<Integer>> preds = new HashMap<Integer, HashSet<Integer>>();
		if (debug) {
			for (Symbol checkState : states) {
				System.out.println(checkState+" -> "+s2i.get(checkState));
			}
		}
		
		// track how many times things are done
		int initCount=0;
		int predCount=0;
		int popCount=0;
		int concatCount=0;
		int shiftCount=0;
		
		// we'll need these rules for parsing
		StringTransducerRule[] arr = getRuleArr();
		// initialize agenda
		for (TransducerRule rule : getRulesOfType(getStartState())) {
			EarleyState es = EarleyStateFactory.getState(this, (StringTransducerRule) rule, 0, 0, 0);
			if (debug) Debug.debug(debug, "Initializing agenda with "+es);
			initCount++;
			es.isAdded = true;
			agenda.push(es);
	//		addedStates.put(es, es);
		}
//		HashSet<Integer> startint = new HashSet<Integer>();
	//	startint.add(0);
		preds[s2i.get(getStartState())][0] = true;
	//	preds.put(s2i.get(getStartState()), startint);
		while (!agenda.isEmpty()) {
			
//			if (agenda.size() % 1000 == 0) {
//				Debug.prettyDebug(agenda.size()+" in agenda");
//			}
			EarleyState item = agenda.pop();
			popCount++;
			int theSym = -1;
			int theInt = -1;
			HashSet<EarleyState>[][] theChart = null;
		

			if (debug) Debug.debug(debug, "Removing "+item+" from agenda");

			// can we predict anything from this item?
			int predSym = item.predict();
			int prednum = item.stringEndPos;
			if (predSym >= 0 && !preds[predSym][prednum]) {
//			if (predSym >= 0 && (!preds.containsKey(predSym) || !preds.get(predSym).contains(prednum))) {

				// add predictions for this item at this integer position
//				if (!preds.containsKey(predSym))
//					preds.put(predSym, new HashSet<Integer>());
//				preds.get(predSym).add(prednum);
				preds[predSym][prednum] = true;
				for (TransducerRule rule : getRulesOfType(i2s.get(predSym))) {
					EarleyState es = EarleyStateFactory.getState(this, (StringTransducerRule) rule, 0, prednum, prednum);
					if (debug) Debug.debug(debug, "\tAdding prediction "+es);
					es.isAdded = true;
					predCount++;
					agenda.push(es);
		//			addedStates.put(es, es);
				}
			}
			// add to finished/unfinished chart if needed 
			if (item.isFinished) {
				theChart = finishedChart;
				theSym = item.src;
				theInt = item.stringStartPos;
				if (debug) Debug.debug(debug, "\tAdding "+item+" to finished chart");
			}
			else if (predSym >= 0){
				theChart = unfinishedChart;
				theSym = predSym;
				theInt = item.stringEndPos;
				if (debug) Debug.debug(debug, "\tAdding "+item+" to unfinished chart");
			}
			else {
				if (debug) Debug.debug(debug, "\tDiscarding "+item);
			}
			// if we can add this to one of the concat charts it can be used as a concat item
			boolean isConcatItem=false;
			if (theChart != null)  {
				isConcatItem=true;
				if (theChart[theSym][theInt] == null)
					theChart[theSym][theInt] = new HashSet<EarleyState>();
				theChart[theSym][theInt].add(item);
			}
			
			// can we shift anything from this item?
			// allow shift at end as long as the item reads null
			if(item.stringEndPos <= string.getSize()) {
				Symbol shiftsym = null;
				if (item.stringEndPos < string.getSize())
					shiftsym = string.getSym(item.stringEndPos);
				else
					shiftsym = Symbol.getEpsilon();
				EarleyState shiftstate = item.shift(shiftsym, arr, this);
				if (shiftstate != null) {
					boolean didAdd = false;
					// if the state is already in there, make sure we have the version with
					// all the edges
					//					if (addedStates.containsKey(shiftstate))
					//						shiftstate = addedStates.get(shiftstate);
					// add the new edges (either to the fresh state or to the recalled one
					shiftstate.update(item);
					// now add the loaded version to the agenda and to the addedStates list
					//					if (!addedStates.containsKey(shiftstate)) {
					if (!shiftstate.isAdded) {
						if (shiftstate.isFinished && minidebug) Debug.debug(minidebug, "Shifted to complete "+shiftstate+"; "+rulearr[shiftstate.rule]);
						shiftstate.isAdded = true;
						agenda.push(shiftstate);
						shiftCount++;
						//						addedStates.put(shiftstate, shiftstate);
						didAdd = true;
					}
					// if this is updating a pointer, the agenda member and hashset member should be
					// changed appropriately!

					if (didAdd) {
						if(debug) Debug.debug(debug, "\tAdding shift "+shiftstate);
					}
					else {
						if(debug) Debug.debug(debug, "\tUpdating shift "+shiftstate);
					}
				}
			}

			// can we concat anything from this item? Only if it's a valid chart item
			if (isConcatItem) {
				if (predSym >= 0) {
					int searchInt = item.stringEndPos;
					if (finishedChart[predSym][searchInt] != null) {
						//							for (EarleyState match : finishedChart.get(predSym).get(searchInt)) {
						for (EarleyState match : finishedChart[predSym][searchInt]) {
							if (debug) Debug.debug(debug, "\tCombining "+item+" and "+match);
							EarleyState combine = EarleyStateFactory.getState(this, arr[item.rule], item.rulepos+1, item.stringStartPos, match.stringEndPos);
							boolean didAdd = false;
							concatCount++;
							// if the state is already in there, make sure we have the version with
							// all the edges
							//							if (addedStates.containsKey(combine))
							//								combine = addedStates.get(combine);
							// add the new edges (either to the fresh state or to the recalled one
							combine.update(item, match, beam);

							// now add the loaded version to the agenda and to the addedStates list
							if (!combine.isAdded) {
								if (minidebug) Debug.debug(minidebug, "Combined to make "+combine+";  "+rulearr[combine.rule]);

								//							if (!addedStates.containsKey(combine)) {
								combine.isAdded = true;
								agenda.push(combine);
								//								addedStates.put(combine, combine);
								didAdd = true;
							}
							if (didAdd) {
								if(debug) Debug.debug(debug, "\tAdding left combine "+combine);
							}
							else {
								if(debug) Debug.debug(debug, "\tUpdating left combine "+combine);
							}
						}
					}
				}
				else {
					int searchInt = item.stringStartPos;
//					if (unfinishedChart.containsKey(item.rule.getState()) && unfinishedChart.get(item.rule.getState()).containsKey(searchInt)) {
					if (unfinishedChart[item.src][searchInt] != null) {
						for (EarleyState match : unfinishedChart[item.src][searchInt]) {
							if (debug) Debug.debug(debug, "\tCombining "+match+" and "+item);
							EarleyState combine = EarleyStateFactory.getState(this, arr[match.rule], match.rulepos+1, match.stringStartPos, item.stringEndPos);
							boolean didAdd = false;
							concatCount++;
							// if the state is already in there, make sure we have the version with
							// all the edges
//							if (addedStates.containsKey(combine))
//								combine = addedStates.get(combine);
							// add the new edges (either to the fresh state or to the recalled one
							combine.update(match, item, beam);

							// now add the loaded version to the agenda and to the addedStates list
							if (!combine.isAdded) {
								if (minidebug) Debug.debug(minidebug, "Combined to make "+combine+"; "+rulearr[combine.rule]);

//								if (!addedStates.containsKey(combine)) {
								combine.isAdded = true;
								agenda.push(combine);
//								addedStates.put(combine, combine);
								didAdd = true;
							}
//							if (!addedStates.containsKey(combine)) {
//								agenda.push(combine);
//								addedStates.put(combine, combine);
//								didAdd = true;
//							}
							if (didAdd) {
								if(debug) Debug.debug(debug, "\tAdding right combine "+combine);
							}
							else {
								if(debug) Debug.debug(debug, "\tUpdating right combine "+combine);
							}
						}
					}
				}
			}				
		}
//		Debug.prettyDebug("done parsing");
		Date postParseTime = new Date();
		Debug.dbtime(timeLevel, 3, preParseTime, postParseTime, "parse string with cfg");
		//Debug.debug(true, initCount+" init "+predCount+" pred "+shiftCount+" shift "+popCount+" pop "+concatCount+" concat");
		
		return finishedChart;
	}
	

	// recursively traverse trt and add all nodes into a list
	private static Vector<TransducerRightTree> getNodes(Vector<TransducerRightTree> vec, TransducerRightTree trt) {
		vec.add(trt);
		for (int i = 0; i < trt.getNumChildren(); i++) {
			vec = getNodes(vec, trt.getChild(i));
		}

		return vec;
	}


	// set up each ttrs such that given a symbol and rank of the rhs we know which rules it applies to 
	private static Vector<Hashtable<Symbol, Hashtable<Integer, Vector<TreeRuleTuple>>>> indexTransducers(Vector<TreeTransducerRuleSet> trsvec) {
		Vector<Hashtable<Symbol, Hashtable<Integer, Vector<TreeRuleTuple>>>> ret = new Vector<Hashtable<Symbol, Hashtable<Integer, Vector<TreeRuleTuple>>>>();
		for (TreeTransducerRuleSet trs : trsvec) {
			Hashtable<Symbol, Hashtable<Integer, Vector<TreeRuleTuple>>> outer = new Hashtable<Symbol, Hashtable<Integer, Vector<TreeRuleTuple>>>();
			for (TransducerRule prer : trs.rules) {
				TreeTransducerRule r = (TreeTransducerRule)prer;
				TransducerRightTree rhsptr = r.getRHS();
				Vector<TransducerRightTree> sitvec = new Vector<TransducerRightTree>();
				sitvec = getNodes(sitvec, rhsptr);
				for (TransducerRightTree trt : sitvec) {
					if (trt.hasLabel()) {
						Symbol sym = trt.getLabel();
						int in = trt.getNumChildren();
						if (!outer.containsKey(sym))
							outer.put(sym, new Hashtable<Integer, Vector<TreeRuleTuple>>());
						if (!outer.get(sym).containsKey(in))
							outer.get(sym).put(in, new Vector<TreeRuleTuple>());
						outer.get(sym).get(in).add(new TreeRuleTuple(trt, r));
					}
				}
			}
			ret.add(outer);
		}
		return ret;

	}

	
	
	// parsing guided by a chain of transducers. used by integrated search/composition.
	// given the xrs (i.e. "this"), string, and a chain of tree transducers, return a chart
	// that allows composition of the chain
	
	
	public HashSet<EarleyState>[][] parseWithTrans(StringItem string, Vector<TreeTransducerRuleSet> chain, int beam, int timeLevel) throws UnusualConditionException {

		boolean popdebug = false;
		boolean debug = true;

		boolean minidebug = false;

		int chainLength = chain.size();
		Vector<Hashtable<Symbol, Hashtable<Integer, Vector<TreeRuleTuple>>>> rhsidx = indexTransducers(chain);

		// initialize the earley state factory
		Date preParseTime = new Date();
		Vector<EarleyState> agenda = new Vector<EarleyState>();
		// track all states that have been added to an agenda. map from the state with no next pointers
		// to the state with all next pointers (which is the state actually added into the agenda) and update
		// the latter. 


		// categorize chart nodes by finished and unfinished variants
		// index by state id and string pos
		HashSet<EarleyState>[][] finishedChart = new HashSet[states.size()][string.getSize()+1];

		HashSet<EarleyState>[][] unfinishedChart = new HashSet[states.size()][string.getSize()+1];
		// track whether prediction items are handled
		// index by symbol id and string position
		boolean[][] preds = new boolean[states.size()][string.getSize()+1];


		// track how many times things are done
		int initCount=0;
		int predCount=0;
		int popCount=0;
		int concatCount=0;
		int shiftCount=0;

		// we'll need these rules for parsing
		StringTransducerRule[] arr = getRuleArr();
		// initialize agenda
		for (TransducerRule rule : getRulesOfType(getStartState())) {
			EarleyState es = EarleyStateFactory.getState(this, (StringTransducerRule) rule, 0, 0, 0);
			if (debug) Debug.debug(debug, "Initializing agenda with "+es+"("+rule+")");
			initCount++;
			es.isAdded = true;
			agenda.add(es);
		}

		preds[s2i.get(getStartState())][0] = true;
		while (!agenda.isEmpty()) {

			//			if (agenda.size() % 1000 == 0) {
			//				Debug.prettyDebug(agenda.size()+" in agenda");
			//			}
			EarleyState item = agenda.remove(0);
			popCount++;
			int theSym = -1;
			int theInt = -1;
			HashSet<EarleyState>[][] theChart = null;


			if (popdebug) Debug.debug(popdebug, "Removing "+item+" from agenda");

			// can we predict anything from this item?
			int predSym = item.predict();
			int prednum = item.stringEndPos;
			if (predSym >= 0 && !preds[predSym][prednum]) {

				preds[predSym][prednum] = true;
				for (TransducerRule rule : getRulesOfType(i2s.get(predSym))) {
					EarleyState es = EarleyStateFactory.getState(this, (StringTransducerRule) rule, 0, prednum, prednum);
					if (debug) Debug.debug(debug, "\tAdding prediction "+es+"("+rule+")");
					es.isAdded = true;
					predCount++;
					agenda.add(es);
				}
			}
			// add to finished/unfinished chart if needed 
			if (item.isFinished) {
				theChart = finishedChart;
				theSym = item.src;
				theInt = item.stringStartPos;
				if (debug) Debug.debug(debug, "\tAdding "+item+" to finished chart");
			}
			else if (predSym >= 0){
				theChart = unfinishedChart;
				theSym = predSym;
				theInt = item.stringEndPos;
				if (debug) Debug.debug(debug, "\tAdding "+item+" to unfinished chart");
			}
			else {
				if (debug) Debug.debug(debug, "\tDiscarding "+item);
			}
			// if we can add this to one of the concat charts it can be used as a concat item
			boolean isConcatItem=false;
			if (theChart != null)  {
				isConcatItem=true;
				if (theChart[theSym][theInt] == null)
					theChart[theSym][theInt] = new HashSet<EarleyState>();
				theChart[theSym][theInt].add(item);
			}

			// can we shift anything from this item?
			// allow shift at end as long as the item reads null
			if(item.stringEndPos <= string.getSize()) {
				Symbol shiftsym = null;
				if (item.stringEndPos < string.getSize())
					shiftsym = string.getSym(item.stringEndPos);
				else
					shiftsym = Symbol.getEpsilon();
				EarleyState shiftstate = item.shift(shiftsym, arr, this);
				if (shiftstate != null) {
					boolean didAdd = false;
					// update state with edge information (it might already exist)
					// if state is finished, also pass in external rule info
					//if (shiftstate.isFinished) {
					//if (minidebug) Debug.debug(minidebug, "Shifted to complete "+shiftstate+"; "+rulearr[shiftstate.rule]);
					Symbol matchsym = rulearr[shiftstate.rule].getLHS().getLabel();
					int matchrank = rulearr[shiftstate.rule].getLHS().getNumChildren();
					if (minidebug) Debug.debug(minidebug, "Looking for rules in fill after shift matching "+matchsym+" and "+matchrank);
					// won't actually do anything with the extra stuff unless we're finished
					shiftstate.update(item, beam, rhsidx, matchsym, matchrank, rulearr[shiftstate.rule]);
					// if no updating happened, abandon this item
					if (shiftstate.next == null || shiftstate.next.size() == 0) {
						if(debug) Debug.debug(debug, "\tshift failed due to external rules"+shiftstate);
					}
					else {
						//}
						//else {
						//		shiftstate.update(item);
						//}
						// now add the loaded version to the agenda and to the addedStates list
						//					if (!addedStates.containsKey(shiftstate)) {
						if (!shiftstate.isAdded) {
							shiftstate.isAdded = true;
							agenda.add(shiftstate);
							shiftCount++;
							didAdd = true;
						}

						// if this is updating a pointer, the agenda member and hashset member should be
						// changed appropriately!

						if (didAdd) {
							if(debug) Debug.debug(debug, "\tAdding shift "+shiftstate);
						}
						else {
							if(debug) Debug.debug(debug, "\tUpdating shift "+shiftstate);
						}
					}
				}
			}

			
			// can we concat anything from this item? Only if it's a valid chart item
			if (isConcatItem) {
				// item on left, match on right (TODO: re-enable!!)
				if (predSym >= 0) {
					int searchInt = item.stringEndPos;
					if (finishedChart[predSym][searchInt] != null) {
						for (EarleyState match : finishedChart[predSym][searchInt]) {
							// no unfinished items should have any external stuff
							EarleyState combine = EarleyStateFactory.getState(this, arr[item.rule], item.rulepos+1, item.stringStartPos, match.stringEndPos);
							Symbol matchsym = rulearr[combine.rule].getLHS().getLabel();
							int matchrank = rulearr[combine.rule].getLHS().getNumChildren();
							if (minidebug) Debug.debug(minidebug, "Looking for rules in fill after combine matching "+matchsym+" and "+matchrank);
							if(debug) Debug.debug(debug, "\tAbout to update left combine "+combine);

							combine.update(item, match, beam, rhsidx, matchsym, matchrank, arr[combine.rule]);
							// if no updating happened, abandon this item
							if (combine.next == null || combine.next.size() == 0) {
								if(debug) Debug.debug(debug, "\tCombine failed due to external rules"+combine);
								continue;
							}
							concatCount++;
							// now add the loaded version to the agenda and to the addedStates list
							if (!combine.isAdded) {
								if (minidebug) Debug.debug(minidebug, "Combined to make "+combine+";  "+rulearr[combine.rule]);
								combine.isAdded = true;
								agenda.add(combine);
								if(debug) Debug.debug(debug, "\tAdded left combine "+combine);
							}
							else {
								if(debug) Debug.debug(debug, "\tUpdated left combine "+combine);
							}
						}

					}	
				}
				// match on left, item on right
				else {
					int searchInt = item.stringStartPos;
					if (unfinishedChart[item.src][searchInt] != null) {
						for (EarleyState match : unfinishedChart[item.src][searchInt]) {
//							// though it would get caught later, avoid extra work by checking that top symbol of match matches parent of uncompleted item tree's first item
//							if (item.matches != null && item.matches.get(0).tree.parent != null && rulearr[match.rule].getLHS().getLabel() != item.matches.get(0).tree.parent.getLabel()) {
//								if (debug) Debug.debug(debug, "\tFiltering out "+match+" as label doesn't match first external rule of "+item);
//								continue;
//							}
//							// also check the rank
//							if (item.matches != null && item.matches.get(0).tree.parent != null && rulearr[match.rule].getLHS().getNumChildren() != item.matches.get(0).tree.parent.getNumChildren()) {
//								if (debug) Debug.debug(debug, "\tFiltering out "+match+" as rank doesn't match first external rule of "+item);
//								continue;
//							}
//							if (debug) Debug.debug(debug, "\tCombining "+match+" and "+item);
							
							// no unfinished items should have any external stuff
							EarleyState combine = EarleyStateFactory.getState(this, arr[match.rule], match.rulepos+1, match.stringStartPos, item.stringEndPos);
							
							// if the state is already in there, make sure we have the version with
							// all the edges
							// add the new edges (either to the fresh state or to the recalled one
							

							// add previous transducer info, duplicating edges as needed
							
							//if (combine.isFinished) {
								Symbol matchsym = rulearr[combine.rule].getLHS().getLabel();
								int matchrank = rulearr[combine.rule].getLHS().getNumChildren();
								if (minidebug) Debug.debug(minidebug, "Looking for rules in fill after combine matching "+matchsym+" and "+matchrank);
								if(debug) Debug.debug(debug, "\tAbout to update right combine "+combine+"=("+match+", "+item+")");
								combine.update(match, item, beam, rhsidx, matchsym, matchrank, arr[combine.rule]);							
								// if no updating happened, abandon this item
								if (combine.next == null || combine.next.size() == 0) {
									if(debug) Debug.debug(debug, "\tCombine failed due to external rules"+combine);
									continue;
								}
								//}
							//else {
						//		combine.update(match, item, beam);
						//	}
							concatCount++;

							// now add the loaded version to the agenda and to the addedStates list
							if (!combine.isAdded) {
								if (minidebug) Debug.debug(minidebug, "Combined to make "+combine+";  "+rulearr[combine.rule]);
								combine.isAdded = true;
								agenda.add(combine);
								if(debug) Debug.debug(debug, "\tAdded right combine "+combine);
							}
							else {
								if(debug) Debug.debug(debug, "\tUpdated right combine "+combine);
							}
							
						}
					}
				}
			}
		}
//		Debug.prettyDebug("done parsing");
		Date postParseTime = new Date();
		Debug.dbtime(timeLevel, 3, preParseTime, postParseTime, "parse string with xrs");
		//Debug.debug(true, initCount+" init "+predCount+" pred "+shiftCount+" shift "+popCount+" pop "+concatCount+" concat");
		EarleyStateFactory.clear();
		return finishedChart;
	}
	


	
	// test code
	public static void main(String argv[]) {
		try {


			TrueRealSemiring s = new TrueRealSemiring();


			String encoding = "euc-jp";
			int beam = 0;
			Vector<TreeTransducerRuleSet> chain = new Vector<TreeTransducerRuleSet>();
			for (int i = 0; i < argv.length-2; i++)
				chain.add(0, new TreeTransducerRuleSet(new BufferedReader(new InputStreamReader(new FileInputStream(argv[i]), encoding)), s));
			StringTransducerRuleSet strs = new StringTransducerRuleSet(new BufferedReader(new InputStreamReader(new FileInputStream(argv[argv.length-2]), encoding)), s);
			StringItem string = new StringItem(new BufferedReader(new InputStreamReader(new FileInputStream(argv[argv.length-1]), encoding)));
			
			
			RTGRuleSet rts = new RTGRuleSet(chain, strs, string, beam, 1);
			
			System.out.println(rts.toString());
			
//			HashSet<EarleyState>[][] chart = strs.parseWithTrans(string, chain, 0, 0);
//			
//			
//			ArrayList<EarleyState> todoList = new ArrayList<EarleyState>();
//
//			if (chart[strs.s2i(strs.startState)][0] != null) {
//				for (EarleyState item : chart[strs.s2i(strs.startState)][0]) {
//					if (item.stringEndPos == string.getSize()) {
//						Debug.debug(true, "Traversing path for "+item);
//						item.isTodo = true;
//						todoList.add(item);
//					}
//				}
//			}
//			RTGRuleSet dummy = new RTGRuleSet(s);
//			while (todoList.size() > 0) {
//				EarleyState item = todoList.remove(0);
//				Symbol itemState = SymbolFactory.getStateSymbol(item.ruleMatch.state+":"+item.src+":"+item.stringStartPos+":"+item.stringEndPos);
//				Debug.debug(true, "Adding RTG Rule for "+item);
//				Vector<RTGRule> newrule = item.buildTransRTG(itemState, todoList, dummy);
//				Debug.debug(true, "Created new RTG Rule "+newrule);
//			}
			

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

	private static void runGC () throws Exception
	{
		// It helps to call Runtime.gc()
		// using several method calls:
		for (int r = 0; r < 4; ++ r) _runGC ();
	}

	private static void _runGC () throws Exception
	{
		long usedMem1 = usedMemory (), usedMem2 = Long.MAX_VALUE;
		for (int i = 0; (usedMem1 < usedMem2) && (i < 500); ++ i)
		{
			s_runtime.runFinalization ();
			s_runtime.gc ();
			Thread.currentThread ().yield ();

			usedMem2 = usedMem1;
			usedMem1 = usedMemory ();
		}
	}

	private static long usedMemory ()
	{
		return s_runtime.totalMemory () - s_runtime.freeMemory ();
	}

	private static final Runtime s_runtime = Runtime.getRuntime ();


}
