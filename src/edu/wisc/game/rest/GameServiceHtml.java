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

/** The HTML wrapper for the First Batch calls, to allow for the "HTML Play".
 */

@Path("/GameServiceHtml") 
public class GameServiceHtml extends GameService {
    private static HTMLFmter  fm = new HTMLFmter(null);

    @POST
    @Path("/newEpisodeHtml") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public String
	newEpisodeHtml(@FormParam("rules") String rules,
		   @DefaultValue("0") @FormParam("pieces") int pieces,
		   @DefaultValue("0") @FormParam("shapes") int shapes,
		   @DefaultValue("0") @FormParam("colors") int colors,
		   @DefaultValue("null") @FormParam("board") String boardName) {

	final boolean recent = false;
	
	String body = "";
	
	NewEpisodeWrapper w = new NewEpisodeWrapper(rules, pieces, shapes, colors, boardName);
	
	String episodeId = w.getEpisodeId();

	String head= episodeId +" : "+ (recent? "mostRecentEpisode":"newEpisode");

	body +=  fm.h3( "The server response")+fm.para(  ""+JsonReflect.reflectToJSONObject(w, true));
	body += fm.hr();

	Episode epi = EpisodeInfo.locateEpisode(episodeId);

	if (w.getError()) {
	    body += fm.para("Error: " + w.getErrmsg());
	} else {	
	    body += moveForm(w.getDisplay(),  episodeId);
	}

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
	body += fm.h3("Server esponse") + fm.para(  ""+JsonReflect.reflectToJSONObject(d, true));
	body += fm.hr();

	body += moveForm(d,  episodeId);
	return fm.html(head, body);	
  }

    static private String showHistoryAndPosition(Episode.Display d, Episode epi) {
	String body = "";
	if  (epi==null) {
	    body += fm.para("No episode in memory");
	} else {
	    body += fm.h3("The rule set");
	    RuleSet.ReportedSrc rs = d.getRulesSrc();
	    Vector<String> orders = rs.getOrders();
	    Vector<String> rows = rs.getRows();
	    body += fm.h4("" + orders.size() + " orders");
	    if (orders.size()>0) {
		body += fm.wrap("ol", "\n<li>" + String.join("\n<li>", orders));
	    }
	    Vector<String> v = new Vector<String>();
	    for(int j=0; j<rows.size(); j++) {
		String s = rows.get(j);
		if (j==d.getRuleLineNo()) {
		    s += fm.br() + fm.em("[Current counters: " + d.getExplainCounters()+"]");
		    s= fm.colored("red", s);
		}
		v.add(fm.wrap("li", s));
	    }	    
	    body += fm.h4("" + rows.size() + " rule lines") + fm.wrap("ol", String.join("\n", v));
	    body += fm.hr();
	    body += fm.h3("The current board position") +fm.pre( epi.graphicDisplay(true));
	    body += fm.para( fm.a(     "../../help-display.html", "[Help]"));
	}
	return body + fm.hr();
	
   }

    
    
    /** Generates a /moveHtml form, if the episode is not completed,
     or a /guess form if it's time for a guess */
    static private String moveForm(Episode.Display d, String  episodeId) {

	String body = "";
	Episode epi = EpisodeInfo.locateEpisode(episodeId);
	body += showHistoryAndPosition(d, epi);


	String form = "";
	if (d.getFinishCode()==Episode.FINISH_CODE.NO) {
	    form += "episode = " + fm.input("episode", episodeId) + fm.br();
	    form += "x = " + fm.input("x", null, 2) + 
		"; y = " + fm.input("y", null, 2) + fm.br();
	    form += "Bucket x = " + fm.input("bx", null, 2) + 
		"; Bucket y = " + fm.input("by", null, 2) + fm.br();
	    form += "Don't modify this field: cnt = " + fm.input("cnt", ""+d.getNumMovesMade()) + fm.br();
	    form += "<input type='submit'>";
	    form = fm.wrap("form", "method='post' action='moveHtml'", form);
	    form = fm.h3( "Your next move") + fm.para(form);
	} else {
	    form = fm.para("Game over - no move possible. The finish code is " + d.getFinishCode());
	}
	body += form + fm.hr();	
	return body;
    }


}
