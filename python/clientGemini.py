#!/usr/bin/python3

#-- !/usr/bin/python3

#----------------------------------------------------------------------
#-- This is a sample Python program that spawns a Captive Game Server
#-- and plays with it using pipe I/O
#--
#-- Usage:
#-- client.py game-data/rules/rules-01.txt 5
#----------------------------------------------------------------------


import subprocess, sys, re, random, json
import gameLoopGemini2

#game='game-data/rules/rules-01.txt'
game=sys.argv[1]

# nPieces = '5'

#-- this is a string, not a number!
nPieces = sys.argv[2]

sys.stdout.write("Rule file=" + game +", #pieces=" + nPieces+"\n")


#----------------------------------------------------------------------
#-- Spawn a CGS as a child process
proc=subprocess.Popen( ['java', '-Doutput=STANDARD', 'edu.wisc.game.engine.Captive', game, nPieces], stdin=subprocess.PIPE, stdout=subprocess.PIPE)

#sys.stdout.write(proc.stdout.read())
rule = ''
test = False

rule = gameLoopGemini2.mainLoop(proc.stdout, proc.stdin, test, rule, 2)

proc=subprocess.Popen( ['java', '-Doutput=STANDARD', 'edu.wisc.game.engine.Captive', game, nPieces], stdin=subprocess.PIPE, stdout=subprocess.PIPE)

test = True
result = gameLoopGemini2.mainLoop(proc.stdout, proc.stdin, test, rule)

