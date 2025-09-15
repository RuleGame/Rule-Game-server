#!/usr/bin/perl

use strict;

#---------------------------------------------------------------------
# Creates a cumulative table of mStar for Gemini "play" runs.
# The data are the same that would be obtained with running for each file
# something like this:
# grep '^At.*, Moving' gemini-cw_0123-250615-0029.txt | sed 's/.*Code=//'
#---------------------------------------------------------------------
# cd /home/vmenkov/gemini/gemini-2.5-flash-logs
# gemini-mastery.pl
# cat mastery.csv
#---------------------------------------------------------------------


my @targets = (10, 25);

my %allRules = ();
#-- keys are "$rule.$target";
my %h = (); 

foreach my $target (@targets) {
    my $dir = "targetStreak-$target";
    (-d $dir) or die "Directory does not exist: $dir";
    opendir my $D, $dir or die "Cannot open directory: $dir\n";
    my @files = readdir $D;
    closedir $D;
    @files = grep( /^gemini-.*.txt$/, @files);
    foreach my $f (@files) {
	open( F, "$dir/$f") or die "Cannot read file $dir/$f\n";
	my @lines = <F>;
	close(F);

	$f =~ /^gemini-(.*)\.txt$/ or die "Cannot extract rule set name from file name: $f\n";
	my $rule = $1;
	$rule =~ s/(-\d+)+//; #-- strip date and time suffixes, if any
	$allRules{$rule} = 1;
	
	my @codes = map( /^At.*, Moving.*Code=(\d+)/ ? $1: (), @lines);
	print "For file $dir/$f: codes=" . join(" ", @codes) . "\n";
	my $streak = 0;
	my $mStar = undef;
	foreach my $j (0..$#codes) {
	    my $code = $codes[$j];
	    if ($code == 0) {
		$streak ++;
	    } else {
		$streak = 0;
	    }
	    if ($streak == $target) {
		$mStar = $j + 2 - $target;
		last;
	    }
	}
	(defined $mStar) or $mStar = "Infinity";
	print "mStar($rule,$target)=$mStar\n";
	my $key = "$rule.$target";
	$h{$key} = $mStar;

	
    }

    my $out = "mastery.csv";
    open( G, ">$out") or "Cannot write to $out\n";
    print "Writing summary to $out\n";
    print G "#ruleId,mStar10,mStar25\n";
    foreach my $rule (sort keys %allRules) {
	my @v = ($rule);
	foreach my $target (@targets) {
	    my $key = "$rule.$target";
	    push @v, (defined $h{$key}? $h{$key}: "");
	}
	print G  join(",", @v) . "\n";
    }
    close(G);
}
 
