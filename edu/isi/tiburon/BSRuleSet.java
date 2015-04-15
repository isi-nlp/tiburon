package edu.isi.tiburon;

// general interface for a rule set that explores things with backward-star iterators
// also knows top state
// cf Grammar, specifically GTOTFGrammar
public interface BSRuleSet {
	// given a state, get an iterator over rules in that state
	public PIterator<Rule> getBSIter(Symbol s) throws UnusualConditionException;
	// what is the start state
	public Symbol getStartState();
	// check if a symbol is a state
	public boolean isState(Symbol s);
	public int getRuleCount();
	public void reportRules();

}
