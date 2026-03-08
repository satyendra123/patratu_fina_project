
		var totalRecord,currentPage;
		// 1. 页面加载成功之后直接发送 ajax 请求得到 分页数据
		//页面加载完成之后，直接发送ajax 请求，去首页
		$(function(){
			$('.date').datetimepicker({
			    format: 'yyyy-mm-dd',
			    autoclose: true,
			    minView: 2,
			    language: 'en'
			})
			//去首页
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
					// 1. 解析并显示员工数据
					build_emp_table(result);
					// 2. 解析并显示分类信息
					buid_page_info(result);
					// 3. 解析并显示分页条信息
					build_page_nav(result);
				}
			});
		}
		
		//解析显示员工信息
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
	           //为编辑按钮添加一个自定义的属性，来表示当前的员工id
	            uploadBtu.attr("upload-id",userId);
				var editBtu = $("<button></button>").addClass("btn btn-primary btn-sm edit_btu")
					.append("<span></span>").append("EditUser");
				editBtu.attr("edit-id",userId);
				
				var delBtu = $("<button></button>").addClass("btn btn-danger btn-sm delete_btu")
							.append("<span></span>").append("DeleteUser");
				//为删除按钮添加一个自定义的属性，来表示当前的员工id
				delBtu.attr("delete-id",userId);
				var btuTd = $("<td></td>").append(delBtu).append(" ").append(editBtu).append(" ").append(uploadBtu);
				//append方法执行完后仍返回原来的元素
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
		//解析显示分页信息
		function buid_page_info(result){
			$("#page_info_area").empty();
			$("#page_info_area").append("Current Page:"+result.extend.pageInfo.pageNum +
					", Count Page:"+result.extend.pageInfo.pages +
					", All Records："+result.extend.pageInfo.total);
			totalRecord = result.extend.pageInfo.pages;
			currentPage = result.extend.pageInfo.pageNum;
		}
		
		//解析显示分页条信息
		function build_page_nav(result){
			$("#page_nav_area").empty();
			var ul = $("<ul></ul>").addClass("pagination");
			
			var firstPageLi = $("<li></li>").append($("<a></a>").append("FirstPage").attr("href","#"));
			var prePageLi = $("<li></li>").append($("<a></a>").append("&laquo;"));
			if(result.extend.pageInfo.hasPreviousPage == false){
				prePageLi.addClass("disabled");
			}else{
				//添加单击事件
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
				//添加单击事件
				nextPageLi.click(function(){
					to_page(result.extend.pageInfo.pageNum +1);
				});
				lastPageLi.click(function(){
					to_page(result.extend.pageInfo.pages);
				});
			}
			//添加首页和前一页
			ul.append(firstPageLi).append(prePageLi);
			$.each(result.extend.pageInfo.navigatepageNums,function(index,item){
				var numLi = $("<li></li>").append($("<a></a>").append(item));
				//添加每一个遍历出来的页码
				if(item == result.extend.pageInfo.pageNum){
					numLi.addClass("active");
				}
				numLi.click(function(){
					to_page(item);
				});
				ul.append(numLi);
			});
			//添加最后一页和末页
			ul.append(nextPageLi).append(lastPageLi);
			//把 ul 添加到 navElv 中去
			var navElv = $("<nav></nav>").append(ul);
			
			navElv.appendTo("#page_nav_area");
		}
		//获取设备信息
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
		//点击新增按钮弹出模态框
		$("#openDoor_modal_btn").click(function(){
			
			$("#opneDoorModal").modal({
				backdrop:"static"
			});
		});
		
		
		//点击新增按钮弹出模态框
		$("#getUserLock_modal_btn").click(function(){
			
			$("#getUserLockModal").modal({
				backdrop:"static"
			});
		});
		
		
		//点击新增按钮弹出模态框
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
		
		
		//点击新增按钮弹出模态框
		$("#accessWeek_emp_modal_btn").click(function(){
			//初始化模态框
			//也可以这么做
			//$("#empAddModal")[0].reset();
			initEmpAddModal("#name_accessweek_input");
			//发送ajax 请求，查出部门信息，显示下拉列表
			//getDepts("#empAddModal select");
			  getAccessDay("#accessWeekAddModal #daySelect")
			//弹出模态框
			$("#accessWeekAddModal").modal({
				backdrop:"static"
			});
		});
		
	
		
		$("#accessDay_emp_modal_btn").click(function(){
			//初始化模态框
			//也可以这么做
			//$("#empAddModal")[0].reset();
			initEmpAddModal("#name_accessweek_input");
			//发送ajax 请求，查出部门信息，显示下拉列表
			//getDepts("#empAddModal select");
			  getAccessDay("#accessWeekAddModal #daySelect")
			//弹出模态框
			$("#accessDayModal").modal({
				backdrop:"static"
			});
		});
		
		//初始化模态框，每次加载清空里面的数据
		function initEmpAddModal(ele){
			$(ele).val("");
			$(ele).parent().removeClass("has-success has-error");
			$(ele).next("span").text("");
		}
		
		
		$("#lockGroup_emp_modal_btn").click(function(){
			//初始化模态框
			//也可以这么做
			
			//弹出模态框
			$("#lockGroupModal").modal({
				backdrop:"static"
			});
		});
		
		//弹出授权模态框
		$("#authorize_emp_modal_btn").click(function(){
			//初始化模态框
			//也可以这么做
			
			//弹出模态框
			$("#userGroupModal").modal({
				backdrop:"static"
			});
		});
		
		//弹出下载用户模态框
		$("#download_emp_modal_btn").click(function(){
			//初始化模态框
			//也可以这么做
			
			//弹出模态框
			getEnrollId();
			$("#downLoadOneUserModal").modal({
				backdrop:"static"
			});
		});
		
		
	      $("#openDoor_btu").click(function(){
				
				//1.弹出是否确认删除对话框
				//获取empName的方法，获取到他的所有的父元素，找到tr,然后再在tr中找到第一个td,获取到第一个td的值
			//	var empId = $(this).parents("tr").find("td:eq(2)").text();
				//alert(empName);
				var doorNum=document.getElementById('doorNum').value;
				var deviceSn=document.getElementById('deviceSelect').value;
				//alert(deviceSn);
				if(confirm("do you want to open the door？")){
					//确认，发送ajax请求，删除
					$.ajax({
						url:"${APP_PATH}/openDoor?doorNum="+doorNum+"&&deviceSn="+deviceSn,
						type:"GET",
						success:function(result){
							alert(result.msg);
							//回到当前页
							$("#opneDoorModal").modal('hide');
							to_page(currentPage);
						}
					});
				}
			});
	      
	      $("#getUserLock_btu").click(function(){
				
				//1.弹出是否确认删除对话框
				//获取empName的方法，获取到他的所有的父元素，找到tr,然后再在tr中找到第一个td,获取到第一个td的值
			//	var empId = $(this).parents("tr").find("td:eq(2)").text();
				//alert(empName);
				var enrollId=document.getElementById('lockEnrollId').value;
				var deviceSn=document.getElementById('deviceSelect').value;
				//alert(deviceSn);
				if(confirm("do you want to collect the user lock info？")){
					//确认，发送ajax请求，删除
					$.ajax({
						url:"${APP_PATH}/geUSerLock?enrollId="+enrollId+"&&deviceSn="+deviceSn,
						type:"GET",
						success:function(result){
							alert(result.msg);
							//回到当前页
							$("#opneDoorModal").modal('hide');
							to_page(currentPage);
						}
					});
				}
			});
	      $("#getDevLock_modal_btn").click(function(){
				
				//1.弹出是否确认删除对话框
				//获取empName的方法，获取到他的所有的父元素，找到tr,然后再在tr中找到第一个td,获取到第一个td的值
			//	var empId = $(this).parents("tr").find("td:eq(2)").text();
				//alert(empName);
				//var doorNum=document.getElementById('doorNum').value;
				var deviceSn=document.getElementById('deviceSelect').value;
				//alert(deviceSn);
				if(confirm("do you want to get device lock info？")){
					//确认，发送ajax请求，删除
					$.ajax({
						url:"${APP_PATH}/getDevLock?deviceSn="+deviceSn,
						type:"GET",
						success:function(result){
							alert(result.msg);
							//回到当前页
						//	$("#opneDoorModal").modal('hide');
							to_page(currentPage);
						}
					});
				}
			});
	      
	      $("#cleanAdmin_modal_btn").click(function(){
				
				//1.弹出是否确认删除对话框
				//获取empName的方法，获取到他的所有的父元素，找到tr,然后再在tr中找到第一个td,获取到第一个td的值
			//	var empId = $(this).parents("tr").find("td:eq(2)").text();
				//alert(empName);
				//var doorNum=document.getElementById('doorNum').value;
				var deviceSn=document.getElementById('deviceSelect').value;
				//alert(deviceSn);
				if(confirm("do you want to clean admin？")){
					//确认，发送ajax请求，删除
					$.ajax({
						url:"${APP_PATH}/cleanAdmin?deviceSn="+deviceSn,
						type:"GET",
						success:function(result){
							alert(result.msg);
							//回到当前页
						//	$("#opneDoorModal").modal('hide');
							to_page(currentPage);
						}
					});
				}
			});
		
	      $("#synchronize_Time").click(function(){
				
				var deviceSn=document.getElementById('deviceSelect').value;
				//确认，发送ajax请求，删除
				$.ajax({
					url:"${APP_PATH}/synchronizeTime?deviceSn="+deviceSn,
					type:"GET",
					success:function(result){
						alert(result.msg);
						//回到当前页
					//	$("#opneDoorModal").modal('hide');
						to_page(currentPage);
					}
				});
			});
	      
		//手动采集用户ajax请求
		$("#collectList_emp_modal_btn").click(function(){
			
			//1.弹出是否确认删除对话框
			//获取empName的方法，获取到他的所有的父元素，找到tr,然后再在tr中找到第一个td,获取到第一个td的值
		//	var empId = $(this).parents("tr").find("td:eq(2)").text();
			//alert(empName);
			var deviceSn=document.getElementById('deviceSelect').value;
			//alert(deviceSn);
			if(confirm("do you want to collect user list？")){
				//确认，发送ajax请求，删除
				$.ajax({
					url:"${APP_PATH}/sendWs?deviceSn="+deviceSn,
					type:"GET",
					success:function(result){
						alert(result.msg);
						//回到当前页
						to_page(currentPage);
					}
				});
			}
		});
		
      $("#collectInfo_emp_modal_btn").click(function(){
			
			//1.弹出是否确认删除对话框
			//获取empName的方法，获取到他的所有的父元素，找到tr,然后再在tr中找到第一个td,获取到第一个td的值
		//	var empId = $(this).parents("tr").find("td:eq(2)").text();
			//alert(empName);
			var deviceSn=document.getElementById('deviceSelect').value;
			//alert(deviceSn);
			if(confirm("do you want to collect user detail？")){
				//确认，发送ajax请求，删除
				$.ajax({
					url:"${APP_PATH}/getUserInfo?deviceSn="+deviceSn,
					type:"GET",
					success:function(result){
						alert(result.msg);
						//回到当前页
						to_page(currentPage);
					}
				});
			}
		});
		$("#setUserToDevice_emp_modal_btn").click(function(){
			var deviceSn=document.getElementById('deviceSelect').value;
			if(confirm("do you want to send user info to device？")){
				//确认，发送ajax请求，删除
				$.ajax({
					url:"${APP_PATH}/setPersonToDevice?deviceSn="+deviceSn,
					type:"GET",
					success:function(result){
						alert(result.msg);
						//回到当前页
						to_page(currentPage);
					}
				});
			}
		});
		
		
		$("#setUserName_modal_btn").click(function(){
			var deviceSn=document.getElementById('deviceSelect').value;
			if(confirm("do you want to send user name to device？")){
				//确认，发送ajax请求，删除
				$.ajax({
					url:"${APP_PATH}/setUsernameToDevice?deviceSn="+deviceSn,
					type:"GET",
					success:function(result){
						alert(result.msg);
						//回到当前页
						to_page(currentPage);
					}
				});
			}
		});
		
		
    $("#initSys_emp_modal_btn").click(function(){
			
    	var deviceSn=document.getElementById('deviceSelect').value;
			if(confirm("do you want to init device？it will clean all info")){
				//确认，发送ajax请求，删除
				$.ajax({
					url:"${APP_PATH}/initSystem?deviceSn="+deviceSn,
					type:"GET",
					success:function(result){
						alert(result.msg);
						//回到当前页
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
    
     //获取设备信息
	$("#getDeviceInfo_modal_btn").click(function(){
		
		//1.弹出是否确认删除对话框
		//获取empName的方法，获取到他的所有的父元素，找到tr,然后再在tr中找到第一个td,获取到第一个td的值
	//	var empId = $(this).parents("tr").find("td:eq(2)").text();
		//alert(empName);
		var deviceSn=document.getElementById('deviceSelect').value;
		//alert(deviceSn);
		if(confirm("do you want to get device info？")){
			//确认，发送ajax请求，删除
			$.ajax({
				url:"${APP_PATH}/getDeviceInfo?deviceSn="+deviceSn,
				type:"GET",
				success:function(result){
					alert(result.msg);
					//回到当前页
					to_page(currentPage);
				}
			});
		}
	});
    
		//查询所有的天时段信息并显示在下拉列表中
		function getAccessDay(ele){
			$(ele).empty();
			$.ajax({
				url:"${APP_PATH}/accessDays",
				type:"GET",
				success:function(result){
					//console.log(result);
					$.each(result.extend.accessdays,function(){
						var optionEle = $("<option></option>").append(this.id);
						optionEle.appendTo(ele);
					});
				}
			});
		}
		
		
		
		//邮箱表单校验
		function validate_add_form_empEmail(){
			//1. 拿到要验证的数据，使用正则表达式
			
			var email = $("#empEmail_add_input").val();
			var regEmail = /^([a-z0-9_\.-]+)@([\da-z\.-]+)\.([a-z\.]{2,6})$/;
			if(!regEmail.test(email)){
				//alert("邮箱不合法.......");
				show_validate_msg("#empEmail_add_input","error","邮箱不合法");
				return false;
			}else{
				show_validate_msg("#empEmail_add_input","success","可以使用");
			}
			return true;
		}
		//显示校验的提示信息
		function show_validate_msg(ele,status,msg){
			//每次弹出模态框之前，清空里面的内容
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
		
		//检验用户名是否合法
		 $("#empName_add_input").change(function(){
			var empName = this.value;
			//表单用户名前台校验
			if(validate_add_form_empName()){
			
				//发送ajax请求，校验用户名是否可用
				$.ajax({
					url:"${APP_PATH}/checkuser",
					type:"POST",
					data:"empName="+empName,
					success:function(result){
						if(result.code == 100){
							show_validate_msg("#empName_add_input","success","用户名可用");
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
		
		//检验邮箱是否合法
		 $("#empEmail_add_input").change(function(){
			if(!validate_add_form_empEmail()){
				$("#emp_save_btu").attr("ajax_validate2","error");
				return false; 
			};
			$("#emp_save_btu").attr("ajax_validate2","success");
		}); 
		
		//点击保存周时段事件
		$("#emp_save_btu").click(function(){
			//1.模态框中的表单数据提交给数据库进行保存
			//2.发送ajax请求保存员工数据
			//alert($("#empAddModal form").serialize());
			
			//1.判断之前的用户名校验是否成功，否则就不往下走
			
			$.ajax({
				url:"${APP_PATH}/setAccessWeek",
				type:"POST",
				data:$("#form1").serialize(),
				success:function(result){
					 	if(result.code == 100){ 
						//alert(result.msg);
						//员工保存成功
						//1.关闭模态框
						$("#accessWeekAddModal").modal('hide');
						//2.跳转到最后一页
						alert(result.msg);
						//回到当前页
						to_page(currentPage);
					 }else{
						 alert("id existed！");
					} 
				}
			});
		});
		
	   //保存天时段
		$("#access_day_btu").click(function(){
			//1.模态框中的表单数据提交给数据库进行保存
			//2.发送ajax请求保存员工数据
			//alert($("#empAddModal form").serialize());
			
			//1.判断之前的用户名校验是否成功，否则就不往下走
			
			$.ajax({
				url:"${APP_PATH}/setAccessDay",
				type:"POST",
				data:$("#form2").serialize(),
				success:function(result){
					 	if(result.code == 100){ 
						//alert(result.msg);
						//员工保存成功
						//1.关闭模态框
						$("#accessDayModal").modal('hide');
						//2.跳转到最后一页
						alert(result.msg);
						//回到当前页
						to_page(currentPage);
					 }else{
						 alert("id existed！");
					} 
				}
			});
		});
		
		 //保存锁组合
		$("#lockgroup_btu").click(function(){
			//1.模态框中的表单数据提交给数据库进行保存
			//2.发送ajax请求保存员工数据
			//alert($("#empAddModal form").serialize());
			
			//1.判断之前的用户名校验是否成功，否则就不往下走
			
			$.ajax({
				url:"${APP_PATH}/setLocckGroup",
				type:"POST",
				data:$("#form3").serialize(),
				success:function(result){
					 	 
						//alert(result.msg);
						//员工保存成功
						//1.关闭模态框
						$("#lockGroupModal").modal('hide');
						//2.跳转到最后一页
						alert(result.msg);
						//回到当前页
						to_page(currentPage);
					 
				}
			});
		});
		 
		 //保存授权数据
		$("#userLock_btu").click(function(){
			//1.模态框中的表单数据提交给数据库进行保存
			//2.发送ajax请求保存员工数据
			//alert($("#empAddModal form").serialize());
			
			//1.判断之前的用户名校验是否成功，否则就不往下走
			
			$.ajax({
				url:"${APP_PATH}/setUserLock",
				type:"POST",
				data:$("#form4").serialize(),
				success:function(result){
					 	 
						//alert(result.msg);
						//员工保存成功
						//1.关闭模态框
						$("#userGroupModal").modal('hide');
						//2.跳转到最后一页
						alert(result.msg);
						//回到当前页
						to_page(currentPage);
					 
				}
			});
		});
		 
		 
		 //保存单个员工数据
		$("#uploadOneUser_btu").click(function(){
			//1.模态框中的表单数据提交给数据库进行保存
			//2.发送ajax请求保存员工数据
			//alert($("#empAddModal form").serialize());
			
			//1.判断之前的用户名校验是否成功，否则就不往下走
			var empId=document.getElementById('enrollId1').value;
			var backupNum=document.getElementById('backupNumSelect').value;
			var deviceSn=document.getElementById('deviceSelect').value;
			$.ajax({
				url:"${APP_PATH}/setOneUser?enrollId="+empId+"&backupNum="+backupNum+"&deviceSn="+deviceSn,
				type:"GET",
				success:function(result){
					 	 
					if(result.code == 100){ 
						//alert(result.msg);
						//员工保存成功
						//1.关闭模态框
						$("#accessWeekAddModal").modal('hide');
						//2.跳转到最后一页
						alert(result.msg);
						//回到当前页
						to_page(currentPage);
					 }else{
						 alert("backup num is not exist！");
					} 
					 
				}
			});
		});
		 
		 //下载单个员工数据
		$("#downloadOneUser_btu").click(function(){
			//1.模态框中的表单数据提交给数据库进行保存
			//2.发送ajax请求保存员工数据
			//alert($("#empAddModal form").serialize());
			
			//1.判断之前的用户名校验是否成功，否则就不往下走
			var empId=document.getElementById('enrollIdSelect').value;
			var backupNum=document.getElementById('backupNumSelect1').value;
			var deviceSn=document.getElementById('deviceSelect').value;
			$.ajax({
				url:"${APP_PATH}/sendGetUserInfo?enrollId="+empId+"&backupNum="+backupNum+"&deviceSn="+deviceSn,
				type:"GET",
				success:function(result){
					 	 
					if(result.code == 100){ 
						//alert(result.msg);
						//员工保存成功
						//1.关闭模态框
						$("#accessWeekAddModal").modal('hide');
						//2.跳转到最后一页
						alert(result.msg);
						//回到当前页
						to_page(currentPage);
					 }else{
						 alert("backup num is not exist！");
					} 
					 
				}
			});
		});
		 
		//获取员工信息
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
		
		//点击更新，更新员工信息
		$("#emp_update_btu").click(function(){
			//验证邮箱是否合法
			var email = $("#empEmail_update_input").val();
			var regEmail = /^([a-z0-9_\.-]+)@([\da-z\.-]+)\.([a-z\.]{2,6})$/;
			 if(!regEmail.test(email)){
				show_validate_msg("#empEmail_update_input","error","邮箱不合法");
				return false;
			}else{
				
				//发送ajax请求，更新员工信息
				
				$.ajax({
					url:"${APP_PATH}/emp/"+$(this).attr("edit-id"),
					type:"PUT",
					data:$("#empUpdateModal form").serialize(),
					success:function(result){
						//alert(result.msg);
							if(result.code == 100){ 
								//1.关闭模态框
								$("#empUpdateModal").modal('hide');
								//2.回到当前页面
								to_page(currentPage);
							}else{
								//显示错误信息
								console.log(result);
								if(undefined != result.extend.errorsFields.email){
									show_validate_msg("#empEmail_update_input","error",result.extend.errorsFields.email);
								}
							}
					}
				});
			}
		});
		
		//为删除按钮绑定单击事件
		$(document).on("click",".delete_btu",function(){
			var deviceSn=document.getElementById('deviceSelect').value;
			//1.弹出是否确认删除对话框
			//获取empName的方法，获取到他的所有的父元素，找到tr,然后再在tr中找到第一个td,获取到第一个td的值
			var empId = $(this).parents("tr").find("td:eq(2)").text();
			//alert(empName);
			if(confirm("are you sure delete number【"+empId +"】 staff？")){
				//确认，发送ajax请求，删除
				alert("发送"+"${APP_PATH}/deletePersonFromDEvice");
				$.ajax({
					url:"${APP_PATH}/deletePersonFromDevice?enrollId="+$(this).attr("delete-id")+"&deviceSn="+deviceSn,
					type:"GET",
					success:function(result){
						alert(result.msg);
						//回到当前页
						to_page(currentPage);
					}
				});
			}
		});
		
		//为下发按钮绑定单击事件
		$(document).on("click",".upload_btu",function(){
			//获取设备编号
			var deviceSn=document.getElementById('deviceSelect').value;
			//1.弹出是否确认删除对话框
			//获取empName的方法，获取到他的所有的父元素，找到tr,然后再在tr中找到第一个td,获取到第一个td的值
			var empId = $(this).parents("tr").find("td:eq(2)").text();
			
		//	initEmpAddModal("#name_accessweek_input");
			//发送ajax 请求，查出部门信息，显示下拉列表
			//getDepts("#empAddModal select");
			//  getAccessDay("#accessWeekAddModal #daySelect")
			//弹出模态框
			$("#uploadOneUserModal").modal({
				backdrop:"static"
			});
			$("#enrollId1").val(empId)
			//alert(empName);
			/* if(confirm("确认下发【"+empId +"】号员工吗？")){
				//确认，发送ajax请求，删除
				$.ajax({
					url:"${APP_PATH}/setOneUser?enrollId="+$(this).attr("upload-id")+"&&deviceSn="+deviceSn,
					type:"GET",
					success:function(result){
						alert(result.msg);
						//回到当前页
						to_page(currentPage);
					}
				});
			} */
		});
		
		
		//完成全选/全不选功能
		$("#check_all").click(function(){
			//prop修改和读取原生dom属性的值
			//attr获取自定义属性的值
			$(".check_item").prop("checked",$(this).prop("checked"));
		});
		
		//单个的选择框全选，顶上的也选择
		$(document).on("click",".check_item",function(){
			//判断当前选中的元素是否是全部的元素
			var flag = ($(".check_item:checked").length==$(".check_item").length)
				$("#check_all").prop("checked",flag);
			
		});
		
		//为多选删除框绑定单击事件
		$("#delete_emp_all_btu").click(function(){
			var empNames="";
			var delidstr="";
			$.each($(".check_item:checked"),function(){
			  empNames += $(this).parents("tr").find("td:eq(2)").text()+",";
			  delidstr += $(this).parents("tr").find("td:eq(1)").text()+"-";
			});
			//alert(delidstr);
			//去除empNames多余的，
			empNames = empNames.substring(0, empNames.length-1);
			//去除delidstr的多余的-
			delidstr = delidstr.substring(0, delidstr.length-1);
			if(empNames == ""){
			    alert("please select the staff you want to delete")
			} else if(confirm("confirm【"+empNames+"】staff num？")){
				//发送ajax请求并删除
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
	
