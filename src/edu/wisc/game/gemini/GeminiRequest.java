package edu.wisc.game.gemini;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.net.*;
import jakarta.json.*;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
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

    /** Call this if you want structured response.
	FIXME: Should also explain what structure we're looking for.
	Prepared-episodes mode is different from play mode.
    */
    void setNeedResponseSchema(boolean _needResponseSchema) {
	if (generationConfig == null) generationConfig = new ConfigElement();
	generationConfig.needResponseSchema = _needResponseSchema;
    }



    public static class InstructionElement {
	Vector<ElementPart> parts = new Vector<>();
	public Vector<ElementPart> getParts() { return parts;}
    }

    ConfigElement generationConfig = null;
    public ConfigElement getGenerationConfig() {
	return generationConfig;
    }
    public static class ConfigElement implements JsonReflect.HasBuilderAugment {
	Double temperature = null;
	public Double getTemperature() { return temperature;}
	private Integer candidateCount=null;
	public Integer getCandidateCount() { return candidateCount; }
	private Integer maxOutputTokens=null;
	public Integer getMaxOutputTokens() { return maxOutputTokens; }
	ThinkingConfigElement thinkingConfig = null;
	public ThinkingConfigElement getThinkingConfig() { return thinkingConfig; } 

	/** This flag should be set if we want this request to ask the
	    Gemini bot for a structured response (rather than plain text)
	*/
	boolean needResponseSchema = false;

	/** Keep this var: reflect needs it! Not used otherwise. */
	private String responseMimeType = "application/json";
	/** Do we want JSON or plain text back? */
	public String getResponseMimeType() {
	    return needResponseSchema?"application/json": "text/plain";
	}
  
	

	//	JsonObject responseSchemaJo
	
	/** This method is called by JsonReflect after the standard conversion
	    to a builder has been carried out, in order to allow this object
	    to add some extra fields to the builder */
	public void augmentBuilder(JsonObjectBuilder ob){
	    System.out.println("augmentBuilder() called");
	    //System.exit(0); 
	    if (needResponseSchema) {
		ob.add( "responseSchema", ResponseSchemaUtil.mkResponseSchema());
	    }
	}
	
    }

    public static class ThinkingConfigElement {
	int thinkingBudget;
	public int getThinkingBudget() { return thinkingBudget; }
    }
    
    Vector<ContentElement> contents = new Vector<>();
    public Vector<ContentElement> getContents()  { return contents; }

    public static class ContentElement {
	/** "user" or "model" */
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

    /** Adds my query */
    void addUserText(String msg) {
	ElementPart p = new ElementPart(msg);
	ContentElement ce = new ContentElement("user");
	ce.parts.add(p);
	contents.add(ce);
    }
    /** Adds a bot's response (to be sent back to it) */
    void addModelText(String msg) {
	ElementPart p = new ElementPart(msg);
	ContentElement ce = new ContentElement("model");
	ce.parts.add(p);
	contents.add(ce);
    }

    void addInstruction(String msg) {

	system_instruction = new InstructionElement();
	system_instruction.parts.add( new ElementPart(msg));
 
    }

    void addTemperature(Double temp) {
	if (temp==null) return;
	if (generationConfig == null) generationConfig = new ConfigElement();
	generationConfig.temperature = temp;

	//System.out.println("Set temp=" + temp +"\nThis request=" + 
	//		   JsonReflect.reflectToJSONObject(this, false, null, 10));
	
    }
    
    void addMaxOutputTokens(Integer m) {
	if (m==null) return;
	if (generationConfig == null) generationConfig = new ConfigElement();
	generationConfig.maxOutputTokens = m;
    }

    void addCandidateCount(Integer m) {
	if (m==null) return;
	if (generationConfig == null) generationConfig = new ConfigElement();
	generationConfig.candidateCount = m;
    }

    void addThinkingBudget(Integer x) {
	if (x==null) return;
	if (generationConfig == null) generationConfig = new ConfigElement();
	generationConfig.thinkingConfig = new ThinkingConfigElement();
	generationConfig.thinkingConfig.thinkingBudget = x;
    }
    
    /** Attaches the responseSchema (supplied as a JsonObject) to a request
	that's supplied as a JsonObject as well
	@param jo Find generationConfig under it, and put responseSchema into 
	that */
    /*
    static void addResponseSchema(JsonObject jo, JsonObject responseSchema) {
	JsonObject gc = jo.getJsonObject("generationConfig");
	if (gc==null) throw new IllegalArgumentException("Cannot add a response to this object, because it has no generationConfig: " + jo);
	gc.
	
	if (jsonText==null) return;
	if (generationConfig == null) generationConfig = new ConfigElement();
    }
    */
	

    
}
