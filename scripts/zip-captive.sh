#!/bin/csh

#--------------------------------------------------------------------
# This scripts prepares a ZIP file suitable for distribution to
# external customers who want to run the Captive Game Server or
# the Gemini Java Harness on their computers.
#
# Before running this script, make sure to run
# ant clean captive-jar
# to ensure that a fresh captive.jar has been built. And then,
# rm ../captive.zip
# to remove the old zip file if it exists.
#--------------------------------------------------------------------

set sc=`dirname $0`
set h=`(cd $sc/../..; pwd)`
cd $h
echo Working in `pwd`

rm -rf /tmp/game
cp -pa game /tmp/game
mkdir /tmp/game/lib/jaxrs-ri
cp -pa jaxrs-ri/ext /tmp/game/lib/jaxrs-ri/

rm -f /tmp/captive.zip
(cd /tmp; zip -r captive.zip game -x 'game/tmp/**' -x game/.git  -x 'game/*.tmp'-x 'game/classes/**' -x game/lib/game.zip -x 'game/.git/**' -x '**/*~' )
mv /tmp/captive.zip .


# "enats/.svn/*" -x enats/resources/NletsStatuteMapping20181218.csv -x "enats/out.doc/html/api/**" -x "enats/src/*" -x "enats/samples/**" -x "enats/samples-old/**" -x "enats/classes/**" -x "enats/charges/*" -x "enats/lib.extra/*"

rm -f /tmp/rules.zip
(cd /opt/w2020; zip -r /tmp/rules.zip game-data/rules game-data/gemini)
mv /tmp/rules.zip .

ls -l $h/captive.zip $h/rules.zip



