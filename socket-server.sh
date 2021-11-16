#!/bin/csh

#-------------------------------------------------------------------
# This script starts the socket-based Captive Game Server.
# Once you have started the server, you can connect to it from
# your Java or Python clients. An example of such a connection
# can be found in captive-python-socket.sh
# Sample usage (to start the server on port 7501):
# ./socket-server.sh 7501
#-------------------------------------------------------------------


#-- The directory where this script is
set sc=`dirname $0`
set h=`(cd $sc; pwd)`
source "$h/scripts/set-var-captive.sh"

# -Doutput=STANDARD
java edu.wisc.game.engine.GameSocketServer  $argv[1-]


