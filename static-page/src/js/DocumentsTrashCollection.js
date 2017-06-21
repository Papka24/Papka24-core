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

/**
 * DocumentsTrashCollection
 * stores trash
 */
var DocumentsTrashCollection = function(models, options){
    "use strict";
    this.renderCount = 0;
    this.initialize = function(models){
        this.docReference = models || [];
        this.models = this.docReference.reduce(function(memo, el, i){ //TODO check performance, cache into LS?
            el.index = i;
            memo[el.id] = new DocumentsModelView(el);
            return memo;
        }, {});
        this.length = this.docReference.length || null;
        this.justRenderedDocs = [];
        this.limit = 50;
        var temp = (((window.location.pathname.match(/\/page\/(\d+)$/) || [])[1] || 0) - 1) * this.limit;
        this.offset = temp > 0 && temp < this.length ? temp : 0;
        this.fullCollection = this.fullCollection || false;
        addEvent(byId('jsTitle'), 'mousemove', this.titleHandler);
        addEvent(byId('contentBlock'), 'mouseout', this.titleHandler);
        addEvent(byId('contentBlock'), 'mousemove', this.titleHandler);
        //this.loadDocument();
        return this;
    };
    this.next = function(){
        var offset = this.offset + this.limit;
        actionDoc.selected = 0;
        this._navigation(offset);
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
    /**
     * @doc - array or object
     * @doNotRender - boolean. Similar to "wait" in Backbone
     * */
    this.addDocument = function(doc, doNotRender){
        doNotRender = doNotRender || this.doNotRenderCheck();
        doNotRender = doNotRender || this.doNotRenderCheck();
        if(!doc){return;}
        if(window.isArray(doc)){
            for(var i = 0, length = doc.length; i < length; i++){
                this.addDocument(doc[i], true);
            }
            if(!doNotRender) {
                this.renderDocuments();
            }
        }else{
            if(doc.status < 20){
                if(this.models[doc.id]){
                    doc.index = this.models[doc.id].attributes.index;
                    this.models[doc.id].set(doc);
                }else{
                    doc.index = ++this.length - 1;
                    this.models[doc.id] = new DocumentsModelView(doc);
                }
                this.models[doc.id].render();
                this.docReference[doc.index] = this.models[doc.id].attributes;
            }else{
                delete this.models[doc.id];
            }
            if(!doNotRender){
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
        }
        this.justRenderedDocs = docs;
        if(!this.renderCount){
            this.renderDocumentList(docs, true);
        }
    };
    this.deleteDocument = function(id, wait){
        var temp;
        if(window.isArray(id)){
            temp = [];
            for(var i = 0, length = id.length; i < length; i++){
                this.deleteDocument(id[i], true);
            }
        }else{
            if(!this.models[id]){return;}
            temp = this.models[id].attributes;
            if(!temp){return;}
            temp.status = temp.status + 10;
            delete this.models[id];
            this.length -= 1;
        }
        return temp;
    };
    this.restoreDocument = function(id, wait){
        var temp, tempEl;
        if(window.isArray(id)){
            temp = [];
            for(var i = 0, length = id.length; i < length; i++){
                this.restoreDocument(id[i], true);
            }
            if(userConfig.docList === 'trash'){
                this.renderDocuments();
            }else{
                window.documentsCollection.renderDocuments();
            }
        }else{
            if(!this.models[id]){return;}
            temp = this.models[id].attributes;
            if(!temp){return;}
            temp.status = temp.status % 10;
            if(window.documentsCollection.fullCollection){
                window.documentsCollection.addDocument(temp, true);
            }else{
                window.documentsCollection.clearDocs();
            }
            delete this.models[id];
            this.length -= 1;
            if(!wait){
                if(userConfig.docList === 'trash'){
                    this.renderDocuments();
                }else{
                    window.documentsCollection.renderDocuments();
                }
            }
        }
        return temp;
    };
    this.doNotRenderCheck = function(){
        var res = false;
        if(window.location.pathname.indexOf('/list/trash') === -1 && window.location.pathname.indexOf('/list/docs') === -1 && window.location.pathname != '/' && window.location.pathname){
            res = true;
        }
        return res;
    };
    this.renderDocuments = function(docs){
        actionDoc.selected = 0;
        this.renderCount = 0;
        if(docs){
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
        // TODO FIX IT! sometime return false
        var table = userConfig.docRender === 'table' ? document.querySelector('.mainTableWrapper > table') : document.querySelector('.documentsWrapper');
        if(table){
            while (table.firstChild) {
                table.removeChild(table.firstChild);
            }
        }
        this.httpMode(this.offset + this.renderCount, this.limit - this.renderCount);
    }, 100, {leading : false});
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

        this.renderDocuments();
    };
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
    this.httpMode = this._throttle(function(offset, limit){
        if(userConfig.docList != 'trash'){return;}
        if(typeof window.ajaxAbort === 'object' && window.ajaxAbort){
            window.ajaxAbort.abort();
        }
        if(this.fullCollection){
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
                signed : signed[userConfig.docFilter.docSignFilter],
                dateFrom : dateFrom,
                dateTo : dateTo,
                docList : userConfig.docList,
                tagFilter : userConfig.tagFilter,
                offset : offset || this.offset,
                limit : limit || this.limit
            };
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
                if(this.offset > 0 && !this.renderCount){
                    this.offset -= this.limit;
                    if(this.offset < 0){this.offset = 0;}
                    this._navigation(this.offset);
                    this.toggleNavButtons(true);
                    return;
                }
                if(!this.renderCount){
                    this.renderDocuments([]);
                    this.toggleNavButtons(true);
                }
                return;
            }
            this.addDocument(docs, true);
            this.justRender(docs);
            this.toggleNavButtons();
        }.bind(this), searchObj, function(e){
            if(!this.renderCount){
                this.renderDocuments([]);
            }
        }.bind(this));
    }, 100, {leading : false});
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
                name : ''
            });
        }
        if(target){
            this.fancyAutocomplete = new FancyAutocomplete(mainArr, target, keys);
        }
    };
    this.deleteDocument = function(id, wait){
        var temp;
        if(window.isArray(id)){
            temp = [];
            for(var i = 0, length = id.length; i < length; i++){
                temp.push(this.deleteDocument(id[i], true));
            }
            this.addDocument(temp, wait);
        }else{
            if(!this.models[id]){return;}
            temp = this.models[id].attributes;
            if(!temp){return;}
            temp.status = temp.status + 10;
            this.addDocument(temp, wait);
        }
        return temp;
    };
    /*actual logic*/
    return this.initialize(models, options);
};