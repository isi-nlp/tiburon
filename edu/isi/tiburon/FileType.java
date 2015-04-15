package edu.isi.tiburon;

// collection of testing  methods and enumerations for determining what type of file is being read
// emphasis is on differentiation, not validation. So if the format is in general not acceptable, this isn't 
// guaranteed to find it. However, if errors are encountered they will be reported
// the differences examined should be commented before each method.

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileType {

	/** Unknowns and errors. Not sure how much
	 *  these are actually used
	 */
	public enum TYPE {
		UNKNOWN,
		ERROR,

		/**
		 * File with a single tree
		 * special subset of TRAIN
		 */
		TREE,

		/**
		 * File with a single string
		 * special subset of TRAIN
		 */
		STRING,

		/** general training type. master type for nesting.
		 *  should be further differentiated
		 */
		TRAIN,

		/** general grammar type. 
		 *  should be further differentiated
		 */
		GRAMMAR,

		/** general transducer type.
		 *  should be further differentiated
		 */
		TRANSDUCER,

		/** specifics of grammar
		 */
		RTG, CFG,

		/** specifics of transducer
		 */
		TREE_TRANS,
		STRING_TRANS,

		/** special batch type. 
		 *   not that different from training
		 */
		BATCH;
		public TYPE getGeneral() {
			switch (this) {
			case RTG:
			case CFG:
			case TREE:
			case STRING:
			case TRAIN:
				return GRAMMAR;
			case TREE_TRANS:
			case STRING_TRANS:
				return TRANSDUCER;
			default:
				return this;
			}
		}
	}


	// nested enum for train type. only applies if this is a training file

	static enum TRAINTYPE { NO_TRAIN, TREE_TREE_COUNT, TREE_STRING_COUNT, TREE_TREE, TREE_STRING, 
		TREE_COUNT, STRING_COUNT, TREE, STRING;
	public boolean isPair() {
		switch (this) {
		case TREE_TREE_COUNT:
		case TREE_STRING_COUNT:
		case TREE_TREE:
		case TREE_STRING:
			return true;
		default:
			return false;
		}
	}
	public boolean isCount() {
		switch (this) {
		case TREE_TREE_COUNT:
		case TREE_STRING_COUNT:
		case TREE_COUNT:
		case STRING_COUNT:
			return true;
		default:
			return false;
		}
	}
	public boolean isFirstTree() {
		switch (this) {
		case STRING_COUNT:
		case STRING:
			return false;
		default:
			return true;
		}
	}
	public boolean isSecondTree() {
		switch (this) {
		case TREE_TREE_COUNT:
		case TREE_TREE:
			return true;
		default:
			return false;
		}
	}
	// just choose a type based on some boolean values: is count, isfirstTree, isSecondTree
	// (this is for training transducers)
	static TRAINTYPE frombools(boolean ic, boolean ift, boolean ist) throws DataFormatException, IOException {
		if (ift) {
			if (ist) {
				// note: this subsumes TREE_COUNT and TREE for now!
				if (ic)
					return TREE_TREE_COUNT;
				else
					return TREE_TREE;
			}
			if (ic)
				return TREE_STRING_COUNT;
			else
				return TREE_STRING;
		}
		// must be a cfg
		else {
			if (ist)
				throw new DataFormatException("Training File type has first item as string "+
				"and second item as tree; only CFGs allowed!");
			if (ic)
				return STRING_COUNT;
			else
				return STRING;
		}

	}
	// just choose a type based on some boolean values: is count, is(first)Tree
	// (this is for training grammars)
	static TRAINTYPE frombools(boolean ic, boolean ift) {
		if (ift)
			if (ic)
				return TREE_COUNT;
			else
				return TREE;
		if (ic)
			return STRING_COUNT;
		else
			return STRING;
	}
	}


	// properties of transducer
	private boolean isCopy;
	private boolean isDelete;
	private boolean isExtend;
	private boolean isInEps;
	private boolean isOutEps;

	public boolean isCopy() { return isCopy; }
	public boolean isDelete() { return isDelete; }
	public boolean isExtend() { return isExtend; }
	public boolean isInEps() { return isInEps; }
	public boolean isOutEps() { return isOutEps; }

	private TYPE type;
	private TRAINTYPE traintype;

	// low limit on how many characters can be read; if this limit is reached
	// reset is impossible
	// 10480 and 10240
	private static final int READ_HARD_LIMIT=10480;
	private static final int READ_LIMIT=10240;


	
	// type identifiers.
	// if types are manually specified, file must start with "% TYPE <TYPE>"
	// <TYPE> can be:

	// RTG, CFG
	// TODO: put in more types!
	private static final String TYPE_SPEC = "TYPE";
	private static final String TYPE_RTG = "RTG";
	private static final String TYPE_CFG = "CFG";
	private static final String TYPE_TREE = "TREE";
	private static final String TYPE_STRING = "STRING";
	private static final String TYPE_TREE_BATCH = "TREEBATCH";
	private static final String TYPE_STRING_BATCH = "STRINGBATCH";
	
	
	// doing it with item constructors to the degree possible, using regexes at other points
	
	// 1) check if the first line is % TYPE and accept declared type if so
	// otherwise
	// 2) ignore all comments fields and blank lines from the head of the file
	// 3) determine if this is a multi-line file.
	// 4) If not multi-line, try to create a tree, then a string. Accept the first that is legitimately read.
	// 5) If multi-line, try to read RTG, CFG, TTT, TST, then various training files. Accept the
	//    first that is legitimately read.
	
	
	// Commented out TYPE and then some content
	private static Pattern typeSpecPat = Pattern.compile("% TYPE (\\S.*\\S)");
	
	// empty spaces or comments regions
	private static Pattern commentPat = Pattern.compile("\\s*(%.*)?");
	
	// something that can be a start state -- no spaces, no parens
	// can be followed by whitespace and comment
	private static Pattern startStatePat = Pattern.compile("\\s*\\S+\\s*(%.*)?");
	
	// strip comments off
	private static Pattern commentStripPat = Pattern.compile("(.*?)(%.*)?");
	
	public FileType(BufferedReader br) throws DataFormatException, IOException {
		this(br, READ_HARD_LIMIT);
	}
	
	public FileType(BufferedReader br, int limit) throws DataFormatException, IOException {
		boolean debug = false;
		br.mark(limit);
		// soft limit for guessing
		int softlimit = 1024;
		if (limit > 1024)
			softlimit = limit-1024;
		if (debug) Debug.debug(debug, "Debugging on");

		type = TYPE.UNKNOWN;
		traintype = TRAINTYPE.NO_TRAIN;
		
		// track number of bytes we're responsible for. assume 2-byte chars. decide early if it's close 
		int totalbytes = 0;
		
		// 1) look for type spec in first line
		String line = br.readLine();
		totalbytes += line.length()*2;
		if (debug) Debug.debug(debug, "Checking for type spec in "+line);
		Matcher typeSpecMatch = typeSpecPat.matcher(line);
		if (typeSpecMatch.lookingAt()) {
			// set type if the matched region is valid or return error if it isn't
			String typeSpec = typeSpecMatch.group(1);
			if (debug) Debug.debug(debug, line+" matched type spec with field "+typeSpec);
			if (typeSpec.equals(TYPE_RTG)) {
				if (debug) Debug.debug(debug, "Manually selecting RTG");
				type = TYPE.RTG;
				return;
			}
			else if (typeSpec.equals(TYPE_CFG)) {
				if (debug) Debug.debug(debug, "Manually selecting CFG");
				type = TYPE.CFG;
				return;
			}
			else if (typeSpec.equals(TYPE_TREE)) {
				if (debug) Debug.debug(debug, "Manually selecting TREE");
				type = TYPE.TREE;
				return;
			}
			else if (typeSpec.equals(TYPE_STRING)) {
				if (debug) Debug.debug(debug, "Manually selecting STRING");
				type = TYPE.STRING;
				return;
			}
			else if (typeSpec.equals(TYPE_TREE_BATCH)) {
				if (debug) Debug.debug(debug, "Manually selecting TREE batch");
				type = TYPE.TRAIN;
				traintype = TRAINTYPE.frombools(false, true, true);
				return;
			}
			else if (typeSpec.equals(TYPE_STRING_BATCH)) {
				if (debug) Debug.debug(debug, "Manually selecting STRING batch");
				type = TYPE.TRAIN;
				traintype = TRAINTYPE.frombools(false, false);
				return;
			}
			// TODO: more complicated manual type setting!
			else {
				throw new DataFormatException("Expected valid type specifier after file-initial % TYPE but read "+typeSpec);
			}
		}
		if (debug) Debug.debug(debug, "No type spec found");
		
		// 2) ignore all comments fields and blank lines in the header, then reset the mark.
		Matcher commentMatch = commentPat.matcher(line);
		while (commentMatch.matches()) {
			if (debug) Debug.debug(debug, "Ignoring comment/whitespace: "+line);
			br.mark(limit);
			line = br.readLine();
			totalbytes = line.length()*2;
			commentMatch = commentPat.matcher(line);
		}
		if (debug) Debug.debug(debug, "No longer comment/whitespace: "+line);
		
		// 3) determine if this is a single-line file. If nothing more to read or if there are only comments/whitespace
		//    it is.
		String nextLine = new String();
		boolean isSingle = true;
		while (br.ready()) {
			nextLine = br.readLine();
			totalbytes += nextLine.length()*2;
			if (debug) Debug.debug(debug, "File has an additional line: "+nextLine);
			Matcher nextCommentMatch = commentPat.matcher(nextLine);
			if (!nextCommentMatch.matches()) {
				isSingle = false;
				break;
			}
			if (debug) Debug.debug(debug, "Ignoring comment/whitespace: "+nextLine);
		}
		if (debug) Debug.debug(debug, "File has no more additional lines");
		// 4) Single line file is either tree or string or error
		if (isSingle) {
			try {
				if (debug) Debug.debug(debug, "Trying to make a tree out of "+line);
				TreeItem t = new TreeItem(new StringBuffer(line));
				if (debug) Debug.debug(debug, "Deciding TREE");
				type = TYPE.TREE;
				br.reset();
				return;
			}
			catch (DataFormatException e) {
				if (debug) Debug.debug(debug, "Couldn't make a tree out of "+line);
			}
			catch (IOException e) {
				throw new IOException("Unable to determine type -- first line has more than "+limit+" bytes");
			}
			try {
				if (debug) Debug.debug(debug, "Trying to make a string out of "+line);
				StringItem s = new StringItem(new StringBuffer(line));
				if (debug) Debug.debug(debug, "Deciding STRING");
				type = TYPE.STRING;
				br.reset();
				return;
			}
			catch (DataFormatException e) {
				if (debug) Debug.debug(debug, "Couldn't make a string out of "+line);
			}
			catch (IOException e) {
				throw new IOException("Unable to determine type -- first line has more than "+limit+" bytes");
			}
			throw new DataFormatException("Couldn't make tree or string out of "+line);
		}
		
		// 5) Multi line file is RTG, CFG, TTT, TST, then training files.
		
		// is this a grammar file? first line must be some type of start state
		Matcher startStateMatch = startStatePat.matcher(line);
		if (debug) Debug.debug(debug, "Trying to get a start state out of "+line);
		if (startStateMatch.matches()) {
			// dummy structures
			TrueRealSemiring dummySem = new TrueRealSemiring();	
			RTGRuleSet dummyRS = new RTGRuleSet(dummySem);
			CFGRuleSet dummyCRS = new CFGRuleSet();
			StringTransducerRuleSet dummySTRS = new StringTransducerRuleSet();
			TreeTransducerRuleSet dummyTTRS = new TreeTransducerRuleSet();
			// try to make each type of rule. If we can make exactly one, decide that.
			// if we can make more than one, continue
			
	
			// for choosing after byte tally is exceeded
			boolean alwaysDidRTG=true;
			boolean alwaysDidCFG=true;
			boolean alwaysDidTTT=true;
			boolean alwaysDidTST=true;
			
			do {
				Matcher commentStripMatch = commentStripPat.matcher(nextLine);
				if (!commentStripMatch.matches())
					throw new DataFormatException("Couldn't strip comments off of "+nextLine);
				String ruleText = commentStripMatch.group(1);
				if (debug) Debug.debug(debug, "Trying to build some rule out of "+ruleText);
				boolean didRTG=false;
				boolean didCFG=false;
				boolean didTTT=false;
				boolean didTST=false;
				try {
					RTGRule r = new RTGRule(dummyRS, ruleText, dummySem);
					if (debug) Debug.debug(debug, "Able to build rtg rule "+r);
					didRTG=true;
				}
				catch (DataFormatException e) {
					if (debug) Debug.debug(debug, "Can't build rtg rule");
					alwaysDidRTG = false;
				}
				try {
					CFGRule r = new CFGRule(dummyCRS, ruleText, dummySem);
					if (debug) Debug.debug(debug, "Able to build cfg rule "+r);
					didCFG=true;
				}
				catch (DataFormatException e) {
					if (debug) Debug.debug(debug, "Can't build cfg rule");
					alwaysDidCFG = false;
				}
				
				try {
					TreeTransducerRule r = new TreeTransducerRule(dummyTTRS, new HashSet<Symbol>(), ruleText, dummySem);
					if (debug) Debug.debug(debug, "Able to build ttt rule "+r);
					didTTT=true;
				}
				catch (DataFormatException e) {
					if (debug) Debug.debug(debug, "Can't build ttt rule");
					alwaysDidTTT = false;
				}
				try {
					StringTransducerRule r = new StringTransducerRule(dummySTRS, new HashSet<Symbol>(), ruleText, dummySem);
					if (debug) Debug.debug(debug, "Able to build tst rule "+r);
					didTST=true;
				}
				catch (DataFormatException e) {
					if (debug) Debug.debug(debug, "Can't build tst rule");
					alwaysDidTST = false;
				}
				// if exactly one is true, choose it
				if (didRTG ^ didCFG ^ didTTT ^ didTST) {
					if (didRTG) {
						type = TYPE.RTG;
						if (debug) Debug.debug(debug, "Deciding RTG");
						try {
							br.reset();
						}
						catch (IOException e) {
							throw new IOException("Unable to determine type within first "+limit+" bytes");
						}
						return;
					}
					if (didCFG) {
						type = TYPE.CFG;
						if (debug) Debug.debug(debug, "Deciding CFG");
						try {
							br.reset();
						}
						catch (IOException e) {
							throw new IOException("Unable to determine type within first "+limit+" bytes");
						}
						return;
					}
					if (didTTT) {
						type = TYPE.TREE_TRANS;
						if (debug) Debug.debug(debug, "Deciding TTT");
						try {
							br.reset();
						}
						catch (IOException e) {
							throw new IOException("Unable to determine type within first "+limit+" bytes");
						}
						return;
					}
					if (didTST) {
						type = TYPE.STRING_TRANS;
						if (debug) Debug.debug(debug, "Deciding TST");
						try {
							br.reset();
						}
						catch (IOException e) {
							throw new IOException("Unable to determine type within first "+limit+" bytes");
						}
						return;
					}
				}

				// if we're over the limit, decide based on what we could have been 
				if (totalbytes > softlimit) {
					// if we could have been all of these, decide ttt->tst->rtg->cfg
					if (alwaysDidTTT) {
						type = TYPE.TREE_TRANS;
						Debug.prettyDebug("Deciding TTT after reaching file read limit");
						try {
							br.reset();
						}
						catch (IOException e) {
							throw new IOException("Unable to determine type within first "+limit+" bytes");
						}
						return;
					}
					if (alwaysDidTST) {
						type = TYPE.STRING_TRANS;
						Debug.prettyDebug("Deciding TST after reaching file read limit");
						try {
							br.reset();
						}
						catch (IOException e) {
							throw new IOException("Unable to determine type within first "+limit+" bytes");
						}
						return;
					}
					if (alwaysDidRTG) {
						type = TYPE.RTG;
						Debug.prettyDebug("Deciding RTG after reaching file read limit");
						try {
							br.reset();
						}
						catch (IOException e) {
							throw new IOException("Unable to determine type within first "+limit+" bytes");
						}
						return;
					}
					if (alwaysDidCFG) {
						type = TYPE.CFG;
						Debug.prettyDebug("Deciding CFG after reaching file read limit");
						try {
							br.reset();
						}
						catch (IOException e) {
							throw new IOException("Unable to determine type within first "+limit+" bytes");
						}
						return;
					}
				}
				nextLine = null;
				boolean iswhitespace = true;
				while (br.ready()) {
					nextLine = br.readLine();
					totalbytes += nextLine.length()*2;
					if (debug) Debug.debug(debug, "File has an additional line: "+nextLine);
					Matcher nextCommentMatch = commentPat.matcher(nextLine);
					if (!nextCommentMatch.matches()) {
						iswhitespace = false;
						if (debug) Debug.debug(debug, "Not a comment or whitespace: ["+nextLine+"]");
						break;
					}
					if (debug) Debug.debug(debug, "Ignoring comment/whitespace: "+nextLine);
				}
				if (iswhitespace)
					break;
			} while (nextLine != null);
			// if we could have been any of these, decide ttt->tst->rtg->cfg
			if (alwaysDidTTT) {
				type = TYPE.TREE_TRANS;
				if (debug) Debug.debug(debug, "Deciding TTT on ambiguous grammar");
				try {
					br.reset();
				}
				catch (IOException e) {
					throw new IOException("Unable to determine type within first "+limit+" bytes");
				}
				return;
			}
			if (alwaysDidTST) {
				type = TYPE.STRING_TRANS;
				if (debug) Debug.debug(debug, "Deciding TST on ambiguous grammar");
				try {
					br.reset();
				}
				catch (IOException e) {
					throw new IOException("Unable to determine type within first "+limit+" bytes");
				}
				return;
			}
			if (alwaysDidRTG) {
				type = TYPE.RTG;
				if (debug) Debug.debug(debug, "Deciding RTG on ambiguous grammar");
				try {
					br.reset();
				}
				catch (IOException e) {
					throw new IOException("Unable to determine type within first "+limit+" bytes");
				}
				return;
			}
			if (alwaysDidCFG) {
				type = TYPE.CFG;
				if (debug) Debug.debug(debug, "Deciding CFG on ambiguous grammar");
				try {
					br.reset();
				}
				catch (IOException e) {
					throw new IOException("Unable to determine type within first "+limit+" bytes");
				}
				return;
			}
		}
		// either fell out of the above or never could match start state. Reset and try to recognize training file...
		if (debug) Debug.debug(debug, "Attempting to read training/batch file");
		type = TYPE.TRAIN;
		// TODO: do this more properly!
		try {
			br.reset();
			differentiateTraining(br);
			br.reset();
		}
		catch (IOException e) {
			throw new IOException("Unable to determine training/batch type within first "+limit+" bytes");
		}
	}
	

	/** Batch FileType. Should be more robust than this */
	public FileType() {
		type = TYPE.BATCH;
		traintype = TRAINTYPE.NO_TRAIN;
	}


	/** access the type */
	public TYPE getType() { return type; }
	/** access the general type */
	public TYPE getGenType() { return type.getGeneral(); }
	public TRAINTYPE getTrainType() { return traintype; }
	// choose between tree-to-tree transducer and tree-to-string transducer
	// this uses code from the constructor for TreeTransducerRule and StringTrandsucerRule

	// additionally, check for copying, deleting, epsilons, extended properties




	// differentiate until all flags are set, file is finished, or read limit is reached,
	// whichever comes first.
	private void differentiateTransducer(BufferedReader isr) throws DataFormatException, IOException {
		boolean debug = false;
		int readCounter = 0;
		try {
			StreamTokenizer st = new StreamTokenizer(isr);
			st.resetSyntax();
			st.eolIsSignificant(true);
			st.wordChars(33, '\u00ff');
			st.quoteChar('"');
			st.commentChar('%');
			// first read start state
			boolean isStartState = false;
			while (!isStartState && st.nextToken() != StreamTokenizer.TT_EOF) {
				switch(st.ttype) {
				case StreamTokenizer.TT_NUMBER:
				case StreamTokenizer.TT_WORD:
				case '"':
					readCounter += st.sval.length();
					if (debug) Debug.debug(debug, "Read start state "+st.sval+"; read at "+readCounter);
					int next = st.nextToken();
					// get rid of any excess tabs
					while (next == 32 || next == 11) {
						readCounter++;
						next = st.nextToken();
					}
					if (next != StreamTokenizer.TT_EOL && next != 13)
						throw new DataFormatException("differentiateTransducer: expected single start state but got "+st.ttype);
					readCounter++;
					if (debug) Debug.debug(debug, "After excess white space, read at "+readCounter);
					isStartState = true;
					break;
				case 13:
				case StreamTokenizer.TT_EOL:
					readCounter++;
					if (debug) Debug.debug(debug, "After excess initial white space, read at "+readCounter);
					break;
				default:
					throw new DataFormatException("differentiateTransducer: didn't Expect "+st.ttype);
				}
			}
			st.resetSyntax();
			st.eolIsSignificant(false);
			st.wordChars(0, '\u00ff');
			st.commentChar('%');
			st.whitespaceChars(10, 13);
			// dummy sets can be useful
			StringTransducerRuleSet dummySTRS = new StringTransducerRuleSet();
			TreeTransducerRuleSet dummyTTRS = new TreeTransducerRuleSet();
			TrueRealSemiring dummySem = new TrueRealSemiring();

			// track properties of the transducer
			isCopy = false;
			isDelete = false;
			isExtend = false;
			isInEps = false;
			isOutEps = false;

			while (type == TYPE.TRANSDUCER && st.nextToken() != StreamTokenizer.TT_EOF && readCounter < READ_LIMIT) {
				if (st.ttype != StreamTokenizer.TT_WORD)
					throw new DataFormatException("FileType:differentiateTransducer: looking for word, but read "+st.ttype);
				readCounter += st.sval.length();
				if (debug) Debug.debug(debug, "Read string "+st.sval+"; read counter at "+readCounter);
				// if we get through the first construction but not the second, retval is set. If we get through neither or both, it's unset
				try {
					StringTransducerRule sr = new StringTransducerRule(dummySTRS, new HashSet<Symbol>(), st.sval, dummySem);
					if (debug) Debug.debug(debug, "Able to build string transducer rule "+sr.toString()+": string transducer");
					type = TYPE.STRING_TRANS;
					// set properties
					if (sr.isCopying()) {
						if (debug) Debug.debug(debug, "It's copying");
						isCopy = true;
					}
					if (sr.isDeleting()) {
						if (debug) Debug.debug(debug, "It's deleting");
						isDelete = true;
					}
					if (sr.isExtended()) {
						if (debug) Debug.debug(debug, "It's extended");
						isExtend = true;
					}
					if (sr.isInEps()) {
						if (debug) Debug.debug(debug, "It's input epsilon");
						isInEps = true;
					}
					if (sr.isOutEps()) {
						if (debug) Debug.debug(debug, "It's output epsilon");
						isOutEps = true;
					}
					TreeTransducerRule tr = new TreeTransducerRule(dummyTTRS, new HashSet<Symbol>(), st.sval, dummySem);
					if (debug) Debug.debug(debug, "Also able to build tree transducer rule "+tr.toString()+": unknown transducer");
					type = TYPE.TRANSDUCER;
				}
				catch (DataFormatException e) {
					if (debug) Debug.debug(debug, "Exception thrown in the first chunk: "+e.getMessage());

				}
				// second chunk: same deal. If just tree works, we're okay. If both work, we continue.
				try {
					TreeTransducerRule tr = new TreeTransducerRule(dummyTTRS, new HashSet<Symbol>(), st.sval, dummySem);		
					if (debug) Debug.debug(debug, "Able to build tree transducer rule "+tr.toString()+": tree transducer");
					type = TYPE.TREE_TRANS;
					// set properties
					if (tr.isCopying()) {
						if (debug) Debug.debug(debug, "It's copying");
						isCopy = true;
					}
					if (tr.isDeleting()) {
						if (debug) Debug.debug(debug, "It's deleting");
						isDelete = true;
					}
					if (tr.isExtended()) {
						if (debug) Debug.debug(debug, "It's extended");
						isExtend = true;
					}
					if (tr.isInEps()) {
						if (debug) Debug.debug(debug, "It's input epsilon");
						isInEps = true;
					}
					if (tr.isOutEps()) {
						if (debug) Debug.debug(debug, "It's output epsilon");
						isOutEps = true;
					}
					StringTransducerRule sr = new StringTransducerRule(dummySTRS, new HashSet<Symbol>(), st.sval, dummySem);
					if (debug) Debug.debug(debug, "Also able to build string transducer rule "+sr.toString()+": unknown transducer");
					type = TYPE.TRANSDUCER;
				}
				catch (DataFormatException e) {
					if (debug) Debug.debug(debug, "Exception thrown in the second chunk: "+e.getMessage());
				}
			}

			// continue checking transducer for properties until all are set or we reach end of transducer
			while (!(isCopy && isDelete && isExtend && isInEps && isOutEps) && 
					st.nextToken() != StreamTokenizer.TT_EOF && 
					readCounter < READ_LIMIT) {
				if (st.ttype != StreamTokenizer.TT_WORD)
					throw new DataFormatException("FileType:differentiateTransducer: looking for word, but read "+st.ttype);
				readCounter += st.sval.length();
				if (debug) Debug.debug(debug, "Read string "+st.sval+"; read counter at "+readCounter);
				try {
					TransducerRule r = null;
					switch (type) {
					case TREE_TRANS:
						r = new TreeTransducerRule(dummyTTRS, new HashSet<Symbol>(), st.sval, dummySem);
						break;
					case STRING_TRANS:
						r = new StringTransducerRule(dummySTRS, new HashSet<Symbol>(), st.sval, dummySem);
						break;
					default:
						throw new IOException("Transducer is of unexpected type "+type);
					}
					// set properties
					if (r.isCopying()) {
						if (debug) Debug.debug(debug, "It's copying");
						isCopy = true;
					}
					if (r.isDeleting()) {
						if (debug) Debug.debug(debug, "It's deleting");
						isDelete = true;
					}
					if (r.isExtended()) {
						if (debug) Debug.debug(debug, "It's extended");
						isExtend = true;
					}
					if (r.isInEps()) {
						if (debug) Debug.debug(debug, "It's input epsilon");
						isInEps = true;
					}
					if (r.isOutEps()) {
						if (debug) Debug.debug(debug, "It's output epsilon");
						isOutEps = true;
					}
				}
				catch (DataFormatException e) {
					Debug.debug(true, "Unable to read rule of type "+type+": "+e.getMessage());
				}
			}
		}
		catch (IOException e) {
			System.err.println("IOException: "+e.toString());
		}
	}


	private void differentiateTraining(BufferedReader isr) throws DataFormatException, IOException{
		boolean ic=false;
		boolean isFirstDecided = false;
		boolean ift = false;
		// have we identified ist?
		boolean isSecondDecided = false;
		boolean ist=false;
		boolean debug = false;
		// count lines to see if this is actually TREE or STRING
		int numLines = 0;
		try {
			StreamTokenizer st = new StreamTokenizer(isr);
			st.resetSyntax();
			st.eolIsSignificant(false);
			st.wordChars(32,  '\u00FF');
			st.whitespaceChars(9, 13);
			st.parseNumbers();
			st.commentChar('%');
			boolean isTriple = (st.nextToken() == StreamTokenizer.TT_NUMBER);
			st.pushBack();
			if (isTriple)
				ic = true;
			else
				ic = false;
			// then, figure out what the items are.

			ITEM lastanswer1 = ITEM.UNK;
			String lastItem1 = "";
			ITEM lastanswer2 = ITEM.UNK;
			String lastItem2 = "";

			while (st.nextToken() != StreamTokenizer.TT_EOF && !isSecondDecided && !isFirstDecided) {
				// ignore the count
				numLines++;
				if (isTriple) {
					if (st.ttype != StreamTokenizer.TT_NUMBER)
						throw new DataFormatException("Expected number but got "+st.ttype);
					if (debug) Debug.debug(debug, "Ignoring count "+st.nval);
					st.nextToken();
					numLines++;
				}
				// identify the first (possibly only) member
				if (debug) Debug.debug(debug, "Checking first item "+st.sval);
				ITEM answer1 = differentiateItem(st.sval);
				switch (answer1) {
				case STRING:
					if (isFirstDecided && answer1 != lastanswer1)
						throw new DataFormatException("Type mismatch in training: "+lastItem1+" and "+st.sval+" are not of the same type");
					ift = false;
					isFirstDecided = true;
					lastanswer1 = answer1;
					lastItem1 = st.sval;
					break;
				case TREE:
					if (isFirstDecided && answer1 != lastanswer1)
						throw new DataFormatException("Type mismatch in training: "+lastItem1+" and "+st.sval+" are not of the same type");
					ift = true;
					isFirstDecided = true;
					lastanswer1 = answer1;
					lastItem1 = st.sval;
					break;
				default:
					throw new DataFormatException("Read "+st.sval+" as type "+answer1);
				}

				// identify the second member
				st.nextToken();
				if (st.ttype == StreamTokenizer.TT_EOF)
					break;
				numLines++;
				// maybe we're a set of single sides?
				if (isTriple && st.ttype == StreamTokenizer.TT_NUMBER) {
					if (debug) Debug.debug(debug, "number found after first item; deciding single-sides");
					break;
				}
				// identify the string
				if (debug) Debug.debug(debug, "Attempting to identify "+st.sval);
				ITEM answer2 = differentiateItem(st.sval);
				switch (answer2) {
				case STRING:
					if (isSecondDecided && answer2 != lastanswer2)
						throw new DataFormatException("Type mismatch in training: "+lastItem2+" and "+st.sval+" are not of the same type");
					ist = false;
					isSecondDecided = true;
					lastanswer2 = answer2;
					lastItem2 = st.sval;
					break;
				case TREE:
					if (isSecondDecided && answer2 != lastanswer2)
						throw new DataFormatException("Type mismatch in training: "+lastItem2+" and "+st.sval+" are not of the same type");
					ist = true;
					isSecondDecided = true;
					lastanswer2 = answer2;
					lastItem2 = st.sval;
					break;
				default:
					throw new DataFormatException("Read "+st.sval+" as type "+answer2);
				}

			}
		}
		catch (IOException e) {
			System.err.println("IOException: "+e.toString());
		}
		if (debug) Debug.debug(debug, "Number of lines is "+numLines);

		// single item case
		if (!isSecondDecided) {
			traintype = TRAINTYPE.frombools(ic, ift);
			if (!ic) {
				if (ift)
					type = TYPE.TREE;
				else
					type = TYPE.STRING;
			}
		}
		else
			traintype = TRAINTYPE.frombools(ic, ift, ist);
		if (debug) Debug.debug(debug, "Type is "+type+" and Training Type is "+traintype);

	}

	// choose between tree and string
	enum ITEM { UNK, NUM, TREE, STRING }

	private static ITEM differentiateItem(String str) throws IOException{
		StreamTokenizer st = new StreamTokenizer(new StringReader(str));
		st.resetSyntax();
		st.wordChars(33,  '\u00FF');
		st.ordinaryChar('(');
		st.ordinaryChar(')');
		st.ordinaryChar(9);
		st.ordinaryChar(32);
		st.quoteChar('"');
		boolean debug = false;
		if (debug) Debug.debug(debug, "differentiateItem: checking "+str);
		while (st.nextToken() != StreamTokenizer.TT_EOF) {
			switch (st.ttype) {
			case '(':
			case ')':
				if (debug) Debug.debug(debug, "Seen paren: deciding tree");
				return ITEM.TREE;
			case 9:
			case 32:
				if (debug) Debug.debug(debug, "Seen whitespace: deciding string");
				return ITEM.STRING;
			case '"':
			case StreamTokenizer.TT_WORD:
				if (debug) Debug.debug(debug, "Seen word "+st.sval+": deferring decision");
				break;
			default:
				if (debug) Debug.debug(debug, "Unexpected token "+st.ttype);
			break;
			}
		}
		if (debug) Debug.debug(debug, "No decision reached");
		return ITEM.UNK;
	}
}



