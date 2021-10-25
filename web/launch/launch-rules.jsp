<%@include file="../include/imports.jsp" %>
<%
   String sp="launch/launch-rules.jsp";
   LaunchRules main=new LaunchRules(request, response);
%>
<html>
<head>
<title>APP Rule Game launch page</title>
</head>
<body>
<h1>APP Rule Game launch page</h1>

<h2>Try out various rule sets!</h2>

<%   if (main.getError()) {   %>  <%@include file="../include/error.jsp" %>
<%   } else if (!main.loggedIn()) { %>

<%@include file="login_form.jsp" %>

<%
} else {
%>

<h2>
Welcome <%= main.getDisplayText() %>
</h2>

<p>The rows of the table below represent various rule sets our researchers have prepared for you to play with.

<!--
none
fix the ones that can't be moved
then add stack memory
then add grid memory.
-->


<p>The columns of the table correspond to different types of "feedback" the player receives, viz.:
<ul>
<li>No feedback (hardest) -- no indication of which pieces are movable, no grid memory or stack memory
<li>Some feedback -- non-movable pieces are marked as such (by cross marks)
<li>More feedback -- ditto, with stack memory aids
<li>Max feedback -- ditto, with  stack and grid memory aids
</ul>



<%= main.tableText%>

<hr>
<h2>Instructions for the experiment manager</h2>

<p>This is how you can get a particular rule set to appear in one of the two tables above.

<p>The <strong>Part A</strong> table is generated based on experiment plans you have provided. You may want to use it if your rule set needs to be used with some "non-standard" parameters, such as an unusual number of game pieces, a set of custom colors or custom shapes, etc. For an experiment plan to appear in it, you need to create sthe directory for that experiment plan somewhere under <tt>trial-lists/APP</tt> (can be at some depth in the directory tree) under the server's game data directory. The experiment plan should contain exactly 1 trial list (because we want player assignment to be deterministic here). The trial list file must may contain one or several data lines (after the usual header line);  most likely you will want to have just one data line (unless you want the player to go through two or more rule sets). Each line will specify the rule set you want to expose, along with other necessary control parameters.

<p>The columns of the table correspond to "short modifier files" from <tt>modifers/APP-short</tt>; each of those files only contains columns corresponding to the feedback modalities and the giving-up option. For each table cell, the game server will combine the trial list of the relevant experiment plan with the columns of the relevant modifier file, the later overriding the former. (This is referred to as a "P:" type dynamic experiment plan).


<p>The <strong>Part B</strong> table is generated directly based on rule sets you have provided. You need to place those rules set files into <tt>rules/APP</tt> (or anywhere in the directory tree under that directory). 

<p>The columns of the table correspond to "long modifier files" from <tt>modifers/APP</tt>; each of those files contains all columns that one would normally find in a "simple" trial list file (one that only uses legacy shapes and legacy colors), with the exception of the ruleSet column. For each table cell, the game server will dynamically create a trial list based on the relevant rule set and the relevant  modifier file. (This is referred to as a "R:" type dynamic experiment plan).


<hr>
<a href="../LogoutServlet">Log out</a>

<% 
} %>

<hr>
<p><small>Info Message: <%= main.infomsg%></small>

</body>
</html>
