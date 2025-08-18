#----------------------------------------------------------------------
#-- This file contains the main game-playing loop, sending commands to
#-- the server and parsing the responses. It can be used both with the
#-- pipe-based CGS and the socket-based CGS.
#----------------------------------------------------------------------

import subprocess, sys, re, random, json
from google import genai
from dotenv import load_dotenv
import os
#----------------------------------------------------------------------
load_dotenv()  # take environment variables from .env.
# Global system prompt

SYSTEM_PROMPT_A = """
# Help me play a game. It involves moving pieces on a board into one of four buckets, following a hidden rule.  
# Your task is to figure out the rule by making moves and observing their results.

## You will be given your best guess of what the rule could be, the last move made, its result, move history and the current board/bucket state.

## Remember:
1. Suggest the NEXT move in the format: MOVE: <piece_id> <bucket_id> .
2. Output a summary of what you think the rule is: RULE: <updated rule> .
3. The RULE should be consistent with the move history and should be your conclusions. SHOULD NOT BE SPECIFIC TO THE BOARD
4. The HYPOTHESIS should be your guess of what the rule could be. It will guide your next moves. SHOULD NOT BE SPECIFIC TO THE BOARD
5. SUGGESTED piece_id SHOULD BE PRESENT ON THE BOARD AND NOT BE IMMOVABLE.
6. bucket_id should be one of 0, 1, 2, or 3.
7. ONLY if the board state is empty i.e. board is cleared, you should suggest piece_id as -1 and bucket_id as -1.
8. Use the thinking to think out loud about your reasoning regarding moves and the rule and the current board state.

## Return exactly four lines:
MOVE: <piece_id> <bucket_id>
RULE: <updated rule>
HYPOTHESIS: <your hypothesis about the rule>
THINKING: <your reasoning>
"""

SYSTEM_PROMPT_B = """
Help me clear a board game. You will be provided:
1. A text explanation of a rule.
2. The current state of the board in JSON format, with each piece described by:
   - 'id': unique identifier for each piece
   - 'shape': shape of the piece
   - 'color': color of the piece
   - 'x' and 'y': coordinates of the piece on the board

Your job is to select exactly {total_pieces} moves that will clear the board according to the given rule.

You MUST return your answer as a single JSON object with two keys:

- "thinking": a short free-text reasoning space where you can explain how you are applying the rule to choose moves. You may use bullet points or plain text. Do not include any game moves here — just your reasoning.
- "moves": a list of exactly {total_pieces} JSON objects, each of the form:
  {{
    "piece_id": <id from board>,
    "bucket_id": < 0 | 1 | 2 | 3 >
  }}

**Example output format:**
```json
{{
  "thinking": "I first group pieces by color. Red pieces go to bucket 0, blue pieces to bucket 1...",
  "moves": [
    {{"piece_id": 12, "bucket_id": 0}},
    {{"piece_id": 5, "bucket_id": 1}},
    ...
  ]
}}
```
"""

def readLine(inx):
    while True:
        s = inx.readline()
        sys.stdout.write("Received: "+ str(s) + "\n")
        # sys.stdout.write("Received type="+ str(type(s)) + "\n")
        if not s:
            return s

        s = s.decode()  #-- for Python3
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
def mapMove(val, id, b):
    # Find the piece by its 'id'
    piece = next((item for item in val if item["id"] == id), None)
    if piece is None:
        raise ValueError(f"Piece with id {id} not found.")

    x = piece["x"]
    y = piece["y"]

    # Set bucket coordinates
    if b == 0:
        bx, by = 0, 7
    elif b == 1:
        bx, by = 7, 7
    elif b == 2:
        bx, by = 7, 0
    elif b == 3:
        bx, by = 0, 0
    else:
        raise ValueError(f"Invalid bucket number: {b}")

    return [y, x, by, bx]

def update_prompt(conversation, val, code, bucket_state):
    # add board state each pieces id, shape, color
    pieces_info = []
    for piece in val:
        piece_info = f"Piece ID: {piece['id']}, Shape: {piece['shape']}, Color: {piece['color']}, Position: ( x: {piece['x']}, y: {piece['y']})"
        pieces_info.append(piece_info)
    board_state = f"Board state: {pieces_info}"
    if code==4:
        conversation.append({"role": "user", "observation": "The last move was denied."})
    if code==0:
        conversation.append({"role": "user", "observation": "The last move was accepted."})
    if code==7:
        conversation.append({"role": "user", "observation": "The last piece moved is immovable as of now. Try again later"})
    
    conversation.append({"role": "user", "content": board_state})
    bucket_summary = []
    if bucket_state:
        bucket_summary = [
            f"Bucket {b}: [{', '.join(bucket_state[b])}]" if bucket_state[b] else f"Bucket {b}: [empty]"
            for b in range(4)
        ]

    conversation.append({"role": "user", "content": "Bucket state: " + "; ".join(bucket_summary)})

    return conversation

def build_prompt(rule_so_far, hypothesis, last_move, last_result, val, bucket_state, immovable_pieces, move_history):
    """
    Build the prompt for Gemini with:
    - Rule so far
    - Last move & result
    - Current board & bucket states
    - Known immovable pieces (ids + short description)
    """

    # Board state
    pieces_info = [
        f"Piece ID: {p['id']}, Shape: {p['shape']}, Color: {p['color']}, Position: ( x: {p['x']}, y: {p['y']})"
        for p in val
    ]
    board_state = "Board state:\n" + "\n".join(pieces_info)

    # Bucket state
    bucket_summary = "; ".join(
        f"Bucket {b}: [{', '.join(bucket_state[b])}]" if bucket_state[b] else f"Bucket {b}: [empty]"
        for b in range(4)
    )
    # Bucket 0 : []
    # add move_history if available
    if move_history:
        history_text = "; ".join(f"{move}: {result}" for move, result in move_history.items())
        bucket_summary += " | Move history: " + history_text
 
    # Immovable pieces summary: give IDs and shape/color/coords if available
    immovable_summary = []
    for pid in sorted(list(immovable_pieces)):
        p = next((item for item in val if item["id"] == pid), None)
        if p:
            immovable_summary.append(f"{pid}: {p['color']} {p['shape']} @({p['x']},{p['y']})")
        else:
            immovable_summary.append(str(pid))
    immovable_text = ", ".join(immovable_summary) if immovable_summary else "None"


    return [
        {
            "role": "system",
            "content": SYSTEM_PROMPT_A
        },
        {"role": "user", "content": f"Rule so far: {rule_so_far}"},
        {"role": "user", "content": f"HYPOTHESIS so far: {hypothesis}"},
        {"role": "user", "content": f"Last move: {last_move if last_move else 'None yet'}"},
        {"role": "user", "content": f"Last move result: {last_result if last_result else 'N/A'}"},
        {"role": "user", "content": " ACTIVE immovable pieces: " + immovable_text},
        {"role": "user", "content": f"Board state: {board_state}"},
        {"role": "user", "content": "Bucket state and move history: " + bucket_summary}
    ]


def mainLoop(inx, outx, test, rule, episodes=1):
    if test:
        stats = mainLoopB(inx, outx, rule)
        return stats
    else:
        if episodes <= 1:
            rule = mainLoopA(inx, outx)  # default asks for final rule
            outx.write("EXIT\n".encode())
            outx.flush()
            return rule
        else:
            # run multiple episodes and synthesize a single rule at the end
            final_rule = severalEpisodes(inx, outx, episodes)
            return final_rule


#-- Play N episodes and EXIT     
def severalEpisodes(inx, outx, N):
    """
    Run N episodes, collect per-episode summaries, then ask Gemini once for a consolidated rule.
    Returns the final consolidated rule text (string).
    """
    client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))
    rule_so_far = "Unknown rule initially."
    hypothesis = "Unknown hypothesis initially."

    for j in range(0, N):
        sys.stdout.write(f"Starting episode {j+1}/{N}\n")
        rule_so_far, hypothesis = mainLoopA(inx, outx, ask_final_rule=False, rule_so_far=rule_so_far, hypothesis=hypothesis)

        # Tell the server to start next episode or exit
        cmd = "EXIT\n" if (j == N-1) else "NEW\n"
        outx.write(cmd.encode())
        outx.flush()

    return rule_so_far


def call_gemini(client, conversation):
    full_prompt = ""
    for msg in conversation:
        role = msg["role"]
        content = msg["content"] if "content" in msg else msg["observation"]
     
        full_prompt += f"{role.upper()}:\n{content}\n\n"

    response = client.models.generate_content(model="gemini-2.5-flash",contents = full_prompt)
    return response.text


def mainLoopA(inx, outx, ask_final_rule=True, rule_so_far="Unknown rule initially.", hypothesis="Unknown hypothesis initially."):
    """
    Play one episode. If ask_final_rule==True: when board is cleared, call Gemini for the final rule and return that text.
    If ask_final_rule==False: when episode finishes return a summary dict (no final Gemini call).
    """
    bucket_state = {0: [], 1: [], 2: [], 3: []}
    client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))
    immovable_pieces = set()
    last_move = None
    last_result = None
    moveHistory = {}
    prev_val = None

    while True:
        statusLine = readLine(inx)
        if not statusLine:
            sys.stdout.write("No status line received in mainLoopA.\n")
            return None if ask_final_rule else {
                "rule_so_far": rule_so_far,
                "moveHistory": moveHistory,
                "bucket_state": bucket_state,
                "move_history": moveHistory,
                "cleared": False
            }
        statusLine = statusLine.strip()
        jsonLine = readLine(inx)
        if not jsonLine:
            sys.stdout.write("No json line received in mainLoopA.\n")
            return None if ask_final_rule else {
                "rule_so_far": rule_so_far,
                "moveHistory": moveHistory,
                "bucket_state": bucket_state,
                "move_history": moveHistory,
                "cleared": False
            }
        jsonLine = jsonLine.strip()

        [code, status, t] = map(int, re.split(r"\s+", statusLine))
        val = json.loads(jsonLine)["value"]
    

        # --- status handling (same logic as before) ---
        if code == 0 and last_move:
            
            piece_id, bucket_id = map(int, last_move.split())
            moved_piece = None
            if prev_val is not None:
                moved_piece = next((p for p in prev_val if p["id"] == piece_id), None)
            if not moved_piece:
                moved_piece = next((p for p in val if p["id"] == piece_id), None)
            if moved_piece:
                shape = moved_piece.get("shape", "unknown")
                color = moved_piece.get("color", "unknown")
                x = moved_piece.get("x", "unknown")
                y = moved_piece.get("y", "unknown")
                bucket_state[bucket_id].append(f"{piece_id}: {color} {shape} @({x},{y})")
            immovable_pieces.clear()
            last_result = "Move accepted."
            moveHistory[last_move] = last_result
        elif code == 4 and last_move:
            last_result = "Move denied."
            moveHistory[last_move] = last_result
        elif code == 7 and last_move:
            try:
                piece_id = int(last_move.split()[0])
                imm_piece = None
                if prev_val is not None:
                    imm_piece = next((p for p in prev_val if p["id"] == piece_id), None)
                if not imm_piece:
                    imm_piece = next((p for p in val if p["id"] == piece_id), None)
                immovable_pieces.add(piece_id)
                if imm_piece:
                    last_result = f"Piece {piece_id} immovable ({imm_piece.get('color','?')} {imm_piece.get('shape','?')} @({imm_piece.get('x','?')},{imm_piece.get('y','?')}))."
                else:
                    last_result = f"Piece {piece_id} immovable."
            except Exception:
                last_result = "Piece immovable (unknown id)."
            moveHistory[last_move] = last_result
            
        elif code == 6 and not last_move:
            last_result = None
        else:
            last_result = f"Code {code}."

        # --- Episode termination handling ---
        if status == 1:
            sys.stdout.write(f"Cleared board in {t} steps.\n")
            # If caller asked us to request the final rule now -> follow previous behavior
            if ask_final_rule:

                prompt_messages = build_prompt(rule_so_far, last_move, last_result, val, bucket_state, immovable_pieces, moveHistory)
                gemResponse = call_gemini(client, prompt_messages)
                move_line = next((l for l in gemResponse.splitlines() if l.strip().upper().startswith("MOVE:")), None)
                rule_line = next((l for l in gemResponse.splitlines() if l.strip().upper().startswith("RULE:")), None)
                hypothesis_line = next((l for l in gemResponse.splitlines() if l.strip().upper().startswith("HYPOTHESIS:")), None)
                if move_line:
                    gemMove = move_line.split(":",1)[1].strip()
                    moveHistory[gemMove] = 'pending'
                    last_move = gemMove
                else:
                    sys.stdout.write(f"Invalid Gemini move format: {gemResponse}\n")
                    break

                if rule_line:
                    rule_so_far = rule_line.split(":",1)[1].strip()

                if hypothesis_line:
                    hypothesis = hypothesis_line.split(":", 1)[1].strip()
                return rule_so_far, hypothesis
            else:
                # Return an episode summary to the caller (no final Gemini call)
                return {
                    "rule_so_far": rule_so_far,
                    "moveHistory": moveHistory,
                    "bucket_state": bucket_state,
                    "cleared": True,
                    "steps": t
                }

        elif status == 2:
            sys.stdout.write(f"Stalemate after {t} steps.\n")
            # For ask_final_rule==True preserve old behavior (return rule_so_far), else return summary
            if ask_final_rule:
                return rule_so_far
            else:
                return {
                    "rule_so_far": rule_so_far,
                    "moveHistory": moveHistory,
                    "bucket_state": bucket_state,
                    "cleared": False,
                    "steps": t
                }

        valid_move = False
        chances = 4  # max reprompts if Gemini suggests invalid piece_id
        # After extracting gemMove
        while not valid_move and chances > 0:
            chances -= 1
            prompt_messages = build_prompt(rule_so_far, hypothesis, last_move, last_result, val, bucket_state, immovable_pieces, moveHistory)
            gemResponse = call_gemini(client, prompt_messages)
            move_line = next((l for l in gemResponse.splitlines() if l.strip().upper().startswith("MOVE:")), None)
            rule_line = next((l for l in gemResponse.splitlines() if l.strip().upper().startswith("RULE:")), None)
            hypothesis_line = next((l for l in gemResponse.splitlines() if l.strip().upper().startswith("HYPOTHESIS:")), None)
            if rule_line:
                rule_so_far = rule_line.split(":", 1)[1].strip()

            if hypothesis_line:
                hypothesis = hypothesis_line.split(":", 1)[1].strip()
                

            if move_line:
                gemMove = move_line.split(":", 1)[1].strip()
                
                # --- Extract piece_id from Gemini move (assuming format like "{piece_id}, {bucket_id}") ---
                try:
                    suggested_piece_id = int(gemMove.split()[0].strip())
                    suggested_bucket_id = int(gemMove.split()[1].strip())
                except Exception as e:
                    sys.stdout.write(f"Could not parse piece_id from Gemini move '{gemMove}': {e}\n")
                    suggested_piece_id = None
                    suggested_bucket_id = None

                # Check if piece_id exists on the board
                valid_piece_ids = {p["id"] for p in val}
                if suggested_piece_id not in valid_piece_ids:
                    sys.stdout.write(f"Gemini suggested piece_id {suggested_piece_id}, which is not on the board. Reprompting...\n")
                    if suggested_piece_id in immovable_pieces:
                        message = f"The suggested piece_id {suggested_piece_id} is immovable. Please suggest a different piece_id."
                    elif suggested_piece_id == -1:
                        message = "The suggested piece_id is -1, which is not valid unless the board is cleared. Please suggest a valid piece_id."
                    else:
                        message = f"The suggested piece_id {suggested_piece_id} is not present on the board. Please suggest a valid piece_id from the current board state."

                elif suggested_bucket_id not in {0, 1, 2, 3}:
                    sys.stdout.write(f"Gemini suggested invalid bucket_id {suggested_bucket_id}. Reprompting...\n")
                    message = f"The suggested bucket_id {suggested_bucket_id} is invalid. Please suggest a bucket_id from 0, 1, 2, or 3."

                else:
                    valid_move = True
                    break                  
                
            else:
                message = "Gemini did not return a valid move format. Please suggest a valid move in the format: MOVE: <piece_id>, <bucket_id>."
                
            # Reprompt Gemini with clarification
                prompt_messages.append({
                    "role": "user",
                    "content": message
                })

        moveHistory[gemMove] = 'pending'
        last_move = gemMove
        sys.stdout.write(f"Rule so far: {rule_so_far}\n")
        sys.stdout.write(f"Immovable pieces: {sorted(list(immovable_pieces))}\n")
        sys.stdout.write(f"Hypothesis: {hypothesis}\n")

        # Send the move to server
        try:
            moveData = mapMove(val, int(gemMove.split()[0]), int(gemMove.split()[1]))
            send = "MOVE " + " ".join(map(str, moveData))
            sys.stdout.write(f"Sending: {send}\n")
            outx.write((send + "\n").encode())
            outx.flush()
        except Exception as e:
            sys.stdout.write(f"Error mapping move: {e}\n")
            break

        # set prev_val for next iteration
        prev_val = val

    # If we fall out of loop unexpectedly:
    if ask_final_rule:
        return rule_so_far
    else:
        return {
            "rule_so_far": rule_so_far,
            "moveHistory": moveHistory,
            "bucket_state": bucket_state,
            "cleared": False
        }



def mainLoopB(inx,outx, rule): 
    #----------------------------------------------------------------------
    #-- Keep playing until the episode is finished
    successful_moves = 0
    client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))
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
        return
    w = json.loads(jsonLine)
    val = w["value"]
    total_pieces = len(val)
    conversation = [{
        "role": "system",
        "content": SYSTEM_PROMPT_B.replace("{total_pieces}", str(total_pieces))
    }]
    conversation.append({"role": "user", "content": rule})

    conversation = update_prompt(conversation, val, code, None)
    movesText = call_gemini(client, conversation)
    sys.stdout.write("Gemini raw response:\n" + movesText + "\n")

    # --- minimal parsing assuming fenced ```json ... ``` output ---
    if movesText.strip().startswith("```json"):
        lines = movesText.strip().splitlines()
        # drop the first and last fence lines and join the middle JSON lines
        moves_json_text = "\n".join(lines[1:-1]).strip()
    else:
        # fallback: assume entire response is JSON
        moves_json_text = movesText.strip()

    try:
        parsed = json.loads(moves_json_text)
    except Exception as e:
        sys.stdout.write("Failed to parse JSON from Gemini response: " + str(e) + "\n")
        return 0

    # Separate thinking (optional) and moves (required)
    thinking_text = parsed.get("thinking")
    if thinking_text:
        sys.stdout.write("Gemini thinking:\n" + str(thinking_text) + "\n")

    if "moves" not in parsed:
        sys.stdout.write("Parsed JSON does not contain 'moves'. Aborting.\n")
        return 0

    moves = parsed["moves"]

    # now continue with your existing loop over moves:
    for move in moves:
        bucket_id = move["bucket_id"] if "bucket_id" in move else None
        piece_id = move["piece_id"] if "piece_id" in move else None
        moveData = mapMove(val, piece_id, bucket_id)
        send = "MOVE " + " ".join( map(repr, moveData))
        sys.stdout.write("Sending: "+ send + "\n")

        outx.write((send + "\n").encode())
        outx.flush()


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
        if code == 0: successful_moves+=1

        if status==0:
            0  #-- all's fine, print no message
            #sys.stdout.write("Keep going...\n")
        elif status==1:
            sys.stdout.write("Cleared board in "+ repr(t) + " steps\n")
            return float(successful_moves/total_pieces)
        elif status==2:
            sys.stdout.write("Stalemate after  "+ repr(t) + " steps\n")
            return 0
        else:
            sys.stdout.write("Unknown status "+repr(status)+"\n")
            
        if not jsonLine:
            sys.stdout.write("No JSON received!\n")
            return 0

        #-- Parse the JSON line into a Python structure
        w = json.loads(jsonLine)
        val = w["value"]
    
        
        

   