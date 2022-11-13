package edu.wisc.game.web;

import java.io.*;
import java.util.*;
import java.text.*;
import java.nio.charset.Charset;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;

/** Returns the content of an image file (SVG, PNG, etc) for one of the
   pages of the instruction booklet.
*/
public class GetBookletPageServlet  extends HttpServlet {
    public void service(HttpServletRequest request,HttpServletResponse response) {
	try {
	   String playerId =  request.getParameter("playerId");
	   if (playerId==null) throw new IllegalArgumentException("Must specify playerId=...");
       
	   String pageNo =  request.getParameter("pageNo");
	   if (pageNo==null) throw new IllegalArgumentException("Must specify pageNo=...");

	   int n = Integer.parseInt(pageNo);

	   PregameService.PregameResponseBase b =
	       new PregameService.PregameResponseBase(playerId);

	   if (n<0 || n>= b.getBookletSize())  throw new IllegalArgumentException("pageNo="+n+ " is out of range [0:"+b.getBookletSize()+"]");
	   
   	   
	   File f = b._getBookletPages()[n];
	   GetImageServlet.sendFile(response, f);
	    
	} catch (Exception e) {
            try {
                e.printStackTrace(System.out);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error in GetImageServlet: " + e); //e.getMessage());
            } catch(IOException ex) {};
        }	    
		
    }
}
