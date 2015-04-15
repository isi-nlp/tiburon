package edu.isi.tiburon;

// represents left and right word boundaries -- used by OTFLMTreeTransducerRuleSet
// TODO: extend to arbitrary ngram boundaries

public class WordBoundariesSymbol extends AliasedSymbol {
	private static PMap<Symbol, PMap<Symbol, WordBoundariesSymbol>> memo;
	private Symbol left;
	private Symbol right;
	private WordBoundariesSymbol(Symbol ls, Symbol rs) {
		left = ls;
		right = rs;
	}
	public Symbol getLeft() { return left; }
	public Symbol getRight() { return right; }
	public String toString() {
		return left+"_"+right;
	}
	public static WordBoundariesSymbol get(Symbol ls, Symbol rs) {
		if (!memo.goc(ls).containsKey(rs))
			memo.get(ls).put(rs, new WordBoundariesSymbol(ls, rs));
		return memo.get(ls).get(rs);
	}
	static {
		memo = new	PMap<Symbol, PMap<Symbol, WordBoundariesSymbol>> ();
	}
}
