package edu.wisc.game.gemini;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.net.*;
import java.text.*;
import jakarta.json.*;

/** Tool to get the last request and its response from a log file written on an earlier run of GeminiPlayer */
class LogFileParser {
    Vector<String> requestLines=null;
    String responseText=null;
    int requestCnt = 0;
    
/** Reading back a log produced by GeminiPlayer at an earlier
	session, in order to restore the session's history. This
	method finds the last request in which GeminiPlayer had
	recapitulated the sessions's history. The log section
	(the request text) this method looks for starts after
	"The text part of the request:", and ends with "YOUR MOVE?"
	(or, for the final request, with the "Request took" [XXX msec] line)
    */
    //private Vector<String> extractLastRequest(File f) throws FileNotFoundException, IOException {
    LogFileParser(File f) throws FileNotFoundException, IOException {
    
	LineNumberReader r = new LineNumberReader(new FileReader(f));
	String s = null;
	Vector<String> v = new Vector<>();
	Vector<String> after = new Vector<>(); // text that's after the last request
	Vector<String> lastFoundText = null;
	boolean inside = false;
	while((s = r.readLine())!=null) {
	    if (s.startsWith(	"The text part of the request:")) {
		v.clear();
		inside = true;
		after.clear();
	    }
	    if (inside) {
		if (s.startsWith("YOUR MOVE") ||
		    s.startsWith("Request took")) {
		    inside = false;
		    lastFoundText = new Vector<>();
		    lastFoundText.addAll(v);
		    v.clear();
		    requestCnt++;
		} else {		    
		    v.add(s);
		}
	    } else {
		after.add(s);
	    }
	}
	System.out.println("Found " + requestCnt + " requests in the log. The last one has " + lastFoundText.size() + "  lines");
	requestLines = lastFoundText;
	// Keep reading the file, now looking for the response
	v.clear();
	inside = false;
	for(String ss: after) {
	    s = ss;
	    //System.out.println("INPUT: " + s.substring(0, 50));
	    final String lede=	"Response text={{";
	    if (s.startsWith(lede)) {
		//System.out.println("INPUT MATCH");
		v.clear();		
		inside = true;
		s = "{" + s.substring(lede.length());
		int pos = s.indexOf("}}");
		boolean alsoLast = (pos>=0);
		if (alsoLast) s = s.substring(0, pos) + "}";
		v.add(s);
		if (alsoLast) break;
		else continue;
	    }
	    if (inside) {
		int pos = s.indexOf("}}");
		boolean alsoLast = (pos>=0);
		if (alsoLast) s = s.substring(0, pos) + "}";
		v.add(s);
		if (alsoLast) break;
	    }	    
	}
	if (v.size()>0) responseText = String.join("\n", v);
    }

}    
