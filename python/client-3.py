#!/usr/bin/python

#----------------------------------------------------------------------
#-- This is a sample Python program that spawns a Captive Game Server
#-- and plays with it using pipe I/O.
#
#-- It demonstrates setting the classpath properly; this works if
#-- some directory $g is the base directory for your libraries and code
#-- (e.g.
#-- g="/home/vmenkov/w2020"
#-- ), and you have $g/jaxrs-ri/ext/*.jar, $g/game/lib/captive.jar, and
#-- this python script is in $g/game/python
#
#----------------------------------------------------------------------


import subprocess, sys, re, random, json, os
import gameLoop

#----------------------------------------------------------------------
# Instead of setting the CLASSPATH based on this script's location, as
# done below, you
# can set CLASSPATH with a shell script. (You need to either use the
# "source" command to run the sell script before you run the Python script,
# or call the Python script from the shell script).
# In csh, you can do
# setenv CLASSPATH $g/game/lib/captive.jar:$g/jaxrs-ri/ext/'*'
# In bash or zsh, you can do
# export CLASSPATH=$g/game/captive.jar:$g/jaxrs-ri/ext/'*'
#----------------------------------------------------------------------


thisScript = os.path.abspath(sys.argv[0])
[pythonDir , dummy] = os.path.split(thisScript)
[gameDir , dummy] = os.path.split(pythonDir)
[g , dummy] = os.path.split(gameDir)

sys.stdout.write("Assuming that the base directory is " + g + "\n")

q = g  + "/game/lib/captive.jar"
if (not os.path.exists(q)):
    sys.stdout.write("JAR file does not exist at this location: "+q +"\n")
    quit()

q = g  + "/jaxrs-ri/ext"
if (not os.path.exists(q)):
    sys.stdout.write("Library directory does not exist at this location: "+q +"\n")
    quit()


cp = g + "/game/lib/captive.jar:" + g +"/jaxrs-ri/ext/*"
sys.stdout.write("Setting CLASSPATH to " + cp + "\n")
os.environ["CLASSPATH"] = cp


#-----------------------------------------------------------
#-- Set the parameters for the CGS

#-- this is a string, not a number!
num_pieces='9'

rules="/opt/w2020/game-data/rules/FDCL/basic/cm_RBKY.txt"

condition='condTrain=/opt/w2020/game-data/cond/vm/top-left-quarter.txt'

sys.stdout.write("Rule file=" + rules +", #pieces=" + num_pieces+"\n")

#----------------------------------------------------------------------
#-- Spawn a CGS as a child process

args=['java', '-Doutput=STANDARD', 'edu.wisc.game.engine.Captive', rules, num_pieces, condition]
sys.stdout.write("Command line = " + " ".join(args) + "\n")

proc=subprocess.Popen( args, stdin=subprocess.PIPE, stdout=subprocess.PIPE)

#sys.stdout.write(proc.stdout.read())

gameLoop.mainLoop(proc.stdout, proc.stdin)

    
