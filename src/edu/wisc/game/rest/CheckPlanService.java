package edu.wisc.game.rest;

import java.io.*;
import java.util.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.json.*;


import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

// For database work
import javax.persistence.*;


import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.formatter.*;
import edu.wisc.game.svg.Composite;

/** The "Check my experiment plan" service. */
@Path("/CheckPlanService") 
public class CheckPlanService extends GameService2 {
    private static Fmter  fm = new HTMLFmter();

    @POST
    @Path("/checkRules") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    /** The "Check rules" service. 
	@param rulesText
     */
    public String checkRulesHtml( @FormParam("rulesText") String rulesText){
	Vector<String> v = new Vector<>();
	int errcnt=0;
	String info = null;
	String title = "Checking a rule set";
	String title1 = "Checking a rule set";

	v.add(fm.h1(title1));

	v.add(fm.para("Input:" + fm.br()+ fm.pre(rulesText)));  

    
	try {
	    RuleSet rules = new RuleSet(rulesText);
	    v.add(fm.para("The rules have been compiled as follows:"));
	    v.add(fm.para(fm.tt(rules.toSrc().replaceAll("\n",fm.br()))));
	    errcnt += bucketVarCheck(rules, null, v);
	    errcnt += stalemateCheck(rules, ParaSet.legacy, v);
	    
	} catch(Exception ex) {
	    if (info != null) v.add(fm.para(info));
	    v.add(fm.para("Error: " + ex));
	    StringWriter sw = new StringWriter();
	    ex.printStackTrace(new PrintWriter(sw));
	    String s = fm.pre(sw.toString());
	    v.add(fm.para(fm.wrap("small", "Details:"  + s)));
	    errcnt ++;
	}
       
	v.add("<hr>");
	if (errcnt>0) {
	    v.add(fm.para(fm.strong("Found " + errcnt + " errors. You may want to fix them before inviting players into this experiment plan")));
	} else {
	    v.add(fm.para(fm.strong("Found no errors.")));
	}


	String body = String.join("\n", v);

	return fm.html(title, body);	

    }

    
    @POST
    @Path("/checkPlan") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    /** The "Check my experiment plan" service. It can be used by the expeiment manager to ensure that the experiment plan (the trial list files, as well as the rules set files and the initial board files referred from them) do not contain obvious errors.
	@param exp The experiment plan. 
     */
    public String checkPlanHtml( @FormParam("exp") String exp){
	
	exp = exp.trim();
	Vector<String> v = new Vector<>();
	int errcnt=0;
	
	String title = "Checking experiment plan " + exp;    
	String title1 = "Checking experiment plan " + fm.tt( exp);    

	v.add(fm.h1(title1));

	//---- color map 
	v.add(fm.h2("Checking the color map"));
	v.add(fm.para("Note: the same global color map is used for shape-and-color-tuple objects in all experiment plans. It is ignored as far as image-and-properties-based objects are concerned."));
	ColorMap cm = new ColorMap();

	Object o = cm.get("error");
	if (o!=null && o.equals(Boolean.TRUE)) {
	    v.add(fm.para("Error: " + cm.get("errmsg")));
	    errcnt ++;
	}
	Vector<String> rows = new Vector<>();
	for(String key: cm.keySet()) {
	    if (key.equals("error")||key.equals("errmsg")) continue;
	    String hexColor = "#" + cm.getHex(key,false);
	    //String hex1 = "#" + cm.getHex(key,true);
	    rows.add(fm.tr(fm.td(key) + fm.td("bgcolor=\"" + hexColor+"\"", fm.space(10))));
	}
	v.add( fm.table("border='1'", rows));
	v.add( fm.para("Loaded the total of " + rows.size()+ " colors"));

	//-- trial list
	v.add(fm.h2("Checking the trial lists"));
	String info = null;
	try {
	    RandomRG random = new RandomRG();
	    Vector<String> lists = TrialList.listTrialLists(exp);	    
	    v.add(fm.para("Found " + lists.size() + " trial lists for experiment plan " + fm.tt(exp)));
	    for(String key: lists) {
		v.add(fm.h3("Checking trial list " + fm.tt(key)));
		TrialList trialList  = new TrialList(exp, key);
		if (trialList.error) {
		    v.add(fm.para("Error: failed to create trial list " +
				  fm.tt(key) + ". Error=" + trialList.errmsg));
		    errcnt ++;
		    continue;
		}

		
		
		
		int npara= trialList.size();
		v.add(fm.para("... the trial list has " + npara + " parameter set(s)"));
		int j=0;
		for( ParaSet para: trialList) {
		    j++;
		    v.add(fm.para(fm.h4("Checking para set no. " + j + " out of "+npara+"...")));

		    para.checkIncentive();

		    
		    para.checkImages();
		    boolean ipb = (para.imageGenerator!=null);
		    // All values of all properties occurring in our objects
		    TreeMap<String,TreeSet<String>> propValues=new TreeMap<>();
		    boolean useDynamicImages = (ipb && !(para.imageGenerator instanceof ImageObject.PickFromList));

		    
		    if (ipb) {
			String[] keys;
			if (useDynamicImages) {
			    Composite.Generator g = (Composite.Generator)para.imageGenerator;
			    v.add(fm.para("This is an image-and-properties-based para set with dynamic image generation,  which uses a "+
					  g.describeBrief() + ". (Keys="+g.asList()+"). Some of these objects are shown below"));
			    keys = g.getSomeConcreteKeys(random);
			} else {
			    v.add(fm.para("This is an image-and-properties-based para set, which uses "+para.imageGenerator.describeBrief()));
			    ImageObject.PickFromList g = (ImageObject.PickFromList)para.imageGenerator;
			    keys = g.getKeys();
			}
		
			rows = new Vector<>();
			

			for(String k: keys) {
			    String ke = java.net.URLEncoder.encode(k, "UTF-8");
			    String z = "<img width='80' src=\"../../GetImageServlet?image="+ ke+"\">";
			    ImageObject io = ImageObject.obtainImageObjectPlain(null, k, false);
  			    
			    rows.add(fm.tr(fm.td(k) + fm.td(z) + fm.td(io.listProperties())));
			    for(String p: io.keySet()) {
				TreeSet<String> h = propValues.get(p);
				if (h==null) propValues.put(p, h=new TreeSet<String>());
				h.add(io.get(p));
			    }
			    
			}
			v.add( fm.table("border='1'", rows));
			v.add( fm.para((useDynamicImages? "Some" : "All") +
				       " properties used by the objects involved in this trial list, and all values found for these properties"+
				       (useDynamicImages? " in a sample of objects" : "") +
				       ", are listed in the following table:"));
			rows.clear();
			rows.add(fm.tr(fm.th("Property") + fm.th("Values")));
			for(String p: propValues.keySet()) {
			    rows.add(fm.tr(fm.td(p) + fm.td( Util.joinNonBlank(", ",propValues.get(p)))));
			}
			v.add( fm.table("border='1'", rows));
			//} else if (useDynamicImages) {
			//v.add(fm.para("This is an image-and-properties-based para set with dynamic image generation,  which uses "+para.imageGenerator.describeBrief() + ". Sample display is not supported yet"));
		    } else {
			v.add(fm.para("Images are not used in this para set, which means that this is a shapes-and-colors para set"));

			//-- Checking the values of "shapes" and "colors" params
			para.checkColors(cm);
			if (para.colors==null) {
			    v.add(fm.para("Colors are not used in this para set"));
			} else {
			    v.add(fm.para("Para set uses "+para.colors.length+" color(s):"));
			    rows = new Vector<>();
			    for( Piece.Color color: para.colors) {
				String hexColor = "#" + cm.getHex(color,false);
				//String hex1 = "#" + cm.getHex(key,true);
				rows.add(fm.tr(fm.td(color.toString()) + fm.td("bgcolor=\"" + hexColor+"\"", fm.space(10))));
			    }
			    v.add( fm.table("border='1'", rows));
			}
			
			
			para.checkShapes();
			if (para.shapes==null) {
			    v.add(fm.para("Shapes are not used in this para set"));
			} else {
			    v.add(fm.para("Para set uses "+para.shapes.length+" shape(s):"));
			    rows = new Vector<>();
			    for( Piece.Shape shape: para.shapes) {
				String sh = shape.toString();
				String z = "<img width='80' src=\"../../GetImageServlet?image="+sh+"\">";
				rows.add(fm.tr(fm.td(sh) + fm.td(z)));
			    }
			    v.add( fm.table("border='1'", rows));
			}
		    }
		    
		    

		    //-- Parsing the rule sets (errors can cause exceptions)
		    info = "The rule set name = " + para.getRuleSetName();
		    GameGenerator gg = GameGenerator.mkGameGenerator(Episode.random, para);
		    info = null;
		    Game game = gg.nextGame();
		    
		    
		    //-- Checking initial boards
		    if (gg instanceof PredefinedBoardGameGenerator) {
			v.add(fm.para("Checking predefined boards..."));
			((PredefinedBoardGameGenerator)gg).checkShapesAndColors(cm);
		    }
		    //-- checking the rule files for properties and their values, or for colors and shapes, as the case may be
		    RuleSet rules = gg.getRules();
		    v.add(fm.para("The rules have been compiled as follows:"));
		    v.add(fm.para(fm.tt(rules.toSrc().replaceAll("\n",fm.br()))));

		    TreeMap<String,TreeSet<String>> w = null;
			
		    if (ipb && !useDynamicImages) {
			w = rules.listAllPropValues();			
			for(String p: w.keySet()) {
			    TreeSet<String> z= new TreeSet<>();
			    z.addAll(w.get(p));

			    TreeSet<String> h = propValues.get(p);
			    if (h==null) {
				v.add(fm.para("Warning: Rule set " + para.getRuleSetName() + " makes use of property " + fm.tt(p) +", which does not appear in any of the image-based objects of this parameter set. Therefore, any references to this property in the rule set won't affect the game."));
			    } else {
				z.removeAll(h);
			    }
			    if (!z.isEmpty()) {
				v.add(fm.para("Warning: When rule set " + para.getRuleSetName() + " has conditions referring to property " +fm.tt(p) +", it makes use of "+z.size()+" values of this property (" + fm.tt(String.join(", ", z)) +"), which do not appear in any of the image-based objects of this parameter set."));
			    }
			}
		    } else if (useDynamicImages) {
			v.add(fm.para("Use dynamic image objects -- cannot analyze properties yet"));
		    } else {
			for(Piece.Shape shape:  rules.listAllShapes()) {
			    File f = Files.getSvgFile(shape);
			    if (!f.canRead())  {
				//throw new IOException("Cannot read file: " + f);
				v.add(fm.para("Warning: Rule set " + para.getRuleSetName() + " mentions shape " + shape +", for which no SVG file exists. Was a different shape intended?"));
				errcnt ++;
			    }
			}
			for(Piece.Color color:  rules.listAllColors()) {
			    if (!cm.hasColor(color))  {    
				v.add(fm.para("Warning: Rule set " + para.getRuleSetName() + " mentions color " + color +", which is not listed in the color map file. Was a different color intended?"));
				errcnt ++;
			    }
			}
		    }
			
		    errcnt += bucketVarCheck(rules,w==null? null: w.keySet(),v);

		    errcnt += stalemateCheck(rules,para,v);

		}

	    }

	} catch(Exception ex) {
	    if (info != null) v.add(fm.para(info));
	    v.add(fm.para("Error: " + ex));
	    StringWriter sw = new StringWriter();
	    ex.printStackTrace(new PrintWriter(sw));
	    String s = fm.pre(sw.toString());
	    v.add(fm.para(fm.wrap("small", "Details:"  + s)));
	    errcnt ++;
	}

	
	//-- put together
	v.add("<hr>");
	if (errcnt>0) {
	    v.add(fm.para(fm.strong("Found " + errcnt + " errors. You may want to fix them before inviting players into this experiment plan")));
	} else {
	    v.add(fm.para(fm.strong("Found no errors.")));
	}

		
	String body = String.join("\n", v);

	return fm.html(title, body);	

    }
	
    /** Checks whether any variables occurring in bucket expressions in a given RuleSet may be undefined.
        @param rules The RuleSet to analyze
	@param If not null, this set contains the names of all properties that may occur in the game pieces. The param may be null if such data is not available
    	@return Number of errors */
    private static int bucketVarCheck(RuleSet rules, Set<String> knownProps, Vector<String> v) {	    
	    int errcnt = 0, propCnt=0;
	    HashSet<String> bv = rules.listAllBucketVars();
	    String varNames[] = bv.toArray(new String[0]);
	    Arrays.sort(varNames);
	    if (varNames.length>0) {
		v.add(fm.para("The rule set uses " + varNames.length + " bucket variables: " + fm.tt(String.join(", ", varNames))));
	    } else {
		v.add(fm.para("The rule set uses no bucket variables."));
	    }
	    
	    for(String varName : bv) {
		try {
		    Enum.valueOf( RuleSet.BucketSelector.class, varName);
		    continue; // Skip known built-in vars
		} catch(IllegalArgumentException ex) {}
		if (varName.startsWith("p.")) {
			propCnt++;
			String prop = varName.substring(2);
			if (knownProps!=null) {
			    if (!knownProps.contains(prop)) v.add(fm.para("Warning: A bucket expression in the rule set refers to property " + fm.tt(prop) +
									  ", which may not occur in the objects used in this game"));
			}
		} else {
		    v.add(fm.para("Error: A bucket expression in the rule set uses unknown variable " + fm.tt(varName)));
		    errcnt ++;
		}
	    }
	    if (knownProps==null && propCnt>0) {
		v.add(fm.para("Note: Bucket expression(s) in this rule set refer to properties of objects (the <tt>p.</tt> syntax), "+
			      "but the validator does not support analysis of properties in games of this type (maybe because of the use of dynamically generated objects"));
	    }
	    return errcnt;
    }


    /**  check the rule set for stalemates
	 @return errcnt
     */
    private int stalemateCheck(RuleSet rules,ParaSet para, Vector<String> v) {

	int errcnt = 0;
	String[] allImages = null;
	if (para.imageGenerator!=null) {
	    if (!(para.imageGenerator instanceof  ImageObject.PickFromList)) {
		v.add(fm.para("This game uses a dynamic ImageObject generator ("+para.imageGenerator.getClass()+"); stalemate testing is not supported"));
		return errcnt;
	    }
	    ImageObject.PickFromList g = (ImageObject.PickFromList)para.imageGenerator;
	    allImages = g.getKeys();
	}
	    
	StalemateTester tester = new StalemateTester(rules);

	//Piece.Shape.legacyShapes, Piece.Color.legacyColors, para.images);
	
	Board stalemated = tester.canStalemate(para.shapes, para.colors, allImages);
	
	if (stalemated!=null) {
	    v.add(fm.para("Error: this rule set can stalemate"));
	    String picture =	(fm instanceof HTMLFmter) ?
		BoardDisplayService.doBoard(stalemated, 48):
		BoardDisplayService.doBoardAscii(stalemated);
	    	    
	    v.add(fm.para("Sample stalemate board:" + fm.br() + picture));	    	    		
	    errcnt++;
	} else {
	    v.add(fm.para("This rule will not stalemate"));
	}
	v.add("Note: stalemate testing is done under GS 4 rules. It is not applicable to rule sets using GS5 syntax");
	return errcnt;
    }
   
    @POST
    @Path("/clearTables") 
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    /** Clears various tables with pre-loaded and pre-compiled data.
	This call should be used after you have modified some trial list
	files, rules set files, etc, in order to ensure that the modified
	files are reloaded and used going forward.
     */
    public String clearTables(){
	AllRuleSets.clearAll();
	ImageObject.clearTable();
	String title = "Clearing server tables";
	
	Vector<String> v = new Vector<>();
	v.add(fm.para("Rule table cleared"));
 	String body = String.join("\n", v);
	return fm.html(title, body);	
   }

    /** Command-line interface to the validator. The command-line
	arguments can be names of experiment plans or names of rule
	set files.
     */
    public static void main(String[] argv) throws Exception {
	CheckPlanService service = new CheckPlanService();
	service.fm = new Fmter();
	for(String a: argv) {
	    String s;	       
	    if (a.endsWith(".txt")) {
		File f = new File(a);
		if (!f.canRead()) throw new IOException("Cannot read file " + f);
		String text = Util.readTextFile(f);
		s = service.checkRulesHtml(text);
	    } else {
		s = service.checkPlanHtml(a);
	    }
	    System.out.println("------------------------------------------------");
	    System.out.println(s);

	}
    }
    
}
	
