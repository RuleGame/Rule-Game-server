#!/usr/bin/perl -s
use strict;

#-----------------------------------------------------------------
# For details, see PK/VM email discsussion (starting 2020-10-10), subject:
# 'GOHR A question for the "minimal competent models"'
#

# PK: ... I think I want to compare "does not try again, although he should" and "does try again."
#
# VM: I plan to categorize all actions (other than the last one, which is not followed by another action) into 3 groups, [doesTryAgain] , [doesNotTryAgain],  [other] as follows:

#(a) Failed pick, followed by an attempt at a different piece. [other] (The only correct action)
#(b) Failed pick, followed by another attempt at the same piece.  [doesTryAgain]  (Misguided: probably results from misunderstanding, poor memory, or a slip of the fingers)
#(c) Successful pick, followed by an attempt at a different piece. [doesNotTryAgain]  (of course successful picks are a strange action; from my point of view, they are either misguided, or a slip of the fingers; but as you say, may be part of a strategy)
#(c) Successful pick, followed by an attempt at the same  piece. [doesTryAgain] 
#(d) Failed move,  followed by an attempt at a different piece. [doesNotTryAgain]
#(e) Failed move,  followed by an attempt at the same piece. [doesTryAgain]
#(d) Successful move,  followed by an attempt at a different piece. [other]  (no other choice here, unless the player likes grabbing at empty cells)


#-- [other] = (failed pick || successful move) && (different piece)

#playerID  experience  doesTryAgain  doesNotTryAgain
#string     string"+"string  count              count

#playerID,experimentPlan,ruleSetName,precedingRules,doesTryAgain,doesNotTryAgain,other
#--------------------------------------------------------------------

my $verbose = $::verbose? 1:0;

#my ($f) = @ARGV;

my $out = (defined $::out? $::out: "out.csv");
open(G, ">$out") or die "Cannot write to $out\n";
print G "#playerID,experimentPlan,ruleSetName,precedingRules,doesTryAgain,doesNotTryAgain,other\n";


my ($other, $doesTryAgain, $doesNotTryAgain)=(0,0,0);

my $fcnt = 0;
foreach my $f (@ARGV) {
    $f =~ s|/+$||;    
    if (-d $f) {
	my @list = `find $f -name '*.split-transcripts.csv'`;
	foreach my $g (@list) {
	    $g =~ s/\s+$//;
	    $verbose and print "# File $g\n";
	    &doFile($g);
	}
    } else {    
	$verbose and print "# File $f\n";
	&doFile($f);
    }
}

close(G);

print "# Processed $fcnt files; results in $out\n";

sub doFile($) {
    my ($f) = @_;
    $fcnt ++;

    #-- first, let's figure the correct column numbers for the columns we need
    my @names = ('ruleSetName', 'playerId', 'experimentPlan', 'trialListId',
		 'episodeId',
		 'y', 'x', 'by', 'bx', 'code', 'precedingRules');

    my %colNo = ();

    foreach my $name (@names) {
	$colNo{$name} = -1;
    }

    open(F, "<$f") or die "Cannot read file $f\n";

    my $header = <F>;
    if ($header =~ /^#/) {
	$header = $'
    } else {
	die "Invalid header in file $f (does not beging with a #): $header";
    }
    $header =~ s/\s+$//;
    my @foundNames = split(/,/, $header);
    foreach my $j (0..$#foundNames) {
	my $name = $foundNames[$j];
	if (defined $colNo{$name}) {
	    $colNo{$name} = $j;
	}	
    }

    foreach my $name (@names) {
	if ($colNo{$name} < 0) { 
	    die "The header of file $f contains no column named '$name'\n";
	}			 
    }

    ($other, $doesTryAgain, $doesNotTryAgain)=(0,0,0);
    my $s =undef;
    my %vals=();
    
    while(defined( $s = <F>)) {
	$s =~ s/\s+$//;
  
	my @cols = split(/,/, $s);
	my %valsNext = ();
	foreach my $name (@names) {
	    $valsNext{$name} = $cols[ $colNo{$name}];	
	}

	
	if (scalar(%vals)>0) {

	    if ($vals{'ruleSetName'}  ne $valsNext{'ruleSetName'}) {
		$verbose and print ("# New rule set\n");
		&outLine(\%vals);
		    
	    } elsif ($vals{'episodeId'} ne $valsNext{'episodeId'}) {
		$verbose and print ("# New episode: ".$valsNext{'episodeId'}."\n");
	    } else {
		#-- compare
		my $isPick = ($vals{'bx'} eq '') && ($vals{'by'} eq '');
		my $success = ($vals{'code'} eq  0);
		my $same = ($vals{'x'} eq $valsNext{'x'}) && ($vals{'y'} eq $valsNext{'y'});
		$verbose and print ("# " . ($isPick? "PK " : "MV ") . ($success? "ok" : "fail") ."\n");
		
		
		#-- [other] = (failed pick || successful move) && (different piece)
		
		if ($same) {
		    $doesTryAgain++;
		} elsif (($isPick && !$success) || (!$isPick && $success)) {
		    $other++;
		} else {
		    $doesNotTryAgain++;
		}

	    }

	} else {
	    $verbose and print "# Start file\n";
	}
	%vals=%valsNext;
		
    }

    $verbose and print "# other=$other, doesTryAgain=$doesTryAgain, doesNotTryAgain=$doesNotTryAgain\n";
    &outLine(\%vals);

    
    close(F);
    
}

sub outLine($) {
    my ($p) = @_;
    my %vals = %{$p};
    print G (join(",", $vals{'playerId'},$vals{'experimentPlan'},$vals{'ruleSetName'},$vals{'precedingRules'},$doesTryAgain,$doesNotTryAgain,$other) . "\n");
    $doesTryAgain=$doesNotTryAgain=$other=0;
		
}
