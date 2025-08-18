#----------------------------------------------------------------------
# gameLoopGemini.py
# Maintains hypotheses (max 3), uses Bayesian updates from cited move UIDs.
#----------------------------------------------------------------------

# This gameloop is in BETA and may not work as intended.
import subprocess, sys, re, random, json
from google import genai
from dotenv import load_dotenv
import os
#----------------------------------------------------------------------
load_dotenv()

# ---------------- Config ----------------
MAX_HYPOTHESES = 3
MIN_CONF = 0.03
ALPHA0 = 1.0
BETA0 = 1.0
MAX_DELTA = 0.15
WEIGHT_UNOBSERVED = 0.05

# ---------------- Prompts ----------------
SYSTEM_PROMPT_A = """
# Help me play a game. It involves moving pieces on a board into one of four buckets, following a hidden rule. 
# The board has pieces, each with an id, shape, color, and (x,y) position. Each piece can be cleared by moving it into one of the four buckets.
# Your task is to figure out the rule by making moves and observing their results while hypothesizing about the rule.
# Note on hypotheses:
1. You will be provided with a set of hypotheses about the rule that you yourself have generated.
1. The system you are working with will discard hypotheses that are not strongly supported by the moves you make.
2. 
Input you will receive: a JSON of current hypotheses (id, description, confidence optional),
the most recent move UID list and their results, the board pieces, bucket state, immovable pieces and move history.

Return EXACTLY one JSON object with keys:
{
  "hypotheses": [
    {
      "id": "H1",
      "description": "candidate description",
      "support_ids": ["M3","M7"],        # move UIDs that the hypothesis claims support it
      "contradict_ids": ["M2"],         # move UIDs that the hypothesis claims contradict it
      "confidence_note": "note if no constradiction exists",
      "note": "optional short note"
    },
    ...
  ],
  "move": {"piece_id": <int>, "bucket_id": <0|1|2|3>, "uid": "M12"},
  "thinking": "short optional reasoning"
}

Rules:
- Do NOT include a confidence field. The system will compute confidences automatically from cited move UIDs.
- For evidence, cite move UIDs (format 'M<number>'). The code only trusts those explicit UIDs.
- piece_id must exist on the current board (or -1 only if board is empty).
- bucket_id must be 0,1,2,3.
- Return only the JSON object (no extra text).
-



"""

SYSTEM_PROMPT_B = """
Help me clear a board game. You will be provided:
1. A text explanation of a rule.
2. The current state of the board in JSON format.

Return EXACTLY one JSON object with:
{ "thinking": "...", "moves": [ {"piece_id":id,"bucket_id":0}, ... ] }
The moves array must have exactly {total_pieces} entries.
"""

# ---------------- Utilities ----------------
def readLine(inx):
    while True:
        s = inx.readline()
        sys.stdout.write("Received: "+ str(s) + "\n")
        if not s:
            return s
        s = s.decode()
        if s.startswith('#'):
            continue
        return s

def extract_json_blob(text):
    txt = text.strip()
    if txt.startswith("```"):
        parts = txt.split("```")
        for p in parts:
            p = p.strip()
            if p.startswith("{") and p.endswith("}"):
                return p
    if "{" in txt and "}" in txt:
        first = txt.find("{"); last = txt.rfind("}")
        if first != -1 and last != -1 and last > first:
            return txt[first:last+1]
    return txt

def call_gemini(client, conversation):
    full_prompt = ""
    for msg in conversation:
        role = msg["role"]
        content = msg.get("content", msg.get("observation", ""))
        full_prompt += f"{role.upper()}:\n{content}\n\n"
    response = client.models.generate_content(model="gemini-2.5-flash", contents = full_prompt)
    return response.text

# ---------------- Move mapping ----------------
def mapMove(val, id, b):
    piece = next((it for it in val if it["id"] == id), None)
    if piece is None:
        raise ValueError(f"Piece with id {id} not found.")
    x = piece["x"]; y = piece["y"]
    if b == 0:
        bx, by = 0, 7
    elif b == 1:
        bx, by = 7, 7
    elif b == 2:
        bx, by = 7, 0
    elif b == 3:
        bx, by = 0, 0
    else:
        raise ValueError(f"Invalid bucket {b}")
    return [y, x, by, bx]

# ---------------- Prompt builder (no rule) ----------------
def build_prompt_no_rule(hypotheses, last_move_uid, last_result, val, bucket_state, immovable_pieces, move_history):
    hyps_to_send = sorted(hypotheses, key=lambda h: h.get("confidence", 0.0), reverse=True)[:MAX_HYPOTHESES]
    pieces_info = [f"id:{p['id']} shape:{p['shape']} color:{p['color']} x:{p['x']} y:{p['y']}" for p in val]
    board_state = " | ".join(pieces_info)
    bucket_summary = "; ".join(f"Bucket {b}: [{', '.join(bucket_state[b])}]" if bucket_state and bucket_state.get(b) else f"Bucket {b}: [empty]" for b in range(4))
    mh_lines = []
    for uid, info in move_history.items():
        mh_lines.append(f"{uid}: {info.get('move','')} -> {info.get('result','')}")
    hyp_text = json.dumps(hyps_to_send, ensure_ascii=False)
    return [
        {"role":"system", "content": SYSTEM_PROMPT_A},
        {"role":"user", "content": "Hypotheses (top 3): " + hyp_text},
        {"role":"user", "content": f"Last_move_uid: {last_move_uid if last_move_uid else 'None'}"},
        {"role":"user", "content": f"Last_result: {last_result if last_result else 'N/A'}"},
        {"role":"user", "content": "Immovable pieces: " + (", ".join(str(x) for x in sorted(list(immovable_pieces))) if immovable_pieces else "None")},
        {"role":"user", "content": "Board: " + board_state},
        {"role":"user", "content": "Bucket state: " + bucket_summary},
        {"role":"user", "content": "Move history:\n" + ("\n".join(mh_lines) if mh_lines else "None")}
    ]

# ---------------- Bayesian helpers ----------------
def map_result_label(result_text):
    if not result_text: return None
    s = result_text.lower()
    if "accept" in s: return "accepted"
    if "deni" in s: return "denied"
    if "immov" in s: return "immovable"
    return None

def compute_support_contradiction_from_ids(hyp, move_history):
    support = 0.0
    contradict = 0.0
    s_ids = hyp.get("support_ids", []) or []
    c_ids = hyp.get("contradict_ids", []) or []
    for uid in s_ids:
        info = move_history.get(uid)
        if info:
            obs = map_result_label(info.get("result",""))
            if obs == "accepted":
                support += 1.0
            else:
                contradict += 1.0
        else:
            support += WEIGHT_UNOBSERVED
    for uid in c_ids:
        info = move_history.get(uid)
        if info:
            obs = map_result_label(info.get("result",""))
            if obs == "accepted":
                contradict += 1.0
            else:
                # model claimed contradiction but observed != accepted -> do not add strong contradiction
                contradict += 0.0
        else:
            contradict += WEIGHT_UNOBSERVED
    return support, contradict

def bayesian_confidence(alpha0, beta0, support, contradict):
    denom = alpha0 + beta0 + support + contradict
    if denom <= 0:
        return 0.0
    raw = (alpha0 + support) / denom
    return max(0.0, min(0.999, raw))

# ---------------- update_prompt used by mainLoopB ----------------
def update_prompt(conversation, val, code, bucket_state):
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

# ---------------- Main loops ----------------
def mainLoop(inx, outx, test, rule, episodes=1):
    if test:
        stats = mainLoopB(inx, outx, rule)
        return stats
    else:
        if episodes <= 1:
            hypotheses = mainLoopA(inx, outx)
            outx.write("EXIT\n".encode()); outx.flush()
            return hypotheses
        else:
            final_hyps = severalEpisodes(inx, outx, episodes)
            return final_hyps

def severalEpisodes(inx, outx, N):
    client = genai.Client(api_key=os.getenv("GEMINI_API_KEY_mc"))
    hypotheses = []
    for j in range(0, N):
        sys.stdout.write(f"Starting episode {j+1}/{N}\n")
        hypotheses = mainLoopA(inx, outx, ask_final_rule=False, initial_hypotheses=hypotheses)
        cmd = "EXIT\n" if (j == N-1) else "NEW\n"
        outx.write(cmd.encode()); outx.flush()
    return hypotheses

def mainLoopA(inx, outx, ask_final_rule=True, initial_hypotheses=None):
    client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))
    bucket_state = {0: [], 1: [], 2: [], 3: []}
    immovable_pieces = set()
    move_counter = 0
    last_move_uid = None
    last_move = None
    last_result = None
    move_history = {}
    hypotheses = []

    if initial_hypotheses:
        tmp = []
        for i, h in enumerate(initial_hypotheses):
            tmp.append({
                "id": h.get("id", f"H{i+1}"),
                "description": h.get("description", ""),
                "confidence": float(h.get("confidence", 0.2)),
                "evidence": h.get("evidence", [])
            })
        tmp.sort(key=lambda x: x.get("confidence", 0.0), reverse=True)
        hypotheses = tmp[:MAX_HYPOTHESES]
    else:
        hypotheses = []

    prev_val = None

    while True:
        statusLine = readLine(inx)
        if not statusLine:
            sys.stdout.write("No status line received. Exiting episode.\n")
            return hypotheses
        statusLine = statusLine.strip()
        jsonLine = readLine(inx)
        if not jsonLine:
            sys.stdout.write("No json line received. Exiting episode.\n")
            return hypotheses
        jsonLine = jsonLine.strip()

        [code, status, t] = map(int, re.split(r"\s+", statusLine))
        val = json.loads(jsonLine)["value"]

        # Update previous move result if pending
        if last_move_uid is not None:
            if code == 0:
                result_text = "Move accepted."
            elif code == 4:
                result_text = "Move denied."
            elif code == 7:
                result_text = "Piece immovable."
            else:
                result_text = f"Code {code}."
            if last_move_uid in move_history:
                move_history[last_move_uid]["result"] = result_text
                move_history[last_move_uid]["step"] = t
            else:
                move_history[last_move_uid] = {"move": last_move, "result": result_text, "piece_id": None, "bucket_id": None, "step": t}
            last_result = result_text
            if code == 0:
                try:
                    pid = move_history[last_move_uid]["piece_id"]
                    bid = move_history[last_move_uid]["bucket_id"]
                    moved_piece = None
                    if prev_val is not None:
                        moved_piece = next((p for p in prev_val if p["id"]==pid), None)
                    if not moved_piece:
                        moved_piece = next((p for p in val if p["id"]==pid), None)
                    if moved_piece:
                        color = moved_piece.get("color","?")
                        shape = moved_piece.get("shape","?")
                        x = moved_piece.get("x","?")
                        y = moved_piece.get("y","?")
                        bucket_state[bid].append(f"{pid}: {color} {shape} @({x},{y})")
                except Exception:
                    pass
                try:
                    immovable_pieces.clear()
                except Exception:
                    pass
            if code == 7:
                try:
                    uid = last_move_uid
                    pid = move_history[uid].get("piece_id")
                    if pid is not None:
                        immovable_pieces.add(pid)
                except:
                    pass
            last_move_uid = None

        # Termination handling
        if status == 1:
            sys.stdout.write(f"Cleared board in {t} steps.\n")
            return hypotheses
        if status == 2:
            sys.stdout.write(f"Stalemate after {t} steps.\n")
            return hypotheses

        # Build prompt (top 3 hypotheses only)
        prompt_msgs = build_prompt_no_rule(hypotheses, last_move_uid, last_result, val, bucket_state, immovable_pieces, move_history)
        gem_raw = call_gemini(client, prompt_msgs)
        sys.stdout.write("Gemini raw response:\n" + gem_raw + "\n")
        blob = extract_json_blob(gem_raw)
        try:
            parsed = json.loads(blob)
        except Exception as e:
            sys.stdout.write("Failed to parse JSON from Gemini: " + str(e) + "\n")
            reprompt = prompt_msgs + [{"role":"user","content":"Your previous reply could not be parsed. Return exactly the JSON object specified."}]
            gem_raw = call_gemini(client, reprompt)
            blob = extract_json_blob(gem_raw)
            try:
                parsed = json.loads(blob)
            except Exception as e2:
                sys.stdout.write("Second parse failed: " + str(e2) + ". Doing fallback move.\n")
                try:
                    fallback = next((p for p in val if p.get("buckets") and len(p.get("buckets"))>0), val[0])
                    piece_id = fallback["id"]
                except:
                    piece_id = val[0]["id"]
                bucket_id = 0
                move_counter += 1
                uid = f"M{move_counter}"
                move_history[uid] = {"move": f"{piece_id} {bucket_id}", "result": "pending", "piece_id": piece_id, "bucket_id": bucket_id}
                last_move = f"{piece_id} {bucket_id}"; last_move_uid = uid
                try:
                    moveData = mapMove(val, piece_id, bucket_id)
                    outx.write(("MOVE " + " ".join(map(str, moveData)) + "\n").encode()); outx.flush()
                except Exception as ex:
                    sys.stdout.write("Fallback mapping failed: " + str(ex) + "\n")
                prev_val = val
                continue

        if not isinstance(parsed, dict) or "move" not in parsed or "hypotheses" not in parsed:
            sys.stdout.write("Parsed JSON missing 'move' or 'hypotheses'. Performing safe fallback move.\n")
            try:
                fallback = next((p for p in val if p.get("buckets") and len(p.get("buckets"))>0), val[0])
                piece_id = fallback["id"]
            except:
                piece_id = val[0]["id"]
            bucket_id = 0
            move_counter += 1
            uid = f"M{move_counter}"
            move_history[uid] = {"move": f"{piece_id} {bucket_id}", "result": "pending", "piece_id": piece_id, "bucket_id": bucket_id}
            last_move = f"{piece_id} {bucket_id}"; last_move_uid = uid
            try:
                moveData = mapMove(val, piece_id, bucket_id)
                outx.write(("MOVE " + " ".join(map(str, moveData)) + "\n").encode()); outx.flush()
            except Exception as ex:
                sys.stdout.write("Fallback mapping failed: " + str(ex) + "\n")
            prev_val = val
            continue

        # Normalize model hypotheses (DO NOT read model-provided confidence)
        model_hyps = parsed.get("hypotheses", []) or []
        normalized = []
        for i, h in enumerate(model_hyps):
            try:
                hid = h.get("id", f"H{i+1}")
                desc = str(h.get("description", ""))
                s_ids = h.get("support_ids", []) or []
                c_ids = h.get("contradict_ids", []) or []
                note = h.get("note", "")
                normalized.append({
                    "id": hid,
                    "description": desc,
                    "support_ids": s_ids,
                    "contradict_ids": c_ids,
                    "evidence_note": note,
                    "evidence": h.get("evidence", [])
                })
            except Exception:
                continue

        # Compute support/contradict and Bayesian update + per-turn cap
        updated_hyps = []
        prior_conf_default = ALPHA0 / (ALPHA0 + BETA0)
        for h in normalized:
            support, contradict = compute_support_contradiction_from_ids(h, move_history)
            old_conf = next((ph["confidence"] for ph in hypotheses if ph["id"] == h["id"]), prior_conf_default)
            conf_raw = bayesian_confidence(ALPHA0, BETA0, support, contradict)
            delta_raw = conf_raw - old_conf
            if delta_raw > MAX_DELTA: delta = MAX_DELTA
            elif delta_raw < -MAX_DELTA: delta = -MAX_DELTA
            else: delta = delta_raw
            conf_new = max(0.0, min(0.999, old_conf + delta))
            h.setdefault("evidence", [])
            h["evidence"].append(f"auto:+{support:.2f}-:{contradict:.2f} => {conf_new:.3f}")
            h["confidence"] = conf_new
            updated_hyps.append(h)

        # carry forward previous hypotheses not returned by model
        prev_map = {ph["id"]: ph for ph in hypotheses}
        model_map = {ph["id"]: ph for ph in updated_hyps}
        for pid, ph in prev_map.items():
            if pid not in model_map:
                updated_hyps.append(ph)

        # prune and cap
        updated_hyps = sorted(updated_hyps, key=lambda x: x.get("confidence", 0.0), reverse=True)
        updated_hyps = [h for h in updated_hyps if h.get("confidence", 0.0) >= MIN_CONF]
        updated_hyps = updated_hyps[:MAX_HYPOTHESES]
        hypotheses = updated_hyps

        # Use the move suggested by model (validate)
        move_obj = parsed.get("move", {})
        piece_id = move_obj.get("piece_id")
        bucket_id = move_obj.get("bucket_id")
        uid_from_model = move_obj.get("uid") if isinstance(move_obj.get("uid"), str) else None
        valid_piece_ids = {p["id"] for p in val}
        if piece_id is None or bucket_id is None or piece_id not in valid_piece_ids or bucket_id not in {0,1,2,3}:
            sys.stdout.write(f"Model suggested invalid move: {move_obj}. Falling back.\n")
            try:
                fallback = next((p for p in val if p.get("buckets") and len(p.get("buckets"))>0), val[0])
                piece_id = fallback["id"]
            except:
                piece_id = val[0]["id"]
            bucket_id = 0
            uid_from_model = None

        move_counter += 1
        uid = uid_from_model if (uid_from_model and uid_from_model not in move_history) else f"M{move_counter}"
        move_history[uid] = {"move": f"{piece_id} {bucket_id}", "result": "pending", "piece_id": piece_id, "bucket_id": bucket_id}
        last_move = f"{piece_id} {bucket_id}"
        last_move_uid = uid

        try:
            moveData = mapMove(val, int(piece_id), int(bucket_id))
            send = "MOVE " + " ".join(map(str, moveData))
            sys.stdout.write(f"Sending: {send} (uid={uid})\n")
            outx.write((send + "\n").encode()); outx.flush()
        except Exception as e:
            sys.stdout.write("Error mapping move: " + str(e) + "\n")
            return hypotheses

        prev_val = val

    # end while
    return hypotheses

# ---------------- mainLoopB (JSON thinking + moves) ----------------
def mainLoopB(inx,outx, rule):
    client = genai.Client(api_key=os.getenv("GEMINI_API_KEY"))
    statusLine = readLine(inx)
    if not statusLine:
        sys.stdout.write("No status line at start of mainLoopB.\n"); return 0
    statusLine = statusLine.strip()
    jsonLine = readLine(inx)
    if not jsonLine:
        sys.stdout.write("No json at start of mainLoopB.\n"); return 0
    jsonLine = jsonLine.strip()

    sys.stdout.write("Unpack: "+ statusLine + "\n")
    [code, status, t] = map( int, re.split(r"\s+", statusLine))
    sys.stdout.write("Code=" +repr(code)+ ", status=" +repr(status)+ ", stepNo="+repr(t)+ "\n")
    if code<0:
        sys.stdout.write("Error code"+ repr(code) + "\n")
        sys.stdout.write("Error message: "+ jsonLine + "\n")
        return 0

    w = json.loads(jsonLine)
    val = w["value"]
    total_pieces = len(val)
    conversation = [{"role": "system", "content": SYSTEM_PROMPT_B.replace("{total_pieces}", str(total_pieces))}]
    conversation.append({"role": "user", "content": rule})
    conversation = update_prompt(conversation, val, code, None)
    movesText = call_gemini(client, conversation)
    sys.stdout.write("Gemini raw response:\n" + movesText + "\n")

    if movesText.strip().startswith("```json"):
        lines = movesText.strip().splitlines()
        moves_json_text = "\n".join(lines[1:-1]).strip()
    else:
        moves_json_text = movesText.strip()

    try:
        parsed = json.loads(moves_json_text)
    except Exception as e:
        sys.stdout.write("Failed to parse JSON from Gemini response: " + str(e) + "\n")
        return 0

    thinking_text = parsed.get("thinking")
    if thinking_text:
        sys.stdout.write("Gemini thinking:\n" + str(thinking_text) + "\n")

    if "moves" not in parsed:
        sys.stdout.write("Parsed JSON does not contain 'moves'. Aborting.\n")
        return 0

    moves = parsed["moves"]
    successful_moves = 0
    for move in moves:
        bucket_id = move.get("bucket_id")
        piece_id = move.get("piece_id")
        try:
            moveData = mapMove(val, piece_id, bucket_id)
        except Exception as e:
            sys.stdout.write(f"Error mapping move (piece {piece_id}, bucket {bucket_id}): {e}\n")
            continue
        send = "MOVE " + " ".join( map(repr, moveData))
        sys.stdout.write("Sending: "+ send + "\n")
        outx.write((send + "\n").encode()); outx.flush()

        statusLine = readLine(inx)
        jsonLine = readLine(inx)
        if not statusLine or not jsonLine:
            sys.stdout.write("No response after move.\n"); return float(successful_moves/total_pieces) if total_pieces>0 else 0
        statusLine = statusLine.strip(); jsonLine = jsonLine.strip()
        sys.stdout.write("Unpack: "+ statusLine + "\n")
        [code, status, t] = map(int, re.split(r"\s+", statusLine))
        sys.stdout.write("Code=" +repr(code)+ ", status=" +repr(status)+ ", stepNo="+repr(t)+ "\n")
        if code<0:
            sys.stdout.write("Error code"+ repr(code) + "\n"); sys.stdout.write("Error message: "+ jsonLine + "\n"); break
        if code == 0: successful_moves += 1
        if status == 1:
            sys.stdout.write("Cleared board in "+ repr(t) + " steps\n")
            return float(successful_moves/total_pieces)
        elif status==2:
            sys.stdout.write("Stalemate after  "+ repr(t) + " steps\n")
            return 0
        try:
            w = json.loads(jsonLine); val = w.get("value", val)
        except:
            sys.stdout.write("Warning: failed to parse board JSON after move; keeping previous board snapshot.\n")
    return float(successful_moves/total_pieces) if total_pieces>0 else 0

# End of file
