package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import java.text.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.json.*;


import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.formatter.*;


import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
//import org.springframework.stereotype.Component;



/** Uploading results files by MLC participants

 */

@Path("/MlcUploadService") 
public class MlcUploadService {
    private static HTMLFmter  fm = new HTMLFmter();

    /** As REST is stateless, this table is used for authorization, instead of sessions */
    private static HashMap<String,String> userKeyTable = new HashMap<String,String>();


    private static Random random = new Random();

    /** Used by the MlcLoginServlet etc */
    public static synchronized String giveKey(String nickname) {
	String s = userKeyTable.get(nickname);
	if (s==null) {
	    byte bytes[] = new byte[10];	    
	    random.nextBytes( bytes);
	    StringBuffer x = new StringBuffer();
	    for(byte b: bytes) {
		x.append((char)('A' + (b & 0x0F)));
		x.append((char)('A' + (b >> 4)));
	    }
	    userKeyTable.put(nickname, s = x.toString());
	}
	return s;
    }

    private boolean matched(String  nickname, String key) {
	String s = userKeyTable.get(nickname);
	return s!=null && s.equals(key);
    }
   
    @Path("/uploadFile")
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public String displayBoardFile(@FormDataParam("nickname") String nickname,
				   @FormDataParam("key") String key,
				   @FormDataParam("file") FormDataBodyPart parts				    ) {
	
	String title="", body="";

	try {

	    if (nickname==null || key==null) {
		throw new IllegalInputException("No nickname/key parameter in the form. (n="+nickname+", k="+key+")");		
	    }

	    if (!matched(nickname, key)) {
		throw new IllegalInputException("Authentication failed. Go back and try to log out and login again");
	    }

	    if (parts==null) {
		throw new IllegalInputException("No board description JSON supplied");		
	    }

	    title ="File uploading for participant " + fm.tt(nickname);

	    File d = Files.mlcUploadDir(nickname, false);


	    
	    for(BodyPart part : parts.getParent().getBodyParts()){
		InputStream file = part.getEntityAs(InputStream.class);
		ContentDisposition fileDisposition = part.getContentDisposition();
		String fileName=fileDisposition.getFileName();
		String type=fileDisposition.getType();

		if (fileName==null) continue; // some other param than a fle

		body += fm.para("Writing "+ fm.tt(fileName) + " ...");

		File g = new File(d, fileName);
		OutputStream out = new FileOutputStream(g);
		int b;
		int cnt=0;
		while ((b=file.read())>=0) {
		    out.write(b);
		    cnt ++;
		}
		out.close();

		body += fm.para("Read " + cnt + " bytes, wrote " +  g.length() + " bytes");	

		body += fm.para(fm.a("../../mlc/", "Back to the MLC participant's dashboard"));


		
	    }
	} catch(Exception ex) {
	    System.err.println(ex);
	    ex.printStackTrace(System.err);
	    title ="Error, nickname = " + nickname;
	    if (ex instanceof IllegalInputException) {
		body = ex.getMessage();
	    } else {
		body = ex.toString();
	    }
	}
	return fm.html(title, body);		
    }
    
 
}
