package edu.wisc.game.sql;

import java.io.*;

import edu.wisc.game.util.*;
import edu.wisc.game.engine.*;

/** Just keeps returning the same game every time */
public class TrivialGameGenerator extends GameGenerator {

    /** Same game every time */
    final Game sameGame;

    
    /** Creates a trivial generator, which keeps returning the same game */
    public TrivialGameGenerator( RandomRG _random, Game g) {
	super(_random, g.rules);
	sameGame = g;
    }

    /** One more game... always the same! */
    public Game nextGame() {
	next(sameGame);
	return sameGame;
    }


}


