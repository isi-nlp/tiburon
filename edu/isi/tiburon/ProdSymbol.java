package edu.isi.tiburon;

import java.util.HashMap;
import java.util.Vector;

// state that is the result of a chain of states, then
// an artificial state formed by combining a grammar rule (or state) and a transducer rule
// and separating at a particular point
// three integers are used to denote this but are combined in a symbol (for simplicity)
// calling class is responsible for avoiding reuse (typically with hashcodes)
// inner representation forgets gram

public class ProdSymbol extends AliasedSymbol {
	private static PMap<Symbol, PMap<Integer, ProdSymbol>> memo;
	private static PMap<Symbol, ProdSymbol> coremap;
	static {
		memo = new PMap<Symbol, PMap<Integer, ProdSymbol>>();
		coremap = new PMap<Symbol, ProdSymbol> ();
	}
	private Symbol sym;
	private Symbol rgt;
	private ProdSymbol core;
	private ProdSymbol(Symbol s, Symbol r, ProdSymbol c) {
		super();
		sym = s;
		rgt = r;
		if (c == null)
			core = this;
		else
			core = c;
	}
	public static ProdSymbol get(Symbol sym, int gram, int rule, int trt) {
		Symbol corecombo = SymbolFactory.getSymbol(rule+"+"+trt);
		if (!coremap.containsKey(corecombo))
			coremap.put(corecombo, new ProdSymbol(sym, corecombo, null));
		ProdSymbol coresym = coremap.get(corecombo);
		// this is just for display differentiation now
		Symbol combo = SymbolFactory.getSymbol(gram+"+"+rule+"+"+trt);
		if (!memo.goc(coresym).containsKey(gram))
			memo.get(coresym).put(gram, new ProdSymbol(sym, combo, coresym));
		return memo.get(coresym).get(gram);
	}
	public Symbol getSym() { return sym; }
	public ProdSymbol getCore() { return core; }
	public Symbol getRule() { return rgt; }
	// unroll. ignore filter
	static public String unrollChain(ProdSymbol s) {
		Vector<Symbol> vec = internalUnrollChain(s);
		StringBuffer buf = new StringBuffer();
		for (Symbol sym : vec)
			buf.append(" "+sym);
		buf.deleteCharAt(0);
		return buf.toString();
	}
	
	static public Vector<Symbol> internalUnrollChain(ProdSymbol s) {
		Vector<Symbol> vec = new Vector<Symbol>();
		Symbol l = s.getSym();
		Symbol c = s.getCore().getRule();
		;
		if (l instanceof ProdSymbol)
			vec.addAll(internalUnrollChain((ProdSymbol)l));
		else if (l instanceof FilteringPairSymbol)
			vec.addAll(FilteringPairSymbol.internalUnrollChain((FilteringPairSymbol)l));
		else
			vec.add(l);
		vec.add(c);
		return vec;		
	}
}
