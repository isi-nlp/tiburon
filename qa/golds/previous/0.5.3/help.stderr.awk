# this is an awk script to verify the lines of a file. If a line is
#  incorrect its number is printed, and if the wrong number of lines
# is found this too is printed
NR==1 && $0 !~ /^This is Tiburon, version [0-9.]+\s*$/ {print NR}
NR==2 && $0 != "Usage: tiburon " {print NR}
NR==3 && $0 != "             [-h|--help] (-e|--encoding) <encoding> (-m|--semiring) <srtype> [-b|--batch] [--leftapply] [--rightapply] [--lookahead <lookahead>] [(-a|--align) <align>] [-l|--left] [-r|--right] [-n|--normalizeweight] [--no-normalize] [--normform] [(-p|--prune) <prune>] [(-d|--determinize) <determ>] [(-t|--train) <train>] [(-x|--xform) <xform>] [--training-deriv-location <trainderivloc>] [--conditional] [--no-deriv] [--randomize] [--timedebug <time>] [-y|--print-yields] [(-k|--kbest) <kbest>] [(-g|--generate) <krandom>] [-c|--check] [(-o|--outputfile) <outfile>] infiles1 infiles2 ... infilesN" {print NR}
NR==4 && $0 != "" {print NR}
NR==5 && $0 != "  [-h|--help]" {print NR}
NR==6 && $0 != "        print this help message" {print NR}
NR==7 && $0 != "" {print NR}
NR==8 && $0 != "  (-e|--encoding) <encoding>" {print NR}
NR==9 && $0 != "        encoding of input and output files, if other than utf-8. Use the same" {print NR}
NR==10 && $0 != "        naming you would use if specifying this charset in a java program" {print NR}
NR==11 && $0 != "        (default: utf-8)" {print NR}
NR==12 && $0 != "" {print NR}
NR==13 && $0 != "  (-m|--semiring) <srtype>" {print NR}
NR==14 && $0 != "        type of weights: can be real (probabilities), truereal (probabilities" {print NR}
NR==15 && $0 != "        with underflow), tropical (max log weights), or tropicalaccumulative" {print NR}
NR==16 && $0 != "        (summed log weights) (default: real)" {print NR}
NR==17 && $0 != "" {print NR}
NR==18 && $0 != "  [-b|--batch]" {print NR}
NR==19 && $0 != "        batch mode: first file is assumed to be either a sequence of" {print NR}
NR==20 && $0 != "        newline-separated trees, tree pairs (indistinguishable from the previous" {print NR}
NR==21 && $0 != "        case except for the requirement the number of lines be even), or" {print NR}
NR==22 && $0 != "        alternating tree/string pairs; subsequent files are any automata" {print NR}
NR==23 && $0 != "        sequence that resolves to a grammar or transducer. If an RTG results the" {print NR}
NR==24 && $0 != "        sequence of trees is intersected, one at a time, and the specified" {print NR}
NR==25 && $0 != "        actions (-k, -g, etc.) are performed. If a transducer results, the" {print NR}
NR==26 && $0 != "        operation depends on the setting of --leftapply and --rightapply." {print NR}
NR==27 && $0 != "" {print NR}
NR==28 && $0 != "  [--leftapply]" {print NR}
NR==29 && $0 != "        instruct batch mode to perform forward application of trees onto" {print NR}
NR==30 && $0 != "        transducer input to form an RTG/CFG. cannot be used with -t. When used" {print NR}
NR==31 && $0 != "        with -rightapply, the result is a derivation RTG." {print NR}
NR==32 && $0 != "" {print NR}
NR==33 && $0 != "  [--rightapply]" {print NR}
NR==34 && $0 != "        instruct batch mode to perform backward application of trees onto" {print NR}
NR==35 && $0 != "        transducer input to form an RTG. cannot be used with -t. When used with" {print NR}
NR==36 && $0 != "        -leftapply, the result is a derivation RTG." {print NR}
NR==37 && $0 != "" {print NR}
NR==38 && $0 != "  [--lookahead <lookahead>]" {print NR}
NR==39 && $0 != "        Sets the lookahead bytes for file type detection to <lookahead> (default" {print NR}
NR==40 && $0 != "        is 10480)" {print NR}
NR==41 && $0 != "" {print NR}
NR==42 && $0 != "  [(-a|--align) <align>]" {print NR}
NR==43 && $0 != "        given sentence pairs and a trained transducer, return the <align> best" {print NR}
NR==44 && $0 != "        alignments." {print NR}
NR==45 && $0 != "" {print NR}
NR==46 && $0 != "  [-l|--left]" {print NR}
NR==47 && $0 != "        left associative composition/application of transducers/automata:" {print NR}
NR==48 && $0 != "        (file1*file2) * file3 ... * fileN This is the default unless the" {print NR}
NR==49 && $0 != "        rightmost automaton is a grammar" {print NR}
NR==50 && $0 != "" {print NR}
NR==51 && $0 != "  [-r|--right]" {print NR}
NR==52 && $0 != "        right associative composition/application of transducers/automata:" {print NR}
NR==53 && $0 != "        file1* ... * (fileN-1 * fileN)This is the default if the rightmost" {print NR}
NR==54 && $0 != "        automaton is a grammar" {print NR}
NR==55 && $0 != "" {print NR}
NR==56 && $0 != "  [-n|--normalizeweight]" {print NR}
NR==57 && $0 != "        normalize weights of RTG/CFG. This option is only relevant to the real" {print NR}
NR==58 && $0 != "        semiring." {print NR}
NR==59 && $0 != "" {print NR}
NR==60 && $0 != "  [--no-normalize]" {print NR}
NR==61 && $0 != "        Don't normalize weights, even when they would be by default. This option" {print NR}
NR==62 && $0 != "        is only relevant to the real semiring. It cannot be used with -n" {print NR}
NR==63 && $0 != "        (obviously)" {print NR}
NR==64 && $0 != "" {print NR}
NR==65 && $0 != "  [--normform]" {print NR}
NR==66 && $0 != "        convert RTG/CFG to chomsky normal form. can't be used with -t" {print NR}
NR==67 && $0 != "" {print NR}
NR==68 && $0 != "  [(-p|--prune) <prune>]" {print NR}
NR==69 && $0 != "        Prune rules that must exist in a tree with score <prune> greater than" {print NR}
NR==70 && $0 != "        the score of the best tree. This is currently implemented for log-based" {print NR}
NR==71 && $0 != "        semirings only (i.e. tropical and tropicalaccumulative). Pruning occurs" {print NR}
NR==72 && $0 != "        before determinization (-d) when both options are included. It cannot be" {print NR}
NR==73 && $0 != "        used with -t." {print NR}
NR==74 && $0 != "" {print NR}
NR==75 && $0 != "  [(-d|--determinize) <determ>]" {print NR}
NR==76 && $0 != "        Determinize the input RTG for <determ> minutes. Determinization requires" {print NR}
NR==77 && $0 != "        normal form (done implicitly) and no loops. It cannot be used with -t." {print NR}
NR==78 && $0 != "" {print NR}
NR==79 && $0 != "  [(-t|--train) <train>]" {print NR}
NR==80 && $0 != "        perform EM training of a transducer given (input, output) training" {print NR}
NR==81 && $0 != "        pairs, or a grammar given trees/strings. Train for up to <train>" {print NR}
NR==82 && $0 != "        iterations. The only acceptable output is the weighted transducer or" {print NR}
NR==83 && $0 != "        weighted grammar , thus -k and -g and -c options aren't valid. -p, -d," {print NR}
NR==84 && $0 != "        --normform, --removeloops, -n, -l, -s are similarly disallowed. There" {print NR}
NR==85 && $0 != "        must be at least 2 files in the input list - the first is assumed to be" {print NR}
NR==86 && $0 != "        a set of training pairs or triples. The remaining are the transducer(s)" {print NR}
NR==87 && $0 != "        or grammar(s). " {print NR}
NR==88 && $0 != "" {print NR}
NR==89 && $0 != "  [(-x|--xform) <xform>]" {print NR}
NR==90 && $0 != "        transform the input automaton, string file, or tree file into <xform>." {print NR}
NR==91 && $0 != "        Possible values are NONE CFG RTG XR XRS . Some information may be lost" {print NR}
NR==92 && $0 != "        by virtue of the type of transformation performed. This operation is" {print NR}
NR==93 && $0 != "        performed after any intersection or composition, but before -d, -p, -k," {print NR}
NR==94 && $0 != "        -g, -l, -r. Transformation of transducers to grammars is domain" {print NR}
NR==95 && $0 != "        projection." {print NR}
NR==96 && $0 != "" {print NR}
NR==97 && $0 != "  [--training-deriv-location <trainderivloc>]" {print NR}
NR==98 && $0 != "        Only valid if -t is used. <trainderivloc> is the file to hold binary" {print NR}
NR==99 && $0 != "        representation of the calculated derivation forests. if not specified," {print NR}
NR==100 && $0 != "        they are written to and read from a temporary file and deleted" {print NR}
NR==101 && $0 != "        afterward." {print NR}
NR==102 && $0 != "" {print NR}
NR==103 && $0 != "  [--conditional]" {print NR}
NR==104 && $0 != "        Train transducers conditionally; i.e. all rules with the same LHS have" {print NR}
NR==105 && $0 != "        probability summing to 1. If not set, training is joint; i.e. all rules" {print NR}
NR==106 && $0 != "        with the same LHS root have probability summing to 1. Eventually this" {print NR}
NR==107 && $0 != "        will be for normalization as well." {print NR}
NR==108 && $0 != "" {print NR}
NR==109 && $0 != "  [--no-deriv]" {print NR}
NR==110 && $0 != "        Only valid if -t is used. If present, do not calculate derivation" {print NR}
NR==111 && $0 != "        forests. Instead, assume they have already been built" {print NR}
NR==112 && $0 != "        (training-deriv-location flag must also be used). Derivation forests are" {print NR}
NR==113 && $0 != "        typically time consuming, but once built don't change." {print NR}
NR==114 && $0 != "" {print NR}
NR==115 && $0 != "  [--randomize]" {print NR}
NR==116 && $0 != "        Randomize the weights of the input structure (grammar or transducer) to" {print NR}
NR==117 && $0 != "        be equivalent to a probability between 0.2 and 0.8. This is mostly" {print NR}
NR==118 && $0 != "        useful for EM training." {print NR}
NR==119 && $0 != "" {print NR}
NR==120 && $0 != "  [--timedebug <time>]" {print NR}
NR==121 && $0 != "        Print timing information to stderr at a variety of levels: 0+ for total" {print NR}
NR==122 && $0 != "        operation, 1+ for each processing stage, 2+ for small info" {print NR}
NR==123 && $0 != "" {print NR}
NR==124 && $0 != "  [-y|--print-yields]" {print NR}
NR==125 && $0 != "        print yield of trees instead of trees. no meaning unless -g or -k is" {print NR}
NR==126 && $0 != "        used on an RTG." {print NR}
NR==127 && $0 != "" {print NR}
NR==128 && $0 != "  [(-k|--kbest) <kbest>]" {print NR}
NR==129 && $0 != "        return the <kbest> highest ranked items in a grammar. This option cannot" {print NR}
NR==130 && $0 != "        be used with -g or -c or -t" {print NR}
NR==131 && $0 != "" {print NR}
NR==132 && $0 != "  [(-g|--generate) <krandom>]" {print NR}
NR==133 && $0 != "        generate <krandom> trees from an RTG or strings from a CFG" {print NR}
NR==134 && $0 != "        stochastically. This option cannot be used with -k or -c or -t" {print NR}
NR==135 && $0 != "" {print NR}
NR==136 && $0 != "  [-c|--check]" {print NR}
NR==137 && $0 != "        check the number of rules, states, and derivations of the grammar or" {print NR}
NR==138 && $0 != "        transducer " {print NR}
NR==139 && $0 != "" {print NR}
NR==140 && $0 != "  [(-o|--outputfile) <outfile>]" {print NR}
NR==141 && $0 != "        file to write output grammar or tree list or summary. If absent, writing" {print NR}
NR==142 && $0 != "        is done to stdout" {print NR}
NR==143 && $0 != "" {print NR}
NR==144 && $0 != "  infiles1 infiles2 ... infilesN" {print NR}
NR==145 && $0 != "        list of input files. If using batch mode (-b) or training mode (-b) the" {print NR}
NR==146 && $0 != "        first file must be a list of batch or training items. Subsequent files" {print NR}
NR==147 && $0 != "        are transducers, grammars, trees, or strings that will be composed in" {print NR}
NR==148 && $0 != "        the 'obvious' way if possible, using either the default associativity" {print NR}
NR==149 && $0 != "        (right if the rightmost file is a grammar or tree, left otherwise) or" {print NR}
NR==150 && $0 != "        that set by -l/-r. The special symbol '-'(no quote) may be specified up" {print NR}
NR==151 && $0 != "        to one time in the file sequence to indicate reading from STDIN. Illegal" {print NR}
NR==152 && $0 != "        composition sequences, such as intersection of two CFGs, a grammar" {print NR}
NR==153 && $0 != "        followed by a copying transducer, or attempted composition of two" {print NR}
NR==154 && $0 != "        extended transducers will result in an error message." {print NR}
NR==155 && $0 != "" {print NR}
{records++}
END{if (records != 155) {printf("%s lines\n", records)}}
