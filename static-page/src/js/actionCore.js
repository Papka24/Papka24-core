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

actionCore = {
    clickOnCanvas: function (e) {
        if (!hasClass(byClass(byId("userBox"), "userBoxMenu")[0], "hide")
            && !cursorOnBlock(byClass(byId("userBox"), "userBoxMenu")[0], e.clientX, e.clientY)
            && !cursorOnBlock(byId("userBox"), e.clientX, e.clientY)) {
            addClass(byClass(byId("userBox"), "userBoxMenu")[0], "hide");
        }

        if (!hasClass(byClass(byId("header"), "companyBoxMenu")[0], "hide")
            && !cursorOnBlock(byClass(byId("header"), "companyBoxMenu")[0], e.clientX, e.clientY)
            && !cursorOnBlock(byId("companyName"), e.clientX, e.clientY)) {
            addClass(byClass(byId("header"), "companyBoxMenu")[0], "hide");
        }

        var menu = byId("openDocMenu");
        if (menu) {
            if (!hasClass(byClass(menu, "docShareMenu")[0], "hide") && e.target.parentNode != menu
                && e.target.parentNode.parentNode
                && e.target.parentNode.parentNode != menu
                && e.target.parentNode.parentNode.parentNode
                && e.target.parentNode.parentNode.parentNode != menu
                && e.target.parentNode.parentNode.parentNode.parentNode
                && e.target.parentNode.parentNode.parentNode.parentNode != menu) {
                addClass(byClass(menu, "docShareMenu")[0], "hide");
            }

            if(byClass(menu, "docName").length > 0) {
                var tagBtn = menu.querySelector(".docName button.pure-button");
                var tabList = byId("tagListSetter");
                if (tagBtn && tabList && !hasClass(tabList, "hide") && e.target.tagName != "BUTTON"
                    && e.target.parentNode != tabList
                    && e.target.parentNode.parentNode
                    && e.target.parentNode.parentNode != tabList
                    && e.target != tagBtn
                    && e.target.parentNode != tagBtn) {
                    addClass(byId("tagListSetter"), "hide");
                }
            }
        }
    },

    resizeContext: function () {
        if(!byId("header") || !byId("contentBlock")){
            return;
        }
        var h = (window.innerHeight | document.documentElement.clientHeight) - (byId("header").offsetHeight) - 5;
        if (h < 200) {
            h = 200;
        }
        byId("contentBlock").style.height = (h - 5) + "px"; /*fixme должна быть нормальная вёрстка*/

        if (byId("openDocInfo") != null) {
            var frame = byTag(byId("contentBlock"), "IFRAME")[0];
            var input = byTag(byId("openDocMenu"), "INPUT")[0];
            var mh = byId("menu").offsetHeight;

            if(mh<500) {
                mh=500;
            }
            var height = mh - 150 + "px";
            var tempWidth = byId("contentBlock").offsetWidth - 400;
            tempWidth = tempWidth < 0 ? 'calc(100% - 400px)' : tempWidth + 'px';
            frame.style.width = tempWidth;
            frame.style.height = height;
            if (userConfig.company.login == userConfig.login) {
                input.style.width = byId("contentBlock").offsetWidth - 478 - 56 - 100 + "px";
            } else {
                input.style.width = byId("contentBlock").offsetWidth - 40 - 56 - 100 + "px";
            }
            byId("openDocInfo").style.height = height;
        }
    },

    loginAction: function (otp) {
        if (byTag(byId("loginForm"), "BUTTON")[0].innerHTML[0] == '<') {
            return;
        }
        var buttonText = byTag(byId("loginForm"), "BUTTON")[0].innerHTML;
        var login = byTag(byId("loginForm"), "INPUT")[0].value;
        var password = byTag(byId("loginForm"), "INPUT")[1].value;
        if (typeof otp == "undefined"){
            otp = 0;
        }
        //byTag(byId("loginForm"), "INPUT")[1].value;
        byTag(byId("loginForm"), "BUTTON")[0].innerHTML = "<img width='60' alt='' src='/img/three-dots.svg'>";
        foreach(byClass(byId("loginForm"), "error"), function (el) {
            addClass(el, "hide")
        });
        if (login.length < 3) {
            rmClass(byClass(byId("loginForm"), "error")[0], "hide");
            byTag(byId("loginForm"), "BUTTON")[0].innerHTML = buttonText;
        } else {
            ajax("/api/login", function (result) {
                // Success
                byTag(byId("loginForm"), "BUTTON")[0].innerHTML = buttonText;
                byTag(byId("loginForm"), "INPUT")[1].value = "";
                actionCore.startToWork(result);
            }, {login: login, password: password, otp:otp}, function (code) {
                // Error
                byTag(byId("loginForm"), "BUTTON")[0].innerHTML = buttonText;
                switch (code) {
                    case 0:
                        if(window.localStorage){
                            window.localStorage.clear();
                        }
                        rmClass(byClass(byId("loginForm"), "error")[2], "hide");
                        byClass(byId("loginForm"), "error")[2].innerHTML = "Сервер не доступний<i class='fa fa-exclamation-triangle fa-lg'></i>";
                        break;
                    case 401:
                        rmClass(byClass(byId("loginForm"), "error")[1], "hide");
                        if(window.localStorage){
                            window.localStorage.clear();    
                        }
                        break;
                    case 404:
                        rmClass(byClass(byId("loginForm"), "error")[0], "hide");
                        if(window.localStorage){
                            window.localStorage.clear();
                        }
                        break;
                    case 423:
                        drawMessage("<br>Введіть OTP<br><input id='otpLoginInput' type='text' maxlength='6' onkeyup='actionCore.checkOTP()'><br><br>",250, true);
                        setTimeout(function(){
                            byId("otpLoginInput").focus();
                        },0);
                        break;
                    case 429:
                        rmClass(byClass(byId("loginForm"), "error")[2], "hide");
                        byClass(byId("loginForm"), "error")[2].innerHTML = "Забагато помилок, обліковий запис заблоковано на 5 хвилин";
                        break;
                    default:
                        if(window.localStorage){
                            window.localStorage.clear();
                        }
                        rmClass(byClass(byId("loginForm"), "error")[2], "hide");
                        byClass(byId("loginForm"), "error")[2].innerHTML = "Сервер не доступний " + code + " <i class='fa fa-exclamation-triangle fa-lg'></i>";
                }
            })
        }
    },

    checkOTP: function(){
        if (byId("otpLoginInput").value.length == 6){
            var otp = parseInt(byId("otpLoginInput").value);
            byId("messageBG").click();
            actionCore.loginAction(otp);
        }
    },

    startToWork: function (loginData) {
        userConfig.docRender = "table";
        userConfig.docSorter = "date";
        userConfig.docList = "docs";
        userConfig.docs = [];
        userConfig.tagFilter = null;
        userConfig.avatarLoadMode = false;
        userConfig.friends = {};
        userConfig.friendsCompany = [];
        userConfig.docId = null;
        if(byId("docTagFilterList")){
            byId("docTagFilterList").innerHTML="";
        }
        if(byId('docAuthorFilter')){
            byTag(byId('docAuthorFilter'),'INPUT')[0].checked =true;
        }
        if(byId('docFilterFrom')){
            byId('docFilterFrom').value='';
        }
        if(byId('docFilterTo')){
            byId('docFilterTo').value='';
        }
        var trash = document.querySelectorAll('.js-active'); /*no need for js-active trash when starting to work - they appear in process of work*/
        for(var temp = 0, length = trash.length; temp < length; temp--){
            rmClass(trash[temp], 'js-active');
        }
        var loginInfo = JSON.parse(loginData);
        localStorage.setItem("sessionId",loginInfo.sessionId);
        if (loginInfo.friends){
            userConfig.friends = loginInfo.friends;
        }

        if (loginInfo.hasOwnProperty("newPartner") && loginInfo.newPartner && loginInfo.newPartner > 0){
            addClass(byId("friendsBox"),"newPartner");
            byTag(byId("friendsBox"),"SPAN")[0].innerHTML=parseInt(loginInfo.newPartner);
        }
        if (loginInfo.friendsCompany){
            userConfig.friendsCompany = loginInfo.friendsCompany;
        }
        localStorage.setItem("papka24login",loginInfo.login);
        // Проверка есть ли детали о ключе пользователя
        userConfig.login = loginInfo.login;
        userConfig.enableOTP = loginInfo.enableOTP;
        if (loginInfo.fullName) {
            userConfig.fullName = loginInfo.fullName;
        }
        userConfig.company = loginInfo.company;
        if(userConfig.company) {
            userConfig.company.login = userConfig.login;
            userConfig.company.iamboss = false;
            /** @namespace userConfig.company.name */
            /** @namespace userConfig.company.companyId */
            /** @namespace userConfig.company.employee */
            foreach(userConfig.company.employee, function (e) {
                /** @namespace {Number} e.role */
                /** @namespace e.login */
                /** @namespace e.status */
                if (e.login == userConfig.login && e.role == 0 && !e.stoptDate) {
                    userConfig.company.iamboss = true;
                    return false;
                }
            });
        } else {
            userConfig.company = {login:loginInfo.login};
        }
        var i = byClass(byId("userBox"), "userImage")[0];
        i.src = userConfig.cdnPath+"avatars/" +  Sha256.hash(userConfig.login)+".png";
        i.onerror = function(){this.onerror=null;this.src="https://secure.gravatar.com/avatar/"+MD5(userConfig.login)+"?d=mm";};
        i = byTag(byClass(byId("userBox"), "userImageBig")[0], "IMG")[0];
        i.src = userConfig.cdnPath+"avatars/" +  Sha256.hash(userConfig.login)+".png";
        i.onerror = function(){this.onerror=null;this.src="https://secure.gravatar.com/avatar/"+MD5(userConfig.login)+"?s=128&d=mm";};
        addEvent(i,"click",function(){
            userConfig.avatarLoadMode=true;
            byId("selectFile").click();
        });
        byId("userName").innerHTML = loginInfo.fullName;
        if (userConfig.company.companyId){
            byTag(byId("companyName"),"SPAN")[0].innerHTML = ((userConfig.company.name&& userConfig.company.name.length>0)?userConfig.company.name:"Група без назви");
            rmClass(byTag(byId("companyName"),"I")[0],"hide");
        } else {
            byTag(byId("companyName"),"SPAN")[0].innerHTML = "Створити групу";
            addClass(byTag(byId("companyName"),"I")[0],"hide");
        }
        
        userConfig.tagList = [];
        if (loginInfo.description && loginInfo.description.length > 0) {
            // Есть настройки пользователя
            var loginDescription = JSON.parse(loginInfo.description);

            /** @namespace loginDescription.showWizard */
            if (loginDescription.showWizard){
                addClass(byId("startPage"), "hide");
                rmClass(byId("firstPage"), "hide");
                actionWizard.step1();
                return;
            }
            userConfig.tagList = [];
            if(typeof loginDescription.tagList !="undefined" && loginDescription.tagList != null){
                var tags = JSON.parse(loginDescription.tagList);
                var tagIds = [];
                foreach(tags, function (t) {
                    if (tagIds.indexOf(t.id) == -1) {
                        tagIds.push(t.id);
                        userConfig.tagList.push(t);
                    }
                });
                // Fix bugs with tags list
                if (userConfig.tagList.length!=tags.length){
                    ajax("/api/login/tags/0",function(){},JSON.stringify(userConfig.tagList),function(){},"PUT");
                }
            }
        } else {
            // Нет настроек пользователя
            addClass(byId("startPage"), "hide");
            rmClass(byId("firstPage"), "hide");
            actionWizard.step1();
            return;
        }
        var keyArr = {
            '90' : false, //z
            '88' : false, //x
            '89' : false //y

        };
        addEvent(window.document, 'keydown', function(e){
            if(keyArr.hasOwnProperty(''+e.keyCode)){
                keyArr[e.keyCode] = true;
            }
            if(keyArr['90'] && keyArr['88'] && keyArr['89']){
                actionCore.cleanup();
            }
        });
        addEvent(window.document, 'keyup', function(e){
            if(keyArr.hasOwnProperty(''+e.keyCode)){
                keyArr[e.keyCode] = false;
            }
        });

        if(window.location.pathname.indexOf('trash') != -1){
            var tempArr = document.querySelectorAll('#mainMenu > li');
            for(var i = 0, length = tempArr.length; i < length; i++){
                rmClass(tempArr[i], 'active');
            }
            addClass(tempArr[tempArr.length - 1], 'active');
            userConfig.docList = 'trash';
            actionCore.loadDocuments();
        }else if (window.location.pathname.indexOf("/wizard/")==0){
            var step = parseInt(window.location.pathname.substr(window.location.pathname.indexOf("/wizard/")+8));
            if (step > 0 && step< 10){
                actionWizard["step"+step]();
            }
        } else if (window.location.pathname.indexOf("/enablecloud/")==0){
            ajax("/api/login/cloud/on", function () {
                window.location = "/";
            })
        }else if(window.location.pathname.indexOf('cleanup') != -1){
            actionCore.cleanup();
        }else{
            // Load all documents
            actionCore.loadDocuments();
        }
    },

    loadDocuments:function(){
        /*WEBSOCKETS actionDoc.loadDocuments();*/
        window.documentsTrashCollection = window.documentsTrashCollection ? window.documentsTrashCollection.initialize() : new DocumentsTrashCollection();
        window.documentsCollection = window.documentsCollection ? window.documentsCollection.initialize() : new DocumentsCollection();
        
        rmClass(byId("canvas"), "loginMode");
        addClass(byClass(byId("userBox"), "userBoxMenu")[0], "hide");
        addClass(byId("firstPage"), "hide");
    },

    logoutAction: function () {
        byId("contentBlock").innerHTML="";
        addClass(byId("canvas"), "loginMode");
        addClass(byId("firstPage"), "hide");
        rmClass(byId("startPage"), "hide");
        addClass(byId("loginForm"), "hide");
        history.replaceState({renderMode:"login"},"","/");
        if (typeof grecaptcha == "undefined"){
            loadJs("https://www.google.com/recaptcha/api.js?render=explicit&hl=uk");
        }
        ajax("/api/login", function () {
            localStorage.removeItem("sessionId");
            userConfig.docs=[];
        }, "", function () {
            // session is deleted
        }, "DELETE");
    },

    clickUserMenu: function (event) {
        //stopBubble(event);
        if (hasClass(byClass(byId("userBox"), "userBoxMenu")[0], "hide")) {
            rmClass(byClass(byId("userBox"), "userBoxMenu")[0], "hide");
        } else {
            addClass(byClass(byId("userBox"), "userBoxMenu")[0], "hide");
        }
        return false;
    },


    showConfig: function(){
        drawMessage({url:"/info/menu-4.html"}, 355, true, 380, m.init);
    },

    createCompany: function(name){
        if (byId("messageBG")){
            byId("messageBG").click();
        }
        // hash from text "createCompany"
        ajax("/api/company/create", function (result) {
            userConfig.company = JSON.parse(result);
            userConfig.company.iamboss=true;
            userConfig.company.login = userConfig.login;
            byTag(byId("companyName"),"SPAN")[0].innerHTML = userConfig.company.name;
            rmClass(byTag(byId("companyName"),"I")[0],"hide");
            actionCore.drawCompanyManager();
        }, name, function (e) {
            console.log("ERROR", e)
        }, "POST");

    },
    
    startCompanyReg: function(){
        if (byId("messageBG")){
            byId("messageBG").click();
        }
        // hash from text "createCompany"
        cpGUI.signHash("w85F+R4rQoMTkHsIWmvrsIClW9jr7DZxCZDgnOxEag8=",function(result){
            ajax("/api/company/create",function (result) {
                userConfig.company = JSON.parse(result);
                byId("userName").innerHTML = "<span>"+userConfig.fullName+"</span><br>"+"<a onclick='actionCore.drawCompanyManager()' class='loginLink'>"+userConfig.company.name+" <i class='fa fa-caret-down'></i></a>";
                actionCore.drawCompanyManager();
            }, result[0].sign,function(e){
                console.log("ERROR",e)
            },"POST");
        },function (){});

    },

    drawCompanyMenu: function(){
        if (userConfig.company.companyId == null){
            actionLogin.drawCompanyCreation();
        } else {
            var links = byClass(byClass(byId("header"),"companyBoxMenu")[0],"loginLink");
            if (userConfig.company.iamboss){
                rmClass(links[0],"hide");
                if(userConfig.company.login == userConfig.login){
                    rmClass(links[1],"hide");
                    rmClass(links[2],"hide");
                    addClass(links[3],"hide");
                } else if(userConfig.company.login == "all"){
                    addClass(links[1],"hide");
                    rmClass(links[2],"hide");
                    rmClass(links[3],"hide");
                } else {
                    rmClass(links[1],"hide");
                    addClass(links[2],"hide");
                    rmClass(links[3],"hide");
                }
                addClass(links[4],"hide");
                rmClass(byTag(byClass(byId("header"),"companyBoxMenu")[0],"span")[0],"hide");
            } else {
                addClass(byTag(byClass(byId("header"),"companyBoxMenu")[0],"span")[0],"hide");
                addClass(links[0],"hide");
                addClass(links[1],"hide");
                addClass(links[2],"hide");
                addClass(links[3],"hide");
                rmClass(links[4],"hide");
            }
        }

        if (userConfig.company.companyId && hasClass(byClass(byId("header"),"companyBoxMenu")[0],"hide")){
            rmClass(byClass(byId("header"),"companyBoxMenu")[0],"hide");
        } else {
            addClass(byClass(byId("header"),"companyBoxMenu")[0],"hide");
        }
    },
    cleanup : function(){
        var lastTimeCleanup = localStorage.getItem('lastTimeCleanup');
        if(lastTimeCleanup > (new Date().getTime() - 60000)){
            return;
        }
        var temp = window.documentsCollection || {},
            temp1 = JSON.parse(JSON.stringify(window.userConfig)),
            temp2 = (window.documentsCollection || {}).wsLog,
            supaTemp;
        delete temp1['imgEnd'];
        delete temp1['pdfReaderPath'];
        delete temp1['getUserPreference'];
        delete temp1['setUserPreference'];
        delete temp1['friendsCompany'];
        delete temp1['friends'];
        delete temp1['cache'];
        supaTemp = [];
        /*for(var i = 0, length = temp1['docs'].length; i < length; i++){
            supaTemp.push(
                {
                    id: temp1['docs'][i].id,
                    status: temp1['docs'][i].status,
                    tags: temp1['docs'][i].tags,
                    signed: temp1['docs'][i].signed,
                    shares: temp1['docs'][i].shares
                }
            )
        }
        temp1['docs'] = supaTemp;*/
        temp = {
            fullCollection : temp.fullCollection,
            length : temp.length,
            limit : temp.limit,
            offset : temp.offset,
            renderCount : temp.renderCount,
            timestamp : temp.timestamp,
            wsClosed : temp.ws.closed,
            waitingForWs : !!temp.failTimer.length
        };
        localStorage.setItem('lastTimeCleanup', (new Date()).getTime());
        ajax("/api/report/cleanup", function () {
            var sessionId = localStorage.getItem('sessionId');
            localStorage.clear();
            localStorage.setItem('sessionId', sessionId);
            window.location.href = '/';
        }, JSON.stringify([/*temp1, */temp, temp2]) + ", userAgent: '" + navigator.userAgent + "', platform:'" + navigator.platform + "', url: '" + window.location.href + "', version: '" + version + "'", function () {
        });
    },
    drawCompanyManager: function(){
        drawMessage({url:"/info/cm-3.html"}, 750, true, 500, cm.init);
    },

    showContacts:function(){
        drawMessage({url:"/info/c.html"}, 750, true, 500, c.init);
    },

    findContacts:function(){
        drawMessage({url:"/info/c-load.html"}, 750, true, 500, c.drawDetector);
    }
};
