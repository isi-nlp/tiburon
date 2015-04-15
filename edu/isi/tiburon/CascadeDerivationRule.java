package edu.isi.tiburon;

import java.io.Externalizable;
import java.io.IOException;
import java.io.Serializable;
import java.util.Vector;

// customized rtg rule for training
// consists of a symbol lhs, 4 ints representing the alignment, a TransducerRule label, and an ordered vector of rhs symbols

public class CascadeDerivationRule implements Externalizable {
	private int lhs;
	// used by Rule and TransducerRule
	private transient Vector<Derivable> label;
	
	// for restoration - must be revived by a call from a TRS
	private Vector<Integer> labelIndex;
	// source of rules (only stored for transducer rules)
	private Vector<Integer> labelSourceIndex;
	// so that the same deriv can be used with multiple semiring, this is re-set at revival time
	private transient Semiring semiring;

//	private StringBuffer alignString;
	// limit the length of the rhs and make it a simpler structure
	private static final int MAXRHS = 40;
	private int nextRHS=0;
	private int[] rhs;
	private boolean isVirtual;

	// just for debugging
//	private int[] align;
	
	// id to use for virtual label indices; higher than highest label index and globally unique
	private static Vector<Integer> virtualIndex;
	
	// externalizables -- update if members change!
	public void readExternal(java.io.ObjectInput s)
	throws ClassNotFoundException, 
	IOException 
	{
		lhs = s.readInt();
		int labelIndexSize = s.readInt();
		labelIndex = new Vector<Integer>();
		for (int i = 0; i < labelIndexSize; i++)
			labelIndex.add(s.readInt());
		int labelSourceIndexSize = s.readInt();
		labelSourceIndex = new Vector<Integer>();
		for (int i = 0; i < labelSourceIndexSize; i++)
			labelSourceIndex.add(s.readInt());
		nextRHS = s.readInt();
		rhs = new int[nextRHS];
		for (int i = 0; i < nextRHS; i++)
			rhs[i] = s.readInt();
		isVirtual = s.readBoolean();
	}
	public void writeExternal(java.io.ObjectOutput s)
	throws IOException 
	{
		s.writeInt(lhs);
		if (labelIndex == null)
			s.writeInt(0);
		else {
			s.writeInt(labelIndex.size());
			for (int li : labelIndex) {
				s.writeInt(li);
			}
		}
		if (labelSourceIndex == null)
			s.writeInt(0);
		else {
			s.writeInt(labelSourceIndex.size());
			for (int lsi : labelSourceIndex) {
				s.writeInt(lsi);
			}
		}
		s.writeInt(nextRHS);
		for (int i = 0; i < nextRHS; i++) {
			s.writeInt(rhs[i]);
		}
		s.writeBoolean(isVirtual);
	}
	
	
	static {
		virtualIndex = new Vector<Integer>();
		virtualIndex.add(2);
	}
	public static Vector<Integer> getVirtualIndex() { return virtualIndex; }
	public static void setVirtualIndex(int i) { 
		if (virtualIndex.get(0) < i) 
			virtualIndex.set(0, i);
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
		if (label != null) {
			for (Derivable r : label)
				hsh = hsh.sag(new Hash(r.getHash()));
		}
		
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
		CascadeDerivationRule r = (CascadeDerivationRule)o;
		if (lhs != r.lhs)
			return false;
		if (label == null ^ r.label == null)
			return false;
		if (label != null && (!label.equals(r.label)))
			return false;
		if (rhs.length != r.rhs.length)
			return false;
		for (int i = 0; i < rhs.length; i++)
			if (rhs[i] != r.rhs[i])
				return false;
		return true;
	}
	// for externalizable
	public CascadeDerivationRule() {
	
	}
	// to be more general, lhs and rhs children all ints - it just has to be internally consistent

	public CascadeDerivationRule(int stt, Vector<Derivable> trvec, int [] v, Semiring s) {
		lhs = stt;
		label = trvec;
		semiring = s;
		
		if (label != null) {
			labelIndex = new Vector<Integer>();
			labelSourceIndex = new Vector<Integer>();
			for (Derivable rule : trvec) {
				int curridx = rule.getIndex();
				labelIndex.add(curridx);
				setVirtualIndex(curridx+1);
				if (rule instanceof TransducerRule) {
					labelSourceIndex.add(((TransducerRule)rule).parent.getID());
				}
			}
		}
		isVirtual = (trvec == null);
		if (v.length > MAXRHS)
			throw new ArrayIndexOutOfBoundsException(v.length+" exceeds hard-set limit of "+MAXRHS);
		rhs = new int[MAXRHS];
		for (int i = 0; i < v.length; i++)
			rhs[i] = v[i];
		nextRHS = v.length;
	}
	public CascadeDerivationRule(int stt, Vector<Derivable> trvec, int [] v, StringBuffer as, Semiring s) {
		this(stt, trvec, v, s);
		//alignString = as;
	}

	
	
	
	public CascadeDerivationRule(int stt, Vector<Derivable> trvec, int [] v, Semiring s, int[] a) {
		this(stt, trvec, v, s);
		//align = a;
	}

	public String toString() {
		StringBuffer l = null;
		if (label != null)
			l = new StringBuffer(lhs+" -> "+label);
		else
			l = new StringBuffer(lhs+" -> (virtual rule)");
		for (int i = 0; i < nextRHS; i++)
			l.append(" | "+rhs[i]);
//		if (align != null) {
//			l.append("[ ");
//			for (int j=0; j < 4; j++) 
//				l.append(align[j]+" ");
//			l.append("]");
//		}
		return l.toString();
	}

	public int getLHS() { return lhs; }
	public Vector<Derivable> getLabel() { return label; }
	
	public Vector<Integer> getLabelIndex() { 
		if (isVirtual)
			return virtualIndex;
		return labelIndex; 
	}
	public int[] getRHS() { return rhs; }
	public int getNumChildren() { return nextRHS;}
	public double getWeight() { 
		if (isVirtual)
			return semiring.ONE();
		double currval = semiring.ONE();
		for (Derivable r : label) {
			currval = semiring.times(currval, r.getWeight());
		}
		return currval; 
	}
	public StringBuffer getAlignString() { 
		return null;
		//	return alignString; 
	}
	// revive - gives a derivation rule an opportunity to restore its label if it's been written
	public void revive(Vector<TransducerRuleSet> trs) {
		if (label == null && labelIndex != null) {
			label = new Vector<Derivable>();
			for (int i = 0; i < labelIndex.size(); i++) {
				int ruleid = labelIndex.get(i);
				int transid = labelSourceIndex.get(i);
				label.add((TransducerRule)(trs.get(transid).getRuleByIndex(ruleid)));
			}
		}
		if (label == null && !isVirtual()) {
			System.err.println("Revival error: virtual state is "+isVirtual()+" unable to revive indices "+labelIndex+", rule is "+toString());
		}
	}
	// Rule set version -- only ever a single rule
	public void revive(RuleSet rs) {
		if (label == null && labelIndex != null) {
			label = new Vector<Derivable>();
			label.add((Rule)rs.getRuleByIndex(labelIndex.get(0)));
		}
		if (label == null && !isVirtual()) {
			System.err.println("Revival error: virtual state is "+isVirtual()+" unable to revive index "+labelIndex+", rule is "+toString());
		}
	}

	public boolean isVirtual() { return isVirtual; }
	// rule index is used for converting to graehl format. pipe through to transducer rule
	public Vector<Integer> getRuleIndex() {
//		if (altLabel != null)
//			return altLabel.getIndex();
		return labelIndex;
	}

	public void setSemiring(Semiring s) {
		semiring = s;
	}
}
