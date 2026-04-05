package com.timmy.job;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.timmy.websocket.WebSocketPool;

public class DirectDeviceKeepaliveScheduler {

	private static final Logger log = LoggerFactory.getLogger(DirectDeviceKeepaliveScheduler.class);
	private static final String ENABLE_PROPERTY = "ws.direct.keepalive.enabled";
	private static final long FIXED_DELAY_MS = 60000L;
	private static final long INITIAL_DELAY_MS = 15000L;
	private static final long SUMMARY_LOG_INTERVAL_MS = 60000L;
	private static final String KEEPALIVE_MESSAGE = "{\"cmd\":\"getdevinfo\"}";

	private volatile long lastSummaryLogAt = 0L;

	@Scheduled(fixedDelay = FIXED_DELAY_MS, initialDelay = INITIAL_DELAY_MS)
	public void sendKeepalive() {
		if (!isEnabled()) {
			return;
		}
		List<String> serials = new ArrayList<String>(WebSocketPool.wsDevice.keySet());
		if (serials.isEmpty()) {
			return;
		}
		int sent = 0;
		for (String serial : serials) {
			if (WebSocketPool.sendMessageToDeviceStatus(serial, KEEPALIVE_MESSAGE)) {
				sent++;
			}
		}
		maybeLogSummary(serials.size(), sent);
	}

	private void maybeLogSummary(int connected, int sent) {
		long now = System.currentTimeMillis();
		if (now - lastSummaryLogAt < SUMMARY_LOG_INTERVAL_MS) {
			return;
		}
		lastSummaryLogAt = now;
		log.info("Direct websocket keepalive active. intervalMs:{} connected:{} sent:{} payload:{}",
				Long.valueOf(FIXED_DELAY_MS), Integer.valueOf(connected), Integer.valueOf(sent), KEEPALIVE_MESSAGE);
	}

	private boolean isEnabled() {
		String raw = System.getProperty(ENABLE_PROPERTY);
		if (raw == null || raw.trim().isEmpty()) {
			return true;
		}
		return "true".equalsIgnoreCase(raw.trim());
	}
}
