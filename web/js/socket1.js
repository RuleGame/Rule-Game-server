"use strict";

//---------------------------------------------------------
// The websocket code for HTML pages generated by
// GameService2Html.java
//---------------------------------------------------------


function getQueryVariable(variable)	{
    var query = window.location.search.substring(1);
    var vars = query.split("&");
    for (var i=0;i<vars.length;i++) {
        var pair = vars[i].split("=");
        if(pair[0] == variable){return pair[1];     }
    }
    return(false);
}

var Chat = {};

Chat.socket = null;

Chat.connect = (function(host) {
   if ('WebSocket' in window) {
      Chat.socket = new WebSocket(host);
   } else if ('MozWebSocket' in window) {
      Chat.socket = new MozWebSocket(host);
   } else {
      window.alert('Error: WebSocket is not supported by this browser.');
//       Console.log('Error: WebSocket is not supported by this browser.');
       return;
   }

   Chat.socket.onopen = function () {
//window.alert('send: IAM '  + myPid);            
	Chat.socket.send('IAM '  + myPid);

        var chatBox =   document.getElementById('chat');
        if (chatBox) {
            chatBox.onkeydown = function(event) {
                if (event.keyCode == 13) { Chat.sendChatMessage(); }
            }
	}
		//pid = getQueryVariable("watch");
		//if (pid)	{	                    Chat.socket.send('WATCH '  + pid);            }

   };

   Chat.socket.onclose = function () {
                document.getElementById('chat').onkeydown = null;
                Console.log('Info: WebSocket closed at '+(new Date()));
   };

   Chat.socket.onmessage = function (message) {
         Console.log("Receieved: " + message.data);
         if (message.data.startsWith("READY EPI")) {
	       document.getElementById("readyEpiForm").submit();
         } else  if (message.data.startsWith("READY DIS")) {
	       document.getElementById("readyDisForm").submit();
         }
   };
});
   
   

Chat.initialize = function() {
   var proto= (window.location.protocol == 'http:') ? "ws" : "wss";
   var path = window.location.pathname;
   var words = path.split("/");
   var app = words[1];
   var url = proto + '://' + window.location.host + '/' + app + '/websocket/watchPlayer';
//  window.alert('Will use URL ' + url);
   Chat.connect(url);
};


var Console = {};

//-- Restores the chat text after the page was reloaded
Console.restore = (function() {
    var console = document.getElementById('console');
    if (!console) { return; }
    var old = sessionStorage.getItem("console");
    if (old) {
	console.innerHTML = old;	
	//Console.log(`Console restored after reload, length ${lenOld} : ${lenNew}`);
    } else {
	//Console.log("Console first loaded");
    }
});

		  
//-- Adds a line of text to the console box
Console.log = (function(message) {
   var console = document.getElementById('console');
   if (!console) { return; }
   var p = document.createElement('p');
   p.style.wordWrap = 'break-word';
   p.innerHTML = "At "+(new Date())    +", " + message;
   console.appendChild(p);
   while (console.childNodes.length > 25) {
      console.removeChild(console.firstChild);
   }
    console.scrollTop = console.scrollHeight;
    sessionStorage.setItem("console", console.innerHTML);
});


Chat.sendChatMessage = (function() {
    var chatBox =   document.getElementById('chat');
    var message = chatBox.value;
    if (message != '') {
        Chat.socket.send('CHAT ' + message);
        chatBox.value = '';
        Console.log("Sending: CHAT " + message);
    }
    if (message === 'show') {
	var console = document.getElementById('console');
	if (!console) { return; }

	var old = console.innerHTML;
	Console.log("Will show console, len=" +old.length);
	var div = document.createElement('div');
	div.innerHTML = old;
	div.style.backgroundColor = "yellow";
	console.appendChild(div);

    }
});

Chat.initialize();


document.addEventListener("DOMContentLoaded", function() {
    Console.restore();
}, false);
    
