#----------------------------------------------------------------------
#-- This file contains the main game-playing loop, sending commands to
#-- the server and parsing the responses. It can be used both with the
#-- pipe-based CGS and the socket-based CGS.
#----------------------------------------------------------------------

import subprocess, sys, re, random, json


#----------------------------------------------------------------------
#-- Reads and returns one line of "informative" text (ignoring any
#-- comment lines
#-- inx = input channel (associated with the stdout of the pipe-based
#-- Captive Game Server, or with a socket used to talk to a socket-based CGS)
#----------------------------------------------------------------------
def readLine(inx):
    while True:
        s = inx.readline()
        sys.stdout.write("Received: "+ s + "\n")
        if not s:
            return s
        if s.startswith('#'):
            continue
        else:
            return s

#----------------------------------------------------------------------        
#-- makes a move by picking the first moveable piece and trying to move
#-- it to a random bucket.
#-- val = a list of piece description, pulled in from the JSON string
#-- returned by the server.
#----------------------------------------------------------------------
def chooseMoveCheating(val):
    #print("Value:\n")
    #print(val);
    m = len(val)
    sys.stdout.write( repr(m) + " pieces still on the board\n")    
    for v in val:
        buckets = v["buckets"]
        movable = (len(buckets)>0)
        if (movable):
            x = v["x"]
            y = v["y"]
            #-- pick random bucket
            bx = 7 * random.randint(0,1) 
            by = 7 * random.randint(0,1) 
            return [y, x, by, bx]

    print("Cannot decide on making a move, because cannot find a moveable piece!\n")
    exit()

#----------------------------------------------------------------------        
#-- makes a move by picking a random piece and trying to move
#-- it to a random bucket.
#-- val = a list of piece description, pulled in from the JSON string
#-- returned by the server.
#----------------------------------------------------------------------
def chooseMove(val):
    #print("Value:\n")
    #print(val);
    m = len(val)
    sys.stdout.write( repr(m) + " pieces still on the board\n")
    j = random.randint(0, m-1);
    v = val[j]
    buckets = v["buckets"]
    x = v["x"]
    y = v["y"]
    #-- pick random bucket
    bx = 7 * random.randint(0,1) 
    by = 7 * random.randint(0,1) 
    return [y, x, by, bx]

    exit()

# inx=proc.stdout
# outx=proc.stdin
def mainLoop(inx,outx): 
    #----------------------------------------------------------------------
    #-- Keep playing until the episode is finished
    while True:
        statusLine = readLine(inx).strip();
        jsonLine = readLine(inx).strip();
        
        sys.stdout.write("Unpack: "+ statusLine + "\n")
        tt = re.split("\s+", statusLine);
        # print(tt);
        [code, status, t] = map( int, re.split("\s+", statusLine));
        sys.stdout.write("Code=" +repr(code)+ ", status=" +repr(status)+ ", stepNo="+repr(t)+ "\n");

        if code<0:
            sys.stdout.write("Error code"+ repr(code) + "\n")
            sys.stdout.write("Error message: "+ jsonLine + "\n")
            break
        
        if status==0:
            0  #-- all's fine, print no message
            #sys.stdout.write("Keep going...\n")
        elif status==1:
            sys.stdout.write("Cleared board in "+ repr(t) + " steps\n")
            break
        elif status==2:
            sys.stdout.write("Stalemate after  "+ repr(t) + " steps\n")
            break
        else:
            sys.stdout.write("Unknown status "+repr(status)+"\n")
            
        if not jsonLine:
            sys.stdout.write("No JSON received!\n")
            break

        #-- Parse the JSON line into a Python structure
        w = json.loads(jsonLine)
        #print("w:\n");
        #print(w);
    
        val = w["value"]

        #-- choose a move to make
        moveData = chooseMove(val)
        send = "MOVE " + " ".join( map(repr, moveData))
        sys.stdout.write("Sending: "+ send + "\n")
        
        outx.write(send + "\n")
        outx.flush()
    
    outx.write("EXIT\n")
    outx.flush()

