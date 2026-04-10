<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>staff list</title>
<%
	pageContext.setAttribute("APP_PATH",request.getContextPath());
%>
<!-- 
	web è·¯å¾„
	ä¸ä»¥/å¼€å¤´çš„ç›¸å¯¹è·¯å¾„ï¼Œæ‰¾èµ„æºï¼Œä¼šä»¥å½“å‰èµ„æºçš„è·¯å¾„ä¸ºåŸºå‡†ï¼Œç»å¸¸å‡ºé—®é¢˜
	ä»¥/å¼€å¤´çš„ç›¸å¯¹è·¯å¾„ï¼Œæ‰¾èµ„æºï¼Œä¼šä»¥æœåŠ¡å™¨è·¯å¾„ä¸ºåŸºå‡†ï¼Œä¸ä¼šå‡ºé—®é¢˜
 -->
<!-- å¼•å…¥jQuery -->
<script type="text/javascript" src="${APP_PATH}/static/js/jquery-1.12.4.min.js"></script>
<!-- å¼•å…¥æ ·å¼ -->
<link href="${APP_PATH}/static/bootstrap-3.3.7-dist/css/bootstrap.min.css" rel="stylesheet">
<link href="${APP_PATH}/static/css/bootstrap-datetimepicker.min.css" rel="stylesheet">
 
<script src="${APP_PATH}/static/bootstrap-3.3.7-dist/js/bootstrap.min.js"></script>
<script src="${APP_PATH}/static/js/bootstrap-datetimepicker.min.js"></script>
	<script src="${APP_PATH}/static/js/bootstrap-datetimepicker.zh-CN.js"></script>
	<style type="text/css">
		#deviceStatusBox {
			margin-top: 8px;
			border: 1px solid #d6d6d6;
			border-radius: 6px;
			padding: 10px 12px;
			background: #fff;
		}
		#deviceStatusHeading {
			font-size: 13px;
			font-weight: 700;
			color: #333;
			margin-bottom: 8px;
		}
		#deviceStatusPanel {
			display: flex;
			flex-wrap: wrap;
			gap: 10px 18px;
		}
		.device-status-chip {
			display: inline-flex;
			align-items: center;
			padding: 2px 0;
			font-size: 12px;
			font-weight: 600;
			color: #333;
			animation: none;
		}
		.device-status-chip.online {
			color: #10763a;
		}
		.device-status-chip.offline {
			color: #9f1f1f;
		}
		.device-status-dot {
			width: 10px;
			height: 10px;
			border-radius: 50%;
			margin-right: 8px;
			position: relative;
			animation: none;
		}
		.device-status-chip.online .device-status-dot {
			background: #17b65a;
			box-shadow: 0 0 0 3px rgba(23, 182, 90, 0.2);
		}
		.device-status-chip.offline .device-status-dot {
			background: #e53935;
			box-shadow: 0 0 0 3px rgba(229, 57, 53, 0.2);
		}
		.device-status-dot:after {
			content: "";
			position: absolute;
			left: 50%;
			top: 50%;
			width: 20px;
			height: 20px;
			border-radius: 50%;
			transform: translate(-50%, -50%) scale(0.5);
			display: none;
			animation: none;
		}
		.page-brand {
			margin: 6px 0 8px;
		}
		.page-brand img {
			max-height: 56px;
			width: auto;
		}
		.top-right-controls {
			display: flex;
			justify-content: flex-end;
			align-items: center;
			gap: 8px;
			white-space: nowrap;
			overflow-x: auto;
			padding-bottom: 2px;
		}
		.top-right-controls label {
			margin: 0;
			font-weight: 600;
			white-space: nowrap;
		}
		#deviceSelect {
			width: 180px;
			height: 35px;
		}
		#deviceInOutModeSelect {
			width: 110px;
			height: 35px;
			padding: 4px 8px;
		}
		#saveDeviceInOutModeBtn {
			height: 35px;
			padding: 6px 12px;
		}
		#deviceInOutModeCurrent {
			margin: 0;
			font-size: 12px;
			font-weight: 600;
			white-space: nowrap;
		}
		.app-footer {
			margin-top: 18px;
			padding: 12px 0 18px;
			border-top: 1px solid #e5e5e5;
			text-align: center;
			color: #666;
			font-size: 12px;
		}
		.device-status-chip.online .device-status-dot:after {
			background: rgba(23, 182, 90, 0.35);
		}
		.device-status-chip.offline .device-status-dot:after {
			background: rgba(229, 57, 53, 0.35);
		}
		.device-status-empty {
			color: #777;
			font-size: 12px;
			font-style: italic;
		}
		@keyframes dotHeartbeat {
			0% { transform: scale(1); }
			20% { transform: scale(1.25); }
			40% { transform: scale(1); }
			100% { transform: scale(1); }
		}
		@keyframes dotPulse {
			0% { transform: translate(-50%, -50%) scale(0.5); opacity: 0.8; }
			70% { transform: translate(-50%, -50%) scale(1.5); opacity: 0; }
			100% { transform: translate(-50%, -50%) scale(1.5); opacity: 0; }
		}
		@keyframes statusPop {
			0% { transform: translateY(6px) scale(0.96); opacity: 0; }
			100% { transform: translateY(0) scale(1); opacity: 1; }
		}
	</style>

</head>
<body>

    <!-- ä¸‹è½½å•ä¸ªç”¨æˆ·æ¨¡æ€æ¡† -->
	<div class="modal fade" id="downLoadOneUserModal" tabindex="-1" role="dialog" aria-labelledby="myModalLabel">
	  <div class="modal-dialog" role="document">
	    <div class="modal-content">
	      <div class="modal-header">
	        <button type="button" class="close" data-dismiss="modal" aria-label="Close"><span aria-hidden="true">&times;</span></button>
	        <h4 class="modal-title">downloadOnePerson</h4>
	      </div>
	      <div class="modal-body">
	        	<form class="form-horizontal" id="form6">
	        	 
	        	   <div class="form-group" >
                      
                         <h5 class="col-xs-9">BackupNum 20-27 is face, 10 is password, 11 is card, 50 is photo</h5>
                    </div> 
	        	  <div class="form-group" >
                        <label class="control-label col-xs-3">EnrollId</label>
                        <div class="col-xs-9">
                           <select id="enrollIdSelect"  class="textbox combo" name="distribute_type" style="width: 180px; height: 35px;">						
                								
                           </select>
                        </div>
                    </div> 
                      <div class="form-group">
                        <label class="control-label col-xs-3">BackupNum</label>
                        <div class="col-xs-9">
                        	<select id="backupNumSelect1" class="form-control">
                        		<option value="10">Password</option>
                        		<option value="11">Card Number</option>
                        		<option value="20">Face 1</option>
                        		<option value="21">Face 1</option>
                        		<option value="22">Face 1</option>
                        		<option value="23">Face 1</option>
                        		<option value="24">Face 2</option>
                        		<option value="25">Face 2</option>
                        		<option value="26">Face 2</option>
                        		<option value="27">Face 2</option>
                        		<option value="50">Photo</option>
                        	</select>
                        </div>
                    </div>
				 
				</form>
	      </div>
	      <div class="modal-footer">
	        <button type="button" class="btn btn-default" data-dismiss="modal">return</button>
	        <button type="button" class="btn btn-primary" id="downloadOneUser_btu">save</button>
	      </div>
	    </div>
	  </div>
	</div>

<!-- ä¸Šä¼ ç”¨æˆ·æ¨¡æ€æ¡† -->
	<div class="modal fade" id="uploadOneUserModal" tabindex="-1" role="dialog" aria-labelledby="myModalLabel">
	  <div class="modal-dialog" role="document">
	    <div class="modal-content">
	      <div class="modal-header">
	        <button type="button" class="close" data-dismiss="modal" aria-label="Close"><span aria-hidden="true">&times;</span></button>
	        <h4 class="modal-title">uploadOneUser</h4>
	      </div>
	      <div class="modal-body">
	        	<form class="form-horizontal" id="form5">
	        	 
	        	   <div class="form-group" >
                      
                         <h5 class="col-xs-9">BackupNum 20-27 is face, 10 is password, 11 is card, 50 is photo</h5>
                    </div> 
	        	  <div class="form-group" >
                        <label class="control-label col-xs-3">EnrollId</label>
                        <div class="col-xs-9">
                        	<input class="form-control" disabled="disabled"  name="enrollId1" id="enrollId1">
                        </div>
                    </div> 
                      <div class="form-group">
                        <label class="control-label col-xs-3">BackupNum</label>
                        <div class="col-xs-9">
                        	<select id="backupNumSelect" class="form-control">
                        	    <option value="-1">name</option>
                        		<option value="10">Password</option>
                        		<option value="11">Card Number</option>
                        		<option value="20">Face 1</option>
                        		<option value="21">Face 1</option>
                        		<option value="22">Face 1</option>
                        		<option value="23">Face 1</option>
                        		<option value="24">Face 2</option>
                        		<option value="25">Face 2</option>
                        		<option value="26">Face 2</option>
                        		<option value="27">Face 2</option>
                        		<option value="50">Photo</option>
                        	</select>
                        </div>
                    </div>
				 
				</form>
	      </div>
	      <div class="modal-footer">
	        <button type="button" class="btn btn-default" data-dismiss="modal">return</button>
	        <button type="button" class="btn btn-primary" id="uploadOneUser_btu">save</button>
	      </div>
	    </div>
	  </div>
	</div>



<!-- è®¾ç½®æŽˆæƒæ¨¡æ€æ¡† -->
	<div class="modal fade" id="userGroupModal" tabindex="-1" role="dialog" aria-labelledby="myModalLabel">
	  <div class="modal-dialog" role="document">
	    <div class="modal-content">
	      <div class="modal-header">
	        <button type="button" class="close" data-dismiss="modal" aria-label="Close"><span aria-hidden="true">&times;</span></button>
	        <h4 class="modal-title">setUserLock</h4>
	      </div>
	      <div class="modal-body">
	        	<form class="form-horizontal" id="form4">
	        	 
	        	  <div class="form-group" >
                        <label class="control-label col-xs-3">enrollId</label>
                        <div class="col-xs-9">
                        	<input class="form-control" name="enrollId">
                        </div>
                    </div> 
                      <div class="form-group">
                        <label class="control-label col-xs-3">weekZone</label>
                        <div class="col-xs-9">
                        	<select class="form-control" name="weekZone">
                        		<option value="1">1</option>
                        		<option value="2">2</option>
                        		<option value="3">3</option>
                        		<option value="4">4</option>
                        		<option value="5">5</option>
                        		<option value="6">6</option>
                        		<option value="7">7</option>
                        		<option value="8">8</option>
                        	</select>
                        </div>
                    </div>
                   <div class="form-group">
                        <label class="control-label col-xs-3">group</label>
                        <div class="col-xs-9">
                        	<select class="form-control" name="group">
                        		<option value="1">1</option>
                        		<option value="2">2</option>
                        		<option value="3">3</option>
                        		<option value="4">4</option>
                        		<option value="5">5</option>
                        		<option value="6">6</option>
                        		<option value="7">7</option>
                        		<option value="8">8</option>
                        	</select>
                        </div>
                    </div>
				    <div class="form-group">
                        <label class="control-label col-xs-3">starttime</label>
                        <div class="col-xs-9">
                        	<input name="starttime" class="form-control date" >
                        </div>
                    </div> 
                     <div class="form-group">
                        <label class="control-label col-xs-3">endtime</label>
                        <div class="col-xs-9">
                        	<input name="endtime" class="form-control date">
                        </div>
                    </div> 
				 
				</form>
	      </div>
	      <div class="modal-footer">
	        <button type="button" class="btn btn-default" data-dismiss="modal">return</button>
	        <button type="button" class="btn btn-primary" id="userLock_btu">save</button>
	      </div>
	    </div>
	  </div>
	</div>


<!-- è¿œç¨‹å¼€é—¨æ¨¡æ€æ¡† -->
	<div class="modal fade" id="opneDoorModal" tabindex="-1" role="dialog" aria-labelledby="myModalLabel">
	  <div class="modal-dialog" role="document">
	    <div class="modal-content">
	      <div class="modal-header">
	        <button type="button" class="close" data-dismiss="modal" aria-label="Close"><span aria-hidden="true">&times;</span></button>
	        <h4 class="modal-title">OpenDoor</h4>
	      </div>
	      <div class="modal-body">
	        	<form class="form-horizontal" id="form5">
	        	 
	        	 
                      <div class="form-group">
                        <label class="control-label col-xs-3">DoorNum</label>
                        <div class="col-xs-9">
                        	<select class="form-control" name="DoorNum" id="doorNum">
                        		<option value="1">1</option>
                        		<option value="2">2</option>
                        		<option value="3">3</option>
                        		<option value="4">4</option>
                        		
                        	</select>
                        </div>
                    </div> 
				</form>
	      </div>
	      <div class="modal-footer">
	        <button type="button" class="btn btn-default" data-dismiss="modal">return</button>
	        <button type="button" class="btn btn-primary" id="openDoor_btu">save</button>
	      </div>
	    </div>
	  </div>
	</div>

<!-- èŽ·å–ç”¨æˆ·é—¨ç¦å‚æ•° -->
	<div class="modal fade" id="getUserLockModal" tabindex="-1" role="dialog" aria-labelledby="myModalLabel">
	  <div class="modal-dialog" role="document">
	    <div class="modal-content">
	      <div class="modal-header">
	        <button type="button" class="close" data-dismiss="modal" aria-label="Close"><span aria-hidden="true">&times;</span></button>
	        <h4 class="modal-title">getUserLock</h4>
	      </div>
	      <div class="modal-body">
	        	<form class="form-horizontal" id="form5">
	        	 
	        	 
                      <div class="form-group">
                        <label class="control-label col-xs-3">enrollId</label>
                        <div class="col-xs-9">
                        	<input class="form-control" name="enrollId" id="lockEnrollId">
                        </div>
                    </div> 
				</form>
	      </div>
	      <div class="modal-footer">
	        <button type="button" class="btn btn-default" data-dismiss="modal">return</button>
	        <button type="button" class="btn btn-primary" id="getUserLock_btu">save</button>
	      </div>
	    </div>
	  </div>
	</div>

<!-- è®¾ç½®é”ç»„åˆæ¨¡æ€æ¡† -->
	<div class="modal fade" id="lockGroupModal" tabindex="-1" role="dialog" aria-labelledby="myModalLabel">
	  <div class="modal-dialog" role="document">
	    <div class="modal-content">
	      <div class="modal-header">
	        <button type="button" class="close" data-dismiss="modal" aria-label="Close"><span aria-hidden="true">&times;</span></button>
	        <h4 class="modal-title">setLockGroup</h4>
	      </div>
	      <div class="modal-body">
	        	<form class="form-horizontal" id="form3">
	        	 
	        	  <div class="form-group" >
                        <label class="control-label col-xs-3">group1</label>
                        <div class="col-xs-9">
                        	<input class="form-control" name="group1">
                        </div>
                    </div> 
                     <div class="form-group">
                        <label class="control-label col-xs-3">group2</label>
                        <div class="col-xs-9">
                        	<input class="form-control" name="group2">
                        </div>
                    </div>  
                   <div class="form-group">
                        <label class="control-label col-xs-3">group3</label>
                        <div class="col-xs-9">
                        	<input class="form-control" name="group3">
                        </div>
                    </div>  
                    
				   <div class="form-group">
                        <label class="control-label col-xs-3">group4</label>
                        <div class="col-xs-9">
                        	<input class="form-control" name="group4">
                        </div>
                    </div> 
				  <div class="form-group">
                        <label class="control-label col-xs-3">group5</label>
                        <div class="col-xs-9">
                        	<input class="form-control" name="group5">
                        </div>
                    </div> 
				 
				</form>
	      </div>
	      <div class="modal-footer">
	        <button type="button" class="btn btn-default" data-dismiss="modal">return</button>
	        <button type="button" class="btn btn-primary" id="lockgroup_btu">save</button>
	      </div>
	    </div>
	  </div>
	</div>


<!-- è®¾ç½®å¤©æ—¶æ®µæ¨¡æ€æ¡† -->
	<div class="modal fade" id="accessDayModal" tabindex="-1" role="dialog" aria-labelledby="myModalLabel">
	  <div class="modal-dialog" role="document">
	    <div class="modal-content">
	      <div class="modal-header">
	        <button type="button" class="close" data-dismiss="modal" aria-label="Close"><span aria-hidden="true">&times;</span></button>
	        <h4 class="modal-title">setAccessDay</h4>
	      </div>
	      <div class="modal-body">
	        	<form class="form-horizontal" id="form2">
	        	 
	        	 <div class="form-group">
                        <label class="control-label col-xs-3">id</label>
                        <div class="col-xs-9">
                        	<select class="form-control" name="id">
                        		<option value="1">1</option>
                        		<option value="2">2</option>
                        		<option value="3">3</option>
                        		<option value="4">4</option>
                        		<option value="5">5</option>
                        		<option value="6">6</option>
                        		<option value="7">7</option>
                        		<option value="8">8</option>
                        	</select>
                        </div>
                    </div>
                     <div class="form-group">
                        <label class="control-label col-xs-3">serial</label>
                        <div class="col-xs-9">
                        	<input class="form-control" name="serial">
                        </div>
                    </div>  
                   <div class="form-group">
                        <label class="control-label col-xs-3">name</label>
                        <div class="col-xs-9">
                        	<input class="form-control" name="name">
                        </div>
                    </div>  
                    
				  <div class="form-group">
                        <label class="control-label col-xs-3">Time1</label>
                        <div class="col-xs-9">
                        	<div class="col-xs-6">
                        		<div class="input-group bootstrap-timepicker">
                        			<input class="form-control time" name="startTime1" value="00:00">
					                <span class="input-group-addon" style="padding:0px;"><i class="fa fa-clock-o"></i></span>
					            </div>
                        	</div>
                        	<div class="col-xs-6">
                        		<div class="input-group bootstrap-timepicker">
                        			<input class="form-control time" name="endTime1" value="00:00">
					                <span class="input-group-addon" style="padding:0px;"><i class="fa fa-clock-o"></i></span>
					            </div>
                        	</div>
                        </div>
                    </div>
				  <div class="form-group">
                        <label class="control-label col-xs-3">Time2</label>
                        <div class="col-xs-9">
                        	<div class="col-xs-6">
                        		<div class="input-group bootstrap-timepicker">
                        			<input class="form-control time" name="startTime2" value="00:00">
					                <span class="input-group-addon" style="padding:0px;"><i class="fa fa-clock-o"></i></span>
					            </div>
                        	</div>
                        	<div class="col-xs-6">
                        		<div class="input-group bootstrap-timepicker">
                        			<input class="form-control time" name="endTime2" value="00:00">
					                <span class="input-group-addon" style="padding:0px;"><i class="fa fa-clock-o"></i></span>
					            </div>
                        	</div>
                        </div>
                    </div>
				  <div class="form-group">
                        <label class="control-label col-xs-3">Time3</label>
                        <div class="col-xs-9">
                        	<div class="col-xs-6">
                        		<div class="input-group bootstrap-timepicker">
                        			<input class="form-control time" name="startTime3" value="00:00">
					                <span class="input-group-addon" style="padding:0px;"><i class="fa fa-clock-o"></i></span>
					            </div>
                        	</div>
                        	<div class="col-xs-6">
                        		<div class="input-group bootstrap-timepicker">
                        			<input class="form-control time" name="endTime3" value="00:00">
					                <span class="input-group-addon" style="padding:0px;"><i class="fa fa-clock-o"></i></span>
					            </div>
                        	</div>
                        </div>
                    </div>
				 <div class="form-group">
                        <label class="control-label col-xs-3">Time4</label>
                        <div class="col-xs-9">
                        	<div class="col-xs-6">
                        		<div class="input-group bootstrap-timepicker">
                        			<input class="form-control time" name="startTime4" value="00:00">
					                <span class="input-group-addon" style="padding:0px;"><i class="fa fa-clock-o"></i></span>
					            </div>
                        	</div>
                        	<div class="col-xs-6">
                        		<div class="input-group bootstrap-timepicker">
                        			<input class="form-control time" name="endTime4" value="00:00">
					                <span class="input-group-addon" style="padding:0px;"><i class="fa fa-clock-o"></i></span>
					            </div>
                        	</div>
                        </div>
                    </div>
				  
				  <div class="form-group">
                        <label class="control-label col-xs-3">Time5</label>
                        <div class="col-xs-9">
                        	<div class="col-xs-6">
                        		<div class="input-group bootstrap-timepicker">
                        			<input class="form-control time" name="startTime5" value="00:00">
					                <span class="input-group-addon" style="padding:0px;"><i class="fa fa-clock-o"></i></span>
					            </div>
                        	</div>
                        	<div class="col-xs-6">
                        		<div class="input-group bootstrap-timepicker">
                        			<input class="form-control time" name="endTime5" value="00:00">
					                <span class="input-group-addon" style="padding:0px;"><i class="fa fa-clock-o"></i></span>
					            </div>
                        	</div>
                        </div>
                    </div>
				</form>
	      </div>
	      <div class="modal-footer">
	        <button type="button" class="btn btn-default" data-dismiss="modal">return</button>
	        <button type="button" class="btn btn-primary" id="access_day_btu">save</button>
	      </div>
	    </div>
	  </div>
	</div>

<!-- add UserModal-->
<div class="modal fade" id="addUserModal" tabindex="-1" role="dialog"
		aria-labelledby="addUserModalTitle" aria-hidden="true">
		<div class="modal-dialog">
			<div class="modal-content">
				<!-- æ¨¡æ€æ¡†çš„æ ‡é¢˜ -->
				<div class="modal-header">
					<button type="button" class="close" data-dismiss="modal">
						<span aria-hidden="true">&times;</span><span class="sr-only">Close</span>
					</button>
					<h4 class="modal-title" id="addUserModalTitle">Add Person</h4>
				</div>
				<!-- æ¨¡æ€æ¡†çš„ä¸»ä½“-è¡¨å•å¤´éƒ¨ -->
				<form class="form-horizontal" role="form"
					action="${pageContext.request.contextPath }/savePerson"
					method="post" id="form" enctype="multipart/form-data">
					<input type="hidden" id="formMode" value="add">
					<div class="modal-body">
						<div class="form-group  form-group-lg">
							<label for="userId" class="col-sm-3 control-label">UserID:</label>
							<div class="col-sm-5">
								<input type="text" class="form-control input-lg"
									id="userIdInput" name="userId" placeholder="enter userId"
									required autofocus>
							</div>
						</div>
						<div class="form-group">
							<label for="name" class="col-sm-3 control-label">Name:</label>
							<div class="col-sm-5">
								<input type="text" class="form-control input-lg"
									id="personNameInput" name="name" placeholder="enter name"
									>
							</div>
						</div>
						<div class="form-group">
							<label for="privilege" class="col-sm-3 control-label">privilege:</label>
							<div class="col-sm-5">
								<select id="privilegeSelect" class="form-control" name="privilege">
								<option value="0">USER</option>
								<option value="1">MANAGER</option>
								</select>
							</div>
						</div>
						<div class="form-group">
							<label for="syncTargetSelect" class="col-sm-3 control-label">Sync To:</label>
							<div class="col-sm-5">
								<select id="syncTargetSelect" class="form-control" name="syncTarget">
									<option value="selected">Selected Device</option>
									<option value="all">All Devices</option>
								</select>
							</div>
						</div>

						<div class="form-group">
							<label for="photo" class="col-sm-3 control-label">photo:</label>
							<div class="col-sm-5">
								<input type="file" class="form-control input-lg"
									id="photoInput" placeholder="select photo" name="pic">
							</div>
						</div>
						
						<div class="form-group">
							<label for="name" class="col-sm-3 control-label">Password:</label>
							<div class="col-sm-5">
								<input type="text" class="form-control input-lg"
									id="passwordInput" name="password" placeholder="enter password" maxlength="10">
							</div>
						</div>
							<div class="form-group">
							<label for="name" class="col-sm-3 control-label">cardnum:</label>
							<div class="col-sm-5">
								<input type="text" class="form-control input-lg"
									id="cardNumInput" name="cardNum" placeholder="enter password" maxlength="20">
							</div>
						</div>
					</div>
					<!-- æ¨¡æ€æ¡†çš„å°¾éƒ¨ -->
					<div class="modal-footer">
						<button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
						<button type="submit" class="btn btn-primary" id="save">Save</button>
					</div>
				</form>
			</div>
		</div>
	</div>

	<!-- æ·»åŠ å‘¨æ—¶é—´æ¨¡æ€æ¡† -->
	<div class="modal fade" id="accessWeekAddModal" tabindex="-1" role="dialog" aria-labelledby="myModalLabel">
	  <div class="modal-dialog" role="document">
	    <div class="modal-content">
	      <div class="modal-header">
	        <button type="button" class="close" data-dismiss="modal" aria-label="Close"><span aria-hidden="true">&times;</span></button>
	        <h4 class="modal-title" id="myModalLabel">setAccessWeek</h4>
	      </div>
	      <div class="modal-body">
	        	<form class="form-horizontal" id="form1">
	        	     <div class="form-group" >
				    <label class="col-sm-2 control-label">id</label>
				    <div class="col-sm-4">
				     	<select class="form-control" name="id">
                        		<option value="1">1</option>
                        		<option value="2">2</option>
                        		<option value="3">3</option>
                        		<option value="4">4</option>
                        		<option value="5">5</option>
                        		<option value="6">6</option>
                        		<option value="7">7</option>
                        		<option value="8">8</option>
                        	</select>
				    </div>
	        	    </div>
	        	    
	        	      <div class="form-group" hidden>
				    <label class="col-sm-2 control-label">serial</label>
				    <div class="col-sm-4">
				      <input type="text" class="form-control" name="serial" id="name_accessweek_input" >
				      <span class="help-block"></span>
				    </div>
				  </div>
	        	      <div class="form-group">
				    <label class="col-sm-2 control-label">name</label>
				    <div class="col-sm-4">
				      <input type="text" class="form-control" name="name" id="name_accessweek_input" >
				      <span class="help-block"></span>
				    </div>
				  </div>
	        	    
	        	   <div class="form-group">
				    <label class="col-sm-2 control-label">Sunday</label>
				    <div class="col-sm-4">
				     	<select id="daySelect" class="form-control" name="sunday">
						 
						</select>
				    </div>
				    </div>
	        	  <div class="form-group">
				    <label class="col-sm-2 control-label">Monday</label>
				    <div class="col-sm-4">
				     	<select id="daySelect" class="form-control" name="monday">
						 
						</select>
				    </div>
				    </div>
	        	 <div class="form-group">
				    <label class="col-sm-2 control-label">Tuesday</label>
				    <div class="col-sm-4">
				     	<select id="daySelect" class="form-control" name="tuesday">
						 
						</select>
				    </div>
	        	</div>
	        	 <div class="form-group">
				    <label class="col-sm-2 control-label">Webnesday</label>
				    <div class="col-sm-4">
				     	<select id="daySelect" class="form-control" name="wednesday">
						 
						</select>
				    </div>
	        	</div>
				 <div class="form-group">
				    <label class="col-sm-2 control-label">Thursday</label>
				    <div class="col-sm-4">
				     	<select id="daySelect" class="form-control" name="thursday">
						 
						</select>
				    </div>
				    </div>
				 
				 <div class="form-group">
				    <label class="col-sm-2 control-label">Friday</label>
				    <div class="col-sm-4">
				     	<select id="daySelect" class="form-control" name="friday">
						 
						</select>
				    </div>
				  </div>
				  <div class="form-group">
				    <label class="col-sm-2 control-label">Saturday</label>
				    <div class="col-sm-4">
				     	<select id="daySelect" class="form-control" name="saturday">
						 
						</select>
				    </div>
				  </div>
				</form>
	      </div>
	      <div class="modal-footer">
	        <button type="button" class="btn btn-default" data-dismiss="modal">è¿”å›ž</button>
	        <button type="button" class="btn btn-primary" id="emp_save_btu">ä¿å­˜</button>
	      </div>
	    </div>
	  </div>
	</div>

	<!-- æ­å»ºæ˜¾ç¤ºé¡µé¢ -->
	<div class="container">
		<!-- æ ‡é¢˜ -->
		<div class="row">
			<div class="col-md-12">
				<div class="page-brand">
					<img src="${APP_PATH}/static/img/housys.jpg" alt="Housys">
				</div>
			</div>
		</div>
		<!-- æŒ‰é’® -->
		<div class="row">
			<div class="col-md-12">
				<div class="top-right-controls">
					<label for="deviceSelect">Select Device</label>
		              <select id="deviceSelect" class="textbox combo" name="distribute_type">						
		              </select>
		              <select id="deviceInOutModeSelect" class="form-control">
		              		<option value="AUTO">AUTO</option>
		              		<option value="IN">IN</option>
		              		<option value="OUT">OUT</option>
		              </select>
		              <button class="btn btn-primary btn-sm" id="saveDeviceInOutModeBtn">Save Device Mode</button>
		              <div id="deviceInOutModeCurrent">Current Mode: AUTO</div>
				</div>
				</div>
			</div>
		<div class="row">
			<div class="col-md-12">
				<div id="deviceStatusBox">
					<div id="deviceStatusHeading">Device Status</div>
					<div id="deviceStatusPanel"></div>
				</div>
			</div>
		</div>
		<div class="row" style="margin-top:8px;">
			<div class="col-md-12">
				<input type="text" class="form-control" id="userSearchInputTop" placeholder="Search by User ID / Name" style="width: 320px; display:inline-block;">
				<button class="btn btn-warning" id="syncDbAllDevicesBtn" style="margin-left:8px;">SetUserToAllDevice (With Image)</button>
				<button class="btn btn-default" id="syncStatusAllDevicesBtn" style="margin-left:8px;">Status Sync (Active/Deactive)</button>
				<button class="btn btn-primary" id="updateDataPageBtn" style="margin-left:8px;">updatedata</button>
				<button class="btn btn-success" id="enableMasterDeviceSyncBtn" style="margin-left:8px;display:none;">Set Selected As Master</button>
				<button class="btn btn-default" id="disableMasterDeviceSyncBtn" style="margin-left:8px;display:none;">Disable Master Sync</button>
				<button class="btn btn-info" id="syncMasterDeviceFullBtn" style="margin-left:8px;display:none;">Master Full Sync -> Others</button>
				<div id="syncDbAllDevicesStatus" style="margin-top:8px;font-weight:600;white-space:normal;word-break:break-word;"></div>
				<div id="syncStatusAllDevicesStatus" style="margin-top:4px;font-weight:600;white-space:normal;word-break:break-word;"></div>
				<div id="syncDbAllDevicesDeviceStatus" style="margin-top:6px;font-size:12px;line-height:1.5;white-space:normal;word-break:break-word;"></div>
				<div id="masterDeviceSyncStatus" style="margin-top:6px;font-size:12px;line-height:1.5;white-space:normal;word-break:break-word;display:none;"></div>
			</div>
		</div>
		<!-- è¡¨æ ¼æ˜¾ç¤ºå†…å®¹ -->
		<div class="row">
			<div class="col-md-12">
				<table class="table table-hover table-striped" id = "emps_table">
					<thead>
						<tr>
							<th>
								<input type="checkbox" id="check_all" />
							</th>
							<th>#</th>
							<th>EmployeeId</th>
							<th>Name</th>
							<th>Photo</th>
							<th>Level</th>
							<th>Status</th>
							<th>Operation</th>
						</tr>
					</thead>
					<tbody>
					
					</tbody>
				</table>
			</div>
		</div>
		<!-- åˆ†é¡µæ˜¾ç¤ºä¿¡æ¯ -->
		<div class="row">
			<!-- æ˜¾ç¤ºåˆ†é¡µæ–‡å­—ä¿¡æ¯ -->
			<div class="col-md-6" id = "page_info_area">
				
			</div>
			<!-- æ˜¾ç¤ºåˆ†é¡µæ¡ä¿¡æ¯ -->
			<div class="col-md-4 col-md-offset-2" id = "page_nav_area">
			
			</div>
		</div>
		<button class="btn btn-primary" id="collectList_emp_modal_btn" disabled="disabled">getUserList</button>
		<button class="btn btn-primary" id="collectInfo_emp_modal_btn" disabled="disabled">getUserInfo</button>
		<button class="btn btn-primary" id="setUserToDevice_emp_modal_btn">SetUserToDevice</button>
		<button class="btn btn-primary" id="setUserName_modal_btn" disabled="disabled">setUserNameToDevice</button>
		<button class="btn btn-primary" id="initSys_emp_modal_btn" disabled="disabled">InitSystem</button>
		<button class="btn btn-primary" id="logInfo_emp_modal_btn">LogRecord</button>
		<button class="btn btn-info" id="climsInfo_emp_modal_btn">CLIMSRecord</button>
		<button class="btn btn-warning" id="schedulerStatus_emp_modal_btn">Status</button>
		<button class="btn btn-primary" id="download_emp_modal_btn" disabled="disabled">downloadSelectMessage</button>
		<button class="btn btn-primary" id="getDeviceInfo_modal_btn" disabled="disabled">getDeviceInfo</button>
		<button class="btn btn-primary" id="openDoor_modal_btn" disabled="disabled">openDoor</button>
		<button class="btn btn-primary" id="addUser_modal_btn" disabled="disabled">Add user</button>
		<button class="btn btn-primary" id="getDevLock_modal_btn" disabled="disabled">getDevLock</button>
		<button class="btn btn-primary" id="getUserLock_modal_btn" disabled="disabled">getUserLock</button>
		<button class="btn btn-primary" id="cleanAdmin_modal_btn" disabled="disabled">cleanAdmin</button>
		<button class="btn btn-primary" id="synchronize_Time" disabled="disabled">SynchronizeTime</button>
		<div class="form-inline" style="margin-top:10px;">
			<input type="text" class="form-control" id="enableUserIdInput" placeholder="User ID (blank = DB sync)" style="width: 190px;">
			<select class="form-control" id="enableFlagSelect">
				<option value="1">Active (1)</option>
				<option value="0">Deactive (0)</option>
			</select>
			<button class="btn btn-primary" id="setUserEnable_btn" disabled="disabled">Send Enable/Disable</button>
		</div>
		<div class="app-footer">Powered by Houston System | © 2026 Houston System. All rights reserved.</div>
	</div>
	<script type="text/javascript">
		var totalRecord,currentPage;
		var currentUserSearchKeyword = "";
		var userSearchDebounceTimer = null;
			var deviceStatusRefreshMs = 5000;
			var deviceStatusRefreshTimer = null;
			var deviceInOutModeBySn = {};

		function normalizeUserSearchText(value){
			return $.trim((value || ""));
		}

		function toInt(value, fallback){
			var parsed = parseInt(value, 10);
			if(isNaN(parsed)){
				return (typeof fallback === "number") ? fallback : 0;
			}
			return parsed;
		}

		function applyUserSearchFilter(){
			var keyword = normalizeUserSearchText(currentUserSearchKeyword).toLowerCase();
			$("#emps_table tbody tr").each(function(){
				var row = $(this);
				var userIdText = normalizeUserSearchText(row.find("td:eq(2)").text()).toLowerCase();
				var userNameText = normalizeUserSearchText(row.find("td:eq(3)").text()).toLowerCase();
				var matched = (keyword === "")
					|| (userIdText.indexOf(keyword) !== -1)
					|| (userNameText.indexOf(keyword) !== -1);
				row.toggle(matched);
			});
		}

		function queueUserSearchReload(){
			if(userSearchDebounceTimer){
				clearTimeout(userSearchDebounceTimer);
			}
			userSearchDebounceTimer = setTimeout(function(){
				to_page(1);
			}, 220);
		}

			function normalizeDeviceOnlineStatus(value){
				return parseInt(value, 10) === 1;
			}

			function normalizeDeviceInOutMode(value){
				var normalized = $.trim((value || "")).toUpperCase();
				if(normalized === "IN" || normalized === "0" || normalized === "ENTRY"){
					return "IN";
				}
				if(normalized === "OUT" || normalized === "1" || normalized === "EXIT"){
					return "OUT";
				}
				return "AUTO";
			}

			function refreshDeviceInOutModeUi(){
				var serialNum = $.trim($("#deviceSelect").val() || "");
				var mode = "AUTO";
				if(serialNum !== "" && deviceInOutModeBySn.hasOwnProperty(serialNum)){
					mode = normalizeDeviceInOutMode(deviceInOutModeBySn[serialNum]);
				}
				$("#deviceInOutModeSelect").val(mode);
				$("#deviceInOutModeCurrent").text("Current Mode: " + mode);
			}

			function saveSelectedDeviceInOutMode(){
				var serialNum = $.trim($("#deviceSelect").val() || "");
				var mode = normalizeDeviceInOutMode($("#deviceInOutModeSelect").val());
				if(serialNum === ""){
					alert("please select device");
					return;
				}
				$("#saveDeviceInOutModeBtn").prop("disabled", true);
				$.ajax({
					url:"${APP_PATH}/setDeviceInOutMode?deviceSn=" + encodeURIComponent(serialNum) + "&mode=" + encodeURIComponent(mode),
					type:"GET",
					cache:false,
					success:function(result){
						var ext = (result && result.extend) ? result.extend : {};
						var savedMode = normalizeDeviceInOutMode(ext.mode || mode);
						deviceInOutModeBySn[serialNum] = savedMode;
						refreshDeviceInOutModeUi();
						var queued = ext && ext.commandQueued === true;
						var alertText = "Device mode saved: " + serialNum + " -> " + savedMode;
						if(queued){
							alertText += "\nDevice command queued successfully.";
						}else if(ext && ext.applyToDeviceRequested === true){
							alertText += "\nMode saved in DB, but device command not queued.";
						}
						alert(alertText);
					},
					error:function(xhr){
						var detail = "";
						if(xhr && xhr.responseText){
							detail = xhr.responseText;
						}
						alert("Failed to save device mode." + (detail ? ("\n" + detail) : ""));
					},
					complete:function(){
						$("#saveDeviceInOutModeBtn").prop("disabled", false);
					}
				});
			}

			function renderDeviceStatusPanel(devices){
			var panel = $("#deviceStatusPanel");
			panel.empty();
			if(!devices || !devices.length){
				$("<div></div>").addClass("device-status-empty")
					.text("No devices available.")
					.appendTo(panel);
				return;
			}
				$.each(devices, function(index, device){
					var serialNum = $.trim((device && device.serialNum) ? device.serialNum : "");
					if(serialNum === ""){
						return;
					}
					var modeText = normalizeDeviceInOutMode(device ? device.inOutMode : "");
					var isOnline = normalizeDeviceOnlineStatus(device.status);
					var chip = $("<div></div>").addClass("device-status-chip")
						.addClass(isOnline ? "online" : "offline");
					$("<span></span>").addClass("device-status-dot").appendTo(chip);
					$("<span></span>").text(serialNum + " [" + modeText + "]").appendTo(chip);
					chip.appendTo(panel);
				});
			if(panel.children().length === 0){
				$("<div></div>").addClass("device-status-empty")
					.text("No devices available.")
					.appendTo(panel);
			}
		}

		function triggerDbAllDevicesSync(withConfirm){
			if(withConfirm){
				if(!confirm("Run full image sync (setuserinfo) to all devices?")){
					return;
				}
			}
			$("#syncDbAllDevicesStatus").text("SetUserToAllDevice starting...");
			$("#syncDbAllDevicesDeviceStatus").text("");
			$.ajax({
				url:"${APP_PATH}/setUserToAllDevice",
				type:"GET",
				cache:false,
				success:function(result){
					var ext = (result && result.extend) ? result.extend : {};
					if(ext.accepted === true){
						$("#syncDbAllDevicesStatus").text("Full image sync started in background...");
					}else if(ext.running === true){
						$("#syncDbAllDevicesStatus").text("Sync already running...");
					}else{
						$("#syncDbAllDevicesStatus").text("Sync trigger sent");
					}
					refreshDbSyncStatus();
				},
				error:function(){
					$("#syncDbAllDevicesStatus").text("Sync failed");
				}
			});
		}

		function renderDbSyncDeviceStatus(deviceDetails, deviceSyncStateRows){
			var panel = $("#syncDbAllDevicesDeviceStatus");
			panel.empty();
			var runtimeDetails = $.isArray(deviceDetails) ? deviceDetails : [];
			var stateRows = $.isArray(deviceSyncStateRows) ? deviceSyncStateRows : [];
			if(runtimeDetails.length === 0 && stateRows.length === 0){
				return;
			}
			var runtimeBySerial = {};
			$.each(runtimeDetails, function(index, detail){
				if(!detail){ return; }
				var runtimeSerial = $.trim(detail.serial || "");
				if(runtimeSerial === ""){ return; }
				runtimeBySerial[runtimeSerial.toUpperCase()] = detail;
			});
			var mergedRows = [];
			$.each(stateRows, function(index, row){
				if(!row){ return; }
				var serial = $.trim(row.serial || "");
				if(serial === ""){ return; }
				var key = serial.toUpperCase();
				mergedRows.push({
					serial: serial,
					state: row,
					runtime: runtimeBySerial[key] || null
				});
				delete runtimeBySerial[key];
			});
			$.each(runtimeBySerial, function(serialKey, detail){
				if(!detail){ return; }
				mergedRows.push({
					serial: $.trim(detail.serial || serialKey),
					state: null,
					runtime: detail
				});
			});
			if(mergedRows.length === 0){
				return;
			}
			var table = $("<table></table>").addClass("table table-condensed table-bordered");
			var thead = $("<thead></thead>");
			$("<tr></tr>")
				.append($("<th></th>").text("#"))
				.append($("<th></th>").text("Device"))
				.append($("<th></th>").text("Online"))
				.append($("<th></th>").text("Last Status"))
				.append($("<th></th>").text("Last Sync At"))
				.append($("<th></th>").text("Last Queued"))
				.append($("<th></th>").text("Delivered (Success)"))
				.append($("<th></th>").text("Last CleanAdmin"))
				.append($("<th></th>").text("Total Runs"))
				.append($("<th></th>").text("Pending Now"))
				.append($("<th></th>").text("Queued Users"))
				.append($("<th></th>").text("Queued This Run"))
				.append($("<th></th>").text("ETA(sec)"))
				.append($("<th></th>").text("Message"))
				.appendTo(thead);
			table.append(thead);
			var tbody = $("<tbody></tbody>");
			$.each(mergedRows, function(index, row){
				if(!row){ return; }
				var state = row.state || {};
				var detail = row.runtime || {};
				var serial = $.trim(row.serial || detail.serial || state.serial || "");
				if(serial === ""){ serial = "device-" + (index + 1); }
				var stateOnline = state.online;
				var detailOnline = detail.online;
				var online = (stateOnline === true || detailOnline === true || toInt(stateOnline, 0) === 1 || toInt(detailOnline, 0) === 1) ? "Yes" : "No";
				var lastStatus = $.trim((state.lastSyncStatus || detail.syncStatus || "NEVER")).toUpperCase();
				var lastSyncAt = $.trim(state.lastSyncAt || detail.lastSyncAt || "");
				if(lastSyncAt === ""){ lastSyncAt = "-"; }
				var lastQueued = toInt(state.lastQueuedSetuserinfo, toInt(detail.queuedSetuserinfo, 0));
				var deliveredSuccess = toInt(state.deliveredSetuserinfoCount, 0);
				var lastCleanAdmin = toInt(state.lastQueuedCleanadmin, toInt(detail.queuedCleanAdmin, 0));
				var totalRuns = toInt(state.totalRuns, 0);
				var pendingNow = (detail.pendingAfter === undefined || detail.pendingAfter === null || detail.pendingAfter === "") ? "-" : toInt(detail.pendingAfter, 0);
				var queuedUsers = (detail.deltaUserCount === undefined || detail.deltaUserCount === null || detail.deltaUserCount === "") ? "-" : toInt(detail.deltaUserCount, 0);
				var queuedThisRun = (detail.queuedSetuserinfo === undefined || detail.queuedSetuserinfo === null || detail.queuedSetuserinfo === "") ? "-" : toInt(detail.queuedSetuserinfo, 0);
				var etaSec = (detail.estimatedDispatchSeconds === undefined || detail.estimatedDispatchSeconds === null || detail.estimatedDispatchSeconds === "") ? "-" : toInt(detail.estimatedDispatchSeconds, 0);
				var message = $.trim(detail.syncMessage || state.lastErrorMessage || state.lastSyncMessage || "");
				if(message === ""){
					message = "-";
				}
				$("<tr></tr>")
					.append($("<td></td>").text(index + 1))
					.append($("<td></td>").text(serial))
					.append($("<td></td>").text(online))
					.append($("<td></td>").text(lastStatus))
					.append($("<td></td>").text(lastSyncAt))
					.append($("<td></td>").text(lastQueued))
					.append($("<td></td>").text(deliveredSuccess))
					.append($("<td></td>").text(lastCleanAdmin))
					.append($("<td></td>").text(totalRuns))
					.append($("<td></td>").text(pendingNow))
					.append($("<td></td>").text(queuedUsers))
					.append($("<td></td>").text(queuedThisRun))
					.append($("<td></td>").text(etaSec))
					.append($("<td></td>").text(message))
					.appendTo(tbody);
			});
			table.append(tbody);
			panel.append(table);
		}

		function refreshDbSyncStatus(){
			$.ajax({
				url:"${APP_PATH}/setUserToAllDeviceStatus",
				type:"GET",
				cache:false,
				success:function(result){
					var ext = (result && result.extend) ? result.extend : {};
					var running = ext.running === true;
					var state = ext.state || "";
					var totalQueued = toInt(ext.totalCommandsQueued);
					var setuserinfoQueued = toInt(ext.setuserinfoCommandsQueued);
					var cleanadminQueued = toInt(ext.cleanAdminCommandsQueued);
					var activeUsers = toInt(ext.activeUsers);
					var enrollRecords = toInt(ext.totalEnrollRecords);
					var eta = toInt(ext.estimatedDeviceDispatchSeconds);
					var stateRows = $.isArray(ext.deviceSyncStateRows) ? ext.deviceSyncStateRows : [];
					var successCount = 0;
					var failedCount = 0;
					var neverCount = 0;
					$.each(stateRows, function(index, row){
						if(!row){ return; }
						var status = $.trim((row.lastSyncStatus || "")).toUpperCase();
						if(status === "SUCCESS"){
							successCount++;
						}else if(status === "FAILED"){
							failedCount++;
						}else if(status === "" || status === "NEVER"){
							neverCount++;
						}
					});
					var message = $.trim(ext.message || "");
					var normalizedState = $.trim((state || "")).toUpperCase();
					if(normalizedState === ""){
						normalizedState = running ? "RUNNING" : "IDLE";
					}
					var text = "SetUserToAllDevice | state=" + normalizedState
						+ " | users=" + activeUsers
						+ " | enrollRecords=" + enrollRecords
						+ " | queued=" + totalQueued
						+ " (setuserinfo=" + setuserinfoQueued + ", cleanadmin=" + cleanadminQueued + ")"
						+ " | devices=" + stateRows.length
						+ " (ok=" + successCount + ", fail=" + failedCount + ", never=" + neverCount + ")"
						+ " | eta=" + eta + " sec";
					if(normalizedState === "FAILED" && message !== ""){
						text += " | reason=" + message.substring(0, 120);
						if(message.length > 120){
							text += "...";
						}
					}
					text += " | details: Status page";
					$("#syncDbAllDevicesStatus").text(text);
					$("#syncDbAllDevicesDeviceStatus").empty();
				}
			});
		}

		function renderMasterDeviceSyncStatus(ext){
			ext = ext || {};
			var enabled = ext.masterSyncEnabled === true;
			var sourceSn = $.trim(ext.masterSyncSourceSn || "");
			var relayed = parseInt(ext.masterSyncRelayedRecords, 10);
			if(isNaN(relayed)){ relayed = 0; }
			var queued = parseInt(ext.masterSyncQueuedCommands, 10);
			if(isNaN(queued)){ queued = 0; }
			var updated = $.trim(ext.masterSyncLastUpdated || "");
			var message = $.trim(ext.masterSyncLastMessage || "");
			var record = $.trim(ext.masterSyncLastRecord || "");
			var statusText = enabled ? "ON" : "OFF";
			var text = "Master Sync " + statusText;
			if(sourceSn !== ""){
				text += ", source=" + sourceSn;
			}
			text += ", relayedRecords=" + relayed + ", queuedCommands=" + queued;
			if(updated !== ""){
				text += ", updated=" + updated;
			}
			if(message !== ""){
				text += ", message=" + message;
			}
			if(record !== ""){
				text += ", lastRecord=" + record;
			}
			$("#masterDeviceSyncStatus").text(text);
		}

		function refreshMasterDeviceSyncStatus(){
			$.ajax({
				url:"${APP_PATH}/masterDeviceSyncStatus",
				type:"GET",
				cache:false,
				success:function(result){
					var ext = (result && result.extend) ? result.extend : {};
					renderMasterDeviceSyncStatus(ext);
				}
			});
		}

		function configureMasterDeviceSyncFromSelection(enabled){
			var deviceSn = $.trim($("#deviceSelect").val());
			if(enabled && deviceSn === ""){
				alert("please select master device");
				return;
			}
			var url = "${APP_PATH}/configureMasterDeviceSync?enabled=" + (enabled ? "true" : "false");
			if(deviceSn !== ""){
				url += "&masterSn=" + encodeURIComponent(deviceSn);
			}
			$.ajax({
				url:url,
				type:"GET",
				cache:false,
				success:function(result){
					var ext = (result && result.extend) ? result.extend : {};
					renderMasterDeviceSyncStatus(ext);
					alert(result.msg);
				}
			});
		}

		function triggerMasterFullSyncFromSelection(){
			var deviceSn = $.trim($("#deviceSelect").val());
			if(deviceSn === ""){
				alert("please select master device");
				return;
			}
			if(!confirm("Queue full registration sync from DB to all devices except selected master?")){
				return;
			}
			$.ajax({
				url:"${APP_PATH}/syncMasterDeviceFullToAll?masterSn=" + encodeURIComponent(deviceSn) + "&includeMaster=false",
				type:"GET",
				cache:false,
				success:function(result){
					var ext = (result && result.extend) ? result.extend : {};
					var note = $.trim(ext.note || "");
					var targetDevices = parseInt(ext.targetDevices, 10);
					if(isNaN(targetDevices)){ targetDevices = 0; }
					var text = "Full sync queued to target devices: " + targetDevices;
					if(note !== ""){
						text += "\n" + note;
					}
					alert(text);
				}
			});
		}
		// 1. é¡µé¢åŠ è½½æˆåŠŸä¹‹åŽç›´æŽ¥å‘é€ ajax è¯·æ±‚å¾—åˆ° åˆ†é¡µæ•°æ®
		//é¡µé¢åŠ è½½å®Œæˆä¹‹åŽï¼Œç›´æŽ¥å‘é€ajax è¯·æ±‚ï¼ŒåŽ»é¦–é¡µ
		$(function(){
			$('.date').datetimepicker({
			    format: 'yyyy-mm-dd',
			    autoclose: true,
			    minView: 2,
			    language: 'en'
			})
			//åŽ»é¦–é¡µ
			var deviceSn=document.getElementById('deviceSelect').value;
			getDevice();
			to_page(1);
			refreshDbSyncStatus();
			refreshMasterDeviceSyncStatus();
			setInterval(refreshDbSyncStatus, 5000);
			setInterval(refreshMasterDeviceSyncStatus, 5000);
			if(deviceStatusRefreshTimer){
				clearInterval(deviceStatusRefreshTimer);
			}
			deviceStatusRefreshTimer = setInterval(function(){
				getDevice();
			}, deviceStatusRefreshMs);
				$("#userSearchInputTop").on("input", function(){
					currentUserSearchKeyword = normalizeUserSearchText($(this).val());
					queueUserSearchReload();
				});
				$("#deviceSelect").on("change", function(){
					refreshDeviceInOutModeUi();
				});
				$("#saveDeviceInOutModeBtn").on("click", function(){
					saveSelectedDeviceInOutMode();
				});
			});
		
		function to_page(pn){
			$.ajax({
				url:"${APP_PATH}/emps",
				data:{
					pn:pn,
					keyword:currentUserSearchKeyword
				},
				type:"GET",
				success:function(result){
					//console.log(result);
					// 1. è§£æžå¹¶æ˜¾ç¤ºå‘˜å·¥æ•°æ®
					build_emp_table(result);
					// 2. è§£æžå¹¶æ˜¾ç¤ºåˆ†ç±»ä¿¡æ¯
					buid_page_info(result);
					// 3. è§£æžå¹¶æ˜¾ç¤ºåˆ†é¡µæ¡ä¿¡æ¯
					build_page_nav(result);
				}
			});
		}
		
		//è§£æžæ˜¾ç¤ºå‘˜å·¥ä¿¡æ¯
		function build_emp_table(result){
			$("#emps_table tbody").empty();
			var emps = result.extend.pageInfo.list;
			var i = result.extend.pageInfo.startRow;
			$.each(emps,function(index,item){
				//alert(item.empName);
				var checkBoxTd = $("<td><input type='checkbox' class='check_item'/></td>");
				var numTd = $("<td></td>").append(i++);
				var userId = item.enrollId || item.id;
				var empIdTd = $("<td></td>").append(userId);
				var empNameTd = $("<td></td>").append(item.name);
				var empImageTd = $("<td></td>");
				if(item.imagePath && item.imagePath !== "null"){
					empImageTd.append("<img style='width:60px; height:60px;' src='"+"${APP_PATH}/img/"+item.imagePath+"'/>");
				}else{
					empImageTd.append("-");
				}
				var roleText = (item.admin && parseInt(item.admin, 10) > 0) ? "Manager" : "User";
				var rollId = $("<td></td>").append(roleText);
				var statusFlag = parseInt(item.status, 10);
				if(isNaN(statusFlag)){
					statusFlag = 1;
				}
				var statusTd = $("<td></td>").append(statusFlag === 0 ? "Deactive" : "Active");
				
				var uploadBtu = $("<button></button>").addClass("btn btn-info btn-sm upload_btu")
				.append("<span></span>").append("uploadPersonToDevice");
	           //ä¸ºç¼–è¾‘æŒ‰é’®æ·»åŠ ä¸€ä¸ªè‡ªå®šä¹‰çš„å±žæ€§ï¼Œæ¥è¡¨ç¤ºå½“å‰çš„å‘˜å·¥id
	            uploadBtu.attr("upload-id",userId);
				var editBtu = $("<button></button>").addClass("btn btn-primary btn-sm edit_btu")
					.append("<span></span>").append("EditUser");
				editBtu.attr("edit-id",userId);
				
				var delBtu = $("<button></button>").addClass("btn btn-danger btn-sm delete_btu")
							.append("<span></span>").append("DeleteUser");
				//ä¸ºåˆ é™¤æŒ‰é’®æ·»åŠ ä¸€ä¸ªè‡ªå®šä¹‰çš„å±žæ€§ï¼Œæ¥è¡¨ç¤ºå½“å‰çš„å‘˜å·¥id
				delBtu.attr("delete-id",userId);
				delBtu.prop("disabled", true).attr("title", "Delete disabled");
				var disableBtu = $("<button></button>").addClass("btn btn-warning btn-sm disable_user_btu")
					.append("<span></span>").append("Disable");
				disableBtu.attr("status-id",userId);
				disableBtu.prop("disabled", true);
				var enableBtu = $("<button></button>").addClass("btn btn-success btn-sm enable_user_btu")
					.append("<span></span>").append("Enable");
				enableBtu.attr("status-id",userId);
				enableBtu.prop("disabled", true);
				var btuTd = $("<td></td>").append(delBtu).append(" ").append(editBtu).append(" ").append(uploadBtu)
					.append(" ").append(disableBtu).append(" ").append(enableBtu);
				//appendæ–¹æ³•æ‰§è¡Œå®ŒåŽä»è¿”å›žåŽŸæ¥çš„å…ƒç´ 
				$("<tr></tr>").append(checkBoxTd)
					.append(numTd)
					.append(empIdTd)
					.append(empNameTd)
					.append(empImageTd)
					.append(rollId)
					.append(statusTd)
					.append(btuTd)
					.appendTo("#emps_table tbody");
			})
		}
		//è§£æžæ˜¾ç¤ºåˆ†é¡µä¿¡æ¯
		function buid_page_info(result){
			$("#page_info_area").empty();
			$("#page_info_area").append("Current Page:"+result.extend.pageInfo.pageNum +
					", Count Page:"+result.extend.pageInfo.pages +
					", All Recordsï¼š"+result.extend.pageInfo.total);
			totalRecord = result.extend.pageInfo.pages;
			currentPage = result.extend.pageInfo.pageNum;
		}
		
		//è§£æžæ˜¾ç¤ºåˆ†é¡µæ¡ä¿¡æ¯
		function build_page_nav(result){
			$("#page_nav_area").empty();
			var ul = $("<ul></ul>").addClass("pagination");
			
			var firstPageLi = $("<li></li>").append($("<a></a>").append("FirstPage").attr("href","#"));
			var prePageLi = $("<li></li>").append($("<a></a>").append("&laquo;"));
			if(result.extend.pageInfo.hasPreviousPage == false){
				prePageLi.addClass("disabled");
			}else{
				//æ·»åŠ å•å‡»äº‹ä»¶
				prePageLi.click(function(){
					to_page(result.extend.pageInfo.pageNum -1);
				});
				firstPageLi.click(function(){
					to_page(1);
				});
			}
			var nextPageLi = $("<li></li>").append($("<a></a>").append("&raquo;"));
			var lastPageLi = $("<li></li>").append($("<a></a>").append("LastPage").attr("href","#"));
			if(result.extend.pageInfo.hasNextPage == false){
				nextPageLi.addClass("disabled");
			}else{
				//æ·»åŠ å•å‡»äº‹ä»¶
				nextPageLi.click(function(){
					to_page(result.extend.pageInfo.pageNum +1);
				});
				lastPageLi.click(function(){
					to_page(result.extend.pageInfo.pages);
				});
			}
			//æ·»åŠ é¦–é¡µå’Œå‰ä¸€é¡µ
			ul.append(firstPageLi).append(prePageLi);
			$.each(result.extend.pageInfo.navigatepageNums,function(index,item){
				var numLi = $("<li></li>").append($("<a></a>").append(item));
				//æ·»åŠ æ¯ä¸€ä¸ªéåŽ†å‡ºæ¥çš„é¡µç 
				if(item == result.extend.pageInfo.pageNum){
					numLi.addClass("active");
				}
				numLi.click(function(){
					to_page(item);
				});
				ul.append(numLi);
			});
			//æ·»åŠ æœ€åŽä¸€é¡µå’Œæœ«é¡µ
			ul.append(nextPageLi).append(lastPageLi);
			//æŠŠ ul æ·»åŠ åˆ° navElv ä¸­åŽ»
			var navElv = $("<nav></nav>").append(ul);
			
			navElv.appendTo("#page_nav_area");
		}
		//èŽ·å–è®¾å¤‡ä¿¡æ¯
		function getDevice() {
			$.ajax({
				url:"${APP_PATH}/device",
				type:"GET",
					cache:false,
					success:function(result){
						var devices = (result && result.extend && result.extend.device) ? result.extend.device : [];
						var latestModeMap = {};
						var selectedValue = $.trim($("#deviceSelect").val() || "");
						var selectEl = $("#deviceSelect");
						selectEl.empty();
						$.each(devices, function(){
							var serialNum = $.trim((this && this.serialNum) ? this.serialNum : "");
							if(serialNum === ""){
								return;
							}
							latestModeMap[serialNum] = normalizeDeviceInOutMode(this ? this.inOutMode : "");
	                        $("<option></option>").append(serialNum).attr("value", serialNum).appendTo(selectEl);
	                    });
						deviceInOutModeBySn = latestModeMap;
						if(selectedValue !== ""){
							selectEl.find("option").each(function(){
								if($(this).val() === selectedValue){
								selectEl.val(selectedValue);
								return false;
							}
						});
					}
						if(!selectEl.val() && selectEl.find("option").length > 0){
							selectEl.prop("selectedIndex", 0);
						}
						renderDeviceStatusPanel(devices);
						refreshDeviceInOutModeUi();

					},
					error:function(){
						deviceInOutModeBySn = {};
						renderDeviceStatusPanel([]);
						refreshDeviceInOutModeUi();
					}
				
			});
		}
		
		
		
		function getEnrollId() {
			$.ajax({
				url:"${APP_PATH}/enrollInfo",
				type:"GET",
				success:function(result){
					$.each(result.extend.enrollInfo, function(){
                        var optionEle = $("<option></option>").append(this.id).attr("value",this.id);
                        optionEle.appendTo("#enrollIdSelect");
                    })

				}
				
			});
		}
		//ç‚¹å‡»æ–°å¢žæŒ‰é’®å¼¹å‡ºæ¨¡æ€æ¡†
		$("#openDoor_modal_btn").click(function(){
			
			$("#opneDoorModal").modal({
				backdrop:"static"
			});
		});
		
		
		//ç‚¹å‡»æ–°å¢žæŒ‰é’®å¼¹å‡ºæ¨¡æ€æ¡†
		$("#getUserLock_modal_btn").click(function(){
			
			$("#getUserLockModal").modal({
				backdrop:"static"
			});
		});
		
		
		//ç‚¹å‡»æ–°å¢žæŒ‰é’®å¼¹å‡ºæ¨¡æ€æ¡†
		$("#addUser_modal_btn").click(function(){
			$("#formMode").val("add");
			$("#addUserModalTitle").text("Add Person");
			$("#userIdInput").prop("readonly", false).val("");
			$("#personNameInput").val("");
			$("#privilegeSelect").val("0");
			$("#syncTargetSelect").val("selected");
			$("#passwordInput").val("");
			$("#cardNumInput").val("");
			$("#photoInput").val("");
			$("#addUserModal").modal({
				backdrop:"static"
			});
		});

		$("#form").on("submit", function(e){
			e.preventDefault();
			var passwordValue = $("#passwordInput").val() || "";
			if(passwordValue.length > 10){
				$("#passwordInput").val(passwordValue.substring(0, 10));
			}
			var cardValue = $("#cardNumInput").val() || "";
			if(cardValue.length > 20){
				$("#cardNumInput").val(cardValue.substring(0, 20));
			}
			var syncTarget = $("#syncTargetSelect").val() || "selected";
			var formData = new FormData(this);
			if(syncTarget === "selected"){
				var deviceSn = document.getElementById('deviceSelect').value;
				if(deviceSn){
					formData.append("deviceSn", deviceSn);
				}
			}
			$.ajax({
				url:"${APP_PATH}/savePerson",
				type:"POST",
				data:formData,
				processData:false,
				contentType:false,
				success:function(result){
					var msg = (result && result.msg) ? result.msg : "Save completed.";
					if(result && result.code == 100){
						var syncQueued = (result.extend && result.extend.syncQueued) ? true : false;
						var hasFaceData = (result.extend && result.extend.hasFaceData) ? true : false;
						var hasFaceTemplate = (result.extend && result.extend.hasFaceTemplate) ? true : false;
						var syncMsg = "";
						if(syncQueued){
							syncMsg = (syncTarget === "all")
								? "\nUser sync queued to all devices."
								: "\nUser sync queued to selected device.";
						}else{
							syncMsg = (syncTarget === "all")
								? "\nSaved in DB only (no devices found)."
								: "\nSaved in DB only (device not selected).";
						}
						var faceMsg = "";
						if(hasFaceTemplate){
							faceMsg = "\nFace template (20-27) exists.";
						}else if(hasFaceData){
							faceMsg = "\nFace photo (50) exists/synced.";
						}else{
							faceMsg = "\nNo face data found in DB for this user.";
						}
						alert(msg + syncMsg + faceMsg);
						$("#addUserModal").modal("hide");
						to_page(currentPage || 1);
					}else{
						var detail = (result && result.extend && result.extend.error) ? result.extend.error : "";
						alert(msg + (detail ? ("\n" + detail) : ""));
					}
				},
				error:function(xhr, textStatus, errorThrown){
					var detail = "";
					if(xhr && xhr.responseText){
						try{
							var errJson = JSON.parse(xhr.responseText);
							if(errJson && errJson.extend && errJson.extend.error){
								detail = errJson.extend.error;
							}else if(errJson && errJson.msg){
								detail = errJson.msg;
							}else{
								detail = xhr.responseText;
							}
						}catch(e1){
							detail = xhr.responseText;
						}
					}
					if(!detail && errorThrown){
						detail = errorThrown;
					}
					if(detail && detail.length > 500){
						detail = detail.substring(0, 500) + "...";
					}
					alert("Save failed." + (detail ? ("\n" + detail) : ""));
				}
			});
		});

		$(document).on("click",".edit_btu",function(){
			var enrollId = $(this).attr("edit-id");
			$.ajax({
				url:"${APP_PATH}/personDetail?enrollId="+encodeURIComponent(enrollId),
				type:"GET",
				success:function(result){
					if(result.code != 100 || !result.extend || !result.extend.person){
						alert(result.msg || "User detail not found.");
						return;
					}
					var p = result.extend.person;
					$("#formMode").val("edit");
					$("#addUserModalTitle").text("Edit Person");
					$("#userIdInput").val(p.userId).prop("readonly", true);
					$("#personNameInput").val(p.name || "");
					$("#privilegeSelect").val(String(p.privilege == null ? 0 : p.privilege));
					$("#syncTargetSelect").val("selected");
					$("#passwordInput").val((p.password || "").substring(0, 10));
					$("#cardNumInput").val((p.cardNum || "").substring(0, 20));
					$("#photoInput").val("");
					$("#addUserModal").modal({
						backdrop:"static"
					});
				}
			});
		});
		
		
		//ç‚¹å‡»æ–°å¢žæŒ‰é’®å¼¹å‡ºæ¨¡æ€æ¡†
				
	
		
				
		//åˆå§‹åŒ–æ¨¡æ€æ¡†ï¼Œæ¯æ¬¡åŠ è½½æ¸…ç©ºé‡Œé¢çš„æ•°æ®
		function initEmpAddModal(ele){
			$(ele).val("");
			$(ele).parent().removeClass("has-success has-error");
			$(ele).next("span").text("");
		}
		
		
				
		//å¼¹å‡ºæŽˆæƒæ¨¡æ€æ¡†
				
		//å¼¹å‡ºä¸‹è½½ç”¨æˆ·æ¨¡æ€æ¡†
		$("#download_emp_modal_btn").click(function(){
			//åˆå§‹åŒ–æ¨¡æ€æ¡†
			//ä¹Ÿå¯ä»¥è¿™ä¹ˆåš
			
			//å¼¹å‡ºæ¨¡æ€æ¡†
			getEnrollId();
			$("#downLoadOneUserModal").modal({
				backdrop:"static"
			});
		});
		
		
	      $("#openDoor_btu").click(function(){
				
				//1.å¼¹å‡ºæ˜¯å¦ç¡®è®¤åˆ é™¤å¯¹è¯æ¡†
				//èŽ·å–empNameçš„æ–¹æ³•ï¼ŒèŽ·å–åˆ°ä»–çš„æ‰€æœ‰çš„çˆ¶å…ƒç´ ï¼Œæ‰¾åˆ°tr,ç„¶åŽå†åœ¨trä¸­æ‰¾åˆ°ç¬¬ä¸€ä¸ªtd,èŽ·å–åˆ°ç¬¬ä¸€ä¸ªtdçš„å€¼
			//	var empId = $(this).parents("tr").find("td:eq(2)").text();
				//alert(empName);
				var doorNum=document.getElementById('doorNum').value;
				var deviceSn=document.getElementById('deviceSelect').value;
				//alert(deviceSn);
				if(confirm("do you want to open the doorï¼Ÿ")){
					//ç¡®è®¤ï¼Œå‘é€ajaxè¯·æ±‚ï¼Œåˆ é™¤
					$.ajax({
						url:"${APP_PATH}/openDoor?doorNum="+doorNum+"&&deviceSn="+deviceSn+"&_t="+new Date().getTime(),
						type:"GET",
						cache:false,
						success:function(result){
							alert(result.msg);
							//å›žåˆ°å½“å‰é¡µ
							$("#opneDoorModal").modal('hide');
							to_page(currentPage);
						}
					});
				}
			});
	      
	      $("#getUserLock_btu").click(function(){
				
				//1.å¼¹å‡ºæ˜¯å¦ç¡®è®¤åˆ é™¤å¯¹è¯æ¡†
				//èŽ·å–empNameçš„æ–¹æ³•ï¼ŒèŽ·å–åˆ°ä»–çš„æ‰€æœ‰çš„çˆ¶å…ƒç´ ï¼Œæ‰¾åˆ°tr,ç„¶åŽå†åœ¨trä¸­æ‰¾åˆ°ç¬¬ä¸€ä¸ªtd,èŽ·å–åˆ°ç¬¬ä¸€ä¸ªtdçš„å€¼
			//	var empId = $(this).parents("tr").find("td:eq(2)").text();
				//alert(empName);
				var enrollId=document.getElementById('lockEnrollId').value;
				var deviceSn=document.getElementById('deviceSelect').value;
				//alert(deviceSn);
				if(confirm("do you want to collect the user lock infoï¼Ÿ")){
					//ç¡®è®¤ï¼Œå‘é€ajaxè¯·æ±‚ï¼Œåˆ é™¤
					$.ajax({
						url:"${APP_PATH}/geUSerLock?enrollId="+enrollId+"&&deviceSn="+deviceSn,
						type:"GET",
						success:function(result){
							alert(result.msg);
							//å›žåˆ°å½“å‰é¡µ
							$("#opneDoorModal").modal('hide');
							to_page(currentPage);
						}
					});
				}
			});
	      $("#getDevLock_modal_btn").click(function(){
				
				//1.å¼¹å‡ºæ˜¯å¦ç¡®è®¤åˆ é™¤å¯¹è¯æ¡†
				//èŽ·å–empNameçš„æ–¹æ³•ï¼ŒèŽ·å–åˆ°ä»–çš„æ‰€æœ‰çš„çˆ¶å…ƒç´ ï¼Œæ‰¾åˆ°tr,ç„¶åŽå†åœ¨trä¸­æ‰¾åˆ°ç¬¬ä¸€ä¸ªtd,èŽ·å–åˆ°ç¬¬ä¸€ä¸ªtdçš„å€¼
			//	var empId = $(this).parents("tr").find("td:eq(2)").text();
				//alert(empName);
				//var doorNum=document.getElementById('doorNum').value;
				var deviceSn=document.getElementById('deviceSelect').value;
				//alert(deviceSn);
				if(confirm("do you want to get device lock infoï¼Ÿ")){
					//ç¡®è®¤ï¼Œå‘é€ajaxè¯·æ±‚ï¼Œåˆ é™¤
					$.ajax({
						url:"${APP_PATH}/getDevLock?deviceSn="+deviceSn,
						type:"GET",
						success:function(result){
							alert(result.msg);
							//å›žåˆ°å½“å‰é¡µ
						//	$("#opneDoorModal").modal('hide');
							to_page(currentPage);
						}
					});
				}
			});
	      
	      $("#cleanAdmin_modal_btn").click(function(){
				
				//1.å¼¹å‡ºæ˜¯å¦ç¡®è®¤åˆ é™¤å¯¹è¯æ¡†
				//èŽ·å–empNameçš„æ–¹æ³•ï¼ŒèŽ·å–åˆ°ä»–çš„æ‰€æœ‰çš„çˆ¶å…ƒç´ ï¼Œæ‰¾åˆ°tr,ç„¶åŽå†åœ¨trä¸­æ‰¾åˆ°ç¬¬ä¸€ä¸ªtd,èŽ·å–åˆ°ç¬¬ä¸€ä¸ªtdçš„å€¼
			//	var empId = $(this).parents("tr").find("td:eq(2)").text();
				//alert(empName);
				//var doorNum=document.getElementById('doorNum').value;
				var deviceSn=document.getElementById('deviceSelect').value;
				//alert(deviceSn);
				if(confirm("do you want to clean adminï¼Ÿ")){
					//ç¡®è®¤ï¼Œå‘é€ajaxè¯·æ±‚ï¼Œåˆ é™¤
					$.ajax({
						url:"${APP_PATH}/cleanAdmin?deviceSn="+deviceSn,
						type:"GET",
						success:function(result){
							alert(result.msg);
							//å›žåˆ°å½“å‰é¡µ
						//	$("#opneDoorModal").modal('hide');
							to_page(currentPage);
						}
					});
				}
			});
		
	      $("#synchronize_Time").click(function(){
				
				var deviceSn=document.getElementById('deviceSelect').value;
				//ç¡®è®¤ï¼Œå‘é€ajaxè¯·æ±‚ï¼Œåˆ é™¤
				$.ajax({
					url:"${APP_PATH}/synchronizeTime?deviceSn="+deviceSn,
					type:"GET",
					success:function(result){
						alert(result.msg);
						//å›žåˆ°å½“å‰é¡µ
					//	$("#opneDoorModal").modal('hide');
						to_page(currentPage);
					}
				});
			});

	      $("#syncDbAllDevicesBtn").click(function(){
			triggerDbAllDevicesSync(true);
	      });

	      $("#syncStatusAllDevicesBtn").click(function(){
			$("#syncStatusAllDevicesStatus").text("Syncing status from DB to all devices...");
			$.ajax({
				url:"${APP_PATH}/syncUsersStatusAllDevices",
				type:"GET",
				cache:false,
				success:function(result){
					var ext = (result && result.extend) ? result.extend : {};
					var devices = toInt(ext.devices, 0);
					var users = toInt(ext.users, 0);
					var activeUsers = toInt(ext.activeUsers, 0);
					var inactiveUsers = toInt(ext.inactiveUsers, 0);
					var queued = toInt(ext.commandsQueued, 0);
					var text = "Status sync done | devices=" + devices
						+ ", users=" + users
						+ " (active=" + activeUsers + ", deactive=" + inactiveUsers + ")"
						+ ", commandsQueued=" + queued;
					$("#syncStatusAllDevicesStatus").text(text);
					alert(result.msg + "\n" + text);
				},
				error:function(){
					$("#syncStatusAllDevicesStatus").text("Status sync failed");
				}
			});
	      });

	      $("#updateDataPageBtn").click(function(){
			window.open("${APP_PATH}/updateDataPage", "_blank");
	      });

	      $("#enableMasterDeviceSyncBtn").click(function(){
			configureMasterDeviceSyncFromSelection(true);
	      });

	      $("#disableMasterDeviceSyncBtn").click(function(){
			configureMasterDeviceSyncFromSelection(false);
	      });

	      $("#syncMasterDeviceFullBtn").click(function(){
			triggerMasterFullSyncFromSelection();
	      });

	      function sendUserEnableCommand(enrollId, enFlag){
			var deviceSn = $.trim($("#deviceSelect").val());
			$.ajax({
				url:"${APP_PATH}/setUserEnable?enrollId="+encodeURIComponent(enrollId)+"&enFlag="+encodeURIComponent(enFlag)+"&deviceSn="+encodeURIComponent(deviceSn),
				type:"GET",
				cache:false,
				success:function(result){
					alert(result.msg);
					to_page(currentPage);
				}
			});
	      }

	      $("#setUserEnable_btn").click(function(){
			var enrollId = $.trim($("#enableUserIdInput").val());
			var enFlag = $.trim($("#enableFlagSelect").val());
			if(enrollId === ""){
				alert("please input user id");
				return;
			}
			if(!/^[0-9]+$/.test(enrollId)){
				alert("please input valid user id");
				return;
			}
			if(enFlag !== "0" && enFlag !== "1"){
				alert("please select valid enFlag");
				return;
			}
			sendUserEnableCommand(enrollId, enFlag);
	      });

	      $(document).on("click",".disable_user_btu",function(){
			var enrollId = $.trim($(this).attr("status-id"));
			if(!/^[0-9]+$/.test(enrollId)){
				alert("invalid user id");
				return;
			}
			sendUserEnableCommand(enrollId, "0");
	      });

	      $(document).on("click",".enable_user_btu",function(){
			var enrollId = $.trim($(this).attr("status-id"));
			if(!/^[0-9]+$/.test(enrollId)){
				alert("invalid user id");
				return;
			}
			sendUserEnableCommand(enrollId, "1");
	      });

		//æ‰‹åŠ¨é‡‡é›†ç”¨æˆ·ajaxè¯·æ±‚
		$("#collectList_emp_modal_btn").click(function(){
			
			//1.å¼¹å‡ºæ˜¯å¦ç¡®è®¤åˆ é™¤å¯¹è¯æ¡†
			//èŽ·å–empNameçš„æ–¹æ³•ï¼ŒèŽ·å–åˆ°ä»–çš„æ‰€æœ‰çš„çˆ¶å…ƒç´ ï¼Œæ‰¾åˆ°tr,ç„¶åŽå†åœ¨trä¸­æ‰¾åˆ°ç¬¬ä¸€ä¸ªtd,èŽ·å–åˆ°ç¬¬ä¸€ä¸ªtdçš„å€¼
		//	var empId = $(this).parents("tr").find("td:eq(2)").text();
			//alert(empName);
			var deviceSn=document.getElementById('deviceSelect').value;
			//alert(deviceSn);
			if(confirm("do you want to collect user listï¼Ÿ")){
				//ç¡®è®¤ï¼Œå‘é€ajaxè¯·æ±‚ï¼Œåˆ é™¤
				$.ajax({
					url:"${APP_PATH}/sendWs?deviceSn="+deviceSn,
					type:"GET",
					success:function(result){
						var ext = (result && result.extend) ? result.extend : {};
						var exportDir = $.trim(ext.exportDir || "");
						var bundleFile = $.trim(ext.bundleFile || "");
						var sourceDeviceSn = $.trim(ext.sourceDeviceSn || "");
						var note = $.trim(ext.note || "");
						var alertText = result.msg;
						if(sourceDeviceSn !== ""){
							alertText += "\nSource Device: " + sourceDeviceSn;
						}
						if(exportDir !== ""){
							alertText += "\nExport: " + exportDir;
						}
						if(bundleFile !== ""){
							alertText += "\nBundle: " + bundleFile;
						}
						if(note !== ""){
							alertText += "\n" + note;
						}
						alert(alertText);
						//å›žåˆ°å½“å‰é¡µ
						to_page(currentPage);
					}
				});
			}
		});
		
      $("#collectInfo_emp_modal_btn").click(function(){
			
			//1.å¼¹å‡ºæ˜¯å¦ç¡®è®¤åˆ é™¤å¯¹è¯æ¡†
			//èŽ·å–empNameçš„æ–¹æ³•ï¼ŒèŽ·å–åˆ°ä»–çš„æ‰€æœ‰çš„çˆ¶å…ƒç´ ï¼Œæ‰¾åˆ°tr,ç„¶åŽå†åœ¨trä¸­æ‰¾åˆ°ç¬¬ä¸€ä¸ªtd,èŽ·å–åˆ°ç¬¬ä¸€ä¸ªtdçš„å€¼
		//	var empId = $(this).parents("tr").find("td:eq(2)").text();
			//alert(empName);
			var deviceSn=document.getElementById('deviceSelect').value;
			//alert(deviceSn);
			if(confirm("do you want to collect user detailï¼Ÿ")){
				//ç¡®è®¤ï¼Œå‘é€ajaxè¯·æ±‚ï¼Œåˆ é™¤
				$.ajax({
					url:"${APP_PATH}/getUserInfo?deviceSn="+deviceSn,
					type:"GET",
					success:function(result){
						var ext = (result && result.extend) ? result.extend : {};
						var exportDir = $.trim(ext.exportDir || "");
						var bundleFile = $.trim(ext.bundleFile || "");
						var note = $.trim(ext.note || "");
						var alertText = result.msg;
						if(exportDir !== ""){
							alertText += "\nExport: " + exportDir;
						}
						if(bundleFile !== ""){
							alertText += "\nBundle: " + bundleFile;
						}
						if(note !== ""){
							alertText += "\n" + note;
						}
						alert(alertText);
						//å›žåˆ°å½“å‰é¡µ
						to_page(currentPage);
					}
				});
			}
		});
			$("#setUserToDevice_emp_modal_btn").click(function(){
				var deviceSn = $.trim(document.getElementById('deviceSelect').value || "");
				if(deviceSn === ""){
					alert("please select device");
					return;
				}
				if(confirm("do you want to send user info to deviceï¼Ÿ")){
					var $btn = $("#setUserToDevice_emp_modal_btn");
					var originalText = $btn.text();
					$btn.prop("disabled", true).text("SetUserToDevice...");
					$.ajax({
						url:"${APP_PATH}/setPersonToDevice?deviceSn="+encodeURIComponent(deviceSn),
						type:"GET",
						cache:false,
						success:function(result){
							var ext = (result && result.extend) ? result.extend : {};
							var alertText = (result && result.msg) ? result.msg : "Success";
							var syncMode = $.trim(ext.syncMode || "");
							if(ext.deviceSn){
								alertText += "\nDevice: " + ext.deviceSn;
							}
							if(typeof ext.activeUsers !== "undefined"){
								alertText += "\nActive Users: " + ext.activeUsers;
							}
							if(typeof ext.totalRecords !== "undefined"){
								alertText += "\nTotal DB Records: " + ext.totalRecords;
							}
							if(typeof ext.queuedUsers !== "undefined"){
								alertText += "\nQueued Users: " + ext.queuedUsers;
							}else if(typeof ext.deltaUsers !== "undefined"){
								alertText += "\nQueued Users: " + ext.deltaUsers;
							}
							if(typeof ext.queuedCount !== "undefined"){
								alertText += "\nQueued Records: " + ext.queuedCount;
							}else if(typeof ext.deltaRecords !== "undefined"){
								alertText += "\nQueued Records: " + ext.deltaRecords;
							}
							if(typeof ext.queuedRecords !== "undefined"){
								alertText += "\nCommands Queued: " + ext.queuedRecords;
							}
							if(typeof ext.imageRecords !== "undefined"){
								alertText += "\nImage Records(backup 50): " + ext.imageRecords;
							}
							if(typeof ext.faceRecords !== "undefined"){
								alertText += "\nFace Records(20-27): " + ext.faceRecords;
							}
							if(typeof ext.passwordRecords !== "undefined"){
								alertText += "\nPassword Records: " + ext.passwordRecords;
							}
							if(typeof ext.cardRecords !== "undefined"){
								alertText += "\nCard Records: " + ext.cardRecords;
							}
							if(typeof ext.pendingBefore !== "undefined"){
								alertText += "\nPending Before: " + ext.pendingBefore;
							}
							if(typeof ext.clearedPending !== "undefined"){
								alertText += "\nCleared Pending: " + ext.clearedPending;
							}
							if(typeof ext.pendingAfter !== "undefined"){
								alertText += "\nPending After: " + ext.pendingAfter;
							}
							if(ext.syncStatus){
								alertText += "\nSync Status: " + ext.syncStatus;
							}
							if(ext.syncMessage){
								alertText += "\nSync Message: " + ext.syncMessage;
							}
							if(typeof ext.estimatedDispatchSeconds !== "undefined"){
								alertText += "\nEstimated Dispatch: " + ext.estimatedDispatchSeconds + " sec";
							}
							if(typeof ext.estimatedDispatchMinutes !== "undefined"){
								alertText += " (" + ext.estimatedDispatchMinutes + " min)";
							}
							if(typeof ext.dispatchRatePerMinute !== "undefined"){
								alertText += "\nCurrent Dispatch Rate: " + ext.dispatchRatePerMinute + " records/min";
							}
							if(syncMode !== ""){
								alertText += "\nSync Mode: " + syncMode;
							}
							if(ext.note){
								alertText += "\n" + ext.note;
							}
							alert(alertText);
							//å›žåˆ°å½“å‰é¡µ
							to_page(currentPage);
						},
						error:function(xhr, textStatus, errorThrown){
							var detail = "";
							if(xhr && xhr.responseJSON && xhr.responseJSON.extend && xhr.responseJSON.extend.error){
								detail = xhr.responseJSON.extend.error;
							}else if(xhr && xhr.responseText){
								try{
									var parsed = JSON.parse(xhr.responseText);
									if(parsed && parsed.extend && parsed.extend.error){
										detail = parsed.extend.error;
									}else if(parsed && parsed.msg){
										detail = parsed.msg;
									}
								}catch(e){
									detail = xhr.responseText;
								}
							}
							if($.trim(detail) === ""){
								detail = errorThrown || textStatus || "Request failed";
							}
							alert("SetUserToDevice failed.\n" + detail);
						},
						complete:function(){
							$btn.prop("disabled", false).text(originalText);
						}
					});
				}
		});
		
		
		$("#setUserName_modal_btn").click(function(){
			var deviceSn=document.getElementById('deviceSelect').value;
			if(confirm("do you want to send user name to deviceï¼Ÿ")){
				//ç¡®è®¤ï¼Œå‘é€ajaxè¯·æ±‚ï¼Œåˆ é™¤
				$.ajax({
					url:"${APP_PATH}/setUsernameToDevice?deviceSn="+deviceSn,
					type:"GET",
					success:function(result){
						alert(result.msg);
						//å›žåˆ°å½“å‰é¡µ
						to_page(currentPage);
					}
				});
			}
		});
		
		
    $("#initSys_emp_modal_btn").click(function(){
			
    	var deviceSn=document.getElementById('deviceSelect').value;
			if(confirm("do you want to init deviceï¼Ÿit will clean all info")){
				//ç¡®è®¤ï¼Œå‘é€ajaxè¯·æ±‚ï¼Œåˆ é™¤
				$.ajax({
					url:"${APP_PATH}/initSystem?deviceSn="+deviceSn,
					type:"GET",
					success:function(result){
						alert(result.msg);
						//å›žåˆ°å½“å‰é¡µ
						to_page(currentPage);
					}
				});
			}
		});
		
    
    $("#logInfo_emp_modal_btn").click(function(){
		window.location.href="${APP_PATH}/logRecords.jsp";
	});

    $("#climsInfo_emp_modal_btn").click(function(){
		var deviceSn=document.getElementById('deviceSelect').value;
		window.location.href="${APP_PATH}/climsRecordsPage?deviceSn="+deviceSn;
	});

    $("#schedulerStatus_emp_modal_btn").click(function(){
		window.location.href="${APP_PATH}/schedulerStatusPage";
	});
    
     //èŽ·å–è®¾å¤‡ä¿¡æ¯
	$("#getDeviceInfo_modal_btn").click(function(){
		
		//1.å¼¹å‡ºæ˜¯å¦ç¡®è®¤åˆ é™¤å¯¹è¯æ¡†
		//èŽ·å–empNameçš„æ–¹æ³•ï¼ŒèŽ·å–åˆ°ä»–çš„æ‰€æœ‰çš„çˆ¶å…ƒç´ ï¼Œæ‰¾åˆ°tr,ç„¶åŽå†åœ¨trä¸­æ‰¾åˆ°ç¬¬ä¸€ä¸ªtd,èŽ·å–åˆ°ç¬¬ä¸€ä¸ªtdçš„å€¼
	//	var empId = $(this).parents("tr").find("td:eq(2)").text();
		//alert(empName);
		var deviceSn=document.getElementById('deviceSelect').value;
		//alert(deviceSn);
		if(confirm("do you want to get device infoï¼Ÿ")){
			//ç¡®è®¤ï¼Œå‘é€ajaxè¯·æ±‚ï¼Œåˆ é™¤
			$.ajax({
				url:"${APP_PATH}/getDeviceInfo?deviceSn="+deviceSn,
				type:"GET",
				success:function(result){
					alert(result.msg);
					//å›žåˆ°å½“å‰é¡µ
					to_page(currentPage);
				}
			});
		}
	});
    
		//æŸ¥è¯¢æ‰€æœ‰çš„å¤©æ—¶æ®µä¿¡æ¯å¹¶æ˜¾ç¤ºåœ¨ä¸‹æ‹‰åˆ—è¡¨ä¸­
		
		
		
		//é‚®ç®±è¡¨å•æ ¡éªŒ
		function validate_add_form_empEmail(){
			//1. æ‹¿åˆ°è¦éªŒè¯çš„æ•°æ®ï¼Œä½¿ç”¨æ­£åˆ™è¡¨è¾¾å¼
			
			var email = $("#empEmail_add_input").val();
			var regEmail = /^([a-z0-9_\.-]+)@([\da-z\.-]+)\.([a-z\.]{2,6})$/;
			if(!regEmail.test(email)){
				//alert("é‚®ç®±ä¸åˆæ³•.......");
				show_validate_msg("#empEmail_add_input","error","é‚®ç®±ä¸åˆæ³•");
				return false;
			}else{
				show_validate_msg("#empEmail_add_input","success","å¯ä»¥ä½¿ç”¨");
			}
			return true;
		}
		//æ˜¾ç¤ºæ ¡éªŒçš„æç¤ºä¿¡æ¯
		function show_validate_msg(ele,status,msg){
			//æ¯æ¬¡å¼¹å‡ºæ¨¡æ€æ¡†ä¹‹å‰ï¼Œæ¸…ç©ºé‡Œé¢çš„å†…å®¹
			$(ele).parent().removeClass("has-success has-error");
			$(ele).next("span").text("");
			if(status == "success"){
				$(ele).parent().addClass("has-success");
				$(ele).next("span").text(msg);	
			}else if(status == "error"){
				$(ele).parent().addClass("has-error");
				$(ele).next("span").text(msg);	
			}
		}
		
		//æ£€éªŒç”¨æˆ·åæ˜¯å¦åˆæ³•
		 $("#empName_add_input").change(function(){
			var empName = this.value;
			//è¡¨å•ç”¨æˆ·åå‰å°æ ¡éªŒ
			if(validate_add_form_empName()){
			
				//å‘é€ajaxè¯·æ±‚ï¼Œæ ¡éªŒç”¨æˆ·åæ˜¯å¦å¯ç”¨
				$.ajax({
					url:"${APP_PATH}/checkuser",
					type:"POST",
					data:"empName="+empName,
					success:function(result){
						if(result.code == 100){
							show_validate_msg("#empName_add_input","success","ç”¨æˆ·åå¯ç”¨");
							$("#emp_save_btu").attr("ajax_validate","success");
						}else{
							show_validate_msg("#empName_add_input","error",result.extend.va_msg);
							$("#emp_save_btu").attr("ajax_validate","error");
						}
					}
				});
			 }else{
				return false;
			} 
		}); 
		
		//æ£€éªŒé‚®ç®±æ˜¯å¦åˆæ³•
		 $("#empEmail_add_input").change(function(){
			if(!validate_add_form_empEmail()){
				$("#emp_save_btu").attr("ajax_validate2","error");
				return false; 
			};
			$("#emp_save_btu").attr("ajax_validate2","success");
		}); 
		
		//ç‚¹å‡»ä¿å­˜å‘¨æ—¶æ®µäº‹ä»¶
				
	   //ä¿å­˜å¤©æ—¶æ®µ
				
		 //ä¿å­˜é”ç»„åˆ
				 
		 //ä¿å­˜æŽˆæƒæ•°æ®
				 
		 
		 //ä¿å­˜å•ä¸ªå‘˜å·¥æ•°æ®
		$("#uploadOneUser_btu").click(function(){
			//1.æ¨¡æ€æ¡†ä¸­çš„è¡¨å•æ•°æ®æäº¤ç»™æ•°æ®åº“è¿›è¡Œä¿å­˜
			//2.å‘é€ajaxè¯·æ±‚ä¿å­˜å‘˜å·¥æ•°æ®
			//alert($("#empAddModal form").serialize());
			
			//1.åˆ¤æ–­ä¹‹å‰çš„ç”¨æˆ·åæ ¡éªŒæ˜¯å¦æˆåŠŸï¼Œå¦åˆ™å°±ä¸å¾€ä¸‹èµ°
			var empId=document.getElementById('enrollId1').value;
			var backupNum=document.getElementById('backupNumSelect').value;
			var deviceSn=document.getElementById('deviceSelect').value;
			$.ajax({
				url:"${APP_PATH}/setOneUser?enrollId="+empId+"&backupNum="+backupNum+"&deviceSn="+deviceSn,
				type:"GET",
				success:function(result){
					 	 
					if(result.code == 100){ 
						//alert(result.msg);
						//å‘˜å·¥ä¿å­˜æˆåŠŸ
						//1.å…³é—­æ¨¡æ€æ¡†
						$("#accessWeekAddModal").modal('hide');
						//2.è·³è½¬åˆ°æœ€åŽä¸€é¡µ
						alert(result.msg);
						//å›žåˆ°å½“å‰é¡µ
						to_page(currentPage);
					 }else{
						 alert("backup num is not existï¼");
					} 
					 
				}
			});
		});
		 
		 //ä¸‹è½½å•ä¸ªå‘˜å·¥æ•°æ®
		$("#downloadOneUser_btu").click(function(){
			//1.æ¨¡æ€æ¡†ä¸­çš„è¡¨å•æ•°æ®æäº¤ç»™æ•°æ®åº“è¿›è¡Œä¿å­˜
			//2.å‘é€ajaxè¯·æ±‚ä¿å­˜å‘˜å·¥æ•°æ®
			//alert($("#empAddModal form").serialize());
			
			//1.åˆ¤æ–­ä¹‹å‰çš„ç”¨æˆ·åæ ¡éªŒæ˜¯å¦æˆåŠŸï¼Œå¦åˆ™å°±ä¸å¾€ä¸‹èµ°
			var empId=document.getElementById('enrollIdSelect').value;
			var backupNum=document.getElementById('backupNumSelect1').value;
			var deviceSn=document.getElementById('deviceSelect').value;
			$.ajax({
				url:"${APP_PATH}/sendGetUserInfo?enrollId="+empId+"&backupNum="+backupNum+"&deviceSn="+deviceSn,
				type:"GET",
				success:function(result){
					 	 
					if(result.code == 100){ 
						//alert(result.msg);
						//å‘˜å·¥ä¿å­˜æˆåŠŸ
						//1.å…³é—­æ¨¡æ€æ¡†
						$("#accessWeekAddModal").modal('hide');
						//2.è·³è½¬åˆ°æœ€åŽä¸€é¡µ
						alert(result.msg);
						//å›žåˆ°å½“å‰é¡µ
						to_page(currentPage);
					 }else{
						 alert("backup num is not existï¼");
					} 
					 
				}
			});
		});
		 
		//èŽ·å–å‘˜å·¥ä¿¡æ¯
		function getEmp(id){
			$.ajax({
				url:"${APP_PATH}/emp/"+id,
				type:"GET",
				success:function(result){
					//console.log(result);
					var empData = result.extend.emp;
					$("#empName_update_static").text(empData.empName);
					$("#empEmail_update_input").val(empData.email);
					$("#empUpdateModal input[name=gender]").val([empData.gender]);
					$("#empUpdateModal select").val([empData.dId]);
				}
			});
			
		}
		
		//ç‚¹å‡»æ›´æ–°ï¼Œæ›´æ–°å‘˜å·¥ä¿¡æ¯
		$("#emp_update_btu").click(function(){
			//éªŒè¯é‚®ç®±æ˜¯å¦åˆæ³•
			var email = $("#empEmail_update_input").val();
			var regEmail = /^([a-z0-9_\.-]+)@([\da-z\.-]+)\.([a-z\.]{2,6})$/;
			 if(!regEmail.test(email)){
				show_validate_msg("#empEmail_update_input","error","é‚®ç®±ä¸åˆæ³•");
				return false;
			}else{
				
				//å‘é€ajaxè¯·æ±‚ï¼Œæ›´æ–°å‘˜å·¥ä¿¡æ¯
				
				$.ajax({
					url:"${APP_PATH}/emp/"+$(this).attr("edit-id"),
					type:"PUT",
					data:$("#empUpdateModal form").serialize(),
					success:function(result){
						//alert(result.msg);
							if(result.code == 100){ 
								//1.å…³é—­æ¨¡æ€æ¡†
								$("#empUpdateModal").modal('hide');
								//2.å›žåˆ°å½“å‰é¡µé¢
								to_page(currentPage);
							}else{
								//æ˜¾ç¤ºé”™è¯¯ä¿¡æ¯
								console.log(result);
								if(undefined != result.extend.errorsFields.email){
									show_validate_msg("#empEmail_update_input","error",result.extend.errorsFields.email);
								}
							}
					}
				});
			}
		});
		
		//ä¸ºåˆ é™¤æŒ‰é’®ç»‘å®šå•å‡»äº‹ä»¶
		$(document).on("click",".delete_btu",function(){
			var deviceSn=document.getElementById('deviceSelect').value;
			//1.å¼¹å‡ºæ˜¯å¦ç¡®è®¤åˆ é™¤å¯¹è¯æ¡†
			//èŽ·å–empNameçš„æ–¹æ³•ï¼ŒèŽ·å–åˆ°ä»–çš„æ‰€æœ‰çš„çˆ¶å…ƒç´ ï¼Œæ‰¾åˆ°tr,ç„¶åŽå†åœ¨trä¸­æ‰¾åˆ°ç¬¬ä¸€ä¸ªtd,èŽ·å–åˆ°ç¬¬ä¸€ä¸ªtdçš„å€¼
			var empId = $(this).parents("tr").find("td:eq(2)").text();
			//alert(empName);
			if(confirm("are you sure delete numberã€"+empId +"ã€‘ staffï¼Ÿ")){
				//ç¡®è®¤ï¼Œå‘é€ajaxè¯·æ±‚ï¼Œåˆ é™¤
				alert("å‘é€"+"${APP_PATH}/deletePersonFromDEvice");
				$.ajax({
					url:"${APP_PATH}/deletePersonFromDevice?enrollId="+$(this).attr("delete-id")+"&deviceSn="+deviceSn,
					type:"GET",
					success:function(result){
						alert(result.msg);
						//å›žåˆ°å½“å‰é¡µ
						to_page(currentPage);
					}
				});
			}
		});
		
		//ä¸ºä¸‹å‘æŒ‰é’®ç»‘å®šå•å‡»äº‹ä»¶
		$(document).on("click",".upload_btu",function(){
			//èŽ·å–è®¾å¤‡ç¼–å·
			var deviceSn=document.getElementById('deviceSelect').value;
			//1.å¼¹å‡ºæ˜¯å¦ç¡®è®¤åˆ é™¤å¯¹è¯æ¡†
			//èŽ·å–empNameçš„æ–¹æ³•ï¼ŒèŽ·å–åˆ°ä»–çš„æ‰€æœ‰çš„çˆ¶å…ƒç´ ï¼Œæ‰¾åˆ°tr,ç„¶åŽå†åœ¨trä¸­æ‰¾åˆ°ç¬¬ä¸€ä¸ªtd,èŽ·å–åˆ°ç¬¬ä¸€ä¸ªtdçš„å€¼
			var empId = $(this).parents("tr").find("td:eq(2)").text();
			
		//	initEmpAddModal("#name_accessweek_input");
			//å‘é€ajax è¯·æ±‚ï¼ŒæŸ¥å‡ºéƒ¨é—¨ä¿¡æ¯ï¼Œæ˜¾ç¤ºä¸‹æ‹‰åˆ—è¡¨
			//getDepts("#empAddModal select");
			//  getAccessDay("#accessWeekAddModal #daySelect")
			//å¼¹å‡ºæ¨¡æ€æ¡†
			$("#uploadOneUserModal").modal({
				backdrop:"static"
			});
			$("#enrollId1").val(empId)
			//alert(empName);
			/* if(confirm("ç¡®è®¤ä¸‹å‘ã€"+empId +"ã€‘å·å‘˜å·¥å—ï¼Ÿ")){
				//ç¡®è®¤ï¼Œå‘é€ajaxè¯·æ±‚ï¼Œåˆ é™¤
				$.ajax({
					url:"${APP_PATH}/setOneUser?enrollId="+$(this).attr("upload-id")+"&&deviceSn="+deviceSn,
					type:"GET",
					success:function(result){
						alert(result.msg);
						//å›žåˆ°å½“å‰é¡µ
						to_page(currentPage);
					}
				});
			} */
		});
		
		
		//å®Œæˆå…¨é€‰/å…¨ä¸é€‰åŠŸèƒ½
		$("#check_all").click(function(){
			//propä¿®æ”¹å’Œè¯»å–åŽŸç”Ÿdomå±žæ€§çš„å€¼
			//attrèŽ·å–è‡ªå®šä¹‰å±žæ€§çš„å€¼
			$(".check_item").prop("checked",$(this).prop("checked"));
		});
		
		//å•ä¸ªçš„é€‰æ‹©æ¡†å…¨é€‰ï¼Œé¡¶ä¸Šçš„ä¹Ÿé€‰æ‹©
		$(document).on("click",".check_item",function(){
			//åˆ¤æ–­å½“å‰é€‰ä¸­çš„å…ƒç´ æ˜¯å¦æ˜¯å…¨éƒ¨çš„å…ƒç´ 
			var flag = ($(".check_item:checked").length==$(".check_item").length)
				$("#check_all").prop("checked",flag);
			
		});
		
		//ä¸ºå¤šé€‰åˆ é™¤æ¡†ç»‘å®šå•å‡»äº‹ä»¶
		$("#delete_emp_all_btu").click(function(){
			var empNames="";
			var delidstr="";
			$.each($(".check_item:checked"),function(){
			  empNames += $(this).parents("tr").find("td:eq(2)").text()+",";
			  delidstr += $(this).parents("tr").find("td:eq(1)").text()+"-";
			});
			//alert(delidstr);
			//åŽ»é™¤empNameså¤šä½™çš„ï¼Œ
			empNames = empNames.substring(0, empNames.length-1);
			//åŽ»é™¤delidstrçš„å¤šä½™çš„-
			delidstr = delidstr.substring(0, delidstr.length-1);
			if(empNames == ""){
			    alert("please select the staff you want to delete")
			} else if(confirm("confirmã€"+empNames+"ã€‘staff numï¼Ÿ")){
				//å‘é€ajaxè¯·æ±‚å¹¶åˆ é™¤
				 $.ajax({
					url:"${APP_PATH}/emp/"+delidstr,
					type:"DELETE",
					success:function(result){
						alert(result.msg);
						to_page(currentPage);
					}
				 });
			}
		});
	</script>
</body>
</html>





