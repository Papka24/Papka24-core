/*
 * Copyright (c) 2017. iDoc LLC
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     (1) Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     (2) Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *
 *     (3)The name of the author may not be used to
 *     endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

var actionWizard = {
    step1:function(){
        history.replaceState({renderType: "wizard", step: 1}, "", "/wizard/1");
        byId("firstContent").innerHTML = "<div><span class='fadeInDown'><span class='title'>Ласкаво просимо в дивовижний безпаперовий світ! :)</span><br><br>" +
            "Щоб підписувати документи в Папка24, Вам знадобиться <br>"+
            "електронно-цифровий підпис (ЕЦП). Це може бути ЕЦП, який<br>"+
            "Ви використовуєте для звітності в податкову або платежів в Інтернет-банку.<br><br>" +
            "У Вас є ключ для електронно-цифрового підпису?<br><br>" +
            "<span class='btn' onclick='CryptoPlugin.connect(actionWizard.step3(),actionWizard.step2())'>Так</span>" +
            "<span class='btn' onclick='actionWizard.step5()'>Ні</span>" +
            "</span></div>";
    },
    step2:function(){
        history.replaceState({renderType: "wizard", step: 2}, "", "/wizard/2");
        if(localStorage.getItem("CryptoPluginWizard")=="true"){
            cpGUI.signHash("", function () {
                localStorage.removeItem("CryptoPluginWizard");
                actionWizard.step3()
            },function(){
                localStorage.removeItem("CryptoPluginWizard");
            });
        }
        byId("firstContent").innerHTML = "<div><span class='fadeInDown'><span class='title'>Добре! Залишилося трохи :)</span><br><br>" +
            "На наступному кроці Вам потрібно буде встановити спеціальну програму <br> (плагін для браузера) і вибрати ключ ЕЦП на Вашому комп'ютері.<br><br>" +
            "<span class='btn' onclick='actionWizard.step1();'>Назад</span>" +
            "<span class='btn' onclick='localStorage.setItem(\"CryptoPluginWizard\",\"true\");cpGUI.signHash(\"\",function(){localStorage.removeItem(\"CryptoPluginWizard\");actionWizard.step3()})'>Далі</span>" +
            "<span class='btn' onclick='actionWizard.step4()'>Зробити це пізніше</span>" +
            "</span></div>";
    },
    step3:function(){
        history.replaceState({renderType: "wizard", step: 3}, "", "/wizard/3");
        byId("firstContent").innerHTML = "<div><span class='fadeInDown'><span class='title'>Все готово! Тепер Ви можете підписувати документи в Папка24 в декілька кліків.</span><br><br>" +
            "<span class='btn' onclick='actionWizard.step1();'>Назад</span>" +
            "<span class='btn' onclick='actionWizard.disableWizard();actionWizard.goToMainPage();actionCore.loadDocuments();'>Перейти до Папка24</span>" +
            "</span></div>";
    },
    step4:function(){
        history.replaceState({renderType: "wizard", step: 4}, "", "/wizard/4");
        byId("firstContent").innerHTML = "<div><span class='fadeInDown'><span class='title'>Ви зможете встановити програму та зазначити<br> ключ ЕЦП пізніше, під час підписання документа.</span><br><br>" +
            "<span class='btn' onclick='actionWizard.step2();'>Назад</span>" +
            "<span class='btn' onclick='actionWizard.disableWizard();actionWizard.goToMainPage();actionCore.loadDocuments();'>Перейти до Папка24</span>" +
            "</span></div>";
    },
    step5:function(){
        history.replaceState({renderType: "wizard", step: 5}, "", "/wizard/5");
        byId("firstContent").innerHTML = "<div><span class='fadeInDown'><span class='title'>Якщо у Вас немає ЕЦП, не біда!</span><br><br>" +
            "Ви можете отримати його просто зараз <b>безкоштовно</b> через ПриватБанк<br><br>"+
            "ЕЦП, який Ви зараз отримуєте, має таку ж юридичну силу як власноручний підпис на паперових документах.<br><br>"+
            "Ваша компанія або ПП – клієнт ПриватБанку?<br><br>"+
            "<span class='btn' onclick='actionWizard.step1();'>Назад</span>" +
            "<span class='btn' onclick='actionWizard.step7();'>Так</span>" +
            "<span class='btn' onclick='actionWizard.step9();'>Ні</span>" +
            "</span></div>";
    },
    step7:function(){
        history.replaceState({renderType: "wizard", step: 7}, "", "/wizard/7");
        byId("firstContent").innerHTML = "<div><span class='fadeInDown'><span class='title'>Отримайте ключ прямо зараз у кілька кліків!</span><br><br>" +
            "Для цього Вам знадобиться логін та пароль Приват24 для бізнесу."+
            "<br><br>"+
            "<span class='btn' onclick='actionWizard.step5();'>Назад</span>" +
            "<a class='btn' href='https://client-bank.privatbank.ua/p24/cert_gen?callBackUrl=https://papka24.com.ua/wizard/1' onclick='actionWizard.step1();'>Отримати ключ ЕЦП</a>"+
            "<span class='btn' onclick='actionWizard.step4()'>Зробити це пізніше</span>" +
            "</span></div>";
    },
    step9:function(){
        history.replaceState({renderType: "wizard", step: 9}, "", "/wizard/9");
        byId("firstContent").innerHTML = "<div><span class='fadeInDown'><span class='title'>Вам достатньо оформити картку ПриватБанку.</span><br><br>" +
            "Безкоштовно в будь-якому відділенні за 15 хвилин або замовити через кур'єра.<br><br>"+
            "Відкрийте наступну сторінку, залиште свій номер телефону, щоб оформити карту, а потім заходьте знову в Папка24, щоб отримати свій ключ ЕЦП і почати користуватися сервісом. <br> <br>"+
            "<span class='btn' onclick='actionWizard.step5();'>Назад</span>" +
            "<a class='btn' href='https://pb.ua/karta' target='_blank'>Замовити картку ПриватБанку</a>"+
            "</span></div>";
    },
    disableWizard: function(){
        ajax("/api/login/wizard", function () {
            // deactivate wizard for this user.
        }, "", function(){

        }, "DELETE");
    },
    goToMainPage : function(){
        history.pushState({renderType: 'list', docList: 'docs', offset : 0, tagFilter : null}, '', '/list/docs');
    }

};