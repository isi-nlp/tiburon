package edu.isi.tiburon;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;

// command line intersection of two weighted grammars
public class Intersect {
	// given two comparable rules, return their combination.
	// I think this can be done by copying one of the rules, getting the 
	// leaf set of both of them, then modifying the symbols if they are states
	private static RTGRule combineRules(RTGRule ra, RTGRule rb, RTGRuleSet sa, RTGRuleSet sb) {
		RTGRule rab = new RTGRule(ra);
		IntersectPair headIntersectPair = new IntersectPair(ra.getLHS(), rb.getLHS());

		rab.setLHS(headIntersectPair.getJoin());
		TreeItem [] abterm = (TreeItem [])rab.getRHS().getItemLeaves();
		TreeItem [] bterm = (TreeItem [])rb.getRHS().getItemLeaves();
		if (abterm.length != bterm.length) {
			Debug.debug(true, "ERROR: rules have different nonterminal length sets!");
			return null;
		}
		for (int i = 0; i < abterm.length; i++) {
			if (sa.states.contains(abterm[i].label)) {
				IntersectPair p = new IntersectPair(abterm[i].label, bterm[i].label);
				abterm[i].label = p.getJoin();
			}
		}
		rab.setWeight(sa.getSemiring().times(rab.getWeight(), rb.getWeight()));
		if (rb.hasTransducerRule())
			rab.tieToTransducerRule((Vector)rb.getTransducerRules());
		return rab;
	}

	// most of the real work is done on the private tree comparison method within
	public static boolean areRulesComparable(RTGRule ra, RTGRule rb, RTGRuleSet sa, RTGRuleSet sb) {
		TreeItem ta = (TreeItem)ra.getRHS();
		TreeItem tb = (TreeItem)rb.getRHS();
		return (areTreesComparable(ta, tb, sa, sb));
	}

	private static boolean areTreesComparable(TreeItem ta, TreeItem tb, RTGRuleSet sa, RTGRuleSet sb) {
		if (ta.numChildren != tb.numChildren)
			return false;
		if (ta.numChildren == 0) {
			// they can both be states, or...
			boolean isAstate = sa.states.contains(ta.label);
			boolean isBstate = sb.states.contains(tb.label);
			if (isAstate && isBstate) {
				//		Debug.debug(true, ta.label.toString()+" and "+tb.label.toString()+" are states");
				return true;
			}
			// they can be the same label (and not be states)
			else if (!isAstate && !isBstate && ta.label.equals(tb.label)) {
				//		Debug.debug(true, ta.label.toString()+" and "+tb.label.toString()+" are the same");
				return true;
			}
			else
				return false;
		}
		// to be true, labels must be true and all children must be true
		else {
			if (!ta.label.equals(tb.label))
				return false;
			for (int i = 0; i < ta.numChildren; i++) {
				if (!areTreesComparable((TreeItem)ta.children[i], (TreeItem)tb.children[i], sa, sb))
					return false;
			}
			return true;
		}
	}

	// given a rule assumed to have a transducer state as its tree (rhs) of the form x->a and a
	// state known to also be a rule state, called b, construct rule x_b -> a_b, where a_b is a combined state
	// in transducer land, but a literal in ruleset land
	public static RTGRule createStateRule(RTGRule ra, Symbol b) {
		RTGRule rab = new RTGRule(ra);
		IntersectPair headIntersectPair = new IntersectPair(ra.getLHS(), b);
		rab.setLHS(headIntersectPair.getJoin());
		IntersectPair dstIntersectPair = new IntersectPair(ra.getRHS().label, b);
		rab.setRHS(new TreeItem(dstIntersectPair.getJoin(), ((TreeItem)ra.getRHS()).getHiddenVariable()));
		return rab;
	}

	// compute the intersection of rule sets (top down rtgs) via rule multiplication
	public static RTGRuleSet intersectRuleSets(RTGRuleSet a, RTGRuleSet b) {

//		Debug.debug(true, "Intersecting "+a.states.size()+" states and "+a.rules.size()+" rules "+
//					      "with "+b.states.size()+" states and "+b.rules.size()+" rules");
//		Debug.debug(true, b.toString());
		boolean debug = false;
		// 	if (debug) {
		// 	    Debug.debug(true, "Intersecting: ");
		// 	    System.err.print(a.toString());
		// 	    Debug.debug(true, "And: ");
		// 	    System.err.print(b.toString());
		// 	}
		// start with start states. create a start state that represents both. Then get all rules
		// that have the same structure, and multiply out the states resulting. continue 
		IntersectPair starts = new IntersectPair(a.startState, b.startState);
		// the set of state combinations we've taken care of
		HashSet<IntersectPair> processedSet = new HashSet<IntersectPair>();
		// the set of state combinations we need to take care of
		HashSet<IntersectPair> todoSet = new HashSet<IntersectPair>();
		todoSet.add(starts);

		// the core algorithm, for each state pair that we can reach, get all 
		// the intersecting rules, and add them to the rules set.
		// also consider the new state pairs that must be processed

		HashSet<RTGRule> newRules = new HashSet<RTGRule>();
		while (!todoSet.isEmpty()) {
			// doset exists so we can delete from todoset
			HashSet<IntersectPair> doSet = new HashSet<IntersectPair>(todoSet);

			// sanity check
			if (debug) Debug.debug(debug, "*****\nTODOSET: "+doSet);
			if (debug) Debug.debug(debug, "PROCSET: "+processedSet);
				

			for (IntersectPair p : doSet) {
				// no longer need to do it
				todoSet.remove(p);
				// and we're covering it now
				processedSet.add(p);
				if (debug) Debug.debug(debug, "Adding "+p.getJoin()+" to processed set");
				// coverage: get pairs of rules that line up, 
				// add these new rules to a new set, and figure out
				// the combo-states that result
				if (debug)System.err.print("Getting rules of type "+p.a+": ");
				ArrayList<Rule> aSet = a.getRulesOfType(p.a);
				if (aSet == null) {
					if (debug) Debug.debug(debug, "0 rules");
					continue;
				}
				if (debug) Debug.debug(debug, aSet.size()+" rules");
				if (debug) System.err.print("Getting rules of type "+p.b+": ");
				ArrayList<Rule> bSet = b.getRulesOfType(p.b);
				if (bSet == null) {
					if (debug) Debug.debug(debug, "0 rules");
					continue;
				}
				if (debug)Debug.debug(true, bSet.size()+" rules");
				Iterator<Rule> ita = aSet.iterator();
				while (ita.hasNext()) {
					RTGRule ra = (RTGRule)ita.next();
					// special case: if ra has a transducer state as its rhs, we don't need to find a rb,
					// but instead create a rule that ties the ra transducer state and the rb state (which
					// in composition is a transducer state too)
					if (((TreeItem)ra.getRHS()).isTransducerState()) {
						RTGRule rab = createStateRule(ra, p.b);
						if (debug) Debug.debug(debug, "Special transducer state rule "+ra.toString()+" yields special construction "+rab.toString());
						newRules.add(rab);
						// consider no new states
					}
					else {
						Iterator<Rule> itb = bSet.iterator();
						while (itb.hasNext()) {
							RTGRule rb = (RTGRule)itb.next();
							if (areRulesComparable(ra, rb, a, b)) {
								if (debug) Debug.debug(debug, "Comparable Rules: \n"+ra.toString()+"\n"+rb.toString());
								RTGRule rab = combineRules(ra, rb, a, b);
								newRules.add(rab);
								// now figure out which new states to consider
								TreeItem []aterms = (TreeItem [])ra.getRHS().getItemLeaves();
								TreeItem []bterms = (TreeItem [])rb.getRHS().getItemLeaves();
								if (aterms.length != bterms.length) {
									System.err.println("ERROR: terminals of trees should be same length!");
									break;
								}
								for (int i = 0; i < aterms.length; i++) {
									IntersectPair np = new IntersectPair(aterms[i].label, bterms[i].label);
									if (!processedSet.contains(np)) {
										if (debug && todoSet.contains(np))
											System.err.println("Todo set already contains "+np.getJoin().toString());
										todoSet.add(np);
										if (debug) Debug.debug(debug, "Adding "+np.getJoin().toString()+" to todo set");
									}
								}
							}
							else {
								if (debug) Debug.debug(debug, "Rules are not comparable: \n"+ra.toString()+"\n"+rb.toString());
							}
						}
					}
				}
			}

		}
		// now we've got the new rule set and the start symbol
		// so, build the rule set and return

		RTGRule [] nra = new RTGRule[newRules.size()];
		Iterator<RTGRule> it = newRules.iterator();
		int idx = 0;
		while (it.hasNext())
			nra[idx++] = it.next();
		RTGRuleSet nrs = new RTGRuleSet(starts.getJoin(), nra, a.getSemiring());

		// now to check for extra spurious states the easy way: there should be no terminal
		// symbols in the new rule set that were not in either of the first two (except for special case states). If there are
		// remove all rules involving that production as they're  probably dangling states.
		if (debug) Debug.debug(debug, "Intersection ruleset before deleting:"+nrs.toString());
		HashSet terms = a.getTerminals();
		terms.addAll(b.getTerminals());
		HashSet newterms = nrs.getTerminals();
		newterms.removeAll(terms);
		while (newterms.size() > 0) {
			if (debug) Debug.debug(debug, "Intersection ruleset before deleting:"+nrs.toString());
			// sanity check
			Iterator ntit = newterms.iterator();
			if (debug) {
				System.err.println("Terminals that must be deleted:");
				while (ntit.hasNext())
					System.err.println("\t"+((Symbol)ntit.next()).toString());
			}

			// the removal process
			// need to copy because we're deleting in iteration
			HashSet rulecopy = new HashSet(nrs.rules);
			Iterator ntrit = rulecopy.iterator();
			while (ntrit.hasNext()) {
				RTGRule r = (RTGRule)ntrit.next();
				HashSet rterms = nrs.findTerminals((TreeItem)r.getRHS());
				// if rterms and newterms have some intersection
				// we need to delete r
				rterms.retainAll(newterms);
				if (rterms.size() > 0) {
					if (debug) Debug.debug(debug, "Removing rule "+r.toString()+" with bad terminals");
					nrs.rules.remove(r);
				}
			}
			nrs.initialize();
			newterms = nrs.getTerminals();
			newterms.removeAll(terms);
		}

		nrs.pruneUseless();   

		return nrs;

	}


	/**
       @deprecated Use Tiburon <no output options> instead
	 */

	public static void main(String argv[]) throws Exception {

		try {
			RealSemiring s = new RealSemiring();
			String encoding = "utf-8";
			RTGRuleSet rs1 = new RTGRuleSet(argv[0], "utf-8", s);
			rs1.makeNormal();
			RTGRuleSet rs2 = new RTGRuleSet(argv[1], "utf-8", s);
			rs2.makeNormal();

			Date preIsectTime = new Date();
			RTGRuleSet rs12 = Intersect.intersectRuleSets(rs1, rs2);
			Date postIsectTime = new Date();
			Debug.dbtime(1, 1, preIsectTime, postIsectTime, "intersect algorithm");
			System.out.println(rs12);

			Date preCompIsectTime = new Date();
			TreeTransducerRuleSet trs1 = new TreeTransducerRuleSet(rs1);
			TreeTransducerRuleSet trs2 = new TreeTransducerRuleSet(rs2);
			TreeTransducerRuleSet trs3 = new TreeTransducerRuleSet(trs1, trs2, 0);
			RTGRuleSet rstrs3 = new RTGRuleSet(trs3);	    
			Date postCompIsectTime = new Date();
			Debug.dbtime(1, 1, preCompIsectTime, postCompIsectTime, "intersect via composition algorithm");
			System.out.println(rstrs3);
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
}
