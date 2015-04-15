package edu.isi.tiburon;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// CFG Rule. Symbol lhs, Vector of Symbols rhs, weight.
public class CFGRule extends Rule implements Serializable {


	public void setRHS(StringItem t) {
		rhs = t;
		setHashCode();
	}

	public CFGRule(CFGRuleSet rs, Symbol inlhs, StringItem inrhs, double inweight, Semiring s) {
		lhs = inlhs;
		rhs = inrhs;
		weight = inweight;
		semiring = s;
		index = rs.getNextRuleIndex();
	}
	public CFGRule(CFGRule r) {
		lhs = r.lhs;
		rhs = new StringItem((StringItem)r.rhs);
		weight = r.weight;
		semiring = r.getSemiring();
		index = r.getIndex();
		tieid = r.tieid;
	}

	// for converting the range of a rule 
	public CFGRule(CFGRuleSet rs, StringTransducerRule oldRule) throws ImproperConversionException {
		if (oldRule.isCopying())
			throw new ImproperConversionException("Can't take domain of copying transducerrule "+oldRule);
		lhs = oldRule.getState();
		try {
			rhs = new StringItem(oldRule.getRHS());
		}
		catch (DataFormatException e) {
			throw new ImproperConversionException("Error constructing from "+oldRule);
		}
		semiring = oldRule.getSemiring();
		weight = oldRule.getWeight();
		index = rs.getNextRuleIndex();
		tieid = oldRule.getTie();
	}


	void setHashCode() {
		hsh = new Hash(lhs.getHash());
		hsh = hsh.sag(new Hash(weight));
		hsh = hsh.sag(rhs.getHash());
	}
	// equals if label is same and trees are same and weights are same
	public boolean equals(Object o) {
		if (!o.getClass().equals(this.getClass()))
			return false;

		CFGRule r = (CFGRule)o;
		if (!lhs.equals(r.lhs))
			return false;
		if (!rhs.equals(r.rhs))
			return false;
		if (weight != r.weight)
			return false;
		if (semiring != r.getSemiring())
			return false;
		return true;
	}

	// TODO: isNormal is dependent on literal/variable properties, which are determined after the entire CFG is read.
	public boolean isNormal() {
		boolean debug = true;
		Debug.debug(debug, "WARNING: isNormal not yet implemented for CFG");
		return true;
	}

	public boolean isSame(Rule r) {
		boolean debug = true;
		Debug.debug(debug, "WARNING: isSame not yet implemented for CFG");
		return true;
	}


	// separate left from right
	private static Pattern sidesPat = Pattern.compile("(\\S+|\"\"\"|\"[^\"]+\")\\s*->\\s*(.*?)\\s*(?:#\\s*(\\S+)\\s*)?(?:@\\s*(\\S+)\\s*)?$");

	
	// create rule from text representation
	public CFGRule(CFGRuleSet rs, String text, Semiring sem) throws DataFormatException, IOException {
		// rule is lhs, rhs vector, weight (optionally), and tie (optionally). 
		// separated by arrow and hash
		boolean debug = false;
		if (debug) Debug.debug(debug, "CFGRule: Creating out of "+text);
		index = rs.getNextRuleIndex();
		semiring = sem;
		
		Matcher sidesMatch = sidesPat.matcher(text);
		if (!sidesMatch.matches())
			throw new DataFormatException("Incorrect rule format: "+text);

		if (sidesMatch.group(1) == null)
			throw new DataFormatException("LHS appears to be empty in "+text);
		if (sidesMatch.group(2) == null)
			throw new DataFormatException("RHS appears to be empty in "+text);

		if (debug) Debug.debug(debug, "LHS is "+sidesMatch.group(1)+" and RHS is "+sidesMatch.group(2));
		// take lhs as state
		lhs = SymbolFactory.getSymbol(sidesMatch.group(1));


		rhs = new StringItem(new StringBuffer(sidesMatch.group(2)));
		if (debug) Debug.debug(debug, "CFGRule: Created rhs "+rhs.toString()+"; empty status is "+(rhs.isEmptyString()));

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

	// conversion creation
	// could throw ImproperConversion, but thus far I don't know of any way we couldn't convert RTG to CFG...
	public CFGRule(CFGRuleSet rs, RTGRule rtg) throws ImproperConversionException {
		lhs = rtg.getLHS();
		try {
			rhs = new StringItem((TreeItem)rtg.getRHS());
		}
		catch (DataFormatException e) {
			throw new ImproperConversionException("Could not convert rhs of "+rtg.toString()+" due to DataFormat: "+e.getMessage());
		}
		weight = rtg.getWeight();
		semiring = rtg.getSemiring();
		index = rs.getNextRuleIndex();
		tieid = rtg.tieid;
	}

	// TODO: unify this by making TrainingString a sharedclass with tree?
	public boolean isItemMatch(Symbol st, TrainingString i, RuleSet rs) {
		if (!st.equals(lhs)) {
			return false;
		}
		return ((StringItem)rhs).isItemMatch(i, (CFGRuleSet)rs);
	}


	// for training: getAllAlignments returns a vector of Vectors of state clusters, which are consecutive states and the string
	// they must fill. It is analogous to getPaths in RTGRule

	// we're assuming the rule is appropriate!
	public Vector<Vector<VariableCluster>> getAllAlignments(VariableCluster qi, RuleSet rs) {
		boolean debug = false;
		TrainingString i = qi.out();
		Vector<Vector<VariableCluster>> v = new Vector<Vector<VariableCluster>>();
		recursiveGetAlignments(v, new Vector<VariableCluster>(), i, (StringItem)rhs, rs, debug);
		// the string transducer version of this had to calculate the mapping to strings
		// I don't think I need to do this, so just return the v directly
		return v;
	}


	// traverse through the rule until the end is reached, adding elements along the way
	private void recursiveGetAlignments(Vector<Vector<VariableCluster>> alignSet, Vector<VariableCluster> currAlign, TrainingString currString, 
			StringItem currRule, RuleSet rs, boolean debug) {
		if(debug) {
			System.err.print("RecursiveGetAlignments: from ");
			System.err.print(currString == null ? "null" : currString.toString());
			System.err.print(" to ");
			System.err.print(currRule == null ? "null" : currRule.toString());
			System.err.println();
		}
		// if no more rule and no more string, add the current alignment
		if (currRule == null && (currString == null || currString.isEpsilon())) {
			if (debug) Debug.debug(debug, "String and rule both null. adding current alignment");
			alignSet.add(currAlign);
			return;
		}
		// if more string and no more rule this alignment is bogus
		if (currRule == null && currString != null) {
			if (debug) Debug.debug(debug, "rule null, string is "+currString.toString()+". abandoning current alignment");
			return;
		}

		// chop off till next variable, but make sure there's a match
		if (!rs.states.contains(currRule.getLabel())) {
			// if rule is epsilon or string is epsilon, only epsilon will do
			if (currRule.isEmptyString()) {
				if (currString == null || currString.isEpsilon()) {
					if (debug) Debug.debug(debug, "Epsilon rule matches epsilon or null string. add alignment and continue");
					alignSet.add(currAlign);
					return;
				}
				if (debug) Debug.debug(debug, "Epsilon rule can only match epsilon string. abandoning current alignment");
				return;
			}
			if (currString == null || currString.isEpsilon()) {
				if (debug) Debug.debug(debug, "Epsilon or null string can only match epsilon rule (or no rule). abandoning current alignment");
				return;
			}
			if (currString == null || !currRule.getLabel().equals(currString.getLabel())) {
				if (debug) Debug.debug(debug, "rule terminal "+currRule.getLabel().toString()+" mismatch with string terminal; abandoning current alignment");
				return;
			}
			if (debug) Debug.debug(debug, "chopping terminal "+currRule.getLabel().toString()+" and continuing");
			recursiveGetAlignments(alignSet, currAlign, currString.next(), currRule.getNext(), rs, debug);
			return;
		}
		if (debug) Debug.debug(debug, "We are matching state "+currRule.getLabel().toString());
		// at variable, identify next terminal, match in all the ways possible, from smallest to largest
		// TODO: implement something like nextTerminal for StringItems!!!
		Symbol nextTerm = currRule.getNextTerm(rs) == null ? null : currRule.getNextTerm(rs).getLabel();
		// if no next Terminal, it's just the rest of the string. only one option
		if (nextTerm == null) {
			if (debug) Debug.debug(debug, "No next terminal");
			VariableCluster newCluster = null;
			if (currString == null)
				newCluster = new VariableCluster(TrainingString.getEpsilon());
			else
				newCluster = new VariableCluster(currString);
			while (currRule != null && rs.states.contains(currRule.getLabel())) {
				// using state tree pair to just hold state; a little weird
				newCluster.addVariable(currRule.getLabel());
				currRule = currRule.getNext();
			}
			if (debug && currString != null) Debug.debug(true, "adding match of nonterminal cluster to  "+currString.toString()+"; done with alignment");
			currAlign.add(newCluster);
			alignSet.add(currAlign);
			return;
		}
		// normal case: for each time nextTerm is found in the string, match the string to that point to the cluster of nonterms
		// and recurse on the rest
		else {
			if (debug) Debug.debug(debug, "next terminal symbol in rule is "+nextTerm.toString());
			VariableCluster newClusterStub = new VariableCluster();
			while (currRule != null && rs.states.contains(currRule.getLabel())) {
				newClusterStub.addVariable(currRule.getLabel());
				currRule = currRule.getNext();
			}
			// first try the epsilon case
			// copy the alignment stub and cluster stub
			Vector<VariableCluster> epsAlign = new Vector<VariableCluster>(currAlign);
			VariableCluster epsCluster = new VariableCluster(newClusterStub);
			// set the cluster with the current substring and add it to the alignment stub
			epsCluster.setString(TrainingString.getEpsilon());
			if (debug) Debug.debug(debug, "aligning cluster to epsilon and continuing");
			epsAlign.add(epsCluster);
			// recurse on the rest of the rule, without shrinking the string
			recursiveGetAlignments(alignSet, epsAlign, currString, currRule, rs, debug);
			// now do the non-epsilon cases if there's anything left to align to
			if (currString != null) {
				TrainingString nextString = currString.next(nextTerm);
				while (nextString != null) {
					// copy the alignment stub and cluster stub
					Vector<VariableCluster> nextAlign = new Vector<VariableCluster>(currAlign);
					VariableCluster newCluster = new VariableCluster(newClusterStub);
					// set the cluster with the current substring and add it to the alignment stub
					newCluster.setString(currString.getSubString(currString.getStartIndex(), nextString.getStartIndex()));
					if (debug) Debug.debug(debug, "aligning cluster to "+newCluster.getString().toString()+" and continuing");
					nextAlign.add(newCluster);
					// recurse on the rest of the rule
					recursiveGetAlignments(alignSet, nextAlign, nextString, currRule, rs, debug);
					// set next string for next alignment
					nextString = nextString.next(nextTerm);
				}
			}
		}
	}

	
	// given a set of symbols that can be epsilons return all the forms of this rhs
	// that are appropriate
	// e.g. if rhs is BCDB and valid epsilon symbols are B, C, add CDB, BDB, BCD, DB, CD, BD, and
	// D.
//	public HashMap<StringItem, Double> epsRemove(HashMap<Symbol, Double> epsSyms) {
//		boolean debug = true;
//		HashMap<StringItem, Double> ret = new HashMap<StringItem, Double>();
//		// start off with this rhs
//		HashSet<StringItem> doneSet = new HashSet<StringItem>();
//		ArrayList<StringItem> todoSet = new ArrayList<StringItem>();
//		todoSet.add((StringItem)rhs);
//		while (todoSet.size() > 0) {
//			StringItem nextrhs = todoSet.remove(0);
//			if (doneSet.contains(nextrhs))
//				continue;
//			doneSet.add(nextrhs);
//			if (debug) Debug.debug(debug, "Doing eps removal for "+nextrhs);
//			if (debug) Debug.debug(debug, nextrhs+" size is "+nextrhs.getSize());
//			for (int i = 0; i < nextrhs.getSize(); i++) {
//				// if something is added it is put into ret and also returned. Otherwise newrhs is null!
//				StringItem newrhs = nextrhs.epsRemove(epsSyms, ret, i);
//				if (newrhs != null)
//					todoSet.add(newrhs);
//			}
//		}
//		return ret;
//	}

	// remove funny looking states from lhs and rhs, given an alias map.
	public void makePrintSafe(RuleSet rs, Hashtable<Symbol, Symbol> alias) {
		boolean debug = false;
		// fix the rhs
		StringItem s = makeStringPrintSafe(alias, (StringItem)rhs);
		if (debug) Debug.debug(debug, "Mapped "+rhs+" to "+s);
		// turn self into the new rule
		lhs = alias.get(lhs);
		rhs = s;
	}

	// create a new StringItem with print-safe states
	private StringItem makeStringPrintSafe(Hashtable<Symbol, Symbol> alias, StringItem rhs) {
		boolean debug = false;
		// if we're starting with epsilon, just return a new epsilon
		if (rhs.isEmptyString()) {
			StringItem ret = new StringItem();
			return ret;
		}
		Vector<Symbol> v = new Vector<Symbol>();
		StringItem ptr = rhs;
		// traverse the stringitem, replacing where necessary
		while (ptr != null) {
			if (alias.containsKey(ptr.getLabel())) {
				Symbol sym = alias.get(ptr.getLabel());
				v.add(sym);
				if (debug) Debug.debug(debug, "Making string print safe by mapping from "+ptr.getLabel().toString()+" to "+sym.toString());
			}
			else {
				v.add(ptr.getLabel());
				if (debug) Debug.debug(debug, "Making string print safe by not modifying symbol "+ptr.getLabel().toString());		
			}
			ptr = ptr.getNext();
		}
		StringItem ret = null;
		try {
			ret = new StringItem(v);
		}
		catch (DataFormatException e) {
			System.err.println("Error making "+rhs.toString()+" print safe: "+e.getMessage());
			System.exit(1);
		}
		return ret;
	}


	// test method
	public static void main(String argv[]) throws Exception {
		InputStreamReader isr = new InputStreamReader(new FileInputStream(argv[0]));
		StreamTokenizer st = new StreamTokenizer(isr);
		st.resetSyntax();
		st.eolIsSignificant(true);
		st.wordChars(14, '\u00ff');
		CFGRuleSet rs = new CFGRuleSet();
		RealSemiring s = new RealSemiring();
		while (st.nextToken() == StreamTokenizer.TT_WORD) {
			System.out.println("Processing "+st.sval);
			CFGRule r = new CFGRule(rs, st.sval, s);
			System.out.println("rule is "+r.toString());
			st.nextToken();
			if (st.ttype != StreamTokenizer.TT_EOL &&
					st.ttype != StreamTokenizer.TT_EOF)
				throw new DataFormatException("saw "+st.ttype+" instead of EOL or EOF");
		}
		System.err.println("Current item is "+st.ttype);
	}
}