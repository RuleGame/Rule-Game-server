package edu.wisc.game.gemini;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.net.*;
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
	System.err.println("Captive Game Server (https://rulegame.wisc.edu/w2020/captive.html)\n");
	System.err.println("Usage:\n");
	System.err.println("  java [options]  edu.wisc.game.engine.Captive game-rule-file.txt board-file.json");
	System.err.println("  java [options]  edu.wisc.game.engine.Captive game-rule-file.txt npieces [nshapes ncolors]");
	System.err.println("  java [options]  edu.wisc.game.engine.Captive trial-list-file.csv rowNumber");
	System.err.println("  java [options]  edu.wisc.game.engine.Captive R:rule-file.txt:modifier-file.csv");
	System.err.println("Each of 'npieces', 'nshapes', and 'ncolors' is either 'n' (for a single value) or 'n1:n2' (for a range). '0' means 'any'");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }

    /**
       curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=XXXX" \
-H 'Content-Type: application/json' \
-X POST \
-d '{
  "contents": [{
    "parts":[{"text": "How much does it cost to transfer between terminals in Manila Airport?"}]
    }]
   }'

    */  

    private void doOneRequest(GeminiRequest gr) throws MalformedURLException, IOException, ProtocolException
    {
	readApiKey();
	String u = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
	u += "?key=" + gemini_api_key;
	
	URL url = new URL(u);
	HttpURLConnection con = (HttpURLConnection)url.openConnection();
	con.setRequestMethod("POST");
	con.setRequestProperty("Content-Type", "application/json");
	con.setDoOutput(true);

	JsonObject jo = JsonReflect.reflectToJSONObject(gr, false, null, 10);

	System.out.println("SENDING: " + jo.toString());
	String jsonInputString =  jo.toString();

	try(OutputStream os = con.getOutputStream()) {
	    byte[] input = jsonInputString.getBytes("utf-8");
	    os.write(input, 0, input.length);			
	}


	int code = con.getResponseCode();
	InputStream is;
    
	if (code != 200) {
	    System.out.println("Error: HTTP response code = " + code);
	    is = con.getErrorStream();
	    //return;
	} else {
	    is = con.getInputStream();
	}
	InputStreamReader isr = new InputStreamReader(is, "utf-8");
    
	try(BufferedReader br = new BufferedReader(isr)) {
	    StringBuilder response = new StringBuilder();
	    String responseLine = null;
	    while ((responseLine = br.readLine()) != null) {
		response.append(responseLine.trim());
	    }
	    System.out.println(response.toString());
	}
	
    }


    static GeminiRequest makeRequest1() {
	GeminiRequest gr = new GeminiRequest();

	gr.addInstruction("Please answer in German, if you can");

	
	gr.addUserText("How do you use borax?");
	return gr;
    }


    static String instructions=null;
    
    
    static String gemini_api_key = null;

    static void readApiKey() throws IOException {
	if ( gemini_api_key != null) return;
	String s = Util.readTextFile(new File("/opt/w2020/gemini-api-key.txt"));
	s = s.replaceAll("\\s", "");
	gemini_api_key = s;
    }

    /** Modeled on Captive.java */
    public static void main(String[] argv) throws Exception {

	Files.allowCachingAllRules(true); // for greater efficiency
	
	// The captive server does not need the master conf file in /opt/w2020
	MainConfig.setPath(null);
	// Enable the computing of feature-lists for Composite objects
	// (which is normally turned off)
	edu.wisc.game.svg.Composite.setNeedFeatures(true);
	
	ParseConfig ht = new ParseConfig();

	// allows seed=... , colors=..., condTrain=..., crowded=... etc among argv
	argv = ht.enrichFromArgv(argv);

	//System.out.println("output=" +  ht.getOption("output", null));
	OutputMode outputMode = ht.getOptionEnum(OutputMode.class, "output", OutputMode.FULL);

	String inputDir=ht.getOption("inputDir", null);
	//System.out.println("#inputDir=" + inputDir);
	if (inputDir!=null) Files.setInputDir(inputDir);

	MlcLog log=null; //Captive.mkLog(ht);		      

	GameGenerator gg=null;
	try {
	    gg = Captive.buildGameGenerator(ht, argv);
	} catch(Exception ex) {
	    usage("Cannot create game generator. Problem: " + ex.getMessage());
	}
	        	
	if (log!=null) log.rule_name = gg.getRules().getFile().getName().replaceAll("\\.txt$", "");


	int gameCnt=0;

	if (log!=null) log.open();

	GeminiPlayer history = new GeminiPlayer();
	
	while(true) {
	    Game game = gg.nextGame();
	    if (outputMode== OutputMode.FULL) System.out.println(Captive.asComment(game.rules.toString()));

	    Episode epi = new Episode(game, outputMode,
				      new InputStreamReader(System.in),
				      new PrintWriter(System.out, true));


	    EpisodeHistory his = new EpisodeHistory(epi);
	    history.add(his);
	    //System.out.println("B=" + his.initialBoardAsString());

	    
	    //----
	    /*
	    boolean z = epi.playGame(gg,gameCnt+1);
	    if (log!=null) log.logEpisode(epi, gameCnt);
	    if (!z) break;
	    */
	    gameCnt++;
	    boolean z = false;
	    if (!z) break;
	}

	if (log!=null) log.close();
  

	//---------

	System.exit(0);

	/*
	GeminiPlayer p = new GeminiPlayer();
	//	IOException,  RuleParseException, ReflectiveOperationException, IllegalInputException{
	GeminiRequest gr = makeRequest1();
	p.doOneRequest(gr);

	File f = new File( Files.geminiDir(), "system.txt");	
	instructions = Util.readTextFile( f);
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
	    // describe all previos episodes.
	    v.add("You have completed " + size() + " episodes so far. Their summary follows.");
	    for(int j=0; j<size()-1 ; j++) {
		EpisodeHistory ehi = get(j);
		v.add("Episode " + (j+1) + " had the following initial board: " +
		      ehi.initialBoardAsString());
		Vector<Pick> moves = ehi.epi.getTranscript();
		v.add("During episode "+(j+1)+", you made the following "+ moves.size() + "move attempts, with the following results:");
		for(int k=0; k<moves.size(); k++) {
		    if (!(moves.get(k) instanceof Move))  throw new IllegalArgumentException("Unexpected entry in the transcript (j=" + j+", k=" + k+", The bot is only supposed to make moves, not picks!");
		    Move move = (Move)moves.get(k);
		    v.add("Move " + (k+1) + " :  " + move);		    
		}
		    
		j++;
	    }
	    
	}
	gr.addUserText(Util.joinNonBlank("\n", v));
	return gr;
    }
    

    /** Plays the last (latest) episode of this GeminiPlayer, until it ends */
    void playingLoop() {
	EpisodeHistory ehi = lastElement();
	Episode epi = ehi.epi;
	while( !epi.isCompleted()){
	    
	}
    }
    
    /*
    static GeminiRequest makeRequestGame(Episode epi) throws IOException {
	GeminiRequest gr = new GeminiRequest();

	gr.addInstruction(instructions);

	String text = "";

	
	
	gr.addUserText("How do you use borax?");
	return gr;
    }
    */

    
    /** Lets this episode play out until either all pieces are
	cleared, or a stalemate is reached, or the player gives up
	(sends an EXIT or NEW command). The episode takes commands from
	the reader, as in the Captive Game Server.

	@param game This is passed just so that we can access the feature list for the FEATURES command
	@param gameCnt The sequential number of the current episode. This is only used in a message.
	@return true if another episode is requested, i.e. the player
	has entered a NEW command. false is returned if the player
	enters an EXIT command, or simply closes the input stream.
    */
    /*
    public static boolean playGame(Episode epi, GameGenerator gg, //Game game,
			    int gameCnt) throws IOException {

	//private final
	PrintWriter out = epi.out;
 
	try {
	    String msg = "# Hello. This is Captive Game Server for Gemini ver. "+Episode.getVersion()+". Starting a new episode (no. "+gameCnt+")";
	if (epi.stalemate) {
	    epi.respond(CODE.STALEMATE, msg + " -- immediate stalemate. Our apologies!");
	} else {
	    epi.respond(CODE.NEW_GAME, msg);
	}
	out.println(epi.displayJson());
	if (epi.outputMode==OutputMode.FULL) out.println(epi.graphicDisplay());
	
	LineNumberReader r = new LineNumberReader(epi.in);
	String line = null;
	while((line=readLine(r))!=null) {
	    line = line.trim();
	    if (line.equals("")) continue;
	    Vector<Token> tokens;
	    try {
		tokens    = Token.tokenize(line);
	    } catch(RuleParseException ex) {
		epi.respond(CODE.INVALID_COMMAND,"# Invalid input - cannot parse");
		continue; 		
	    }
	    if (tokens.size()==0 || tokens.get(0).type!=Token.Type.ID) {
		epi.respond(CODE.INVALID_COMMAND, "# Invalid input");
		continue;
	    }
	    String cmd = tokens.get(0).sVal.toUpperCase();
	    if (cmd.equals("EXIT")) {
		epi.respond(CODE.EXIT, "# Goodbye");
		return false;
	    } else if (cmd.equals("VERSION")) {
		out.println("# " + version);
	    } else if (cmd.equals("NEW")) {
		return true;
	    } else if (cmd.equals("HELP")) {		
		out.println("# Commands available:");
		out.println("# FEATURES");
		out.println("# MOVE row col bucket_row bucket_col");
		out.println("# MOVE piece_id bucket_id");
		out.println("# NEW");
		out.println("# DISPLAY");
		out.println("# DISPLAYFULL");
		out.println("# MODE <BRIEF|STANDARD|FULL>");
		out.println("# COND <train|test>");
		out.println("# EXIT");
	    } else if (cmd.equals("DISPLAY")) {
		out.println(displayJson());
		if (outputMode==OutputMode.FULL) out.println(graphicDisplay());
	    } else if (cmd.equals("FEATURES")) {
		//Map<String, Vector<Object>> features = game.getAllFeatures();
		JsonObject json = JsonReflect.reflectToJSONObject(gg.getAllFeatures(), true);
		out.println(json);
	    } else if (cmd.equals("DISPLAYFULL")) {
		out.println(displayJson());
		out.println(graphicDisplay());
	    } else if (cmd.equals("MODE")) {
		tokens.remove(0);
		if (tokens.size()!=1 || tokens.get(0).type!=Token.Type.ID) {
		    epi.respond(CODE.INVALID_ARGUMENTS, "# MODE command must be followed by the new mode value");
		    continue;
		}
		String s = tokens.get(0).sVal.toUpperCase();
		try {
		    outputMode= Enum.valueOf( OutputMode.class, s);
		} catch(IllegalArgumentException ex) {
		    epi.respond(CODE.INVALID_ARGUMENTS, "# Not a known mode: " + s);
		    continue;
		}
		out.println("# OK, mode=" + outputMode);
	    } else if (cmd.equals("COND")) {
		tokens.remove(0);		
		if (tokens.size()==0) {
		    //-- just show the current state
		} else if (tokens.size()!=1 || tokens.get(0).type!=Token.Type.ID) {
		    epi.respond(CODE.INVALID_ARGUMENTS, "# COND command must be followed by 'train' or 'test'");
		    continue;
		} else {
		    String s = tokens.get(0).sVal.toLowerCase();
		    if (s.equals("train")) {
			gg.setTesting(false);
		    } else if (s.equals("test")) {
			gg.setTesting(true);
		    } else {
			epi.respond(CODE.INVALID_ARGUMENTS, "# COND command  must be followed by 'train' or 'test'; invalid value=" + s);
		    }
		}
		out.println("# OK, cond=" + (gg.getTesting()? "test" : "train"));
	    } else if (cmd.equals("MOVE")) {
		
		tokens.remove(0);
		int q[] = new int[tokens.size()];
		if (tokens.size()!=4 && tokens.size()!=2) {
		    epi.respond(CODE.INVALID_ARGUMENTS, "# Invalid input");
		    continue;
		}

		// y x By Bx
		// pieceId bucketId
		
		boolean invalid=false;
		for(int j=0; j<q.length; j++) {
		    if (tokens.get(j).type!=Token.Type.NUMBER) {
			epi.respond(CODE.INVALID_ARGUMENTS, "# Invalid input: "+tokens.get(j));
			invalid=true;
			break;
		    }
		    q[j] = tokens.get(j).nVal;

		}
		if (invalid) continue;

		Display mr =
		    (q.length==2)?
		    doMove2(q[0], q[1],  attemptCnt):
		    doMove(q[0], q[1], q[2], q[3], attemptCnt);
		epi.respond(mr.code, "# " + mr.errmsg);
		if (outputMode!=OutputMode.BRIEF) out.println(displayJson());
		if (outputMode==OutputMode.FULL) out.println(graphicDisplay());
		
	    } else {
		epi.respond(CODE.INVALID_COMMAND, "# Invalid command: " +cmd);
		continue;
	    }
	}
	return false;
	} finally {
	    out.flush();
	}
    }
    */
    
}
