#!/usr/bin/perl -p

s/<p>/\n/g;
s/<em>/{\\it /g;
s|</em>|}|g;
s|<tt>|{\\tt |g;
s|</tt>|}|g;
s|_|\\_|g;
s|<strong>|{\\bf |g;
s|</strong>|}|g;
