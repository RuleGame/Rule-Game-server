package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import java.text.*;
//import java.util.zip.*;

import javax.servlet.*;
import javax.servlet.http.*;

import edu.wisc.game.util.*;

/** Returns the content of the SVG file for the specified shape */
public class GetImageServlet  extends HttpServlet {

    private static String[] mimeExtAndType = {
	"svg","image/svg+xml",
	"png","image/png",
	"jpeg","image/jpeg",
	"jpg","image/jpeg",
	"gif","image/gif",
	"bmp","image/bmp",
	"ico", "image/vnd.microsoft.icon",
	"tiff","image/tiff",
    };
    
    private static HashMap<String,String> mimeTypes = Util.array2map(mimeExtAndType);


    
    public void service(HttpServletRequest request,HttpServletResponse response) {
	try {
	    String shape = request.getParameter("image");
	    if (shape==null) throw new IllegalArgumentException("Must specify image=...");
       
	    
	    File f = Files.getImageFile(shape);
	    if (f==null)  throw new IOException("No matching file exists for image=" + shape);
	    if (!f.isFile() || !f.canRead())  throw new IOException("Cannot read file " + f);

	    String s  = f.getName().toLowerCase();
	    String q[] = s.split("\\.");
	    String mime =  mimeTypes.get(q[q.length-1]);
	    if (mime==null) mime = "application/octet-stream";
	    response.setContentType(mime);
	    response.setContentLengthLong(f.length());
	    FileInputStream fileInputStream=new FileInputStream(f);
	    ServletOutputStream out = response.getOutputStream();
	    int aByte;   
	    while ((aByte=fileInputStream.read()) != -1) {  
		out.write(aByte);   
	    }   
	    fileInputStream.close();
	    out.close();
	} catch (Exception e) {
            try {
                e.printStackTrace(System.out);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error in GetImageServlet: " + e); //e.getMessage());
            } catch(IOException ex) {};
        }	    
	
	
    }
}

