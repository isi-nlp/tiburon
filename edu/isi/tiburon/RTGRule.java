package edu.isi.tiburon;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
//import edu.dickinson.braught.Rounding;
// RTG rule. Symbol lhs, Tree rhs. weight.
public class RTGRule extends Rule {


//	// for composition. not always filled
//	private Vector<TreeTransducerRule> tiedRules=null;
//
//
//	// unique accessors
//
//	public boolean hasTransducerRule() { return tiedRules != null; }
//	public Vector<TreeTransducerRule> getTransducerRules() { return tiedRules; }
	// extended accessor
	public Symbol[] getRHSLeaves() { return ((TreeItem)rhs).getLeaves(); }

	// unique settors
	public void setRHS(TreeItem t) {
		rhs = t;
		setHashCode();
	}

//	public void tieToTransducerRule(TreeTransducerRule ttr) {
//		if (tiedRules == null)
//			tiedRules = new Vector<TreeTransducerRule>();
//		tiedRules.add(ttr);
//		setHashCode();
//	}
//	public void tieToTransducerRule(Vector<TreeTransducerRule> ttr) {
//		if (ttr == null)
//			return;
//		if (tiedRules == null)
//			tiedRules = new Vector<TreeTransducerRule>(ttr);
//		else
//			tiedRules.addAll(ttr);
//		setHashCode();
//	}

	// unique constructors
	public RTGRule(RTGRuleSet rs, Symbol inlhs, TreeItem inrhs, double inweight, Semiring s) {
		lhs = inlhs;
		rhs = inrhs;
		weight = inweight;
		semiring = s;
		index = rs.getNextRuleIndex();
	}
	public RTGRule(RTGRuleSet rs, Symbol inlhs, Symbol inrhs, double inweight, Semiring s) {
		lhs = inlhs;
		rhs = new TreeItem(inrhs);
		weight = inweight;
		semiring = s;
		index = rs.getNextRuleIndex();
	}

	public RTGRule(RTGRule r) {
		lhs = r.lhs;
		rhs = new TreeItem((TreeItem)r.rhs);
		weight = r.weight;
		semiring = r.getSemiring();
		index = r.getIndex();
		tieid = r.tieid;
	}

	// for conversion purposes
	public RTGRule(RTGRuleSet rs, Symbol dummy, CFGRule oldRule, HashMap map) throws ImproperConversionException {
		// lhs is the same, transform rhs, preserve weight
		lhs = (Symbol)map.get(oldRule.getLHS());
		try {
			rhs = new TreeItem((StringItem)oldRule.getRHS(), dummy, map);
		}
		catch (ImproperConversionException e) {
			throw new ImproperConversionException("Could not convert rhs of "+oldRule.toString()+": "+e.getMessage());
		}
		weight = oldRule.getWeight();
		semiring = oldRule.getSemiring();
		index = rs.getNextRuleIndex();
		tieid = oldRule.tieid;
	}

	//     // for converting domain of transducer to rtg
	//     public RTGRule(RTGRuleSet rs, TransducerRule oldRule, HashMap<HashSet<Symbol>, Symbol> map) throws ImproperConversionException {
	// 	lhs = oldRule.getState();
	// 	try {
	// 	    rhs = new TreeItem(oldRule.getLHS(), oldRule.getTRVM(), map); 
	// 	}
	// 	catch (ImproperConversionException e) {
	// 	    throw new ImproperConversionException("Couldn't map domain of "+oldRule+" to RTG rhs: "+e.getMessage());
	// 	}
	// 	// TODO: make rhs by mapping states from rhs of oldRule to lhs of oldRule
	// 	// getGrammarRule in TransducerRule seems to be in the right direction...
	// 	weight = oldRule.getWeight();
	// 	semiring = oldRule.getSemiring();
	// 	index = rs.getNextRuleIndex();
	// 	// note: ties not kept
	//     }

	// for converting the common domain of several rules in a transducer to rtg
	public RTGRule(RTGRuleSet rs, 
			HashSet<Symbol> startState, 
			Vector<TransducerRule> oldRules, 
			Vector<HashSet<Symbol>> varSet,
			HashMap<HashSet<Symbol>, Symbol> map) throws ImproperConversionException {
		boolean debug = false;
		if (debug) Debug.debug(debug, "Building RTG rule that goes from "+startState+" to "+varSet+" using "+oldRules+" and "+map);

		if (startState.size() == 1) {
			Symbol[] ar = new Symbol[1];
			lhs = startState.toArray(ar)[0];
		}
		else {
			if (!map.containsKey(startState))
				throw new ImproperConversionException("No mapped symbol for start state cluster "+startState);
			lhs = map.get(startState);
		}
		// rules will either all have a symbol-full lhs that is the same, all be epsilon, or some be epsilon and
		// the rest have the same lhs. If any has an epsilon, use it. Otherwise, use a symbol lhs
		TransducerLeftTree template = null;
		for (TransducerRule tr : oldRules) {
			if (!tr.isInEps())
				continue;
			template = tr.getLHS();
			break;
		}
		if (template == null)
			template = oldRules.get(0).getLHS();
		// only use the first rule in the rule set -- we assume they're all the same pattern


		try {
			rhs = new TreeItem(template, varSet, map);
		}
		catch (ImproperConversionException e) {
			throw new ImproperConversionException("Couldn't map domain of "+oldRules+" to RTG rhs: "+e.getMessage());
		}       
		semiring = oldRules.get(0).getSemiring();
		// new: each weight is one
//		weight = semiring.ONE();
		// old: weight is combination of weights in original
		// weights should be multiplied! (was added)
		 	weight = semiring.ONE();
		 	for (TransducerRule r : oldRules)
		 	    weight = semiring.times(weight, r.getWeight());
		index = rs.getNextRuleIndex();
	}

	// for converting the range of a rule 
	public RTGRule(RTGRuleSet rs, TreeTransducerRule oldRule) throws ImproperConversionException {
		if (oldRule.isCopying())
			throw new ImproperConversionException("Can't take domain of copying transducerrule "+oldRule);
		lhs = oldRule.getState();
		rhs = new TreeItem(oldRule.getRHS());
		semiring = oldRule.getSemiring();
		weight = oldRule.getWeight();
		index = rs.getNextRuleIndex();
		tieid = oldRule.getTie();

	}

	// for backward application from transducer rule and state substitution vector
	public RTGRule(RTGRuleSet rs, Symbol state, TransducerRule oldRule, Hashtable<TransducerRightSide, Symbol> staterep) throws ImproperConversionException {
		if (oldRule.isCopying())
			throw new ImproperConversionException("Can't do backward application on copying transducer rule "+oldRule);
		lhs = state;
		rhs = new TreeItem(oldRule.getLHS(), oldRule.trvm, staterep);
		semiring = oldRule.getSemiring();
		weight = oldRule.getWeight();
		index = rs.getNextRuleIndex();
		tieid = oldRule.getTie();
		
	}
	
//	// for backward application from transducer rule and state substitution vector in parser-driven search
//	// same as above but weight is separately specified
//	public RTGRule(RTGRuleSet rs, Symbol state, TransducerRule oldRule, double wgt, Hashtable<TransducerRightSide, Symbol> staterep) throws ImproperConversionException {
//		if (oldRule.isCopying())
//			throw new ImproperConversionException("Can't do backward application on copying transducer rule "+oldRule);
//		lhs = state;
//		rhs = new TreeItem(oldRule.getLHS(), oldRule.trvm, staterep);
//		semiring = oldRule.getSemiring();
//		weight = wgt;
//		index = rs.getNextRuleIndex();
//		tieid = oldRule.getTie();
//		
//	}
	// for backward application from transducer rule and simple state vector in parser-driven search
	// same as above but weight is separately specified
	public RTGRule(RTGRuleSet rs, Symbol srcstate, TransducerRule oldRule, double wgt, Vector<Symbol> dststates) throws ImproperConversionException {
		if (oldRule.isCopying())
			throw new ImproperConversionException("Can't do backward application on copying transducer rule "+oldRule);
		lhs = srcstate;
		rhs = new TreeItem(oldRule.getLHS(), dststates);
		semiring = oldRule.getSemiring();
		weight = wgt;
		index = rs.getNextRuleIndex();
		tieid = oldRule.getTie();
		
	}
	
	// implemented methods

	void setHashCode() {
		hsh = new Hash(lhs.getHash());
		hsh = hsh.sag(new Hash(weight));
		hsh = hsh.sag(rhs.getHash());
		if (getTransducerRules() != null) {
			for (TransducerRule tiedRule : getTransducerRules())
				hsh = hsh.sag(tiedRule.getHash());
		}
	}
	// equals if label is same and trees are same and weights are same
	public boolean equals(Object o) {
		if (!o.getClass().equals(this.getClass()))
			return false;

		RTGRule r = (RTGRule)o;
		if (!lhs.equals(r.lhs))
			return false;
		if (!rhs.equals(r.rhs))
			return false;
		if (weight != r.weight)
			return false;
		if (semiring != r.getSemiring())
			return false;
		if (getTransducerRules() != null) {
			Vector<TransducerRule> theseRules = getTransducerRules();
			Vector<TransducerRule> thoseRules = r.getTransducerRules();
			if (thoseRules == null || !theseRules.equals(thoseRules))
				return false;
		}
		return true;
	}

	// check if this tree is in normal form, or if it's been decided to be in normal form
	public boolean isNormal() {
		return ((TreeItem)rhs).isNormal();
	}

	// checks for rhs similarity
	public boolean isSame(Rule r) {
		return r instanceof RTGRule && rhs.equals(((RTGRule)r).rhs);
	}


	// separate left from right
	private static Pattern sidesPat = Pattern.compile("(\\S+|\"\"\"|\"[^\"]+\")\\s*->\\s*(.*?)\\s*(?:#\\s*(\\S+)\\s*)?(?:@\\s*(\\S+)\\s*)?$");

	// create rule from text representation
	public RTGRule(RTGRuleSet rs, String text, Semiring s) throws DataFormatException, IOException {
		// rule is lhs, tree, weight (optionally), and tie (optionally). 
		// separated by arrow and hash
		boolean debug = false;
		if (debug) Debug.debug(debug, "RTGRule: Creating out of "+text);
		index = rs.getNextRuleIndex();
		semiring = s;

		Matcher sidesMatch = sidesPat.matcher(text);
		if (!sidesMatch.matches())
			throw new DataFormatException("Incorrect rule format: "+text);

		if (sidesMatch.group(1) == null)
			throw new DataFormatException("LHS appears to be empty in "+text);
		if (sidesMatch.group(2) == null)
			throw new DataFormatException("RHS appears to be empty in "+text);

		// take lhs as state
		lhs = SymbolFactory.getSymbol(sidesMatch.group(1));


		rhs = new TreeItem(new StringBuffer(sidesMatch.group(2)));
		if (debug) Debug.debug(debug, "Read rule rhs "+sidesMatch.group(2));

		// now read in the weight if it exists
		if (sidesMatch.group(3) != null) {
			try {
				weight = semiring.printToInternal((new Double(sidesMatch.group(3))).doubleValue());
			}
			catch (NumberFormatException e) {
				throw new DataFormatException("Bad number format from "+sidesMatch.group(3)+": "+e.getMessage(), e);
			}
		}
		else {
			weight = semiring.ONE();
		}

		// now add a tie if it exists
		if (sidesMatch.group(4) != null) {
			try {
				tieid = (new Integer(sidesMatch.group(4))).intValue();
				if (debug) Debug.debug(debug, "Read tieid as "+tieid);
			}
			catch (NumberFormatException e) {
				throw new DataFormatException("Tie id not integer value in "+sidesMatch.group(4)+": "+e.getMessage(), e);
			}
		}
	}

	// for training
	public boolean isItemMatch(Symbol st, Item i, RuleSet rs) {
		if (!st.equals(lhs)) {
			return false;
		}
		return ((TreeItem)rhs).isItemMatch(i, rs);
	}

	// we're assuming the rule is appropriate!
	public Vector getPaths(StateItem qi, RuleSet rs) {
		Symbol q = qi.state();
		Item item = qi.item();
		int[] align = qi.align();
		Vector retVec = new Vector();
		try {
			retVec = rhs.mapItem(item, align[0], align[1], rs);
		}
		catch (Exception e) {
			System.err.println("Getting paths from "+item.toString()+" in "+toString()+": "+e.getMessage());
		}
		return retVec;
	}

	// remove funny looking states from lhs and rhs, given an alias map.
	public void makePrintSafe(RuleSet rs, Hashtable<Symbol, Symbol> alias) {
		
		// do it in place
		makeTreePrintSafe(alias, (TreeItem)rhs);
		((TreeItem)rhs).unmemoize();
		lhs = alias.get(lhs);
	}

	// recursive function to replace states in tree with print-safe states
	private TreeItem makeTreePrintSafe(Hashtable<Symbol, Symbol> alias, TreeItem t) {
		boolean debug = false;
		if (alias.containsKey(t.getLabel())) {
			if (debug) Debug.debug(debug, "Replacing aliased "+t.getLabel()+" with "+alias.get(t.getLabel()));
			t.setLabel(alias.get(t.getLabel()));
		}
		else {
			if (debug) Debug.debug(debug, "No alias for "+t.getLabel());
		}
		for (int i = 0; i < t.getNumChildren(); i++) {
			makeTreePrintSafe(alias, t.getChild(i));
		}
		return t;
	}




}
