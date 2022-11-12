<%@include file="../include/imports.jsp" %>
<%
   String sp="launch/launch-rules-brm.jsp";
   LaunchRules main=new LaunchRules(request, response, LaunchRules.Mode.BRM);
%>
<html>
<head>
<title>Sample Games Launch Page (for BRM readers)</title>
</head>
<body>
<h1>Sample Games Launch Page (for BRM readers)</h1>

<h2>Try out a few rule sets!</h2>

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

<%= main.tableText%>

<hr>
<h2>Instructions for the experiment manager</h2>

<p>The experiment manager can find the setup instructions for this page <a href="launch-setup.html">here</a>.


<hr>
<form action="../LogoutServlet">
<input type="hidden" name="redirect" value="launch/launch-rules-brm.jsp">
<button type="submit">Log out</button>
</form>

<% 
} %>

<hr>
<p><small>Info Message: <%= main.infomsg%></small>

</body>
</html>
