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
import edu.wisc.game.sql.Episode.OutputMode;
import edu.wisc.game.sql.Episode.CODE;
import edu.wisc.game.sql.Episode.Pick;
import edu.wisc.game.sql.Episode.Move;
import edu.wisc.game.rest.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.saved.TranscriptManager;


public class GeminiPlayer  extends Vector<EpisodeHistory> {

    /** Should we admonish the Gemini bot if it makes redundant moves? */
    static boolean remind = true;

    /** Various token count statistics, summed over all server responses
	in this session */
    static int sumPromptTokenCount,
	sumCandidatesTokenCount,
	sumTotalTokenCount,
	sumThoughtsTokenCount;

    
   static private void usage() {
	usage(null);
    }
    static private void usage(String msg) {
	System.err.println("Gemini Player (https://rulegame.wisc.edu/w2020/captive.html)\n");
	System.err.println("Usage:\n");
	System.err.println("  java [options]  edu.wisc.game.gemini.GeminiPlayer game-rule-file.txt board-file.json");
	System.err.println("  java [options]  edu.wisc.game.gemini.GeminiPlayer game-rule-file.txt npieces [nshapes ncolors]");
	System.err.println("  java [options]  edu.wisc.game.gemini.GeminiPlayer uildtrial-list-file.csv rowNumber");
	System.err.println("  java [options]  edu.wisc.game.gemini.GeminiPlayer R:rule-file.txt:modifier-file.csv");
	System.err.println("Each of 'npieces', 'nshapes', and 'ncolors' is eitheвr 'n' (for a single value) or 'n1:n2' (for a range). '0' means 'any'");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }

    public static final DateFormat sqlDf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    static Date lastRequestTime = null;
    
    static String now() {
	return sqlDf.format(new Date());
    }
    static String reqt() {
	return sqlDf.format(new Date());
    }
	   
    int failedRepeatsCnt = 0;
    int failedRepeatsCurrentStreak = 0;
    int failedRepeatsLongestStreak = 0;
    
    
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


    boolean isFirstRequest = true;
    boolean isFirstResponse = true;
    
    /** Sends a request to the Gemini bot, and extracts the main (text) part
	of the response from the received JSON structure. 

<pre>
 FIRST SERVER RESPONSE:
 {"candidates":    [{"content":{"parts":[{"text":"MOVE 0 3"}],"role":"model"},"finishReason":"STOP","index":0}],
  "usageMetadata":{"promptTokenCount":1139,"candidatesTokenCount":5,"totalTokenCount":4438,"promptTokensDetails":[{"modality":"TEXT","tokenCount":1139}],"thoughtsTokenCount":3294},
   "modelVersion":"models/gemini-2.5-flash-preview-05-20",
   "responseId":"GW1UaKCbDo2OjMcPipaA8Qc"}
</pre>


@return An array of candidate texts returned by the request. Usually it contains just 1 element, unless candidateCount was set to a higher value

*/
    private String[] doOneRequest(GeminiRequest gr) throws MalformedURLException, IOException, ProtocolException, ClassCastException
    {
	readApiKey();
	String u = "https://generativelanguage.googleapis.com/v1beta/models/";
	u += model + ":generateContent";
	u += "?key=" + gemini_api_key;
	
	URL url = new URL(u);

	//	JsonObject jo = JsonReflect.reflectToJSONObject(gr, false, null, 10);
	JsonObjectBuilder jb = JsonReflect.reflectToJSONObjectBuilder(gr, true, null, 10);
	JsonObject jo = jb.build();

	/*
	JsonObject responseSchemaJo = null;
	if (responseSchemaJo != null) {
	    JsonObjectBuilder rsjb = JsonReflect.reflectToJSONObjectBuilder(responseSchemaJo, false, null, 10);
	    
	}
	*/
	
	if (isFirstRequest) {
	    System.out.println("SENDING FIRST REQUEST: " + jo.toString());
	    isFirstRequest = false;
	    
	}
	String jsonInputString =  jo.toString();

	int retryCnt=0;
	JsonObject responseJo = null;       
	int code=0;
	int budgetDivider = 1;
	for(; retryCnt < 4; retryCnt++) {

	    // Have to re-create the request with a lower budget
	    if (budgetDivider>1) {
		int t0 =  (thinkingBudget==null)? 8192:thinkingBudget;
		int tb = t0/budgetDivider;
		System.out.println("Reducing thinkingBudget to " + tb);
		gr.addThinkingBudget( tb);
		jo = JsonReflect.reflectToJSONObject(gr, false, null, 10);
		jsonInputString =  jo.toString();
	    }

	    
	    lastRequestTime  = new Date();
	    HttpURLConnection con = (HttpURLConnection)url.openConnection();
	    con.setRequestMethod("POST");
	    con.setRequestProperty("Content-Type", "application/json");
	    con.setDoOutput(true);
	    
	    
	    try(OutputStream os = con.getOutputStream()) {
		byte[] input = jsonInputString.getBytes("utf-8");
		os.write(input, 0, input.length);			
	    } catch( javax.net.ssl.SSLHandshakeException ex) {
		System.out.println("Caught exception when writing to connection: "+ ex);
		int waitSec = 60;
		System.out.println("Waiting for " + waitSec + " seconds to retry after an exception");
		waitABit(waitSec * 1000);
		continue;
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
	    
	    responseJo = null;
	    try(BufferedReader br = new BufferedReader(isr)) {
		JsonReader jsonReader = Json.createReader(br);
		responseJo = jsonReader.readObject();
		jsonReader.close();
	    } catch( jakarta.json.stream.JsonParsingException ex) {
		System.out.println("Exception when reading response: " + ex);
	    }
	    
	    Date now = new Date();
	    long msecUsed = now.getTime() - lastRequestTime.getTime();

	    System.out.println("Request took " + msecUsed + " msec");
	    if (responseJo == null) {
		int waitSec = 60;
		System.out.println("Waiting for " + waitSec + " seconds to retry after a failed read");
		waitABit(waitSec * 1000);
		continue;
	    }

	    if (isFirstResponse) {
		System.out.println("At "+	reqt()+", FIRST SERVER RESPONSE: " + responseJo.toString());
		isFirstResponse = false;
		//System.exit(0);
	    }

	    boolean hitMax =  (code==200) && hitMaxTokens(responseJo);
	    if (code==200 && !hitMax) 	break;

	    System.out.println("At "+	reqt()+", SERVER RESPONSE: " + responseJo.toString());

	    if (hitMax) {
		budgetDivider *= 2;
		System.out.println("Hit MAX_TOKENS; raised budgetDivider to " + budgetDivider);
	    } else if (code==429) {
		int waitSec = error429(responseJo);
		System.out.println("Waiting for " + waitSec + " seconds to retry, as told by the server");
		waitABit(waitSec * 1000);
	    } else if (code==503) {
	// Error: HTTP response code = 503
	// SERVER RESPONSE: {"error":{"code":503,"message":"The model is overloaded. Please try again later.","status":"UNAVAILABLE"}}

		// This error often repeats even after 1-2 min, so let's have
		// longer periods: 1, 2, 4, 8 min.
		

		int waitSec = 60;
		for(int i=0; i<retryCnt; i++) waitSec*=2;

		System.out.println("Waiting for " + waitSec + " seconds to retry, as a wild guess");
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
	int ncan = candidatesJa.size();
	if (ncan<1)  throw new IllegalArgumentException("Expected to find 1 or more candidate, found " + ncan + ". RESPONSE=\n" + responseJo);


	String[] texts = new String[ncan];
	

	for(int jcan =0; jcan<ncan; jcan++) {
	
	    JsonObject contentJo = candidatesJa.getJsonObject(jcan).getJsonObject("content");
	    JsonArray partsJa = contentJo.getJsonArray("parts");
	    if (partsJa==null)  throw new IllegalArgumentException("No 'parts' found. RESPONSE=\n" + responseJo);	
	    if (partsJa.size()<1)  throw new IllegalArgumentException("Expected to find 1 part, found " + partsJa.size() + ". RESPONSE=\n" + responseJo);
	    Vector<String> v = new Vector<>();
	    for(int j=0; j<partsJa.size(); j++)  {
		v.add( partsJa.getJsonObject(j).getString("text"));
	    }
	    texts[jcan] = Util.joinNonBlank("\n", v);
	}


	//  "usageMetadata":{"promptTokenCount":1139,"candidatesTokenCount":5,"totalTokenCount":4438,"promptTokensDetails":[{"modality":"TEXT","tokenCount":1139}],"thoughtsTokenCount":3294},
	JsonObject umdJo =responseJo.getJsonObject("usageMetadata");
	System.out.println("usageMetadata: " + umdJo);
	int promptTokenCount = umdJo.getJsonNumber("promptTokenCount").intValue();
	sumPromptTokenCount += promptTokenCount;
	int candidatesTokenCount= umdJo.getJsonNumber("candidatesTokenCount").intValue();
	sumCandidatesTokenCount += candidatesTokenCount;
	int totalTokenCount = umdJo.getJsonNumber("totalTokenCount").intValue();
	sumTotalTokenCount += totalTokenCount;
	int thoughtsTokenCount = umdJo.getJsonNumber("thoughtsTokenCount").intValue();
	sumThoughtsTokenCount += thoughtsTokenCount;
	
	return texts;
	/*
	"candidates":[
  {"content": {"parts": [{"text": "MOVE 0 0\n"}],"role": "model"},
   "finishReason": "STOP",
   "avgLogprobs": -1.9414728740230203e-05}
  ],
"usageMetadata": ...
	*/	
	
    }
    
    /** Sometimes, a code-200 response may look like this:

<pre>
{ "candidates":[{"content":{"role":"model"},"finishReason":"MAX_TOKENS","index":0}],
  "usageMetadata":{"promptTokenCount":1360,"totalTokenCount":66895,"promptTokensDetails":[{"modality":"TEXT","tokenCount":1360}],"thoughtsTokenCount":65535},
  "modelVersion":"models/gemini-2.5-flash-preview-05-20",
  "responseId":"y3lUaNivFu6q1MkPvduf2Ag"}
</pre>
    */


  /*
This usually only happens with temperature=0, when Gemini thinks especially hard. When that happens, we'll try to reduce the thinking budget...
    */
    private boolean hitMaxTokens(JsonObject responseJo) {
	JsonArray candidatesJa = responseJo.getJsonArray("candidates");
	int ncan = candidatesJa.size();
	for(int j=0; j<ncan; j++) {
	    //if (candidatesJa.size()!=1)  throw new IllegalArgumentException("Expected to find 1 candidate, found " + candidatesJa.size() + ". RESPONSE=\n" + responseJo);

	    String finishReason = candidatesJa.getJsonObject(j).getString("finishReason");	
	    if (finishReason!=null && finishReason.equals("MAX_TOKENS")) return true;
	}
	return false;
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
	try {
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
	} catch(IllegalArgumentException ex) {
	    int sec = 60;
	    System.out.println("Difficult to handle error 429: " +  ex +"\n; sleeping " +sec + " seconds instead");
	    return sec;
	}
    }

    static final Pattern secPat = Pattern.compile("([0-9]+)s");

    /** Just a test: creates a request with system instruction */
    static GeminiRequest makeRequest1() {
	GeminiRequest gr = new GeminiRequest();
	gr.addInstruction("Please answer in German, if you can");	
	gr.addUserText("How do you use borax?");
	return gr;
    }


    static String instructions=null;
    //    static String responseSchema=null;
    
    
    static String instructionsFile = null;
    /** null means "let the model use its default value" */
    static Double temperature = null;
    static Integer thinkingBudget = null;
    static Integer candidateCount = null;
    
    static String keyFile = "/opt/w2020/gemini-api-key.txt";
    static String gemini_api_key = null;
    static String model = "gemini-2.0-flash";
    static long wait = 4000;
    static int max_boards=10;
    static int max_requests=0;

    /** In a prepared-episode run, how many completed episodes are shown to the bot to learn? (AKA the training set) */
    static int prepared_episodes = 0;
    /** In a prepared-episode run, how many initial boards episodes are shown to the bot to solve? (AKA the test set) */
    static int future_episodes = 0;
    static boolean bob = false;
    enum PrepareMode {
	random, orderly;
    }
    static PrepareMode prepareMode = PrepareMode.random;
    
    static void readApiKey() throws IOException {
	if ( gemini_api_key != null) return;
	String s = Util.readTextFile(new File(keyFile));
	s = s.replaceAll("\\s", "");
	gemini_api_key = s;
    }

    static NumberFormat dollarFmt = new DecimalFormat("0.00");

   /** @param  _targetStreak this is how many consecutive error-free moves the player must make (e.g. 10) in order to demonstrate successful learning. If 0 or negative, this criterion is turned off
	@param _targetR the product of R values of a series of consecutive moves should be at least this high) in order to demonstrate successful learning. If 0 or negative, this criterion is turned off
    */   
    private static int targetStreak=10;
    private static double targetR=0;
    private static OutputMode outputMode =  OutputMode.FULL; 
    private static MlcLog log=null;

    /** If not null, the detailed transcript is written here */
    private File transcriptFile = null;
    private Captive.GGWrapper ggw=null;

    
    /** Modeled on Captive.java
	model=gemini-2.0-flash
	wait=4000  (wait time between requests in msec)
     */
    public static void main(String[] argv) throws Exception {
	
	Files.allowCachingAllRules(true); // for greater efficiency
	
	// The captive server does not need the master conf file in /opt/w2020
	MainConfig.setPath(null);
	// Enable the computing of feature-lists for Composite objects
	// (which is normally turned off)
	edu.wisc.game.svg.Composite.setNeedFeatures(true);
	
	ParseConfig ht = new ParseConfig();

	// allows seed=... , colors=..., condTrain=..., crowded=... etc among argv. Some of them are passed on to the buildGameGenerator method
	argv = ht.enrichFromArgv(argv);

	model = ht.getOption("model", model);
	wait = ht.getOptionLong("wait", wait);
	max_boards  = ht.getOption("max_boards", max_boards);
	keyFile = ht.getOption("keyFile", keyFile);
	instructionsFile = ht.getOption("instructionsFile", instructionsFile);
	max_requests  = ht.getOption("max_requests", max_requests);
	targetStreak = ht.getOption("targetStreak", targetStreak);
	targetR = ht.getOptionDouble("targetR", targetR);
	
	temperature = ht.getOptionDoubleObject("temperature", temperature);
	boolean tZero = (temperature!=null && temperature.doubleValue()==0);
	thinkingBudget = ht.getOptionIntegerObject("thinkingBudget",
						   tZero ? 8192: null);
	candidateCount  = ht.getOptionIntegerObject("candidateCount", candidateCount);

	prepared_episodes = ht.getOption("prepared_episodes", prepared_episodes);
	if (prepared_episodes >0) future_episodes = ht.getOption("future_episodes", 5);
	
	prepareMode = ht.getOptionEnum(PrepareMode.class, "prepareMode", PrepareMode.random);	    
	bob = (prepared_episodes>0);

	File f =  (instructionsFile==null)? new File( Files.geminiDir(), "system.txt"):
	    new File(instructionsFile);
	instructions = Util.readTextFile( f);

	
	String resumeFileName = ht.getOption("resume", null);
	File resumeFrom = null;
	if (resumeFileName!=null) resumeFrom  = new File( resumeFileName);

	
	System.out.println("At " + now() +", starting playing with Gemini. Game Server ver. "+ Episode.getVersion());
	System.out.println("Gemini model=" + model);
	//System.out.println("output=" +  ht.getOption("output", null));
	outputMode = ht.getOptionEnum(OutputMode.class, "output", outputMode);

	String inputDir=ht.getOption("inputDir", null);
	//System.out.println("#inputDir=" + inputDir);
	if (inputDir!=null) Files.setInputDir(inputDir);

	log=Captive.mkLog(ht);		      
	GeminiPlayer history = new GeminiPlayer();

	GameGenerator gg=null;
	try {
	    history.ggw = Captive.buildGameGenerator(ht, argv);
	    gg = history.ggw.gg;
	} catch(Exception ex) {
	    usage("Cannot create game generator. Problem: " + ex.getMessage());
	}
	        	
	if (log!=null) log.rule_name = gg.getRules().getFile().getName().replaceAll("\\.txt$", "");


	System.out.println("Game generator=" + gg);
	File ruleFile = gg.getRules().getFile();
	System.out.println("Rule set=" + ruleFile);
	System.out.println("Rule text={\n" +Util.readTextFile(ruleFile) +"\n}");
		
	int gameCnt=0;

	if (log!=null) log.open();

	String transcriptFileName=ht.getOption("detailedTranscript", null);
	if (transcriptFileName!=null) {
	    //if (log==null) usage("Cannot use 'detailedTranscript=...' without 'log=...'");			       
	    history.transcriptFile = new File(transcriptFileName);
	}


	


	System.out.println("Instructions are: {\n" + instructions +  "\n}");
	if (temperature==null) {
	    System.out.println("Using the model's default temperature");
	} else {
	    System.out.println("Temperature=" + temperature);
	}

	System.out.println("ThinkingBudget=" +
			   (thinkingBudget==null? "default": ""+thinkingBudget));

	boolean won = false;

	if (prepared_episodes>0) {
	    long seed = ht.getOptionLong("seed", 0L);
	    final RandomRG random= (seed != 0L)? new RandomRG(seed): new RandomRG();

	    for(int i=0; i<prepared_episodes; i++) {
		System.out.println("Generating prepared episode " + i);
		history.addPreparedEpisode(gg, random);
	    }

	    //System.exit(0);
	    
	    // Add a few empty episode, for the bot to solve

	    GeminiPlayer future = new GeminiPlayer();
	    future.addFutureBoards(gg);
	    
	    history.askAboutPreparedEpisodes(future);
	    System.out.println(history.costReport());
	    if (log!=null) log.close();

	    
	    System.exit(0);
	}


	
	if (resumeFrom != null) {
	    System.out.println("Resuming from old log file " + resumeFrom);
	    history.readLogBack( gg, resumeFrom);
	}



	
	try {
	    
	for(; gameCnt < max_boards && !won; gameCnt++) {
	    Game game = gg.nextGame();
	    //	    if (outputMode== OutputMode.FULL) System.out.println(Captive.asComment(game.rules.toString()));

	    System.out.println("At "+  now()+ " Starting episode " + (gameCnt+1) + " of up to " + max_boards);

	    Episode epi = new Episode(game, outputMode,
				      new InputStreamReader(System.in),
				      new PrintWriter(System.out, true));
	
	    EpisodeHistory his = new EpisodeHistory(epi);
	    history.add(his);

	    //System.out.println("DEBUG: B=" + his.initialBoardAsString());

	    try {
		won = history.playingLoop();
	    } finally {
		if (log!=null) {
		    log.logEpisode(epi, gameCnt);
		    System.out.println("Logged episode " + (gameCnt+1) );
		}
	    }

	    if (max_requests > 0 && history.requestCnt >= max_requests) {
		break;
	    }

	    
	}


	if (won) { // Ask the bot how he did it
	    GeminiRequest gr = history.makeRequestHow();
	    String lines[] = history.doOneRequest(gr);
	    for(int j=0; j<lines.length; j++) {
		if (lines.length>0) System.out.println("Candidate " + j+ " of " + lines.length);
		System.out.println("Response text={" + lines[j].trim() + "}");
	    }
	}
	} finally {
	    System.out.println(history.costReport());
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


    /** Adds a few future boards to this GeminiPlayer object */
void addFutureBoards(GameGenerator gg) {

    for(int j=0; j<future_episodes; j++) {
    
	Game game = gg.nextGame();
	Episode epi = new Episode(game, outputMode,
				  new InputStreamReader(System.in),
				  new PrintWriter(System.out, true));
    
	EpisodeHistory his = new EpisodeHistory(epi);
	add(his);
    }
}


    private String costReport() {
	Vector<String> v = new Vector<>();
	v.add("In this session of "+totalAttemptCnt+" move attempts, there were "+
	      failedRepeatsCnt + " redundant repeated bad moves in streaks; the longest streak included " + failedRepeatsLongestStreak + " redundant repeats.");
	    

	v.add("Recorded costs over this session:" +
	      " sum(promptTokenCount)="+ sumPromptTokenCount +
	      " sum(CandidatesTokenCount)="+sumCandidatesTokenCount +
	      " sum(ThoughtsTokenCount)="+sumThoughtsTokenCount +
	      " sum(TotalTokenCount)="+sumTotalTokenCount);

	// https://ai.google.dev/gemini-api/docs/pricing
	// $0.30 per million input tokens,
	// $2.50 per mln output and thinking tokens
	int in = sumPromptTokenCount, out = sumTotalTokenCount - in;
	    
	double cost =  (0.30 * in + 2.50 * out)*1e-6;
	v.add("G2.5F cost estimate: $" + dollarFmt.format(cost));
	return String.join("\n", v);
    }
    
    GeminiPlayer() { super(); }

    static Integer maxToken = 200000;
    
    static GeminiRequest makeRequestAskHow() {
	GeminiRequest gr = new GeminiRequest();
	gr.addInstruction(instructions);
	gr.addTemperature(temperature);
	gr.addMaxOutputTokens(maxToken);
	gr.addThinkingBudget(thinkingBudget);
	gr.addCandidateCount(candidateCount);
	//gr.addUserText("How do you use borax?");
	return gr;
    }

    /** If this flag is true, we don't print the full text of 
	every request into the log, to save space */
    static boolean logsBrief = true;
    
    /** Creates a request object based on the current state of this
	GeminiPlayer, i.e. all episodes that have been completed, and
	the one still in progress */
    GeminiRequest makeRequest() throws IOException {
	GeminiRequest gr = new GeminiRequest();	    
	gr.addInstruction(instructions);
	gr.addTemperature(temperature);
	gr.addMaxOutputTokens(maxToken);
	gr.addThinkingBudget(thinkingBudget);
	gr.addCandidateCount(candidateCount);
	Vector<String> v = describeHistory(false);
	v.add("YOUR MOVE?");
	String text = Util.joinNonBlank("\n", v);
	System.out.println("===========================================\n"+
			   "The text part of the request:\n" + text);
	gr.addUserText(text);
	return gr;
    }

    /** Produces a request to be sent at the end of the session, if the bot
	has demonstrated its mastery of the rules. */
    GeminiRequest makeRequestHow() throws IOException {
	GeminiRequest gr = new GeminiRequest();	    
	gr.addInstruction(instructions);
	Vector<String> v = describeHistory(true);
	v.add("You have played pretty well recently. Could you now EXPLAIN your understanding of the secret rule?");
	String text = Util.joinNonBlank("\n", v);
	System.out.println("===========================================\n"+
			   "The text part of the request:\n" + text);
	gr.addUserText(text);
	return gr;
    }

/** Makes a request describing prepared episodes (stored in this
    player) and future episodes.
    @param future The initial boards for the future episodes.
 */
    private GeminiRequest makeRequestPrepared(GeminiPlayer future) throws IOException {
	GeminiRequest gr = new GeminiRequest();
	gr.setNeedResponseSchema(true); // ask for structured response
	
	gr.addInstruction(instructions);
	gr.addTemperature(temperature);
	gr.addMaxOutputTokens(maxToken);
	gr.addThinkingBudget(thinkingBudget);
	gr.addCandidateCount(candidateCount);
	Vector<String> v = describeHistory(true);
	v.add("Please tell me what you think the hidden rules are.");
	v.add("Finally, based on your idea of the hidden rules, please propose, for each of the following " + future.size() +  " future episodes, a sequence of move attempts that are most likely to clear the board in that episode");

	v.addAll(future.describeFutureEpisodes());

	
	String text = Util.joinNonBlank("\n", v);
	System.out.println("===========================================\n"+
			   "The text part of the request:\n" + text);
	gr.addUserText(text);
	return gr;
    }


    /** Produces a text describing the entire history of the session, to
	be sent to the Gemini bot with the request.
	
	@param how If true, this history is produced for a "how did you do it" 
	request, rather than a "YOUR MOVE?" request.
    */
    private Vector<String> describeHistory(boolean how) {
	Vector<String> v = new Vector<>();
	
	if (size()==0) throw new IllegalArgumentException("No episode exists yet. What to ask?");
	//	EpisodeHistory ehi = lastElement();
	//Episode epi = ehi.epi;
	if (lastElement().epi.isCompleted() && !how)  throw new IllegalArgumentException("Last episode already completed. What to ask?");

	if (size()>1) {
	    // A rare case of mastery demonstrated on the last piece
	    // of the board
	    boolean lastIsClearedToo = lastElement().epi.getCleared();	    

	    // describe all previous episodes.
	    int n= lastIsClearedToo?  size() : size()-1;

	    String youHave = (bob?"Bob has":"You have");
	    if (n==1) {
		v.add(youHave + " completed 1 episode so far. Its summary follows.");
	    } else {
		v.add(youHave + " completed " + n + " episodes so far. Their summary follow.");
	    }
	}
	for(int j=0; j<size(); j++) {
	    v.addAll( episodeText(j));
	}
	return v;
    }

    /** Describes the boards for the bot to solve. Used in
	prepared-episodes runs
     */
    private Vector<String> describeFutureEpisodes() {
	Vector<String> v = new Vector<>();
	
	if (size()==0) throw new IllegalArgumentException("No episode exists yet. What to ask?");

	for(int j=0; j<size(); j++) {
	    v.add( "The initial board for future episode No. " + (j+1)+ ":");
	    EpisodeHistory ehi = get(j);
	    v.add( ehi.initialBoardAsString());
	}
	return v;
    }


    /** Creates lines describing an episode, to go into a request.
	The language is a bit different for the older (completed)
	episodes and the current (incomplete) episode. (E.g. past vs.
	present tense, etc).
	@param j The epsiode's sequential (0-based) number in the 
	series. In the output, the number is converted to 1-based though.
     */
    private Vector<String> episodeText(int j) {
	
	Vector<String> v = new Vector<>();
	boolean isLast = (j==size()-1);
	EpisodeHistory ehi = get(j);

	
	Vector<Pick> moves = ehi.epi.getTranscript();

	
	// All episodes other than the last are expected to have the board
	// cleared. The last one usually has the board not cleared yet,
	// except in a rare "corner case" when the player had displayed mastery
	// (made 10th successful move in a row) exactly at the point of
	// removing the last piece from the board.	
	boolean cleared = ehi.epi.getCleared();
	int n = moves.size();

	if (j==size()-1) {
	    if (cleared) {
		v.add((bob? "Bob has": "You have")+ " just completed Episode "+(j+1)+".");
	    } else {
		v.add((bob? "Bob is" : "You are")+" playing Episode "+(j+1)+" now.");
	    }
	}

	
	v.add("Episode " + (j+1)  +
	      (cleared? " had" : " has") +	      
	      " the following initial board: " +
	      ehi.initialBoardAsString());

	if (cleared) {
	    v.add("During episode "+(j+1)+", "+
		  (bob? "Bob" : "you") +
		  " cleared the board by making " +
		  n + " move attempts. They are shown below, along with their results.");
	} else if (n==0) {
	    v.add("You are about to make your first move now");
	} else {
	    
	    v.add("During episode "+(j+1)+
		  (bob? ", Bob " : ", you ")+
		  (isLast ? "have made so far ": "made ") +
		  "the following "+ n + " move attempt" + (n>1? "s":"") +
		  ", with the following results:");
	}
	boolean redundant = false;
	for(int k=0; k<n; k++) {
	    if (!(moves.get(k) instanceof Move))  throw new IllegalArgumentException("Unexpected entry in the transcript (j=" + j+", k=" + k+", The bot is only supposed to make moves, not picks!");
	    Move move = (Move)moves.get(k);
	    //	    v.add("Move " + (k+1) + " :  " + move);

	    /*
"MOVE id bucketId response",
where "id" is the ID of the object that you attempted to move, "bucketId" is the ID of the bucket into which you wanted to place it, and "response" is whatever response I have given to that move. The response is one word, which can be one of the following: ACCEPT, NOT_MOVABLE, DENY, INVALID.
	    */
	    int code = move.getCode();
	    
	    String s = "MOVE " + move.getPieceId() +" "+ move.getBucketNo()+" "+
		CODE.toBasicName(code);

	    redundant = ehi.repeats.redundant.get(k);
	    if (remind) {
		if (redundant) {
		    s += " -- redundant!";
		}
	    }
	    
	    v.add(s);
	    
	}


	if (remind && ehi.repeats.totalRepeats()>0) {
	    String  s= "The episode includes " + ehi.repeats.totalRepeats()
		+ " redundant moves";
	    if (redundant) s += ", including the last one";
	    s += ". You really should not make such redundant move attempts, since they give you no new information. Remember that the as long as the board state has not changed, the response to the repeated move won't change either!";
	    v.add(s);
	}
    
    
	if (isLast && n>0) {
	    // Showing the current board to the bot, because, at least very
	    // occasional, the bot seems to "forget" that some pieces have been
	    // removed. Hoping this will take care of the issue.
	    v.add("Now the board contains the following objects:");
	    v.add( ehi.currentBoardAsString());
	}
	return v;
    }

    //    final Pattern movePat = Pattern.compile("\\bMOVE\\s+([0-9]+)\\s+([0-9]+)");
    final Pattern movePat = Pattern.compile("^MOVE\\s+([0-9]+)\\s+([0-9]+)",
					    Pattern.MULTILINE);
    int lastStretch;
    double lastR;

    /** The total number of Gemini requests made so far in all episodes played in this run.
	Normally (if no retries are ever needed) this is equals to the number of moves
	in all episodes so far.	
    */
    int requestCnt = 0;

    /** Total number of attempted moves the bot has made in all episodes */
    int totalAttemptCnt = 0;


    
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

Very occasionally, the "parts" array has multiple elements, each one havng a "text" in it. 

@return true on victory (mastery demonstrated), false otherwise
     */

    boolean playingLoop()  throws IOException {

        EpisodeHistory ehi = lastElement();
	Episode epi = ehi.epi;
	
	while( !epi.isCompleted()){
	    GeminiRequest gr = makeRequest();

	    int tryCnt = 0;
	    Matcher m = null;
	    int[] w = null;
	    while(true) {
		String lines[] = doOneRequest(gr);
		if (lines.length!=1) throw new IllegalArgumentException("Expected 1 candidate, found " + lines.length);
		String line=lines[0];
		requestCnt ++;
		tryCnt++;
		System.out.println("Response text={" + line.trim() + "}");
		MoveLine[] r = parseResponse(line);
		if (r.length==1) {
		    w = r[0].asPair();
		    break;
		} else if (r.length>1) {
		    throw new IllegalArgumentException("Unexpectedly found multiple moves in the response");
		}

		if (tryCnt>=2) {
		    throw new IllegalArgumentException("Could not find 'MOVE id bid' in this response text, even after "+tryCnt+" attempts: {" + line +"}");
		}
		// try to tell the bot to use the proper format
		gr.addModelText(line);
		gr.addUserText("I don't understand English very well. Please say again what YOUR MOVE is, remembering to describe your attempted move in the following format: 'MOVE objectId bucketId'!");
		System.out.println("At "+reqt()+", received an incomprehensible response, and am trying to ask again");
		waitABit(wait);
	    }
	
	    Boolean b = digestMove(w);
	    if (b!=null) return b;
	    
	    waitABit(wait); // 5 sec wait before the next request, to keep Gemini happy
	    
	}
  
	System.out.println("Episode ended. transcript has "+epi.getTranscript().size()+" moves. Board pop="+epi.getValues().size()+". lastStretch=" + lastStretch + ", lastR=" + lastR);
     
	return false;
  }


/** Creates and sends a one-shot request, asking to analyze prepared episodes and solve future boards; then processes the response.
    @param future the boards for future episodes (to be solved)
*/
void askAboutPreparedEpisodes(GeminiPlayer future) throws IOException,  ReflectiveOperationException {

	GeminiRequest gr = makeRequestPrepared(future);
	String lines[] = doOneRequest(gr);
	for(int j=0; j<lines.length; j++) {
	    if (lines.length>0) System.out.println("Candidate " + j+ " of " + lines.length);
	    log.run=j;
	    future.digestProposedMoves(lines[j]);
	}
}

/** Handles one candidate with proposed moves in it. This is called on the 
"future" object  */
private void digestProposedMoves(String line)  throws ReflectiveOperationException {
    System.out.println("Response text={" + line.trim() + "}");

    MoveLine[][] r =  PreparedEpisodesResponse.parseResponse(line);
    if (r==null) { // field not supplied
	return;
    }
    
    System.out.println("Found " + r.length + " proposed solutions in the response, for "+size() + " future boards");
    if (r.length != size()) throw new IllegalArgumentException("Future board count mismatch");
    
    int nc=0, nGoodMoves=0, nAttempts=0;
    
    for(int j=0; j<r.length; j++) {
	
	Episode epi = get(j).epi;
	epi.reset();
	int n = epi.getNPiecesStart();
	if (n!=r[j].length) {
	    System.err.println("Warning: the number of returned moves (" + r[j].length +  ") is different from the board population ("+n+")");
	}
	
	for(int i=0; i<r[j].length; i++) {
	    int [] w = r[j][i].asPair();
	    int code = digestMoveBasic(get(j), w);
	    nAttempts ++;
	    if (code == Episode.CODE.ACCEPT) nGoodMoves ++;
	}	   
	    
	System.out.println("Future board "+j+ " of "+r.length+": Result of proposed moves: cleared=" + epi.getCleared() + ", good moves count=" + epi.getDoneMoveCnt() + "/" + r[j].length);
	if ( epi.getCleared() ) nc++;
	
	log.logEpisode(epi, j);
	
		
    }
    
    System.out.println("Overall, cleared boards: " + nc + "/" + r.length +", good moves: " + nGoodMoves + "/" + nAttempts);
}



/** @return null if the episode needs to continue; a boolean value, to be 
    returned by playingLoop(), is the episodes ends now */
private int digestMoveBasic(EpisodeHistory ehi, int[] w) //throws IOException
{
    Episode epi = ehi.epi;
    totalAttemptCnt++;
    int id = w[0];
    int bid = w[1];
    
    Episode.Display q = epi.doMove2(id, bid,  epi.getTranscript().size());
    int code = q.getCode();
    System.out.println("At "+	reqt()+", Moving piece " + id + " to bucket " + bid + ". Code=" + code);
    //System.out.println("DEBUG B: transcript=" + epi.getTranscript());

    if (code==CODE.ATTEMPT_CNT_MISMATCH) {
	System.out.println("I have ended up with an attempt count mismatch somehow. My logic bug probably. Terminating");
	System.exit(1);
	//return false;
    }
	    
    Vector<Pick> moves = epi.getTranscript();
    final int n = moves.size();
	    
    if (code==CODE.ACCEPT) { // add to the "mastery stretch"
	lastStretch++;
	if (lastR==0) lastR=1;
	lastR *= epi.getLastMove().getRValue();		    
    } else { // a failed move or pick breaks the "mastery stretch"
	lastStretch=0;
	lastR = 0;
	
	if (n>1 &&
	    ((Move)moves.get(n-1)).sameMove(moves.get(n-2))) {
	    // The bot has just repeated the last move attempt,
	    // despite its failure
	    
	    failedRepeatsCnt++;
	    failedRepeatsCurrentStreak++;
	    if (failedRepeatsCurrentStreak>failedRepeatsLongestStreak) {
		failedRepeatsLongestStreak = failedRepeatsCurrentStreak;
	    }
	}		
    }

    if (n>0) {
	// Normally, n is positive, but if the first move was invalid
	// (code=-10), then it wasn't added to moves[]	
	boolean redundant = ehi.repeats.add((Move)moves.get(n-1), code);
    }
    
    return code;
}

/** @return null if the episode needs to continue; a boolean value, to be 
    returned by playingLoop(), is the episodes ends now */
private Boolean digestMove(int[] w)// throws IOException
{

    EpisodeHistory ehi = lastElement();
    Episode epi = ehi.epi;

    int code = digestMoveBasic(ehi, w);

    //-- transcript
    if (transcriptFile != null) {
	TranscriptManager.ExtraTranscriptInfo extra = new  TranscriptManager.ExtraTranscriptInfo();
	extra.playerId = model;
	// zzzz
	extra.trialListId = ggw.trialListId;
	extra.seriesNo = ggw.seriesNo; 
	extra.ruleId = log.rule_name;
	extra.episodeNo = size()-1;
	TranscriptManager.saveDetailedTranscriptToFile(epi, extra, transcriptFile);
    }
	   

    
    Vector<Pick> moves = epi.getTranscript();
    final int n = moves.size();

    boolean redundant = ehi.repeats.lastAddResult;
	    
    String stats = "Transcript has "+epi.getTranscript().size()+" moves. Board pop="+epi.getValues().size()+". lastStretch=" + lastStretch + ", lastR=" + lastR;
    
    System.out.println(stats);
    
    if (remind && ehi.repeats.totalRepeats()>0) {
	stats = "The episode includes " + ehi.repeats.totalRepeats()
	    + " redundant moves";
	if (redundant) stats += ", including the last one.";
	System.out.println(stats);
    }


    boolean won = false;
    if (targetStreak>0 & lastStretch>=targetStreak) won = true;
    if (targetR>0 & lastR>=targetR) won = true;
    

    if (won) {
	System.out.println("Victory: mastery demonstrated! " + stats);
	return true;
    }
    

    if (max_requests > 0 && requestCnt >= max_requests) {
	System.out.println("Request limit (" + max_requests+") reached");
	return false;
    }
    return null;
}

static class MoveLine {
    final int pieceId, bucketNo;
    MoveLine(int p, int b) {
	pieceId=p;
	bucketNo=b;
    }
    int [] asPair() {
	return new int[] {pieceId, bucketNo};
    }

}


    /** Parses the text returned from Gemini, looking for the last "MOVE id bid" pattern
	(thus skipping any preliminary discussion, quoting from the session's history,
	which Gemini sometimes includes before its proposed move).
	@return {id, bid} or null
    */
MoveLine[] parseResponse(String line) {
	Matcher m = movePat.matcher(line);
	Vector<MoveLine> result = new Vector<>();
	while(m.find()) {
	    MoveLine q= new MoveLine(
		Integer.parseInt( m.group(1)),
		Integer.parseInt( m.group(2)));
	    result.add(q);
	}
	return result.toArray(new MoveLine[0]);
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



    /** Reading log back, for restoring a history.
	The text starts after 
	"The text part of the request:",
	and ends with 
	"YOUR MOVE?"

    */
    private Vector<String> extractLastRequest(File f) throws FileNotFoundException, IOException {
	
	LineNumberReader r = new LineNumberReader(new FileReader(f));
	String s = null;
	Vector<String> v = new Vector<>();
	Vector<String> lastFoundText = null;
	boolean inside = false;
	while((s = r.readLine())!=null) {
	    if (s.startsWith(	"The text part of the request:")) {
		v.clear();
		inside = true;
	    }
	    if (inside) {
		if (s.startsWith("YOUR MOVE")) {
		    inside = false;
		    lastFoundText = new Vector<>();
		    lastFoundText.addAll(v);
		    v.clear();
		    requestCnt++;
		} else {		    
		    v.add(s);
		}
	    }	    
	}
	System.out.println("Found " + requestCnt + " requests in the log. The last one has " + lastFoundText.size() + "  lines");
	return lastFoundText;
    }

    
    private static final Pattern boardPat = Pattern.compile("^Episode ([0-9]+) .*?(\\{.*\\})");
    //	movePat("^(MOVE [0-9]+ [0-9]+)");
		

    /** Fills this GeminiPlayer with the recorded history from a file */
    private void readLogBack(GameGenerator gg, File f) throws IOException,
							      ReflectiveOperationException {
	Vector<String> v = extractLastRequest(f);
	int eNo = 0;
	for(String line: v) {
	    // "Episode 1 had the following initial board: {"value":...}"
	    Matcher m = boardPat.matcher(line);
	    if (m.find()) {
		int j = Integer.parseInt(m.group(1));
		String boardText = m.group(2);
		if (j==eNo+1) eNo++;
		else throw new IllegalArgumentException("Episode number out of order: " + j);
		System.out.println("Found board text=" + boardText);
		Board board = Board.readBoardFromString(boardText);
		board.dropLabels();
		Episode epi = new Episode(gg.getRules(), board, outputMode,
					  new InputStreamReader(System.in),
					  new PrintWriter(System.out, true));

		EpisodeHistory his = new EpisodeHistory(epi);
		/* history.*/add(his);


		continue;

	    }
	    MoveLine[] w = parseResponse(line);
	    if (w.length>0) {
		digestMove(w[0].asPair());
	    }
		
	    
	}
	
    }

    /** Creates an episode of (e.g.) random moves and adds them to the history
     */
    void addPreparedEpisode(GameGenerator gg,
			    RandomRG random
			    ) //throws IOException,
					 {
	Game game = gg.nextGame();
	Episode epi = new Episode(game, outputMode,
				  new InputStreamReader(System.in),
				  new PrintWriter(System.out, true));
	
	EpisodeHistory his = new EpisodeHistory(epi);
	add(his);

	while( !epi.getCleared()) {

	    RecentKnowledge rk = new RecentKnowledge(epi.getTranscript(), false);

	    //System.out.println("rk=" + rk);
	    Board b = epi.getCurrentBoard(false);
	    Piece p = rk.chooseOnePiece( random, b);
	    if (p==null) throw new IllegalArgumentException("No movable pieces left; stalemate?");

	    //-- Must cast long to int before using it as the key for the
	    //-- RecentKnowledge HashMap!!!
	    int j= (int)p.getId();
	    RecentKnowledge.Datum d = rk.get( j);
	    //System.out.println("Datum(" + j + ")=" + d);

	    BitSet q = new BitSet(Episode.NBU);
	    if (d!=null && d.getDeniedBuckets()!=null) {
		for(int z: d.getDeniedBuckets()) { q.set(z); }
	    }
	    q.flip(0, Episode.NBU); // allowed buckets
	    int[] allowedBuckets = Util.listBits(q);

	    //System.out.println("For piece " + p.getId() + ", allowed buckets are " + Util.joinNonBlank(", ", allowedBuckets));

	    
	    if (prepareMode==PrepareMode.random) {
		
		int k = random.nextInt(allowedBuckets.length);
		int bucketNo = allowedBuckets[k];
		int[] w = {(int)p.getId(), bucketNo};
		int code = digestMoveBasic(his, w);
		//if (code == CODE.ACCEPT) continue;
	    } else 	    if (prepareMode==PrepareMode.orderly) {
		for(int bucketNo: allowedBuckets) {
		    int[] w = {(int)p.getId(), bucketNo};
		    int code = digestMoveBasic(his, w);
		    if (code == CODE.ACCEPT) break;		    
		}
	    } else throw new IllegalArgumentException("Wrong mode: " + prepareMode);
	    
	    
	}

	
    }
    
}
