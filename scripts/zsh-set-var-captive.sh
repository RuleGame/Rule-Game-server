#!/bin/zsh

#---------------------------------------------------------------------
# This script is meant for zsh users to set the CLASSPATH
# environment variable before running Python scripts that talk
# to the CGS.
#
# Sample usage:
#
# source scripts/zsh-set-var-captive.sh
# python/client.py game-data/rules/rules-01.txt 5
#---------------------------------------------------------------------


#-- The directory where this script is (e.g. ~/w2020/game/scripts)
sc=`dirname $0`
echo "sc=$sc"
#-- $h is the main directory (~/w2020/game)
h=`(cd $sc/..; pwd)`

g=`(cd $h/..; pwd)`
echo "h=$h, g=$g"

export CLASSPATH=$h/lib/captive.jar:$g/jaxrs-ri/ext/'*'

echo "CLASSPATH is $CLASSPATH"

echo "Now you can run your Python scripts, e.g. python/client.py"


