'use strict';


var app = angular.module('myApp', ['ngRoute','ngTouch','ngSanitize', 
                                   'myApp.filters',
                                   'myApp.services',
                                   'myApp.directives'
            ]);


app.config(['$routeProvider', function($routeProvider) {
  $routeProvider.when('/view1', {templateUrl: 'partials/partial1.html', controller: 'MyCtrl', view: 'center'});
  $routeProvider.otherwise({redirectTo: '/view1'});
}]);

var instances =  1;// 24;

//example code
var ab = new ArrayBuffer(256); // 256-byte ArrayBuffer.
var faFull = new Uint8Array(ab);
var faFirstHalf = new Uint8Array(ab, 0, 128);
var faThirdQuarter = new Uint8Array(ab, 128, 64);
var faRest = new Uint8Array(ab, 192);



function str2ab(str) {
  var buf = new ArrayBuffer(str.length*2); // 2 bytes for each char
  var bufView = new Uint16Array(buf);
  for (var i=0, strLen=str.length; i<strLen; i++) {
    bufView[i] = str.charCodeAt(i);
  }
  return buf;
}

function ab2str(buf) {
    return String.fromCharCode.apply(null, new Uint16Array(buf));
}

function bigMessage(text, size) {
	var i = size;
	var result = text;
	while (--i>0) {
		result = result + text;
	}
	return result;
}


app.controller('MyCtrl', (function($scope, $http) {
	
		var socket;
		var speedTestStart;
		var speedTestLoopCount;
		var messageSize;
		
		$scope.send = function(message) {
		  		  
		  if (!window.WebSocket) { return; }
		  if (socket.readyState == WebSocket.OPEN) {
		    socket.send(str2ab(message));
		  } else {
		    alert("The socket is not open.");
		    open()
		  }
		}
		
		$scope.speedTest = function(message) {
    
		  var count = 1200;		
		  message = "@@TEST@@"+bigMessage(message,count);
		  messageSize = message.length; 		
		
		  speedTestStart = new Date().getTime();
		  speedTestLoopCount = 0;
		  
		  var ta = document.getElementById('responseText');  
		  ta.value = ta.value + '\n' + 'starting speed test with message size:'+messageSize;
		  		  
          if (!window.WebSocket) { return; }
		  if (socket.readyState == WebSocket.OPEN) {
		  	var i = instances;//kick off this many simultaneous in flight messages
		  	while (--i>=0) {
		    	socket.send(message+i);
		    }
		  } else {
		    alert("The socket is not open.");
		    open()
		  }
		}
		
		//NOTE: Firefox WebSocket implementation is much much faster than Chrome.
		
		 angular.element(document).ready(function () {
      		 if (!window.WebSocket) {
			  window.WebSocket = window.MozWebSocket;
			}
			if (window.WebSocket) {
			   socket = new WebSocket("ws://"+location.host+"/websocket");
			  socket.binaryType = 'arraybuffer';
			  socket.onmessage = function(event) {
			    	
			    			   
	            var view = new Uint8Array(event.data);
			    	
			    if (view[0]==64 && view[1]==64) { //starts with @@ so its the speed test
			    
			       speedTestLoopCount = speedTestLoopCount + 1;	
			       
			       if (speedTestLoopCount % 10000 == 0) {
			       			            		            
             		    var ta = document.getElementById('responseText');
			       		var dif = new Date().getTime() - speedTestStart;
			       		var perSecond = (1000*speedTestLoopCount)/dif;
			       		
			       		var mBitsPerSecond = (messageSize*perSecond*8)/(1024*1024);
			       		
			       		ta.value = ta.value + '\n' + speedTestLoopCount+" roundTrips/sec "+perSecond+" mbps "+mBitsPerSecond;
			       		
			       }
			       socket.send(event.data);
			    
			    } else {
			        var ta = document.getElementById('responseText');
			    	ta.value = ta.value + '\n' + ab2str(event.data);
			    }
			    
			  };
			  socket.onopen = function(event) {
			    var ta = document.getElementById('responseText');
			    ta.value = "Web Socket opened!";
			  };
			  socket.onclose = function(event) {
			    var ta = document.getElementById('responseText');
			    ta.value = ta.value + "Web Socket closed"; 
			  };
			} else {
			  alert("Your browser does not support Web Socket.");
			}
  		  });
      
  }));

