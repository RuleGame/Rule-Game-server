package edu.wisc.game.rest;

import java.io.*;
import java.util.*;


/** Information about the data files the Rule Game web server reads and writes */
public class Files {

    static final File savedDir = new File("/opt/tomcat/saved");

    /** The file into which guesses by a given player are written */
    public static File guessesFile(String playerId) throws IOException {

	File d = new File(savedDir, "guesses");
	if (d.exists()) {
	    if (!d.isDirectory() || !d.canWrite())  throw new IOException("Not a writeable directory: " + d);
	} else {
	    if (!d.mkdirs())  throw new IOException("Failed to create directory: " + d);
	}
	File f= new File(d, playerId + ".guesses.csv");
	return f;
    }

    /** The file into which the initial boards of all episodes played by a given player are written */
    public static File boardsFile(String playerId) throws IOException {

	File d = new File(savedDir, "boards");
	if (d.exists()) {
	    if (!d.isDirectory() || !d.canWrite())  throw new IOException("Not a writeable directory: " + d);
	} else {
	    if (!d.mkdirs())  throw new IOException("Failed to create directory: " + d);
	}
	File f= new File(d, playerId + ".boards.csv");
	return f;
    }

    public static File transcriptsFile(String playerId) throws IOException {

	File d = new File(savedDir, "transcripts");
	if (d.exists()) {
	    if (!d.isDirectory() || !d.canWrite())  throw new IOException("Not a writeable directory: " + d);
	} else {
	    if (!d.mkdirs())  throw new IOException("Failed to create directory: " + d);
	}
	File f= new File(d, playerId + ".transcripts.csv");
	return f;
    }
    
}

    
