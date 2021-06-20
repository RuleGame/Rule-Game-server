package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.json.*;


import javax.ws.rs.*;
import javax.ws.rs.core.*;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.formatter.*;


import org.glassfish.jersey.media.multipart.BodyPartEntity;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
//import org.springframework.stereotype.Component;



/** HTML display for user-upload boards (in JSON format), for use by
    the ML team (Shubham)

    <p>
    The file upload technique is as per
    https://www.lkit.dev/jax-rs-single-file-upload-example/ , 
    using the library jersey-media-multipart-2.34.jar   from https://mvnrepository.com/artifact/org.glassfish.jersey.media/jersey-media-multipart/
    Make sure not use version 3.*, because they jave switched from "json" naming
    to "jakarta" naming.
 */

@Path("/BoardDisplayService") 
public class BoardDisplayService {
    private static HTMLFmter  fm = new HTMLFmter(null);

    @POST
    @Path("/displayBoard") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    /** @param boardJsonText a JSON element describing a board 
     */
    public String displayBoard(@FormParam("boardJson") String boardJsonText
			  ){
	String title="", body="";
	try {
	    if (boardJsonText==null || boardJsonText.trim().equals("")) {
		throw new IllegalInputException("No board description JSON supplied");		
	    }
	    
	    Board board = Board.readBoardFromString(boardJsonText);
	    String s = doBoard(board);
	    title ="Board display";
	    body += fm.para(boardJsonText);
	    body += fm.para(s);

	} catch(Exception ex) {
	    title ="Error";
	    body = ex.toString();
	}
	return fm.html(title, body);	

    }

    private String doBoard(Board board) {	    
	Piece[] pieces= board.asBoardPieces();
	boolean[] isMoveable = new boolean[Board.N*Board.N+1];	
	return Episode.doHtmlDisplay(pieces, -1, false, isMoveable);
    }
   
    @Path("/displayBoardFile")
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public String  displayBoardFile(
				    @FormDataParam("file") InputStream file,
                @FormDataParam("file") FormDataContentDisposition fileDisposition			      ) {

	String title="", body="";
	try {
	    if (file==null) {
		throw new IllegalInputException("No board description JSON supplied");		
	    }
	    
	    Board board = Board.readBoard(new InputStreamReader(file));
	    String s = doBoard(board);
	    title ="Board display";
	    //body += fm.para(boardJsonText);
	    body += fm.para(s);

	} catch(Exception ex) {
	    title ="Error";
	    body = ex.toString();
	}
	return fm.html(title, body);		
    }
    
    public static class BoardList {
	private Vector<Board> boards= new  Vector<>();
	public void addBoard(Board c) {
	    boards.add(c);
	}
	
	public Vector<Board> getBoards() { return boards; }
	//@XmlElement
	public void setBoards(Vector<Board> _boards) {
	    boards.setSize(0);
	    for(Board p: _boards) addBoard(p);
	}

	static BoardList  readBoardList(Reader r) throws IOException, ReflectiveOperationException {
	    
	//System.err.println("ReadBoard " + f);
	JsonReader jsonReader = Json.createReader(r);
	JsonObject obj = jsonReader.readObject();
	jsonReader.close();

	BoardList boardList = new BoardList();
	JsonToJava.json2java(obj, boardList);

	//System.err.println("Have imported the board:\n" + 
	// JsonReflect.reflectToJSONObject(board, true));	

	return boardList;
    }
	
    }


    @POST
    @Path("/displayBoardList") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    /** @param boardListJsonText a JSON element describing a BoardList
     */
    public String displayBoardList(@FormParam("boardListJson") String boardListJsonText
			  ){
	String title="", body="";
	try {
	    if (boardListJsonText==null || boardListJsonText.trim().equals("")) {
		throw new IllegalInputException("No board description JSON supplied");		
	    }
	    
	    BoardList boardList = BoardList.readBoardList(new StringReader(boardListJsonText));
	    title ="Board list display";
	    body += fm.para(boardListJsonText);
	    int cnt=0;
	    for(Board board: boardList.getBoards()) {
		String s = doBoard(board);
		cnt++;
		
		body += fm.para("Board no. " + cnt);
		
		body += fm.para(s);
	    }

	} catch(Exception ex) {
	    title ="Error";
	    body = ex.toString();
	}
	return fm.html(title, body);	

    }
    
}
