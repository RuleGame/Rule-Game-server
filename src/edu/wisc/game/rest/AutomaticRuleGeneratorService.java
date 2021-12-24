package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import javax.servlet.http.HttpServletResponse;
import javax.json.*;


import javax.ws.rs.*;
import javax.ws.rs.core.*;

// For database work
import javax.persistence.*;


import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.formatter.*;

/** The  AutomaticRuleGenerator demo service. */
@Path("/AutomaticRuleGeneratorService") 
public class  AutomaticRuleGeneratorService extends GameService2 {
    private static Fmter  fm = new HTMLFmter("../../css/rule-game.css");

    @POST
    @Path("/generateHtml") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    /** 
	@param paraText The parameters 
     */
    public String generateHtml( @FormParam("paraText") String paraText,
				@FormParam("templateText") String templateText,
				@FormParam("n") int n
				){

	Vector<String> v = new Vector<>();
	int errcnt=0;
	String info = null;
	String title = "Generating rules";
	String title1 = title;

	v.add(fm.h1(title1));

	try {
	    ParaSet para = ParaSet.textToParaSet(paraText); 	    
	    AutomaticRuleGenerator ag = new  AutomaticRuleGenerator(-1L, para);

	    v.add( fm.para("Variable substitutions are drawn from the following sets:"));

	    Vector<String> w = new Vector<>();
	    for(String  s: ag.varReport()) {
		w.add(fm.wrap("li", s));
	    }
	    v.add( fm.wrap("ul", String.join("\n", w)));

	    int outCnt = 0;
	    //NumberFormat nfmt = new DecimalFormat("000");

	    for(int j=0; j<n; j++) {
		v.add(fm.h4("Auto rule set No. " + j));  
		RuleSet rules = new RuleSet(templateText, ag);

		StalemateTester tester = new  StalemateTester(rules);
		Board stalemated =
		    tester.canStalemate(//Piece.Shape.legacyShapes,
					//Piece.Color.legacyColors,
					Piece.Shape.findShapes(ag.getShapeNames()),
					Piece.Color.findColors(ag.getColorNames()),

					null);

		String extra = "";

		w.clear();
		w.add(fm.pre( rules.toSrc()));
		if (stalemated!=null) {
		    w.add(fm.para("The rule set above can stalemate"));
		    String picture =	(fm instanceof HTMLFmter) ?
			BoardDisplayService.doBoard(stalemated, 48):
			BoardDisplayService.doBoardAscii(stalemated);
	    	    
		    w.add(fm.para("Sample stalemate board:" + fm.br() + picture));	    	    		
		    extra = "class=\"pink\"";
		} else {
		    w.add(fm.para("No stalemate detected" ));
		    outCnt++;
		}
		v.add( fm.wrap("div", extra, String.join("\n", w)));		
	    }
	    v.add(fm.h3("Summary"));
	    v.add(fm.para("Generated " + n + " boards, including " + outCnt + " non-stalemating"));
	    
	} catch(Exception ex) {
	    if (info != null) v.add(fm.para(info));
	    v.add(fm.para("Error: " + ex));
	    StringWriter sw = new StringWriter();
	    ex.printStackTrace(new PrintWriter(sw));
	    String s = fm.pre(sw.toString());
	    v.add(fm.para(fm.wrap("small", "Details:"  + s)));
	    //errcnt ++;
	}

	
	String body = String.join("\n", v);
	return fm.html(title, body);	

    }


    
}
