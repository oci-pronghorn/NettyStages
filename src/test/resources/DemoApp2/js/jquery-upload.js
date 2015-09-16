$(document).ready(function(){
    $('#fileupload').fileupload({
        dataType: 'json'});
});

TestCtrl = function($scope){
    $scope.fileList = [];
    $('#fileupload').bind('fileuploadadd', function(e, data){
        // Add the files to the list
        numFiles = $scope.fileList.length
        for (var i=0; i < data.files.length; ++i) {
            var file = data.files[i];
        // .$apply to update angular when something else makes changes
        $scope.$apply(
            $scope.fileList.push({name: file.name})
            );
        }
        // Begin upload immediately
        data.submit();
    });
    $scope.addButtonClicked = function(){
        var numFiles = $scope.fileList.length;
        $scope.fileList.push({name: ('fileName' + numFiles)});
    }
}
