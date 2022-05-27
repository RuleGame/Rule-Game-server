#!/bin/csh

#--------------------------------------------------------------------
# This scripts prepares a ZIP file suitable for distribution to
# external customers who want to run the Captive Game Server on
# their computers.
#
# Before running this script, make sure to run
# ant clean captive-jar
# to ensure that a fresh captive.jar has been built. And then,
# rm ../captive.zip
# to remove the old zip file if it exists.
#--------------------------------------------------------------------

set sc=`dirname $0`
cd $sc/../..
echo Working in `pwd`

zip -r captive.zip game -x 'game/tmp/**'  -x 'game/*.tmp'-x 'game/classes/**' -x game/lib/game.zip -x 'game/.git/**' -x '**/*~'


# "enats/.svn/*" -x enats/resources/NletsStatuteMapping20181218.csv -x "enats/out.doc/html/api/**" -x "enats/src/*" -x "enats/samples/**" -x "enats/samples-old/**" -x "enats/classes/**" -x "enats/charges/*" -x "enats/lib.extra/*"


ls -l captive.zip


