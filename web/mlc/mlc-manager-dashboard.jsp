<%@include file="../include/imports.jsp" %>
<%
   String sp="mlc/mlc-manager-dashboard.jsp";
   MlcManagerDashboard main=new MlcManagerDashboard(request, response);
%>
<html>
<head>
<title>MLC Manager Dashboard</title>
</head>
<body>
<h1>MLC Manager Dashboard</h1>

<%   if (main.getError()) {   %>  <%@include file="../include/error.jsp" %>
<%   } else if (!main.loggedIn()) { %>

<%@include file="login_form.jsp" %>

<%
} else {
%>

<h2>
Welcome <tt><!-- %= main.getDisplayText() % --></tt>
</h2>

<%= main.report%>

<hr>



<hr>
<h2>Instructions for the experiment manager</h2>

<p>The experiment manager can find the setup instructions for the player's pages <a href="mlc-manage.html">here</a>.

<% 
} %>

<hr>
<a href="../LogoutServlet?redirect=/mlc/mlc-manager-dashboard.jsp">Log out</a>


<hr>
<p><small>Info Message: <%= main.infomsg%></small>


</body>
</html>
