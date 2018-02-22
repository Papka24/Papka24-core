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

actionDoc = {
    selected : 0,
    signs:[],
    tagColors: ["#e57373","#f06292","#ba68c8","#9575cd","#7986cb","#4fc3f7","#4dd0e1","#4db6ac","#81c784","#aed581","#dce775","#fff176","#ffd54f","#ffb74d","#ff8a65","#90a4ae"],
    detectOfficeType:function(file){
        return (file.type == "application/vnd.ms-excel" ||
            file.type == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
            file.type == "application/msword" ||
            file.type == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
            file.type == "application/vnd.oasis.opendocument.text")

    },
    // Загрузка новых документов на сервер
    upload: function (files) {
        var nf = [];
        var lastError = "";
        if (userConfig.avatarLoadMode) {
            if (files[0].type != "image/png" && files[0].type != "image/jpeg") {
                lastError = "Аватарку можливо створити з PNG JPEG файлів";
            } else if (files[0].size > 2048000) {
                lastError = "Завантаження файлів більше 2 МБ не підтримується :(";
            } else {
                nf.push(files[0]);
            }
        } else {
            foreach(files, function (f) {
                console.log(f.type);
                if (actionDoc.detectOfficeType(f)){
                    if (files.length>1){
                        lastError = "<div>Конвертація групи файлів не підтримується :( </div>";
                    } else {
                        if (f.size < 1024000) {
                            actionDoc.convert(f);
                            lastError = null;
                        } else {
                            lastError = "<div>Конвертація файлів більше 1МБ не підтримується :( </div>";
                        }
                    }
                } else {
                    if (f.size < 2048000) {
                        nf.push(f);
                    } else {
                        lastError = "<div>Завантаження файлів більше 2 МБ не підтримується :( Ви можете зменшити файл за допогою іншого сервісу, наприклад <a href='http://www.ilovepdf.com/ru/compress_pdf' target='_blank'>ilovepdf</a> або <a href='https://smallpdf.com/ru/compress-pdf' target='_blank'>smallpdf</a></div>";
                    }
                }
            });
        }

        if (nf && nf.length==0){
            if (lastError) {
                drawWarning(lastError);
            }
            return false;
        }

        actionDoc.up(nf, nf.length-1);
        return false;
    },

    // Convert files before loading
    convert:function(f){
        // /services/pdfconvert
        ajaxRawRePost("/services/pdfconvert",f, function(answer){
            answer.name = f.name;
            actionDoc.up([answer],0);
        }, function(e){
            console.warn(e);
        });
    },

    // download files to server
    up: function (f, index) {
        var xhr = new XMLHttpRequest();
        xhr.onload = xhr.onerror = function (ignored, data) {
            var progress = byClass(byTag(byId("menu"), "BUTTON")[0], "progress")[0];
            var span = byTag(byTag(byId("menu"), "BUTTON")[0], "SPAN")[0];
            rmClass(span, "hide");
            addClass(progress, "hide");

            if (this.status == 200) {
                if (userConfig.avatarLoadMode) {
                    addClass(byClass(byId("userBox"), "userBoxMenu")[0], "hide");
                    byClass(byId("userBox"), "userImage")[0].src = "data:image/jpeg;base64," + this.responseText;
                    byTag(byClass(byId("userBox"), "userImageBig")[0], "IMG")[0].src = "data:image/jpeg;base64," + this.responseText;
                    userConfig.avatarLoadMode = false;
                } else {
                    var newDocId = actionDoc.pushDocument(this.responseText);
                    window.documentsCollection.addDocument(JSON.parse(this.responseText), true);
                    if (f.length == 1) {
                        window.documentsCollection.renderDocument(newDocId);
                    } else {
                        if (index > 0) {
                            actionDoc.up(f, index - 1);
                        } else {
                            window.documentsCollection.offset = 0;
                            window.documentsCollection._navigation(window.documentsCollection.offset);
                        }
                    }

                }
                byId("upload").reset();
            } else {
                if (f.length == 1) {
                    if (this.status == 402) {
                        if (parseInt(this.responseText) == 1) {
                            drawWarning("Завантаження файлів було заблоковано, бо ви не сплатили борг минулого місяця");
                        } else {
                            var egrpou = parseInt(this.responseText);
                            if (egrpou < 10000000) {
                                egrpou = new Array(9 - ("" + egrpou).length).join("0") + egrpou;
                            }
                            drawWarning("Завантаження файлів було заблоковано вашій компанії (ЄДРПОУ " + egrpou + "), бо ви не сплатили борг минулого місяця");
                        }
                    } else if (this.status == 409) {
                        drawWarning("Ви вже завантажили такий файл.");
                    } else if (this.status == 415) {
                        drawWarning("Підтримується завантаження тільки PDF файлів та конвертація DOC, DOCX, ODT, RTF, XLS, XLSX.");
                    } else if (this.status == 413) {
                        drawWarning("Документ занадто великий");
                    } else {
                        drawWarning("Не вдалося завантажити файл, помилка " + this.status);
                    }
                } else {
                    if (index > 0) {
                        actionDoc.up(f, index - 1);
                    } else {
                        window.documentsCollection.renderDocumentList();
                    }
                }
            }
        };

        // обработчик для загрузки
        xhr.upload.onprogress = function (event) {
            /** @namespace event.loaded */
            /** @namespace event.total */
            var progress = byClass((byTag(byId("menu"), "BUTTON"))[0], "progress")[0];
            var span = byTag(byTag(byId("menu"), "BUTTON")[0], "SPAN")[0];
            addClass(span, "hide");
            rmClass(progress, "hide");
            var percent = parseInt(event.loaded * 100. / event.total);
            if (percent == 100) {
                progress.style.background = "none";
                progress.style.textAlign = "center";
                progress.innerHTML = "<img width='60' alt='' src='/img/three-dots.svg'>";
            } else {
                progress.style.background = "";
                progress.style.textAlign = "";
                progress.innerHTML = "&nbsp;&nbsp;" + percent + "%";
            }
            progress.style.width = percent + "%";
        };
        xhr.open("POST", "/upload", true);
        xhr.setRequestHeader("sessionid", localStorage.getItem("sessionId"));
        xhr.setRequestHeader("v", version);
        xhr.setRequestHeader("filename", encodeURI(f[index].name));
        if (userConfig.avatarLoadMode) {
            xhr.setRequestHeader("avatar", Sha256.hash(userConfig.login));
        }
        xhr.send(f[index]);
    },

    /**
     * Render windows for work with document
     * @param id
     */
    renderDocument: function (id) {
        localStorage.removeItem("papkaSignPrintInfo");
        localStorage.removeItem("papkaStampPrintInfo");
        if (typeof id === "object") {
            id = this.parentNode.getAttribute('docId');
        }
        if (byId("docTagFilterList").innerHTML==""){
            actionDoc.renderTagMenu();
        }
        addClass(byId("viewControl"), "hide");
        //if(!hasClass(byId("docAuthorFilter"),"hide")){
        //    byClass(byId("docFilter"),"filterTitle")[0].click();
        //}
        addClass(byId("docFilter"), "hide");

        userConfig.docId = id;

        actionDoc.getDocument(id, function(doc){
            if (doc == null) { //try to get resources from groups
                ajax('/api/resource/' + id, function (groupsDoc) {
                    groupsDoc = JSON.parse(groupsDoc);
                    userConfig.doc = groupsDoc;
                    actionDoc.actualRenderDocument(groupsDoc);
                }, false, function () {
                    actionDoc.actualRenderDocument(null);
                }, 'GET');
            } else {
                userConfig.doc = doc;
                actionDoc.actualRenderDocument(doc)
            }
        });
        actionDoc.updateCoreSearch();
    },
    actualRenderDocument : function (doc) {
        if (doc == null) {
            history.replaceState({renderType: "list", docList: userConfig.docList}, "", "/list/" + userConfig.docList);
            window.documentsCollection.renderDocumentList();
            return;
        }
        if (doc.author != userConfig.login && doc.status<2){
            doc.status = 2;
            var newCount = parseInt(byId("docCount").innerHTML.substr(1));
            if (newCount>1){
                byId("docCount").innerHTML="("+(newCount-1)+")";
            } else {
                byId("docCount").innerHTML="";
            }
        }
        var width = byId("contentBlock").offsetWidth - 340 - 70 + "px";
        var height = byId("menu").offsetHeight - 150 + "px";

        var docMenuNode = buildNode("DIV", {id: "openDocMenu"});
        var chatNode = buildNode("DIV", {className: "documentChat content"});
        var chat = new DocumentChat({id : doc.id});
        chatNode.appendChild(chat.el);
        docMenuNode.appendChild(buildNode("BUTTON", {
            docId: doc.id,
            className: "pure-button backButton",
            title: "Закрити"
        }, "<i class='fa fa-reply'></i>", {click: function(){documentsCollection._navigation(documentsCollection.offset);}})); //FIXME you SHOULDN'T touch _(underscore) methods, they are for internal logic
        docMenuNode.appendChild(buildNode("DIV", {className: "docName"}, [
                buildNode("INPUT", {
                    className : "docNameInput",
                    doc:doc,
                    docName: doc.name,
                    type: "text",
                    title: "Перейменувати",
                    value: doc.name,
                    readOnly:(doc.author != userConfig.login || userConfig.login != (userConfig.company || {}).login)
                }, null, {change:actionDoc.renameDoc}),
                (userConfig.login == userConfig.company.login?
                    buildNode("BUTTON", {
                        docId: doc.id,
                        style:{float:"left"},
                        className: "pure-button",
                        title: "Встановити ярлики для документа"
                    }, "<i class='fa fa-tags fa-lg'></i>", {click: actionDoc.setTags}):null),
                buildNode("BUTTON", {
                    docId: doc.id,
                    docHash: doc.hash,
                    docName: doc.name,
                    docPrefix: doc.src,
                    className: "pure-button",
                    style:{float:"left"},
                    title: "Завантажити документ з ЕЦП"
                }, "<i class='fa fa-download fa-lg'></i>", {click: actionDoc.downloadDocWithSign}),
                (userConfig.login == userConfig.company.login?buildNode("BUTTON", {
                    docId: doc.id,
                    className: "pure-button",
                    style:{float:"left"},
                    title: (doc.status<10?"Перенести документ в кошик ":" Видалити документ назавжди")
                }, "<i class='fa fa-trash fa-lg'></i>", {click:function(){this.disabled = true;actionDoc.deleteDocument.call(this);}}):null)
            ]
        ));

        if (userConfig.login == userConfig.company.login) {
            docMenuNode.appendChild(buildNode("DIV", {id: "tagListSetter", className: "card-1 hide"}, [
                buildNode("DIV", {})]));

            docMenuNode.appendChild(buildNode("BUTTON", {
                docId: doc.id,
                id: "docMenuSign",
                className: "pure-button coreBtn",
                title: "Підписати документ",
                style: {width: "170px"}
            }, "<i class='fa fa-pencil-square-o'></i> Підписати&nbsp;", {click: actionDoc.signDoc}));

            docMenuNode.appendChild(buildNode("BUTTON", {
                docId: doc.id,
                className: "pure-button",
                style: {width: "160px", margin: "0"},
                title: "Надіслати запрошення для перегляду або підписання документа"
            }, "<i class='fa fa-user-plus fa-lg'></i> Надати доступ", {click: actionDoc.addSharedPeople}));
        }
        docMenuNode.appendChild(actionDoc.buildShareMenu(doc.author == userConfig.login, doc));
        var iFrameNode = buildNode("IFRAME", {
            src: (userConfig.pdfReaderPath + "?file=" + userConfig.cdnPath + doc.src + "/" + doc.hash + "?embedded=true&disablehistory=true&locale=uk"),
            frameborder: 0,
            style: {width: width, height: height}
        });

        var docInfoNode = buildNode("DIV", {id: "openDocInfo", className : 'openDocInfoDocView', style: {height: height}});
        docInfoNode.appendChild(buildNode("DIV", {className: "list"}, [
            buildNode("DIV", {className: "header doNotClick"},
                [
                    "Підписали",
                    buildNode("BUTTON",{id:"downloadSignReport",title:"Завантажити протокол підписів1",className:"pure-button",style:{display:"none",float:"right",margin:"3px",padding:"2px 3px"}},"<i class='fa fa-list fa-lg'></i>",{click:actionDoc.downloadSignReport})
                ]),
            buildNode("DIV", {className: "signers content"})
        ]));
        docInfoNode.appendChild(buildNode("DIV", {className: "list"}, [
            buildNode("DIV", {className: "header"}, "Мають доступ <i style='float:right;margin:8px;cursor: pointer' class='fa fa-caret-down'></i>"),
            buildNode("DIV", {className: "shared content"})
        ]));
        if (userConfig.enableChat) {
            docInfoNode.appendChild(buildNode("DIV", {className: "list documentChat inactive"}, [
                buildNode("DIV", {className: "header"}, "Обговорення документу<i style='float:right;margin:8px;cursor: pointer' class='fa fa-caret-down'></i>"),
                chatNode
            ]));
        }

        byId("contentBlock").innerHTML = "";
        byId("contentBlock").appendChild(buildNode("DIV", {id: "openDoc"}, [docMenuNode, iFrameNode, docInfoNode]));
        addEvent(docInfoNode, 'click', function(e){
            var target = e.target;
            while(target && !hasClass(target, 'header')){
                target = target.id === 'openDocInfo' ? false : target.parentNode;
            }
            if(!target || hasClass(target, 'doNotClick')){return;}
            target = target.parentNode;
            if(hasClass(target, 'inactive')){
                rmClass(target, 'inactive');
            }else{
                addClass(target, 'inactive');
            }
        });

        userConfig.cache.signList=[];
        actionDoc.signs = [];
        ajax("/api/sign/" + userConfig.docId, actionDoc.updateSignList, null, function () {
            console.warn("Can't get signs for res", userConfig.docId)
        });

        window.setTimeout(function(){
            ajax("/api/share/" + userConfig.docId, actionDoc.updateShareList, null, function () {
                console.warn("Can't get sharing for res", userConfig.docId)
            });
        }, 50);

        addEvent(byId("shareAllLinkBtn"),"click",function(){
            drawChangeInfo("Посилання на документ скопійована в буфер обміну","",2000);
        });

        addEvent(byId("shareAllLink"),"focus",function(){
            byId("shareAllLinkBtn").click();
        });
        new Clipboard('#shareAllLinkBtn');
        setTimeout(actionCore.resizeContext, 10);
    },
    buildShareMenu: function(enableShareAll, doc){
        doc = doc || actionDoc.getDocument(userConfig.docId) || {};
        var subNodes = [];
        if (enableShareAll){
            subNodes=[
                buildNode("INPUT", {id:"shareAllChecker", type:"checkbox",className:"title", style:{width:"20px",height:"20px",position:"absolute"}, checked:userConfig.doc.status%10==6},"",{click:actionDoc.shareDocumentAll}),
                buildNode("SPAN", {className:"title", style:{width:"280px","margin-left":"25px"}},"Доступ за посиланням (тільки перегляд)"),
                buildNode("DIV", {className:"globalShare"+(userConfig.doc.status%10==6?" active":"")},
                    "<input id='shareAllLink' style='float: left' value='https://"+userConfig.domain+"/share/"+doc.hash+userConfig.docId+"'>"+
                    "<button id='shareAllLinkBtn' data-clipboard-target='#shareAllLink' class='pure-button'><i class='fa fa-clipboard'></i></button>"
                )];
        } else {
            subNodes=[buildNode("SPAN"), buildNode("DIV")];
        }
        subNodes.push(buildNode("SPAN", {className:"title", style:(enableShareAll?{"border-top":"1px solid #e5e5e5","margin-top":"5px","padding-top":"5px"}:"")},"Доступ по email (перегляд та підпис)"));
        subNodes.push(buildNode("DIV", {style: {"overflow-y":"auto","overflow-x":"hidden","margin-bottom":"10px", "padding-right":"5px", "max-height":"200px"}}));
        var emailSetter = buildNode("INPUT", {id: "emailShareInput", type: "text", placeholder: "Email отримувача"});
        var emailWrapper = buildNode("DIV", {className : 'emailShareWrapper'});
        emailWrapper.appendChild(emailSetter);
        subNodes.push(emailWrapper);
        var addEmail = buildNode("BUTTON", {className: "pure-button addEmail"}, "<i class='fa fa-plus'></i>");
        subNodes.push(addEmail);
        subNodes.push(buildNode("BR"));
        subNodes.push(buildNode("BR"));
        subNodes.push(buildNode("INPUT",{id:"shareWithoutSign",type:"checkbox", style:{width:"20px",height:"20px",position:"absolute"}}));
        subNodes.push(buildNode("SPAN", {className:"title",style:{width:"280px", marginLeft:"25px",marginTop:"-5px"}},"Не очікую підпису від отримувача<br>(не слати емейл-нагадування)"));
        subNodes.push(buildNode("TEXTAREA", {style:{width:"295px","margin-bottom":"10px",resize:"vertical",background:"white",border:"1px solid #e5e5e5"},placeholder:"Коментар до листа-запрошення"}));
        subNodes.push(buildNode("BUTTON", {className: "pure-button coreBtn"}, "<i class='fa fa-paper-plane'></i> Відправити", {click: actionDoc.shareDocument}));
        subNodes.push(buildNode("BUTTON", {className: "pure-button"}, "Відміна", {"click": actionDoc.clearDocShareMenu}));

        addEvent(emailSetter, 'keyup', function(e){
           var keyCode = e.keyCode;
            if(keyCode === 27){
                actionDoc.clearDocShareMenu();
            }
        });
        var cusomComplete = this.initSharesAutocomplete(emailSetter);
        addEvent(addEmail, 'click', function(){
            cusomComplete.callbackSearch();
        });
        return buildNode("DIV", {className: "docShareMenu card-1 hide"},subNodes);;
    },
    initSharesAutocomplete : function(target){
        var customComplete,
            mainArr = actionDoc.buildAutocompleteList(),
            keys = {
                valueKeys : ['email'],
                renderKeys : ['email', 'name', 'company']
            };
        customComplete = new FancyAutocomplete(mainArr, target, keys);

        customComplete.callbackSearch = actionDoc.addTempEmailToList.bind(customComplete);
        customComplete.updateTargetValue = function(){};
        customComplete.setSelectedValues = function(){
            var arr = this.target.value.split(',').map(function(item){
                    return item.replace(/^["\s]+|["\s]+$/g, '');
                }),
                length = arr.length,
                i,
                res = [];
            for(i = 0; i < length; ++i){ //_.compact
                if(arr[i]){
                    res.push(arr[i]);
                }
            }
            return res.length ? res : '';
        };
        window.customComplete = customComplete;
        return customComplete;
    },
    setTags:function(){
        var docTagMenu = byId("tagListSetter");
        if (this.docId) {
            docTagMenu.style.left = getOffset(this).left - 176 + "px";
        } else {
            docTagMenu.style.left = getOffset(this).left - 156 + "px";
        }
        docTagMenu.style.top = getOffset(this).top + 40 + "px";
        var doc = null;
        var configTag = null;
        var docs = [];
        if (this.docId) {
            doc = window.documentsCollection.get(userConfig.docId).attributes;
            configTag = doc.tags;
        } else {
            foreach(byClass(byId("contentBlock"), "selected", "TR"), function (el) {
                docs.push(el.getAttribute('docId'));
                if (configTag != null){
                    if (configTag!=window.documentsCollection.get(el.getAttribute('docId')).get('tags')){
                        configTag = 0;
                    }
                } else {
                    configTag = window.documentsCollection.get(el.getAttribute('docId')).get('tags');
                }
            });
        }
        docTagMenu.innerHTML="";
        for (var t  in userConfig.tagList){
            if (userConfig.tagList.hasOwnProperty(t)){
                var tagInfo = userConfig.tagList[t];
                var tag = buildNode("DIV",{tagId: tagInfo.id, className:"tagFilter"},
                    "<div class='tagBlock"+((configTag>>tagInfo.id&0x01==1)?"":" disable")+"' style='background: "+actionDoc.tagColors[tagInfo.color]+"'></div><span>"+ tagInfo .text+"</span>",{
                        click:function(){
                            var block = byClass(this,"tagBlock")[0];
                            var mask = 1 << this.tagId;
                            if(hasClass(block,"disable")){
                                rmClass(block,"disable");
                                configTag |= mask;
                            } else {
                                addClass(block, "disable");
                                configTag &= ~mask;
                            }
                            if (docs.length==0) {
                                doc.tags = configTag;
                                ajax("/api/resource/tag/" + doc.id, function () {}, doc.tags, function () {}, "PUT");
                            } else {
                                foreach(docs, function(id){
                                    window.documentsCollection.get(id).set('tags', configTag);
                                });
                                ajax("/api/resource/tags/" + docs.join("_"), function () {
                                    window.documentsCollection.renderDocuments();
                                }, configTag, function () {}, "PUT");
                            }

                        }
                    });
                docTagMenu.appendChild(tag);
            }
        }
        docTagMenu.appendChild(buildNode("BUTTON",{style:{margin:"5px 0 0 0"},className:"pure-button",title:"Управління ярликами"},"Управління ярликами",{click:actionDoc.renderTagManager}));
        if (hasClass(docTagMenu, "hide")) {
            rmClass(docTagMenu, "hide");
        } else {
            addClass(docTagMenu, "hide");
        }
    },

    downloadDocWithSign: function() {
        var docName = this.docName;
        var docHash = this.docHash;
        var docPrefix = this.docPrefix;
        var docId = this.docId;
        if(!userConfig.cache.signList || userConfig.cache.signList.length==0){
            ajaxRaw("/cdn/"+docPrefix+"/" + docHash, function (doc) {
                saveAs(b64toBlob(doc), docName.replace (/[,|<>\?\*\/\\:]/g, "") + ".pdf");
            }, function(e){console.error("Error load doc",e);})
        } else {
            cpGUI.checkPlugin(
                function() {
                    var dn = docName;
                    var dh = docHash;
                    var dp = docPrefix;
                    return function(){
                    //drawMessage(loaderSnippet,120, false, 120);
                    ajaxRaw("/cdn/"+dp+"/" + dh, function (doc) {

                            cpGUI.addSignToDoc(doc, userConfig.cache.signList, function (e) {
                                /** @namespace e.CMS */
                                byId("message").click();
                                if(e && e.hasOwnProperty("CMS")) {
                                    saveAs(b64toBlob(e.CMS), "Підписаний_" + dn.replace(/[,|<>\?\*\/\\:]/g, "") + ".pdf");
                                }
                            }, function (e) {
                                byId("message").click();
                                drawWarning(e.message + " [код: " + e.code + "]");
                            });

                    }, function(e){console.error("Error load doc",e);})
                    }
                }(),
                function() {
                    var id = docId;
                    var dn = docName;
                    return function(){
                        drawMessage("<div style='margin:50px 40px'>" + loaderSnippet + "</div>", 200, true, 200);
                        ajaxRaw("/api/resource/withsign/" + id, function (doc) {
                            saveAs(b64toBlob(doc), "Підписаний_" + dn.replace(/[,|<>\?\*\/\\:]/g, "") + ".pdf");
                            byId("messageBG").click();
                        }, function (e) {
                        })
                    }
                }()
            );
        }
    },

    // Add email to DIV in docShareMenu block
    addTempEmailToList: function (arr) {
        //this === autocomplete
        var that = this;
        if(!arr || !arr.length){
            var tempValue = that.target.value;
            if(tempValue){
                actionDoc.addTempEmailToList.call(that, [tempValue]);
            }
            return;
        }
        var email = arr[0];
        if(!email){
            return;
        }
        if (!emailRegexp.test(email) && email != userConfig.login) {
            var found = false;
            for (var f in userConfig.friends){
                if(userConfig.friends.hasOwnProperty(f) && userConfig.friends[f]==email){
                    email = f;
                    found = true;
                    break;
                }
            }
            if (!found){
                return;
            }
        }

        var els = byClass(byClass(byId("openDocMenu"), "docShareMenu")[0], "shareItemEmail"),
            superMegaCheck = false;
        for (var e in els) {
            if (els.hasOwnProperty(e)) {
                var d = byTag(els[e], "DIV");
                if (d != null && d[0] != null && email.toLowerCase()== d[0].innerHTML.toLowerCase()) {

                    superMegaCheck = true;
                    break;
                }
            }
        }
        if(superMegaCheck){
            byId("emailShareInput").value = "";
            return;
        }
        var user = buildNode("DIV", {className: "shareItemEmail"+(userConfig.friends.hasOwnProperty(email) ? "" : " missing")}, [
            buildNode("DIV", {title: email+' з '+actionDoc.getCompanyNameByEmail(email)+'.'}, email),
            buildNode("SPAN", {title: "Видалити зі списку"}, "<i class='fa fa-times'></i>", {
                click: function () {
                    this.parentNode.parentNode.removeChild(this.parentNode);
                    var arr = document.querySelectorAll('.docShareMenu .shareItemEmail > div'),
                        length, i,
                        res = [];
                    for(i = 0, length = arr.length; i < length; i++){
                        res.push(arr[i].innerText);
                    }
                    that.selectedValues = res.length ? res : '';
                }
            }),
            (userConfig.friends.hasOwnProperty(email) ? "" : buildNode("DIV", {className: "info"}, "Email використовується вперше."))
        ]);
        byTag(byClass(byId("openDocMenu"), "docShareMenu")[0], "DIV")[1].appendChild(user);
        byId("emailShareInput").value = "";
        var someNewArr = document.querySelectorAll('.docShareMenu .shareItemEmail > div'),
            length, i,
            res = [];
        for(i = 0, length = someNewArr.length; i < length; i++){
            res.push(someNewArr[i].innerText);
        }
        this.selectedValues = res.length ? res : '';
    },

    shareDocumentAll: function () {
        if(hasClass(byId("shareAllLink").parentNode,"active")){
            rmClass(byId("shareAllLink").parentNode,"active");
            setTimeout(function(){
                ajax("/api/resource/shareall/"+userConfig.docId,function(){},"",function(){},"DELETE");
            },10);
            // FIXME change status depends on shared people
            actionDoc.getDocument(userConfig.docId).status=5;
        } else {
            addClass(byId("shareAllLink").parentNode,"active");
            setTimeout(function(){
                ajax("/api/resource/shareall/"+userConfig.docId,function(){},"",function(){},"PUT");
            },10);
            actionDoc.getDocument(userConfig.docId).status=6;
        }
    },

    // Clear docShareMenu
    clearDocShareMenu: function () {
        if(!hasClass(byClass(byId("openDocMenu"), "docShareMenu")[0], "hide")) {
            byTag(byClass(byId("openDocMenu"), "docShareMenu")[0], "INPUT")[0].value = "";
            byTag(byClass(byId("openDocMenu"), "docShareMenu")[0], "DIV")[1].innerHTML = "";
            addClass(byClass(byId("openDocMenu"), "docShareMenu")[0], "hide");
            byTag(byId("emailShareInput").parentNode.parentNode,"TEXTAREA")[0].value="";
        }
    },

    // Share document with specific emails
    shareDocument: function () {
        actionDoc.addTempEmailToList.call(window.customComplete); //FIXME no need for window.customComplete
        var divs = byClass(byTag(byClass(byId("openDocMenu"), "docShareMenu")[0], "DIV")[1], "shareItemEmail");
        var emails = [];
        foreach(divs, function (el) {
            var d = byTag(el, "DIV");
            if (d != null && d[0] != null) {
                emails.push(d[0].innerHTML);
            }
        });
        if (emails.length > 0) {
            var requests=[];
            var mode = byId("shareWithoutSign").checked?1:0;
            var comment = byTag(byId("emailShareInput").parentNode.parentNode,"TEXTAREA")[0].value;
            foreach(emails, function(e){
                requests.push({
                    email:e,
                    comment:comment,
                    mode:mode
                });
            });
            var id = "";
            if (userConfig.docId){
                id = userConfig.docId;
            } else {
                var docs = [];
                foreach(byClass(byId("contentBlock"), "selected", "TR"), function (el) {
                    docs.push(el.getAttribute('docId'));
                });
                id = docs.join("_");
            }
            ajax("/api/share/" + id, actionDoc.updateShareList, JSON.stringify({requestList:requests}), function (error) {
                console.warn(error)
            }, "POST");
            byTag(byId("emailShareInput").parentNode.parentNode,"TEXTAREA")[0].value="";
        } else {
            // TODO: add error message if user don't enter any emails.
        }
        byId("shareWithoutSign").checked=false;
        actionDoc.clearDocShareMenu();
    },

    // Разобрать список приглашений и отобразить на экране или обновить список документов
    updateShareList: function (listJSON) {
        var newShare = JSON.parse(listJSON);
        if (window.isArray(newShare)){
            var data = {};
            data[userConfig.docId]= newShare;
            newShare = data;
        }
        foreach(newShare, function(shares, id) {
            actionDoc.getDocument(id, function (doc) {
                if(!doc){return false;} //fix for groups
                var doc2 = documentsCollection.get(id);
                foreach(shares,function(s){
                    if(s.hasOwnProperty("user")) {
                        doc.shares = doc.shares || {};
                        doc.shares[s.user] = s.status;
                    }
                });
                if(doc2){
                    doc2.attributes.shares = doc.shares;
                }
                if (userConfig.docId) {
                    var users = buildNode("DIV");
                    if (!userConfig.company || userConfig.company.login != doc.author) {
                        var tempAuthorEmail = userConfig.friends.hasOwnProperty(doc.author) && userConfig.friends[doc.author] != "" ? userConfig.friends[doc.author] : doc.author;
                        var temp = actionDoc.getCompanyNameByEmail(tempAuthorEmail);
                        temp = temp ? tempAuthorEmail + ' з ' + temp : tempAuthorEmail;
                        users.appendChild(buildNode('DIV', {className: 'shareItemEmail '+(doc.deleteByOwner?'statusAuthorDeleted':'statusAuthor')}, [
                            buildNode('DIV', {className : 'shareItemEmailWrap'}, [
                                buildNode('DIV', {
                                    title : temp
                                }, tempAuthorEmail),
                                buildNode("SPAN")
                            ]),
                            buildNode("IMG", {src: userConfig.cdnPath + "avatars/" + Sha256.hash(doc.author) + ".png"}, null, {
                                error: function () {
                                    this.onerror = null;
                                    this.src = "https://secure.gravatar.com/avatar/" + MD5(doc.author) + "?d=mm";
                                }
                            })
                        ]));
                    }
                    foreach(shares, function (el) {
                        if (userConfig.login != el.user) {
                            var temp = actionDoc.getCompanyNameByEmail(el.user);
                            temp = temp ? el.user + ' з '+actionDoc.getCompanyNameByEmail(el.user)+'.' : el.user;
                            users.appendChild(buildNode('DIV', {
                                className: 'shareItemEmail status' + el.status % 10,
                                title : temp
                            }, [
                                buildNode('DIV', {className : 'shareItemEmailWrap'}, [
                                    buildNode('DIV', {}, userConfig.friends.hasOwnProperty(el.user) && userConfig.friends[el.user] != "" ? userConfig.friends[el.user] : el.user),
                                    buildNode("SPAN")
                                ]),
                                (el.status%10 <= 1 ? buildNode("P", {title: "Відізвати запрошення"}, "<i class='fa fa-times'></i>", {
                                    click: function () {
                                        var share = el;
                                        var localDoc = doc;
                                        return function () {
                                            ajax("/api/share/" + localDoc.id + "/" + share.user, function () {
                                                foreach(document.querySelectorAll('#openDocInfo .shareItemEmail'), function () {
                                                    var u = share.user;
                                                    return function (e) {
                                                        if (e.title == u) {
                                                            e.parentNode.removeChild(e);
                                                            return false;
                                                        }
                                                    }
                                                }());
                                                delete window.documentsCollection.get(localDoc.id).attributes.shares[share.user];
                                                window.documentsCollection.changeDocReference();
                                                window.documentsCollection.saveToLocalStorage();
                                            }, "", function () {
                                            }, "DELETE");
                                        }
                                    }()
                                }) : ""),
                                (el.status%10 > 1 ?
                                    buildNode("IMG", {src: userConfig.cdnPath + "avatars/" + Sha256.hash(el.user) + ".png"}, null, {
                                        error: function () {
                                            this.onerror = null;
                                            this.src = "https://secure.gravatar.com/avatar/" + MD5(el.user) + "?d=mm";
                                        }
                                    })
                                    :
                                    ""
                                )
                            ]));
                        }
                    });
                    var openDocInfo = byClass(byId('openDocInfo'), 'shared')[0];
                    if(!openDocInfo){
                        return; //FIXME wtf sometimes NO openDocInfo
                    }
                    if (users.innerHTML == "") {
                        openDocInfo.innerHTML = "<div class='centeredTitle'>Має доступ тільки автор</div>";
                    } else {
                        openDocInfo.innerHTML = "";
                        openDocInfo.appendChild(users);
                    }
                }
            });
            if (!userConfig.docId) {
                window.documentsCollection.renderDocumentList();
            }
        });
        window.documentsCollection.changeDocReference();
        window.documentsCollection.saveToLocalStorage();
    },

    updateSignListTimer: null,

    // Разобрать и отобразить информацию о подписи под документом
    updateSignList: function (listJSON) {
        if(byId("openDocInfo")==null){return;}
        if (actionDoc.updateSignListTimer!=null){
            clearTimeout(actionDoc.updateSignListTimer);
            actionDoc.updateSignListTimer = null;
        }
        var list;
        if (typeof listJSON == "string") {
            try {
                list = JSON.parse(listJSON);
                userConfig.cache.signList = list;
            } catch (e) {
                list = userConfig.cache.signList;
            }
        } else {
            list = userConfig.cache.signList;
        }
        actionDoc.drawSignList(list);
    },

    drawSignList:function(list){
        function drawSignList(result){
            if (byId("docMenuSign")){
                byId("docMenuSign").disabled=true;
            }
            var signs = [];
            var signText = "";
            var stampCompany = [];
            var signDescription = [];
            foreach(result, function (el) {
                /** @namespace el.cert.subject.CN */
                /** @namespace el.cert.subject.O */
                /** @namespace el.cert.subject.SRN */
                /** @namespace el.cert.subject.GN */
                /** @namespace el.tspValue */
                /** @namespace el.signingTimeValue */
                /** @namespace el.tspStatus */
                /** @namespace {String} el.ocsp.status */
                /** @namespace el.ocsp.OCSPRespSign */
                /** @namespace el.ocsp.OCSPRespStatus */
                /** @namespace {String} el.ocsp.status */
                /** @namespace el.ocsp.revocationReason */
                /** @namespace el.ocsp.revocationTime */
                var info;
                if (el.verifyTime==null){
                    el.verifyTime = Date.now();
                }
                el.time = (el.tspValue ? el.tspValue : el.signingTimeValue);
                if(el.hasOwnProperty('cert') && el.cert && el.cert.hasOwnProperty("subject") && el.cert.subject && el.cert.subject.hasOwnProperty("CN") && el.cert.subject.CN) {
                    actionDoc.signs.push([el.cert.subject.CN, el.cert.subject.O, el.cert]);
                    info = "<i class='signTime'>";
                    var signError = false;
                    var ocspError = false;
                    var d = userConfig.doc;
                    if (d!=null){
                        d = d.hash;
                    }
                    if (typeof el.hash != "undefined" && el.hash.replace(/\+/g,'-').replace(/\//g,'_').replace(/=/g,'')!=d){
                        signError = true;
                        info += " <i style='color:#f44336'>[ПОМИЛКОВИЙ ПІДПИС]</i>";
                    } else if (el.time){
                        info += new Date(el.time * 1000).toTimestampString();
                        signError = el.time < el.cert.notValidBefore
                            || el.time > el.cert.notValidAfter
                            || el.verifyResult.indexOf("valid") != 0;
                        if (el.ocsp!=null && el.ocsp.status !== "unknown"){
                            if (el.ocsp.status != "good" && el.time > el.ocsp.revocationTime){
                                info += " <i style='color:#f44336'>[ВІДКЛИКАНО "+new Date(el.ocsp.revocationTime * 1000).toDatastampString()+"]</i>";
                                signError = true;
                            }
                        } else {
                            ocspError = true;
                            if(cpGUI.coreCert.hasOwnProperty(el.cert.issuer.SN)) {
                                info += " [НЕВІДОМИЙ СТАТУС]";
                                if (!signError) {
                                    info += "<br>Увага! Підпис вірний, але дійсність сертифікату необхідно перевірити особисто. Автоматично перевірити статус сертифікату не вдалось.";
                                }
                            } else {
                                info += " [СЕРТИФІКАТ НЕ З АЦСК]";
                                if (!signError) {
                                    info += "<br>Увага! Підпис вірний, але сертифікат отриман не через АЦСК та не має юридичної сили";
                                }
                            }
                        }
                    } else {
                        signError = el.verifyResult.indexOf("valid") != 0;
                        info += "<i style='color:#ff6d00'>Час не вказано</i>";
                        if (el.ocsp!=null && el.ocsp.status !== "unknown"){
                            if (el.ocsp.status != "good"){
                                info += " <i style='color:#f44336'>[ВІДКЛИКАНО "+new Date(el.ocsp.revocationTime * 1000).toDatastampString()+"]</i>";
                                signError = true;
                            }
                        } else {
                            ocspError = true;
                            if(cpGUI.coreCert.hasOwnProperty(el.cert.issuer.SN)) {
                                info += " [НЕВІДОМИЙ СТАТУС]";
                                if (!signError) {
                                    info += "<br>Увага! Підпис вірний, але дійсність сертифікату необхідно перевірити особисто. Автоматично перевірити статус сертифікату не вдалось.";
                                }
                            } else {
                                info += " [СЕРТИФІКАТ НЕ З АЦСК]";
                                if (!signError) {
                                    info += "<br>Увага! Підпис вірний, але сертифікат отриман не через АЦСК та не має юридичної сили";
                                }
                            }
                        }
                    }
                    //if ()
                    //+ ((el.ocsp!=null && el.ocsp.status!="good")?" <b>[ВІДКЛИКАНИЙ]</b>":"");
                    info += "</i>";
                    if (el.cert.isStamp){
                        info += "<div>Електронна печатка</div>";
                        if(el.cert.subject.O && el.cert.subject.O.length > 0 && stampCompany.indexOf(el.cert.subject.O)==-1){
                            stampCompany.push(el.cert.subject.O);
                        }
                    } else {
                        info += "<div>" + el.cert.subject.CN + "</div>";
                    }
                    if (typeof el.cert.subject.O === "string"){
                        if (el.cert.isStamp){
                            info += "<span>";
                            info += el.cert.subject.O;
                            if (typeof  el.cert.subject.SRN != "undefined" && typeof  el.cert.subject.GN != "undefined" && el.cert.subject.SRN && el.cert.subject.GN && el.cert.subject.O.indexOf(el.cert.subject.SRN+" "+el.cert.subject.GN)==-1){
                                info += "<br>"+el.cert.subject.SRN+" "+el.cert.subject.GN;
                            }
                            info += "</span>";
                        } else {
                            info +=  "<span>";
                            if (typeof el.cert.subject.T === "string"){
                                info += el.cert.subject.T+", ";
                            }
                            info += el.cert.subject.O + "</span>";
                        }
                    }

                    signDescription.push(actionDoc.renderTextSignInfo(el, actionDoc.getDocument(userConfig.docId), el.verifyTime,true));

                    if (!signError){
                        if (el.cert.isStamp){
                            //signText += "Печатка "+ el.cert.subject.CN;
                        } else {
                            signText += el.cert.subject.CN;
                            if (el.cert.subject.O){
                                if (el.cert.subject.T){
                                    signText += ", " + el.cert.subject.T + " " + el.cert.subject.O;
                                } else {
                                    signText += ", " + el.cert.subject.O;
                                }
                            }
                        }
                        if (el.time){
                            signText +=  " ("+new Date(el.time * 1000).toTimestampString()+")";
                        }
                        signText += ";";
                    }

                    signs.push(buildNode('DIV', {
                        className: (signError ? 'signInfo signError' : (ocspError ?'signInfo nonameCert':'signInfo')),
                        signInfo: el
                    }, info, {"click": actionDoc.showSignInfo}));
                } else {
                    info = "<i class='signTime'>Неможливо перевірити</i>";
                    info += "<div>Невідомий формат підпису</div>";
                    info +="<span></span>";

                    signs.push(buildNode('DIV', {
                        className: 'signInfo signError',
                        signInfo: el
                    }, info));
                }
            });

            if (signText!=""){
                localStorage.setItem("papkaSignPrintInfo", "Документ підписаний за допомогою ЕЦП на ресурсі papka24.com.ua наступними особами: "+signText);
            }

            if (stampCompany.length>0){
                var stampImg = [];
                foreach(stampCompany,function (c) {
                    stampImg.push(actionDoc.drawStamp(c));
                });
                localStorage.setItem("papkaStampPrintInfo", JSON.stringify(stampImg));
            }

            var signsBlocks = buildNode("DIV");
            signsBlocks.signList = list;

            signs.sort(function(a,b){
                var result;
                a = a.signInfo;
                b = b.signInfo;
                if(typeof a.cert!="undefined" && typeof b.cert!="undefined"
                    && a.cert && b.cert
                    && a.cert.subject && b.cert.subject){
                    if (typeof a.cert.subject.O === "string" && typeof b.cert.subject.O === "string"){
                        result = a.cert.subject.O.localeCompare(b.cert.subject.O);
                        if (result != 0){
                            return result;
                        }
                    }
                    result = a.cert.subject.CN.localeCompare(b.cert.subject.CN);
                    if (result!=0){
                        return result;
                    }
                    if (a.cert.isStamp && !b.cert.isStamp){
                        return 1;
                    }
                    if (b.cert.isStamp && !a.cert.isStamp){
                        return -1;
                    }
                    return 0;
                } else {
                    if (typeof a.cert == "undefined" && !a.cert){
                        return -1;
                    } else if (typeof b.cert == "undefined" && !b.cert){
                        return 1;
                    } else {
                        if (a.verifyResult.indexOf("valid") == 0 && b.verifyResult.indexOf("valid") != 0) {
                            return -1;
                        } else if (b.verifyResult.indexOf("valid") == 0 && a.verifyResult.indexOf("valid") != 0) {
                            return 1
                        }
                        return 0;
                    }
                }
            });

            foreach(signs,function(s){
                signsBlocks.appendChild(s);
            });

            if (byClass(byId('openDocInfo'), 'signers').length>0) {
                if (signs.length == "") {
                    byClass(byId('openDocInfo'), 'signers')[0].innerHTML = "<div class='centeredTitle'>Документ ще не підписан</div>";
                } else {
                    var tempOpenDocInfo = byClass(byId('openDocInfo'), 'signers')[0];
                    if(tempOpenDocInfo && tempOpenDocInfo.appendChild){
                        tempOpenDocInfo.innerHTML = "";
                        byClass(byId('openDocInfo'), 'signers')[0].appendChild(signsBlocks);
                    }
                    if(byId('downloadSignReport')){
                        byId('downloadSignReport').style.display="block";
                        byId('downloadSignReport').signDescription = signDescription;
                    }
                }
            }
            if (byId("docMenuSign")){
                byId("docMenuSign").disabled=false;
            }
        }

        if(list && list.length>0) {
            if (byId("docMenuSign")){
                byId("docMenuSign").disabled=true;
            }
            byClass(byId('openDocInfo'), 'signers')[0].innerHTML = "<div style='margin-left:110px;font-weight: 100'><br>Перевiрка ЕЦП<br>" + loaderSnippet + "</div>";
            cpGUI.verify(list, drawSignList, function(){
                var hashSign = [];
                var hashMap = {};
                foreach(list, function(sign){
                    var h = Sha256.hash(sign);
                    hashSign.push(Sha256.hash(sign));
                    hashMap[h]={sign:sign,result:null,time:null};
                });
                ajax("/api/sign/cache",function(e){
                    var signs = JSON.parse(e);
                    var h;
                    foreach(signs, function(s){
                        /** @namespace si.verifyInfo */
                        var si = JSON.parse(s);
                         if(si.hasOwnProperty("hash") && si.hasOwnProperty("verifyInfo") && hashMap.hasOwnProperty(si.hash)){
                            hashMap[si.hash].result=si.verifyInfo;
                            hashMap[si.hash].time=si.time;
                        }
                    });
                    var newSign = [];
                    for(h in hashMap){
                        if(hashMap.hasOwnProperty(h) && hashMap[h].result==null){
                            newSign.push(hashMap[h].sign);
                        }
                    }
                    if (newSign.length>0){
                        ajax("/api/sign/cache",function(e){
                            var signs = JSON.parse(e);
                            foreach(signs, function(s){
                                var si = JSON.parse(s);
                                if(si.hasOwnProperty("hash") && si.hasOwnProperty("verifyInfo") && hashMap.hasOwnProperty(si.hash)){
                                    hashMap[si.hash].result=si.verifyInfo;
                                    hashMap[si.hash].time=si.time;
                                }
                            });
                            var results=[];
                            for(var h in hashMap){
                                if(hashMap.hasOwnProperty(h) && hashMap[h].result!=null){
                                    foreach(hashMap[h].result, function(){
                                        var time = hashMap[h].time;
                                        var sign = hashMap[h].sign;
                                        return function(r){
                                            r.verifyTime = time;
                                            r.sign = sign;
                                            results.push(r);
                                        }
                                    }());
                                }
                            }
                            drawSignList(results);
                        }, JSON.stringify(newSign), function(e){console.warn("ERROR 2",e)});
                    } else {
                        var results=[];
                        for(h in hashMap){
                            if(hashMap.hasOwnProperty(h) && hashMap[h].result!=null){
                                foreach(hashMap[h].result, function(){
                                    var time = hashMap[h].time;
                                    var sign = hashMap[h].sign;
                                    return function(r){
                                        r.verifyTime = time;
                                        r.sign = sign;
                                        results.push(r);
                                    }
                                }());
                            }
                        }
                        drawSignList(results);
                    }
                },JSON.stringify(hashSign),function(e){console.warn("ERROR 1",e)},"PUT");
                if (byId("docMenuSign")){
                    byId("docMenuSign").disabled=false;
                }
            });
        } else {
            if (byId("docMenuSign")){
                byId("docMenuSign").disabled=false;
            }
            byClass(byId('openDocInfo'), 'signers')[0].innerHTML = "<div class='centeredTitle'>Документ ще ніким не підписаний</div>";
        }
    },

    drawStamp:function(companyName){
        var canvas = document.createElement('canvas');
        canvas.width=350;
        canvas.height=350;
        var context = canvas.getContext('2d');
        var centerX = canvas.width / 2;
        var centerY = canvas.height / 2;
        var radius = 150;
        var color = '#01579b';
        var text  = 'Електронна печатка       накладена в Папка24       ';
        var startRotation = -1.1;
        var blockH = 80;

        var numRadsPerLetter = 2*Math.PI / text.length;
        context.globalAlpha = 0.7;
        context.save();
        context.translate(centerX,centerY);
        context.rotate(startRotation);

        for(var i=0;i<text.length;i++){
            context.save();
            context.rotate(i*numRadsPerLetter);
            context.font="24px Sans-serif";
            context.fillStyle = color;
            context.fillText(text[i],0,-radius*0.79);
            context.restore();
        }
        context.restore();
        context.beginPath();
        context.arc(centerX, centerY, radius, 0, 2 * Math.PI, false);
        context.lineWidth = 3;
        context.strokeStyle = color;
        context.stroke();

        context.beginPath();
        context.arc(centerX, centerY, radius*0.7, 0, 2 * Math.PI, false);
        context.lineWidth = 2;
        context.strokeStyle = color;
        context.stroke();

        context.fillStyle=color;
        context.lineWidth = 2;
        context.clearRect(centerX-radius-9, centerY-blockH/2+1, 2*radius+18, blockH-2);
        context.roundRect(centerX-radius-10, centerY-blockH/2, 2*radius+20, blockH,10).stroke();

        context.font="14px Monospace";
        var shift = 14;
        var w = context.measureText(companyName).width;
        if (w<radius*2/3) {
            context.font="36px Monospace";
            shift = 36;
        } else if(w<radius*1.6) {
            context.font="24px Monospace";
            shift = 24;
        } else if(w<radius*3.5) {
            context.font="18px Monospace";
            shift = 18;
        }
        context.wrapText(companyName, centerX - radius, centerY - blockH/2+shift*1.27, radius*2, shift*1.4);
        return canvas.toDataURL("image/png");
    },

    showSignInfo:function(){
        /** @namespace {Object} this.signInfo */
        /** @namespace this.signInfo.verifyTime */
        /** @namespace this.signInfo.cert */
        /** @namespace this.signInfo.cert.serialNumber */
        /** @namespace this.signInfo.cert.subject */
        /** @namespace this.signInfo.cert.subject.INN */
        /** @namespace this.signInfo.cert.subject.CN */
        /** @namespace this.signInfo.cert.subject.O */
        /** @namespace this.signInfo.cert.subject.ST */
        /** @namespace this.signInfo.cert.subject.T */
        /** @namespace this.signInfo.cert.subject.L */
        /** @namespace this.signInfo.cert.subject.EDRPOU */
        /** @namespace this.signInfo.cert.issuer */
        /** @namespace this.signInfo.cert.issuer.CN */
        /** @namespace this.signInfo.cert.issuer.O */
        /** @namespace this.signInfo.cert.issuer.SN */
        /** @namespace this.signInfo.notValidBefore */
        /** @namespace this.signInfo.notValidAfter */
        var cert = this.signInfo.cert;

        var certInfo = "<br><br><br>";
        if (cert.isStamp){
            certInfo += "<span class='center'>СЕРТИФІКАТ ПЕЧАТКИ";
        } else {
            certInfo += "<span class='center'>СЕРТИФІКАТ EЦП";
        }

        if (this.signInfo.ocsp != null && this.signInfo.ocsp.status != "good" && this.signInfo.ocsp.status != "unknown") {
            certInfo += ",  <b>відкликано " + new Date(this.signInfo.ocsp.revocationTime * 1000).toDatastampString();
            certInfo += this.signInfo.ocsp.revocationReason ? " [" + (cpGUI.revokeReasons.hasOwnProperty(this.signInfo.ocsp.revocationReason.toLowerCase()) ? cpGUI.revokeReasons[this.signInfo.ocsp.revocationReason.toLowerCase()] : this.signInfo.ocsp.revocationReason) + "]</b>" : "</b>";
            certInfo += "</span>";
        } else if (this.signInfo.ocsp == null || this.signInfo.ocsp.status == "unknown") {
            if(cpGUI.coreCert.hasOwnProperty(cert.issuer.SN)) {
                certInfo += " <b>[НЕВІДОМИЙ СТАТУС]</b></span><br><span class='center'>Дійсність сертифікату необхідно перевірити особисто</span>";
            } else {
                certInfo += " <b>[ВИДАНИЙ НЕ АЦСК]</b></span><br><span class='center'>Сертифікат отриман не через АЦСК та не має юридичної сили</span>";
            }
        } else {
            certInfo += "</span>";
        }
        certInfo+= "<br><br>";

        if(!this.signInfo.time || this.signInfo.time<cert.notValidBefore || this.signInfo.time>cert.notValidAfter ){
            if (this.signInfo.time){
                certInfo+="<span class='errorInfo'>";
            } else {
                certInfo+="<span class='warnInfo'>";
            }

        }
        certInfo+="Термін дії: з <span class='info'>"+new Date(cert.notValidBefore * 1000).toTimestampString()+"</span> до <span class='info'>"+new Date(cert.notValidAfter * 1000).toTimestampString()+"</span>";

        if(!this.signInfo.time || this.signInfo.time<cert.notValidBefore || this.signInfo.time>cert.notValidAfter ){
            if (!this.signInfo.time){
                certInfo+=" <b>Підпис не містить дату</b></span><br>";
            } else {
                certInfo+="</span><br><br><span class='errorInfo'> <b>Час підпису: "+new Date(this.signInfo.time * 1000).toTimestampString()+"</b>";
                certInfo+= (this.signInfo.tspValue ? " [час з підписом АЦСК]</span><br>" : " [вказано клієнтом]")
            }
        } else {
            certInfo+="<br>Час підпису: <span class='info'>"+new Date(this.signInfo.time * 1000).toTimestampString()+"</b></span>";
            certInfo+= (this.signInfo.tspValue ? " [час з підписом АЦСК]" : " [вказано клієнтом]");
            certInfo+= "</span><br>";
        }
        if (cert.isStamp){
            if (typeof  cert.subject.SRN != "undefined" && typeof  cert.subject.GN != "undefined" && cert.subject.SRN && cert.subject.GN){
                certInfo+="<br>Власник печатки: <span class='info'>"+cert.subject.SRN+" "+cert.subject.GN+"</span><br>";
            }
        } else {
            certInfo+="<br>Повне ім'я: <span class='info'>"+cert.subject.CN+"</span><br>";
        }
        if (cert.subject.INN){
            certInfo+="ІНН: <span class='info'>"+cert.subject.INN+"</span><br>";
        }
        if (cert.subject.EDRPOU){
            certInfo+="ЄДРПОУ: <span class='info'>"+cert.subject.EDRPOU+"</span><br>";
        }
        if (cert.subject.O){
            certInfo+="Організація: <span class='info'>"+cert.subject.O+"</span><br>";
        }
        if (cert.subject.T && !cert.isStamp){
            certInfo+="Посада: <span class='info'>"+cert.subject.T+"</span><br>";
        }
        if (cert.subject.L) {
            certInfo += "Місто: <span class='info'>" + cert.subject.L + "</span><br>";
        }
        certInfo+="SN: <span class='info'>"+cert.serialNumber+"</span><br><br>";

        certInfo+="<span class='center'>ВИДАВЕЦЬ СЕРТИФІКАТУ</span><br>";
        if (cert.subject.CN) {
            certInfo += "АЦСК: <span class='info'>" + cert.issuer.CN + "</span><br>";
        }
        if (cert.subject.O) {
            certInfo += "Організація: <span class='info'>" + cert.issuer.O + "</span><br>";
        }
        if (cert.subject.L) {
            certInfo += "Місто: <span class='info'>" + cert.issuer.L + "</span><br>";
        }
        if (cert.subject.SN) {
            certInfo += "SN сертифіката видавця: <span class='info'>" + cert.issuer.SN + "</span><br>";
        }
        certInfo+="<br>";
        var signInfo = buildNode("DIV",{
            className:"certDetails",
            style:{
                width:"800px",
                height:"562px",
                background:"url('/img/cert.png')",
                color:(this.signInfo.ocsp!=null
                    && this.signInfo.ocsp.status!="good"
                    && this.signInfo.ocsp.status=="unknown"
                    && this.signInfo.time || this.signInfo.ocsp!=null
                    && this.signInfo.time && this.signInfo.ocsp.revocationTime != null
                    && this.signInfo.time > this.signInfo.ocsp.revocationTime
                )?"#f44336":(this.signInfo.ocsp==null || this.signInfo.ocsp.status == "unknown")?"#ff6d00":"#5c9d21"
            }
        },certInfo);
        var btns = buildNode("SPAN", {className:"center"},[
            buildNode("A",{data:b64toBlob(this.signInfo.signCertificate)},"<i class='fa fa-download'></i> Завантажити сертификат",{click:function(){
                saveAs(this.data,cert.serialNumber+".cer");
            }}),
            buildNode("A",{style:{marginLeft:"20px"},data:actionDoc.renderTextSignInfo(this.signInfo, actionDoc.getDocument(userConfig.docId), this.signInfo.verifyTime,false)},"<i class='fa fa-pencil-square-o'></i> Завантажити iнфо про підпис для друку", {click:function(){
                saveAs(new Blob([(userConfig.browserInfo.os == "windows"?this.data.replace(/\n/g,"\r\n"):this.data)], {type: "text/plain;charset=utf-8"}), "Деталі_підпису_документ_№"+userConfig.docId+"_"+cert.subject.CN.replace(/\s/g, "_").replace (/[,|<>\?\*\/\\:]/g, "")+".txt")
            }})
        ]);
        signInfo.appendChild(btns);
        drawMessage(signInfo,800,true);
    },

    renderTextSignInfo:function(signInfo, doc, verifyTime, shortInfo){
        if(!doc || !signInfo){
            return "";
        }
        var docHash = base64ToHex(doc.hash).toUpperCase();
        var signHash = base64ToHex(signInfo.hash).toUpperCase();
        var result="";
        if (!shortInfo) {
            result += "Документ: " + doc.name + "\n";
            result += "    Розмір документа: " + doc.size + " Байт\n";
            result += "    Геш значення документа:\n        " + docHash + "\n";
        } else {
            if (signInfo.cert.isStamp){
                result+="Електронна печатка: " + signInfo.cert.subject.CN +"\n";
                if (typeof  signInfo.cert.subject.SRN != "undefined" && typeof  signInfo.cert.subject.GN != "undefined" && signInfo.cert.subject.SRN && signInfo.cert.subject.GN){
                    result+="    Власник печатки: " + signInfo.cert.subject.SRN+" "+signInfo.cert.subject.GN+"\n";
                }
            } else {
                result+="Повне ім'я власника: " + signInfo.cert.subject.CN +"\n";
            }

            if (signInfo.cert.subject.INN && signInfo.cert.subject.INN.length > 0){
                result+="    Ідентифікаційний номер: " + signInfo.cert.subject.INN +"\n";
            }
            if (signInfo.cert.subject.O && signInfo.cert.subject.O.length > 0){
                result+="    Організація: " + signInfo.cert.subject.CN +"\n";
            }
            if (signInfo.cert.subject.EDRPOU && signInfo.cert.subject.EDRPOU.length > 0){
                result+="    ЄДРПОУ: " + signInfo.cert.subject.EDRPOU +"\n";
            }
            if (signInfo.cert.subject.ST && signInfo.cert.subject.ST.length > 0){
                result+="    Область: " + signInfo.cert.subject.ST +"\n";
            }
            if (signInfo.cert.subject.L && signInfo.cert.subject.L.length > 0){
                result+="    Місто: " + signInfo.cert.subject.L +"\n";
            }
        }

        result += "Результат перевірки ЕЦП: ";
        if (signInfo.verifyResult.indexOf("valid")==0) {
            if (docHash!=signHash){
                result += "УВАГА! Геш значення підпису не відповідає геш значенню документа! Підпис належить іншому документу\n"
            } else if (typeof signInfo.time == "undefined") {
                result += "УВАГА! Підпис вірний, але не містить час підписання!\n";
            } else if (typeof signInfo.ocsp != "undefined" && signInfo.ocsp) {
                if (signInfo.ocsp.status == "good" || (signInfo.ocsp.revocationTime > signInfo.time)) {
                   result += "Підпис вірний\n";
                } else {
                    result += "УВАГА! Підпис не має сили. Сертифікат відкликано до підписання докумена\n";
                }
            } else {
                result += "УВАГА! Підпис вірний, але неможливо перевірити сертифікат, особисто перевірте його по серійному номеру!\n";
            }
        } else {
            result += "УВАГА! Підпис помилковий!\n";
        }
        if (signInfo.time){
            result += "    Час формування ЕЦП: " + new Date(signInfo.time*1000).toTimestampString() + "\n";
            result += "        Тип позначки часу: " + (signInfo.tspValue ? "з підписом АЦСК" : "вказано клієнтом") + "\n";
        } else {
            result += "    Час формування ЕЦП: НЕВІДОМО\n";
        }
        result += "    Геш значення підписаного документа:\n        " + signHash + "\n";
        if (typeof verifyTime != "undefined") {
            result += "    Час перевірки ЕЦП: " + new Date(verifyTime).toTimestampString() + "\n"
        }
        if (typeof signInfo.ocsp != "undefined" && signInfo.ocsp){
            //noinspection JSValidateTypes
            if (signInfo.ocsp.status == "good"){
                result += "Статус перевірки сертифікату: Cертифікат діє\n";
            } else {
                result += "Статус перевірки сертифікату: Cертифікат відкликано\n";
                result += "    Дата відликання: "+new Date(signInfo.ocsp.revocationTime*1000).toTimestampString()+"\n";
                result += "    Причина відликання: "+cpGUI.revokeReasons[signInfo.ocsp.revocationReason]+"\n";
            }
            if (!shortInfo) {
                result += "    Підпис сервісу OCSP: " + (signInfo.ocsp && signInfo.ocsp.hasOwnProperty("OCSPRespSign") && signInfo.ocsp.OCSPRespSign.indexOf("valid") == 0 ? "Підпис вірний" : "УВАГА! Підпис помилковий!") + "\n";
            }
        } else {
            result += "Увага! Неможливо перевірити сертифікат, особисто перевірте його по серійному номеру!\n";
        }

        if (!shortInfo) {
            result += "Байти підпису:\n    " + base64ToHex(signInfo.sign).toUpperCase().match(/.{1,76}/g).join("\n    ") + "\n";
        }
        return result;
    },
    updateCoreSearch: function(){
        var list = [];
        var newCount = 0;
        var friendFilter=[];
        for (var d in userConfig.docs) {
            if (userConfig.docs.hasOwnProperty(d)) {
                var doc = userConfig.docs[d];
                if (doc.author!= userConfig.login && friendFilter.indexOf(doc.author)==-1){
                    friendFilter.push(doc.author);
                }
                if (doc.shares){
                    for (var u in doc.shares){
                        if (doc.shares.hasOwnProperty(u) && friendFilter.indexOf(u)==-1){
                            friendFilter.push(u);
                        }
                    }
                }
                if (doc.author != userConfig.login && doc.status<2){
                    newCount++;
                }
                list.push([doc.name, doc.status < 10 ? 2 : 0, (doc.status < 10 ? "" : "[В корзине] ") + (doc.author == userConfig.login ? "мой" : (userConfig.friends.hasOwnProperty(doc.author)&&userConfig.friends[doc.author]!=""?(userConfig.friends[doc.author]+" "+doc.author): doc.author)) + " " + new Date(doc.time).toDatastampString(),doc.id]);
            }
        }
        byId("docCount").innerHTML=(newCount>0 && userConfig.company.login == userConfig.login)?" ("+newCount+")":"";

        /*var friendBlock = "<option>Будь хто</option>";
        foreach(friendFilter,function(f){
            friendBlock+="<option>"+f+"</option>";
        });

        byId("option-four").innerHTML=friendBlock;*/
        actionDoc.initContractorSearch();
    },
    initContractorSearch : function(){
        var mainArr = actionDoc.buildAutocompleteList(),
            target = byId('contractorSearch'),
            keys = {
                valueKeys : ['email'],
                renderKeys : ['email', 'name', 'company']
            },
            fancyAutocomplete;
        if(target){
            fancyAutocomplete = new FancyAutocomplete(mainArr, target, keys);
        }
    },
    buildAutocompleteList : function(){ //fixme refactor, friendsFilter seems obsolete
        var friendFilter = [],
            friendsFilter = [],
            res = [],
            friendsObj = JSON.parse(JSON.stringify(userConfig.friends)),
            temp,
            length,
            indexOf,
            newCount,
            list = [];
        for (var d in userConfig.docs) {
            if (userConfig.docs.hasOwnProperty(d)) {
                var doc = userConfig.docs[d];
                if (doc.author!= userConfig.login && friendFilter.indexOf(doc.author)==-1){
                    friendFilter.push(doc.author);
                }
                if (doc.shares){
                    for (var u in doc.shares){
                        if (doc.shares.hasOwnProperty(u) && friendFilter.indexOf(u)==-1){
                            friendFilter.push(u);
                        }
                    }
                }
                if (doc.author != userConfig.login && doc.status<2){
                    newCount++;
                }
                list.push([doc.name, doc.status < 10 ? 2 : 0, (doc.status < 10 ? "" : "[В корзине] ") + (doc.author == userConfig.login ? "мой" : (userConfig.friends.hasOwnProperty(doc.author)&&userConfig.friends[doc.author]!=""?(userConfig.friends[doc.author]+" "+doc.author): doc.author)) + " " + new Date(doc.time).toDatastampString(),doc.id]);
            }
        }
        friendsObj[userConfig.login] = userConfig.fullName; //сам себе "друг"
        for(temp in friendsObj){
            indexOf = friendsFilter.indexOf(temp);
            if(indexOf != -1){ // удаляет из friendsFilter если есть в друзьях
                friendsFilter.splice(indexOf, 1);
            }
            res.push({
                email : temp,
                name : friendsObj[temp],
                company : actionDoc.getCompanyNameByEmail(temp)
            })
        }
        for(temp = 0, length = friendsFilter.length; temp < length; temp++){ //массив значений без имени
            res.push({
                email : friendsFilter[temp],
                name : '',
                company : ''
            });
        }
        return res;
    },
    getCompanyNameByEmail : function(email){
        var friendsCompany = userConfig.friendsCompany,
            i, length,
            res = '';
        for(i = 0, length = friendsCompany.length; i < length; i++){
            if(friendsCompany[i].emails.indexOf(email) != -1){
                res += friendsCompany[i].name+', ';
            }
        }
        res = res.replace(/,\s$/, '');
        return res;
    },
    /**
     * Load all documents from DB
     */
    loadDocuments: function (callback) {
        userConfig.docs=[];
        var searchUrl = "/api/resource/search";
        if (userConfig.company.login=="all"){
            searchUrl = "/api/company/search/"+userConfig.company.companyId;
        }
        ajax(searchUrl, function (result) {
            userConfig.docs = JSON.parse(result);
            actionDoc.updateCoreSearch();
            if (location.pathname.indexOf("/doc/") == 0) {
                var index = parseInt(location.pathname.substr(5));
                if (index > 0) {
                    history.replaceState({"renderType": "doc", "docId": index}, "", "/doc/" + index);
                    /*window.documentsCollection.renderDocument(index);*/
                } else {
                    history.replaceState({renderType: "list", docList: userConfig.docList}, "", "/list/" + userConfig.docList);
                    /*window.documentsCollection.renderDocuments();*/
                }
            } else {
                history.replaceState({renderType: "list", docList: userConfig.docList}, "", "/list/" + userConfig.docList);
                /*window.documentsCollection.renderDocuments();*/
            }
            if(typeof callback === "function"){ /*fixme need to return promise*/
                callback();
            }
        }, "{}", function (code) {
            if (code == 403) {
                actionCore.logoutAction();
            } else {
                drawMessage("<i class='fa fa-exclamation-triangle fa-2'></i> Не вдалося завантажити документи користувача, помилка " + code);
            }
        })
    },
    openSearch: function (docName) {
        if(typeof docName === "number"){
            foreach(userConfig.docs, function (doc) {
                if(!doc){return;}
                if (doc.id == docName) {
                    history.pushState({"renderType": "doc", "docId": doc.id}, "", "/doc/" + doc.id);
                    window.documentsCollection.renderDocument(doc.id);
                    if(byId("search")){
                        byId("search").value = "";
                    }
                    return false;
                }
            });
        } else {
            foreach(userConfig.docs, function (doc) {
                if(!doc){return;}
                if (doc.name == docName) {
                    history.pushState({"renderType": "doc", "docId": doc.id}, "", "/doc/" + doc.id);
                    window.documentsCollection.renderDocument(doc.id);
                    if(byId("search")){
                        byId("search").value = "";
                    }
                    return false;
                }
            });
        }
    },

    /**
     * Delete selected document
     */
    deleteDocument: function () {
        var temp;
        history.replaceState({}, "", "/");
        if (this.docId) {
            temp = this.docId;
            if(window.documentsCollection.fullCollection){
                window.documentsCollection.deleteDocument(temp);
            }
            ajax("/api/resource/" + this.docId, function () {
                history.replaceState({renderType: "list", docList: userConfig.docList}, "", "/list/" + userConfig.docList);
                if(!window.documentsCollection.fullCollection){
                    window.documentsCollection.deleteDocument(temp);
                }
                var dId = this.docId;
                drawChangeInfo("Документ був видалений", function(){
                    var id = dId;
                    return function(){
                        ajax("/api/resource/restore/" + id, function () {
                            window.documentsTrashCollection.restoreDocument(id);
                        },"", function(){},"PUT");
                    }
                }(), 5000);
            }, "", function(){}, "DELETE");
        } else {
            var docs = [];
            foreach(byClass(byId("contentBlock"), "selected", "TR"), function (el) {
                docs.push(el.getAttribute('docId'));
            });
            if(window.documentsCollection.fullCollection){
                window.documentsCollection.deleteDocument(docs);
            }
            ajax("/api/resource/" + docs.join("_"), function () {
                history.replaceState({renderType: "list", docList: userConfig.docList}, "", "/list/" + userConfig.docList);
                if(!window.documentsCollection.fullCollection){
                    window.documentsCollection.deleteDocument(docs);
                }
                if (userConfig.docList=="trash"){
                    drawChangeInfo("Видалено документів: "+docs.length, null, 5000);
                } else {
                    drawChangeInfo("Відправлено у кошик документів: "+docs.length, function(){
                        var id = docs.join("_");
                        return function(){
                            window.documentsTrashCollection.restoreDocument(docs);
                            ajax("/api/resource/restore/" + id, function () {
                            },"", function(){},"PUT");
                        }
                    }(), 5000);
                }
            }, "", function(){}, "DELETE");
        }
    },
    /**
     * Restore selected document
     */
    restoreDocument: function () {
        var docs = [];
        foreach(byClass(byId("contentBlock"), "selected", "TR"), function (el) {
            docs.push(el.getAttribute('docId'));
        });
        ajax("/api/resource/restore/" + docs.join("_"), function () {
            window.documentsTrashCollection.restoreDocument(docs);
        }, "", function(){}, "PUT");
    },


    renderTagMenu: function(){
        var list = byId("docTagFilterList");
        list.innerHTML="";
        var tagList = userConfig.tagList;
        foreach(tagList, function(tagInfo){
            var tag = buildNode("DIV",{tagId: tagInfo.id, className:"tagFilter"+((tagInfo.id==userConfig.tagFilter)?" selected":"")},
                "<div class='tagBlock' style='background: "+(tagInfo.id==null?"#a1a1a1":actionDoc.tagColors[tagInfo.color])+"'></div><span>"+ tagInfo.text+"</span>",{
                    click:function(){
                        foreach(byClass(byId("docTagFilterList"),"tagFilter"),function(el){
                            rmClass(el, "selected");
                        });
                        addClass(this, "selected");
                        userConfig.tagFilter = this.tagId;
                        userConfig.docList = 'tag';
                        var menuBtns = byClass(byId("mainMenu"), "pure-menu-item");
                        addClass(menuBtns[0], "active");
                        rmClass(menuBtns[1], "active");
                        history.replaceState({renderType: "list", docList: userConfig.docList, tagFilter : userConfig.tagFilter}, "", "/list/tag/" + this.tagId);
                        window.documentsCollection.offset = 0;
                        window.documentsCollection.renderDocuments();
                    }
                });
            list.appendChild(tag);
        });
    },

    /**
     * Render document list by general list
     */
    renderDocumentList: function (documentsList) {
        var docs = [];
        userConfig.doc = null;
        if (byId("docTagFilterList").innerHTML==""){
            actionDoc.renderTagMenu();
        }
        rmClass(byId("viewControl"), "hide");
        rmClass(byId("docFilter"), "hide");
        byId("contentBlock").innerHTML="";
        //rmClass(byClass(byId("mainMenu"),"pure-menu-item")[3], "hide");
        //byId("docCount").innerHTML = "(" + userConfig.documents.length + ") ";
        userConfig.viewMode = "list";
        userConfig.docId = null;
        documentsList = documentsList || actionDoc.getDocumentsByGroup(userConfig.docList);
        var count = 0;
        var newCount = 0;
        if(documentsList && Array === documentsList.constructor){

            documentsList.sort(function (a, b) {
                if (a.time > b.time) {
                    return -1;
                } else if (a.time < b.time) {
                    return 1;
                } else {
                    return 0;
                }
            });
            foreach(documentsList, function (doc) {
                if(!doc){return;}
                /** @namespace doc.id */
                /** @namespace doc.name */
                /** @namespace doc.hash */
                /** @namespace doc.author */
                /** @namespace doc.deleteByOwner */
                /** @namespace doc.time */
                /** @namespace doc.shares */
                if (doc.status % 10 < 2 && doc.author != userConfig.login) {
                    ++newCount;
                }
                if (userConfig.docRender === "table") {
                    var names = "<div class='usersList'>";
                    var statuses = ["Ще не підключився", "Ще не переглянув", "Переглянув", "Підписав"];
                    var statusesImage = ["<i class='fa fa-1 fa-question' style='font-size:10px;color:#f44336'></i>&nbsp;", "<i class='fa fa-1 fa-eye-slash' style='color:#ab47bc'></i>&nbsp;", "<i class='fa fa-1 fa-eye' style='color:#42a5f5'></i>&nbsp;", "<i class='fa fa-1 fa-pencil'  style='color:#5c9d21'></i>&nbsp;", "<i class='fa fa-1 fa-pencil' style='color:#ddd'></i>&nbsp;"];
                    var s;
                    if (doc.author == userConfig.login && userConfig.company.login == userConfig.login) {
                        for (s in doc.shares) {
                            if (doc.shares.hasOwnProperty(s)) {
                                names += "<span style='margin-right: 10px' title='" + statuses[doc.shares[s] % 10] + "'>" + statusesImage[doc.shares[s] % 10] + (userConfig.friends.hasOwnProperty(s) && userConfig.friends[s] != "" ? userConfig.friends[s] : s) + "</span>";
                            }
                        }
                    } else {
                        if (doc.author != userConfig.company.login) {
                            names += "<span title='" + (doc.deleteByOwner ? "Видалено автором" : "Автор") +
                                "' style='margin-right: 10px'><i class='fa fa-paw' style='color:" +
                                (doc.deleteByOwner ? "#ef5350" : "#dce775") +
                                "'></i>&nbsp;" + (userConfig.friends.hasOwnProperty(doc.author) && userConfig.friends[doc.author] != "" ? userConfig.friends[doc.author] : doc.author) + "</span>";
                        }
                        for (s in doc.shares) {
                            if (doc.shares.hasOwnProperty(s)) {
                                if (s != userConfig.login) {
                                    names += "<span style='margin-right: 10px' title='" + statuses[doc.shares[s] % 10] + "'>" + statusesImage[doc.shares[s] % 10] + (userConfig.friends.hasOwnProperty(s) && userConfig.friends[s] != "" ? userConfig.friends[s] : s) + "</span>";
                                } else {
                                    doc.signed = doc.shares[s] % 10 == 3;
                                }
                            }
                        }
                    }
                    names += "</div>";
                    var row = buildNode("TR", {
                        docId: doc.id,
                        className: "tableDocument" + ((doc.author != userConfig.company.login && doc.status < 2 && userConfig.company.login != "all" && userConfig.login === userConfig.company.login) ? " newDocument" : "")
                    });
                    row.appendChild(buildNode("TD", {}, "<i class='fa checkbox'></i>", {click: actionDoc.tableClickHandler}));

                    //tags
                    var tags = "";
                    if (userConfig.company.login == userConfig.login) {
                        for (var t  in userConfig.tagList) {
                            if (userConfig.tagList.hasOwnProperty(t)) {
                                var tagInfo = userConfig.tagList[t];
                                if (doc.tags >> tagInfo.id & 0x01 == 1) {
                                    tags += "<div class='tag' style='background:" + actionDoc.tagColors[tagInfo.color] + "'></div>";
                                }
                            }
                        }
                    }
                    row.appendChild(buildNode("TD", {className: "tableData"}, tags, {click: actionDoc.tableClickHandler}));
                    //var docNameWidth = byId("contentBlock").offsetWidth - 580;
                    row.appendChild(buildNode("TD", {
                        className: "tableData",
                        title: (doc.signed ? 'Ви підписали документ' : 'Документ не підписан')
                    }, (doc.signed ? statusesImage[3] : statusesImage[4]) + "&nbsp;<span class='docName'>" + doc.name + "</span>", {click: actionDoc.tableClickHandler}));
                    row.appendChild(buildNode("TD", {className: "tableData"}, names, {click: actionDoc.tableClickHandler}));
                    row.appendChild(buildNode("TD", {
                        title: new Date(doc.time).toTimestampString()
                    }, (new Date(doc.time).toDatastampString() == new Date().toDatastampString() ? new Date(doc.time).toTimeString() : new Date(doc.time).toDatastampString()), {click: actionDoc.tableClickHandler}));
                    docs.push(row);
                    count++;
                } else {
                    var docMenu = buildNode("DIV", {className: "docMenu"});
                    docMenu.appendChild(buildNode("DIV", {className: "docInfo"}, [
                        buildNode("SPAN", {
                            className: "docDate",
                            title: new Date(doc.time).toTimestampString()
                        }, new Date(doc.time).toDatastampString()),
                        buildNode("SPAN", {className: "docAuthor"}, doc.author),
                        buildNode("SPAN", {className: "docName", title: doc.name}, doc.name)
                    ]));
                    docMenu.appendChild(buildNode("DIV", {className: "docControl"}, [
                        //buildNode("BUTTON", {
                        //    className: "pure-button",
                        //    title: "Відкрити доступ за лінком"
                        //}, "<i class='fa fa-link fa-lg'></i>", {"click": actionDoc.addSharedPeople}),
                        buildNode("A", {
                            className: "pure-button",
                            href: userConfig.cdnPath + doc.src + "/" + doc.hash,
                            download: doc.name + ".pdf",
                            title: "Завантажити оригінальний документ"
                        }, "<i class=' fa fa-download fa-lg'></i>"),
                        buildNode("BUTTON", {
                            docId: doc.id,
                            className: "pure-button",
                            title: "Перекласти документ у кошик"
                        }, "<i class='fa fa-trash fa-lg'></i>", {"click": actionDoc.deleteDocument})
                    ]));

                    var docPrev = buildNode("DIV", {
                        className: "docImg",
                        style: {"background-image": "url(\"" + (doc.type == 0 ? (userConfig.cdnPath + doc.src + "/" + doc.hash + userConfig.imgEnd) : "/img/pdf-password.png") + "\")"}
                    }, doc.type == 0 ? null : "<br>Зашифровано паролем", {click: actionDoc.blockClickHandler});
                    docs.push(buildNode("DIV", {
                        docId: doc.id,
                        className: "document" + ((doc.author != userConfig.login && userConfig.company.login == userConfig.login && doc.status < 2 && userConfig.login === userConfig.company.login) ? " newDocument" : ""),
                        title: doc.name
                    }, [docMenu, docPrev]));
                    count++;
                }
            });
            if (userConfig.docRender === "table") {
                byId("contentBlock").innerHTML = "";

                var docMenuNode = buildNode("DIV", {id: "openDocMenu"});
                docMenuNode.appendChild(buildNode("BUTTON", {
                    className: "pure-button floatLeft",
                    style: "width:40px",
                    title: "Выбрать все"
                }, "<i class='fa checkbox big'></i>", {click: actionDoc.selectAll}));
                docMenuNode.appendChild(buildNode("BUTTON", {className: "pure-button floatLeft hideTop"}, "<i class='fa fa-trash'></i>" + (userConfig.docList == "trash" ? " Видалити назавжди" : " У кошик"), {
                    click: function () {
                        this.disabled = true;
                        actionDoc.deleteDocument.call(this);
                    }
                }));
                if (userConfig.docList == "trash") {
                    docMenuNode.appendChild(buildNode("BUTTON", {className: "pure-button floatLeft hideTop"}, "<i class='fa fa-plus-circle'></i>" + " Відновити", {click: actionDoc.restoreDocument}));
                } else {
                    docMenuNode.appendChild(buildNode("DIV", {id: "tagListSetter", className: "card-1 hide"}, [
                        buildNode("DIV", {})]));
                    docMenuNode.appendChild(buildNode("BUTTON", {
                            docId: null,
                            className: "pure-button floatLeft hideTop",
                            title: "Встановити ярлики для документа"
                        },
                        "<i class='fa fa-tags'></i> Ярлики", {click: actionDoc.setTags}));
                    docMenuNode.appendChild(buildNode("BUTTON", {
                            docId: null,
                            className: "pure-button floatLeft hideTop",
                            title: "Надіслати запрошення для перегляду або підписання документа"
                        },
                        "<i class='fa fa-user-plus fa-lg'></i> Надати доступ", {click: actionDoc.addSharedPeople}));
                    docMenuNode.appendChild(buildNode("BUTTON", {className: "pure-button floatLeft hideTop"}, "<i class='fa fa-pencil-square-o'></i> Підписати", {click: actionDoc.signDoc}));
                    docMenuNode.appendChild(actionDoc.buildShareMenu(false));

                }
                //docMenuNode.appendChild(buildNode("BUTTON", {className: "pure-button"}, "<i class='fa fa-chevron-left fa-lg'></i>"));
                if (count > 0) {
                    byId("contentBlock").appendChild(docMenuNode);
                } else {
                    byId("contentBlock").appendChild(buildNode("DIV", {className: "emptyBlock"}, actionDoc.getEmptyDescriptionByGroup(userConfig.docList)))
                }
                byId("contentBlock").appendChild(buildNode("TABLE", {className: "pure-table pure-table-horizontal"}, docs));
            } else {
                byId("contentBlock").innerHTML = "";
                if (count == 0) {
                    byId("contentBlock").appendChild(buildNode("DIV", {className: "emptyBlock"}, actionDoc.getEmptyDescriptionByGroup(userConfig.docList)))
                }
                for (var d in docs) {
                    if (docs.hasOwnProperty(d)) {
                        byId("contentBlock").appendChild(docs[d]);
                    }
                }
            }
            if (userConfig.docList == "me" && count == 0) {
                var cat = byClass(byId("contentBlock"), "helpCat")[0];
                addClass(cat, "slideInUp");
                rmClass(cat, "hide");
                setTimeout(function () {
                    rmClass(byClass(byId("contentBlock"), "helpCatInfo")[0], "hide");
                }, 2000);
            }
            if (userConfig.docList != "trash") {
                byId("docCount").innerHTML = (newCount > 0 && userConfig.company.login == userConfig.login) ? " (" + newCount + ")" : "";
            }
        } else {
            byId("contentBlock").appendChild(buildNode("DIV", {className:"emptyBlock"}, actionDoc.getEmptyDescriptionByGroup(userConfig.docList)));
        }
    },
    getEmptyDescriptionByGroup : function (group){
        var docs = actionDoc.getDocumentsByGroup(group, null, true);
        if(docs.length == 0 || group === 'trash'){
            if(userConfig.login != (userConfig.company || {}).login){
                return "Відсутні документи";
            }
            switch (group) {
                case "docs":
                    return "<div style='text-align: left'>1. Завантажте Ваш документ у форматі PDF<br>2. Підпишіть його за допомогою вашого ЕЦП.<br>3. Надайте доступ до цього документу Вашому контрагенту.<br><br>" + byId("youtubeBlock").innerHTML;
                    break;
                case "tag":
                    return "<div style='text-align: left'>1. Завантажте Ваш документ у форматі PDF<br>2. Підпишіть його за допомогою вашого ЕЦП.<br>3. Надайте доступ до цього документу Вашому контрагенту.<br><br>" + byId("youtubeBlock").innerHTML;
                    break;
                case "trash":
                    return "Ваш кошик порожній.";
                    break;
                default:
                    return "Відсутні документи";
                    break;
            }
        } else {
            var tempFullFlag = window.documentsCollection ? window.documentsCollection.fullCollection : false;
            return "За встановленими фільтрам нічого не знайдено" + (tempFullFlag ?  " з доступних "+docs.length+" документів." : '.');
        }
    },
    changeDocRender: function () {
        var button = byTag(byId("viewControl"), "BUTTON")[0];
        var image = byTag(button, "I")[0];
        if (userConfig.docRender == "blocks") {
            userConfig.docRender = "table";
            button.title = "У вигляді документів";
            rmClass(image, "fa-list-ul");
            addClass(image, "fa-th-large");
        } else {
            userConfig.docRender = "blocks";
            button.title = "У вигляді таблиці";
            rmClass(image, "fa-th-large");
            addClass(image, "fa-list-ul");
        }
        history.replaceState({renderType: "list", docList: userConfig.docList}, "", "/list/" + userConfig.docList);
        documentsCollection.models = documentsCollection.docReference.reduce(function(memo, el, i){ //TODO check performance, cache into LS?
            el.index = i;
            memo[el.id] = new DocumentsModelView(el);
            return memo;
        }, {});
        documentsCollection.renderDocuments();
        documentsCollection.toggleNavButtons();
    },

    changeSortType: function () {
        var button = byTag(byId("viewControl"), "BUTTON")[1];
        var image = byTag(button, "I")[0];
        if(!image){return;}
        if (userConfig.docSorter == "date") {
            userConfig.docSorter = "name";
            button.title = "Сортировка по даті";
            rmClass(image, "fa-sort-alpha-asc");
            addClass(image, "fa-sort-numeric-desc");
        } else {
            userConfig.docSorter = "date";
            button.title = "Сортировка по назві";
            rmClass(image, "fa-sort-numeric-desc");
            addClass(image, "fa-sort-alpha-asc");
        }
        history.replaceState({renderType: "list", docList: userConfig.docList}, "", "/list/" + userConfig.docList);
        window.documentsCollection.renderDocumentList();
    },

    addSharedPeople: function () {
        var docShareMenu = byClass(byId("openDocMenu"), "docShareMenu", "DIV")[0];
        docShareMenu.style.left = getOffset(this).left - 265 + "px";
        docShareMenu.style.top = getOffset(this).top + 40 + "px";
        if (hasClass(docShareMenu, "hide")) {
            rmClass(docShareMenu, "hide");
            byId("emailShareInput").focus();
        } else {
            addClass(docShareMenu, "hide");
        }
    },
    blockClickHandler: function () {
        history.pushState({"renderType": "doc", "docId": this.parentNode.getAttribute('docId')}, "", "/doc/" + this.parentNode.getAttribute('docId'));
        var length, i,
            id = this.parentNode.getAttribute('docId'),
            doc = window.documentsCollection.get(id);
        if((doc.get('author') != userConfig.login && userConfig.company.login == userConfig.login && doc.get('status') < 2 && userConfig.login === userConfig.company.login)){
            doc.attributes.status = 2;
            window.documentsCollection.changeDocReference();
            window.documentsCollection.saveToLocalStorage();
        }
        window.documentsCollection.renderDocument(this.parentNode.getAttribute('docId'));
    },

    tableClickHandler: function (e) {
        if (hasClass(this, "tableData")) {
            if(e.ctrlKey){
                window.open('/doc/'+this.parentNode.getAttribute('docId'));
                e.preventDefault();
                return false;
            }else{
                history.pushState({"renderType": "doc", "docId": this.parentNode.getAttribute('docId')}, "", "/doc/" + this.parentNode.getAttribute('docId'));
                var length, i,
                    id = this.parentNode.getAttribute('docId'),
                    doc = window.documentsCollection.get(id);
                if((doc.get('author') != userConfig.login && userConfig.company.login == userConfig.login && doc.get('status') < 2 && userConfig.login === userConfig.company.login)){
                    doc.attributes.status = 2;
                    window.documentsCollection.changeDocReference();
                    window.documentsCollection.saveToLocalStorage();
                }
                window.documentsCollection.renderDocument(this.parentNode.getAttribute('docId'));
            }

            return;
        }

        var self = this.parentNode;

        if (hasClass(self, "selected")) {
            actionDoc.selected = actionDoc.selected - 1;
            if(actionDoc.selected < 0){
                actionDoc.selected = 0;
            }
            rmClass(self, "selected");
        } else {
            actionDoc.selected = actionDoc.selected + 1;
            addClass(self, "selected");
        }
        var buttons = byTag(byId("openDocMenu"), "BUTTON");
        if (actionDoc.selected > 0) {
            rmClass(buttons[1], "hideTop");
            rmClass(buttons[2], "hideTop");
            rmClass(buttons[3], "hideTop");
            rmClass(buttons[4], "hideTop");
        } else {
            addClass(buttons[1], "hideTop");
            addClass(buttons[2], "hideTop");
            addClass(buttons[3], "hideTop");
            addClass(buttons[4], "hideTop");
        }
        if (actionDoc.selected == documentsCollection.renderCount) {
            addClass(byTag(byId("openDocMenu"), "I")[0], "selected");
        } else {
            rmClass(byTag(byId("openDocMenu"), "I")[0], "selected");
        }
    },

    selectAll: function () {
        var buttons = byTag(byId("openDocMenu"), "BUTTON"),
            temp;
        if (userConfig.docList === 'trash' ? documentsTrashCollection.renderCount == actionDoc.selected : documentsCollection.renderCount == actionDoc.selected) {
            rmClass(byTag(this, "I")[0], "selected");
            temp = document.querySelectorAll('.mainTableWrapper tr.selected');
            foreach(temp, function (el) {
                rmClass(el, "selected")
            });
            addClass(buttons[1], "hideTop");
            addClass(buttons[2], "hideTop");
            addClass(buttons[3], "hideTop");
            addClass(buttons[4], "hideTop");
            actionDoc.selected = 0;
        } else {
            addClass(byTag(this, "I")[0], "selected");
            temp = byTag(byId("contentBlock"), "TR");
            foreach(temp, function (el) {
                addClass(el, "selected")
            });
            rmClass(buttons[1], "hideTop");
            rmClass(buttons[2], "hideTop");
            rmClass(buttons[3], "hideTop");
            rmClass(buttons[4], "hideTop");
            actionDoc.selected = userConfig.docList === 'trash' ? documentsTrashCollection.renderCount : documentsCollection.renderCount;
        }
    },

    renameDoc: function () {
        if (this.doc.author != userConfig.login){
            return;
        }
        var dId = this.doc.id,
            oldName = window.documentsCollection.get(dId).get('name'),
            temp = window.documentsCollection.get(dId);
        if(temp){
            temp.attributes.name = byTag(byId("openDocMenu"), "INPUT")[0].value;
        }

        ajax("/api/resource/name/" + dId, function (text) {
            drawChangeInfo("Документ перейменований на '"+text+"'", function(){
                var id = dId;

                return function(){
                    ajax("/api/resource/name/" + id, function (text) {
                        window.documentsCollection.get(dId).attributes.name = text;
                        if (byId("openDocMenu")){
                            byTag(byId("openDocMenu"), "INPUT")[0].value = text;
                        } else {
                            window.documentsCollection.renderDocumentList();
                        }
                    },oldName,function(){},"PUT");
                }
            }(),10000);
            actionDoc.getDocument(dId).name = text;
        }, byTag(byId("openDocMenu"), "INPUT")[0].value, function () {
            byTag(byId("openDocMenu"), "INPUT")[0].value = oldName;
            window.documentsCollection.get(dId).attributes.name = oldName;
        }, "PUT");
        actionDoc.updateCoreSearch();
    },

    signDoc: function () {
        var docs = [];
        var hashs = [];
        if (this.docId) {
            docs.push(userConfig.doc);
        } else {
            hashs.documents = {};
            foreach(byClass(byId("contentBlock"), "selected", "TR"), function (el) {
                var doc = window.documentsCollection.get(el.getAttribute('docId')).attributes;
                if(doc) {
                    hashs.documents[doc.id] = doc.hash;
                    docs.push(doc);
                }
            });
        }

        foreach(docs, function (d) {
            if (d && hashs.indexOf(d.hash) == -1) {
                hashs.push(d.hash);
            }
        });

        cpGUI.signHash(hashs,
            function() {
                var usedDocs = docs;
                return function (resultSigns) {
                    drawMessage(loaderSnippet,120, false, 120);
                    var CMSList = [];
                    foreach(resultSigns, function (el) {
                        CMSList.push(el.sign);
                    });
                    cpGUI.verify(CMSList, function (signInfo) {
                        var newSignsCounter = 0;
                        var newSigns = {};
                        var lastWarnings = null;

                        for (var i = 0; i < signInfo.length; i++) {
                            if (signInfo[i].cert.notValidAfter * 1000 < Date.now()) {
                                if (signInfo.length == 1) {
                                    localStorage.removeItem("CryptoPluginKeyStore");
                                    drawWarning("Термін дії ключа закінчився " + new Date(signInfo[i].cert.notValidAfter * 1000).toDatastampString() + ".<br> Оберіть інший ключ");
                                    return;
                                } else {
                                    lastWarnings = "термін дії ключа закінчився " + new Date(signInfo[i].cert.notValidAfter * 1000).toDatastampString();
                                    continue;
                                }
                            }
                            if (typeof signInfo[i].ocsp != "undefined" && signInfo[i].ocsp && signInfo[i].ocsp.hasOwnProperty("status") && signInfo[i].ocsp.status != "good" && signInfo[i].ocsp.status != "unknown") {
                                if (signInfo.length == 1) {
                                    localStorage.removeItem("CryptoPluginKeyStore");
                                    drawWarning("Вибачте, Ваш сертифікат був відкликан АЦСК<br>" + new Date(signInfo[i].ocsp.revocationTime * 1000).toDatastampString() + ",<br>з причини: " + cpGUI.revokeReasons[signInfo[i].ocsp.revocationReason] + "<br>[SN:" + signInfo[i].cert.serialNumber + "]<br>Оберіть інший ключ для підпису");
                                    return;
                                } else {
                                    lastWarnings = "Ваш сертифікат був відкликан АЦСК " + new Date(signInfo[i].ocsp.revocationTime * 1000).toDatastampString() + ",<br>з причини: " + cpGUI.revokeReasons[signInfo[i].ocsp.revocationReason];
                                    continue;
                                }
                            }
                            var SN = "-";
                            if (signInfo[i].cert && signInfo[i].cert.issuer && signInfo[i].cert.issuer.hasOwnProperty("SN") && signInfo[i].cert.issuer.SN) {
                                SN = signInfo[i].cert.issuer.SN;
                            }
                            if (!cpGUI.coreCert.hasOwnProperty(SN)) {
                                ajax("/api/report/signError", function () {}, JSON.stringify(signInfo[i]), function () {});
                                localStorage.removeItem("CryptoPluginKeyStore");
                                var issuerDescription = "";
                                if (signInfo[i].cert && signInfo[i].cert.issuer && signInfo[i].cert.issuer.hasOwnProperty("CN") && signInfo[i].cert.issuer.CN) {
                                    issuerDescription = "<br>" + signInfo[i].cert.issuer.CN;
                                }
                                issuerDescription += "<br>[SN:" + SN + "]";
                                drawWarning("Вибачте, зараз ми не підтримуємо сертифікати видані"+issuerDescription+"<br>Проблема була передана розробнику для аналізу необхідності доопрацювання сервісу");
                                return;
                            }
                            for(var d in usedDocs){
                                if (usedDocs.hasOwnProperty(d) && signInfo[i].hash.replace(/\+/g,'-').replace(/\//g,'_').replace(/=/g,'') == usedDocs[d].hash){
                                    if(!newSigns.hasOwnProperty(usedDocs[d].id)){
                                        newSigns[usedDocs[d].id]=[];
                                    }
                                    newSignsCounter ++;
                                    newSigns[usedDocs[d].id].push(signInfo[i].sign);
                                }
                            }

                        }
                        byId("messageBG").click();
                        if (newSignsCounter > 0) {
                            if (lastWarnings) {
                                drawWarning("Деякі підписи не вдалось сформуваті, оскільки<br>" + lastWarnings);
                            }
                            if (Object.keys(newSigns).length>0) {
                                var docId = Object.keys(newSigns)[0];
                                ajax("/api/sign/"+userConfig.docId, function (listJSON) {
                                    listJSON = JSON.parse(listJSON);
                                    var result = {};
                                    if (isArray(listJSON)){
                                        result[docId]=listJSON;
                                    } else {
                                        result = listJSON;
                                    }
                                    for (var s in result) {
                                        if (result.hasOwnProperty(s)) {
                                            var temp = window.documentsCollection.get(s);
                                            if (temp) {
                                                temp.attributes.signed = true;
                                                window.documentsCollection.changeDocReference();
                                                window.documentsCollection.saveToLocalStorage();
                                                temp.render();
                                            }
                                            actionDoc.updateSignList(JSON.stringify(result[s]));
                                        }
                                    }
                                }, JSON.stringify(newSigns), function (e) {
                                    console.warn("Can't add sign", e);
                                });
                            }
                        } else {
                            if (lastWarnings) {
                                setTimeout(function () {
                                    drawWarning("Жодного підпису не вдалось сформуваті, оскільки<br>" + lastWarnings);
                                },0);
                            }
                        }
                        foreach(byTag(byId("contentBlock"), "TR"), function (el) {
                            rmClass(el, "selected")
                        });
                    });
                }
            }())
    },

    getDocument: function (id, callback) {
        if (userConfig.doc && userConfig.doc.id == id){
            // TODO: remove this hack for old logic, which load document by this function
            if (typeof callback == "function"){
                callback(userConfig.doc);
                return;
            } else {
                return userConfig.doc;
            }
        }
        var detect = null;
        if (typeof id != "undefined") {
            foreach(userConfig.docs, function (doc) {
                if(!doc){return;}
                if (doc.id == id) {
                    detect = doc;
                    return false;
                }
            });
        }
        if (typeof callback =="function"){
            callback(detect);
            return;
        } else {
            return detect;
        }
    },
    pushDocument: function (json) {
        try {
            var newDoc = JSON.parse(json);
            if (newDoc) {
                var replace = false;
                for (var doc in userConfig.docs) {
                    if (userConfig.docs.hasOwnProperty(doc) && userConfig.docs[doc].id == newDoc.id) {
                        userConfig.docs[doc] = newDoc;
                        replace = true;
                        break
                    }
                }
                if (!replace) {
                    userConfig.docs.push(newDoc)
                }
            }
            return newDoc.id;
        } catch (e) {
            console.warn("Can't parse new document from json " + json);
        }
    },
    renderTagManager:function(){
        function selectColor(){
            this.parentNode.parentNode.colorId=this.colorId;
            this.parentNode.parentNode.style.background = actionDoc.tagColors[this.colorId];
            this.parentNode.style.display="";
        }

        function deleteTag(){
            this.parentNode.parentNode.removeChild(this.parentNode);
            byId("message").style.height = byTag(byId("message"), "DIV")[0].offsetHeight - 5 + "px";
        }

        var manager = buildNode("DIV",{id:"tagManager"});
        manager.appendChild(buildNode("DIV",{style:{background: "#eee","text-align": "right"}}," Управління ярликами&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<div class='closeButton pure-button' onclick='byId(\"messageBG\").click()'>X</div>"));
        if(userConfig.tagList.length>0){
            manager.appendChild(buildNode("BR"));
            manager.appendChild(buildNode("SPAN",{className:"centeredTitle"},"Зміна існуючих ярликів"));
        }
        var usedColor=[];
        foreach (userConfig.tagList, function(tag){
            usedColor.push(tag.color);
            var colorCircle = buildNode("DIV",{className:"colorSelector"});
            foreach(actionDoc.tagColors,function(color,i){
                colorCircle.appendChild(buildNode("DIV",{colorId:i,style:{background:color}},"",{click:selectColor}));
            });
            manager.appendChild(buildNode("DIV",{tagId:tag.id, className:"tagFilter",style:{padding:"5px"}},[
                buildNode("DIV",{colorId:tag.color,className:"tagBlock",style:{background:actionDoc.tagColors[tag.color]}},colorCircle),
                buildNode("INPUT",{value:tag.text}),
                buildNode("BUTTON",{className:"pure-button", style:{float: "right"}, title:"Видалити ярлик"},"<i class='fa fa-trash'></i>",{click:deleteTag})
            ]))
        });
        var colorCircle = buildNode("DIV",{className:"colorSelector"});
        foreach(actionDoc.tagColors,function(color,i){
            colorCircle.appendChild(buildNode("DIV",{colorId:i,style:{background:color}},"",{click:selectColor}));
        });
        manager.appendChild(buildNode("BR"));
        var newColor=-1;
        if(usedColor.length<actionDoc.tagColors) {
            while (newColor == -1 || usedColor.indexOf(newColor) >= 0) {
                newColor = Math.floor(Math.random() * (actionDoc.tagColors.length))
            }
        } else {
            newColor = Math.floor(Math.random() * (actionDoc.tagColors.length));
        }
        manager.appendChild(buildNode("SPAN",{className:"centeredTitle"},"Додавання нового ярлика"));
        manager.appendChild(buildNode("DIV",{className:"tagFilter newTag", style:{padding:"5px"}},[
            buildNode("DIV",{colorId:newColor,className:"tagBlock",style:{background:actionDoc.tagColors[newColor]}},colorCircle),
            buildNode("INPUT",{value:""}),
            buildNode("BUTTON",{id:"addNewTagBtn",className:"pure-button", style:{float: "right"}, title:"Створити новий ярлик"},"<i class='fa fa-plus'></i>",{click:function(){
                if (byClass(byId("tagManager"),"tagFilter").length>=32){
                    byId("saveTagsList").click();
                    setTimeout(function(){drawWarning("Не можливо створити більше 32 ярликів");},50);
                    return;
                }
                var input = byTag(this.parentNode,"INPUT")[0];
                if(input.value ==""){
                    return;
                }
                var needAdd = true;
                foreach(byClass(byId("tagManager"),"tagFilter"),function(el){
                    if (!hasClass(el,"newTag")){

                        var d = byTag(el,"input");
                        if (d != null && d[0] != null && d[0].value == input.value){
                            needAdd = false;
                            input.value = "";
                            byTag(input.parentNode,"INPUT")[0].value="";
                            return false;
                        }
                    }
                });
                if (!needAdd) {
                    return;
                }
                var colorCircle = buildNode("DIV",{className:"colorSelector"});
                foreach(actionDoc.tagColors,function(color,i){
                    colorCircle.appendChild(buildNode("DIV",{colorId:i,style:{background:color}},"",{click:selectColor}));
                });
                var lastSpan = byTag(manager,"BR");
                lastSpan = lastSpan[lastSpan.length-4];
                manager.insertBefore(buildNode("DIV",{tagId:null, className:"tagFilter",style:{padding:"5px"}},[
                    buildNode("DIV",{colorId:byClass(this.parentNode,"tagBlock")[0].colorId,className:"tagBlock",style:{background:actionDoc.tagColors[byClass(this.parentNode,"tagBlock")[0].colorId]}},colorCircle),
                    buildNode("INPUT",{value:stripHTMLTags(input.value,46)}),
                    buildNode("BUTTON",{className:"pure-button", style:{float: "right"}, title:"Удалить ярлык"},"<i class='fa fa-trash'></i>",{click:deleteTag})
                ]), lastSpan);
                byId("message").style.height = byTag(byId("message"), "DIV")[0].offsetHeight - 5 + "px";
                input.value="";
            }})
        ]));

        manager.appendChild(buildNode("BR"));
        manager.appendChild(buildNode("BUTTON",{id:"saveTagsList", className:"pure-button"},"Зберегти",{click:function(){
            byId("addNewTagBtn").click();
            var tags = byClass(byId("tagManager"),"tagFilter");
            var newTags = [];
            var tagsIds = [];
            foreach(tags, function(t){
                if (hasClass(t,"newTag") || typeof t != "object"){
                    return;
                }
                if (t.tagId!=null){
                    tagsIds.push(t.tagId);
                }

                newTags.push({id:t.tagId, text:byTag(t,"INPUT")[0].value, color:byTag(t,"DIV")[0].colorId});
            });
            var replaceTags=[];
            var id=0;
            var uniqName = {};
            foreach(newTags, function(t){
                // add number to copy text.
                if(!uniqName.hasOwnProperty(t.text)){
                    uniqName[t.text]=0;
                } else {
                    uniqName[t.text]++;
                    t.text=t.text+uniqName[t.text];
                }
                if(t.id==null){

                    while(tagsIds.indexOf(id)>=0){
                        id++;
                    }
                    tagsIds.push(id);
                    replaceTags.push(id);
                    t.id=id;
                }
            });
            var update = 0;
            foreach(replaceTags, function(i){
                update += (1<<i);
            });

            ajax("/api/login/tags/"+update,function(){
                if (userConfig.docId){
                    actionDoc.getDocument(userConfig.docId,function(doc){
                        foreach(replaceTags,function(tag){
                            doc.tags |= (1 << tag);
                        });
                        ajax("/api/resource/tag/"+userConfig.docId,function(){},doc.tags,function(){},"PUT");
                    });
                }
            },JSON.stringify(newTags),function(){},"PUT");
            foreach(userConfig.docs,function(d){
                d.tags &= ~update;
            });
            userConfig.tagList = newTags;
            actionDoc.renderTagMenu();
            byId("messageBG").click();
            if (!userConfig.docId){
                window.documentsCollection.renderDocumentList();
            }
        }}));
        manager.appendChild(buildNode("BR"));
        manager.appendChild(buildNode("BR"));
        drawMessage(manager,280,true);
        byId("message").style.overflow="visible";

    },
    /**
     * Filter list of documents and get filtered list by group
     * @param group
     * @param callback
     * @param disableFilters
     */
    getDocumentsByGroup: function (group, callback, disableFilters) {
        if (typeof disableFilters==="undefined"){
            disableFilters = false;
        }
        group = group || userConfig.docList;
        var documentsList = [];
        var authorFilter = userConfig.docFilter.docAuthor;
        var signFilter = userConfig.docFilter.docSignFilter;
        var docUser = userConfig.docFilter.docUser;
        var timeFilterFrom = null;
        var timeFilterTo = null;
        if (userConfig.docFilter.dateFrom!=null){
            timeFilterFrom = userConfig.docFilter.dateFrom.getTime();
        }
        if (userConfig.docFilter.dateTo!=null){
            // + 1 day
            timeFilterTo = userConfig.docFilter.dateTo.getTime() + 24*60*60*1000;
        }
        foreach(userConfig.docs, function (doc) {
            /** @namespace doc.status */
            /** @namespace doc.time */
            /** @namespace doc.author */
            /** @namespace doc.tags */
            if ((group == "docs" || group == 'tag') && doc.status < 10 ||
                group == "trash" && doc.status >= 10) {
                if(disableFilters){
                    documentsList.push(doc);
                } else if (authorFilter == "all" ||
                    authorFilter == "my" && doc.author == userConfig.login ||
                    authorFilter == "me" && doc.author != userConfig.login) {
                    if(signFilter === 'onlySigned' && !doc.signed){return;}
                    if(signFilter === 'onlyWithoutSign' && doc.signed){return;}
                    if (timeFilterFrom != null && doc.time < timeFilterFrom) {
                        return;
                    }
                    if (timeFilterTo != null && doc.time > timeFilterTo) {
                        return;
                    }
                    if (docUser) {
                        var docUserFlag = true,
                            keys = doc.hasOwnProperty("shares") ? Object.keys(doc.shares) : '';
                        if(window.isArray(keys)){
                            keys.push(doc.author);
                            keys = keys.join('');
                        }
                        for(var docUserI = 0, docUserLength = docUser.length; docUserI < docUserLength; docUserI++){
                            if(docUser[docUserI] && keys.indexOf(docUser[docUserI]) != -1){
                                docUserFlag = false;
                                break;
                            }
                        };
                        if(docUserFlag){
                            return;
                        }
                    }
                    //tag filter
                    if (userConfig.tagFilter == null || group == "trash") {
                        documentsList.push(doc)
                    } else if (doc.tags >> userConfig.tagFilter & 0x01 == 1) {
                        documentsList.push(doc)
                    }
                }
            }
        });
        documentsList = documentsList.sort(function (a, b) {
            if (a.time > b.time) {
                return -1;
            } else if (a.time < b.time) {
                return 1;
            } else {
                return 0;
            }
        });
        if (typeof callback === "function") {
            callback(documentsList);
        } else {
            return documentsList;
        }
    },

    downloadSignReport:function(){
        var doc = actionDoc.getDocument(userConfig.docId);
        var data = "Документ: " + doc.name;
        data+="\nРозмір документа: " + doc.size + " Байт";
        data+="\nГеш значення документа:\n    "+base64ToHex(doc.hash).toUpperCase();
        data+="\nКількість підписів: " +this.signDescription.length+"\n";
        var i =0;
        foreach(this.signDescription, function (s) {
            i++;
            data+="\nІнформація про ЕЦП №"+i+":\n"+s;
        });
        saveAs(new Blob([(userConfig.browserInfo.os == "windows"?data.replace(/\n/g,"\r\n"):data)], {type: "text/plain;charset=utf-8"}), doc.name.replace (/[,|<>\?\*\/\\:]/g, "")+"-Протокол_підписів_"+new Date().toDatastampString()+".txt");
    },
    searchDocument: function (queryString) {
        if(!queryString){return false;}
        queryString = queryString.toLowerCase();
        var filteredDocs = actionDoc.getDocumentsByGroup(),
            res = [],
            length = filteredDocs.length,
            i,
            shareKeys,
            friendsThroughShare,
            tempShare;
            for(i = 0; i < length; ++i){
                tempShare = filteredDocs[i].shares || {};
                shareKeys = Object.keys(tempShare);
                friendsThroughShare = (function(){
                    var res = [],
                        friends = JSON.parse(JSON.stringify(userConfig.friends)); //cloning
                    friends[userConfig.login] = userConfig.fullName; //сам себе хороший друг
                    for(var shareI = 0, shareLength = shareKeys.length; shareI < shareLength; shareI++){
                        if(userConfig.friends[shareKeys[shareI]]){
                            res.push(userConfig.friends[shareKeys[shareI]]);
                        }
                    }
                    // WTF?
                    return res;
                })();
                if(
                    filteredDocs[i].name.toLowerCase().indexOf(queryString) != -1 //поиск по имени документа
                    ||
                    filteredDocs[i].author.toLowerCase().indexOf(queryString) != -1 //поиск документа по автору
                    ||
                    shareKeys.join(' ').toLowerCase().indexOf(queryString) != -1 // поиск по shares
                    ||
                    friendsThroughShare.join(' ').toLowerCase().indexOf(queryString) != -1 //поиск по именам friends
                ){
                    res.push(filteredDocs[i]);
                }
            }
        return res; //returns filled/empty arr or false when no query
    },
    externalSign:function(){
        var res = this.res;
        cpGUI.signHash([userConfig.doc.hash],
            function() {
                var usedDocs = [userConfig.doc];
                return function (resultSigns) {
                    drawMessage(loaderSnippet,120, false, 120);
                    var CMSList = [];
                    foreach(resultSigns, function (el) {
                        CMSList.push(el.sign);
                    });
                    cpGUI.verify(CMSList, function (signInfo) {
                        var newSignsCounter = 0;
                        var newSigns = {};
                        var lastWarnings = null;

                        for (var i = 0; i < signInfo.length; i++) {
                            if (signInfo[i].cert.notValidAfter * 1000 < Date.now()) {
                                if (signInfo.length == 1) {
                                    localStorage.removeItem("CryptoPluginKeyStore");
                                    drawWarning("Термін дії ключа закінчився " + new Date(signInfo[i].cert.notValidAfter * 1000).toDatastampString() + ".<br> Оберіть інший ключ");
                                    return;
                                } else {
                                    lastWarnings = "термін дії ключа закінчився " + new Date(signInfo[i].cert.notValidAfter * 1000).toDatastampString();
                                    continue;
                                }
                            }
                            if (typeof signInfo[i].ocsp != "undefined" && signInfo[i].ocsp && signInfo[i].ocsp.hasOwnProperty("status") && signInfo[i].ocsp.status != "good" && signInfo[i].ocsp.status != "unknown") {
                                if (signInfo.length == 1) {
                                    localStorage.removeItem("CryptoPluginKeyStore");
                                    drawWarning("Вибачте, Ваш сертифікат був відкликан АЦСК<br>" + new Date(signInfo[i].ocsp.revocationTime * 1000).toDatastampString() + ",<br>з причини: " + cpGUI.revokeReasons[signInfo[i].ocsp.revocationReason] + "<br>[SN:" + signInfo[i].cert.serialNumber + "]<br>Оберіть інший ключ для підпису");
                                    return;
                                } else {
                                    lastWarnings = "Ваш сертифікат був відкликан АЦСК " + new Date(signInfo[i].ocsp.revocationTime * 1000).toDatastampString() + ",<br>з причини: " + cpGUI.revokeReasons[signInfo[i].ocsp.revocationReason];
                                    continue;
                                }
                            }
                            var SN = "";
                            var CN = "";
                            if (signInfo[i].cert && signInfo[i].cert.issuer && signInfo[i].cert.issuer.hasOwnProperty("SN") && signInfo[i].cert.issuer.SN) {
                                SN = signInfo[i].cert.issuer.SN;
                                if (signInfo[i].cert.issuer.hasOwnProperty("CN") && signInfo[i].cert.issuer.CN){
                                    CN = signInfo[i].cert.issuer.CN;
                                }
                            }
                            if (!cpGUI.coreCert.hasOwnProperty(SN)) {
                                ajax("/api/report/signError", function () {}, JSON.stringify(signInfo[i]), function () {});
                                localStorage.removeItem("CryptoPluginKeyStore");
                                var errorText = "Вибачте, зараз ми не підтримуємо сертифікати видані";
                                if (CN && SN){
                                    errorText += "<br>" + CN + "<br>[SN:" + SN + "]";
                                } else if(SN) {
                                    errorText += " [SN:" + SN + "]";
                                } else {
                                    errorText += " цим центром";
                                }

                                drawWarning(errorText +"<br> Можливо ви отримали ключ не в акредитованому центрі сертифікації ключів");

                                return;
                            }
                            for(var d in usedDocs){
                                if (usedDocs.hasOwnProperty(d) && signInfo[i].hash.replace(/\+/g,'-').replace(/\//g,'_').replace(/=/g,'') == usedDocs[d].hash){
                                    if(!newSigns.hasOwnProperty(usedDocs[d].id)){
                                        newSigns[usedDocs[d].id]=[];
                                    }
                                    newSignsCounter ++;
                                    newSigns[usedDocs[d].id].push(signInfo[i].sign);
                                }
                            }

                        }
                        byId("messageBG").click();
                        if (newSignsCounter > 0) {
                            if (lastWarnings) {
                                drawWarning("Деякі підписи не вдалось сформуваті, оскільки<br>" + lastWarnings);
                            }
                            for(var s in newSigns){
                                if (newSigns.hasOwnProperty(s)) {
                                    ajax("/api/externalSign/" + res.id, function(){
                                        window.location.replace(res.redirectURL);
                                    }, newSigns[s], function (e) {
                                        console.warn("Can't add sign", e);
                                    });
                                }
                            }

                        } else {
                            if (lastWarnings) {
                                setTimeout(function () {
                                    drawWarning("Жодного підпису не вдалось сформуваті, оскільки<br>" + lastWarnings);
                                },0);
                            }
                        }
                    });
                }
            }())

    }
};