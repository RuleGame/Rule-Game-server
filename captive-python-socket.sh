#!/bin/csh

#-------------------------------------------------------------------
# This shell script starts a sample Python application that will
# "talk" to the socket-based Captive Game Server. (The server itself
# can be started with socket-server.sh)
# Sample usage:
# ./captive-python-socket.sh localhost 7501 /opt/tomcat/game-data/rules/MLC/vm/test-05.txt 4
#-------------------------------------------------------------------

#-- The directory where this script is
set sc=`dirname $0`
#echo sc=$sc
set h=`(cd $sc; pwd)`
source "$h/scripts/set-var-captive.sh"

# $sc/python/client-socket.py localhost 7501 $sc/game-data/rules/rules-02.txt 5
$sc/python/client-socket.py   $argv[1-]





