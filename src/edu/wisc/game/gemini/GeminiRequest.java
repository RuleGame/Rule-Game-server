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

public class GeminiRequest {

    /**
       A JSON object sent to Gemini
       
       curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=AIzaSyBv_dFQT2bpLNnyYwyx600fREhHWwhAWUE" \
-H 'Content-Type: application/json' \
-X POST \
-d '{
  "contents": [{
    "parts":[{"text": "How much does it cost to transfer between terminals in Manila Airport?"}]
    }]
   }'

    */

    InstructionElement system_instruction = null;
    public InstructionElement getSystem_instruction() { return system_instruction; }

    public static class InstructionElement {
	Vector<ElementPart> parts = new Vector<>();
	public Vector<ElementPart> getParts() { return parts;}
    }
    
    Vector<ContentElement> contents = new Vector<>();
    public Vector<ContentElement> getContents()  { return contents; }

    public static class ContentElement {
	final String role;
	public String getRole() { return role; }
	Vector<ElementPart> parts = new Vector<>();
	public Vector<ElementPart> getParts() { return parts;}
	ContentElement(String _role) { role = _role; }
    }

    public static class ElementPart {
	final String text;
	public String getText() { return  text; }
	ElementPart(String _text) { text = _text; }
    }

    void addUserText(String msg) {
	ElementPart p = new ElementPart(msg);
	ContentElement ce = new ContentElement("user");
	ce.parts.add(p);
	contents.add(ce);
    }

    void addInstruction(String msg) {

	system_instruction = new InstructionElement();
	system_instruction.parts.add( new ElementPart(msg));
 
    }
    
}
