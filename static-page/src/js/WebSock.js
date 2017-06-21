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