#!/usr/bin/perl

use strict;
my @lines = `grep -r Overall .`;

#seed-5/gemini-special_spiralInward-seed5.txt:Victory: mastery demonstrated! All 3 episodes have 35 moves. lastStretch=10, lastR=2177280.0

print "#Path,rule,won,trainEpisodes,trainMoves,testBoards\n";


foreach my $line (@lines) {
    $line =~ m|(.*/(gemini.*txt)):| or next;
    my ($path, $f) = ($1,$2);
    if ($path =~ /error/) { next; }

    #--- rule name
    $f =~/^gemini-(.*)-seed./ or die "Cannot parse file name: $f\n";
    my $rule = $1;

    #--- test boards
    # Overall, cleared boards: 0/5, good moves: 15/45
    my $s =  `grep  "Overall" $path`;
    $s =~ m|cleared boards: (\d/\d), good moves: (\d+/\d+)| or die "Cannot parse Overall line in $path: $s\n";
    my $testBText = $1;
    
    #--- episodes and moves
    $s =  `grep -c "Logged episode" $path`;
    $s =~ s/\s+$//;
    $s =~ /^\s*(\d+)$/ or  $s =~ /:\s*(\d+)$/ or die "Cannot count episodes in $path: $s\n";
    my $nepi = $1;
    
    $s =  `grep "Request limit" $path`;
    $s =~ s/\s*$//;
    my $moves = undef;
    my $won = 0;
    if ($s ne "") {
	$s =~ /Request limit \((\d+)\) reached/ or die "Cannot parse line: $s\n";
	$moves = $1;
    } else {    
	my $svic = `grep -n Victory $path`;
	$won = ($svic =~ /^\s*(\d+):/ ) ? 1: 0;
	if ($won) {
	    my $nvic = $1;
	    # or die "No Victory line file $path";
	    my $hasMoves = (  $svic =~ /All (\d+) episodes have (\d+) moves/ );
	    if ($hasMoves) {
		($nepi,$moves) = ($1,$2);
	    } else {	    
		$moves = &countMoveLines($path, $nvic);
	    }
	} else {
	    my $sfin = `grep -n "Instructions for the final request" $path`;
	    
	    ($sfin =~ /^\s*(\d+):/ ) or die "Cannot find the final request line for $path\n";
	    my $nfin = $1;
	    $moves = &countMoveLines($path, $nfin);
	}
    }
    print "$path,$rule,$won,$nepi,$moves,$testBText\n";
}

sub countMoveLines($$) {
    my ($path, $nvic)  = @_;
    my $s = `wc $path`;
    $s =~ /(\d+)/ or die "Can't parse wc: $s\n";
    my $len = $1;
    ($len > 0) or die "Don't know what len means: $len";
    my $tail = $len - $nvic;
    $s = `tail -${tail} $path| grep -c '^MOVE'`;
    $s =~ /(\d+)/ or die "Can't parse tail grep: $s\n";
    my $moves = $1;
    return $moves;
}


