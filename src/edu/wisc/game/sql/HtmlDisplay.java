package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.Board.Pos;
import edu.wisc.game.rest.ColorMap;
import edu.wisc.game.formatter.*;


/** Drawing boards in HTML.

    Buttons: with some inspiration from
    https://stackoverflow.com/questions/63846699/how-do-i-keep-the-selected-button-highlighted

    <p>
    For the buttons etc to work correctly, the HTML page
    into which this code is included should also include the JS
    snippet from js/boardDisplay.js

*/
public class HtmlDisplay {

   private static HTMLFmter fm = new HTMLFmter();


    
/** @param pieces An array of N*N values, with nulls for empty cells
    @param canMove If true, the player can make a move, so buttons will be enabled
 */
    public static String htmlDisplay(Piece[] pieces, int lastMovePos, boolean weShowAllMovables, boolean[] isMoveable, int cellWidth, boolean canMove) {


	String result="";
	
	ColorMap cm = new ColorMap();
 	
	Vector<String> rows = new Vector<>();	
		
	for(int y=Board.N+1; y>=0; y--) {
	    Vector<String> v = new Vector<>();

	    if (y==Board.N+1 || y==0) { // top or bottom row, with buckets and column numbers
		for(int x=0; x<=Board.N+1; x++) {
		    if (x==0 || x==Board.N+1) {
			String a = ""+x+","+y;
			String s = "<button onclick=\"doBucket("+a+");\">B("+a+")</button>\n";
			v.add(fm.td(s));
		    } else {
			v.add(fm.td("align='center'", "" + x));
		    }
		}			
	    } else {
		v.add(fm.td(""+y));		
		//-- we use borders if there are any images with color properties
		boolean needBorder=false;
		for(Piece p: pieces) {
		    if (p!=null && p.xgetColor()!=null) needBorder=true;
		}		
		
		for(int x=1; x<=Board.N; x++) {
		    int pos = (new Pos(x,y)).num();
		    //-- blank pieces are used to make sure that even
		    //-- empty columns are of the same width as non-empty ones
		    String sh = "BLANK";
		    String hexColor = "#FFFFFF";
		    ImageObject io = null;

		    boolean nonBlank = (pieces[pos]!=null);
		    if (nonBlank) {
			Piece p = pieces[pos];
			io = p.getImageObject();		    
			sh = (io!=null) ? io.key : p.xgetShape().toString();
			hexColor = "#"+ (io!=null? "FFFFFF" : cm.getHex(p.xgetColor(), true));
		    }

		    //-- The style is provided to ensure a proper color border
		    //-- around Ellise's elements, whose color is not affected by
		    //-- the background color of TD
		    String z = "<img ";
		    // if (needBorder) z += "style='border: 5px solid "+hexColor+"' ";
		    if (needBorder) z += "style='background:"+hexColor+"' ";
		    String ke = null;
		    try {
			ke  = java.net.URLEncoder.encode(sh, "UTF-8");
		    } catch( UnsupportedEncodingException ex) {}
		    
		    z += "height='"+cellWidth+"' src=\"../../GetImageServlet?image="+ke+"\">";

		    if (canMove && nonBlank) { //-- make the image into a BUTTON
			String a = ""+x+","+y;
			String c = a.replaceAll(",", "_");
			String id = "Button"+c;
			String s = "<input type=\"radio\" name=\"Button\" class=\"ButtonState\"  id=\""+id+"\" value=\""+
			    a+"\"  onchange=\"selectXY("+a+");\"/>\n";
			s += "<label class=\"Button\" for=\""+id+"\">" + z + "</label>\n";
			z = s;
		    }
		    
		    boolean isLastMovePos =  (lastMovePos==pos);
		    boolean padded=true;
		
		    if (isMoveable[pos] && (weShowAllMovables || isLastMovePos)) {
			z="(" + z + ")";
			padded=true;
		    }
		    
		    if (isLastMovePos) {
			z="[" + z + "]";
			padded=true;
		    }

		    if (!padded) z = "&nbsp;" + z + "&nbsp;";
		    String td = (io!=null)?
			fm.td( z):
			fm.td("bgcolor=\"" + hexColor+"\"", z);
		    v.add(td);
		}
		v.add(fm.td(""+y));		
	    }
	    rows.add(fm.tr(String.join("", v)));
	}
	result+= fm.table("border='1'", rows);
	return result; 
    }

    public static String notation(boolean weShowAllMovables) {
       String s = 
	   fm.wrap("li", "(X) - a movable piece" +
		   (!weShowAllMovables? " (only marked on the last touched piece)": "")) +
	   fm.wrap("li","[X] - the position to which the last move or pick attempt (whether successful or not) was applied");
	return fm.para( "Notation: " + fm.wrap("ul",s));
    }


}
