#----------------------------------------------------------------------
#-- This file contains the main game-playing loop, sending commands to
#-- the server and parsing the responses. It can be used both with the
#-- pipe-based CGS and the socket-based CGS.
#--
#-- Unlike gameLoop.py, this player can "cheat", by looking at the list
#-- of allowed moves, and only making those
#-- 
#----------------------------------------------------------------------

import subprocess, sys, re, random, json, math
import gameLoop


#----------------------------------------------------------------------        
#-- makes a move by picking the first moveable piece and trying to move
#-- it to a random bucket.
#-- val = a list of piece description, pulled in from the JSON string
#-- returned by the server.
#----------------------------------------------------------------------
def chooseMoveCanCheat(val, doCheat):
    print("doCheat=" + str(doCheat))
    if (not doCheat):
        return gameLoop.chooseMove(val)
    
    #print("Value:\n")
    #print(val);
    m = len(val)
    #sys.stdout.write( repr(m) + " pieces still on the board\n")    
    for v in val:
        buckets = v["buckets"]
        movable = (len(buckets)>0)
        if (movable):
            x = v["x"]
            y = v["y"]
            #-- pick random bucket
            #  bx = 7 * random.randint(0,1) 
            #  by = 7 * random.randint(0,1) 
            # return [y, x, by, bx]
            #-- pick an allowed bucket
            j = random.randint(0,len(buckets)-1)
            b = buckets[j]
            by = 7*(1 - b/2)
            bx = 7*(((b+1)%4)/2)
            return [y, x, by, bx]
        
    print("Cannot decide on making a move, because cannot find a moveable piece!\n")
    exit()


# inx=proc.stdout
# outx=proc.stdin

#--- Play 1 episode and EXIT
#def mainLoop(inx,outx): 
#    mainLoopA(inx,outx);
#    outx.write("EXIT\n".encode());  #-- for Python3
#    outx.flush();

#-- Play N episodes and EXIT. After N0 episodes, start cheating     
def severalEpisodes(inx,outx,N,N0):
    for j in range(0, N):
        cheatProb = 0.0 if (j<N0) else 1.0 - math.exp( 0.3*(N0-j));
        mainLoopB(inx,outx,cheatProb);
        cmd = "EXIT\n" if (j==N-1) else "NEW\n";
        outx.write(cmd.encode());  #-- for Python3
        outx.flush();

    
def mainLoopB(inx,outx,cheatProb): 
    print("cheatProb=" + str(cheatProb))
    #----------------------------------------------------------------------
    #-- Keep playing until the episode is finished
    while True:
        statusLine = gameLoop.readLine(inx).strip();
        jsonLine = gameLoop.readLine(inx).strip();
        
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
        
        moveData = chooseMoveCanCheat(val, random.random()<cheatProb)
        send = "MOVE " + " ".join( map(repr, moveData))
        sys.stdout.write("Sending: "+ send + "\n")
        
#        outx.write(send + "\n")
        outx.write((send + "\n").encode())
        outx.flush()
    

