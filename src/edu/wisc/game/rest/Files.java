package edu.wisc.game.rest;

import java.io.*;
import java.util.*;


/** Information about the data files the Rule Game web server reads and writes */
public class Files {

    static final File savedDir = new File("/opt/tomcat/saved");
    static final File inputDir = new File("/opt/tomcat/game-data");

    static private void testDir(File d) throws IOException {
	if (d.exists()) {
	    if (!d.isDirectory() || !d.canWrite())  throw new IOException("Not a writeable directory: " + d);
	} else {
	    if (!d.mkdirs())  throw new IOException("Failed to create directory: " + d);
	}
    }

    /** The file into which guesses by a given player are written */
    public static File guessesFile(String playerId) throws IOException {

	File d = new File(savedDir, "guesses");
	testDir(d);
	File f= new File(d, playerId + ".guesses.csv");
	return f;
    }

    /** The file into which the initial boards of all episodes played by a given player are written */
    public static File boardsFile(String playerId) throws IOException {

	File d = new File(savedDir, "boards");
	testDir(d);
	File f= new File(d, playerId + ".boards.csv");
	return f;
    }

    
    public static File transcriptsFile(String playerId) throws IOException {
	File d = new File(savedDir, "transcripts");
	testDir(d);
	return new File(d, playerId + ".transcripts.csv");
    }

    public static File detailedTranscriptsFile(String playerId) throws IOException {
	File d = new File(savedDir, "detailed-transcripts");
	testDir(d);
	return new File(d, playerId + ".detailed-transcripts.csv");
    }

    

    static File trialListMainDir() {
	return new File(inputDir, "trial-lists");
    }
    
}

    
