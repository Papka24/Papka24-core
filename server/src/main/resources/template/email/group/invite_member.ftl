<html>
<body>
<b>${authorName}</b> запросив(-ла) Вас приєднатися у свою групу
<#if groupName?has_content>
"${groupName}"
</#if>.
<br><br>
Якщо Ви приймете це запрошення, всі Ваші документи у Папка24 будуть доступні адміністраторам циєї групи.
<br><br>
<#include "buttons.ftl">
</body>
</html>