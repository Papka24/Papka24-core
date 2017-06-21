/**
 * WebSock class used for websocket connetion
 * */
var WebSock = function(fullUrl, listener, closeCallback){
    "use strict";
    /*
     * todo on send/close - return promise
     * todo Callbacks are evil! Use Events instead so you could listen in your code
     * */
    if(!fullUrl){throw new Error('Url must be specified');}
    if(!listener){throw new Error('No listener for opened WebSocket');}
    this.closed = false;
    this.queue = [];
    this.failCount = 0;
    this.onMessage = function(e){
        var raw = e.data,
            parsed = JSON.parse(raw);
        listener(parsed);
    };
    this.openWebSocket = function(){
        if(this.failCount > 3){
            this.close('1000', 'could not connect');
            return;
        }
        try{
            this.ws = new WebSocket(fullUrl);
            this.ws.onmessage = this.onMessage.bind(this);
            this.ws.onclose = this.onClose.bind(this);
            return true;
        }catch(e){
            ++this.failCount;
            console.error('Can\'t open WebSocket connection'); //todo throw error?
            return false;
        }
    };
    this.onClose = function(){ //todo parse and log arguments?
        if(closeCallback){
            closeCallback();
        }else if(!this.closed){ //reopen websocket
            window.setTimeout(this.openWebSocket.bind(this), 1000);
        }
    };
    this.send = function(obj, doNotTouchMyData){
        if(!obj){throw new Error('no arguments provided');}
        obj = doNotTouchMyData ? obj : JSON.stringify(obj);
        this.queue.push(obj);
    }.bind(this);
    this.close = function(code, reason){
        if(this.ws && this.ws.close){
            this.ws.close(code, reason);
        }
        if(closeCallback){
            closeCallback();
        }
        this.closed = true;
    };
    this.processQueue = function(){
        if(this.closed || this.failCount > 3){
            this.closed = true;
            window.clearInterval(this.interval);
            return;
        }
        var temp;
        while(temp = this.queue.shift()){ //first in first out
            try {
                this.ws.send(temp);
            }catch(e){
                console.warn('Websockets send error', arguments);
                ++this.failCount;
                this.queue.unshift(temp);
                window.setTimeout(this.openWebSocket.bind(this), 1000);
                break;
            }
        }
    };
    if(this.openWebSocket()){
        this.ws.onopen = this.onOpen.bind(this); //fixme can cause issues if send or close will happen before onopen
    }
    this.interval = window.setInterval(this.processQueue.bind(this), 100);
    return this;
};
WebSock.prototype = {
    pingTimer : 300000, // 5 minutes
    ping : function(){
        this.send('ping');
    },
    onOpen : function (){
        if(!this.closed){
            this.ping();
            setTimeout(this.onOpen.bind(this), this.pingTimer); //ping each 5 minutes
        }
    }
};