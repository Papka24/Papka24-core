(function () {
    function r(e, t) {
        if (e && t !== "null" && t !== "undefined")for (var n in t)if (t.hasOwnProperty(n) && t[n].test(e))return n;
        return "unknown"
    }

    function i() {
        var e = document.documentElement.lang ? document.documentElement.lang : "unknown";
        return e != "unknown" && e.length > 2 && !/es-419|pt-br|zh-cn|zh-tw/.test(e) && (e = e.substr(0, 2)), e
    }

    function s() {
        var n = r(e, t);
        return n == "chrome" ? /(crios)\/([\w\.]+)/.test(e) ? e.match(/crios\/([\w\.]+)/)[1] : /(crmo)\/([\w\.]+)/.test(e) ? e.match(/crmo\/([\w\.]+)/)[1] : /(chrome)\/([\w\.]+)/.test(e) ? e.match(/chrome\/([\w\.]+)/)[1] : "unknown" : n == "opera" ? /\s(opr)\/([\w\.]+)/.test(e) ? e.match(/(opr)\/([\w\.]+)/)[2] : /\s(opios)\/([\w\.]+)/.test(e) ? e.match(/(opios)\/([\w\.]+)/)[2] : /(opera)[\/\s]+([\w\.]+)/.test(e) ? e.match(/version\/([\w\.]+)/)[1] : "unknown" : n == "firefox" ? /firefox\/([\w\.]+)/.test(e) ? e.match(/firefox\/([\w\.]+)/)[1] : "unknown" : n == "msie" ? /(msie)\s([\w\.]+)/.test(e) ? e.match(/(msie)\s([\w\.]+)/)[2] : /\brv[ :]+(\w+)/.test(e) ? e.match(/\brv[ :]+(\w+)/)[1] : /edge\/([\w\.]+)/.test(e) ? e.match(/edge\/([\w\.]+)/)[1] : "unknown" : n == "safari" ? /version\/([\w\.]+)/.test(e) ? e.match(/version\/([\w\.]+)/)[1] : "unknown" : n == "yandex" ? /yabrowser\/([\w\.]+)/.test(e) ? e.match(/yabrowser\/([\w\.]+)/)[1] : "unknown" : "unknown"
    }

    function o() {
        var t = r(e, n);
        return t == "windows" || t == "osx" || t == "linux" ? "desktop" : t == "blackberry" || t == "symbian" || t == "firefox os" || t == "chrome os" || t == "windows phone" || t == "android" || t == "ios" ? "mobile" : "unknown"
    }

    function u() {
        var t = r(e, n);
        return t == "blackberry" ? /version\/([\w\.]+)/.test(e) ? e.match(/version\/([\w\.]+)/)[1] : "unknown" : t == "symbian" ? /series\s60/.test(e) ? "symbian" : /symbos/.test(e) ? "6" : /symbianos\/([\w\.]+)/.test(e) ? e.match(/symbianos\/([\w\.]+)/)[1] : "unknown" : t == "firefox os" ? /gecko\/([\w\.]+)/.test(e) ? e.match(/gecko\/([\w\.]+)/)[1] : "unknown" : t == "chrome os" ? /cros\s([\w]+)\s([\w\.]+)/.test(e) ? e.match(/cros\s([\w]+)\s([\w\.]+)/)[2] : "unknown" : t == "windows phone" ? /windows\sphone\sos/.test(e) ? e.match(/windows\sphone\sos\s([\w\.]+)/)[1] : /windows\sphone/.test(e) ? e.match(/windows\sphone\s([\w\.]+)/)[1] : "unknown" : t == "android" ? /android\s([\w\.]+)/.test(e) ? e.match(/android\s([\w\.]+)/)[1] : "unknown" : t == "ios" ? /os\s([\d_]+)/.test(e) ? e.match(/os\s([\d_]+)/)[1].replace(/_/g, ".") : "unknown" : t == "linux" ? /linux\s([\w\.]+)/.test(e) ? e.match(/linux\s([\w\.]+)/)[1] : "unknown" : t == "osx" ? /os\sx\s([\d_\.]+)/.test(e) ? e.match(/os\sx\s([\d_\.]+)/)[1].replace(/_/g, ".") : "unknown" : t == "windows" ? /windows\s95/.test(e) ? "95" : /windows\s98/.test(e) ? "98" : /windows\snt\s5\.(1|2)/.test(e) ? "xp" : /windows\snt\s6\.0/.test(e) ? "vista" : /windows\snt\s6\.1/.test(e) ? "7" : /windows\snt\s6\.2/.test(e) ? "8" : /windows\snt\s6\.3/.test(e) ? "8.1" : /windows\snt\s10\./.test(e) ? "10" : "unknown" : "unknown"
    }

    function a() {
        var e = "unknown";
        if (typeof document.body == "undefined")return e;
        var t = document.createElement("DIV");
        return t.id = "adbanner", t.style.position = "absolute", t.style.top = "-9999px", t.style.left = "-9999px", t.appendChild(document.createTextNode("&nbsp;")), document.body.appendChild(t), t && (e = t.clientHeight == 0, document.body.removeChild(t)), e
    }

    function f() {
        var f = {
            name: "unknown",
            version: "unknown",
            platform: "unknown",
            os: "unknown",
            osVer: "unknown",
            language: "unknown",
            adblockState: "unknown"
        };
        return e && (f.name = r(e, t), f.version = s(), f.platform = o(), f.os = r(e, n), f.osVer = u(), f.language = i(), f.adblockState = a()), f
    }

    var e = navigator.userAgent.toLowerCase(), t = {
        kindle: /(kindle)\/([\w\.]+)/,
        avant: /avant\sbrowser?[\/\s]?([\w\.]*)/,
        chromium: /(chromium)\/([\w\.-]+)/,
        skyfire: /(skyfire)\/([\w\.-]+)/,
        vivaldi: /(vivaldi)\/([\w\.-]+)/,
        yandex: /(yabrowser)\/([\w\.]+)/,
        ucbrowser: /(uc\s?browser)[\/\s]?([\w\.]+)/,
        firefox: /(firefox)\/([\w\.-]+)/,
        netscape: /(navigator|netscape)\/([\w\.-]+)/,
        coast: /(coast)\/([\w\.]+)/,
        opera: /(op(era|r|ios))[\/\s]+([\w\.]+)/,
        msie: /(?:ms|\()(ie)|((trident).+rv[:\s]([\w\.]+).+like\sgecko)|(edge)\/((\d+)?[\w\.]+)/,
        "android browser": /android.+version\/([\w\.]+)\s+(?:mobile\s?safari|safari)/,
        safari: /version\/([\w\.]+).+?(mobile\s?safari|safari)/,
        chrome: /(chrome|crmo|crios)\/([\w\.]+)/
    }, n = {
        blackberry: /\((bb)(10);|(blackberry)\w*\/?([\w\.]+)*/,
        symbian: /(symbian\s?os|symbos|s60(?=;))[\/\s-]?([\w\.]+)*/,
        "firefox os": /mozilla.+\((mobile|tablet);.+gecko.+firefox/,
        "chrome os": /(cros)\s[\w]+\s([\w\.]+\w)/,
        "windows phone": /windows\s(phone|mobile)|iemobile/,
        android: /android/,
        ios: /ipad|iphone|ipod/,
        linux: /(x11|linux|unix)\s?([\w\.]+)*/,
        osx: /(mac\sos\sx)\s?([\w\s\.]+\w)*/,
        windows: /windows/
    };
    BrowserInfo = f
})();