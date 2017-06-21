cm = {
    init: function () {
        if (!hasClass(byClass(byId("header"),"companyBoxMenu")[0],"hide")){
            addClass(byClass(byId("header"),"companyBoxMenu")[0],"hide");
        }
        if (userConfig.company.iamboss) {
            var list = [];
            for (var i in userConfig.friends) {
                if (userConfig.friends.hasOwnProperty(i) && userConfig.friends[i] != "") {
                    list.push([i, 0, userConfig.friends[i]]);
                }
            }
            new Autocomplete(byId("emailEmployeeInput"), list, 0, function () {
            }, function () {
            });
            var btns = byTag(byId("emailEmployeeInput").parentNode, "BUTTON");

            addEvent(btns[0], "click", function (e) {
                stopBubble(e);
                cm.invite(byId('emailEmployeeInput').value, 1);
            });
            addEvent(btns[1], "click", function (e) {
                stopBubble(e);
                cm.invite(byId('emailEmployeeInput').value, 0);
            });
            addEvent(byId("emailEmployeeInput"),"onkeyup",function(){
                //console.log(byId("emailEmployeeInput").value);

            });
            addEvent(byTag(byId("companyNameSetter").parentNode,"BUTTON")[0], "click", cm.changeName);

        } else {
            byId("emailEmployeeInput").parentNode.style.display = "none";
        }
        byId("companyNameSetter").value = userConfig.company.name;
        cm.drawEmployeeList();
    },

    changeName:function(){
        ajax("/api/company/name",function(result){
            userConfig.company.name = result;
            byTag(byId("companyName"),"SPAN")[0].innerHTML = result;
            drawMessage("<br>Назви змінено на "+result+"<br><br>");
        }, byId("companyNameSetter").value, function(){},"PUT")
    },

    invite: function (email, mode) {
        
        email = email || '';
        var emails = [];
        if (email.indexOf(',') > 0){
            email = email.replace(/\s+/g, '').split(',');
            // multiemail mode
            foreach(email, function(e){
                if (emailRegexp.test(e) && e != userConfig.login){
                    emails.push(e);
                }
            });
        } else {
            if (emailRegexp.test(email) && email != userConfig.login){
                emails.push(email);
            }
        }

        if (emails.length>0) {
            var request = [];
            foreach(emails, function(e){
                request.push({email:e, role: mode});
            });
            ajax("/api/company/invite", function (result) {
                userConfig.company.employee = JSON.parse(result);
                cm.drawEmployeeList();
            }, JSON.stringify(request), function () {})
        }
        byId("emailEmployeeInput").value = "";
    },

    fire: function (email, status) {
        if (userConfig.company.iamboss && email != userConfig.login) {
            if (confirm((status == 0 ? "Відізвати запрощення людини?" : "Видалити людину з групи?"))) {
                ajax("/api/company/remove/" + email, function (result) {
                    userConfig.company.employee = JSON.parse(result);
                    cm.drawEmployeeList();
                }, null, function () {}, "DELETE")
            }
        } else {
            cm.leave();
        }
    },

    leave: function(email){
        if(typeof email == "undefined" || !email){
            email = userConfig.login;
        }
        if (confirm("Вийти? Увага, документи отримані в компанії будуть недоступні.")) {
            ajax("/api/company/remove/" + email, function () {
                userConfig.company.login = userConfig.login;
                window.documentsCollection.reset();
                if (byId("messageBG")) {
                    byId("messageBG").click();
                }
            }, null, function (e) {
                console.log(e);
            }, "DELETE")
        }
    },

    deleteGroup:function(){
        if (confirm("Вийти з групи та розформувати її? (усі документи повернуться до авторів)")) {
            ajax("/api/company/drop/", function () {
                userConfig.company.login = userConfig.login;
                window.documentsCollection.reset();
                userConfig.company.id = null;
                userConfig.company.name = null;
                userConfig.company.employee = null;
                byTag(byId("companyName"),"SPAN")[0].innerHTML = "Створити групу";
                addClass(byTag(byId("companyName"),"I")[0],"hide");
                if (byId("messageBG")) {
                    byId("messageBG").click();
                }
            }, null, function () {}, "DELETE")
        }
    },
    
    disableOtherListMode: function(){
        userConfig.company.login = userConfig.login;        
        byId("contentBlock").innerHTML = "<div style='margin:100px 40%'><img width='152' alt='' src='/img/ring.svg'></div>";
        byId("userName").innerHTML = userConfig.fullName;
        byId("docTagFilter").style.display = '';
        byTag(byId("menu"), "BUTTON")[0].style.display = '';
        byClass(byId("menu"), "dummy")[0].style.height = '';
        window.documentsCollection.reset();    
        if (byId("messageBG")) {
            byId("messageBG").click();
        }
    },

    enableCompanyListMode: function(){
        userConfig.company.login = "all";
        byId("userName").innerHTML = "Всі користувачі";
        byId("contentBlock").innerHTML = "<div style='margin:100px 40%'><img width='152' alt='' src='/img/ring.svg'></div>";
        history.replaceState({renderType: "list", docList: userConfig.docList}, "", "/list/" + userConfig.docList);
        window.documentsCollection.reset();

        byId("docTagFilter").style.display = 'none';
        byTag(byId("menu"), "BUTTON")[0].style.display = 'none';
        byClass(byId("menu"), "dummy")[0].style.height = '80px';
        if (byId("messageBG")) {
            byId("messageBG").click();
        }
    },
    selectUser:function(){
        if (!hasClass(byClass(byId("header"),"companyBoxMenu")[0],"hide")){
            addClass(byClass(byId("header"),"companyBoxMenu")[0],"hide");
        }
        addClass(byId("companyNameSetter").parentNode, "hide");
        if (byId("emailEmployeeInput")) {
            addClass(byId("emailEmployeeInput").parentNode, "hide");
            addClass(byTag(byId("emailEmployeeInput").parentNode.parentNode, "DIV")[0], "hide");
        }
        cm.drawEmployeeList(true);
    },
    drawEmployeeList: function (selectDocs) {
        if (typeof selectDocs == "undefined"){
            selectDocs = false;
        }
        byId("employeeTable").innerHTML = "";
        var employeesName = {};
        var employees = [];
        var missedLogin = null;
        var ddosLogin = null;
        var adminCount = 0;
        var workerCount = 0;
        foreach(userConfig.company.employee, function (e) {
            if (!employeesName.hasOwnProperty(e.login)) {
                if (e.status == -2) {
                    ddosLogin = e.login;
                } else if (e.status == -1) {
                    missedLogin = e.login;
                } else {
                    employeesName[e.login] = e.status;
                    employees.push(e);
                }
            } else {
                if (e.status == -1) {
                    missedLogin = e.login;
                } else {
                    foreach(employees, function (se) {
                        if (se.login == e.login && e.startDate > se.startDate) {
                            se.status = e.status;
                            se.startDate = e.startDate;
                            se.stopDate = e.stopDate;
                            se.initiator = e.initiator;
                            se.role = e.role;
                            return false;
                        }
                    })
                }
            }
        });
        foreach(userConfig.company.employee, function (e) {
            if (e.role == 0 && e.status == 1) {
                adminCount++;
            }
            if (e.status==1){
                workerCount++;
            }
        });

        employees.sort(function (a, b) {
            if (a.login == userConfig.login) {
                return -1;
            } else if (b.login == userConfig.login) {
                return 1;
            } else {
                return 0;
            }
        });

        foreach(employees, function (e) {
            /** @namespace e.startDate */
            /** @namespace e.stopDate */
            /** @namespace e.status */
            /** @namespace e.role */
            var style = {};
            if (userConfig.company.login == e.login && selectDocs) {
                style.borderLeft = "3px solid #5c9d21";
                style.cursor = "default";
            } else if (e.status != 0) {
                if (userConfig.company.iamboss) {
                    style.cursor = "pointer";
                }
            }

            var node = buildNode("TR", {style: style});

            if (selectDocs){
                addEvent(node, "click", function () {
                    history.replaceState({renderType: "list", docList: userConfig.docList}, "", "/list/" + userConfig.docList);
                    if (e.status == 0 || userConfig.company.login == e.login || !userConfig.company.iamboss) {
                        return;
                    }
                    userConfig.company.login = e.login;
                    byId("contentBlock").innerHTML = "<div style='margin:100px 40%'><img width='152' alt='' src='/img/ring.svg'></div>";
                    window.documentsCollection.reset();

                    if (userConfig.company.login == userConfig.login) {
                        byId("userName").innerHTML = userConfig.fullName;
                        byId("docTagFilter").style.display = '';
                        byTag(byId("menu"), "BUTTON")[0].style.display = '';
                        byClass(byId("menu"), "dummy")[0].style.height = '';
                    } else {
                        byId("userName").innerHTML = ((userConfig.friends.hasOwnProperty(e.login) && userConfig.friends[e.login] != "" ? userConfig.friends[e.login] : e.login) + " (Перегляд)");
                        byId("docTagFilter").style.display = 'none';
                        byTag(byId("menu"), "BUTTON")[0].style.display = 'none';
                        byClass(byId("menu"), "dummy")[0].style.height = '80px';
                    }
                    if (byId("messageBG")) {
                        byId("messageBG").click();
                    }
                });
            }

            node.appendChild(buildNode("TD", {}, ((e.login == userConfig.login) ? "Я" : e.login)));
            node.appendChild(buildNode("TD", {}, ((e.role == 1) ? "Учасник" : "Адміністратор")));
            var status = "";
            if (selectDocs && (e.status == 0 || e.status == 2)){
                return;
            }
            switch (e.status) {
                case 0:
                    status = "<span style='color:#ffb74d'>Отримав запрошення</span>";
                    break;
                case 1:
                    status = "<span style='color:#5c9d21'>У групі з " + new Date(e.startDate).toDatastampString() + "</span>";
                    break;
                case 2:
                    status = "<span style='color:#ba68c8'>Відмовився</span>";
                    break;
                case 3:
                    status = "<span style='color:#e57373'>Покинув " + new Date(e.stopDate).toDatastampString() + "</span>";
                    break;
            }
            node.appendChild(buildNode("TD", {}, status));
            var btns = [];

            if (e.login != userConfig.login && userConfig.company.iamboss) {
                if (e.status == 0 || e.status == 2) {
                    btns.push(buildNode("BUTTON", {
                        style: {marginRight: "5px"},
                        className: "pure-button",
                        title: "Вислати запрошення ще раз"
                    }, "<i class='fa fa-envelope-o'></i>", {
                        "click": function (e) {
                            stopBubble(e);
                            cm.invite(e.login, e.role);
                        }
                    }));
                    //btns += '<button onclick="cm.invite(\'' + e.login + '\', e.role)" class="pure-button" title="Вислати запрошення ще раз"><i class="fa fa-envelope-o"></i></button>&nbsp;'
                }
                if (e.status != 3) {
                    btns.push(buildNode("BUTTON", {
                        className: "pure-button",
                        title: (e.status == 0 ? "Відкликати запрошення" : "Звільнити")
                    }, "<i class='fa fa-times'></i>", {
                        "click": function (event) {
                            stopBubble(event);
                            cm.fire(e.login, e.status);
                        }
                    }));
                } else {
                    btns.push(buildNode("BUTTON", {
                        className: "pure-button",
                        title: "Видалити історію перебування та повернути документи власнику"
                    }, "<i class='fa fa-trash'></i>", {
                        "click": function (event) {
                            stopBubble(event);
                            var email = e.login;
                            if (confirm("Видалити історію перебування та повернути документи власнику?")) {
                                ajax("/api/company/return/" + email, function () {
                                    userConfig.company.employee.splice(userConfig.company.employee.indexOf(email),1);
                                    cm.drawEmployeeList();
                                }, null, function () {}, "DELETE")
                            }
                        }
                    }));
                }

            } else if (userConfig.login == e.login) {
                btns.push(buildNode("BUTTON", {
                    className: "pure-button",
                    title: "Покинути групу"
                }, "<i class='fa fa-sign-out'></i>", {
                    "click": function (event) {
                        stopBubble(event);
                        if (adminCount <= 1) {
                            if (workerCount == 1) {
                                cm.deleteGroup();
                            } else {
                                alert("Ви останній адміністратор в групі та не можете ії покинути. Потрібно звільнити усіх користувачів та повторити дію.");
                            }
                        } else {
                            cm.fire(e.login, e.status);
                        }
                    }
                }));
            }
            if (!selectDocs) {
                node.appendChild(buildNode("TD", {style: {width: "90px", "textAlign": "right"}, login: e.login}, btns));
            }
            byId("employeeTable").appendChild(node);
        });
        if (missedLogin) {
            alert("Користувач " + missedLogin + " знаходиться в іншій групі.");
        }
        if (ddosLogin) {
            alert("Користувачу " + missedLogin + " занадто часто надсилають email`ы. Запит проігноровано.");
        }
    }
};


