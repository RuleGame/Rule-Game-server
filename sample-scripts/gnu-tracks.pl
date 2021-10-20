#!/usr/bin/perl 



#---------------------------------------------------
# Draws all players' tracks
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


open( GNU, ">cmd-tracks.gnu");
print GNU "set grid xtics ytics;\n";
print GNU "set xlabel 'successful moves';\n";
print GNU "set ylabel 'attempts per move';\n";

my $win = 0;

#-- ignore shorter transcripts than that
my $minN = 10;

#-- Prepare a separate file for each rule set
foreach my $rule (@rules) {
    $rule =~ s/\s+$//;
    
#-- Select the rows for this rule, and only keep parameter columns entries
#    `grep "^$rule," s.tmp  > "rule-$rule.tmp"`;
    `grep "^$rule," s.tmp | cut -d , -f 2,6-  > pid-param.tmp`;    
    
    print "For rule=$rule:" . `wc pid-param.tmp`;
    
    my $r=$rule;
    $r =~ s|/|-|g;
    my $rtitle = $rule;
    $rtitle =~ s/_/-/g;
    my $gname =  "tracks-$r.dat";



    open(G, ">$gname");

    my @allPid = ();
    my %pidToAvgY=();


    open(F, "<pid-param.tmp");
    while(<F>) {
	#ruleSetName,playerId,experimentPlan,trialListId,seriesNo,yy,B,C,t_I,k,Z,n,L/n,AIC/n
	
	my ($playerId,$yy,$B,$C,$t_I,$k,$Z,$n,$Ln,$AICn) = split /,/;
	my @yval = split / +/, $yy;
	(scalar(@yval) == $n) or die "yy has " + scalar(@yval) + " values, while n=$n!\n";

	@allPid = (@allPid, $playerId);
	$pidToAvgY{$playerId}= 	&avgY(@yval);	    
    }
    close(F);

    my $cnt=scalar(@allPid);


    
    #-- Sort players by avgY (ascending)
    @allPid = sort {$pidToAvgY{$a} <=>$pidToAvgY{$b}} @allPid;

    #-- Divide all players in groups 1, 2, 3, according to their avgY
    my %pidToGroup = ();
    my $ng = 3;
    foreach my $j (0..$#allPid) {
	my $pid = $allPid[$j];
	my $group = 1 + int(($j*$ng )/scalar(@allPid));
	$pidToGroup{$pid} = $group;
    }

    #-- index=0 for the avg value, followed by index=1,2..,ng for the ng groups
    my @sumOne=();
    my @sumSy =();
    foreach my $j (0..$ng) {
	$sumOne[$j] = [];
	$sumSy[$j] = [];
    }
    

    
    open(F, "<pid-param.tmp");
    while(<F>) {
	#ruleSetName,playerId,experimentPlan,trialListId,seriesNo,yy,B,C,t_I,k,Z,n,L/n,AIC/n
	
	my ($playerId,$yy,$B,$C,$t_I,$k,$Z,$n,$Ln,$AICn) = split /,/;

	
	#-- the player's track
	my @yval = split / +/, $yy;
	my $goodCnt=0;
	my $sy = 0;
	while(scalar(@yval)>0) {
	    my $q = shift @yval;
	    $sy++;
	    if ($q == 1) {
		$goodCnt++;
		#-- each player's curve
		#print G "$goodCnt\t$sy\n";

		my $group = 	$pidToGroup{$playerId};

		foreach my $k (0,$group) {
		    if (!defined $sumOne[0]->[$goodCnt]) { 
			$sumOne[$k]->[$goodCnt]=0; 
			$sumSy[$k]->[$goodCnt] =0;
		    }
		    $sumOne[$k]->[$goodCnt] ++;
		    $sumSy[$k]->[$goodCnt] += $sy;
		}
		
		
		$sy = 0;		
	    }
	}
	#print G "\n";
    }
    close(F);

    #print join(", ", @sumOne) . "\n";
    #print join(", ", @sumSy) . "\n";

    #-- avg curve
    foreach my $k (0..$ng) {
	foreach my $j (1..$#{$sumOne[$k]}) {
	    my $sy =  $sumSy[$k]->[$j];
	    my $ones =  $sumOne[$k]->[$j];
	    print G join("\t", ($j, $sy/$ones)) . "\n";
	}
	print G "\n\n";
    }
    
    close(G);

    print GNU "set term x11 $win;\n";
    print GNU "set title 'Average players tracks - $rtitle, $cnt players';\n";
#    print GNU "plot '$gname' ". 'using ($1):($2)' . " title 'Tracks' with lines;\n";
    print GNU "plot '$gname' index 0 ". 'using ($1):($2)' . " title 'All players' with lines lw 5";
    foreach my $k (1..$ng) {
	#my $lc = $k;
	#if ($k>=1) {$lc++;} #-- skip some colors
	print GNU ", '$gname' index $k ". ' using ($1):($2)' . " title 'Group $k' with lines lw 2";
    }
    print GNU ";\n";


    print GNU "set term x11 $win;\n";

    print GNU "set out 'tracks-$r.png';\n";
    print GNU "set term png size 800,640; replot; \n";

    $win++;
}

close(GNU);
