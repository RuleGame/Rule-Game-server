<html>
  <head><title>REST JAX-RS test, using XMLHttpRequest </title>
    <!-- Advice on passing data as application/json:
https://www.nabisoft.com/tutorials/java-ee/producing-and-consuming-json-or-xml-in-java-rest-services-with-jersey-and-jackson
https://stackoverflow.com/questions/19446544/post-request-to-include-content-type-and-json
      -->
    <script>
      var cnt=0;
      function foo() {
      cnt++;
      alert("FOO  " + cnt);
       var url = "<%=request.getContextPath() %>";
      url =  url +"/game-data/GameService/piece";
      //      url =  url +"/game-data/GameService/saveBoard";
      alert("Call "+cnt+", Will try GET to url=" + url);
      var xhttp = new XMLHttpRequest();
      xhttp.onreadystatechange = function() {
      if (this.readyState == 4 && this.status == 200) {
      // Typical action to be performed when the document is ready:
      document.getElementById("demo").innerHTML = "("+cnt+") " + xhttp.responseText;
      }
      };
      xhttp.open("GET", url, true);
      xhttp.send();
      }


         function getBoard() {
      cnt++;
      alert("GetBoard  " + cnt);
       var url = "<%=request.getContextPath() %>";
      url =  url +"/game-data/GameService/getBoard";

      alert("Call "+cnt+", Will try POST to url=" + url);
      var xhttp = new XMLHttpRequest();
      xhttp.onreadystatechange = function() {
      if (this.readyState == 4 && this.status == 200) {
      // Typical action to be performed when the document is ready:
      document.getElementById("demo").innerHTML = "("+cnt+") " + xhttp.responseText;
      }
      };
      xhttp.open("POST", url, true);
      xhttp.setRequestHeader('Content-type', 'application/x-www-form-urlencoded');

      var text="id=" + document.getElementById("id").value ;
      xhttp.send(text);
      }

      // This example works
      function saveBoard() {
      cnt++;
      alert("GetBoard  " + cnt);
       var url = "<%=request.getContextPath() %>";
      url =  url +"/game-data/GameService/saveBoard";

      var xhttp = new XMLHttpRequest();
      xhttp.onreadystatechange = function() {
      if (this.readyState == 4 && this.status == 200) {
      // Typical action to be performed when the document is ready:
      document.getElementById("demo").innerHTML = "Result("+cnt+")=" + xhttp.responseText;
      }
      };
      xhttp.open("POST", url, true);
      xhttp.setRequestHeader('Content-type', 'application/json; charset=utf-8');
      let json = document.getElementById("board").value;

      alert("Call "+cnt+", Will try POST to url=" + url +"\n,json="
       + json);

      xhttp.send(json);
      }
      

      
      //foo();
    </script>
    <!--
      var ctxPath = "<%=request.getContextPath() %>";
      $(function(){                
      $("#post1").on("click", function(){
      $.ajax({
      url: ctxPath+"/game-data/GameService/saveBoard",
      type: "POST",
      data: document.getElementById("board").value,
      contentType: "application/json",
      cache: false,
      dataType: "json"
      });
      });                
      });
      -->
  </head>
  <body>
 
    <h2>Test B4</h2>
    <ul>
    <li>    <a href="game-data/GameService/pieceX">Piece XML</a>
    <li>    <a href="game-data/GameService/piece">Piece JSON</a>      
    <li>    <a href="game-data/GameService/boardX">Board XML</a>
    <li>    <a href="game-data/GameService/board">Board JSON</a>
    <li>Get board
      <form method="post" action="game-data/GameService/getBoard"
	      enctype="application/x-www-form-urlencoded">
	id= <input name="id" type="text" id="id">;
	<input type="submit">
      </form>
	or <button id="getBoard" onclick="getBoard();">Get Board using JS 2</button>
    <li>Save new board
      <!-- form --><!-- method="post" action="game-data/GameService/saveBoard"
	      enctype="application/json" -->
	<textarea id="board" rows="6" cols="60">
{"id":0,"name":"Sample board","value":[{"color":"RED","shape":"CIRCLE","x":1,"y":5},{"color":"YELLOW","shape":"STAR","x":2,"y":4}]}
	</textarea>
	<br>
	<!-- input type="submit" -->
	<button id="saveBoard" onclick="saveBoard();">Save Board Description 5</button>



      <!-- /form -->
    <li><a href="gui/">Try the game GUI</a>
      </ul>

      <br><hr>
      The result of pulling data with JS: <div id="demo">NONE YET</a>



  </body>
</html>
  
