package edu.isi.tiburon;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Hashtable;

// different symbols for creation and display. used when state creation
// results in less-than-ideal symbols
public class AliasedSymbol extends Symbol implements Serializable {
	static private int nextID = 0;
	private String intern;
    private Hash hsh;
	private String display;
    public AliasedSymbol(String s){
    	intern = s.intern();
   		display = "q"+(nextID++);
    	hsh = new Hash(intern);
    }
    // when the symbol content doesn't matter
    public AliasedSymbol(){
    	display = "q"+(nextID++);
    	intern = display;
    	hsh = new Hash(intern);
    }
    public int hashCode(){
    	return hsh.bag(Integer.MAX_VALUE);
    }
    public Hash getHash() { return hsh; }
	
    // upon restoration, use symbolfactory's copy
    public Object readResolve() throws ObjectStreamException {
	return SymbolFactory.getSymbol(intern);
    }
    
    public String toString() { return display; }
    public String toInternalString() { return intern; }
    // prevent any slow comparisons
    public boolean equals(Object o){
		return this == o;
    }
 
    // advance nextID if need be
    static protected int update(int id) {
    	if (nextID <= id)
    		nextID = id+1;
    	return nextID;
    }

}
