package edu.wisc.game.util;

import java.io.*;
import java.util.*;
import java.text.*;
import java.lang.reflect.*;

import javax.servlet.*;
import javax.servlet.http.*;

import edu.wisc.game.reflect.*;

/** Various methods related to generating HTML forms and their
    components, and processing requests sent by the web browser when
    those forms are filled
 */
public class Tools {

    final static String NONE = "none";

    static String makeHmName(String name) {
	return name + "_hm";
    }

    /** Creates an "input type=hidden" HTML tag */
    public static String inputHidden(String name, boolean val) {
	return "<input type=\"hidden\" name=\""+name+"\" value=\""+val+"\">\n";
    }
    public static String inputHidden(String name, long val) {
	return "<input type=\"hidden\" name=\""+name+"\" value=\""+val+"\">\n";
    }
    public static String inputHidden(String name, String val) {
	return "<input type=\"hidden\" name=\""+name+"\" value=\""+val+"\">\n";
    }

    public static String inputText(String name) {
	return inputText(name, null, 0);
    }

    static String inputText(String name, long val) {
	return inputText(name, ""+val, 0);
    }

    /** Creates an 'input type=text' tag.
	@param val the value to display (if not null)
	@param size 0 means default
     */
    public static String inputText(String name, Object val, int size) {
	String s = "<input type=\"text\" name=\""+name+"\"";
	if (val!=null) s += " value=\""+val+"\"";
	if (size > 0) s += " size="+size;			  
	s += ">\n";
	return s;
    }

    public static String inputTextArea(String name, Object val, 
				       int rows, int cols) {
	String s = "<textarea name=\""+name+"\"";
	s += " rows="+rows;			  
	s += " cols="+cols;			  
	s += ">";
	if (val!=null) s += val;
	s +=  "</textarea>\n";
	return s;
    }

    public static String radio(String name, Object value, Object text, boolean selected) {
	return radioOrBox(name, "radio", value, text, selected);
    }

    public static String checkbox(String name, Object value, Object text, boolean selected) {
	return radioOrBox(name, "checkbox", value, text, selected);
    }

    /** Creates an HTML "input" element of the "radio" or "checkbox" type.
	@param type must be "radio" or "checkbox"
     */
    public static String radioOrBox(String name, String type, Object value, Object text, boolean selected) {
	return  radioOrBox( name,  type,  value,  text, selected,  null);
    }

    /** Creates an HTML "input" element of the "radio" or "checkbox" type.
	@param type must be "radio" or "checkbox"
	@param style Is meant to control presentation. For example, in a system providing canned scripts to telephone operators, items that the user needs to read aloud may be rendered differently.
     */
    public static String radioOrBox(String name, String type, Object value, Object text, boolean selected, String style) {
	String s = 
	    "<input type=\""+type+"\" name=\""+name+"\" value=\""+value+"\"" +
	    (selected? "  checked=\"checked\"/>" : "/>");
	if (text != null) {
	    s += text; //(style!=null)? Style.SPAN(style) +  text + "</SPAN>" : text;
	}
	s += "\n";
	return s;

    }


    /** Retrives an integer HTTP request parameter. If not found in
      the HTTP request, also looks in the attributes (which can be used
      by SurveyLogicServlet in case of internal redirect)
     */
    static public long getLong(HttpServletRequest request, String name, long defVal) {
	String s = request.getParameter(name);
	if (s==null) {
	    Long a = (Long)request.getAttribute(name);
	    return (a!=null) ? a.longValue() : defVal;
	}
	try {
	    return Long.parseLong(s);
	} catch (Exception ex) {
	    return defVal;
	}
    }

    static public double getDouble(HttpServletRequest request, String name, double defVal) {
	String s = request.getParameter(name);
	if (s==null) {
	    Double a = (Double)request.getAttribute(name);
	    return (a!=null) ? a.doubleValue() : defVal;
	}
	try {
	    return Double.parseDouble(s);
	} catch (Exception ex) {
	    return defVal;
	}
    }


    static public boolean getBoolean(HttpServletRequest request, String name, boolean defVal) {
	String s = request.getParameter(name);
	if (s==null) {
	    Boolean a = (Boolean)request.getAttribute(name);
	    return (a!=null) ? a.booleanValue() : defVal;
	}
	try {
	    return Boolean.parseBoolean(s);
	} catch (Exception ex) {
	    return defVal;
	}
    }

    static public <T extends Enum<T>> T  getEnum(HttpServletRequest request, Class<T> retType, String name,  T defVal) {
	String s = request.getParameter(name);
	if (s==null) {
	    return defVal;
	}
	try {
	    return Enum.valueOf(retType, s);
	} catch (Exception ex) {
	    return defVal;
	}
    }

    static public <T extends Enum<T>> Vector<T>  getEnums(HttpServletRequest request, Class<T> retType, String name) {
	Vector<T> v = new Vector<>();
	String[] ss = request.getParameterValues(name);
	if (ss==null) {
	    return v;
	}
	for(String s: ss) {
	    try {
		v.add( Enum.valueOf(retType, s));
	    } catch (Exception ex) {
	    }	    
	}
	return v;
    }

    static public String getString(HttpServletRequest request, String name, String defVal) {
	String s = request.getParameter(name);
	return  (s==null)? defVal : s;
    }


 

    /** Returns true if the time expressed by cal is at exactly h:m,
     * or within the next dm minutes (not inclusive)
     */
    static boolean timeInRange(Calendar cal, int h, int m, int dm) {
	int hNext=h, mNext = m + dm;
	if (mNext>=60) {
	    mNext=0;
	    hNext++;
	}
	Calendar t1 = new GregorianCalendar( cal.get(Calendar.YEAR),
					     cal.get(Calendar.MONTH),
					     cal.get(Calendar.DAY_OF_MONTH),
					     h, m),
	    t2 = new GregorianCalendar(cal.get(Calendar.YEAR),
				       cal.get(Calendar.MONTH),
				       cal.get(Calendar.DAY_OF_MONTH),
				       hNext, mNext);

	boolean result= (t1.compareTo(cal)<=0) && (cal.compareTo(t2)<0);
	if (result) Logging.info("" + t1 + " <= " + cal + " < " + t2);
	return result;
    }

    private static String option(Object value, Object text) {
	return option(value, text, false);
    }
    private static String option(Object value, Object text, boolean selected) {
	return  "<OPTION value=\"" + value + "\"" +
	    (selected?  " SELECTED>" : ">") +  text + "</OPTION>\n";	
    }

    /** Generates a SELECT HTML tage with OPTIONs for all values of a given
	enum type
	@param name name of the request parameter to be sent by the form due to this tag
	@param t an Enum type
	@param old Contains the default value
     */
    static String mkSelector(String name, Class t, Object old) {
	StringBuffer b = new StringBuffer();
	b.append("<SELECT NAME=\""+name+"\">\n");
	Object[] con = t.getEnumConstants();
	for(int j=0; j<con.length; j++) {
	    boolean selected = (old == null)? (j==0): (old==con[j]);
	    //	    String text = Util.getEA((Enum)con[j], con[j].toString());
	    String text = con.toString();
	    b.append(option( con[j], text, selected));
	}
	b.append("</SELECT>");
	return b.toString();
    }

    static String mkSelectorBoolean(String name, Object old) {
	StringBuffer b = new StringBuffer();
	b.append("<SELECT NAME=\""+name+"\">\n");
	boolean ov = (old==null)? false: ((Boolean)old).booleanValue();
	b.append(option("false", "No", !ov));
	b.append(option("true", "Yes", ov));
	b.append("</SELECT>");		    
	return b.toString();
    }

}
