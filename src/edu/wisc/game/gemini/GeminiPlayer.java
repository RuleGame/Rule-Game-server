package edu.wisc.game.gemini;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.net.*;
import jakarta.json.*;


import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
//import edu.wisc.game.sql.*;
//import edu.wisc.game.parser.*;
//import edu.wisc.game.sql.Episode.OutputMode;
import edu.wisc.game.rest.*;
import edu.wisc.game.engine.*;

public class GeminiPlayer {

    /**
       curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=XXXX" \
-H 'Content-Type: application/json' \
-X POST \
-d '{
  "contents": [{
    "parts":[{"text": "How much does it cost to transfer between terminals in Manila Airport?"}]
    }]
   }'

    */  

    private void doOneRequest(GeminiRequest gr) throws MalformedURLException, IOException, ProtocolException
    {
	readApiKey();
	String u = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
	u += "?key=" + gemini_api_key;
	
	URL url = new URL(u);
	HttpURLConnection con = (HttpURLConnection)url.openConnection();
	con.setRequestMethod("POST");
	con.setRequestProperty("Content-Type", "application/json");
	con.setDoOutput(true);

	JsonObject jo = JsonReflect.reflectToJSONObject(gr, false, null, 10);

	System.out.println("SENDING: " + jo.toString());
	String jsonInputString =  jo.toString();

	try(OutputStream os = con.getOutputStream()) {
	    byte[] input = jsonInputString.getBytes("utf-8");
	    os.write(input, 0, input.length);			
	}


	int code = con.getResponseCode();
	InputStream is;
    
	if (code != 200) {
	    System.out.println("Error: HTTP response code = " + code);
	    is = con.getErrorStream();
	    //return;
	} else {
	    is = con.getInputStream();
	}
	InputStreamReader isr = new InputStreamReader(is, "utf-8");
    
	try(BufferedReader br = new BufferedReader(isr)) {
	    StringBuilder response = new StringBuilder();
	    String responseLine = null;
	    while ((responseLine = br.readLine()) != null) {
		response.append(responseLine.trim());
	    }
	    System.out.println(response.toString());
	}
	
    }


    static GeminiRequest makeRequest1() {
	GeminiRequest gr = new GeminiRequest();

	gr.addInstruction("Please answer in German, if you can");

	
	gr.addUserText("How do you use borax?");
	return gr;
    }


    static String instructions=null;
    
    static GeminiRequest makeRequestGame() throws IOException {
	GeminiRequest gr = new GeminiRequest();

	if (instructions==null) {
	    instructions= Util.readTextFile(new File("/opt/w2020/system.txt"));
	}

	gr.addInstruction(instructions);

	
	gr.addUserText("How do you use borax?");
	return gr;
    }

    
    static String gemini_api_key = null;

    static void readApiKey() throws IOException {
	if ( gemini_api_key != null) return;
	String s = Util.readTextFile(new File("/opt/w2020/gemini-api-key.txt"));
	s = s.replaceAll("\\s", "");
	gemini_api_key = s;
    }
    
    public static void main(String[] argv) throws Exception {
	GeminiPlayer p = new GeminiPlayer();
	//	IOException,  RuleParseException, ReflectiveOperationException, IllegalInputException{
	GeminiRequest gr = makeRequest1();
	p.doOneRequest(gr);

	File f = new File( Files.geminiDir(), "system.txt");	
	String s= Util.readTextFile( f);
	gr = new GeminiRequest();

	gr.addInstruction(s);

	


	
    }

    
}
