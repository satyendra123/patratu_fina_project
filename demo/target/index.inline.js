
		var totalRecord,currentPage;
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
		});
		
		function to_page(pn){
			$.ajax({
				url:"${APP_PATH}/emps",
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
				var btuTd = $("<td></td>").append(delBtu).append(" ").append(editBtu).append(" ").append(uploadBtu);
				//appendæ–¹æ³•æ‰§è¡Œå®ŒåŽä»è¿”å›žåŽŸæ¥çš„å…ƒç´ 
				$("<tr></tr>").append(checkBoxTd)
					.append(numTd)
					.append(empIdTd)
					.append(empNameTd)
					.append(empImageTd)
					.append(rollId)
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
				success:function(result){
					$.each(result.extend.device, function(){
                        var optionEle = $("<option></option>").append(this.serialNum).attr("value",this.serialNum);
                        optionEle.appendTo("#deviceSelect");
                    })

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
			$("#passwordInput").val("");
			$("#cardNumInput").val("");
			$("#photoInput").val("");
			$("#addUserModal").modal({
				backdrop:"static"
			});
		});

		$("#form").on("submit", function(e){
			e.preventDefault();
			var formData = new FormData(this);
			$.ajax({
				url:"${APP_PATH}/savePerson",
				type:"POST",
				data:formData,
				processData:false,
				contentType:false,
				success:function(result){
					alert(result.msg);
					if(result.code == 100){
						$("#addUserModal").modal("hide");
						to_page(currentPage || 1);
					}
				},
				error:function(){
					alert("Save failed.");
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
					$("#passwordInput").val(p.password || "");
					$("#cardNumInput").val(p.cardNum || "");
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
						url:"${APP_PATH}/openDoor?doorNum="+doorNum+"&&deviceSn="+deviceSn,
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
						alert(result.msg);
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
						alert(result.msg);
						//å›žåˆ°å½“å‰é¡µ
						to_page(currentPage);
					}
				});
			}
		});
		$("#setUserToDevice_emp_modal_btn").click(function(){
			var deviceSn=document.getElementById('deviceSelect').value;
			if(confirm("do you want to send user info to deviceï¼Ÿ")){
				//ç¡®è®¤ï¼Œå‘é€ajaxè¯·æ±‚ï¼Œåˆ é™¤
				$.ajax({
					url:"${APP_PATH}/setPersonToDevice?deviceSn="+deviceSn,
					type:"GET",
					success:function(result){
						alert(result.msg);
						//å›žåˆ°å½“å‰é¡µ
						to_page(currentPage);
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
		
    	var deviceSn=document.getElementById('deviceSelect').value;
    	alert("serial num"+deviceSn);
    	window.location.href="${APP_PATH}/logRecords.jsp?deviceSn="+deviceSn;
    	
		
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
	
