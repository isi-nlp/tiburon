package edu.isi.tiburon;

import java.io.Serializable;

// customized rtg rule for training
// consists of a symbol lhs, 4 ints representing the alignment, a TransducerRule label, and an ordered vector of rhs symbols

public class DerivationRule implements Serializable {
	private int lhs;
	private transient TransducerRule label;
	// altLabel used when drs represents an rtg/cfg. this is theoretically temporary...
	private transient Rule altLabel;
	// for restoration - must be revived by a call from a TRS
	private int labelIndex=-1;
	// so that the same deriv can be used with multiple semiring, this is re-set at revival time
	private transient Semiring semiring;

	private StringBuffer alignString;
	// limit the length of the rhs and make it a simpler structure
	private static final int MAXRHS = 40;
	private int[] rhs;
	private int nextRHS=0;
	private boolean isVirtual;

	// just for debugging
	private int[] align;
	
	// id to use for virtual label indices; higher than highest label index and globally unique
	private static int virtualIndex=2;
	public static int getVirtualIndex() { return virtualIndex; }
	public static void setVirtualIndex(int i) { 
		if (virtualIndex < i) 
			virtualIndex = i;
	}

	// equality stuff
	private Hash hsh=null;
	public int hashCode() { 
		if (hsh == null) {
			setHashCode();
		}
		return hsh.bag(Integer.MAX_VALUE);
	}

	private void setHashCode() {
		hsh = new Hash(lhs);
		if (label != null)
			hsh = hsh.sag(new Hash(label.getHash()));
		else if (altLabel != null)
			hsh = hsh.sag(new Hash(altLabel.getHash()));
		for (int i = 0; i < rhs.length; i++)
			hsh = hsh.sag(new Hash(rhs[i]));
	}
	public Hash getHash() {
		if (hsh == null) {
			setHashCode();
		}
		return hsh;
	}
	// equals if lhs and rhs are equal (rhs by-item equality) and if
	// labels are both null or both equal
	public boolean equals(Object o) {
		if (!o.getClass().equals(this.getClass()))
			return false;
		DerivationRule r = (DerivationRule)o;
		if (lhs != r.lhs)
			return false;
		if (label == null ^ r.label == null)
			return false;
		if (label != null && (!label.equals(r.label)))
			return false;
		if (altLabel == null ^ r.altLabel == null)
			return false;
		if (altLabel != null && (!altLabel.equals(r.altLabel)))
			return false;
		if (rhs.length != r.rhs.length)
			return false;
		for (int i = 0; i < rhs.length; i++)
			if (rhs[i] != r.rhs[i])
				return false;
		return true;
	}
	// to be more general, lhs and rhs children all ints - it just has to be internally consistent

	public DerivationRule(int stt, TransducerRule tr, int [] v, Semiring s) {
		lhs = stt;
		label = tr;
		altLabel = null;
		semiring = s;
		if (label == null)
			labelIndex = -1;
		else {
			labelIndex = tr.getIndex();
			if (virtualIndex <= labelIndex) {
				virtualIndex = labelIndex+1;
			}
		}
		isVirtual = (tr == null);
		if (v.length > MAXRHS)
			throw new ArrayIndexOutOfBoundsException(v.length+" exceeds hard-set limit of "+MAXRHS);
		rhs = new int[MAXRHS];
		for (int i = 0; i < v.length; i++)
			rhs[i] = v[i];
		nextRHS = v.length;
	}
	public DerivationRule(int stt, TransducerRule tr, int [] v, StringBuffer as, Semiring s) {
		this(stt, tr, v, s);
		alignString = as;
	}

	// shadow versions for RTg
	public DerivationRule(int stt, Rule rule, int [] v, Semiring s) {
		lhs = stt;
		label = null;
		altLabel = rule;
		semiring = s;
		if (altLabel == null)
			labelIndex = -1;
		else {
			labelIndex = rule.getIndex();
			if (virtualIndex <= labelIndex) {
				virtualIndex = labelIndex+1;
			}
		}
		isVirtual = (rule == null);
		if (v.length > MAXRHS)
			throw new ArrayIndexOutOfBoundsException(v.length+" exceeds hard-set max of "+MAXRHS);
		rhs = new int[MAXRHS];
		for (int i = 0; i < v.length; i++)
			rhs[i] = v[i];
		nextRHS = v.length;
	}
	
	// debugging versions
	public DerivationRule(int stt, Rule rule, int [] v, StringBuffer as, Semiring s) {
		this(stt, rule, v, s);
		alignString = as;
	}
	
	public DerivationRule(int stt, Rule rule, int [] v, Semiring s, int[] a) {
		this(stt, rule, v, s);
		align = a;
	}

	public String toString() {
		StringBuffer l = null;
		if (label != null)
			l = new StringBuffer(lhs+" -> "+label);
		else if (altLabel != null)
			l = new StringBuffer(lhs+" -> "+altLabel);
		else
			l = new StringBuffer(lhs+" -> (virtual rule)");
		for (int i = 0; i < nextRHS; i++)
			l.append(" | "+rhs[i]);
		if (align != null) {
			l.append("[ ");
			for (int j=0; j < 4; j++) 
				l.append(align[j]+" ");
			l.append("]");
		}
		return l.toString();
	}

	public int getLHS() { return lhs; }
	public TransducerRule getLabel() { return label; }
	public Rule getAltLabel() { return altLabel; }
	public int getLabelIndex() { 
		if (isVirtual)
			return virtualIndex;
		return labelIndex; 
	}
	public int[] getRHS() { return rhs; }
	public int getNumChildren() { return nextRHS;}
	public double getWeight() { 
		if (altLabel != null) {
			if (isVirtual)
				return semiring.ONE();
			return altLabel.getWeight();
		}
		if (isVirtual)
			return semiring.ONE();
		return label.getWeight(); 
	}
	public StringBuffer getAlignString() { return alignString; }
	// revive - gives a derivation rule an opportunity to restore its label if it's been written
	public void revive(TransducerRuleSet trs) {
		if (label == null && labelIndex >= 0)
			label = (TransducerRule) trs.getRuleByIndex(labelIndex);
		if (label == null && !isVirtual()) {
			System.err.println("Revival error: virtual state is "+isVirtual()+" unable to revive index "+labelIndex+", rule is "+toString());
		}
	}
	// shadow version
	public void revive(RuleSet rs) {
		if (altLabel == null && labelIndex >= 0)
			altLabel = (Rule) rs.getRuleByIndex(labelIndex);
		if (altLabel == null && !isVirtual()) {
			System.err.println("Revival error: virtual state is "+isVirtual()+" unable to revive index "+labelIndex+", rule is "+toString());
		}
	}

	public boolean isVirtual() { return isVirtual; }
	// rule index is used for converting to graehl format. pipe through to transducer rule
	public int getRuleIndex() {
		if (altLabel != null)
			return altLabel.getIndex();
		return label.getIndex();
	}

	public void setSemiring(Semiring s) {
		semiring = s;
	}
}
