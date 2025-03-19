package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.Board.Pos;
import edu.wisc.game.rest.ColorMap;
import edu.wisc.game.formatter.*;
import edu.wisc.game.sql.Episode.Move;
import edu.wisc.game.sql.Episode.Pick;

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


    
/** @param pieces A dense array with no nulls, like Board.values
    @param canMove If true, the player can make a move, so buttons will be enabled
 */
    public static String htmlDisplay(Vector<Piece> pieces,
				     Pick lastMove, boolean weShowAllMovables, boolean[] isJMoveable, int cellWidth, boolean canMove) {


	String result="";
	
	ColorMap cm = new ColorMap();
 	
	Vector<String> rows = new Vector<>();	
		
	for(int y=Board.N+1; y>=0; y--) {
	    Vector<String> v = new Vector<>();

	    if (y==Board.N+1 || y==0) { // top or bottom row, with buckets and column numbers
		for(int x=0; x<=Board.N+1; x++) {
		    if (x==0 || x==Board.N+1) {
			int bid = Board.findBucketId( Board.Pos(x,y));
			
			String a = ""+bid;
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

		    int[] jj = Episode.findJforPos(pos, pieces);
		    int m = jj.length;
		    if (m==0) m = 1;
		    Vector<String> tds = new Vector<>();
		    for(int i=0; i<m; i++) {
		    
			//-- blank pieces are used to make sure that even
			//-- empty columns are of the same width as non-empty ones
			String sh = "BLANK";
			String hexColor = "#FFFFFF";
			ImageObject io = null;

			
			boolean nonBlank = (i<jj.length);

			Piece p = nonBlank? pieces.get( jj[i]) : null;
			if (nonBlank) {

			    io = p.getImageObject();		    
			    sh = (io!=null) ? io.key : p.xgetShape().toString();
			    hexColor = "#"+ (io!=null? "FFFFFF" : cm.getHex(p.xgetColor(), true));
			}
		    
			//-- The style is provided to ensure a proper color border
			//-- around Ellise's elements, whose color is not affected by
			//-- the background color of TD
			String z = "<img ";
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
		    	
			//			boolean isLastMovePos =  (lastMovePos==pos);
			boolean padded=true;

			long lastPieceId = (lastMove==null) ? -1: lastMove.getPieceId();
			boolean lastWasHere = p!=null && lastPieceId==p.getId();

			// show that either this piece is movable,
			// or we have just removed a piece from here
			if (i<jj.length?
			    isJMoveable[jj[i]] && (weShowAllMovables || lastWasHere) :
			    (lastMove!=null && lastMove.pos==pos)) {
			    z="(" + z + ")";
			    padded=true;
			}
		    	
			if (lastWasHere && lastMove.code != Episode.CODE.ACCEPT) {
			    z="[" + z + "]";
			    padded=true;
			}

			if (!padded) z = "&nbsp;" + z + "&nbsp;";
			String td = (io!=null)?
			    fm.td( z):
			    fm.td("bgcolor=\"" + hexColor+"\"", z);
			tds.add(td);
		    }
		    if (tds.size()==1) {
			v.add(tds.get(0));
		    } else {
			String tr = fm.tr(String.join("", tds));
			String zrows[] = { fm.row("", tr)};
			String table = fm.table("", zrows);
			v.add( table );
		    }
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
