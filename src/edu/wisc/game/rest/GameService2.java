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
    /** @param exp The experiment plan. If not supplied (null), the experiment
	plan will be guessed from the playerId.
     */
    public PlayerResponse player(@FormParam("playerId") String playerId,
				 @DefaultValue("null") @FormParam("exp") String exp){
	return new PlayerResponse( playerId, exp);
    }

    @POST
    @Path("/mostRecentEpisode") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public NewEpisodeWrapper2
	mostRecentEpisode(@FormParam("playerId") String playerId) {
	return new NewEpisodeWrapper2(playerId, true, false, false);
    }


    @POST
    @Path("/newEpisode") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public NewEpisodeWrapper2
	newEpisode(@FormParam("playerId") String playerId,
		   @DefaultValue("false") @FormParam("activateBonus") boolean activateBonus,
		   @DefaultValue("false") @FormParam("giveUp") boolean giveUp) {
	return new NewEpisodeWrapper2(playerId, false, activateBonus, giveUp);
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
	Episode.Display rv=null;
	Episode epi = EpisodeInfo.locateEpisode(episodeId);
	if (epi==null) return dummyEpisode.new Display(Episode.CODE.NO_SUCH_EPISODE, "# Invalid episode ID: "+episodeId);
	try {	    
	    return rv=epi.doMove(y,x,by,bx, cnt);
	} catch( Exception ex) {
	    System.err.print("/move: " + ex);
	    ex.printStackTrace(System.err);
	    return rv=epi.new Display(Episode.CODE.INVALID_ARGUMENTS, ex.getMessage());
	} finally {
	    Logging.info("move(epi=" +  episodeId +", ("+x+","+y+") to ("+bx+","+by+"), cnt="+cnt+"), return " + JsonReflect.reflectToJSONObject(rv, true));
	}
    }

    @POST
    @Path("/pick") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Episode.Display move(@FormParam("episode") String episodeId,
				@FormParam("x") int x,
				@FormParam("y") int y,
				@FormParam("cnt") int cnt
				)   {
	Episode.Display rv=null;
	Episode epi = EpisodeInfo.locateEpisode(episodeId);
	if (epi==null) return dummyEpisode.new Display(Episode.CODE.NO_SUCH_EPISODE, "# Invalid episode ID: "+episodeId);
	try {	    
	    return rv=epi.doPick(y,x, cnt);
	} catch( Exception ex) {
	    System.err.print("/pick: " + ex);
	    ex.printStackTrace(System.err);
	    return rv=epi.new Display(Episode.CODE.INVALID_ARGUMENTS, ex.getMessage());
	} finally {
	    Logging.info("pick(epi=" +  episodeId +", ("+x+","+y+"), cnt="+cnt+"), return " + JsonReflect.reflectToJSONObject(rv, true));
	}
    }

    
    private static Episode dummyEpisode = new Episode();

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
	giveUp(@FormParam("playerId") String playerId,
		      @FormParam("seriesNo") int seriesNo) {
	return new  GiveUpWrapper(playerId);
    }

    
    @GET
    @Path("/debug") 
    @Produces(MediaType.APPLICATION_JSON)
    public PlayerResponse debug(@QueryParam("playerId") String playerId,
				@DefaultValue("null") @QueryParam("exp") String exp){
	return new PlayerResponse( playerId, exp, true);
    }


    /** Records a player's guess about the rules. This is typically used at the
	end of an episode.
    */
    @POST 
    @Path("/guess")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public FileWriteReport guess(@FormParam("episode") String episodeId,
				 @FormParam("data") String text,
				 @DefaultValue("-1") @FormParam("confidence") int confidence
				 ) {
	return GuessWriteReport.writeGuess( episodeId, text, confidence);
    }

    /** Returns a hash map that maps each color name (in upper case)
	to a vector of 3 integers, representing RGB values. The data 
	come from the file in game-data/colors. Note that calling this method
	causes the system to re-read the file; so the client may
	want to cache the data.
    */
    @GET
    @Path("/colorMap") 
    @Produces(MediaType.APPLICATION_JSON)
    public ColorMap  colorMap() {
	return new ColorMap();
    }

    /** Lists the names of all shapes. This can be used e.g. in a board editor. */
    @GET
    @Path("/listShapes") 
    @Produces(MediaType.APPLICATION_JSON)
    public  ListShapesWrapper listShapes() {
	return new ListShapesWrapper();
    }

    /** Reports the current version of the server */
    @GET
    @Path("/getVersion") 
    @Produces(MediaType.APPLICATION_JSON)
    public  String getVersion() {
	return Episode.version;
    }

    
}
