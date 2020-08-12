#!/usr/bin/perl

use strict;

#------------------------------------------
# Input:
#   private String id; 
# Output:
#   Get and Set methods
#-------------------------------------------

my $s;

while(defined($s=<>)) {
    $s =~ /^(\s*)/;
    my $indent = $1;
    $s =~ s/^\s+//;
    $s =~ s/\s+$//;
    ($s =~ /;$/) or next;
    $s =~ s/\s*;$//;
    $s =~ s/\s*=.*//;
    my @q = split( /\s+/, $s);
    while (scalar(@q)>1) {
	if ($q[0] eq "private")	{ shift @q;  }
	elsif ($q[0] eq "final")	{ shift @q;  }
	else { last; }
    }

    scalar(@q)==2 or die  "Wrong number of tokens in: $s\n";
    my ($type,$name) = @q;
    my $Name = &cap1($name);
    print "${indent}public ${type} get${Name}() \{ return $name; }\n";
    print $indent . '@' . "XmlElement\n";
    print "${indent}public void set${Name}(${type} _$name) \{ $name = _$name; }\n";
    
}
    
#-- Capitalizes the first letter
sub cap1($) {
    my ($name) = @_;
    $name =~ /^(.)/;
    my ($c,$x) = ($1,$');
    $c =~ tr/a-z/A-Z/;
    return $c . $x;
}
