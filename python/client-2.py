#!/usr/bin/python

#----------------------------------------------------------------------
#-- This is a sample Python program that spawns a Captive Game Server
#-- and plays several episodes with it using pipe I/O
#--
#-- Usage:
#-- client.py game-data/rules/rules-01.txt 5
#----------------------------------------------------------------------


import subprocess, sys
#, re, random, json
import gameLoop

jarg =  ['java', '-Doutput=STANDARD', 'edu.wisc.game.engine.Captive']
jarg.extend( sys.argv[1:])

print "Spawning CGS with the following command: " , jarg

#----------------------------------------------------------------------
#-- Spawn a CGS as a child process

proc=subprocess.Popen(jarg, stdin=subprocess.PIPE, stdout=subprocess.PIPE)

#-- play N episodes
N=5

gameLoop.severalEpisodes(proc.stdout, proc.stdin, N)

