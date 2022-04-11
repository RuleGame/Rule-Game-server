<%@include file="include/imports.jsp" %>
<%
ImageObjectReport main	= new ImageObjectReport(request, response);
%>
<html>
<head>
<title>Image Object Report</title>
</head>
<body>
<h1>Image Object Report</h1>

<%   if (main.getError()) {   %>  <%@include file="include/error.jsp" %>
<%
} else {
%>
<p>Name: <tt><%= main.name %>
<p><img src="GetImageServlet?image=<%= main.name %>">
<p>Properties:
<ul>
<li>
<%=
main.io.listProperties("\n<li>") 
%>
</ul>

<% 
} %>

<hr>
<p><small>Info Message: <%= main.infomsg%></small>

</body>
</html>
