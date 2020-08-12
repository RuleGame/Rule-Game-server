<html>
  <head><title>File writing demo A</title>
   <script>

     function sendData() {
       let url = "<%=request.getContextPath() %>";
      url =  url +"/game-data/GameService/writeFile";
      document.getElementById("demo").innerHTML = "Sending data to " + url + " ...";
      
      alert("Will try POST to url=" + url);
      let xhttp = new XMLHttpRequest();
      xhttp.onreadystatechange = function() {
      if (this.readyState == 4 && this.status == 200) {
      // Typical action to be performed when the result is ready:
      document.getElementById("demo").innerHTML = xhttp.responseText;
      }
      };
      xhttp.open("POST", url, true);
      xhttp.setRequestHeader('Content-type', 'application/x-www-form-urlencoded');


      //let form = document.getElementById("wfForm"); //querySelector(".wfForm");
      //let data = new FormData(form);
      //console.log('form=' + form + "; " + JSON.stringify(form));
      //console.log('data=' + data +  "; " + JSON.stringify(form));

//      let data = new FormData();
//      data.append("dir", document.getElementById("wf.dir").value); 
//      data.append("file", document.getElementById("wf.file").value); 
//      data.append("data", document.getElementById("wf.data").value);

	let data="dir=" +encodeURIComponent( document.getElementById("wf.dir").value) +
	    "&file=" +encodeURIComponent(document.getElementById("wf.file").value) +
	    "&data=" +encodeURIComponent( document.getElementById("wf.data").value);

      xhttp.send(data);
      }
   </script>
  
</head>

<body>
<h1>File writing demo</h1>


<ul>	

   <li><strong>Option 1: sending data using an HTML form</strong>
      <form
        id="wfForm"
        class="wfForm"
	method="post" action="game-data/GameService/writeFile"
	      enctype="application/x-www-form-urlencoded">
	Subdirectory (relative to /opt/tomcat/saved; optional)<input name="dir" type="text" id="wf.dir" size="50" value="test"><br>
	File name (without directory name)<input name="file" type="text" id="wf.file" size="50" value="sample.txt"><br>
	<textarea id="wf.data" name="data" rows="10" cols="60">
Please type some arbitrary text data here
	</textarea><br>
When you click "submit", the data will be sent to the server, where they will be written into a specified file. The success (or error) report, in JSON format, on will be loaded into the browser.
	<input type="submit">
      </form>

<li>  <strong>Option 2: sending data using JavaScript</strong><br>

      <button  onclick="sendData();">Send data using JS</button>
      <br><hr>


      The response from the server will appear below:
       <div id="demo">NONE YET</a>


      </ul>

<hr>

<p>See also <a href="write-2.html">writing example 2</a>, which is identical to this one, but uses absolute URLs.
    

  </body>
</html>
  
