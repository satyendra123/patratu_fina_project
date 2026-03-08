package com.timmy.websocket;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

public class StartFilter implements Filter {

	private static final AtomicBoolean STARTED = new AtomicBoolean(false);

	@Override
	public void destroy() {
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		chain.doFilter(request, response);
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		startWebsocketInstantMsg(filterConfig);
	}

	/**
	 * Start websocket server from existing web application context.
	 */
	public void startWebsocketInstantMsg(FilterConfig filterConfig) {
		if (!STARTED.compareAndSet(false, true)) {
			return;
		}
		ApplicationContext context = WebApplicationContextUtils.getWebApplicationContext(filterConfig.getServletContext());
		if (context == null) {
			return;
		}
		WSServer ws = (WSServer) context.getBean("webSocket");
		ws.startSafely();
	}
}
