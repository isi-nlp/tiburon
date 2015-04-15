package edu.isi.tiburon;

import java.util.Vector;

// common interface for ngram lm tt and regular (OTFTreeTransducer) tt when being strictly binary

public interface BinaryTreeTransducer {
	abstract public Iterable<TreeTransducerRule> getForwardRules(Vector<Symbol> vs, Symbol label) throws UnusualConditionException;
	abstract public Iterable<TreeTransducerRule> getRelPosLexRules(Symbol s, int i);
	abstract public boolean hasLeftStateRules(Symbol state, Symbol label);
	abstract public boolean hasRightStateRules(Symbol state, Symbol label);
	abstract public boolean isStartState(Symbol s);

}
