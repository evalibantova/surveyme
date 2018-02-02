function run() {
    $("#bgvid" + video_index).removeClass("active");
    video_index++;
    if (video_index >= videos.length) {
        video_index = 0;
    } 

    vid = document.getElementById("bgvid" + video_index);
    videoPlayer = document.getElementById("ss" + video_index);  
 
    vid.play();
    $("#bgvid" + video_index).addClass("active");
}

function showVideo() {
    vid = document.getElementById("bgvid" + video_index);
    vid.play();
    $("#bgvid" + video_index).addClass("active");
}


function stopVideo() {
	//$("#bgvid" + video_index).removeClass("active");
    vid = document.getElementById("bgvid" + video_index);
    vid.pause();
}

function saveAnswer(value) {
	console.log(value);
    if(typeof app != "undefined"){
	    app.saveAnswer(value);
	    saving = false;
	}
}

function showQuestion(question) {
    saving = false;
	$("#question-text").text(question);
	$(".icon").removeClass("focus");
	$(".content.centered").addClass("shown");
	setTimeout(function() {
		stopVideo();
	}, 1000);
}

function hideQuestion() {
	showVideo();
	$(".content.centered").removeClass("shown");
	setTimeout(function() {
		$(".icon").removeClass("focus");
	}, 1000);
}

var vid,
	videos,
	video_index,
	videoPlayer,
    saving = false;

$( document ).ready(function() {
	
	$("#rate .icon").on("click",function() {
	    if(!saving){
	        saving = true;
            $(".icon").removeClass("focus");
            $(this).addClass("focus");

            var rating = $(this).data("value");
            setTimeout(function() {
                hideQuestion();
            }, 100);
            setTimeout(function(value) {
                saveAnswer(value);
            }, 1000, rating);
        }
	});

    if(typeof app != "undefined"){
        videos = JSON.parse(app.getVideos());
    } else {
        var path ="file://C:/projects/marketingapp/survey-app/";
        videos = [path + "videos/1_video.mp4", path + "videos/2_video.mp4"]
    }

    for (var i = 0; i < videos.length; i++) {
        var el = '<video id="bgvid' + i + '" playsinline>'
                    + '<source id="ss' + i + '" src="' + videos[i] + '" type="video/mp4" poster="http://dummyimage.com/320x240/ffffff/fff" >'
                + '</video>';
        $(".videos").append(el);

        var tmpVid = document.getElementById("bgvid" + i);
        tmpVid.load();
        tmpVid.addEventListener('ended', function() {
            run();
        });

    }

    vid = document.getElementById("bgvid0");
    video_index = 0;
    videoPlayer = document.getElementById("ss0");

    vid.play();
    $("#bgvid0").addClass("active");

     if(typeof app != "undefined"){
        app.loaded();
     }
});