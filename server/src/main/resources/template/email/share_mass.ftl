<html>
<body>
На <a href='https://${mainDomain}/'>Папка24</a> було завантажено документи та надан Вам до них доступ.
<table style='border: 0 solid black; border-collapse: collapse;'>
    <br>
    <tr class='vertical-align:middle !important; height:60px;'>
        <th style='text-align: left; padding: 5px 10px 5px 0; border: 0 solid black;'>Контрагент</th>
        <th style='text-align: left; padding: 5px 10px 5px 0; border: 0 solid black;'>Документ</th>
        <th style='text-align: left; padding: 5px 10px 5px 0; border: 0 solid black;'>Коментар до документу:</th>
    </tr>
<#list emailEvent as ee>
    <tr class='vertical-align:middle !important; height:60px;'>
        <td style='vertical-align:middle; height:40px; text-align: left; border: 0 solid black; padding: 5px 10px 5px 0;'>${ee.authorName}</td>
        <td style='vertical-align:middle; height:40px; text-align: left; border: 0 solid black; padding: 5px 10px 5px 0;'>
            <#if sendInvite>
            ${ee.docName}
            <#else>
                <a style='color:#999' href='https://${mainDomain}/doc/${ee.docId?c}'>${ee.docName}</a>
            </#if>
        </td>
        <td style='vertical-align:middle; height:40px; text-align: left; border: 0 solid black; padding: 5px 10px 5px 0;'>${ee.comment}</td>
    </tr>
</#list>
</table>
<#if sendInvite>
Ви ще не зареєстровані в сервісі. Зробіть це прямо зараз за одну хвилину,
і Ви відразу отримаєте доступ до цього документа.
<br><br>
<a style='background:#5c9d21; width:200px; text-align:center; display:block; text-decoration:none; padding:10px; color:white; font-weight:bold'
   href='https://${mainDomain}/register?email=${secret}'>Зареєструватися</a>
<br>
З Papka24 Ви легко зможете підписувати і зберігати свої договори, акти та інші документи в електронній,
а не паперової формі. У цілковитій відповідності з українським законодавством.
</#if>
<br><br>
<a style='color:#999' href='https://${mainDomain}/api/login/email/${encryptedEmail}'>Відписатись від листів</a>
<#if sendInvite>
<br>
<a style='color:#999' href='https://${mainDomain}/api/login/email/${encryptedId}'>Відписатись від усих типiв листів</a>
</#if>
</body>
</html>