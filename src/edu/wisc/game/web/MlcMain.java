package edu.wisc.game.web;

import java.io.*;
import java.util.*;
import java.text.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import javax.persistence.*;


import edu.wisc.game.util.*;
//import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
//import edu.wisc.game.engine.*;
import edu.wisc.game.formatter.*;
import edu.wisc.game.rest.*;
//import edu.wisc.game.parser.*;

public class MlcMain  extends ResultsBase  {

    private ContextInfo ci;

    private static HTMLFmter fm = new HTMLFmter();

    public String report = "";
    
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

	try {
	    final String nickname = displayName;
	    if (nickname==null) {
		hasError("Nickname not specified");
		return;
	    }
	    File d = Files.mlcUploadDir(nickname, false);

	    Vector<String> rows = new Vector<>();
	
	    File[] files = d.listFiles();
	    Vector<String> v = new Vector<String>();
	    for(File cf: files) {
		if (!cf.isFile()) continue;
		String fname = cf.getName();
		rows.add( fm.row(fname, "" +  cf.length() + " bytes"));
	    }

	    report += fm.para(rows.size()==0?
			      "We have no files uploaded by you so far":
			      "We have "+rows.size()+" files uploaded by you so far");
	    report += fm.para( fm.table("", rows));
	} catch(Exception ex) {
	    hasException(ex);
	}

    }


    
}
