package com.timmy.websocket;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.java_websocket.WebSocket;

import com.timmy.entity.DeviceStatus;

public class WebSocketPool {

	public static final Map<String, DeviceStatus> wsDevice = new ConcurrentHashMap<String, DeviceStatus>();

	private static String normalizeSn(String sn) {
		if (sn == null) {
			return null;
		}
		String normalized = sn.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	public static WebSocket getDeviceSocketBySn(String deviceSn) {
		String normalizedSn = normalizeSn(deviceSn);
		DeviceStatus deviceStatus = normalizedSn == null ? null : wsDevice.get(normalizedSn);
		return deviceStatus == null ? null : deviceStatus.getWebSocket();
	}

	public static void addDeviceAndStatus(String deviceSn, DeviceStatus deviceStatus) {
		String normalizedSn = normalizeSn(deviceSn);
		if (normalizedSn != null && deviceStatus != null) {
			wsDevice.put(normalizedSn, deviceStatus);
		}
	}

	public static void sendMessageToDeviceStatus(String sn, String message) {
		WebSocket conn = getDeviceSocketBySn(sn);
		if (conn != null) {
			conn.send(message);
		}
	}

	public static boolean removeDeviceStatus(String sn) {
		String normalizedSn = normalizeSn(sn);
		if (normalizedSn == null) {
			return false;
		}
		return wsDevice.remove(normalizedSn) != null;
	}

	public static String removeDeviceByWebsocket(WebSocket webSocket) {
		if (webSocket == null) {
			return null;
		}
		for (Entry<String, DeviceStatus> entry : wsDevice.entrySet()) {
			DeviceStatus status = entry.getValue();
			if (status != null && status.getWebSocket() == webSocket) {
				wsDevice.remove(entry.getKey(), status);
				return status.getDeviceSn();
			}
		}
		return null;
	}

	public static String getSerialNumber(WebSocket webSocket) {
		if (webSocket == null) {
			return null;
		}
		Collection<DeviceStatus> deviceStatuses = wsDevice.values();
		for (DeviceStatus status : deviceStatuses) {
			if (status != null && status.getWebSocket() == webSocket) {
				wsDevice.remove(normalizeSn(status.getDeviceSn()));
				return status.getDeviceSn();
			}
		}
		return null;
	}

	public static DeviceStatus getDeviceStatus(String sn) {
		String normalizedSn = normalizeSn(sn);
		return normalizedSn == null ? null : wsDevice.get(normalizedSn);
	}

	public static void sendMessageToAllDeviceFree(String message) {
		Collection<DeviceStatus> deviceStatuses = wsDevice.values();
		synchronized (deviceStatuses) {
			for (DeviceStatus status : deviceStatuses) {
				if (status != null && status.getWebSocket() != null) {
					status.getWebSocket().send(message);
				}
			}
		}
	}
}
