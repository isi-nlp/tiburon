package edu.isi.tiburon;

// manages hashmaps of symbols -> strings and vice versa
import java.util.HashMap;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class SymbolFactory {
	static private HashMap<HString, Symbol> str2Sym;
	static private HashMap<Set<Symbol>, Symbol> set2Sym;
	static private HashMap<Symbol, HashMap<TreeItem, HashMap<Integer, HashMap<Integer, StateTreePair>>>> str2Stp;
	static {
		str2Sym = new HashMap<HString, Symbol>();
		str2Stp = new HashMap<Symbol, HashMap<TreeItem, HashMap<Integer, HashMap<Integer, StateTreePair>>>>();
		set2Sym = new HashMap<Set<Symbol>, Symbol>();
	}

	// has to do a regex check for q\d+, unfortunately. this will slow things down
	private static Pattern statePat = Pattern.compile("q(\\d+)");

	private static final int REP_SIZE=1000;
	private static int normalSymCount=0;
	private static int stateSymCount=0;
	private static int setSymCount=0;
	private static int stpSymCount=0;
	private static int vecSymCount=0;
	static public Symbol getSymbol(String str) {
		boolean debug = false;
		boolean capDebug = false;
		if (debug) Debug.debug(debug, "getting symbol for "+str);
		HString h = new HString(str);
		if (!str2Sym.containsKey(h)) {
			//	     	if (!str2Sym.containsKey(str.intern())) {
			if (debug) Debug.debug(debug, "Checking unseen symbol for state collision");
			Matcher spmatch = statePat.matcher(str);
			if (spmatch.matches()) {
				int update = AliasedSymbol.update(Integer.parseInt(spmatch.group(1)));
				if (debug) Debug.debug(debug, "Updating AliasedSymbol index with "+str+" to "+update);
			}
			if (debug) Debug.debug(debug, "creating new symbol from "+str);
			OldSymbol sym = new OldSymbol(str);
			//	    	    str2Sym.put(str.intern(), sym);
			str2Sym.put(h, sym);
			normalSymCount++;
			if (capDebug && normalSymCount % REP_SIZE == 0)
				Debug.debug(capDebug, "Created "+normalSymCount+" normal symbols");
		}
		//	 	return (Symbol)str2Sym.get(str.intern());
		return str2Sym.get(h);
		//return sym;
	}
	
	static public Symbol getStateSymbol(String str) {
		boolean debug = false;
		boolean capDebug = false;

		if (debug) Debug.debug(debug, "getting state symbol for "+str);
		HString h = new HString(str);
		if (!str2Sym.containsKey(h)) {
			//	     	if (!str2Sym.containsKey(str.intern())) {
			if (debug) Debug.debug(debug, "creating new symbol from "+str);
			AliasedSymbol sym = new AliasedSymbol(str);
			//	    	    str2Sym.put(str.intern(), sym);
			str2Sym.put(h, sym);
			stateSymCount++;
			if (capDebug && stateSymCount % REP_SIZE == 0)
				Debug.debug(capDebug, "Created "+stateSymCount+" state symbols");
		}
		//	 	return (Symbol)str2Sym.get(str.intern());
		return str2Sym.get(h);
		//return sym;
	}
	static public Symbol getStateSymbol() {
		boolean debug = false;
		boolean capDebug = false;
		if (debug) Debug.debug(debug, "getting new state symbol ");
		AliasedSymbol sym = new AliasedSymbol();
		str2Sym.put(new HString(sym.toString()), sym);
		stateSymCount++;
		if (capDebug && stateSymCount % REP_SIZE == 0)
			Debug.debug(capDebug, "Created "+stateSymCount+" state symbols");
		return sym;
	}
	
	static public Symbol getSymbol(Set<Symbol> set) {
		boolean debug = false;
		boolean capDebug = false;

		if (debug) Debug.debug(debug, "getting symbol for set "+set);
		if (!set2Sym.containsKey(set)) {
			if (debug) Debug.debug(debug, "creating new symbol from set "+set);
			StringBuffer buf = new StringBuffer();
			for (Symbol sym : set)
				buf.append(sym.toString()+"_");
			OldSymbol sym = new OldSymbol(buf.toString());
			if (debug) Debug.debug(debug, "new symbol is "+sym);
			set2Sym.put(set, sym);
			setSymCount++;
			if (capDebug && setSymCount % REP_SIZE == 0)
				Debug.debug(capDebug, "Created "+setSymCount+" set symbols");
		}
		return set2Sym.get(set);
	}
	
	static public Symbol getStateSymbol(Set<Symbol> set) {
		boolean debug = false;
		boolean capDebug = false;
		if (debug) Debug.debug(debug, "getting state symbol for set "+set);
		if (!set2Sym.containsKey(set)) {
			if (debug) Debug.debug(debug, "creating new symbol from set "+set);
			StringBuffer buf = new StringBuffer();
			for (Symbol sym : set)
				buf.append(sym.toString()+"_");
			AliasedSymbol sym = new AliasedSymbol(buf.toString());
			if (debug) Debug.debug(debug, "new symbol is "+sym);
			set2Sym.put(set, sym);
			setSymCount++;
			if (capDebug && setSymCount % REP_SIZE == 0)
				Debug.debug(capDebug, "Created "+setSymCount+" set symbols");
		}
		return set2Sym.get(set);
	}

	// factory for StateTreePair, which is also a symbol
	// lookahead can be null and a collision is ignored
	static public StateTreePair getStateTreePair(Symbol s, TreeItem t, int ts, int te) {
		boolean debug = false;
		boolean capDebug = false;
		boolean doFill = false;
		if (!str2Stp.containsKey(s)) {
			str2Stp.put(s, new 	 HashMap<TreeItem, HashMap<Integer, HashMap<Integer, StateTreePair>>>());
			str2Stp.get(s).put(t, new HashMap<Integer, HashMap<Integer, StateTreePair>>());
			str2Stp.get(s).get(t).put(ts, new HashMap<Integer, StateTreePair>());
			doFill = true;
		}
		
		else if (!str2Stp.get(s).containsKey(t)) {
			str2Stp.get(s).put(t, new HashMap<Integer, HashMap<Integer, StateTreePair>>());
			str2Stp.get(s).get(t).put(ts, new HashMap<Integer, StateTreePair>());
			doFill = true;
		}
		else if (!str2Stp.get(s).get(t).containsKey(ts)) {
			str2Stp.get(s).get(t).put(ts, new HashMap<Integer, StateTreePair>());
			doFill = true;
		}

		if (doFill || !str2Stp.get(s).get(t).get(ts).containsKey(te)) {
			StateTreePair sym = new StateTreePair(s,t, ts,te);
			str2Stp.get(s).get(t).get(ts).put(te, sym);
			stpSymCount++;
			if (capDebug && stpSymCount % REP_SIZE == 0)
				Debug.debug(capDebug, "Created "+stpSymCount+" stp symbols");
		}
		return str2Stp.get(s).get(t).get(ts).get(te);
//		String str = new String(s+":"+t+":"+ts+":"+te);
//		if (debug) Debug.debug(debug, "getting state tree pair for "+str);
//		HString h = new HString(str);
//		if (!str2Stp.containsKey(h)) {
//			//	     	if (!str2Sym.containsKey(str.intern())) {
//			if (debug) Debug.debug(debug, "creating new symbol from "+str);
//			StateTreePair sym = new StateTreePair(s,t,ts,te);
//			//	    	    str2Sym.put(str.intern(), sym);
//			str2Stp.put(h, sym);
//			stpSymCount++;
//			if (capDebug && stpSymCount % REP_SIZE == 0)
//				Debug.debug(capDebug, "Created "+stpSymCount+" stp symbols");
//		}
//		//	 	return (Symbol)str2Sym.get(str.intern());
//		return (StateTreePair)str2Stp.get(h);	
	}
	// experiment with ditching states after operation
	static public void resetSTP() {
		boolean debug = false;
		str2Stp = new HashMap<Symbol, HashMap<TreeItem, HashMap<Integer, HashMap<Integer, StateTreePair>>>>();
		if (debug) Debug.debug(debug, "Resetting stp cache");
		stpSymCount=0;
	}
	
	// factory for symbol vector, which is also a symbol
	static public VecSymbol getVecSymbol(Vector<Symbol>vec) {
		boolean capDebug = false;
		String str = vec.toString();
		HString h = new HString(str);
		if (!str2Sym.containsKey(h)) {
			VecSymbol sym = new VecSymbol(vec);
			str2Sym.put(h, sym);
			vecSymCount++;
			if (capDebug && vecSymCount % REP_SIZE == 0)
				Debug.debug(capDebug, "Created "+vecSymCount+" vec symbols");
		}
		return (VecSymbol)str2Sym.get(h);
	}
	
	// factory for adjoining symbol vector, which is also a symbol
	static public VecSymbol getVecSymbol(VecSymbol orig, Symbol nextsym) {
		boolean capDebug = false;
		Vector<Symbol> vec = new Vector<Symbol>(orig.getVec());
		vec.add(nextsym);
		String str = vec.toString();
		HString h = new HString(str);
		if (!str2Sym.containsKey(h)) {
			VecSymbol sym = new VecSymbol(vec);
			str2Sym.put(h, sym);
			vecSymCount++;
			if (capDebug && vecSymCount % REP_SIZE == 0)
				Debug.debug(capDebug, "Created "+vecSymCount+" vec symbols");
		}
		return (VecSymbol)str2Sym.get(h);
	}
	// factory for adjoining symbol vector, which is also a symbol
	static public VecSymbol getVecSymbol(Symbol nextsym, VecSymbol orig) {
		boolean capDebug = false;
		Vector<Symbol> vec = new Vector<Symbol>();
		vec.add(nextsym);
		vec.addAll(orig.getVec());
		String str = vec.toString();
		HString h = new HString(str);
		if (!str2Sym.containsKey(h)) {
			VecSymbol sym = new VecSymbol(vec);
			str2Sym.put(h, sym);
			vecSymCount++;
			if (capDebug && vecSymCount % REP_SIZE == 0)
				Debug.debug(capDebug, "Created "+vecSymCount+" vec symbols");
		}
		return (VecSymbol)str2Sym.get(h);
	}
	
	static public int getSymSize() { 
		//return 0;
		return str2Sym.size();
	}
	static public int getSetSymSize() { 
		//return 0;
		return set2Sym.size();
	}
	static public int getSTPSymSize() { 
		//return 0;
		return stpSymCount;
	}
	static public String getString(Symbol sym) {
		return sym.toString();
		// 	if (!sym2Str.containsKey(sym)) {
		// 	    return null;
		// 	}
		// 	return (String)sym2Str.get(sym);
	}
}
