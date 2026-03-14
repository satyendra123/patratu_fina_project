package com.timmy.serviceImpl;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.timmy.entity.Device;
import com.timmy.entity.EnrollInfo;
import com.timmy.entity.MachineCommand;
import com.timmy.entity.Person;
import com.timmy.service.DeviceService;
import com.timmy.service.EnrollInfoService;
import com.timmy.service.PersonService;

@Service
public class DeviceDeltaQueueSyncService {

	private static final Logger log = LoggerFactory.getLogger(DeviceDeltaQueueSyncService.class);
	private static final int INSERT_BATCH_SIZE = 500;
	// Keep admin sync disabled to avoid forcing manager verification on device settings.
	private static final boolean ALLOW_DEVICE_ADMIN_SYNC = false;

	private static final String QUEUE_FILE_PROPERTY = "idsl.sync.queue.file";
	private static final String STATE_DIR_PROPERTY = "idsl.sync.state.dir";
	private static final String PROCESS_NEW_USERS_PROPERTY = "device.delta.sync.process.new.users";
	private static final String DEFAULT_STATE_DIR_NAME = "idsl-sync-state";
	private static final String QUEUE_FILE_NAME = "device_delta_queue.json";
	private static final String LAST_RUN_FILE_NAME = "device_delta_java_last_run.json";

	private final Object syncLock = new Object();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private DeviceService deviceService;

	@Autowired
	private PersonService personService;

	@Autowired
	private EnrollInfoService enrollInfoService;

	@Autowired
	private DataSource dataSource;

	public SyncResult syncFromQueueFile(String trigger) {
		synchronized (syncLock) {
			long startedMs = System.currentTimeMillis();
			SyncResult result = new SyncResult();
			result.setTrigger(trigger);
			result.setStartedAt(new Date(startedMs));

			try {
				File queueFile = resolveQueueFile();
				result.setQueueFile(queueFile.getAbsolutePath());
				result.setReportFile(getReportFile(queueFile).getAbsolutePath());
				result.setNewUsersProcessingEnabled(isProcessNewUsersEnabled());
				int retriedCommands = requeueFailedDisableDeleteCommands();
				result.setRetriedCommandsQueued(retriedCommands);

				DeltaQueue queue = readQueue(queueFile);
				result.setNewUsersRequested(queue.newUserIds.size());
				result.setDisabledUsersRequested(queue.disabledUserIds.size());
				result.setDeletedUsersRequested(queue.deletedUserIds.size());

				if (queue.isEmpty()) {
					result.setSuccess(true);
					if (retriedCommands > 0) {
						result.setReason("Queue empty. Re-queued failed disable/delete commands=" + retriedCommands + ".");
					} else {
						result.setReason("Queue file is empty. Nothing to sync.");
					}
					return finishAndReport(result, startedMs);
				}

				List<String> serials = getAllKnownDeviceSerials();
				result.setDevices(serials.size());
				if (serials.isEmpty()) {
					result.setSuccess(false);
					result.setError("No devices found in DEVICEINFO.");
					return finishAndReport(result, startedMs);
				}

				Set<Long> processedNew = new LinkedHashSet<Long>();
				Set<Long> processedDisabled = new LinkedHashSet<Long>();
				Set<Long> processedDeleted = new LinkedHashSet<Long>();

				int addCommands = 0;
				if (result.isNewUsersProcessingEnabled()) {
					addCommands = queueNewUsersToAllDevices(queue.newUserIds, serials, processedNew, result);
				} else {
					processedNew.addAll(queue.newUserIds);
					result.setNewUsersSkipped(queue.newUserIds.size());
				}
				int disableCommands = queueDisableUsersToAllDevices(queue.disabledUserIds, serials, processedDisabled);
				int deleteCommands = queueDeleteUsersToAllDevices(queue.deletedUserIds, serials, processedDeleted);

				result.setAddCommandsQueued(addCommands);
				result.setDisableCommandsQueued(disableCommands);
				result.setDeleteCommandsQueued(deleteCommands);
				result.setTotalCommandsQueued(addCommands + disableCommands + deleteCommands + retriedCommands);

				result.setNewUsersProcessed(processedNew.size());
				result.setDisabledUsersProcessed(processedDisabled.size());
				result.setDeletedUsersProcessed(processedDeleted.size());

				QueueFileState after = removeProcessedIdsFromQueue(queueFile, processedNew, processedDisabled, processedDeleted);
				result.setQueueNewUsersRemaining(after.newUsersRemaining);
				result.setQueueDisabledUsersRemaining(after.disabledUsersRemaining);
				result.setQueueDeletedUsersRemaining(after.deletedUsersRemaining);

				result.setSuccess(true);
				if (result.getTotalCommandsQueued() == 0) {
					result.setReason("Delta queue was read, but no commands were queued (users may be missing data).");
				} else if (retriedCommands > 0) {
					result.setReason("Delta queue processed and failed disable/delete commands retried.");
				}
			} catch (Exception ex) {
				result.setSuccess(false);
				result.setError(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
				log.error("[FILE-DELTA-SYNC] failed trigger={}, error={}", trigger, result.getError(), ex);
			}
			return finishAndReport(result, startedMs);
		}
	}

	private SyncResult finishAndReport(SyncResult result, long startedMs) {
		long finishedMs = System.currentTimeMillis();
		result.setFinishedAt(new Date(finishedMs));
		result.setDurationMs(finishedMs - startedMs);
		try {
			writeRunReport(result);
		} catch (Exception ex) {
			log.warn("[FILE-DELTA-SYNC] failed to write report {}", result.getReportFile(), ex);
		}
		log.info(
				"[FILE-DELTA-SYNC] finish trigger={}, success={}, durationMs={}, requested(new={},disabled={},deleted={}), processed(new={},disabled={},deleted={}), skippedNewUsers={}, processNewUsers={}, queued(total={},add={},disable={},delete={},retry={}), remaining(new={},disabled={},deleted={}), reason={}, error={}, queueFile={}",
				result.getTrigger(), result.isSuccess(), result.getDurationMs(), result.getNewUsersRequested(),
				result.getDisabledUsersRequested(), result.getDeletedUsersRequested(), result.getNewUsersProcessed(),
				result.getDisabledUsersProcessed(), result.getDeletedUsersProcessed(), result.getNewUsersSkipped(),
				result.isNewUsersProcessingEnabled(), result.getTotalCommandsQueued(), result.getAddCommandsQueued(),
				result.getDisableCommandsQueued(), result.getDeleteCommandsQueued(), result.getRetriedCommandsQueued(),
				result.getQueueNewUsersRemaining(), result.getQueueDisabledUsersRemaining(),
				result.getQueueDeletedUsersRemaining(), result.getReason(), result.getError(), result.getQueueFile());
		return result;
	}

	private int queueNewUsersToAllDevices(List<Long> userIds, List<String> serials, Set<Long> processedUserIds,
			SyncResult result) {
		if (userIds == null || userIds.isEmpty() || serials == null || serials.isEmpty()) {
			return 0;
		}
		int queued = 0;
		List<MachineCommand> batch = new ArrayList<MachineCommand>(INSERT_BATCH_SIZE);
		for (Long userId : userIds) {
			if (userId == null) {
				continue;
			}
			Person person = personService.selectByPrimaryKey(userId);
			if (person == null) {
				result.getMissingUsers().add(userId);
				continue;
			}
			List<EnrollInfo> enrollInfos = enrollInfoService.selectByEnrollId(userId);
			if (enrollInfos == null || enrollInfos.isEmpty()) {
				result.getUsersWithoutEnrollData().add(userId);
				continue;
			}
			int commandsForThisUser = 0;
			for (EnrollInfo enrollInfo : enrollInfos) {
				if (!isValidEnrollInfo(enrollInfo)) {
					continue;
				}
				for (String serial : serials) {
					batch.add(buildSetUserInfoCommand(userId.longValue(), person.getName(), person.getRollId(),
							enrollInfo.getBackupnum().intValue(), enrollInfo.getSignatures(), serial));
					commandsForThisUser++;
					queued++;
					if (batch.size() >= INSERT_BATCH_SIZE) {
						insertBatch(batch);
						batch.clear();
					}
				}
			}
			if (commandsForThisUser > 0) {
				processedUserIds.add(userId);
			}
		}
		if (!batch.isEmpty()) {
			insertBatch(batch);
		}
		return queued;
	}

	private int queueDisableUsersToAllDevices(List<Long> userIds, List<String> serials, Set<Long> processedUserIds) {
		if (userIds == null || userIds.isEmpty() || serials == null || serials.isEmpty()) {
			return 0;
		}
		int queued = 0;
		List<MachineCommand> batch = new ArrayList<MachineCommand>(INSERT_BATCH_SIZE);
		for (Long userId : userIds) {
			if (userId == null) {
				continue;
			}
			for (String serial : serials) {
				batch.add(buildEnableUserCommand(userId.longValue(), 0, serial));
				queued++;
				if (batch.size() >= INSERT_BATCH_SIZE) {
					insertBatch(batch);
					batch.clear();
				}
			}
			processedUserIds.add(userId);
		}
		if (!batch.isEmpty()) {
			insertBatch(batch);
		}
		return queued;
	}

	private int queueDeleteUsersToAllDevices(List<Long> userIds, List<String> serials, Set<Long> processedUserIds) {
		if (userIds == null || userIds.isEmpty() || serials == null || serials.isEmpty()) {
			return 0;
		}
		int queued = 0;
		List<MachineCommand> batch = new ArrayList<MachineCommand>(INSERT_BATCH_SIZE);
		for (Long userId : userIds) {
			if (userId == null) {
				continue;
			}
			for (String serial : serials) {
				batch.add(buildDeleteUserCommand(userId.longValue(), serial));
				queued++;
				if (batch.size() >= INSERT_BATCH_SIZE) {
					insertBatch(batch);
					batch.clear();
				}
			}
			processedUserIds.add(userId);
		}
		if (!batch.isEmpty()) {
			insertBatch(batch);
		}
		return queued;
	}

	private boolean isProcessNewUsersEnabled() {
		String raw = System.getProperty(PROCESS_NEW_USERS_PROPERTY);
		if (!hasText(raw)) {
			return false;
		}
		return "true".equalsIgnoreCase(raw.trim());
	}

	private int requeueFailedDisableDeleteCommands() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		String sql = "select DC_ID as id, SLNO as serial, CAST(DC_CMD as NVARCHAR(MAX)) as content, CMD_DESC as cmd_desc "
				+ "from DEVICECMD "
				+ "where CMD_DESC in ('JAVA:enableuser','JAVA:deleteuser') "
				+ "and ISNULL(CASE WHEN ISNUMERIC(DC_RES)=1 THEN CONVERT(INT, DC_RES) ELSE NULL END, 0) = 2 "
				+ "and ISNULL(CONVERT(INT, IS_DEL_EXECUTED), 0) = 0";
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
		if (rows == null || rows.isEmpty()) {
			return 0;
		}

		List<MachineCommand> retryBatch = new ArrayList<MachineCommand>(rows.size());
		List<Integer> archivedIds = new ArrayList<Integer>(rows.size());
		for (Map<String, Object> row : rows) {
			Integer id = toInt(row.get("id"));
			String serial = toText(row.get("serial"));
			String content = toText(row.get("content"));
			String commandDesc = toText(row.get("cmd_desc"));
			String commandName = normalizeCommandName(commandDesc);
			if (id == null || !hasText(serial) || !hasText(content) || !hasText(commandName)) {
				continue;
			}
			MachineCommand retry = new MachineCommand();
			retry.setSerial(serial);
			retry.setName(commandName);
			retry.setContent(content);
			retry.setStatus(0);
			retry.setSendStatus(0);
			retry.setErrCount(0);
			retry.setGmtCrate(new Date());
			retry.setGmtModified(new Date());
			retryBatch.add(retry);
			archivedIds.add(id);
		}
		if (retryBatch.isEmpty()) {
			return 0;
		}
		int queued = insertBatch(retryBatch);
		markCommandsArchivedAfterRetry(archivedIds);
		log.info("[FILE-DELTA-SYNC] re-queued failed disable/delete commands. count={}", queued);
		return queued;
	}

	private void markCommandsArchivedAfterRetry(List<Integer> ids) {
		if (ids == null || ids.isEmpty()) {
			return;
		}
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		String sql = "update DEVICECMD "
				+ "set DC_RES = '1', IS_DEL_EXECUTED = 1, DC_RESDATE = GETDATE(), DC_cmd_date = GETDATE() "
				+ "where DC_ID = ? and CMD_DESC in ('JAVA:enableuser','JAVA:deleteuser')";
		for (Integer id : ids) {
			if (id == null) {
				continue;
			}
			jdbcTemplate.update(sql, id);
		}
	}

	private boolean isValidEnrollInfo(EnrollInfo enrollInfo) {
		if (enrollInfo == null || enrollInfo.getBackupnum() == null) {
			return false;
		}
		int backupNum = enrollInfo.getBackupnum().intValue();
		if (backupNum == 10 || backupNum == 11) {
			return enrollInfo.getSignatures() != null && enrollInfo.getSignatures().trim().length() > 0;
		}
		if (backupNum == 50 || (backupNum >= 20 && backupNum <= 27)) {
			return enrollInfo.getSignatures() != null && enrollInfo.getSignatures().trim().length() > 0;
		}
		return false;
	}

	private MachineCommand buildSetUserInfoCommand(long userId, String name, Integer admin, int backupNum, String record,
			String serial) {
		MachineCommand command = new MachineCommand();
		command.setSerial(serial);
		command.setName("setuserinfo");
		command.setStatus(0);
		command.setSendStatus(0);
		command.setErrCount(0);
		command.setGmtCrate(new Date());
		command.setGmtModified(new Date());
		String safeName = name == null ? "" : name;
		String safeRecord = record == null ? "" : record;
		int adminValue = sanitizeAdminValue(userId, admin);
		if (backupNum == 10 || backupNum == 11) {
			command.setContent("{\"cmd\":\"setuserinfo\",\"enrollid\":" + userId + ",\"name\":\"" + safeName
					+ "\",\"backupnum\":" + backupNum + ",\"admin\":" + adminValue + ",\"record\":" + safeRecord
					+ "}");
		} else {
			command.setContent("{\"cmd\":\"setuserinfo\",\"enrollid\":" + userId + ",\"name\":\"" + safeName
					+ "\",\"backupnum\":" + backupNum + ",\"admin\":" + adminValue + ",\"record\":\"" + safeRecord
					+ "\"}");
		}
		return command;
	}

	private int sanitizeAdminValue(long userId, Integer admin) {
		if (!ALLOW_DEVICE_ADMIN_SYNC) {
			return 0;
		}
		if (admin == null) {
			return 0;
		}
		return Math.max(0, admin.intValue());
	}

	private MachineCommand buildEnableUserCommand(long userId, int enFlag, String serial) {
		MachineCommand command = new MachineCommand();
		command.setSerial(serial);
		command.setName("enableuser");
		command.setStatus(0);
		command.setSendStatus(0);
		command.setErrCount(0);
		command.setGmtCrate(new Date());
		command.setGmtModified(new Date());
		command.setContent("{\"cmd\":\"enableuser\",\"enrollid\":" + userId + ",\"enrolled\":" + userId
				+ ",\"enflag\":" + enFlag + "}");
		return command;
	}

	private MachineCommand buildDeleteUserCommand(long userId, String serial) {
		MachineCommand command = new MachineCommand();
		command.setSerial(serial);
		command.setName("deleteuser");
		command.setStatus(0);
		command.setSendStatus(0);
		command.setErrCount(0);
		command.setGmtCrate(new Date());
		command.setGmtModified(new Date());
		command.setContent("{\"cmd\":\"deleteuser\",\"enrollid\":" + userId + ",\"backupnum\":13}");
		return command;
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

	private QueueFileState removeProcessedIdsFromQueue(File queueFile, Set<Long> processedNew, Set<Long> processedDisabled,
			Set<Long> processedDeleted) throws Exception {
		DeltaQueue current = readQueue(queueFile);
		current.newUserIds.removeAll(processedNew);
		current.disabledUserIds.removeAll(processedDisabled);
		current.deletedUserIds.removeAll(processedDeleted);
		writeQueue(queueFile, current);

		QueueFileState state = new QueueFileState();
		state.newUsersRemaining = current.newUserIds.size();
		state.disabledUsersRemaining = current.disabledUserIds.size();
		state.deletedUsersRemaining = current.deletedUserIds.size();
		return state;
	}

	private void writeQueue(File queueFile, DeltaQueue queue) throws Exception {
		File parent = queueFile.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}
		ObjectNode root = objectMapper.createObjectNode();
		root.put("version", 1);
		root.put("updated_at", String.valueOf(System.currentTimeMillis()));
		root.set("new_user_ids", toArrayNode(queue.newUserIds));
		root.set("disabled_user_ids", toArrayNode(queue.disabledUserIds));
		root.set("deleted_user_ids", toArrayNode(queue.deletedUserIds));

		File tempFile = new File(queueFile.getAbsolutePath() + ".tmp");
		objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile, root);
		try {
			Files.move(tempFile.toPath(), queueFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception ex) {
			Files.move(tempFile.toPath(), queueFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void writeRunReport(SyncResult result) throws Exception {
		if (result.getReportFile() == null) {
			return;
		}
		File reportFile = new File(result.getReportFile());
		File parent = reportFile.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}
		ObjectNode root = objectMapper.createObjectNode();
		root.put("version", 1);
		root.put("generated_at", String.valueOf(System.currentTimeMillis()));
		root.put("trigger", value(result.getTrigger()));
		root.put("success", result.isSuccess());
		root.put("error", value(result.getError()));
		root.put("reason", value(result.getReason()));
		root.put("duration_ms", result.getDurationMs());
		root.put("devices", result.getDevices());
		root.put("queue_file", value(result.getQueueFile()));
		root.put("process_new_users", result.isNewUsersProcessingEnabled());

		ObjectNode requested = root.putObject("requested");
		requested.put("new_users", result.getNewUsersRequested());
		requested.put("disabled_users", result.getDisabledUsersRequested());
		requested.put("deleted_users", result.getDeletedUsersRequested());

		ObjectNode processed = root.putObject("processed");
		processed.put("new_users", result.getNewUsersProcessed());
		processed.put("new_users_skipped", result.getNewUsersSkipped());
		processed.put("disabled_users", result.getDisabledUsersProcessed());
		processed.put("deleted_users", result.getDeletedUsersProcessed());
		processed.set("missing_users", toArrayNode(result.getMissingUsers()));
		processed.set("users_without_enroll_data", toArrayNode(result.getUsersWithoutEnrollData()));

		ObjectNode queued = root.putObject("queued");
		queued.put("add_commands", result.getAddCommandsQueued());
		queued.put("disable_commands", result.getDisableCommandsQueued());
		queued.put("delete_commands", result.getDeleteCommandsQueued());
		queued.put("retry_commands", result.getRetriedCommandsQueued());
		queued.put("total_commands", result.getTotalCommandsQueued());

		ObjectNode remaining = root.putObject("remaining_queue");
		remaining.put("new_users", result.getQueueNewUsersRemaining());
		remaining.put("disabled_users", result.getQueueDisabledUsersRemaining());
		remaining.put("deleted_users", result.getQueueDeletedUsersRemaining());

		objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportFile, root);
	}

	private DeltaQueue readQueue(File queueFile) throws Exception {
		if (queueFile == null || !queueFile.exists()) {
			return new DeltaQueue();
		}
		byte[] bytes = Files.readAllBytes(queueFile.toPath());
		if (bytes == null || bytes.length == 0) {
			return new DeltaQueue();
		}
		JsonNode root = objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
		DeltaQueue queue = new DeltaQueue();
		queue.newUserIds = parseLongList(root.path("new_user_ids"));
		queue.disabledUserIds = parseLongList(root.path("disabled_user_ids"));
		queue.deletedUserIds = parseLongList(root.path("deleted_user_ids"));
		return queue;
	}

	private ArrayNode toArrayNode(List<Long> ids) {
		ArrayNode arrayNode = objectMapper.createArrayNode();
		if (ids == null) {
			return arrayNode;
		}
		for (Long id : ids) {
			if (id != null) {
				arrayNode.add(id.longValue());
			}
		}
		return arrayNode;
	}

	private List<Long> parseLongList(JsonNode node) {
		if (node == null || !node.isArray()) {
			return new ArrayList<Long>();
		}
		Set<Long> ids = new LinkedHashSet<Long>();
		for (int i = 0; i < node.size(); i++) {
			JsonNode value = node.get(i);
			Long parsed = parseLongValue(value);
			if (parsed != null) {
				ids.add(parsed);
			}
		}
		return new ArrayList<Long>(ids);
	}

	private Long parseLongValue(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		try {
			if (node.isIntegralNumber()) {
				return Long.valueOf(node.longValue());
			}
			String raw = node.asText(null);
			if (raw == null || raw.trim().isEmpty()) {
				return null;
			}
			return Long.valueOf(raw.trim());
		} catch (Exception ex) {
			return null;
		}
	}

	private List<String> getAllKnownDeviceSerials() {
		Set<String> serials = new LinkedHashSet<String>();
		List<Device> devices = deviceService.findAllDevice();
		for (Device device : devices) {
			if (device == null || device.getSerialNum() == null) {
				continue;
			}
			String serial = device.getSerialNum().trim();
			if (!serial.isEmpty()) {
				serials.add(serial);
			}
		}
		return new ArrayList<String>(serials);
	}

	private File resolveQueueFile() {
		String queueFilePath = System.getProperty(QUEUE_FILE_PROPERTY);
		if (hasText(queueFilePath)) {
			return new File(queueFilePath.trim());
		}

		List<File> candidates = new ArrayList<File>();
		String stateDirPath = System.getProperty(STATE_DIR_PROPERTY);
		if (hasText(stateDirPath)) {
			candidates.add(new File(stateDirPath.trim(), QUEUE_FILE_NAME));
		}

		String userDir = System.getProperty("user.dir");
		if (hasText(userDir)) {
			candidates.add(new File(new File(userDir, DEFAULT_STATE_DIR_NAME), QUEUE_FILE_NAME));
		}

		String catalinaBase = System.getProperty("catalina.base");
		if (hasText(catalinaBase)) {
			candidates.add(new File(new File(catalinaBase, DEFAULT_STATE_DIR_NAME), QUEUE_FILE_NAME));
		}

		String userHome = System.getProperty("user.home");
		if (hasText(userHome)) {
			File desktopWorking = new File(new File(userHome, "Desktop"), "JAVA_2512_working");
			candidates.add(new File(new File(desktopWorking, DEFAULT_STATE_DIR_NAME), QUEUE_FILE_NAME));
		}

		for (File candidate : candidates) {
			if (candidate != null && candidate.exists()) {
				return candidate;
			}
		}

		if (!candidates.isEmpty()) {
			return candidates.get(0);
		}
		return new File(new File(".", DEFAULT_STATE_DIR_NAME), QUEUE_FILE_NAME);
	}

	private File getReportFile(File queueFile) {
		if (queueFile == null) {
			return new File(new File(".", DEFAULT_STATE_DIR_NAME), LAST_RUN_FILE_NAME);
		}
		File parent = queueFile.getParentFile();
		if (parent == null) {
			return new File(LAST_RUN_FILE_NAME);
		}
		return new File(parent, LAST_RUN_FILE_NAME);
	}

	private boolean hasText(String value) {
		return value != null && value.trim().length() > 0;
	}

	private String toText(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private Integer toInt(Object value) {
		if (value == null) {
			return null;
		}
		try {
			if (value instanceof Number) {
				return Integer.valueOf(((Number) value).intValue());
			}
			String text = String.valueOf(value).trim();
			if (text.isEmpty()) {
				return null;
			}
			return Integer.valueOf(text);
		} catch (Exception ex) {
			return null;
		}
	}

	private String normalizeCommandName(String commandDesc) {
		if (!hasText(commandDesc)) {
			return null;
		}
		String normalized = commandDesc.trim();
		if (normalized.startsWith("JAVA:")) {
			normalized = normalized.substring(5);
		}
		if ("enableuser".equalsIgnoreCase(normalized)) {
			return "enableuser";
		}
		if ("deleteuser".equalsIgnoreCase(normalized)) {
			return "deleteuser";
		}
		return null;
	}

	private String value(String text) {
		return text == null ? "" : text;
	}

	private static final class QueueFileState {
		private int newUsersRemaining;
		private int disabledUsersRemaining;
		private int deletedUsersRemaining;
	}

	private static final class DeltaQueue {
		private List<Long> newUserIds = new ArrayList<Long>();
		private List<Long> disabledUserIds = new ArrayList<Long>();
		private List<Long> deletedUserIds = new ArrayList<Long>();

		private boolean isEmpty() {
			return newUserIds.isEmpty() && disabledUserIds.isEmpty() && deletedUserIds.isEmpty();
		}
	}

	public static class SyncResult {
		private boolean success;
		private String trigger;
		private String reason;
		private String error;
		private String queueFile;
		private String reportFile;
		private int devices;
		private boolean newUsersProcessingEnabled;
		private int newUsersRequested;
		private int disabledUsersRequested;
		private int deletedUsersRequested;
		private int newUsersProcessed;
		private int newUsersSkipped;
		private int disabledUsersProcessed;
		private int deletedUsersProcessed;
		private int addCommandsQueued;
		private int disableCommandsQueued;
		private int deleteCommandsQueued;
		private int retriedCommandsQueued;
		private int totalCommandsQueued;
		private int queueNewUsersRemaining;
		private int queueDisabledUsersRemaining;
		private int queueDeletedUsersRemaining;
		private long durationMs;
		private Date startedAt;
		private Date finishedAt;
		private List<Long> missingUsers = new ArrayList<Long>();
		private List<Long> usersWithoutEnrollData = new ArrayList<Long>();

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

		public String getReason() {
			return reason;
		}

		public void setReason(String reason) {
			this.reason = reason;
		}

		public String getError() {
			return error;
		}

		public void setError(String error) {
			this.error = error;
		}

		public String getQueueFile() {
			return queueFile;
		}

		public void setQueueFile(String queueFile) {
			this.queueFile = queueFile;
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

		public boolean isNewUsersProcessingEnabled() {
			return newUsersProcessingEnabled;
		}

		public void setNewUsersProcessingEnabled(boolean newUsersProcessingEnabled) {
			this.newUsersProcessingEnabled = newUsersProcessingEnabled;
		}

		public int getNewUsersRequested() {
			return newUsersRequested;
		}

		public void setNewUsersRequested(int newUsersRequested) {
			this.newUsersRequested = newUsersRequested;
		}

		public int getDisabledUsersRequested() {
			return disabledUsersRequested;
		}

		public void setDisabledUsersRequested(int disabledUsersRequested) {
			this.disabledUsersRequested = disabledUsersRequested;
		}

		public int getDeletedUsersRequested() {
			return deletedUsersRequested;
		}

		public void setDeletedUsersRequested(int deletedUsersRequested) {
			this.deletedUsersRequested = deletedUsersRequested;
		}

		public int getNewUsersProcessed() {
			return newUsersProcessed;
		}

		public void setNewUsersProcessed(int newUsersProcessed) {
			this.newUsersProcessed = newUsersProcessed;
		}

		public int getNewUsersSkipped() {
			return newUsersSkipped;
		}

		public void setNewUsersSkipped(int newUsersSkipped) {
			this.newUsersSkipped = newUsersSkipped;
		}

		public int getDisabledUsersProcessed() {
			return disabledUsersProcessed;
		}

		public void setDisabledUsersProcessed(int disabledUsersProcessed) {
			this.disabledUsersProcessed = disabledUsersProcessed;
		}

		public int getDeletedUsersProcessed() {
			return deletedUsersProcessed;
		}

		public void setDeletedUsersProcessed(int deletedUsersProcessed) {
			this.deletedUsersProcessed = deletedUsersProcessed;
		}

		public int getAddCommandsQueued() {
			return addCommandsQueued;
		}

		public void setAddCommandsQueued(int addCommandsQueued) {
			this.addCommandsQueued = addCommandsQueued;
		}

		public int getDisableCommandsQueued() {
			return disableCommandsQueued;
		}

		public void setDisableCommandsQueued(int disableCommandsQueued) {
			this.disableCommandsQueued = disableCommandsQueued;
		}

		public int getDeleteCommandsQueued() {
			return deleteCommandsQueued;
		}

		public void setDeleteCommandsQueued(int deleteCommandsQueued) {
			this.deleteCommandsQueued = deleteCommandsQueued;
		}

		public int getRetriedCommandsQueued() {
			return retriedCommandsQueued;
		}

		public void setRetriedCommandsQueued(int retriedCommandsQueued) {
			this.retriedCommandsQueued = retriedCommandsQueued;
		}

		public int getTotalCommandsQueued() {
			return totalCommandsQueued;
		}

		public void setTotalCommandsQueued(int totalCommandsQueued) {
			this.totalCommandsQueued = totalCommandsQueued;
		}

		public int getQueueNewUsersRemaining() {
			return queueNewUsersRemaining;
		}

		public void setQueueNewUsersRemaining(int queueNewUsersRemaining) {
			this.queueNewUsersRemaining = queueNewUsersRemaining;
		}

		public int getQueueDisabledUsersRemaining() {
			return queueDisabledUsersRemaining;
		}

		public void setQueueDisabledUsersRemaining(int queueDisabledUsersRemaining) {
			this.queueDisabledUsersRemaining = queueDisabledUsersRemaining;
		}

		public int getQueueDeletedUsersRemaining() {
			return queueDeletedUsersRemaining;
		}

		public void setQueueDeletedUsersRemaining(int queueDeletedUsersRemaining) {
			this.queueDeletedUsersRemaining = queueDeletedUsersRemaining;
		}

		public long getDurationMs() {
			return durationMs;
		}

		public void setDurationMs(long durationMs) {
			this.durationMs = durationMs;
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

		public List<Long> getMissingUsers() {
			return missingUsers;
		}

		public void setMissingUsers(List<Long> missingUsers) {
			this.missingUsers = missingUsers == null ? new ArrayList<Long>() : missingUsers;
		}

		public List<Long> getUsersWithoutEnrollData() {
			return usersWithoutEnrollData;
		}

		public void setUsersWithoutEnrollData(List<Long> usersWithoutEnrollData) {
			this.usersWithoutEnrollData = usersWithoutEnrollData == null ? new ArrayList<Long>() : usersWithoutEnrollData;
		}
	}
}
