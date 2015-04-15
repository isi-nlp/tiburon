package edu.isi.tiburon;
// overrides hash functions of string. otherwise, the same
public class HString  {
    private String intern;
    public HString(String s) {
	intern = s.intern();
    }
    public int hashCode() {
	Hash h = new Hash(intern);
	return h.bag(Integer.MAX_VALUE);
    }
    public boolean equals(Object o) {
	if (!o.getClass().equals(this.getClass()))
 	    return false;
	HString s = (HString)o;
	return s.intern.equals(intern);
    }
}
