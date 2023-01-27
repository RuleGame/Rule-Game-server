package edu.wisc.game.web;

import java.io.*;
import java.util.*;
import java.text.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import javax.persistence.*;


import edu.wisc.game.util.*;
import edu.wisc.game.sql.*;
import edu.wisc.game.formatter.*;
import edu.wisc.game.rest.*;


/** As requested by Paul, "Rule Game -- challenge UWisc faculty and staff",
    2021-09-23.

<pre> What I am thinking is a table with rows corresponding to
specific rules, and columns that would lead to a way to play the game
(with a logic like what we have at Selected Games to Isolate Facets
(rutgers.edu) which makes a workerId such as
"pbk_pkZhistory_A_7412708" [The Z was because I was not sure that "/"
is allowed in a workerId; the last part is some digits from the time,
which lets me continue as pbk whenever I use it] and brings the player
into the game.

I'd like  for you  to select about  a dozen rules  that you  think are
interesting and kind  of span the syntax, and, in  particular, what we
have already worked with.

Then, could you set up the corresponding trial files?.  No need to
ever offer the bonus, I think, and we can let people play a lot of
boards (say, as many as 10).

New rules in the same "learning class" as existing ones are also OK,
and thinking about that may advance the arm dealing with the
smartphone version. In that arm, we probably want to be able to say
something like "try easy games first" and, as you suggested, be able
to generate new ones in various classes.

The columns of the table for this researcher challenge should provide these options:

1 play with minimal information; no Xs; no test tubes or numbers displayed
2 play with memory aids -- test tubes and numbers displayed
3 with memory aids and movability signs.
4 "click this to see the rule itself"
</pre>

*/
public class LaunchMain  extends LaunchRulesBase  {

    public LaunchMain(HttpServletRequest request, HttpServletResponse response)  {
	super(request,response);
	if (error || !loggedIn()) return;
			  
	String z = "APP"; // can use APP's mods for now
	String[] modsLong = {z + "/" + z + "-no-feedback",
			     z + "/" + z + "2-some-feedback",
			     z + "/" + z + "2-more-feedback",
			     z + "/" + z + "-max-feedback"};

	String z1 = "APP-short";
	String[] modsShort = {z1 + "/" + z + "-short-no-feedback",
			     z1 + "/" + z + "2-short-some-feedback",
			     z1 + "/" + z + "2-short-more-feedback",
			     z1 + "/" + z + "-short-max-feedback"};

	String[] hm = {"No feedback",
		       "Some feedback",
		       "More feedback",
		       "Max feedback" };


	// As per Paul's request, give them debug mode
	mustUseDevClient = true;
	buildTable(modsShort, modsLong, hm, Mode.MLC, null);
    }

    
 
}
