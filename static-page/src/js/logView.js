logView = {
    getDayLog: function () {
        var logDay = byId("logDay").value;
        var logUser = byId("logUser").value;
        var request = "/api/admin/log/20160406";
        ajax(request, function (result) {
            byId("getLogResult").value = result;
        }, null, function () {
        }, "GET");
    }
};

addEvent(window, "load", function () {
    new Datepickr(byId("startSelectionDate"));
});