<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Login</title>
<%
	pageContext.setAttribute("APP_PATH", request.getContextPath());
%>
<script type="text/javascript" src="${APP_PATH}/static/js/jquery-1.12.4.min.js"></script>
<link href="${APP_PATH}/static/bootstrap-3.3.7-dist/css/bootstrap.min.css" rel="stylesheet">
<script src="${APP_PATH}/static/bootstrap-3.3.7-dist/js/bootstrap.min.js"></script>
<style type="text/css">
	html, body {
		height: 100%;
		margin: 0;
		overflow: hidden;
	}
	body {
		background: #f5f7fb;
	}
	.login-shell {
		height: 100vh;
		width: 100%;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 20px;
		box-sizing: border-box;
	}
	.login-card {
		width: 100%;
		max-width: 1080px;
		height: 100%;
		max-height: 640px;
		background: #fff;
		border: 1px solid #e2e8f0;
		border-radius: 12px;
		overflow: hidden;
		box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
	}
	.login-image-panel {
		height: 100%;
		background: #0f172a;
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.login-image-panel img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	.login-form-panel {
		height: 100%;
		padding: 40px 48px;
		display: flex;
		flex-direction: column;
		justify-content: center;
	}
	.login-title {
		margin: 0 0 8px 0;
		font-size: 30px;
		font-weight: 600;
		color: #0f172a;
	}
	.login-subtitle {
		margin-bottom: 28px;
		color: #475569;
	}
	@media (max-width: 991px) {
		html, body {
			overflow: auto;
		}
		.login-shell {
			height: auto;
			min-height: 100vh;
			padding: 12px;
		}
		.login-card {
			height: auto;
			max-height: none;
		}
		.login-image-panel {
			height: 220px;
		}
		.login-form-panel {
			height: auto;
			padding: 24px 20px;
		}
	}
</style>
</head>
<body>
	<div class="login-shell">
		<div class="login-card row" style="margin:0;">
			<div class="col-md-7 login-image-panel">
				<img src="${APP_PATH}/static/img/27530.jpg" alt="Login Visual">
			</div>
			<div class="col-md-5 login-form-panel">
				<h1 class="login-title">Device Console Login</h1>
				<p class="login-subtitle">Sign in to manage users and device operations.</p>
				<c:if test="${param.error == '1'}">
					<div class="alert alert-danger" role="alert" style="margin-bottom:20px;">
						Invalid username or password.
					</div>
				</c:if>
				<form action="${APP_PATH}/login" method="post" autocomplete="off">
					<div class="form-group">
						<label for="usernameInput">Username</label>
						<input id="usernameInput" type="text" name="username" class="form-control" required>
					</div>
					<div class="form-group" style="margin-top:14px;">
						<label for="passwordInput">Password</label>
						<input id="passwordInput" type="password" name="password" class="form-control" required>
					</div>
					<button type="submit" class="btn btn-primary btn-block" style="margin-top:22px;">Login</button>
				</form>
			</div>
		</div>
	</div>
</body>
</html>
