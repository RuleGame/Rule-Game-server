<%@include file="../include/imports.jsp" %>
<%
   String sp="launch/index.jsp";
   LaunchMain main=new LaunchMain(request, response);
%>
<html>
<head>
<title>MLC Rule Game Launch Page</title>
</head>
<body>
<h1>MLC Rule Game Launch Page</h1>

<%   if (main.getError()) {   %>  <%@include file="../include/error.jsp" %>
<%   } else if (!main.loggedIn()) { %>

<%@include file="login_form.jsp" %>

<%
} else {
%>

<h2>
Welcome <%= main.getDisplayText() %>
</h2>

<%= main.tableText%>


<hr>

<p>See also the <a href="launch-rules.jsp">students' page (APP)</a>


<hr>
<h2>Instructions for the experiment manager</h2>

<p>The experiment manager can find the setup instructions for this page <a href="launch-setup.html">here</a>.


<hr>
<a href="../LogoutServlet">Log out</a>

<% 
} %>

<hr>
<p><small>Info Message: <%= main.infomsg%></small>


</body>
</html>
