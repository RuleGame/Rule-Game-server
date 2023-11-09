package edu.wisc.game.rest;

import java.io.*;
import java.util.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.Piece;
import edu.wisc.game.web.LaunchRulesBase;

/** Information about the data files the Rule Game web server reads and writes */
public class Files {

    /** The place where the Game Server saves transcripts etc.
	Normally, this is always the same location; but, for example,
	an analysis script may use an archive copy of these data from
	a different location.

	We may also adjust the paths if we're on a DoIT shared hosting host (with a chrooted shell).

     */
    static File savedDir =  MainConfig.getFile("FILES_SAVED", "/opt/tomcat/saved");
    static File inputDir = MainConfig.getFile("FILES_GAME_DATA", "/opt/tomcat/game-data");


    /** Sets the path to the input directory, which is the root
	of the tree that contains the experiment control files.
	You only need to use this method if you want trial list 
	files, rule set files, etc. to be read from directories
	in a directory tree other than the default ("/opt/tomcat/game-data").
	So, for example, the Captive Game Server may use this method. If you use
	it, do so early in your application.
	@param path E.g. "/opt/tomcat/game-data" 
    */
    static public void setInputDir(String path) {
	inputDir = new File(path);
	//System.out.println("DEBUG: inputDir := " + inputDir);
    }

    /** Sets the path to the saved-data directory. The Game Server
	itself would never need to do that; however, one can
	imagine an analysis script running on a different computer
	(e.g., you use your desktop computer to analyze the data
	accumulated at the main server), in which case it may
	read the transcript etc. data to be analyzed from a different
	location.
     */
    static public void setSavedDir(String path) {
	savedDir = new File(path);
	//System.out.println("DEBUG: inputDir := " + inputDir);
    }

    
    /** Checks the existence of a directory, and, if necessary, tries
	to create it */
    static void testWriteDir(File d) throws IOException {
	if (d.exists()) {
	    if (!d.isDirectory() || !d.canWrite())  throw new IOException("Not a writeable directory: " + d);
	} else if (!d.mkdirs()) {
	    throw new IOException("Failed to create directory: " + d);
	}
    }

    /** The file into which guesses by a given player are written */
    public static File guessesFile(String playerId) throws IOException {

	File d = new File(savedDir, "guesses");
	testWriteDir(d);
	File f= new File(d, playerId + ".guesses.csv");
	return f;
    }

    /** The file into which the initial boards of all episodes played by a given player are written */
    public static File boardsFile(String playerId) throws IOException {
	return boardsFile(playerId, false);
    }
    
    public static File boardsFile(String playerId, boolean readOnly) throws IOException {

	File d = new File(savedDir, "boards");
	if (!readOnly) testWriteDir(d);
	File f= new File(d, playerId + ".boards.csv");
	return f;
    }

   
     public static File transcriptsFile(String playerId) throws IOException {
	 return transcriptsFile(playerId,false); 
    }

    public static File transcriptsFile(String playerId, boolean readOnly) throws IOException {
	File d = new File(savedDir, "transcripts");
	if (!readOnly) testWriteDir(d);
	return new File(d, playerId + ".transcripts.csv");
    }

    public static File detailedTranscriptsFile(String playerId) throws IOException {
	File d = new File(savedDir, "detailed-transcripts");
	testWriteDir(d);
	return new File(d, playerId + ".detailed-transcripts.csv");
    }

    

    static File trialListMainDir() {
	return new File(inputDir, "trial-lists");
    }

    public static File shapesDir() {
	return new File(inputDir, "shapes");
    }

    /** The upload directory for a particular MLC participant. It is created if it does not exist yet */
    public static File mlcUploadDir(String nickname, boolean readOnly) throws IOException {

	File d = new File(savedDir, "mlc");
	if (!readOnly) testWriteDir(d);
	File m = new File(d, nickname);
	if (!readOnly) testWriteDir(m);
	return m;
    }

   
    /** Append this to convert a rule set name to the file name (relative
	to the main rules directory, of course) */
    static final String RULES_EXT = ".txt";
    
    /** @param  ruleSetName Either a complete absolute path name ("/home/vmenkov/foo.txt") starting with a slash, or just a file name without the extension ("foo"). In the later case, the file is assumed to be in the standard rules directory.
     */
    public static File rulesFile(String ruleSetName) throws IOException {
	if (ruleSetName==null || ruleSetName.equals("")) throw new IOException("Rule set name not specified");
	return inputFile(ruleSetName, "rules", RULES_EXT);
    }

    private static boolean canCacheAllRules = false;

    /** This can be used (typically, in the captive server context) to allow caching all rules,
	for greater efficiency */
    public  static void allowCachingAllRules(boolean x) {	
	canCacheAllRules=x;
    }

    /** Can the game server cache this rule set? The convention is, names not
	starting with a slash refer to files in the tomcat directory,
	whose content is supposed to be stable; therefore, they can be
	cached in the web app. Names starting with a slash are interpreted
	as absolute paths of files, presumably belonging to developers
	and thus possibly not stable; so the game server does not cache those.
     */
    public static boolean rulesCanBeCached(String ruleSetName) {
	return canCacheAllRules || !ruleSetName.startsWith("/");
    }
    
    public static File initialBoardFile(String boardName ) throws IOException {
	if (boardName==null || boardName.equals("")) throw new IOException("Board name not specified");
	return inputFile(boardName, "boards", ".json");
    }

    /** A subdirectory of the input boards directory, for use in a param
	set with initial boards */
    public static File inputBoardSubdir(String boardSubdirName ) throws IOException {
	if (boardSubdirName.startsWith("/")) {
	    return new File(boardSubdirName);
	} else {
	    File d = new File(inputDir, "boards");
	    return new File(d, boardSubdirName);
	}
    }


    /**
       @param name If it starts with a "/" or "~", it is understand as
       an absolute path, which is handy for development (to point to
       files in researchers' home directories) or in the Captive Game
       Server environment; in this case, it is also expected to
       already have an extension. Otherwise, it is understood as
       referring to files in some subdirectory of the Tomcat game data
       directory; in this case, an extension will be added, if it's
       missing.
       @param subdir The subdirectory of the  Tomcat game data directory
       where we will look for the file. (The "name" is understood as relative
       to that subdirectory).
       @param ext The extension (e.g. ".txt" or ".json") that we will
       add to the name.
     */    
    private static File inputFile(String name, String subdir, String ext) throws IOException {
	//System.out.println("DEBUG: inputFile(name=" +name+", subdir=" + subdir+", ext=" + ext+")");
	if (name==null || name.equals("")) throw new IOException("File name not specified");
	if (name.startsWith("/") || name.startsWith("~")) {
	    return new File(name);
	} else {     
	    File base = new File(inputDir, subdir);
	    if (!name.endsWith(ext)) name += ext;
	    //System.out.println("DEBUG: result=" + new File(base, name));
	    return new File(base, name);
	}
    }

    /** Lists all rules files, or boards files, etc in a directory, without 
     extensions. */
    static Vector<String> listInputs( String subdir, String ext) throws IOException {
	return listInputs(new File(inputDir, subdir), ext);
    }
    
    static Vector<String> listInputs( File dir, String ext) throws IOException {
	File[] files = dir.listFiles();
	Vector<String> v = new Vector<String>();
	for(File cf: files) {
	    if (!cf.isFile()) continue;
	    String fname = cf.getName();
	    if (!fname.endsWith(ext)) continue;
	    v.add( fname.substring(0, fname.length()-ext.length()));
	}
	return v;	
    }


    static public File getSvgFile(Piece.Shape shape) {
	return  getSvgFile(shape.toString());
    }
    
    /** @param shape Case-insensitive shape name, e.g. "circle", or
	"arrows/up". To obtain the SVG file name, the shape name
	is converted to lower case, and ".svg" is added.
    */
    static public File getSvgFile(String shape) {
	return new File(shapesDir(), shape.toLowerCase() + ".svg");
    }

    /** Looks for an image file with an appropriate name in the shapes directory.
	@return A File object if a file has been found, or null otherwise
*/
    static public File getImageFile(String shape) {
	File f = new File(shapesDir(), shape);
	if (f.exists()) return f;	
	f = new File(shapesDir() + ".svg", shape);
	if (f.exists()) return f;
	f = new File(shapesDir(), shape.toLowerCase() + ".svg");
	if (f.exists()) return f;
	return null;
    }

    static Vector<String> listAllShapesRecursively() throws IOException {
	return listAllShapesRecursively("", shapesDir());
    }

    /** @param prefix May be "", or "parent/", or "parent1/parent2/" etc
     */
    static Vector<String> listAllShapesRecursively(String prefix, File dir)  throws IOException {
	//	File d = new File(inputDir, subdir);
	final String ext = ".svg";
	Vector<String> v =new Vector<>();
	if (!dir.canRead()) throw new IOException("Directory not readable: " + dir);
	Logging.info("Reading dir: " + dir);
	File[] files = dir.listFiles();
	for(File cf: files) {
	    String fname = cf.getName();
	    if (cf.isDirectory()) {
		v.addAll(listAllShapesRecursively(prefix+fname+"/",cf));
	    } else if (cf.isFile() && fname.endsWith(ext)) {
		v.add( prefix+fname.substring(0, fname.length()-ext.length()));
	    }
	}
	return v;	
    }

    /** List all existing experiment plans (based on the directory
	names in the appropriate tree) */
    public static String[] listSAllExperimentPlans()  throws IOException{
	return  listSAllExperimentPlansInTree(null);
    }

    public static String[] listSAllExperimentPlansInTree(String subdir) throws IOException{
	File root =  trialListMainDir();
	if (subdir!=null && subdir.length()>0) root = new File(root, subdir);
	String[] a = listAllExperimentPlanDirsInTree(root);
	Arrays.sort(a);

	if (subdir!=null && subdir.length()>0) {
	    for(int j=0; j<a.length; j++) {
		a[j] = subdir + "/" + a[j];
	    }
	}

	
	return a;	
    }

    private static String[] listAllExperimentPlanDirsInTree(File root) throws IOException {
	File[] files = root.listFiles();
	if (files==null) throw new IOException("Cannot read directory " + root);
	Vector<String> v = new Vector<>();
	
	for(File cf: files) {	    
	    if (cf.isDirectory()) {
		String fname = cf.getName();
		if (TrialList.listTrialLists(cf).size()>0) {
		    v.add(fname);
		}
		
		for(String x: listAllExperimentPlanDirsInTree(cf)) {
		    v.add(fname + File.separator + x);
		}
	    }
	}
	return v.toArray( new String[0]);
    }

    /** Lists all rule sets under rules/s
       @param s The position of the root of the tree relative to the
       main rules directory
       @return A list of proper rule set names (in the same format they 
       would be likely to be encountered in trial list files, i.e.
       path names (relative to "rules") without extension.
    */
    public static String[] listAllRuleSetsInTree(String s) throws IOException {
	File root = new File(inputDir, "rules");
	if (s.length()>0) root = new File(root, s);

	String[] w = listAllRuleSetsInTree(root);
	Arrays.sort(w);
	if (s.length()>0) {
	    for(int j=0; j<w.length; j++) {
		w[j] = s + "/" + w[j];
	    }
	}
	return w;
    }

    /**@param root Look for rule set files in the tree rooted at this point.
       @return The names of all rule sets whose files are in a given tree. These are generally not complete rule set names, but their names relative to the specified root directory.
     */
    private static String[] listAllRuleSetsInTree(File root) throws IOException {
	if (!root.isDirectory()) throw new IOException("" + root + " is not a directory");
	File[] files = root.listFiles();
	Vector<String> v = new Vector<>();

	for(File cf: files) {
	    String fname = cf.getName();
	    if (cf.isDirectory()) {
		for(String x: listAllRuleSetsInTree(cf)) {
		    v.add(fname + File.separator + x);
		}
	    } else if (cf.isFile() && fname.endsWith(RULES_EXT)) {
		v.add(fname.substring(0, fname.length()-RULES_EXT.length()));
	    }
	}
	return v.toArray( new String[0]);
    }

    
    /** Creates an HTML snippet (to be used inside a FORM) listing
	all currently existing experiment plans.
     */
    public static String listSAllExperimentPlansHtml()  throws IOException{
	Vector<String> v=new Vector<>();
	for(String exp: listSAllExperimentPlans()) {
	    v.add( Tools.radio("exp", exp, exp, false));
	}
	return String.join("<br>\n", v);
    }

    static File launchDir() {
	return new File(inputDir, "launch");
    }

    /** The control file for the repeat-user launch page
	@param mode APP, MLC, etc
     */
    static public File getLaunchFileA(LaunchRulesBase.Mode mode) {
	String suffix = mode.toString().toLowerCase();
	return new File(launchDir(), "launch-"+suffix+".csv");
    }

    static public File getLaunchFileB(LaunchRulesBase.Mode mode) {
	String suffix = mode.toString().toLowerCase();
	return new File(launchDir(), "launch-"+suffix+"-b.csv");
    }


    public static File modifierFile(String modifierName) throws IOException {
	if (modifierName==null || modifierName.equals("")) throw new IOException("Modifier name not specified");
	return inputFile(modifierName, "modifiers", ".csv");
    }


    /** The directory where the bundle pregame experience files is stored for 
	a specific experience name */
    public static File pregameDir(String pregame) throws IOException {
	return new File( new File(inputDir, "pregame"), pregame);
    }
}

    
