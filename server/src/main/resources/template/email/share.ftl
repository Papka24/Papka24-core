<html>
    <body>
        <b>${authorName}</b> завантажив на <a href='https://${mainDomain}/'>Papka24</a> документ
        <b>${docName}</b> і надав Вам до нього доступ.
        <br><br>
        <#if comment?has_content>
            <b>Коментар до документу:</b> <i>${comment}</i><br><br>
        </#if>
        <#if sendInvite>
            Ви ще не зареєстровані в сервісі. Зробіть це прямо зараз за одну хвилину,
            і Ви відразу отримаєте доступ до цього документа.
            <br><br>
            <a style='background:#5c9d21; width:200px; text-align:center; display:block; text-decoration:none; padding:10px; color:white; font-weight:bold'
               href='https://${mainDomain}/register?email=${secret}'>Зареєструватися</a>
            <br>
            З Papka24 Ви легко зможете підписувати і зберігати свої договори, акти та інші документи в електронній,
            а не паперової формі. У цілковитій відповідності з українським законодавством.
        <#else>
            <a style='background:#5c9d21; width:200px; text-align:center; display:block; text-decoration:none; padding:10px; color:white; font-weight:bold'
               href='https://${mainDomain}/doc/${docId?c}'>Відкрити документ</a>
        </#if>
        <br><br>
        <a style='color:#999' href='https://${mainDomain}/api/login/email/${encryptedEmail}'>Відписатись від листів</a>
        <#if sendInvite>
            <br>
            <a style='color:#999' href='https://${mainDomain}/api/login/email/${encryptedId}'>Відписатись від усих типiв листів</a>
        </#if>
    </body>
</html>