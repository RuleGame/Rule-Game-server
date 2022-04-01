package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import java.text.*;
//import java.util.zip.*;
import java.nio.charset.Charset;

import javax.servlet.*;
import javax.servlet.http.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.ImageObject;
import edu.wisc.game.svg.Composite;

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

	    if (Composite.isCompositeName(shape)) {
		Composite compo = (Composite)ImageObject.obtainImageObjectPlain(null, shape, false);
		if (compo==null)  throw new IllegalInputException("Cannot convert this path ("+shape+") to composite image");
		// SVG is supposed to be in UTF-8.
		String encoding = "UTF-8";
		Charset cs =  Charset.forName(encoding);
		byte[] svgBytes = compo.getSvg().getBytes(cs);
		ByteArrayInputStream bis = new 	ByteArrayInputStream(svgBytes);
		String mime = "image/svg+xml";
		sendData(response, bis, svgBytes.length, mime);
	    } else {
	    
	    
	    File f = Files.getImageFile(shape);
	    if (f==null)  throw new IOException("No matching file exists for image=" + shape);
	    if (!f.isFile() || !f.canRead())  throw new IOException("Cannot read file " + f);

	    String s  = f.getName().toLowerCase();
	    String q[] = s.split("\\.");
	    String mime =  mimeTypes.get(q[q.length-1]);
	    if (mime==null) mime = "application/octet-stream";
	    //response.setContentType(mime);
	    //response.setContentLengthLong(f.length());
	    FileInputStream fileInputStream=new FileInputStream(f);
	    sendData(response, fileInputStream,f.length(), mime);
	    /*
	    ServletOutputStream out = response.getOutputStream();
	    int aByte;   
	    while ((aByte=fileInputStream.read()) != -1) {  
		out.write(aByte);   
	    }   
	    fileInputStream.close();
	    out.close();
	    */
	    }
	    
	} catch (Exception e) {
            try {
                e.printStackTrace(System.out);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error in GetImageServlet: " + e); //e.getMessage());
            } catch(IOException ex) {};
        }	    
		
    }

    /** Writes the data from a given stream to the HTTP connection */
    private void sendData(HttpServletResponse response, InputStream in, long len, String mime) throws IOException {
	response.setContentType(mime);
	response.setContentLengthLong(len);
	ServletOutputStream out = response.getOutputStream();
	int aByte;   
	while ((aByte=in.read()) != -1) {  
	    out.write(aByte);   
	}
	in.close();
	out.close();
	    
    }
    
}

