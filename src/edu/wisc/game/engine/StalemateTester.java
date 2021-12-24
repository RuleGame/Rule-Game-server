package edu.wisc.game.engine;

import java.io.*;
import java.util.*;
import java.text.*;

import javax.json.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.sql.Board.Pos;


/** Tools for testing a rule set for a possibility of a stalemate. A stalemate
    is achieved when no piece cannot be removed from the board any more.
    This class may be useful for a rule set designer, to ensure that any
    rule sets offered to players will never stalemate.
 */
public class StalemateTester {

  
    /** A "multi-Board" is like a regular Board, but can have a set of pieces
	in each cell, rather than just a single pieces. 
    */
    static class MBoard extends Vector<HashSet<Piece>> { //pieceGroups;

	private MBoard() {}
	
    /** Creates an initial multi-board, which has each possible piece in 
	each cell. */
	MBoard(Piece.Shape[] shapes, Piece.Color[] colors,
	       String[] allImages) {

	    boolean ipb = (allImages != null);
	    if (ipb) {
		//if (shapes!=null || colors!=null) throw new IllegalArgumentException("Cannot supply shapes or colors if images have been supplied");
	    } else {
		if (shapes==null || colors==null) throw new IllegalArgumentException("Shapes or colors not supplied");
	    }
		       
	    
	    add( new HashSet<Piece>());
	    for(int j=1; j<Board.N*Board.N + 1; j++) {
		Pos pos = new Pos(j);
		HashSet<Piece> g = new HashSet<Piece>();

		if (ipb) {
		    for(String image: allImages) {
			Piece p = new Piece(image,  pos.x, pos.y);
			g.add(p);
		    }				    
		} else {		
		    for( Piece.Shape shape: shapes) {
			for( Piece.Color color: colors) {
			    Piece p = new Piece(shape, color, 	pos.x, pos.y);
			    //System.out.println("Adding " + p);
			    g.add(p);
			    //System.out.println("|g|=" + g.size()+", g={" +Util.joinNonBlank(", ", g)+"}");
			}
		    }
		}
		add(g);
		//System.out.println("Built |g|=" + g.size());
		
	    }
	    for(int j=0; j<size(); j++) {
		//System.out.println("Have |g("+j+")|=" + pieceGroups.get(j).size());
	    }
	}


	/** Creates a bit set with bits set in the positions where there are
	    pieces */
	private BitSet onBoard() {
	    BitSet onBoard = new BitSet(Board.N*Board.N+1);
	    for(int i=0; i<this.size(); i++) {
		if (get(i).size()>0) onBoard.set(i);
	    }
	    return onBoard;
	}

	/** Creates an m-Board with no more than 1 piece per cell.
	    That board will be based on this m-Board, with any "extra"
	    pieces discasrded from each cell.
	 */
	MBoard mkMono() {
	    MBoard mono = new MBoard();
	    mono.clear();
	    for(int j=0; j<size(); j++) {
		HashSet<Piece> g0 = get(j), g=new HashSet<>();
		if (g0.size()>0) g.add( g0.iterator().next());
		mono.add(g);
	    }
	    return mono;
	}
	
	/** If this multi-Board contains no more than 1 piece per cell,
	    describes it as a plain N*N+1 array of pieces, so that it 
	    can be converted to a regular Board object.
	 */
	Piece[] mono() {
	    if (size()!=Board.N*Board.N + 1) throw new IllegalArgumentException("Wrong size");
	    Piece pieces[] = new Piece[size()];
	    for(int j=0; j<size(); j++) {
		HashSet<Piece> g = get(j);
		if (g.size()==0) {
		    pieces[j]=null;
		} else if (g.size()==1) {
		    pieces[j]=g.iterator().next();
		} else {
		    throw new IllegalArgumentException("Cannot represent multi-Board as mono-Board, because of multiple pieces in cell "+j);
					       
		}
	    }
	    return pieces;
	}

	MBoard copy() {
	    MBoard q= new MBoard();
	    for(int j=0; j<size(); j++) {
		HashSet<Piece> g = get(j), h = new HashSet<>();
		h.addAll(g);
		q.add(h);
	    }
	    return q;
	}

    }
    RuleSet rules;
    
    public StalemateTester(RuleSet rules0) {
	rules = rules0.mkLite();

    }

    /** Will this game stalemate if started with a full board (all possible
	pieces in each cell)? 
	@return A sample stalemate board, or null if no stalemate is possible
    */

    // MBoard(Piece.Shape[] shapes, Piece.Color[] colors,
    //	       String[] allImages) {

    
    /** Is there any board composed of any pieces of the specified
	colors and shapes on which this rule set will stalemate?  As
	always, we assume that all atoms in the rules are
	"non-disappearing", i.e. each bucket expression always produces
	a non-empty result.
     */
    public Board canStalemate(Piece.Shape[] shapes, Piece.Color[] colors,
	       String[] allImages) {
  	
	MBoard pieceGroups = new MBoard(shapes, colors, allImages);
	if (canStalemate(pieceGroups )) {
	    MBoard q =  simplifyStalematedBoard(pieceGroups);
	    return new Board( q.mono(), null, null);
	} else {
	    return null;
	}

    }
	

    
    /** Is this piece accepted as movable by our rules? */
    private boolean pieceCanBeMoved(int posNum, Piece p, EligibilityForOrders eligibleForEachOrder) {	
	for(RuleSet.Atom atom: rules.rows.get(0)) {
	    if (!atom.acceptsColorShapeAndProperties(p)) continue;
	    if (!atom.plist.allowsPicking(posNum, eligibleForEachOrder)) continue;
	    return true;
	}
	return false;
    }
    
    /** Will this game stalemate if started with a specified board? 
	@param pieceGroups The m-board whose content we'll try to
	remove.  The method modififes the content of the board,
	removing all pieces it can. If the game does not stalemate,
	this board will be empty upon return from this method; if a stalemate
	is encountered, the board will contain pieces that can't be removed.	
     */
    public boolean canStalemate(MBoard pieceGroups) {
	BitSet onBoard = pieceGroups.onBoard();
	int cnt = onBoard.cardinality();
	while(cnt>0) {
	    EligibilityForOrders eligibleForEachOrder = new EligibilityForOrders(rules, onBoard);
	    //System.out.println("cnt=" + cnt+", Eli=" + eligibleForEachOrder );

	    int rmCnt = 0;
	    for(int i=0; i<pieceGroups.size(); i++) {
		HashSet<Piece> g = pieceGroups.get(i);
		//System.out.println("|g("+i+")|=" + g.size());
		if (g.size()==0) continue;
		Iterator<Piece> iterator = g.iterator();
		while (iterator.hasNext()) {
		    Piece p = iterator.next();
		    if ( pieceCanBeMoved(i, p, eligibleForEachOrder)) {
			//System.out.println("Removing: " + p);
			iterator.remove();
			rmCnt++;
		    }
		}
		//System.out.println("done |g("+i+")|=" + g.size());
	    }
	    if (rmCnt==0) return true;
	    onBoard = pieceGroups.onBoard();
	    cnt = onBoard.cardinality();		
	}
	return false;
    }

    /** Given a known stalemated m-Board, find a smaller stalemated board
       @param  pieceGroups A known stalemated m-Board
       @return param A Board that's a subset of the input board, and which
       still stalemates
     */
    private MBoard simplifyStalematedBoard(MBoard pieceGroups) {
	MBoard q = pieceGroups.mkMono();
	if (!canStalemate(q)) throw new IllegalArgumentException("Internal error in StalemateTester");

	final HashSet<Piece> empty = new HashSet<>();
	int rmCnt;
	do {
	    rmCnt = 0;
	    for(int j=1; j<q.size(); j++) {
		if (q.get(j).size()==0) continue;
		MBoard z = q.copy();
		z.set(j,empty);
		if (canStalemate(z)) { // cool, can do without this piece!
		    rmCnt++;
		    q = z;
		}		
	    }
	    break;
	} while(rmCnt>0);
	    	
	
	return q;
    }

    
    public static void main(String[] argv) throws IOException,  RuleParseException {
	System.out.println("Have " + argv.length + " files to read");
	for(String a: argv) {
	    File f = new File(a);
	    System.out.println("Reading file " + f);
	    String text = Util.readTextFile(f);
	    RuleSet rules = new RuleSet(text);
	    System.out.println("---------- Rules --------------");
	    System.out.println(rules.toSrc());
	    System.out.println("---------- Lite rules --------------");
	    RuleSet lite = rules.mkLite();
	    System.out.println(lite.toSrc());
	    StalemateTester tester = new  StalemateTester(rules);
	    Board stalemated = tester.canStalemate( Piece.Shape.legacyShapes,
						    Piece.Color.legacyColors,
						    null);
	    if (stalemated!=null) {
		System.out.println("Can stalemate");

		JsonObject jo = JsonReflect.reflectToJSONObject(stalemated, true);

		
		System.out.println("Sample stalemate board: " + jo);
		
	    } else {
		System.out.println("No stalemate");
	    }

	}

    }
}
