<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Log Records</title>
<%
    pageContext.setAttribute("APP_PATH", request.getContextPath());
    pageContext.setAttribute("deviceSn", request.getParameter("deviceSn"));
%>
<script type="text/javascript" src="${APP_PATH}/static/js/jquery-1.12.4.min.js"></script>
<link href="${APP_PATH}/static/bootstrap-3.3.7-dist/css/bootstrap.min.css" rel="stylesheet">
<script src="${APP_PATH}/static/bootstrap-3.3.7-dist/js/bootstrap.min.js"></script>
<link href="${APP_PATH}/static/css/bootstrap-datetimepicker.min.css" rel="stylesheet">
<script src="${APP_PATH}/static/js/bootstrap-datetimepicker.min.js"></script>
</head>
<body>
<div class="container">
    <div class="row" style="margin-top: 10px;">
        <div class="col-md-12">
            <h1>LogRecords</h1>
        </div>
    </div>

    <div class="row" style="margin-bottom: 10px;">
        <div class="col-md-12">
            <button class="btn btn-primary" id="CollectLog_emp_modal_btn">CollectLog</button>
            <button class="btn btn-primary" id="CollectNewLog_emp_modal_btn">CollectNewLog</button>
            <button class="btn btn-default" id="refresh_btn">Refresh</button>
            <button class="btn btn-success" id="download_report_btn">Download Report</button>
            <label style="margin-left: 12px; font-weight: normal;">
                <input type="checkbox" id="autoRefreshToggle" checked> Auto refresh (5s)
            </label>
            <span id="live_status" style="margin-left: 10px; color: #337ab7;"></span>
        </div>
    </div>

    <div class="row" style="margin-bottom: 10px;">
        <div class="col-md-2">
            <select class="form-control" id="deviceFilterSelect">
                <option value="">All Devices (Optional)</option>
            </select>
        </div>
        <div class="col-md-2">
            <input type="text" class="form-control" id="logSearchInputTop" placeholder="Search by User ID / Name (Optional)">
        </div>
        <div class="col-md-3">
            <div class="input-group date form_datetime" id="fromDatePicker" data-date-format="yyyy-mm-dd hh:ii:ss">
                <input class="form-control" size="16" type="text" id="fromDateTimeInput" placeholder="From Date Time" readonly>
                <span class="input-group-addon"><span class="glyphicon glyphicon-calendar"></span></span>
            </div>
        </div>
        <div class="col-md-3">
            <div class="input-group date form_datetime" id="toDatePicker" data-date-format="yyyy-mm-dd hh:ii:ss">
                <input class="form-control" size="16" type="text" id="toDateTimeInput" placeholder="To Date Time" readonly>
                <span class="input-group-addon"><span class="glyphicon glyphicon-calendar"></span></span>
            </div>
        </div>
        <div class="col-md-2">
            <button class="btn btn-info" id="applyFilterBtn">Apply Filter</button>
        </div>
    </div>

    <div class="row" style="margin-bottom: 8px;">
        <div class="col-md-12" id="page_info_area"></div>
    </div>
    <div class="row" style="margin-bottom: 8px;">
        <div class="col-md-12 text-right" id="page_nav_area"></div>
    </div>

    <div class="row">
        <div class="col-md-12">
            <table class="table table-hover table-striped" id="emps_table">
                <thead>
                    <tr>
                        <th>#</th>
                        <th>LogId</th>
                        <th>PersonId</th>
                        <th>Name</th>
                        <th>RecordsTime</th>
                        <th>InOut</th>
                        <th>Device</th>
                        <th>Location Name</th>
                        <th>Log Image</th>
                    </tr>
                </thead>
                <tbody></tbody>
            </table>
        </div>
    </div>
</div>

<script type="text/javascript">
    var initialDeviceSn = "";
    var currentRows = [];
    var latestTopRecordId = null;
    var autoRefreshTimer = null;
    var autoRefreshMs = 5000;
    var pendingRequest = null;
    var latestQuerySignature = "";
    var isFilterApplied = false;
    var currentPage = 1;
    var currentPageSize = 30;
    var currentTotalPages = 0;
    var appliedFilters = {
        deviceSn: "",
        keyword: "",
        fromTime: "",
        toTime: ""
    };

    function normalizeText(value) {
        return $.trim((value || ""));
    }

    function toPositiveInt(value, fallback) {
        var n = parseInt(value, 10);
        return isNaN(n) || n < 1 ? fallback : n;
    }

    function buildImageSource(item) {
        if (!item) {
            return "";
        }
        var base64 = normalizeText(item.imageBase64);
        if (base64) {
            if (base64.indexOf("data:image") === 0) {
                return base64;
            }
            return "data:image/jpeg;base64," + base64;
        }
        var imageName = normalizeText(item.image);
        if (imageName) {
            return "${APP_PATH}/img/" + imageName;
        }
        return "";
    }

    function getSelectedDeviceSn() {
        return normalizeText($("#deviceFilterSelect").val());
    }

    function loadDeviceOptions(done) {
        var select = $("#deviceFilterSelect");
        select.empty();
        select.append($("<option></option>").val("").text("All Devices (Optional)"));

        $.ajax({
            url: "${APP_PATH}/device",
            type: "GET",
            cache: false,
            success: function(result) {
                var ext = (result && result.extend) ? result.extend : {};
                var devices = $.isArray(ext.device) ? ext.device : [];
                $.each(devices, function(index, item) {
                    var sn = normalizeText(item && item.serialNum);
                    if (!sn) {
                        return;
                    }
                    select.append($("<option></option>").val(sn).text(sn));
                });

                if (initialDeviceSn) {
                    select.val(initialDeviceSn);
                }
            },
            complete: function() {
                if (typeof done === "function") {
                    done();
                }
            }
        });
    }

    function buildQueryPayload(pageNo) {
        var payload = {};
        var selectedDeviceSn = normalizeText(appliedFilters.deviceSn);
        var keyword = normalizeText(appliedFilters.keyword);
        var fromTime = normalizeText(appliedFilters.fromTime);
        var toTime = normalizeText(appliedFilters.toTime);

        if (isFilterApplied) {
            payload.applyFilter = true;
            payload.pn = toPositiveInt(pageNo, currentPage);
            payload.pageSize = currentPageSize;
        } else {
            payload.limit = 30;
        }

        if (selectedDeviceSn) {
            payload.deviceSn = selectedDeviceSn;
        }
        if (keyword) {
            payload.keyword = keyword;
        }
        if (fromTime) {
            payload.fromTime = fromTime;
        }
        if (toTime) {
            payload.toTime = toTime;
        }
        return payload;
    }

    function applyFilterStateFromInputs() {
        appliedFilters.deviceSn = getSelectedDeviceSn();
        appliedFilters.keyword = normalizeText($("#logSearchInputTop").val());
        appliedFilters.fromTime = normalizeText($("#fromDateTimeInput").val());
        appliedFilters.toTime = normalizeText($("#toDateTimeInput").val());
    }

    function buildPayloadSignature(payload) {
        return [
            payload.applyFilter ? "F" : "L",
            payload.pn || 1,
            payload.pageSize || 0,
            payload.deviceSn || "",
            payload.keyword || "",
            payload.fromTime || "",
            payload.toTime || "",
            payload.limit || 30
        ].join("|");
    }

    function escapeCsvCell(value) {
        var text = value == null ? "" : String(value);
        return '"' + text.replace(/"/g, '""') + '"';
    }

    function formatNowStamp() {
        var d = new Date();
        var yyyy = d.getFullYear();
        var mm = ("0" + (d.getMonth() + 1)).slice(-2);
        var dd = ("0" + d.getDate()).slice(-2);
        var hh = ("0" + d.getHours()).slice(-2);
        var mi = ("0" + d.getMinutes()).slice(-2);
        var ss = ("0" + d.getSeconds()).slice(-2);
        return yyyy + "-" + mm + "-" + dd + " " + hh + ":" + mi + ":" + ss;
    }

    function startAutoRefresh() {
        stopAutoRefresh();
        autoRefreshTimer = setInterval(function() {
            loadLatestRecords({ silent: true, onlyIfChanged: !isFilterApplied });
        }, autoRefreshMs);
    }

    function stopAutoRefresh() {
        if (autoRefreshTimer) {
            clearInterval(autoRefreshTimer);
            autoRefreshTimer = null;
        }
    }

    function buildEmpTable(rows) {
        var tbody = $("#emps_table tbody");
        tbody.empty();

        if (!rows || rows.length === 0) {
            $("<tr></tr>")
                .append($("<td></td>").attr("colspan", 9).text("No records found."))
                .appendTo(tbody);
            return;
        }

        $.each(rows, function(index, item) {
            var imageTd = $("<td></td>");
            var imageSrc = buildImageSource(item);
            if (imageSrc) {
                imageTd.append("<img style='width:60px; height:60px;' src='" + imageSrc + "'/>");
            } else {
                imageTd.append("-");
            }
            var serialNo = isFilterApplied ? (((currentPage - 1) * currentPageSize) + index + 1) : (index + 1);

            $("<tr></tr>")
                .append($("<td></td>").append(serialNo))
                .append($("<td></td>").append(item.id))
                .append($("<td></td>").append(item.enrollId != null ? item.enrollId : "-"))
                .append($("<td></td>").append(item.userName ? item.userName : "-"))
                .append($("<td></td>").append(item.recordsTime ? item.recordsTime : "-"))
                .append($("<td></td>").append(item.intout == 0 ? "In" : "Out"))
                .append($("<td></td>").append(item.deviceSerialNum ? item.deviceSerialNum : "-"))
                .append($("<td></td>").append(item.locationName ? item.locationName : "-"))
                .append(imageTd)
                .appendTo(tbody);
        });
    }

    function buildInfo(meta) {
        meta = meta || {};
        var totalMatched = typeof meta.totalMatched === "number" ? meta.totalMatched : null;
        var text = "";
        if (isFilterApplied) {
            var matched = totalMatched != null ? totalMatched : currentRows.length;
            var displayTotalPages = currentTotalPages < 1 ? 1 : currentTotalPages;
            text = "Filtered data: matched " + matched + ", page " + currentPage + " of " + displayTotalPages
                + ", showing " + currentRows.length + " rows.";
        } else {
            text = "Showing latest " + currentRows.length + " records (max 30).";
            if (totalMatched != null) {
                text += " Matched: " + totalMatched + ".";
            }
        }
        var selectedDeviceSn = normalizeText(appliedFilters.deviceSn);
        text += " Device: " + (selectedDeviceSn ? selectedDeviceSn : "All Devices") + ".";
        text += " Last update: " + formatNowStamp() + ".";
        $("#page_info_area").text(text);
    }

    function buildPageNav() {
        var area = $("#page_nav_area");
        area.empty();
        if (!isFilterApplied || currentTotalPages <= 1) {
            return;
        }
        var ul = $("<ul></ul>").addClass("pagination");

        function addPage(label, pageNo, disabled, active) {
            var li = $("<li></li>");
            if (disabled) {
                li.addClass("disabled");
            }
            if (active) {
                li.addClass("active");
            }
            var a = $("<a></a>").attr("href", "#").text(label);
            if (!disabled && !active) {
                a.on("click", function(e) {
                    e.preventDefault();
                    loadLatestRecords({ pageNo: pageNo, forceRender: true });
                });
            }
            li.append(a);
            ul.append(li);
        }

        addPage("First", 1, currentPage <= 1, false);
        addPage("Prev", currentPage - 1, currentPage <= 1, false);
        var start = Math.max(1, currentPage - 2);
        var end = Math.min(currentTotalPages, start + 4);
        if ((end - start) < 4) {
            start = Math.max(1, end - 4);
        }
        for (var p = start; p <= end; p++) {
            addPage(String(p), p, false, p === currentPage);
        }
        addPage("Next", currentPage + 1, currentPage >= currentTotalPages, false);
        addPage("Last", currentTotalPages, currentPage >= currentTotalPages, false);
        area.append(ul);
    }

    function loadLatestRecords(options) {
        options = options || {};
        var requestedPage = toPositiveInt(options.pageNo, currentPage);
        var payload = buildQueryPayload(requestedPage);
        var payloadSignature = buildPayloadSignature(payload);

        if (pendingRequest) {
            try {
                pendingRequest.abort();
            } catch (ignore) {}
            pendingRequest = null;
        }

        if (!options.silent) {
            $("#live_status").text("Loading...");
        }

        pendingRequest = $.ajax({
            url: "${APP_PATH}/recordsLatest",
            type: "GET",
            data: payload,
            cache: false,
            success: function(result) {
                if (!result || result.code !== 100) {
                    var err = result && result.extend && result.extend.error ? result.extend.error : "Unable to load records.";
                    $("#live_status").text(err);
                    return;
                }

                var ext = result.extend || {};
                var rows = ext.records || [];
                if (isFilterApplied) {
                    currentPage = toPositiveInt(ext.pn, requestedPage);
                    currentTotalPages = Math.max(0, parseInt(ext.totalPages, 10) || 0);
                } else {
                    currentPage = 1;
                    currentTotalPages = 0;
                }
                var topId = rows.length > 0 ? rows[0].id : null;
                var queryChanged = payloadSignature !== latestQuerySignature;
                var shouldRender = options.forceRender || !options.onlyIfChanged || isFilterApplied
                        || queryChanged || topId !== latestTopRecordId;

                if (shouldRender) {
                    currentRows = rows;
                    buildEmpTable(rows);
                    buildInfo(ext);
                    buildPageNav();
                }

                latestTopRecordId = topId;
                latestQuerySignature = payloadSignature;
                $("#live_status").text("Live");
            },
            error: function(xhr, status) {
                if (status === "abort") {
                    return;
                }
                $("#live_status").text("Load failed");
                $("#page_info_area").text("Unable to load log records.");
                $("#page_nav_area").empty();
            },
            complete: function() {
                pendingRequest = null;
            }
        });
    }

    function downloadCurrentReport() {
        if (!currentRows || currentRows.length === 0) {
            alert("No data available to export.");
            return;
        }
        var lines = [];
        lines.push([
            "PersonId", "RecordTime", "InOut", "Devices"
        ].map(escapeCsvCell).join(","));

        $.each(currentRows, function(index, item) {
            lines.push([
                item.enrollId != null ? item.enrollId : "",
                item.recordsTime ? item.recordsTime : "",
                item.intout == 0 ? "In" : "Out",
                item.deviceSerialNum ? item.deviceSerialNum : ""
            ].map(escapeCsvCell).join(","));
        });

        var csvContent = "\ufeff" + lines.join("\r\n");
        var blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
        var url = window.URL.createObjectURL(blob);
        var a = document.createElement("a");
        a.href = url;
        a.download = "log-report-" + formatNowStamp().replace(/[: ]/g, "-") + ".csv";
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
    }

    function triggerCollect(endpointUrl) {
        var selectedDeviceSn = getSelectedDeviceSn();
        if (!selectedDeviceSn) {
            alert("Please select a device for collect action.");
            return;
        }
        if (!confirm("Do you want to collect punch record?")) {
            return;
        }

        $.ajax({
            url: endpointUrl,
            type: "GET",
            data: { deviceSn: selectedDeviceSn },
            cache: false,
            success: function(result) {
                alert(result.msg);
                latestTopRecordId = null;
                loadLatestRecords();
            },
            error: function() {
                alert("Collect request failed.");
            }
        });
    }

    $(function() {
        $('.form_datetime').datetimepicker({
            format: 'yyyy-mm-dd hh:ii:ss',
            autoclose: true,
            todayBtn: true,
            todayHighlight: true,
            minuteStep: 1,
            language: 'en'
        });

        $("#applyFilterBtn").on("click", function() {
            applyFilterStateFromInputs();
            isFilterApplied = true;
            currentPage = 1;
            latestTopRecordId = null;
            loadLatestRecords({ pageNo: 1, forceRender: true });
        });

        $("#refresh_btn").on("click", function() {
            loadLatestRecords();
        });

        $("#download_report_btn").on("click", function() {
            downloadCurrentReport();
        });

        $("#autoRefreshToggle").on("change", function() {
            if ($(this).prop("checked")) {
                startAutoRefresh();
                $("#live_status").text("Live");
            } else {
                stopAutoRefresh();
                $("#live_status").text("Auto refresh off");
            }
        });

        $("#CollectLog_emp_modal_btn").on("click", function() {
            triggerCollect("${APP_PATH}/getAllLog");
        });

        $("#CollectNewLog_emp_modal_btn").on("click", function() {
            triggerCollect("${APP_PATH}/getNewLog");
        });

        loadDeviceOptions(function() {
            applyFilterStateFromInputs();
            isFilterApplied = false;
            currentPage = 1;
            loadLatestRecords({ forceRender: true });
            startAutoRefresh();
        });
    });
</script>
</body>
</html>
