package edu.isi.tiburon;

import java.util.HashMap;

import edu.isi.tiburon.FilteringPairSymbol.FILTER;


// try doing this without leaning on symbol factory
public class PairSymbol extends AliasedSymbol {
	private static HashMap<Symbol, HashMap<Symbol, PairSymbol>> memo;
	private static int count=0;
	private Symbol a;
	private Symbol b;
	private PairSymbol(Symbol as, Symbol bs) {
		super();
		a = as;
		b = bs;
	}
	
	public static PairSymbol get(Symbol as, Symbol bs) {
		if (memo == null)
			memo = new HashMap<Symbol, HashMap<Symbol, PairSymbol>>();
		if (!memo.containsKey(as))
			memo.put(as, new HashMap<Symbol, PairSymbol>());
		if (!memo.get(as).containsKey(bs)) {
			memo.get(as).put(bs, new PairSymbol(as, bs));
			count++;
		}
		return memo.get(as).get(bs);
	}
	public Symbol getLeft() { return a; }
	public Symbol getRight() { return b; }
//	public String toString() { return toInternalString(); }
	public String toInternalString() { return a+":"+b; }
	static public void clear() {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Clearing "+count);
		count = 0;
		memo = new HashMap<Symbol, HashMap<Symbol, PairSymbol>>();
	}
}
