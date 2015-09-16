'use strict';


var app = angular.module('myApp', ['ngRoute','ngTouch','ngSanitize', 
                                   'myApp.filters',
                                   'myApp.services',
                                   'myApp.directives'
            ]);


app.config(['$routeProvider', function($routeProvider) {
  $routeProvider.when('/admin', {templateUrl: 'partials/partial1.html', controller: 'MyCtrl', view: 'center'});
  $routeProvider.when('/pump', {templateUrl: 'partials/partial2.html', controller: 'MyCtrl', view: 'center'});
  $routeProvider.when('/tank', {templateUrl: 'partials/partial3.html', controller: 'MyCtrl', view: 'center'});
  $routeProvider.when('/monitor', {templateUrl: 'partials/partial4.html', controller: 'MyCtrl', view: 'center'});
  $routeProvider.otherwise({redirectTo: '/view1'});
}]);


app.controller('MyCtrl', (function($scope, $http) {
	
		var socket;
		
		$scope.send = function(message) {
		  if (!window.WebSocket) { return; }
		  if (socket.readyState == WebSocket.OPEN) {
		    socket.send(message);
		  } else {
		    alert("The socket is not open.");
		    open()
		  }
		}
		
		 angular.element(document).ready(function () {
		 
      		if (!window.WebSocket) {
			  window.WebSocket = window.MozWebSocket;
			}
			if (window.WebSocket) {
			  socket = new WebSocket("ws://127.0.0.1:8080/websocket");
			  socket.onmessage = function(event) {
			    var ta = document.getElementById('responseText');
			    ta.value = ta.value + '\n' + event.data
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

