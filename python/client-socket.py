#!/usr/bin/python

#----------------------------------------------------------------------
#-- This is a sample Python program that plays a game with a socket-based
#-- Game Server
#--
#-- Usage:
#-- client-socket.py host port rule-filet nPieces
#-- e.g.
#-- client-socket.py localhost 7501 game-data/rules/rules-01.txt 5
#----------------------------------------------------------------------


import subprocess, sys, re, random, json
import gameLoop
import socket


#game='game-data/rules/rules-01.txt'
host=sys.argv[1]
#sys.stdout.write("host="+host +"\n")
port=int(sys.argv[2])

otherParamString = " ".join(sys.argv[3:])

game=sys.argv[3]

#-- this is a string, not a number!
nPieces = sys.argv[4]

sys.stdout.write("Port="+repr(port)+" Rule file=" + game +", #pieces=" + nPieces+"\n")

sys.stdout.write("Host=" + host+", Port="+repr(port)+", game params: " + otherParamString +"\n")

try: 
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM) 
    print( "Socket successfully created")
except socket.error as err: 
    print( "socket creation failed with error {0}".format(err) )

try: 
    host_ip = socket.gethostbyname(host) 
except socket.gaierror: 
    # this means could not resolve the host 
    print( "there was an error resolving the host")
    sys.exit() 
  
    
# connecting to the server
try: 
    sock.connect((host_ip, port))
except socket.error as err:
    print( "socket connect failed with error {0}".format(err) )
    sys.exit() 

print( "the socket has successfully connected to host=" + host_ip+ " on port=" + repr(port))


#-- see https://docs.python.org/2/library/socket.html
#-- https://www.linuxtopia.org/online_books/programming_books/python_programming/python_ch36s06.html
rfile = sock.makefile('rb')
wfile = sock.makefile('wb')
 
# msg = "GAME \"" + game + "\" " + nPieces
msg = "GAME " + otherParamString 

print( "Sending: " + msg + "\n")
wfile.write((msg + "\n").encode())
wfile.flush()

gameLoop.mainLoop(rfile, wfile)

    
