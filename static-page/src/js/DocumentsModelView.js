var DocumentsModelView = function(data){
    "use strict";
    this.el = userConfig.docRender === 'table' ? buildNode('TR') : buildNode('DIV');
    this.defaults = {
        hash : '',
        size : null,
        time : null,
        type : null,
        src : '',
        name : '',
        author : '',
        status : null,
        signs : null,
        tags : null,
        signed : false,
        shares : {},
        companyId : null,
        deleteByOwner : false
    };
    this.get = function(name){
        return this.attributes[name];
    };
    this.set = function(setAttr, value){
        if(typeof setAttr === 'object') {
            for (var attr in setAttr) {
                this.attributes[attr] = setAttr[attr];
            }
        }else{
            this.attributes[setAttr] = value;
        }
    };
    this.initialize = function(attr){
        this.attributes = attr || this.defaults; //should be _.extend or merge
        this.render();
        addEvent(this.el, 'click', this.clickHandler.bind(this));
        return this;
    };
    this.clickHandler = function(e){
        if(userConfig.docRender != 'table'){
            if(e.target.tagName === 'DIV' || e.target.tagName === 'SPAN'){
                this.openHandler(e);    
            }
            return;
        }
        var target = e.target,
            nodes = this.el.querySelectorAll('td'),
            index;
        while(target && target.tagName != 'TD'){
            target = target.parentNode;
        }
        index =  Array.prototype.indexOf.call(nodes, target);
        if(index < 0){throw new Error('clickHandler in DocumentsModelView failed. Return result is -1');}
        if(index === 0 || index === nodes.length - 1){ //first or last element - selectHandler
            this.selectHandler();
        }else{
            this.openHandler(e);
        }
    };
    this.openHandler = function(e){
        if(e.ctrlKey){
            window.open('/doc/'+this.get('id'));
            e.preventDefault();
        }else{
            var id = this.get('id'),
                doc = this;
            history.pushState({"renderType": "doc", "docId": id}, "", "/doc/" + id);
            if((doc.get('author') != userConfig.login && userConfig.company.login == userConfig.login && doc.get('status') < 2 && userConfig.login === userConfig.company.login)){
                doc.attributes.status = 2;
                window.documentsCollection.changeDocReference();
                window.documentsCollection.saveToLocalStorage();
            }
            window.documentsCollection.renderDocument(id);
        }
    };
    this.selectHandler = function(){
        if (hasClass(this.el, "selected")) {
            window.actionDoc.selected = window.actionDoc.selected - 1;
            if(window.actionDoc.selected < 0){
                window.actionDoc.selected = 0;
            }
            rmClass(this.el, "selected");
        } else {
            window.actionDoc.selected = actionDoc.selected + 1;
            addClass(this.el, "selected");
        }
        var buttons = byTag(byId("openDocMenu"), "BUTTON");
        if (window.actionDoc.selected > 0) {
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
        if (window.actionDoc.selected == window.documentsCollection.renderCount) {
            addClass(byTag(byId("openDocMenu"), "I")[0], "selected");
        } else {
            rmClass(byTag(byId("openDocMenu"), "I")[0], "selected");
        }
    };
    this.render = function(){ //TODO big freaking refactor
        var doc = this.attributes;
        while (this.el.firstChild) {
            this.el.removeChild(this.el.firstChild);
        }
        if (userConfig.docRender === "table") {
            var names = "<div class='usersList'>";
            var statuses = ["Ще не підключився", "Ще не переглянув", "Переглянув", "Підписав"];
            var statusesImage = ["<i class='fa fa-1 fa-question' style='font-size:10px;color:#f44336'></i>&nbsp;", "<i class='fa fa-1 fa-eye-slash' style='color:#ab47bc'></i>&nbsp;", "<i class='fa fa-1 fa-eye' style='color:#42a5f5'></i>&nbsp;", "<i class='fa fa-1 fa-pencil'  style='color:#5c9d21'></i>&nbsp;", "<i class='fa fa-1 fa-pencil' style='color:#ddd'></i>&nbsp;"];
            var s;
            if (doc.author == userConfig.login && userConfig.company.login == userConfig.login) {
                for (s in doc.shares) {
                    if (doc.shares.hasOwnProperty(s)) {
                        names += "<span style='margin-right: 10px' data-status-code='"+(doc.shares[s] % 10)+"'  data-status='" + statuses[doc.shares[s] % 10] + "'>" + statusesImage[doc.shares[s] % 10] + (userConfig.friends.hasOwnProperty(s) && userConfig.friends[s] != "" ? userConfig.friends[s] : s) + "</span>";
                    }
                }
            } else {
                if (doc.author != userConfig.company.login) {
                    names += "<span data-status-code='999999999' data-status='" + (doc.deleteByOwner ? "Видалено автором" : "Автор") +
                        "' style='margin-right: 10px'><i class='fa fa-paw' style='color:" +
                        (doc.deleteByOwner ? "#ef5350" : "#dce775") +
                        "'></i>&nbsp;" + (userConfig.friends.hasOwnProperty(doc.author) && userConfig.friends[doc.author] != "" ? userConfig.friends[doc.author] : doc.author) + "</span>";
                }
                for (s in doc.shares) {
                    if (doc.shares.hasOwnProperty(s)) {
                        if (s != userConfig.login) {
                            names += "<span style='margin-right: 10px' data-status-code='"+(doc.shares[s] % 10)+"' data-status='" + statuses[doc.shares[s] % 10] + "'>" + statusesImage[doc.shares[s] % 10] + (userConfig.friends.hasOwnProperty(s) && userConfig.friends[s] != "" ? userConfig.friends[s] : s) + "</span>";
                        } else {
                            doc.signed = doc.shares[s] % 10 == 3;
                        }
                    }
                }
            }
            names += "</div>";
            this.el.setAttribute('docId', doc.id);
            this.el.setAttribute('class', "tableDocument" + ((doc.author != userConfig.company.login && doc.status < 2 && userConfig.company.login != "all" && userConfig.login === userConfig.company.login) ? " newDocument" : ""));
            this.el.appendChild(buildNode("TD", {}, "<i class='fa checkbox'></i>"));

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
            this.el.appendChild(buildNode("TD", {className: "tableData"}, tags));
            //var docNameWidth = byId("contentBlock").offsetWidth - 580;
            this.el.appendChild(buildNode("TD", {
                className: "tableData",
                title: (doc.signed ? 'Ви підписали документ' : 'Документ не підписан')
            }, (doc.signed ? statusesImage[3] : statusesImage[4]) + "&nbsp;<span class='docName'>" + doc.name + "</span>"));
            this.el.appendChild(buildNode("TD", {className: "tableData titleHelper"}, names));
            this.el.appendChild(buildNode("TD", {
                title: new Date(doc.time).toTimestampString()
            }, (new Date(doc.time).toDatastampString() == new Date().toDatastampString() ? new Date(doc.time).toTimeString() : new Date(doc.time).toDatastampString())));
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
            }, doc.type == 0 ? null : "<br>Зашифровано паролем");
            this.el.setAttribute('docId', doc.id);
            this.el.setAttribute('class', "document" + ((doc.author != userConfig.login && userConfig.company.login == userConfig.login && doc.status < 2 && userConfig.login === userConfig.company.login) ? " newDocument" : ""));
            this.el.setAttribute('title', doc.name);
            this.el.appendChild(docMenu);
            this.el.appendChild(docPrev);
        }
    };
    this.initialize(data);
    return this;
};