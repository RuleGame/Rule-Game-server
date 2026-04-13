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
import edu.wisc.game.parser.RuleParseException;
import edu.wisc.game.saved.*;
import edu.wisc.game.saved.TranscriptManager.ReadTranscriptData;
import edu.wisc.game.tools.AnalyzeTranscriptsUtils;
import edu.wisc.game.gemini.PreparedEpisodesResponse.MoveLine;

/** The base class of GeminiPlayer and GenaiPlayer */
class BasePlayer  extends Vector<EpisodeHistory> {

        /** Should we admonish the Gemini bot if it makes redundant moves? */
    static boolean remind = true;

    /** Various token count statistics, summed over all server responses
	in this session */
    static int sumPromptTokenCount,
	sumCandidatesTokenCount,
	sumTotalTokenCount,
	sumThoughtsTokenCount;

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
    
    
    //    int sentCnt = 0;

    
    /** In a prepared-episode run (either random or human), or in a
     * play-mode run's final question,how many initial boards episodes
     * are shown to the bot to solve? (AKA the test set) */
    static int future_episodes = 0;
    static String who = "you";
    enum PrepareMode {
	random, orderly, positive, negative1;
    }
    static PrepareMode prepareMode = PrepareMode.random;
    
    static void readApiKey() throws IOException {
	if ( gemini_api_key != null) return;
	String s = Util.readTextFile(new File(keyFile));
	s = s.replaceAll("\\s", "");
	gemini_api_key = s;
    }

    static NumberFormat dollarFmt = new DecimalFormat("0.00");



    static String instructions=null;
    /** In Play mode, these are the instructions used for the final request,
	when asking to explain the rules and play future episodes */
    static String instructions2=null;
    
    static String instructionsFile = null;
    static String instructionsFile2 = null;
    /** null means "let the model use its default value" */
    static Double temperature = null;
    static Integer thinkingBudget = null;
    static Integer candidateCount = null;
    
    static String keyFile = "/opt/w2020/gemini-api-key.txt";
    static String gemini_api_key = null;
    static String model = "gemini-3-flash-preview";
    static long wait = 4000;
    static int max_boards=10;
    static int max_requests=0;

    /** Are we in prepared-episodes mode? (That includes both random episodes,
	with prepared_episodes&gt;0, and human-player transcripted episodes,
	with human!=null).  */
    protected static boolean prepared = false;
    
    /** In a prepared-episode run with random episodes, how many completed episodes are shown to the bot to learn? (AKA the training set) */
    protected static int prepared_episodes = 0;

    /** In a prepared-episode run using old human player's episodes, the transcript file to read */
    protected static File human=null;
    /** If non-negative, the human player's transcript is truncated to this many move attempts */
    protected static int humanMaxMoves=0;


    
   /** @param  _targetStreak this is how many consecutive error-free moves the player must make (e.g. 10) in order to demonstrate successful learning. If 0 or negative, this criterion is turned off
	@param _targetR the product of R values of a series of consecutive moves should be at least this high) in order to demonstrate successful learning. If 0 or negative, this criterion is turned off
    */   
    protected static int targetStreak=10;
    protected static double targetR=0;
    protected static OutputMode outputMode =  OutputMode.FULL; 
    protected static MlcLog log=null;

    /** If not null, the detailed transcript is written here */
    protected File transcriptFile = null;
    protected Captive.GGWrapper ggw=null;

    /** Used with human players' transcripts If it's 4, we don't show most of the
	final winning streak to the bot */
    protected static int xFactor=0;

    /** Looking for lines beginning with "MOVE nn nn", or "**MOVE nn nn". (G3F
	sometimes ends a long disquisition with a move in the latter format)
    */
    //    final Pattern movePat = Pattern.compile("\\bMOVE\\s+([0-9]+)\\s+([0-9]+)");
    //    final Pattern movePat = Pattern.compile("^MOVE\\s+([0-9]+)\\s+([0-9]+)\\s*$",   Pattern.MULTILINE);
    final Pattern movePat = Pattern.compile("^\\**\\s*MOVE\\s+([0-9]+)\\s+([0-9]+)",   Pattern.MULTILINE);
    protected int lastStretch;
    protected double lastR;

    /** The total number of Gemini requests made so far in all episodes played in this run.
	Normally (if no retries are ever needed) this is equals to the number of moves
	in all episodes so far.	
    */
    int requestCnt = 0;

    /** Total number of attempted moves the bot has made in all episodes */
    int totalAttemptCnt = 0;



    
        /** If transcripting is requested, saves the detailed transcript of the most recent episode.
	Should be called at the end of each episode */
    protected void saveDetailedTranscript() {
	if (transcriptFile != null) {

	    EpisodeHistory ehi = lastElement();
	    Episode epi = ehi.epi;

	    System.out.println("Writing transcript for episode no. " + size());
	    
	    
	    TranscriptManager.ExtraTranscriptInfo extra = new  TranscriptManager.ExtraTranscriptInfo();
	    extra.playerId = model;
	    extra.trialListId = ggw.trialListId;
	    extra.seriesNo = ggw.seriesNo; 
	    extra.ruleSetName = log.rule_name;
	    extra.episodeNo = size()-1;
	    TranscriptManager.saveDetailedTranscriptToFile(epi, extra, transcriptFile);
	}
    }

    /** Adds a few future boards to this GeminiPlayer object. Each
	board is put into a not-played-yet Episode object, which is
	added to this GeminiPlayer object.
     */
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

    /** Creates a BasePlayer that stores empty episodes based on those
	that were played in this episode, but with a different
	("alternative") rule set.  This is used to analyze the inferred
	rule set proposed by the bot.
     */
    private BasePlayer makePlayerUsingAltRules(RuleSet rules) {
	BasePlayer alt = new BasePlayer();
	for( EpisodeHistory his0: this) {
	    Episode epi0 = his0.epi;
	    Episode epi = new Episode(rules, epi0.getInitialBoard(),
				      outputMode,
				      new InputStreamReader(System.in),
				      new PrintWriter(System.out, true));
	    EpisodeHistory his = new EpisodeHistory(epi);
	    alt.add(his);
	}
	return alt;
    }

    protected String costReport() {
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

    /** Handles one candidate with proposed moves in it.
	@line the text of the response for the final request (in play
	mode) or the only request (in prepared episodes mode). Its
	content should be JSON code, as per the response schema that we have
	sent with the request.


	/// zzzz FIXME: if inferredRulesFormal has been received, need to do the following:
	* apply them to the old episodes (that is, replay the transcript of
	old episodes against the bot's inferred rules, to see if the outcome
	of any move is different from the one recorded originally)
	* compare the results on the old episodes against what the bot said they would be
	* apply them to the future episodes (that is, run's bot's proposed moves
	against the bot's inferred rules, to see (a) if the bot's proposed rules are consistent with the bot's theory... and (b) whether the bot's theory gives the same outcome as our real rules).
	
	
    */
    protected void digestFinalResponse(BasePlayer future, String line)  throws ReflectiveOperationException {
	System.out.println("Response text={" + line.trim() + "}");

	// Process JSON
	PreparedEpisodesResponse per = PreparedEpisodesResponse.parseResponse(line);
	MoveLine[][] r =  per.getMoves();
	if (r==null) { // field not supplied
	    return;
	}
	System.out.println("Found " + r.length + " proposed solutions in the response, for "+size() + " future boards");
	if (r.length != size()) throw new IllegalArgumentException("Future board count mismatch");
	
	future.digestProposedMoves(r, false);
	String irFormal = per.getInferredRulesFormal();
	if (irFormal==null) return;
	System.out.println("Gemini described inferred rules as follows:\n" + irFormal);
	RuleSet iRules = null;
	try {
	    iRules = new  RuleSet(irFormal);
	} catch( RuleParseException ex) {
	    System.out.println("Rule set cannot be parsed:" + ex);
	    ex.printStackTrace(System.out);
	    return;
	}
	System.out.println("How well do inferred rules explain training episodes?");
	int matchCnt=0, mismatchCnt=0, epiMatchCnt = 0;
	for(int j=0; j<size(); j++) {
	    Episode epi0 = get(j).epi;
	    Episode epi = new Episode(iRules, epi0.getInitialBoard(),
				      outputMode,
				      new InputStreamReader(System.in),
				      new PrintWriter(System.out, true));
	    Vector<Pick> tra0  = epi0.getTranscript();
	    boolean hasMismatch=false;
	    for(Pick pick: tra0) {
		Move move = (Move)pick;
		int[] w = {move.getPieceId(), move.getBucketNo()};
		int code = digestMoveBasic(epi, w);
		if (code == move.getCode()) {
		    matchCnt++;
		} else {
		    mismatchCnt++;
		    hasMismatch = true;
		}
	    }
	    if (!hasMismatch) {
		epiMatchCnt ++;
	    } 
	}
	System.out.println("If the training episodes had been played with inferred rules, "+matchCnt+"/" +(matchCnt+mismatchCnt)+ " moves would have had the same outcome, and " + epiMatchCnt +"/" + size()+ " episodes would have been identical");
	// zzzz
	System.out.println("How well do inferred rules explain the bot's solutions for test episodes?");
	BasePlayer altFuture = future.makePlayerUsingAltRules(iRules);
	altFuture.digestProposedMoves(r, true);
    }

    /**
       	This is called on the 
	"future" object (which contains the future boards, AKA prepared episodes).
	@param ir Running with inferred rules, rather than with the original hidden rules
	
    */
    private void digestProposedMoves(MoveLine[][] r, boolean ir) {

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
		int code = digestMoveBasic(epi, w);
		Vector<Pick> moves = epi.getTranscript();
		if (!moves.isEmpty()) {
		    // Normally, n is positive, but if the first move was invalid
		    // (code=-10), then it wasn't added to moves[]	
		    boolean redundant = get(j).repeats.add((Move)moves.lastElement(), code);
		}

		nAttempts ++;
		if (code == Episode.CODE.ACCEPT) nGoodMoves ++;
	    }	   
	    
	    String msg = "Future board "+j+ " of "+r.length+": Result of proposed moves";
	    if (ir) msg += " applied to inferred rules";
	    msg += ": cleared=" + epi.getCleared() + ", good moves count=" + epi.getDoneMoveCnt() + "/" + r[j].length;
	    System.out.println(msg);
	    if ( epi.getCleared() ) nc++;
	
	    log.logEpisode(epi, j);
	
		
	}
    
	String msg = ir?
	    "Proposed moves applied to inferred rules: overall, cleared boards" :
	    "Overall, cleared boards";
	msg += ": " + nc + "/" + r.length +", good moves: " + nGoodMoves + "/" + nAttempts;
	System.out.println(msg);
    }



    /**@param w {pieceId, bucketId}
       
       @return null if the episode needs to continue; a boolean value, to be 
	returned by playingLoop(), is the episode ends now */
    protected int digestMoveBasic(Episode epi, int[] w) {
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

	return code;
    }

    /** @return null if the episode needs to continue; a boolean value (true
	on meeting the mastery criterion, false on reaching max count), to be 
	returned by playingLoop(), is the episodes needs to end now */
    protected Boolean digestMove(int[] w)// throws IOException
    {
	
	EpisodeHistory ehi = lastElement();
	Episode epi = ehi.epi;
	
	int code = digestMoveBasic(epi, w);
	Vector<Pick> moves = epi.getTranscript();
	if (!moves.isEmpty()) {
	    // Normally, n is positive, but if the first move was invalid
	    // (code=-10), then it wasn't added to moves[]	
	    boolean redundant = ehi.repeats.add((Move)moves.lastElement(), code);
	}
    
	
	//final int n = moves.size();
	
	boolean redundant = ehi.repeats.lastAddResult;

	
	String stats = "This episode has "+epi.getTranscript().size()+" moves. Board pop="+epi.getValues().size()+". lastStretch=" + lastStretch + ", lastR=" + lastR;
	
	System.out.println(stats);
    
	if (remind && ehi.repeats.totalRepeats()>0) {
	    String stats1 = "The episode includes " + ehi.repeats.totalRepeats() + " redundant moves";
	    if (redundant) stats1 += ", including the last one.";
	    System.out.println(stats1);
	}
	

	boolean won = false;
	if (targetStreak>0 & lastStretch>=targetStreak) won = true;
	if (targetR>0 & lastR>=targetR) won = true;
	

	
	if (won) {
	    int sumLen = 0;
	    for(EpisodeHistory eh: this) sumLen += eh.epi.getTranscript().size();
	    stats =  "All "+size()+" episodes have "+sumLen+" moves. lastStretch=" + lastStretch + ", lastR=" + lastR;

	    System.out.println("Victory: mastery demonstrated! " + stats);
	    return true;
	}
    

	if (max_requests > 0 && requestCnt >= max_requests) {
	    System.out.println("Request limit (" + max_requests+") reached");
	    return false;
	}
	return null;
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
	//	return result.toArray(new MoveLine[0]);
	return (result.size()==0)? new MoveLine[0]: new MoveLine[] { result.lastElement() };
    }

        int[] listAllowedBuckets(Piece p, 	    RecentKnowledge rk) {
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
	return  Util.listBits(q);
    }

    
    /** Creates an episode of (e.g.) random moves and adds them to the history
     */
    Episode mkPreparedEpisode(GameGenerator gg,  RandomRG random) {
	Game game = gg.nextGame();
	Episode epi = new Episode(game, outputMode,
				  new InputStreamReader(System.in),
				  new PrintWriter(System.out, true));
	epi.setShowAllMovables(false);

	while( !epi.getCleared()) {

	    RecentKnowledge rk = new RecentKnowledge(epi.getTranscript(), false);

	    //System.out.println("rk=" + rk);
	    Board b = epi.getCurrentBoard(false);
	    Piece p = rk.chooseOnePiece( random, b);
	    if (p==null) throw new IllegalArgumentException("No movable pieces left; stalemate?");
	    
	    int[] allowedBuckets = listAllowedBuckets( p,rk);
	    //System.out.println("For piece " + p.getId() + ", allowed buckets are " + Util.joinNonBlank(", ", allowedBuckets));

	    if (prepareMode==PrepareMode.random) {
		
		int k = random.nextInt(allowedBuckets.length);
		int bucketNo = allowedBuckets[k];
		int[] w = {(int)p.getId(), bucketNo};
		int code = digestMoveBasic(epi, w);
		//if (code == CODE.ACCEPT) continue;
	    } else if (prepareMode==PrepareMode.positive) {
		int [] w = new int[2];
		int cnt = 0;
		while(true) {
		    if (cnt++ >1000) {
			System.out.println("Cannot find a good move!");
			System.exit(1);
		    }
		    int k = random.nextInt(allowedBuckets.length);
		    int bucketNo = allowedBuckets[k];
		    w[0] = (int)p.getId();
		    w[1] = bucketNo;
		    Pick move= epi.form2(w[0], w[1]);
		    //System.out.println("Try moving " + w[0] + " to " + w[1]);
		    if (epi.acceptPreview(move)== CODE.ACCEPT) break;
		    p = rk.chooseOnePiece( random, b);
		    allowedBuckets = listAllowedBuckets( p,rk);
		} 
		
		int code = digestMoveBasic(epi, w);
	    } else if (prepareMode==PrepareMode.negative1) {
		int [] w = new int[2];
		int k = random.nextInt(allowedBuckets.length);
		int bucketNo = allowedBuckets[k];
		w[0] = (int)p.getId();
		w[1] = bucketNo;
		Pick move= epi.form2(w[0], w[1]);
		//System.out.println("Try moving " + w[0] + " to " + w[1]);
		// In negative1, the record of the episode ends before the first good move
		if (epi.acceptPreview(move)== CODE.ACCEPT) break;
		int code = digestMoveBasic(epi, w);
	
	    } else 	    if (prepareMode==PrepareMode.orderly) {
		for(int bucketNo: allowedBuckets) {
		    int[] w = {(int)p.getId(), bucketNo};
		    int code = digestMoveBasic(epi, w);
		    if (code == CODE.ACCEPT) break;		    
		}
	    } else throw new IllegalArgumentException("Wrong mode: " +prepareMode);	    	    
	}
	return epi;	
    }

    /** Creates an episode based on a human player's transcript and adds it to the history
	@param allowedMoves. This must always be supplied, and non-negative.
     */
    Episode mkRestoredEpisode(GameGenerator gg0,
			      RandomRG random,
			      Board initialBoard, TranscriptManager.ReadTranscriptData.Entry[] oldTranscript,
			      int allowedMoves) 	 {
	RuleSet rules = gg0.getRules();
	Game game = new Game(rules, initialBoard);
	
	Episode epi = new Episode(game, outputMode,
				  new InputStreamReader(System.in),
				  new PrintWriter(System.out, true));
	// FIXME: if this was a human player transcript, and the para set mandated "fixed" mode, this would be wrong
	epi.setShowAllMovables(false);

	for(int j=0; j<oldTranscript.length && allowedMoves>0; j++) {
	    Pick pick  = oldTranscript[j].pick;
	    if (!(pick instanceof Move) && pick.getCode()==CODE.ACCEPT) {
		// Our system instructions have not told the bot about successful
		// picks, so let's just skip them
		System.out.println("Ignore successful pick in transcript, j="+j);
		continue;
	    }

	    int bucketNo = (pick instanceof Move)? ((Move)pick).getBucketNo(): 0;
	    int[] w = {(int)pick.getPieceId(), bucketNo};
	    
	    int code = digestMoveBasic(epi, w);	    
	    if (code != pick.getCode())  throw new IllegalArgumentException("Code mismatch: Stored move=" + pick +", code="+ pick.getCode()+"; replay gives code=" + code);
	    if (!(pick instanceof Move) && code!=CODE.IMMOVABLE) throw new IllegalArgumentException("The old transcript contains a pick, but the code is not IMMOVABLE. Cannot handle this");
	    allowedMoves --;
	}
	     
	return epi;	
    }

    /** Preprocessing of "good learners'" transcripts before feeding them to the bot,
	as per Paul's request. This only should be called when */
    static protected void removeFinalWinningStreak(Vector<ReadTranscriptData.Entry> section) {
	int n0 = section.size();

	// remove the final winning streak, except for its first element, as per PBK's request
	int lastFail = 0;
	for(int k=0; k<  section.size(); k++) {
	    Pick move = section.get(k).pick;
	    if (move.getCode()!=CODE.ACCEPT) lastFail = k;
	}

	// j will point to the last element to keep, which should be the
	// first successful move after the last fail. (May need to ignore
	// any successful picks in between)
	int j = lastFail;
	if (j+1 < section.size()) j++; // keep one good move
	
	while( j+1<section.size() &&  !(section.get(j).pick instanceof Move))  j++;
	
	int n = j+1;
	section.setSize(n);
	System.out.println("Shortened the series transcript from "+n0+" to " + n + " moves and/or picks");
    }

    /** Describes the boards for the bot to solve. Used in
	prepared-episodes runs
     */
    protected Vector<String> describeFutureEpisodes() {
	Vector<String> v = new Vector<>();
	
	if (size()==0) throw new IllegalArgumentException("No episode exists yet. What to ask?");

	for(int j=0; j<size(); j++) {
	    v.add( "The initial board for future episode No. " + (j+1)+ ":");
	    EpisodeHistory ehi = get(j);
	    v.add( ehi.initialBoardAsString());
	}
	return v;
    }

    /** Out model is gemini-2.0-flash, which allows 15 RPM in the free tier.
    https://ai.google.dev/gemini-api/docs/rate-limits
    */
    protected void waitABit(long msec) {
    
	try {
            Thread.sleep(msec); 
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected static int computeWait(int retryCnt) {
	int waitSec = 120;
	for(int i=0; i<retryCnt; i++) waitSec*=2;
	return waitSec;
    }

    /** Reads the contents of one or several files and concatenates them.
       @param fileList "f.txt", or maybe "f1.txt:f2.txt:...."
     */
    protected static String readInstructions(String fileList) throws IOException {
	String names [] = fileList.split(":");
	Vector<String> v = new Vector<>();
	for(String name: names) {
	    File f = new File(name);
	    v.add( Util.readTextFile( f));
	}
	return Util.joinNonBlank("\n---\n", v);
    }

    
    
}
