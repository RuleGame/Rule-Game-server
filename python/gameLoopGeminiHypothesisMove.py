#----------------------------------------------------------------------
# gameLoopGemini_full.py
# Single-file implementation containing:
#  - hypothesis generation (new-only)
#  - per-hypothesis evidence extraction (N^2 reduced)
#  - Bayesian updating of confidences
#  - top-K move generation (send top 3 hypotheses to move LLM)
#  - deduplication, pruning, immovable tracking, simple validation
#
# Added: PRE_HYP_MOVES to control how many moves to make before asking
#        for new hypotheses / re-running evidence extraction + Bayesian update.
#----------------------------------------------------------------------

import subprocess, sys, re, random, json, math
from google import genai
from dotenv import load_dotenv
import os
#----------------------------------------------------------------------
load_dotenv()

# ---------------- Config (tweak as desired) ----------------
MAX_ACTIVE_HYPS = 10        # keep at most 10 hypotheses in the active pool
TOP_K_FOR_MOVE_MODEL = 5    # send top 5 hypotheses (descriptions + ids) to move model
MAX_HYPOTHESES_TO_RETURN = 5  # how many new hyps model may propose (recommend small)
MIN_CONF = 0.03
ALPHA0 = 1.0
BETA0 = 1.0
MAX_DELTA = 0.30            # per-turn cap on confidence change
WEIGHT_UNOBSERVED = 0.05    # small weight if model cites a UID not present in history

# NEW: how many moves to perform before asking for new hypotheses / recomputing confidences.
# If PRE_HYP_MOVES = 0 then we generate hypotheses every loop as before.
PRE_HYP_MOVES = 2

# ---------------- System Prompts ----------------
SYSTEM_PROMPT_HYP_GEN = """
# Help me play a game. It involves moving pieces on a board into one of four buckets, following a hidden rule which can be static or dynamic. 
# The board has pieces, each with an id, shape, color, and (x,y) position. Each piece can be cleared by moving it into one of the four buckets.
# Your task is to help generate candidate hypotheses about the hidden rule by inspecting the board, the move history, and observed move results.

# Note on generating and understanding hypotheses:
1. You will be provided with a set of hypotheses about the rule that you yourself have generated.
2. The system will discard hypotheses that are not strongly supported by the moves you cite.
3. Form hypotheses about the hidden rule that governs how the board can be cleared. It should be positive.
4. Each hypothesis should explain how "all" the pieces can be cleared from the board successfully and not a single kind.
5. If the provided hypotheses have low confidence please suggest completelynew ones that are more plausible.

Input you will receive:
- A JSON list of current/top hypotheses (id + description + optional computed confidence).  
- The most recent move UID and its result (if any).
- The board pieces (id,shape,color,x,y).
- Bucket state and coordinates.
- Discovered immovable pieces.
- The full recent move history with UIDs (format: M<number>, and each move line includes piece id, target bucket, and observed result).
  Example move-history line: `M12: 5 0 -> Move accepted.`

Limits & format:
- Return EXACTLY one JSON object (no extra text) with the key `new_hypotheses` whose value is a list of hypothesis objects.
- Use unique IDs for the new hypotheses, e.g., `NH1`, `NH2`, ...
- If uncertain, return: `{"new_hypotheses": []}` rather than inventing evidence.
- The system will re-validate any cited UIDs; do not cite UIDs not present in the provided move history.

Each hypothesis object schema (required):
{
  "id": "NH1",
  "description": "verbose rule",
  "support_ids": ["M3","M7"],          # list of UIDs from supplied move history that support this hypothesis (may be [])
  "contradict_ids": ["M2"],            # list of UIDs from supplied move history that contradict it (may be [])
  "thinking": "1-2 sentence explanation why these UIDs support/contradict"  # short reasoning
}

Rules:
- Do NOT include a confidence field. The system computes confidences automatically.
- For evidence, cite move UIDs (format 'M<number>'). The system only trusts those explicit UIDs.
- piece_id must exist on the current board (or -1 only if board is empty).
- bucket_id must be 0,1,2,3. The coordinates for buckets are (x,y) positions:
  - Bucket 0: (0,7)
  - Bucket 1: (7,7)
  - Bucket 2: (7,0)
  - Bucket 3: (0,0)
- Return only the JSON object (no extra text).


Important: If you cannot produce valid JSON exactly as specified, return {"new_hypotheses": []} rather than prose.
"""

SYSTEM_PROMPT_EVIDENCE = """
You are given:
- A single hypothesis (short description).
- A list of move UIDs and the full move lines (each line like "M12: 5 0 -> Move accepted.").

Task:
Return EXACTLY one JSON object with keys:
{
  "support_ids": [ ... ],      # subset of the provided UIDs that support the hypothesis
  "contradict_ids": [ ... ]    # subset that contradict the hypothesis
  "thinking": "1-2 sentence explanation why these UIDs support/contradict or are not relevant"  # in depth reasoning
}

Rules:
- A move "supports" the hypothesis if the outcome of that move (accepted/denied/immovable)
  is consistent evidence *for* the hypothesis as stated.
- A move "contradicts" if its outcome is evidence *against* the hypothesis.
- Check for contradictions carefully because they have a stronger impact on the validity of the hypothesis.
- A move neither supports nor contradicts if it's irrelevant to the hypothesis. So check that carefully.
- Only list UIDs that were supplied in the moves list.

"""

SYSTEM_PROMPT_MOVE = """
You will receive:
- Up to {k} candidate hypotheses (id + description) that may explain the hidden rule.
- Board snapshot (pieces with id, shape, color, x, y).
- Bucket state & immovable pieces & brief move history.

Task:
Return EXACTLY one JSON object with:
{
  "move": {"piece_id": <int>, "bucket_id": <0|1|2|3>},
  "thinking": "optional short text about why you chose the move (may reference hypothesis ids)."
}

Rules:
- piece_id must be present on the current board.
- bucket_id must be 0,1,2,3.
- Return only the JSON object, no extra text.
- You can "randomly" choose any hypothesis to test. 
- Don't be biased by confidence scores
"""


SYSTEM_PROMPT_B = """
Help me clear a board game. You will be provided:
1. Top hypotheses of what the hidden rule to clear the pieces from the board could be.
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
    """
    Extract a JSON object or array from text that may include surrounding fences or commentary.
    Returns the trimmed JSON string (possibly the whole text if it's pure JSON).
    """
    if text is None:
        return text
    txt = text.strip()
    # handle fenced ````json blocks
    if txt.startswith("```"):
        parts = txt.split("```")
        # find a part that looks like JSON
        for p in parts:
            p = p.strip()
            if p.startswith("{") and p.endswith("}") or (p.startswith("[") and p.endswith("]")):
                return p
    # fallback: grab first {...} or [...]
    if "{" in txt and "}" in txt:
        first = txt.find("{"); last = txt.rfind("}")
        if first != -1 and last != -1 and last > first:
            return txt[first:last+1]
    if "[" in txt and "]" in txt:
        first = txt.find("["); last = txt.rfind("]")
        if first != -1 and last != -1 and last > first:
            return txt[first:last+1]
    return txt

import time
import threading

# ---- Rate-limit / retry globals (for call_gemini) ----
_GEMINI_LAST_CALL = 0.0
_GEMINI_LOCK = threading.Lock()

def call_gemini(client, conversation, max_retries=5):
    """
    Call Gemini with reactive rate limiting - send requests until quota error, then wait.
    No pre-emptive waiting between requests.
    """
    # Build full prompt text as before
    full_prompt = ""
    for msg in conversation:
        role = msg.get("role", "")
        content = msg.get("content", msg.get("observation", ""))
        full_prompt += f"{role.upper()}:\n{content}\n\n"

    attempt = 0
    backoff = 1.0

    while True:
        attempt += 1

        try:
            response = client.models.generate_content(model="gemini-2.5-flash", contents=full_prompt)
            return response.text

        except Exception as e:
            msg = str(e)
            # detect quota / 429-like errors from exception message
            is_quota = ('RESOURCE_EXHAUSTED' in msg) or ('429' in msg) or ('quota' in msg.lower())

            if is_quota and attempt <= max_retries:
                # Extract retry delay from error message if present
                retry_delay = None
                try:
                    m = re.search(r"retryDelay':\s*'(\d+)s", msg)
                    if m:
                        retry_delay = int(m.group(1))
                except Exception:
                    retry_delay = None

                if retry_delay is None:
                    # For quota errors, wait longer (to respect ~9 req/min)
                    sleep_time = 7.0 + random.random() * 2.0  # 7-9 seconds
                else:
                    sleep_time = max(retry_delay, 7.0)  # At least 7 seconds

                sys.stdout.write(f"[call_gemini] quota hit (attempt {attempt}/{max_retries}). "
                                 f"Sleeping {sleep_time:.1f}s before retrying...\n")
                time.sleep(sleep_time)
                backoff = min(backoff * 1.5, 10.0)  # Gentler backoff
                continue

            # Non-retryable or retries exhausted
            sys.stdout.write(f"[call_gemini] error (attempt {attempt}) - giving up: {msg}\n")
            raise

def safe_call_gemini(client, conversation, fallback_response="", max_attempts=3):
    for attempt in range(max_attempts):
        try:
            # CHANGED: removed rate_limited=True parameter
            response = call_gemini(client, conversation)
            
            if response is None:
                sys.stdout.write(f"[safe_call] attempt {attempt+1}: got None response\n")
                continue
                
            response_text = str(response).strip()
            if not response_text:
                sys.stdout.write(f"[safe_call] attempt {attempt+1}: got empty response\n")
                continue
                
            return response_text
            
        except Exception as e:
            sys.stdout.write(f"[safe_call] attempt {attempt+1} failed: {e}\n")
            if attempt == max_attempts - 1:
                break
            continue
    
    sys.stdout.write(f"[safe_call] all {max_attempts} attempts failed, using fallback\n")
    return fallback_response
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
    CONTRADICT_MULTIPLIER = 15.0  # Make each contradiction worth 15x a support
    SUPPORT_MULTIPLIER = 2.0  # Each support is worth 2x

    s_ids = hyp.get("support_ids", []) or []
    c_ids = hyp.get("contradict_ids", []) or []
    
    for uid in s_ids:
        info = move_history.get(uid)
        if info:
            obs = map_result_label(info.get("result",""))
            if obs == "accepted":
                support += SUPPORT_MULTIPLIER
            else:
                contradict += CONTRADICT_MULTIPLIER  # Heavy penalty
        else:
            support += WEIGHT_UNOBSERVED
            
    for uid in c_ids:
        info = move_history.get(uid)
        if info:
            obs = map_result_label(info.get("result",""))
            if obs == "accepted":
                contradict += CONTRADICT_MULTIPLIER  # Heavy penalty
        else:
            contradict += WEIGHT_UNOBSERVED
            
    return support, contradict
def bayesian_confidence(alpha0, beta0, support, contradict):
    denom = alpha0 + beta0 + support + contradict
    if denom <= 0:
        return 0.0
    raw = (alpha0 + support) / denom
    return max(0.0, min(0.999, raw))

# ---------------- Prompt builders ----------------
def call_duplicate_checker(client, hypotheses_list):
    """
    Ask LLM to identify duplicate hypotheses and return IDs of less generic ones to remove.
    Returns list of IDs to remove.
    """
    if len(hypotheses_list) < 2:
        return []
    
    # Build hypothesis summary for LLM
    hyp_summary = []
    for h in hypotheses_list:
        hyp_summary.append({
            "id": h.get("id"),
            "description": h.get("description", "")
        })
    
    prompt = f"""You are given a list of hypotheses about a hidden rule. Identify which hypotheses are duplicates or near-duplicates of each other (same core idea but different wording).

For each group of duplicates, return the ID of the MORE SPECIFIC hypothesis to remove (keep the more GENERAL one). In general also remove the ids of the very specific hypotheses that cannot be used to clear every piece.

Hypotheses:
{json.dumps(hyp_summary, indent=2)}

Return EXACTLY this JSON format:
{{"remove_ids": ["H1", "H2"], "reasoning": "brief explanation of duplicates found"}}

If no duplicates found, return: {{"remove_ids": [], "reasoning": "no duplicates"}}"""

    msgs = [{"role": "user", "content": prompt}]
    
    try:
        raw = safe_call_gemini(client, msgs)
        parsed, err = _parse_and_validate_json(raw, expected_key="remove_ids")
        if err:
            sys.stdout.write(f"[dedup] parse error: {err}. Skipping dedup.\n")
            return []
        
        remove_ids = parsed.get("remove_ids", [])
        reasoning = parsed.get("reasoning", "")
        sys.stdout.write(f"[dedup] {reasoning}. Removing: {remove_ids}\n")
        return [str(x) for x in remove_ids]
        
    except Exception as e:
        sys.stdout.write(f"[dedup] error: {e}. Skipping dedup.\n")
        return []
    
def build_prompt_for_hyp_gen(current_hypotheses, val, bucket_state, immovable_pieces, move_history):
    """
    Build messages to call the hypothesis-generator LLM (SYSTEM_PROMPT_HYP_GEN).
    We provide current top hypotheses (id+description), board, bucket state, immovable pieces, and move history text.
    """
    hyps_simple = [{"id": h.get("id"), "description": h.get("description","")} for h in (current_hypotheses or [])]
    hyp_text = json.dumps(hyps_simple, ensure_ascii=False)

    pieces_info = [f"id:{p['id']} shape:{p.get('shape','?')} color:{p.get('color','?')} x:{p.get('x','?')} y:{p.get('y','?')}"
                   for p in (val or [])]
    board_state = " | ".join(pieces_info) if pieces_info else "None"

    bucket_summary = "; ".join(f"Bucket {b}: [{', '.join(bucket_state.get(b) or [])}]" for b in range(4)) if bucket_state is not None else "None"

    immovable_text = ", ".join(str(x) for x in sorted(list(immovable_pieces))) if immovable_pieces else "None"

    mh_lines = []
    for uid, info in (move_history or {}).items():
        if isinstance(info, dict):
            mv = info.get("move","")
            res = info.get("result","")
            mh_lines.append(f"{uid}: {mv} -> {res}")
        else:
            mh_lines.append(f"{uid}: {str(info)}")

    msgs = [
        {"role":"system", "content": SYSTEM_PROMPT_HYP_GEN},
        {"role":"user", "content": "Top hypotheses (id+description): " + hyp_text},
        {"role":"user", "content": "Board: " + board_state},
        {"role":"user", "content": "Bucket state: " + bucket_summary},
        {"role":"user", "content": "Discovered Immovable pieces: " + immovable_text},
        {"role":"user", "content": "Recent move history:\n" + ("\n".join(mh_lines) if mh_lines else "None")},
        {"role":"user", "content": f"Return only NEW hypotheses that are not duplicates of the Top hypotheses provided. Max {MAX_HYPOTHESES_TO_RETURN} new hypotheses allowed."}
    ]
    return msgs

def build_prompt_for_evidence(hyp_desc, candidate_uids, move_history):
    """
    Build messages for SYSTEM_PROMPT_EVIDENCE to ask which UIDs support/contradict a single hypothesis.
    candidate_uids: list like ["M1","M2",...]
    move_history: dict mapping UIDs to lines
    """
    moves_text_lines = []
    for uid in candidate_uids:
        info = move_history.get(uid)
        if isinstance(info, dict):
            mv = info.get("move","")
            res = info.get("result","pending")
            moves_text_lines.append(f"{uid}: {mv} -> {res}")
        else:
            moves_text_lines.append(f"{uid}: {str(info)}")
    msgs = [
        {"role":"system", "content": SYSTEM_PROMPT_EVIDENCE},
        {"role":"user", "content": f"Hypothesis: {hyp_desc}"},
        {"role":"user", "content": "Moves (UID: move -> result):\n" + ("\n".join(moves_text_lines) if moves_text_lines else "None")},
        {"role":"user", "content": "From the given move UIDs, return exactly the JSON object with support_ids and contradict_ids (subsets of provided UIDs)."}
    ]
    return msgs

def build_prompt_for_move_gen(top_hypotheses, val, bucket_state, immovable_pieces, move_history):
    """
    Build messages to ask the move-generation LLM. top_hypotheses is list of dicts with id & description.
    We'll include the top hypothesis descriptions (no confidences).
    """
    hyps_simple = [{"id": h.get("id"), "description": h.get("description","")} for h in (top_hypotheses or [])]
    hyp_text = json.dumps(hyps_simple, ensure_ascii=False)

    pieces_info = [f"id:{p['id']} shape:{p.get('shape','?')} color:{p.get('color','?')} x:{p.get('x','?')} y:{p.get('y','?')}"
                   for p in (val or [])]
    board_text = " | ".join(pieces_info) if pieces_info else "None"
    bucket_summary = "; ".join(f"Bucket {b}: [{', '.join(bucket_state.get(b) or [])}]" for b in range(4)) if bucket_state is not None else "None"
    immovable_text = ", ".join(str(x) for x in sorted(list(immovable_pieces))) if immovable_pieces else "None"

    msgs = [
        {"role":"system", "content": SYSTEM_PROMPT_MOVE.replace("{k}", str(TOP_K_FOR_MOVE_MODEL))},
        {"role":"user", "content": "Candidate hypotheses (id+description): " + hyp_text},
        {"role":"user", "content": "Board: " + board_text},
        {"role":"user", "content": "Bucket state: " + bucket_summary},
        {"role":"user", "content": "Immovable pieces: " + immovable_text},
        {"role":"user", "content": "Recent move history (brief): " + ("; ".join(list(move_history.keys())[-10:]) if move_history else "None")}
    ]
    return msgs

# ---------------- LLM call wrappers (with validation) ----------------
def _parse_and_validate_json(raw_text, expected_key, reprompt_instruction=None):
    """
    Extract JSON blob and parse. If fails, return (None, error_message).
    expected_key may be None or a key we expect in the top-level object (for quick validation).
    """
    blob = extract_json_blob(raw_text)
    try:
        parsed = json.loads(blob)
    except Exception as e:
        return None, f"JSON parse error: {e}; raw blob: {blob[:200]}"
    if expected_key and not (isinstance(parsed, dict) and expected_key in parsed):
        return None, f"Parsed JSON missing expected key '{expected_key}'. Parsed keys: {list(parsed.keys()) if isinstance(parsed, dict) else type(parsed)}"
    return parsed, None

# ---------------- Hypothesis-gen & evidence extraction ----------------
def call_hypothesis_generator(client, current_hypotheses, val, bucket_state, immovable_pieces, move_history):
    """
    Calls hypothesis-generator LLM to request NEW hypotheses.
    Returns list of normalized new hypothesis dicts:
      {id, description, support_ids (list), contradict_ids (list), thinking}
    """
    msgs = build_prompt_for_hyp_gen(current_hypotheses, val, bucket_state, immovable_pieces, move_history)
    raw = safe_call_gemini(client, msgs, fallback_response='{"new_hypotheses": []}')
    sys.stdout.write("[hyp-gen] raw reply:\n" + raw + "\n")
    parsed, err = _parse_and_validate_json(raw, expected_key="new_hypotheses")
    if err:
        # reprompt once for correct JSON
        sys.stdout.write("[hyp-gen] first parse failed: " + err + ". Reprompting for exact JSON.\n")
        msgs.append({"role":"user", "content": "Your previous reply could not be parsed. Return EXACTLY the JSON object specified and nothing else."})
        raw = safe_call_gemini(client, msgs, fallback_response='{"new_hypotheses": []}')
        sys.stdout.write("[hyp-gen] reprompt raw:\n" + raw + "\n")
        parsed, err = _parse_and_validate_json(raw, expected_key="new_hypotheses")
        if err:
            sys.stdout.write("[hyp-gen] reprompt parse also failed: " + err + ". Giving up on new hypotheses this turn.\n")
            return []

    new_hyps_raw = parsed.get("new_hypotheses") or []
    normalized = []
    # build set of existing descriptions normalized to deduplicate
    existing_desc_norms = set()
    for h in (current_hypotheses or []):
        desc = h.get("description","")
        desc_norm = " ".join(desc.lower().strip().split())
        existing_desc_norms.add(desc_norm)

    for i, h in enumerate(new_hyps_raw[:MAX_HYPOTHESES_TO_RETURN]):
        try:
            hid = h.get("id") or f"NH{random.randint(1000,9999)}"
            desc = str(h.get("description","")).strip()
            if not desc:
                continue
            desc_norm = " ".join(desc.lower().strip().split())
            if desc_norm in existing_desc_norms:
                # skip duplicate
                sys.stdout.write(f"[hyp-gen] skipping duplicate hypothesis description: {desc}\n")
                continue
            supports = [str(x) for x in (h.get("support_ids") or [])]
            contradicts = [str(x) for x in (h.get("contradict_ids") or [])]
            thinking = str(h.get("thinking","") or h.get("note","")).strip()
            normalized.append({
                "id": hid,
                "description": desc,
                "support_ids": supports,
                "contradict_ids": contradicts,
                "thinking": thinking
            })
            existing_desc_norms.add(desc_norm)
        except Exception as e:
            sys.stdout.write(f"[hyp-gen] skipping malformed item: {e}\n")
            continue

    return normalized

def call_evidence_extractor(client, hyp_desc, candidate_uids, move_history, max_attempts=2):
    """
    Ask the LLM, for a single hypothesis description, which of candidate_uids support/contradict it.
    Validates that returned UIDs are subsets of candidate_uids.
    Returns (support_ids, contradict_ids) lists (possibly empty).
    """
    if not candidate_uids:
        return [], []
    msgs = build_prompt_for_evidence(hyp_desc, candidate_uids, move_history)
    attempts = 0
    available = set(candidate_uids)
    while attempts < max_attempts:
        attempts += 1
        raw = safe_call_gemini(client, msgs, fallback_response='{"support_ids": [], "contradict_ids": []}')
        sys.stdout.write(f"[evidence] raw (attempt {attempts}):\n{raw}\n")
        parsed, err = _parse_and_validate_json(raw, expected_key=None)
        if err:
            sys.stdout.write(f"[evidence] parse error: {err}. Reprompting for exact JSON.\n")
            msgs.append({"role":"user","content":"Your previous reply could not be parsed. Return exactly the JSON object with 'support_ids' and 'contradict_ids' only."})
            continue
        if not isinstance(parsed, dict):
            msgs.append({"role":"user","content":"Return a JSON object with keys 'support_ids' and 'contradict_ids' (both lists)."})
            continue
        s_ids = parsed.get("support_ids", []) or []
        c_ids = parsed.get("contradict_ids", []) or []
        # validate subsets
        bad = [uid for uid in (s_ids + c_ids) if uid not in available]
        if bad:
            sys.stdout.write(f"[evidence] model returned unknown UIDs: {bad}. Reprompting.\n")
            msgs.append({"role":"user", "content": f"You cited unknown UIDs: {sorted(bad)}. Only these UIDs are available: {sorted(list(available))}. Return the JSON object again."})
            continue
        # ok
        return [str(x) for x in s_ids], [str(x) for x in c_ids]

    # failed to parse/validate after retries
    sys.stdout.write("[evidence] failed to get valid evidence from model. Returning empty lists.\n")
    return [], []

# ---------------- Main game loop functions ----------------
def severalEpisodes(inx, outx, N):
    """
    Run N episodes; collect hypotheses across episodes; after all episodes return final hypotheses list.
    We call mainLoopA with ask_final_rule=False so we don't request final rule mid-episode.
    """
    client = genai.Client(api_key=os.getenv("GEMINI_API_KEY_sg"))
    active_hypotheses = []   # each item: dict with id, description, confidence, support_ids, contradict_ids, evidence list
    for j in range(0, N):
        sys.stdout.write(f"=== Starting episode {j+1}/{N}\n")
        active_hypotheses = mainLoopA(inx, outx, ask_final_rule=False, initial_hypotheses=active_hypotheses)
        cmd = "EXIT\n" if (j == N-1) else "NEW\n"
        outx.write(cmd.encode()); outx.flush()
    return active_hypotheses

def mainLoop(inx, outx, test, rule, episodes=1):
    if test:
        stats = mainLoopB(inx, outx, rule)
        return stats
    else:
        if episodes <= 1:
            hyps = mainLoopA(inx, outx)
            outx.write("EXIT\n".encode()); outx.flush()
            return hyps
        else:
            return severalEpisodes(inx, outx, episodes)

def mainLoopA(inx, outx, ask_final_rule=True, initial_hypotheses=None):
    """
    Play a single episode.
    - ask_final_rule: if True, when board cleared, ask model once for final synthesis? (we skip that complexity here)
    - initial_hypotheses: list of previously active hyps to seed the pool
    Returns final hypotheses pool (list).
    """
    client = genai.Client(api_key=os.getenv("GEMINI_API_KEY_sg"))
    bucket_state = {0: [], 1: [], 2: [], 3: []}
    immovable_pieces = set()
    move_counter = 0
    last_move_uid = None
    last_move = None
    last_result = None
    move_history = {}    # mapping uid -> {"move": "pid bid", "result": "...", "piece_id":pid, "bucket_id":bid}
    hypotheses = []      # active pool: dicts {id, description, confidence, support_ids, contradict_ids, evidence}

    # initialize with prior hypotheses if provided (carry over confidence & evidence)
    if initial_hypotheses:
        for i, h in enumerate(initial_hypotheses):
            hypotheses.append({
                "id": h.get("id", f"H{i+1}"),
                "description": h.get("description",""),
                "confidence": float(h.get("confidence", 0.2)) if h.get("confidence") is not None else 0.2,
                "support_ids": list(h.get("support_ids") or []),
                "contradict_ids": list(h.get("contradict_ids") or []),
                "evidence": list(h.get("evidence") or [])
            })
        # ensure sorted
        hypotheses = sorted(hypotheses, key=lambda x: x.get("confidence", 0.0), reverse=True)[:MAX_ACTIVE_HYPS]
    prev_val = None

    # NEW: track how many moves we've made since the last hypothesis generation / confidence recalculation
    moves_since_last_hyp = 0
    recent_uids = []

    while True:
        statusLine = readLine(inx)
        if not statusLine:
            sys.stdout.write("No status line: exiting episode early.\n")
            return hypotheses
        statusLine = statusLine.strip()
        jsonLine = readLine(inx)
        if not jsonLine:
            sys.stdout.write("No JSON line: exiting.\n")
            return hypotheses
        jsonLine = jsonLine.strip()

        [code, status, t] = map(int, re.split(r"\s+", statusLine))
        val = json.loads(jsonLine)["value"]  # current board snapshot

        # If there was a pending last_move_uid, update its result from this status code
        if last_move_uid is not None:
            if code == 0:
                result_text = "Move accepted."
            elif code == 4:
                result_text = "Move denied."
            elif code == 7:
                result_text = "Piece immovable."
            else:
                result_text = f"Code {code}."
            # update history
            if last_move_uid in move_history:
                move_history[last_move_uid]["result"] = result_text
                move_history[last_move_uid]["step"] = t
            else:
                move_history[last_move_uid] = {"move": last_move or "", "result": result_text, "piece_id": None, "bucket_id": None, "step": t}
            last_result = result_text

            # if accepted -> update bucket_state and clear immovable set
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
                immovable_pieces.clear()
            # if immovable, mark piece
            if code == 7:
                try:
                    pid = move_history[last_move_uid].get("piece_id")
                    if pid is not None:
                        immovable_pieces.add(pid)
                except:
                    pass
            last_move_uid = None

        # Episode termination
        if status == 1:
            sys.stdout.write(f"Cleared board in {t} steps.\n")
            return hypotheses
        if status == 2:
            sys.stdout.write(f"Stalemate after {t} steps.\n")
            return hypotheses

        # ---------------- Decide whether to run hypothesis-generation & recalculation ----------------
        # Run hyp-gen if:
        #  - there are currently NO hypotheses (force generation), OR
        #  - we've done at least PRE_HYP_MOVES moves since last hypothesis generation
        need_hyp_gen = (not hypotheses) or (moves_since_last_hyp >= PRE_HYP_MOVES)

        if need_hyp_gen:
            # ---------------- Hypothesis generation step ----------------
            # Ask model for NEW hypotheses (it may cite supporting UIDs). We'll re-check evidence below.
            new_hyps = call_hypothesis_generator(client, hypotheses, val, bucket_state, immovable_pieces, move_history)
            sys.stdout.write(f"[main] got {len(new_hyps)} new hypotheses from model\n")

            # Merge new hypotheses into active candidate pool (deduplicate by normalized description)
            def normalize_desc(s): return " ".join(str(s).lower().strip().split())
            existing_descs = { normalize_desc(h["description"]): h for h in hypotheses }
            for nh in new_hyps:
                dnorm = normalize_desc(nh["description"])
                if dnorm in existing_descs:
                    sys.stdout.write("[main] skipping new hyp duplicate vs existing: " + nh["description"] + "\n")
                    continue
                # create active hyp record with placeholder confidence (prior)
                hypotheses.append({
                    "id": nh.get("id"),
                    "description": nh.get("description"),
                    "confidence": ALPHA0/(ALPHA0+BETA0),
                    "support_ids": [],      # we will fill from evidence extraction
                    "contradict_ids": [],
                    "evidence": []
                })

            if len(hypotheses) > 1:
                remove_ids = call_duplicate_checker(client, hypotheses)
                if remove_ids:
                    hypotheses = [h for h in hypotheses if h.get("id") not in remove_ids]
                    sys.stdout.write(f"[main] removed {len(remove_ids)} duplicate hypotheses\n")
            

            # ---------------- Evidence extraction (N^2 optimized) ----------------
            # For new hypotheses: we check ALL past moves (costly but only for new ones)
            # For existing hypotheses: only check the latest move (if any), to update incrementally.
            # Implementation detail: identify which are new this turn by whether support_ids empty.
            all_uids = list(move_history.keys())
            latest_uid = None
            if move_history:
                latest_uid = list(move_history.keys())[-1]

            # We will call evidence-extractor per hypothesis (but with smaller UID lists for old hyps)
            for hyp in list(hypotheses):  # iterate over a copy
                # if hypothesis already has evidence populated, treat it as 'old' and only check latest move
                need_all = (not hyp.get("support_ids") and not hyp.get("contradict_ids"))  # fresh hypothesis
                candidate_uids = all_uids if need_all else recent_uids 
                if not candidate_uids:
                    # nothing new to test
                    continue
                # ask evidence extractor (rate-limited calls recommended if you hit rate limits)
                s_ids, c_ids = call_evidence_extractor(client, hyp["description"], candidate_uids, move_history)

                # Fix: Handle incremental vs full evidence updates properly
                if need_all:  # Fresh hypothesis - replace all evidence
                    hyp["support_ids"] = s_ids
                    hyp["contradict_ids"] = c_ids
                else:  # Existing hypothesis - append new evidence
                    hyp["support_ids"] = list(set(hyp["support_ids"] + s_ids))
                    hyp["contradict_ids"] = list(set(hyp["contradict_ids"] + c_ids))

            # ---------------- Bayesian update of confidences ----------------
            updated_hyps = []
            prior_conf_default = ALPHA0 / (ALPHA0 + BETA0)
            for h in hypotheses:
                support, contradict = compute_support_contradiction_from_ids(h, move_history)
                old_conf = h.get("confidence", prior_conf_default)
                conf_raw = bayesian_confidence(ALPHA0, BETA0, support, contradict)
                delta_raw = conf_raw - old_conf
                if delta_raw > MAX_DELTA:
                    delta = MAX_DELTA
                elif delta_raw < -MAX_DELTA:
                    delta = -MAX_DELTA
                else:
                    delta = delta_raw
                conf_new = max(0.0, min(0.999, old_conf + delta))
                h["confidence"] = conf_new
                h.setdefault("evidence", []).append(f"auto:+{support:.2f}-:{contradict:.2f} => {conf_new:.3f}")
                updated_hyps.append(h)

            # carry forward & prune duplicates by normalized description
            # deduplicate (keep highest confidence among duplicates)
            # This final sequence is GOOD - keep it as is:
            dedup = {}
            for h in sorted(updated_hyps, key=lambda x: x.get("confidence",0.0), reverse=True):
                dnorm = normalize_desc(h["description"])
                if dnorm in dedup:
                    continue
                dedup[dnorm] = h
            hypotheses = list(dedup.values())
            hypotheses = sorted(hypotheses, key=lambda x: x.get("confidence",0.0), reverse=True)[:MAX_ACTIVE_HYPS]
            hypotheses = [h for h in hypotheses if h.get("confidence",0.0) >= MIN_CONF]

            sys.stdout.write(f"[main] active hypotheses count: {len(hypotheses)}\n")

            # reset moves_since_last_hyp because we just generated/updated hypotheses
            moves_since_last_hyp = 0
            recent_uids = [] 

        else:
            # We are skipping hypothesis-generation / evidence extraction this loop.
            # Just report current active hypotheses count for debugging.
            sys.stdout.write(f"[main] skipping hyp-gen; moves_since_last_hyp={moves_since_last_hyp}; active hypotheses={len(hypotheses)}\n")

        # ---------------- Move generation step ----------------
        # Send top-K hypotheses (descriptions only) to move model and request a move
        top_for_move = hypotheses[:TOP_K_FOR_MOVE_MODEL]
        # print top_for_move being sent
        sys.stdout.write(f"[move-gen] sending top hypotheses:\n")
        for h in top_for_move:
            sys.stdout.write(f" - {h['description']} (conf: {h.get('confidence', 0.0):.3f})\n")
        msgs = build_prompt_for_move_gen(top_for_move, val, bucket_state, immovable_pieces, move_history)
        raw_move_resp = safe_call_gemini(client, msgs, fallback_response='{"move": {"piece_id": -1, "bucket_id": 0}}')
        sys.stdout.write("[move-gen] raw response:\n" + raw_move_resp + "\n")
        parsed_move, err = _parse_and_validate_json(raw_move_resp, expected_key="move")
        if err:
            sys.stdout.write("[move-gen] parse error: " + err + ". Reprompting for exact JSON.\n")
            msgs.append({"role":"user", "content":"Your reply could not be parsed. Return EXACTLY the JSON object with key 'move' and 'thinking'."})
            raw_move_resp = safe_call_gemini(client, msgs, fallback_response='{"move": {"piece_id": -1, "bucket_id": 0}}')
            parsed_move, err = _parse_and_validate_json(raw_move_resp, expected_key="move")
            if err:
                sys.stdout.write("[move-gen] reprompt parse failed: " + err + ". Doing fallback move.\n")
                # fallback: choose first movable piece (if any)
                try:
                    fallback = next(p for p in val if p.get("buckets") and len(p.get("buckets"))>0)
                except StopIteration:
                    fallback = val[0] if val else None
                if not fallback:
                    sys.stdout.write("No pieces on board; exiting.\n")
                    return hypotheses
                piece_id = fallback["id"]
                bucket_id = 0
                move_obj = {"move": {"piece_id": piece_id, "bucket_id": bucket_id}, "thinking": "fallback"}
            else:
                move_obj = parsed_move
        else:
            move_obj = parsed_move

        # Validate the suggested move
        move_payload = move_obj.get("move", {})
        piece_id = move_payload.get("piece_id")
        bucket_id = move_payload.get("bucket_id")
        valid_piece_ids = {p["id"] for p in val}
        if piece_id is None or bucket_id is None or piece_id not in valid_piece_ids or bucket_id not in {0,1,2,3}:
            sys.stdout.write(f"[move-gen] invalid suggested move {move_payload}. Falling back to safe move.\n")
            try:
                fallback = next(p for p in val if p.get("buckets") and len(p.get("buckets"))>0)
            except StopIteration:
                fallback = None
            if fallback:
                piece_id = fallback["id"]
            else:
                piece_id = val[0]["id"]
            bucket_id = 0

        # Record move UID and send move to server
        move_counter += 1
        uid = f"M{move_counter}"
        move_history[uid] = {"move": f"{piece_id} {bucket_id}", "result": "pending", "piece_id": piece_id, "bucket_id": bucket_id}
        recent_uids.append(uid)
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

        # update moves_since_last_hyp:
        # if we performed hypothesis generation & recalculation this loop, we reset earlier to 0,
        # but since we've just made a move we should set it to 1 (we have one move after that hyp-gen).
        if need_hyp_gen:
            moves_since_last_hyp = 1
        else:
            moves_since_last_hyp += 1

        # loop continues to read response for this move at top of loop

# ---------------- mainLoopB: clear-board using rule text (keeps thinking area separate) ----------------
def mainLoopB(inx,outx, hyps):
    """
    Ask the model to produce a JSON with 'thinking' and 'moves' to clear the board.
    Minimal parsing and execution (we assume well-formed JSON returned).
    """
    # convert hyps returned by mainloop a into text
    rule_text = "\n".join(f" - {hyp}" for hyp in hyps)
    client = genai.Client(api_key=os.getenv("GEMINI_API_KEY_sg"))
    statusLine = readLine(inx)
    if not statusLine:
        sys.stdout.write("No status line at start of mainLoopB.\n"); return 0.0
    statusLine = statusLine.strip()
    jsonLine = readLine(inx)
    if not jsonLine:
        sys.stdout.write("No json at start of mainLoopB.\n"); return 0.0
    jsonLine = jsonLine.strip()

    sys.stdout.write("Unpack: "+ statusLine + "\n")
    [code, status, t] = map( int, re.split(r"\s+", statusLine))
    sys.stdout.write("Code=" +repr(code)+ ", status=" +repr(status)+ ", stepNo="+repr(t)+ "\n")
    if code<0:
        sys.stdout.write("Error code"+ repr(code) + "\n")
        sys.stdout.write("Error message: "+ jsonLine + "\n"); return 0.0

    w = json.loads(jsonLine)
    val = w["value"]
    total_pieces = len(val)
    conversation = [{"role": "system", "content": SYSTEM_PROMPT_B.replace("{total_pieces}", str(total_pieces))}]
    conversation.append({"role":"user", "content": rule_text})
    # minimal board summary
    conversation.append({"role":"user", "content": "Board JSON: " + json.dumps(val)})
    raw = call_gemini(client, conversation)
    sys.stdout.write("Gemini raw response:\n" + raw + "\n")
    blob = extract_json_blob(raw)
    try:
        parsed = json.loads(blob)
    except Exception as e:
        sys.stdout.write("Failed to parse JSON from Gemini response: " + str(e) + "\n"); return 0.0

    thinking_text = parsed.get("thinking")
    if thinking_text:
        sys.stdout.write("Gemini thinking:\n" + str(thinking_text) + "\n")
    if "moves" not in parsed:
        sys.stdout.write("Parsed JSON does not contain 'moves'. Aborting.\n"); return 0.0

    moves = parsed["moves"]
    successful_moves = 0
    for mv in moves:
        piece_id = mv.get("piece_id"); bucket_id = mv.get("bucket_id")
        try:
            moveData = mapMove(val, piece_id, bucket_id)
        except Exception as e:
            sys.stdout.write(f"Error mapping move (piece {piece_id}, bucket {bucket_id}): {e}\n")
            continue
        send = "MOVE " + " ".join(map(repr, moveData))
        sys.stdout.write("Sending: "+ send + "\n")
        outx.write((send + "\n").encode()); outx.flush()

        statusLine = readLine(inx); jsonLine = readLine(inx)
        if not statusLine or not jsonLine:
            sys.stdout.write("No response after move.\n"); return float(successful_moves/total_pieces) if total_pieces>0 else 0.0
        statusLine = statusLine.strip(); jsonLine = jsonLine.strip()
        sys.stdout.write("Unpack: "+ statusLine + "\n")
        [code, status, t] = map(int, re.split(r"\s+", statusLine))
        sys.stdout.write("Code=" +repr(code)+ ", status=" +repr(status)+ ", stepNo="+repr(t)+ "\n")
        if code<0:
            sys.stdout.write("Error code"+ repr(code) + "\n"); sys.stdout.write("Error message: "+ jsonLine + "\n"); break
        if code == 0: successful_moves += 1
        if status == 1:
            sys.stdout.write("Cleared board in "+ repr(t) + " steps\n")
            return float(successful_moves/total_pieces) if total_pieces>0 else 0.0
        elif status==2:
            sys.stdout.write("Stalemate after  "+ repr(t) + " steps\n"); return 0.0
        try:
            w = json.loads(jsonLine); val = w.get("value", val)
        except:
            sys.stdout.write("Warning: failed to parse board JSON after move; keeping previous board snapshot.\n")
    return float(successful_moves/total_pieces) if total_pieces>0 else 0.0

# ---------------- End of file ----------------
