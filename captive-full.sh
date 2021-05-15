#!/bin/csh

#---------------------------------------------------------------------
#   Usage examples:
#
#   ./captive-full.sh  /opt/tomcat/game-data/rules/vm/TD-01.txt 6
#
#   ./captive-full.sh -inputDir /home/vmenkov/my-game-data ~/my-game-data/rules/vm/TD-01.txt 6
#
#   ./captive-full.sh   /opt/tomcat/game-data/trial-lists/vmColorTest/trial_1.csv 1
#
#   ./captive-full.sh   /opt/tomcat/game-data/trial-lists/vmColorTest/trial_1.csv 1
#
#   ./captive-full.sh  -inputDir /home/vmenkov/my-game-data ~/my-game-data/trial-lists/vmColorTest/trial_1.csv 1
#---------------------------------------------------------------------


#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc; pwd)`
source "$h/scripts/set-var-captive.sh"

set opt="-Doutput=FULL"

#-- Checking for some command-line options

if ($#argv >= 2 && $1 == "-inputDir") then
    shift
    set x=$1
    shift
    set opt="$opt -DinputDir=$x"
endif

echo "Options are $opt"

#java -Doutput=FULL -DinputDir=/home/vmenkov/my-game-data edu.wisc.game.engine.Captive  $argv[1-]

echo java $opt edu.wisc.game.engine.Captive  $argv[1-]
java $opt edu.wisc.game.engine.Captive  $argv[1-]



