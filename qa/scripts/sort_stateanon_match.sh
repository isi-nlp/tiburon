#!/usr/bin/env bash

# files should match when sorted if we're agnostic about states (return 0). Diff to stderr and return 1 otherwise
# only useful when we've made states anonymized (q\d+)

# used to ensure sort order
LANG=C;

A=$1;
B=$2;

SA=`mktemp -t sortmatcha.XXXXXX`;
SB=`mktemp -t sortmatchb.XXXXXX`;

sed 's/q[0-9][0-9]*/qX/g' $A | sort > $SA;
sed 's/q[0-9][0-9]*/qX/g' $B | sort > $SB;
RES=`diff $SA $SB | grep ^[0-9]`;
rm $SA;
rm $SB;
if [ -n "$RES" ]; then
    echo "$RES" 1>&2;
    exit 1;    
else
    exit 0;
fi;
