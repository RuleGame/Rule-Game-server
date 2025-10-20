package edu.wisc.game.util;

import java.io.*;
import java.util.*;
//import java.util.regex.*;
//import java.text.*;

/** Auxiliary methods for files and directories */
public class Util {

    /** Creates index.html in the specified directory, and all subdirectories */
    static public void mkIndexes(File dir) {
	File[] files = dir.listFiles();
	Vector<File> subdirs = new Vector<>();
	Vector<String> v = new  Vector<>(), v2 = new  Vector<>();
	v.add("<p>Content of " + dir);
	v.add("<ul>");
	for(File f: files) {
	    if (f.isDirectory() && !f.startsWith(".")) {
		subdirs.add(f);
		v.add("<li><a href=\"" + f.getName() + "\">");
	    } else if (f.isFile() && !f.equals("index.html")) {
		v2.add("<li><a href=\"" + f.getName() + "\">");
	    }
	}
	v.addAll(v2);
	v.add("</ul>");
	File g = new File(dir, "index.html");
	Util.writeTextFile(g, Util.joinNonBlank("\n", v));
	// recurse to subdirectories
	for(File f: subdirs) mkIndexes(f);
    }

}
