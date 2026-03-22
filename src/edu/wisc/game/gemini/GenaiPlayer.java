package edu.wisc.game.gemini;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.net.*;
import java.text.*;
import jakarta.json.*;


import com.google.genai.Client;
import com.google.genai.Chat;
import com.google.genai.types.*;
/*
  import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Candidate;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
*/
import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.sql.Episode.OutputMode;
import edu.wisc.game.sql.Episode.CODE;
import edu.wisc.game.sql.Episode.Pick;
import edu.wisc.game.sql.Episode.Move;
import edu.wisc.game.rest.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.saved.*;
import edu.wisc.game.saved.TranscriptManager.ReadTranscriptData;
import edu.wisc.game.tools.AnalyzeTranscriptsUtils;

import edu.wisc.game.gemini.PreparedEpisodesResponse.MoveLine;

public class GenaiPlayer  extends BasePlayer {

    /** Should we admonish the Gemini bot if it makes redundant moves? */
    static boolean remind = true;

    /** Various token count statistics, summed over all server responses
	in this session */
    /* xxx
    static int sumPromptTokenCount,
	sumCandidatesTokenCount,
	sumTotalTokenCount,
	sumThoughtsTokenCount;
    */
    
   static private void usage() {
	usage(null);
    }
    static private void usage(String msg) {
	System.err.println("Gemini Player (https://rulegame.wisc.edu/w2020/captive.html)\n");
	System.err.println("Usage:\n");
	System.err.println("  java [options]  edu.wisc.game.gemini.GenaiPlayer game-rule-file.txt board-file.json");
	System.err.println("  java [options]  edu.wisc.game.gemini.GenaiPlayer game-rule-file.txt npieces [nshapes ncolors]");
	System.err.println("  java [options]  edu.wisc.game.gemini.GenaiPlayer uildtrial-list-file.csv rowNumber");
	System.err.println("  java [options]  edu.wisc.game.gemini.GenaiPlayer R:rule-file.txt:modifier-file.csv");
	System.err.println("Each of 'npieces', 'nshapes', and 'ncolors' is eitheвr 'n' (for a single value) or 'n1:n2' (for a range). '0' means 'any'");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }

    /** xxx
    public static final DateFormat sqlDf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    static Date lastRequestTime = null;

    static Date now = null;
    static String now() {
	return sqlDf.format(now = new Date());
    }
    static String reqt() {
	return sqlDf.format(new Date());
    }
	   
    int failedRepeatsCnt = 0;
    int failedRepeatsCurrentStreak = 0;
    int failedRepeatsLongestStreak = 0;
    */
    
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
    
    /** Sends a "YOUR MOVE?" request to the Gemini bot, and extracts the main (text) part
	of the response from the received JSON structure. 
    */
    private String[] doOneRequest(Chat chat) throws MalformedURLException, IOException, ProtocolException, ClassCastException
    {

	Vector<String> v = new Vector<>();
	//---
	int j=size()-1;
	EpisodeHistory ehi = get(j);
	Vector<Pick> moves = ehi.epi.getTranscript();
	if (moves.size()==0) {
	    v.add("NEW EPISODE");
	    v.add(ehi.initialBoardAsString());
	}
	v.add("YOUR MOVE?");
	String text = Util.joinNonBlank("\n", v);
	System.out.println("At " + new Date() + ", sending text: " + text);
	GenerateContentResponse response = chat.sendMessage(text);

	System.out.println("At " + new Date() + ", received response");
	Optional<FinishReason> oreason = response.candidates().get().get(0).finishReason();
	if (oreason.isPresent()) {
	    System.out.println("Finish reason=" + oreason.get());
	    if (oreason.get().knownEnum().equals( FinishReason.Known.SAFETY)) {
		// Handle blocked content
		throw new IllegalArgumentException("SAFETY blocked content");
	    }
	}

	// 5. Parse parts to separate Reasoning from the Answer
	System.out.println("\nGemini's Process:");

	Optional<List<Candidate>> candidates = response.candidates();
	Candidate candidate = candidates.get().get(0);

	String answer[] = {null};
	for (Part part : candidate.content().get().parts().get()) {
	    if (part.thought().isPresent()) {
		System.out.println("[THOUGHTS]: " + part.text());
	    } else if (part.text() != null) {
		//System.out.println("[ANSWER]: " + part.text());
		if (answer[0]!=null) throw new IllegalArgumentException("Multiple text parts found in response");
		answer[0] = part.text().get();
	    }
	}
	
	return answer;
    }

    /** xxx
    static String instructions=null;
    
    static String instructionsFile = null;
    // null means "let the model use its default value" 
    static Double temperature = null;
    static Integer thinkingBudget = null;
    static Integer candidateCount = null;
    
    static String keyFile = "/opt/w2020/gemini-api-key.txt";
    static String gemini_api_key = null;
    static String model = "gemini-3-flash-preview";
    static long wait = 4000;
    static int max_boards=10;
    static int max_requests=0;
    */
    
    /** Are we in prepared-episodes mode? (That includes both random episodes,
	with prepared_episodes&gt;0, and human-player transcripted episodes,
	with human!=null).  */
    //private static boolean prepared = false;
    
    /** In a prepared-episode run with random episodes, how many completed episodes are shown to the bot to learn? (AKA the training set) */
    //private static int prepared_episodes = 0;

    /** In a prepared-episode run using old human player's episodes, the transcript file to read */
    //private static java.io.File human=null;
    /** If non-negative, the human player's transcript is truncated to this many move attempts */
    //private static int humanMaxMoves=0;

    /** In a prepared-episode run (either random or human), or in a
     * play-mode run's final question,how many initial boards episodes
     * are shown to the bot to solve? (AKA the test set) */
    /* xxx
    static int future_episodes = 0;
    static String who = "you";
    enum PrepareMode {
	random, orderly, positive, negative1;
    }
    static PrepareMode prepareMode = PrepareMode.random;
    
    static NumberFormat dollarFmt = new DecimalFormat("0.00");
    */
   /** @param  _targetStreak this is how many consecutive error-free moves the player must make (e.g. 10) in order to demonstrate successful learning. If 0 or negative, this criterion is turned off
	@param _targetR the product of R values of a series of consecutive moves should be at least this high) in order to demonstrate successful learning. If 0 or negative, this criterion is turned off
    */
    /* xxx
    private static int targetStreak=10;
    private static double targetR=0;
    private static OutputMode outputMode =  OutputMode.FULL; 
    private static MlcLog log=null;
    */
    /** If not null, the detailed transcript is written here */
    //    private java.io.File transcriptFile = null;
    //private Captive.GGWrapper ggw=null;

    /** Used with human players' transcripts If it's 4, we don't show most of the
	final winning streak to the bot */
    //private static int xFactor=0;
    
    /** Modeled on Captive.java
	model=gemini-2.0-flash
	wait=4000  (wait time between requests in msec)
     */
    public static void main(String[] argv) throws Exception {
	
	Files.allowCachingAllRules(true); // for greater efficiency

	// Since 8.051, we do need the config file, in order to access human
	// players' board files.
	//-- The captive server does not need the master conf file in /opt/w2020
	//-- MainConfig.setPath(null);
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
	//	instructionsFile2 = ht.getOption("instructionsFile2", instructionsFile2);
	max_requests  = ht.getOption("max_requests", max_requests);
	targetStreak = ht.getOption("targetStreak", targetStreak);
	targetR = ht.getOptionDouble("targetR", targetR);
	xFactor = ht.getOption("xFactor", xFactor);

	
	//	temperature = ht.getOptionDoubleObject("temperature", temperature);
	//boolean tZero = (temperature!=null && temperature.doubleValue()==0);
	//thinkingBudget = ht.getOptionIntegerObject("thinkingBudget",
	//					   tZero ? 8192: null);
	//	candidateCount  = ht.getOptionIntegerObject("candidateCount", candidateCount);

	prepared_episodes = ht.getOption("prepared_episodes", prepared_episodes);
	String humanFileName = ht.getOption("human", null);
	if (humanFileName !=null) {
	    if (prepared_episodes >0) throw new IllegalArgumentException("Cannot combined 'human' and 'prepared_episodes'");
	    human = new java.io.File(humanFileName);
	    humanMaxMoves = ht.getOption("humanMaxMoves", 0);
	}
	prepared = (prepared_episodes>0) || (human!=null);
	
	prepareMode = ht.getOptionEnum(PrepareMode.class, "prepareMode", PrepareMode.random);	    

	if (prepared) who = (prepareMode==PrepareMode.positive)? "Alice" : "Bob";
	System.out.println("prepareMode=" + prepareMode+", who=" + who);

	// future episodes can be used in both prepared-episodes mode and play modes (for the final questions)
	//if (prepared)
	    future_episodes = ht.getOption("future_episodes", 5);

	//System.exit(0);
	
	java.io.File f =  (instructionsFile==null)? new java.io.File( Files.geminiDir(), "system-genai.txt"):
	    new java.io.File(instructionsFile);
	instructions = Util.readTextFile( f);

	String resumeFileName = ht.getOption("resume", null);
	java.io.File resumeFrom = null;
	if (resumeFileName!=null) resumeFrom  = new java.io.File( resumeFileName);
	
	System.out.println("At " + now() +", starting playing with Gemini. Game Server ver. "+ Episode.getVersion());
	Date startDate = now;
	System.out.println("Gemini model=" + model);
	//System.out.println("output=" +  ht.getOption("output", null));
	outputMode = ht.getOptionEnum(OutputMode.class, "output", outputMode);

	String inputDir=ht.getOption("inputDir", null);
	//System.out.println("#inputDir=" + inputDir);
	if (inputDir!=null) Files.setInputDir(inputDir);

	log=Captive.mkLog(ht);		      
	GenaiPlayer history = new GenaiPlayer();

	GameGenerator gg=null;
	try {
	    history.ggw = Captive.buildGameGenerator(ht, argv);
	    gg = history.ggw.gg;
	} catch(Exception ex) {
	    usage("Cannot create game generator. Problem: " + ex.getMessage());
	}
	        	
	if (log!=null) log.rule_name = gg.getRules().getFile().getName().replaceAll("\\.txt$", "");


	System.out.println("Game generator=" + gg);
	java.io.File ruleFile = gg.getRules().getFile();
	System.out.println("Rule set=" + ruleFile);
	System.out.println("Rule text={\n" +Util.readTextFile(ruleFile) +"\n}");
		
	int gameCnt=0;

	if (log!=null) log.open();

	String transcriptFileName=ht.getOption("detailedTranscript", null);
	if (transcriptFileName!=null) {
	    //if (log==null) usage("Cannot use 'detailedTranscript=...' without 'log=...'");			       
	    history.transcriptFile = new java.io.File(transcriptFileName);
	}

	System.out.println("instructionsFile=" + instructionsFile );
	System.out.println("Instructions are: {\n" + instructions +  "\n}");
	if (temperature==null) {
	    System.out.println("Using the model's default temperature");
	} else {
	    System.out.println("Temperature=" + temperature);
	}

	System.out.println("ThinkingBudget=" +
			   (thinkingBudget==null? "default": ""+thinkingBudget));

	boolean won = false;

	if (prepared) {
	    throw new IllegalArgumentException("Genai is not needed for prepared-episode mode");
	}

	boolean mustResumeNow = false;
	
	if (resumeFrom != null) {
	    throw new IllegalArgumentException("Resume mode is not available with Genai yet");
	    /*
	    System.out.println("Resuming from old log file " + resumeFrom);
	    history.readLogBack( gg, resumeFrom);
	    System.out.println("Restored " + history.size() + " episodes");
	    mustResumeNow = history.size()>0 && !history.lastElement().epi.isCompleted();// unfinished last episode (resumeFile mode);
	    */
	}


        // 1. Initialize the Client (Instead of having GEMINI_API_KEY is in your environment, we get it from the command line)
        Client client = Client.builder()
            .apiKey(gemini_api_key)
            .build();

	Content systemInstruction = Content.builder()
            .parts(Collections.singletonList(
                Part.builder()
                    .text(instructions)
                    .build()
            ))
            .build();
        // 2. Configure Thinking (Required for Gemini 3 models to show reasoning)
        GenerateContentConfig config = GenerateContentConfig.builder()
            .thinkingConfig(ThinkingConfig.builder()
                .includeThoughts(true) // Allows us to see the "thought" parts
                .build())
	    .systemInstruction(systemInstruction)
            .build();

        // 3. Start a stateful Chat session
        // The SDK's Chat object automatically passes thoughtSignatures back and forth
        Chat chat = client.chats.create(model, config);

           
	try {
	    
	for(; gameCnt < max_boards && !won; gameCnt++) {
	    Game game = gg.nextGame();
	    //	    if (outputMode== OutputMode.FULL) System.out.println(Captive.asComment(game.rules.toString()));

	    System.out.println("At "+  now()+ " Starting episode " + (gameCnt+1) + " of up to " + max_boards);

	    Episode epi;
	    EpisodeHistory his;
	    if (mustResumeNow) { // unfinished last episode (resumeFile mode);
		his = history.lastElement();
		epi = his.epi;
		mustResumeNow = false;
	    } else {
		epi = new Episode(game, outputMode,
				  new InputStreamReader(System.in),
				  new PrintWriter(System.out, true));
		epi.setShowAllMovables(false);
		his = new EpisodeHistory(epi);
		history.add(his);
	    }

	    //System.out.println("DEBUG: B=" + his.initialBoardAsString());

	    try {
		won = history.playingLoop(chat);
	    } finally {
		if (log!=null) {
		    log.logEpisode(epi, gameCnt);
		    System.out.println("Logged episode " + (gameCnt+1) );
		}
		history.saveDetailedTranscript();
	    }

	    if (max_requests > 0 && history.requestCnt >= max_requests) {
		break;
	    }
	}
	

	GenaiPlayer future = new GenaiPlayer();
	future.addFutureBoards(gg);

	history.askAboutPreparedEpisodes(chat, future);
	} finally {
	    //System.out.println(history.costReport());
	    if (log!=null) log.close();

	    
	    long msec = new Date().getTime() - startDate.getTime();
	    System.out.println("The run took " + msec + " msec");
	    
	}
    
	System.exit(0);
    }


    /** Adds a few future boards to this GenaiPlayer object */
    /* xxx
    void addFutureBoards(GameGenerator gg) {

	for(int j=0; j<future_episodes; j++) {
	    
	    Game game = gg.nextGame();
	    Episode epi = new Episode(game, outputMode,
				      new InputStreamReader(System.in),
				      new PrintWriter(System.out, true));
	    epi.setShowAllMovables(false);

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

	// Feb 2026,  G3F: 0.50 input, 3.00 output
	
	int in = sumPromptTokenCount, out = sumTotalTokenCount - in;
	    
	//double cost =  (0.30 * in + 2.50 * out)*1e-6;
	//v.add("G2.5F cost estimate: $" + dollarFmt.format(cost));
	double cost =  (0.50 * in + 3.00 * out)*1e-6;
	v.add("G3F cost estimate: $" + dollarFmt.format(cost));

	return String.join("\n", v);
    }
    */
    
    GenaiPlayer() { super(); }

    static Integer maxToken = 200000;
 
    /** If this flag is true, we don't print the full text of 
	every request into the log, to save space */
    static boolean logsBrief = true;
    
    /** Describes the boards for the bot to solve. Used in
	prepared-episodes runs
     */
    /* xxx
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


    final Pattern movePat = Pattern.compile("^MOVE\\s+([0-9]+)\\s+([0-9]+)",   Pattern.MULTILINE);
    private int lastStretch;
    private double lastR;
    */
    /** The total number of Gemini requests made so far in all episodes played in this run.
	Normally (if no retries are ever needed) this is equals to the number of moves
	in all episodes so far.	
    */
    //int requestCnt = 0;

    /** Total number of attempted moves the bot has made in all episodes */
    //int totalAttemptCnt = 0;

    
    /** Plays the last (latest) episode of this GenaiPlayer, until it ends.

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

    boolean playingLoop(Chat chat)  throws IOException {

        EpisodeHistory ehi = lastElement();
	Episode epi = ehi.epi;
	
	while( !epi.isCompleted()){
	    //	    GeminiRequest gr = makeRequest();

	    
	    //	    System.exit(0); //zzz
	    int tryCnt = 0;
	    Matcher m = null;
	    int[] w = null;
	    while(true) {
		String lines[] = doOneRequest(chat);
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

		throw new IllegalArgumentException("Could not find 'MOVE id bid' in this response text; exiting");
		/*
		
		if (tryCnt>=2) {
		    throw new IllegalArgumentException("Could not find 'MOVE id bid' in this response text, even after "+tryCnt+" attempts: {" + line +"}");
		}
		// try to tell the bot to use the proper format
		gr.addModelText(line);
		gr.addUserText("I don't understand English very well. Please say again what YOUR MOVE is, remembering to describe your attempted move in the following format: 'MOVE objectId bucketId'!");
		System.out.println("At "+reqt()+", received an incomprehensible response, and am trying to ask again");
		waitABit(wait);
		*/	       
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
    void askAboutPreparedEpisodes(Chat chat, GenaiPlayer future) throws IOException,  ReflectiveOperationException {

	String schemaString = ResponseSchemaUtil.mkResponseSchema(false).build().toString();
	// Configure the specific turn for JSON
	GenerateContentConfig jsonConfig = GenerateContentConfig.builder()
	    .responseMimeType("application/json")
            .responseJsonSchema(schemaString)
            .build();

	Vector<String> v = new Vector<>();
	v.add("EXPLAIN");
	v.add("Finally, based on your idea of the hidden rules, please propose, for each of the following " + future.size() +  " future episodes, a sequence of move attempts that are most likely to clear the board in that episode");

	v.addAll(future.describeFutureEpisodes());

	String msg = Util.joinNonBlank("\n", v);
	
        // Request the structured data
	GenerateContentResponse response = chat.sendMessage(msg,  jsonConfig);
      
	String lines[] = {response.text()};
	for(int j=0; j<lines.length; j++) {
	    if (lines.length>0) System.out.println("Candidate " + j+ " of " + lines.length);
	    if (log!=null) log.run=j;
	    future.digestProposedMoves(lines[j]);
	}
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


}

