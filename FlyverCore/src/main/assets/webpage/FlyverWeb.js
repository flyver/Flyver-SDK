function FlyverWeb() {
  var hostname = window.location.hostname;
  // var scrollbox = document.getElementById('scrollbox');

  var dataset = new vis.DataSet();
  var groups = new vis.DataSet();
  groups.add({id:0, content: 'pitch' });
  groups.add({id:1, content: 'roll' });
  groups.add({id:2, content: 'yaw'});
  var container = document.getElementById('chart');
  var options = {
    start: vis.moment().add(-30, 'seconds'),
    end: vis.moment(),
    legend: {right: {position: 'top-left'}},
    dataAxis: {
      customRange: {
        left: {
          min:-4, max: 4
        }
      }
    },
    drawPoints: false,
    shaded: {
      orientation: 'bottom'
    }
  };
  var graph2d = new vis.Graph2d(container, dataset, groups, options);

  function renderStep() {
    var now = vis.moment();
    var range = graph2d.getWindow();
    var interval = range.end - range.start;
    graph2d.setWindow(now - interval, now, {animate: false});
    // if (now > range.end) {
    //   graph2d.setWindow(now - 0.1 * interval, now + 0.9 * interval);
    // }
  }

  function y(x) {
    return (Math.sin(x / 2) + Math.cos(x / 4)) * 5;
  }

  function addDataPoint(fst, snd, trd) {
    if(isNaN(fst) && isNaN(snd) && isNaN(trd)) {
      return;
    }
    var now = vis.moment();
    dataset.add([
      {x: now,y: fst, group: 0},
      {x: now, y: snd, group: 1},
      {x: now, y: trd, group: 2},
    ]);
    var range = graph2d.getWindow();
    var interval = range.end - range.start;
    var oldIds = dataset.getIds( {
      filter: function(item) {
        return item.x < range.start - interval
      }
    });
    dataset.remove(oldIds);
  }

  var requestData = function() {
    var xhttpRequest = new XMLHttpRequest();
    xhttpRequest.onreadystatechange = function() {
      if(xhttpRequest.readyState == 4) {
        var response = xhttpRequest.responseText;
        var arrays = response.split(']');
        var splitted = [];
        // var html = "";
        for(var a = 0; a < arrays.length; a++) {
          var b = arrays[a];
          b = b.replace(/\[|\]|\"|,/g, "");
          // html+= ("<p>" + b + "</p>");
          var tmp = b.split(" ");
          splitted.push(tmp);
        }
        var medianPitch = 0;
        var medianRoll = 0;
        var medianYaw = 0;
        for(var a = 0; a < splitted.length - 2; a++) {
          medianPitch += parseFloat(splitted[a][0]);
          medianRoll += parseFloat(splitted[a][1]);
          medianYaw += parseFloat(splitted[a][2]);
        }
        addDataPoint((medianPitch / (splitted.length - 2)), (medianRoll / (splitted.length - 2)), (medianYaw / (splitted.length - 2)));
        renderStep();
        // scrollbox.innerHTML = html;
        console.log(response);
      }
    }
    xhttpRequest.open('POST', hostname, true);
    xhttpRequest.send('sensors');
  }
  var requestInterval = setInterval(requestData, 50);
}
