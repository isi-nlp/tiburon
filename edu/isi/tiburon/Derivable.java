package edu.isi.tiburon;

// things that can be in a derivationrule
public interface Derivable {
	public Hash getHash();
	public int getIndex();
	public double getWeight();
	public Symbol getLHSSym();
    public Symbol getLHSCondSym();
}
