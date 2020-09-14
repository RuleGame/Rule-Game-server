package edu.wisc.game.reflect;

import java.util.*;
import java.text.*;
import java.lang.reflect.*;
import javax.persistence.*;

//import javax.json.*;

import edu.wisc.game.util.Logging;
import edu.wisc.game.sql.Role;

/** A bunch of methods to figure what fields a class has, and how to
 * print them out in a more or less sensible way.
 */
@SuppressWarnings("unchecked")
public class Reflect {

    public static final DateFormat sqlDf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static public String makeGetMethodName(String name) {
	String capName = name.substring(0,1).toUpperCase() + name.substring(1);
	return "get" + capName; 
    }
    static public String makeGetMethodName2(String name) {
	String capName = name.substring(0,1).toUpperCase() + name.substring(1);
	return "is" + capName; 
    }
    static public String  makeSetMethodName(String name) {
	String capName = name.substring(0,1).toUpperCase() + name.substring(1);
	return "set" + capName; 
    }

    /** An entry describes one field of the class, complete with its
     * access methods and the display hints
     */
    static public class Entry implements Comparable {
	public String name;
	public boolean editable, rp, payment;
	public Field f;
	public Method g, s;
	double order;
	public int 	compareTo(Object _o) {
	    if (!(_o instanceof Entry)) throw new IllegalArgumentException();
	    Entry o = (Entry)_o;
	    if (order == o.order) return 0;
	    else if (order == 0) return +1;
	    else if (o.order == 0) return -1;
	    else return (order  - o.order > 0) ? 1 : -1;
	}

	/** Returns true if this is an enum field which is stored in
	    the SQL database as a string (rather than int)
	*/
	private boolean enumAsString() {
	    Enumerated anno = (Enumerated)f.getAnnotation(Enumerated.class);
	    return anno!=null && anno.value()==EnumType.STRING;
	}

	/** The name of the field, or the alt value, if provided */
	String compactTitle() {
	    Display anDisplay = (Display)f.getAnnotation(Display.class);
	    if (anDisplay!=null) {
		if (anDisplay.alt()!=null && anDisplay.alt().length()>0) 
		    return anDisplay.alt();
	    } 
	    return  name;
	}

	/** The name of the field with the "explanation", or the alt value, if provided */
	String explainedTitle() {
	    return explainedTitle(false);
	}

	/** The name of the field with the "explanation", or the alt
	    value, if provided.
	@param html If true, some extra HTML formatting may be done.
	*/
	String explainedTitle(boolean html) {
	    Display anDisplay = (Display)f.getAnnotation(Display.class);
	    if (anDisplay!=null) {
		if (anDisplay.alt()!=null && anDisplay.alt().length()>0) 
		    return anDisplay.alt();
		if (anDisplay.text()!=null && anDisplay.text().length()>0) 
		    return name + (html? "<br><small>" : " ") +
			"("+anDisplay.text()+")" +
			(html? "</small>" : "");
	    } 
	    return  name;
	}

	/** Does this field store an MD5 digest, rather than the actual value?
	 */
	public boolean isDigest() {
	    Display anDisplay = (Display)f.getAnnotation(Display.class);
	    return (anDisplay!=null) && anDisplay.digest();
	}

	/** Class.field, e.g. "Respondent.first_name" */
	public String destName() {
	    return f.getDeclaringClass().getSimpleName()+"."+ f.getName();
	}
	
	

    }


    public Entry[] entries = null;
    private  HashMap<String,Entry> entryTable = new HashMap<String,Entry>();
    public Entry getEntry(String name) { return entryTable.get(name);}

    /** Finds the entry that describes the field whose type is the the
     * enumerated class for which e is one of the values. This method only 
     * makes sense to use if the class has only field with that enum type;
     * otherwise, an error will be thrown
     @return A matching Entry object, or null
     */
    public Entry getOwningEntry(Enum e) throws IllegalArgumentException {
	Class ec = e.getClass();
	Entry z = null;
	for(Entry entry: entries) {
	    if (ec.equals(entry.f.getType())) {
		if (z!=null) throw new IllegalArgumentException("The class has multiple fields of the enum type of "+e+": " + z.f +", " + entry.f);
		z = entry;
	    }
	} 
	return z;
    }

    private static HashMap<Class, Reflect> table = new HashMap<Class,Reflect>();
    /** Looks up or creates a Reflect instance for a specified class.

	@param c Class to analyze. We reduce it to an existing basic
	class, if possible (in case it is of an automatically derived
	type, such as
	org.apache.openjpa.enhance.edu.wisc.game.sql$Respondent$pcsubclass
	)
    */
    static public synchronized Reflect getReflect(Class c) {
	Class basics [] = {  //User.class, Action.class
	};
	for(Class b: basics) {
	    if (b.isAssignableFrom(c)) {
		c = b;
		break;
	    }
	}

	Reflect r = table.get(c);
	if (r==null) {
	    r = new Reflect(c);
	    table.put(c,r);
	}
	return r;
    }

    /** One can do it the other way: use getMethods() for all public methods...
     */
    private Reflect(Class c) {
	//	Logging.info("Reflect(" + c +"), has " + c.getFields().length + " fields, " + c.getDeclaredFields().length + " declared fields");
	Vector<Entry> v = new Vector<Entry>();

	for(;c!=null && c!=Object.class; c=c.getSuperclass()) {
	
	for(Field f: c.getDeclaredFields()) {
	    Entry e = new Entry();
	    e.f = f;
	    e.name = f.getName();
	    //Logging.info("Reflect(" + c +"), field named " + e.name);
	    String gn = makeGetMethodName(e.name),
		 gn2 = makeGetMethodName2(e.name),
		sn=makeSetMethodName(e.name);
	    e.g = e.s = null;
	    try {
		e.g =c.getMethod(gn);
		e.s =c.getMethod(sn, e.f.getType() );	      
	    } catch (Exception ex) { 	    }

	    if (e.g==null) {
		try {
		    e.g =c.getMethod(gn2);
		} catch (Exception ex) { 	    }
	    }
	    
	    if (e.g==null) {
		// Fields with no public getter are not shown. The setter,
		// OTOH, is optional
		continue;
	    }

	    if (e.g.getAnnotation(javax.xml.bind.annotation.XmlTransient.class)!=null) {
		// This annotation is used to prevent REST from converting a field
		// to JSON... so we should ignore it to. I use it to prevent
		// infinite looping on back links
		continue;
	    }
	    
	    Display anno = (Display)e.f.getAnnotation(Display.class);
	    e.editable = (anno!=null) && anno.editable(); // default no
	    e.rp = (anno!=null) && anno.rp(); // default no
	    e.payment = (anno!=null) && anno.payment(); // default no
	    e.order = (anno==null) ? 0 : anno.order();
	    v.addElement(e);
	    entryTable.put(e.name, e);
	}
	
	}
	entries = v.toArray(new Entry[v.size()]);
	Arrays.sort(entries);
	//	Logging.info("Reflect(" + c +") successful, e.length="+entries.length);	
    } 

    /** Prints all appropriate fields of the specified object in the default
	(toString) format
     */
    public static String reflectToString(Object o) {
	return reflectToString(o, true); 
    }

    
    /** Compact human readable format, with no extra quotes, for various
	HTML tables
    */
    public static String compactFormat(Object val) {
	String s;
	if (val==null) return "null";
	else if (val instanceof Date) {
	    s = sqlDf.format((Date)val);
	    final String suffix = " 00:00:00";
	    if (s.endsWith(suffix)) s = s.substring(0, s.length()-suffix.length());
	} else {
	    s = val.toString();
	}
	return s;
    }

    public static String reflectToString(Object o, boolean skipNulls) {
	
	StringBuffer b = new StringBuffer();
	Reflect r = Reflect.getReflect(  o.getClass());
	//Logging.info("Reflecting on " + o.getClass() +"; reflect=" + r + ", has " + r.entries.length + " entries");
	for(Reflect.Entry e: r.entries) {
	    if (o instanceof OurTable &&((OurTable)o).ignores(e.name)) continue;
	    Object val = null;
	    try {
		val = e.g.invoke(o);
	    } catch (IllegalAccessException ex) {
		Logging.error(ex.getMessage());
		val = "ACCESS_ERROR";
	    } catch (InvocationTargetException ex) {
		Logging.error(ex.getMessage());
		val = "INVOCATION_TARGET_ERROR";
	    }
	    if (skipNulls && val==null || val.toString().equals("")) continue;
	    if (skipNulls && e.name.equals("version")) continue;
	    b.append(e.name+"=" + compactFormat(val) +"; ");
	}
	return b.toString();
    }

    /** More pretty version of {@link #reflectToString(Object o)} */
    public static String  customizedReflect(Object o, PairFormatter f) {
	StringBuffer b = new StringBuffer();
	Reflect r = Reflect.getReflect(  o.getClass());

	// the rest of the fields
	for(Reflect.Entry e: r.entries) {
	    if (o instanceof OurTable &&((OurTable)o).ignores(e.name)) continue;
	    Object val = null;
	    try {
		val = e.g.invoke(o);
	    } catch (IllegalAccessException ex) {
		Logging.error(ex.getMessage());
		val = "ACCESS_ERROR";
	    } catch (InvocationTargetException ex) {
		Logging.error(ex.getMessage());
		val = "INVOCATION_TARGET_ERROR";
	    }
	    if (val==null || val.toString().equals("")) continue;
	    if (e.name.equals("version")) continue;

	    //	    if (o instanceof PhoneCall) {
	    //		if (e.name.equals("resume") && val.toString().equals("0")) continue;
	    //}

	    b.append(f.row(e.compactTitle(), Reflect.compactFormat(val)));
	}
	return b.toString();
    }


    public static String csvRow(Object o) {
	return csvRow(o, "\n");
    }

    /** Saves the object as a row of comma-separated file
	@param end The text to append to the end (CR, or "")
     */
    public static String csvRow(Object o, String end) {
	Vector<String> row = asStringVector(o, "\"");
	StringBuffer b = new StringBuffer();
	for(String s: row) {
	    if (b.length()>0) b.append(",");
	    b.append(s);
	}
	b.append(end);
	return b.toString();
    }

    public static String htmlRow(Object o, boolean TR) {
	return htmlRow(o, TR, true);
    }

    /** Returns a complete TR  element, or just a bunch of TD cells.
	@param TR Include the TR element
	@param dolinks Include hyperlinks to other pages on fields that have 
	the "link" value set in their "Display" attribute. This is useful
	in research pages, but (usually) not in user-facing pages.
     **/
    public static String htmlRow(Object o, boolean TR, boolean dolinks) {
	Vector<String> row = asStringVector(o, "", dolinks);

	StringBuffer b = new StringBuffer("");
	if (TR) b.append("<tr>");
	for(String s: row) {
	    b.append("<td>"+ s +"</td>");
	}
	if (TR) b.append("</tr>");
	return b.toString();
    }

   public static String htmlHeaderRow(Class c, boolean TR) {
	StringBuffer b = new StringBuffer("");
	if (TR) b.append("<tr>");
	Reflect r = Reflect.getReflect(c);
	for(Reflect.Entry e: r.entries) {
	    b.append("<th valign=\"top\">"+ e.explainedTitle(true) +"</th>");
	}
	if (TR) b.append("</tr>");
	return b.toString();
    }


    public static Vector<String>  asStringVector(Object o, String quote) {
	return asStringVector(o,quote,false);
    }

    /** @param quote The string to use for quotes (may be an empty string, if no quotes are needed)  
	@param dolinks If true, attention is paid to the link() attribute, converting some fields into hyperlinks
     */

    public static Vector<String>  asStringVector(Object o, String quote, boolean dolinks) {

	Vector<String> v = new Vector<String>();

	Reflect r = Reflect.getReflect(  o.getClass());
	
	for(Reflect.Entry e: r.entries) {
	    Object val = null;
	    try {
		val = e.g.invoke(o);
	    } catch (IllegalAccessException ex) {
		Logging.error(ex.getMessage());
		val = "ACCESS_ERROR";
	    } catch (InvocationTargetException ex) {
		Logging.error(ex.getMessage());
		val = "INVOCATION_TARGET_ERROR";
	    }
	    String q=formatAsString(val,quote);

	    // FIXME: the "<= 0" test must be generalized or made pragma-controlled
	    if (dolinks &&
		!((val instanceof Number) && ((Number)val).longValue()<=0)) {

		Display anDisplay = (Display)e.f.getAnnotation(Display.class);
		if (anDisplay!=null && anDisplay.link()!=null &&
		    !anDisplay.link().equals("")) {
		    // FIXME: must encode ID if it's not just a number...
		    String url = anDisplay.link();
	
		    if (!(url.endsWith("=")||url.endsWith("/"))) { 
			url += "?id="; 
		    }
		    url += q;
		    q = "<a href=\"" + url + "\">" + q + "</a>"; 
		}
	    }
	    v.addElement( q);
	}
	return v;
    }

    /** Formats a single field of an object.

	Note the somewhat peculiar treatment of boolean values in
	OpenJPA. If the object has been retreived with a query
	obtained with createQuery() (i.e., a JPQL query), then a
	boolean value will be retrieved as Boolean object. But if
	createNativeQuery() (over MySQL, at any rate) has been used -
	i.e., we have a SQL query - then boolean values will appear as
	strings, one character long, containing char(0) or char(1)!
	This is because in MySQL booleans are "synonyms for TINYINT(1)".
	http://dev.mysql.com/doc/refman/5.0/en/numeric-type-overview.html

	<P> Arrays and other collections are printed elementwise if it
	can be done consicely enough; otherwise, just element count
     */
    public static String formatAsString(Object val, String quote) {
	boolean needQuotes = 
	    !(val instanceof Enum || val instanceof Number || val instanceof Boolean);
	if (val==null) {
	    return "";
	}

	String s;
	if (val instanceof OurTable) s = "" + ((OurTable)val).getLongId();
	else if (val instanceof Date) {
	    s =  sqlDf.format((Date)val);
	} else if (val instanceof String) {
	    s = (String) val;
	    if (s.length()==1) {
		// special treatment is needed for booleans in native
		// (SQL) queries over MySQL, to make them human-readable
		char x = s.charAt(0);
		if (x==(char)0) s = "false";
		else if (x==(char)1) s = "true";
	    }
	} else if (val instanceof Collection) {
	    Collection col  = (Collection)val;
	    if (col.size()==0) s="[]";
	    else {
		int nullCnt = 0, objCnt=0;
		Class oc=null;
		boolean printable = true;

		String q = "";
		for(Object o: col) {
		    if (o==null) nullCnt++;
		    else {
			objCnt++;
			oc = (oc==null) ? o.getClass() : 
			    commonParent(oc, o.getClass());
			printable = printable &&
			    (o instanceof Enum || o instanceof Number || o instanceof Boolean || o instanceof String ||
			     o instanceof Role);
		    }
		    if (printable) {
			q += (q.length()>0? " ":"") + formatAsString(o,  quote);
		    }
		}
		if (printable) {
		    s= "[" + q + "]";
		} else {
		    s = "[";
		    if (objCnt>0)  s += "" + objCnt + " " + 
				   (oc.equals(Object.class)? "objects" : "x " + oc.getName());
		    if (nullCnt>0)  s += " " + nullCnt + " x null";
		    s += "]";
		}
	    }
	} else {
	    // FIXME: there should be a better way to escape double quotes
	    s = // "OTHER["+val.getClass()+"]:" + 
		val.toString().replace('"', '\'');		
	}
	
	if (needQuotes) s = quote + s + quote;
	return s;
    }

    private static Class commonParent(Class a, Class b) {
	Vector<Class> vb= new 	Vector<Class>();
	for(Class z=b; z!=null; z=z.getSuperclass()) {
	    if (a.equals(z)) return a;
	    vb.add(z);
	}
	Vector<Class> va= new 	Vector<Class>();
	for(Class z=a; z!=null; z=z.getSuperclass()) {
	    va.add(z);
	}
	int jb = vb.size(), ja=va.size();
	Class common = null;
	while(jb>0 && ja>0) {
	    if (vb.elementAt(--jb).equals(va.elementAt(--ja))) 
		common=vb.elementAt(jb);
	    else break;
	}
	return common;
    }


    /** Saves the class description as the header line of a comma-separated file
     */
    public static String csvHeader(Class c) {	
	StringBuffer b = new StringBuffer();

	Reflect r = Reflect.getReflect(c);
	
	for(Reflect.Entry e: r.entries) {
	    if (b.length()>0) b.append(",");
	    b.append( e.name);
	}
	return b.toString();
    }

    /** Returns the array of field names */
    public static String[] getNames(Class c) {	
	Reflect r = Reflect.getReflect(c);
	String a[] = new String[r.entries.length];
	int i=0;
	for(Reflect.Entry e: r.entries) {
	    a[i++] = e.name;
	}
	return a;
    }

    /** FIXME: should get info from the @Entity annotation instead */
    static String getTableName(Object val) {
	//if (val instanceof Respondent) return "Respondent";
	//else if (val instanceof PhoneCall) return "PhoneCall";
	//else if (val instanceof Response) return "Response";
	//else
	    throw new IllegalArgumentException("Don't know what table stores objects of the type " + val.getClass() + ". It's time to learn about the 'Entity' annotation");
    }

    // FIXME: there should be a better way to escape special chars
    private static String  escapeStringForSQL(String s) {
	StringBuffer b = new StringBuffer(s.length());
	for(int i=0; i<s.length(); i++) {
	    char x = s.charAt(i);
	    if (x == '\'') b.append("''");
	    else if (Character.isWhitespace(x)) b.append(" ");
	    else if (x=='%' || x=='\\') b.append( "\\" + x);
	    else b.append(x);
	}
	return b.toString();
    }
    
    /** Saves the object as a MySQL "INSERT" statement
     */
    public static String saveAsInsert(Object o) {
	
	Reflect r = Reflect.getReflect(  o.getClass());

	StringBuffer b = new StringBuffer("INSERT INTO " + getTableName(o) );

	StringBuffer names = new StringBuffer(), values = new StringBuffer();

	for(Reflect.Entry e: r.entries) {

	    Object val = null;
	    try {
		val = e.g.invoke(o);
	    } catch (IllegalAccessException ex) {
		Logging.error(ex.getMessage());
		val = "ACCESS_ERROR";
	    } catch (InvocationTargetException ex) {
		Logging.error(ex.getMessage());
		val = "INVOCATION_TARGET_ERROR";
	    }
	    if (val==null) continue;

	    if (names.length()>0) names.append(",");

	    // the name of SQL table column
	    String name = e.name;
	    if (val instanceof OurTable) name += "_id";
	    names.append(name);

	    boolean needQuotes = 
		(val instanceof Enum) ? e.enumAsString() :
		(val instanceof String || val instanceof Date);

	    if (values.length()>0) values.append(",");

	    if (needQuotes) values.append("'");
	    String s;
	    if (val instanceof OurTable) s = "" + ((OurTable)val).getLongId();
	    else if (val instanceof Number) s = val.toString();
	    else if (val instanceof Boolean) s = ((Boolean)val).booleanValue()? "1":"0";
	    else if (val instanceof Enum) {
		s = e.enumAsString()? val.toString() : ""+((Enum)val).ordinal();
	    } else if (val instanceof Date) {
		s = sqlDf.format((Date)val);
	    } else if (val instanceof String) {
		// FIXME: there should be a better way to escape double quotes
		s = escapeStringForSQL((String)val);
	    } else {
		throw new IllegalArgumentException("Data type " + val.getClass() + " is not supported. Field = " + e.name);
	    }
	    values.append(s);
	    if (needQuotes) values.append("'");
	}
	b.append( " ("+names+") VALUES ("+values+")");
	return b.toString();
    }


}
