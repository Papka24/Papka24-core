var DocumentChat = function(options){
    "use strict";
    this.initialize = function(options){
        this.failTimer = [];
        this.chatId = options.id;
        this.company = null;
        this.employee = null;
        this.hidden = true;
        this.messages = {};
        this.length = 0;
        this.el = buildNode('DIV', {className : 'DocumentChat'}, [
            buildNode('DIV', {className : 'chatContent'}),
            buildNode('DIV', {className : 'chatControls'}, [
                buildNode('TEXTAREA', {className : 'chatInput', placeholder : 'Текст повідомлення'}),
                buildNode('BUTTON', {className : 'chatSend pure-button'}, [
                    buildNode('I', {className : 'fa fa-paper-plane-o'})
                ])
            ])
        ]);
        window.addEventListener('popstate', this.remove.bind(this));
        addEvent(this.el.querySelector('.chatSend'), 'click', this.sendMessage.bind(this));
        addEvent(this.el.querySelector('.chatInput'), 'keydown', this.handleEnter.bind(this));
        // Hack, for load chat after sign and share
        window.setTimeout(function(){
            this.loadChatHistory()
        }.bind(this), 100);
        window.documentChat = this; //fixme хуита же
        return this;
    };
    this.remove = function(){
        window.removeEventListener('popstate', this.remove.bind(this));
        window.documentChat = null;
        if (this.el.parentNode) { // fix error {"message":"TypeError: this.el.parentNode is null","file":"https://papka24.com.ua/js/all.js","line":6,"col":7311,"error":{}} url: 'https://papka24.com.ua/list/docs'
            this.el.parentNode.removeChild(this.el);
        }
    };
    this.addMessage = function(message){
        if(window.isArray(message)){
            for(var i = 0, length = message.length; i < length; i++){
                this.addMessage(message[i]);
            }
        } else {
            this.lastAuthor = this.lastAuthor || "";
            if(!this.messages[message.time]) {
                message.newAuthor = message.login == this.lastAuthor;
                this.lastAuthor = message.login;
                this.messages[message.time] = new this.MessageModel(message);
                this.el.querySelector('.chatContent').appendChild(
                    this.messages[message.time].el
                );
                this.length += 1;
            }else {
                this.messages[message.time].set(message);
                this.messages[message.time].render();
            }
            this.messages[message.time].el.scrollIntoView(false);
        }
    };
    this.sendMessage = function(){
        var valueTarget = this.el.querySelector('.chatInput'),
            data = valueTarget.value;
        if(!valueTarget.value){return;}
        ajax('/api/chat/'+this.chatId, function(json){
            var parsed = JSON.parse(json);
            this.addMessage(parsed);
        }.bind(this), data, function(){
            //TODO error handling
        }, 'POST');
        valueTarget.value = '';
    };
    this.handleEnter = function(e){
        if(e && e.keyCode === 13){
            e.preventDefault();
            this.sendMessage();
            return false;
        }
    };
    this.webSocketListener = function(parsed){
        this.addMessage(parsed);
    };
    this.MessageModel = function(attr){ //костылёк, нужно в отдельном файле
        this.initialize = function(initAttr){
            this.attributes = {
                login : '',
                time : null, //number
                text : '',
                humanReadable : '',
                imageUrl : '',
                failImageUrl : '',
                title : ''
            };
            this.set(initAttr);
            this.el = buildNode('p');
            this.render();
            addEvent(this.el, 'click', this.onclick.bind(this));
            return this;
        };
        this.onclick = function(e){
            var target = e.target;
            if(hasClass(target, 'messageDelete') || hasClass(target.parentNode, 'messageDelete')){
                this.remove();
            }
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
        this.render = function(){
            var template = _.template('<div class="messageAuthor" title="<%=login%>"><img src="<%=imageUrl%>" title="<%=title%>" onerror="this.onerror = null; this.src = \'<%=failImageUrl%>\';"></div><span class="message"><%=text%></span><span class="messageTime"><%=humanReadable%></span><span class="messageDelete" title="Видалити повідомлення"><i class="fa-times fa"></i></span>'),
                temp;
            this.set('humanReadable', this.convertToHumanReadableFormat(this.attributes.time));
            temp = this.get('login');
            if(userConfig.friends && typeof userConfig.friends == 'object'){
                temp = userConfig.friends[temp] || temp;
            }
            this.set('title', temp);
            this.set('imageUrl', userConfig.cdnPath + "avatars/" + Sha256.hash(this.get('login')) + ".png");
            this.set('failImageUrl', "https://secure.gravatar.com/avatar/" + MD5(this.get('login')) + "?d=mm");
            if (!this.get('newAuthor')) {
                addClass(this.el, 'logo');
            }
            this.el.innerHTML = template(this.attributes);
            if(this.get('login') === userConfig.login){
                addClass(this.el, 'authorMessage');
            }
        };
        this.remove = function(){
            ajax(
                '/api/chat/'+window.documentChat.chatId+'/'+this.get('time'),
                function(){
                    if(hasClass(this.el, 'logo') && hasClass(this.el.nextSibling, 'authorMessage')){
                        addClass(this.el.nextSibling, 'logo');
                    }
                    this.el.parentNode.removeChild(this.el);
                }.bind(this),
                '',
                function(){},
                'DELETE'
            );

        };
        this.convertToHumanReadableFormat = function(timestamp){
            var date = new Date(timestamp),
                todayDate = new Date(),
                yesterday = (new Date(todayDate.getFullYear(), todayDate.getMonth(), todayDate.getDate() - 1)).getTime(),
                today = (new Date(todayDate.getFullYear(), todayDate.getMonth(), todayDate.getDate())).getTime();
            if(timestamp >= today){
                return  'Сьогодні ' + date.toTimeString();
            } else if(timestamp >= yesterday){
                return 'Вчора ' + date.toTimeString();
            } else {
                return date.toTimestampString()
            }
        };
        return this.initialize(attr);
    };
    this.loadChatHistory = function(){
        ajax('/api/chat/'+this.chatId, function(json){
            var parsed = JSON.parse(json);
            if(parsed.length > 0){
                rmClass(this.el.parentNode.parentNode, 'inactive'); //fixme should be better
            }
            parsed.sort(function (a, b) {
                if (a.time > b.time) {
                    return 1;
                } else if (a.time < b.time) {
                    return -1;
                } else {
                    return 0;
                }
            });
            this.addMessage(parsed);
        }.bind(this));
    };
    return this.initialize(options);
};