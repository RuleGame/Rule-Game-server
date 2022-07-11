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

    /** As REST is stateless, this table is used for authorization, instead of sessions. It is worked by MlcLoginServlet (via LoginServlet) */
    private static HashMap<String,String> userKeyTable = new HashMap<String,String>();


    private static Random random = new Random();

    /** Used by the MlcLoginServlet etc */
    public static synchronized String giveKey(String nickname) {
	if (nickname==null) return null;
	
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
	if (nickname==null) return false;
	String s = userKeyTable.get(nickname);
	return s!=null && s.equals(key);
    }


    /**
nickname,rule_name,trial_id,board_id,number_of_pieces,number_of_moves,move_acc,if_clear
RandomTest,alternateShape2Bucket_color2Bucket,0,0,9,29,0.3103448275862069,1
RandomTest,alternateShape2Bucket_color2Bucket,0,1,9,20,0.45,1
...
    */
    @Path("/uploadFile")
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public String uploadFile(@FormDataParam("nickname") String nickname,
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
		throw new IllegalInputException("No data uploaded");		
	    }

	    title ="File uploading for MLC participant " + fm.tt(nickname);

	    File d = Files.mlcUploadDir(nickname, false);

	    Date now = new Date();
	    
	    for(BodyPart part : parts.getParent().getBodyParts()){
		InputStream file = part.getEntityAs(InputStream.class);
		ContentDisposition fileDisposition = part.getContentDisposition();
		String fileName=fileDisposition.getFileName();
		String type=fileDisposition.getType();

		if (fileName==null) continue; // some other param than a fle

		body += fm.para("Writing "+ fm.tt(fileName) + " ...");

		File g = new File(d, fileName);
		/*
		OutputStream out = new FileOutputStream(g);
		int b;
		int cnt=0;
		while ((b=file.read())>=0) {
		    out.write(b);
		    cnt ++;
		}
		out.close();
		*/

		PrintWriter w = new PrintWriter(new FileWriter(g));
		InputStreamReader r0 = new InputStreamReader(file);
		LineNumberReader r = new LineNumberReader(r0);
		String line = null;
		long lineCnt = 0, charCnt=0;
		Vector<MlcEntry> results = new Vector<>();
		MlcEntry e = null;
		while((line = r.readLine())!=null) {
		    if (lineCnt==0) {
			String h0 = "nickname,rule_name,trial_id,board_id,number_of_pieces,number_of_moves,move_acc,if_clear";
			if (!line.trim().equals(h0)) {
			    body += fm.para("Error: unexpected header. Expected: " + fm.pre(h0) + "Found: " + fm.pre(line));
			    break;
			} 
		    } else {
			
			String q[] = line.trim().split(",");
			int M = 8;
			if (q.length!=M) {
			    body += fm.para("Error: unexpected number of columns in line "+(lineCnt+1)+" Expected: " + M+ " columns, found " + q.length + ". Line: " + fm.pre(line));
			    break;
			}	
		    
			String _nickname = q[0];
			if (!_nickname.equals(nickname)) {
			      body += fm.para("Error: unexpected nickname in line "+(lineCnt+1)+" Expected: " + nickname+ " columns, found " + _nickname + ". Line: " + fm.pre(line));
			    break;
			}
		    			
			String rule_name = q[1];
			int runNo = Integer.parseInt(q[2]);

			if (e!=null && e.matches(nickname, rule_name, runNo)) {
			    // keep going
			} else {
			    e = new MlcEntry(nickname, rule_name, runNo, now);
			    results.add(e);
			}
		 			  
			
			int episodeNo = Integer.parseInt(q[3]);			
			int number_of_pieces =Integer.parseInt(q[4]);
			int number_of_moves=Integer.parseInt(q[5]);
			double move_acc = Double.parseDouble(q[6]);
			boolean if_clear = Boolean.parseBoolean(q[7]);

			StringBuffer errmsg = new StringBuffer();
			if (!e.addEpisode( episodeNo,
					   number_of_pieces,
					   number_of_moves,
					   move_acc,
					   if_clear,
					   errmsg)) {
	
			    body += fm.para("Error in line "+(lineCnt+1)+". " + errmsg + ". Line: " + fm.pre(line));
			    break;
			    
			}
		    }
		    w.print(line);
		    lineCnt++;
		    charCnt += line.length();
		}
		file.close();
		w.close();
		
		body += fm.para("Copied " + lineCnt + " lines, wrote file "+ g+ " with the length of " +  g.length() + " bytes");	

		body += fm.para(fm.a("../../mlc/", "Back to the MLC participant's dashboard"));

		body += fm.para("Processed data for " + results.size() + " runs");

		Vector<String> rows = new Vector<>();
		rows.add( fm.tr( fm.th("Rule Set") +
				 fm.th("Run No.") +
				 fm.th("Episodes") +
				 fm.th("Total moves") +
				 fm.th("Total errors") +
				 fm.th("Learned?") +
				 fm.th("Episodes till learned") +
				 fm.th("Moves till learned")));
		String lastRule = "";
		for(MlcEntry _e: results) {
		    e = _e;
		    String rule = e.getRuleSetName();

		    String s = 	(rule.equals(lastRule))? fm.td(""): fm.td(rule);
		    lastRule = rule;
		    s +=
			fm.td( ""+e.getRunNo())+
			fm.td( ""+e.getTotalEpisodes()) +		      
			fm.td("" +e.getTotalMoves()) +		      
			fm.td("" +e.getTotalErrors()) +		      
			fm.th(e.getLearned()? "Yes" : "No");
		    if (e.getLearned()) {
			s += fm.td("" + e.getEpisodesUntilLearned() ) +
			    fm.td("" +e.getMovesUntilLearned());
		    }
		    rows.add(fm.tr(s));
		}			      
			  				 
		body += fm.table(		 "border=\"1\"", rows);
		
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
