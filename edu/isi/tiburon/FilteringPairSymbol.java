package edu.isi.tiburon;

import java.util.HashMap;
import java.util.Vector;

public class FilteringPairSymbol extends AliasedSymbol {
	public enum FILTER {
		LEFTEPS,
		RIGHTEPS,
		NOEPS,
		REAL
	}
	private static HashMap<PairSymbol, HashMap<FILTER, FilteringPairSymbol>> memo;
	private static int count=0;
	private PairSymbol s;
	private FILTER f;
	private FilteringPairSymbol(PairSymbol as, FILTER fil) {
		super();
		s = as;
		f = fil;
	}
	public static FilteringPairSymbol get(Symbol as, Symbol bs, FILTER fil) {
		PairSymbol sym = PairSymbol.get(as, bs);
		if (memo == null)
			memo = new HashMap<PairSymbol, HashMap<FILTER, FilteringPairSymbol>>();
		if (!memo.containsKey(sym))
			memo.put(sym, new HashMap<FILTER, FilteringPairSymbol>());
		if (!memo.get(sym).containsKey(fil)) {
			memo.get(sym).put(fil, new FilteringPairSymbol(sym, fil));
			count++;
		}
		return memo.get(sym).get(fil);
	}
	public FILTER getFilter() { return f; }
	public Symbol getLeft() { return s.getLeft(); }
	public Symbol getRight() { return s.getRight(); }
	public String toString() { return s.getLeft()+":"+s.getRight(); }
	public String toInternalString() { return s.toInternalString()+":"+f; }
	// unroll. ignore filter
	static public String unrollChain(FilteringPairSymbol s) {
		Vector<Symbol> vec = internalUnrollChain(s);
		StringBuffer buf = new StringBuffer();
		for (Symbol sym : vec)
			buf.append(" "+sym);
		buf.deleteCharAt(0);
		return buf.toString();
	}
	static public Vector<Symbol> internalUnrollChain(FilteringPairSymbol s) {
		Vector<Symbol> vec = new Vector<Symbol>();
		Symbol l = s.getLeft();
		Symbol r = s.getRight();
		if (l instanceof FilteringPairSymbol)
			vec.addAll(internalUnrollChain((FilteringPairSymbol)l));
		else if (l instanceof ProdSymbol)
			vec.addAll(ProdSymbol.internalUnrollChain((ProdSymbol)l));
		else
			vec.add(l);
		if (r instanceof FilteringPairSymbol)
			vec.addAll(internalUnrollChain((FilteringPairSymbol)r));
		else if (r instanceof ProdSymbol)
			vec.addAll(ProdSymbol.internalUnrollChain((ProdSymbol)r));
		else
			vec.add(r);
		return vec;		
	}
	static public void clear() {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Clearing "+count);
		count = 0;
		memo = new HashMap<PairSymbol, HashMap<FILTER, FilteringPairSymbol>>();
	}
}
