<%@include file="include/imports.jsp" %>
<html>
<head>
<title>Validate your experiment plan</title>
</head>
<body>
<h1>Validate your experiment plan</h1>

<P>You can use this form to check your experiment plan's input files for errors (syntax errors, references to non-existent files, attempts to generate boards with non-existence colors or shapes, etc). Just choose the name of your experiment plan in the form below, and click on the "Check" button.
  
   <form method="post" action="game-data/CheckPlanService/checkPlan"
	 enctype="application/x-www-form-urlencoded">
	 <tt>
	 <%= Files.listSAllExperimentPlansHtml() %><br>
	     </tt>    
     <button type="submit">Check</button>
   </form>


</body>
</html>
