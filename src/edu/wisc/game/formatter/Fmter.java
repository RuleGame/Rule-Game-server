package edu.wisc.game.formatter;

import java.io.*;
import java.util.*;
 

/** Auxiliary class for formatting output as plain text of HTML.  */
public class  Fmter {

    public boolean html;
    public boolean color=true;
    
    public Fmter()  { 
	html = false;
    }

    public String style() { return "";}

    
    /** This only affects HTML, not plain text */
    public void setColor(boolean _color) {
	color = _color;
    }

    public String br() { 
       return "\n";
   }
    
    /** Generates a BR in HTML mode, and a space otherwise. Handy for
	breaks in table headers. */
    public String brHtml() {
	return " ";
    }


    public String hr() {
	return "---------------------------------------------------------\n";
    }

    public String hrThin() {
	return hr();
    }
    
    public String space() { return space(1); }
    public String space(int n) {
	String z = " ";
	StringBuffer b= new 	StringBuffer(6*n);
	for(int i=0;i<n; i++) b.append(z);
	return b.toString();
    }

    public String wrap(String wrap, String text) {
	return wrap(wrap, "", text);
    }

    public String wrap(String wrap, String extra, String text) {
	return text;
    }

    public String wrap2(String wrap, String text) {
	return wrap2(wrap, "", text);	
    }

    public String wrap2(String wrap, String extra, String text) {
	return wrap(wrap, extra, "\n" + text + "\n");
    }

    
    final public  String code(String text) {
	return wrap("code", text);
    }

    final public  String tt(String text) {
	return wrap("tt", text);
    }

    
    final public  String em(String text) {
	return wrap("em", text);
    }

    final public  String strong(String text) {
	return wrap("strong", text);
    }

    final public  String small(String text) {
	return wrap("small", text);
    }

    /** Font emphasis for texts that are "CHEETA inference"  */
    final public String ei(String text) {
	return text;
	//return em(text);
    }

    /** Font emphasis for text directly sourced from the rap sheet */
    final public String src(String text) {
	return wrap("span", "class=\"src\"", text);
    }

    
    final public  String button(String text) {
	return wrap("button", text);
    }

    public  String para(String text) {
	if (text==null || text.length()==0) return "";
	return wrap("p", text) + "\n";
    }

    public  String paraEi(String text) {
	return para(ei(text));
    }

    
    final public  String td(String text) {
	return wrap("td", text) + "\t";
    }
    final public  String th(String text) {
	return wrap("th", text) + "\t";
    }
    final public  String th(String extra, String text) {
	return wrap("th", extra, text) + "\t";
    }

    final public  String td(String extra, String text) {
	return wrap("td", extra, text) + "\t";
    }

    final public  String tr(String text) {
	return wrap("tr", text);
    }

    /** Generates a TABLE ... /TABLE structure.
	@param extra E.g. "border=\"1\""
	@param rows Each one is a TR ... /TR
     */
    final public  String table(String extra, String... rows) {
	return wrap("table", extra, String.join("\n", rows)) + "\n";
    }
    final public  String table(String extra, Vector<String> rows) {
	return wrap("table", extra, String.join("\n", rows)) + "\n";
    }

    final public  String h1(String text) {
	return wrap("h1", text) + "\n";
    }
    final public  String h2(String text) {
	return wrap("h2", text) + "\n";
    }
    final public  String h3(String text) {
	return wrap("h3", text) + "\n";
    }
    final public  String h4(String text) {
	return wrap("h4", text) + "\n";
    }
    final public  String h5(String text) {
	return wrap("h4", text) + "\n";
    }
    final public  String pre(String text) {
	return wrap("pre", text) + "\n";
    }

    /** A dummy plain text formatter. This can be used whenever you
	want plain-text formatting */
    static public Fmter dummy = new Fmter();

  

    public String a(String url, String text) {
	return  a(url, text, null);
    }

    public String a(String url, String text, String extraClauses) {
	return text;
    }

  

    public String colored(String color, String text) {
	return text;
    }


    public String row(Vector<String>  cols) {
	return row(cols.toArray(new String[0]));
    }
    public String rowExtra(String extra, Vector<String>  cols) {
	return rowExtra(extra,cols.toArray(new String[0]));
    }

    
    /** Generates a table row (a TR element).
	@param cols The content of columns (without the TD wrapping; the wrapping will be done in this method)
     */
    public String row(String... cols) {
	String s = "";
	for(String col: cols) s +=  td(col);
	return tr(s);
    }

    public String rowTh(String key, String tdExtra, String... cols) {
	String s = th("align='left'", key);
	for(String col: cols) s +=  td(tdExtra, col);
	return tr(s);
    }

    public String rowTh(String key, String tdExtra, Vector<String> cols) {
	return rowTh(key, tdExtra, cols.toArray(new String[0]));
    }

    
    public String rowExtra(String extra, String... cols) {
	String s = "";
	for(String col: cols) s +=  td(extra, col);
	return tr(s);
    }

    //    public static Fmter plaintText = new Fmter();


    public String html(String title, String body) {
	String h = wrap("title", title) + style();
	return html2( h, body);       
    }

    public String html2(String head, String body) {
	return wrap("html", wrap("head", head) + "\n" + wrap("body", body));       
    }


    public String sprintf(String fmt, Object... data) {
	StringWriter sw = new StringWriter();
	PrintWriter pw = new PrintWriter(sw);
	pw.format(fmt, data);
	return sw.toString();
    }

}
