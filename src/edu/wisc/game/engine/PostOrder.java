package edu.wisc.game.engine;

import java.io.*;
import java.util.*;
import java.text.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.sql.Board.Pos;

public class PostOrder {
    /** When an Order is applied not to the entire board, but only
	to the pieces that are acceptable based on all other criteria.
	For example, "finding the topmost red triangle(s)".

	This can be used from Episode.buildAcceptanceMap, to modify
	the acceptanceMap obtained by applying all other clauses
	of atoms.

	<p>
        Introduced in GS 6.041 (2024-10-22)

	@param acceptanceMap The bit acceptanceMap[pos][atomNo][dest] is set if the
	rule atom[atomNo] in the current rule line allows moving
	the piece currently at pos to bucket[dest]. 
    //private BitSet[][] acceptanceMap = new BitSet[Board.N*Board.N+1][];
    */

    public static void applyPostPosToAcceptanceMap(RuleSet rules, RuleSet.Row atoms, Vector<Piece> values, BitSet[][] jAcceptanceMap) {

	for(int atomNo = 0; atomNo < atoms.size(); atomNo++) {
	    RuleSet.Atom atom = atoms.elementAt(atomNo);
	    if (atom.postPlist.isTrivial()) continue;

	    // Which game pieces are moveable (to any bucket) in
	    // accordance with this atom (without taking into account
	    // any postPos clause)?
	    // Create a bit set with bits set in the positions where
	    // there are pieces potentially moveable under this
	    // atom. The positions are, as usual, numbered 1 thru N^2.
	    BitSet movableOnBoard = new BitSet(Board.N*Board.N+1);
	    for(int j=0; j<jAcceptanceMap.length; j++) {
		if (jAcceptanceMap[j]!=null &&
		    !jAcceptanceMap[j][atomNo].isEmpty()) {
		    int pos = values.get(j).xgetPos().num();
		    movableOnBoard.set(pos);
		}
	    }

	    // Which of these pieces are "in front" of each order?
	    EligibilityForOrders eligibleForEachOrder = new EligibilityForOrders(rules, movableOnBoard);

	    for(int j=0; j<jAcceptanceMap.length; j++) {
		if (jAcceptanceMap[j]==null) continue;
		BitSet m = jAcceptanceMap[j][atomNo];
		int pos = values.get(j).xgetPos().num();
		if (!m.isEmpty() && !atom.postPlist.allowsPicking(pos, eligibleForEachOrder)) m.clear();
	    }



	    /*
	    BitSet movableOnBoard = new BitSet(Board.N*Board.N+1);
	    for(int pos=0; pos<acceptanceMap.length; pos++) {
		if (acceptanceMap[pos]!=null &&
		    !acceptanceMap[pos][atomNo].isEmpty()) movableOnBoard.set(pos);
	    }

	    // Which of these pieces are "in front" of each order?
	    EligibilityForOrders eligibleForEachOrder = new EligibilityForOrders(rules, movableOnBoard);

	    for(int pos=0; pos<acceptanceMap.length; pos++) {
		if (acceptanceMap[pos]==null) continue;
		BitSet m = acceptanceMap[pos][atomNo];
		if (!m.isEmpty() && !atom.postPlist.allowsPicking(pos, eligibleForEachOrder)) m.clear();
	    }
	    */
	}
    }

    

}
   
