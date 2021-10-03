#!/usr/bin/perl 


# Based on the dat file produced by gnu-z-w.pl


use strict;

my $summary = 'summary-flat.csv';

#-- only keep MTurk entries, A12 or A13 
`egrep  '^[-/a-zA-Z0-9_]+,A[0-9A-Z]+,' $summary > s.tmp`;

#-- What rule sets are represented in the summary file?
my @rules = `cut -d , -f 1 s.tmp | sort | uniq`;

open( GNU, ">cmd-dy-vs-dp.gnu");
print GNU "set grid xtics ytics;\n";
print GNU "set xlabel 'delta y = Y2-Y1';\n";
print GNU "set ylabel 'delta p = p(n-1)-p(0)';\n";

my $win = 0;

#-- Prepare a separate file for each rule set
foreach my $rule (@rules) {
    $rule =~ s/\s+$//;
       my $r=$rule;
    $r =~ s|/|-|g;
    my $rr = $r;
    $rr =~ s/_/-/g;
    my $gname =  "Z-C-W-$r.dat";

    print GNU "set term x11 $win;\n";
    print GNU "set title 'Delta Y vs delta p - $rr';\n";
    print GNU "plot '$gname' ". 'using (($5)-($4)):(($3)-($1))' . " title '$rr'  with points pt 2, x with lines title 'dp=dy';\n";
    print GNU "set term x11 $win;\n";

    print GNU "set out 'dy-vs-dp-scatter-$r.png';\n";
    print GNU "set term png; replot; \n";

    $win++;
}

close(GNU);
