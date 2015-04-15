package edu.isi.tiburon;

import java.util.Comparator;
import java.util.Hashtable;
import java.util.Vector;

public class TreeRuleTuple implements StateSeqMember {

	TransducerRightTree tree;
	TreeTransducerRule rule;
	TreeRuleTuple(TransducerRightTree t, TreeTransducerRule r) {tree=t; rule=r;}
	private Hash hsh;
	public String toString() {return tree+":"+rule;}
	public boolean equals(Object o) {
		if (o instanceof TreeRuleTuple) {
			TreeRuleTuple p = (TreeRuleTuple)o;
			return (tree.equals(p.tree) && rule.equals(p.rule));
		}
		return false;
	}
	public Hash getHash() {
		if (hsh == null) {
			setHashCode();
		}
		return hsh;
	}
	private void setHashCode() {
		hsh = new Hash();
		hsh = hsh.sag(tree.getHash());
		hsh = hsh.sag(rule.getHash());
	}


}

