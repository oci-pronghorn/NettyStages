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

app.controller('MyCtrl', (function($scope, $http) {
	

      
  }));

app.filter('objOrder', function () {
        return function(object) {
            var array = [];
            angular.forEach(object, function (value, key) {
                array.push({key: key, value: value});
            });
            return array;
        };
});
