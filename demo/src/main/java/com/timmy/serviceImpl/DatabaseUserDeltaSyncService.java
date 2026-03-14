package com.timmy.serviceImpl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.timmy.entity.Device;
import com.timmy.entity.MachineCommand;
import com.timmy.entity.Person;
import com.timmy.service.DeviceService;
import com.timmy.service.PersonService;
import com.timmy.websocket.WebSocketPool;

@Service
public class DatabaseUserDeltaSyncService {

	private static final Logger log = LoggerFactory.getLogger(DatabaseUserDeltaSyncService.class);
	private static final int BATCH_SIZE = 500;
	private static final int ESTIMATED_COMMANDS_PER_SECOND_PER_DEVICE = 10;
	private static final int COMMAND_DEDUPE_USER_CHUNK = 200;
	private static final String SNAPSHOT_FILE_NAME = "idsl-user-status-sync-cache.properties";
	private static final String ACTIVE_PREFIX = "U.";
	private static final String DELETED_PREFIX = "D.";
	private static final String TRANSITION_AUDIT_TABLE = "JAVA_VERIFY_SCHEDULER_AUDIT";
	private static final String USER_SYNC_STATE_TABLE = "JAVA_VERIFY_USER_SYNC_STATE";
	private static final Pattern ENABLE_CMD_ENROLL_PATTERN = Pattern.compile("\"enrollid\"\\s*:\\s*(\\d+)");
	private static final Pattern ENABLE_CMD_ENFLAG_PATTERN = Pattern.compile("\"enflag\"\\s*:\\s*(\\d+)");

	private final Object syncLock = new Object();
	private volatile boolean transitionAuditTableEnsured;
	private volatile boolean userSyncStateTableEnsured;

	@Autowired
	private PersonService personService;

	@Autowired
	private DeviceService deviceService;

	@Autowired
	private DataSource dataSource;

	public SyncResult syncChangedStatusTransitionsByVerify(String trigger) {
		synchronized (syncLock) {
			long startedMs = System.currentTimeMillis();
			SyncResult result = new SyncResult();
			result.setTrigger(trigger);
			result.setStartedAt(new Date(startedMs));
			result.setSnapshotFile("");
			Long auditId = createTransitionSyncAuditStart(result.getStartedAt());
			log.info("[DB-VERIFY-TRANSITION-SYNC] start trigger={}, mode=daily-transition-only", trigger);
			try {
				List<String> serials = getAllKnownDeviceSerials();
				result.setDevices(serials.size());
				result.setOnlineDevices(countOnlineDevices(serials));
				if (serials.isEmpty()) {
					result.setSuccess(false);
					result.setError("No devices found in database.");
					result.setReason("No devices configured in database.");
					long finishedNoDeviceMs = System.currentTimeMillis();
					result.setFinishedAt(new Date(finishedNoDeviceMs));
					result.setDurationMs(finishedNoDeviceMs - startedMs);
				} else {
					Map<Long, Integer> currentActive = loadCurrentActiveStatuses();
					result.setActiveUsers(currentActive.size());
					result.setDeletedUsers(0);
					result.setSnapshotLoaded(false);

					Map<Long, Integer> currentStatusByUser = normalizeStatusMap(currentActive);
					Map<Long, Integer> stateStatusByUser = loadUserSyncStateByUser(currentStatusByUser.keySet());
					Map<Long, Integer> upsertsForState = new LinkedHashMap<Long, Integer>();
					if (stateStatusByUser.isEmpty()) {
						// First scheduler run initializes baseline state and does not flood devices.
						upsertsForState.putAll(currentStatusByUser);
						saveUserSyncState(upsertsForState);
						result.setSuccess(true);
						result.setReason(
								"No previous sync state found; baseline initialized. Transition sync will start from next run.");
						result.setEnabledUsers(0);
						result.setDisabledUsers(0);
						result.setChangedStatusUsers(0);
						result.setChangedDeletedUsers(0);
						result.setEnableCommandsQueued(0);
						result.setDisableCommandsQueued(0);
						result.setStatusCommandsQueued(0);
						result.setDeleteCommandsQueued(0);
						result.setTotalCommandsQueued(0);
						result.setEstimatedDeviceDispatchSeconds(0L);
					} else {
						Map<Long, Integer> changedToEnabled = new LinkedHashMap<Long, Integer>();
						Map<Long, Integer> changedToDisabled = new LinkedHashMap<Long, Integer>();
						for (Map.Entry<Long, Integer> entry : currentStatusByUser.entrySet()) {
							Long userId = entry.getKey();
							if (userId == null) {
								continue;
							}
							int currentStatus = entry.getValue().intValue();
							Integer previousStatus = stateStatusByUser.get(userId);
							if (previousStatus == null) {
								// New user in state tracking; initialize baseline without transition command.
								upsertsForState.put(userId, Integer.valueOf(currentStatus));
								continue;
							}
							if (currentStatus == previousStatus.intValue()) {
								continue;
							}
							if (currentStatus == 0) {
								changedToDisabled.put(userId, Integer.valueOf(0));
							} else {
								changedToEnabled.put(userId, Integer.valueOf(1));
							}
							upsertsForState.put(userId, Integer.valueOf(currentStatus));
						}

						int enableCommandsQueued = queueStatusCommands(changedToEnabled, serials);
						int disableCommandsQueued = queueStatusCommands(changedToDisabled, serials);
						int totalCommandsQueued = enableCommandsQueued + disableCommandsQueued;
						int changedUsers = changedToEnabled.size() + changedToDisabled.size();
						saveUserSyncState(upsertsForState);

						result.setEnabledUsers(changedToEnabled.size());
						result.setDisabledUsers(changedToDisabled.size());
						result.setChangedStatusUsers(changedUsers);
						result.setChangedDeletedUsers(0);
						result.setEnableCommandsQueued(enableCommandsQueued);
						result.setDisableCommandsQueued(disableCommandsQueued);
						result.setStatusCommandsQueued(totalCommandsQueued);
						result.setDeleteCommandsQueued(0);
						result.setTotalCommandsQueued(totalCommandsQueued);
						result.setEstimatedDeviceDispatchSeconds(estimateDeviceDispatchSeconds(changedUsers));

						if (result.getTotalCommandsQueued() == 0) {
							result.setEstimatedDeviceDispatchSeconds(0L);
							if (upsertsForState.isEmpty()) {
								result.setReason("No enable/disable transitions found. Nothing queued.");
							} else {
								result.setReason(
										"No transitions found; new users added to sync-state baseline without queueing commands.");
							}
						} else if (result.getOnlineDevices() <= 0) {
							result.setReason("Commands queued in DEVICECMD, but no websocket device is online right now.");
						}

						log.info(
								"[DB-VERIFY-TRANSITION-SYNC] queued trigger={}, activeUsers={}, changedUsers={}, changedToEnabled={}, changedToDisabled={}, queued(total={}, enable={}, disable={}), devices={}, onlineDevices={}",
								trigger, result.getActiveUsers(), result.getChangedStatusUsers(), result.getEnabledUsers(),
								result.getDisabledUsers(), result.getTotalCommandsQueued(), result.getEnableCommandsQueued(),
								result.getDisableCommandsQueued(), result.getDevices(), result.getOnlineDevices());
					}
					result.setSuccess(true);
					if (hasText(result.getReason())) {
						log.warn("[DB-VERIFY-TRANSITION-SYNC] reason trigger={}, message={}", trigger, result.getReason());
					}
				}
			} catch (Exception ex) {
				result.setSuccess(false);
				result.setError(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
				log.error("[DB-VERIFY-TRANSITION-SYNC] failed trigger={}, error={}", trigger, result.getError(), ex);
			}
			long finishedMs = System.currentTimeMillis();
			result.setFinishedAt(new Date(finishedMs));
			result.setDurationMs(finishedMs - startedMs);
			log.info(
					"[DB-VERIFY-TRANSITION-SYNC] finish trigger={}, success={}, durationMs={}, devices={}, onlineDevices={}, queuedTotal={}, reason={}, error={}",
					trigger, result.isSuccess(), result.getDurationMs(), result.getDevices(), result.getOnlineDevices(),
					result.getTotalCommandsQueued(), result.getReason(), result.getError());
			updateTransitionSyncAuditFinish(auditId, result);
			return result;
		}
	}

	private Long createTransitionSyncAuditStart(Date startedAt) {
		try {
			JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
			ensureTransitionAuditTable(jdbcTemplate);
			String sql = "insert into " + TRANSITION_AUDIT_TABLE + " (ACTIVE_USER, DISABLE_USER, STATUS, STARTED_AT, FINISHED_AT) "
					+ "output INSERTED.ID values (?, ?, ?, ?, ?)";
			return jdbcTemplate.queryForObject(sql, Long.class, 0, 0, "RUNNING", toTimestamp(startedAt), null);
		} catch (Exception ex) {
			log.warn("[DB-VERIFY-TRANSITION-SYNC] failed to create start audit row. error={}", ex.getMessage());
			return null;
		}
	}

	private void updateTransitionSyncAuditFinish(Long auditId, SyncResult result) {
		if (auditId == null || result == null) {
			return;
		}
		try {
			JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
			ensureTransitionAuditTable(jdbcTemplate);
			String sql = "update " + TRANSITION_AUDIT_TABLE
					+ " set ACTIVE_USER = ?, DISABLE_USER = ?, STATUS = ?, FINISHED_AT = ? where ID = ?";
			jdbcTemplate.update(sql,
					result.getEnabledUsers(),
					result.getDisabledUsers(),
					result.isSuccess() ? "SUCCESS" : "FAILED",
					toTimestamp(result.getFinishedAt()),
					auditId);
		} catch (Exception ex) {
			log.warn("[DB-VERIFY-TRANSITION-SYNC] failed to update finish audit row. auditId={}, error={}",
					auditId, ex.getMessage());
		}
	}

	private void ensureTransitionAuditTable(JdbcTemplate jdbcTemplate) {
		if (transitionAuditTableEnsured) {
			return;
		}
		String ddl = "IF OBJECT_ID('dbo." + TRANSITION_AUDIT_TABLE + "', 'U') IS NULL "
				+ "BEGIN "
				+ "CREATE TABLE dbo." + TRANSITION_AUDIT_TABLE + " ("
				+ "ID BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY, "
				+ "ACTIVE_USER INT NOT NULL DEFAULT 0, "
				+ "DISABLE_USER INT NOT NULL DEFAULT 0, "
				+ "STATUS VARCHAR(20) NOT NULL, "
				+ "STARTED_AT DATETIME2(0) NOT NULL, "
				+ "FINISHED_AT DATETIME2(0) NULL "
				+ "); "
				+ "END; "
				+ "IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_" + TRANSITION_AUDIT_TABLE
				+ "_STARTED_AT' AND object_id = OBJECT_ID('dbo." + TRANSITION_AUDIT_TABLE + "')) "
				+ "BEGIN "
				+ "CREATE INDEX IX_" + TRANSITION_AUDIT_TABLE + "_STARTED_AT ON dbo." + TRANSITION_AUDIT_TABLE
				+ " (STARTED_AT DESC); "
				+ "END;";
		jdbcTemplate.execute(ddl);
		transitionAuditTableEnsured = true;
	}

	private Timestamp toTimestamp(Date value) {
		if (value == null) {
			return null;
		}
		return new Timestamp(value.getTime());
	}

	public SyncResult syncAllUsersToAllDevicesByVerify(String trigger) {
		synchronized (syncLock) {
			long startedMs = System.currentTimeMillis();
			SyncResult result = new SyncResult();
			result.setTrigger(trigger);
			result.setStartedAt(new Date(startedMs));
			result.setSnapshotFile("");
			log.info("[DB-VERIFY-SYNC] start trigger={}, mode=daily-full-verify", trigger);
			try {
				List<String> serials = getAllKnownDeviceSerials();
				result.setDevices(serials.size());
				result.setOnlineDevices(countOnlineDevices(serials));
				if (serials.isEmpty()) {
					result.setSuccess(false);
					result.setError("No devices found in database.");
					result.setReason("No devices configured in database.");
					long finishedNoDeviceMs = System.currentTimeMillis();
					result.setFinishedAt(new Date(finishedNoDeviceMs));
					result.setDurationMs(finishedNoDeviceMs - startedMs);
					return result;
				}

				Map<Long, Integer> currentActive = loadCurrentActiveStatuses();
				result.setActiveUsers(currentActive.size());
				result.setDeletedUsers(0);
				result.setSnapshotLoaded(false);

				List<Long> enabledUserIds = new ArrayList<Long>();
				List<Long> disabledUserIds = new ArrayList<Long>();
				for (Map.Entry<Long, Integer> entry : currentActive.entrySet()) {
					Long userId = entry.getKey();
					if (userId == null) {
						continue;
					}
					int normalizedStatus = normalizeEnableStatus(entry.getValue());
					if (normalizedStatus == 0) {
						disabledUserIds.add(userId);
					} else {
						enabledUserIds.add(userId);
					}
				}
				Collections.sort(enabledUserIds);
				Collections.sort(disabledUserIds);

				int enableCommandsQueued = queueEnableDisablePhaseCommands(enabledUserIds, 1, serials);
				int disableCommandsQueued = queueEnableDisablePhaseCommands(disabledUserIds, 0, serials);
				int totalCommandsQueued = enableCommandsQueued + disableCommandsQueued;
				Map<Long, Integer> currentStatusByUser = normalizeStatusMap(currentActive);
				saveUserSyncState(currentStatusByUser);

				result.setEnabledUsers(enabledUserIds.size());
				result.setDisabledUsers(disabledUserIds.size());
				result.setChangedStatusUsers(currentActive.size());
				result.setChangedDeletedUsers(0);
				result.setEnableCommandsQueued(enableCommandsQueued);
				result.setDisableCommandsQueued(disableCommandsQueued);
				result.setStatusCommandsQueued(totalCommandsQueued);
				result.setDeleteCommandsQueued(0);
				result.setTotalCommandsQueued(totalCommandsQueued);
				result.setEstimatedDeviceDispatchSeconds(estimateDeviceDispatchSeconds(currentActive.size()));

				if (result.getTotalCommandsQueued() == 0) {
					result.setEstimatedDeviceDispatchSeconds(0L);
					result.setReason("No active users found to queue.");
				} else if (result.getOnlineDevices() <= 0) {
					result.setReason("Commands queued in DEVICECMD, but no websocket device is online right now.");
				}
				result.setSuccess(true);
				log.info(
						"[DB-VERIFY-SYNC] queued trigger={}, users(total={}, enabled={}, disabled={}), queued(total={}, enable={}, disable={}), devices={}, onlineDevices={}",
						trigger, result.getActiveUsers(), result.getEnabledUsers(), result.getDisabledUsers(),
						result.getTotalCommandsQueued(), result.getEnableCommandsQueued(),
						result.getDisableCommandsQueued(), result.getDevices(), result.getOnlineDevices());
				if (hasText(result.getReason())) {
					log.warn("[DB-VERIFY-SYNC] reason trigger={}, message={}", trigger, result.getReason());
				}
			} catch (Exception ex) {
				result.setSuccess(false);
				result.setError(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
				log.error("[DB-VERIFY-SYNC] failed trigger={}, error={}", trigger, result.getError(), ex);
			}
			long finishedMs = System.currentTimeMillis();
			result.setFinishedAt(new Date(finishedMs));
			result.setDurationMs(finishedMs - startedMs);
			log.info(
					"[DB-VERIFY-SYNC] finish trigger={}, success={}, durationMs={}, devices={}, onlineDevices={}, queuedTotal={}, reason={}, error={}",
					trigger, result.isSuccess(), result.getDurationMs(), result.getDevices(), result.getOnlineDevices(),
					result.getTotalCommandsQueued(), result.getReason(), result.getError());
			return result;
		}
	}

	public SyncResult syncChangedUsersToAllDevices(String trigger) {
		synchronized (syncLock) {
			long startedMs = System.currentTimeMillis();
			SyncResult result = new SyncResult();
			result.setTrigger(trigger);
			result.setStartedAt(new Date(startedMs));
			result.setSnapshotFile(getSnapshotFile().getAbsolutePath());
			log.info(
					"[DB-DELTA-SYNC] start trigger={}, snapshotFile={}, estimatedCmdRatePerDevice={} cmd/sec",
					trigger, result.getSnapshotFile(), ESTIMATED_COMMANDS_PER_SECOND_PER_DEVICE);
			try {
				List<String> serials = getAllKnownDeviceSerials();
				result.setDevices(serials.size());
				result.setOnlineDevices(countOnlineDevices(serials));
				if (serials.isEmpty()) {
					result.setSuccess(false);
					result.setError("No devices found in database.");
					result.setReason("No devices configured in database.");
					log.warn("[DB-DELTA-SYNC] stop trigger={} reason={}", trigger, result.getReason());
					long finishedNoDeviceMs = System.currentTimeMillis();
					result.setFinishedAt(new Date(finishedNoDeviceMs));
					result.setDurationMs(finishedNoDeviceMs - startedMs);
					return result;
				}

				Snapshot previous = loadSnapshot();
				result.setSnapshotLoaded(previous.loadedFromFile);
				Map<Long, Integer> currentActive = loadCurrentActiveStatuses();
				Set<Long> currentDeleted = loadCurrentDeletedUsers();
				result.setActiveUsers(currentActive.size());
				result.setDeletedUsers(currentDeleted.size());
				log.info(
						"[DB-DELTA-SYNC] context trigger={}, devices={}, onlineDevices={}, snapshotLoaded={}, activeUsers={}, deletedUsers={}",
						trigger, result.getDevices(), result.getOnlineDevices(), result.isSnapshotLoaded(),
						result.getActiveUsers(), result.getDeletedUsers());

				Map<Long, Integer> changedStatuses = new LinkedHashMap<Long, Integer>();
				Set<Long> newlyDeleted = new LinkedHashSet<Long>();
				for (Map.Entry<Long, Integer> entry : currentActive.entrySet()) {
					Long userId = entry.getKey();
					Integer currentStatus = entry.getValue();
					Integer previousStatus = previous.activeStatuses.get(userId);
					if (previousStatus == null || previousStatus.intValue() != currentStatus.intValue()) {
						changedStatuses.put(userId, currentStatus);
					}
				}
				for (Long deletedId : currentDeleted) {
					if (!previous.deletedUserIds.contains(deletedId)) {
						newlyDeleted.add(deletedId);
					}
				}

				int statusCommandsQueued = queueStatusCommands(changedStatuses, serials);
				int deleteCommandsQueued = queueDeleteCommands(newlyDeleted, serials);
				saveSnapshot(currentActive, currentDeleted);

				int changedUsersPerDevice = changedStatuses.size() + newlyDeleted.size();
				result.setChangedStatusUsers(changedStatuses.size());
				result.setChangedDeletedUsers(newlyDeleted.size());
				result.setStatusCommandsQueued(statusCommandsQueued);
				result.setDeleteCommandsQueued(deleteCommandsQueued);
				result.setTotalCommandsQueued(statusCommandsQueued + deleteCommandsQueued);
				result.setEstimatedDeviceDispatchSeconds(estimateDeviceDispatchSeconds(changedUsersPerDevice));
				if (result.getTotalCommandsQueued() == 0) {
					result.setEstimatedDeviceDispatchSeconds(0L);
					if (!changedStatuses.isEmpty() || !newlyDeleted.isEmpty()) {
						result.setReason(
								"Delta detected but duplicate enable/disable commands were skipped because same status was already queued/sent.");
					} else {
						result.setReason(
								"No status or deleted-user delta found. Snapshot matches current database.");
					}
				} else if (result.getOnlineDevices() <= 0) {
					result.setReason(
							"Commands queued in DEVICECMD, but no websocket device is online right now.");
				}
				result.setSuccess(true);
				log.info(
						"[DB-DELTA-SYNC] delta trigger={}, changedStatusUsers={}, changedDeletedUsers={}, queuedStatusCmd={}, queuedDeleteCmd={}, queuedTotal={}, estimatedDispatchSeconds={}",
						trigger, result.getChangedStatusUsers(), result.getChangedDeletedUsers(),
						result.getStatusCommandsQueued(), result.getDeleteCommandsQueued(), result.getTotalCommandsQueued(),
						result.getEstimatedDeviceDispatchSeconds());
				if (hasText(result.getReason())) {
					log.warn("[DB-DELTA-SYNC] reason trigger={}, message={}", trigger, result.getReason());
				}
			} catch (Exception ex) {
				result.setSuccess(false);
				result.setError(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
				log.error("[DB-DELTA-SYNC] failed trigger={}, error={}", trigger, result.getError(), ex);
			}
			long finishedMs = System.currentTimeMillis();
			result.setFinishedAt(new Date(finishedMs));
			result.setDurationMs(finishedMs - startedMs);
			log.info(
					"[DB-DELTA-SYNC] finish trigger={}, success={}, durationMs={}, devices={}, onlineDevices={}, queuedTotal={}, reason={}, error={}",
					trigger, result.isSuccess(), result.getDurationMs(), result.getDevices(),
					result.getOnlineDevices(), result.getTotalCommandsQueued(), result.getReason(), result.getError());
			return result;
		}
	}

	private Map<Long, Integer> loadCurrentActiveStatuses() {
		Map<Long, Integer> result = new LinkedHashMap<Long, Integer>();
		List<Person> persons = personService.selectAll();
		for (Person person : persons) {
			if (person == null || person.getId() == null) {
				continue;
			}
			result.put(person.getId(), Integer.valueOf(normalizeEnableStatus(person.getStatus())));
		}
		return result;
	}

	private Set<Long> loadCurrentDeletedUsers() {
		Set<Long> result = new LinkedHashSet<Long>();
		List<Long> deletedUsers = personService.selectDeletedUserIds();
		for (Long userId : deletedUsers) {
			if (userId != null) {
				result.add(userId);
			}
		}
		return result;
	}

	private int normalizeEnableStatus(Integer status) {
		return (status != null && status.intValue() == 0) ? 0 : 1;
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

	private int queueStatusCommands(Map<Long, Integer> changedStatuses, List<String> serials) {
		if (changedStatuses == null || changedStatuses.isEmpty() || serials == null || serials.isEmpty()) {
			return 0;
		}
		Map<String, Integer> latestStatusByDeviceUser = loadLatestEnableStatusByDeviceUser(changedStatuses.keySet(),
				serials);
		int queued = 0;
		int skippedDuplicate = 0;
		List<MachineCommand> batch = new ArrayList<MachineCommand>(BATCH_SIZE);
		for (Map.Entry<Long, Integer> entry : changedStatuses.entrySet()) {
			Long userId = entry.getKey();
			int enFlag = entry.getValue().intValue();
			for (String serial : serials) {
				String key = buildStatusKey(serial, userId.longValue());
				Integer latestFlag = latestStatusByDeviceUser.get(key);
				if (latestFlag != null && latestFlag.intValue() == enFlag) {
					skippedDuplicate++;
					continue;
				}
				batch.add(buildEnableCommand(userId, enFlag, serial));
				queued++;
				if (batch.size() >= BATCH_SIZE) {
					insertBatch(batch);
					batch.clear();
				}
			}
		}
		if (!batch.isEmpty()) {
			insertBatch(batch);
		}
		if (skippedDuplicate > 0) {
			log.info(
					"[DB-DELTA-SYNC] skipped duplicate enable/disable commands. skipped={}, changedUsers={}, devices={}",
					skippedDuplicate, changedStatuses.size(), serials.size());
		}
		return queued;
	}

	private int queueEnableDisablePhaseCommands(List<Long> userIds, int enFlag, List<String> serials) {
		if (userIds == null || userIds.isEmpty() || serials == null || serials.isEmpty()) {
			return 0;
		}
		int queued = 0;
		List<MachineCommand> batch = new ArrayList<MachineCommand>(BATCH_SIZE);
		for (Long userId : userIds) {
			if (userId == null) {
				continue;
			}
			for (String serial : serials) {
				batch.add(buildEnableCommand(userId, enFlag, serial));
				queued++;
				if (batch.size() >= BATCH_SIZE) {
					insertBatch(batch);
					batch.clear();
				}
			}
		}
		if (!batch.isEmpty()) {
			insertBatch(batch);
		}
		return queued;
	}

	private Map<Long, Integer> normalizeStatusMap(Map<Long, Integer> source) {
		Map<Long, Integer> normalized = new LinkedHashMap<Long, Integer>();
		if (source == null || source.isEmpty()) {
			return normalized;
		}
		for (Map.Entry<Long, Integer> entry : source.entrySet()) {
			Long userId = entry.getKey();
			if (userId == null) {
				continue;
			}
			normalized.put(userId, Integer.valueOf(normalizeEnableStatus(entry.getValue())));
		}
		return normalized;
	}

	private Map<Long, Integer> loadUserSyncStateByUser(Set<Long> userIds) {
		Map<Long, Integer> statusByUser = new LinkedHashMap<Long, Integer>();
		if (userIds == null || userIds.isEmpty()) {
			return statusByUser;
		}
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		ensureUserSyncStateTable(jdbcTemplate);
		List<Long> userIdList = new ArrayList<Long>(userIds);
		for (int start = 0; start < userIdList.size(); start += COMMAND_DEDUPE_USER_CHUNK) {
			int end = Math.min(start + COMMAND_DEDUPE_USER_CHUNK, userIdList.size());
			List<Long> chunk = userIdList.subList(start, end);
			String sql = "select USER_ID, LAST_SENT_STATUS from " + USER_SYNC_STATE_TABLE + " where USER_ID in ("
					+ buildPlaceholders(chunk.size()) + ")";
			List<Object> params = new ArrayList<Object>(chunk.size());
			params.addAll(chunk);
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
			for (Map<String, Object> row : rows) {
				Long userId = parseLong(toText(row.get("USER_ID")));
				Integer status = parseStatus(toText(row.get("LAST_SENT_STATUS")));
				if (userId != null && status != null) {
					statusByUser.put(userId, status);
				}
			}
		}
		return statusByUser;
	}

	private void saveUserSyncState(Map<Long, Integer> stateByUser) {
		if (stateByUser == null || stateByUser.isEmpty()) {
			return;
		}
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		ensureUserSyncStateTable(jdbcTemplate);
		List<Map.Entry<Long, Integer>> entries = new ArrayList<Map.Entry<Long, Integer>>(stateByUser.entrySet());
		final String sql = "MERGE " + USER_SYNC_STATE_TABLE + " AS target "
				+ "USING (SELECT ? AS USER_ID, ? AS LAST_SENT_STATUS) AS src "
				+ "ON target.USER_ID = src.USER_ID "
				+ "WHEN MATCHED THEN UPDATE SET LAST_SENT_STATUS = src.LAST_SENT_STATUS, LAST_UPDATED_AT = SYSDATETIME() "
				+ "WHEN NOT MATCHED THEN INSERT (USER_ID, LAST_SENT_STATUS, LAST_UPDATED_AT) "
				+ "VALUES (src.USER_ID, src.LAST_SENT_STATUS, SYSDATETIME());";
		jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps, int i) throws SQLException {
				Map.Entry<Long, Integer> entry = entries.get(i);
				ps.setLong(1, entry.getKey().longValue());
				ps.setInt(2, normalizeEnableStatus(entry.getValue()));
			}

			@Override
			public int getBatchSize() {
				return entries.size();
			}
		});
	}

	private void ensureUserSyncStateTable(JdbcTemplate jdbcTemplate) {
		if (userSyncStateTableEnsured) {
			return;
		}
		String ddl = "IF OBJECT_ID('dbo." + USER_SYNC_STATE_TABLE + "', 'U') IS NULL "
				+ "BEGIN "
				+ "CREATE TABLE dbo." + USER_SYNC_STATE_TABLE + " ("
				+ "USER_ID BIGINT NOT NULL PRIMARY KEY, "
				+ "LAST_SENT_STATUS INT NOT NULL, "
				+ "LAST_UPDATED_AT DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()"
				+ "); "
				+ "END; "
				+ "IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_" + USER_SYNC_STATE_TABLE
				+ "_UPDATED_AT' AND object_id = OBJECT_ID('dbo." + USER_SYNC_STATE_TABLE + "')) "
				+ "BEGIN "
				+ "CREATE INDEX IX_" + USER_SYNC_STATE_TABLE + "_UPDATED_AT ON dbo." + USER_SYNC_STATE_TABLE
				+ " (LAST_UPDATED_AT DESC); "
				+ "END;";
		jdbcTemplate.execute(ddl);
		userSyncStateTableEnsured = true;
	}

	private Map<String, Integer> loadLatestEnableStatusByDeviceUser(Set<Long> userIds, List<String> serials) {
		Map<String, Integer> latestStatus = new LinkedHashMap<String, Integer>();
		if (userIds == null || userIds.isEmpty() || serials == null || serials.isEmpty()) {
			return latestStatus;
		}
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		List<Long> userIdList = new ArrayList<Long>(userIds);
		for (int start = 0; start < userIdList.size(); start += COMMAND_DEDUPE_USER_CHUNK) {
			int end = Math.min(start + COMMAND_DEDUPE_USER_CHUNK, userIdList.size());
			List<Long> chunk = userIdList.subList(start, end);
			queryLatestEnableStatusChunk(jdbcTemplate, serials, chunk, latestStatus);
		}
		return latestStatus;
	}

	private void queryLatestEnableStatusChunk(JdbcTemplate jdbcTemplate, List<String> serials, List<Long> userIds,
			Map<String, Integer> latestStatus) {
		if (userIds == null || userIds.isEmpty() || serials == null || serials.isEmpty()) {
			return;
		}
		StringBuilder sql = new StringBuilder();
		sql.append("select SLNO as serial, CAST(DC_CMD AS NVARCHAR(MAX)) as content, ");
		sql.append(
				"ISNULL(CASE WHEN ISNUMERIC(DC_RES) = 1 THEN CONVERT(INT, DC_RES) ELSE NULL END, 0) as cmd_status, ");
		sql.append("DC_ID as cmd_id ");
		sql.append("from DEVICECMD ");
		sql.append("where CMD_DESC = 'JAVA:enableuser' ");
		sql.append("and SLNO in (").append(buildPlaceholders(serials.size())).append(") ");
		sql.append("and (").append(buildEnrollIdLikePredicates(userIds.size())).append(") ");
		sql.append("order by DC_ID desc");

		List<Object> params = new ArrayList<Object>(serials.size() + userIds.size());
		params.addAll(serials);
		for (Long userId : userIds) {
			params.add("%\"enrollid\":" + userId + "%");
		}

		Set<Long> userSet = new HashSet<Long>(userIds);
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
		for (Map<String, Object> row : rows) {
			String serial = toText(row.get("serial"));
			if (!hasText(serial)) {
				continue;
			}
			int cmdStatus = toInt(row.get("cmd_status"), -1);
			if (cmdStatus != 0 && cmdStatus != 1) {
				continue;
			}
			String content = toText(row.get("content"));
			if (!hasText(content)) {
				continue;
			}
			Long enrollId = extractLong(content, ENABLE_CMD_ENROLL_PATTERN);
			if (enrollId == null || !userSet.contains(enrollId)) {
				continue;
			}
			Integer enFlag = extractInt(content, ENABLE_CMD_ENFLAG_PATTERN);
			if (enFlag == null || (enFlag.intValue() != 0 && enFlag.intValue() != 1)) {
				continue;
			}
			String key = buildStatusKey(serial.trim(), enrollId.longValue());
			if (!latestStatus.containsKey(key)) {
				latestStatus.put(key, enFlag);
			}
		}
	}

	private String buildPlaceholders(int size) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < size; i++) {
			if (i > 0) {
				sb.append(",");
			}
			sb.append("?");
		}
		return sb.toString();
	}

	private String buildEnrollIdLikePredicates(int size) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < size; i++) {
			if (i > 0) {
				sb.append(" or ");
			}
			sb.append("CAST(DC_CMD AS NVARCHAR(MAX)) like ?");
		}
		return sb.toString();
	}

	private String buildStatusKey(String serial, long userId) {
		return serial + "|" + userId;
	}

	private String toText(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private int toInt(Object value, int defaultValue) {
		if (value == null) {
			return defaultValue;
		}
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		try {
			return Integer.parseInt(String.valueOf(value).trim());
		} catch (Exception ex) {
			return defaultValue;
		}
	}

	private Long extractLong(String text, Pattern pattern) {
		if (text == null || pattern == null) {
			return null;
		}
		Matcher matcher = pattern.matcher(text);
		if (!matcher.find()) {
			return null;
		}
		try {
			return Long.valueOf(matcher.group(1));
		} catch (Exception ex) {
			return null;
		}
	}

	private Integer extractInt(String text, Pattern pattern) {
		if (text == null || pattern == null) {
			return null;
		}
		Matcher matcher = pattern.matcher(text);
		if (!matcher.find()) {
			return null;
		}
		try {
			return Integer.valueOf(matcher.group(1));
		} catch (Exception ex) {
			return null;
		}
	}

	private int queueDeleteCommands(Set<Long> newlyDeleted, List<String> serials) {
		if (newlyDeleted == null || newlyDeleted.isEmpty() || serials == null || serials.isEmpty()) {
			return 0;
		}
		int queued = 0;
		List<MachineCommand> batch = new ArrayList<MachineCommand>(BATCH_SIZE);
		for (Long userId : newlyDeleted) {
			for (String serial : serials) {
				batch.add(buildDeleteCommand(userId, serial));
				queued++;
				if (batch.size() >= BATCH_SIZE) {
					insertBatch(batch);
					batch.clear();
				}
			}
		}
		if (!batch.isEmpty()) {
			insertBatch(batch);
		}
		return queued;
	}

	private MachineCommand buildEnableCommand(Long userId, int enFlag, String serial) {
		MachineCommand command = new MachineCommand();
		command.setSerial(serial);
		command.setName("enableuser");
		command.setContent("{\"cmd\":\"enableuser\",\"enrollid\":" + userId + ",\"enrolled\":" + userId + ",\"enflag\":"
				+ enFlag + "}");
		command.setStatus(0);
		command.setSendStatus(0);
		command.setErrCount(0);
		command.setGmtCrate(new Date());
		command.setGmtModified(new Date());
		return command;
	}

	private MachineCommand buildDeleteCommand(Long userId, String serial) {
		MachineCommand command = new MachineCommand();
		command.setSerial(serial);
		command.setName("deleteuser");
		command.setContent("{\"cmd\":\"deleteuser\",\"enrollid\":" + userId + ",\"backupnum\":13}");
		command.setStatus(0);
		command.setSendStatus(0);
		command.setErrCount(0);
		command.setGmtCrate(new Date());
		command.setGmtModified(new Date());
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
				if (key.startsWith(ACTIVE_PREFIX)) {
					Long userId = parseLong(key.substring(ACTIVE_PREFIX.length()));
					Integer status = parseStatus(properties.getProperty(key));
					if (userId != null && status != null) {
						snapshot.activeStatuses.put(userId, status);
					}
				} else if (key.startsWith(DELETED_PREFIX)) {
					Long userId = parseLong(key.substring(DELETED_PREFIX.length()));
					if (userId != null) {
						snapshot.deletedUserIds.add(userId);
					}
				}
			}
		} catch (Exception ignore) {
			snapshot.activeStatuses.clear();
			snapshot.deletedUserIds.clear();
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

	private void saveSnapshot(Map<Long, Integer> activeStatuses, Set<Long> deletedUserIds) throws Exception {
		File file = getSnapshotFile();
		File parent = file.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}
		Properties properties = new Properties();
		for (Map.Entry<Long, Integer> entry : activeStatuses.entrySet()) {
			properties.setProperty(ACTIVE_PREFIX + entry.getKey(), String.valueOf(entry.getValue()));
		}
		for (Long deletedId : deletedUserIds) {
			properties.setProperty(DELETED_PREFIX + deletedId, "1");
		}
		properties.setProperty("meta.updatedAt", String.valueOf(System.currentTimeMillis()));

		FileOutputStream output = null;
		try {
			output = new FileOutputStream(file);
			properties.store(output, "IDSL User Status Snapshot");
		} finally {
			if (output != null) {
				try {
					output.close();
				} catch (Exception ignore) {
				}
			}
		}
	}

	private File getSnapshotFile() {
		String tempDir = System.getProperty("java.io.tmpdir");
		File dir = new File(tempDir, "idsl-sync");
		return new File(dir, SNAPSHOT_FILE_NAME);
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

	private long estimateDeviceDispatchSeconds(int commandsPerDevice) {
		if (commandsPerDevice <= 0) {
			return 0L;
		}
		return (long) Math.ceil((double) commandsPerDevice / (double) ESTIMATED_COMMANDS_PER_SECOND_PER_DEVICE);
	}

	private Long parseLong(String value) {
		try {
			return Long.valueOf(value);
		} catch (Exception ex) {
			return null;
		}
	}

	private Integer parseStatus(String value) {
		try {
			int status = Integer.parseInt(value);
			return Integer.valueOf(status == 0 ? 0 : 1);
		} catch (Exception ex) {
			return null;
		}
	}

	private boolean hasText(String text) {
		return text != null && text.trim().length() > 0;
	}

	private static final class Snapshot {
		private boolean loadedFromFile;
		private final Map<Long, Integer> activeStatuses = new LinkedHashMap<Long, Integer>();
		private final Set<Long> deletedUserIds = new LinkedHashSet<Long>();
	}

	public static class SyncResult {
		private boolean success;
		private String trigger;
		private String error;
		private String snapshotFile;
		private int devices;
		private int activeUsers;
		private int deletedUsers;
		private int changedStatusUsers;
		private int changedDeletedUsers;
		private int statusCommandsQueued;
		private int deleteCommandsQueued;
		private int totalCommandsQueued;
		private int enabledUsers;
		private int disabledUsers;
		private int enableCommandsQueued;
		private int disableCommandsQueued;
		private int onlineDevices;
		private long estimatedDeviceDispatchSeconds;
		private long durationMs;
		private boolean snapshotLoaded;
		private String reason;
		private Date startedAt;
		private Date finishedAt;

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

		public String getSnapshotFile() {
			return snapshotFile;
		}

		public void setSnapshotFile(String snapshotFile) {
			this.snapshotFile = snapshotFile;
		}

		public int getDevices() {
			return devices;
		}

		public void setDevices(int devices) {
			this.devices = devices;
		}

		public int getActiveUsers() {
			return activeUsers;
		}

		public void setActiveUsers(int activeUsers) {
			this.activeUsers = activeUsers;
		}

		public int getDeletedUsers() {
			return deletedUsers;
		}

		public void setDeletedUsers(int deletedUsers) {
			this.deletedUsers = deletedUsers;
		}

		public int getChangedStatusUsers() {
			return changedStatusUsers;
		}

		public void setChangedStatusUsers(int changedStatusUsers) {
			this.changedStatusUsers = changedStatusUsers;
		}

		public int getChangedDeletedUsers() {
			return changedDeletedUsers;
		}

		public void setChangedDeletedUsers(int changedDeletedUsers) {
			this.changedDeletedUsers = changedDeletedUsers;
		}

		public int getStatusCommandsQueued() {
			return statusCommandsQueued;
		}

		public void setStatusCommandsQueued(int statusCommandsQueued) {
			this.statusCommandsQueued = statusCommandsQueued;
		}

		public int getDeleteCommandsQueued() {
			return deleteCommandsQueued;
		}

		public void setDeleteCommandsQueued(int deleteCommandsQueued) {
			this.deleteCommandsQueued = deleteCommandsQueued;
		}

		public int getTotalCommandsQueued() {
			return totalCommandsQueued;
		}

		public void setTotalCommandsQueued(int totalCommandsQueued) {
			this.totalCommandsQueued = totalCommandsQueued;
		}

		public int getEnabledUsers() {
			return enabledUsers;
		}

		public void setEnabledUsers(int enabledUsers) {
			this.enabledUsers = enabledUsers;
		}

		public int getDisabledUsers() {
			return disabledUsers;
		}

		public void setDisabledUsers(int disabledUsers) {
			this.disabledUsers = disabledUsers;
		}

		public int getEnableCommandsQueued() {
			return enableCommandsQueued;
		}

		public void setEnableCommandsQueued(int enableCommandsQueued) {
			this.enableCommandsQueued = enableCommandsQueued;
		}

		public int getDisableCommandsQueued() {
			return disableCommandsQueued;
		}

		public void setDisableCommandsQueued(int disableCommandsQueued) {
			this.disableCommandsQueued = disableCommandsQueued;
		}

		public int getOnlineDevices() {
			return onlineDevices;
		}

		public void setOnlineDevices(int onlineDevices) {
			this.onlineDevices = onlineDevices;
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

		public String getReason() {
			return reason;
		}

		public void setReason(String reason) {
			this.reason = reason;
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
	}

}
