# this is an awk script to verify the lines of a file. If a line is
#  incorrect its number is printed, and if the wrong number of lines
# is found this too is printed

# look for a start state on line 1 and 8 rules with values between 0.2
# and 0.8
BEGIN { FS=" # "; }
NR==1 && $0 !~ /^[^ ]+[ \t\n\r]*$/ {print NR}
NR>1 && ($1 !~ /^[^ ]+ -> / || $2 < 0.2 || $2 > 0.8) {print NR}
{records++}
END{if (records != 9) {printf("%s lines\n", records)}}

