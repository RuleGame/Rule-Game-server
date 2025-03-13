package edu.wisc.game.engine;

import java.io.*;
import java.util.*;

import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.sql.Board.Pos;
import edu.wisc.game.rest.ColorMap;
import edu.wisc.game.engine.RuleSet.BucketSelector;


//import edu.wisc.game.sql.EpisodeMemory.BucketVarMap;
import edu.wisc.game.sql.EpisodeMemory.BucketVarMap2;

/** For the training/testing restrictions on boards, as introduced in GS 6.010. See email discusion with Paul on 2023-03-08, and captive.html#cond
 */

public class BoardConditionsChecker {

    /** @param testing How is the condition applied? In the training
	mode (testing==false), the board is acceptable if no game
	piece satisfies any condition (i.e. is accepted by any
	row). In the testing mode (testing==true), the board is
	acceptable if at least one piece does.

	@param board The board to test
	@param rules The conditions, formatted as a rule set. Each row of the rule set represents one condition. 
    */
	
    public static boolean boardIsAcceptable(Board board, RuleSet rules, boolean testing) {

	Piece[] pieces = board.toPieceList();
	
	EligibilityForOrders eligibleForEachOrder = new EligibilityForOrders(rules, Episode.onBoard(pieces));

	int acceptedPiecesCnt=0;
	for(int pos=0; pos<pieces.length; pos++) {
	    if (pieces[pos]==null) continue;
	    if (pieceIsAcceptedByHowManyRows(pieces[pos], rules, eligibleForEachOrder)>0) {
		acceptedPiecesCnt ++;
	    }
	}

	return testing? acceptedPiecesCnt>0 : acceptedPiecesCnt==0;
    }


    /** How many rows will accept the specified game piece?

	We do the tests NBU (=4) times, with different destination
	buckets. Normally, this should not be necessary, as the
	training/testing conditions are not likely to refer to bucket
	numbers. But if for some reasons they do (e.g. because the
	experiment designer just reused a rule set file for his conditions),
	the semantics is, "a piece is considered 'accepted by the row'
	if a move to any bucket is allowed".
     */
    private static int pieceIsAcceptedByHowManyRows(Piece p, RuleSet rules, EligibilityForOrders eligibleForEachOrder) {
	 EpisodeMemory memory = new EpisodeMemory();
	 int acceptingRowCnt = 0;
	 
	 for(RuleSet.Row row: rules.rows) {
	    Pos pos = p.pos();
	    boolean rowAccepts = false;
	    final int NBU = Board.buckets.length; // 4
	    for(int bucketNo=0; bucketNo<NBU && !rowAccepts; bucketNo++) {
		BucketVarMap2  varMap = memory.new BucketVarMap2(p, bucketNo);

		int acceptingAtomsCnt=0;
		for(int j=0; j<row.size() && !rowAccepts; j++) {
		    RuleSet.Atom atom = row.get(j);
		    if (bucketNo==0) {
			//	System.out.println("DEBUG: Trying p=" + p +" for atom=" + atom);
		    }
		    if (!atom.acceptsColorShapeAndProperties(p, varMap)) continue;
		    if (bucketNo==0) {
			//	System.out.println("DEBUG: Accepted");
		    }

		    if (!atom.plist.allowsPicking(pos.num(), eligibleForEachOrder)) continue;
		    if (atom.bucketList.destinationAllowed( varMap, bucketNo)) {
			acceptingAtomsCnt ++;
		    }
		}
		if (acceptingAtomsCnt == row.size()) rowAccepts = true;
	    }
	    if (rowAccepts) acceptingRowCnt++;
	 }
	 return  acceptingRowCnt++;
    }

    ///for(int pos=1; pos<= Board.N * Board.N; pos++) {

    /** This is to make board generation more efficient in the presence of
	a position-only rule. (For GS 7.008)
	@param rules a very simple rules set, consisting of a single fixed-position-only rule

	@return In training (testing==false), returns true if the rule set does not accept this position. In testing (testing==true), returns true if the rule set accepts this position.
    */
    public static boolean positionIsAllowed(int pos, RuleSet rules, boolean testing) {

	int acceptingRowCnt = 0;
	 
	for(RuleSet.Row row: rules.rows) {

	    int acceptingAtomCnt = 0;
	    for(int j=0; j<row.size(); j++) {
		RuleSet.Atom atom = row.get(j);
		if (atom.plist.allowsPicking(pos, null)) acceptingAtomCnt++;
	    }
	    if (acceptingAtomCnt == row.size())  acceptingRowCnt++;
	}
	boolean accepts = (acceptingRowCnt>0);
	
	return testing ? accepts: !accepts;
    }

    
}
