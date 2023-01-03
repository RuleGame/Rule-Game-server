package edu.wisc.game.web;

import java.io.*;
import java.util.*;
import java.text.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import javax.persistence.*;


import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.formatter.*;
import edu.wisc.game.rest.*;

public class MlcManagerDashboard  extends ResultsBase  {

    private ContextInfo ci;

    private static HTMLFmter fm = new HTMLFmter();

    public String key = "";
    public String report = "";
    public String nickname = null;
    
    public MlcManagerDashboard(HttpServletRequest request, HttpServletResponse response)  {
	super(request,response,true);
	if (error) return;
	//if (!loggedIn()) return;
	ci = new ContextInfo(request,  response);
	if (ci.error) {
	    giveError(ci);
	    return;
	}
	//	if (!sd.getPasswordMatched()) {
	//	    giveError("Apparently you have logged in through a wrong page, or too long ago. Please log out and then log in again. (Nickname="+displayName+")");
	//	    return;	    
	//}
	EntityManager em=null;

	try {
	    
	    
	    em = Main.getNewEM();
	    Query q = em.createQuery("select m.nickname, m.ruleSetName, count(m) from MlcEntry m group by  m.nickname, m.ruleSetName order by  m.nickname, m.ruleSetName");

	    List<Object[]> results = (List<Object[]>)q.getResultList();

	    if (results.size()==0) {
		report += fm.para("No submissions have been recorded in the database");
	    } else {


		
		report += fm.h2("Participants' submissions");

		report += fm.para("The following submissions have been recorded in the database");

		Vector<String> rows = new Vector<>();
		rows.add( fm.tr( fm.th("ML algo nickname") +
				 fm.th("Rule Set") +
				 fm.th("Number of runs") +
				 fm.th("Actions")));

		String lastNick = "";

		for(Object[] line: results) {
		    String nickname = (String)(line[0]);
		    String rule = (String)(line[1]);
		    Long runs = (Long)(line[2]);
		    String aCmp = "";
		    if (!nickname.equals(lastNick)) {
			final String base = "../game-data/MlcUploadService";
			String cmpLink = base+"/compareRules?nickname="+nickname;
			aCmp = fm.a(cmpLink, "Compare rule sets for this algo", null);
			lastNick=nickname;
		    } 
		    
		    String[] v = {nickname, rule, "" + runs, aCmp};

		    String s="";
		    for(String x: v) s += fm.td(x);
		    rows.add( fm.tr( s));
		}
		
		report += fm.table(	 "border=\"1\"", rows);

		
	    }	      
			

	    
	} catch(Exception ex) {
	    hasException(ex);
	} finally {	
	    if (em!=null) try {
		    em.close();
		} catch(Exception ex) {}
	}


    }


    
}
