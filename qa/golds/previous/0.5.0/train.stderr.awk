# this is an awk script to verify the lines of a file. If a line is
#  incorrect its number is printed, and if the wrong number of lines
# is found this too is printed
NR==1 && $0 !~ /^This is Tiburon, version [0-9.]+/ {print NR}
NR==2 && $0 !~ "Cross entropy with normalized initial weights is 1.2535263[0-9]+; corpus prob is e\\^-3672.832184[0-9]+" {print NR}
NR==3 && $0 !~ "Cross entropy after 1 iterations is 0.979164998[0-9]+; corpus prob is e\\^-2868.953444[0-9]+" {print NR}
NR==4 && $0 !~ "Cross entropy after 2 iterations is 0.95112754[0-9]+; corpus prob is e\\^-2786.803699572[0-9]+" {print NR}
NR==5 && $0 !~ "Cross entropy after 3 iterations is 0.9388194[0-9]+; corpus prob is e\\^-2750.7409854[0-9]+" {print NR}
NR==6 && $0 !~ "Cross entropy after 4 iterations is 0.932701245[0-9]+; corpus prob is e\\^-2732.814648[0-9]+" {print NR}
NR==7 && $0 !~ "Cross entropy after 5 iterations is 0.92952982[0-9]+; corpus prob is e\\^-2723.522[0-9]+" {print NR}
NR==8 && $0 !~ "Cross entropy after 6 iterations is 0.927752888[0-9]+; corpus prob is e\\^-2718.315963[0-9]+" {print NR}
NR==9 && $0 !~ "Cross entropy after 7 iterations is 0.9266454[0-9]+; corpus prob is e\\^-2715.071076[0-9]+" {print NR}
NR==10 && $0 !~ "Cross entropy after 8 iterations is 0.925891589[0-9]+; corpus prob is e\\^-2712.86235577[0-9]+" {print NR}
NR==11 && $0 !~ "Cross entropy after 9 iterations is 0.9253374[0-9]+; corpus prob is e\\^-2711.238745[0-9]+" {print $0}
{records++}
END{if (records != 11) {printf("%s lines\n", records)}}
