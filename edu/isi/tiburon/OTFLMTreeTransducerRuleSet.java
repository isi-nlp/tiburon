package edu.isi.tiburon;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


// ngram LM version of a tree transducer.
// given a terminal x, return x_x.x -> x # 0
// given state pair and symbol w_x, y_z, X, return w_z.X(x0: x1:) -> X(w_x.x0 y_z.x1) # p(y|x)
// TODO: extend to arbitrary ngram
public class OTFLMTreeTransducerRuleSet implements BinaryTreeTransducer {

	
	// really shouldn't be vectors here
	// TODO: change signature to produce iterator, then wrap individual items in single-item iter?
	// TODO: something for UNK?
	
	private PMap<Symbol, Vector<TreeTransducerRule>> lexRules;
	private PMap<Symbol, Pair<Vector<TreeTransducerRule>, PMap<Symbol, Pair>>> nonLexRules;
//	private PMap<VecSymbol, PMap<Symbol, Vector<TreeTransducerRule>>> nonLexRules;
	
	private HashMap<Symbol, HashMap<Symbol, Double>> table;
	private Semiring semiring;
	
	// simple line pattern -- previous word then sequence of next word and score
	private static Pattern linePat = Pattern.compile("^*(\\S+)\\s*(.*)");
	// token pattern -- optional whitespace then two separated blocks
	private static Pattern tokPat = Pattern.compile("\\s*(\\S+)\\s+(\\S+)");
	// empty TRVM -- for all the lexical rules
	private static TransducerRuleVariableMap emptyTRVM;
	
	
	private void initialize() {
		lexRules = new	PMap<Symbol, Vector<TreeTransducerRule>> ();
		nonLexRules = new PMap<Symbol, Pair<Vector<TreeTransducerRule>, PMap<Symbol, Pair>>> ();

//		nonLexRules = new PMap<VecSymbol, PMap<Symbol, Vector<TreeTransducerRule>>> ();
		table = new HashMap<Symbol, HashMap<Symbol, Double>> ();
		emptyTRVM = new TransducerRuleVariableMap();
	}
	
	public OTFLMTreeTransducerRuleSet(String filename, String encoding, Semiring s) throws FileNotFoundException, IOException, DataFormatException  {
		this(new BufferedReader(new InputStreamReader(new FileInputStream(filename), encoding)), s);
	} 

	public OTFLMTreeTransducerRuleSet(BufferedReader br, Semiring s) throws  IOException, DataFormatException {
		boolean debug = false;
		semiring = s;
		initialize();
		while (br.ready()) {
			String line = br.readLine();
			Matcher lineMatch = linePat.matcher(line);
			if (!lineMatch.matches())
				throw new DataFormatException("Bad line format: "+line);
			Symbol leftWord = SymbolFactory.getSymbol(lineMatch.group(1));
			HashMap<Symbol, Double> entry = new HashMap<Symbol, Double>();
			table.put(leftWord, entry);
			Matcher tokMatch = tokPat.matcher(lineMatch.group(2));
			while (tokMatch.find()) {
				Symbol rightWord = SymbolFactory.getSymbol(tokMatch.group(1));
				double val = Double.parseDouble(tokMatch.group(2));
				if (debug) Debug.debug(debug, "Adding "+leftWord+" -> "+rightWord+" = "+val);
				entry.put(rightWord, val);
			}
		}
	}
	
	
	// look up probability between elements
	public Iterable<TreeTransducerRule> getForwardRules(Vector<Symbol> vs,
			Symbol label) throws UnusualConditionException {
		boolean debug = false;
		if (!nonLexRules.containsKey(label))
			nonLexRules.put(label, new Pair<Vector<TreeTransducerRule>, PMap<Symbol, Pair>>(null, new PMap<Symbol, Pair>()));
		Pair<Vector<TreeTransducerRule>, PMap<Symbol, Pair>> currPair = nonLexRules.get(label);
		int currChild = 0;
		double currProb = semiring.ONE();
		while (currChild < vs.size()) {
			if (!currPair.r().containsKey(vs.get(currChild)))
				currPair.r().put(vs.get(currChild), new Pair<Vector<TreeTransducerRule>, PMap<Symbol, Pair>>(new Vector<TreeTransducerRule>(), new PMap<Symbol, Pair>()));
			currPair = currPair.r().get(vs.get(currChild++));
		}
		// nothing stored, so have to store it
		if (currPair.l().size() == 0) {
			if (vs.size() > 2)
				throw new UnusualConditionException("Too many elements in "+vs);
			if (! (vs.get(0) instanceof WordBoundariesSymbol))
				throw new UnusualConditionException("incorrect type for members of "+vs);
			WordBoundariesSymbol leftSym = (WordBoundariesSymbol)vs.get(0);
			WordBoundariesSymbol state = null;
			double wgt = semiring.ONE();
			// monadic case -- no cost and state doesn't change
			if (vs.size() == 1) {
				state = leftSym;
			}
			// pair case -- state is external boundaries and weight is from table
			else {
				if (! (vs.get(1) instanceof WordBoundariesSymbol))
					throw new UnusualConditionException("incorrect type for members of "+vs);
				WordBoundariesSymbol rightSym = (WordBoundariesSymbol)vs.get(1);
				state = WordBoundariesSymbol.get(leftSym.getLeft(), rightSym.getRight());
				if (!table.containsKey(leftSym.getRight()) ||
						!table.get(leftSym.getRight()).containsKey(rightSym.getLeft()))
					//		wgt = 30;
					//		wgt = semiring.ZERO();
					// "smoothed"
					wgt = 5;
				else
					wgt = table.get(leftSym.getRight()).get(rightSym.getLeft());
			}
			TransducerRuleVariableMap trvm = new TransducerRuleVariableMap();
			TransducerLeftTree left = new TransducerLeftTree(label, vs.size(), trvm);
			Vector<TransducerRightTree> kids = new Vector<TransducerRightTree>();
			for (int i = 0; i < vs.size(); i++) {
				kids.add(new TransducerRightTree(left.getChild(i), vs.get(i)));
			}
			TransducerRightTree right = new TransducerRightTree(label, kids, trvm);
			TreeTransducerRule rule = new TreeTransducerRule(state, left, right, trvm, wgt, semiring);
			if (debug) Debug.debug(debug, "Built "+rule+" from "+vs+" and "+label);
			currPair.l().add(rule);
		}
		return currPair.l();
	}

	// integer ignored and around just for signature matching -- bad if it's > 0
	public Iterable<TreeTransducerRule> getRelPosLexRules(Symbol s, int i) {
		boolean debug = false;
		if (!lexRules.containsKey(s)) {
			// build the rule
			WordBoundariesSymbol state = WordBoundariesSymbol.get(s, s);
			TransducerLeftTree left = new TransducerLeftTree(s);
			TransducerRightTree right = new TransducerRightTree(s);
			TreeTransducerRule rule = new TreeTransducerRule(state, left, right, emptyTRVM, semiring.ONE(), semiring);
			if (debug) Debug.debug(debug, "Built "+rule+" from "+s);
			Vector<TreeTransducerRule> vec = new Vector<TreeTransducerRule>();
			vec.add(rule);
			lexRules.put(s, vec);
		}
		return lexRules.get(s);

	}

	
	// all values are accepted
	public boolean hasLeftStateRules(Symbol state, Symbol label) {
		return true;
	}

	public boolean hasRightStateRules(Symbol state, Symbol label) {
		return true;
	}

	// everything is start
	public boolean isStartState(Symbol s) {
		return true;
	}

}
