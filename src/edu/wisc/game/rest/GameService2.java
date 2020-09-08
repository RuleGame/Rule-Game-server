package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import javax.servlet.http.HttpServletResponse;
import javax.json.*;


import javax.ws.rs.*;
import javax.ws.rs.core.*;

// For database work
import javax.persistence.*;


// test
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.formatter.*;

/** The "Second Batch" of API calls, primarily for use with players constrained by an experiment plan, and playing a sequence of games as outlined in the trial list to which the player is assigned. This API is set up in accordance with Kevin Mui's request, 2020-08-17.
 */

@Path("/GameService2") 
public class GameService2 {


    @POST
    @Path("/player") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public PlayerResponse player(@FormParam("playerId") String playerId){
	return new PlayerResponse( playerId);
    }

    @POST
    @Path("/mostRecentEpisode") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public NewEpisodeWrapper2
	mostRecentEpisode(@FormParam("playerId") String playerId) {
	return new NewEpisodeWrapper2(playerId, true);
    }


    @POST
    @Path("/newEpisode") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public NewEpisodeWrapper2
	newEpisode(@FormParam("playerId") String playerId,
		   @DefaultValue("false") @FormParam("activateBonus") boolean activateBonus,
		   @DefaultValue("false") @FormParam("giveUp") boolean giveUp) {
	if (activateBonus) new  ActivateBonusWrapper(playerId);
	if (giveUp)  new GiveUpWrapper(playerId);
	return new NewEpisodeWrapper2(playerId, false);
    }

    
    @GET
    @Path("/display") 
    @Produces(MediaType.APPLICATION_JSON)
    public Episode.Display display(@QueryParam("episode") String episodeId)   {
	Episode epi = EpisodeInfo.locateEpisode(episodeId);
	if (epi==null) return dummyEpisode.new Display(Episode.CODE.NO_SUCH_EPISODE, "# Invalid episode ID");
	//return epi.new Display(Episode.CODE.JUST_A_DISPLAY, "Display requested");
	return epi.mkDisplay();
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
				    @FormParam("cnt") int cnt
				    )   {
	EpisodeInfo epi = (EpisodeInfo)EpisodeInfo.locateEpisode(episodeId);
	if (epi==null) return dummyEpisode.new Display(Episode.CODE.NO_SUCH_EPISODE, "# Invalid episode ID: "+episodeId);
	try {	    
	    return epi.doMove(y,x,by,bx, cnt);
	} catch( Exception ex) {
	    System.err.print("/move: " + ex);
	    ex.printStackTrace(System.err);
	    return epi.new Display(Episode.CODE.INVALID_ARGUMENTS, ex.getMessage());
	}
    }

    Episode dummyEpisode = new Episode();

    @POST
    @Path("/activateBonus") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public  ActivateBonusWrapper
	activateBonus(@FormParam("playerId") String playerId) {
	return new  ActivateBonusWrapper(playerId);
    }

    @POST
    @Path("/giveUp") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public  GiveUpWrapper
	activateBonus(@FormParam("playerId") String playerId,
		      @FormParam("seriesNo") int seriesNo) {
	return new  GiveUpWrapper(playerId);
    }

    
    @GET
    @Path("/debug") 
    @Produces(MediaType.APPLICATION_JSON)
    public PlayerResponse debug(@QueryParam("playerId") String playerId){
	return new PlayerResponse( playerId, true);
    }



    @POST 
    @Path("/guess")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public FileWriteReport guess(@FormParam("episode") String episodeId,
				 @FormParam("data") String text) {
	return GuessWriteReport.writeGuess( episodeId, text);
    }


    //-------------------------------
    private static HTMLFmter  fm = new HTMLFmter(null);

    @POST
    @Path("/mostRecentEpisodeHtml") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public String mostRecentEpisodeHtml(@FormParam("playerId") String playerId) {
	return newOrRecentEpisodeHtml(playerId,true, false, false);
    }

    @POST
    @Path("/newEpisodeHtml") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public String newEpisodeHtml(@FormParam("playerId") String playerId,
		   @DefaultValue("false") @FormParam("activateBonus") boolean activateBonus,
		   @DefaultValue("false") @FormParam("giveUp") boolean giveUp) {
	return newOrRecentEpisodeHtml(playerId,false, activateBonus, giveUp);
    }

    private String newOrRecentEpisodeHtml( String playerId, boolean recent,
					    boolean activateBonus, boolean giveUp) {
	String body = "";

	//String msg = "";
	if (activateBonus) {
	    ActivateBonusWrapper w=new  ActivateBonusWrapper(playerId);
	    body += fm.h4("Activate") + fm.para(""+JsonReflect.reflectToJSONObject(w, true)) + fm.hr();
	    //msg += " (Activate: " + w.getErrmsg();
	}
	if (giveUp) {
	    GiveUpWrapper w =new  GiveUpWrapper(playerId);
	    body += fm.h4("Give up") + fm.para(""+JsonReflect.reflectToJSONObject(w, true)) + fm.hr();
	    //msg += " (Activate: " + w.getErrmsg();
	}



	NewEpisodeWrapper2 w = new NewEpisodeWrapper2(playerId, recent);
	String episodeId = w.getEpisodeId();

	String head= episodeId +" : "+ (recent? "mostRecentEpisode":"newEpisode");

	body +=  fm.h4( "Response")+fm.para(  ""+JsonReflect.reflectToJSONObject(w, true));
	body += fm.hr();

	EpisodeInfo epi = (EpisodeInfo)EpisodeInfo.locateEpisode(episodeId);

	body += moveForm(w.getDisplay(),  episodeId);

	return fm.html(head, body);	
	
    }


    @POST
    @Path("/moveHtml") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public String moveHtml(@FormParam("episode") String episodeId,
				    @FormParam("x") int x,
				    @FormParam("y") int y,
				    @FormParam("bx") int bx,
				    @FormParam("by") int by,
				    @FormParam("cnt") int cnt)   {
	Episode.Display d=move(episodeId,x,y,bx,by,cnt);
	
	String head= episodeId +" : MOVE " + x + " " +y + " " + bx + " " + by;

	String body = "";
	body += fm.h4("Response") + fm.para(  ""+JsonReflect.reflectToJSONObject(d, true));
	body += fm.hr();

	body += moveForm(d,  episodeId);
	return fm.html(head, body);	
  }

    static private String showHistoryAndPosition(EpisodeInfo epi) {
	String body =
	    (epi==null) ?
	    fm.para("No episode in memory") :
	    
	    fm.h4("Player's history") +
	    fm.para("All episodes, completed and incomplete, are listed below, one series per line. The format for each episode is:<br>[EpisodeID; FC=finishCode g-if-guess-saved; MainOrBonus; moveCnt/initPieceCnt; $reward]") +
	    fm.wrap("pre",epi.getPlayer().report())+ fm.hr() +
	    fm.h4("Current position") + fm.pre( epi.graphicDisplay(true));
	return body + fm.hr();
	
   }

    
    /** Generates a /moveHtml form, if the episode is not completed,
     or a /guess form if it's time for a guess */
    static private String moveForm(Episode.Display _d, String  episodeId) {

	if (!(_d instanceof EpisodeInfo.ExtendedDisplay)) {
	    return fm.para("Cannot cast to  EpisodeInfo.ExtendedDisplay");
	}
    
	EpisodeInfo.ExtendedDisplay d = (EpisodeInfo.ExtendedDisplay)_d;

	String body = "";
	EpisodeInfo epi = (EpisodeInfo)EpisodeInfo.locateEpisode(episodeId);
	body += showHistoryAndPosition(epi);


	String form = "";
	if (d.getFinishCode()==Episode.FINISH_CODE.NO) {
	    form += "episode = " + fm.input("episode", episodeId) + fm.br();
	    form += "x = " + fm.input("x") + fm.br();
	    form += "y = " + fm.input("y") + fm.br();
	    form += "Bucket x = " + fm.input("bx") + fm.br();
	    form += "Bucket y = " + fm.input("by") + fm.br();
	    form += "cnt = " + fm.input("cnt", ""+d.getNumMovesMade()) + fm.br();
	    form += "<input type='submit'>";
	    form = fm.wrap("form", "method='post' action='moveHtml'", form);
	    form = fm.h4( "Your next move") + fm.para(form);
	} else {
	    form = fm.para("Game over - no move possible");
	}
	body += form + fm.hr();

	if (d.getFinishCode()==Episode.FINISH_CODE.FINISH && !d.getGuessSaved()) {
	    form = "";
	    form += "episode = " + fm.input("episode", episodeId) + fm.br();
	    form += "Enter your guess: " + fm.input("data") + fm.br();
	    form += "<input type='submit'>";	    
	    form =  fm.wrap("form", "method='post' action='guessHtml'", form);
	    form = fm.h4("Your guess") + fm.para(form);
	    body += form + fm.hr();
	}
	
	
	return body;
    }


    @POST 
    @Path("/guessHtml")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public String guessHtml(@FormParam("episode") String episodeId,
				 @FormParam("data") String guessText) {
	FileWriteReport _r = GuessWriteReport.writeGuess( episodeId, guessText);

	EpisodeInfo epi = (EpisodeInfo)EpisodeInfo.locateEpisode(episodeId);
	PlayerInfo x = epi.getPlayer();
	
	String head= episodeId +" : Guess";

	String body = "";
	body += fm.h4("Response") + fm.para(  ""+JsonReflect.reflectToJSONObject(_r, true));
	body += fm.hr();
	body += fm.h4("What the player can do next");
	
	if (_r instanceof GuessWriteReport && epi!=null) {
	    GuessWriteReport r = (GuessWriteReport)_r;
	    PlayerInfo.TransitionMap map = r.getTransitionMap();	    

	    Vector<String> rows= new Vector<>();

	    for(PlayerInfo.Transition key: map.keySet()) {
		PlayerInfo.Action val =map.get(key);
		Vector<String> v = new Vector<>();
		v.add(""+key);
		v.add(""+val);

		if (key==PlayerInfo.Transition.END && val==PlayerInfo.Action.DEFAULT) {
		    v.add( "All series have been completed. Goodbye!");
		    v.add("");
		} else {
		    String action="newEpisodeHtml";
		    String form =  "<form method='post' action='"+action+"'>\n";
		    form +=  fm.hidden("playerId", x.getPlayerId());
    
		    if (val==PlayerInfo.Action.ACTIVATE) {
			form += fm.hidden("activateBonus", "true");
		    } else if (val==PlayerInfo.Action.GIVE_UP) {
			form += fm.hidden("giveUp", "true");
		    } 

		    String text =
			(key==PlayerInfo.Transition.MAIN) ? "Next episode (non-bonus)":
			(key==PlayerInfo.Transition.BONUS)?
			(val==PlayerInfo.Action.ACTIVATE? "Activate Bonus":
			 "Next bonus episode"):
			(key==PlayerInfo.Transition.NEXT)?
			(val==PlayerInfo.Action.DEFAULT? "Start the next series":
			 "Give up on this series and start the next"):
			(key==PlayerInfo.Transition.END)?
		    	(val==PlayerInfo.Action.DEFAULT? "THE END":
			 "Give up on this (last) series and end the expriment"):
			"Error - unknown action";
		
		    form += "<input type='submit'></form>\n";
		    v.add(text);
		    v.add(form);
		}
		rows.add(fm.row(v));
	    }
	    body += fm.wrap("table", "border=1", String.join("\n",rows)) +fm.br();

	}
	return fm.html(head, body);	

    }

    private String transitionButton(String action, String episodeId, String text) {
	return "<form method='post' action='"+action+"'><strong>"+text+"</strong><input type='submit'></form>";
    }
    
}
