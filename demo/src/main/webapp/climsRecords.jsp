<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>CLIMSVIEW records</title>
<%
    pageContext.setAttribute("APP_PATH", request.getContextPath());
    pageContext.setAttribute("deviceSn", request.getParameter("deviceSn"));
%>
<script type="text/javascript" src="${APP_PATH}/static/js/jquery-1.12.4.min.js"></script>
<link href="${APP_PATH}/static/bootstrap-3.3.7-dist/css/bootstrap.min.css" rel="stylesheet">
<script src="${APP_PATH}/static/bootstrap-3.3.7-dist/js/bootstrap.min.js"></script>
</head>
<body>
    <div class="container">
        <div class="row">
            <div class="col-md-12">
                <h1>CLIMSVIEW Records</h1>
            </div>
        </div>
        <div class="row" style="margin-bottom: 10px;">
            <div class="col-md-12">
                <button class="btn btn-default" id="back_log_btn">Back To LogRecords</button>
                <button class="btn btn-primary" id="refresh_btn">Refresh</button>
                <span id="source_info" style="display:none;"></span>
            </div>
        </div>
        <div class="row" style="margin-bottom: 10px;">
            <div class="col-md-12">
                <input type="text" class="form-control" id="climsSearchInputTop"
                    placeholder="Search by User ID / Name" style="width: 320px; display:inline-block;">
            </div>
        </div>
        <div class="row">
            <div class="col-md-12">
                <table class="table table-hover table-striped" id="clims_table">
                    <thead>
                        <tr>
                            <th>#</th>
                            <th>DeviceLogId</th>
                            <th>DownloadDate</th>
                            <th>ProjectId</th>
                            <th>UserId</th>
                            <th>Name</th>
                            <th>LogDate</th>
                            <th>Direction</th>
                            <th>DeviceSerial</th>
                            <th>DeviceId</th>
                        </tr>
                    </thead>
                    <tbody>
                    </tbody>
                </table>
            </div>
        </div>
        <div class="row">
            <div class="col-md-6" id="page_info_area"></div>
            <div class="col-md-4 col-md-offset-2" id="page_nav_area"></div>
        </div>
    </div>

    <script type="text/javascript">
        var totalRecord, currentPage;
        var currentDeviceSn = "${deviceSn}";
        var currentClimsSearchKeyword = "";
        var climsSearchDebounceTimer = null;
        var climsAllRecords = [];
        var climsFilteredRecords = [];
        var climsPageSize = 8;
        var climsSourceMeta = {
            source: "",
            deviceSn: "",
            mappingMissing: false
        };

        function normalizeClimsSearchText(value) {
            return $.trim((value || ""));
        }

        function queueClimsSearchReload() {
            if (climsSearchDebounceTimer) {
                clearTimeout(climsSearchDebounceTimer);
            }
            climsSearchDebounceTimer = setTimeout(function() {
                applyClimsFilterAndRender(true);
            }, 220);
        }

        function buildNavigatePageNums(pageNum, pages) {
            var nums = [];
            if (pages <= 0) {
                return nums;
            }
            var navSize = 5;
            var start = pageNum - Math.floor(navSize / 2);
            var end = start + navSize - 1;
            if (start < 1) {
                start = 1;
                end = Math.min(pages, navSize);
            }
            if (end > pages) {
                end = pages;
                start = Math.max(1, end - navSize + 1);
            }
            for (var i = start; i <= end; i++) {
                nums.push(i);
            }
            return nums;
        }

        function buildClimsPageResult(pn) {
            var total = climsFilteredRecords.length;
            var pages = total === 0 ? 1 : Math.ceil(total / climsPageSize);
            var safePageNum = pn;
            if (!safePageNum || safePageNum < 1) {
                safePageNum = 1;
            }
            if (safePageNum > pages) {
                safePageNum = pages;
            }
            var startIndex = (safePageNum - 1) * climsPageSize;
            var endIndex = Math.min(startIndex + climsPageSize, total);
            var list = climsFilteredRecords.slice(startIndex, endIndex);
            var startRow = total === 0 ? 0 : startIndex + 1;
            return {
                extend: {
                    pageInfo: {
                        list: list,
                        pageNum: safePageNum,
                        pages: pages,
                        total: total,
                        startRow: startRow,
                        hasPreviousPage: safePageNum > 1,
                        hasNextPage: safePageNum < pages,
                        navigatepageNums: buildNavigatePageNums(safePageNum, pages)
                    }
                }
            };
        }

        function applyClimsFilterAndRender(resetToFirstPage) {
            var keyword = normalizeClimsSearchText(currentClimsSearchKeyword).toLowerCase();
            if (keyword === "") {
                climsFilteredRecords = climsAllRecords.slice(0);
            } else {
                climsFilteredRecords = $.grep(climsAllRecords, function(item) {
                    var userIdText = normalizeClimsSearchText(item ? item.userId : "").toLowerCase();
                    var userNameText = normalizeClimsSearchText(item ? item.userName : "").toLowerCase();
                    return userIdText.indexOf(keyword) !== -1 || userNameText.indexOf(keyword) !== -1;
                });
            }
            if (resetToFirstPage) {
                currentPage = 1;
            }
            to_page(currentPage || 1);
        }

        function loadClimsRecordsData() {
            var payload = { fetchAll: true };
            if (currentDeviceSn && currentDeviceSn.length > 0) {
                payload.deviceSn = currentDeviceSn;
            }
            $.ajax({
                url: "${APP_PATH}/climsRecords",
                data: payload,
                type: "GET",
                success: function(result) {
                    var ext = result && result.extend ? result.extend : {};
                    var pageInfo = ext.pageInfo ? ext.pageInfo : null;
                    climsAllRecords = pageInfo && pageInfo.list ? pageInfo.list : [];
                    climsSourceMeta = {
                        source: ext.source ? ext.source : "",
                        deviceSn: ext.deviceSn ? ext.deviceSn : "",
                        mappingMissing: ext.mappingMissing ? true : false
                    };
                    applyClimsFilterAndRender(true);
                },
                error: function() {
                    climsAllRecords = [];
                    climsSourceMeta = { source: "", deviceSn: currentDeviceSn, mappingMissing: false };
                    applyClimsFilterAndRender(true);
                }
            });
        }

        $(function() {
            loadClimsRecordsData();
            $("#climsSearchInputTop").on("input", function() {
                currentClimsSearchKeyword = normalizeClimsSearchText($(this).val());
                queueClimsSearchReload();
            });
        });

        $("#refresh_btn").click(function() {
            loadClimsRecordsData();
        });

        $("#back_log_btn").click(function() {
            window.location.href = "${APP_PATH}/logRecords.jsp?deviceSn=" + currentDeviceSn;
        });

        function to_page(pn) {
            if (climsSearchDebounceTimer) {
                clearTimeout(climsSearchDebounceTimer);
                climsSearchDebounceTimer = null;
            }
            var result = buildClimsPageResult(pn);
            build_table(result);
            build_page_info(result);
            build_page_nav(result);
            build_source_info();
        }

        function build_source_info() {
            $("#source_info").text("");
        }

        function build_table(result) {
            $("#clims_table tbody").empty();
            var rows = result.extend.pageInfo.list;
            var i = result.extend.pageInfo.startRow;
            $.each(rows, function(index, item) {
                $("<tr></tr>")
                    .append($("<td></td>").append(i++))
                    .append($("<td></td>").append(item.deviceLogId))
                    .append($("<td></td>").append(item.downloadDate))
                    .append($("<td></td>").append(item.projectId))
                    .append($("<td></td>").append(item.userId))
                    .append($("<td></td>").append(item.userName ? item.userName : "-"))
                    .append($("<td></td>").append(item.logDate))
                    .append($("<td></td>").append(item.direction))
                    .append($("<td></td>").append(item.deviceSerialNum ? item.deviceSerialNum : "-"))
                    .append($("<td></td>").append(item.deviceId))
                    .appendTo("#clims_table tbody");
            });
        }

        function build_page_info(result) {
            $("#page_info_area").empty();
            $("#page_info_area").append(
                "Current_Page:" + result.extend.pageInfo.pageNum +
                ", Count_Page :" + result.extend.pageInfo.pages +
                ", All_Records:" + result.extend.pageInfo.total
            );
            totalRecord = result.extend.pageInfo.pages;
            currentPage = result.extend.pageInfo.pageNum;
        }

        function build_page_nav(result) {
            $("#page_nav_area").empty();
            var ul = $("<ul></ul>").addClass("pagination");
            var firstPageLink = $("<a></a>").append("first").attr("href", "javascript:void(0);");
            var prePageLink = $("<a></a>").append("&laquo;").attr("href", "javascript:void(0);");
            var firstPageLi = $("<li></li>").append(firstPageLink);
            var prePageLi = $("<li></li>").append(prePageLink);

            if (!result.extend.pageInfo.hasPreviousPage) {
                firstPageLi.addClass("disabled");
                prePageLi.addClass("disabled");
            } else {
                firstPageLink.on("click", function(e) {
                    e.preventDefault();
                    to_page(1);
                });
                prePageLink.on("click", function(e) {
                    e.preventDefault();
                    to_page(result.extend.pageInfo.pageNum - 1);
                });
            }
            ul.append(firstPageLi).append(prePageLi);

            $.each(result.extend.pageInfo.navigatepageNums, function(index, item) {
                var numLink = $("<a></a>").append(item).attr("href", "javascript:void(0);");
                var numLi = $("<li></li>").append(numLink);
                if (result.extend.pageInfo.pageNum == item) {
                    numLi.addClass("active");
                }
                numLink.on("click", function(e) {
                    e.preventDefault();
                    to_page(item);
                });
                ul.append(numLi);
            });

            var nextPageLink = $("<a></a>").append("&raquo;").attr("href", "javascript:void(0);");
            var lastPageLink = $("<a></a>").append("last").attr("href", "javascript:void(0);");
            var nextPageLi = $("<li></li>").append(nextPageLink);
            var lastPageLi = $("<li></li>").append(lastPageLink);
            if (!result.extend.pageInfo.hasNextPage) {
                nextPageLi.addClass("disabled");
                lastPageLi.addClass("disabled");
            } else {
                nextPageLink.on("click", function(e) {
                    e.preventDefault();
                    to_page(result.extend.pageInfo.pageNum + 1);
                });
                lastPageLink.on("click", function(e) {
                    e.preventDefault();
                    to_page(result.extend.pageInfo.pages);
                });
            }
            ul.append(nextPageLi).append(lastPageLi);

            var navEle = $("<nav></nav>").append(ul);
            navEle.appendTo("#page_nav_area");
        }
    </script>
</body>
</html>
