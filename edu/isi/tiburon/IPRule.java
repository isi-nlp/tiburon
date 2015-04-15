package edu.isi.tiburon;

import java.util.HashMap;
import java.util.Vector;


// representation of a tree transducer or string transducer rule for 
// integrated parsing
// all common templates collapsed into one object for ease of processing.
public class IPRule {
	
	// template info. potentially common to more than one rule
	// memoization object of templates->states
	private static HashMap<String, Template> template_factory = null;
	static class Template {
		private int state;
		private int label; // this is -1 if tree-string transducer
		private Vector<Integer> rhs;
		public int getState() { return state; }
		public int getLabel() { return label; }
		public Vector<Integer> getRHS() { return rhs; }
		private Template(int s, int l, Vector<Integer> r) { state = s; label = l; rhs = r; }
		public static Template get(int s, int l, Vector<Integer> r) {
			String str = s+":"+l+":"+r;
			if (template_factory == null)
				template_factory = new HashMap<String, Template>();
			if (!template_factory.containsKey(str))
				template_factory.put(str, new Template(s, l, r));
			return template_factory.get(str);
		}
		public String toString() { return state+":"+label+":"+rhs; }
	}
	private Template template;
	// semantics info. specializes the template into a rule
	
	static class Semantics {
		private int semlabel; // this is -1 if not a finishing rule
		private int semchildren; // number of children in semantics
		private HashMap<Integer, Integer> childmap; // maps 0-based variable order to 0-based child in rhs
		private double weight;
		private double vitweight; // special weight placed in the leftmost lowest sub-rule of the original
		public Semantics(int sl, int sc, HashMap<Integer, Integer> cm, double w, double vw) {
			semlabel = sl;
			semchildren = sc;
			childmap = cm;
			weight = w;
			vitweight = vw;
		}
		public HashMap<Integer, Integer> getMap() { return childmap; }
		public int getLabel() { return semlabel; }
		public int getSemChildren() { return semchildren; }
		public double getWeight() { return weight; }
		public double getVitWeight() { return vitweight; }
		public String toString() {
			StringBuffer ret = new StringBuffer();
			if (semlabel >= 0) {
				ret.append(semlabel);
				if (semchildren > 0)
					ret.append("( ");
				for (int i = 0; i < semchildren; i++)
					ret.append("* ");
				if (semchildren > 0)
					ret.append(") ");
			}
			ret.append(getMap());
			return ret.toString();
		}
		// TODO: something has to connect to lower rules
	}
	private Vector<Semantics> semantics;
	
	public Semiring semiring;
	
	// memoization object of templates->states
	private static HashMap<Template, IPRule> memo_factory=null;

	// template-only constructor
	private IPRule(int s, int l, Vector<Integer> r, Semiring semi) {
		template = Template.get(s, l, r);
		semiring = semi;
		semantics = new Vector<Semantics>();
	}
	// constructor using already-made template
	private IPRule(Template t, Semiring s) {
		template = t;
		semiring = s;
		semantics = new Vector<Semantics>();
	}
	// memoization access
	public static IPRule get(int s, int l, Vector<Integer> r, int sl, int sc, HashMap<Integer, Integer> cm, double w, double vw, Semiring semi) {
		Template t =  Template.get(s, l, r);
		if (memo_factory == null)
			memo_factory = new HashMap<Template, IPRule>();
		if (!memo_factory.containsKey(t))
			memo_factory.put(t, new IPRule(t, semi));
		IPRule ret = memo_factory.get(t);
		ret.semantics.add(new Semantics(sl, sc, cm, w, vw));
		return ret;
	}
	
	
	public int getState() { return template.getState(); }
	public int getLabel() { return template.getLabel(); }
	public Vector<Integer> getRHS() { return template.getRHS(); }
	public Vector<Semantics> getSemantics() { return semantics; }
	
	public String toString() {
		StringBuffer ret = new StringBuffer(template.getState()+" -> ");
		if (template.getLabel() >= 0) {
			ret.append(template.getLabel()+"(");
		}
		ret.append(template.getRHS().toString());
		if (template.getLabel() >= 0) {
			ret.append(")");
		}
		ret.append(" { ");
		for (Semantics sem : getSemantics()) {
			ret.append(sem.toString()+", ");
		}
		ret.append("}");
		return ret.toString();
	}
	
}
