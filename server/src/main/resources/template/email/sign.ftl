<html>
<body>Користувач <b>${authorName}</b> підписав документ <b>${docName}</b>
<br><br>
<a style='background:#5c9d21; width:200px; text-align:center; display:block; text-decoration:none; padding:10px; color:white; font-weight:bold'
   href='https://${mainDomain}/doc/${docId?c}'>Відкрити документ</a>
<br>
<br>
<a style='color:#999' href='https://${mainDomain}/api/login/email/${encriptedEmail}'>Відписатись від листів</a>
</body></html>