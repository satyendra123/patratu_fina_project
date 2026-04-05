package com.timmy.entity;

import org.java_websocket.WebSocket;

public class DeviceStatus {
	private String deviceSn;
	private WebSocket webSocket;
	private int status;
	private volatile long lastSeenAt = System.currentTimeMillis();

	public WebSocket getWebSocket() {
		return webSocket;
	}

	public void setWebSocket(WebSocket webSocket) {
		this.webSocket = webSocket;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public String getDeviceSn() {
		return deviceSn;
	}

	public void setDeviceSn(String deviceSn) {
		this.deviceSn = deviceSn;
	}

	public long getLastSeenAt() {
		return lastSeenAt;
	}

	public void setLastSeenAt(long lastSeenAt) {
		this.lastSeenAt = lastSeenAt > 0L ? lastSeenAt : System.currentTimeMillis();
	}

	public void touch() {
		this.lastSeenAt = System.currentTimeMillis();
	}

	@Override
	public String toString() {
		return "DeviceStatus [deviceSn=" + deviceSn + ", webSocket=" + webSocket + ", status=" + status
				+ ", lastSeenAt=" + lastSeenAt + "]";
	}
}
