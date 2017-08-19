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

var actionLogin = {
    goToLogin: function () {
        actionLogin.scrollToSection(0, function () {
            byId("loginForm").style.left = getOffset(byId("loginLink")).left - 240 + "px";
            byId("loginForm").style.top = getOffset(byId("loginLink")).top + 55 + "px";
            if (hasClass(byId("loginForm"), "hide")) {
                rmClass(byId("loginForm"), "hide");
            } else {
                addClass(byId("loginForm"), "hide");
                if (parseInt(byId("captcha").style.top) < 200) {
                    addClass(byId("captcha"), "hide");
                }
            }
            byTag(byId("loginForm"), "INPUT")[0].focus();
        });
    },
    startRegistration: function () {
        actionLogin.parent = this.parentNode;
        var parent = this.parentNode;
        var inputs = byTag(parent, "INPUT");
        var json = {
            name: inputs[0].value,
            login: inputs[1].value,
            password: inputs[2].value
        };
        foreach(byClass(parent, "error"), function (el) {
            addClass(el, "hide")
        });

        var hasError = false;
        if (json.name.length < 5 || json.name.indexOf(" ") < 2 || json.name.indexOf(" ") > (json.name.length - 4)) {
            rmClass(byClass(parent, "error")[0], "hide");
            hasError = true;
        }
        if (json.login.length < 6 || !emailRegexp.test(json.login)) {
            rmClass(byClass(parent, "error")[1], "hide");
            hasError = true;
        }
        if (json.password.length < 6) {
            rmClass(byClass(parent, "error")[2], "hide");
            hasError = true;
        }
        if (!hasError) {
            actionLogin.captchaReset();
            byId("captcha").style.left = getOffset(parent).left + 90 + "px";
            byId("captcha").style.top = parent.offsetTop + 100 + "px";
            rmClass(byId("captcha"), "hide");
            foreach(inputs, function (el) {
                addClass(el, "hide")
            });
            foreach(byClass(parent, "error"), function (el) {
                addClass(el, "hide");
            });
            addClass(byTag(parent, "BUTTON")[0], "hide");
            addClass(byClass(parent, "smallText", "DIV")[0], "hide");

            actionLogin.recaptchaCallback = function () {
                var parent = parent;
                return function () {
                    json.captcha = grecaptcha.getResponse();
                    addClass(byId("captcha"), "hide");
                    ajax("/api/reg/", function () {
                        rmClass(byClass(parent, "title", "DIV")[0], "hide");
                        rmClass(byClass(parent, "fox", "DIV")[0], "hide");
                    }, JSON.stringify(json), function (errorCode) {
                        // TODO добавить понятные описания ошибок
                        foreach(byTag(actionLogin.parent, "INPUT"), function (el) {
                            rmClass(el, "hide")
                        });

                        rmClass(byTag(actionLogin.parent, "BUTTON")[0], "hide");
                        rmClass(byClass(actionLogin.parent, "error")[3], "hide");
                        rmClass(byClass(actionLogin.parent, "smallText")[0], "hide");
                        if (errorCode == 409) {
                            byClass(actionLogin.parent, "error")[3].innerHTML = "Вказанний email вже зареєстрований<i class='fa fa-exclamation-triangle fa-lg'></i>";
                        } else {
                            byClass(actionLogin.parent, "error")[3].innerHTML = "Помилка сервера реєстрації " + errorCode + " <i class='fa fa-exclamation-triangle fa-lg'></i>";
                        }
                    });
                }
            }();
            actionLogin.captchaReset();
        }
    },
    finishRegistration: function () {
        if (window.location.hash.length >= 20) {
            var secret = window.location.hash.substr(1);
            if (secret.indexOf("?") > 0){
                secret = secret.substring(0, secret.indexOf("?"));
            }
            ajax("/api/reg/", function (login) {
                actionCore.startToWork(login);
            }, secret, function (errorCode) {
                if (errorCode == 409) {
                    drawWarning("Користувач вже зареєстрований в сервісі");
                } else if (errorCode == 404) {
                    drawWarning("Заявка вже була використана або не існує");
                } else {
                    drawWarning("Непередбачена помилка сервісу " + errorCode);
                }
                window.history.replaceState({renderMode: "login"}, "", "/");
            }, "PUT");
        }
    },
    captchaReset: function () {
        if (typeof actionLogin.grecaptcha != "undefined" && actionLogin.grecaptcha != null) {
            grecaptcha.reset(actionLogin.grecaptcha);
        } else {
            actionLogin.grecaptcha = grecaptcha.render("captcha", {
                sitekey:"6Lf6eCcUAAAAAAjxthnI5pwRnYGCP9i_Z-t3cEs7",
                callback: function () {
                    if (typeof actionLogin.recaptchaCallback == "function") {
                        actionLogin.recaptchaCallback();
                    }
                }
            });
        }
    },
    startRestorePassword: function () {
        if (emailRegexp.test(byTag(byId("loginForm"), "INPUT")[0].value)) {
            byId("captcha").style.left = getOffset(byId("loginForm")).left + "px";
            byId("captcha").style.top = byTag(byId("loginForm"), "button")[0].offsetTop + 50 + "px";
            rmClass(byId("captcha"), "hide");
            actionLogin.recaptchaCallback = function () {
                return function () {
                    addClass(byId("captcha"), "hide");
                    ajax("/api/reg/restore/" + byTag(byId("loginForm"), "INPUT")[0].value, function () {
                        drawMessage("<br>На " + byTag(byId("loginForm"), "INPUT")[0].value + " надіслано email для зміни пароля<br><br>");
                        actionLogin.goToLogin();
                    }, grecaptcha.getResponse(), function (errorCode) {
                        if (errorCode == 404) {
                            drawWarning("Користувач не знайдений серед зареєстрованих в сервісі");
                        } else {
                            drawWarning("Непередбачена помилка сервісу" + errorCode);
                        }
                    }, "POST");
                }
            }();
            actionLogin.captchaReset();
        } else {
            drawWarning("Введіть будь ласка коректний email");
        }
    },
    restorePassword: function (secret) {
        drawMessage("<br><br><input type='password' min='6' placeholder='Новий пароль' id='newPassword'>" +
            "<button id='newPasswordSaveBtn' class='pure-button'>Зберегти</button><br><div class='error hide' style='width:200px; margin-left:20px'>Мінімум 6 символів<i class='fa fa-exclamation-triangle fa-lg'></i></div><br><br><br>", 400, true);
        window.setTimeout(function () {
            addEvent(byId("newPasswordSaveBtn"), "click", function (e) {
                if (byId("newPassword").value.length < 6) {
                    rmClass(byClass(byId("newPassword").parentNode, "error")[0], "hide");
                } else {
                    ajax("/api/reg/restoreFinish/" + secret, function () {
                        drawMessage("<br>Пароль успішно змінений!<br><br>");
                        window.history.replaceState({renderMode: "login"}, "", "/");
                    }, byId("newPassword").value, function (errorCode) {
                        if (errorCode == 404) {
                            drawWarning("Заявка вже була використана або не існує");
                        } else {
                            drawWarning("Непередбачена помилка сервісу " + errorCode);
                        }
                        window.history.replaceState({renderMode: "login"}, "", "/");
                    }, "POST");
                    byId("messageBG").click();
                }
            })
        }, 10);
    },
    startPos: null,
    timer: 0,
    autoScroll: false,
    scrollToSection: function (endPos, fun) {
        actionLogin.startPos = byId("startPage").scrollTop;
        actionLogin.autoScroll = true;
        actionLogin.timer = window.setInterval(function () {
            if (actionLogin.startPos > endPos) {
                actionLogin.startPos -= Math.ceil((actionLogin.startPos - endPos) / 20);
                actionLogin.scrollToPost(byId("startPage"), actionLogin.startPos);
                if (actionLogin.startPos <= endPos) {
                    window.setTimeout(function () {
                        actionLogin.autoScroll = false;
                        if (typeof fun === "function") {
                            fun();
                        }
                    }, 10);
                    clearInterval(actionLogin.timer);
                }
            } else {
                actionLogin.startPos += Math.ceil((endPos - actionLogin.startPos) / 20);
                actionLogin.scrollToPost(byId("startPage"), actionLogin.startPos);
                if (actionLogin.startPos >= endPos || (byId("startPage").innerHeight + byId("startPage").scrollY) > Math.max(byId("startPage").scrollHeight, byId("startPage").offsetHeight)) {
                    window.setTimeout(function () {
                        actionLogin.autoScroll = false;
                        if (typeof fun === "function") {
                            fun();
                        }
                    }, 10);
                    clearInterval(actionLogin.timer);
                }
            }
        }, 10);
    },
    scrollToPost: function (el, y) {
        if (typeof el.scrollTo === "function") {
            el.scrollTo(0, y);
        } else {
            el.scrollTop = y;
        }
    },
    drawFAQ: function () {
        drawMessage({url:"/info/faq.html"}, 800, true, 500);
    },

    drawLicence: function(){
        drawMessage({url:"/info/ui.html"}, 800, true, 500);
    },

    drawCompanyCreation: function(){
        drawMessage({url:"/info/cc.html"}, 600, true, 440);
    },

    drawLawFAQ: function () {
        drawMessage({url:"/info/lawfaq.html"}, 800, true, 500);
    },

    openChat:function(){

        if (typeof SenderWidget === "undefined" || SenderWidget === null){
            window.senderCallback = function() {
                SenderWidget.init({
                    companyId: "i33169048572",
                    showButton: false,
                    autostart: false
                });
            };

                (function(d, s, id) {
                    var js, fjs = d.getElementsByTagName(s)[0];
                    js = d.createElement(s);
                    js.id = id;
                    js.src = "https://widget.sender.mobi/build/init.js";
                    fjs.parentNode.insertBefore(js, fjs, 'sender-widget');
                    setTimeout(function(){SenderWidget.showWidget()}, 500);
                })(document, 'script');


        } else {
            SenderWidget.showWidget()
        }
    }
};