<%@include file="include/imports.jsp" %>
<%
FrontEndForm2 main = new FrontEndForm2(request,response);
String redirectURL = main.clientUrl + "?server=" + main.serverUrl + "&exp=" +main.exp +
"&workerId=" + main.prefix+ "auto-" + main.stamp;
response.sendRedirect(redirectURL);%>
