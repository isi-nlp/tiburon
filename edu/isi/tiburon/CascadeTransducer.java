package edu.isi.tiburon;

public interface CascadeTransducer {
	public Symbol getStartState();
	public Iterable getBackwardRules(Symbol state, Symbol label, int rank);
	public Semiring getSemiring();
}
