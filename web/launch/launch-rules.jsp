<%@include file="../include/imports.jsp" %>
<%
   String sp="launch/index.jsp";
   LaunchRules main=new LaunchRules(request, response);
%>
<html>
<head>
<title>Rule Game -- Try out various rule sets!</title>
</head>
<body>
<h1>Rule Game -- Try out various rule sets!</h1>

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
<a href="../LogoutServlet">Log out</a>

<% 
} %>

<hr>
<p><small>Info Message: <%= main.infomsg%></small>

</body>
</html>
