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

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
							if (entry.getValue() != null && entry.getValue().getWebSocket() != null) {
								String payload = normalizePayloadForSend(nextToSend);
								entry.getValue().getWebSocket().send(payload);
								machineCommandMapper.updateCommandStatus(0, 1, new Date(), nextToSend.getId());
								if ("enableuser".equals(nextToSend.getName()) || "deleteuser".equals(nextToSend.getName())
										|| "setquestionnaire".equals(nextToSend.getName())) {
									log.info("[SEND-ORDER] sent command id={} serial={} name={} payload={}",
											nextToSend.getId(), nextToSend.getSerial(), nextToSend.getName(),
											payload);
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
				// keep polling
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
		if (pendingToRetry.getErrCount() < MAX_RETRY_COUNT) {
			pendingToRetry.setErrCount(pendingToRetry.getErrCount() + 1);
			pendingToRetry.setRunTime(new Date());
			machineCommandMapper.updateByPrimaryKey(pendingToRetry);
			Device device = deviceMapper.selectDeviceBySerialNum(pendingToRetry.getSerial());
			if (device != null && device.getStatus() != 0 && entry.getValue() != null
					&& entry.getValue().getWebSocket() != null) {
				String payload = normalizePayloadForSend(pendingToRetry);
				entry.getValue().getWebSocket().send(payload);
				log.warn("[SEND-ORDER] retry command id={} serial={} name={} retryCount={}",
						pendingToRetry.getId(), pendingToRetry.getSerial(), pendingToRetry.getName(),
						pendingToRetry.getErrCount());
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
		if (!"enableuser".equals(command.getName())) {
			return command.getContent();
		}
		return ensureEnableUserPayloadHasEnrolled(command);
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
