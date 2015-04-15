package edu.isi.tiburon;

import java.util.HashMap;

// represents a state and a pair of string indices

public class IndexSymbol extends AliasedSymbol {
	private static PMap<Symbol, PMap<Index, IndexSymbol>> memo;
	private static int count;
	private Symbol a;
	private Index ind;
	private IndexSymbol(Symbol as, Index index) {
		super();
		a = as;
		ind = index;
	}
	public Symbol getSym() { return a; }
	public Index getIndex() { return ind; }
	public String toString() {
		return a+"["+ind.left()+","+ind.right()+"]";
	}
	public static IndexSymbol get(Symbol as, Index index) {
		if (!memo.goc(as).containsKey(index)) {
			memo.get(as).put(index, new IndexSymbol(as, index));
			count++;
		}
		return memo.get(as).get(index);
	}
	public static void clear() {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Clearing "+count);
		count = 0;
		memo = new	PMap<Symbol, PMap<Index, IndexSymbol>> ();
	}
	static {
		count = 0;
		memo = new	PMap<Symbol, PMap<Index, IndexSymbol>> ();
	}
}
