#!/bin/csh

#---------------------------------------------------------------------
#   Usage is similar to capitve-full.sh, e.g.:
#
#   scripts/gemini.sh /opt/w2020/game-data/rules/FDCL/basic/ccw.txt 9 max_boards=100 
#   scripts/gemini.sh /opt/w2020/game-data/rules/FDCL/basic/cm_KRBY.txt 9
#
#   scripts/gemini.sh  /opt/tomcat/game-data/rules/vm/TD-01.txt 6
#
#   scripts/gemini.sh -inputDir /home/vmenkov/my-game-data ~/my-game-data/rules/vm/TD-01.txt 6
#
#   scripts/gemini.sh   /opt/tomcat/game-data/trial-lists/vmColorTest/trial_1.csv 1
#
#   scripts/gemini.sh   /opt/tomcat/game-data/trial-lists/vmColorTest/trial_1.csv 1
#
#   scripts/gemini.sh  -inputDir /home/vmenkov/my-game-data ~/my-game-data/trial-lists/vmColorTest/trial_1.csv 1
#
# scripts/gemini.sh inputDir=game-data log=sample.csv log.nickname=JohnDoe log.run=0  R:MLC/BMK/colOrd_nearby.txt:MLC/BMK/bmk.csv


# scripts/gemini.sh  /opt/w2020/game-data/rules/FDCL/basic/ccw.txt  9 max_requests=1 max_boards=10 wait=1000 instructionsFile=/opt/w2020/game-data/gemini/system-prepared.txt  prepared_episodes=10 candidateCount=3 

#

#---------------------------------------------------------------------


#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc/..; pwd)`
source "$h/scripts/set-var-captive.sh"


#echo "CP is $CLASSPATH"

#set opt="-Doutput=FULL"

#-- Checking for some command-line options

#if ($#argv >= 2 && $1 == "-inputDir") then
#    shift
#    set x=$1
#    shift
#    set opt="$opt -DinputDir=$x"
#endif

#echo "Options are $opt"

java  edu.wisc.game.gemini.GeminiPlayer  $argv[1-]



