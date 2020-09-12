#!/usr/bin/perl -p
#-----------------------------------------------------------------------
# This script processes a CSV file exported from a SQL database, 
# converting bit values and nulls into a human-readable form
#-----------------------------------------------------------------------

s/\0/0/g; 
s/\x01/1/g; 
s/\\N/NULL/g;
    
