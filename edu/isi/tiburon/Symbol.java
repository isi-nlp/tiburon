package edu.isi.tiburon;
// dummy class valued only for its inherent hash codes
// all accessing to info (i.e. string value) through 
public abstract class Symbol {
    abstract public String toString();
    private static Symbol eps;
    private static Symbol star;
    static {
	eps = SymbolFactory.getSymbol("*e*");
	star = SymbolFactory.getSymbol("*");
    }
    public static Symbol getEpsilon() {
	return eps;
    }
    public static Symbol getStar() {
	return star;
    }
    //    public String toString() { return SymbolFactory.getString(this); }
    abstract public int hashCode();
    abstract public Hash getHash();
}
