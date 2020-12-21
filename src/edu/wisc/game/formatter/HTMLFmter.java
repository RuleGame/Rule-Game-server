package edu.wisc.game.formatter;

import java.io.*;
import java.util.*;

import edu.wisc.game.util.Util;


/** Auxiliary class for formatting output as HTML. */
public class HTMLFmter extends Fmter {

    private String css=null;

    /** @param cssFile The location of the CSS file which will be inserted into the head of
	the HTML report (both the the batch mode and in the GUI tool) */
    public HTMLFmter(File cssFile) { //throws IOException { 
	html = true;
	try {
	    css =  (cssFile!=null) ? Util.readTextFile(cssFile) : null;
	} catch (IOException ex) {}
    }

    /** The STYLE element */
    public String style() {
	return css==null? "" : wrap2("style",  "type=\"text/css\"", css) +"\n";
    }

    
    public String br() { 
	return "<br>\n";
    }

    public  String hr() {
	return "<hr>\n";
    }

    public  String hrThin() {
	return "<hr class=\"thin\">\n";
    }

    public String space(int n) {
	String z = "&nbsp;";
	StringBuffer b= new 	StringBuffer(6*n);
	for(int i=0;i<n; i++) b.append(z);
	return b.toString();
    }

    /** Builds an HTML element with an opening tag (with optional attributes)
	and a matching closing tag.
	@param wrap An HTML tag, e.g. "P", "H2", or "TD"
	@param extra Additional attributes to go into the opening tag, e.g.
	"valign='top'". Could be null or "" for none.
	@param text The text to be wrapped into the tag (i.e. to go 
	between e.g. TD and /TD).
     */
    public String wrap(String wrap, String extra, String text) {
	String wrapStart = wrap;
	if (extra!=null && extra.length()>0) {
	    wrapStart  += " " + extra;
	}
	return "<" + wrapStart + ">" + text + "</" + wrap + ">";
    }

    public String wrap(String wrap, String text) {
	return wrap(wrap, null, text);
    }

     
    public String input(String name, String value, int size) {
	String s = "<input name='" + name + "' type='text'";
	if (value!=null) s += " value='"+value+"'";
	if (size>0) s+= " size='"+size+"'";
	s += ">";
	return s;
    }
  
    public String input(String name, String value) { 
	return input(name, value, 0);
   }

    public String input(String name) {
	return input(name, null, 0);
    }

    public String hidden(String name, String value) {
	return  "<input type='hidden' name='"+name+"' value='"+value+"'>\n";
    }
    
    
    /** The total number of forms written so far into the document. This
	is used for the sequential numbering of forms within the HTML
	document, starting from 1
    */
    private int formCnt = 0;

    /** This must be called before the beginning of the document generation */
    public void resetFormCnt() { formCnt = 0; }

    /** This is used to generate IDs of HTML forms (for feedback) */
    //    static final String FORM_ID_PREFIX = "ff";
    //    static final public String NAME_FILE = "file", NAME_JSEL="jsel";

    /** Generates the ID of the k-th HTML form (for feedback) */
    /*
    public static String mkFid(int k) {
	String fid = FORM_ID_PREFIX + k;
	return fid;
    }
    */
    
    //    public static final String[] FORM_VALS= {"DQ", "NotDQ", "NotDQ_dismissed", "NotDQ_scope", "ARR"};

    /** @return In index into FORM_VALS[] */
    /*
    static private int getJsel(boolean dq, Decision.Code dcode) {
	int jsel = (dq ? 0 : 1);
	if (dcode!=null) {
	    if (dcode.isDismissed()) jsel=2;
	    else if (dcode.isOutOfScope()) jsel=3;
	}
	return jsel;
    }
    */
    
    /** Builds an HTML FORM element for feedback about a charge, if this formatter is configured to generate them.

     */
    /*
    public String makeFeedbackForm(String actionID, int chargeSeq, boolean dq, Decision.Code dcode) {
	if (savedFeedback!=null) {
	    return   showSavedFeedback( actionID, chargeSeq, dq, dcode);
	}


	if (!feedback) return "";//"Feedback not on"; // no feedback forms requested
	formCnt ++;
	String fid = mkFid( formCnt);
	String s = "<form id=\""+fid+"\">";

	s+="<input type=\"hidden\" name=\""+NAME_FILE+ "\" value=\""+
	    rapsheetFile.getPath()    +"\">\n";

	int jsel = getJsel(dq, dcode);

	s+="<input type=\"hidden\" name=\""+NAME_JSEL+"\" value=\""+jsel+"\">\n";

	String vals[]= FORM_VALS;
	String texts[] = {"DQ", "Not DQ", "Not DQ: dismissed", "Not DQ: scope", 
			  "Need additional research"};

	String base =  fid + "." + actionID + "."+ chargeSeq;	
	s+="<select name=\"" + base + "\">\n";


	for(int j=0; j<vals.length; j++) {
	    String val =   vals[jsel] +  "." + vals[j];
	    boolean sel = (j==jsel);

	    if (sel) val += ".x";
	    s += "<option value=\"" + val + "\"";
	    if (sel) 	s += " selected";	    
	    s += ">" +texts[j]+ "</option>\n";
	}
	s+="</select>\n";

	String name = base + ".comment";
	s += " &nbsp; Comments: <input type=\"text\" name=\"" +name+ "\" size=\"90\">\n";
	s += "</form>\n";
	return s;
    }
    */
    
 
    public String colored(String col, String text) {
	if (col.equals("yellow")) col="orange"; // to be brighter
	return (color? "<span style=\"color:"+col+"\">" :  "<span>") +
	    text + "</span>";
    }

    public String html(String title, String body) {
	return wrap("html", wrap("head", wrap("title", title)) + "\n" + wrap("body", body));       
    }

    

    public String a(String url, String text, String extraClauses) {
	String s= "<a href=\"" + url + "\"";
	if (extraClauses!=null) s += " " +extraClauses;
	s += ">" + text + "</a>";
	return s;
    }
    
    public static Fmter htmlFmter = new HTMLFmter(null);
 
}
