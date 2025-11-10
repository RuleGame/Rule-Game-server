#!/usr/bin/perl -s
use strict;

#-----------------------------------------------------------------
# This script can be used to modify an mStar export file produced by
# analyze-transcripts-mwh.sh, adding the "bot assist" info to the
# name of each rule set. This will allow the subsequent analysis
# to distinguish the experiences of human players who played a certain
# rule with bot assist from that of the players who playes the same
# rule without bot assist.
#-----------------------------------------------------------------
# Example:
#  /home/vmenkov/w2020/game/scripts/analyze-transcripts-mwh.sh -precMode Every -export every.csv Bot_4
#  /home/vmenkov/w2020/game/scripts/analyze-transcripts-mwh.sh -precMode EveryCond -export everyCond.csv Bot_4
#  /home/vmenkov/w2020/game/scripts/mwh-add-bot-to-rule.pl every.csv Bot_4
#  /home/vmenkov/w2020/game/scripts/mwh-add-bot-to-rule.pl everyCond.csv Bot_4
#  then use every.out.csv in subsequent analysis
#
# One can also include multiple experiment names on the command line, if
# they are all involved in the data set under study
#-----------------------------------------------------------------

my @aa = @ARGV;
my $inputFile = shift @aa;

my $baseDir = "/opt/w2020/game-data/trial-lists";
my %flags = ();

foreach my $exp (@aa) {
#    if ($expDir !~ m|^/| && $expDir !~ m|^\.| ) {
	#-- no leading slash or dot in expDir, so this is probably just a plan name
    my $expDir = "$baseDir/$exp";
 #   }
    print "Reading all trial list files from $expDir ...\n";
    (-d $expDir) or die "No directory found: $expDir\n";
    opendir my $dir, $expDir or die "Cannot open directory: $expDir";
    my @files = readdir $dir;
    closedir $dir;
    # print join("\n", @files);
    foreach my $x (@files) {
	($x =~ /\.csv$/) or next;
	&readTrialList($exp, $x);
    }

    foreach my $key (keys %flags) {
	print "flag( $key )=". $flags{$key} . "\n";
    }
    
}

($inputFile =~ /\.csv$/) or die "Input file name ($inputFile) does not end in .csv\n";
my $outFile = $inputFile;
$outFile =~ s/.csv$/.out.csv/;
print "Input = $inputFile\n";
print "Output= $outFile\n";

open(F, $inputFile) or die "Cannot read $inputFile\n";
open(G, ">$outFile") or die "Cannot write to $outFile\n";

my $s;


my $colRule;
my $colPreceding;
my ($colExp, $colTrialList, $colSeriesNo);

for(my $lineNo=0; defined($s = <F>); $lineNo++) {
    $s =~ s/\s+$//;
    
    #ruleSetName,precedingRules,exp,trialListId,seriesNo,playerId,learned,total_moves,total_errors,mStar,mDagger

    if ($lineNo==0) {
	my $header = $s;
	$header =~ s/^#//;
	my %colNos = %{&parseHeaderLine($header)};
	$colRule = $colNos{ 'ruleSetName'};
	$colPreceding = $colNos{'precedingRules'};
	$colExp = $colNos{ 'exp'};
	$colTrialList = $colNos{ 'trialListId'};
	$colSeriesNo = $colNos{ 'seriesNo'};
	(defined $colRule) && (defined $colPreceding) && (defined $colExp) && (defined $colTrialList)
	    && (defined $colSeriesNo) or die "Some column headers are missing in $inputFile\n";
	
	print "Key column numbers are: ".join(", " , ($colExp, $colTrialList, $colSeriesNo)) . "\n";

    } else {
	#print "s=$s\n";
	my @fields = split(/,/, $s);
	#print "$colExp : " . $fields[$colExp] ."\n";
	my $key = join(".", ($fields[$colExp], $fields[ $colTrialList], $fields[$colSeriesNo]));
	my $flag = $flags{$key};
	#print "key=$key, flag=$flag\n";
	#die;
	if (defined $flag) {
	    $fields[$colRule] .= "_b${flag}";
	}
	
	if ($fields[$colPreceding] ne '') {
	    my @p = split(":", $fields[$colPreceding]);
	    foreach my $j (0..$#p) {
		$key = join(".", ($fields[$colExp], $fields[ $colTrialList], $j));
		$flag = $flags{$key};
		if (defined $flag) {
		    $p[$j] .= "_b${flag}";		
		}
	    }
	    $fields[$colPreceding] = join(":", @p);
	}
	$s = join(",", @fields);
    }

    
    print G "$s\n";
}


close(F);
close(G);


#---------------------------------------------------------------------------------------
# rule_id,max_boards,min_points,max_points,min_objects,max_objects,min_shapes,max_shapes,min_colors,max_colors,b,feedback_switches,stack_memory_show_order,grid_memory_show_order,give_up_at,x2_likelihood,x4_likelihood,pregame,images,bot_assist,pseudo_halftime
# FDCL/basic/cm_KRBY,5,5,10,9,9,4,4,4,4,1.5,free,FALSE,FALSE,6,1000,1000000,Bot_assist,,pseudo,16

# bixi-Latitude-E5420:~/w2020/game/scripts> cut -d , -f 1,20- /opt/w2020/game-data/trial-lists/Bot_3_ph16/*
# rule_id,bot_assist,pseudo_halftime
# FDCL/basic/cm_KRBY,pseudo,16
# FDCL/basic/shaOrdL1_qcts,pseudo,16
# FDCL/basic/quadNearby,,

sub readTrialList($) {
    my ($exp, $trialListFile) = @_;

    my $f = "$baseDir/$exp/$trialListFile";
    (-f $f) or return;
    my $trialList = $trialListFile;
    $trialList =~ s/\.csv$//;


    
    print "  Reading trial list file $f\n";
    open(F, $f) or die "Cannot read file $f\n";
    #-- the first line is supposed to be a header line, starting with "rule_id"
    my $header = <F>;
    defined $header or die "No header in $f\n";
    my %colNos = %{&parseHeaderLine($header)};
    my $colBot = $colNos{ 'bot_assist'};
    my $colH = $colNos{'pseudo_halftime'};
    if (!defined $colBot) {
	print "Warning: Could not find 'bot_assist' column in the header line of $f. Skipping the file\n";
	return;
    }
    if (!defined $colH) {
	print "Warning: Could not find 'pseudo_halftime' column in the header line of $f. Skipping the file\n";
	return;
    }
    
    my $s;
    my $seriesNo = 0;
    while(defined ($s =<F>)) {
	$s =~ s/#.*$//;
	$s =~ s/\s+$//;
	if ($s eq '') { next; }

	my @fields = split(/,/, $s);
	my $bot = $fields[ $colBot];
	my $h = $fields[ $colH];
	if ($bot ne '') { 
	    ($bot eq 'pseudo') or die "Unexpected value bot_assist='$bot' in file $f, series $seriesNo\n";
	    if ($h eq '') { $h=8; }
	    ($h == 4 || $h == 8 || $h==16) or die "Unexpected value h='$h' in file $f, series $seriesNo\n";
	    my $key = "$exp.$trialList.$seriesNo";
	    $flags{$key} = $h;
	}
	$seriesNo ++;

    }
    
    
    #my @fields = split(/,/, $header);
    
    close(F);    
}

sub parseHeaderLine($) {
    my ($s) = @_;
    $s =~ s/\s+$//;
    my @fields = split(/,/, $s);
    my %colNos = ();
    foreach my $j (0..$#fields) {
	$colNos{ $fields[$j] } =$j;
    }
    return \%colNos;
}
