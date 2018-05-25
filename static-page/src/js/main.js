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

var loaderSnippet = '<div class="gearLoader"><div class="gear one"><svg fill="#5c9d21" viewBox="0 0 100 100" id="blue"><path d="M97.6,55.7V44.3l-13.6-2.9c-0.8-3.3-2.1-6.4-3.9-9.3l7.6-11.7l-8-8L67.9,20c-2.9-1.7-6-3.1-9.3-3.9L55.7,2.4H44.3l-2.9,13.6      c-3.3,0.8-6.4,2.1-9.3,3.9l-11.7-7.6l-8,8L20,32.1c-1.7,2.9-3.1,6-3.9,9.3L2.4,44.3v11.4l13.6,2.9c0.8,3.3,2.1,6.4,3.9,9.3      l-7.6,11.7l8,8L32.1,80c2.9,1.7,6,3.1,9.3,3.9l2.9,13.6h11.4l2.9-13.6c3.3-0.8,6.4-2.1,9.3-3.9l11.7,7.6l8-8L80,67.9      c1.7-2.9,3.1-6,3.9-9.3L97.6,55.7z M50,65.6c-8.7,0-15.6-7-15.6-15.6s7-15.6,15.6-15.6s15.6,7,15.6,15.6S58.7,65.6,50,65.6z"/></svg></div><div class="gear two"><svg fill="#5c9d21" viewBox="0 0 100 100" id="pink"><path d="M97.6,55.7V44.3l-13.6-2.9c-0.8-3.3-2.1-6.4-3.9-9.3l7.6-11.7l-8-8L67.9,20c-2.9-1.7-6-3.1-9.3-3.9L55.7,2.4H44.3l-2.9,13.6      c-3.3,0.8-6.4,2.1-9.3,3.9l-11.7-7.6l-8,8L20,32.1c-1.7,2.9-3.1,6-3.9,9.3L2.4,44.3v11.4l13.6,2.9c0.8,3.3,2.1,6.4,3.9,9.3      l-7.6,11.7l8,8L32.1,80c2.9,1.7,6,3.1,9.3,3.9l2.9,13.6h11.4l2.9-13.6c3.3-0.8,6.4-2.1,9.3-3.9l11.7,7.6l8-8L80,67.9      c1.7-2.9,3.1-6,3.9-9.3L97.6,55.7z M50,65.6c-8.7,0-15.6-7-15.6-15.6s7-15.6,15.6-15.6s15.6,7,15.6,15.6S58.7,65.6,50,65.6z"/></svg></div><div class="gear three"><svg fill="#5c9d21" viewBox="0 0 100 100" id="yellow"><path d="M97.6,55.7V44.3l-13.6-2.9c-0.8-3.3-2.1-6.4-3.9-9.3l7.6-11.7l-8-8L67.9,20c-2.9-1.7-6-3.1-9.3-3.9L55.7,2.4H44.3l-2.9,13.6      c-3.3,0.8-6.4,2.1-9.3,3.9l-11.7-7.6l-8,8L20,32.1c-1.7,2.9-3.1,6-3.9,9.3L2.4,44.3v11.4l13.6,2.9c0.8,3.3,2.1,6.4,3.9,9.3      l-7.6,11.7l8,8L32.1,80c2.9,1.7,6,3.1,9.3,3.9l2.9,13.6h11.4l2.9-13.6c3.3-0.8,6.4-2.1,9.3-3.9l11.7,7.6l8-8L80,67.9      c1.7-2.9,3.1-6,3.9-9.3L97.6,55.7z M50,65.6c-8.7,0-15.6-7-15.6-15.6s7-15.6,15.6-15.6s15.6,7,15.6,15.6S58.7,65.6,50,65.6z"></path></svg></div></div>';

var userConfig = {};

// Onload configuration
addEvent(window, "load", function () {
    setTimeout(function () {
        // Only Chrome & Opera pass the error object.
        if (typeof version == 'undefined' || !version) {
            window.version = 0;
        }
        window.onerror = function (message, file, line, col, error) {
            var e = {message: message, file: file, line: line, col: col, error: error};
            ajax("/api/report/jsError", function () {
            }, JSON.stringify(e) + ", userAgent: '" + navigator.userAgent + "', platform:'" + navigator.platform + "', url: '" + window.location.href + "', version: '" + version + "'", function () {
            });
        };
        // Only Chrome & Opera have an error attribute on the event.
        window.addEventListener("error", function (e) {
            if (e.hasOwnProperty("error") && e.error && JSON.stringify(e.error).length > 2) {
                ajax("/api/report/jsError", function () {
                }, JSON.stringify(e.error) + ", userAgent: '" + navigator.userAgent + "', platform:'" + navigator.platform + "', url: '" + window.location.href + "', version: '" + version + "'", function () {
                });
            }
        });

        // CryptoPlugin.config.debugFunction = function(e, type){
        //     if(type == 'info') {
        //         console.log(e);
        //     } else {
        //         console.warn(e);
        //     }
        // };

        // Fix for old version
        if (localStorage.getItem("CryptoPluginAddTimeStamp")) {
            if (localStorage.getItem("CryptoPluginAddTimeStamp") == "false") {
                localStorage.setItem("CryptoPluginTimeStamp", "systemTime");
            } else {
                localStorage.setItem("CryptoPluginTimeStamp", "normal");
            }
            localStorage.removeItem("CryptoPluginAddTimeStamp");
        }

        userConfig = { //attributes list
            login: "",
            fullName: "",
            company: null,
            domain: window.location.host,
            doc: null,
            docRender: "table",  // blocks, table
            docSorter: "date",   // date, names
            enableChat: false,
            enableWss: true,
            docFilter: {
                dateFrom: null,
                dateTo: null,
                docAuthor: "all",// "all", "my", "me"
                docUser: null,  // null or [email]
                docTags: []
            },
            docList: "docs",     // docs, trash, tag
            pdfReaderPath: "/pdf/14.1/web/viewer.v1.html",
            docs: [],
            cdnPath: "/cdn/",
            imgEnd: "-50.png",
            avatarLoadMode: false,
            browserInfo: BrowserInfo(),
            // List of knows persons
            // {"email1@test.com":"Full name of email1", "email2@test.com":"Full name of email2", ...}
            friends: {},
            // List of knows companies by logins
            // Format: [{"name":"ПриватБанк","inn":84369785,"emails":["email1@gmail.com","email2@gmail.com"]}, {...}]
            friendsCompany: [],
            // Id of open document
            docId: null,
            cache: {signList: []},
            tagList: [],
            enableOTP: false,
            _userPreferences: (function () {// can have LOADS of shit like preferred mode of search or literally anything else. Defaults to {}
                var res = {},
                    raw = localStorage.getItem('userPreferences');
                if (raw) {
                    res = JSON.parse(raw);
                }
                return res;
            })(),
            /*userConfig-specific functions aka MODEL*/
            setUserPreference: function (setting, value) {
                userConfig._userPreferences[setting] = value;
                localStorage.setItem('userPreferences', JSON.stringify(userConfig._userPreferences));
                return userConfig.getUserPreference(setting);
            },
            getUserPreference: function (setting) { /*вложенные структуры?*/
                return userConfig._userPreferences[setting] || false; // false or actual setting
            }
        };
        if(byId("search")){
            byId("search").value = "";
        }
        switch (userConfig.browserInfo.os) {
            case "windows":
            case "linux":
            case "osx":
                var browserVersion = parseInt(userConfig.browserInfo.version);
                switch (userConfig.browserInfo.name) {
                    case "msie":
                        if (browserVersion != 11) {
                            location.href = "/old.html";
                        }
                        break;
                    case "opera":
                        if (browserVersion < 32) {
                            location.href = "/old.html";
                        }
                        break;
                    case "chromium":
                    case "chrome":
                        if (browserVersion < 48 ) {
                            location.href = "/old.html";
                        }
                        break;
                    case "safari":
                        if (browserVersion < 8) {
                            location.href = "/old.html";
                        }
                        break;
                    case "firefox":
                        if (browserVersion < 41) {
                            location.href = "/old.html";
                        }
                        break;
                    case "kindle":
                    case "avant":
                    case "skyfire":
                    case "vivaldi":
                    case "yandex":
                    case "ucbrowser":
                    case "netscape":
                    case "coast":
                    case "android browser":
                        location.href = "/old.html";
                }
                break;
            case "blackberry":
            case "symbian":
            case "windows phone":
            case "chrome os":
                location.href = "/old.html";
                break;
            //case "android":
            //case "ios":
            //    location.href = "/m.html";
        }
        if ('FileReader' in window) {
            var dragAndDropDownloadTimer = null;
            document.ondragover = function (event) {
                if (userConfig.login != (userConfig.company || {}).login) {
                    return
                }
                if (hasClass(byId("dragAndDropDownload"), "hide")) {
                    rmClass(byId("dragAndDropDownload"), "hide");
                    addClass(byId("dragAndDropDownload"), "slideInUp");
                }
                if (dragAndDropDownloadTimer) {
                    clearTimeout(dragAndDropDownloadTimer);
                    dragAndDropDownloadTimer = null;
                }
                event.preventDefault();
            };
            document.ondragleave = function (event) {
                if (userConfig.login != (userConfig.company || {}).login) {
                    return
                }
                if (dragAndDropDownloadTimer == null && !hasClass(byId("dragAndDropDownload"), "hide")) {
                    dragAndDropDownloadTimer = setTimeout(function () {
                        addClass(byId("dragAndDropDownload"), "hide");
                        rmClass(byId("dragAndDropDownload"), "slideInUp");
                        dragAndDropDownloadTimer = null;
                    }, 400);

                }
                event.preventDefault();
            };
            document.ondrop = function (e) {
                if (userConfig.login != (userConfig.company || {}).login) {
                    return
                }
                e.stopPropagation();
                e.preventDefault();
                addClass(byId("dragAndDropDownload"), "hide");
                rmClass(byId("dragAndDropDownload"), "slideInUp");
                /** @namespace e.dataTransfer */
                if (e.dataTransfer.files.length > 0) {
                    if (hasClass(byId("messageBG"), "active")) {
                        byId("messageBG").click();
                    }
                    window.documentsCollection.upload(e.dataTransfer.files);
                }
            };
        }

        rmClass(byId("viewControl"), "hide");

        if (location.pathname.indexOf("/accept") == 0) {
            actionLogin.finishRegistration();
        } else if (location.pathname.indexOf("/register") == 0) {
            if (getParameterByName("email")) {
                var newEmail = getParameterByName("email");
                byTag(byId("registrationForm1"), "INPUT")[1].value = newEmail;
                byTag(byId("registrationForm2"), "INPUT")[1].value = newEmail;
                setTimeout(function () {
                    byId("regName").focus()
                }, 300);
            }

            if (getParameterByName("name")) {
                var newName = getParameterByName("name");
                byTag(byId("registrationForm1"), "INPUT")[0].value = newName;
                byTag(byId("registrationForm2"), "INPUT")[0].value = newName;
                setTimeout(function () {
                    if(getParameterByName("email")){
                        byTag(byId("registrationForm2"), "INPUT")[2].focus();
                    } else {
                        byTag(byId("registrationForm2"), "INPUT")[1].focus();
                    }
                }, 300);
            }
        } else if (location.pathname.indexOf("/passreset") == 0) {
            actionLogin.restorePassword(location.hash.substr(1));
        } else if (location.pathname.indexOf("/share") == 0 || window.location.pathname.indexOf("/external/")==0) {
            var externalId = null;
            if( window.location.pathname.indexOf("/external/")==0) {
                externalId = window.location.pathname.substr(window.location.pathname.indexOf("/external/") + 10);
                // TODO CREATE
            }

            byId("firstPage").innerHTML = "";
            rmClass(byId("firstPage"), "hide");
            ajax((externalId)?
                    ("/api/externalSign/"+externalId):
                    ("/api/resource/shareall/" + location.pathname.substr(7)),
                function (res) {
                    res = JSON.parse(res);
                    res.id = externalId ? externalId : res.id;
                    var doc = res;
                    if (externalId){
                        /** @namespace res.redirectURL */
                        /** @namespace res.resource */
                        doc = res.resource;
                    }
                    userConfig.docId = doc.id;
                    userConfig.docs = [doc];
                    userConfig.doc = doc;
                    if (doc.signs && doc.signs.length > 0) {
                        userConfig.cache.signList = doc.signs;
                    } else {
                        userConfig.cache.signList = [];
                    }
                    var iFrameNode = buildNode("IFRAME", {
                        src: (userConfig.pdfReaderPath + "?file=" + userConfig.cdnPath + doc.src + "/" + doc.hash + "?embedded=true&disablehistory=true"),
                        frameborder: 0,
                        style: {width: "100%", height: (externalId)?"calc(100% - 45px)":"100%", border: "none", outline: "1px solid #d9d9d9"}
                    });
                    byId("firstPage").appendChild(iFrameNode);
                    if (externalId) {
                        // render external sign
                        byId("firstPage").style.display = "block";
                        byId("firstPage").style.overflowY = "hidden";
                        byId("firstPage").style.padding = 0;
                        var docSignMenu = buildNode("DIV", {
                            id: "openDocInfo",
                            className : "openDocInfoDocView openDocInfoDocViewRow",
                            style: {border: "none", width: "100%", height:"40px"}
                        });
                        docSignMenu.appendChild(buildNode("BUTTON", {
                            res:res,
                            id: "docMenuSign",
                            title: "Підписати документ ЕЦП",
                            className: "pure-button",
                            style: {width:"40%", background:"#5c9d21 none repeat scroll 0 0",color:"#f8f8f8", margin:"0 10% 5% 5%"},
                        },"<i class='fa fa-pencil-square-o fa-lg'></i> Підписати", {click:actionDoc.externalSign}));

                        docSignMenu.appendChild(buildNode("BUTTON", {
                            title: "Відмовитись від підписання та вернутись назад",
                            className: "pure-button",
                            style: {width:"40%", background:"#e57373 none repeat scroll 0 0",color:"#f8f8f8", margin:"0 0 5% 0"},
                        },"<i class='fa fa-times fa-lg'></i> Відмовитись", {click:function(){
                            window.location.replace(res.redirectURL)
                        }}));

                        byId("firstPage").appendChild(docSignMenu);
                    } else {
                        // render share all
                        byId("firstPage").style.overflowY = "hidden";
                        byId("firstPage").style.padding = 0;
                        var docInfoNode = buildNode("DIV", {
                            id: "openDocInfo",
                            className : "openDocInfoDocView",
                            style: {height: "calc(100% - 2px)", width: "430px"}
                        });
                        docInfoNode.appendChild(buildNode("DIV", {
                            className: "list",
                            style: "height: calc(100% - 45px);"
                        }, [
                            buildNode("DIV", {className: "header"},
                                ["Підписали",
                                    buildNode("BUTTON", {
                                        id: "downloadSignReport",
                                        title: "Завантажити протокол підписів",
                                        className: "pure-button",
                                        style: {display: "none", float: "right", margin: "3px", padding: "2px 3px"}
                                    }, "<i class='fa fa-list fa-lg'></i>", {click: actionDoc.downloadSignReport})]), //do not replace to documentsCollection
                            buildNode("DIV", {className: "signers"}),
                            buildNode("BUTTON", {
                                docHash: doc.hash,
                                docName: doc.name,
                                docPrefix: doc.src,
                                docId : res.id,
                                className: "pure-button",
                                style: {width:"calc(100% - 10px)", background:"#5c9d21 none repeat scroll 0 0",color:"#f8f8f8", margin:"5px"},
                                title: (userConfig.cache.signList.length > 0 ? "Завантажити документ з ЕЦП" : "Завантажити")
                            }, "<i class='fa fa-download fa-lg'></i> " + (userConfig.cache.signList.length > 0 ? "Завантажити з ЕЦП" : "Завантажити"), {click: actionDoc.downloadDocWithSign})
                        ]));

                        byId("firstPage").appendChild(docInfoNode);
                        actionDoc.drawSignList(doc.signs); //do not replace to documentsCollection
                    }
                }, null, function () {

                drawWarning("Документ не знайдено або автор закрив доступ");
            });
            return;
        }

        //addEvent(byId("search"), "keydown", actionDoc.searchDocument);
        //addEvent(byId("search"),"blur",actionDocHandler.searchDocument);

        addEvent(window, "resize", actionCore.resizeContext);
        addEvent(byId("canvas"), "click", actionCore.clickOnCanvas);
        addEvent(byId("userBox"), "click", actionCore.clickUserMenu);
        addEvent(byId("companyName"), "click", actionCore.drawCompanyMenu);

        addEvent(window, "popstate", function (e) {
            if (e.state && e.state.renderType == "doc") {
                window.documentsCollection.renderDocument(e.state.docId);
            } else if (e.state && e.state.renderType == "wizard") {
                actionWizard["step" + e.state.step]();
            } else {
                if (e.state && e.state.docList && typeof e.state.docList === "string") {
                    userConfig.docList = e.state.docList;
                }
                window.documentsCollection.offset = 0;
                window.documentsCollection.renderDocuments();
            }
        });


        actionCore.resizeContext();
        // Check if session is exist in cookie
        if (localStorage.getItem("sessionId")) {
            ajax("/api/login", function (result) {
                // session is exist
                actionCore.startToWork(result);
            }, null, function (e) {
                // session doesn't exist
                history.replaceState({renderType: "login"}, "", "/");
                //localStorage.removeItem("sessionId");
                addClass(byId("canvas"), "loginMode");
                addEvent(byId("startPage"), 'click', function () {
                    if (byTag(byId("captcha"), "DIV").length > 0 && !hasClass(byId("loginForm"), "hide")) {
                        addClass(byId("captcha"), "hide");
                    }
                    addClass(byId("loginForm"), "hide");
                });
                addEvent(byId("loginForm"), 'click', function (e) {
                    stopBubble(e);
                });
                addEvent(byId("loginLink"), 'click', function (e) {
                    stopBubble(e);
                });
                addClass(byId("firstPage"), "hide");
                rmClass(byId("startPage"), "hide");
                if (typeof grecaptcha == "undefined") {
                    loadJs("https://www.google.com/recaptcha/api.js?render=explicit&hl=uk");
                }

                if (localStorage.getItem("papka24login")) {
                    byTag(byId("loginForm"), "INPUT")[0].value = localStorage.getItem("papka24login");
                }
            });
        } else {
            history.replaceState({renderType: "login"}, "", "/");
            addClass(byId("firstPage"), "hide");
            rmClass(byId("startPage"), "hide");
            addEvent(byId("startPage"), 'click', function () {
                if (byTag(byId("captcha"), "DIV").length > 0 && !hasClass(byId("loginForm"), "hide")) {
                    addClass(byId("captcha"), "hide");
                }
                addClass(byId("loginForm"), "hide");
            });
            addEvent(byId("loginForm"), 'click', function (e) {
                stopBubble(e);
            });
            addEvent(byId("loginLink"), 'click', function (e) {
                stopBubble(e);
            });
            if (typeof grecaptcha == "undefined") {
                loadJs("https://www.google.com/recaptcha/api.js?render=explicit&hl=uk");
            }
            if (localStorage.getItem("papka24login")) {
                byTag(byId("loginForm"), "INPUT")[0].value = localStorage.getItem("papka24login");
            }
        }
        for (var i = 0; i < 2; i++) {
            addEvent(byTag(byId("loginForm"), "INPUT")[i], "keypress", function (e) {
                var code = (e.keyCode ? e.keyCode : e.which);
                if (code == 13) {
                    actionCore.loginAction();
                }
            });
        }
        if(byId("selectFile")){
            byId("selectFile").onchange = function () {
                byId("uploadFile").click();
            };
        }
        if(document.forms.upload){
            document.forms.upload.onsubmit = function () {
                if (this.elements.file.files.length > 0) {
                    window.documentsCollection.upload(this.elements.file.files);
                }
                return false;
            };
        }

        // Menu button handler
        var menuBtns = byClass(byId("mainMenu"), "pure-menu-item");
        addEvent(menuBtns[0], "click", function () {
            document.getElementById('search').value = ''; //TODO should filter by query
            addClass(menuBtns[0], "active");
            rmClass(menuBtns[1], "active");
            userConfig.docList = "docs";
            userConfig.tagFilter = null;
            foreach(byClass(byId("docTagFilterList"), "tagFilter"), function (el) {
                rmClass(el, "selected");
            });
            //userConfig.docFilter={dateFrom: null,dateTo: null,docAuthor: "all", docTags: []};
            history.replaceState({renderType: "list", docList: userConfig.docList}, "", "/list/" + userConfig.docList);
            window.documentsCollection.offset = 0;
            window.documentsCollection.renderDocuments();
        });
        addEvent(menuBtns[1], "click", function () {
            rmClass(menuBtns[0], "active");
            addClass(menuBtns[1], "active");
            userConfig.docList = "trash";
            foreach(byClass(byId("docTagFilterList"), "tagFilter"), function (el) {
                rmClass(el, "selected");
            });
            //userConfig.docFilter={dateFrom: null,dateTo: null,docAuthor: "all", docTags: []};
            history.replaceState({renderType: "list", docList: userConfig.docList}, "", "/list/" + userConfig.docList);
            window.documentsTrashCollection.offset = 0;
            window.documentsTrashCollection.renderDocuments();
        });
        /*addEvent(byId("option-four"), "change",function(){
         if (byId("option-four").value == "Будь хто"){
         userConfig.docFilter.docUser = 'all';
         } else {
         userConfig.docFilter.docUser=byId("option-four").value;
         }
         actionDoc.renderDocumentList();
         });*/
        var authFilterBtn = byTag(byId("docAuthorFilter"), "LABEL");
        foreach(authFilterBtn, function (btn) {
            addEvent(btn, "click", function () {
                userConfig.docFilter.docAuthor = document.docAuthorFilter.author.value;
                userConfig.docFilter.docSignFilter = document.docAuthorFilter.sign.value;
                history.pushState({renderType: "list", docList: userConfig.docList}, "", "/list/" + userConfig.docList);
                window.documentsCollection.offset = 0;
                window.documentsCollection.renderDocuments();
            });
        });
        function parseDate(date) {
            var parts = date.split('.');
            if (parts.length != 3 || parts[0].length > 2 || parts[1].length > 2 || parts[2].length != 4 || parseInt(parts[0], 10) > 31 || parseInt(parts[1], 10) > 12) {
                return null;
            }
            var result = new Date(parts[2], parts[1] - 1, parts[0]);
            if (isNaN(result.getTime())) {
                return;
            }
            return result;
        }

        function updateTimeFilter() {
            userConfig.docFilter.dateFrom = parseDate(byId("docFilterFrom").value);
            userConfig.docFilter.dateTo = parseDate(byId("docFilterTo").value);
            if (userConfig.docFilter.dateFrom != null && userConfig.docFilter.dateTo != null) {
                if (userConfig.docFilter.dateFrom > userConfig.docFilter.dateTo) {
                    var buf = userConfig.docFilter.dateFrom;
                    userConfig.docFilter.dateFrom = userConfig.docFilter.dateTo;
                    userConfig.docFilter.dateTo = buf;
                }
            }
            history.pushState({renderType: "list", docList: userConfig.docList}, "", "/list/" + userConfig.docList);
            window.documentsCollection.offset = 0;
            window.documentsCollection.renderDocuments();
        }

        addEvent(byId("docFilterFrom"), "change", updateTimeFilter);
        addEvent(byId("docFilterTo"), "change", updateTimeFilter);

        (function (i, s, o, g, r, a, m) {
            i['GoogleAnalyticsObject'] = r;
            i[r] = i[r] || function () {
                    (i[r].q = i[r].q || []).push(arguments)
                }, i[r].l = 1 * new Date();
            a = s.createElement(o),
                m = s.getElementsByTagName(o)[0];
            a.async = 1;
            a.src = g;
            m.parentNode.insertBefore(a, m)
        })(window, document, 'script', '//www.google-analytics.com/analytics.js', 'ga');
        ga('create', 'UA-72950532-1', 'auto');
        ga('send', 'pageview');
        var dateTo = new Datepickr(byId("docFilterTo"));
        addEvent(document.querySelector('#searchBox .extendedSearchWrap'), 'click', function (e) {
            e.preventDefault();
            e.cancelBubble = true;
            var target = e.currentTarget;
            if (hasClass(target, 'js-active')) {
                rmClass(target, 'js-active');
            } else {
                addClass(target, 'js-active');
                document.addEventListener('click', function (e) {
                    document.removeEventListener(e.type, arguments.callee);
                    rmClass(target, 'js-active');
                });
            }
        });
        addEvent(document.querySelector('#searchBox .extendedSearchContent'), 'keyup', function (e) {
            var keyCode = e.keyCode;
            if (keyCode === 27) { //handle ESC button
                rmClass(document.querySelector('#searchBox .extendedSearchWrap'), 'js-active');
            }
        });
        addEvent(document.querySelector('#searchBox .extendedSearchContent'), 'click', function (e) {
            var flag = e.target.className.indexOf('closeExtendedSearchContent') != -1;
            e.cancelBubble = !flag;
            if (flag) {
                clearExtendedFilters();
            }
            rmClass(document.querySelector('#searchBox .extendedSearchContent .dateSelectBox'), 'js-active');
            if (e.target.className != 'datapickr') {
                dateTo.close();
            }
            var tagName = e.target.tagName.toLowerCase();
            if (tagName === 'input' || tagName === 'option') {
                renderClearFilterButton();
            }
        });
        addEvent(document.querySelector('#searchBox .extendedSearchContent'), 'blur', function (e) {
            renderClearFilterButton();
        });
        addEvent(document.querySelector('#searchBox .extendedSearchContent'), 'change', function (e) {
            renderClearFilterButton();
        });
        addEvent(document.querySelector('#searchBox .extendedSearchContent'), 'keyup', function (e) {
            renderClearFilterButton();
        });

        addEvent(document.querySelector('#searchBox .searchQuery'), 'click', function (e) {
            history.pushState({renderType: "list", docList: userConfig.docList}, "", "/list/" + userConfig.docList);
            window.documentsCollection.offset = 0;
            window.documentsCollection.renderDocuments();
        });
        addEvent(document.getElementById('search'), 'keyup', function (e) {
            var setting = !userConfig.getUserPreference('responsiveSearch'); //defaults to true
            if (e.keyCode === 13 || setting) {
                history.pushState({renderType: "list", docList: userConfig.docList}, "", "/list/" + userConfig.docList);
                window.documentsCollection.offset = 0;
                window.documentsCollection.renderDocuments();
            }

        });
        addEvent(document.querySelector('.extendedSearchContent .dateSelectBox'), 'click', function (e) {
            e.cancelBubble = true;
            var target = e.currentTarget;
            if (hasClass(target, 'js-active')) {
                rmClass(target, 'js-active');
            } else {
                addClass(target, 'js-active');
                document.addEventListener('click', function (e) {//refactor, may have memory leaks
                    document.removeEventListener(e.type, arguments.callee);
                    rmClass(target, 'js-active');
                });
            }
        });
        addEvent(document.querySelector('.extendedSearchContent .dateSelectBox ul'), 'click', function (e) {
            var target = e.target,
                date,
                selectedEl = document.querySelector('.extendedSearchContent .dateSelectBox .selected');
            if (target.tagName.toLocaleLowerCase() === 'li') {
                selectedEl.setAttribute('data-date', target.getAttribute('data-date'));
                selectedEl.innerHTML = target.innerHTML;
                var toDate = parseDate(document.getElementById('docFilterTo').value) || new Date(),
                    fromDate = toDate.getTime() - target.getAttribute('data-date'),
                    formatedDate = formatDate(fromDate),
                    fromDateEl = document.getElementById('docFilterFrom'),
                    evt = document.createEvent("HTMLEvents");
                evt.initEvent("change", false, true);
                fromDateEl.value = formatedDate;
                fromDateEl.dispatchEvent(evt);
                renderClearFilterButton();
            }
        });
        addEvent(document.querySelector('.applyExtendedFilter'), 'click', function () { /*fixme говнокод аналог callbackSearch в fancyAutocomplete*/
            var arr = byId('contractorSearch').value.split(',').map(function (item) {
                return item.replace(/^["\s]+|["\s]+$/g, '');
            });
            userConfig.docFilter.docUser = arr.join('') ? arr : null;
            history.pushState({renderType: "list", docList: userConfig.docList}, "", "/list/" + userConfig.docList);
            window.documentsCollection.offset = 0;
            window.documentsCollection.renderDocuments();
        }.bind(this));
        function formatDate(ms) {
            var date = new Date(ms);
            return ('00' + (date.getDate())).slice(-2) + '.' + ('00' + (date.getMonth() + 1)).slice(-2) + '.' + date.getFullYear();
        }

        function clearExtendedFilters() { //TODO needs to be updated constantly. Depends on the form && date-type
            byId('docAuthorFilter').reset();
            var inputs = document.getElementById('docAuthorFilter').querySelectorAll('input[name=author]:checked, select, input:not([name=author])'),
                clickEvent = document.createEvent("HTMLEvents"),
                changeEvent = document.createEvent("HTMLEvents"),
                i, length, temp;
            clickEvent.initEvent("click", true, true);
            changeEvent.initEvent("change", true, true);
            for (i = 0, length = inputs.length; i < length; i++) {
                temp = (inputs[i].getAttribute('type') || '').toLocaleLowerCase();
                if (inputs[i].tagName.toLocaleLowerCase() === 'input' && temp != 'radio') {
                    inputs[i].value = '';
                    inputs[i].dispatchEvent(changeEvent);
                } else if (temp === 'radio' && inputs[i].getAttribute('name') === 'author') {
                    userConfig.docFilter.docAuthor = inputs[i].value;
                } else if (temp === 'radio' && inputs[i].getAttribute('name') === 'sign') {
                    userConfig.docFilter.docSignFilter = inputs[i].value;
                } else {
                    inputs[i].dispatchEvent(clickEvent);
                }
            }
            temp = document.querySelector('#docAuthorFilter .dateSelectBox .selected');
            temp.setAttribute('data-date', '86400000');
            temp.innerHTML = '1 день';
            userConfig.docFilter.docAuthor = 'all';
            userConfig.docFilter.docSignFilter = 'SignedOrNot';
            userConfig.docFilter.docUser= null;
            history.pushState({renderType: "list", docList: userConfig.docList}, "", "/list/" + userConfig.docList);
            window.documentsCollection.offset = 0;
            window.documentsCollection.renderDocuments();
            rmClass(document.querySelector('.extendedSearchClear'), 'js-active');
            document.getElementById('search').value = '';
        }

        function renderClearFilterButton() {
            if (nonDefault()) {
                addClass(document.querySelector('.extendedSearchClear'), 'js-active');
            } else {
                rmClass(document.querySelector('.extendedSearchClear'), 'js-active');
            }
        }

        function nonDefault() {
            var res = false,
                defaults = { //name of inputs
                    contractor: '',
                    author: 'all',
                    docFilterFrom: '',
                    docFilterTo: '',
                    sign : 'SignedOrNot'
                },
                temp;
            temp = byId('docAuthorFilter').querySelectorAll('#contractorSearch, input[name=author]:checked, input[name=sign]:checked, #docFilterFrom, #docFilterTo') //fixme должно браться из defaults по именам полей
            foreach(temp, function (node) {
                if (node.value != defaults[node.getAttribute('name')]) {
                    res = true;
                }
            });
            return res;
        }

        addEvent(document.querySelector('.extendedSearchClear'), 'click', clearExtendedFilters);
    }, 10)
});