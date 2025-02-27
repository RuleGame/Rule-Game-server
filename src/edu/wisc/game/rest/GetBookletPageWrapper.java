package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import java.text.*;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import edu.wisc.game.util.*;

/** Returns the content of the specified page of the instruction booklet
    for the specified player */
public class GetBookletPageWrapper {
  
    public GetBookletPageWrapper( HttpServletRequest request, HttpServletResponse response, Writer outw){

       try {
	   String playerId =  request.getParameter("playerId");
	   if (playerId==null) throw new IllegalArgumentException("Must specify playerId=...");
       
	   String pageNo =  request.getParameter("pageNo");
	   if (pageNo==null) throw new IllegalArgumentException("Must specify pageNo=...");

	   int n = Integer.parseInt(pageNo);

	   PregameService.PregameResponseBase b =
	       new PregameService.PregameResponseBase(playerId);

	   if (n<0 || n>= b.bookletSize)  throw new IllegalArgumentException("pageNo="+n+ " is out of range [0:"+b.bookletSize+"]");
	   
   	   
	   File f = b.bookletPages[n];
	   if (!f.canRead())  throw new IOException("Cannot read file: " + f);
	   
	   response.setContentType(PregameService.getMimeType(f.getName()));

	   int x;
	   FileInputStream r=new FileInputStream(f);
	   while((x = r.read()) != -1) {
	       outw.write(x);
	   }
	   r.close();
	   
	   
       } catch(Exception ex) {
	   try {
	       response.setContentType("text/html");
	       response.sendError(404, "Error: "+ ex.getMessage());
	   } catch (Exception ex2) {
	       Logging.error("GetBookletPageWrapper: Cannot send an error code to the client: " + ex2);
	   }
       }                
   }

}
