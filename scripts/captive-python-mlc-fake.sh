#!/bin/csh

#--------------------------------------------------------------------------
# This is the main script for generating a sample results file, as a sample
# on which MLC participants can model their programs' output.
# Unlike captive-python-mlc.sh, this script makes the player cheat,
# so that it can produce a results file indicating (fake) convergence.
#
# Usage:
#   captive-python-mlc-fake.sh  N0
#--------------------------------------------------------------------------


#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc/..; pwd)`
#-- set classpath etc
source "$sc/set-var-captive.sh"

#--- when to start cheating
if ($#argv < 1) then
    echo "Usage: $0 N0"
    exit
endif

set N0=$1

#--- the main input data directory 
set in=$h/game-data

#-- the list of rule sets
set rules=`(cd $in/rules; ls MLC/BMK/*.txt)`
set log=Fake-${N0}.csv
echo "Will collect results in file $log"

rm $log


# set rules=(MLC/BMK/alternateShape2Bucket_color2Bucket.txt)


foreach r ($rules)


    # foreach run (0 1 2 3 4 5 6 7 8 9) 

    set run = 0
    while ( $run < 10 )

        echo "Rule set $r, run No. $run"
	$h/python/client-2-fake.py $N0 inputDir=${in} logappend=${log} log.nickname=Fake-${N0} log.run=${run} R:${r}:MLC/BMK/bmk.csv
	@ run++

    end
     
end     
