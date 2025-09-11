package edu.wisc.game.saved;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.tools.EpisodeHandle;
import edu.wisc.game.tools.AnalyzeTranscripts.TrialListMap;

import edu.wisc.game.sql.Board.Pos;
import edu.wisc.game.sql.Episode.Pick;
import edu.wisc.game.sql.Episode.Move;
import edu.wisc.game.sql.Episode.CODE;


/** An auxiliary class for writing and reading transcript files. It is used
    both in the server in real time, and during later post factum analysis.
*/
public class TranscriptManager {

   /** Let's just write one file at a time */
    static final String file_writing_lock = "Transcript file writing lock";
	

    /* Saves all the recorded moves (the transcript of the episode) into a CSV file.

       <pre>
       transcripts/pid.transcript.csv
      pid,episodeId,moveNo,y,x,by,bx,code[,followed]
</pre>

       @param includeFollowHeader Include the "followed" column (although
       it may contain blank for this particular episode, if that episode 
       did not have bot assist)
       @param includeFollowValues Actually put the values into the "followed" column 

    */    
    public static void saveTranscriptToFile(String pid, String eid, File f,     Vector<Pick> transcript, boolean includeFollowHeader, boolean includeFollowValues) {
	synchronized(file_writing_lock) {
	try {	    
	    PrintWriter w = new PrintWriter(new	FileWriter(f, true));
	    if (f.length()==0) {
		String header = "#pid,episodeId,moveNo,timestamp,mover,objectId,y,x,by,bx,code";
		if (includeFollowHeader) header += ",followed";
		w.println(header);
	    }
	    Vector<String> v = new Vector<>();
	    int k=0;
	    for(Pick move: transcript) {
		String s = move2line(pid, eid, k++, move, includeFollowHeader,
				     includeFollowValues    );
		w.println(s);
	    }
	    w.close();
	} catch(IOException ex) {
	    System.err.println("Error writing the transcript: " + ex);
	    ex.printStackTrace(System.err);
	}	    
	}  
    }
    
    /*
    playerId -- I can put here the same value that appears in the "nickname" field of CGS logs (supplied in the log.nickname option)
trialListId -- in those runs when the CGS is actually given the name of a trial list file as an argument, I can print that; otherwise, blank
seriesNo --  in those runs when the CGS is actually given the name of a trial list file and a row number as an argument, I can print the row number; otherwise, blank. (Christo, would you prefer 0?)
ruleId -- in those runs when the CGS is actually given the name of a trial list file and a row number as an argument, the rule name comes from the parameter set in the trial list file. In other runs, that can be inferred from the rule-set-file command line argument; however, I probably will just use the file name without the full path relative to the game-data/rules directory (e.g. just "ccw" rather than "FDCL/basic/ccw"), because the full name may not always be provided anyway. (Christo, Let me know if this is a problem!)
episodeNo, -- same as in log files (sequentially numbered within run or within series, as applicable, starting from 0)
    */

    /** This is used when the CGS wants to create a transcript similar
	to one the game server would create for a human player. Since
	the CGS just has an Episode, not an EpisodeInfo, the missing
	info has to be supplied separately.
     */
    public class ExtraTranscriptInfo {
	public String playerId, trialListId, ruleId;
	public int seriesNo, episodeNo;

	ExtraTranscriptInfo() {}
	ExtraTranscriptInfo(EpisodeInfo ei) {
	    PlayerInfo x =  ei.getPlayer();
	    playerId = x.getPlayerId();
	    trialListId = x.getTrialListId();
	    seriesNo = ei.getSeriesNo();
	    PlayerInfo.Series ser = ei.mySeries();
	    ruleId = ser.para.getRuleSetName();
	    episodeNo = ser.episodes.indexOf(ei);
	}
    }

    /**
       @param extra If epi is an Episode, rather than EpisodeInfo, this structure should
       contain missing values.
     */
    void saveDetailedTranscriptToFile(Episode epi, ExtraTranscriptInfo extra, File f) {

       final String[] keys = 
	   { "playerId",
	     "trialListId",  // string "trial_1"
	     "seriesNo",  // 0-based
	     "ruleId", // "TD-5"
	     "episodeNo", // position of the episode in the series, 0-based
	     "episodeId",
	     "moveNo", // 0-based number of the move in the transcript
	     "timestamp", // YYYYMMDD-hhmmss.sss
	     "mover", // who made the move? 0 or 1. (Since ver 7.001)
	     "reactionTime", // (* diff ; also use e.startTime)
	     "objectType", // "yellow_circle" in GS 1&2; image.png for image-based objects in GS 3
	     "objectId", // Typically 0-based index within the episode's object list
	     "y", "x",
	     "bucketId", // 0 thru 3
	     "by", "bx",
	     "code", 
	     "objectCnt", // how many pieces are left on the board after this move
	   };

       HashMap<String, Object> h = new HashMap<>();

       if (epi instanceof EpisodeInfo) extra = new ExtraTranscriptInfo((EpisodeInfo) epi);        

       int moveNo=0;
       Date prevTime = epi.getStartTime();
       int objectCnt = epi.getNPiecesStart();
       Vector<String> lines=new  Vector<String>();
       for(Pick move: epi.getTranscript()) {
	   h.clear();
	   h.put( "playerId", extra.playerId);
	   h.put( "trialListId", extra.trialListId);
	   h.put( "seriesNo", extra.seriesNo);
	   h.put( "ruleId", extra.ruleId);
	   h.put( "episodeNo", extra.episodeNo);
	   h.put( "episodeId", epi.getEpisodeId());	   
	   h.put( "moveNo", moveNo++);
	   h.put( "timestamp", 	Episode.sdf2.format(move.time));
	   h.put( "mover", ""+move.getMover());
	   long msec = move.time.getTime() - prevTime.getTime();
	   h.put(  "reactionTime", "" + (double)msec/1000.0);
	   prevTime = move.time;
	   // can be null if the player tried to move a non-existent piece,
	   // which the GUI normally prohibits
	   Piece piece = move.getPiece(); 
	   h.put("objectType", (piece==null? "": piece.objectType()));
	   h.put("objectId",  (piece==null? "": piece.getId()));
	   Board.Pos q = new Board.Pos(move.getPos());
	   h.put("y", q.y);
	   h.put("x", q.x);

	   if (move instanceof Move) { // a real move with a destination
	       Move m = (Move)move;
	       h.put("bucketId", m.bucketNo);	   
	       Board.Pos b = Board.buckets[m.bucketNo];
	       h.put("by", b.y);
	       h.put("bx", b.x);	       
	   } else { // just a pick -- no destination
	       h.put("bucketId", "");
	       h.put("by", "");
	       h.put("bx", "");	       
	   }

	   
	   h.put("code",move.getCode());
	   if (move instanceof Move && move.getCode()==CODE.ACCEPT) 	   objectCnt--;
	   h.put("objectCnt",objectCnt);
	   Vector<String> v = new Vector<>();
	   for(String key: keys) v.add("" + h.get(key));
	   lines.add(String.join(",", v));
       }
          
       
       synchronized(file_writing_lock) {
	   try {	    
	       PrintWriter w = new PrintWriter(new	FileWriter(f, true));
	       if (f.length()==0) w.println("#" + String.join(",", keys));
	       for(String line: lines) {
		   w.println(line);
	       }
	       w.close();
	   } catch(IOException ex) {
	       System.err.println("Error writing the transcript: " + ex);
	       ex.printStackTrace(System.err);
	   }	    
       }
   }



    /** Creates one line of the transcript file */
    private static String move2line(String pid, String eid, int k, Pick move, boolean includeFollowHeader, boolean includeFollowValues) {

	Vector<String> v = new Vector<>();
	v.add(pid);
	v.add(eid);
	v.add(""+k);
	v.add( Episode.sdf2.format(move.time));
	v.add(""+move.getMover());
	v.add(""+ move.getPieceId());
	Pos q = new Pos(move.pos);
	v.add(""+q.y);
	v.add(""+q.x);
	if (move instanceof Move) { // a real move with a destination
	    Move m = (Move)move;
	    Board.Pos b = Board.buckets[m.bucketNo];
	    v.add(""+b.y);
	    v.add(""+b.x);
	} else { // just a pick -- no destination
	    v.add("");
	    v.add("");
	}
	v.add(""+move.getCode());
	if (includeFollowHeader) {
	    v.add(includeFollowValues? (move.getDidFollow()? "1":"0"): "");
	}
	return String.join(",", v);	
    }
    
    
    /** Some of the transcript data read back from a file. This is used
	when we need to read and statistically analyze old transcripts.
     */
    public static class ReadTranscriptData extends Vector<ReadTranscriptData.Entry> {

	final public CsvData.BasicLineEntry header;
	final boolean hasMover, hasObjectId;
	
	/** Stores the content of one line (representing one move/pick
	    attempt) read back from the transcript file */
	public static class Entry {
	    
	    // thru ver 6.*: "#pid,episodeId,moveNo,timestamp,y,x,by,bx,code"
	    // from ver 7.*: "#pid,episodeId,moveNo,timestamp,mover,y,x,by,bx,code"
	    // from ver 9.*: "#pid,episodeId,moveNo,timestamp,mover,objectId,y,x,by,bx,code"
	    
	    final public CsvData.BasicLineEntry csv;	    
	    
	    final public String pid, eid;
	    final public int k;
	    /** YYYYMMDD-hhmmss.sss */
	    final public String timeString;
	    /** Pick or move, as the case may be */
	    final public Pick pick;
	    /** The success code read from the transcript */
	    final public int code;
	    final public int mover;

	    /** Parses the timeString value, using the same 
		format that was used to write it */
	    public Date timestamp() {
		ParsePosition pos = new ParsePosition(0);
		return Episode.sdf2.parse(timeString, pos);
	    }
	    
	    
	    Entry(CsvData.BasicLineEntry e, boolean hasMover, boolean hasObjectId) {
		//-- the "mover" column was added in GS 7.0
		
		csv = e;
		int j=0;
		pid = e.getCol(j++);
		eid = e.getCol(j++);
		k = e.getColInt(j++);
		timeString = e.getCol(j++);
		mover = hasMover? e.getColInt(j++) : 0;
		int objectId = hasObjectId? e.getColInt(j++) : -1;
		int qy = e.getColInt(j++);
		int qx = e.getColInt(j++);
		if (objectId < 0) objectId = BoardManager.substituteObjectId(qx,qy);
		Integer by = e.getColInt(j++);
		Integer bx = e.getColInt(j++);
		boolean isMove =(by!=null);
	
		Pos pos = new Pos(qx,qy);
		pick = isMove?
		    new Move(pos, new Pos(bx, by)):
		    new Pick(pos);
		pick.setPieceId(objectId);
		
		code = e.getColInt(j++);
		pick.setCode( code);
	    }

	    /** Requires the equality of the strings in all columns */
	    public boolean equals(Object o) {
		return (o instanceof Entry) &&
		    csv.equals(((Entry)o).csv);
	    }
	    
	}
	/** Reads in the entire content of a transcript file for a player.
	    Ignores picks at empty cells, as they represent the player's
	    failing to understand the notation, or "slips of the fingers",
	    and may drive p0 calculation crazy.
	 */
	public ReadTranscriptData(File csvFile) throws IOException,  IllegalInputException {
	    CsvData csv = new CsvData(csvFile, false, false, null);
	    header = csv.header;



	    // thru ver 6.*: "#pid,episodeId,moveNo,timestamp,y,x,by,bx,code"
	    // from ver 7.*: "#pid,episodeId,moveNo,timestamp,mover,y,x,by,bx,code"
	    // from ver 9.*: "#pid,episodeId,moveNo,timestamp,mover,objectId,y,x,by,bx,code"

	    int ja=4;
	    hasMover = header.getCol(ja).equals("mover");
	    if (hasMover) 	ja++;
	    hasObjectId=header.getCol(ja).equals("objectId");
	    if (hasObjectId)		ja++;

	    if (!header.getCol(ja).equals("y")) {
		throw new IllegalInputException("Column y not found in " + csvFile);
	    }
	    
	    for(CsvData.LineEntry _e: csv.entries) {
		CsvData.BasicLineEntry e= (CsvData.BasicLineEntry )_e;
		Entry z = new Entry(e, hasMover, hasObjectId);
		// ignore picks at empty cells, as they may drive p0
		// calculation crazy
		if (z.code == Episode.CODE.EMPTY_CELL) continue;
		add(z);
	    }

	}

	/** Returns an array of 0s and 1s, for denied and accepted move
	    attempts */
	static public int[] asVectorY(Vector<ReadTranscriptData.Entry> v) {
	    int[] q = new int[v.size()];
	    for(int j=0; j<v.size(); j++) {
		q[j] = (v.get(j).code==Episode.CODE.ACCEPT)? 1:0;
	    }
	    return q;
	}
	
    }


    /** This method is used as a helper in a unit test for ReplayedEpisode;
	its purpose is to find rule set names for various episodes
	without accessing the SQL server.
	@param  detailedTranscriptsFile A detailed transcript file, from which the rule set names will be obtained

	<pre>
	% more ./detailed-transcripts/RU-FDCL-basic-auto-20241105-113759-EPVDYK.detailed-transcripts.csv 
#playerId,trialListId,seriesNo,ruleId,episodeNo,episodeId,moveNo,timestamp,reactionTime,objectType,objectId,y,x,bucketId,by,bx,code,objectCnt
RU-FDCL-basic-auto-20241105-113759-EPVDYK,basic-07-A,0,FDCL/basic/ordL1,0,20241105-113932-D9DX8Y,0,20241105-113934.266,2.149,RED_SQUARE,0,1,1,,,,7,9
</pre>
    */
    public static HashMap<String,EpisodeHandle> findRuleSetNames(String exp, TrialListMap trialListMap, File detailedTranscriptsFile) throws IOException, IllegalInputException {
	CsvData csv = new CsvData(detailedTranscriptsFile, false, false, null);
	//String header = csv.header;


	HashMap<String,EpisodeHandle> h = new HashMap<>();
	for(CsvData.LineEntry _e: csv.entries) {
	    CsvData.BasicLineEntry e= (CsvData.BasicLineEntry )_e;
	    /*
	    String tid = e.getCol(1);
	    String rid = e.getCol(3);
	    String eid = e.getCol(4);
	    h.put(rid,eid);
	    */
	    EpisodeHandle eh = new EpisodeHandle(exp, trialListMap, e);
	    h.put(eh.episodeId, eh);
	}
	return h;

    }
    
    
}
