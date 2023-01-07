package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import java.text.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.json.*;

import javax.persistence.*;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.formatter.*;
import edu.wisc.game.math.*;
import edu.wisc.game.tools.MwByHuman;
//import edu.wisc.game.sql.MlcLog.LogFormat;


/** Tools for the manager dashboard. Essentially, a web interface for what MwByHumans command-line tool does.
 */

@Path("/ManagerDashboardService") 
public class ManagerDashboardService {
    private static HTMLFmter  fm = new HTMLFmter();

    @GET
    @Path("/compareRulesForHumans")
    @Produces(MediaType.TEXT_HTML)
    public String summary(@QueryParam("exp") List<String> _plans,
			  @DefaultValue("10") @QueryParam("targetStreak") int targetStreak,

			  @DefaultValue("naive") @QueryParam("prec") String precString
			  
			  ) {
	String title="", body="", errmsg = null;
	EntityManager em=null;

	try {
	    Vector<String> plans = new Vector<>();
	    plans.addAll(_plans);
	    Vector<String> pids = new Vector<>();
	    Vector<String> nicknames = new Vector<>();
	    Vector<Long> uids = new Vector<>();

	    title = "Comparing rule sets w.r.t. their difficulty for human players";

	    body += fm.para("Taking into account players assigned to the following experiment plans: " + fm.tt( String.join(", " , plans)));
		
	    body +=  MwByHuman.process(plans, pids, nicknames, uids, targetStreak, null, fm);


	    
	    //if (rule==null || rule.trim().equals(""))  {
	    //	throw new IllegalInputException("No rule parameter in the form.");		
	    //}

	    /*
	    em = Main.getNewEM();

	    Query q = em.createQuery("select m from MlcEntry m where m.nickname=:n and m.ruleSetName=:r");
	    q.setParameter("n", nickname);
	    q.setParameter("r", rule);
	    List<MlcEntry> res = (List<MlcEntry>)q.getResultList();

	    title = "Submitted results for algorithm " + nickname +
		" on rule set " + rule;
	    
	    body += fm.h2("Summary of " + res.size() + " runs by "+fm.tt(nickname)+" on rule set " +
			  fm.tt(rule));
	    body += summaryTable(res);
	    */
	    
	} catch(Exception ex) {
	    title = "Error";
	    body = fm.para(ex.toString());
	    ex.printStackTrace(System.err);
	} finally {	
	    if (em!=null) try {
		    em.close();
		} catch(Exception ex) {}
	}

	return fm.html(title, body);		
 
    }

}
