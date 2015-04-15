package edu.isi.tiburon;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

// structure suggested by Jon: a node object that has a label and an index and points to the adjacent symbol and the closest of each 
// unique symbol. Note that this is a fairly memory intensive object so it should be created once (as a TrainingStringInside) and 
// TrainingString instances, which are really navigators, are used to traverse it. Navigators also have limit info

public class TrainingString implements Serializable, Trainable {

	static class TrainingStringInside implements Serializable{
		private Symbol label;
		private int index;
		private TrainingStringInside adj;
		private Hashtable next;
		private Hash hsh;

		TrainingStringInside(Symbol l, int i, TrainingStringInside a) {
			label = l;
			index = i;
			adj = a;
			if (a != null) {
				next = new Hashtable(a.next);
				next.put(a.label, a);
			}
			else
				next = new Hashtable();
			hsh = new Hash(label.getHash());
			hsh = hsh.sag(adj == null ? new Hash() : adj.getHash());
		}

		public int hashCode() { return hsh.bag(Integer.MAX_VALUE); }
		public Hash getHash() { return hsh; }
		// the typical way a training string is created - from a vector of symbols
		static TrainingStringInside createTrainingString(Vector v) {
			TrainingStringInside last = null;
			for (int i = v.size()-1; i >= 0; i--) {
				TrainingStringInside ts = new TrainingStringInside((Symbol)v.get(i), i, last);
				last = ts;
			}
			return last;
		}
		public String toString() {
			StringBuffer s = new StringBuffer(label.toString());
			if (next != null)
				s.append(" "+next.toString());
			return s.toString();
		}
		// from start to just before end
		public String toString(int start, int end) throws UnusualConditionException {
			if (index < start || end < start)
				throw new UnusualConditionException("TrainingStringInside: at index "+index+" start and end of "+start+", "+end+" are improper");
			if (index > start)
				return adj.toString(start+1, end);
			StringBuffer s = new StringBuffer(label.toString());
			if (end > (index+1))
				s.append(" "+adj.toString(start+1, end));
			return s.toString();
		}
		// for debugging - show the next pointers
		void printDataStructure() {
			Debug.debug(true, "From "+label.toString()+"("+index+"):");
			if (adj != null) {
				Debug.debug(true, "\tNext symbol is "+adj.label.toString());
				Iterator it = next.keySet().iterator();
				while (it.hasNext()) {
					Symbol s = (Symbol)it.next();
					//		    Debug.debug(true, "\tNext "+s.toString()+" begins "+((TrainingStringInside)next.get(s)).toString());
					Debug.debug(true, "\tNext "+s.toString()+" is at index "+((TrainingStringInside)next.get(s)).index);
				}
				adj.printDataStructure();
			}
		}

	}
	private TrainingStringInside inside;
	private int start;
	private int end;
	private boolean isEpsilon = false;
	private Hash hsh=null;

	// for CFG training
	public double weight;

	/** for the interface */
	public double getWeight() { return weight; }

	// hash code is inside's code with modifications for start and end
	public int hashCode() {
		if (hsh == null)
			setHashCode();
		return  hsh.bag(Integer.MAX_VALUE);
	}
	public Hash getHash() {
		if (hsh == null)
			setHashCode();
		return hsh;
	}
	private void setHashCode() {
		hsh = inside == null ? new Hash() : new Hash(inside.getHash());
		hsh = hsh.sag(new Hash(start));
		hsh = hsh.sag(new Hash(end));
	}
	public boolean equals(Object o) {
		if (!o.getClass().equals(this.getClass()))
			return false;
		TrainingString s = (TrainingString)o;
		if (start != s.start ||
				end != s.end ||
				(inside == null ^ s.inside == null)) {
			return false;
		}
		if (inside == null && s.inside == null)
			return true;
		return inside.equals(s.inside);
	}


	// special epsilon training string. represented in text by *e*. In structure, this is a string with an empty inside and 0
	// indices.
	private static TrainingString epsilon = null;
	static {
		epsilon = new TrainingString(null, 0, 0);
		epsilon.isEpsilon = true;
	}

	static public TrainingString getEpsilon() {
		return epsilon;
	}

	public boolean isEpsilon() { return isEpsilon; }
	private TrainingString(TrainingStringInside i, int s, int e) {
		inside = i;
		start = s;
		end = e;
	}

	public TrainingString(Vector v) {
		inside = TrainingStringInside.createTrainingString(v);
		start = 0;
		end = v.size();
	}
	// EOL note: must also trap for CR (#13)
	public TrainingString(StringBuffer text) throws DataFormatException {
		StringReader sr = null;
		Vector v = new Vector();
		try {
			sr = new StringReader(text.toString());
			StreamTokenizer st = new StreamTokenizer(sr);
			st.resetSyntax();
			st.wordChars(33, '\u00FF');
			st.eolIsSignificant(true);
			st.whitespaceChars(9, 9);
			st.whitespaceChars(32, 32);
			st.commentChar('%');
			st.quoteChar('"');
			while (st.nextToken() != StreamTokenizer.TT_EOF && 
					st.ttype != StreamTokenizer.TT_EOL && st.ttype != 13) {
				switch (st.ttype) {
				case StreamTokenizer.TT_WORD:
					Symbol s = SymbolFactory.getSymbol(st.sval);
					v.add(s);
					break;
				case '"':
					s = SymbolFactory.getSymbol('"'+st.sval+'"');
					v.add(s);
					break;
				case StreamTokenizer.TT_NUMBER:
					s = SymbolFactory.getSymbol(""+st.nval);
					v.add(s);
					break;
				default: 
					throw new DataFormatException("TrainingString: Expected word for label but got type "+st.ttype);
				}
			}
		}
		catch(IOException e2) {
			System.err.println("IO Exception!");
			System.exit(0);
		}
		finally {
			sr.close();
		}
		inside = TrainingStringInside.createTrainingString(v);
		start = 0;
		end = v.size();
	}

	// read-from-file constructors for a single instance (mostly used for debugging/unit testing
	//     public TrainingString(String file) throws DataFormatException, FileNotFoundException, IOException {
	// 	this(file, "utf-8");
	//     }
	//     public TrainingString(String file, String encoding) throws DataFormatException, FileNotFoundException, IOException {
	// 	this(new File(file), encoding);
	//     }

	//     public TrainingString(File file, String encoding) throws DataFormatException, FileNotFoundException, IOException {
	// 	this (new StreamTokenizer(new InputStreamReader(new FileInputStream(file), encoding)));
	//     }

	public TrainingString(BufferedReader br) throws DataFormatException, IOException {
		this (new StreamTokenizer(br));
	}

	// when only expecting one. use the StringBuffer constructor and explode it
	// for some reason, slurping the whole string doesn't work in euc-jp (and possibly others) so we'll
	// copy code here

	// EOL note: must also trap for CR (#13)
	public TrainingString(StreamTokenizer st) throws DataFormatException, IOException {
		boolean debug = false;
		st.resetSyntax();
		st.wordChars(33, '\u00ff');
		st.eolIsSignificant(false);	
		st.whitespaceChars(9, 9);
		st.whitespaceChars(32, 32);
		st.commentChar('%');
		st.quoteChar('"');
		Vector v = new Vector();
		while (st.nextToken() != StreamTokenizer.TT_EOF && 
				st.ttype != StreamTokenizer.TT_EOL && st.ttype != 13) {
			switch (st.ttype) {
			case StreamTokenizer.TT_WORD:
				if (debug) Debug.debug(debug, "Got word "+st.sval);
				Symbol s = SymbolFactory.getSymbol(st.sval);
				v.add(s);
				break;
			case '"':
				if (debug) Debug.debug(debug, "Got quoted word "+st.sval);
				s = SymbolFactory.getSymbol('"'+st.sval+'"');
				v.add(s);
				break;
			case StreamTokenizer.TT_NUMBER:
				if (debug) Debug.debug(debug, "Got number "+st.nval);
				s = SymbolFactory.getSymbol(""+st.nval);
				v.add(s);
				break;
			default: 
				throw new DataFormatException("TrainingString: Expected word for label but got type "+st.ttype);
			}
		}
		// if we didn't read anything make it epsilon
		if (v.size() == 0) {
			inside = null;
			start = 0;
			end = 0;
			isEpsilon = true;
		}
		else {
			inside = TrainingStringInside.createTrainingString(v);
			start = 0;
			end = v.size();
			isEpsilon = false;
		}
	}



	public String toString() {
		if (isEpsilon)
			return "*e*";
		try {
			return inside.toString(start, end);
		}
		catch (Exception e) {
			StackTraceElement elements[] = e.getStackTrace();
			int n = elements.length;
			for (int i = 0; i < n; i++) {       
				System.err.println(e.toString()+": "+elements[i].getFileName() + ":" 
						+ elements[i].getLineNumber() 
						+ ">> " 
						+ elements[i].getMethodName() + "()");
			}
			System.exit(-1);
		}
		return null;
	}

	// accessors
	public Symbol getLabel() { 
		if (isEpsilon) return null;
		return inside.label; }
	public int getStartIndex() {
		return start;
	}
	public int getEndIndex() {
		return end;
	}
	public int getSize() {
		return end - start;
	}
	// adjacent node, limited by end
	public TrainingString next() { 
		if (isEpsilon)
			return null;
		int nextStart = inside.index+1;
		if (nextStart >= end)
			return null;
		if (inside.adj == null)
			return null;
		return new TrainingString(inside.adj, nextStart, end);
	}
	// next node with a particular symbol. limited by end
	public TrainingString next(Symbol s) {
		if (isEpsilon)
			return null;
		TrainingStringInside retVal = (TrainingStringInside)inside.next.get(s);
		if (retVal == null)
			return null;
		if (retVal.index >= end)
			return null;
		return new TrainingString(retVal, retVal.index, end);
	}

	// sub-training-string. Limited by bounds
	public TrainingString getSubString(int s) {
		if (isEpsilon)
			return epsilon;
		return getSubString(s, end);
	}
	public TrainingString getSubString(int s, int e) {
		if (isEpsilon)
			return null;
		try {
			if (s < start || e > end)
				return null;
			if (s > e)
				throw new Exception("TrainingString:getSubString: attempted to return a substring with improper interval "+s+", "+e);
			if (s == e)
				return getEpsilon();
			TrainingStringInside retVal = inside;
			while (retVal != null && retVal.index < s)
				retVal = retVal.adj;
			if (retVal == null)
				return null;
			return new TrainingString(retVal, s, e);
		}
		catch (Exception ex) {
			System.err.println(ex.getMessage());
		}
		Debug.debug(true, "TrainingString:getSubString: shouldn't be at this point!");
		return null;
	}

	// for debugging - show the next pointers
	public void printDataStructure() {
		if (isEpsilon) {
			Debug.debug(true, "(epsilon)");
		}
		inside.printDataStructure();
	}

	// test code
	static public void main(String argv[]) {
		try {
			InputStreamReader isr = new InputStreamReader(new FileInputStream(argv[0]));
			StreamTokenizer st = new StreamTokenizer(isr);
			st.resetSyntax();
			st.wordChars(32, '\u00FF');
			st.eolIsSignificant(true);
			st.commentChar('%');
			while (st.nextToken() != StreamTokenizer.TT_EOF) {
				switch (st.ttype) {
				case StreamTokenizer.TT_WORD:
					StringBuffer sb = new StringBuffer(st.sval);
					TrainingString ts = new TrainingString(sb);
					Debug.debug(true, "Read in "+st.sval);
					Debug.debug(true, "Training String data structure is ["+ts.toString()+"]");
					// let's look at the "next" structures:
					ts.printDataStructure();
					break;
				case 13:
				case StreamTokenizer.TT_EOL:
					break;
				default:
					throw new DataFormatException("TrainingString test code: Expected word but got type "+st.ttype);
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}
}	
