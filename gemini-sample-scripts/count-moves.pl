#!/usr/bin/perl

use strict;
use Getopt::Long;


#-------------------------------------------------------------------------------------------------
# This script reads gemini-*-seed?.txt log files in the current directory tree, extracting from them
# statistical information about Gemini play-mode runs.
# Skipping files in "error" subdirectories.
# Options
# --long : to print more fields 
#-------------------------------------------------------------------------------------------------

my $long           = undef;
GetOptions ('long' => \$long);

#--------------------------------------------------------------------------------
my @lines = `grep -r Overall .`;

#seed-5/gemini-special_spiralInward-seed5.txt:Victory: mastery demonstrated! All 3 episodes have 35 moves. lastStretch=10, lastR=2177280.0

my $header = "#path,rule,won,trainEpisodes,trainMoves,testBoards";
if ($long) {
    $header .= ",move_logs";
}
   
print "$header\n";


foreach my $line (@lines) {
    $line =~ m|(.*/(gemini.*txt)):| or next;
    my ($path, $f) = ($1,$2);
    if ($path =~ /error/) { next; }

    #--- rule name
    $f =~/^gemini-(.*)-seed./ or die "Cannot parse file name: $f\n";
    my $rule = $1;
    $rule =~ s/-resume//;  #-- seen in some old run names

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

    my $movesReported = undef;
    my $tailStart = undef; #-- the line number where play ends and final request begins
    my ($moves, $codes) = (undef, undef);
    my $won = 0;

    
    #-- See if the run was ended on reaching the move limit 
    $s =  `grep -n "Request limit" $path`;
    $s =~ s/\s*$//;
    if ($s ne "") {
	$s =~ /^\s*(\d+):Request limit \((\d+)\) reached/ or die "Cannot parse line: $s\n";
	($tailStart, $movesReported) = ($1,$2);
    } else {    
	my $svic = `grep -n Victory $path`;
	$won = ($svic =~ /^\s*(\d+):/ ) ? 1: 0;
	if ($won) {
	    $tailStart = $1;
	    my $hasMoves = (  $svic =~ /All (\d+) episodes have (\d+) moves/ );
	    if ($hasMoves) {
		($nepi,$movesReported) = ($1,$2);
	    } 
	} else {
	    my $sfin = `grep -n "Instructions for the final request" $path`;
	    
	    ($sfin =~ /^\s*(\d+):/ ) or die "Cannot find the final request line for $path\n";
	    $tailStart = $1;
	}
    }
    
    ($moves,$codes) = &countMoveLines($path, $tailStart);
    if (defined $movesReported) {
	$movesReported == $moves or die "Mismatch in file $path: Victory line has moves=$movesReported, but found $moves MOVE lines in final request";
    }
	    

    print "$path,$rule,$won,$nepi,$moves,$testBText";
    if ($long) { print ",$codes";}
    print "\n";
}

#---------------------------------------------------------------------------------------------
#-- Counts "^MOVE" lines in the final request. This is identified as the tail end of the
#-- file, after the line No. $nvicMOVE 1 0 DENY
# MOVE 1 1 DENY
# MOVE 1 3 ACCEPT
# MOVE 2 3 IMMOVABLE
#---------------------------------------------------------------------------------------------
sub countMoveLines($$) {
    my ($path, $nvic)  = @_;
    my $s = `wc $path`;
    $s =~ /(\d+)/ or die "Can't parse wc: $s\n";
    my $len = $1;
    ($len > 0) or die "Don't know what len means: $len";
    my $tail = $len - $nvic;
    my @lines = `tail -${tail} $path| grep  '^MOVE'`;
#    $s =~ /(\d+)/ or die "Can't parse tail grep: $s\n";
    my $moves = scalar @lines;
    my @codes = ();
    my $cnt=0;
    foreach my $line (@lines) {
	$line =~ /^MOVE \d+ \d+ ([A-Z]+)\s*$/        or die "Cannot parse MOVE line in $path, pos $cnt in sec [$nvic,$len]: $line\n";
	my $w = $1;
	$w eq "ACCEPT" ||$w eq "DENY" ||$w eq "IMMOVABLE" or die "Cannot parse code=$w\n";
	$w =~ /^(.)/;
	push @codes, $1;
	$cnt++;
    }
    return ($moves, join(//, @codes));
}


