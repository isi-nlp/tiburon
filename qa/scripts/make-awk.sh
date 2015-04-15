#!/usr/bin/env bash
# make an awk file that transforms a text file into a series of awk scripts
# that match each line exactly. Good for modification when only some lines of a file
# can be partial matches, and others must be exact matches.

OS=`uname -a | awk '{print $1}'`;

INFILE=$1;
OUTFILE=$2;
SIZE=`wc -l $INFILE | awk '{print $1}'`;
PREFIX=`mktemp -t prefix.XXXXXX`;
SUFFIX=`mktemp -t suffix.XXXXXX`;
OS=`uname -a | awk '{print $1}'`;
if [[ $OS == "Darwin" ]]; then
    SEQCMD="jot $SIZE 1";
else
    SEQCMD="seq 1 $SIZE";
fi
for i in `$SEQCMD`; do
    echo "NR==$i && \$0 != \"" >> $PREFIX;
    echo "\" {print NR}" >> $SUFFIX;
done
# this is an awk script to verify the lines of a file. If a line is
#  incorrect its number is printed, and if the wrong number of lines
# is found this too is printed

cat << 'EOF' > $OUTFILE
# this is an awk script to verify the lines of a file. If a line is
#  incorrect its number is printed, and if the wrong number of lines
# is found this too is printed
EOF
if [[ $OS == "Darwin" ]]; then
    DELIMIT="\\0";
else
    DELIMIT="\"\"";
fi
paste -d $DELIMIT $PREFIX $INFILE $SUFFIX >> $OUTFILE;
echo "{records++}" >> $OUTFILE;
echo "END{if (records != $SIZE) {printf(\"%s lines\\n\", records)}}" >> $OUTFILE;
rm $PREFIX;
rm $SUFFIX;
