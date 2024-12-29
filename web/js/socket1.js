"use strict";


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
		//pid = getQueryVariable("watch");
		//if (pid)	{	                    Chat.socket.send('WATCH '  + pid);            }

   };

   Chat.socket.onclose = function () {
                document.getElementById('chat').onkeydown = null;
//                Console.log('Info: WebSocket closed at '+(new Date()));
   };

   Chat.socket.onmessage = function (message) {
      if(true) { // typeOf message.data === String ){
//         window.alert("Received text message: " + message.data);
//       Console.log(message.data);
         if (message.data.startsWith("READY EPI")) {
	       document.getElementById("readyEpiForm").submit();
         } else  if (message.data.startsWith("READY DIS")) {
	       document.getElementById("readyDisForm").submit();
         }
      } else {
//         window.alert("Received non-text ("+typeof(message.data)   +") message: " + message.data);
      }
   };
});
   
   

Chat.initialize = function() {
   var proto= (window.location.protocol == 'http:') ? "ws" : "wss";
   var path = window.location.pathname;
   var words = path.split("/");
   var app = words[1];
           var url = proto + '://' + window.location.host + '/' + app + '/websocket/watchPlayer';
  window.alert('Will use URL ' + url);
           Chat.connect(url);
};

Chat.sendMessage = (function() {
            var message = document.getElementById('chat').value;
            if (message != '') {
                Chat.socket.send('WATCH ' + message);
                document.getElementById('chat').value = '';
            }
});

Chat.initialize();

//   document.addEventListener("DOMContentLoaded", function() {
//   }, false);
    
