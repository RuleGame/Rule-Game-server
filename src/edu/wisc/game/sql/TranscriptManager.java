package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;

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
    static void saveTranscriptToFile(String pid, String eid, File f,     Vector<Pick> transcript) {
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
		Board.Pos q = new Board.Pos(move.pos);
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
		v.add(""+move.code);
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
	    final public int qy,qx;
	    final public boolean isMove;
	    final public Integer by,bx;
	    final public int code;
	    Entry(CsvData.BasicLineEntry e) {
		csv = e;
		int j=0;
		pid = e.getCol(j++);
		eid = e.getCol(j++);
		k = e.getColInt(j++);
		String timeString = e.getCol(j++);
		qy = e.getColInt(j++);
		qx = e.getColInt(j++);
		by = e.getColInt(j++);
		bx = e.getColInt(j++);
		isMove=(by!=null);
		code = e.getColInt(j++);
	    }
	}
	public ReadTranscriptData(File csvFile) throws IOException,  IllegalInputException {
	    CsvData csv = new CsvData(csvFile);
	    for(CsvData.LineEntry _e: csv.entries) {
		CsvData.BasicLineEntry e= (CsvData.BasicLineEntry )_e;
		Entry z = new Entry(e);
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
