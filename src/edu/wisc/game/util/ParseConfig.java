package edu.wisc.game.util;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;


/**
 * 
 * This class is used to obtain configuration parameters, from a configuration
 * file or from Java System Properties.  If this is an applet,
 * SecurityException is caught safely.
 * 
 * @author Qin Shi
 * @author Vladimir Menkov
 * //@date 1999-2018
 */

public final class ParseConfig extends Hashtable<String,Object> {
	final static String prefix = ""; // "Ant."

	/**
	 * Creates an empty hashtable. That can be used simply as a convenient interface for accessing Java system options.
	 */
	public ParseConfig() {
	}

	/**
	 * Creates a hashtable that contains the parsed contents of the specified configuration file.
	 * @param aFname Configuration file name.	 
	 */

	public ParseConfig(String aFname) throws FileNotFoundException, IOException {
		this(new FileReader(aFname));
	}

	/**
	 * Creates a hashtable that contains the parsed data obtained from an open reader (which may, for example, be associated with an open file), 
	 * and then closes the reader.
	 * <p>
	 * The configuration file syntax:
	 * <ul>
	 * <li> Lines (or "tails" of lines) beginning with a '#' are comments, and
	 * are ignored
	 * <li> Blank lines are ignored
	 * <li> A line of the form
	 * <pre>
	 * 	name  value
	 * </pre>
	 * or
	 * <pre>
	 * 	name = value
	 * </pre>
	 * assigns a value to the named variable. The equal sign is optional. There can be a semicolon at the end of the line, but it's optional.
	 * The value may be a number (with no quotes), or a string surrounded by double quotes. If the string consists only of alphanumeric characters,
	 * with possible '/' and ':' chars, then quotes are optional too.
	 * </ul>
	 * <p>
	 * A ParseConfig structure is created by reading a specified configuration file. 
	 * The values of parameters stored in the table can be accessed by using accessor methods, such as getOption or getOptionDouble.
	 * <p>
	 * This method throws various exceptions, so that the caller method could produce a meaningful error report.
	 * @param in A Reader (a file reader, etc.)
	 */
	public ParseConfig(Reader in) throws IOException {
		// create an underlying hash table
		super(20);
		String param = "";
		String lastName = "N/A";

		try {
			StreamTokenizer token = new StreamTokenizer(in);

			// Semicolns are completely and utterly ignored
			token.whitespaceChars((int) ';', (int) ';');

			// These characters often appear in URLs. 
			// They should be treated as word chars, so that URLs would be "words" and wouldn't need to be quoted
			token.wordChars((int) '/', (int) '/');
			token.wordChars((int) ':', (int) ':');
			token.wordChars((int) '.', (int) '.');
			token.wordChars((int) '_', (int) '_');

			// Comments begin with a '#', not '//'
			token.slashSlashComments(false);
			token.commentChar('#');
			token.eolIsSignificant(false);

			// read the name
			while (token.nextToken() != StreamTokenizer.TT_EOF) {
				String name = "";
				if (token.ttype == StreamTokenizer.TT_WORD) {
					name = token.sval;
					lastName = name;
				} else {
					throw new IOException("Syntax error in config file: A WORD token expected for a parameter name. The last parmeter read was `" + lastName + "'");
				}

				// read the value 
				if (token.nextToken() == StreamTokenizer.TT_EOF) {
					throw new IOException("Syntax error in config file: No value for" + name);
				}

				if (token.ttype == (int) '=') {
					// This just was an optional equal sign. The value must be * in the next token.
					if (token.nextToken() == StreamTokenizer.TT_EOF) {
						throw new IOException("Syntax error in config file: No value found for"	+ name);
					}
				}

				Object value = null;
				if (token.ttype == StreamTokenizer.TT_WORD || token.ttype == '"') {
					// a String 
					value = token.sval;
				} else if (token.ttype == StreamTokenizer.TT_NUMBER) {
					// A number
					value = new Double(token.nval);
				} else {
					System.err.println("Syntax error in config file: unexpected value token type " + token.ttype);
					continue;
				}
				
				// store in the hashtable
				put(name, value);
			}
		}
		finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
				}
			}
		}
	}

	/**
	 * Looks up the system property. 
	 * Returns the value, or null if the property is not found or if look up fails with a security exception because we're in an applet.
	 */
	private String getPropertySafe(String name) {
		String property = null;
		try {
			property = System.getProperty(name);
		} catch (SecurityException e) {
			// We must be in an applet, and system properties are not available. Ignore the problem.
		}
		return property;
	}

	/**
	 * Gets the requested value from the hash table or from the Java system property aName.
	 * The Java system property, if given, overrides the value from the hash table.
	 */
	public String getOption(String aName, String aDefault) {
		String value = aDefault;
		Object obj = get(aName);
		
		if (obj != null) {
		    //System.out.println("get("+aName + ")=" + obj);
			if (obj instanceof String)
				value = (String) obj;
			else if (obj instanceof Number) {
				String msg = "Property `" + aName + "' read from the config file " + "should be a string, not a number! Ignoring.";
				System.err.println(msg);
			} else {
				String msg = "Property `" + aName + "' read from the config file " + "is not a String";
				System.err.println(msg);
			}
		} else {
		    //System.out.println("get("+aName + ") gives null, use default");
		}
		
		String property = getPropertySafe(prefix + aName);
		if (property != null) {
		    //System.out.println("getPS("+aName + ")=" + property);
		    value = property;
		} else {
		    //System.out.println("getPS("+aName + ") gives null, use default");
		}
		return value;
	}

	/**
	 * Gets the requested double value from the hash table or from the Java system property aName.
	 */
	public double getOptionDouble(String aName, double aDefault) {
		double value = aDefault;
		Object obj = get(aName);
		if (obj != null) {
			if (obj instanceof Number)
				value = ((Number) obj).doubleValue();
			else {
				String msg = "Property `" + aName + "' read from the config file " + "is not a number! Ignored.";
				System.err.println(msg);
			}
		}
		String property = getPropertySafe(prefix + aName);
		if (property != null)
			value = Double.parseDouble(property);
		return value;
	}

	/**
	 * Gets the requested integer value from the hash table or from the Java system property aName.
	 */
	public int getOption(String aName, int aDefault) {
		int value = aDefault;
		Object obj = get(aName);
		if (obj != null) {
			if (obj instanceof Number)
				value = ((Number) obj).intValue();
			else {
				String msg = "Property `" + aName + "' read from the config file " + "is not a number! Ignored.";
				System.err.println(msg);
			}
		}
		String property = getPropertySafe(prefix + aName);
		if (property != null)
			value = Integer.parseInt(property);
		return value;
	}

	public long getOptionLong(String aName, long aDefault) {
	    long value = aDefault;
	    Object obj = get(aName);
	    if (obj != null) {
		if (obj instanceof Number)
		    value = ((Number) obj).longValue();
		else {
		    String msg = "Property `" + aName + "' read from the config file " + "is not a number! Ignored.";
		    System.err.println(msg);
		}
	    }
	    String property = getPropertySafe(prefix + aName);
	    if (property != null)
		value = Long.parseLong(property);
	    return value;
	}


	/**
	 * Gets the requested integer value from the hash table or from the Java system property aName.
	 */
	public boolean getOption(String aName, boolean aDefault) {
		boolean value = aDefault;
		Object obj = get(aName);
		if (obj != null) {
			if (obj instanceof String) {
				String v = (String) obj;
				value = (new Boolean(v)).booleanValue();
			} else {
				String msg = "Property `" + aName + "' read from the config file " + "is not a boolean! Ignored.";
				System.err.println(msg);
			}
		}
		String property = getPropertySafe(prefix + aName);
		if (property != null)
			value = (new Boolean(property)).booleanValue();
		return value;
	}

	/** Gets a date parameter, in the format YYYY-MM-DD
	   @param aDefault Default date, in format 'YYYY-MM-DD' 
	*/
	public Date getOptionDate(String aName, String aDefault) throws java.text.ParseException {
	    String x= getOption(aName, aDefault);
	    if (x==null) return null;
	    final DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
	    return fmt.parse(x);
	}

	/** Returns the value of the specified parameter if it can be
	    interpreted as a value of the specified enumerated type.
	    If no parameter with the specified value has been supplied,
	    or if its value cannot be interpreted as a value of the desired
	    type, the supplied default value is returned
	    @param defVal The default value to be returned. May be null.
	 */

	public  <T extends Enum<T>> T getOptionEnum(Class<T> retType, String aName, T defVal) {	    

	    Object obj = get(aName);
	    if (obj != null && obj.getClass() == retType) {
		return (T)obj;
	    }
	    
	    String x=  (obj instanceof String)? (String)obj:
		getOption(aName, null);
	    if (x==null) return defVal;
	    try {
		return Enum.valueOf(retType, x);
	    } catch (Exception ex) {
		return defVal;			
	    }

	}

	/** Reads a comma-separated array of enums. E.g. -Dcolors=RED,GREEN,BLUE */
	public  <T extends Enum<T>> T[] getOptionEnumArray(Class<T> retType, String aName, T[] defVal) {
	    String x= getOption(aName, null);
	    if (x==null) return defVal;
	    try {
		String q[] = x.split(",");

		//System.err.println("Got options: q=" + Util.join(";", q));

		T[] z = (T[])java.lang.reflect.Array.newInstance(retType, q.length);
		
		//T[] z = new T[q.length];
		for(int i=0; i<q.length; i++) {
		    z[i] = Enum.valueOf(retType, q[i]);
		}
		//System.err.println("Got options: z=" + Util.join(";", q));
		return z;
	    } catch (Exception ex) {
		Logging.error("getOptionEnumArray(" + aName + ") error on x=" + x +"\n" + ex.getMessage());
		return defVal;
	    }
	}


	public boolean containsKey(String aName) {	    
	    return super.containsKey( aName) ||
		getPropertySafe(prefix + aName) !=null;
	}

	/**
	 * Gets the requested value from the hash table. If the value is not found, IOException is thrown.
	 */
	public String getParameter(String aName) throws IOException {
	    String value = null;
	    Object obj = get(aName);
	    if (obj != null) {
		if (obj instanceof String)
		    return (String) obj;
		else if (obj instanceof Number)
		    return "" + ((Number) obj).intValue();
		else {
		    throw new IOException("Invalid type for parameter " + aName);
		}
	    } else {
		throw new IOException("Missing parameter " + aName);
	    }
	}

	public long getLong(String name, long defVal) {
	    return getOptionLong(name, defVal);
	}
	public int getInt(String name, int defVal) {
	    return getOption(name, defVal);
	}
	public double getDouble(String name, double defVal) {
	    return getOptionDouble(name, defVal);
	}
	public String getString(String name, String defVal) {
	    return getOption(name, defVal);
	}

	public boolean getBoolean(String name, boolean defVal) {
	    return getOption(name, defVal);
	}

	public  <T extends Enum<T>> T getEnum(Class<T> retType, String name, T defVal) { 
	    return getOptionEnum(retType, name, defVal);
	}


    /** Scans the argv array, identifying all elements of the form X=Y.
        For each such element, adds the (key,value) pair (X,Y) to this
	ParseConfig table.
	@return An array that contains all other elements from argv
	(those not of the form X=Y).
     */
    public String[] enrichFromArgv(String [] argv) {
	Vector<String> v = new Vector<>();
	Pattern p = Pattern.compile("([a-zA-Z_][a-zA_Z_\\.]*)=(.*)");
	for(String s: argv) {
	    Matcher m = p.matcher(s.trim());
	    if (m.matches()) {
		String key=m.group(1), val=m.group(2);
		put(key, val);
	    } else {
		v.add(s);
	    }
	}
	return v.toArray(new String[0]);
    }
    

	/** 
	 * Purely for testing.
	 */
	static public void main(String argv[]) throws FileNotFoundException, IOException {
		for (int i = 0; i < argv.length; i++) {
			System.out.print("Reading " + argv[i]);
			ParseConfig ht = new ParseConfig(argv[i]);
			for (String name:  ht.keySet()) {
				Object value = ht.get(name);
				System.out.print("h[" + name + "] = ");
				if (value instanceof Number) {
					System.out.println(" number(" + ((Number) value).doubleValue() + ")");
				} else if (value instanceof String) {
					System.out.println(" string(" + (String) value + ")");
				}
			}
		}
	}
}

/*
Copyright 2009, Rutgers University, New Brunswick, NJ.

All Rights Reserved

Permission to use, copy, and modify this software and its documentation for any purpose 
other than its incorporation into a commercial product is hereby granted without fee, 
provided that the above copyright notice appears in all copies and that both that 
copyright notice and this permission notice appear in supporting documentation, and that 
the names of Rutgers University, DIMACS, and the authors not be used in advertising or 
publicity pertaining to distribution of the software without specific, written prior 
permission.

RUTGERS UNIVERSITY, DIMACS, AND THE AUTHORS DISCLAIM ALL WARRANTIES WITH REGARD TO 
THIS SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR 
ANY PARTICULAR PURPOSE. IN NO EVENT SHALL RUTGERS UNIVERSITY, DIMACS, OR THE AUTHORS 
BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER 
RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, 
NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR 
PERFORMANCE OF THIS SOFTWARE.
*/
