package edu.isi.tiburon;

import java.util.HashMap;
import java.util.Vector;
// representation of a string for parsing in a way that is useful for OTF algorithms
public class OTFString {
	private HashMap<Symbol, Vector<Index>> hash;
	private Index span;
	public Iterable<Symbol> getLabels() {
		return hash.keySet();
	}
	public Iterable<Index> getMatches(Symbol s) {
		return hash.get(s);
	}
	public Index getSpan() {
		return span;
	}
	public OTFString(StringItem si) {
		hash = new HashMap<Symbol, Vector<Index>>();
		Symbol[] syms = si.getLeaves();
		// epsilon spans -- kind of silly
		hash.put(Symbol.getEpsilon(), new Vector<Index>());
		hash.get(Symbol.getEpsilon()).add(Index.get(0, 0));
		for (int i = 0; i < syms.length; i++) {
			Symbol sym = syms[i];
			if (!hash.containsKey(sym))
				hash.put(sym, new Vector<Index>());
			hash.get(sym).add(Index.get(i, i+1));
			hash.get(Symbol.getEpsilon()).add(Index.get(i+1, i+1));
		}
		span = Index.get(0, syms.length);
	}
}
