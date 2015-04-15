package edu.isi.tiburon;
import java.io.ObjectStreamException;
import java.io.Serializable;
// dummy class valued only for its inherent hash codes
// all accessing to info (i.e. string value) through 
public class OldSymbol  extends Symbol implements Serializable {
    public OldSymbol(String s){intern = s.intern(); hsh = new Hash(intern);}
    private String intern;
    private Hash hsh;
    public String toString() { return intern; }
    //    public String toString() { return SymbolFactory.getString(this); }
    public int hashCode(){
	return hsh.bag(Integer.MAX_VALUE);
    }
    public Hash getHash() { return hsh; }
    // common empty symbol defined here

    // upon restoration, use symbolfactory's copy
    public Object readResolve() throws ObjectStreamException {
	return SymbolFactory.getSymbol(intern);
    }
    // prevent any slow comparisons
    public boolean equals(Object o){
		return this == o;
//  	if (!o.getClass().equals(this.getClass()))
//  	    return false;
// 	//	Debug.debug(true, "Hashcode comparison: "+hashCode()+" vs "+o.hashCode());
// 	//	Debug.debug(true, "Symbol equality; checking intern of "+intern+" vs "+((Symbol)o).intern+":"+(intern.equals(((Symbol)o).intern)));
//  	return intern.equals(((Symbol)o).intern);
// // 	Symbol s = (Symbol)o;
// // 	//if (s.hashCode() != this.hashCode())
// // 	//    return false;
// // 	return s.toString().equals(this.toString());
    }
}
