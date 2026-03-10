#!/bin/bash

#------------------------------------------------------------------------------
# Run this script to remove all 2PG experiment plans from ~/opt/w2020/game-data
# (Paul asked to remove them because the Plesk hosts apparently don't support
# websockets well).
#
# There is no "find" program on the Plesk hosts, so we just use lots of wildcards.
#
# Install this script to the Plesk hosts as follows:
# scp remove-2pg.sh test-rulegame@wwwtest.rulegame.wisc.edu:mybin/
# scp remove-2pg.sh rulegame@rulegame.wisc.edu:mybin/
#------------------------------------------------------------------------------

rm -rf /opt/w2020/game-data/trial-lists/*/adve.*
rm -rf /opt/w2020/game-data/trial-lists/*/*/adve.*

rm -rf /opt/w2020/game-data/trial-lists/*/coop.*
rm -rf /opt/w2020/game-data/trial-lists/*/*/coop.*
