#!/bin/csh

#--------------------------------------------------------------
# You can copy this script file to your own directory, and
# then modify it as needed for your use case
#--------------------------------------------------------------
# This script demonstrates creating a few random board files satisfying
# desired criteria
#--------------------------------------------------------------


#--------------------------------------------------------------
# The location of the main directory for the Game Server project,
# where the code was checked out from GitHub and a build was
# performed. On sapir, you can simply use ~vmenkov/w2020/game, but if
# you have checked it out from GitHub and then did a build yourself,
# you can set this location accordingly (e.g. to ~/w2020/game). See
# http://sapir.psych.wisc.edu:7150/w2020/deploy.html for details.
# --------------------------------------------------------------
set main=~vmenkov/w2020/game

#-- the directory in which you want to create JSON files
set dir=tmp

#-- Create the output directory, if it does not exist yet
mkdir $dir

#--------------------------------------------------------------
# Generate 100 random files with a specified number of game pieces,
# number of shapes, and number of colors. Unless you want to use the
# legacy shapes and colors, you can also supply the lists from which
# shapes and colors are drawn.
#
# Here we are creating images for experiment plan name_disjunction,
# trial list  nameability_disjunction_color_high_first.csv, the first
# series:
# disjunct_color_high,8,2,10,4,4,4,CAMELLOW;CATLOW;BIRDLOW;DUCKLOW,4,4,BLUE_HIGH;ORANGE_HIGH;RED_HIGH;YELLOW_HIGH,4,4,1.5,2,3,1.3,free,0,FALSE,FALSE,/opt/tomcat/
#--------------------------------------------------------------

$main/scripts/random-boards.sh $dir 100 4 4 4 'CAMELLOW;CATLOW;BIRDLOW;DUCKLOW' 'BLUE_HIGH;ORANGE_HIGH;RED_HIGH;YELLOW_HIGH'

#--------------------------------------------------------------
# Now, we delete board files with undesirable color-shape combinations:
# Rule set disjunct_color_high.txt 
# ruleArrayName=disjunctColorHigh
# (*,birdlow,*,*,[0]) (*,*,red_high,*,[0])
# (*,camellow,*,*,[1]) (*,*,yellow_high,*,[1])
# (*,ducklow,*,*,[2]) (*,*,orange_high,*,[2])
# (*,catlow,*,*,[3]) (*,*,blue_high,*,[3])
#
# We make use of the fact that colors and shapes happen to appear in the JSON
# file in a certain order, e.g.
# {"id":0,"color":"YELLOW_HIGH","shape":"CAMELLOW","x":5,"y":1,"buckets":[]}
# This order of properties, actually, is not intentionally controlled by
# our board generator, so if for some reason is changes, we'd have to change
# the "grep" command below.
#--------------------------------------------------------------
#
# Below, we have 4 "find" commands, each of which uses "grep" to find
# JSON files with a particular undesirable (shape,color) combination
# in the same piece, and removes such files. As simple combinatorics
# shows, each such command removes, on average, a bit less than 1/4 of
# all files.  (More preciely, it keeps, on average, (15/16)^4 = 77.2%
# of all files.) So after the four "find" commands, one would expect, on
# average, about 35% of all initially generated files to remain.
# --------------------------------------------------------------

find $dir -name '*.json' -exec grep -i '"color":"RED_HIGH","shape":"birdlow"' {} \; -delete
find $dir -name '*.json' -exec grep -i '"color":"YELLOW_HIGH","shape":"camellow"' {} \; -delete
find $dir -name '*.json' -exec grep -i '"color":"ORANGE_HIGH","shape":"ducklow"' {} \; -delete
find $dir -name '*.json' -exec grep -i '"color":"BLUE_HIGH","shape":"catlow"' {} \; -delete


