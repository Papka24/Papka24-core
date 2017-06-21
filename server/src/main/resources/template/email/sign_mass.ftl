<html>
<body>
Документи, що було підписано
<br><br>
<table style='border: 0 solid black; border-collapse: collapse;'>
    <br>
    <tr class='vertical-align:middle !important; height:60px;'>
        <th style='text-align: left; padding: 5px 10px 5px 0; border: 0 solid black;'>Контрагент</th>
        <th style='text-align: left; padding: 5px 10px 5px 0; border: 0 solid black;'>Документ</th>
    </tr>
    <#list emailEvent as ee>
    <tr class='vertical-align:middle !important; height:60px;'>
        <td style='vertical-align:middle; height:40px; text-align: left; border: 0 solid black; padding: 5px 10px 5px 0;'>${ee.authorName}</td>
        <td style='vertical-align:middle; height:40px; text-align: left; border: 0 solid black; padding: 5px 10px 5px 0;'>
            <a style='color:#999'
                href='https://${mainDomain}/doc/${ee.docId?c}'>${ee.docName}</a>
        </td>
    </tr>
</#list>
</table>
<br>
<br>
<a style='color:#999' href='https://${mainDomain}/api/login/email/${encriptedEmail}'>Відписатись від листів</a>
</body></html>