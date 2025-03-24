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


/** An auxiliary class for writing and reading transcript files. It is used
    both in the server in real time, and during later post factum analysis.
*/
public class TranscriptManager {

   /** Let's just write one file at a time */
    static final String file_writing_lock = "Transcript file writing lock";
	

    /* Saves all the recorded moves (the transcript of the episode) into a CSV file.
       <pre>
       transcripts/pid.transcript.csv
      pid,episodeId,moveNo,y,x,by,bx,code
</pre>
    */    
    public static void saveTranscriptToFile(String pid, String eid, File f,     Vector<Pick> transcript) {
	synchronized(file_writing_lock) {
	try {	    
	    PrintWriter w = new PrintWriter(new	FileWriter(f, true));
	    if (f.length()==0) w.println("#pid,episodeId,moveNo,timestamp,mover,objectId,y,x,by,bx,code");
	    Vector<String> v = new Vector<>();
	    int k=0;
	    for(Pick move: transcript) {
		v.clear();
		v.add(pid);
		v.add(eid);
		v.add(""+(k++));
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
		w.println(String.join(",", v));
	    }
	    w.close();
	} catch(IOException ex) {
	    System.err.println("Error writing the transcript: " + ex);
	    ex.printStackTrace(System.err);
	}	    
	}  
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
	    final public String timeString;
	    /** Pick or move, as the case may be */
	    final public Pick pick;
	    /** The success code read from the transcript */
	    final public int code;
	    final public int mover;

	    
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
