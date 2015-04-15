
package edu.isi.tiburon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StreamTokenizer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import gnu.trove.TIntHashSet;
import gnu.trove.TObjectDoubleHashMap;
import gnu.trove.TObjectDoubleIterator;
import gnu.trove.TObjectIntHashMap;

// this will be built  bit by bit...
public class TransducerTraining {


	public static boolean ISCONDITIONAL = true;
	public static void setConditional(boolean t) { ISCONDITIONAL = t; }

	// a pair of weighted tree, something
	public static abstract class TreePair implements Serializable {
		public TreeItem in;
		public double weight;
		abstract public String toString();
		abstract public int getSize();
		// TODO: hashcode and equals
	}

	// a pair of weighted tree, trees
	public static  class TreeTreePair extends TreePair{
		public TreeItem out;
		public TreeTreePair(TreeItem i, TreeItem o, double w) { in = i; out = o; weight = w; }
		public String toString() { return in.toString()+", "+out.toString()+", "+weight; }
		// number of nodes in both trees
		public int getSize() {
			return in.numNodes()+out.numNodes();
		}

		// TODO: hashcode and equals
	}
	// a pair of weighted tree, strings
	public static class TreeStringPair extends TreePair{
		public TrainingString out;
		public TreeStringPair(TreeItem i, TrainingString o, double w) { in = i; out = o; weight = w; }
		public String toString() { return in.toString()+", "+out.toString()+", "+weight; }
		// number of nodes in tree + size of string
		public int getSize() {
			return in.numNodes()+out.getSize();
		}
		// TODO: hashcode and equals
	}

	// read a list of tree/tree or tree/string pairs from file
	// format of a record is:
	// count (optional)
	// intree
	// out(tree|string)
	// derivfile(optional) (NOT YET IMPLEMENTED)
	// determine what you're reading using file differentiation

	// all training pairs are written as a big temporary file and re-loaded
	// as needed

	// return vector contains a File and an Integer (so I don't have to change the signature for now...)
	static public Vector<Comparable> readTreeSet(BufferedReader br, 
			boolean isTriple, boolean readTree, 
			Semiring sr)  throws DataFormatException, 
			FileNotFoundException, 
			IOException {

		Vector<Comparable> v = new Vector<Comparable>();
		boolean debug = false;
		StreamTokenizer st = new StreamTokenizer(br);
		st.resetSyntax();
		st.eolIsSignificant(false);
		st.wordChars(32,  '\u00FF');
		// whitespace is only newline stuff!
		st.whitespaceChars(10, 13);
		//	st.parseNumbers();
		st.commentChar('%');

		// the output file
		File of = null;
		of = File.createTempFile("treeset", "tmp");
		of.deleteOnExit();
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(of));

		int counter = 0;
		Date readTime = new Date();
		try {
			while (st.nextToken() != StreamTokenizer.TT_EOF) {
				// first get a number
				double count;
				if (isTriple) {
					if (st.ttype != StreamTokenizer.TT_WORD)
						throw new DataFormatException("Expected word but got "+st.ttype);
					if (debug) Debug.debug(debug, "Reading count "+st.sval);
					count = sr.convertFromReal(Double.parseDouble(st.sval));
					st.nextToken();
				}
				else
					count = sr.convertFromReal(1);
				if (debug) Debug.debug(debug, "Reading intree "+st.sval);

				TreeItem intree = new TreeItem(new StringBuffer(st.sval));
				st.nextToken();
				if (debug) Debug.debug(debug, "Reading out element "+st.sval);
				if (readTree) {
					TreeItem outtree = new TreeItem(new StringBuffer(st.sval));
					if (debug) Debug.debug(debug, "Read tree tree triple "+count+", "+intree.toString()+", "+outtree.toString());
					TreeTreePair ttp = new TreeTreePair(intree, outtree, count);
					if (debug) Debug.debug(debug, "Made pair");

					if (debug) Debug.debug(debug, "About to write object");
					oos.writeObject(ttp);
					oos.reset();
					if (debug) Debug.debug(debug, "Wrote object");
					ttp = null;
					outtree = null;
				} 
				else {
					TrainingString outstring = new TrainingString(new StringBuffer(st.sval));
					if (debug) Debug.debug(debug, "Read tree string triple "+count+", "+intree.toString()+", "+outstring.toString());
					TreeStringPair tsp = new TreeStringPair(intree, outstring, count);
					oos.writeObject(tsp);
					oos.reset();
					tsp = null;
					outstring = null;
				}
				intree = null;
				counter++;
				if (counter >= 10000 && counter % 10000 == 0) {
					Date pause = new Date();
					long lapse = pause.getTime() - readTime.getTime();
					readTime = pause;
					Debug.prettyDebug("Read "+counter+" training examples: "+lapse);		    
				}
			}
		}
		catch (IOException e) {
			System.err.println("Problem while trying to write pair from line "+counter+" to temporary file; "+e.getMessage());
			System.exit(1);
		}
		catch (DataFormatException e) {
			throw new DataFormatException("At example "+counter+": "+e.getMessage());
		}
		oos.close();
		v.add(of);
		v.add(new Integer(counter));

		if (debug) Debug.debug(debug, "Done reading");
		return v;
	}



	// if no derivations have been built...
	//     static public TransducerRuleSet train(TransducerRuleSet intrs, 
	// 					  Vector treePairs,
	// 					  double epsilon,
	// 					  int maxit,
	// 					  boolean debug) {

	// 	Semiring semiring = intrs.getSemiring();

	// 	//create all the derivation forests and write them to file, and get pointers
	// 	Hashtable derivMap = getAllDerivationRuleSets(intrs, treePairs, null);
	// 	return train(intrs, derivMap, epsilon, maxit, debug);

	//     }

	// jon's train algorithm - set weights on transducer rules
	// TODO: add normalization function
	// TODO: add prior counts
	static public TransducerRuleSet train(TransducerRuleSet intrs, 
			File setFile,
			int setSize,
			File derivFile,
			double epsilon,
			int maxit,
			boolean wasNorm) {
		boolean debug = false;
		boolean getOneBests = false;
		if (ISCONDITIONAL) {
			if (debug) Debug.debug(debug, "doing conditional training");
		}
		Semiring semiring = intrs.getSemiring();
		// don't modify old trs
		TransducerRuleSet trs  = null;

		if (intrs instanceof TreeTransducerRuleSet)
			trs = new TreeTransducerRuleSet((TreeTransducerRuleSet)intrs);
		// 	else if (intrs instanceof RTGRuleSet)
		// 	    trs = new RTGRuleSet((RTGRuleSet) intrs);
		else if (intrs instanceof StringTransducerRuleSet)
			trs = new StringTransducerRuleSet((StringTransducerRuleSet)intrs);
		else {
			Debug.debug(true, "TransducerTraining did not receive a TreeTransducerRuleSet, an RTGRuleSet, or a StringTransducerRuleSet");
			System.exit(-1);
		}


		// log likelihood change
		double delta = epsilon;
		double lastL = Double.NEGATIVE_INFINITY;
		int itno = 0;
		//  	long beforeUsage = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
		//  	Debug.prettyDebug("Before training: heap at "+beforeUsage);
		//	Debug.debug(debug, "Input transducer is"+trs.toString());

		// em core
		// for calculating cross-entropy
		int totalSize = 0;
		boolean totalSizeSet = false;
		while ((epsilon == 0 || delta >= epsilon) && itno < maxit) {
			// TODO - use dirichilet priors
			// TODO - not have to do this every iteration
			// for now, if the rule isn't in the hashmap, count is assumed to be 0
			//	    boolean tempdebug = debug;
			//	    debug = true;
			if (debug) Debug.debug(debug, "Iteration "+itno);
			//	    debug = tempdebug;
			Hashtable<TransducerRule, Double> counts = new Hashtable<TransducerRule, Double>();
			// TODO - use variable method for Z?
			Hashtable<Symbol, Double> Z = new Hashtable<Symbol, Double>();
			double L = semiring.ONE();

			ObjectInputStream setois = null;
			ObjectInputStream drsois = null;
			try {
				setois = new ObjectInputStream(new FileInputStream(setFile));
				drsois = new ObjectInputStream(new FileInputStream(derivFile));
			}
			catch (IOException e) {
				System.err.println("Problem in training while trying to open the info files: "+e.getMessage());
				System.exit(1);
			} 
			// estimate step
			//	    Debug.prettyDebug("Set size is "+setSize);
			for (int setSizeCounter = 0; setSizeCounter < setSize; setSizeCounter++) {
				// read in the training file and derivation forest
				DerivationRuleSet drs = null;
				TreePair tp = null;
				try {
					tp = (TreePair)setois.readObject();
					drs = (DerivationRuleSet)drsois.readObject();
					if (drs == null)
						continue;
					drs.revive(intrs, semiring);
				}
				catch (IOException e) {
					System.err.println("Problem while trying to read in a derivation and tree pair "+e.getMessage());
					System.exit(1);
				}
				catch (ClassNotFoundException e) {
					System.err.println("Class-casting problem while trying to read in a derivation and tree pair "+e.getMessage());
					System.exit(1);
				}
				if (drs == null || tp == null) {
					Debug.prettyDebug("Reading of training example completed, but objects are null; continuing...");
					continue;
				}
				// 		Debug.prettyDebug("Derivation Rule Set for "+tp.toString()+" has "+drs.getNumRules()+" rules");
				//  		and "+drs.getNumberOfDerivations()+" derivations");
				// calculateWeights is based on the tr, so changing that changes the dr
				drs.calculateWeights();
				
				Iterator drsruleit = drs.getRules().iterator();
				while (drsruleit.hasNext()) {
					DerivationRule dr = (DerivationRule)drsruleit.next();

					if (debug) {
						if (dr.isVirtual())
							Debug.debug(debug, "\tGamma for virtual "+dr.toString()+" is "+drs.getAlpha(dr.getLHS()));
						else
							Debug.debug(debug, "\tGamma for "+dr.toString()+" is "+drs.getAlpha(dr.getLHS())+" times "+dr.getWeight());
					}
					double gamma = dr.isVirtual() ? drs.getAlpha(dr.getLHS()) : semiring.times(drs.getAlpha(dr.getLHS()), dr.getWeight());
					int [] drrhs = drs.getLeafChildren(dr);
					for (int i = 0; i < drrhs.length; i++) {
						int s = drrhs[i];
						try {
							gamma = semiring.times(gamma, drs.getBeta(s));
							if (debug) Debug.debug(debug, " times "+drs.getBeta(s));
						}
						catch (UnusualConditionException e) {
							Debug.debug(true, "Unusual Condition getting beta "+i+" for rule "+dr.toString()+": "+e.getMessage());
						}
					}
					if (debug) Debug.debug(debug, " equals "+gamma);
					if (dr.isVirtual()) {
						if (debug) Debug.debug(debug, "Ignoring estimation step for virtual rule");
						continue;
					}
					TransducerRule tr = dr.getLabel();
					// the new value for the count of this rule
					double newCount = semiring.ZERO();
					// the portion we're adding. This is so we can properly increase Z
					double newCountPortion = semiring.ZERO();
					if (counts.containsKey(tr)) {
						if (debug) Debug.debug(debug, "Increasing prior count prediction of "+counts.get(tr).doubleValue());
						newCount = semiring.plus(newCount, counts.get(tr).doubleValue());
					}
					else {
						if (debug) Debug.debug(debug, "First time for "+tr.toString()+" this iteration");
					}
					double startBeta = semiring.ZERO();
					try {
						startBeta = drs.getBeta(drs.getStartState());
					}
					catch (UnusualConditionException e) {
						Debug.debug(true, "Unusual Condition getting start beta: "+e.getMessage());
					}
					if (!dr.isVirtual())
						if (debug) Debug.debug(debug, tr.toString()+" : "+tp.in+" : "+dr.getLHS()+" : "+drs.getAlpha(dr.getLHS()));
					//		    Debug.debug(debug, tr.toString()+" : "+newCountPortion+"+("+tp.weight+"*"+gamma+"/"+startBeta+") = ");
					newCountPortion = semiring.plus(newCountPortion, 
							semiring.times(tp.weight, 
									(semiring.times(gamma, 
											semiring.inverse(startBeta)
									)
									)
							)
					);

					newCount = semiring.plus(newCount, newCountPortion);

					if (debug) Debug.debug(debug, "Estimating counts of "+tr.toString()+" at "+newCount);

					counts.put(tr, new Double(newCount));
					// conditional is the bucket of z we're storing in
					// it should be LHS ROOT (i.e., the state and left symbol only, no children!)
					// in conditional mode, it should be entire LHS!
					Symbol cond;
					if (ISCONDITIONAL) {
						cond = dr.getLabel().getLHSCondSym();
						//			Debug.prettyDebug("Z for "+dr.getLabel().toString()+" is "+cond.toString());
					}
					else {
						cond = dr.getLabel().getLHSSym();
					}
					if (!Z.containsKey(cond))
						Z.put(cond, new Double(newCountPortion));
					else
						Z.put(cond, new Double(semiring.plus(Z.get(cond).doubleValue(), newCountPortion)));
					if (debug) Debug.debug(debug, "Setting Z of "+cond.toString()+" to "+Z.get(cond).doubleValue());

					//		    Z += newCount;
				}
				double startBeta = semiring.ZERO();
				try {
					startBeta = drs.getBeta(drs.getStartState());
				}
				catch (UnusualConditionException e) {
					Debug.debug(true, "Unusual Condition getting start beta after iteration: "+e.getMessage());
				}
				if (debug) Debug.debug(debug, "done with estimation");
				if (debug) Debug.debug(debug, "Likelihood of "+drs.getStartState()+" is "+startBeta);
				// 		if (debug && semiring.ZERO() < semiring.ONE())
				// 		    Debug.debug(debug, "neg log is "+(-Math.log(drs.getBeta(drs.getStartState()))));
				if (debug) Debug.debug(debug, "item weight is "+tp.weight+", i.e. "+semiring.internalToPrint(tp.weight));
				//		Debug.prettyDebug("Adding "+startBeta+" * "+tp.weight);
				L = semiring.times(L, semiring.times(startBeta, tp.weight));
				if (!totalSizeSet)
					totalSize += (tp.getSize()*semiring.internalToPrint(tp.weight));
				drs = null;
			}
			totalSizeSet=true;
			//	    Debug.prettyDebug("After estimation, L is at "+L+"; in print form that's "+semiring.internalToPrint(L)+" and size is "+totalSize);
			if (debug) Debug.debug(debug, "After estimation, L is at "+L+"; in print form that's "+semiring.internalToPrint(L)+" and size is "+totalSize);
			// 	    if (debug && semiring.ZERO() < semiring.ONE())
			// 		Debug.debug(debug, "neg log of L is "+(-Math.log(L)));

			// cross entropy is -ln(p(corpus)). L is p(corpus) if "real", -ln(p(corpus) if of log form.

			double crossentropy;
			double corpusprob;
			if (semiring.ZERO() < semiring.ONE())
				corpusprob = -Math.log(L);
			else
				corpusprob = L;
			crossentropy = corpusprob/totalSize;
			String prefix;
			if (itno == 0)
				if (wasNorm)
					prefix = "Cross entropy with normalized initial weights is ";
				else
					prefix = "Cross entropy with non-normalized initial weights is ";
			else
				prefix = "Cross entropy after "+itno+" iterations is ";
			Debug.prettyDebug(prefix+crossentropy+"; corpus prob is e^"+(-corpusprob));	    

			// maximize step
			// first check on tied rules, then do the rest

			// 1 - scaling factors for appropriate lhs. if not present, we can assume the scale is 1 (i.e. the 1 - scale is 0)
			TObjectDoubleHashMap scaling = new TObjectDoubleHashMap();
			
			// for each tie class, get the combined count of all rules and the combined sum of all denominators

			TObjectDoubleHashMap Zsubtracts = new TObjectDoubleHashMap();
			for (int tie : trs.getTies()) {
				
				Iterator tieit = trs.getTiedRules(tie).iterator();
				double rulecount = semiring.ZERO();
				double denomcount = semiring.ZERO();

				if (debug) Debug.debug(debug, "Setting weights for tie class "+tie);
				// collect all the counts
				while (tieit.hasNext()) {
					TransducerRule tr = (TransducerRule)tieit.next();

					// 		    if (!trs.getRules().contains(tr)) {
					// 			Debug.debug(true, tr.toString()+"not found in main rules before doing anything!");
					// 			System.exit(1);
					// 		    }

					Symbol lhs;
					if (ISCONDITIONAL)
						lhs = tr.getLHSCondSym();
					else
						lhs = tr.getLHSSym();
					Double thisrulecount = counts.get(tr);
					if (thisrulecount == null)
						thisrulecount = new Double(semiring.ZERO());
					if (Z.containsKey(lhs))
						if (debug) Debug.debug(debug, "Rule "+tr.toString()+" has lhs "+lhs.toString()+
								" with counts "+semiring.internalToPrint(thisrulecount.doubleValue())+
								" and "+semiring.internalToPrint(Z.get(lhs).doubleValue()));
					rulecount = semiring.plus(rulecount, thisrulecount.doubleValue());
					if (Z.containsKey(lhs)) {
						//			Debug.debug(true, "Increasing denom for tie class of "+tr.toString()+" from "+
						//					denomcount+" by Z for "+lhs.toString()+" which is "+((Double)Z.get(lhs)).doubleValue());
						denomcount = semiring.plus(denomcount, Z.get(lhs).doubleValue());
					}
					if (debug) Debug.debug(debug, "Changed rulecount for "+tie+" to "+semiring.internalToPrint(rulecount)+
							"; denomcount to "+semiring.internalToPrint(denomcount));
				}
				// what if we make this or instead of and?
				if (semiring.betteroreq(semiring.ZERO(), rulecount) && semiring.betteroreq(semiring.ZERO(), denomcount)) {
					//		if (rulecount == semiring.ZERO() || denomcount == semiring.ZERO()) {
					if (debug) Debug.debug(debug, "No counts for any member; continuing");
					continue;
				}
				// figure out the value
				double tieweight = semiring.times(rulecount, semiring.inverse(denomcount));
				//		    Debug.debug(true, rulecount+" / "+denomcount+" = "+tieweight);
				if (debug) Debug.debug(debug, "Tie weight is "+semiring.internalToPrint(tieweight));
				// now go back and set all the rules, increase all the scaling factors, and determine how much weight to remove from the Z
				tieit = trs.getTiedRules(tie).iterator();
				while (tieit.hasNext()) {
					TransducerRule tr = (TransducerRule)tieit.next();
					Symbol lhs;
					if (ISCONDITIONAL)
						lhs = tr.getLHSCondSym();
					else
						lhs = tr.getLHSSym();
					Double reductionobj = counts.get(tr);
					double reduction;
					if (reductionobj == null)
						reduction = semiring.ZERO();
					else
						reduction = reductionobj.doubleValue();
					if (Zsubtracts.containsKey(lhs)) {
						Zsubtracts.put(lhs, semiring.plus(Zsubtracts.get(lhs), reduction));
					}
					else {
						Zsubtracts.put(lhs, reduction);
					}
					if (debug) Debug.debug(debug, "Amount subtracted for Z of "+lhs.toString()+" is "+semiring.internalToPrint(Zsubtracts.get(lhs)));
					Debug.debug(debug, "Setting tied rule "+tr.toString()+" to "+tieweight);
					tr.setWeight(tieweight);

					// 		    if (!trs.getRules().contains(tr)) {
					// 			Debug.debug(true, tr.toString()+" not found in main rules after changing weight normally!");
					// 			Debug.debug(true, trs.toString());
					// 			System.exit(1);
					// 		    }

					if (scaling.containsKey(lhs))
						scaling.put(lhs, semiring.plus(scaling.get(lhs), tieweight));
					else
						scaling.put(lhs, tieweight);
					if (debug) Debug.debug(debug, "Scale for "+lhs.toString()+" is "+semiring.internalToPrint(scaling.get(lhs)));
				}
			}
			// now set Z appropriately, subtracting the stored value, as long as there's something to subtract
			TObjectDoubleIterator zit = Zsubtracts.iterator();
			while (zit.hasNext()) {
				zit.advance();
				Symbol lhs = (Symbol)zit.key();
				Double oldZ = Z.get(lhs);
				Double reduction = new Double(zit.value());
				if (oldZ == null)
					continue;
				if ( debug && reduction == null) {
					Debug.debug(debug, "Warning: reduction is null when subtracting value for "+lhs.toString()+" so not subtracting");
					continue;
				}
				else {
					Double newZ = new Double(semiring.minus(oldZ.doubleValue(), reduction.doubleValue()));

					if (debug) Debug.debug(debug, "Reducing Z of "+lhs.toString()+" by "+semiring.internalToPrint(reduction.doubleValue())+
							"; from "+semiring.internalToPrint(oldZ.doubleValue())+" to "+semiring.internalToPrint(newZ.doubleValue()));
					if (semiring.betteroreq(semiring.ZERO(), newZ.doubleValue())) {
						//			Debug.debug(true, "Subtracted "+reduction.doubleValue()+" from "+oldZ.doubleValue()+
						//				    " to make a Z value for "+lhs.toString()+" less than ZERO: "+newZ.doubleValue()+"; setting to ZERO ("+semiring.ZERO()+")");
						newZ = new Double(semiring.ZERO());
					}
					Z.put(lhs, newZ);
				}
			}

			Iterator trsruleit = trs.getRules().iterator();
			while (trsruleit.hasNext()) {
				TransducerRule tr = (TransducerRule)trsruleit.next();
				Symbol lhs;
				if (ISCONDITIONAL)
					lhs = tr.getLHSCondSym();
				else
					lhs = tr.getLHSSym();
				if (tr.getTie() > 0) {
					if (debug) Debug.debug(debug, "skipping "+tr.toString()+" because it's tied");
					continue;
				}
				// TODO: make sure these rules are reflected in the rules that are stored as indices for count
				if (debug) Debug.debug(debug, "Changing "+tr.toString()+" to ");
				Double rulecount = counts.get(tr);
				if (rulecount == null || semiring.betteroreq(semiring.ZERO(), rulecount.doubleValue())) {
					tr.setWeight(semiring.ZERO());
					// 		    if (!trs.getRules().contains(tr)) {
					// 			Debug.debug(true, tr.toString()+"not found in main rules after changing weight to zero!");
					// 			System.exit(1);
					// 		    }

					if (debug) Debug.debug(debug, semiring.ZERO()+" (null rule)");
				}
				else {
					double scale = semiring.ONE();
					if (scaling.containsKey(lhs)) {
						//			double oldscale = scale;
						scale = semiring.minus(scale, scaling.get(lhs));
						if (debug) Debug.debug(debug, "scaling "+lhs.toString()+" to "+scale);
						if (semiring.betteroreq(semiring.ZERO(), scale)) {
							//			    Debug.debug(true, "Subtracted "+scaling.get(lhs)+" from "+oldscale+" to get a scale for "+
							//					lhs.toString()+" less than ZERO; "+scale+"; setting to ZERO("+semiring.ZERO()+")");
							scale = semiring.ZERO();
						}
					}
					// nip potential division errors in the bud: if scale or Z is zero, the weight is zero
					if (semiring.betteroreq(semiring.ZERO(), scale) || semiring.betteroreq(semiring.ZERO(), Z.get(lhs).doubleValue())) {
						Debug.debug(debug, "Setting weight of "+tr.toString()+" to ZERO because either scale or Z is ZERO");
						tr.setWeight(semiring.ZERO());
						// 			if (!trs.getRules().contains(tr)) {
						// 			    Debug.debug(true, tr.toString()+"not found in main rules after changing weight to zero based on scale, Z!");
						// 			    System.exit(1);
						// 			}

					}
					// sometimes division seems to not work quite so right, so make sure x/x = 1
					else if (rulecount.doubleValue() == Z.get(lhs).doubleValue())
						tr.setWeight(scale);
					else
						tr.setWeight(semiring.times(scale, semiring.times(rulecount.doubleValue(), semiring.inverse(Z.get(lhs).doubleValue()))));
					// 		    if (!trs.getRules().contains(tr)) {
					// 			Debug.debug(true, tr.toString()+"not found in main rules after changing weight via scaling!");
					// 			Debug.debug(true, trs.toString());
					// 			System.exit(1);
					// 		    }

					//		    if (scale == semiring.ZERO())
					//			Debug.debug(true, "WARNING: scale of ZERO for "+lhs.toString()+" trying to set "+tr.toString());
					// 		    if (semiring.better(tr.getWeight(), semiring.ONE())) {
					// 			Debug.debug(true, "rule with value greater than ONE ("+semiring.ONE()+"):"+tr.toString()+" : "+scale+" * "+rulecount.doubleValue()+" / "+((Double)Z.get(lhs)).doubleValue()+" = "+tr.getWeight());
					// 			System.exit(0);
					// 		    }
					if (scaling.containsKey(lhs))
						Debug.debug(debug, tr.toString()+" : "+scale+" * "+rulecount.doubleValue()+" / "+Z.get(lhs).doubleValue()+" = "+tr.getWeight());
				}
			} 
			delta = (L - lastL)/Math.abs(L);
			if (debug) Debug.debug(debug, "L is "+L+", lastL is "+lastL);
			if (debug) Debug.debug(debug, "Iteration changed L by "+delta);
			lastL = L;
			itno++;
			Debug.debug(debug, "At end of iteration "+(itno-1)+", rule set is:\n"+trs.toString());
			try {
				setois.close();
				drsois.close();
			}
			catch (IOException e) {
				System.err.println("Problem in training while trying to close the info files: "+e.getMessage());
				System.exit(1);
			} 
		}
		if (debug) Debug.debug(debug, "Final rule set is:\n"+trs.toString());
		// output 1-best of the derivation forest
		if (getOneBests) {
			ObjectInputStream drsois = null;
			try {
				drsois = new ObjectInputStream(new FileInputStream(derivFile));
			}
			catch (IOException e) {
				System.err.println("Problem in training while trying to open the info files: "+e.getMessage());
				System.exit(1);
			} 
			//	    Debug.prettyDebug("Set size is "+setSize);
			for (int setSizeCounter = 0; setSizeCounter < setSize; setSizeCounter++) {
				// read in the training file and derivation forest
				DerivationRuleSet drs = null;
				try {
					drs = (DerivationRuleSet)drsois.readObject();
					if (drs == null)
						continue;
					drs.revive(intrs, semiring);
					drs.pruneUselessAndZero();
				}
				catch (IOException e) {
					System.err.println("Problem while trying to read in a derivation and tree pair "+e.getMessage());
					System.exit(1);
				}
				catch (ClassNotFoundException e) {
					System.err.println("Class-casting problem while trying to read in a derivation and tree pair "+e.getMessage());
					System.exit(1);
				}
				if (drs == null) {
					Debug.prettyDebug("Reading of training example completed, but objects are null; continuing...");
					continue;
				}
//				drs.calculateWeights();

				// output 1-best of the derivation forest
				try {
					DRSKBest dkb = new DRSKBest(drs);
					TreeItem[] oneBestDeriv = dkb.getKBestTrees(1, false);
					for (int i = 0; i < oneBestDeriv.length; i++) {
						Debug.prettyDebug(""+oneBestDeriv[i]);
					}
				}
				catch (UnusualConditionException e) {
					System.err.println("Weird when getting DRSKBest: "+e.getMessage());
					System.exit(1);
				}
			}
			try {
				drsois.close();
			}
			catch (IOException e) {
				System.err.println("Problem in training while trying to close the info files: "+e.getMessage());
				System.exit(1);
			} 
		}		
		return trs;
	}

	// TODO: bring this back once other stuff is settled
	// TODO: make this work for xrs too

	// to test against jon's code, this writes to three files: 1) packed-forest version of the rtg, with everything represented numerically, 
	// 2) normalization groups, again represented numerically, in list of list format, 3) key mapping numbers to rules for human inspection
	// it must do the deriv part of the training code
	//     public static void generateGraehlData(File forestFile, File normGroupFile, 
	// 					  File mapFile, TreeTransducerRuleSet intrs, 
	// 					  Vector treePairs, boolean debug) throws IOException {


	// 	// 1) create all the derivation forests
	// 	Hashtable derivMap = getAllDerivationRuleSets(intrs, treePairs);
	// 	// 2) now write a forest line, norm group line, map line for each drs
	// 	OutputStreamWriter forestWriter = new OutputStreamWriter(new FileOutputStream(forestFile));
	// 	OutputStreamWriter normGroupWriter = new OutputStreamWriter(new FileOutputStream(normGroupFile));
	// 	OutputStreamWriter mapWriter = new OutputStreamWriter(new FileOutputStream(mapFile));
	// 	Iterator tpit = treePairs.iterator();

	// 	while (tpit.hasNext()) {
	// 	    TreePair tp  = (TreePair)tpit.next();
	// 	    if (debug) Debug.debug(debug, "Considering "+tp.toString());
	// 	    DerivationRuleSet drs = (DerivationRuleSet)derivMap.get(tp);
	// 	    if (debug) Debug.debug(debug, "Deriv set is "+drs.toString());
	// 	    String forestString = getForestString(drs, drs.getStartState(), debug);
	// 	    if (debug) Debug.debug(debug, "Forest string is "+forestString);
	// 	    forestWriter.write(forestString+"\n");
	// 	    // inefficient but that's not really the point of this method
	// 	    Hashtable normGroups = new Hashtable();
	// 	    Iterator drit = drs.getRules().iterator();
	// 	    while (drit.hasNext()) {
	// 		DerivationRule dr = (DerivationRule)drit.next();
	// 		Symbol cond = dr.getLabel().getLHS();
	// 		if (!normGroups.containsKey(cond))
	// 		    normGroups.put(cond, new HashSet());
	// 		((HashSet)normGroups.get(cond)).add(dr.getRuleIndex());
	// 	    }
	// 	    normGroupWriter.write("(");
	// 	    Iterator ngit = normGroups.keySet().iterator();
	// 	    while (ngit.hasNext()) {
	// 		Symbol key = (Symbol)ngit.next();
	// 		HashSet items = (HashSet)normGroups.get(key);
	// 		normGroupWriter.write("(");
	// 		Iterator itit = items.iterator();
	// 		while (itit.hasNext()) {
	// 		    String item = (String)itit.next();
	// 		    normGroupWriter.write(item+" ");
	// 		}
	// 		normGroupWriter.write(")");
	// 	    }
	// 	    normGroupWriter.write(")\n");
	// 	}
	// 	// at this point the transducer should have indices set
	// 	Iterator trit = intrs.getIndexedRules().iterator();
	// 	while (trit.hasNext()) {
	// 	    TreeTransducerRule tr = (TreeTransducerRule)trit.next();
	// 	    mapWriter.write(tr.getIndex()+" : "+tr.toString()+"\n");
	// 	}
	// 	forestWriter.close();
	// 	normGroupWriter.close();
	// 	mapWriter.close();
	//     }


	//     // recursive call parented by generateGraehlData for getting the forest of a state
	//     // 1) if the map has a reference for the state, just return that.
	//     //    otherwise create a new OR node
	//     public static String getForestString(DerivationRuleSet drs, Symbol state) {
	// 	return getForestString(drs, state, false);
	//     }
	//     public static String getForestString(DerivationRuleSet drs, Symbol state, boolean debug) {
	// 	if (drs.hasStateIndex(state)) {
	// 	    if (debug) Debug.debug(debug, "index for "+state.toString()+" already set");
	// 	    return (String)drs.getStateIndex(state);
	// 	}
	// 	else {
	// 	    String stateRef = "#"+(drs.getNewStateIndex(state));
	// 	    if (debug) Debug.debug(debug, "created state index "+stateRef);
	// 	    StringBuffer retVal = new StringBuffer(stateRef+"(OR ");
	// 	    HashSet currRules = drs.getRulesByLHS(state);
	// 	    if (currRules == null) {
	// 		if (debug) Debug.debug(debug, "rules for "+state.toString()+" are null; returning "+retVal.toString());
	// 		return retVal.toString();
	// 	    }
	// 	    Iterator crit = currRules.iterator();
	// 	    while (crit.hasNext()) {
	// 		DerivationRule dr = (DerivationRule)crit.next();
	// 		if (debug) Debug.debug(debug, "Considering derivation rule "+dr.toString());
	// 		// if dr has state children numeric label gets put in paren. otherwise it's by itself
	// 		Vector kids = drs.getLeafChildren(dr);
	// 		if (debug) Debug.debug(debug, kids.size()+" kids");
	// 		if (kids.size() > 1) {
	// 		    retVal.append("("+dr.getRuleIndex()+" ");
	// 		    if (debug) Debug.debug(debug, "Rule index is "+dr.getRuleIndex());
	// 		    Iterator kit = kids.iterator();
	// 		    while (kit.hasNext()) {
	// 			Symbol kid = (Symbol)kit.next();
	// 			if (debug) Debug.debug(debug, "About to get forest string for "+kid.toString());
	// 			retVal.append(getForestString(drs, kid, debug)+" ");
	// 			if (debug) Debug.debug(debug, "forest string to "+retVal.toString());
	// 		    }
	// 		    retVal.append(")");
	// 		}
	// 		else {
	// 		    if (debug) Debug.debug(debug, "(no kids) Rule index is "+dr.getRuleIndex());
	// 		    retVal.append(dr.getRuleIndex());
	// 		}
	// 	    }
	// 	    retVal.append(")");
	// 	    return retVal.toString();
	// 	}
	//     }

	// get all the rule sets given files pointing to tree pairs, write them to files, and return pointers to the files.
	//  Used by train and to generate graehl data
	static public File getAllDerivationRuleSets(TransducerRuleSet intrs, int setSize, File setFile, String derivFileName, boolean isTree, int timeLevel) {
		boolean debug = false;
		Hashtable derivMap = new Hashtable();
		if (debug) Debug.debug(debug, "Calculating derivation rule sets for "+setSize+" pairs");
		int goodPairs = 0;
		int badPairs = 0;
		ObjectInputStream tsois = null;
		ObjectOutputStream dsoos = null;
		File of = null;
		try {
			tsois = new ObjectInputStream(new FileInputStream(setFile));
			if (derivFileName == null) {
				of = File.createTempFile("drs", "tmp");
				of.deleteOnExit();
			}
			else
				of = new File(derivFileName);
			dsoos = new ObjectOutputStream(new FileOutputStream(of));
		}
		catch (IOException e) {
			System.err.println("IO Problem preparing to get all derivation rule sets: "+e.getMessage());
			System.exit(1);
		}
		for (int i = 0; i < setSize; i++) {
			TreePair tp = null;
			//	    Date preReadTime = new Date();

			try {
				tp = (TreePair)tsois.readObject();
			}
			catch (IOException e) {
				System.err.println("Problem while trying to get derivation forest of training example: "+
						(i+1)+" of "+setSize+": "+e.getMessage());
				System.exit(1);
			}
			catch (ClassNotFoundException e) {
				System.err.println("Class-casting problem while trying to get derivation forest of training example: "+
						(i+1)+" of "+setSize+": "+e.getMessage());
				System.exit(1);
			}

			//    Date postReadTime = new Date();
			//Debug.dbtime(timeLevel, 3, preReadTime, postReadTime, "read in training object "+tp.toString());

			DerivationRuleSet drs = null;
			if (debug) Debug.debug(debug, "Attempting to derive "+tp.toString());
			if (isTree) {
				Date preDerivTime = new Date();
				drs = deriv((TreeTransducerRuleSet)intrs, tp.in, ((TreeTreePair)tp).out, timeLevel);
				if (debug) Debug.debug(debug, "Done deriving");		

				Date postDerivTime = new Date();
				Debug.dbtime(timeLevel, 3, preDerivTime, postDerivTime, "calculate derivation rule set");
				if (drs == null || drs.getNumRules() == 0) {
					badPairs++;
					Debug.prettyDebug("Warning: Tree tree pair "+tp.in.toString()+", "+((TreeTreePair)tp).out.toString()+" could not be explained by transducer. Removing from training set");
					Debug.prettyDebug("So far: "+badPairs+" bad sentence pairs, and "+goodPairs+" good sentence pairs");
				}
				else {
					goodPairs++;
					Date prePruneTime = new Date();
					drs.pruneUseless();
					Date postPruneTime = new Date();
					Debug.dbtime(timeLevel, 3, prePruneTime, postPruneTime, "prune drs");
					if (debug) Debug.debug(debug, "Done pruning");
					Date preCalcTime = new Date();
					drs.calculateWeights();
					Date postCalcTime = new Date();
					Debug.dbtime(timeLevel, 3, preCalcTime, postCalcTime, "calculate weights");
					if (debug) Debug.debug(debug, "Derivation Rule Set for "+tp.toString()+" has "+drs.getNumRules()+" rules");
					//if (debug) Debug.debug(debug, drs.toString()+"\n");
				}
				Date preWriteTime = new Date();
				try {
					if (debug) Debug.debug(debug, "About to write file...");
					dsoos.writeObject(drs);
					dsoos.reset();
					if (debug) Debug.debug(debug, "Done writing file");
				}
				catch (IOException e) {
					System.err.println("Problem while trying to write derivation of "+
							tp.toString()+" to file: "+e.getMessage());
					System.exit(1);
				}
				Date postWriteTime = new Date();
				Debug.dbtime(timeLevel, 3, preWriteTime, postWriteTime, "write drs");
			}
			else {
				Date preDerivTime = new Date();
				drs = deriv((StringTransducerRuleSet)intrs, tp.in, ((TreeStringPair)tp).out);
				if (debug) Debug.debug(debug, "Done deriving");		

				Date postDerivTime = new Date();
				Debug.dbtime(timeLevel, 3, preDerivTime, postDerivTime, "calculate derivation rule set");
				if (drs == null || drs.getNumRules() == 0) {
					badPairs++;
					Debug.prettyDebug("Warning: Tree string pair "+tp.in.toString()+", "+((TreeStringPair)tp).out.toString()+" could not be explained by transducer. Removing from training set");
					Debug.prettyDebug("So far: "+badPairs+" bad sentence pairs, and "+goodPairs+" good sentence pairs");
				}
				else {
					goodPairs++;
					Date prePruneTime = new Date();
					drs.pruneUseless();
					if (debug) Debug.debug(debug, "Done pruning");

					Date postPruneTime = new Date();
					Debug.dbtime(timeLevel, 3, prePruneTime, postPruneTime, "prune drs");
					Date preCalcTime = new Date();
					drs.calculateWeights();
					Date postCalcTime = new Date();
					Debug.dbtime(timeLevel, 3, preCalcTime, postCalcTime, "calculate weights");
					if (debug) Debug.debug(debug, "Done calculating weights");
					if (debug) Debug.debug(debug, "Derivation Rule Set for "+tp.toString()+" has "+drs.getNumRules()+" rules");
					//		    if (debug) Debug.debug(debug, drs.toString()+"\n");
				}
				try {
					dsoos.writeObject(drs);
					dsoos.reset();
					if (debug) Debug.debug(debug, "Done writing file");
				}
				catch (IOException e) {
					System.err.println("Problem while trying to write derivation of "+
							tp.toString()+" to file: "+e.getMessage());
					System.exit(1);
				}
			}
			drs = null;
			// 	    if (debug) Debug.debug(debug, "Memo now has "+memo.size()+" entries");
			// 	    long afterwrite = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
			// 	    if (debug) Debug.debug(debug, "after write: heap at "+afterwrite);
			// TODO: if not done already, prune useless deriv weights
		}
		try {
			tsois.close();
			dsoos.close();
		}
		catch (IOException e) {
			System.err.println("Problem in training while trying to open the info files: "+e.getMessage());
			System.exit(1);
		} 
		return of;
	}


	// jon's deriv algorithm - build one drs given a trs and a pair

	static public DerivationRuleSet deriv(TreeTransducerRuleSet trs, TreeItem i, TreeItem o, int timeLevel) {
		boolean debug = false;
		Date preSetupTime = new Date();
		TIntHashSet states = new TIntHashSet();
		HashSet<DerivationRule> rules = new HashSet<DerivationRule>();
		// memoize production decisions
		Hashtable<StateTreeTree, Boolean> memo = new Hashtable<StateTreeTree, Boolean>();
		// track unique pairs in this derivation and represent them as ints in the drs
		TObjectIntHashMap pairMap = new TObjectIntHashMap();
		StateTreeTree.resetNextIndex();
		StateTreeTree startState = new StateTreeTree(trs.getStartState(), i, 0, i.getLeaves().length, o, 0, o.getLeaves().length);
		pairMap.put(startState, StateTreeTree.getNextIndex());
		Date postSetupTime = new Date();
		Debug.dbtime(timeLevel, 4, preSetupTime, postSetupTime, "setup for deriv");
		// create an empty drs to begin with, and replace it if a derivation comes up
		DerivationRuleSet drs = new DerivationRuleSet(pairMap.get(startState), states, rules, trs.getSemiring());
		Date preProduceTime = new Date();
		if (produce(trs, startState, states, rules, memo, pairMap, timeLevel, 0, debug)) {
			Date postProduceTime = new Date();
			if (debug) Debug.debug(debug, "produce was successful");
			Debug.dbtime(timeLevel, 4, preProduceTime, postProduceTime, "do produce");
			Date preDRSTime = new Date();
			drs = new DerivationRuleSet(pairMap.get(startState), states, rules, trs.getSemiring());
			Date postDRSTime = new Date();
			Debug.dbtime(timeLevel, 4, preDRSTime, postDRSTime, "make the drs");
		}
		else {
			// zero it out so we don't confuse this for success
			if (debug) Debug.debug(debug, "produce NOT successful");
			drs = null;
		}
		return drs;
	}


	// jon's deriv algorithm for xrs - build one drs given a trs and a pair

	static public DerivationRuleSet deriv(StringTransducerRuleSet trs, TreeItem i, TrainingString o) {
		boolean debug = false;
		TIntHashSet states = new TIntHashSet();
		HashSet<DerivationRule> rules = new HashSet<DerivationRule>();
		// memoize production decisions
		Hashtable<VariableCluster, Boolean> memo = new Hashtable<VariableCluster, Boolean>();
		// track unique pairs in this derivation and represent them as ints in the drs
		// map variablecluster -> int
		TObjectIntHashMap pairMap = new TObjectIntHashMap();
		VariableCluster.resetNextIndex();
		if (debug) Debug.debug(debug, "getting derivation from "+i.toString()+" to "+o.toString()+":"+o.getStartIndex()+", "+o.getEndIndex());
		VariableCluster startState = new VariableCluster(trs.getStartState(), 
				i, 0, i.getLeaves().length, 
				o, o.getStartIndex(), o.getEndIndex());
		pairMap.put(startState, VariableCluster.getNextIndex());
		if (debug) Debug.debug(debug, "Associating "+startState.toString()+" with "+pairMap.get(startState));
		// create an empty drs to begin with, and replace it if a derivation comes up
		DerivationRuleSet drs = new DerivationRuleSet(pairMap.get(startState), states, rules, trs.getSemiring());

		Date preProduce = new Date();
		if (produce(trs, startState, states, rules, memo, pairMap, debug, 0)) {
			//	    Debug.dbtime(3, preProduce, ""+(i.getTreeLeaves().length+o.getSize()));
			//	    Debug.dbtime(2, preProduce, "calculated derviation forest");
			//	    Debug.debug(true, states.size()+"\t"+rules.size()+"\t"+memo.size()+"\t"+pairMap.size());
			drs = new DerivationRuleSet(pairMap.get(startState), states, rules, trs.getSemiring());
			if (debug) Debug.debug(debug, "produce successful");
		}
		else {
			// zero it out so we don't confuse this for success
			if (debug) Debug.debug(debug, "produce NOT successful");
			drs = null;
		}
		return drs;
	}



	// jon's produce algorithm - return true if something was created. Also add to the hash sets passed along
	// build DerivationRules, which are lhs states, rhs transducer rule, and a list of rhs states
	// as it goes, add the states too

	// memoization: memo maps qio -> Boolean
	static public boolean produce(TreeTransducerRuleSet trs, StateTreeTree qio, TIntHashSet stateSet, HashSet<DerivationRule> ruleSet,
			Hashtable<StateTreeTree, Boolean> memo, TObjectIntHashMap pairMap, int timeLevel, int indent) {
		return produce(trs, qio, stateSet, ruleSet, memo, pairMap, timeLevel, indent, false);
	}

	static public boolean produce(TreeTransducerRuleSet trs, StateTreeTree qio, TIntHashSet stateSet, HashSet<DerivationRule> ruleSet,
			Hashtable<StateTreeTree, Boolean> memo, TObjectIntHashMap pairMap, int timeLevel, int indent,  boolean debug) {
		// memoization check
		Date preMemoTime = new Date();
		if (memo.containsKey(qio)) {
			Boolean entry = memo.get(qio);
			if (debug) Debug.debug(debug, indent, ((entry.booleanValue()) ? "Success: " : "Fail: ")+qio.toString()+" (memo) ");
			Date postMemoTime = new Date();
			Debug.dbtime(timeLevel, 4, preMemoTime, postMemoTime, "check memoization and succeed");
			return entry.booleanValue();

		}
		Date postMemoTime = new Date();
		Debug.dbtime(timeLevel, 4, preMemoTime, postMemoTime, "check memoization and fail");
		// potentially matching rules
		boolean foundMatch = false;
		Symbol q = qio.state();
		TreeItem i = qio.in();
		TreeItem o = qio.out();
		if (debug) Debug.debug(debug, indent, "Attempting to produce: "+qio.toString());
		ArrayList<TreeTransducerRule> trsrules = null;
		Date preGetRules = new Date();
		try {
			trsrules = trs.getTrainingRules(q, i, o);
		}
		catch (UnexpectedCaseException e) {
			System.err.println("Unexpected when getting training rules for "+qio.toString()+"; "+e.getMessage());
			return false;
		}
		Date postGetRules = new Date();
		Debug.dbtime(timeLevel, 4, preGetRules, postGetRules, "get "+trsrules.size()+" training rules");

		for (TreeTransducerRule tr : trsrules) {
			if (debug) Debug.debug(debug, indent, "Attempting: "+qio.toString()+" with "+tr.toString());
			// get vector of state, intree, outtree triples
			Date preGetPaths = new Date();
			Vector pairV = tr.getPaths(qio);
			Date postGetPaths = new Date();
			Debug.dbtime(timeLevel, 5, preGetPaths, postGetPaths, "get "+pairV.size()+" training paths");
			Vector v = (Vector)pairV.get(0);
			StringBuffer alignStr = (StringBuffer)pairV.get(1);
			// index version of v. for derivationrule
			int[] sv = new int[v.size()];
			// must be able to produce in children to continue;
			Iterator vit = v.iterator();
			boolean allOkay = true;
			int svidx = 0;
			while (vit.hasNext()) {
				StateTreeTree stt = (StateTreeTree)vit.next();
				if (!produce(trs, stt, stateSet, ruleSet, memo, pairMap, timeLevel, (indent+1), debug)) {
					if (debug) Debug.debug(debug, indent, "Can't produce in "+stt.toString());
					allOkay = false;
					break;
				}
				if (!pairMap.containsKey(stt))
					pairMap.put(stt, StateTreeTree.getNextIndex());
				sv[svidx++] = pairMap.get(stt);
			}
			if (!allOkay)
				continue;
			if (debug) Debug.debug(debug, indent, "Successfully produced "+qio.toString()+" with "+tr.toString());
			Date preCreate = new Date();
			// create the derivation rule and states
			if (!pairMap.containsKey(qio))
				pairMap.put(qio, StateTreeTree.getNextIndex());
			int qioidx = pairMap.get(qio);
			DerivationRule dr = new DerivationRule(qioidx, tr, sv, alignStr, trs.getSemiring());
			if (debug) Debug.debug(debug, indent, "Rule created is "+dr.toString()+" with alignstring "+alignStr);
			ruleSet.add(dr);
			stateSet.add(qioidx);
			for (int j = 0; j < sv.length; j++)
				stateSet.add(sv[j]);
			foundMatch = true;
			Date postCreate = new Date();
			Debug.dbtime(timeLevel, 5, preCreate, postCreate, "create rule");
		}
		// archive the decision
		memo.put(qio, Boolean.valueOf(foundMatch));
		return foundMatch;
	}



	// jon's produce algorithm for xrs - return true if something was created. Also add to the hash sets passed along
	// build DerivationRules, which are lhs states, rhs string transducer rule, and a list of rhs states
	// as it goes, add the states too

	// memoization: memo maps qio -> Boolean.
	// pairMap: maps qio hashcode (int) -> int. Why hashcode? Because some clusters 
	// have to look like qio's too and that's an easy way to
	// do it.
	static public boolean produce(StringTransducerRuleSet trs, VariableCluster qio, TIntHashSet stateSet, HashSet<DerivationRule> ruleSet, 
			Hashtable<VariableCluster, Boolean> memo,  TObjectIntHashMap pairMap) {
		return produce(trs, qio, stateSet, ruleSet, memo,  pairMap, false, 0);
	}


	static public boolean produce(StringTransducerRuleSet trs, VariableCluster qio, TIntHashSet stateSet, HashSet<DerivationRule> ruleSet, 
			Hashtable<VariableCluster, Boolean> memo, TObjectIntHashMap pairMap, boolean debug, int indent) {
		// memoization check
		if (memo.containsKey(qio)) {
			Boolean entry = memo.get(qio);
			//	    if (debug) Debug.debug(debug, indent, entry.booleanValue()+":"+qio.toString()+" (memo) ");
			return entry.booleanValue();
		}
		//	if (debug) Debug.debug(debug, indent, "deferring the decision by assuming true for "+qio.toString());
		memo.put(qio, Boolean.TRUE);
		// potentially matching rules
		boolean foundMatch = false;
		Symbol q = qio.state();
		TreeItem i = qio.in();
		TrainingString o = qio.out();
		//	if (debug) Debug.debug(debug, indent, "Attempting: "+qio.toString());
		ArrayList<StringTransducerRule> trsrules = null;
		Date preGetRules = new Date();
		try {
			trsrules = trs.getTrainingRules(q, i, o);
		}
		catch (UnexpectedCaseException e) {
			System.err.println("Unexpected when getting training rules for "+qio.toString()+"; "+e.getMessage());
			return false;
		}
		//	Debug.dbtime(3, preGetRules, "get training rules");


		for (StringTransducerRule tr : trsrules) {

			//   if (debug) Debug.debug(debug, indent, "Attempting: "+qio.toString()+" with "+tr.toString());

			// it's possible this rule could help to produce the ostring from the itree a number 
			// of ways -- these are the various "p"s from the k/g journal paper.
			// alignments is a vector of sequences of consecutive variables (nonterminals) 
			// paired with literals from a training string
			// .(a vector of vectors). 
			// Each "cluster" variables needs to be processed
			// to see if we can accept the alignment
			//  Date preGetAlignments = new Date();
			Vector alignmentsPair = tr.getAllAlignments(qio);
			//	    Debug.dbtime(3, preGetAlignments, "get alignments for "+qio.toString());
			Vector alignments = (Vector)alignmentsPair.get(0);
			Vector alignmentsStrings = (Vector)alignmentsPair.get(1);
			Iterator alit = alignments.iterator();
			Iterator alstrit = alignmentsStrings.iterator();
			while (alit.hasNext()) {
				Vector v = (Vector)alit.next();
				StringBuffer altext = (StringBuffer)alstrit.next();
				// index version of v. for derivationrule
				int[] sv = new int[v.size()];
				Iterator vit = v.iterator();
				// 		if (debug) {
				// 		    System.err.print("Checking alignment [");
				// 		    while (vit.hasNext()) {
				// 			VariableCluster c = (VariableCluster)vit.next();
				// 			System.err.print("("+c.toString()+") ");
				// 		    }
				// 		    Debug.debug(true, "]");
				// 		    vit = v.iterator();
				// 		}

				boolean allOkay = true;
				int svidx = 0;
				// check each item of the alignment vector for a correct derivation. If a span is wrong, the whole
				// alignment is wrong. If a span is undecided, the whole alignment is undecided and must be deferred
				while (vit.hasNext()) {
					VariableCluster c = (VariableCluster)vit.next();
					if (!spanToSpan(trs, c, stateSet, ruleSet, memo, pairMap, debug, indent+1)) {
						//			if (debug) Debug.debug(debug, indent, "Nonterminal Cluster "+c.toString()+" could not be resolved");
						allOkay = false;
						break;
					}
					// otherwise, make an int representation for the cluster
					else {
						if (!pairMap.containsKey(c)) {
							pairMap.put(c, VariableCluster.getNextIndex());
							//			    if (debug) Debug.debug(debug, indent, "Associating "+c.hashCode()+" with "+pairMap.get(c.hashCode()));
						}
						sv[svidx++] = pairMap.get(c);
					}
				}
				// check for failure at some point. if so, no need to make the rule
				if (!allOkay) {
					//		    if (debug) Debug.debug(debug, indent, "Fail: "+qio.toString()+" with "+tr.toString());
					continue;
				}
				// since we're creating at least one rule, we can declare this state true
				foundMatch = true;		
				//		if (debug) Debug.debug(debug, indent, "Success: "+qio.toString()+" with "+tr.toString());
				// create the derivation rule and states

				// if the head state has no int representation, create it 
				if (!pairMap.containsKey(qio)) {
					pairMap.put(qio, VariableCluster.getNextIndex());
					//		    if (debug) Debug.debug(debug, indent, "Associating "+qio.hashCode()+" with "+pairMap.get(qio.hashCode()));
				}
				int qioidx = pairMap.get(qio);

				// sanity check
				//		if (debug) Debug.debug(debug, indent, "About to create rule with head "+qio.toString()+" ("+qioidx+")");
				if (debug) {
					for (int j = 0; j< v.size(); j++) {
						VariableCluster clust = (VariableCluster)v.get(j);
						//			if (debug) Debug.debug(debug, indent, "Child "+j+" = "+clust.toString()+" ("+sv[j]+")");
					}
				}

				// actually build the rule
				DerivationRule dr = new DerivationRule(qioidx, tr, sv, altext, trs.getSemiring());
				if (debug) Debug.debug(debug, indent, "Created new rule "+dr.toString()+" in produce"+" with alignment string "+altext.toString());

				// archive the rule
				ruleSet.add(dr);
				stateSet.add(qioidx);
				for (int j = 0; j < sv.length; j++)
					stateSet.add(sv[j]);

			}
		}
		// archive the decision (it was already set to true, so no need to re-update unless it's now false)
		//	if (debug) Debug.debug(debug, indent, "deciding "+qio.toString()+": "+foundMatch);
		if (!foundMatch)
			memo.put(qio, Boolean.valueOf(foundMatch));
		return foundMatch;
	}


	// span to span - historical to the km journal paper. this is similar to 
	// produce but we have to do dynamic programming since
	// there may be more than one nonterminal in the nonterminal cluster. 
	// In such a case, we create a virtual node in the rule set.

	// memoization: memo maps c -> Boolean
	// pairMap: maps c hash (int) -> int. See produce for why c hash and not c itself.

	static public boolean spanToSpan(StringTransducerRuleSet trs, VariableCluster c, TIntHashSet stateSet, HashSet<DerivationRule> ruleSet, 
			Hashtable<VariableCluster, Boolean> memo, TObjectIntHashMap pairMap, boolean debug, int indent) {
		// one non-terminal case - go back to produce
		if (c.numVariables() < 2)
			return produce(trs, new VariableCluster(c.getVariable(0).getState(), 
					c.getVariable(0).getTree(), c.getVariable(0).getStart(), c.getVariable(0).getEnd(),
					c.getString(), c.getString().getStartIndex(), c.getString().getEndIndex()), 
					stateSet, ruleSet, memo, pairMap, debug, indent);

		// memoization check
		if (memo.containsKey(c)) {
			Boolean entry = memo.get(c);
			//	    if (debug) Debug.debug(debug, indent, entry.booleanValue()+": "+c.toString()+" (memo) ");
			return entry.booleanValue();
		}
		//	if (debug) Debug.debug(debug, indent, "deferring the decision by deciding true for "+c.toString());
		memo.put(c, Boolean.TRUE);
		//	if (debug) Debug.debug(debug, indent, "Attempting: "+c.toString());
		boolean foundMatch = false;
		// normal epsilon case - binarize, but string is always epsilon
		if (c.getString().isEpsilon()) {
			// produce on the first nonterminal the chosen subspan
			VariableCluster leftSide =  new VariableCluster(c.getVariable(0), c.getString());
			//	    if (debug) Debug.debug(debug, "Epsilon spantospan: trying to match "+leftSide.toString()+"...");
			// if left side not bad, try to get right
			if (produce(trs, leftSide, stateSet, ruleSet, memo, pairMap, debug, indent+1)) {
				VariableCluster rightSide = c.getSubCluster(0);
				//		if (debug) Debug.debug(debug, "Epsilon spantospan: ...and now trying the remaining "+rightSide.toString());

				// recurse on the remainder (there must be some - no one-state clusters get to here
				if (spanToSpan(trs, rightSide, stateSet, ruleSet, memo, pairMap, debug, indent+1)) {
					if (debug) Debug.debug(debug, indent, "Success: "+c.toString()+"["+c.hashCode()+"] "+" with division ["+leftSide.toString()+"["+leftSide.hashCode()+"] "+", "+rightSide.toString()+"["+c.hashCode()+"] "+"]");
					// this cluster alignment is okay. 
					// build a virtual rule, adding each element to pairMap if necessary
					if (!pairMap.containsKey(c)) {
						pairMap.put(c, VariableCluster.getNextIndex());
						//			if (debug) Debug.debug(debug, "Associating "+c.hashCode()+" with "+pairMap.get(c.hashCode()));
					}
					if (!pairMap.containsKey(leftSide)) {
						pairMap.put(leftSide, VariableCluster.getNextIndex());
						//			if (debug) Debug.debug(debug, "Associating "+leftSide.hashCode()+" with "+pairMap.get(leftSide.hashCode()));
					}
					if (!pairMap.containsKey(rightSide)) {
						pairMap.put(rightSide, VariableCluster.getNextIndex());
						//			if (debug) Debug.debug(debug, "Associating "+rightSide.hashCode()+" with "+pairMap.get(rightSide.hashCode()));
					}
					int [] v = {pairMap.get(leftSide), pairMap.get(rightSide)};
					DerivationRule dr = new DerivationRule(pairMap.get(c), (TransducerRule)null, v, trs.getSemiring());
					if (debug) Debug.debug(debug, indent, "Created new rule "+dr.toString()+" in epsilon s2s");
					ruleSet.add(dr);
					stateSet.add(pairMap.get(c));
					stateSet.add(v[0]);
					stateSet.add(v[1]);
					foundMatch = true;
				}
			}
		}
		else {
			// normal case - binarize as follows: decide a dividing point. Try to match the first nonterm to that point and the rest to the rest.
			//	    if (debug) Debug.debug(debug, "String span is "+c.getString().getStartIndex()+", "+c.getString().getEndIndex());
			for (int i = c.getString().getEndIndex(); i >= c.getString().getStartIndex(); i--) {
				// produce on the first nonterminal the chosen subspan
				VariableCluster leftSide =  new VariableCluster(c.getVariable(0), c.getString().getSubString(c.getString().getStartIndex(), i));
				//		if (debug) Debug.debug(debug, "spantospan: trying to match "+leftSide.toString()+"...");
				if (!produce(trs, leftSide, stateSet, ruleSet, memo, pairMap, debug, indent+1))
					continue;
				VariableCluster rightSide = c.getSubCluster(i);
				//		if (debug) Debug.debug(debug, "spantospan: ...and now trying the remaining "+rightSide.toString());
				// recurse on the remainder (there must be some - no one-state clusters get to here)
				if (spanToSpan(trs, rightSide, stateSet, ruleSet, memo, pairMap, debug, indent+1)) {
					//		    if (debug) Debug.debug(debug, indent, "Success: "+c.toString()+" with division ["+leftSide.toString()+", "+rightSide.toString()+"]");
					// build a virtual rule, adding each element to pairMap if necessary
					if (!pairMap.containsKey(c)) {
						pairMap.put(c, VariableCluster.getNextIndex());
						//			if (debug) Debug.debug(debug, "Associating "+c.hashCode()+" with "+pairMap.get(c.hashCode()));
					}
					if (!pairMap.containsKey(leftSide)) {
						pairMap.put(leftSide, VariableCluster.getNextIndex());
						//			if (debug) Debug.debug(debug, "Associating "+leftSide.hashCode()+" with "+pairMap.get(leftSide.hashCode()));
					}
					if (!pairMap.containsKey(rightSide)) {
						pairMap.put(rightSide, VariableCluster.getNextIndex());
						//			if (debug) Debug.debug(debug, "Associating "+rightSide.hashCode()+" with "+pairMap.get(rightSide.hashCode()));
					}
					int [] v = {pairMap.get(leftSide), pairMap.get(rightSide)};
					DerivationRule dr = new DerivationRule(pairMap.get(c), (TransducerRule)null, v, trs.getSemiring());
					if (debug) Debug.debug(debug, indent, "Created new rule "+dr.toString()+" in regular s2s");
					ruleSet.add(dr);
					stateSet.add(pairMap.get(c));
					stateSet.add(v[0]);
					stateSet.add(v[1]);
					foundMatch = true;
				}
			}
		}
		// archive the decision
		// actually only need to if it's false, since we assumed true
		//	if (debug) Debug.debug(debug, indent, "deciding "+c.toString()+": "+foundMatch);
		if (!foundMatch)
			memo.put(c, Boolean.valueOf(foundMatch));
		return foundMatch;
	}

	// test code
	public static void main(String argv[]) {
		try {
			RealSemiring s = new RealSemiring();
			String encoding = "utf-8";

			// first, just create the deriv files and exit
			// 	    TransducerRuleSet trs = new StringTransducerRuleSet(argv[1], encoding, s, false);
			// 	    //	    Debug.debug(true, trs.toString());
			// 	    Vector trainingSet = TransducerTraining.readTreeSet(new File(argv[0]), encoding);
			// 	    Hashtable drsmap = TransducerTraining.getAllDerivationRuleSets(trs, trainingSet);

			// now, just do training.
			// 	    TransducerRuleSet trs = new StringTransducerRuleSet(argv[1], encoding, s, false);
			// 	    int trainingType = FileDifferentiation.differentiateTraining(new File(argv[0]), encoding);
			// 	    boolean isTree = trainingType >= FileDifferentiation.UNK_TREE_TRAINING;
			// 	    boolean isCount = (trainingType % 3 == FileDifferentiation.COUNT_UNK_TRAINING);
			// 	    Vector trainingSet = TransducerTraining.readTreeSet(new File(argv[0]), encoding, isCount, isTree, s);
			// 	    Hashtable table = new Hashtable();
			// 	    String filedir = "/tmp/";
			// 	    for (int i = 0; i < trainingSet.size(); i++) {
			// 		File f = new File(filedir+i);
			// 		table.put(trainingSet.get(i), f);
			// 	    }
			// 	    TransducerRuleSet trainedtrs = TransducerTraining.train(trs, table, 0, 5, false);
			// 	    Debug.debug(true, trainedtrs.toString());

			// 	    OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(argv[2]), encoding);
			// 	    Iterator it = drsmap.keySet().iterator();
			// 	    while (it.hasNext()) {
			// 		TreePair tp = (TreePair)it.next();
			// 		w.write(tp.toString()+"\n");
			// 		w.write(((DerivationRuleSet)drsmap.get(tp)).toString());
			// 	    }
			// 	    w.close();
			//	    TransducerRuleSet newtrs = train(trs, trainingSet, 0, 5, false);
			//	    Debug.debug(true, newtrs.toString());
			// 	    TransducerTraining.generateGraehlData(new File(argv[2]), new File(argv[3]),
			// 						  new File(argv[4]), trs, trainingSet, false);

			// 	    Debug.debug(true, trs.toString());
			// 	    Vector is = Tree.readTreeSet(new File(argv[1]), "utf-8");
			// 	    Vector os = Tree.readTreeSet(new File(argv[2]), "utf-8");
			// 	    Iterator iit = is.iterator();
			// 	    Iterator oit = os.iterator();
			// 	    Vector v = new Vector();
			// 	    while (iit.hasNext() && oit.hasNext()) {
			// 		Tree i = (Tree)iit.next();
			// 		Tree o = (Tree)oit.next();
			// 		Debug.debug(true, i.toString()+", "+o.toString());
			// 		v.add(new TreePair(i, o, 1));
			// 	    }
			// 	    TreeTransducerRuleSet newtrs = train(trs, v, 0, 10, false);
			// 	    Debug.debug(true, newtrs.toString());
			//	    DerivationRuleSet drs = deriv(trs, i, o);
			// experiment: if I modify the weights of the trs that this drs was made from,
			// is the change propagated to the tr hiding in each dr?
			// 	    Debug.debug(true, drs.toString());
			// 	    HashSet trules = trs.getRules();
			// 	    Iterator trit = trules.iterator();
			// 	    while (trit.hasNext()) {
			// 		TransducerRule tr = (TransducerRule)trit.next();
			// 		tr.setWeight(99);
			// 	    }
			// 	    Debug.debug(true, drs.toString());

			// 	    drs.calculateWeights();
		}
		catch (Exception e) {
			System.err.println(e.getMessage());
		}
	}
}
