package edu.isi.tiburon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.util.Date;
import java.util.Vector;


// takes a heap size, k-best, and then a file sequence as follows:
// trees tree-trans* trees
// each of the final trees becomes an identity language model. each of the initial trees becomes
// an identity rtg and decodes through the cascade.

public class GTOTFGrammarExactLMCascade {

	/**
	 * @param args
	 */
	public static void main(String[] argv) {
		boolean runTopDown = true;
		boolean doExhaust = false;
		boolean doClear = true;
		TropicalSemiring semiring = new TropicalSemiring();
		//		Debug.debug(true, Runtime.getRuntime().totalMemory()+" total");
		//		Debug.debug(true, Runtime.getRuntime().freeMemory()+" free");
		//		Debug.debug(true, "Running "+(runTopDown ? "top-down" : "bottom-up"));
		try {
			String charset = "euc-jp";
			int mh = Integer.parseInt(argv[0]);
			// argv1 is not used, but it's left in so the difference between this and TG is minimal
			int k = Integer.parseInt(argv[2]);
			int endPoint = 3;
			Debug.debug(true, "Max heap of "+mh+" and getting "+k+" answers");


			// where the end of the chain of tree transducers is. 
			int startPoint = argv.length-2;
			// load all the tree transducers into a vector from front to back
			Vector<OTFTreeTransducerRuleSet> tts = new Vector<OTFTreeTransducerRuleSet>();
			for (int i = startPoint; i > endPoint; i--) {
				TreeTransducerRuleSet trs = new TreeTransducerRuleSet(argv[i], charset, semiring);
				//			Debug.prettyDebug("Done loading "+argv[i]);
				OTFTreeTransducerRuleSet ottrs = new OTFTreeTransducerRuleSet(trs);
				tts.add(0, ottrs);
			}

			// if batch, read first and last files as trees

			Vector lmTreeVec;
			Vector decodeTreeVec;
			decodeTreeVec = RuleSetTraining.readItemSet(
					new BufferedReader(new InputStreamReader(new FileInputStream(argv[endPoint]), charset)), 
					false, true, semiring);
			lmTreeVec = RuleSetTraining.readItemSet(
					new BufferedReader(new InputStreamReader(new FileInputStream(argv[argv.length-1]), charset)), 
					false, true, semiring);


			File decodefile = (File)decodeTreeVec.get(0);
			int itemcount = ((Integer)decodeTreeVec.get(1)).intValue();
			ObjectInputStream decodeOis = new ObjectInputStream(new FileInputStream(decodefile));

			File lmfile = (File)lmTreeVec.get(0);
			int lmitemcount = ((Integer)lmTreeVec.get(1)).intValue();
			if (itemcount != lmitemcount)
				throw new UnusualConditionException("Read in different numbers of trees: "+itemcount+" vs "+lmitemcount);
			ObjectInputStream lmOis = new ObjectInputStream(new FileInputStream(lmfile));


			// for each item, get 1-tree lm target, convert to tree trans. get 1-tree decode target,
			// convert it to rtg, wrap in cascade, and do operation
			for (int itemnum = 0; itemnum < itemcount; itemnum++) {

				if (doClear) {
					Date preClearTime = new Date();
					FilteringPairSymbol.clear();
					PairSymbol.clear();
					System.gc(); System.gc(); System.gc();
					Date postClearTime = new Date();
					Debug.dbtime(1, 1, preClearTime, postClearTime, "time to clear memory");
				}
				
				Date startTime = new Date();
				RTGRuleSet rtg;

				TreeItem decodetree = (TreeItem)decodeOis.readObject();
				Date preForestTime = new Date();
				rtg = new RTGRuleSet(decodetree, semiring);
				Date postForestTime = new Date();
				Debug.dbtime(1, 1, preForestTime, postForestTime, "time to build forest from tree");


				Date preAssembleTime = new Date();

				//					Debug.prettyDebug("Done loading rtg");
				ConcreteGrammar gr = new ConcreteGrammar(rtg);
				Grammar g = gr;
				for (OTFTreeTransducerRuleSet ottrs : tts) {
					GTOTFGrammar otfg = new GTOTFGrammar(g, ottrs, semiring, mh);
					if (mh < 0) {
						g = new ConcreteGrammar(otfg);
						g.reportRules();
					}
					else
						g = otfg;
				}

				TreeItem lmtree = (TreeItem)lmOis.readObject();
				OTFTreeTransducerRuleSet lmottrs = new OTFTreeTransducerRuleSet(new TreeTransducerRuleSet(new RTGRuleSet(lmtree, semiring)));
				GTOTFGrammar otfg = new GTOTFGrammar(g, lmottrs, semiring, mh);
				if (mh < 0) {
					g = new ConcreteGrammar(otfg);
					g.reportRules();
				}
				else
					g = otfg;

				Date postAssembleTime = new Date();
				Debug.dbtime(1, 1, preAssembleTime, postAssembleTime, "time to assemble cascade");

				Date preGenTime = new Date();
				if (doExhaust) {
					g.exhaust(true);
				}
				else {
					for (int i = 0; i < k; i++) {
						Pair<TreeItem, Pair<Double, Integer>> tt = g.getNextTree(runTopDown);
						if (tt == null)
							System.out.println("EOL");
						else
							System.out.println((i+1)+":"+tt.l()+" # "+tt.r().l()+" # "+tt.r().r());

					}
				}
				Date postGenTime = new Date();			
				Debug.dbtime(1, 1, preGenTime, postGenTime, "time to generate");
				Date endTime = new Date();
				Debug.dbtime(1, 1, startTime, endTime, "total time");
				g.reportRules();

				//					GTOTFGrammar.printTimers();

				//					g.reportRules();
				//					GTOTFGrammar.resetTimers();

				//					System.out.println(Runtime.getRuntime().totalMemory()+" total");
				//					System.out.println(Runtime.getRuntime().freeMemory()+" free");
			}
		}

		catch (ClassNotFoundException e) {
			System.err.println("Couldn't find class while reading batch");
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
		catch (DataFormatException e) {
			System.err.println("Bad data format reading rtg "+argv[0]);
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
		catch (IOException e) {
			System.err.println("IO error reading rtg "+argv[0]);
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
		catch (ImproperConversionException e) {
			System.err.println("Couldn't convert grammar -- probably not in normal form");
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
		catch (UnusualConditionException e) {
			System.err.println("Unusual Condition!");
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
	}
}
