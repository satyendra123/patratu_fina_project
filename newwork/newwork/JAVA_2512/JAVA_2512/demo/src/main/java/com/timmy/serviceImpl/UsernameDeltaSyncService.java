package com.timmy.serviceImpl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.timmy.entity.Device;
import com.timmy.entity.MachineCommand;
import com.timmy.entity.Person;
import com.timmy.service.DeviceService;
import com.timmy.service.PersonService;
import com.timmy.websocket.WebSocketPool;

@Service
public class UsernameDeltaSyncService {

	private static final Logger log = LoggerFactory.getLogger(UsernameDeltaSyncService.class);
	private static final int INSERT_BATCH_SIZE = 500;
	private static final int USERNAME_CHUNK_SIZE = 2000;
	private static final int ESTIMATED_COMMANDS_PER_SECOND_PER_DEVICE = 10;
	private static final String SNAPSHOT_FILE_NAME = "idsl-username-delta-sync-cache.properties";
	private static final String REPORT_FILE_NAME = "idsl-username-delta-sync-report.properties";
	private static final String USER_PREFIX = "U.";
	private static final String USER_NAME_SUFFIX = ".name";
	private static final String USER_STATUS_SUFFIX = ".status";

	private final Object syncLock = new Object();

	@Autowired
	private PersonService personService;

	@Autowired
	private DeviceService deviceService;

	@Autowired
	private DataSource dataSource;

	public SyncResult syncChangedUsersToAllDevices(String trigger) {
		synchronized (syncLock) {
			long startedMs = System.currentTimeMillis();
			SyncResult result = new SyncResult();
			result.setTrigger(trigger);
			result.setStartedAt(new Date(startedMs));
			result.setSnapshotFile(getSnapshotFile().getAbsolutePath());
			result.setReportFile(getReportFile().getAbsolutePath());
			try {
				List<String> serials = getAllKnownDeviceSerials();
				result.setDevices(serials.size());
				result.setOnlineDevices(countOnlineDevices(serials));
				if (serials.isEmpty()) {
					result.setSuccess(false);
					result.setError("No devices found in database.");
					result.setReason("No devices configured in database.");
					return finishAndReport(result, startedMs);
				}

				Snapshot previous = loadSnapshot();
				result.setSnapshotLoaded(previous.loadedFromFile);

				Map<Long, UserState> currentUsers = loadCurrentUsers();
				result.setTotalUsers(currentUsers.size());

				List<UserState> usernameDeltaUsers = new ArrayList<UserState>();
				List<UserState> statusDeltaUsers = new ArrayList<UserState>();
				for (Map.Entry<Long, UserState> entry : currentUsers.entrySet()) {
					Long userId = entry.getKey();
					UserState current = entry.getValue();
					UserState previousState = previous.users.get(userId);
					if (previousState == null || !safeEquals(previousState.name, current.name)) {
						usernameDeltaUsers.add(current);
					}
					if (previousState == null) {
						if (current.status == 0) {
							statusDeltaUsers.add(current);
						}
					} else if (previousState.status != current.status) {
						statusDeltaUsers.add(current);
					}
				}

				QueueStats queueStats = queueDeltaCommands(serials, usernameDeltaUsers, statusDeltaUsers);
				result.setUsernameDeltaUsers(usernameDeltaUsers.size());
				result.setStatusDeltaUsers(statusDeltaUsers.size());
				result.setUsernameCommandsQueued(queueStats.usernameCommandsQueued);
				result.setStatusCommandsQueued(queueStats.statusCommandsQueued);
				result.setTotalCommandsQueued(queueStats.totalCommandsQueued);
				result.setEstimatedDeviceDispatchSeconds(
						estimateDeviceDispatchSeconds(queueStats.maxCommandsQueuedPerDevice));
				result.setDeviceDetails(queueStats.deviceDetails);

				saveSnapshot(currentUsers);

				if (result.getTotalCommandsQueued() == 0) {
					result.setReason("No delta found. Snapshot matches current database users.");
				} else if (result.getOnlineDevices() <= 0) {
					result.setReason("Commands queued in DEVICECMD, but no websocket device is online right now.");
				}
				result.setSuccess(true);
			} catch (Exception ex) {
				result.setSuccess(false);
				result.setError(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
				log.error("[USERNAME-DELTA-SYNC] failed trigger={}, error={}", trigger, result.getError(), ex);
			}
			return finishAndReport(result, startedMs);
		}
	}

	private SyncResult finishAndReport(SyncResult result, long startedMs) {
		long finishedMs = System.currentTimeMillis();
		result.setFinishedAt(new Date(finishedMs));
		result.setDurationMs(finishedMs - startedMs);
		try {
			saveRunReport(result);
		} catch (Exception ex) {
			log.warn("[USERNAME-DELTA-SYNC] failed to write report file {}", result.getReportFile(), ex);
		}
		return result;
	}

	private Map<Long, UserState> loadCurrentUsers() {
		Map<Long, UserState> result = new LinkedHashMap<Long, UserState>();
		List<Person> persons = personService.selectAll();
		for (Person person : persons) {
			if (person == null || person.getId() == null) {
				continue;
			}
			UserState state = new UserState();
			state.userId = person.getId();
			state.name = normalizeName(person.getName());
			state.status = normalizeStatus(person.getStatus());
			result.put(state.userId, state);
		}
		return result;
	}

	private QueueStats queueDeltaCommands(List<String> serials, List<UserState> usernameDeltaUsers,
			List<UserState> statusDeltaUsers) {
		QueueStats stats = new QueueStats();
		List<MachineCommand> batch = new ArrayList<MachineCommand>(INSERT_BATCH_SIZE);
		for (String serial : serials) {
			DeviceSyncDetail detail = new DeviceSyncDetail();
			detail.setSerial(serial);

			if (usernameDeltaUsers != null && !usernameDeltaUsers.isEmpty()) {
				for (int start = 0; start < usernameDeltaUsers.size(); start += USERNAME_CHUNK_SIZE) {
					int end = Math.min(start + USERNAME_CHUNK_SIZE, usernameDeltaUsers.size());
					List<UserState> chunk = usernameDeltaUsers.subList(start, end);
					batch.add(buildSetUsernameCommand(serial, chunk));
					detail.setUsernameRecords(detail.getUsernameRecords() + chunk.size());
					detail.setUsernameCommands(detail.getUsernameCommands() + 1);
					stats.usernameCommandsQueued++;
					stats.totalCommandsQueued++;
					if (batch.size() >= INSERT_BATCH_SIZE) {
						insertBatch(batch);
						batch.clear();
					}
				}
			}

			if (statusDeltaUsers != null && !statusDeltaUsers.isEmpty()) {
				for (UserState state : statusDeltaUsers) {
					batch.add(buildEnableUserCommand(serial, state));
					detail.setStatusRecords(detail.getStatusRecords() + 1);
					detail.setStatusCommands(detail.getStatusCommands() + 1);
					stats.statusCommandsQueued++;
					stats.totalCommandsQueued++;
					if (batch.size() >= INSERT_BATCH_SIZE) {
						insertBatch(batch);
						batch.clear();
					}
				}
			}

			detail.setTotalCommands(detail.getUsernameCommands() + detail.getStatusCommands());
			if (detail.getTotalCommands() > stats.maxCommandsQueuedPerDevice) {
				stats.maxCommandsQueuedPerDevice = detail.getTotalCommands();
			}
			stats.deviceDetails.add(detail);
		}
		if (!batch.isEmpty()) {
			insertBatch(batch);
		}
		return stats;
	}

	private MachineCommand buildSetUsernameCommand(String serial, List<UserState> users) {
		MachineCommand command = new MachineCommand();
		Date now = new Date();
		command.setSerial(serial);
		command.setName("setusername");
		command.setContent(buildSetUsernamePayload(users));
		command.setStatus(0);
		command.setSendStatus(0);
		command.setErrCount(0);
		command.setGmtCrate(now);
		command.setGmtModified(now);
		return command;
	}

	private MachineCommand buildEnableUserCommand(String serial, UserState state) {
		MachineCommand command = new MachineCommand();
		Date now = new Date();
		command.setSerial(serial);
		command.setName("enableuser");
		command.setContent("{\"cmd\":\"enableuser\",\"enrollid\":" + state.userId + ",\"enrolled\":"
				+ state.userId + ",\"enflag\":" + state.status + "}");
		command.setStatus(0);
		command.setSendStatus(0);
		command.setErrCount(0);
		command.setGmtCrate(now);
		command.setGmtModified(now);
		return command;
	}

	private String buildSetUsernamePayload(List<UserState> users) {
		StringBuilder sb = new StringBuilder();
		sb.append("{\"cmd\":\"setusername\",\"count\":").append(users.size()).append(",\"record\":[");
		for (int i = 0; i < users.size(); i++) {
			UserState user = users.get(i);
			if (i > 0) {
				sb.append(",");
			}
			sb.append("{\"enrollid\":").append(user.userId).append(",\"name\":\"")
					.append(escapeJson(user.name)).append("\"}");
		}
		sb.append("]}");
		return sb.toString();
	}

	private int insertBatch(final List<MachineCommand> commands) {
		if (commands == null || commands.isEmpty()) {
			return 0;
		}
		final String sql = "insert into DEVICECMD (SLNO, DC_CMD, DC_DATE, DC_EXECDATE, DC_RES, DC_RESDATE, CMD_DESC, REF_ID, src_sno, DC_cmd_date, IS_DEL_EXECUTED) "
				+ "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		int[] rows = jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps, int i) throws SQLException {
				MachineCommand cmd = commands.get(i);
				Timestamp createdAt = new Timestamp(
						cmd.getGmtCrate() == null ? System.currentTimeMillis() : cmd.getGmtCrate().getTime());
				Timestamp modifiedAt = new Timestamp(
						cmd.getGmtModified() == null ? createdAt.getTime() : cmd.getGmtModified().getTime());
				ps.setString(1, cmd.getSerial());
				ps.setString(2, cmd.getContent());
				ps.setTimestamp(3, createdAt);
				ps.setNull(4, Types.TIMESTAMP);
				ps.setString(5, "0");
				ps.setNull(6, Types.TIMESTAMP);
				ps.setString(7, "JAVA:" + (cmd.getName() == null ? "command" : cmd.getName()));
				ps.setInt(8, cmd.getErrCount() == null ? 0 : cmd.getErrCount().intValue());
				ps.setNull(9, Types.VARCHAR);
				ps.setTimestamp(10, modifiedAt);
				ps.setInt(11, 0);
			}

			@Override
			public int getBatchSize() {
				return commands.size();
			}
		});
		return rows == null ? 0 : rows.length;
	}

	private Snapshot loadSnapshot() {
		Snapshot snapshot = new Snapshot();
		File file = getSnapshotFile();
		if (!file.exists()) {
			return snapshot;
		}
		snapshot.loadedFromFile = true;
		Properties properties = new Properties();
		FileInputStream input = null;
		try {
			input = new FileInputStream(file);
			properties.load(input);
			for (String key : properties.stringPropertyNames()) {
				if (!key.startsWith(USER_PREFIX)) {
					continue;
				}
				if (key.endsWith(USER_NAME_SUFFIX)) {
					Long userId = parseUserId(key, USER_NAME_SUFFIX);
					if (userId == null) {
						continue;
					}
					UserState state = ensureUser(snapshot.users, userId.longValue());
					state.name = normalizeName(properties.getProperty(key));
				} else if (key.endsWith(USER_STATUS_SUFFIX)) {
					Long userId = parseUserId(key, USER_STATUS_SUFFIX);
					if (userId == null) {
						continue;
					}
					UserState state = ensureUser(snapshot.users, userId.longValue());
					state.status = parseStatus(properties.getProperty(key));
				}
			}
		} catch (Exception ex) {
			snapshot.users.clear();
			log.warn("[USERNAME-DELTA-SYNC] failed to load snapshot file {}", file.getAbsolutePath(), ex);
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (Exception ignore) {
				}
			}
		}
		return snapshot;
	}

	private void saveSnapshot(Map<Long, UserState> users) throws Exception {
		File file = getSnapshotFile();
		File parent = file.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}
		Properties properties = new Properties();
		for (Map.Entry<Long, UserState> entry : users.entrySet()) {
			Long userId = entry.getKey();
			UserState state = entry.getValue();
			properties.setProperty(USER_PREFIX + userId + USER_NAME_SUFFIX, normalizeName(state.name));
			properties.setProperty(USER_PREFIX + userId + USER_STATUS_SUFFIX, String.valueOf(state.status));
		}
		properties.setProperty("meta.updatedAt", String.valueOf(System.currentTimeMillis()));

		FileOutputStream output = null;
		try {
			output = new FileOutputStream(file);
			properties.store(output, "IDSL Username Delta Snapshot");
		} finally {
			if (output != null) {
				try {
					output.close();
				} catch (Exception ignore) {
				}
			}
		}
	}

	private void saveRunReport(SyncResult result) throws Exception {
		File file = getReportFile();
		File parent = file.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}
		Properties properties = new Properties();
		properties.setProperty("meta.updatedAt", String.valueOf(System.currentTimeMillis()));
		properties.setProperty("run.trigger", valueOrEmpty(result.getTrigger()));
		properties.setProperty("run.success", String.valueOf(result.isSuccess()));
		properties.setProperty("run.error", valueOrEmpty(result.getError()));
		properties.setProperty("run.reason", valueOrEmpty(result.getReason()));
		properties.setProperty("run.snapshotFile", valueOrEmpty(result.getSnapshotFile()));
		properties.setProperty("run.reportFile", valueOrEmpty(result.getReportFile()));
		properties.setProperty("run.startedAt", result.getStartedAt() == null ? "" : String.valueOf(result.getStartedAt().getTime()));
		properties.setProperty("run.finishedAt", result.getFinishedAt() == null ? "" : String.valueOf(result.getFinishedAt().getTime()));
		properties.setProperty("run.durationMs", String.valueOf(result.getDurationMs()));
		properties.setProperty("run.devices", String.valueOf(result.getDevices()));
		properties.setProperty("run.onlineDevices", String.valueOf(result.getOnlineDevices()));
		properties.setProperty("run.totalUsers", String.valueOf(result.getTotalUsers()));
		properties.setProperty("run.usernameDeltaUsers", String.valueOf(result.getUsernameDeltaUsers()));
		properties.setProperty("run.statusDeltaUsers", String.valueOf(result.getStatusDeltaUsers()));
		properties.setProperty("run.usernameCommandsQueued", String.valueOf(result.getUsernameCommandsQueued()));
		properties.setProperty("run.statusCommandsQueued", String.valueOf(result.getStatusCommandsQueued()));
		properties.setProperty("run.totalCommandsQueued", String.valueOf(result.getTotalCommandsQueued()));
		properties.setProperty("run.estimatedDeviceDispatchSeconds",
				String.valueOf(result.getEstimatedDeviceDispatchSeconds()));

		List<DeviceSyncDetail> details = result.getDeviceDetails();
		properties.setProperty("run.deviceCount", String.valueOf(details == null ? 0 : details.size()));
		if (details != null) {
			for (int i = 0; i < details.size(); i++) {
				DeviceSyncDetail detail = details.get(i);
				String prefix = "device." + i + ".";
				properties.setProperty(prefix + "serial", valueOrEmpty(detail.getSerial()));
				properties.setProperty(prefix + "usernameRecords", String.valueOf(detail.getUsernameRecords()));
				properties.setProperty(prefix + "statusRecords", String.valueOf(detail.getStatusRecords()));
				properties.setProperty(prefix + "usernameCommands", String.valueOf(detail.getUsernameCommands()));
				properties.setProperty(prefix + "statusCommands", String.valueOf(detail.getStatusCommands()));
				properties.setProperty(prefix + "totalCommands", String.valueOf(detail.getTotalCommands()));
			}
		}

		FileOutputStream output = null;
		try {
			output = new FileOutputStream(file);
			properties.store(output, "IDSL Username Delta Sync Report");
		} finally {
			if (output != null) {
				try {
					output.close();
				} catch (Exception ignore) {
				}
			}
		}
	}

	private int countOnlineDevices(List<String> serials) {
		if (serials == null || serials.isEmpty()) {
			return 0;
		}
		int online = 0;
		for (String serial : serials) {
			if (isDeviceOnline(serial)) {
				online++;
			}
		}
		return online;
	}

	private boolean isDeviceOnline(String serial) {
		if (!hasText(serial)) {
			return false;
		}
		if (WebSocketPool.getDeviceSocketBySn(serial) != null) {
			return true;
		}
		String normalized = serial.trim();
		for (String connectedSn : WebSocketPool.wsDevice.keySet()) {
			if (connectedSn != null && normalized.equalsIgnoreCase(connectedSn.trim())
					&& WebSocketPool.getDeviceSocketBySn(connectedSn) != null) {
				return true;
			}
		}
		return false;
	}

	private List<String> getAllKnownDeviceSerials() {
		Set<String> serials = new LinkedHashSet<String>();
		List<Device> devices = deviceService.findAllDevice();
		for (Device device : devices) {
			if (device != null && hasText(device.getSerialNum())) {
				serials.add(device.getSerialNum().trim());
			}
		}
		return new ArrayList<String>(serials);
	}

	private long estimateDeviceDispatchSeconds(int maxCommandsQueuedPerDevice) {
		if (maxCommandsQueuedPerDevice <= 0) {
			return 0L;
		}
		return (long) Math.ceil(
				(double) maxCommandsQueuedPerDevice / (double) ESTIMATED_COMMANDS_PER_SECOND_PER_DEVICE);
	}

	private File getSnapshotFile() {
		String tempDir = System.getProperty("java.io.tmpdir");
		File dir = new File(tempDir, "idsl-sync");
		return new File(dir, SNAPSHOT_FILE_NAME);
	}

	private File getReportFile() {
		String tempDir = System.getProperty("java.io.tmpdir");
		File dir = new File(tempDir, "idsl-sync");
		return new File(dir, REPORT_FILE_NAME);
	}

	private Long parseUserId(String key, String suffix) {
		if (key == null || suffix == null) {
			return null;
		}
		if (key.length() <= USER_PREFIX.length() + suffix.length()) {
			return null;
		}
		String userPart = key.substring(USER_PREFIX.length(), key.length() - suffix.length());
		try {
			return Long.valueOf(userPart);
		} catch (Exception ex) {
			return null;
		}
	}

	private int parseStatus(String value) {
		try {
			int status = Integer.parseInt(value);
			return status == 0 ? 0 : 1;
		} catch (Exception ex) {
			return 1;
		}
	}

	private UserState ensureUser(Map<Long, UserState> users, long userId) {
		UserState state = users.get(Long.valueOf(userId));
		if (state == null) {
			state = new UserState();
			state.userId = Long.valueOf(userId);
			state.name = "";
			state.status = 1;
			users.put(Long.valueOf(userId), state);
		}
		return state;
	}

	private int normalizeStatus(Integer status) {
		return (status != null && status.intValue() == 0) ? 0 : 1;
	}

	private String normalizeName(String name) {
		return name == null ? "" : name;
	}

	private String valueOrEmpty(String value) {
		return value == null ? "" : value;
	}

	private boolean safeEquals(String left, String right) {
		if (left == null && right == null) {
			return true;
		}
		if (left == null || right == null) {
			return false;
		}
		return left.equals(right);
	}

	private boolean hasText(String text) {
		return text != null && text.trim().length() > 0;
	}

	private String escapeJson(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder out = new StringBuilder(value.length() + 16);
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			switch (ch) {
			case '\\':
				out.append("\\\\");
				break;
			case '"':
				out.append("\\\"");
				break;
			case '\b':
				out.append("\\b");
				break;
			case '\f':
				out.append("\\f");
				break;
			case '\n':
				out.append("\\n");
				break;
			case '\r':
				out.append("\\r");
				break;
			case '\t':
				out.append("\\t");
				break;
			default:
				if (ch < 0x20) {
					String hex = Integer.toHexString(ch);
					out.append("\\u");
					for (int j = hex.length(); j < 4; j++) {
						out.append('0');
					}
					out.append(hex);
				} else {
					out.append(ch);
				}
				break;
			}
		}
		return out.toString();
	}

	private static final class Snapshot {
		private boolean loadedFromFile;
		private final Map<Long, UserState> users = new LinkedHashMap<Long, UserState>();
	}

	private static final class UserState {
		private Long userId;
		private String name;
		private int status;
	}

	private static final class QueueStats {
		private int usernameCommandsQueued;
		private int statusCommandsQueued;
		private int totalCommandsQueued;
		private int maxCommandsQueuedPerDevice;
		private final List<DeviceSyncDetail> deviceDetails = new ArrayList<DeviceSyncDetail>();
	}

	public static class DeviceSyncDetail {
		private String serial;
		private int usernameRecords;
		private int statusRecords;
		private int usernameCommands;
		private int statusCommands;
		private int totalCommands;

		public String getSerial() {
			return serial;
		}

		public void setSerial(String serial) {
			this.serial = serial;
		}

		public int getUsernameRecords() {
			return usernameRecords;
		}

		public void setUsernameRecords(int usernameRecords) {
			this.usernameRecords = usernameRecords;
		}

		public int getStatusRecords() {
			return statusRecords;
		}

		public void setStatusRecords(int statusRecords) {
			this.statusRecords = statusRecords;
		}

		public int getUsernameCommands() {
			return usernameCommands;
		}

		public void setUsernameCommands(int usernameCommands) {
			this.usernameCommands = usernameCommands;
		}

		public int getStatusCommands() {
			return statusCommands;
		}

		public void setStatusCommands(int statusCommands) {
			this.statusCommands = statusCommands;
		}

		public int getTotalCommands() {
			return totalCommands;
		}

		public void setTotalCommands(int totalCommands) {
			this.totalCommands = totalCommands;
		}
	}

	public static class SyncResult {
		private boolean success;
		private String trigger;
		private String error;
		private String reason;
		private String snapshotFile;
		private String reportFile;
		private int devices;
		private int onlineDevices;
		private int totalUsers;
		private int usernameDeltaUsers;
		private int statusDeltaUsers;
		private int usernameCommandsQueued;
		private int statusCommandsQueued;
		private int totalCommandsQueued;
		private long estimatedDeviceDispatchSeconds;
		private long durationMs;
		private boolean snapshotLoaded;
		private Date startedAt;
		private Date finishedAt;
		private List<DeviceSyncDetail> deviceDetails = new ArrayList<DeviceSyncDetail>();

		public boolean isSuccess() {
			return success;
		}

		public void setSuccess(boolean success) {
			this.success = success;
		}

		public String getTrigger() {
			return trigger;
		}

		public void setTrigger(String trigger) {
			this.trigger = trigger;
		}

		public String getError() {
			return error;
		}

		public void setError(String error) {
			this.error = error;
		}

		public String getReason() {
			return reason;
		}

		public void setReason(String reason) {
			this.reason = reason;
		}

		public String getSnapshotFile() {
			return snapshotFile;
		}

		public void setSnapshotFile(String snapshotFile) {
			this.snapshotFile = snapshotFile;
		}

		public String getReportFile() {
			return reportFile;
		}

		public void setReportFile(String reportFile) {
			this.reportFile = reportFile;
		}

		public int getDevices() {
			return devices;
		}

		public void setDevices(int devices) {
			this.devices = devices;
		}

		public int getOnlineDevices() {
			return onlineDevices;
		}

		public void setOnlineDevices(int onlineDevices) {
			this.onlineDevices = onlineDevices;
		}

		public int getTotalUsers() {
			return totalUsers;
		}

		public void setTotalUsers(int totalUsers) {
			this.totalUsers = totalUsers;
		}

		public int getUsernameDeltaUsers() {
			return usernameDeltaUsers;
		}

		public void setUsernameDeltaUsers(int usernameDeltaUsers) {
			this.usernameDeltaUsers = usernameDeltaUsers;
		}

		public int getStatusDeltaUsers() {
			return statusDeltaUsers;
		}

		public void setStatusDeltaUsers(int statusDeltaUsers) {
			this.statusDeltaUsers = statusDeltaUsers;
		}

		public int getUsernameCommandsQueued() {
			return usernameCommandsQueued;
		}

		public void setUsernameCommandsQueued(int usernameCommandsQueued) {
			this.usernameCommandsQueued = usernameCommandsQueued;
		}

		public int getStatusCommandsQueued() {
			return statusCommandsQueued;
		}

		public void setStatusCommandsQueued(int statusCommandsQueued) {
			this.statusCommandsQueued = statusCommandsQueued;
		}

		public int getTotalCommandsQueued() {
			return totalCommandsQueued;
		}

		public void setTotalCommandsQueued(int totalCommandsQueued) {
			this.totalCommandsQueued = totalCommandsQueued;
		}

		public long getEstimatedDeviceDispatchSeconds() {
			return estimatedDeviceDispatchSeconds;
		}

		public void setEstimatedDeviceDispatchSeconds(long estimatedDeviceDispatchSeconds) {
			this.estimatedDeviceDispatchSeconds = estimatedDeviceDispatchSeconds;
		}

		public long getDurationMs() {
			return durationMs;
		}

		public void setDurationMs(long durationMs) {
			this.durationMs = durationMs;
		}

		public boolean isSnapshotLoaded() {
			return snapshotLoaded;
		}

		public void setSnapshotLoaded(boolean snapshotLoaded) {
			this.snapshotLoaded = snapshotLoaded;
		}

		public Date getStartedAt() {
			return startedAt;
		}

		public void setStartedAt(Date startedAt) {
			this.startedAt = startedAt;
		}

		public Date getFinishedAt() {
			return finishedAt;
		}

		public void setFinishedAt(Date finishedAt) {
			this.finishedAt = finishedAt;
		}

		public List<DeviceSyncDetail> getDeviceDetails() {
			return deviceDetails;
		}

		public void setDeviceDetails(List<DeviceSyncDetail> deviceDetails) {
			this.deviceDetails = deviceDetails == null ? new ArrayList<DeviceSyncDetail>() : deviceDetails;
		}
	}

}
