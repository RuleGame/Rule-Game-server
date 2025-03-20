package edu.wisc.game.saved;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.rest.ColorMap;
import edu.wisc.game.rest.Files;

/** Reading and writing Boards to CSV files */

public class BoardManager {
        /** Let's just write one file at a time */
    static private final String file_writing_lock = "Board file writing lock";
	
    /* Saves this board in CSV file.
       <pre>
      boards/pid.board.csv
      pid,episode-id,y,x,shape,color,image
</pre>
    */    
    public static void saveToFile(Board b, String pid, String eid, File f) {
	synchronized(file_writing_lock) {
	try {	    
	    PrintWriter w = new PrintWriter(new	FileWriter(f, true));
	    if (f.length()==0) w.println("#playerId,episodeId,objectId,y,x,shape,color,objectType");
	    Vector<String> v = new Vector<>();
	    for(Piece p: b.getValue()) {
		v.clear();
		v.add(pid);
		v.add(eid);
		v.add(""+p.getId());
		v.add(""+p.getY());
		v.add(""+p.getX());
		v.add(""+p.xgetShape());
		v.add(""+p.xgetColor());
		v.add(""+p.objectType());
		w.println(String.join(",", v));
	    }
	    w.close();
	} catch(IOException ex) {
	    System.err.println("Error writing the board: " + ex);
	    ex.printStackTrace(System.err);
	}	    
	}  
    }

    /** Reads a CSV file into which a number of boards have been written by
	Board.saveToFile()

	@return A hash map that maps episode IDs to initial boards.
	
<PRE>
Old:
//#playerId,episodeId,y,x,shape,color
//vmf001,20201008-131657-06RY9E,4,1,STAR,YELLOW
New (thru ver 7.*):
//#playerId,episodeId,y,x,shape,color,objectType
//vm-2021-08-29-b,20210829-224113-YWLKSX,1,2,STAR,RED,RED_STAR

// from ver 8.0
"#playerId,episodeId,objectId,y,x,shape,color,objectType"

     */
    public static HashMap<String,Board> readBoardFile(File f,
						      HashMap <String,Boolean> useImagesTable)
	throws IOException,  IllegalInputException{
	HashMap<String,Board> h =new HashMap<>();
	//CsvData csv = new CsvData(f);
	CsvData csv = new CsvData(f, false, false, null);


	CsvData.BasicLineEntry header = csv.header;


	boolean hasObjectId = header.getCol(2).equals("objectId");
	if (header.getCol(2 + (hasObjectId?1:0)).equals("y")) throw new  IllegalInputException("Don't see the y column in its place");
	
	Board b = null;
	String lastEid =null;
	for(CsvData.LineEntry _e: csv.entries) {
	    int ja=0;

	    ja++; // playerId;
	    CsvData.BasicLineEntry e= (CsvData.BasicLineEntry )_e;
	    //Entry z = new Entry(e);
	    String episodeId = e.getCol(ja++);
	    boolean useImages =  useImagesTable.get(episodeId);

	    if (!episodeId.equals(lastEid)) {
		if (b!=null) {
		    h.put(lastEid,b);
		}
		b = new Board();
		lastEid=episodeId;
	    }

	    int objectId = hasObjectId?  e.getColInt(ja++) : -1;
	    
	    int y =  e.getColInt(ja++);
	    int x =  e.getColInt(ja++);

	    String _shape = e.getCol(ja++);
	    String _color = e.getCol(ja++);

	    Piece p;
	    if (useImages) {
		if (e.nCol()<7) throw new IllegalArgumentException("The board file " + f + " seems to lack the ObjectType column");
		String image = e.getCol(ja++);
		p = new Piece(image, x,y);
	    } else {
		Piece.Shape shape = Piece.Shape.findShape(_shape);
		Piece.Color color = Piece.Color.findColor(_color);
		p = new Piece(shape, color, x,y);
	    }


	    if (objectId < 0) objectId = substituteObjectId(x,y);
	    
	    b.addPiece(p);
	}


	if (b!=null) {
	    h.put(lastEid,b);
	    b = new Board();
	}

	
	return h;
    }
    

    /** Used when reading old (pre GS 8.*) board files. This
	will match similar substitute IDs constructed when
	reading the transcript, but will be in a mismatch
	with the true object IDs found in the detailed transcript
	file.
     */    
    static int substituteObjectId(int x, int y) {
	return y*Board.N + x;
    }
}
