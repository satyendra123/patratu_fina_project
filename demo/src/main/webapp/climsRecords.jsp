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
                <span id="source_info" style="margin-left: 15px; color: #337ab7;"></span>
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

        $(function() {
            to_page(1);
        });

        $("#refresh_btn").click(function() {
            to_page(currentPage || 1);
        });

        $("#back_log_btn").click(function() {
            window.location.href = "${APP_PATH}/logRecords.jsp?deviceSn=" + currentDeviceSn;
        });

        function to_page(pn) {
            var payload = { pn: pn };
            if (currentDeviceSn && currentDeviceSn.length > 0) {
                payload.deviceSn = currentDeviceSn;
            }
            $.ajax({
                url: "${APP_PATH}/climsRecords",
                data: payload,
                type: "GET",
                success: function(result) {
                    build_table(result);
                    build_page_info(result);
                    build_page_nav(result);
                    build_source_info(result);
                }
            });
        }

        function build_source_info(result) {
            var source = result.extend && result.extend.source ? result.extend.source : "";
            var deviceSn = result.extend && result.extend.deviceSn ? result.extend.deviceSn : "";
            var mappingMissing = result.extend && result.extend.mappingMissing ? true : false;
            var text = source ? ("Data Source: " + source) : "Data Source: N/A";
            if (deviceSn) {
                text += " | Device: " + deviceSn;
            }
            if (mappingMissing) {
                text += " | NetWork mapping missing for selected device";
            }
            $("#source_info").text(text);
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
            var firstPageLi = $("<li></li>").append($("<a></a>").append("first").attr("href", "#"));
            var prePageLi = $("<li></li>").append($("<a></a>").append("&laquo;"));

            if (!result.extend.pageInfo.hasPreviousPage) {
                firstPageLi.addClass("disabled");
                prePageLi.addClass("disabled");
            } else {
                firstPageLi.click(function() {
                    to_page(1);
                });
                prePageLi.click(function() {
                    to_page(result.extend.pageInfo.pageNum - 1);
                });
            }
            ul.append(firstPageLi).append(prePageLi);

            $.each(result.extend.pageInfo.navigatepageNums, function(index, item) {
                var numLi = $("<li></li>").append($("<a></a>").append(item));
                if (result.extend.pageInfo.pageNum == item) {
                    numLi.addClass("active");
                }
                numLi.click(function() {
                    to_page(item);
                });
                ul.append(numLi);
            });

            var nextPageLi = $("<li></li>").append($("<a></a>").append("&raquo;"));
            var lastPageLi = $("<li></li>").append($("<a></a>").append("last").attr("href", "#"));
            if (!result.extend.pageInfo.hasNextPage) {
                nextPageLi.addClass("disabled");
                lastPageLi.addClass("disabled");
            } else {
                nextPageLi.click(function() {
                    to_page(result.extend.pageInfo.pageNum + 1);
                });
                lastPageLi.click(function() {
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
