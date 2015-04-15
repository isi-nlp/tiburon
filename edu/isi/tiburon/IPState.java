package edu.isi.tiburon;


// re-implementation of states for integrated parsing

// integrated parsing state. has start and end index. can return base level state
public interface IPState {
	public int getStart();
	public int getEnd();
	public int getBaseState();
	public boolean isAdded();
	public void setAdded();
}
