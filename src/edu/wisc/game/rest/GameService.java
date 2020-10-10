package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import javax.json.*;

// For database work
import javax.persistence.*;


import javax.ws.rs.*;
import javax.ws.rs.core.*;
// test
import javax.ws.rs.core.MediaType;

import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;


/** The "First Batch" of API calls, primarily for use in the research environment, where a player can choose any game to play */
@Path("/GameService") 
public class GameService {  

    @GET 
    @Path("/pieceX") 
    @Produces(MediaType.APPLICATION_XML) 
    public Piece getPiece1(){
	Piece p = new  Piece( Piece.Shape.SQUARE, Piece.Color.BLACK, 0,0);
	return p;	
    }

    @GET 
    @Path("/piece") 
    @Produces(MediaType.APPLICATION_JSON) 
    public Piece getPiece2(){
	Piece p = new  Piece( Piece.Shape.SQUARE, Piece.Color.BLACK, 0,0);
	return p;	
    }

    @GET 
    @Path("/boardX") 
    @Produces(MediaType.APPLICATION_XML) 
    public Board getBoard1(){
	return new Board();	
    }
    
    @GET 
    @Path("/board") 
    @Produces(MediaType.APPLICATION_JSON) 
    public Board getBoard2(){
	return new Board();	
    }
    
    /** Prints 
	Hello: edu.wisc.game.sql.Board@3692d23f
    */
     @GET 
     @Path("/hello1") 
     @Produces(MediaType.APPLICATION_JSON) 
     public String getHello1() {
	 Board b = new Board();
	 return "Hello: " + b.toString();	
     }
    
    @GET 
    @Path("/hello2") 
    @Produces(MediaType.APPLICATION_JSON) 
    public String getHello2() {
	Board b = new Board();


	JsonObject jo = JsonReflect.reflectToJSONObject(b, true);
	return "Hello: " + jo.toString();	

      
      //	MediaType m = MediaType.APPLICATION_JSON_TYPE;
      //	Entity<Board> e	= Entity.<Board>entity(b, m);

	// Prints:
	// Hello: Entity{entity=edu.wisc.game.sql.Board@3ea536b6, variant=Variant[mediaType=application/json, language=null, encoding=null], annotations=[]}
	//return "Hello: " + e.toString();	
   }


    /** Gets a Board object by ID
       @return The matching Board object, or null if none found
     */
    @POST 
    @Path("/getBoard") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON) 
    public Board getBoard(@FormParam("id") String id) {
	EntityManager em = Main.getEM();
	try {
	    Board b = (Board)em.find(Board.class, id);
	    if (b==null) {
		// FIXME: must return an error message of some kind...
		return null;
	    } else {
		return b;
	    }
	} finally {
	    //em.close();
	}
   }

   /** 

     */
    @POST 
    @Path("/saveBoard") 
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Board saveBoard( Board b) {

	System.out.println("saveBoard(" + b.getName()+")" );
	b.persistNewBoard();
		
	return b;
    }

    @POST 
    @Path("/writeFile")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public FileWriteReport writeFile(@FormParam("dir") String dir,
				     @FormParam("file") String file,
				     @FormParam("append") String appendString,
				     @FormParam("data") String data) {
	try {

	    boolean append = (appendString!=null) && appendString.trim().equalsIgnoreCase("true");
	    
	    if (file==null) throw new IOException("File name not specified");
	    file = file.trim();
	    if (file.length()==0) throw new IOException("Empty file name");
	    if (data==null) throw new IOException("No data supplied");
	    
	    File base = new File("/opt/tomcat/saved");
	    String [] pp = (dir==null)? new String[0]: dir.trim().split("/");
	    File d = base;
	    if (pp.length>0) {
		for(String p:pp) {
		    if (p.length()==0) continue;
		    if (p.startsWith(".") || p.indexOf("..")>=0) throw new IOException("Invalid path: " + dir);
		    d = new File(d, p);
		}
	    }
	    if (d.exists()) {
		if (!d.isDirectory() || !d.canWrite())  throw new IOException("Not a writeable directory: " + d);
	    } else {
		if (!d.mkdirs())  throw new IOException("Failed to create directory: " + d);
	    }
	    File f= new File(d, file);	    
	    if (f.exists() && !append) throw new IOException("File already exists, and append="+append+": " + f);
	    PrintWriter w = new PrintWriter(new FileWriter(f, append));
	    w.print(data);
	    w.close();	    
	    return  new	FileWriteReport(f, f.length());
	} catch(IOException ex) {
	    FileWriteReport r = new	    FileWriteReport();
	    r.setError(true);
	    r.setErrmsg(ex.getMessage());
	    return r;
	}

    }

    /** Gets the entire parameter set, identified by name */
    @GET 
    @Path("/getParaSet") 
    @Produces(MediaType.APPLICATION_JSON)
    public ParaSet getParam(@QueryParam("name") String name){
	return new ParaSet(name);
    }
    
    @POST
    @Path("/newEpisode") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public NewEpisodeWrapper
	newEpisode(@FormParam("rules") String rules,
		   @DefaultValue("0") @FormParam("pieces") int pieces,
		   @DefaultValue("0") @FormParam("shapes") int shapes,
		   @DefaultValue("0") @FormParam("colors") int colors,
		   @DefaultValue("null") @FormParam("board") String boardName) {
	return new NewEpisodeWrapper(rules, pieces, shapes, colors, boardName);
    }

    @GET
    @Path("/display") 
    @Produces(MediaType.APPLICATION_JSON)
    public Episode.Display display(@QueryParam("episode") String episodeId)   {
	Episode epi = EpisodeInfo.locateEpisode(episodeId);
	if (epi==null) return dummyEpisode.new Display(Episode.CODE.NO_SUCH_EPISODE, "# Invalid episode ID");
	return epi.new Display(Episode.CODE.JUST_A_DISPLAY, "Display requested");
    }

    
    @POST
    @Path("/move") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Episode.Display move(@FormParam("episode") String episodeId,
				@FormParam("x") int x,
				@FormParam("y") int y,
				@FormParam("bx") int bx,
				@FormParam("by") int by,
				@FormParam("cnt") int cnt   )   {
	Episode epi = EpisodeInfo.locateEpisode(episodeId);
	if (epi==null) return dummyEpisode.new Display(Episode.CODE.NO_SUCH_EPISODE, "# Invalid episode ID");
	try {	    
	    return epi.doMove(y,x,by,bx, cnt);
	} catch(IOException ex) {
	    return epi.new Display(Episode.CODE.INVALID_ARGUMENTS, ex.getMessage());
	}
    }

    Episode dummyEpisode = new Episode();

    @POST
    @Path("/startTrial") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public PlayerResponse startTrial(@FormParam("playerId") String playerId,
				 @DefaultValue("null") @FormParam("exp") String exp){
	return new PlayerResponse( playerId, exp);
    }

    
    @GET
    @Path("/listRules")
    @Produces(MediaType.APPLICATION_JSON)
    public Vector<String> 	listRules() {
	try {
	    return Files.listInputs("rules", ".txt");
	} catch(IOException ex) {
	    return null;
	}
    }

 
    @GET
    @Path("/listInitialBoards")
    @Produces(MediaType.APPLICATION_JSON)
    public Vector<String> 	listInitialBoards() {
	try {
	    return Files.listInputs("boards", ".json");
	} catch(IOException ex) {
	    return null;
	}
    }

    
    /**------------------- Just testing ------------------------*/
    /** Gets the entire parameter set, identified by name */
    @GET 
    @Path("/getParaSetHtml") 
    @Produces(MediaType.TEXT_HTML)
    public String getParamHtml(@QueryParam("name") String name){
	//	ParaSet para =	   new ParaSet(name);
	ParaSet para =	   getParam(name);
	String s = "Para:<br><em>" + para  + "</em>";
	s = "<html><body>"  + s + "</body></html>";
	return s;
    }
  
  
}
