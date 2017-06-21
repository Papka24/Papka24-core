/**
 * DocumentsCollection
 * Used to modify userConfig.docs && render && etc
 */
var DocumentsCollection = function(models, options){
    "use strict";
    this.ws = {};
    this.renderCount = 0;
    this.initialize = function(models, options){
        this.docReference = models || this.getFromLocalStorage() || [];
        window.userConfig.docs = this.docReference;
        this.models = this.docReference.reduce(function(memo, el, i){ //TODO check performance, cache into LS?
            el.index = i;
            memo[el.id] = new DocumentsModelView(el);
            return memo;
        }, {});
        this.hash = null;
        this.oldHash = null;
        this.version = '19122016';
        this.timestamp = this.getTimeStamp() || null;
        this.length = this.docReference.length || null;
        this.failTimer = [];
        this.justRenderedDocs = [];
        this.limit = 50;
        var temp = (((window.location.pathname.match(/\/page\/(\d+)$/) || [])[1] || 0) - 1) * this.limit;
        this.offset = temp > 0 && temp < this.length ? temp : 0;
        this.fullCollection = this.fullCollection || false;
        this.wsLog = []; //[{time : 123123, message : obj}]
        var CustomWebSock = WebSock;
        if(this.version != localStorage.getItem('documentsCollectionDBversion')){
            this.clearDocs();
            localStorage.setItem('documentsCollectionDBversion', this.version);
        }
        CustomWebSock.prototype.ping = function(){
            this.setTimerOnWS();
            var res = {
                timestamp : this.length ? this.timestamp || null : null,
                sessionid : localStorage.getItem("sessionId"),
                length : this.length,
                page : this.offset / this.limit,
                limit : this.limit
            };
            if(userConfig.company.login != userConfig.login){
                this.company = userConfig.company.companyId;
                this.employee = userConfig.company.login;
                res.company = this.company;
                res.employee = this.employee;
            }else{
                this.company = null;
                this.employee = null;
            }
            //console.log('WS send', res);
            this.wsLog.push({
                time : (new Date()).getTime(),
                message : res
            });
            this.ws.send(res);
        }.bind(this);
        this.CustomWebSock = CustomWebSock;
        this.ws = new this.CustomWebSock('wss://'+(window.location.hostname === 'localhost' ? 'papka24.in.ua' : window.location.hostname)+'/wss', this.webSocketListener);
        this.setTimerOnWS();
        if(/^\/doc\/\d*$/g.test(window.location.pathname)){//test for /doc/id in url
            this.renderDocument(window.location.pathname.match(/\d*$/g)[0]);
        }else if(window.location.pathname.indexOf('trash') != -1){
            byId("contentBlock").innerHTML = "<div style='margin:100px 40%'><img width='152' alt='' src='/img/ring.svg'></div>"; // draw_preloader
            if(!window.documentsTrashCollection.navigationHandler(window.history.state)){
                window.documentsTrashCollection.renderDocuments();
            }
        }else{
            if(!this.navigationHandler(window.history.state)){
                this._navigation(this.offset);
            }
        }
        this.toggleNavButtons();
        this.initContractorSearch();
        addEvent(byId('jsTitle'), 'mousemove', this.titleHandler);
        addEvent(byId('contentBlock'), 'mouseout', this.titleHandler);
        addEvent(byId('contentBlock'), 'mousemove', this.titleHandler);
        window.addEventListener('popstate', this.navigationHandler.bind(this));
        //this.loadDocument();
        return this;
    };
    this.navigationHandler = function(e){
        if(!e){return;}
        var data = e.state || e;
        if(this.length < data.offset){
            return;
        }
        userConfig.docList = data.docList || 'docs';
        this.offset = data.offset || 0;
        userConfig.tagFilter = data.tagFilter;
        /*blocks selected flag removed*/
        var block = byId('mainMenu'),
            items = block.querySelectorAll('.active, .selected'),
            temp, i, length;
        for(i = 0, length = items.length; i < items; i++){
            rmClass(items[i], 'active');
            rmClass(items[i], 'selected');
        }
        if(data.tagFilter != null){
            addClass(byId('allDocsMenu'), 'active');
            temp = document.querySelectorAll('.tagFilter');
            if(!temp){throw new Error('o is null "line":6,"col":12271, FIXME and WTF is happening? Tags are selected but no tagFilter class present');}
            for(i = 0, length = temp.length; i < length; i++){
                if(temp[i].tagId == data.tagFilter){
                    addClass(temp[i], 'selected');
                    break;
                }
            }
        }else{
            if(data.docList == 'trash'){
                addClass(document.querySelector('#mainMenu .trashLink'), 'active');
            }else{
                addClass(byId('allDocsMenu'), 'active');
            }
        }
        this.renderDocuments();
        return true;
    };
    this.setTimeStamp = function(timestamp){
        if(timestamp && timestamp != 'null' && !this.employee && !this.company){
            this.timestamp = timestamp;
            window.localStorage.setItem('docsTimeStamp', JSON.stringify({
                login : userConfig.login,
                timestamp : timestamp
            }));
        }
    };
    this.getTimeStamp = function(){
        var parsed = JSON.parse(window.localStorage.getItem('docsTimeStamp')) || {},
            timestamp = null;
        if(parsed.login === userConfig.login){
            timestamp = +parsed.timestamp;
        }
        return timestamp;
    };
    this.changeDocReference = function(){
        var el, res = [];
        for(el in this.models){
            if(this.models.hasOwnProperty(el)){
                res.push(this.models[el].attributes);
            }
        }
        res = res.sort(function (a, b) {
            if (a.time > b.time) {
                return -1;
            } else if (a.time < b.time) {
                return 1;
            } else {
                return 0;
            }
        });
        this.docReference = res;
        this.length = this.docReference.length;
        userConfig.docs = res;
    };
    this.saveToLocalStorage = function(){
        if(!this.company && !this.employee){
            window.localStorage.setItem('docs', JSON.stringify(
                {
                    login : userConfig.login,
                    data: this.docReference,
                    fullCollection : this.fullCollection
                }
            ));
        }
    };
    this._navigation = function(offset){
        this.offset = offset;
        var table = userConfig.docRender === 'table' ? document.querySelector('.mainTableWrapper > table') : document.querySelector('.documentsWrapper'),
            temp = '';
        if(userConfig.tagFilter != null){
            temp = '/'+userConfig.tagFilter
        }
        if(Math.floor(this.offset / this.limit)){
            temp += '/page/' + (Math.floor(this.offset / this.limit) + 1);
        }
        history.pushState({renderType: "list", docList: userConfig.docList, offset : offset, tagFilter : userConfig.tagFilter}, "", "/list/" + userConfig.docList + temp);
        if(table) {
            while (table.firstChild) {
                table.removeChild(table.firstChild);
            }
        }
        if(userConfig.docList === 'trash'){
            window.documentsTrashCollection.renderDocuments();
        }else{
            this.renderDocuments();
        }
    };
    this.next = function(){
        var offset = this.offset + this.limit;
        actionDoc.selected = 0;
        this._navigation(offset);
    };
    this.loadDocuments = function(){
      this.httpMode();
    };
    this.previous = function(){
        actionDoc.selected = 0;
        var offset = this.offset - this.limit;
        if(offset < 0){
            offset = 0;
        }
        this._navigation(offset);
    };
    this.toggleNavButtons = function(next, previous){//true means hide
        previous = this.offset <= 0 || previous;
        next = (this.limit - this.renderCount > 0) || next || ((this.offset + this.renderCount) === this.length && this.fullCollection);
        var nextButton = byId('collectionNext'),
            previousButton = byId('collectionPrevious'),
            counter = byId('collectionSpaceIndicator');
        if(nextButton){
            if(next){
                nextButton.setAttribute('disabled', true);
            }else{
                nextButton.removeAttribute('disabled');
            }
        }
        if(previousButton){
            if(previous){
                previousButton.setAttribute('disabled', true);
            }else{
                previousButton.removeAttribute('disabled');
            }
        }
        if(counter){
            counter.innerHTML = (this.offset + 1) + ' - ' + (this.offset + this.renderCount);
        }
        return;
    };
    this.get = function(id){
        var res = this.models[id];
        if(!res){
            for(var i = 0, length = this.justRenderedDocs.length; i < length; i++){ //fixme не должно быть других моделей
                if(this.justRenderedDocs[i].get('id') == id){
                    res = this.justRenderedDocs[i];
                    break;
                }
            }
        }
        return res;
    };
    this.getFromLocalStorage = function(){
        var res = [],
            parsed = JSON.parse(window.localStorage.getItem('docs')) || {},
            flag = parsed.fullCollection || false,
            arr =  parsed.data || [],
            length = arr.length,
            i;
        if(parsed.login != userConfig.login){
            return res;
        }
        this.fullCollection = flag;
        for(i = 0; i < length; ++i){ //_.compact
            if(arr[i]){
                res.push(arr[i]);
            }
        }
        return res
    };
    this.calculateHash = function(id){
        var hash = '';
        if(id){
            hash = MD5(JSON.stringify(this.get(id).attributes) || '');
        }else{
            hash = MD5(JSON.stringify(this.models) || '');
            this.oldHash = ''+this.hash;
            this.hash = hash;
        }
        return hash;
    };
    this.compareHash = function(){
        return this.oldHash === this.hash;
    };
    /**
     * @doc - array or object
     * @doNotRender - boolean. Similar to "wait" in Backbone
     * */
    this.addDocument = function(doc, doNotRender){
        doNotRender = doNotRender || this.doNotRenderCheck();
        if(!doc){return;}
        if(window.isArray(doc)){
            for(var i = 0, length = doc.length; i < length; i++){
                this.addDocument(doc[i], true);
            }
            this.changeDocReference();
            this.saveToLocalStorage(); //TODO issue change event
            this.calculateHash();
            if(!doNotRender && !this.compareHash()) {
                this.renderDocuments();
            }
        }else{
            doc.shares = doc.shares || null;
            doc.signs = doc.signs || null;
            if(doc.status < 10){
                if(this.models[doc.id]){
                    doc.index = this.models[doc.id].attributes.index;
                    if(/^\/doc\/\d*$/g.test(window.location.pathname) && window.location.pathname.match(/\d*$/g)[0] == doc.id) {
                        this.updateDocumentView(doc);
                    }
                    this.models[doc.id].set(doc);
                }else{
                    doc.index = ++this.length - 1;
                    this.models[doc.id] = new DocumentsModelView(doc);
                }
                this.models[doc.id].render();
                this.docReference[doc.index] = this.models[doc.id].attributes;
            }else if(doc.status < 20){
                if(this.models[doc.id]){
                    this.deleteDocument(doc.id);
                }
            }else{
                delete this.models[doc.id];
                delete window.documentsTrashCollection.models[doc.id];
                if(userConfig.docList === 'trash'){
                    window.documentsTrashCollection.renderDocuments();
                }else if(/^\/doc\/\d*$/g.test(window.location.pathname) && window.location.pathname.match(/\d*$/g)[0] == doc.id && !doNotRender){
                    history.pushState({renderType: "list", docList: userConfig.docList}, "", "/list/" + userConfig.docList);
                    this.renderDocuments();
                }
            }
            if(!doNotRender){
                this.changeDocReference();
                this.saveToLocalStorage();
                //TODO issue add event
                this.calculateHash();
                this.renderDocuments();
            }
        }
    };
    this.justRender = function(rawDocs){
        var docs = [],
            count = 0,
            temp,
            el = userConfig.docRender === 'table' ? document.querySelector('.mainTableWrapper > table') : document.querySelector('.documentsWrapper');
        rawDocs = rawDocs.sort(function (a, b) {
            if (a.time > b.time) {
                return -1;
            } else if (a.time < b.time) {
                return 1;
            } else {
                return 0;
            }
        });
        for(var length = rawDocs.length; count < length; ++count) {
            if (!rawDocs[count] || rawDocs[count].status >= 20) {
                continue;
            }
            temp = new DocumentsModelView(rawDocs[count]);
            if(this.renderCount){
                this.renderCount += 1;
                el.appendChild(temp.el);
            }else{
                docs.push(temp);
            }
        };
        this.justRenderedDocs = docs;
        if(!this.renderCount){
            this.renderDocumentList(docs, true);
        }
    }
    this.updateDocumentView = function(doc){
        var originalDoc = this.get(doc.id),
            required1 = {
                name : doc.name,
                status : doc.status,
                signs : doc.signs,
                tags : doc.tags,
                signed : doc.signed,
                shares : doc.shares
            },
            required2 = {
                name : originalDoc.get('name'),
                status : originalDoc.get('status'),
                signs : originalDoc.get('signs'),
                tags : originalDoc.get('tags'),
                signed : originalDoc.get('signed'),
                shares : originalDoc.get('shares')
            },
            hash1 = MD5(JSON.stringify(required1)),
            hash2 = MD5(JSON.stringify(required2)),
            tempShare;
        if(hash1 != hash2){
            tempShare = {};
            tempShare[doc.id] = [];
            for(var temp in doc.shares){
                tempShare[doc.id].push(
                    {
                        user : temp,
                        status : doc.shares[temp]
                    }
                )
            }
            actionDoc.updateShareList(JSON.stringify(tempShare));
            ajax("/api/sign/" + doc.id, function(arr){
                if(JSON.parse(arr).length != userConfig.cache.signList.length){
                    userConfig.cache.signList=[];
                    actionDoc.signs = [];
                    actionDoc.updateSignList(arr);
                }
            }, null, function () {
                console.warn("Can't get signs for res", userConfig.docid)
            });
            window.setTimeout(function(){
                ajax("/api/share/" + doc.id, actionDoc.updateShareList, null, function () {
                    console.warn("Can't get sharing for res", userConfig.docid)
                });
            }, 50);

            var docNameInput = document.querySelector('.docNameInput');
            if(docNameInput){
                docNameInput.value = doc.name;
            }
        }
    };
    this.doNotRenderCheck = function(){
        var res = false;
        if(window.location.pathname.indexOf('/list/trash') === -1 && window.location.pathname.indexOf('/list/docs') === -1 && window.location.pathname != '/' && window.location.pathname){
            res = true;
        }
        return res;
    };
    this.renderDocuments = function(docs){
        if(userConfig.docList === 'trash'){
            return;
        }
        actionDoc.selected = 0;
        if(/^\/doc\/\d*$/g.test(window.location.pathname)){
            var docId = window.location.pathname.match(/\d*$/g)[0];
            if(userConfig.docId != docId){
                this.renderDocument();
            }
        }else if(docs){
            this.renderDocumentList(docs);
        }else{
            this.search();
        }
    };
    this.titleHandler = function (e){
        var temp = e.relatedTarget || e.target,
            title = '',
            block = byId('jsTitle'),
            arr = [];
        while(temp && temp.tagName != 'TD' && temp.id != 'jsTitle'){
            temp = temp.id == 'contentBlock'? false : temp.parentNode;
        }
        if(!block){
            block = buildNode('DIV', {
                id : 'jsTitle',
                className : 'hidden'
            });
            document.body.appendChild(block);
        }
        if(!temp){
            addClass(block, 'hidden');
            return;
        }
        if(hasClass(temp, 'titleHelper')){
            temp = temp.querySelectorAll('.usersList > span');
            title = '';
            for(var i = 0, length = temp.length; i < length; i++){
                arr.push({
                    status: +temp[i].getAttribute('data-status-code'),
                    title: temp[i].innerHTML + ' - ' + temp[i].getAttribute('data-status') + '<br/>'
                });
            }
            if(!arr.length){
                addClass(block, 'hidden');
                return;
            }
            block.innerHTML = arr.sort(function(a, b){
                var res = 0;
                if(a.status < b.status){
                    res = 1;
                }else if(a.status > b.status){
                    res = -1;
                }
                return res;
            }).reduce(function(memo, i){
                return memo += i.title;
            }, '');
            rmClass(block, 'hidden');
            var top = (e.clientY - (block.offsetHeight + 2));
            if(top < 70){
                top = 70;
            }
            block.style.top = top + 'px';
            block.style.left = (e.clientX - (block.offsetWidth + 2)) + 'px';
        }else if(temp.id === 'jsTitle'){ //a bit glitchy bit non-relavent
            var top = (e.clientY - (block.offsetHeight + 2)),
                temp = document.querySelector('.mainTableWrapper .titleHelper');
            if(temp){
                temp = temp.offsetLeft + 10;
            }else{
                return;
            }
            if(temp > e.clientX){
                addClass(block, 'hidden');
                return;
            }
            if(top < 70){
                top = 70;
            }
            block.style.top = top + 'px';
            block.style.left = (e.clientX - (block.offsetWidth + 16)) + 'px';
        }else{
            addClass(block, 'hidden');
        }
    }
    this.renderDocument = function(id){
        if(id){
            history.pushState({"renderType": "doc", "docId": id}, "", "/doc/" + id);
            cpGUI.cleanUp(); // Stop cryptoplugin
            actionDoc.renderDocument(id);
        }
    };
    this.renderDocumentList = function(documentsList, doNotSkip){
            cpGUI.cleanUp(); // Stop cryptoplugin
            var docs = [],
                that = this;
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
            documentsList = documentsList || this.getDocumentsByGroup(userConfig.docList);
            var count = 0;
            var newCount = 0;
            var renderCount = 0;
            var that = this;
            if(window.isArray(documentsList)){
                documentsList.sort(function (a, b) {
                    if (a.get('time') > b.get('time')) {
                        return -1;
                    } else if (a.get('time') < b.get('time')) {
                        return 1;
                    } else {
                        return 0;
                    }
                });
                for(var length = documentsList.length; count < length; ++count) {
                    if (!documentsList[count] || (documentsList[count].status >= 10 && userConfig.docList != 'trash')) {
                        continue;
                    }
                    if (documentsList[count].get('status') % 10 < 2 && documentsList[count].get('author') != userConfig.login) {
                        ++newCount;
                    }
                    if (renderCount >= that.limit) {
                        break;
                    }
                    if (count < that.offset && !doNotSkip) {
                        continue;
                    }
                    documentsList[count].render();
                    docs.push(documentsList[count].el);
                    ++renderCount;
                };
                this.renderCount = renderCount;
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
                    docMenuNode.appendChild(buildNode("DIV", {className: "NavigationWrapper"}, [
                        buildNode("SPAN", {
                            id : 'collectionSpaceIndicator'
                        }),
                        buildNode("BUTTON", {
                            id : 'collectionPrevious',
                            className : 'fa fa-chevron-left pure-button'
                        }, '', {
                            click : function(){that.previous();}
                        }),
                        buildNode("BUTTON", {
                            id : 'collectionNext',
                            className : 'fa fa-chevron-right pure-button'
                        }, '', {
                            click : function(){that.next();}
                        })
                    ]));
                    if (count > 0) {
                        byId("contentBlock").appendChild(docMenuNode);
                    } else {
                        byId("contentBlock").appendChild(buildNode("DIV", {className: "emptyBlock"}, actionDoc.getEmptyDescriptionByGroup(userConfig.docList)))
                    }
                    byId("contentBlock").appendChild(
                        buildNode("DIV", {className : 'mainTableWrapper'}, [
                            buildNode("TABLE", {className: "pure-table pure-table-horizontal"}, docs)
                        ])
                    );
                    this.toggleNavButtons();
                } else {
                    byId("contentBlock").innerHTML = "";
                    var docMenuNode = buildNode("DIV", {id: "openDocMenu"});
                    docMenuNode.appendChild(buildNode("DIV", {className: "NavigationWrapper"}, [
                        buildNode("SPAN", {
                            id : 'collectionSpaceIndicator'
                        }),
                        buildNode("BUTTON", {
                            id : 'collectionPrevious',
                            className : 'fa fa-chevron-left pure-button'
                        }, '', {
                            click : function(){that.previous();}
                        }),
                        buildNode("BUTTON", {
                            id : 'collectionNext',
                            className : 'fa fa-chevron-right pure-button'
                        }, '', {
                            click : function(){that.next();}
                        })
                    ]));
                    byId("contentBlock").appendChild(docMenuNode);
                    if (count == 0) {
                        byId("contentBlock").appendChild(buildNode("DIV", {className: "emptyBlock"}, actionDoc.getEmptyDescriptionByGroup(userConfig.docList)))
                    }
                    var tempEl = buildNode('DIV', {className : 'documentsWrapper'});
                    byId("contentBlock").appendChild(tempEl);
                    for (var d in docs) {
                        if (docs.hasOwnProperty(d)) {
                            tempEl.appendChild(docs[d]);
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
                if (userConfig.docList != "trash" && userConfig.tagFilter == null) {
                    byId("docCount").innerHTML = (newCount > 0 && userConfig.company.login == userConfig.login) ? " (" + newCount + ")" : "";
                }
            } else {
                byId("contentBlock").appendChild(buildNode("DIV", {className:"emptyBlock"}, actionDoc.getEmptyDescriptionByGroup(userConfig.docList)));
            }
    };
    var _now = Date.now || function(){
            return new Date().getTime();
        };
    this._throttle = function(func, wait, options) {
        var context, args, result;
        var timeout = null;
        var previous = 0;
        if (!options) options = {};
        var later = function() {
            previous = options.leading === false ? 0 : _now();
            timeout = null;
            result = func.apply(context, args);
            if (!timeout) context = args = null;
        };
        return function() {
            var now = _now();
            if (!previous && options.leading === false) previous = now;
            var remaining = wait - (now - previous);
            context = this;
            args = arguments;
            if (remaining <= 0 || remaining > wait) {
                if (timeout) {
                    clearTimeout(timeout);
                    timeout = null;
                }
                previous = now;
                result = func.apply(context, args);
                if (!timeout) context = args = null;
            } else if (!timeout && options.trailing !== false) {
                timeout = setTimeout(later, remaining);
            }
            return result;
        };
    };
    this.search = this._throttle(function(){
        if(typeof window.ajaxAbort === 'object' && window.ajaxAbort){
            window.ajaxAbort.abort();
        }
        var query = document.getElementById('search').value;
        // TODO FIX IT! sometime return false
        var searchDocs = this.searchDocument(query);
        var table = userConfig.docRender === 'table' ? document.querySelector('.mainTableWrapper > table') : document.querySelector('.documentsWrapper');
        if(table){
            while (table.firstChild) {
                table.removeChild(table.firstChild);
            }
        }
        this.renderDocumentList(searchDocs);
        if(this.renderCount < this.limit){
            this.httpMode(this.offset + this.renderCount, this.limit - this.renderCount);
        }else{
            this.toggleNavButtons();
        }
    }, 100, {leading : false});
    this.searchDocument = function(queryString){
        if(!queryString){return false;}
        queryString = queryString.toLowerCase();
        var filteredDocs = this.getDocumentsByGroup(),
            res = [],
            length = filteredDocs.length,
            i,
            shareKeys,
            friendsThroughShare,
            tempShare;
        for(i = 0; i < length; ++i){
            tempShare = filteredDocs[i].get('shares') || {};
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
                filteredDocs[i].get('name').toLowerCase().indexOf(queryString) != -1 //поиск по имени документа
                ||
                filteredDocs[i].get('author').toLowerCase().indexOf(queryString) != -1 //поиск документа по автору
                ||
                shareKeys.join(' ').toLowerCase().indexOf(queryString) != -1 // поиск по shares
                ||
                friendsThroughShare.join(' ').toLowerCase().indexOf(queryString) != -1 //поиск по именам friends
            ){
                res.push(filteredDocs[i]);
            }
        }
        return res; //returns filled/empty arr or false when no query
    };
    this.getDocumentsByGroup = function (group, callback, disableFilters) {
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
        foreach(this.models, function (doc) {
            var real_doc = doc;
            doc = doc.attributes;
            /** @namespace doc.status */
            /** @namespace doc.time */
            /** @namespace doc.author */
            /** @namespace doc.tags */
            if ((group == "docs" || group == 'tag') && doc.status < 10 ||
                group == "trash" && doc.status >= 10) {
                if(disableFilters){
                    documentsList.push(real_doc);
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
                            keys = doc.hasOwnProperty("shares") ? Object.keys(doc.shares || {}) : '';
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
                        documentsList.push(real_doc)
                    } else if (doc.tags >> userConfig.tagFilter & 0x01 == 1) {
                        documentsList.push(real_doc)
                    }
                }
            }
        });
        documentsList = documentsList.sort(function (a, b) {
            if (a.get('time') > b.get('time')) {
                return -1;
            } else if (a.get('time') < b.get('time')) {
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
    };
    this.stopRecursion = false;
    this.httpMode = this._throttle(function(offset, limit, addFlag){
        this.deleteTimerOnWS();
        if(typeof window.ajaxAbort === 'object' && window.ajaxAbort){
            window.ajaxAbort.abort();
        }
        if(this.fullCollection && userConfig.docList != 'trash'){ //TODO fixme отдельная коллекция под мусор
            if(!this.renderCount){
                if(this.stopRecursion){
                    this.stopRecursion = false;
                    return;
                }
                this.stopRecursion = true;
                this.previous(); //костылёк, нужно считать сколько реально можно показать документов на след странице
                this.toggleNavButtons(true);
            }
            return;
        }
        if(/^\/doc\/\d*$/g.test(window.location.pathname)) {//fixme хак опять
            return;
        }
        if(!this.renderCount){
            byId("contentBlock").innerHTML = "<div style='margin:100px 40%'><img width='152' alt='' src='/img/ring.svg'></div>"; // draw_preloader
        }
        var dateFrom = userConfig.docFilter.dateFrom ? userConfig.docFilter.dateFrom.getTime() : null,
            dateTo = userConfig.docFilter.dateTo ? userConfig.docFilter.dateTo.getTime() : null,
            signed = {SignedOrNot : null, onlySigned : true, onlyWithoutSign : false},
            searchObj = {
                searchQuery : document.getElementById('search').value,
                contractor : userConfig.docFilter.docUser,
                author : userConfig.docFilter.docAuthor,
                signed : signed[userConfig.docFilter.docSignFilter] === undefined ? null : signed[userConfig.docFilter.docSignFilter],
                dateFrom : dateFrom,
                dateTo : dateTo,
                docList : userConfig.docList,
                tagFilter : userConfig.tagFilter,
                offset : offset || this.offset,
                limit : limit || this.limit
            };
        if(searchObj.signed !== null || searchObj.searchQuery || searchObj.contractor || searchObj.dateFrom || searchObj.dateTo || typeof userConfig.tagFilter == 'number' || userConfig.docList != 'docs'){
            addFlag = true;
        }
        window.ajaxAbort = ajax('/api/resource/search', function(docs){
            var statusFlag = userConfig.docList != 'trash';
            docs = JSON.parse(docs);
            docs = docs.reduce(function(memo, el){ //FIXME НЕ ДОЛЖНО БЫТЬ ФИЛЬТРАЦИИ ОТВЕТОВ ОТ СЕРВЕРА!
                var tempFlag;
                if(statusFlag){
                    tempFlag = el.status < 10;
                }else{
                    tempFlag = el.status >= 10 && el.status < 20;
                }
                if(tempFlag){
                    memo.push(el);
                }
                return memo;
            }, []);
            if(!docs.length){
                if(!addFlag){
                    this.fullCollection = true;
                    this.saveToLocalStorage();
                }
                if((this.length > 0 || this.offset > 0) && !addFlag){
                    this.offset -= this.limit;
                    if(this.offset < 0){this.offset = 0;}
                    this._navigation(this.offset);
                    this.toggleNavButtons(true);
                } else if(this.renderCount === 0) {
                    this.renderDocuments([]);
                    this.toggleNavButtons(true);
                }
                return;
            }
            if(((this.renderCount + docs.length) < this.limit) && !addFlag){
                this.fullCollection = true;
                this.saveToLocalStorage();
            }

            var crunchFlag = false; // если нет дубликатов
            if (window.isArray(docs)) {
                for (var i = 0, length = docs.length; i < length; i++) {
                    if (this.models[docs[i].id]) {
                        crunchFlag = true;
                        break;
                    }
                }
            }

            if(crunchFlag) {
                this.reset();
            } else {
                if (!addFlag) {
                    this.addDocument(docs);
                } else {
                    this.justRender(docs);
                }
            }
            
            this.toggleNavButtons();
        }.bind(this), searchObj, function(e){
            if(!this.renderCount){
                this.renderDocuments([]);
            }
        }.bind(this));
    }, 100, {leading : false});
    this.getFriendsFilter = function(){
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
        return friendFilter;
    };
    this.initContractorSearch = function(friendsFilter){
        friendsFilter = friendsFilter || this.getFriendsFilter();
        var mainArr = [],
            target = byId('contractorSearch'),
            friendsObj = JSON.parse(JSON.stringify(userConfig.friends)),
            temp,
            length,
            indexOf,
            keys = {
                valueKeys : ['email'],
                renderKeys : ['email', 'name', 'company']
            };
        friendsObj[userConfig.login] = userConfig.fullName; //сам себе "друг"
        for(temp in friendsObj){
            indexOf = friendsFilter.indexOf(temp);
            if(indexOf != -1){ // удаляет из friendsFilter если есть в друзьях
                friendsFilter.splice(indexOf, 1);
            }
            mainArr.push({
                email : temp,
                name : friendsObj[temp],
                company : actionDoc.getCompanyNameByEmail(temp)
            })
        }
        for(temp = 0, length = friendsFilter.length; temp < length; temp++){ //массив значений без имени
            mainArr.push({
                email : friendsFilter[temp],
                name : '',
                company : ''
            });
        }
        if(target){
            this.fancyAutocomplete = new FancyAutocomplete(mainArr, target, keys);
        }
    };
    this.webSocketListener = function(parsed){
        //console.log('WS Get', arguments);
        this.deleteTimerOnWS();
        if(parsed.method == 'chatMessage' ){
            if(window.documentChat) {
                for (var i = 0, length = parsed.data.length; i < length; i++) {
                    if(parsed.data[i].roomId == window.documentChat.chatId){
                        window.documentChat.addMessage(parsed.data[i]);
                    }
                }
            }
            return;
        }
        if(parsed.method === 'SHOWMESSAGE'){
            var that = this;
            if(parsed.templateName === 'invite.html'){
                var temp = byId('friendsBox'),
                    span = byTag(temp, 'span')[0],
                    num;
                if(span.innerText === '···'){
                    return;
                }
                num = (+span.innerText || 0) + 1;
                if(num > 99){
                    num = '···';
                }
                span.innerText = num;
                addClass(temp, 'newPartner');
            }else{
                ajax("/templates/"+parsed.templateName, function(html){
                    var template = _.template(html)(parsed.data),
                        node = buildNode('DIV', {className : 'showWSmessage'});
                    node.innerHTML = template;
                    drawMessage(node, 740, true, 230);
                    (function(){ //FIXME костыли такие костыли
                        var messageBg = byId('messageBG');
                        messageBg.onclick = null;
                    })()
                    var temp = byId("message"),
                        botherMeNot = temp.querySelector('.doNotBotherMe'),
                        rejectOffer = temp.querySelector('.rejectOffer'),
                        sendInvitationEmails = temp.querySelector('.sendInvitationEmails');
                    if(botherMeNot){
                        addEvent(botherMeNot, 'click', function(){
                            var messageBg = byId('messageBG');
                            messageBg.onclick = function(){rmClass(this, 'active'); if(byTag(byId('message'),'DIV').length>0){byTag(byId('message'),'DIV')[0].parentNode.removeChild(byTag(byId('message'),'DIV')[0])}};
                            rmClass(byId('messageBG'), 'active');
                            while (temp.firstChild) {
                                temp.removeChild(temp.firstChild);
                            }
                            that.ws.send({
                                id : parsed.id,
                                type : 'userResponseToMessage',
                                data : 'doNotBotherMe'
                            });
                        });
                    }
                    if(rejectOffer){
                        addEvent(rejectOffer, 'click', function(){
                            var messageBg = byId('messageBG');
                            messageBg.onclick = function(){rmClass(this, 'active'); if(byTag(byId('message'),'DIV').length>0){byTag(byId('message'),'DIV')[0].parentNode.removeChild(byTag(byId('message'),'DIV')[0])}};
                            rmClass(byId('messageBG'), 'active');
                            while (temp.firstChild) {
                                temp.removeChild(temp.firstChild);
                            }
                            that.ws.send({
                                id : parsed.id,
                                type : 'userResponseToMessage',
                                data : 'no'
                            });
                        });
                    }
                    if(sendInvitationEmails){
                        addEvent(sendInvitationEmails, 'click', function(){
                            var messageBg = byId('messageBG');
                            messageBg.onclick = function(){rmClass(this, 'active'); if(byTag(byId('message'),'DIV').length>0){byTag(byId('message'),'DIV')[0].parentNode.removeChild(byTag(byId('message'),'DIV')[0])}};
                            rmClass(byId('messageBG'), 'active');
                            while (temp.firstChild) {
                                temp.removeChild(temp.firstChild);
                            }
                            that.ws.send({
                                id : parsed.id,
                                type : 'userResponseToMessage',
                                data : 'sendInvitationEmails'
                            });
                        });
                    }
                });
            }
            return;
        }
        if(parsed.status === 'OK'){
            if(parsed.data){
                this.wsLog.push({
                    time : (new Date()).getTime(),
                    message : parsed
                });
            }
            return; //just PONG nothing special
        }
        if(parsed.method === 'RESET'){
            var supaTemp = [],
                parsedTemp = JSON.parse(JSON.stringify(parsed));
            for(var i = 0, length = parsed['data'].length; i < length; i++){
                supaTemp.push(
                    {
                        id: parsed['data'][i].id,
                        status: parsed['data'][i].status,
                        tags: parsed['data'][i].tags,
                        signed: parsed['data'][i].signed,
                        shares: parsed['data'][i].shares
                    }
                )
            }
            parsedTemp.data = supaTemp;
            this.wsLog.push({
                time : (new Date()).getTime(),
                message : parsedTemp
            });
        }else{
            this.wsLog.push({
                time : (new Date()).getTime(),
                message : parsed
            });
        }
        if((this.company || this.employee) && parsed.method != 'RESET'){ //do nothing when in company view
            return;
        }
        if(parsed.method === 'RESET'){
            this.clearDocs();
            this.addDocument(parsed.data);
        }else if(parsed.method === 'LEAVE_COMPANY' || parsed.method === 'JOIN_COMPANY'){
            this.clearDocs();
            window.location.href = window.location.href; //TODO normal company change
        }
        if(parsed.method === 'DELETE'){
            this.deleteDocument(parsed.resourceId);
        }else if(parsed.method === 'UPDATE'){
            if(window.isArray(parsed.data)){
                var tempWait = false;
                for(var length = parsed.data.length, i = 0; i < length; i++){
                    if(this.models[parsed.data[i].id]){
                        parsed.data[i].index = this.models[parsed.data[i].id].attributes.index;
                        var hash1 = MD5(JSON.stringify(this.models[parsed.data[i].id].attributes)),
                            hash2 = MD5(JSON.stringify(parsed.data[i]));
                        this.addDocument(parsed.data[i], true);
                        if(hash1 != hash2) {
                            tempWait = true;
                        }
                    }else if(window.documentsTrashCollection.models[parsed.data[i].id]){
                        if(parsed.data[i].status < 10){
                            window.documentsTrashCollection.restoreDocument(parsed.data[i].id);
                        }else if(userConfig.docList === 'trash'){
                            window.documentsTrashCollection.renderDocuments();
                        }
                    }
                }
                if(tempWait){
                    this.changeDocReference();
                    this.saveToLocalStorage();
                    this.renderDocuments();
                }
            }else{
                if(this.models[parsed.data.id]){
                    this.addDocument(parsed.data);
                }else if(window.documentsTrashCollection.models[parsed.data.id]){
                    if(parsed.data.status < 10){
                        window.documentsTrashCollection.restoreDocument(parsed.data[i].id);
                    }else if(userConfig.docList === 'trash'){
                        window.documentsTrashCollection.renderDocuments();
                    }
                }
            }
        }else {
            this.addDocument(parsed.data); //see parsed format https://holder/test/papka/wiki/internal/WssService.md
        }
        if(parsed.timestamp){
            this.setTimeStamp(parsed.timestamp);
        }
        this.issueToastr(parsed);
    }.bind(this);
    this.issueToastr = function(parsed){
        var string = '',
            length,
            temp;
        if(parsed.method != 'RESET'){
            if(parsed.userLogin != userConfig.login){
                if(!parsed.data){return;}
                length = parsed.data.length;
                if(parsed.eventType === 'SHARED'){ //TODO add LEAVE_COMPANY и JOIN_COMPANY
                    temp = length > 1 ? 'документів' : 'документа';
                    string = 'Вам надано доступ до '+length+' '+temp;
                }else if(parsed.eventType === 'SIGN'){
                    temp = length > 1 ? 'документів' : 'документ';
                    string = 'Було підписано '+length+' '+temp;
                }
                if(string) {
                    drawChangeInfo(string, false, 8000, {
                        noCancelPopup: true
                    });
                }
            }
        }
    };
    this.clearDocs = function(){
        this.models = {};
        this.fullCollection = false;
        this.changeDocReference();
        this.saveToLocalStorage();
    };
    this.reset = function(){
        this.clearDocs();
        this.timestamp = null;
        window.localStorage.setItem('docsTimeStamp', JSON.stringify({
            login : userConfig.login,
            timestamp : null
        }));
        this.length = null;
        this.ws.ping();
    };
    this.deleteDocument = function(id, wait){
        var temp;
        if(window.isArray(id)){
            temp = [];
            for(var i = 0, length = id.length; i < length; i++){
                this.deleteDocument(id[i], true);
            }
            if(!wait && userConfig.docList === 'trash'){
                window.documentsTrashCollection.renderDocuments();
            }else if(!wait){
                this.renderDocuments();
            }
            this.saveToLocalStorage();
        }else{
            if(!this.models[id]){return;}
            temp = this.models[id].attributes;
            if(!temp){return;}
            temp.status = temp.status + 10;
            window.documentsTrashCollection.addDocument(temp, wait);
            delete this.models[id];
            this.length -= 1;
            if(!wait && userConfig.docList === 'trash'){
                window.documentsTrashCollection.renderDocuments();
            }else if(!wait){
                this.renderDocuments();
                this.saveToLocalStorage();
            }
        }
        return temp;
    };
    this.setTimerOnWS = function(){
        this.failTimer.push(window.setTimeout(this.loadDocument.bind(this), 5000)); //wait 5 seconds to go
    };
    this.deleteTimerOnWS = function(){
        for(var i = 0, length = this.failTimer.length; i < length; i++){
            window.clearTimeout(this.failTimer[i]);
        }
        this.failTimer = [];
    };
    this.loadDocument = function(){
        if(this.ws.close) {
            this.ws.close('4999', 'Timeout: haven\'t got response in time.');
        }
        this.deleteTimerOnWS();
        this.clearDocs();
        this.httpMode();
    };
    /*
     * TODO FUNCTIONS THAT SHOULD BE REFACTORED
     * for now it is just transphere to actionDoc
     * */
    this.downloadSignReport = function(){
        return actionDoc.downloadSignReport.apply(this, arguments);
    };
    this.upload = function(files){
        return actionDoc.upload(files);
    };
    this.downloadDocWithSign = function(){
        return actionDoc.downloadDocWithSign.apply(this, arguments);
    };
    this.drawSignList = function(signs){
        return actionDoc.drawSignList(signs);
    };
    this.getEmptyDescriptionByGroup = function(docList){
        return actionDoc.getEmptyDescriptionByGroup(docList);
    };

    
    /*actual logic*/
    return this.initialize(models, options);
};