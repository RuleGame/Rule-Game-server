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

public class MlcMain  extends ResultsBase  {

    private ContextInfo ci;

    private static HTMLFmter fm = new HTMLFmter();

    public String key = "";
    public String report = "";
    public String nickname = null;
    
    public MlcMain(HttpServletRequest request, HttpServletResponse response)  {
	super(request,response,true);
	if (error || !loggedIn()) return;
	ci = new ContextInfo(request,  response);
	if (ci.error) {
	    giveError(ci);
	    return;
	}
	if (!sd.getPasswordMatched()) {
	    giveError("Apparently you have logged in through a wrong page, or too long ago. Please log out and then log in again. (Nickname="+displayName+")");
	    return;	    
	}
	EntityManager em=null;

	try {
	    nickname = displayName;
	    if (nickname==null) {
		giveError("Nickname not specified");
		return;
	    }


	    key = MlcUploadService.giveKey(nickname);

	    
	    File d = Files.mlcUploadDir(nickname, false);

	    Vector<String> rows = new Vector<>();
	
	    File[] files = d.listFiles();
	    Vector<String> v = new Vector<String>();
	    for(File cf: files) {
		if (!cf.isFile()) continue;
		String fname = cf.getName();
		rows.add( fm.row(fname, "" +  cf.length() + " bytes"));
	    }

	    if (rows.size()==0) {
		report += fm.para("We have no files uploaded by you so far.");
	    } else {
		report += fm.para("We have "+rows.size()+" files uploaded by you so far.");
		report += fm.para( fm.table("border=\"1\"", rows));
	    }

	    em = Main.getNewEM();
	    Query q = em.createQuery("select distinct m.ruleSetName from MlcEntry m where m.nickname=:n");
	    q.setParameter("n", nickname);
	    List<String> myRules = (List<String>)q.getResultList();
	    if (myRules.size()>0) {
		report += fm.h2("Compare your results");
		report += fm.para("You have submitted results for this algorithm's performance on " + myRules.size() + " rule set(s). You can use links in the table below to see how these results compare to those obtained by other algorithms");
		rows.clear();
		rows.add( fm.tr( fm.th("Rule Set") +
				 fm.th("My data summary") +
				 fm.th("Comparison to others")));

		final String base = "../game-data/MlcUploadService";
		
		for(String rule: myRules) {
		    String sumLink = base+"/summary?nickname="+nickname+"&rule=" + rule;
		    String aSum = fm.a(sumLink, "summary", null);

		    String cmpLink = base+"/compare?nickname="+nickname+"&rule=" + rule;
		    String aCmp = fm.a(cmpLink, "compare", null);

		    
		    rows.add( fm.tr( fm.td(rule) +
				     fm.th(aSum) +
				     fm.th(aCmp)));
		    
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
