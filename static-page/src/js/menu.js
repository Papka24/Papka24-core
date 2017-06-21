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


