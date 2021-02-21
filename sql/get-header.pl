#!/usr/bin/perl 
#-----------------------------------------------------------------------
# This script gets the column names for a specified table, and
# produces a header line for use in a CSV file
#-----------------------------------------------------------------------

my @lines = `mysqlshow game $ARGV[0] | grep '^|' | grep -v '^| Field' `;
@lines = map  s/^\| (\S+)\b.*\n/$1/ @lines;
print join(",", @lines);
print "\n";

