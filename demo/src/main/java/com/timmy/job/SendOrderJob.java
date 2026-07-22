package com.timmy.job;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.java_websocket.WebSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timmy.entity.Device;
import com.timmy.entity.DeviceStatus;
import com.timmy.entity.MachineCommand;
import com.timmy.mapper.DeviceMapper;
import com.timmy.mapper.MachineCommandMapper;
import com.timmy.websocket.WebSocketPool;

public class SendOrderJob extends Thread {
	private static final Logger log = LoggerFactory.getLogger(SendOrderJob.class);

	// Keep only risky command types blocked from auto dispatch.
	private static final Set<String> BLOCKED_SYNC_COMMANDS = new HashSet<String>(
			Arrays.asList("opendoor"));
	private static final int MAX_RETRY_COUNT = 3;
	private static final long COMMAND_TIMEOUT_MS = 20 * 1000L;
	private static final Pattern ENROLL_ID_PATTERN = Pattern.compile("\"enrollid\"\\s*:\\s*(\\d+)");
	private static final ObjectMapper COMMAND_OBJECT_MAPPER = new ObjectMapper();

	@Autowired
	MachineCommandMapper machineCommandMapper;

	@Autowired
	DeviceMapper deviceMapper;

	Map<String, DeviceStatus> wdList = WebSocketPool.wsDevice;

	private final AtomicBoolean running = new AtomicBoolean(false);
	private final AtomicBoolean startedOnce = new AtomicBoolean(false);

	public synchronized void startThread() {
		if (running.get()) {
			return;
		}
		running.set(true);
		if (startedOnce.compareAndSet(false, true)) {
			super.start();
		}
	}

	public synchronized void stopThread() {
		running.set(false);
		this.interrupt();
	}

	@Override
	public void run() {
		while (running.get() && !Thread.currentThread().isInterrupted()) {
			Iterator<Entry<String, DeviceStatus>> entries = wdList.entrySet().iterator();
			try {
				while (entries.hasNext()) {
					Entry<String, DeviceStatus> entry = entries.next();
					List<MachineCommand> inSending = machineCommandMapper.findPendingCommand(0, entry.getKey());

					if (inSending.size() > 0) {
						MachineCommand nextToSend = pickNextCommand(inSending);
						if (nextToSend == null) {
							continue;
						}

						List<MachineCommand> pendingCommand = machineCommandMapper.findPendingCommand(1, entry.getKey());
						if (pendingCommand.size() <= 0) {
							WebSocket activeSocket = getActiveSocket(entry);
							if (activeSocket != null) {
								String payload = normalizePayloadForSend(nextToSend);
								if (trySend(entry.getKey(), activeSocket, nextToSend, payload, false)) {
									machineCommandMapper.updateCommandStatus(0, 1, new Date(), nextToSend.getId());
									if ("enableuser".equals(nextToSend.getName()) || "deleteuser".equals(nextToSend.getName())
											|| "setquestionnaire".equals(nextToSend.getName())) {
										log.info("[SEND-ORDER] sent command id={} serial={} name={} payload={}",
												nextToSend.getId(), nextToSend.getSerial(), nextToSend.getName(),
												payload);
									}
								}
							}
						} else {
							MachineCommand pendingToRetry = pickPendingRetry(pendingCommand);
							if (pendingToRetry != null
									&& ("enableuser".equals(pendingToRetry.getName())
											|| "deleteuser".equals(pendingToRetry.getName()))) {
								log.debug("[SEND-ORDER] waiting pending command before next send. id={} serial={} name={} retryCount={}",
										pendingToRetry.getId(), pendingToRetry.getSerial(), pendingToRetry.getName(),
										pendingToRetry.getErrCount());
							}
							retryPendingIfTimedOut(entry, pendingToRetry);
						}
					} else {
						List<MachineCommand> pendingCommand = machineCommandMapper.findPendingCommand(1, entry.getKey());
						if (pendingCommand.size() != 0) {
							MachineCommand pendingToRetry = pickPendingRetry(pendingCommand);
							retryPendingIfTimedOut(entry, pendingToRetry);
						}
					}
				}
			} catch (Exception e) {
				log.warn("[SEND-ORDER] polling loop exception", e);
			}

			try {
				Thread.sleep(100L);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	private void retryPendingIfTimedOut(Entry<String, DeviceStatus> entry, MachineCommand pendingToRetry) {
		if (pendingToRetry == null || pendingToRetry.getRunTime() == null) {
			return;
		}
		if (System.currentTimeMillis() - (pendingToRetry.getRunTime()).getTime() <= COMMAND_TIMEOUT_MS) {
			return;
		}
		WebSocket activeSocket = getActiveSocket(entry);
		if (activeSocket == null) {
			requeuePendingForReconnect(pendingToRetry, "websocket disconnected; waiting for reconnect");
			markDeviceOffline(entry == null ? null : entry.getKey(), "retry-timeout-no-active-websocket", null);
			return;
		}
		int currentErrCount = safeErrCount(pendingToRetry);
		if (currentErrCount < MAX_RETRY_COUNT) {
			String payload = normalizePayloadForSend(pendingToRetry);
			if (trySend(entry == null ? null : entry.getKey(), activeSocket, pendingToRetry, payload, true)) {
				pendingToRetry.setErrCount(Integer.valueOf(currentErrCount + 1));
				pendingToRetry.setRunTime(new Date());
				pendingToRetry.setGmtModified(new Date());
				machineCommandMapper.updateByPrimaryKey(pendingToRetry);
				log.warn("[SEND-ORDER] retry command id={} serial={} name={} retryCount={}",
						pendingToRetry.getId(), pendingToRetry.getSerial(), pendingToRetry.getName(),
						pendingToRetry.getErrCount());
			} else {
				requeuePendingForReconnect(pendingToRetry, "retry send failed; queued for reconnect");
			}
		} else {
			machineCommandMapper.updateCommandStatus(2, 0, new Date(), pendingToRetry.getId());
			log.error(
					"[SEND-ORDER] command marked failed after max retries. id={} serial={} name={} retryCount={} payload={}",
					pendingToRetry.getId(), pendingToRetry.getSerial(), pendingToRetry.getName(),
					pendingToRetry.getErrCount(), pendingToRetry.getContent());
		}
	}

	private MachineCommand pickNextCommand(List<MachineCommand> inSending) {
		if (inSending == null || inSending.isEmpty()) {
			return null;
		}

		Map<String, Integer> latestEnableCmdIdByUser = findLatestEnablePendingByUser(inSending);
		MachineCommand firstPriority = null;
		MachineCommand firstNormal = null;
		MachineCommand latestEnable = null;
		for (int i = 0; i < inSending.size(); i++) {
			MachineCommand cmd = inSending.get(i);
			if (cmd == null) {
				continue;
			}
			if (isBlockedSyncCommand(cmd.getName())) {
				markCommandSkipped(cmd);
				continue;
			}
			if (isStaleEnablePending(cmd, latestEnableCmdIdByUser)) {
				log.info("[SEND-ORDER] skipped stale enableuser command id={} serial={}", cmd.getId(), cmd.getSerial());
				markCommandSkipped(cmd);
				continue;
			}
			if ("setquestionnaire".equals(cmd.getName())) {
				if (firstPriority == null) {
					firstPriority = cmd;
				}
				continue;
			}
			if ("enableuser".equals(cmd.getName())) {
				latestEnable = cmd;
				continue;
			}
			if (firstNormal == null) {
				firstNormal = cmd;
			}
		}
		if (firstPriority != null) {
			return firstPriority;
		}
		return firstNormal != null ? firstNormal : latestEnable;
	}

	private MachineCommand pickPendingRetry(List<MachineCommand> pendingCommands) {
		if (pendingCommands == null || pendingCommands.isEmpty()) {
			return null;
		}
		for (int i = 0; i < pendingCommands.size(); i++) {
			MachineCommand cmd = pendingCommands.get(i);
			if (cmd == null) {
				continue;
			}
			if (isBlockedSyncCommand(cmd.getName())) {
				markCommandSkipped(cmd);
				continue;
			}
			return cmd;
		}
		return null;
	}

	private WebSocket getActiveSocket(Entry<String, DeviceStatus> entry) {
		if (entry == null || entry.getKey() == null) {
			return null;
		}
		return WebSocketPool.getDeviceSocketBySn(entry.getKey());
	}

	private boolean trySend(String serial, WebSocket socket, MachineCommand command, String payload, boolean retry) {
		if (socket == null || payload == null) {
			return false;
		}
		try {
			if (!socket.isOpen() || socket.isClosing() || socket.isClosed()) {
				markDeviceOffline(serial, retry ? "retry-socket-not-open" : "socket-not-open", null);
				return false;
			}
			socket.send(payload);
			return true;
		} catch (RuntimeException ex) {
			markDeviceOffline(serial, retry ? "retry-send-failed" : "send-failed", ex);
			return false;
		}
	}

	private void requeuePendingForReconnect(MachineCommand command, String reason) {
		if (command == null || command.getId() == null) {
			return;
		}
		command.setStatus(Integer.valueOf(0));
		command.setSendStatus(Integer.valueOf(0));
		command.setRunTime(null);
		command.setGmtModified(new Date());
		machineCommandMapper.updateByPrimaryKey(command);
		log.warn("[SEND-ORDER] re-queued command for reconnect. id={} serial={} name={} reason={}",
				command.getId(), command.getSerial(), command.getName(), reason);
	}

	private void markDeviceOffline(String serial, String reason, Throwable cause) {
		if (serial == null || serial.trim().isEmpty()) {
			return;
		}
		WebSocketPool.removeDeviceStatus(serial);
		try {
			Device device = deviceMapper == null ? null : deviceMapper.selectDeviceBySerialNum(serial);
			if (device != null && device.getId() != null && device.getStatus() != 0) {
				deviceMapper.updateStatusByPrimaryKey(device.getId().intValue(), 0);
			}
		} catch (Exception ex) {
			log.warn("[SEND-ORDER] failed to mark device offline. serial={} reason={}", serial, reason, ex);
		}
		if (cause == null) {
			log.warn("[SEND-ORDER] device marked offline. serial={} reason={}", serial, reason);
		} else {
			log.warn("[SEND-ORDER] device marked offline. serial={} reason={}", serial, reason, cause);
		}
	}

	private int safeErrCount(MachineCommand command) {
		return command == null || command.getErrCount() == null ? 0 : command.getErrCount().intValue();
	}

	private boolean isBlockedSyncCommand(String commandName) {
		return commandName != null && BLOCKED_SYNC_COMMANDS.contains(commandName);
	}

	private void markCommandSkipped(MachineCommand command) {
		if (command == null || command.getId() == null) {
			return;
		}
		machineCommandMapper.updateCommandStatus(1, 1, new Date(), command.getId());
	}

	private Map<String, Integer> findLatestEnablePendingByUser(List<MachineCommand> commands) {
		Map<String, Integer> latest = new LinkedHashMap<String, Integer>();
		if (commands == null || commands.isEmpty()) {
			return latest;
		}
		for (int i = 0; i < commands.size(); i++) {
			MachineCommand cmd = commands.get(i);
			if (cmd == null || cmd.getId() == null || !"enableuser".equals(cmd.getName())) {
				continue;
			}
			Long enrollId = extractEnrollId(cmd.getContent());
			if (enrollId == null || cmd.getSerial() == null) {
				continue;
			}
			latest.put(buildEnableKey(cmd.getSerial(), enrollId.longValue()), cmd.getId());
		}
		return latest;
	}

	private boolean isStaleEnablePending(MachineCommand command, Map<String, Integer> latestEnableCmdIdByUser) {
		if (command == null || command.getId() == null || !"enableuser".equals(command.getName())) {
			return false;
		}
		Long enrollId = extractEnrollId(command.getContent());
		if (enrollId == null || command.getSerial() == null) {
			return false;
		}
		Integer latestId = latestEnableCmdIdByUser.get(buildEnableKey(command.getSerial(), enrollId.longValue()));
		return latestId != null && latestId.intValue() != command.getId().intValue();
	}

	private String buildEnableKey(String serial, long enrollId) {
		return serial + "|" + enrollId;
	}

	private String normalizePayloadForSend(MachineCommand command) {
		if (command == null || command.getContent() == null) {
			return command == null ? null : command.getContent();
		}
		if ("setuserinfo".equals(command.getName())) {
			return unwrapSetuserinfoPayload(command.getContent());
		}
		if (!"enableuser".equals(command.getName())) {
			return command.getContent();
		}
		return ensureEnableUserPayloadHasEnrolled(command);
	}

	private String unwrapSetuserinfoPayload(String content) {
		if (content == null || content.trim().isEmpty()) {
			return content;
		}
		try {
			JsonNode node = COMMAND_OBJECT_MAPPER.readTree(content);
			if (node != null && node.has("payload") && node.get("payload") != null && node.get("payload").isObject()) {
				return node.get("payload").toString();
			}
		} catch (Exception ex) {
			log.debug("[SEND-ORDER] failed to unwrap setuserinfo payload; using original content", ex);
		}
		return content;
	}

	private String ensureEnableUserPayloadHasEnrolled(MachineCommand command) {
		String content = command.getContent();
		if (content.indexOf("\"enrolled\"") >= 0) {
			return content;
		}
		Long enrollId = extractEnrollId(content);
		if (enrollId == null) {
			return content;
		}
		String normalized = injectEnrolled(content, enrollId.longValue());
		if (!normalized.equals(content)) {
			log.info("[SEND-ORDER] normalized enableuser payload id={} serial={} enrollid={}",
					command.getId(), command.getSerial(), enrollId);
		}
		return normalized;
	}

	private Long extractEnrollId(String content) {
		Matcher matcher = ENROLL_ID_PATTERN.matcher(content);
		if (!matcher.find()) {
			return null;
		}
		try {
			return Long.valueOf(matcher.group(1));
		} catch (Exception ex) {
			return null;
		}
	}

	private String injectEnrolled(String content, long enrollId) {
		int enflagIndex = content.indexOf("\"enflag\"");
		if (enflagIndex >= 0) {
			return content.substring(0, enflagIndex) + "\"enrolled\":" + enrollId + "," + content.substring(enflagIndex);
		}
		int closeIndex = content.lastIndexOf("}");
		if (closeIndex > 0) {
			return content.substring(0, closeIndex) + ",\"enrolled\":" + enrollId + content.substring(closeIndex);
		}
		return content;
	}
}
