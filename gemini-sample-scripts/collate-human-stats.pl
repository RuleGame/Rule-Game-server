#!/usr/bin/perl
use strict;

#--------------------------------------------------------------------------------
#vmenkov-ThinkPad-X1-Yoga-Gen-5:~/w2020/gemini_logs_7/human-naive/tmp> head summary-flat.csv
#ruleSetName,playerId,experimentPlan,trialListId,seriesNo,yy,B,C,t_I,k,Z,n,L/n,AIC/n,episodes,moves,sec
#FDCL/basic/cm_RBKY,JF-1024a,FDCL/basic,basic-01-A,0,1 0 0 0 1 1 1 1 0 0 1 0 1 0 1 1 1 0 1,0.25090121186765,0.6664932147386826,3.494545576959727,9.562719203941633,0.2509012118676513,19,-0.6214141377610769,1.8142568469507252,1,19,131.979

#* FROM summary-flat:
#ruleSetName,playerId,experimentPlan,trialListId,seriesNo,episodes,moves,

#NEED: trainingMoves,xFactor,botClearedTestBoards,botAllTestBoards
#* moves in transcript
#* x0/x4
#* bot's clearedTestBoards
#* bot's allTestBoards
#--------------------------------------------------------------------------------

my $flatFile = "tmp/summary-flat.csv";
open(F, $flatFile) or die "Cannot read $flatFile\n";
my $cnt = 0;
my @flatHeader = ();
my %columnPos = ();
my %flat = ();
foreach my $line (<F>) {
    $line =~ s/\s+$//;
    my @cells = split(/,/, $line);
#    print "LINE $line; ".scalar(@cells) ." CELLS\n";
    if ($cnt==0) {
	$line =~ /^#/ or die "No header?\n";
	$cells[0] =~ s/^#//;
	for(my $i=0; $i<=$#cells; $i++) {
	    $columnPos{$cells[$i]}  = $i;
	}
    } elsif (scalar(@cells)==0) {
	next;
    } else {
	my $key = join(",", @cells[0..1]);
#	print "KEY $key\n";
	$flat{$key} = \@cells;	 
    }
    $cnt++;
}
close(F);

#foreach my $key (keys %flat) {
#    my @cells = @{$flat{$key}};
#    print "$key : ". join("+", @cells) . "\n";
#}



print "#ruleSetName,playerId,experimentPlan,trialListId,seriesNo,episodes,moves,";
print "trainingMoves,xFactor,botClearedTestBoards,botAllTestBoards\n";

#-- prepared-sm_csqt-x4-prolific-674356a24a8d722d903654ac-673520973e45f02809e56f00.txt
my $xx = "x4";
for my $line (`grep -H Overall prepared-*-${xx}-*.txt`) {
    $line =~ s/\s+$//;
    $line =~ /^(.*?):\s*(.*)/ or die "Cannot parse line: $line";       
    my ($file,$datum) = ($1,$2);
    $file =~ /prepared-(.*?)-x(\d)-(.*).txt/ or die "Cannot parse file name: $file";       
    my ($rule,$xFactor,$player) = ($1,$2,$3);

#    $ruleSet{$rule} ++;
    $datum =~ /Overall, cleared boards: (.*), good moves: (.*)/ or die "Cannot parse datum: $datum";       
    my ($goodB, $allB)  = split('/', $1);
    my $key="FDCL/basic/$rule,$player";
    my $pf = 	$flat{$key};
    defined $pf or die "No summary data for key=$key\n";
    my @cells = @{$pf};

    my @q = ($rule, $player);
    push @q, map { $cells[$columnPos{$_}]} qw(experimentPlan trialListId seriesNo episodes moves);
    my $s = `grep -c  '^MOVE' $file`;
    $s =~ s/\s+$//;
    push @q, ($s, $xFactor, $goodB, $allB);
    print join(",", @q) ."\n";
}
