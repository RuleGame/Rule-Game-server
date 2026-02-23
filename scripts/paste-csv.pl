#!/usr/bin/perl

use strict;

#--------------------------------------------------------------------
# This script merges to file, using the values in the first column of each
# one as the key. Example:
#
# File 1:
# Name,V1,V2
# aaa,12,34
# bbb,56,78
# ccc,90,01
#
# File 2:
# Name,V3,V4
# ccc,z01,z02
# aaa,y01,y02
#
# Output:
# Name,V1,V2,V3,V4
# aaa,12,34,y01,y02
# bbb,56,78,,
# ccc,90,01,z01,z02
#--------------------------------------------------------------------

my ($f1,$f2) = @ARGV;

open(F, $f1) or die "Cannot read $f1\n";
my @lines1 = <F>;
close(F);

my $maxN1 = 0;
foreach my $s (@lines1) {
    $s =~ s/\s+$//;
    my $n = scalar( split /,/, $s);
    if ($n>$maxN1) { $maxN1 = $n; }
}

# print "File $f1 has $maxN1 columns\n";

open(F, $f2) or die "Cannot read $f2\n";
my @lines2 = <F>;
close(F);

my %h2 = (); 
my $maxN2 = 0;
foreach my $s (@lines2) {
    $s =~ s/\s+$//;
    my @a = split( /,/, $s);
    my $n = scalar( @a);
    if ($n>$maxN2) { $maxN2 = $n; }
    ($n>0) or next;
    $h2{$a[0]} = $s;
}

foreach my $s (@lines1) {
    $s =~ s/\s+$//;
    my @a = split( /,/, $s);
    while( scalar(@a) <$maxN1) { push(@a, ""); }
    my $z = $h2{$a[0]};
    my @b = ();
    if (defined $z) {
	@b = split( /,/, $z);
	shift @b;
    }
    while( scalar(@b) <$maxN2-1) { push(@b, ""); }
    push(@a,@b);
    print join(",", @a) . "\n";
}
