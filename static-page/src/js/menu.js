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

m = {
    otp:null,
    init: function () {

        var btns = byClass(byId("newPassword").parentNode,"closeButton");
        addEvent(btns[0],"click", function() {
            if (byId("newPassword").value.length < 6) {
                setTimeout(function () {
                    drawWarning("Пароль повинен бути мінімум 6 символів")
                }, 50)
            } else {
                ajax("/api/login/password", function () {
                    drawMessage("<br>Пароль змінено <i class='fa fa-smile-o fa-lg'></i><br><br>");
                }, byId("newPassword").value, function () {
                }, "PUT");
                byId("messageBG").click();
            }
        });

        addEvent(btns[1],"click", function() {
            userConfig.fullName = byId("newName").value;
            byId("userName").innerHTML=byId("newName").value;
            ajax("/api/login/name",function(){
            }, byId("newName").value,function(){},"PUT");
            byId("messageBG").click();
        });

        byId("newName").value=userConfig.fullName;

        if (localStorage.getItem("CryptoPluginTimeStamp")==null || localStorage.getItem("CryptoPluginTimeStamp")=="normal"){
            byId("addTimeStamp").checked=true;
        } else if (localStorage.getItem("CryptoPluginTimeStamp")=="systemTime") {
            byId("addSigningTime").checked=true;
        } else {
            byId("addNoTimeStamp").checked=true;
        }

        if(userConfig.enableOTP){
            byId("enableGoogleAuth").checked= true;
        } 

        addEvent(byId("enableGoogleAuth"), "click", function(){
            var checker = this;
            if(checker.checked){
                byId("qrGoogleAuth").src = "/img/three-dots.svg";
                ajax("/api/login/otp/",function(result){
                    m.otp = result;
                    rmClass(byId("qrGoogleAuthConfig"), "hide");
                    addClass(byId("mainMenuBlock"), "hide");
                    var data =  "otpauth://totp/Papka24:"+userConfig.login+"?secret="+result+"&issuer=Papka24";
                    byId("qrGoogleAuth").src = "https://api.qrserver.com/v1/create-qr-code/?size=150x150&data="+data;
                }, null, function(){
                    checker.checked = true;
                },"GET");
            } else {
                ajax("/api/login/otp/",function(){
                    addClass(byId("qrGoogleAuthConfig"), "hide");
                }, null, function(){
                    checker.checked = true;
                },"DELETE");
            }
        });

        addEvent(byId("addTimeStamp"), "click", function(){
            localStorage.setItem("CryptoPluginTimeStamp", "normal");
        });

        addEvent(byId("addSigningTime"), "click", function(){
            localStorage.setItem("CryptoPluginTimeStamp", "systemTime");
        })

        addEvent(byId("addNoTimeStamp"), "click", function(){
            localStorage.setItem("CryptoPluginTimeStamp", "noTime");
        });
    },
    setOTP: function(){
        if (byId("newOTPPassword").value.length==6) {
            ajax("/api/login/otp/"+byId("newOTPPassword").value, function () {
                drawMessage("<br>Двоетапна перевірка підключена.<br><br>")
            }, m.otp, function (e) {
                alert("Невірний код. Можливо необхідно сінхронізувати час на вашому девайсі.");
            }, "PUT");
        }
    }
};


