<html>
<body>Доброго дня.
<br><br>
<#if singleDocument>
З'явилися нові повідомлення в обговореннях документа
<#else>
З'явилися нові повідомлення в обговореннях документів:
</#if>
<br><br>
<#list resourcesInfo as key, value>
  - <a href='https://${mainDomain}/doc/${value?c}'>${key}</a><br>
</#list>
<br>
<br>
<a style='color:#999' href='https://${mainDomain}/api/login/email/${unsubscribeId}'>Відписатись від листів</a>
</body></html>