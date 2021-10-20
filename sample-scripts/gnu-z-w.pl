#!/usr/bin/perl 



#---------------------------------------------------
# Extracting data from summary files, in order to produce
# (Z,W) (i.e. (p(0),p(n-1))) scatter plots.
#---------------------------------------------------
# Usage:
# cut -d , -f 7- summary.csv > param.csv
# ./tmp.pl param.csv > Z-C-W.dat
#
#
# cut -d , -f 7- tmp-color.csv |./tmp.pl> color-Z-C-W.dat
# cut -d , -f 7- tmp-shape.csv |./tmp.pl> shape-Z-C-W.dat
#---------------------------------------------------


use strict;

sub avgY(@) {
    my @yy = @_;
    my $sum = 0;
    foreach my $y (@yy) { $sum += $y; }
    return $sum/scalar(@yy);
}


#-- List of all "good" (useful for analysis) player IDs.
my $goodPidFile = '/home/vmenkov/w2020/ellise/rule_all_rules_ppts_by_rule.csv';
my %goodPids = ();
open(F, "<$goodPidFile");
foreach my $line (<F>) {
    my @q = split(/,/, $line);
    my $pid = $q[0];
    if ($pid =~ /player/) { next; }
    $goodPids{$q[0]} = 1;
}
close(F);


my $summary = 'summary-flat.csv';

#-- only keep MTurk entries, A12 or A13 
`egrep  '^[-/a-zA-Z0-9_]+,A[0-9A-Z]+,' $summary > s0.tmp`;


#-- Only keep good players
open(F, "<s0.tmp");
open(G, ">s.tmp");
foreach my $line (<F>) {
    #ruleSetName,playerId,experimentPlan,trialListId,seriesNo,yy,B,C,t_I,k,Z,n,L/n,AIC/n
    my @q = split(/,/, $line);
    my $pid = $q[1];
    if (defined $goodPids{$pid})   {  print G $line;}
}
close(F);
close(G);



#-- What rule sets are represented in the summary file?
my @rules = `cut -d , -f 1 s.tmp | sort | uniq`;


open( GNU, ">cmd.gnu");
print GNU "set grid xtics ytics;\n";
print GNU "set xlabel 'p(0)=Z, or Y1';\n";
print GNU "set ylabel 'p(n-1), or Y2';\n";

my $win = 0;

#-- ignore shorter transcripts than that
my $minN = 10;

#-- Prepare a separate file for each rule set
foreach my $rule (@rules) {
    $rule =~ s/\s+$//;
    
#-- Select the rows for this rule, and only keep parameter columns entries
#    `grep "^$rule," s.tmp  > "rule-$rule.tmp"`;
    `grep "^$rule," s.tmp | cut -d , -f 6-  > param.tmp`;    
    
    print "For rule=$rule:" . `wc param.tmp`;
    
    open(F, "<param.tmp");
    my $r=$rule;
    $r =~ s|/|-|g;
    my $rr = $r;
    $rr =~ s/_/-/g;
    my $gname =  "Z-C-W-$r.dat";



    open(G, ">$gname");
    my $cnt=0;
    
    while(<F>) {
	#ruleSetName,playerId,experimentPlan,trialListId,seriesNo,yy,B,C,t_I,k,Z,n,L/n,AIC/n
	
	my ($yy,$B,$C,$t_I,$k,$Z,$n,$Ln,$AICn) = split /,/;

	#-- averages for the first and second halves of each transcript 
	my @yval = split / +/, $yy;
	(scalar(@yval) == $n) or die "yy has " + scalar(@yval) + " values, while n=$n!\n";
	my $nh = int($n/2);
	my $y1 = &avgY( @yval[0..$nh]);
	my $y2 = &avgY( @yval[$nh..$n-1]);

	
	if (($B ne 'B') && ($n >= $minN)) { 
	    
	    my $u = $k*($n-1.0-$t_I);
	    my $W = $B/(1+exp($u)) + $C/(1+exp(-$u));
	    my $defect = $C-$W;
	    $_ = join("\t", ($Z, $C, $W, $y1, $y2)) . "\n";
	    $cnt++;
	} else {
	    $_ = '';
	}
	print G $_;
    }
    close(G);

    print GNU "set term x11 $win;\n";
    print GNU "set title 'Learning curve end points - $rr, $cnt players';\n";
    print GNU "plot '$gname' ". 'using ($1):($3)' . " title 'Learning curve end points' with points pt 2, '$gname' ". 'using ($4):($5)' . " title 'Two halves of Y' with points pt 6, x with lines;\n";
    print GNU "set term x11 $win;\n";

    print GNU "set out 'z-w-scatter-$r.png';\n";
    print GNU "set term png; replot; \n";

    $win++;
}

close(GNU);
