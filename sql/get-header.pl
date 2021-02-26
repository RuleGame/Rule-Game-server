#!/usr/bin/perl 
#-----------------------------------------------------------------------
# This script gets the column names for a specified table, and
# produces a header line for use in a CSV file.
#
# Usage:
#  get-header.pl TableName
#  get-header.pl TableName ",extra,fields"
#-----------------------------------------------------------------------

my $table =  $ARGV[0];
my $extra = (defined $ARGV[1]? $ARGV[1]: "");

my @lines = `mysqlshow game $ARGV[0] | grep '^|' | grep -v '^| Field' `;
@lines = map  {s/^\| (\S+)\b.*\n/$1/; $_}  @lines;
print '#' . join(",", @lines) . "$extra\n";

