package edu.isi.tiburon;

import java.util.Hashtable;
import java.util.Vector;



/* used to wrangle EarleyStates, and not do unnecessary creation or tracking
 * Holds a list of states by rule id, rule position, string start, and string *length* (not end -- this saves space!)
 * length = end-start
 * goes to constructor if cell is unfilled, and returns existing cell if filled.
 * only used during parsing!
 * 
 * initialized with capacities at beginning of parsing
 */
public class EarleyStateFactory {
	static private Hashtable<Integer, Hashtable<Integer, Hashtable<Integer, Hashtable<Integer, EarleyState>>>> states = new Hashtable<Integer, Hashtable<Integer, Hashtable<Integer, Hashtable<Integer, EarleyState>>>>();
	// states that have transducer rule constraints
	// TODO: generalize this to vectors
	// TODO: merge this with the above table
	// will distinct but identical trees match the same here? should i index on gorn address instead?
//	static private Hashtable<Integer, 
//	Hashtable<Integer, 
//	Hashtable<Integer, 
//	Hashtable<Integer, 
//	Hashtable<Vector<TreeRuleTuple>, EarleyState>>>>> trstates = new Hashtable<Integer, 
//																    Hashtable<Integer, 
//																    Hashtable<Integer, 
//																    Hashtable<Integer, 
//																    Hashtable<Vector<TreeRuleTuple>, EarleyState>>>>>();
//																   

	//static private Hashtable<EarleyState, Hashtable<EarleyState, EarleyState[]>> combedges = new Hashtable<EarleyState, Hashtable<EarleyState, EarleyState[]>>();
	//static private Hashtable<EarleyState, EarleyState[]> shiftedges = new Hashtable<EarleyState, EarleyState[]>();

	//	static public void init(CFGRuleSet rules, int strsize) {
//		int maxlen = strsize+1;
//		int rulesize = rules.getNumRules();
//		boolean debug = true;
//		if (debug) Debug.debug(debug, "Initializing to "+rulesize+", ?, up to "+maxlen);
//		states = new EarleyState[rulesize][][][];
//		for (Rule r : rules.rules) {
//			int rhssize = r.rhs.getSize()+1;
//			states[r.index] = new EarleyState[rhssize][maxlen][];
//			// triangulate last dimension -- only can be as long as the remaining length
//			// same triangle for each position of rhssize, hence the out-of-order initialization
//			for (int beg = 0; beg < maxlen; beg++) {
//				int len = maxlen-beg;
//				for (int pos = 0; pos < rhssize; pos++) {
//					states[r.index][pos][beg] = new EarleyState[len];
//				}
//			}
//		}
//	}
	static private int statecount=0;
	static private int callcount=0;
	//static private int combedgecount=0;
	//static private int shiftedgecount = 0;
	static public int getCount() { return statecount; }
	static public EarleyState getState(CFGRuleSet rs, CFGRule r, int rp, int ssp, int sep) {
		int ruleid = r.index;
		
		if (!states.containsKey(ruleid))
			states.put(ruleid, new Hashtable<Integer, Hashtable<Integer, Hashtable<Integer, EarleyState>>>());
		if (!states.get(ruleid).containsKey(rp))
			states.get(ruleid).put(rp, new Hashtable<Integer, Hashtable<Integer, EarleyState>>());
		if (!states.get(ruleid).get(rp).containsKey(ssp)) {
			states.get(ruleid).get(rp).put(ssp, new Hashtable<Integer, EarleyState>());
			statecount++;
//			if (statecount % 100 == 0)
//				Debug.prettyDebug("Added "+statecount+" states");
		}
		if (!states.get(ruleid).get(rp).get(ssp).containsKey(sep))
			states.get(ruleid).get(rp).get(ssp).put(sep, new EarleyState(rs, r, rp, ssp, sep));
//		try {
//			
//			if (states[ruleid][rp][ssp][len] == null)
//				states[ruleid][rp][ssp][len] = new EarleyState(rs, r, rp, ssp, sep);
//		}
//		catch (ArrayIndexOutOfBoundsException e) {
//			throw new ArrayIndexOutOfBoundsException("tried to get "+ruleid+", "+rp+", "+ssp+", "+len+"( from "+sep+")"+" using "+r);
//		}
		callcount++;
//		if (callcount % 10000 == 0)
//			Debug.prettyDebug("Called "+callcount+" states");
		return states.get(ruleid).get(rp).get(ssp).get(sep);
	}
	
	static public EarleyState getState(StringTransducerRuleSet rs, StringTransducerRule r, int rp, int ssp, int sep) {
		int ruleid = r.getIndex();

		EarleyState ret = null;
		
			if (!states.containsKey(ruleid))
				states.put(ruleid, new Hashtable<Integer, Hashtable<Integer, Hashtable<Integer, EarleyState>>>());
			if (!states.get(ruleid).containsKey(rp))
				states.get(ruleid).put(rp, new Hashtable<Integer, Hashtable<Integer, EarleyState>>());
			if (!states.get(ruleid).get(rp).containsKey(ssp)) {
				states.get(ruleid).get(rp).put(ssp, new Hashtable<Integer, EarleyState>());
				statecount++;
				//			if (statecount % 100 == 0)
				//				Debug.prettyDebug("Added "+statecount+" states");
			}
			if (!states.get(ruleid).get(rp).get(ssp).containsKey(sep))
				states.get(ruleid).get(rp).get(ssp).put(sep, new EarleyState(rs, r, rp, ssp, sep));

			callcount++;
			//		if (callcount % 10000 == 0)
			//			Debug.prettyDebug("Called "+callcount+" states");
			ret = states.get(ruleid).get(rp).get(ssp).get(sep);
//		}
//		else {
//			if (!trstates.containsKey(ruleid))
//				trstates.put(ruleid, new Hashtable<Integer, 
//						Hashtable<Integer, 
//						Hashtable<Integer, 
//						Hashtable<Vector<TreeRuleTuple>, EarleyState>>>>());
//			if (!trstates.get(ruleid).containsKey(rp))
//				trstates.get(ruleid).put(rp, new Hashtable<Integer, 
//						Hashtable<Integer, 
//						Hashtable<Vector<TreeRuleTuple>, EarleyState>>>());
//			if (!trstates.get(ruleid).get(rp).containsKey(ssp))
//				trstates.get(ruleid).get(rp).put(ssp, new Hashtable<Integer, 
//						Hashtable<Vector<TreeRuleTuple>, EarleyState>>());
//			if (!trstates.get(ruleid).get(rp).get(ssp).containsKey(sep))
//				trstates.get(ruleid).get(rp).get(ssp).put(sep, new Hashtable<Vector<TreeRuleTuple>, EarleyState>());
//			statecount++;
//
//			//			if (statecount % 100 == 0)
//			//				Debug.prettyDebug("Added "+statecount+" trstates");
//
//			if (!trstates.get(ruleid).get(rp).get(ssp).get(sep).containsKey(tuples))
//				trstates.get(ruleid).get(rp).get(ssp).get(sep).put(tuples, new EarleyState(rs, r, rp, ssp, sep, tuples));
//
//			callcount++;
//			//		if (callcount % 10000 == 0)
//			//			Debug.prettyDebug("Called "+callcount+" trstates");
//			ret = trstates.get(ruleid).get(rp).get(ssp).get(sep).get(tuples);
//		}
		return ret;
	}

/*	static public EarleyState getState(EarleyState oldState, Vector<TreeRuleTuple> tuples) {
		
		int ruleid = oldState.rule;
		int rp = oldState.rulepos;
		int ssp = oldState.stringStartPos;
		int sep = oldState.stringEndPos;
		if (!trstates.containsKey(ruleid))
			trstates.put(ruleid, new Hashtable<Integer, 
									 Hashtable<Integer, 
									 Hashtable<Integer, 
									 Hashtable<Vector<TreeRuleTuple>, EarleyState>>>>());
		if (!trstates.get(ruleid).containsKey(rp))
			trstates.get(ruleid).put(rp, new Hashtable<Integer, 
										     Hashtable<Integer, 
										     Hashtable<Vector<TreeRuleTuple>, EarleyState>>>());	
		if (!trstates.get(ruleid).get(rp).containsKey(ssp))
			trstates.get(ruleid).get(rp).put(ssp, new Hashtable<Integer, 
													  Hashtable<Vector<TreeRuleTuple>, EarleyState>>());
		if (!trstates.get(ruleid).get(rp).get(ssp).containsKey(sep))
			trstates.get(ruleid).get(rp).get(ssp).put(sep, new Hashtable<Vector<TreeRuleTuple>, EarleyState>());
			statecount++;
		
		//			if (statecount % 100 == 0)
		//				Debug.prettyDebug("Added "+statecount+" trstates");

		if (!trstates.get(ruleid).get(rp).get(ssp).get(sep).containsKey(tuples))
			trstates.get(ruleid).get(rp).get(ssp).get(sep).put(tuples, new EarleyState(oldState, tuples));

		callcount++;
		//		if (callcount % 10000 == 0)
		//			Debug.prettyDebug("Called "+callcount+" trstates");
		return trstates.get(ruleid).get(rp).get(ssp).get(sep).get(tuples);
	}
	
	*/
	// construct (or recall) a state using rightitem's vector of rules, advancing position in the last rule
	// used by both ways of combining
/*	
	static public EarleyState getInsertState(StringTransducerRuleSet rs, StringTransducerRule r, EarleyState leftitem, EarleyState rightitem, int beam) {
		Vector<TreeRuleTuple> newvec = new Vector<TreeRuleTuple>();
		Vector<TreeRuleTuple> oldvec = rightitem.matches;
		// base: change the parent
		TreeRuleTuple base = new TreeRuleTuple(oldvec.get(0).tree.parent, oldvec.get(0).rule);
		//boolean atTop = base.tree.parent == null;
		newvec.add(base);
		// rest: insert rules with no change
		for (int i = 1; i < oldvec.size(); i++) {
			newvec.add(oldvec.get(i));
		}
		// create/get the new state
		EarleyState ret = getState(rs, r, leftitem.rulepos+1, leftitem.stringStartPos, rightitem.stringEndPos, newvec);
		// update the state with these two items
		ret.update(leftitem, rightitem, beam);
		return ret;
	}
	
	*/
	
	/*// construct/recall one or more states by combining two states and forming the appropriate vectors
	static public Vector<EarleyState> getExternalMatches(StringTransducerRuleSet trs,
															StringTransducerRule leftRule,
															 EarleyState leftitem, 
															 EarleyState rightitem, 
															 Vector<Hashtable<Symbol, Hashtable<Integer, Vector<TreeRuleTuple>>>> rhsidx,
															 Symbol matchsym,
															 int matchrank,
															 int beam) {
		Vector<EarleyState> ret = new Vector<EarleyState>();
		boolean debug = true;
		// i'm having a hard time envisioning this so i'm just building based on the last item
		// TODO: make this construction correct!
		// TODO: use more than last item!
		
		// left item pointing to a state that matches the state completed by right item
		// stick with same items as left item but move pos over.
		if (leftitem.matches != null && leftitem.matches.size() > 0) {
			TreeRuleTuple lefttup = leftitem.matches.get(0);
			TreeRuleTuple righttup = rightitem.matches.get(0);
			if (lefttup.tree.getChild(leftitem.rulepos).hasState() && lefttup.tree.getChild(leftitem.rulepos).getState() == righttup.rule.getState()) {
				if (debug) Debug.debug(debug, "\tMerging two extant external rule items: "+leftitem+" and "+rightitem);
				EarleyState combine = EarleyStateFactory.getState(trs, leftRule, leftitem.rulepos+1, leftitem.stringStartPos, rightitem.stringEndPos, leftitem.matches);
				combine.update(leftitem, rightitem, beam);
				ret.add(combine);
			}
		}
		// left item not pointing to anything. check all possible additions and add them
		// use EarleyState's method, and then do the updating
		// TODO: maybe memoize this or something?
		// TODO: I think this needs to check state!
		else {
			Vector<EarleyState> rawset = leftitem.getExternalMatches(rhsidx, matchsym, matchrank);
			for (EarleyState raw : rawset)  {
				raw.update(leftitem, rightitem, beam);
				ret.add(raw);
			}
		}
		
		
		return ret;
		
	}
	
	*/
//	static public EarleyState[] getCombEdge(EarleyState es1, EarleyState es2) {
//		if (!combedges.containsKey(es1))
//			combedges.put(es1, new Hashtable<EarleyState, EarleyState[]>());
//		if(!combedges.get(es1).containsKey(es2)) {
//			EarleyState[] v = new EarleyState[2];
//			v[0] = es1;
//			v[1] = es2;
//			combedges.get(es1).put(es2, v);
//			combedgecount++;
//			if (combedgecount % 100000 == 0)
//				Debug.prettyDebug("Added "+combedgecount+" comb edges");
//		}
//		else {
//			Debug.debug(true, "Reusing edge "+es1+", "+es2);
//		}
//		return combedges.get(es1).get(es2);
//	}
//	static public EarleyState[] getShiftEdge(EarleyState es1) {
//		if (!shiftedges.containsKey(es1)) {
//			EarleyState[] v = new EarleyState[2];
//			v[0] = es1;
//			shiftedges.put(es1, v);
//			shiftedgecount++;
////			if (shiftedgecount % 1000 == 0)
//				Debug.prettyDebug("Added "+shiftedgecount+" shift edges");
//		}
//		else {
//			Debug.debug(true, "Reusing edge "+es1);
//		}
//		return shiftedges.get(es1);
//	}
	static public void clear() {
		
	//	long beforeclear = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
	//	Debug.debug(true, "before clear: heap at "+beforeclear);
		states = new Hashtable<Integer, Hashtable<Integer, Hashtable<Integer, Hashtable<Integer, EarleyState>>>>();
//		System.gc();
	//	long afterclear = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
	//	Debug.debug(true, "after clear: heap at "+afterclear);
		//	combedges = new Hashtable<EarleyState, Hashtable<EarleyState, EarleyState[]>>();
		//shiftedges = new Hashtable<EarleyState, EarleyState[]>();
		//statecount = combedgecount = shiftedgecount = 0;
		statecount = 0;
		callcount = 0;
	}
	
}
