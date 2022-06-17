package edu.wisc.game.sql;

import java.io.*;
import java.util.*;
import java.text.*;

import jakarta.json.*;
import javax.persistence.*;

import edu.wisc.game.util.*;
import edu.wisc.game.reflect.*;
import edu.wisc.game.engine.*;
import edu.wisc.game.parser.*;
import edu.wisc.game.sql.Board.Pos;
import edu.wisc.game.rest.ColorMap;
import edu.wisc.game.engine.RuleSet.BucketSelector;
import edu.wisc.game.formatter.*;

import jakarta.xml.bind.annotation.XmlElement; 

import edu.wisc.game.sql.EpisodeMemory.BucketVarMap;
import edu.wisc.game.sql.EpisodeMemory.BucketVarMap2;


/** An Episode is a single instance of a Game played by a person or
    machine with our game server. It describes the current state of
    the game, and has methods for processing player's actions. The
    episode object contains the top-level controls describing the 
    current state of the rule set associated with this episode.
*/
@Entity
public class Episode {

        @Id 
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private long id;


    /** This is used to assign episode IDs, which are unique within a
	given server run. The IDs are not meant to be persistent.
    */
    @Basic
    public String episodeId;
    public String getEpisodeId() { return episodeId; }
    public void setEpisodeId(String _episodeId) { episodeId = _episodeId; }
 
    // When this Episode was created
    Date startTime;    
    public Date getStartTime() { return startTime; }
    public void setStartTime(Date _startTime) { startTime = _startTime; }

    // The number of buckets
    static public final int NBU = Board.buckets.length; // 4

    /** A Pick instance describes the act of picking a piece, without 
	specifying its destination */
    public static class Pick {
	/** The position of the piece being moved, in the [1:N*N] range */
	public final int pos;
	Pick(int _pos) { pos = _pos; }
	public Pick(Pos  pos) { this(pos.num()); }
	int pieceId = -1;
	public int getPos() { return pos; }
        public int getPieceId() { return pieceId; }	
	Piece piece =  null;
	/** Acceptance code; will be recorded upon processing */
	int code;
 	public int getCode() { return code; }
	final public Date time = new Date();
	public String toString() {
	    return "PICK " + pos + " " +new Pos(pos) +", code=" + code;
	}
    }

    /** A Move instance describes an [attempted] act of picking a piece
	and dropping it into a bucket.
     */
    public static class Move extends Pick {
 	/** (Attempted) destination, in the [0:3] range */
	public final int bucketNo;
	public int getBucketNo() { return bucketNo ; }
 	Move(int _pos, int b) {
	    super(_pos);
	    bucketNo = b;
	}
	public Move(Pos pos, Pos bu) {
	    this(pos.num(), bu.bucketNo());
	}
	public String toString() {
	    return "MOVE " + pos + " " +new Pos(pos) +	" to B" + bucketNo+", code=" + code;
	}
   }
    
     @Transient
    //final
    RuleSet rules;
    /** Only used for Episodes restored from the SQL server, 
	just to keep the GUI client from crashing */
    void setRules(RuleSet _rules) { rules = _rules; }

    
    /** The current board: an array of N*N+1 elements (only positions
	[1..N*N] are used), with nulls for empty cells and non-nulls
	for positions where pieces currently are. */
    @Transient
    private Piece[] pieces = new Piece[Board.N*Board.N + 1];
    /** Pieces are moved into this array once they are removed from the board.
	This is only shown in web UI.
     */
    @Transient
    private Piece[] removedPieces = new Piece[Board.N*Board.N + 1];

    /** The cost of a pick in terms of the cost of a move. EpisodeInfo 
	overrides this method, making use of ParaSet */
    double xgetPickCost() { return 1.0;}

    
    /** The count of all attempts (move and pick) done so far, including successful and unsuccessful ones. */
    int attemptCnt=0;
    /** The total cost of all attempts (move and pick) done so far,
	including successful and unsuccessful ones. If cost_pick!=1,
	this value may be different from attemptCnt. */
    double attemptSpent=0;
    /** All successful moves so far.  Since each successful move removes
	a game piece, the number of pieces remaining on the board can be 
	computed as (nPiecesStart - doneMoveCnt).
     */
    int doneMoveCnt;
    @Transient
    Vector<Pick> transcript = new Vector<>();
  
    /** Set when appropriate at the end of the episode */
    boolean stalemate = false;
    boolean cleared = false;
    boolean givenUp = false;
    boolean lost = false;
    boolean earlyWin = false;
    
    /** Which row of rules do we look at now? (0-based) */
    @Transient
    protected int ruleLineNo = 0;

    @Transient
    protected RuleLine ruleLine = null;

    @Transient
    private EpisodeMemory memory = new EpisodeMemory();
    
    /** Will return true if this is, apparently, an episode restored
	from SQL server, and cannot be played anymore because the boad
	position and the egine state (ruleLine) is not persisted. This
	is a rare event; it may only become an issue if an episode for
	some reason became persisted before it was finished (this is
	rare; can be caused by cascading e.g. on an "Accept Bonus" event), 
	and then the web app was restarted before the episode
	was finished (this is even rarer).
    */
    boolean isNotPlayable() {
	return ruleLine == null;
    }
    
    
    /** Our interface to the current rule line. It includes the underlying 
	rule line and the current counters which indicate how many times
	the atoms in that line have been used. 	When pieces are removed, this
	structure updates itself, until it cannot pick any pieces anymore. */
    class RuleLine {
	final RuleSet.Row row;
	/** Negative values mean "no restriction" */
	private int ourGlobalCounter;
	private int ourCounter[];

	private String showCounter(int k) {
	    return k<0? "*" : "" + k;
	}

	public String explainCounters() {
	    Vector<String> v = new Vector<>();
	    for(int k:  ourCounter) v.add(showCounter(k));
	    return 
		showCounter(ourGlobalCounter)+ " / " + String.join(",", v);
	}

	
	public String toString() {
	    return "[RL: " + row.toSrc() + " / " + explainCounters() + "]";
	}

	
	RuleLine(RuleSet rules, int rowNo) {
	    if (rowNo < 0 || rowNo >= rules.rows.size()) throw new IllegalArgumentException("Invalid row number");
	    row = rules.rows.get(rowNo);
	    ourGlobalCounter = row.globalCounter;
	    ourCounter = new int[row.size()];
	    for(int i=0; i<ourCounter.length; i++) ourCounter[i] = row.get(i).counter;
	    doneWith = exhausted() || !buildAcceptanceMap();
	    //   	    System.err.println("Constructed "+ this+"; exhausted()=" + exhausted()+"; doneWith =" + doneWith);
	}

	/** This is set once all counters are exhausted, or no more
	    pieces can be picked by this rule. The caller should check
	    this flag after every use, and advance ruleLine */
	boolean doneWith =false;

	/** acceptanceMap[pos][atomNo][dest] if the rule atom[atomNo] 
	    in the current rule line allows moving the piece currently
	    at pos to bucket[dest]. */
	private BitSet[][] acceptanceMap = new BitSet[Board.N*Board.N+1][];
	protected boolean[] isMoveable = new boolean[Board.N*Board.N+1];

	/** Computes the probability that a random pick by a frugal player
	    (one who does not repeat a failed pick) would be successful.
	    @param knownFailedPicks The number of distinct pieces that the 
	    player has already tried to pick (and failed) from this board.
	    This reduces the denominator (= the number of still-not-tested
	    pieces). 

	    This method should not be called on episodes where movable
	    pieces are shown to the player, since a frugal player won't
	    ever do picks there. (And the GUI should not allow picks in
	    such episodes anyway).	    
	 */

	double computeP0ForPicks(int knownFailedPicks) {
	    int countPieces=0, countMovablePieces=0;
	    for(int pos=0; pos<pieces.length; pos++) {		
		if (pieces[pos]!=null) {
		    countPieces++;
		    if (isMoveable[pos]) countMovablePieces++;
		}		
	    }
	    if ( knownFailedPicks>countPieces) throw new IllegalArgumentException("You could not have really tried to pick " +knownFailedPicks+ " pieces, as the board only has " +  countPieces);
	    countPieces -= knownFailedPicks;
	    if (countMovablePieces>countPieces)  throw new IllegalArgumentException("What, there are more moveable pieces (" + countMovablePieces+") than not-tested-yet pieces ("+countPieces+")?");
	    return (countPieces==0)? 0.0: countMovablePieces/(double)countPieces;
	}

	 
	/** Computes the probability that a random move would be successful */
  	double computeP0ForMoves(int knownFailedMoves) {
	    int countMoves=0, countAllowedMoves=0;
	    for(int pos=0; pos<pieces.length; pos++) {		
		if (pieces[pos]!=null) {
		    // In the show-movables mode, only movable pieces are taken into account
		    if (weShowAllMovables() && !isMoveable[pos]) continue;

		    
		    countMoves += 4;

		    BitSet a = new BitSet();
		    for(BitSet z: acceptanceMap[pos]) {
			a.or(z);
		    }
		    countAllowedMoves += a.cardinality();
		}		
	    }
	    if ( knownFailedMoves>countMoves) throw new IllegalArgumentException("You could not have really tried to make " +knownFailedMoves+ " moves, as the board only has enough pieces for " +  countMoves);
	    countMoves -= knownFailedMoves;
	    
	    if (countAllowedMoves>countMoves)  throw new IllegalArgumentException("What, there are more allowed moves (" + countAllowedMoves+") than not-tested-yet moves ("+countMoves+")?");
	    return (countMoves==0)? 0.0: countAllowedMoves/(double)countMoves;
	}
	

	/** For each piece, where can it go? (OR of all rules). 
	    The acceptanceMap needs to be computed before this
	    method can be called.
	 */
	BitSet[] moveableTo() {
	    BitSet[] q =  new BitSet[Board.N*Board.N+1];
	    for(int pos=0; pos<pieces.length; pos++) {
		q[pos] = new BitSet();
		if (pieces[pos]!=null) {
		    BitSet r = new BitSet(); // to what buckets this piece can go
		    for(BitSet b: acceptanceMap[pos]) {
			q[pos].or(b);
		    }	

		}
	    }
	    return q;	    
	}
	
	/** For each piece currently on the board, find which rules in the 
	    current rule line allow this piece to be moved, and to which buckets.
	    @return true if at least one piece can be moved
	*/
	private boolean buildAcceptanceMap() {

	    EligibilityForOrders eligibleForEachOrder = new EligibilityForOrders(rules, onBoard());
	    //System.err.println("eligibileForEachOrder=" + eligibleForEachOrder);

	    
	    for(int pos=0; pos<pieces.length; pos++) {
		if (pieces[pos]==null) {
		    acceptanceMap[pos]=null;
		    isMoveable[pos]=false;
		} else {
		    acceptanceMap[pos] = pieceAcceptance(pieces[pos], eligibleForEachOrder);
		    BitSet r = new BitSet(); // to what buckets this piece can go
		    for(BitSet b: acceptanceMap[pos]) {
			r.or(b);
		    }
		    isMoveable[pos]= !r.isEmpty();
		}
	    }
	    return  isAnythingMoveable();
	}

	/** Looks at the current acceptance map to see if any of the 
	    currently present pieces can be moved */
	private boolean isAnythingMoveable() {
	    for(int pos=0; pos<pieces.length; pos++) {
		if (pieces[pos]!=null && isMoveable[pos]) return true;
	    }
	    return false;	
	}

	
	/** Into which buckets, if any, can the specified piece be moved?
	    (The original method, used in GS 1 thru 4)
	   @return result[j] is the set of buckets into which the j-th
	   rule (atom) allows the specified piece to be moved.
	*/
	private BitSet[] pieceAcceptance0(Piece p,  EligibilityForOrders eligibleForEachOrder) {
	    
	    //	    System.err.println("DEBUG: pieceAcceptance(p=" +p+")");
	    if (doneWith) throw new IllegalArgumentException("Forgot to scroll?");

	    if (row.globalCounter>=0 &&  ourGlobalCounter<=0)  throw new IllegalArgumentException("Forgot to set the scroll flag on 0 counter!");
	    
	    // for each rule, the list of accepting buckets
	    BitSet whoAccepts[] = new BitSet[row.size()];
	    Pos pos = p.pos();
	    BucketVarMap  varMap = memory.new  BucketVarMap(p);

	    //System.err.println("varMap=" + varMap);

	    
	    for(int j=0; j<row.size(); j++) {
		whoAccepts[j] = new BitSet(NBU);
		RuleSet.Atom atom = row.get(j);
		if (atom.counter>=0 && ourCounter[j]==0) continue;
		if (!atom.acceptsColorShapeAndProperties(p, null)) continue;
		//System.err.println("Atom " +j+" shape and color OK");
		if (!atom.plist.allowsPicking(pos.num(), eligibleForEachOrder)) continue;
		//System.err.println("DEBUG: Atom " +j+" allowsPicking ok");
		BitSet d = atom.bucketList.destinations( varMap);
		whoAccepts[j].or(d);
		//System.err.println("pieceAcceptance(p=" +p+"), dest="+d+", whoAccepts["+j+"]=" + 	whoAccepts[j]);
	    }
	    return whoAccepts;
	}

	/** For GS 5. Here, we try each bucket separately, as the
	    destination bucket may affect some variables.
	 */	
	private BitSet[] pieceAcceptance(Piece p,  EligibilityForOrders eligibleForEachOrder) {
	    
	    //	    System.err.println("DEBUG: pieceAcceptance(p=" +p+")");
	    if (doneWith) throw new IllegalArgumentException("Forgot to scroll?");

	    if (row.globalCounter>=0 &&  ourGlobalCounter<=0)  throw new IllegalArgumentException("Forgot to set the scroll flag on 0 counter!");

	    
	    // for each rule, the list of accepting buckets
	    BitSet whoAccepts[] = new BitSet[row.size()];
	    for(int j=0; j<row.size(); j++) {
		whoAccepts[j] = new BitSet(NBU);
	    }
	    Pos pos = p.pos();

	    // try each bucket separately
	    for(int bucketNo=0; bucketNo<NBU; bucketNo++) {
	    
		BucketVarMap2  varMap = memory.new BucketVarMap2(p, bucketNo);

		//System.err.println("varMap=" + varMap);
		
	    
		for(int j=0; j<row.size(); j++) {
		    RuleSet.Atom atom = row.get(j);
		    if (atom.counter>=0 && ourCounter[j]==0) continue;
		    if (!atom.acceptsColorShapeAndProperties(p, varMap)) continue;
		    if (!atom.plist.allowsPicking(pos.num(), eligibleForEachOrder)) continue;
		    boolean can = atom.bucketList.destinationAllowed( varMap, bucketNo);
		    if (can) whoAccepts[j].set(bucketNo);
		}
	    }
	    return whoAccepts;
	}
    
	/** Requests acceptance for this move or pick. In case of
	    acceptance of an actual move (not just a pick), decrements
	    appropriate counters, and removes the piece from the
	    board.

	    @return result  (accept/deny)
	*/
	int accept(Pick pick) {

	    //System.err.println("RL.accept: "+this+", move="+ move);
	    
	    if (doneWith) throw  new IllegalArgumentException("Forgot to scroll?");
	    transcript.add(pick);
	    attemptCnt++;
	    attemptSpent += (pick instanceof Move) ? 1.0: xgetPickCost();

	    pick.piece =pieces[pick.pos];
	    if (pick.piece==null) return pick.code=CODE.EMPTY_CELL;	    
	    pick.pieceId = (int)pick.piece.getId();
	    
	    if (!(pick instanceof Move)) {
		pick.code = isMoveable[pick.pos]? CODE.ACCEPT: CODE.DENY;
		return pick.code;
	    }
	    Move move  = (Move) pick;

	    BitSet[] r = acceptanceMap[pick.pos];
	    
	    Vector<Integer> acceptingAtoms = new  Vector<>();
	    Vector<String> v = new Vector<>();
	    for(int j=0; j<row.size(); j++) {
		if (r[j].get(move.bucketNo)) {
		    v.add("" + j + "(c=" + ourCounter[j]+")");
		    acceptingAtoms.add(j);
		    if (ourCounter[j]>0) {
			ourCounter[j]--;
		    }
		}
	    }
	    
	    //System.err.println("Acceptors: " + String.join(" ", v));
	    if (acceptingAtoms.isEmpty()) return  move.code=CODE.DENY;

	    //--- Process the acceptance -------------------------
	    if (ourGlobalCounter>0) ourGlobalCounter--;

	    doneMoveCnt++;

	    // Remember where this piece was moved
	    memory.enterMove(move.piece, move.bucketNo);

	    pieces[move.pos].setBuckets(new int[0]); // empty the bucket list for the removed piece
	    removedPieces[move.pos] = pieces[move.pos];
	    removedPieces[move.pos].setDropped(move.bucketNo);
	    pieces[move.pos] = null; // remove the piece
	    //System.err.println("Removed piece from pos " + move.pos);
		    
	    // Check if this rule can continue to be used, and if so,
	    // update its acceptance map
	    doneWith = exhausted() || !buildAcceptanceMap();
	    //System.err.println("doneWith=" + doneWith);
	    
	    //System.err.println("Episode.accept: move accepted. card=" + onBoard().cardinality());
	    cleared = (onBoard().cardinality()==0);
	    
	    //	    Logging.info("Accepted, return " + CODE.ACCEPT);
	    return  move.code=CODE.ACCEPT;
	}


	/** Is this row of rules "exhausted", based either on the
	    global counter for the row, or the individual rules?
	    "Control moves to the next row when either all counters OR
	    the global counter reach zero", as per Paul's specs.
	*/
	boolean exhausted() {
	    if (row.globalCounter>=0 && ourGlobalCounter==0) return true;
	    for(int j=0; j<row.size(); j++) {
		if (row.get(j).counter<0) return false; // "*" cannot be exhaustd
		if (ourCounter[j]>0) return false;
	    }
	    return true;
	}
	
    }
    
  
    
    @Transient
    private OutputMode outputMode;
    @Transient
    private final PrintWriter out;
    /** In the captive server, this is where the client's commands come from.
	This is null in web server. */
    @Transient
    private final Reader in;
   
    public static final DateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
    /** with milliseconds */
    public static final DateFormat sdf2 = new SimpleDateFormat("yyyyMMdd-HHmmss.SSS");

    public static final RandomRG random = new RandomRG();
    
    /** Creates a word made out of random letters and digits */
    public static String randomWord(int len) {
	StringBuffer b=new StringBuffer(len);
	for(int i=0; i<len; i++) {
	    int k =  random.nextInt(10 + 'Z'-'A'+1);
	    char c = (k<10) ? (char)('0' + k) : (char)('A' + k-10);
	    b.append(c);
	}
	return b.toString();
    }

    
    /** Creates a more or less unique string ID for this Episode object */
    private String buildId() {
	return sdf.format(startTime) + "-" +randomWord(6);
    }

    /** Dummy constructor; only used for error code production, and maybe
	also by JPA when restoring a player's info (with all episodes)
	from the database.
    */
    public Episode() {
	episodeId=null;
	rules=null;
	out=null;
	in=null;
	nPiecesStart = 0;
	startTime = new Date();
    };

    /** The initial number of pieces on the board */
    @Basic
    private int nPiecesStart;
    public int getNPiecesStart() { return nPiecesStart; }
    public void setNPiecesStart(int _nPiecesStart) { nPiecesStart = _nPiecesStart; }


    /** Creates a new Episode for a given Game (which defines rules and the 
	properties of the initial board). Depending on what the Game is,
	the episode may use a pre-created board stored in the Game object,
	or a random board created on the fly.
	@param _in The input stream for commands; it will be null in the web app
	@param _out Will be null in the web app.
    */
    public Episode(Game game, OutputMode _outputMode, Reader _in, PrintWriter _out) {
	this( game,  _outputMode,  _in,  _out, null);	
    }

    protected Episode(Game game, OutputMode _outputMode, Reader _in, PrintWriter _out, String _episodeId ) {
	startTime = new Date();    
	in = _in;
	out = _out;
	outputMode = _outputMode;
	episodeId = (_episodeId==null)?  buildId():    _episodeId;
    
	rules = game.rules;
	Board b =  game.initialBoard;
	if (b==null) {
	    if (game.imageGenerator!=null) {
		b = new Board(game.random,  game.randomObjCnt,  game.imageGenerator);
	    } else { 
		b = new Board(game.random,  game.randomObjCnt, game.nShapes, game.nColors, game.allShapes, game.allColors);
	    }
	}
	nPiecesStart = b.getValue().size();
	for(Piece p: b.getValue()) {
	    Pos pos = p.pos();
	    pieces[pos.num()] = p;
	}
	doPrep();
	
    }

    /** Return codes for the /move and /display API web API calls,
	and for the MOVE command in the captive game server.
     */
    public static class CODE {
	public static final int
	/** move accepted and processed */
	    ACCEPT = 0,
	/** Move rejected, and no other move is possible
	    (stalemate). This means that the rule set is bad, and we
	    owe an apology to the player */
	    STALEMATE=2,
	/** move rejected, because there is no piece in the cell */
	    EMPTY_CELL= 3,
	/** move rejected, because this destination is not allowed */
	    DENY = 4,
	/** Exit requested */
	    EXIT = 5,
	/** New game requested */
	    NEW_GAME = 6
	    ;
	
	public static final int
	    INVALID_COMMAND= -1,
	    INVALID_ARGUMENTS= -2,
	    INVALID_POS= -3,
	// No game is on now. Start a game first!
	    NO_GAME = -4,
	// Used in socket server GAME  command
	    INVALID_RULES = -5,
	// Used in web app, when trying to access a non-existent episode
	    NO_SUCH_EPISODE = -6,
	// The number of preceding attempts does not match. This may indicate
	// that some HTTP requests have been lost, or a duplicate request
	    ATTEMPT_CNT_MISMATCH = -7,
	// This code is returned on successful DISPLAY calls, to
	// indicate that it was a display (no actual move requested)
	// and not a MOVE	    
	    JUST_A_DISPLAY = -8;
	    
    }

    public static class FINISH_CODE {
	public static final int
	/** there are still pieces on the board, and some are moveable */
	    NO = 0,
	/** no pieces left on the board */
	    FINISH = 1,
	/** there are some pieces on the board, but none can be moved anymore */
	    STALEMATE=2,
	/** The player has said he does not want to play any more. This
	    may only happen in some GUI versions. */
	    GIVEN_UP =3,
	/** This was a bonus round which has been terminated by the 
	    system because the player has failed to complete it within the 
	    required number of steps */
	    LOST = 4,
	/** The server decided that the player is so good that 
	    it terminated the episode early, giving the player full
	    points that he'd get if he completed the episode 
	    without any additional errors. This is sometimes done
	    when the DOUBLING incentive scheme is in effect. */
	    EARLY_WIN = 5
	    ;
    }

    /** Creates a bit set with bits set in the positions where there are
	pieces */
    private BitSet onBoard() {
	BitSet onBoard = new BitSet(Board.N*Board.N+1);
	for(int i=0; i<pieces.length; i++) {
	    if (pieces[i]!=null) onBoard.set(i);
	}
	return onBoard;
    }

    @Transient
    private boolean prepReady = false;

    /** Run this method at the beginning of the game, and
	every time a piece has been removed, to update various
	auxiliary data structures.
	@return true, unless stalemate (no piece can be picked) is
	detected, in which case it return false
    */
    private boolean doPrep() {

	// Which pieces currently on the border can be picked under
	// various ordering schemes?
	//	eligibleForEachOrder.update();
	//	System.err.println("doPrep: eligibileForEachOrder=" + eligibleForEachOrder);

	boolean mustUpdateRules = (ruleLine==null || ruleLine.doneWith);

	//	System.err.println("doPrep: mustUpdateRules=" + mustUpdateRules +", ruleLineNo="+ ruleLineNo);


	
	if (mustUpdateRules) {
	    ruleLineNo= (ruleLine==null) ? 0: (ruleLineNo+1) %rules.rows.size();
	    ruleLine = new RuleLine(rules, ruleLineNo);
	    //System.err.println("doPrep: updated ruleLineNo="+ ruleLineNo+", doneWith=" + ruleLine.doneWith);

	    final int no0 = ruleLineNo;
	    // if the new rule is exhausted, or allows no pieces to be moved,
	    // scroll on
	    //	    System.out.println("# BAM(line " + ruleLineNo + ")");
	    while( ruleLine.doneWith) {
		 ruleLineNo=  (ruleLineNo+1) %rules.rows.size();
		 //		 System.err.println("# BAM(scroll to line " + ruleLineNo + ")");
		 // If we have checked all lines, and no line contains
		 // a rule that can move any piece, then it's a stalemate
		 if (ruleLineNo==no0) {
		     stalemate=true;
		     return false;
		 }
		 ruleLine = new RuleLine(rules, ruleLineNo);
	    }
	}
	
	prepReady = true;
	return true;
    }

    /** The last pick or move (successful or failed attempt) */
    @Transient
    private Pick lastMove = null;

    /**    	    One normally should not use this method directly; use
	    doPick() or doMove() instead.
    */
    protected int accept(Pick move) {
	lastMove = move;
	if (stalemate) {
	    return CODE.STALEMATE;
	}
	if (move.pos<1 || move.pos>=pieces.length) return CODE.INVALID_POS;

	int code = ruleLine.accept(move);

	// Update the data structures describing the current rule line, acceptance, etc
	if (move instanceof Move && code==CODE.ACCEPT && !cleared && !earlyWin && !stalemate && !givenUp) {
	    doPrep();
	}
	
	return code;
    }


    /** The basic mode tells the player where all movable pieces are, 
	but EpisodeInfo will override it if the para set mandates "free" mode.
     */
    boolean weShowAllMovables() {
	return true;
    }
 

    private static HTMLFmter fm = new HTMLFmter();


    /** Graphic display of the board */
    public String graphicDisplay() {
	return graphicDisplay(false);
    }

    /** Generates an HTML table displaying the current board state,
	@param html If false, call the ASCII-graphics routine instead (for
	printing on terminal, instead of a web browser)
    */
    public String graphicDisplay(boolean html) {

	int lastMovePos =  (lastMove==null)? -1:  lastMove.pos;
	//	private boolean[] isMoveable = new boolean[Board.N*Board.N+1];
	boolean[] isMoveable = ruleLine.isMoveable;

	if (isNotPlayable()) {
	    return "This episode must have been restored from SQL server, and does not have the details necessary to show the board";
	}
	


	if (!html) return// graphicDisplayAscii(html);
					  graphicDisplayAscii(pieces, lastMovePos,  weShowAllMovables(), isMoveable, html);
	
	String s = 
	    fm.wrap("li", "(X) - a movable piece" +
		    (!weShowAllMovables()? " (only marked on the last touched piece)": "")) +
	    fm.wrap("li","[X] - the position to which the last move or pick attempt (whether successful or not) was applied");
	String result = fm.para( "Notation: " + fm.wrap("ul",s));
	
	result+=doHtmlDisplay(pieces, lastMovePos,  weShowAllMovables(), isMoveable, 80);
	return result;

    }

    /** @param pieces An array of N*N values, with nulls for empty cells */
    public static String doHtmlDisplay(Piece[] pieces, int  lastMovePos, boolean weShowAllMovables, boolean[] isMoveable, int cellWidth) {


	String result="";
	
	ColorMap cm = new ColorMap();
 	
	Vector<String> rows = new Vector<>();
	
	Vector<String> v = new Vector<>();
	v.add(fm.td(""));
	for(int x=1; x<=Board.N; x++) v.add(fm.td("align='center'", "" + x));
	String topRow = fm.tr(String.join("", v));
	rows.add(topRow);
	
	
	for(int y=Board.N; y>0; y--) {
	    v.clear();
	    v.add(fm.td(""+y));

	    //-- we use borders if there are any images with color properties
	    boolean needBorder=false;
	    for(Piece p: pieces) {
		if (p!=null && p.xgetColor()!=null) needBorder=true;
	    }
	    

	    for(int x=1; x<=Board.N; x++) {
		int pos = (new Pos(x,y)).num();
		String sh = "BLANK";
		String hexColor = "#FFFFFF";
		ImageObject io = null;
		
		if (pieces[pos]!=null) {
		    Piece p = pieces[pos];
		    io = p.getImageObject();		    
		    sh = (io!=null) ? io.key : p.xgetShape().toString();
		    hexColor = "#"+ (io!=null? "FFFFFF" : cm.getHex(p.xgetColor(), true));
		}

		//-- The style is provided to ensure a proper color border
		//-- around Ellise's elements, whose color is not affected by
		//-- the background color of TD
		String z = "<img ";
		if (needBorder) z += "style='border: 5px solid "+hexColor+"' ";
		String ke = null;
		try {
		    ke  = java.net.URLEncoder.encode(sh, "UTF-8");
		} catch( UnsupportedEncodingException ex) {}
		
		z += "width='"+cellWidth+"' src=\"../../GetImageServlet?image="+ke+"\">";
		//z = (lastMove!=null && lastMovePos==pos) ?    "[" + z + "]" :
		//    ruleLine.isMoveable[pos]?     "(" + z + ")" :
		//    "&nbsp;" + z + "&nbsp;";

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
	    rows.add(fm.tr(String.join("", v)));
	}
	rows.add(topRow);
	result+= fm.table("border='1'", rows);
	return result; 
    }


    //(Piece[] pieces, int  lastMovePos, boolean weShowAllMovables, boolean[] isMoveable, int cellWidth) {

    
    /** Retired from the web game server; still used in Captive Game Server. */
    static public String graphicDisplayAscii(
Piece[] pieces, int  lastMovePos, boolean weShowAllMovables, boolean[] isMoveable, 

					boolean html) {

	Vector<String> w = new Vector<>();

	String div = "#---+";
	for(int x=1; x<=Board.N; x++) div += "-----";
	w.add(div);
	
	
	for(int y=Board.N; y>0; y--) {
	    String s = "# " + y + " |";
	    for(int x=1; x<=Board.N; x++) {
		int pos = (new Pos(x,y)).num();
		String z = html? "." :   " .";
		if (pieces[pos]!=null) {
		    Piece p = pieces[pos];
		    ImageObject io = p.getImageObject();
		    z = (io!=null)? io.symbol() :  p.xgetShape().symbol();
		    if (html) {
			String color =  p.getColor();
			if (color!=null) z=fm.colored( color.toLowerCase(), z);
			z = fm.wrap("strong",z);
		    } else {
			Piece.Color color = p.xgetColor();
			if (color!=null) z = color.symbol() + z;
		    }
		}

		z = (lastMovePos==pos) ?    "[" + z + "]" :
		    isMoveable[pos]?     "(" + z + ")" :
		    " " + z + " ";

		s += " " + z;
	    }
	    w.add(s);
	}
	w.add(div);
	String s = "#   |";
	for(int x=1; x<=Board.N; x++) s += "  "+(html?"": " ") + x + " ";
	w.add(s);
	return String.join("\n", w);
    }

    int getFinishCode() {
	 return cleared? FINISH_CODE.FINISH :
	     stalemate? FINISH_CODE.STALEMATE :
	     givenUp?  FINISH_CODE.GIVEN_UP :
	     lost?  FINISH_CODE.LOST :
	     earlyWin? FINISH_CODE.EARLY_WIN :
	     FINISH_CODE.NO;
    }
       

    private void respond(int code, String msg) {
	String s = "" + code + " " + getFinishCode() +" "+attemptCnt;
	if (msg!=null) s += "\n" + msg;
	out.println(s);
    }    
    

    /** Returns the current board, or, on a restored-from-SQL-server episodes,
	null (or empty board, to keep the client from crashing).
    */
    Board getCurrentBoard(boolean showRemoved) {
	if (isNotPlayable()) {
	    return cleared? new Board() : null;
	} else {
	    return new Board(pieces,
			     (showRemoved?removedPieces:null),
			     ruleLine.moveableTo());
	}
    }

    /** Shows the current board (without removed [dropped] pieces) */
    public Board getCurrentBoard() {
	return getCurrentBoard(false);
    }

    /** No need to show this field */
    private static final HashSet<String> excludableNames =  Util.array2set("dropped");
    
    private String displayJson() {
	Board b = getCurrentBoard();
	JsonObject json = JsonReflect.reflectToJSONObject(b, true, excludableNames);
	return json.toString();
    }


    public static final String version = "5.002";

    public static String getVersion() { return version; }

    private String readLine( LineNumberReader​ r) throws IOException {
	out.flush();
	return r.readLine();
    }

    /** Can be sent to the web client in JSON format, where it would 
	be used to display the current state of the episode */
    public class Display     {
	// The following describe the state of this episode, and are only used in the web GUI
	int finishCode = Episode.this.getFinishCode();
	Board board =  getCurrentBoard(true);

	/** What pieces are on the board now, and what pieces have been removed */
        public Board getBoard() { return board; }
        @XmlElement
        public void setBoard(Board _b) { board = _b; }

	/** Is this episode still continues (code 0), has stalemated (2), or has the board been cleared (4)? */
	public int getFinishCode() { return finishCode; }
    
	int code;
	String errmsg;

	/** A kludgy attempt to get the client not to display the board when
	    the episode is not playable */
	boolean error=false;
	public boolean getError() { return error; }
	@XmlElement
	public void setError(boolean _error) { error = _error; }
 
	
	/** On a /move call: Has this move been accepted or rejected? (When returned by /display response, the value is -8). */
        public int getCode() { return code; }
        @XmlElement
        public void setCode(int _code) { code = _code; }
	/** The error or debug messsage, if any */
        public String getErrmsg() { return errmsg; }
        @XmlElement
        public void setErrmsg(String _msg) { errmsg = _msg; }

       	int numMovesMade = Episode.this.attemptCnt;
	/** How many move attempts (successful or not) has been made so far */
        public int getNumMovesMade() { return numMovesMade; }
        @XmlElement
        public void setNumMovesMade(int _numMovesMade) { numMovesMade = _numMovesMade;}
	
	private Vector<Pick> transcript =  Episode.this.transcript;
	/** The list of all move attempts (successful or not) done so far
	    in this episode */
	public Vector<Pick> getTranscript() { return transcript; }

	RuleSet.ReportedSrc rulesSrc = (rules==null)? null:rules.reportSrc();
	/** A structure that describes the rules of the game being played in this episode. */
	public RuleSet.ReportedSrc getRulesSrc() { return rulesSrc; }

	
	String explainCounters =
	    Episode.this.ruleLine==null? null:
	    Episode.this.ruleLine.explainCounters();
	/** The "explanation" of the current state of the current rule line */
	public String getExplainCounters() { return explainCounters; }
	

	int ruleLineNo = Episode.this.ruleLineNo;
	/** Zero-based position of the line of the rule set that the
	    game engine is currently looking at. This line will be the
	    first line the engine will look at when accepting the
	    player's next move. */
	public int getRuleLineNo() { return ruleLineNo; }


	public Display(int _code, 	String _errmsg) {
	    code = _code;
	    errmsg = _errmsg;
	}
    }

    /** Builds a Display objecy to be sent out over the web UI upon a /display
	call (rather than a /move or /pick) */
    public Display mkDisplay() {
    	return new Display(Episode.CODE.JUST_A_DISPLAY, "Display requested");
    }

    /** Checks for errors in some of the arguments of a /pick or /move call.
	@return a Display reporting an error, or null if no error has been found
    */
    private Display inputErrorCheck1(int y, int x, int _attemptCnt) {
	if (isCompleted()) {
	    return new Display(CODE.NO_GAME, "No game is on right now (cleared="+cleared+", stalemate="+stalemate+", earlyWin="+earlyWin+"). Use NEW to start a new game");
	}

	if (_attemptCnt != attemptCnt)  return new Display(CODE.ATTEMPT_CNT_MISMATCH, "Given attemptCnt="+_attemptCnt+", expected " + attemptCnt);
		
	if (x<1 || x>Board.N) return new Display(CODE.INVALID_ARGUMENTS, "Invalid input: column="+x+" out of range");
	if (y<1 || y>Board.N) return new Display(CODE.INVALID_ARGUMENTS, "Invalid input: row="+y+" out of range");
	return null;
    }
    
    /** Evaluate a pick attempt */
    public Display doPick(int y, int x, int _attemptCnt) throws IOException {
	Display errorDisplay =inputErrorCheck1(y, x, _attemptCnt);
	if (errorDisplay!=null) return errorDisplay;
	Pos pos = new Pos(x,y);
	Pick move = new Pick( pos.num());
	int code = accept(move);
	return new Display(code, mkDisplayMsg());
    }
    
    /** Evaluate a move attempt */
    public Display doMove(int y, int x, int by, int bx, int _attemptCnt) throws IOException {
	Display errorDisplay =inputErrorCheck1(y, x, _attemptCnt);
	if (errorDisplay!=null) return errorDisplay;
	if (bx!=0 && bx!=Board.N+1) return new Display(CODE.INVALID_ARGUMENTS, "Invalid input: bucket column="+bx+" is not 0 or "+(Board.N+1));
	if (by!=0 && by!=Board.N+1) return new Display(CODE.INVALID_ARGUMENTS, "Invalid input: bucket row="+by+" is not 0 or "+(Board.N+1));

	Pos pos = new Pos(x,y), bu =  new Pos(bx, by);
	int buNo=bu.bucketNo();
	if (buNo<0 || buNo>=Board.buckets.length) {
	    return new Display(CODE.INVALID_ARGUMENTS, "Invalid bucket coordinates");
	}
	Move move = new Move(pos.num(), buNo);
	int code = accept(move);
	return new Display(code, mkDisplayMsg());
    }

    /** A message to put along with the accept/deny code into the Display object */
    private String mkDisplayMsg() {
    	return 
	    (outputMode==OutputMode.BRIEF) ? null:
	    cleared?  "Game cleared - the board is clear" :
	    stalemate?  "Stalemate - no piece can be moved any more. Apology for these rules!" :
	    givenUp? "You have given up this episode" :
	    earlyWin?  "You won by a long error-free stretch" :
	    null;
    }

    /** Lets this episode play out until either all pieces are
	cleared, or a stalemate is reached, or the player gives up
	(sends an EXIT or NEW command). The episode takes commands from
	the reader.
	@param log If not null, save the result of each episode there
	@return true if another episode is requested, i.e. the player
	has entered a NEW command. false is returned if the player
	enters an EXIT command, or simply closes the input stream.
    */
    public boolean playGame(int gameCnt) throws IOException {
	try {
	String msg = "# Hello. This is Captive Game Server ver. "+version+". Starting a new episode (no. "+gameCnt+")";
	if (stalemate) {
	    respond(CODE.STALEMATE, msg + " -- immediate stalemate. Our apologies!");
	} else {
	    respond(CODE.NEW_GAME, msg);
	}
	out.println(displayJson());
	if (outputMode==OutputMode.FULL) out.println(graphicDisplay());
	
	LineNumberReader​ r = new LineNumberReader​(in);
	String line = null;
	while((line=readLine(r))!=null) {
	    line = line.trim();
	    if (line.equals("")) continue;
	    Vector<Token> tokens;
	    try {
		tokens    = Token.tokenize(line);
	    } catch(RuleParseException ex) {
		respond(CODE.INVALID_COMMAND,"# Invalid input - cannot parse");
		continue; 		
	    }
	    if (tokens.size()==0 || tokens.get(0).type!=Token.Type.ID) {
		respond(CODE.INVALID_COMMAND, "# Invalid input");
		continue;
	    }
	    String cmd = tokens.get(0).sVal.toUpperCase();
	    if (cmd.equals("EXIT")) {
		respond(CODE.EXIT, "# Goodbye");
		return false;
	    } else if (cmd.equals("VERSION")) {
		out.println("# " + version);
	    } else if (cmd.equals("NEW")) {
		return true;
	    } else if (cmd.equals("HELP")) {		
		out.println("# Commands available:");
		out.println("# MOVE row col bucket_row bucket_col");
		out.println("# NEW");
		out.println("# DISPLAY");
		out.println("# DISPLAYFULL");
		out.println("# MODE <BRIEF|STANDARD|FULL>");
		out.println("# EXIT");
	    } else if (cmd.equals("DISPLAY")) {
		out.println(displayJson());
		if (outputMode==OutputMode.FULL) out.println(graphicDisplay());
	    } else if (cmd.equals("DISPLAYFULL")) {
		out.println(displayJson());
		out.println(graphicDisplay());
	    } else if (cmd.equals("MODE")) {
		tokens.remove(0);
		if (tokens.size()!=1 || tokens.get(0).type!=Token.Type.ID) {
		    respond(CODE.INVALID_ARGUMENTS, "# MODE command must be followed by the new mode value");
		    continue;
		}
		String s = tokens.get(0).sVal.toUpperCase();
		try {
		    outputMode= Enum.valueOf( OutputMode.class, s);
		} catch(IllegalArgumentException ex) {
		    respond(CODE.INVALID_ARGUMENTS, "# Not a known mode: " + s);
		    continue;
		}
		out.println("# OK, mode=" + outputMode);
	    } else if (cmd.equals("MOVE")) {
		
		tokens.remove(0);
		if (tokens.size()!=4) {
		    respond(CODE.INVALID_ARGUMENTS, "# Invalid input");
		    continue;
		}
		int q[] = new int[4];
		// y x By Bx

		boolean invalid=false;
		for(int j=0; j<4; j++) {
		    if (tokens.get(j).type!=Token.Type.NUMBER) {
			respond(CODE.INVALID_ARGUMENTS, "# Invalid input: "+tokens.get(j));
			invalid=true;
			break;
		    }
		    q[j] = tokens.get(j).nVal;

		}
		if (invalid) continue;

		Display mr = doMove(q[0], q[1], q[2], q[3], attemptCnt);
		respond(mr.code, "# " + mr.errmsg);
		if (outputMode!=OutputMode.BRIEF) out.println(displayJson());
		if (outputMode==OutputMode.FULL) out.println(graphicDisplay());
		
	    } else {
		respond(CODE.INVALID_COMMAND, "# Invalid command: " +cmd);
		continue;
	    }
	}
	return false;
	} finally {
	    out.flush();
	}
    }

    public enum OutputMode {
	// One-line response on MOVE; no comments
	BRIEF,
	// Two-line response on MOVE; no comments
	STANDARD,
	// Two-line response on MOVE; human-readable comments
	FULL}; 

    /** @return true if this episode cannot accept any more move attempts,
	for any reason (board cleared, stalemate, given up, lost).
     */
    boolean isCompleted() {
	return cleared || earlyWin|| stalemate || givenUp || lost;
    }

    /** Marks this episode as "given up" (unless it's already marked
	as completed in some other way) */
    void giveUp() {
	if (!isCompleted()) givenUp = true;
    }

    static final String file_writing_lock = "Board file writing lock";
	
        /** Concise report, handy for debugging */
    public String report() {
	return "["+episodeId+"; FC="+getFinishCode()+
	    " " +
	    attemptCnt + "/"+getNPiecesStart()  +
	    "]";
    }
    
}
