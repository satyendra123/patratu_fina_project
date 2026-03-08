<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>punch record</title>
<%
	pageContext.setAttribute("APP_PATH",request.getContextPath());
   
     pageContext.setAttribute("deviceSn",request.getParameter("deviceSn"));
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
<script src="${APP_PATH}/static/bootstrap-3.3.7-dist/js/bootstrap.min.js"></script>
</head>
<body>


<!-- ä¿®æ”¹å‘˜å·¥æ¨¡æ€æ¡† -->
	<div class="modal fade" id="empUpdateModal" tabindex="-1" role="dialog" aria-labelledby="myModalLabel">
	  <div class="modal-dialog" role="document">
	    <div class="modal-content">
	      <div class="modal-header">
	        <button type="button" class="close" data-dismiss="modal" aria-label="Close"><span aria-hidden="true">&times;</span></button>
	        <h4 class="modal-title">staff update</h4>
	      </div>
	      <div class="modal-body">
	        	<form class="form-horizontal">
				  <div class="form-group">
				    <label class="col-sm-2 control-label">empName</label>
				    <div class="col-sm-10">
				       <p class="form-control-static" id="empName_update_static">email@example.com</p>
				      <span class="help-block"></span>
				    </div>
				  </div>
				  <div class="form-group">
				    <label class="col-sm-2 control-label">email</label>
				    <div class="col-sm-10">
				      <input type="text" class="form-control" name="email" id="empEmail_update_input" placeholder="email@qq.com">
				      <span class="help-block"></span>
				    </div>
				  </div>
				  <div class="form-group">
				    <label class="col-sm-2 control-label">gender</label>
				    <div class="col-sm-10">
				     	<label class="radio-inline">
						 <input type="radio" name="gender" id="gender1_update_input" value="M" checked="checked"> ç”·
						</label>
						<label class="radio-inline">
						  <input type="radio" name="gender" id="gender2_update_input" value="F"> å¥³
						</label>
				    </div>
				  </div>
				  
				  <div class="form-group">
				    <label class="col-sm-2 control-label">deptName</label>
				    <div class="col-sm-4">
				     	<select class="form-control" name="dId">
						 
						</select>
				    </div>
				  </div>
				</form>
	      </div>
	      <div class="modal-footer">
	        <button type="button" class="btn btn-default" data-dismiss="modal">return</button>
	        <button type="button" class="btn btn-primary" id="emp_update_btu">update</button>
	      </div>
	    </div>
	  </div>
	</div>



	<!-- æ·»åŠ å‘˜å·¥æ¨¡æ€æ¡† -->
	<div class="modal fade" id="empAddModal" tabindex="-1" role="dialog" aria-labelledby="myModalLabel">
	  <div class="modal-dialog" role="document">
	    <div class="modal-content">
	      <div class="modal-header">
	        <button type="button" class="close" data-dismiss="modal" aria-label="Close"><span aria-hidden="true">&times;</span></button>
	        <h4 class="modal-title" id="myModalLabel">å‘˜å·¥æ·»åŠ </h4>
	      </div>
	      <div class="modal-body">
	        	<form class="form-horizontal">
				  <div class="form-group">
				    <label class="col-sm-2 control-label">empName</label>
				    <div class="col-sm-10">
				      <input type="text" class="form-control" name="empName" id="empName_add_input" placeholder="empName">
				      <span class="help-block"></span>
				    </div>
				  </div>
				  <div class="form-group">
				    <label class="col-sm-2 control-label">email</label>
				    <div class="col-sm-10">
				      <input type="text" class="form-control" name="email" id="empEmail_add_input" placeholder="email@qq.com">
				      <span class="help-block"></span>
				    </div>
				  </div>
				  <div class="form-group">
				    <label class="col-sm-2 control-label">gender</label>
				    <div class="col-sm-10">
				     	<label class="radio-inline">
						 <input type="radio" name="gender" id="gender1_add_input" value="M" checked="checked"> ç”·
						</label>
						<label class="radio-inline">
						  <input type="radio" name="gender" id="gender2_add_input" value="F"> å¥³
						</label>
				    </div>
				  </div>
				  
				  <div class="form-group">
				    <label class="col-sm-2 control-label">deptName</label>
				    <div class="col-sm-4">
				     	<select class="form-control" name="dId">
						 
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
				<h1>LogRecords</h1>
			</div>
		</div>
		<!-- æŒ‰é’® -->
		<div class="row">
			<div class="col-md-2 col-md-offset-10">
				<button class="btn btn-primary" id="CollectLog_emp_modal_btn">CollectLog</button>
				<button class="btn btn-primary" id="CollectNewLog_emp_modal_btn">CollectNewLog</button>
			</div>
		</div>
		<!-- è¡¨æ ¼æ˜¾ç¤ºå†…å®¹ -->
		<div class="row">
			<div class="col-md-12">
				<table class="table table-hover table-striped" id = "emps_table">
					<thead>
						<tr>
							
							<th>#</th>
							<th>Id</th>
							<th>PersonId</th>
							<th>RecordsTime</th>
							<th>Mode</th>
							<th>Event</th>
							<th>Device</th>
							<th>Temperature</th>
							<th>Log Image</th>
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
	</div>
	<script type="text/javascript">
		var totalRecord,currentPage;
		// 1. é¡µé¢åŠ è½½æˆåŠŸä¹‹åŽç›´æŽ¥å‘é€ ajax è¯·æ±‚å¾—åˆ° åˆ†é¡µæ•°æ®
		//é¡µé¢åŠ è½½å®Œæˆä¹‹åŽï¼Œç›´æŽ¥å‘é€ajax è¯·æ±‚ï¼ŒåŽ»é¦–é¡µ
		$(function(){
			//åŽ»é¦–é¡µ
			to_page(1);
		});
		
		function to_page(pn){
			$.ajax({
				url:"${APP_PATH}/records",
				data:"pn="+pn,
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
			var records = result.extend.pageInfo.list;
			var i = result.extend.pageInfo.startRow;
			$.each(records,function(index,item){
				//alert(item.empName);
				
				var numTd = $("<td></td>").append(i++);
				var empIdTd = $("<td></td>").append(item.enrollId);
				var recordsTimeTd = $("<td></td>").append(item.recordsTime);
				var modeTd = $("<td></td>").append(item.mode);
				var intoutTd = $("<td></td>").append(item.intout == 0 ? "In" : "Out");
				var eventTd = $("<td></td>").append(item.event);	
				var deviceTd = $("<td></td>").append(item.deviceSerialNum);
				var temperature = $("<td></td>").append(item.temperature);
				var image = $("<td></td>").append( "<img style='width:60px; height:60px;' src='"+"${APP_PATH}/img/"+item.image+"'/>");
				//appendæ–¹æ³•æ‰§è¡Œå®ŒåŽä»è¿”å›žåŽŸæ¥çš„å…ƒç´ 
				$("<tr></tr>").append(numTd)
					.append(empIdTd)
					.append(recordsTimeTd)
					.append(modeTd)
					.append(intoutTd)
					.append(eventTd)
					.append(deviceTd)
					.append(temperature)
					.append(image)
					.appendTo("#emps_table tbody");
			})
		}
		//è§£æžæ˜¾ç¤ºåˆ†é¡µä¿¡æ¯
		function buid_page_info(result){
			$("#page_info_area").empty();
			$("#page_info_area").append("Current_Page:"+result.extend.pageInfo.pageNum +
					", Count_Page :"+result.extend.pageInfo.pages +
					", All_Recordsï¼š"+result.extend.pageInfo.total);
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
		
		//ç‚¹å‡»æ–°å¢žæŒ‰é’®å¼¹å‡ºæ¨¡æ€æ¡†
		$("#add_emp_modal_btn").click(function(){

			//åˆå§‹åŒ–æ¨¡æ€æ¡†
			//ä¹Ÿå¯ä»¥è¿™ä¹ˆåš
			//$("#empAddModal")[0].reset();
			initEmpAddModal("#empName_add_input");
			initEmpAddModal("#empEmail_add_input");
			//å‘é€ajax è¯·æ±‚ï¼ŒæŸ¥å‡ºéƒ¨é—¨ä¿¡æ¯ï¼Œæ˜¾ç¤ºä¸‹æ‹‰åˆ—è¡¨
			getDepts("#empAddModal select");
			
			//å¼¹å‡ºæ¨¡æ€æ¡†
			$("#empAddModal").modal({
				backdrop:"static"
			});
		});
		
		//ä»Žè®¾å¤‡ä¸Šé‡‡é›†
      $("#CollectLog_emp_modal_btn").click(function(){
			
			//1.å¼¹å‡ºæ˜¯å¦ç¡®è®¤åˆ é™¤å¯¹è¯æ¡†
			//èŽ·å–empNameçš„æ–¹æ³•ï¼ŒèŽ·å–åˆ°ä»–çš„æ‰€æœ‰çš„çˆ¶å…ƒç´ ï¼Œæ‰¾åˆ°tr,ç„¶åŽå†åœ¨trä¸­æ‰¾åˆ°ç¬¬ä¸€ä¸ªtd,èŽ·å–åˆ°ç¬¬ä¸€ä¸ªtdçš„å€¼
		//	var empId = $(this).parents("tr").find("td:eq(2)").text();
			//alert(empName);
				var device="${deviceSn}";
				alert(device);
			if(confirm("do you want to collect punch recordï¼Ÿ")){
				//ç¡®è®¤ï¼Œå‘é€ajaxè¯·æ±‚ï¼Œåˆ é™¤
				$.ajax({
					url:"${APP_PATH}/getAllLog?deviceSn="+device,
					type:"GET",
					success:function(result){
						alert(result.msg);
						//å›žåˆ°å½“å‰é¡µ
						to_page(totalRecord);
					}
				});
			}
		});
		
      $("#CollectNewLog_emp_modal_btn").click(function(){
			
			//1.å¼¹å‡ºæ˜¯å¦ç¡®è®¤åˆ é™¤å¯¹è¯æ¡†
			//èŽ·å–empNameçš„æ–¹æ³•ï¼ŒèŽ·å–åˆ°ä»–çš„æ‰€æœ‰çš„çˆ¶å…ƒç´ ï¼Œæ‰¾åˆ°tr,ç„¶åŽå†åœ¨trä¸­æ‰¾åˆ°ç¬¬ä¸€ä¸ªtd,èŽ·å–åˆ°ç¬¬ä¸€ä¸ªtdçš„å€¼
		//	var empId = $(this).parents("tr").find("td:eq(2)").text();
			//alert(empName);
				var device="${deviceSn}";
				alert(device);
			if(confirm("do you want to collect punch recordï¼Ÿ")){
				//ç¡®è®¤ï¼Œå‘é€ajaxè¯·æ±‚ï¼Œåˆ é™¤
				$.ajax({
					url:"${APP_PATH}/getNewLog?deviceSn="+device,
					type:"GET",
					success:function(result){
						alert(result.msg);
						//å›žåˆ°å½“å‰é¡µ
						to_page(totalRecord);
					}
				});
			}
		});
		
		//åˆå§‹åŒ–æ¨¡æ€æ¡†ï¼Œæ¯æ¬¡åŠ è½½æ¸…ç©ºé‡Œé¢çš„æ•°æ®
		function initEmpAddModal(ele){
			$(ele).val("");
			$(ele).parent().removeClass("has-success has-error");
			$(ele).next("span").text("");
		}
		
		//æŸ¥è¯¢æ‰€æœ‰çš„éƒ¨é—¨ä¿¡æ¯å¹¶æ˜¾ç¤ºåœ¨ä¸‹æ‹‰åˆ—è¡¨ä¸­
		function getDepts(ele){
			$(ele).empty();
			$.ajax({
				url:"${APP_PATH}/depts",
				type:"GET",
				success:function(result){
					//console.log(result);
					$.each(result.extend.depts,function(){
						var optionEle = $("<option></option>").append(this.deptName).attr("value",this.deptId);
						optionEle.appendTo(ele);
					});
				}
			});
		}
		
		//ç”¨æˆ·åè¡¨å•æ ¡éªŒ
		function validate_add_form_empName(){
			//1. æ‹¿åˆ°è¦éªŒè¯çš„æ•°æ®ï¼Œä½¿ç”¨æ­£åˆ™è¡¨è¾¾å¼
			var empName = $("#empName_add_input").val();
			var regName = /(^[a-zA-Z0-9_-]{6,16}$)|(^[\u2E80-\u9FFF]{2,5})/;
			if(!regName.test(empName)){
				//alert("ç”¨æˆ·åå¯ä»¥æ˜¯2-5ä½ä¸­æ–‡æˆ–è€…æ˜¯6-16ä½è‹±æ–‡å’Œæ•°å­—çš„ç»„åˆ");
				show_validate_msg("#empName_add_input","error","ç”¨æˆ·åå¯ä»¥æ˜¯2-5ä½ä¸­æ–‡æˆ–è€…æ˜¯6-16ä½è‹±æ–‡å’Œæ•°å­—çš„ç»„åˆ");
				return false;
			}else{
				show_validate_msg("#empName_add_input","success","");
			}
			return true;
		}
		
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
		
		//ç‚¹å‡»ä¿å­˜äº‹ä»¶
		$("#emp_save_btu").click(function(){
			//1.æ¨¡æ€æ¡†ä¸­çš„è¡¨å•æ•°æ®æäº¤ç»™æ•°æ®åº“è¿›è¡Œä¿å­˜
			//2.å‘é€ajaxè¯·æ±‚ä¿å­˜å‘˜å·¥æ•°æ®
			//alert($("#empAddModal form").serialize());
			
			//1.åˆ¤æ–­ä¹‹å‰çš„ç”¨æˆ·åæ ¡éªŒæ˜¯å¦æˆåŠŸï¼Œå¦åˆ™å°±ä¸å¾€ä¸‹èµ°
			if($(this).attr("ajax_validate")=="error"){
				return false;
			} 
			//2.åˆ¤æ–­ä¹‹å‰çš„é‚®ç®±æ ¡éªŒæ˜¯å¦æˆåŠŸï¼Œå¦åˆ™å°±ä¸å¾€ä¸‹èµ°
			 if($(this).attr("ajax_validate2")=="error"){
				return false;
			} 
			$.ajax({
				url:"${APP_PATH}/emp",
				type:"POST",
				data:$("#empAddModal form").serialize(),
				success:function(result){
					 	if(result.code == 100){ 
						//alert(result.msg);
						//å‘˜å·¥ä¿å­˜æˆåŠŸ
						//1.å…³é—­æ¨¡æ€æ¡†
						$("#empAddModal").modal('hide');
						//2.è·³è½¬åˆ°æœ€åŽä¸€é¡µ
						to_page(totalRecord);
					 }else{
						//æ˜¾ç¤ºé”™è¯¯ä¿¡æ¯
						console.log(result);
						if(undefined != result.extend.errorsFields.empName){
							show_validate_msg("#empName_add_input","error",result.extend.errorsFields.empName);
						}
						//alert(result.extend.errorsFields.email);
						if(undefined != result.extend.errorsFields.email){
							show_validate_msg("#empEmail_add_input","error",result.extend.errorsFields.email);
						}
					} 
				}
			});
		});
		
		//ä¸ºç¼–è¾‘æŒ‰é’®ç»‘å®šäº‹ä»¶
		$(document).on("click",".edit_btu",function(){
			//alert("edit");
			
			//1.æŸ¥å‡ºéƒ¨é—¨ä¿¡æ¯ï¼Œæ˜¾ç¤ºéƒ¨é—¨ä¿¡æ¯
			getDepts("#empUpdateModal select");
			//2.æŸ¥å‡ºå‘˜å·¥ä¿¡æ¯ï¼Œæ˜¾ç¤ºå‘˜å·¥ä¿¡æ¯
			getEmp($(this).attr("edit-id"));
			//3.æŠŠå‘˜å·¥çš„idä¼ é€’ç»™æ¨¡æ€æ¡†çš„æ›´æ–°æŒ‰é’®
			$("#emp_update_btu").attr("edit-id",$(this).attr("edit-id"));
			//å¼¹å‡ºæ¨¡æ€æ¡†
			//åˆå§‹åŒ–
			initEmpAddModal("#empName_update_input");
			initEmpAddModal("#empEmail_update_input");
			$("#empUpdateModal").modal({
				backdrop:"static"
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
			//1.å¼¹å‡ºæ˜¯å¦ç¡®è®¤åˆ é™¤å¯¹è¯æ¡†
			//èŽ·å–empNameçš„æ–¹æ³•ï¼ŒèŽ·å–åˆ°ä»–çš„æ‰€æœ‰çš„çˆ¶å…ƒç´ ï¼Œæ‰¾åˆ°tr,ç„¶åŽå†åœ¨trä¸­æ‰¾åˆ°ç¬¬ä¸€ä¸ªtd,èŽ·å–åˆ°ç¬¬ä¸€ä¸ªtdçš„å€¼
			var empName = $(this).parents("tr").find("td:eq(2)").text();
			//alert(empName);
			if(confirm("ç¡®è®¤åˆ é™¤ã€"+empName +"ã€‘å·å‘˜å·¥å—ï¼Ÿ")){
				//ç¡®è®¤ï¼Œå‘é€ajaxè¯·æ±‚ï¼Œåˆ é™¤
				$.ajax({
					url:"${APP_PATH}/emp/"+$(this).attr("delete-id"),
					type:"DELETE",
					success:function(result){
						alert(result.msg);
						//å›žåˆ°å½“å‰é¡µ
						to_page(currentPage);
					}
				});
			}
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
			    alert("è¯·é€‰æ‹©è¦åˆ é™¤çš„å‘˜å·¥")
			} else if(confirm("ç¡®è®¤åˆ é™¤ã€"+empNames+"ã€‘å·å‘˜å·¥å—ï¼Ÿ")){
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
