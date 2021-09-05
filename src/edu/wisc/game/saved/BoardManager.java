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
	    if (f.length()==0) w.println("#playerId,episodeId,y,x,shape,color,objectType");
	    Vector<String> v = new Vector<>();
	    for(Piece p: b.getValue()) {
		v.clear();
		v.add(pid);
		v.add(eid);
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
New:
//#playerId,episodeId,y,x,shape,color,objectType
//vm-2021-08-29-b,20210829-224113-YWLKSX,1,2,STAR,RED,RED_STAR

     */
    public static HashMap<String,Board> readBoardFile(File f,
						      HashMap <String,Boolean> useImagesTable)
	throws IOException,  IllegalInputException{
	HashMap<String,Board> h =new HashMap<>();
	CsvData csv = new CsvData(f);
	Board b = null;
	String lastEid =null;
	for(CsvData.LineEntry _e: csv.entries) {
	    CsvData.BasicLineEntry e= (CsvData.BasicLineEntry )_e;
	    //Entry z = new Entry(e);
	    String episodeId = e.getCol(1);
	    boolean useImages =  useImagesTable.get(episodeId);

	    if (!episodeId.equals(lastEid)) {
		if (b!=null) {
		    h.put(lastEid,b);
		}
		b = new Board();
		lastEid=episodeId;
	    }


	    
	    int y =  e.getColInt(2);
	    int x =  e.getColInt(3);

	    Piece p;
	    if (useImages) {
		if (e.nCol()<7) throw new IllegalArgumentException("The board file " + f + " seem to lack the ObjectType column");
		String image = e.getCol(6);
		p = new Piece(image, x,y);
	    } else {
		Piece.Shape shape = Piece.Shape.findShape(e.getCol(4));
		Piece.Color color = Piece.Color.findColor(e.getCol(5));
		p = new Piece(shape, color, x,y);
	    }
	    b.addPiece(p);
	}


	if (b!=null) {
	    h.put(lastEid,b);
	    b = new Board();
	}

	
	return h;
    }
    

    
}
