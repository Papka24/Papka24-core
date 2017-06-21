
var emailRegexp = /^(([^<>()[\]\\.,;:\s@"]+(\.[^<>()[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;

/**
 * Execute AJAX request
 * @param{string} request - path for request
 * @param{function=} func - callback function
 * @param{string=} params - data for POST request
 * @param errorFunction - function for catch error
 * @param mode - mode DELETE, POST, GET etc.
 */
function ajax(request, func, params, errorFunction, mode) {
    var ajaxRequest = window.XMLHttpRequest ? new XMLHttpRequest() : new ActiveXObject('Msxml2.XMLHTTP');
    if (typeof mode === 'string' && mode != "RAW") {
        ajaxRequest.open(mode, request, true);
    } else {
        ajaxRequest.open((typeof params === 'undefined' || params == null) ? "GET" : "POST", request, true);
    }
    ajaxRequest.timeout = 20000; // 20 секунд
    ajaxRequest.onload = ajaxRequest.onerror = function () {
        if ((this.status >= 200 && ajaxRequest.status <= 206) && typeof func === 'function') {
            if(mode == "RAW"){
                func(this.response);
            } else {
                func(this.responseText);
            }
        } else {
            if (typeof errorFunction === 'function') {
                errorFunction(this.status);
            }
        }
    };
    if(mode == "RAW"){
        ajaxRequest.responseType = 'arraybuffer';
        ajaxRequest.setRequestHeader("Content-Type", "application/bytes");
    } else {
        ajaxRequest.setRequestHeader("Content-Type", "text/plain; charset=utf-8");
    }

    if(mode == "DELETE"){
        params = "";
    }

    if (localStorage.getItem("sessionId")) {
        ajaxRequest.setRequestHeader("sessionid", localStorage.getItem("sessionId"));
    }
    if (userConfig.company && userConfig.company.login && userConfig.company.login != userConfig.login ){
        ajaxRequest.setRequestHeader("employee", userConfig.company.login);
    }
    if(typeof version !== "undefined"){
        ajaxRequest.setRequestHeader("v", version);
    }
    if (typeof params === 'object' && mode != "RAW") {
        params = JSON.stringify(params);
    }
    ajaxRequest.send(params);
    return ajaxRequest;
}

/*
 * Ajax overload for fetch data on base64 format
 */
function ajaxRaw(url, callback, error){
    ajax(url, function(){
        var ff = callback;
        return function(result){
            ff(_arrayBufferToBase64(result));
        }
    }(), null, error, "RAW");
}

function ajaxRawRePost(url, file, callback, error){
    ajax(url, function(){
        var ff = callback;
        return function(result){
            ff(result);
        }
    }(), file, error, "RAW");
}


function _arrayBufferToBase64(buffer) {
    var binary = '';
    var bytes = new Uint8Array(buffer);
    var len = bytes.byteLength;
    for (var i = 0; i < len; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
}

function b64toBlob(b64Data, contentType, sliceSize) {
    if (typeof b64Data == "undefined" || b64Data == null ){
        return new Blob([], {type: contentType});
    }
    contentType = contentType || 'application/octet-stream';
    sliceSize = sliceSize || 512;

    var byteCharacters = atob(b64Data.replace(/[ \r\n]+$/, ""));
    var byteArrays = [];

    for (var offset = 0; offset < byteCharacters.length; offset += sliceSize) {
        var slice = byteCharacters.slice(offset, offset + sliceSize);

        var byteNumbers = new Array(slice.length);
        for (var i = 0; i < slice.length; i++) {
            byteNumbers[i] = slice.charCodeAt(i);
        }

        var byteArray = new Uint8Array(byteNumbers);

        byteArrays.push(byteArray);
    }

    return new Blob(byteArrays, {type: contentType});
}

/**
 * Get Element by id
 * @param id
 * @returns {Node}
 */
function byId(id) {
    return document.getElementById(id)
}

/**
 * Get Element by tag
 * @param domNode
 * @param tagName
 * @returns {NodeList}
 */
function byTag(domNode, tagName) {
    if (domNode == null || typeof domNode != "object" || typeof tagName === "undefined") {
        return null;
    }
    return domNode.getElementsByTagName(tagName);
}


/**
 * Get Element by class name
 * @param domNode
 * @param searchClass
 * @param tagName
 * @returns {Array}
 */
function byClass(domNode, searchClass, tagName) {
    var tags;
    var el = [];
    if (typeof domNode == "string"){
        domNode = document.getElementById(domNode);
    }
    if (domNode == null) {
        domNode = document;
    }
    if (domNode.getElementsByClassName) {
        tags = domNode.getElementsByClassName(searchClass);
        var t;
        if (tagName) {
            for (t in tags) {
                if (tags.hasOwnProperty(t) && tags[t].tagName == tagName) {
                    el.push(tags[t])
                }
            }
        } else {
            for (t in tags) {
                if (tags.hasOwnProperty(t)) {
                    el.push(tags[t])
                }
            }
        }
        return el;
    }
    if (tagName == null)
        tagName = '*';
    tags = domNode.getElementsByTagName(tagName);
    var tcl = " " + searchClass + " ";
    for (var i = 0, j = 0; i < tags.length; i++) {
        var test = " " + tags[i].className + " ";
        if (test.indexOf(tcl) != -1)
            el[j++] = tags[i];
    }
    return el;
}

/**
 * Check if element has class
 * @param el
 * @param name
 * @returns {boolean}
 */
function hasClass(el, name) {
    if(typeof el == "undefined" || el == null){
        return false;
    }
    return new RegExp('(\\s|^)' + name + '(\\s|$)').test(el.className);
}

/**
 * Add class to element
 * @param el
 * @param name
 */
function addClass(el, name) {
    if(typeof el == "undefined" || el == null){
        return false;
    }
    if (!hasClass(el, name)) {
        el.className += (el.className ? ' ' : '') + name;
    }
}

/**
 * Remove class from element
 * @param el
 * @param name
 */
function rmClass(el, name) {
    if(el === void 0 || el == null){
        return false;
    }
    if (hasClass(el, name)) {
        el.className = el.className.replace(name, "").trim();
    }
}

function addEvent(el, type, eventHandle) {
    if(el === void 0 || el == null){
        return;
    }
    if (el.addEventListener) {
        el.addEventListener(type, eventHandle, false);
    } else if (el.attachEvent) {
        el.attachEvent("on" + type, eventHandle);
    } else {
        el["on" + type] = eventHandle;
    }
}

// Return date in format hh:mm
Date.prototype.toTimeString = function () {
    var d = this;
    var h = d.getHours() < 10 ? "0" + d.getHours() : d.getHours();
    var m = d.getMinutes() < 10 ? "0" + d.getMinutes() : d.getMinutes();
    //var s = d.getSeconds() < 10 ? "0" + d.getSeconds() : d.getSeconds();
    //return h + ":" + m + ":" + s;
    return h + ":" + m;
};

//Return date in format 'hh:mm dd.mm.yyyy'
Date.prototype.toTimestampString = function () {
    var d = this;
    var ds = d.getDate() < 10 ? "0" + d.getDate() : d.getDate();
    var ms = d.getMonth() < 9 ? "0" + (d.getMonth() + 1) : (d.getMonth() + 1);
    var h = d.getHours() < 10 ? "0" + d.getHours() : d.getHours();
    var m = d.getMinutes() < 10 ? "0" + d.getMinutes() : d.getMinutes();
    return h + ":" + m + " " + ds + "." + ms + "." + d.getFullYear();
};

//Return date in format 'yyyy.mm.dd hh:mm'
Date.prototype.toRevertTimestampString = function () {
    var d = this;
    var ds = d.getDate() < 10 ? "0" + d.getDate() : d.getDate();
    var ms = d.getMonth() < 9 ? "0" + (d.getMonth() + 1) : (d.getMonth() + 1);
    var h = d.getHours() < 10 ? "0" + d.getHours() : d.getHours();
    var m = d.getMinutes() < 10 ? "0" + d.getMinutes() : d.getMinutes();
    return d.getFullYear()+ "." +ms+"."+ds +" " + h + ":" + m;
};

function today(td){
    var d = new Date();
    return td.getDate() == d.getDate() && td.getMonth() == d.getMonth() && td.getFullYear() == d.getFullYear();
}

//Return date in format 'dd.mm.yyyy'
Date.prototype.toDatastampString = function () {
    var d = this;
    var ds = d.getDate() < 10 ? "0" + d.getDate() : d.getDate();
    var ms = d.getMonth() < 9 ? "0" + (d.getMonth() + 1) : (d.getMonth() + 1);
    return ds + "." + ms + "." + d.getFullYear();
};


if (!Date.prototype.toISOString) {
    (function () {

        function pad(number) {
            if (number < 10) {
                return '0' + number;
            }
            return number;
        }

        Date.prototype.toISOString = function () {
            return this.getUTCFullYear() +
                '-' + pad(this.getUTCMonth() + 1) +
                '-' + pad(this.getUTCDate()) +
                'T' + pad(this.getUTCHours()) +
                ':' + pad(this.getUTCMinutes()) +
                ':' + pad(this.getUTCSeconds()) +
                '.' + (this.getUTCMilliseconds() / 1000).toFixed(3).slice(2, 5) +
                'Z';
        };
    }());
}

function stopBubble(event) {
    if (event) {
        if (event.stopPropagation) {
            event.stopPropagation();
        } else {
            event.cancelBubble = true;
        }
    }

}

//Return date in format 'hh:mm dd.mm.yyyy'
Date.prototype.toTimestampString = function () {
    var d = this;
    var ds = d.getDate() < 10 ? "0" + d.getDate() : d.getDate();
    var ms = d.getMonth() < 9 ? "0" + (d.getMonth() + 1) : (d.getMonth() + 1);
    var h = d.getHours() < 10 ? "0" + d.getHours() : d.getHours();
    var m = d.getMinutes() < 10 ? "0" + d.getMinutes() : d.getMinutes();
    return h + ":" + m + " " + ds + "." + ms + "." + d.getFullYear();
};

// Return date in format dd.mm.yyyy
Date.prototype.toSimpleString = function () {
    var d = this;
    var ds = d.getDate() < 10 ? "0" + d.getDate() : d.getDate();
    var ms = d.getMonth() < 9 ? "0" + (d.getMonth() + 1) : (d.getMonth() + 1);
    return ds + "." + ms + "." + d.getFullYear();
};

/**
 * Get all elements from array items and execute function with each of them.
 * @param items
 * @param callback
 * @param parentNode
 */
function foreach(items, callback, parentNode) {
    if(typeof items === void 0 || items == null){
        return void 0;
    }
    if (parentNode !== void 0) {
        callback = function (value, index, collection) {
            return callback.call(parentNode, value, index, collection);
        };
    }
    var i, length = items.length;
    if (typeof length == 'number' && length >= 0) {
        for (i = 0; i < length; i++) {
            callback(items[i], i, items);
        }
    } else {
        var keys = [];
        for (var key in items) if (items.hasOwnProperty(key)) keys.push(key);
        for (i = 0, length = keys.length; i < length; i++) {
            callback(items[keys[i]], keys[i], items);
        }
    }
    return items;
}

var buildCache = [];
function buildNode(nodeName, attributes, content, events) {
    var element;
    if (!(nodeName in buildCache)) {
        buildCache[nodeName] = document.createElement(nodeName);
    }
    element = buildCache[nodeName].cloneNode(false);

    if (attributes != null) {
        for (var attribute in attributes) {
            if (attributes.hasOwnProperty(attribute)) {
                if (attribute == "style" && typeof attributes["style"] !== 'string') {
                    var sts = attributes["style"];
                    for (var s in sts) {
                        if (sts.hasOwnProperty(s)) {
                            try {
                                element.style[s] = sts[s];
                            } catch (e) {
                            }
                        }
                    }
                } else {
                    element[attribute] = attributes[attribute];
                }
            }
        }
    }
    if (content != null) {
        if (typeof(content) == 'object') {
            if (content.constructor == Array) {
                for (var c in content) {
                    if (content.hasOwnProperty(c) && content[c]) {
                        if (typeof(content[c]) == 'object') {
                            element.appendChild(content[c]);
                        } else {
                            element.appendChild(document.createTextNode(content[c]));
                        }
                    }
                }
            } else {
                element.appendChild(content);
            }
        } else {
            element.innerHTML = content;
        }
    }
    if (events != null) {
        for (var e in events) {
            if (events.hasOwnProperty(e) && typeof events[e] === "function") {
                addEvent(element, e, events[e]);
            }
        }
    }
    return element;
}

function getOffset(el) {
    var _x = 0,
        _y = 0;
    while (el && el.tagName.toLowerCase() != 'body' && !isNaN(el.offsetLeft) && !isNaN(el.offsetTop)) {
        _x += el.offsetLeft - el.scrollLeft;
        _y += el.offsetTop - el.scrollTop;
        el = el.offsetParent;
    }
    return {top: _y, left: _x};
}

function cursorOnBlock(el, x, y){
    if(!el){return false;}
    var offset = getOffset(el);
    return !(x < offset.left || y < offset.top || x > (offset.left + el.offsetWidth) || y > (offset.top + el.offsetHeight));

}

// clone object
function clone(obj) {
    if (null == obj || "object" != typeof obj) return obj;
    var copy = obj.constructor();
    for (var attr in obj) {
        if (obj.hasOwnProperty(attr)) copy[attr] = obj[attr];
    }
    return copy;
}

String.prototype.insertAt = function (str, position) {
    return [this.slice(0, position), str, this.slice(position)].join('');
};




function setCaretPosition(element, start, end) {
    if (!end) {
        end = start;
    }
    if (element.setSelectionRange)
        element.setSelectionRange(start, end);
    else if (element.createTextRange) {
        var range = element.createTextRange();
        range.collapse(true);
        range.moveStart('character', start);
        range.moveEnd('character', end);
        range.select();
    }
}

/**
 * dynamical load js file
 *
 * @param url
 * @param callback
 */
function loadJs(url, callback) {
    var node = document.createElement('script');
    node.setAttribute("type", "text/javascript");
    node.setAttribute("src", url);
    if (typeof callback == "function") {
        // TODO: fix it
        setTimeout(function(){callback()},2000);
        //addEvent('onreadystatechange', callback);
    }
    if (typeof node != "undefined") {
        document.getElementsByTagName("head")[0].appendChild(node);
    }
}

function stripHTMLTags(html, maxSize){
    if (typeof maxSize === "number"){
        return html.replace(/</gi,'&lt;').replace(/>/gi,'&gt;').substr(0, maxSize);
    } else {
        return html.replace(/</gi,'&lt;').replace(/>/gi,'&gt;');
    }

}

var Base64={_keyStr:"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=",encode:function(e){var t="";var n,r,i,s,o,u,a;var f=0;e=Base64._utf8_encode(e);while(f<e.length){n=e.charCodeAt(f++);r=e.charCodeAt(f++);i=e.charCodeAt(f++);s=n>>2;o=(n&3)<<4|r>>4;u=(r&15)<<2|i>>6;a=i&63;if(isNaN(r)){u=a=64}else if(isNaN(i)){a=64}t=t+this._keyStr.charAt(s)+this._keyStr.charAt(o)+this._keyStr.charAt(u)+this._keyStr.charAt(a)}return t},decode:function(e){var t="";var n,r,i;var s,o,u,a;var f=0;e=e.replace(/[^A-Za-z0-9\+\/\=]/g,"");while(f<e.length){s=this._keyStr.indexOf(e.charAt(f++));o=this._keyStr.indexOf(e.charAt(f++));u=this._keyStr.indexOf(e.charAt(f++));a=this._keyStr.indexOf(e.charAt(f++));n=s<<2|o>>4;r=(o&15)<<4|u>>2;i=(u&3)<<6|a;t=t+String.fromCharCode(n);if(u!=64){t=t+String.fromCharCode(r)}if(a!=64){t=t+String.fromCharCode(i)}}t=Base64._utf8_decode(t);return t},_utf8_encode:function(e){e=e.replace(/\r\n/g,"\n");var t="";for(var n=0;n<e.length;n++){var r=e.charCodeAt(n);if(r<128){t+=String.fromCharCode(r)}else if(r>127&&r<2048){t+=String.fromCharCode(r>>6|192);t+=String.fromCharCode(r&63|128)}else{t+=String.fromCharCode(r>>12|224);t+=String.fromCharCode(r>>6&63|128);t+=String.fromCharCode(r&63|128)}}return t},_utf8_decode:function(e){var t="";var n=0;var r=c1=c2=0;while(n<e.length){r=e.charCodeAt(n);if(r<128){t+=String.fromCharCode(r);n++}else if(r>191&&r<224){c2=e.charCodeAt(n+1);t+=String.fromCharCode((r&31)<<6|c2&63);n+=2}else{c2=e.charCodeAt(n+1);c3=e.charCodeAt(n+2);t+=String.fromCharCode((r&15)<<12|(c2&63)<<6|c3&63);n+=3}}return t}};

function hexToBase64(str) {
    return btoa(String.fromCharCode.apply(null,
        str.replace(/\r|\n/g, "").replace(/([\da-fA-F]{2}) ?/g, "0x$1 ").replace(/ +$/, "").split(" "))
    );
}

function base64ToHex(str, joinChar) {
    str=str.replace(/-/g,'\+').replace(/_/g,'\/');
    for (var i = 0, bin = atob(str.replace(/[ \r\n]+$/, "")), hex = []; i < bin.length; ++i) {
        var tmp = bin.charCodeAt(i).toString(16);
        if (tmp.length === 1) tmp = "0" + tmp;
        hex[hex.length] = tmp;
    }
    if (typeof joinChar=="undefined"){
        joinChar = "";
    }
    return hex.join(joinChar);
}

function b64revert(str){
    var data = base64ToHex(str);
    var newData = "";
    for(var i = data.length-1; i>0;i-=2){
        newData += data[i-1]+data[i];
    }
    return hexToBase64(newData);
}

CanvasRenderingContext2D.prototype.roundRect = function (x, y, w, h, r) {
    if (w < 2 * r) r = w / 2;
    if (h < 2 * r) r = h / 2;
    this.beginPath();
    this.moveTo(x+r, y);
    this.arcTo(x+w, y,   x+w, y+h, r);
    this.arcTo(x+w, y+h, x,   y+h, r);
    this.arcTo(x,   y+h, x,   y,   r);
    this.arcTo(x,   y,   x+w, y,   r);
    this.closePath();
    return this;
};

CanvasRenderingContext2D.prototype.wrapText = function (text, x, y, maxWidth, lineHeight) {
    var cars = text.split("\n");

    for (var ii = 0; ii < cars.length; ii++) {

        var line = "";
        var words = cars[ii].split(" ");

        for (var n = 0; n < words.length; n++) {
            var testLine = line + words[n] + " ";
            var metrics = this.measureText(testLine);
            var testWidth = metrics.width;

            if (testWidth > maxWidth) {
                this.fillText(line, x+(maxWidth-this.measureText(line).width)/2, y);
                line = words[n] + " ";
                y += lineHeight;
            }
            else {
                line = testLine;
            }
        }
        this.fillText(line, x+(maxWidth-this.measureText(line).width)/2, y);
        y += lineHeight;
    }
};

function getParameterByName(name, url) {
    if (!url) url = window.location.href;
    name = name.replace(/[\[\]]/g, "\\$&");
    var regex = new RegExp("[?&]" + name + "(=([^&#]*)|&|#|$)"),
        results = regex.exec(url);
    if (!results) return null;
    if (!results[2]) return '';
    return decodeURIComponent(results[2].replace(/\+/g, " "));
}

window.isArray = (function(){
    return window.Array.isArray || function(obj) {
        return window.Object.toString.call(obj) === '[object Array]';
    };
})();

var _ = {
    template : function(text, settings) {
        var escaper = /\\|'|\r|\n|\u2028|\u2029/g;

        // When customizing `templateSettings`, if you don't want to define an
        // interpolation, evaluation or escaping regex, we need one that is
        // guaranteed not to match.
        var noMatch = /(.)^/;
        settings = settings || {
                evaluate: /<%([\s\S]+?)%>/g,
                interpolate: /<%=([\s\S]+?)%>/g,
                escape: /<%-([\s\S]+?)%>/g
            };

        // Combine delimiters into one regular expression via alternation.
        var matcher = RegExp([
                (settings.escape || noMatch).source,
                (settings.interpolate || noMatch).source,
                (settings.evaluate || noMatch).source
            ].join('|') + '|$', 'g');

        // Compile the template source, escaping string literals appropriately.
        var index = 0;
        var source = "__p+='";
        text.replace(matcher, function (match, escape, interpolate, evaluate, offset) {
            source += text.slice(index, offset).replace(escaper, function (match) {
                return '\\' + match;
            });
            index = offset + match.length;

            if (escape) {
                source += "'+\n((__t=(" + escape + "))==null?'':_.escape(__t))+\n'";
            } else if (interpolate) {
                source += "'+\n((__t=(" + interpolate + "))==null?'':__t)+\n'";
            } else if (evaluate) {
                source += "';\n" + evaluate + "\n__p+='";
            }

            // Adobe VMs need the match returned to produce the correct offest.
            return match;
        });
        source += "';\n";

        // If a variable is not specified, place data values in local scope.
        if (!settings.variable) source = 'with(obj||{}){\n' + source + '}\n';

        source = "var __t,__p='',__j=Array.prototype.join," +
            "print=function(){__p+=__j.call(arguments,'');};\n" +
            source + 'return __p;\n';

        try {
            var render = new Function(settings.variable || 'obj', '_', source);
        } catch (e) {
            e.source = source;
            throw e;
        }

        var template = function (data) {
            return render.call(this, data, _);
        };

        // Provide the compiled source as a convenience for precompilation.
        var argument = settings.variable || 'obj';
        template.source = 'function(' + argument + '){\n' + source + '}';

        return template;
    }
};
