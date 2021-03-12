package edu.wisc.game.web;

import java.io.*;
import java.util.*;
import java.text.*;
import javax.servlet.*;
import javax.servlet.http.*;

//import javax.xml.bind.annotation.XmlTransient; 
//import javax.json.*;

import edu.wisc.game.util.*;
//import edu.wisc.game.reflect.*;
//import edu.wisc.game.sql.*;
//import edu.wisc.game.engine.*;
import edu.wisc.game.formatter.*;
import edu.wisc.game.parser.*;

/** A tool for an easy online testing of arithmetic expression evaluation.
The data come from arithmetic-result.jsp.
 */
public class ArithmeticResult      extends ResponseBase  {

    public final static String prefix = "var.";

    /** Output to display, line by line */
    public final Vector<String> v = new Vector<>();

    static final HTMLFmter fmt = HTMLFmter.htmlFmter;
    
    public ArithmeticResult(HttpServletRequest request, HttpServletResponse response){

	try {
	   String exp = request.getParameter("expression");
	   if (exp==null || exp.trim().equals("")) {
	       giveError("No expression specified");
	       return;
	   }
	   
	   // process variable values, which can be set-based, e.g.
	   // "var.p=1 2 3"
	   HashMap<String, HashSet<Integer>> hh = new HashMap<>();
	   for(String name: request.getParameterMap().keySet()) {
	       if (!name.startsWith(prefix)) continue;
	       String key = name.substring(prefix.length());
	       String vs = request.getParameter(name);
	       if (vs==null) continue;
	       vs = vs.trim();
	       if (vs.length()==0) continue;
	       HashSet<Integer> z = new HashSet<>();
	       for(String s: vs.split("[,\\s]+")) {
		   z.add( Integer.valueOf(s));
	       }
	       hh.put(key, z);
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
