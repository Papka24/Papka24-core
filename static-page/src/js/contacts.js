c = {
    init: function () {
        c.drawCompanyList();
    },
    drawCompanyList: function(){
        if (userConfig.friendsCompany.length==0){
            drawMessage("<br>Контрагенти відсутні<br><br>");
        } else {
            var node = buildNode("TR");
                node.appendChild(buildNode("TH", {}, "Назва компанії"));
                node.appendChild(buildNode("TH", {}, "ЄДРПОУ"));
                node.appendChild(buildNode("TH", {}, "Email`ы"));
            byId("companyTable").appendChild(node);
            foreach(userConfig.friendsCompany, function (e) {
                /** @namespace e.name */
                /** @namespace e.inn */
                /** @namespace e.emails */
                var node = buildNode("TR");
                node.appendChild(buildNode("TD", {}, e.name));
                node.appendChild(buildNode("TD", {}, e.inn));
                node.appendChild(buildNode("TD", {}, e.emails.join("<br>")));
                byId("companyTable").appendChild(node);
            })
        }
    },
    checkEGRPOU:function(egrpou){
        if (typeof egrpou !== "string"){
            egrpou = ""+egrpou;
        }

        if (egrpou.length > 10 || egrpou.length <= 3){
            return false;
        } else {
            var i, sum;
            if (egrpou.length>8){
                // inn check https://uk.wikipedia.org/wiki/%D0%86%D0%B4%D0%B5%D0%BD%D1%82%D0%B8%D1%84%D1%96%D0%BA%D0%B0%D1%86%D1%96%D0%B9%D0%BD%D0%B8%D0%B9_%D0%BD%D0%BE%D0%BC%D0%B5%D1%80_%D1%84%D1%96%D0%B7%D0%B8%D1%87%D0%BD%D0%BE%D1%97_%D0%BE%D1%81%D0%BE%D0%B1%D0%B8
                var kInn = [-1,5,7,9,4,6,10,5,7];
                if (egrpou.length == 9){
                    egrpou = c.normalize(egrpou)+"";
                }
                sum = 0;
                for (i=0;i<9;i++){
                    sum+=kInn[i]*parseInt(egrpou[i]);
                }
                var k = sum%11;
                if (k==10){
                    k=0;
                }
                return k == parseInt(egrpou[9]);
            } else {
                // egrpou check (http://1cinfo.com.ua/Articles/Proverka_koda_po_EDRPOU.aspx)
                if (egrpou.length<8){
                    egrpou = c.normalize(egrpou)+"";
                }
                var k1 = [7,1,2,3,4,5,6];
                var k2 = [9,3,4,5,6,7,8];
                if (parseInt(egrpou<30000000 || egrpou > 60000000)){
                    k1 = [1,2,3,4,5,6,7];
                    k2 = [3,4,5,6,7,8,9];
                }
                sum = 0;
                for (i=0;i<7;i++){
                    sum+=k1[i]*parseInt(egrpou[i]);
                }
                if (sum%11>9){
                    sum = 0;
                    for (i=0;i<7;i++){
                        sum+=k2[i]*parseInt(egrpou[i]);
                    }
                }
                return sum % 11 == parseInt(egrpou[7]);
            }
        }
    },
    loadEgrpous:function(egrpous){
        if (isArray(egrpous) && egrpous.length>0){
            var fe=[];
            foreach(egrpous,function(e){
                if(fe.length>1000){
                    return false;
                }
                if (c.checkEGRPOU(e) && fe.indexOf(parseInt(e))==-1){
                    fe.push(parseInt(e));
                }
            });
            ajax("/api/partner",function(e){
                byTag(byId("contactsInfoBlock"),"span")[0].style.display="inline";
                byTag(byId("contactsInfoBlock"),"span")[1].className="hide";
                c.renderBankData(e);
            }, JSON.stringify(fe));
        }
    },

    renderBankData:function(data){
        rmClass(byId("friendsBox"),"newPartner");
        byTag(byId("friendsBox"),"SPAN")[0].innerHTML="";
        data = JSON.parse(data);
        var my=[],notmy=[];
        var cache=[];
        c.inviteP = [];
        foreach(data,function(d){
            /** @namespace d.k */
            /** @namespace d.id */
            /** @namespace d.n */
            /** @namespace d.c */
            var text = c.normalize(d.id);
            if (d.n && d.n.length>0){
                text = "<b>"+d.n.replace("<","").replace(">","")+"</b> ("+text+")";
            }
            if (d.hasOwnProperty("c") && d.c){
                text = "<span style='color:black'>"+text+"</span>";
            }
            if (cache.indexOf(d.id)<0) {
                cache.push(d.id);
                if (d.k) {
                    my.push(text);
                } else {
                    c.inviteP.push(d.id);
                    notmy.push(text);
                }
            }
        });
        var t = byId("companyTable");
        t.innerHTML = "<tr style='color: black'><th>Вже користуються Папка24</th><th>Ще не користуються <button style='margin-left: 10px' class='pure-button primary loginLink' onclick='c.invite()'>Запросити</button></th></tr>";
        var max = Math.max(my.length, notmy.length);
        for (var i =0;i<max;i++ ){
            t.appendChild(buildNode("TR", {}, [
                buildNode("TD", {}, (i>=my.length?"":my[i])),
                buildNode("TD", {}, (i>=notmy.length?"":notmy[i]))]));
        }
    },

    inviteP:[],
    invite:function(){
        byId("messageBG").click();
        if (c.inviteP.length>0) {
            ajax("/api/partner", function () {
                drawInfo("Ваше запрошення буде доставлене контрагентам");
            }, JSON.stringify(c.inviteP), function () {
            }, "PUT");
        }
    },

    normalize:function(egrpou){
        if (typeof egrpou != "number") {
            egrpou = parseInt(egrpou);
        }
        if (Math.abs(egrpou) < 10000000) {
            return new Array(9 - ("" + Math.abs(egrpou)).length).join("0") + Math.abs(egrpou);
        } else if (Math.abs(egrpou) < 1000000000) {
            return new Array(11 - ("" + Math.abs(egrpou)).length).join("0") + Math.abs(egrpou);
        } else {
            return egrpou;
        }
    },

    // Окно поиска контрагентов
    drawDetector:function() {
        if (typeof XLSX == 'undefined') {
            loadJs("/js/xlsx.core.min.js");
        }
        byId("contactSearch").onclick = function(){
            byId('selectFileContacts').click();
        };
        byId("selectFileContacts").onchange = function () {
            byId("uploadFileContacts").click();
        };
        ajax("/api/partner", function(e){
            byTag(byId("contactsInfoBlock"),"span")[0].style.display="none";
            byTag(byId("contactsInfoBlock"),"span")[1].className="";
            c.renderBankData(e);
        }, null, function(e){
            console.log(e);
            if (e!=404){
                drawWarning("Вибачте, але Ви ще не підписували документи<br> ключами підприємста. Нажаль, функція заблокована.");
            }
        });
        document.forms.uploadContacts.onsubmit = function (e) {
            stopBubble(e);
            if (this.elements.file.files.length > 0) {
                var n = this.elements.file.files[0].name;
                var ext = /[^.]+$/.exec(this.elements.file.files[0].name);
                if (this.elements.file.files[0].size > 1024000){
                    drawWarning("Завантаження файлів більше 1 МБ не підтримується");
                } else if (ext == "xlsx"){
                    var reader = new FileReader();
                    reader.onload = function(e) {
                        var data = e.target.result;

                        /* if binary string, read with type 'binary' */
                        var workbook = XLSX.read(data, {type: 'binary'});
                        var sheet_name_list = workbook.SheetNames;
                        var egrpous = [];
                        sheet_name_list.forEach(function(y) { /* iterate through sheets */
                            var worksheet = workbook.Sheets[y];
                            for (var z in worksheet) {
                                /* all keys that do not begin with "!" correspond to cell addresses */
                                if(z[0] === '!') continue;
                                egrpous = egrpous.concat(JSON.stringify(worksheet[z].v).match(/\d+/g));
                            }
                        });
                        c.loadEgrpous(egrpous);
                        /* DO SOMETHING WITH workbook HERE */
                    };
                    reader.readAsBinaryString(this.elements.file.files[0]);
                } else if(ext == "csv"|| ext == "txt"){
                    var r = new FileReader();
                    r.onloadend = function () {
                        if (r.error) {
                            alert("Your browser couldn't read the specified file (error code " + r.error.code + ").");
                        } else {
                            c.loadEgrpous(r.result.match(/\d+/g));
                        }
                    };
                    r.readAsBinaryString(this.elements.file.files[0]);
                } else {
                    drawWarning("Підтримуються файли лише у форматі *.csv, *.txt, *.xslx");
                }
            }
            return false;
        };
    }

};


