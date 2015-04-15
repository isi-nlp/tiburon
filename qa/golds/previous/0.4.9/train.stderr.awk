# this is an awk script to verify the lines of a file. If a line is
#  incorrect its number is printed, and if the wrong number of lines
# is found this too is printed
NR==1 && $0 !~ /^This is Tiburon, version [0-9.]+/ {print NR}
NR==2 && $0 != "Cross entropy with normalized initial weights is 1.2535263427858514; corpus prob is e^-3672.832184362545" {print NR}
NR==3 && $0 != "Cross entropy after 1 iterations is 0.9791649980369791; corpus prob is e^-2868.9534442483487" {print NR}
NR==4 && $0 != "Cross entropy after 2 iterations is 0.9511275425161232; corpus prob is e^-2786.803699572241" {print NR}
NR==5 && $0 != "Cross entropy after 3 iterations is 0.9388194489518161; corpus prob is e^-2750.740985428821" {print NR}
NR==6 && $0 != "Cross entropy after 4 iterations is 0.9327012451068297; corpus prob is e^-2732.814648163011" {print NR}
NR==7 && $0 != "Cross entropy after 5 iterations is 0.9295298289662491; corpus prob is e^-2723.52239887111" {print NR}
NR==8 && $0 != "Cross entropy after 6 iterations is 0.9277528885988403; corpus prob is e^-2718.315963594602" {print NR}
NR==9 && $0 != "Cross entropy after 7 iterations is 0.9266454184930059; corpus prob is e^-2715.071076184507" {print NR}
NR==10 && $0 != "Cross entropy after 8 iterations is 0.9258915890000021; corpus prob is e^-2712.862355770006" {print NR}
NR==11 && $0 != "Cross entropy after 9 iterations is 0.925337455684217; corpus prob is e^-2711.2387451547556" {print NR}
{records++}
END{if (records != 11) {printf("%s lines\n", records)}}
