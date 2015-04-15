package edu.isi.tiburon;

import java.util.Collection;

// any of a number of agenda strategies for integrated parsing
public interface IPAgenda {
	public void addState(IPState state);
	public IPState getState();
	public boolean isEmpty();
	public boolean addAll(Collection<? extends IPState> list);
}
