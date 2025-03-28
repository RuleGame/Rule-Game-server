package edu.wisc.game.tools;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.*;
import java.sql.*;

import edu.wisc.game.util.*;
import edu.wisc.game.rest.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.saved.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.parser.RuleParseException;

import jakarta.json.*;


/** This is a substitute for something like this:
<pre>
select * into outfile '/var/lib/mysql-files/tmp-PlayerInfo.csv'   FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'   LINES TERMINATED BY '\n'   FROM game.PlayerInfo;
</pre>
It is to be used on machines where the MySQL server is not set up with permissions to write to any directory (i.e. @@secure_file_priv is NULL).

<P>Unlike most of other database-connecting tools, this class uses the plain JDBC Connection, rather than JPA. This is done so that we can send over plain SQL commands, such as "SELECT * FROM ...", and to access the ResultSet's metadata, finding out the names of the columns in the result. The point of this exercise is to be able to print the table header (column names) into the output CSV file.
*/
public class ExportTable {

    static private void usage() {
	usage(null);
    }
    static private void usage(String msg) {
	//System.err.println("For usage info, please see:\n");
	//System.err.println("http://rulegame.wisc.edu/w2020/analyze-transcripts.html");
	System.err.println("Usage: ExportTable tableName outputFileName:\n");
	if (msg!=null) 	System.err.println(msg + "\n");
	System.exit(1);
    }

    /** Gets information about the database name, password, etc from
	the MainConfig structure, and creates a traditional JDBC
	connection to the database, without using JPA at all. */	
    static Connection getConnection() throws SQLException {

	Properties prop = System.getProperties();
	Hashtable<Object,Object>  h = (Hashtable<Object,Object>) prop.clone();

	Connection conn = null;

	String database = MainConfig.getString("JDBC_DATABASE", null);
	if (database==null) throw new IllegalArgumentException("JDBC_DATABASE not specified in the config file");
	String url = "jdbc:mysql://localhost/"+database;
	    
	String user  = MainConfig.getString("JDBC_USER", null);
	if (user==null) throw new IllegalArgumentException("JDBC_USER not specified in the config file");
       
	String pwd = MainConfig.getString("JDBC_PASSWORD", null);
	if (pwd==null) throw new IllegalArgumentException("JDBC_PASSWORD not specified in the config file");

	Properties connectionProps = new Properties();
	connectionProps.put("user", user);
	connectionProps.put("password", pwd);
	connectionProps.put("serverTimezone", "UTC");
	    
	conn = DriverManager.getConnection(url, connectionProps);
	
	//System.out.println("Connected to database");
	return conn;
    }

    
    public static void main(String[] argv) throws Exception {

	String table = null;
	String outFile = null;
	String config = null;
	
	Vector<String> plans = new Vector<>();
	Vector<String> pids = new Vector<>();
	Vector<String> nicknames = new Vector<>();
	Vector<Long> uids = new Vector<>();

	String qs=null;
	//int used = 0;
	int j=0;
	for(; j<argv.length; j++) {
	    String a = argv[j];
	    if (j+1< argv.length && a.equals("-config")) {
		config = argv[++j];
	    } else if (j+1< argv.length && a.equals("-query")) {
		qs = argv[++j];
	    } else {
		break;
		//if (used == 0) table = argv[j];
		//else if (used == 1)  outFile = argv[j];
		//else usage("Too many arguments");
		//used ++;
	    }
	}

	if (qs==null) {
	    if (j<argv.length) table=argv[j++];
	    else usage("Neither query nor table specified");
	    if (table.startsWith("-")) usage("Invalid query or table name: " + table);

	    if (table.indexOf(" ")>=0) {
		// A string with spaces in it is understood as a query, rather than a table name
		qs = table;
		table=null;
	    } else {
		qs = "select * from "+table;
	    }

	}



	
	if (j<argv.length) outFile=argv[j++];
	else usage("Output file not specified");
	if (outFile.startsWith("-")) usage("Invalid file name: " + table);

	if (j<argv.length) usage("Too many arguments");
	
	//if (outFile==null) usage("Some arguments have not been provided");

	if (config!=null) {
	    // Instead of the master conf file in /opt/w2020, use the
	    // customized one, so that a different database can be used
	    MainConfig.setPath(config);
	}

	File f = new File(outFile);
	doQuery(qs, f);
    }


    static public void doQuery(String sql, File f) throws IOException, SQLException {
	String[] queries = sql.split(";");
	doQuery(queries, f);
    }

    /** Executes a query, or several statements the last of which is a query.
	Prints out the result of the last query into a CSV file.
     */
    static public void doQuery(String[] queries, File f) throws IOException, SQLException {

	Connection conn  = getConnection();
	doQuery2(conn, queries, f);
	conn.close();
	
    }

    /** This one just sends commands to an existing connection, without closing it */
    static public void doQuery2(Connection conn, String[] queries, File f) throws IOException, SQLException {
	
	Statement stmt = conn.createStatement();

	for(int j=0; j<queries.length-1; j++) {
	    String qs = queries[j];
	    //System.out.println("Preliminary statement: " + qs);
	    stmt.execute(qs);
	}

	String qs = queries[queries.length-1];
	//System.out.println("Query: " + qs);
	PrintWriter w = new PrintWriter(new FileWriter(f));
       
	ResultSet rs = stmt.executeQuery(qs);
	ResultSetMetaData rsmd = rs.getMetaData();
	Vector<String> names = new Vector<>();
	int ncol = rsmd.getColumnCount();
	for(int j=0; j<ncol; j++) {
	    String name = rsmd.getColumnName(j+1);
	    names.add(name);
	}
	w.println("#" + ImportCSV.escape( names.toArray(new String[0])));

	while (rs.next()) {
	    Vector<String> v = new Vector<>();
	    for(int j=0; j<ncol; j++) {
		String s = rs.getString(j+1);
		v.add(s);
	    }
	    w.println( ImportCSV.escape( v.toArray(new String[0])));
	}
	w.close();
	stmt.close();
    }

    
}
