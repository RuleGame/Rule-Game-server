<%@include file="include/imports.jsp" %>
<% 
   ArithmeticResult main=new ArithmeticResult(request, response);
%>
<html>
<head>
<title>Arithmetic test: results</title>
</head>
<body>
<h1>Arithmetic test: results</h1>

<%   if (main.getError()) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {
for(String s: main.v) {
%>
<p><%=s %>
</p>
<% }
} %>



<hr>
[<a href="arithmetic-form.jsp">Back to the expression entry form</a>]
[<a href="arithmetic.html">Bucket expression arithmetic</a>]
[<a href="index.html">Main page</a>]



</body>
</html>
