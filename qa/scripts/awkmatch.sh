#!/usr/bin/env bash

# the data file (DATA) should validate against the awk script (SCRIPT)
# resulting in empty content. If the file has any line numbers they
# are reported as incorrect lines
SCRIPT=$1;
DATA=$2;
TEMPFILE=`mktemp -t awkmatch.XXXXXX`;
awk -f $SCRIPT $DATA > $TEMPFILE;
SIZE=`wc -l $TEMPFILE | awk '{print $1}'`;
if [ $SIZE -eq 0 ]; then
    rm $TEMPFILE;
    exit 0;
else
    echo "Problems with the following lines and/or number of lines:" 1>&2;
    cat $TEMPFILE 1>&2;
    rm $TEMPFILE;
    exit 1;    
fi;
