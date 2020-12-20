package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import java.text.*;
//import java.util.zip.*;

import javax.servlet.*;
import javax.servlet.http.*;

import edu.wisc.game.util.*;

/** Returns the content of the SVG file for the specified shape */
public class GetSvgWrapper {
  
   public GetSvgWrapper( HttpServletRequest request, HttpServletResponse response){

       try {
	   String shape = request.getParameter("shape");
	   if (shape==null) throw new IllegalArgumentException("Must specify shape=...");
       
	   
	   File f = new File(Files.shapesDir(), shape.toLowerCase() + ".svg");
	   if (!f.canRead())  throw new IOException("Cannot read file: " + f);
	   
	   response.setContentType("image/svg+xml");   
	   //response.setHeader("Content-Disposition","attachment; filename=\"" + f.getName() + "\"");   
  
	   FileInputStream fileInputStream=new FileInputStream(f);
	   ServletOutputStream out = response.getOutputStream();
	   int i;   
	   while ((i=fileInputStream.read()) != -1) {  
	       out.write(i);   
	   }   
	   fileInputStream.close();
	   out.close();
       } catch(Exception ex) {
	   try {
	       response.setContentType("text/html");
	       response.sendError(404, "Error: "+ ex.getMessage());
	   } catch (Exception ex2) {
	       Logging.error("GetSvgWrapper: Cannot send an error code to the client: " + ex2);
	   }
       }                
   }

}
