'use strict';

/* Controllers */


// Controller for the drawing editor page
function DrawingController($scope, $http, $routeParams) {
    google.charts.load('current', {'packages': ['bar']})
    google.charts.setOnLoadCallback(function () {
        console.log("google charts loaded!");
    });

    $scope.drawingCanvas = document.getElementById('drawing');
    $scope.shapeType = "BIG_CIRCLE";
    if (navigator.appVersion.indexOf("Chrome") > 0)
        $scope.shapeColor = "BLUE";
    else
        $scope.shapeColor = "GREEN";
    // open a web socket connection for a given drawing

    $scope.websocket = new WebSocket("ws://" + document.location.host + "/websockets/" + $routeParams.drawingId);
    $scope.websocket.onmessage = function (evt) {
        console.log(evt.data);
        var data = eval("(" + evt.data + ")");
        if (data.sseLocation) {
            $scope.sseLocation = data.sseLocation;
            $scope.drawing = $http.get(document.location.protocol + '//' + data.sseLocation + '/api/drawings/' + $routeParams.drawingId)
                    .then(function (res) {
                        $scope.drawing = res.data;
                    });
        } else if (data.fnPredictLocation)
            $scope.fnPredictLocation = data.fnPredictLocation;
        else
            $scope.drawShape(data);
    };
    $scope.prediction = "no prediction yet";
    $scope.predict = function (evt) {
        var canvas = document.getElementById("drawing");
        var img = canvas.toDataURL().split("base64,")[1];
        var xhr = new XMLHttpRequest();
        xhr.open("POST", $scope.fnPredictLocation, true);
        xhr.onreadystatechange = function () {
            if (this.readyState == XMLHttpRequest.DONE && this.status == 200) {
                var resp_array = xhr.response.replace(/"/g, '').split(" ").map(Number);
                $scope.prediction = resp_array.shift();
                $scope.probabilities = resp_array;
                console.log($scope.probabilities);
                $scope.drawChart();
            }
            ;
        };
        xhr.send(img);
    };



    // clean up
    $scope.$on("$destroy", function (event) {

        // sometimes when this function is called, the websocket is already closed
        if ($scope.websocket.readyState > 0)
            $scope.websocket.close();
    });

    // draws a given shape
    $scope.drawShape = function (shape) {
        var context = $scope.drawingCanvas.getContext('2d');
        var radius = 8;
        //Canvas commands go here
        //context.strokeStyle = "#000000";
        context.fillStyle = shape.color;
        if (shape.type == 'SMALL_CIRCLE') {
            context.beginPath();
            context.arc(shape.x, shape.y, radius, 0, Math.PI * 2, true);
            context.closePath();
            context.fill();
        } else if (shape.type == 'BIG_CIRCLE') {
            context.beginPath();
            context.arc(shape.x, shape.y, 2 * radius, 0, Math.PI * 2, true);
            context.closePath();
            context.fill();
        } else if (shape.type == 'BIG_SQUARE') {
            //context.fillRect(0,1,10,10);
            context.fillRect((shape.x - (2 * radius)), (shape.y - (2 * radius)), (4 * radius), (4 * radius));
            //context.fill();
        } else if (shape.type == 'SMALL_SQUARE') {
            context.fillRect((shape.x - (radius)), (shape.y - (radius)), (2 * radius), (2 * radius));
        }
    }

    // mouseMove event handler
    $scope.mouseMove = function (event) {
        if (event.shiftKey) {
            $scope.mouseDown(event);
        }
    }

    // mouseDown event handler
    $scope.mouseDown = function (e) {
        var totalOffsetX = 0;
        var totalOffsetY = 0;
        var currentElement = $scope.drawingCanvas;

        do {
            totalOffsetX += currentElement.offsetLeft;
            totalOffsetY += currentElement.offsetTop;
        } while (currentElement = currentElement.offsetParent);


        var posx = e.pageX - totalOffsetX;
        var posy = e.pageY - totalOffsetY;

        var msg = '{"x" : ' + posx +
                ', "y" : ' + posy +
                ', "color" : "' + $scope.shapeColor +
                '", "type" : "' + $scope.shapeType + '"}';


        $scope.websocket.send(msg);

    }

    $scope.drawChart = function () {
        var dat = [];
        dat.push(['Digit Probability', '']);
        console.log($scope.probabilities);
        for (var i = 1; i < 10; i++) {
            dat.push([i.toString(), $scope.probabilities[i - 1]]);
        }
        dat.push(['0', $scope.probabilities[9]]);
        var data = google.visualization.arrayToDataTable(dat);
        var formatter = new google.visualization.NumberFormat({
            pattern: '#.#####'
        });
        formatter.format(data, 1);
        var options = {
            chart: {
                title: 'Prediction probabilities',
                subtitle: ' ',
            },
            bars: 'vertical', // Required for Material Bar Charts.
            height: 250,
            legend: {position: 'none'}
        };
        var chart = new google.charts.Bar(document.getElementById('barchart'));
        chart.draw(data, google.charts.Bar.convertOptions(options));

    }

}
