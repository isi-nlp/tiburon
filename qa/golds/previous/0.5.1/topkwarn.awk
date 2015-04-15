# this is an awk script to verify the lines of a file. If a line is
#  incorrect its number is printed, and if the wrong number of lines
# is found this too is printed
NR==1 && $0 !~ /^This is Tiburon, version [0-9.]+/ {print NR}
NR==2 && $0 != "Warning: returning fewer trees than requested" {print NR}
{records++}
END{if (records != 2) {printf("%s lines\n", records)}}
