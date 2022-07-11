#!/bin/csh

#--------------------------------------------------------------------------
# This is the main script for generating a sample results file, as a sample
# on which MLC participants can model their programs' output.
#--------------------------------------------------------------------------


#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc/..; pwd)`
#-- set classpath etc
source "$sc/set-var-captive.sh"

#--- the main input data directory 
set in=$h/game-data

#-- the list of rule sets
set rules=`(cd $in/rules; ls MLC/BMK/*.txt)`
set log=test.csv
echo "Will collect results in file $log"

rm $log

foreach r ($rules)

    foreach run (0 1) 

        echo "Rule set $r, run No. $run"

	$h/python/client-2.py inputDir=${in} logappend=${log} log.nickname=RandomTest log.run=${run} R:${r}:MLC/BMK/bmk.csv

    end
     
end     
