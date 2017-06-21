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

/*
 * Widget Utils
 */
function drawWarning(text) {
    drawMessage("<div style='padding:20px;align-items:center;display:flex;justify-content:center;'><i class='fa fa-exclamation-triangle fa-lg' style='position: relative;left: -10px'></i>&nbsp;&nbsp; " + text + "</div>");
}

function drawInfo(text) {
    drawMessage("<div style='padding:20px;align-items:center;display:flex;justify-content:center;'><i class='fa fa-info fa-lg' style='position: relative;left: -10px; color:#5c9d21'></i>&nbsp;&nbsp; " + text + "</div>");
}
var drawChangeInfoTimer;
function drawChangeInfo(text, cancelFunction, time, options) {
    options = options || {};
    options.cancelText = options.cancelText || 'Скасувати';
    options.cancelClickText = options.cancelClickText || 'Дію скасовано';
    options.noCancelPopup = options.noCancelPopup || false;
    if (drawChangeInfoTimer){
        clearTimeout(drawChangeInfoTimer);
    }
    rmClass(byId("eventMessage"),"hideEvent");
    addClass(byId("eventMessage"),"bounceInDown");
    var block = byId("eventMessage");
    block.innerHTML = "<div>" + text + "</div>";
    block.style.height = byTag(block, "DIV")[0].offsetHeight - 5 + "px";
    block.style.left = (window.innerWidth || document.body.clientWidth)/2 - byTag(block, "DIV")[0].offsetWidth / 2 + "px";
    if (typeof cancelFunction === "function"){
        byTag(block, "DIV")[0].appendChild(buildNode("A",{},options.cancelText,{click:function(){
            cancelFunction();
            if(!options.noCancelPopup) {
                rmClass(byId("eventMessage"), "bounceInDown");
                addClass(byId("eventMessage"), "hideEvent");
                setTimeout(function () {
                    drawChangeInfo(options.cancelClickText, null, 1000);
                }, 50);
            }
        }}));
    }
    drawChangeInfoTimer = setTimeout(function(){
        rmClass(byId("eventMessage"),"bounceInDown");
        addClass(byId("eventMessage"),"hideEvent");
    },time);
}

function drawMessage(text, width, disableCloseByClick,height, successCallback) {
    if (typeof text === "object"){
        if (text.hasOwnProperty("url")){
            // load from internet mode
            ajax(text.url, function(result){
                if(result.indexOf("<!doctype")==0){
                    byId("messageBG").click();
                } else {
                    byId("message").innerHTML = "<div>" + result + "</div>";
                    setTimeout(function(){
                        if (typeof successCallback == "function"){
                            successCallback();
                        }
                    },0);
                }
            },null,function(){                
                byId("message").innerHTML = "<div>iнформацiя не знайдена</div>";
            });
            byId("message").innerHTML = "<div style='margin:200px 330px'>"+loaderSnippet+"</div>";
        } else {
            // add dom element
            byId("message").innerHTML ="";
            byId("message").appendChild(text);
        }
    } else {
        byId("message").innerHTML = "<div>" + text + "</div>";
    }
    addClass(byId("messageBG"), "active");
    if (width === void 0) {
        byId("message").style.width = "500px";
    } else {
        byId("message").style.width = width + "px";
    }
    if (disableCloseByClick) {
        byId("message").onclick = stopBubble;
    } else {
        byId("message").onclick = function () {
        };
    }
    if (height === void 0){
        byId("message").style.height = byTag(byId("message"), "DIV")[0].offsetHeight - 5 + "px";
    } else {
        byId("message").style.height = height + "px";
    }
    byId("message").style.overflow="hidden";
}
