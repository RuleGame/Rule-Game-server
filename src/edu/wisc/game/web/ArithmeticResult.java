package edu.wisc.game.web;

import java.io.*;
import java.util.*;
import java.text.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

//import jakarta.xml.bind.annotation.XmlTransient; 
//import jakarta.json.*;

import edu.wisc.game.util.*;
//import edu.wisc.game.reflect.*;
//import edu.wisc.game.sql.*;
//import edu.wisc.game.engine.*;
import edu.wisc.game.formatter.*;
import edu.wisc.game.parser.*;

/** A tool for an easy online testing of arithmetic expression evaluation.
The data come from arithmetic-result.jsp.
 */
public class ArithmeticResult      extends ResultsBase  {

    public final static String prefix = "var.";

    /** Output to display, line by line */
    public final Vector<String> v = new Vector<>();

    static final HTMLFmter fmt = HTMLFmter.htmlFmter;

    public int  version = 5;
    
    public ArithmeticResult(HttpServletRequest request, HttpServletResponse response){
	super(request,response,false);
	if (error) return;
	try {
	   String exp = request.getParameter("expression");
	   if (exp==null || exp.trim().equals("")) {
	       giveError("No expression specified");
	       return;
	   }

	   try {
	       version = Integer.parseInt( request.getParameter("version"));
	   } catch(Exception ex) {}


	   
	   // process variable values, which can be set-based, e.g.
	   // "var.p=1 2 3"
	   Expression.VarMap hh = new Expression.VarMap();
	   Expression.VarMap2 hh2 = new Expression.VarMap2();
	   
	   for(String name: request.getParameterMap().keySet()) {
	       if (!name.startsWith(prefix)) continue;
	       String key = name.substring(prefix.length());
	       String vs = request.getParameter(name);
	       if (vs==null) continue;
	       vs = vs.trim();
	       if (vs.length()==0) continue;
	       for(String s: vs.split("[,\\s]+")) {
		   try {
		       hh.addValue(key, Integer.valueOf(s));
		   } catch(Exception ex) {}
		   hh2.addValue(key, s);
	       }
	   }

	   if (hh.size()==0) {
	       v.add("No variables have values set");
	   } else {	
	       v.add("Have values set for "+hh.size()+" variables:");
	       for(String key : hh.keySet()) {
		   v.add(key+"=[" + Util.joinNonBlank(", ",hh.get(key))+"]");
	       }
	   }

	   v.add("Expression to process: " + exp);
	   
	   Vector<Token> tokens= Token.tokenize(exp);
	   v.add("Expression tokenized: " + Token.toString(tokens));

	   Expression ex = Expression.mkLongestArithmeticExpression(tokens);
	   
	   v.add("Expression parsed: " + ex);

	   if (tokens.size()>0) v.add("Warning: ignoring the last " + tokens.size() + " tokens of they input, because they don't fit with the expression");
	   if (ex instanceof Expression.ArithmeticExpression) {
	       Expression.ArithmeticExpression ae = (Expression.ArithmeticExpression)ex;
	       HashSet<Integer> hv = ae.evalSet(hh);
	       hv = Expression.moduloNB(hv);

	       v.add(fmt.wrap("strong","Expression evaluates to [" +Util.joinNonBlank(", ",hv)+"]"));

	   } else {
	       System.out.println("Error: Not an arithmetic expression");
	   }
	} catch(Exception e) {
	    hasException(e);
	}
	  
     }

}
