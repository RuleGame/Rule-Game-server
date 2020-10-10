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

    /** @param  ruleSetName Either a complete absolute path name ("/home/vmenkov/foo.txt") starting with a slash, or just a file name without the extension ("foo"). In the later case, the file is assumed to be in the standard rules directory.
     */
    public static File rulesFile(String ruleSetName) throws IOException {
	if (ruleSetName==null || ruleSetName.equals("")) throw new IOException("Rule set name not specified");
	return inputFile(ruleSetName, "rules", ".txt");
   }

    public static File initialBoardFile(String boardName ) throws IOException {
	if (boardName==null || boardName.equals("")) throw new IOException("Board name not specified");
	return inputFile(boardName, "boards", ".json");
    }

    private static File inputFile(String name, String subdir, String ext) throws IOException {
	if (name==null || name.equals("")) throw new IOException("File name not specified");
	if (name.startsWith("/")) {
	    return new File(name);
	} else {     
	    File base = new File(inputDir, subdir);
	    if (!name.endsWith(ext)) name += ext;
	    return new File(base, name);
	}
    }

    /** Lists all rules files, or boards files, etc in a directory, without 
     extensions. */
    static Vector<String> listInputs( String subdir, String ext) throws IOException {
	File d = new File(inputDir, subdir);

	File[] files = d.listFiles();
	Vector<String> v = new Vector<String>();
	for(File cf: files) {
	    if (!cf.isFile()) continue;
	    String fname = cf.getName();
	    if (!fname.endsWith(ext)) continue;
	    v.add( fname.substring(0, fname.length()-ext.length()));
	}
	return v;	
    }
    
}

    
