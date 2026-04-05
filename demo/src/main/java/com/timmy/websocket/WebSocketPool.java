package com.timmy.websocket;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.timmy.entity.DeviceStatus;

public class WebSocketPool {

	private static final Logger log = LoggerFactory.getLogger(WebSocketPool.class);
	// Keep device sockets usable even when they are idle. Actual disconnects are
	// handled by close/error callbacks and send failures.
	private static final long STALE_CONNECTION_TTL_MS = 0L;

	public static final Map<String, DeviceStatus> wsDevice = new ConcurrentHashMap<String, DeviceStatus>();

	private static String normalizeSn(String sn) {
		if (sn == null) {
			return null;
		}
		String normalized = sn.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	public static WebSocket getDeviceSocketBySn(String deviceSn) {
		DeviceStatus deviceStatus = getDeviceStatus(deviceSn);
		return deviceStatus == null ? null : deviceStatus.getWebSocket();
	}

	public static void addDeviceAndStatus(String deviceSn, DeviceStatus deviceStatus) {
		String normalizedSn = normalizeSn(deviceSn);
		if (normalizedSn != null && deviceStatus != null) {
			deviceStatus.setDeviceSn(normalizedSn);
			deviceStatus.touch();
			WebSocket webSocket = deviceStatus.getWebSocket();
			if (webSocket != null) {
				try {
					webSocket.setAttachment(normalizedSn);
				} catch (Exception ignore) {
				}
			}
			wsDevice.put(normalizedSn, deviceStatus);
		}
	}

	public static void touchDevice(String sn) {
		DeviceStatus deviceStatus = getDeviceStatus(sn);
		if (deviceStatus != null) {
			deviceStatus.touch();
		}
	}

	public static String touchDeviceByWebsocket(WebSocket webSocket) {
		String sn = getSerialNumber(webSocket);
		if (sn == null) {
			return null;
		}
		DeviceStatus deviceStatus = wsDevice.get(sn);
		if (deviceStatus != null) {
			deviceStatus.touch();
		}
		return sn;
	}

	public static boolean sendMessageToDeviceStatus(String sn, String message) {
		DeviceStatus deviceStatus = getDeviceStatus(sn);
		if (deviceStatus == null || message == null) {
			return false;
		}
		try {
			deviceStatus.getWebSocket().send(message);
			deviceStatus.touch();
			return true;
		} catch (RuntimeException ex) {
			String normalizedSn = normalizeSn(sn);
			cleanupStaleStatus(normalizedSn, deviceStatus, "send-failed");
			log.warn("Failed sending websocket message. sn:{} remote:{}", normalizedSn,
					safeRemoteAddress(deviceStatus.getWebSocket()), ex);
			return false;
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
		String sn = getSerialNumber(webSocket);
		if (sn == null) {
			return null;
		}
		DeviceStatus status = wsDevice.get(sn);
		if (status != null && status.getWebSocket() == webSocket) {
			wsDevice.remove(sn, status);
			return sn;
		}
		return null;
	}

	public static String getSerialNumber(WebSocket webSocket) {
		if (webSocket == null) {
			return null;
		}
		try {
			Object attachment = webSocket.getAttachment();
			if (attachment instanceof String) {
				String normalizedSn = normalizeSn((String) attachment);
				if (normalizedSn != null) {
					return normalizedSn;
				}
			}
		} catch (Exception ignore) {
		}
		for (Entry<String, DeviceStatus> entry : wsDevice.entrySet()) {
			DeviceStatus status = entry.getValue();
			if (status != null && status.getWebSocket() == webSocket) {
				return entry.getKey();
			}
		}
		return null;
	}

	public static DeviceStatus getDeviceStatus(String sn) {
		String normalizedSn = normalizeSn(sn);
		if (normalizedSn == null) {
			return null;
		}
		DeviceStatus deviceStatus = wsDevice.get(normalizedSn);
		if (!isStatusUsable(normalizedSn, deviceStatus)) {
			return null;
		}
		return deviceStatus;
	}

	public static void sendMessageToAllDeviceFree(String message) {
		Collection<String> serials = wsDevice.keySet();
		synchronized (serials) {
			for (String serial : serials) {
				sendMessageToDeviceStatus(serial, message);
			}
		}
	}

	private static boolean isStatusUsable(String normalizedSn, DeviceStatus deviceStatus) {
		if (deviceStatus == null) {
			return false;
		}
		if (!isSocketUsable(deviceStatus.getWebSocket())) {
			cleanupStaleStatus(normalizedSn, deviceStatus, "socket-not-open");
			return false;
		}
		if (!isHeartbeatFresh(deviceStatus)) {
			cleanupStaleStatus(normalizedSn, deviceStatus, "heartbeat-timeout");
			return false;
		}
		return true;
	}

	private static boolean isSocketUsable(WebSocket webSocket) {
		return webSocket != null && webSocket.isOpen() && !webSocket.isClosing() && !webSocket.isClosed();
	}

	private static boolean isHeartbeatFresh(DeviceStatus deviceStatus) {
		if (STALE_CONNECTION_TTL_MS <= 0L) {
			return true;
		}
		long lastSeenAt = deviceStatus.getLastSeenAt();
		if (lastSeenAt <= 0L) {
			return true;
		}
		return System.currentTimeMillis() - lastSeenAt <= STALE_CONNECTION_TTL_MS;
	}

	private static void cleanupStaleStatus(String normalizedSn, DeviceStatus deviceStatus, String reason) {
		if (normalizedSn == null || deviceStatus == null) {
			return;
		}
		if (wsDevice.remove(normalizedSn, deviceStatus)) {
			log.warn("Removed stale websocket mapping. sn:{} reason:{} lastSeenAt:{} remote:{}", normalizedSn, reason,
					Long.valueOf(deviceStatus.getLastSeenAt()), safeRemoteAddress(deviceStatus.getWebSocket()));
		}
	}

	private static Object safeRemoteAddress(WebSocket webSocket) {
		if (webSocket == null) {
			return null;
		}
		try {
			return webSocket.getRemoteSocketAddress();
		} catch (Exception ex) {
			return null;
		}
	}
}
