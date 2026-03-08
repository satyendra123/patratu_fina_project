package com.timmy.entity;

import java.util.LinkedList;
import java.util.Queue;

import org.java_websocket.WebSocket;

public class DeviceStatus {
	private String deviceSn;
	private  WebSocket webSocket;
	private int status;

	
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
	@Override
	public String toString() {
		return "DeviceStatus [deviceSn=" + deviceSn + ", webSocket="
				+ webSocket + ", status=" + status + "]";
	}
	
	

}
