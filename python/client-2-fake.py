#!/usr/bin/python

#----------------------------------------------------------------------
#-- This is a sample Python program that spawns a Captive Game Server
#-- and plays several episodes with it using pipe I/O. Unlike client-2.py,
#-- it will start "cheating" after a specified point, so that
#-- results demonstrating (fake) learning will be produced.
#--
#-- This script is used to produce results files for testing the MLC
#-- Leaderboard.
#--
#-- Usage:
#--  client-2-fake.py N0
#-- Here, N0 is an integer number indicating after how many episodes the
#-- tool can start cheating.
#----------------------------------------------------------------------


import subprocess, sys
#, re, random, json
import gameLoop, gameLoopFake


N0=int( sys.argv[1] )

jarg =  ['java', '-Doutput=STANDARD', 'edu.wisc.game.engine.Captive']
jarg.extend( sys.argv[2:])

print( "N0="+str(N0)+". Spawning CGS with the following command: " + " ".join(jarg))

#----------------------------------------------------------------------
#-- Spawn a CGS as a child process

proc=subprocess.Popen(jarg, stdin=subprocess.PIPE, stdout=subprocess.PIPE)

#-- play N episodes
N=100

gameLoopFake.severalEpisodes(proc.stdout, proc.stdin, N, N0)

