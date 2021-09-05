package edu.wisc.game.saved;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.sql.*;

import edu.wisc.game.sql.Board.Pos;
import edu.wisc.game.sql.Episode.Pick;
import edu.wisc.game.sql.Episode.Move;


/** An auxiliary class for writing and reading transcript files.
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
	    if (f.length()==0) w.println("#pid,episodeId,moveNo,timestamp,y,x,by,bx,code");
	    Vector<String> v = new Vector<>();
	    int k=0;
	    for(Pick move: transcript) {
		v.clear();
		v.add(pid);
		v.add(eid);
		v.add(""+(k++));
		v.add( Episode.sdf2.format(move.time));
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
	/** Stores the content of one line read back from the transcript 
	    file */
	public static class Entry {
	    final public CsvData.BasicLineEntry csv;
	    
	    final public String pid, eid;
	    final public int k;
	    // time
	    //	    final public int qy,qx;
	    //final public boolean isMove;
	    //	    final public Integer by,bx;
	    /** Pick or move, as the case may be */
	    final public Pick pick;
	    final public int code;

	    Entry(CsvData.BasicLineEntry e) {
		csv = e;
		int j=0;
		pid = e.getCol(j++);
		eid = e.getCol(j++);
		k = e.getColInt(j++);
		String timeString = e.getCol(j++);
		int qy = e.getColInt(j++);
		int qx = e.getColInt(j++);
		Integer by = e.getColInt(j++);
		Integer bx = e.getColInt(j++);
		boolean isMove =(by!=null);
	
		Pos pos = new Pos(qx,qy);
		    pick = isMove?
		    new Move(pos, new Pos(bx, by)):
		    new Pick(pos);

		
		code = e.getColInt(j++);
	    }
	}
	public ReadTranscriptData(File csvFile) throws IOException,  IllegalInputException {
	    CsvData csv = new CsvData(csvFile);
	    for(CsvData.LineEntry _e: csv.entries) {
		CsvData.BasicLineEntry e= (CsvData.BasicLineEntry )_e;
		Entry z = new Entry(e);
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

    
}
