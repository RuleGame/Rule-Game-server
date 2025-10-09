#!/usr/bin/python3

#-- !/usr/bin/python3

#----------------------------------------------------------------------
#-- This is a sample Python program that spawns a Captive Game Server
#-- and plays with it using pipe I/O
#--
#-- Usage:
#-- client.py game-data/rules/rules-01.txt 5
#----------------------------------------------------------------------


#!/usr/bin/python3

import subprocess, sys, re, random, json, os
import gameLoopGeminiHypothesisMove

game = sys.argv[1]
nPieces = sys.argv[2]

# Extract rule name from path (e.g., "rules-01" from "game-data/rules/rules-01.txt")
rule_name = os.path.splitext(os.path.basename(game))[0]

sys.stdout.write("Rule file=" + game +", #pieces=" + nPieces+"\n")

# Set log filename BEFORE calling mainLoop
gameLoopGeminiHypothesisMove.setup_logging(rule_name, nPieces)

#----------------------------------------------------------------------
#-- Spawn a CGS as a child process
proc=subprocess.Popen( ['java', '-Doutput=STANDARD', 'edu.wisc.game.engine.Captive', game, nPieces], stdin=subprocess.PIPE, stdout=subprocess.PIPE)

rule = ''
test = False

rule = gameLoopGeminiHypothesisMove.mainLoop(proc.stdout, proc.stdin, test, rule, 2)

proc=subprocess.Popen( ['java', '-Doutput=STANDARD', 'edu.wisc.game.engine.Captive', game, nPieces], stdin=subprocess.PIPE, stdout=subprocess.PIPE)

test = True
result = gameLoopGeminiHypothesisMove.mainLoop(proc.stdout, proc.stdin, test, rule)

