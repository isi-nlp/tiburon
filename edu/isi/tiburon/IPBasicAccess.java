package edu.isi.tiburon;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Vector;


// just uses a hashtable
// if this is a stringtransducer first int is state, vector is rhs
// if this is a tree transducer first int is label, vector is rhs
public class IPBasicAccess extends HashMap<Integer, HashMap<Vector<Integer>, HashSet<IPRule>>> implements IPTemplateAccess  {

	public void addItem(IPRule r) {
		int state = r.getState();
		if (r.getLabel() > -1)
			state = r.getLabel();
		Vector<Integer> rhskids = r.getRHS();
		if (!containsKey(state))
			put(state, new HashMap<Vector<Integer>, HashSet<IPRule>>());
		if (!get(state).containsKey(rhskids))
			get(state).put(rhskids, new HashSet<IPRule>());
		get(state).get(rhskids).add(r);
	}
	// given a state or label,(if string parsing, should be state. tree parsing, can be label), get all rhs templates that come from that state
	public Iterable<Vector<Integer>> getTemplates(int s) {
		if (containsKey(s))
			return get(s).keySet();
		return new Vector<Vector<Integer>>();
	}
	
	// given a state or label and rhs template, get IPRules that come from that state
	public Iterable<IPRule> getTemplates(int s, Vector<Integer> v) {
		if (containsKey(s) && get(s).containsKey(v))
			return get(s).get(v);
		return new Vector<IPRule>();
	}

}
