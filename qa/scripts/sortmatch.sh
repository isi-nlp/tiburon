#!/usr/bin/env bash

# files should match when sorted (return 0). Diff to stderr and return 1 otherwise
A=$1;
B=$2;

SA=`mktemp -t sortmatcha.XXXXXX`;
SB=`mktemp -t sortmatchb.XXXXXX`;
sort $A > $SA;
sort $B > $SB;
RES=`diff $SA $SB | grep ^[0-9]`;
rm $SA;
rm $SB;
if [ -n "$RES" ]; then
    echo "$RES" 1>&2;
    exit 1;    
else
    exit 0;
fi;
