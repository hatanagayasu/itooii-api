//    var mediaConstraints = {
//        optional: [],
//        mandatory: {
//            OfferToReceiveAudio: true,
//            OfferToReceiveVideo: true
//        }
//    };
var socket, user_id, video_chat_id, pair_talk;
var peer, offer_config, answer_config, iceServers;
var remote_video = document.getElementById('remote_video');
var local_video = document.getElementById('local_video');

function send(data) {
    json = JSON.stringify(data);
    console.log(json);
    socket.send(json);
}

getUserMedia({
    video: local_video,
    onsuccess: function(stream) {
        offer_config = {
            iceServers: iceServers,
            attachStream: stream,
            onICE : function(candidate) {
                send({
                    action: "video/candidate",
                    video_chat_id: video_chat_id,
                    candidate: candidate
                });
            },
            onRemoteStream: function(stream) {
                remote_video.src = URL.createObjectURL(stream);
                remote_video.play();

                if (pair_talk) {
                    send({
                        action: "video/connected",
                        video_chat_id: video_chat_id
                    });
                }
            },
            onRemoteStreamEnded: function(stream) {
            },
            onOfferSDP: function(sdp) {
                send({
                    action: "video/offer",
                    video_chat_id: video_chat_id,
                    description: sdp
                });
            }
        };

        answer_config = {
            iceServers: iceServers,
            attachStream: stream,
            onICE : function(candidate) {
                send({
                    action: "video/candidate",
                    video_chat_id: video_chat_id,
                    candidate: candidate
                });
            },
            onRemoteStream: function(stream) {
                remote_video.src = URL.createObjectURL(stream);
                remote_video.play();

                if (pair_talk) {
                    send({
                        action: "video/connected",
                        video_chat_id: video_chat_id
                    });
                }
            },
            onRemoteStreamEnded: function(stream) {
            },
            onAnswerSDP: function(sdp) {
                send({
                    action: "video/answer",
                    video_chat_id: video_chat_id,
                    description: sdp
                });
            }
        };

        //open_socket();
    },
    onerror: function() {
        alert('Either you not allowed access to your microphone/webcam or another application already using it.');
    }
});

//    var video_constraints = {
//        mandatory: {},
//        optional: []
//    };
//
//    function getUserMedia(callback) {
//        var n = navigator;
//        n.getMedia = n.webkitGetUserMedia || n.mozGetUserMedia;
//        n.getMedia({
//            audio: true,
//            video: video_constraints
//        }, callback, onerror);
//
//        function onerror(e) {
//            alert(JSON.stringify(e, null, '\t'));
//        }
//    }

$(function() {
    var host = window.location.host;
    $.ajax({
        type: 'get',
        url: 'http://' + host + '/video/ice_servers',
        dataType: 'json',
        timeout: 30000,
        success: function(data, status) {
                console.log(data);
                iceServers = data;
        },
        error: function() {
        }
    });

    $("#login").click(function() {
        var email = $("#email").val(),
            password = $("#password").val();

        $.ajax({
            type: 'post',
            url: 'http://' + host + '/access_token',
            data: {email: email, password: password},
            dataType: 'json',
            timeout: 30000,
            success: function(data, status) {
                if (data.access_token) {
                    console.log(data);
                    $("#access_token").val(data.access_token);
                }
            },
            error: function() {
            }
        });
    });

    $("#online").click(function() {
        var access_token = $("#access_token").val(),
            ws = 'ws://' + host + '/websocket?access_token=' + access_token;
        socket = new WebSocket(ws, "itooii");

        socket.onopen = function() {
            console.log('連線已開啟');
        };

        socket.onmessage = function(event) {
            console.log(event);

            var params = JSON.parse(event.data);

            if (params.action == 'event') {
                $("<p>").text(event.data).prependTo("#messages");
            } else if (params.action == 'video/request') {
                pair_talk = false;
                video_chat_id = params.video_chat_id;
                user_id = params.user_id;
                $("#user_id").val(user_id);

                if (confirm("video/request from " + user_id)) {
                    send({
                        action: "video/response",
                        video_chat_id: video_chat_id,
                        user_id: user_id,
                        confirm: true
                    });
                } else {
                    send({
                        action: "video/response",
                        user_id: user_id,
                        confirm: false
                    });
                }
            } else if (params.action == 'video/response') {
                if (params.confirm) {
                    video_chat_id = params.video_chat_id;
                    peer = RTCPeerConnection(offer_config);
                }
                else {
                    alert("reject");
                }
            } else if (params.action == 'video/pair') {
                pair_talk = true;
                user_id = params.user_id;
                $("#user_id").val(user_id);
                send({
                    action: "video/pair_request",
                    video_chat_id: params.video_chat_id
                });
            } else if (params.action == 'video/pair_request') {
                pair_talk = true;
                video_chat_id = params.video_chat_id;
                user_id = params.user_id;
                $("#user_id").val(user_id);
                send({
                    action: "video/pair_response",
                    video_chat_id: video_chat_id
                });
            } else if (params.action == 'video/pair_response') {
                video_chat_id = params.video_chat_id;
                peer = RTCPeerConnection(offer_config);
            } else if (params.action == 'video/offer') {
                answer_config.offerSDP = params.description;
                peer = RTCPeerConnection(answer_config);
            } else if (params.action == 'video/answer') {
                peer.addAnswerSDP(params.description);
            } else if (params.action == 'video/candidate') {
                peer.addICE(params.candidate);
            } else if (params.action == 'video/leave') {
                peer.peer.close();
                remote_video.src = "";

                alert('video/leave');
            } else if (params.action == 'video/unpair') {
                $("<p>").text(event.data).prependTo("#messages");
            }
        };

        socket.onclose = function(event) {
            console.log('連線已關閉... state :' + socket.readyState);
        };

        socket.onerror = function(event) {
            console.log('發生錯誤: ' + event.data);
        };
    });

    $("#offline").click(function() {
        socket.close();
    });

    $("#request").click(function() {
        pair_talk = false;
        user_id = $("#user_id").val();
        send({
            action: "video/request",
            user_id: user_id
        });
    });

    $("#ready").click(function() {
        send({action: "video/ready"});
    });

    $("#leave").click(function() {
        if (peer)
            peer.peer.close();
        if (remote_video.src)
            remote_video.src = "";
        send({action: "video/leave"});
    });

    $("#submit").click(function() {
        var access_token = $("#access_token").val(),
            text = $("#message").val();

        user_id = $("#user_id").val();

        $.ajax({
            type: 'post',
            url: 'http://' + host + '/messages/' + user_id + "?access_token=" + access_token,
            data: {text: text},
            dataType: 'json',
            timeout: 30000,
            success: function(data, status) {
                console.log(data);
            },
            error: function(){
            }
        });
    });
});
