#!/usr/bin/python

#----------------------------------------------------------------------
#-- This is a sample Python program that spawns a Captive Game Server
#-- and plays with it using pipe I/O
#--
#-- Usage:
#-- client.py game-data/rules/rules-01.txt 5
#----------------------------------------------------------------------


import subprocess, sys, re, random, json
import gameLoop

#game='game-data/rules/rules-01.txt'
game=sys.argv[1]

# nPieces = '5'

#-- this is a string, not a number!
nPieces = sys.argv[2]

sys.stdout.write("Rule file=" + game +", #pieces=" + nPieces+"\n")


#----------------------------------------------------------------------
#-- Spawn a CGS as a child process
# proc=subprocess.Popen( ['java', '-Doutput=STANDARD', 'edu.wisc.game.engine.Captive', game, nPieces], stdin=subprocess.PIPE, stdout=subprocess.PIPE)

jarg =  ['java', '-Doutput=STANDARD', 'edu.wisc.game.engine.Captive']
jarg.extend( sys.argv[1:])

print "Running the following command: " , jarg



proc=subprocess.Popen(jarg, stdin=subprocess.PIPE, stdout=subprocess.PIPE)

#sys.stdout.write(proc.stdout.read())

gameLoop.mainLoop(proc.stdout, proc.stdin)

    
