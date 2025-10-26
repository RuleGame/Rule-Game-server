package edu.wisc.game.util;

import java.io.*;
import java.util.*;

/** Auxiliary methods for files and directories */
public class FileUtil {

    /** Creates index.html in the specified directory, and all subdirectories */
    static public void mkIndexes(File dir) throws IOException {
	File[] files = dir.listFiles();
	Vector<File> subdirs = new Vector<>();
	Vector<String> v = new  Vector<>(), v2 = new  Vector<>();
	v.add("<p>Content of " + dir);
	v.add("<ul>");
	for(File f: files) {
	    String name = f.getName();

	    String li = "<li><a href=\"" + name + "\">" + name + "</a>";
	    
	    if (f.isDirectory() && !name.startsWith(".")) {
		subdirs.add(f);
		v.add(li);
	    } else if (f.isFile() && !name.equals("index.html")) {
		v2.add(li);
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
