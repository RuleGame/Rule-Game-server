#!/usr/bin/perl

#prepared-5-cm_RBKY_cw_0123-260220-1805.txt
#prepared-5-ordRevOfL1_Remotest-260220-1808.txt
#prepared-5-special-ordBou-260208-1622.txt
#prepared-5-cm_RBKY_cw_0123-260221-2012-seed6.txt

# Human runs
#ruleSetName,playerId,experimentPlan,trialListId,seriesNo,episodes,moves,trainingMoves,xFactor,botClearedTestBoards,botAllTestBoards
# Random runs
#ruleSetName,seed,experimentPlan,trainingMoves,botClearedTestBoards,botAllTestBoards

print "#ruleSetName,seed,trainingEpisodes,trainingMoves,botClearedTestBoards,botAllTestBoards\n";

#prepared-5-ordRevOfL1_Remotest-260220-1809.txt
#prepared-5-special-ordBou-260208-1624.txt
#prepared-5-cm_RBKY_cw_0123-260221-2012-seed6.txt
my $xx = "x4";
my $seedBase=100; #-- substitute for missing seed value
for my $line (`grep -H Overall prepared-*.txt`) {
    $line =~ s/\s+$//;
    $line =~ /^(.*?):\s*(.*)/ or die "Cannot parse line: $line";       
    my ($file,$datum) = ($1,$2);
    my $n,$rule,$rest;
    if ($file =~ /-special-/) {
	$file =~ /prepared-(\d+)-special-([0-9a-zA-Z_].*?)-(.+).txt/ or die "Cannot parse file name: $file";
	($n,$rule,$rest) = ($1,"special-$2",$3);
	next;
    } else {
	$file =~ /prepared-(\d+)-([0-9a-zA-Z_].*?)-(.+).txt/ or die "Cannot parse file name: $file";
	($n,$rule,$rest) = ($1,$2,$3);
    }
    
    my $seed =     ($rest =~ /seed(\d+)/) ?$1 : $seedBase++;
    
    $datum =~ /Overall, cleared boards: (.*), good moves: (.*)/ or die "Cannot parse datum: $datum";       
    my ($goodB, $allB)  = split('/', $1);

    my $s = `grep -c  '^MOVE' $file`;
    $s =~ s/\s+$//;
    my @q = ($rule, $seed, $n, $s, $goodB, $allB);
    print join(",", @q) ."\n";
}
