<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Update Data Sync</title>
<%
	pageContext.setAttribute("APP_PATH", request.getContextPath());
%>
<script type="text/javascript" src="${APP_PATH}/static/js/jquery-1.12.4.min.js"></script>
<link href="${APP_PATH}/static/bootstrap-3.3.7-dist/css/bootstrap.min.css" rel="stylesheet">
<link href="${APP_PATH}/static/css/bootstrap-datetimepicker.min.css" rel="stylesheet">
<script src="${APP_PATH}/static/bootstrap-3.3.7-dist/js/bootstrap.min.js"></script>
<script src="${APP_PATH}/static/js/bootstrap-datetimepicker.min.js"></script>
<script src="${APP_PATH}/static/js/bootstrap-datetimepicker.zh-CN.js"></script>
<style type="text/css">
	body {
		background: #f6f8fb;
	}
	.sync-card {
		margin-top: 24px;
		background: #fff;
		border: 1px solid #dfe6ee;
		border-radius: 8px;
		padding: 20px 22px;
		box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
	}
	.sync-title {
		font-size: 22px;
		font-weight: 700;
		color: #1f2d3d;
		margin-bottom: 8px;
	}
	.sync-subtitle {
		color: #5f6b7a;
		font-size: 13px;
		margin-bottom: 18px;
	}
	.result-box {
		margin-top: 14px;
		padding: 12px 14px;
		border-radius: 6px;
		border: 1px solid #d9e2ec;
		background: #fbfdff;
		font-size: 13px;
		line-height: 1.6;
		word-break: break-word;
		white-space: normal;
	}
	.result-success {
		border-color: #b9dfc3;
		background: #f4fff6;
	}
	.result-fail {
		border-color: #e8b8b8;
		background: #fff6f6;
	}
	.run-state {
		margin-top: 10px;
		font-size: 13px;
		font-weight: 600;
		color: #1f2d3d;
	}
	.run-state.running {
		color: #0b63b6;
	}
	.run-state.success {
		color: #2e7d32;
	}
	.run-state.failed {
		color: #c62828;
	}
	.device-checklist {
		margin-top: 12px;
		max-height: 220px;
		overflow-y: auto;
		border: 1px solid #d9e2ec;
		border-radius: 6px;
		padding: 10px 12px;
		background: #fbfdff;
	}
	.device-check-item {
		display: block;
		margin-bottom: 8px;
		font-size: 13px;
		font-weight: 400;
	}
	.device-check-item:last-child {
		margin-bottom: 0;
	}
	.device-inline-row {
		display: flex;
		align-items: center;
		gap: 14px;
		flex-wrap: wrap;
		margin-top: 12px;
	}
</style>
</head>
<body>
	<div class="container">
		<div class="row">
			<div class="col-md-8 col-md-offset-2">
				<div class="sync-card">
					<div class="sync-title">updatedata</div>

					<div class="form-inline">
						<label for="syncDateInput" style="margin-right:8px;">Select Date</label>
						<div class="input-group date" id="updateDataDatePicker" style="width:220px; display:inline-table; vertical-align:middle;">
							<input type="text" id="syncDateInput" class="form-control" placeholder="yyyy-mm-dd">
							<span class="input-group-addon">
								<span class="glyphicon glyphicon-calendar"></span>
							</span>
						</div>
						<button class="btn btn-primary" id="syncStatusByDateBtn" style="margin-left:10px;">SyncStatus</button>
						<button class="btn btn-danger" id="syncDeleteByDateBtn" style="margin-left:8px;">SyncDelete</button>
					</div>

					<div id="syncStatusResult" class="result-box" style="margin-top:18px;">Update Sync Result:</div>
					<div id="syncDeleteResult" class="result-box">Delete Sync Result:</div>
					<div id="runStateMsg" class="run-state">Idle</div>
				</div>

				<div class="sync-card">
					<div class="sync-title">delete user from all devices</div>
					<div class="form-inline">
						<label for="deleteEnrollIdInput" style="margin-right:8px;">User ID</label>
						<input type="text" id="deleteEnrollIdInput" class="form-control" placeholder="Enter User ID" style="width:220px;">
						<button class="btn btn-danger" id="deleteUserAllDevicesBtn" style="margin-left:10px;">Delete From All Devices</button>
					</div>

					<div id="deleteUserAllDevicesResult" class="result-box" style="margin-top:18px;">Result:</div>
				</div>

				<div class="sync-card">
					<div class="sync-title">send users to selected online devices</div>
					<div class="form-group" style="margin-bottom:0;">
						<label for="multiEnrollIdsInput" style="margin-bottom:8px;">User IDs</label>
						<input type="text" id="multiEnrollIdsInput" class="form-control" placeholder="490625, 518795, 447129">
					</div>
					<div class="device-inline-row">
						<label class="device-check-item" style="margin-bottom:0;">
							<input type="checkbox" id="selectAllOnlineDevices"> All Devices
						</label>
						<button class="btn btn-default btn-sm" id="refreshOnlineDevicesBtn" type="button">Refresh Devices</button>
						<button class="btn btn-primary" id="sendUsersSelectedDevicesBtn" type="button">Send To Selected Devices</button>
					</div>
					<div id="onlineDevicesList" class="device-checklist">Loading online devices...</div>
					<div id="sendUsersSelectedDevicesResult" class="result-box" style="margin-top:18px;">Result:</div>
				</div>

			</div>
		</div>
	</div>

<script type="text/javascript">
	function pad2(value){
		return value < 10 ? "0" + value : String(value);
	}

	function formatDate(date){
		return date.getFullYear() + "-" + pad2(date.getMonth() + 1) + "-" + pad2(date.getDate());
	}

	function setResult($target, isSuccess, text){
		$target.removeClass("result-success result-fail");
		$target.addClass(isSuccess ? "result-success" : "result-fail");
		$target.text(text);
	}

	function getSelectedDate(){
		return $.trim($("#syncDateInput").val());
	}

	var isSyncRunning = false;

	function setRunState(state, text){
		var $msg = $("#runStateMsg");
		$msg.removeClass("running success failed");
		if(state === "running"){
			$msg.addClass("running");
		}else if(state === "success"){
			$msg.addClass("success");
		}else if(state === "failed"){
			$msg.addClass("failed");
		}
		$msg.text(text);
	}

	function setSyncUiBusy(busy){
		isSyncRunning = busy === true;
		$("#syncStatusByDateBtn").prop("disabled", isSyncRunning);
		$("#syncDeleteByDateBtn").prop("disabled", isSyncRunning);
		$("#syncDateInput").prop("disabled", isSyncRunning);
		$("#deleteUserAllDevicesBtn").prop("disabled", isSyncRunning);
		$("#deleteEnrollIdInput").prop("disabled", isSyncRunning);
		$("#multiEnrollIdsInput").prop("disabled", isSyncRunning);
		$("#refreshOnlineDevicesBtn").prop("disabled", isSyncRunning);
		$("#sendUsersSelectedDevicesBtn").prop("disabled", isSyncRunning);
		$("#selectAllOnlineDevices").prop("disabled", isSyncRunning);
		$(".online-device-checkbox").prop("disabled", isSyncRunning);
	}

	function getSelectedOnlineDeviceSerials(){
		var selected = [];
		$(".online-device-checkbox:checked").each(function(){
			var serial = $.trim($(this).val());
			if(serial !== ""){
				selected.push(serial);
			}
		});
		return selected;
	}

	function syncSelectAllDevicesCheckbox(){
		var total = $(".online-device-checkbox").length;
		var checked = $(".online-device-checkbox:checked").length;
		$("#selectAllOnlineDevices").prop("checked", total > 0 && total === checked);
	}

	function renderOnlineDevices(devices){
		var $list = $("#onlineDevicesList");
		$list.empty();
		if(!devices || !devices.length){
			$list.text("No online devices found.");
			$("#selectAllOnlineDevices").prop("checked", false);
			return;
		}
		$.each(devices, function(_, item){
			var serial = item && item.serialNum ? item.serialNum : "";
			if(serial === ""){
				return;
			}
			var $label = $("<label></label>").addClass("device-check-item");
			var $checkbox = $("<input>").attr("type", "checkbox").addClass("online-device-checkbox").val(serial);
			$label.append($checkbox).append(" " + serial);
			$list.append($label);
		});
		syncSelectAllDevicesCheckbox();
	}

	function loadOnlineDevices(){
		$("#onlineDevicesList").text("Loading online devices...");
		$.ajax({
			url: "${APP_PATH}/onlineDevicesForDirectUserSend",
			type: "GET",
			cache: false,
			success: function(result){
				var ext = (result && result.extend) ? result.extend : {};
				renderOnlineDevices(ext.devices || []);
			},
			error: function(){
				$("#onlineDevicesList").text("Failed to load online devices.");
			}
		});
	}

	function buildStatusText(ext){
		return "Status direct send | date=" + (ext.syncDate || "")
			+ ", devices=" + (ext.devices || 0)
			+ ", onlineDevices=" + (ext.onlineDevices || 0)
			+ ", offlineDevices=" + (ext.offlineDevices || 0)
			+ ", users=" + (ext.users || 0)
			+ " (enable=" + (ext.enabledUsers || 0)
			+ ", disable=" + (ext.disabledUsers || 0) + ")"
			+ ", commandsSent=" + (ext.commandsSent || 0)
			+ " (enable=" + (ext.enableCommandsSent || 0)
			+ ", disable=" + (ext.disableCommandsSent || 0) + ")"
			+ ", plannedForOnlineDevices=" + (ext.commandsPlannedForOnlineDevices || 0)
			+ ", estimatedDispatchSeconds=" + (ext.estimatedDeviceDispatchSeconds || 0)
			+ ((ext.reason && ext.reason !== "null") ? ", reason=" + ext.reason : "");
	}

	function buildDeleteText(ext){
		return "Delete direct send | date=" + (ext.syncDate || "")
			+ ", devices=" + (ext.devices || 0)
			+ ", onlineDevices=" + (ext.onlineDevices || 0)
			+ ", offlineDevices=" + (ext.offlineDevices || 0)
			+ ", deletedUsers=" + (ext.deletedUsers || 0)
			+ ", commandsSent=" + (ext.commandsSent || 0)
			+ ", plannedForOnlineDevices=" + (ext.commandsPlannedForOnlineDevices || 0)
			+ ", estimatedDispatchSeconds=" + (ext.estimatedDeviceDispatchSeconds || 0)
			+ ((ext.reason && ext.reason !== "null") ? ", reason=" + ext.reason : "");
	}

	$(function(){
		$('#updateDataDatePicker').datetimepicker({
			format: 'yyyy-mm-dd',
			autoclose: true,
			minView: 2,
			language: 'en'
		});
		$("#syncDateInput").val(formatDate(new Date()));
		loadOnlineDevices();

		$("#syncStatusByDateBtn").click(function(){
			if(isSyncRunning){
				return;
			}
			var syncDate = getSelectedDate();
			if(syncDate === ""){
				setResult($("#syncStatusResult"), false, "Update Sync Result: Please select a date.");
				return;
			}
			setSyncUiBusy(true);
			setRunState("running", "Status sync is running for " + syncDate + ". Please wait...");
			setResult($("#syncStatusResult"), true, "Update Sync Result: Running direct status send for " + syncDate + " ...");
			$.ajax({
				url: "${APP_PATH}/syncUsersStatusByUpdDateAllDevices",
				type: "GET",
				cache: false,
				data: { syncDate: syncDate },
				success: function(result){
					var ext = (result && result.extend) ? result.extend : {};
					var text = buildStatusText(ext);
					var ok = result && result.code === 100;
					setResult($("#syncStatusResult"), ok, "Update Sync Result: " + text + ((result && result.code !== 100 && ext.error) ? " | error=" + ext.error : ""));
					setRunState(ok ? "success" : "failed", ok ? ("Status sync SUCCESS for " + syncDate) : ("Status sync FAILED for " + syncDate));
				},
				error: function(){
					setResult($("#syncStatusResult"), false, "Update Sync Result: Direct status send failed for " + syncDate + ".");
					setRunState("failed", "Status sync FAILED for " + syncDate);
				},
				complete: function(){
					setSyncUiBusy(false);
				}
			});
		});

		$("#syncDeleteByDateBtn").click(function(){
			if(isSyncRunning){
				return;
			}
			var syncDate = getSelectedDate();
			if(syncDate === ""){
				setResult($("#syncDeleteResult"), false, "Delete Sync Result: Please select a date.");
				return;
			}
			setSyncUiBusy(true);
			setRunState("running", "Delete sync is running for " + syncDate + ". Please wait...");
			setResult($("#syncDeleteResult"), true, "Delete Sync Result: Running direct delete send for " + syncDate + " ...");
			$.ajax({
				url: "${APP_PATH}/syncUsersDeleteByDelDateAllDevices",
				type: "GET",
				cache: false,
				data: { syncDate: syncDate },
				success: function(result){
					var ext = (result && result.extend) ? result.extend : {};
					var text = buildDeleteText(ext);
					var ok = result && result.code === 100;
					setResult($("#syncDeleteResult"), ok, "Delete Sync Result: " + text + ((result && result.code !== 100 && ext.error) ? " | error=" + ext.error : ""));
					setRunState(ok ? "success" : "failed", ok ? ("Delete sync SUCCESS for " + syncDate) : ("Delete sync FAILED for " + syncDate));
				},
				error: function(){
					setResult($("#syncDeleteResult"), false, "Delete Sync Result: Direct delete send failed for " + syncDate + ".");
					setRunState("failed", "Delete sync FAILED for " + syncDate);
				},
				complete: function(){
					setSyncUiBusy(false);
				}
			});
		});

		$("#deleteUserAllDevicesBtn").click(function(){
			if(isSyncRunning){
				return;
			}
			var enrollId = $.trim($("#deleteEnrollIdInput").val());
			if(enrollId === ""){
				setResult($("#deleteUserAllDevicesResult"), false, "Result: Please enter user ID.");
				return;
			}
			if(!/^[0-9]+$/.test(enrollId)){
				setResult($("#deleteUserAllDevicesResult"), false, "Result: Please enter valid numeric user ID.");
				return;
			}
			setSyncUiBusy(true);
			setRunState("running", "Delete by ID is running for user " + enrollId + ". Please wait...");
			setResult($("#deleteUserAllDevicesResult"), true, "Result: Sending delete command for user " + enrollId + " to all devices ...");
			$.ajax({
				url: "${APP_PATH}/deleteUserFromAllDevices",
				type: "GET",
				cache: false,
				data: { enrollId: enrollId },
				success: function(result){
					var ext = (result && result.extend) ? result.extend : {};
					var ok = result && result.code === 100;
					var text = "Delete by ID | userId=" + enrollId
						+ ", devices=" + (ext.devices || 0)
						+ ", onlineDevices=" + (ext.onlineDevices || 0)
						+ ", offlineDevices=" + (ext.offlineDevices || 0)
						+ ", commandsSent=" + (ext.commandsSent || 0);
					if(!ok && ext.error){
						text += " | error=" + ext.error;
					}
					setResult($("#deleteUserAllDevicesResult"), ok, "Result: " + text);
					setRunState(ok ? "success" : "failed", ok ? ("Delete by ID SENT for user " + enrollId) : ("Delete by ID FAILED for user " + enrollId));
				},
				error: function(){
					setResult($("#deleteUserAllDevicesResult"), false, "Result: Delete by ID failed for user " + enrollId + ".");
					setRunState("failed", "Delete by ID FAILED for user " + enrollId);
				},
				complete: function(){
					setSyncUiBusy(false);
				}
			});
		});

		$("#refreshOnlineDevicesBtn").click(function(){
			if(isSyncRunning){
				return;
			}
			loadOnlineDevices();
		});

		$(document).on("change", ".online-device-checkbox", function(){
			syncSelectAllDevicesCheckbox();
		});

		$("#selectAllOnlineDevices").change(function(){
			var checked = $(this).is(":checked");
			$(".online-device-checkbox").prop("checked", checked);
		});

		$("#sendUsersSelectedDevicesBtn").click(function(){
			if(isSyncRunning){
				return;
			}
			var enrollIds = $.trim($("#multiEnrollIdsInput").val());
			if(enrollIds === ""){
				setResult($("#sendUsersSelectedDevicesResult"), false, "Result: Please enter user IDs.");
				return;
			}
			var selectedDevices = getSelectedOnlineDeviceSerials();
			if(!selectedDevices.length){
				setResult($("#sendUsersSelectedDevicesResult"), false, "Result: Please select online devices.");
				return;
			}
			setSyncUiBusy(true);
			setRunState("running", "Selected device send is running. Please wait...");
			setResult($("#sendUsersSelectedDevicesResult"), true, "Result: Sending registration data to selected online devices ...");
			$.ajax({
				url: "${APP_PATH}/directSendUsersToSelectedDevices",
				type: "GET",
				cache: false,
				data: { enrollIds: enrollIds, deviceSns: selectedDevices.join(",") },
				success: function(result){
					var ext = (result && result.extend) ? result.extend : {};
					var ok = result && result.code === 100;
					var text = "Result: requestedIds=" + (ext.requestedIds || 0)
						+ ", usersFound=" + (ext.usersFound || 0)
						+ ", missingUsers=" + (ext.missingUsers || 0)
						+ ", devices=" + (ext.devices || 0)
						+ ", onlineDevices=" + (ext.onlineDevices || 0)
						+ ", offlineDevices=" + (ext.offlineDevices || 0)
						+ ", setuserinfoSent=" + (ext.setuserinfoSent || 0)
						+ ", imageRecordsPlanned=" + (ext.imageRecordsPlanned || 0)
						+ ", enableCommandsSent=" + (ext.enableCommandsSent || 0)
						+ ", commandsSent=" + (ext.commandsSent || 0);
					if(ext.missingIds && ext.missingIds.length){
						text += ", missingIds=" + ext.missingIds.join(",");
					}
					if(ext.selectedDevices && ext.selectedDevices.length){
						text += ", selectedDevices=" + ext.selectedDevices.join(",");
					}
					if(!ok && ext.error){
						text += " | error=" + ext.error;
					}
					setResult($("#sendUsersSelectedDevicesResult"), ok, text);
					setRunState(ok ? "success" : "failed", ok ? "Selected device send SUCCESS" : "Selected device send FAILED");
				},
				error: function(){
					setResult($("#sendUsersSelectedDevicesResult"), false, "Result: Selected device send failed.");
					setRunState("failed", "Selected device send FAILED");
				},
				complete: function(){
					setSyncUiBusy(false);
					loadOnlineDevices();
				}
			});
		});
	});
</script>
</body>
</html>
