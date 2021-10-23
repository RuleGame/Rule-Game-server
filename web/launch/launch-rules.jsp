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
<a href="../LogoutServlet">Log out</a>

<% 
} %>

<hr>
<p><small>Info Message: <%= main.infomsg%></small>

</body>
</html>
