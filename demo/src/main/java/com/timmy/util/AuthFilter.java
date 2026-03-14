package com.timmy.util;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class AuthFilter implements Filter {

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		// no-op
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
			chain.doFilter(request, response);
			return;
		}

		HttpServletRequest req = (HttpServletRequest) request;
		HttpServletResponse resp = (HttpServletResponse) response;
		String contextPath = req.getContextPath();
		String uri = req.getRequestURI();
		String path = uri.substring(contextPath.length());

		if (isAllowedWithoutLogin(path)) {
			chain.doFilter(request, response);
			return;
		}

		HttpSession session = req.getSession(false);
		Object authUser = session == null ? null : session.getAttribute(SimpleAuthConfig.SESSION_USER_KEY);
		if (authUser != null) {
			chain.doFilter(request, response);
			return;
		}

		if (isAjaxRequest(req) || path.startsWith("/emp") || path.startsWith("/addPerson") || path.startsWith("/savePerson")
				|| path.startsWith("/set")) {
			resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			resp.setContentType("application/json;charset=UTF-8");
			resp.getWriter().write("{\"code\":401,\"msg\":\"UNAUTHORIZED\"}");
			return;
		}

		resp.sendRedirect(contextPath + "/login");
	}

	private boolean isAllowedWithoutLogin(String path) {
		return path.equals("/login") || path.equals("/login.jsp") || path.startsWith("/login?")
				|| path.startsWith("/static/") || path.equals("/favicon.ico");
	}

	private boolean isAjaxRequest(HttpServletRequest req) {
		String requestedWith = req.getHeader("X-Requested-With");
		return requestedWith != null && "XMLHttpRequest".equalsIgnoreCase(requestedWith);
	}

	@Override
	public void destroy() {
		// no-op
	}
}
