<html>
<head>
    <title>monit</title>
</head>
<body>
<h3>Main Properties</h3>
<div>
    nodeName=${main["nodeName"]!"N/A"}<br>
</div>
<hr>
<h3>Jdbc Properties</h3>
<div>
    isActive=${jdbcActive?string("yes","no")}<br>
</div>
<hr>
<h3>Email server</h3>
<div>
    enableSpam=${main["enableSpam"]!"N/A"}<br>
    emailQuerySize=${emailQuerySize}<br>
    emailRedisQuery=${emailRedisQuery}<br>
</div>
<hr>
<h3>Redis server</h3>
<div>
    redis.email=${redisEmail}<br>
    isActive=${redisActive?string("yes","no")}<br>
</div>
<hr>
<h3>Scylla Cluster</h3>
<div>
    isActive=${scyllaActive?string("yes","no")}<br>
</div>
<hr>
<h3>Subscribe server</h3>
<div>
    subscribe.service=${subscribeService}<br>
    notifyCompletedTaskCount=${notifyCompletedTaskCount}<br>
    notifyNotifyQueueSize=${notifyNotifyQueue}<br>

</div>
<hr>

</body>
</html>