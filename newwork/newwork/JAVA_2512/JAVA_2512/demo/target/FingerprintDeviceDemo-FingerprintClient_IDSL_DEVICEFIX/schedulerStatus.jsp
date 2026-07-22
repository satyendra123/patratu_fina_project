<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Scheduler Daily Status</title>
<%
    pageContext.setAttribute("APP_PATH", request.getContextPath());
%>
<script type="text/javascript" src="${APP_PATH}/static/js/jquery-1.12.4.min.js"></script>
<link href="${APP_PATH}/static/bootstrap-3.3.7-dist/css/bootstrap.min.css" rel="stylesheet">
<script src="${APP_PATH}/static/bootstrap-3.3.7-dist/js/bootstrap.min.js"></script>
<style type="text/css">
    .status-pill {
        display: inline-block;
        padding: 3px 8px;
        border-radius: 12px;
        color: #fff;
        font-size: 11px;
        font-weight: 600;
        letter-spacing: 0.3px;
    }
    .status-success { background: #2e7d32; }
    .status-failed { background: #c62828; }
    .status-partial { background: #ef6c00; }
    .status-running { background: #1565c0; }
    .status-not-run { background: #616161; }
    .status-unknown { background: #546e7a; }
    .sync-summary-box {
        margin-top: 8px;
        margin-bottom: 8px;
        padding: 8px 10px;
        border: 1px solid #d9e2ec;
        border-radius: 4px;
        background: #f8fbff;
        font-size: 12px;
        line-height: 1.5;
        word-break: break-word;
    }
    .sync-section-title {
        margin-top: 8px;
        margin-bottom: 8px;
        font-weight: 700;
    }
    .view-toggle-wrap {
        margin-left: 12px;
    }
    .view-toggle-wrap .btn {
        min-width: 150px;
    }
    .status-view {
        display: none;
    }
    .status-view.active {
        display: block;
    }
</style>
</head>
<body>
<div class="container">
    <div class="row" style="margin-top: 12px;">
        <div class="col-md-12">
            <h2>Scheduler Daily Status</h2>
        </div>
    </div>

    <div class="row" style="margin-bottom: 10px;">
        <div class="col-md-12">
            <button class="btn btn-default" id="back_btn">Back To Home</button>
            <button class="btn btn-primary" id="refresh_btn">Refresh</button>
            <div class="btn-group view-toggle-wrap" role="group">
                <button class="btn btn-info" id="show_per_device_btn">Per Device Status</button>
                <button class="btn btn-default" id="show_scheduled_btn">Scheduled Status</button>
            </div>
            <span id="status_msg" style="margin-left: 12px; color: #337ab7;"></span>
        </div>
    </div>

    <div id="view_per_device" class="status-view">
        <div class="row">
            <div class="col-md-12">
                <h4 class="sync-section-title">SetUserToAllDevice Per Device Status</h4>
                <div id="db_sync_summary" class="sync-summary-box">Loading sync status...</div>
                <table class="table table-hover table-striped table-bordered" id="db_sync_device_table">
                    <thead>
                        <tr>
                            <th>#</th>
                            <th>Device</th>
                            <th>Online</th>
                            <th>Last Status</th>
                            <th>Last Sync At</th>
                            <th>Last Queued</th>
                            <th>Delivered</th>
                            <th>Pending</th>
                            <th>Queued Users</th>
                            <th>Queued This Run</th>
                            <th>ETA(sec)</th>
                            <th>Message</th>
                        </tr>
                    </thead>
                    <tbody></tbody>
                </table>
            </div>
        </div>
    </div>

    <div id="view_scheduled" class="status-view">
        <div class="row" style="margin-bottom: 10px;">
            <div class="col-md-12">
                <label style="font-weight: normal;">
                    Last
                    <select id="days_select" class="form-control" style="display:inline-block; width:90px; height:30px;">
                        <option value="7">7 Days</option>
                        <option value="15">15 Days</option>
                        <option value="30" selected="selected">30 Days</option>
                        <option value="60">60 Days</option>
                    </select>
                </label>
                <label style="margin-left: 12px; font-weight: normal;">
                    Page Size
                    <select id="page_size_select" class="form-control" style="display:inline-block; width:90px; height:30px;">
                        <option value="10" selected="selected">10</option>
                        <option value="20">20</option>
                        <option value="30">30</option>
                        <option value="50">50</option>
                    </select>
                </label>
            </div>
        </div>

        <div class="row" style="margin-bottom: 8px;">
            <div class="col-md-12" id="summary_area"></div>
        </div>

        <div class="row">
            <div class="col-md-12">
                <table class="table table-hover table-striped" id="scheduler_table">
                    <thead>
                        <tr>
                            <th>#</th>
                            <th>Date</th>
                            <th>Daily Status</th>
                            <th>Run Count</th>
                            <th>Success</th>
                            <th>Failed</th>
                            <th>First Start</th>
                            <th>Last Finish</th>
                            <th>Enabled Users</th>
                            <th>Disabled Users</th>
                        </tr>
                    </thead>
                    <tbody></tbody>
                </table>
            </div>
        </div>

        <div class="row" style="margin-bottom: 12px;">
            <div class="col-md-12 text-right" id="pagination_area"></div>
        </div>
    </div>
</div>

<script type="text/javascript">
    var currentPage = 1;
    var currentView = "perDevice";
    var schedulerLoadedOnce = false;

    function toInt(value, fallback) {
        var n = parseInt(value, 10);
        if (isNaN(n)) {
            return (typeof fallback === "number") ? fallback : 0;
        }
        return n;
    }

    function safeText(value) {
        if (value === null || value === undefined || value === "") {
            return "-";
        }
        return value;
    }

    function safeUpper(value) {
        return $.trim((value || "")).toUpperCase();
    }

    function safeMessage(value) {
        var text = $.trim(value || "");
        if (text === "") {
            return "-";
        }
        if (text.length > 120) {
            return text.substring(0, 120) + "...";
        }
        return text;
    }

    function statusClass(status) {
        if (status === "SUCCESS") { return "status-success"; }
        if (status === "FAILED") { return "status-failed"; }
        if (status === "PARTIAL") { return "status-partial"; }
        if (status === "RUNNING") { return "status-running"; }
        return "status-unknown";
    }

    function nowText() {
        var d = new Date();
        var yyyy = d.getFullYear();
        var mm = ("0" + (d.getMonth() + 1)).slice(-2);
        var dd = ("0" + d.getDate()).slice(-2);
        var hh = ("0" + d.getHours()).slice(-2);
        var mi = ("0" + d.getMinutes()).slice(-2);
        var ss = ("0" + d.getSeconds()).slice(-2);
        return yyyy + "-" + mm + "-" + dd + " " + hh + ":" + mi + ":" + ss;
    }

    function buildSummary(rows, days, pn, pageSize, totalRecords, totalPages) {
        var successDays = 0;
        var failedDays = 0;
        var runningDays = 0;

        $.each(rows || [], function(i, row) {
            var status = row && row.dailyStatus ? row.dailyStatus : "UNKNOWN";
            if (status === "SUCCESS") { successDays++; }
            if (status === "FAILED") { failedDays++; }
            if (status === "RUNNING") { runningDays++; }
        });

        $("#summary_area").text(
            "Review Window: " + days + " days"
            + " | Days with runs: " + toInt(totalRecords)
            + " | Page: " + pn + "/" + (totalPages <= 0 ? 1 : totalPages)
            + " | Page Size: " + pageSize
            + " | Success (this page): " + successDays
            + " | Failed (this page): " + failedDays
            + " | Running (this page): " + runningDays
        );
    }

    function buildTable(rows, pn, pageSize) {
        var tbody = $("#scheduler_table tbody");
        tbody.empty();
        if (!rows || rows.length === 0) {
            $("<tr></tr>")
                .append($("<td></td>").attr("colspan", 10).text("No scheduler status records found."))
                .appendTo(tbody);
            return;
        }

        $.each(rows, function(index, item) {
            var status = item && item.dailyStatus ? item.dailyStatus : "UNKNOWN";
            var serial = ((pn - 1) * pageSize) + index + 1;
            var badge = $("<span></span>")
                .addClass("status-pill")
                .addClass(statusClass(status))
                .text(status);
            $("<tr></tr>")
                .append($("<td></td>").text(serial))
                .append($("<td></td>").text(safeText(item.runDate)))
                .append($("<td></td>").append(badge))
                .append($("<td></td>").text(toInt(item.runCount)))
                .append($("<td></td>").text(toInt(item.successCount)))
                .append($("<td></td>").text(toInt(item.failedCount)))
                .append($("<td></td>").text(safeText(item.firstStartedAt)))
                .append($("<td></td>").text(safeText(item.lastFinishedAt)))
                .append($("<td></td>").text(toInt(item.enabledUsers)))
                .append($("<td></td>").text(toInt(item.disabledUsers)))
                .appendTo(tbody);
        });
    }

    function buildPagination(pn, totalPages, totalRecords) {
        var area = $("#pagination_area");
        area.empty();

        if (!totalRecords || totalRecords <= 0) {
            area.text("No pages");
            return;
        }

        var prevBtn = $("<button></button>")
            .addClass("btn btn-default btn-sm")
            .text("Prev")
            .prop("disabled", pn <= 1);
        var nextBtn = $("<button></button>")
            .addClass("btn btn-default btn-sm")
            .css("margin-left", "8px")
            .text("Next")
            .prop("disabled", pn >= totalPages);
        var info = $("<span></span>")
            .css("margin-left", "10px")
            .text("Page " + pn + " of " + totalPages + " | Total Days: " + totalRecords);

        prevBtn.click(function() {
            if (pn > 1) {
                loadSchedulerStatus(pn - 1);
            }
        });
        nextBtn.click(function() {
            if (pn < totalPages) {
                loadSchedulerStatus(pn + 1);
            }
        });

        area.append(prevBtn).append(nextBtn).append(info);
    }

    function renderDbSyncSummary(ext) {
        ext = ext || {};
        var running = ext.running === true;
        var state = safeUpper(ext.state || "");
        if (state === "") {
            state = running ? "RUNNING" : "IDLE";
        }
        var msg = "state=" + state
            + " | users=" + toInt(ext.activeUsers)
            + " | enrollRecords=" + toInt(ext.totalEnrollRecords)
            + " | queued=" + toInt(ext.totalCommandsQueued)
            + " (setuserinfo=" + toInt(ext.setuserinfoCommandsQueued)
            + ", cleanadmin=" + toInt(ext.cleanAdminCommandsQueued) + ")"
            + " | devices=" + toInt(ext.devices)
            + " | onlineDevices=" + toInt(ext.onlineDevices)
            + " | eta=" + toInt(ext.estimatedDeviceDispatchSeconds) + " sec";
        var detailMessage = $.trim(ext.message || "");
        if (detailMessage !== "") {
            msg += " | note=" + safeMessage(detailMessage);
        }
        $("#db_sync_summary").text(msg);
    }

    function mergeDbSyncRows(deviceDetails, deviceSyncStateRows) {
        var runtimeDetails = $.isArray(deviceDetails) ? deviceDetails : [];
        var stateRows = $.isArray(deviceSyncStateRows) ? deviceSyncStateRows : [];
        var runtimeBySerial = {};
        $.each(runtimeDetails, function(index, detail) {
            if (!detail) { return; }
            var serial = $.trim(detail.serial || "");
            if (serial === "") { return; }
            runtimeBySerial[serial.toUpperCase()] = detail;
        });
        var merged = [];
        $.each(stateRows, function(index, row) {
            if (!row) { return; }
            var serial = $.trim(row.serial || "");
            if (serial === "") { return; }
            var key = serial.toUpperCase();
            merged.push({
                serial: serial,
                state: row,
                runtime: runtimeBySerial[key] || null
            });
            delete runtimeBySerial[key];
        });
        $.each(runtimeBySerial, function(serialKey, detail) {
            if (!detail) { return; }
            merged.push({
                serial: $.trim(detail.serial || serialKey),
                state: null,
                runtime: detail
            });
        });
        return merged;
    }

    function renderDbSyncDeviceTable(deviceDetails, deviceSyncStateRows) {
        var tbody = $("#db_sync_device_table tbody");
        tbody.empty();
            var mergedRows = mergeDbSyncRows(deviceDetails, deviceSyncStateRows);
            if (!mergedRows.length) {
                $("<tr></tr>")
                    .append($("<td></td>").attr("colspan", 12).text("No per-device sync status available."))
                    .appendTo(tbody);
                return;
            }
        $.each(mergedRows, function(index, row) {
            var state = row.state || {};
            var runtime = row.runtime || {};
            var serial = $.trim(row.serial || runtime.serial || state.serial || "");
            if (serial === "") { serial = "device-" + (index + 1); }
            var stateOnline = state.online;
            var runtimeOnline = runtime.online;
            var online = (stateOnline === true || runtimeOnline === true || toInt(stateOnline) === 1 || toInt(runtimeOnline) === 1) ? "Yes" : "No";
            var lastStatus = safeUpper(state.lastSyncStatus || runtime.syncStatus || "NEVER");
            var lastSyncAt = safeText(state.lastSyncAt || runtime.lastSyncAt || "");
            var lastQueued = toInt(state.lastQueuedSetuserinfo, toInt(runtime.queuedSetuserinfo));
            var delivered = toInt(state.deliveredSetuserinfoCount);
            var pending = (runtime.pendingAfter === undefined || runtime.pendingAfter === null || runtime.pendingAfter === "") ? "-" : toInt(runtime.pendingAfter);
            var queuedUsers = (runtime.deltaUserCount === undefined || runtime.deltaUserCount === null || runtime.deltaUserCount === "") ? "-" : toInt(runtime.deltaUserCount);
            var queuedThisRun = (runtime.queuedSetuserinfo === undefined || runtime.queuedSetuserinfo === null || runtime.queuedSetuserinfo === "") ? "-" : toInt(runtime.queuedSetuserinfo);
            var etaSec = (runtime.estimatedDispatchSeconds === undefined || runtime.estimatedDispatchSeconds === null || runtime.estimatedDispatchSeconds === "") ? "-" : toInt(runtime.estimatedDispatchSeconds);
            var message = safeMessage(runtime.syncMessage || state.lastErrorMessage || state.lastSyncMessage || "");
            $("<tr></tr>")
                .append($("<td></td>").text(index + 1))
                .append($("<td></td>").text(serial))
                .append($("<td></td>").text(online))
                .append($("<td></td>").text(lastStatus))
                .append($("<td></td>").text(lastSyncAt))
                .append($("<td></td>").text(lastQueued))
                .append($("<td></td>").text(delivered))
                .append($("<td></td>").text(pending))
                .append($("<td></td>").text(queuedUsers))
                .append($("<td></td>").text(queuedThisRun))
                .append($("<td></td>").text(etaSec))
                .append($("<td></td>").text(message))
                .appendTo(tbody);
        });
    }

    function loadDbSyncStatus(updateStatusMsg) {
        var shouldUpdateStatusMsg = (updateStatusMsg !== false);
        if (shouldUpdateStatusMsg) {
            $("#status_msg").text("Loading...");
        }
        $.ajax({
            url: "${APP_PATH}/setUserToAllDeviceStatus",
            type: "GET",
            cache: false,
            success: function(result) {
                if (!result || result.code !== 100) {
                    var err = result && result.extend && result.extend.error ? result.extend.error : "Unable to load SetUserToAllDevice status.";
                    $("#db_sync_summary").text(err);
                    renderDbSyncDeviceTable([], []);
                    if (shouldUpdateStatusMsg) {
                        $("#status_msg").text(err);
                    }
                    return;
                }
                var ext = result.extend || {};
                renderDbSyncSummary(ext);
                renderDbSyncDeviceTable(ext.deviceDetails, ext.deviceSyncStateRows);
                if (shouldUpdateStatusMsg) {
                    $("#status_msg").text("Last refreshed: " + nowText());
                }
            },
            error: function() {
                $("#db_sync_summary").text("Load failed: SetUserToAllDevice status");
                renderDbSyncDeviceTable([], []);
                if (shouldUpdateStatusMsg) {
                    $("#status_msg").text("Load failed");
                }
            }
        });
    }

    function loadSchedulerStatus(pageToLoad) {
        var days = toInt($("#days_select").val());
        var pageSize = toInt($("#page_size_select").val());
        if (days <= 0) {
            days = 30;
        }
        if (pageSize <= 0) {
            pageSize = 10;
        }
        var targetPage = toInt(pageToLoad);
        if (targetPage <= 0) {
            targetPage = currentPage <= 0 ? 1 : currentPage;
        }
        $("#status_msg").text("Loading...");
        $.ajax({
            url: "${APP_PATH}/schedulerDailyStatus",
            type: "GET",
            data: { days: days, pn: targetPage, pageSize: pageSize },
            cache: false,
            success: function(result) {
                if (!result || result.code !== 100) {
                    var err = result && result.extend && result.extend.error ? result.extend.error : "Unable to load scheduler status.";
                    $("#status_msg").text(err);
                    buildTable([], 1, pageSize);
                    buildSummary([], days, 1, pageSize, 0, 0);
                    buildPagination(1, 0, 0);
                    return;
                }
                var ext = result.extend || {};
                var rows = ext.rows || [];
                var pn = toInt(ext.pn);
                var totalPages = toInt(ext.totalPages);
                var totalRecords = toInt(ext.totalRecords);
                var effectivePageSize = toInt(ext.pageSize);
                var effectiveDays = toInt(ext.days);
                if (pn <= 0) { pn = 1; }
                if (totalPages < 0) { totalPages = 0; }
                if (effectivePageSize <= 0) { effectivePageSize = pageSize; }
                if (effectiveDays <= 0) { effectiveDays = days; }
                currentPage = pn;
                buildTable(rows, pn, effectivePageSize);
                buildSummary(rows, effectiveDays, pn, effectivePageSize, totalRecords, totalPages);
                buildPagination(pn, totalPages, totalRecords);
                $("#status_msg").text("Last refreshed: " + nowText());
                schedulerLoadedOnce = true;
            },
            error: function() {
                $("#status_msg").text("Load failed");
                buildTable([], 1, pageSize);
                buildSummary([], days, 1, pageSize, 0, 0);
                buildPagination(1, 0, 0);
            }
        });
    }

    function setStatusView(viewName, forceReload) {
        var isScheduled = (viewName === "scheduled");
        currentView = isScheduled ? "scheduled" : "perDevice";

        $("#view_per_device").toggleClass("active", !isScheduled);
        $("#view_scheduled").toggleClass("active", isScheduled);

        $("#show_per_device_btn")
            .toggleClass("btn-info", !isScheduled)
            .toggleClass("btn-default", isScheduled);
        $("#show_scheduled_btn")
            .toggleClass("btn-info", isScheduled)
            .toggleClass("btn-default", !isScheduled);

        if (isScheduled) {
            if (forceReload || !schedulerLoadedOnce) {
                loadSchedulerStatus(currentPage <= 0 ? 1 : currentPage);
            } else {
                $("#status_msg").text("Showing Scheduled Status");
            }
        } else if (forceReload) {
            loadDbSyncStatus(true);
        } else {
            $("#status_msg").text("Showing Per Device Status");
        }
    }

    $("#refresh_btn").click(function() {
        if (currentView === "scheduled") {
            loadSchedulerStatus(currentPage);
        } else {
            loadDbSyncStatus(true);
        }
    });

    $("#days_select").change(function() {
        currentPage = 1;
        loadSchedulerStatus(1);
    });

    $("#page_size_select").change(function() {
        currentPage = 1;
        loadSchedulerStatus(1);
    });

    $("#back_btn").click(function() {
        window.location.href = "${APP_PATH}/index.jsp";
    });

    $("#show_per_device_btn").click(function() {
        setStatusView("perDevice", true);
    });

    $("#show_scheduled_btn").click(function() {
        setStatusView("scheduled", true);
    });

    $(function() {
        setStatusView("perDevice", false);
        loadDbSyncStatus(true);
        setInterval(function() {
            if (currentView === "perDevice") {
                loadDbSyncStatus(false);
            }
        }, 5000);
    });
</script>
</body>
</html>
