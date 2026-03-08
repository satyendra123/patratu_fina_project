package com.timmy.util;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.timmy.job.SendOrderJob;
import com.timmy.websocket.WSServer;

public class InitializationListener implements ServletContextListener {

	private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		if (!INITIALIZED.compareAndSet(false, true)) {
			return;
		}
		ApplicationContext act = WebApplicationContextUtils.getWebApplicationContext(sce.getServletContext());
		if (act == null) {
			INITIALIZED.set(false);
			return;
		}
		if (act.containsBean("webSocket")) {
			WSServer wsServer = (WSServer) act.getBean("webSocket");
			wsServer.startSafely();
		}
		if (act.containsBean("sendOrderJob")) {
			SendOrderJob sendOrderJob = (SendOrderJob) act.getBean("sendOrderJob");
			sendOrderJob.startThread();
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		if (!INITIALIZED.compareAndSet(true, false)) {
			return;
		}
		ApplicationContext act = WebApplicationContextUtils.getWebApplicationContext(sce.getServletContext());
		if (act == null) {
			return;
		}
		if (act.containsBean("webSocket")) {
			WSServer wsServer = (WSServer) act.getBean("webSocket");
			try {
				wsServer.stopSafely();
			} catch (Exception e) {
				// ignore stop exceptions during container shutdown
			}
		}
		if (act.containsBean("sendOrderJob")) {
			SendOrderJob sendOrderJob = (SendOrderJob) act.getBean("sendOrderJob");
			sendOrderJob.stopThread();
		}
	}

}
