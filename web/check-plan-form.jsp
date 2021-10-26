<%@include file="include/imports.jsp" %>
<html>
<head>
<title>Validate your experiment plan</title>
</head>
<body>
<h1>Validate your experiment plan</h1>

<h2>Static experiment plans</h2>
<P>You can use this form to check your experiment plan's input files for errors (syntax errors, references to non-existent files, attempts to generate boards with non-existence colors or shapes, etc). Just choose the name of your experiment plan in the form below, and click on the "Check" button.
  
   <form method="post" action="game-data/CheckPlanService/checkPlan"
	 enctype="application/x-www-form-urlencoded">
	 <tt>
	 <%= Files.listSAllExperimentPlansHtml() %><br>
	     </tt>    
     <button type="submit">Check</button>
   </form>


<h2>Dynamic experiment plans</h2>

<p>Type  the name of a plan, e.g. <tt>P:pilot06:max-feedback</tt>,
<tt>R:APP/OCT26/OCT26-1:APP/APP-no-feedback</tt>,
or <tt>R:bottomLeft_then_topRight:simple</tt>

   <form method="post" action="game-data/CheckPlanService/checkPlan"
	 enctype="application/x-www-form-urlencoded">
	 <input type="text" name="exp" size="40">
	 <br>
     <button type="submit">Check</button>
   </form>

<h2>Individual rules</h2>

<p>You can type the text of an individual rule set in the box below, e.g.
<pre>
1 (*, square, *, *, 0) (*, triangle, *, *,1) (*, circle, *, *,2) (*, square, *, *,3)
(*,*,*,*,[p-1,p+1])
</pre>

   <form method="post" action="game-data/CheckPlanService/checkRules"
	 enctype="application/x-www-form-urlencoded">

<textarea  name="rulesText" rows="10" cols="120"></textarea><br>

	 <br>
     <button type="submit">Check</button>
   </form>

</body>
</html>
