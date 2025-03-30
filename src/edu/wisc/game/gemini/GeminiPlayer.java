package edu.wisc.game.gemini;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.net.*;
import java.text.*;
import jakarta.json.*;


import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
//import edu.wisc.game.parser.*;
import edu.wisc.game.sql.Episode.OutputMode;
import edu.wisc.game.sql.Episode.CODE;
import edu.wisc.game.sql.Episode.Pick;
import edu.wisc.game.sql.Episode.Move;
import edu.wisc.game.rest.*;
import edu.wisc.game.engine.*;



public class GeminiPlayer  extends Vector<GeminiPlayer.EpisodeHistory> {

   static private void usage() {
	usage(null);
    }
    static private void usage(String msg) {
	System.err.println("Gemini Player (https://rulegame.wisc.edu/w2020/captive.html)\n");
	System.err.println("Usage:\n");
	System.err.println("  java [options]  edu.wisc.game.gemini.GeminiPlayer game-rule-file.txt board-file.json");
	System.err.println("  java [options]  edu.wisc.game.gemini.GeminiPlayer game-rule-file.txt npieces [nshapes ncolors]");
	System.err.println("  java [options]  edu.wisc.game.gemini.GeminiPlayer trial-list-file.csv rowNumber");
	System.err.println("  java [options]  edu.wisc.game.gemini.GeminiPlayer R:rule-file.txt:modifier-file.csv");
	System.err.println("Each of 'npieces', 'nshapes', and 'ncolors' is either 'n' (for a single value) or 'n1:n2' (for a range). '0' means 'any'");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }

    public static final DateFormat sqlDf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    static Date lastRequestTime = null;
    
    //    int sentCnt = 0;
    
    /** Sends a request to the Gemini server, and processes the results.

	@param gr The request to send
	@return the "text" part of the response

	<pre>	
       curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=XXXX" \
-H 'Content-Type: application/json' \
-X POST \
-d '{
  "contents": [{
    "parts":[{"text": "How much does it cost to transfer between terminals in Manila Airport?"}]
    }]
   }'
   </pre>
    */  

  
    private String doOneRequest(GeminiRequest gr) throws MalformedURLException, IOException, ProtocolException, ClassCastException
    {
	readApiKey();
	String u = "https://generativelanguage.googleapis.com/v1beta/models/";
	u += model + ":generateContent";
	u += "?key=" + gemini_api_key;
	
	URL url = new URL(u);

	JsonObject jo = JsonReflect.reflectToJSONObject(gr, false, null, 10);
	//System.out.println("SENDING: " + jo.toString());
	String jsonInputString =  jo.toString();

	int retryCnt=0;
	JsonObject responseJo = null;
	int code=0;
	for(; retryCnt < 3; retryCnt++) {
	    lastRequestTime  = new Date();
	    HttpURLConnection con = (HttpURLConnection)url.openConnection();
	    con.setRequestMethod("POST");
	    con.setRequestProperty("Content-Type", "application/json");
	    con.setDoOutput(true);
	    
	    
	    try(OutputStream os = con.getOutputStream()) {
		byte[] input = jsonInputString.getBytes("utf-8");
		os.write(input, 0, input.length);			
	    }
	    
	    
	    code = con.getResponseCode();
	    InputStream is;
	    
	    if (code != 200) {
		System.out.println("Error: HTTP response code = " + code);
		is = con.getErrorStream();
	    } else {
		is = con.getInputStream();
	    }
	    InputStreamReader isr = new InputStreamReader(is, "utf-8");
	    
	    try(BufferedReader br = new BufferedReader(isr)) {
		/*
		  StringBuilder response = new StringBuilder();
		  String responseLine = null;
		  while ((responseLine = br.readLine()) != null) {
		  response.append(responseLine.trim());
		  }
		*/
		
		JsonReader jsonReader = Json.createReader(br);
		responseJo = jsonReader.readObject();
		jsonReader.close();
	    }		
	    if (code==200) break;

	    System.out.println("At "+	sqlDf.format(lastRequestTime)+", SERVER RESPONSE: " + responseJo.toString());

	    if (code==429) {
		int waitSec = error429(responseJo);
		System.out.println("Waiting for " + waitSec + " seconds to retry, as told by the server");
		waitABit(waitSec * 1000);
	    } else {
		throw new IllegalArgumentException("Don't know what to do with server error code " + code +"; terminating");
	    }
	    
	}


	if (code != 200) {
	    throw new IllegalArgumentException("Retry count exceeded? Terminating");
	}
	
	if (responseJo==null) throw new IllegalArgumentException("Has not read anything");

	

	
	JsonArray candidatesJa = responseJo.getJsonArray("candidates");
	if (candidatesJa.size()!=1)  throw new IllegalArgumentException("Expected to find 1 candidate, found " + candidatesJa.size() + ". RESPONSE=\n" + responseJo);
	JsonObject contentJo = candidatesJa.getJsonObject(0).getJsonObject("content");
	JsonArray partsJa = contentJo.getJsonArray("parts");
	if (partsJa.size()!=1)  throw new IllegalArgumentException("Expected to find 1 part, found " + partsJa.size() + ". RESPONSE=\n" + responseJo);	
	String text = partsJa.getJsonObject(0).getString("text");
	return text;
	/*
	"candidates":[
  {"content": {"parts": [{"text": "MOVE 0 0\n"}],"role": "model"},
   "finishReason": "STOP",
   "avgLogprobs": -1.9414728740230203e-05}
  ],
"usageMetadata": ...
	*/	
	
	}

	

    /* Extracts the recommended retry time from the error response of the Gemini
       server.
       <pre>
        {"error":{"code":429,
		 "message":"You exceeded your current quota, please check your plan and billing details. For more information on this error, head to: https://ai.google.dev/gemini-api/docs/rate-limits.",
		 "status":"RESOURCE_EXHAUSTED",
		 "details":[
		 {"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{"quotaMetric":"generativelanguage.googleapis.com/generate_content_free_tier_requests","quotaId":"GenerateRequestsPerMinutePerProjectPerModel-FreeTier","quotaDimensions":{"model":"gemini-2.0-flash","location":"global"},"quotaValue":"15"}]},
		 {"@type":"type.googleapis.com/google.rpc.Help","links":[{"description":"Learn more about Gemini API quotas","url":"https://ai.google.dev/gemini-api/docs/rate-limits"}]		 },
		 {"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"38s"}]
		 }}
		 </pre>
    */

    static int error429(JsonObject responseJo) {
	JsonObject errorJo = responseJo.getJsonObject("error");
	JsonArray detailsJa = errorJo.getJsonArray("details");
	if (detailsJa==null)  throw new IllegalArgumentException("The Error 429 response came without the 'error/details' field: " + responseJo);
	for(int j=0; j<detailsJa.size(); j++) {
	    JsonObject detailJo = detailsJa.getJsonObject(j);
	    String type = detailJo.getString("@type");
	    if (type.equals("type.googleapis.com/google.rpc.RetryInfo")) {
		String retryDelay = detailJo.getString("retryDelay");
		Matcher m = secPat.matcher(retryDelay);
		if (m.matches()) {
		    int sec = Integer.parseInt( m.group(1));
		    return sec;
		} else throw new IllegalArgumentException("Could not parse retryDelay=" + retryDelay);
	    }
	}
	throw new IllegalArgumentException("Could not find type.googleapis.com/google.rpc.RetryInfo in this response: " + responseJo);
    }

    static final Pattern secPat = Pattern.compile("([0-9]+)s");
    
    static GeminiRequest makeRequest1() {
	GeminiRequest gr = new GeminiRequest();
	gr.addInstruction("Please answer in German, if you can");	
	gr.addUserText("How do you use borax?");
	return gr;
    }


    static String instructions=null;
    
    
    static String gemini_api_key = null;
    static String model = "gemini-2.0-flash";
    static long wait = 4000;
    static int max_boards=10;
    
    static void readApiKey() throws IOException {
	if ( gemini_api_key != null) return;
	String s = Util.readTextFile(new File("/opt/w2020/gemini-api-key.txt"));
	s = s.replaceAll("\\s", "");
	gemini_api_key = s;
    }

    /** Modeled on Captive.java
	model=gemini-2.0-flash
	wait=4000  (wait time between requests in msec)
     */
    public static void main(String[] argv) throws Exception {

	File f = new File( Files.geminiDir(), "system.txt");	
	instructions = Util.readTextFile( f);

	Files.allowCachingAllRules(true); // for greater efficiency
	
	// The captive server does not need the master conf file in /opt/w2020
	MainConfig.setPath(null);
	// Enable the computing of feature-lists for Composite objects
	// (which is normally turned off)
	edu.wisc.game.svg.Composite.setNeedFeatures(true);
	
	ParseConfig ht = new ParseConfig();

	// allows seed=... , colors=..., condTrain=..., crowded=... etc among argv
	argv = ht.enrichFromArgv(argv);

	model = ht.getOption("model", model);
	wait = ht.getOptionLong("wait", wait);
	max_boards  = ht.getOption("max_boards", max_boards);
	
	System.out.println("Gemini model=" + model);
	//System.out.println("output=" +  ht.getOption("output", null));
	OutputMode outputMode = ht.getOptionEnum(OutputMode.class, "output", OutputMode.FULL);

	String inputDir=ht.getOption("inputDir", null);
	//System.out.println("#inputDir=" + inputDir);
	if (inputDir!=null) Files.setInputDir(inputDir);

	MlcLog log=Captive.mkLog(ht);		      

	GameGenerator gg=null;
	try {
	    gg = Captive.buildGameGenerator(ht, argv);
	} catch(Exception ex) {
	    usage("Cannot create game generator. Problem: " + ex.getMessage());
	}
	        	
	if (log!=null) log.rule_name = gg.getRules().getFile().getName().replaceAll("\\.txt$", "");


	System.out.println("Game generator=" + gg);
	System.out.println("Rule set=" + gg.getRules().getFile());

	
	int gameCnt=0;

	if (log!=null) log.open();

	GeminiPlayer history = new GeminiPlayer();

	System.out.println("Instructions are: " + instructions);

	try {
	
	for(; gameCnt < max_boards; gameCnt++) {
	    Game game = gg.nextGame();
	    //	    if (outputMode== OutputMode.FULL) System.out.println(Captive.asComment(game.rules.toString()));

	    System.out.println("Starting episode " + (gameCnt+1) + " of up to " + max_boards);

	    Episode epi = new Episode(game, outputMode,
				      new InputStreamReader(System.in),
				      new PrintWriter(System.out, true));


	    EpisodeHistory his = new EpisodeHistory(epi);
	    history.add(his);

	    //System.out.println("DEBUG: B=" + his.initialBoardAsString());

	    try {
		history.playingLoop();
	    } finally {
		if (log!=null) {
		    log.logEpisode(epi, gameCnt);
		    System.out.println("Logged episode " + (gameCnt+1) );
		}
	    }
	}

	} finally {
	    if (log!=null) log.close();
	}
  

	//---------

	System.exit(0);

	/*
	GeminiPlayer p = new GeminiPlayer();
	//	IOException,  RuleParseException, ReflectiveOperationException, IllegalInputException{
	GeminiRequest gr = makeRequest1();
	p.doOneRequest(gr);

	gr = new GeminiRequest();

	gr.addInstruction(instructions);
	*/
    }

    

    
    /** What you need to know about an episode when sending that info to
	Gemini */
    static class EpisodeHistory {
	final Episode epi;
	final Board initialBoard;
	EpisodeHistory(Episode _epi) {
	    epi = _epi;
	    initialBoard = epi.getCurrentBoard(false);
	}
	String initialBoardAsString() {
	    HashSet<String> excludableNames = new HashSet<>();
	    excludableNames.add("buckets");
	    excludableNames.add("0.id");
	    JsonObject jo = JsonReflect.reflectToJSONObject(initialBoard, true,  excludableNames);
	    return jo.toString();
	}
    }


    GeminiPlayer() { super(); }


    /** Creates a request object based on the current state of this
	GeminiPlayer, i.e. all episodes that have been completed, and
	the one still in progress */
    GeminiRequest makeRequest() throws IOException {
	GeminiRequest gr = new GeminiRequest();
	    
	gr.addInstruction(instructions);

	Vector<String> v = new Vector<>();
	
	if (size()==0) throw new IllegalArgumentException("No episode exists yet. What to ask?");
	//	EpisodeHistory ehi = lastElement();
	//Episode epi = ehi.epi;
	if (lastElement().epi.isCompleted())  throw new IllegalArgumentException("Last episode already completed. What to ask?");

	if (size()>1) {
	    // describe all previous episodes.
	    int n=size()-1; v.add("You have completed " + n + " episodes so far. "+(n>1?"Their":"Its") + " summary follows.");
	}
	for(int j=0; j<size(); j++) {
	    v.addAll( episodeText(j));
	}

	v.add("YOUR MOVE?");
	String text = Util.joinNonBlank("\n", v);
	System.out.println("===========================================\n"+
			   "The text part of the request:\n" + text);
	gr.addUserText(text);
	return gr;
    }


    static int lastLen = -1;
    
    /** Creates lines describing an episode, to go into a request. */
    private Vector<String> episodeText(int j) {
	Vector<String> v = new Vector<>();
	boolean isLast = (j==size()-1);
	EpisodeHistory ehi = get(j);

	Vector<Pick> moves = ehi.epi.getTranscript();

	
	if (j==size()-1) {
	    v.add("You are playing Episode "+(j+1)+" now.");
	}
	
	v.add("Episode " + (j+1)  + " had the following initial board: " +
	      ehi.initialBoardAsString());
	int n = moves.size();
	if (n==lastLen) {
	    throw new IllegalArgumentException("n="+ n + " still? how come?");
	} else {
	    lastLen = n;
	}
	v.add("During episode "+(j+1)+", you "+
	      (isLast ? "have made so far ": "made ") +
	      (n>0?       "the following ":"")+
	      n + 	      " move attempt" + (n>1? "s":"") +
	      (n>0?       ", with the following results:": "."));
	for(int k=0; k<n; k++) {
	    if (!(moves.get(k) instanceof Move))  throw new IllegalArgumentException("Unexpected entry in the transcript (j=" + j+", k=" + k+", The bot is only supposed to make moves, not picks!");
	    Move move = (Move)moves.get(k);
	    //	    v.add("Move " + (k+1) + " :  " + move);

	    /*
"MOVE id bucketId response",
where "id" is the ID of the object that you attempted to move, "bucketId" is the ID of the bucket into which you wanted to place it, and "response" is whatever response I have given to that move. The response is one word, which can be one of the following: ACCEPT, NOT_MOVABLE, DENY, INVALID.
	    */
	    int code = move.getCode();

	    
	    String s = "MOVE " + move.getPieceId() + " " + move.getBucketNo() + " "+
		CODE.toBasicName(code);
	    v.add(s);
	    
	}
	return v;
    }

    final Pattern movePat = Pattern.compile("\\bMOVE\\s+([0-9]+)\\s+([0-9]+)");

    int lastStretch;
    double lastR;
    
    /** Plays the last (latest) episode of this GeminiPlayer, until it ends.

	What comes back from Gemini is this:
<pre>	
{
"candidates":[
  {"content": {"parts": [{"text": "MOVE 0 0\n"}],"role": "model"},
   "finishReason": "STOP",
   "avgLogprobs": -1.9414728740230203e-05}
  ],
"usageMetadata":
   {"promptTokenCount": 1219,
    "candidatesTokenCount": 6,
    "totalTokenCount": 1225,
    "promptTokensDetails": [{"modality": "TEXT","tokenCount": 1219}],
    "candidatesTokensDetails": [{"modality": "TEXT","tokenCount": 6}]
    },
"modelVersion": "gemini-2.0-flash"}
</pre>

@return true on victory (master demonstrated), false otherwise
     */
    boolean playingLoop()  throws IOException {

	EpisodeHistory ehi = lastElement();
	Episode epi = ehi.epi;
	while( !epi.isCompleted()){
	    GeminiRequest gr = makeRequest();

	    int tryCnt = 0;
	    Matcher m = null;
	    while(true) {
		String line = doOneRequest(gr);
		tryCnt++;
		System.out.println("Response text={" + line + "}");
		m = movePat.matcher(line);
		if (m.find()) break;
		if (tryCnt>=2) {
		    throw new IllegalArgumentException("Could not find 'MOVE id bid' in this response text, even after "+tryCnt+" attempts: {" + line +"}");
		}
		// try to tell the bot to use the proper format
		gr.addModelText(line);
		gr.addUserText("I don't understand English very well. Please say again what YOUR MOVE is, remembering to describe your attempted move in the following format: 'MOVE objectId bucketId'!");
		System.out.println("At "+	sqlDf.format(lastRequestTime)+", received an incomprehensible response, and trying to ask again");
		waitABit(wait);
	    }
	    int id = Integer.parseInt( m.group(1));
	    int bid = Integer.parseInt( m.group(2));

	    Episode.Display q = epi.doMove2(id, bid,  epi.getTranscript().size());
	    //if (outputMode!=OutputMode.BRIEF) out.println(displayJson());
	    //if (outputMode==OutputMode.FULL) out.println(graphicDisplay());
	    int code = q.getCode();
	    System.out.println("At "+	sqlDf.format(lastRequestTime)+", Moving piece " + id + " to bucket " + bid + ". Code=" + code);
	    //System.out.println("DEBUG B: transcript=" + epi.getTranscript());


	    if (code==CODE.ATTEMPT_CNT_MISMATCH) {
		System.out.println("I have ended up with an attempt count mismatch somehow. My logic bug probably. Terminating");
		return false;
	    }
	    
	    if (code==CODE.ACCEPT) { // add to the "mastery stretch"
		lastStretch++;
		if (lastR==0) lastR=1;
		lastR *= epi.getLastMove().getRValue();		    
	    } else { // a failed move or pick breaks the "mastery stretch"
		lastStretch=0;
		lastR = 0;
	    }

	    String stats = "Transcript has "+epi.getTranscript().size()+" moves. Board pop="+epi.getValues().size()+". lastStretch=" + lastStretch + ", lastR=" + lastR;
	    
	    System.out.println(stats);

	    if (lastStretch>10 || lastR > 1e6) {
		System.out.println("Victory: mastery demonstrated! " + stats);
		return true;
	    }
	    waitABit(wait); // 5 sec wait before the next request, to keep Gemini happy
	    
	}

	System.out.println("Episode ended. transcript has "+epi.getTranscript().size()+" moves. Board pop="+epi.getValues().size()+". lastStretch=" + lastStretch + ", lastR=" + lastR);
	    
	return false;
    }

    /** Out model is gemini-2.0-flash, which allows 15 RPM in the free tier.
    https://ai.google.dev/gemini-api/docs/rate-limits
    */
    private void waitABit(long msec) {
    
	try {
            Thread.sleep(msec); 
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    // {"text": "MOVE 0 0\n"
  
   
}
