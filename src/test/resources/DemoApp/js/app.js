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

var instances = 70;

app.controller('MyCtrl', (function($scope, $http) {
	
		var socket;
		var speedTestStart;
		var speedTestLoopCount;
		
		$scope.send = function(message) {
		  if (!window.WebSocket) { return; }
		  if (socket.readyState == WebSocket.OPEN) {
		    socket.send(message);
		  } else {
		    alert("The socket is not open.");
		    open()
		  }
		}
		
		$scope.speedTest = function(message) {
		  //growth here is making little difference to speed
		  message = "@@TEST@@dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd" +
		                     "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"+ message;

		  speedTestStart = new Date().getTime();
		  speedTestLoopCount = 0;
		  		  
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
			  socket = new WebSocket("ws://127.0.0.1:8080/websocket");
			  
			  socket.onmessage = function(event) {
			    			    
			    if (event.data.startsWith("@@")) {
			    
			       speedTestLoopCount = speedTestLoopCount + 1;	
			       
			       if (speedTestLoopCount % 10000 == 0) {
			       
             		    var ta = document.getElementById('responseText');
			       		var dif = new Date().getTime() - speedTestStart;
			       		var perSecond = (1000*speedTestLoopCount)/dif;
			       		ta.value = ta.value + '\n' + speedTestLoopCount+" round trips per/sec "+perSecond+" total ms "+dif;
			       		
			       }
			       socket.send(event.data);
			    
			    } else {
			        var ta = document.getElementById('responseText');
			    	ta.value = ta.value + '\n' + event.data
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

