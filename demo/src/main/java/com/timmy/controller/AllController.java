package com.timmy.controller;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.timmy.entity.Device;
import com.timmy.entity.EnrollInfo;
import com.timmy.entity.MachineCommand;
import com.timmy.entity.Msg;
import com.timmy.entity.Person;
import com.timmy.entity.PersonTemp;
import com.timmy.entity.Records;
import com.timmy.entity.UserInfo;
import com.timmy.serviceImpl.DatabaseUserDeltaSyncService;
import com.timmy.util.ControllerBase;
import com.timmy.util.ImageProcess;
import com.timmy.websocket.WebSocketPool;

@Controller

public class AllController extends ControllerBase {
	private static final Logger log = LoggerFactory.getLogger(AllController.class);

	@Autowired
	private DataSource dataSource;

	@Autowired
	private DatabaseUserDeltaSyncService databaseUserDeltaSyncService;

	/*
	 * @Autowired EnrollInfoService enrollInfoService;
	 */
	private static final String PERSON_PHOTO_DIR = "C:/dynamicface/picture/";
	private static final int PASSWORD_MAX_LENGTH = 10;
	private static final int CARD_MAX_LENGTH = 20;
	private static final String SYNC_TARGET_ALL = "all";
	private static final int BULK_SYNC_INSERT_BATCH_SIZE = 500;
	private static final String DB_SYNC_STATE_IDLE = "IDLE";
	private static final String DB_SYNC_STATE_RUNNING = "RUNNING";
	private static final String DB_SYNC_STATE_SUCCESS = "SUCCESS";
	private static final String DB_SYNC_STATE_FAILED = "FAILED";
	// Relay-only mode: block backup/user sync APIs to prevent repeated setuserinfo traffic.
	private static final boolean BACKUP_SYNC_ENABLED = false;
	private final AtomicBoolean dbSyncRunning = new AtomicBoolean(false);
	private volatile String dbSyncState = DB_SYNC_STATE_IDLE;
	private volatile String dbSyncMessage = "Not started.";
	private volatile long dbSyncStartedAtEpochMs = 0L;
	private volatile long dbSyncFinishedAtEpochMs = 0L;
	private volatile int dbSyncDevices = 0;
	private volatile int dbSyncOnlineDevices = 0;
	private volatile int dbSyncActiveUsers = 0;
	private volatile int dbSyncDeletedUsers = 0;
	private volatile int dbSyncChangedStatusUsers = 0;
	private volatile int dbSyncChangedDeletedUsers = 0;
	private volatile int dbSyncQueuedCommands = 0;
	private volatile long dbSyncDurationMs = 0L;
	private volatile long dbSyncEstimatedDeviceDispatchSeconds = 0L;

	@RequestMapping("/hello1")
	public String hello() {
		return "hello";
	}

	@RequestMapping(value = "/climsRecordsPage", method = RequestMethod.GET)
	public String climsRecordsPage() {
		return "climsRecords";
	}

	/* èŽ·å–æ‰€æœ‰è€ƒå‹¤æœº */
	@ResponseBody
	@RequestMapping(value = "/device", method = RequestMethod.GET)
	public Msg getAllDevice() {
		List<Device> deviceList = deviceService.findAllDevice();
		return Msg.success().add("device", deviceList);
	}

	/* èŽ·å–æ‰€æœ‰è€ƒå‹¤æœº */
	@ResponseBody
	@RequestMapping(value = "/enrollInfo", method = RequestMethod.GET)
	public Msg getAllEnrollInfo() {
		List<Person> enrollInfo = personService.selectAll();

		return Msg.success().add("enrollInfo", enrollInfo);
	}

	/* é‡‡é›†æ‰€æœ‰çš„ç”¨æˆ· */
	@ResponseBody
	@RequestMapping(value = "/sendWs", method = RequestMethod.GET)
	public Msg sendWs(@RequestParam("deviceSn") String deviceSn) {
		if (!BACKUP_SYNC_ENABLED) {
			return Msg.fail().add("error", "Backup sync is disabled in relay-only mode.");
		}
		String message = "{\"cmd\":\"getuserlist\",\"stn\":true}";

		System.out.println("sss" + deviceSn);

		// WebSocketPool.sendMessageToDeviceStatus(deviceSn, message);
		List<Device> deviceList = deviceService.findAllDevice();
		for (int i = 0; i < deviceList.size(); i++) {
			MachineCommand machineCommand = new MachineCommand();
			machineCommand.setContent(message);
			machineCommand.setName("getuserlist");
			machineCommand.setStatus(0);
			machineCommand.setSendStatus(0);
			machineCommand.setErrCount(0);
			machineCommand.setSerial(deviceList.get(i).getSerialNum());
			machineCommand.setGmtCrate(new Date());
			machineCommand.setGmtModified(new Date());
			machineCommand.setContent(message);
			machineComandService.addMachineCommand(machineCommand);
		}

		return Msg.success();
	}

	@ResponseBody
	@RequestMapping(value = "addPerson", method = RequestMethod.POST)
	public Msg addPerson(PersonTemp personTemp, @RequestParam(value = "pic", required = false) MultipartFile pic) throws Exception {
		if (personTemp != null && personTemp.getUserId() != null) {
			upsertPersonInClientDb(personTemp, pic);
			return Msg.success();
		}

		String path = "C:/dynamicface/picture/";
		System.out.println("å›¾ç‰‡çœŸå®žè·¯å¾„" + path);
		System.out.println("æ–°å¢žäººå‘˜ä¿¡æ¯===================" + personTemp);
		String photoName = "";
		String newName = "";
		// EnrollInfo enrollInfo=new EnrollInfo();
		if (pic != null) {
			if (pic.getOriginalFilename() != null && !("").equals(pic.getOriginalFilename())) {
				photoName = pic.getOriginalFilename();
				newName = UUID.randomUUID().toString() + photoName.substring(photoName.lastIndexOf("."));
				File photoFile = new File(path, newName);
				if (!photoFile.exists()) {
					photoFile.mkdirs();
				}
				pic.transferTo(photoFile);

			}

		}
		Person person = new Person();
		person.setId(personTemp.getUserId());
		person.setName(personTemp.getName());
		person.setRollId(personTemp.getPrivilege());
		Person person2 = personService.selectByPrimaryKey(personTemp.getUserId());
		if (person2 == null) {
			personService.insert(person);
		}
		if (personTemp.getPassword() != null && !personTemp.getPassword().equals("")) {
			EnrollInfo enrollInfoTemp2 = new EnrollInfo();
			enrollInfoTemp2.setBackupnum(10);
			enrollInfoTemp2.setEnrollId(personTemp.getUserId());
			enrollInfoTemp2.setSignatures(personTemp.getPassword());
			enrollInfoService.insertSelective(enrollInfoTemp2);
		}
		if (personTemp.getCardNum() != null && !personTemp.getCardNum().equals("")) {
			EnrollInfo enrollInfoTemp3 = new EnrollInfo();
			enrollInfoTemp3.setBackupnum(11);
			enrollInfoTemp3.setEnrollId(personTemp.getUserId());
			enrollInfoTemp3.setSignatures(personTemp.getCardNum());
			enrollInfoService.insertSelective(enrollInfoTemp3);
		}

		if (newName != null && !newName.equals("")) {
			EnrollInfo enrollInfoTemp = new EnrollInfo();
			enrollInfoTemp.setBackupnum(50);
			enrollInfoTemp.setEnrollId(personTemp.getUserId());
			String base64Str = ImageProcess.imageToBase64Str("C:/dynamicface/picture/" + newName);
			enrollInfoTemp.setImagePath(newName);
			enrollInfoTemp.setSignatures(base64Str);
			System.out.println("å›¾ç‰‡æ•°æ®é•¿åº¦" + base64Str.length());
			enrollInfoService.insertSelective(enrollInfoTemp);
		}

		return Msg.success();

	}

	@ResponseBody
	@RequestMapping(value = "savePerson", method = RequestMethod.POST)
	public Msg savePerson(PersonTemp personTemp, @RequestParam(value = "pic", required = false) MultipartFile pic,
			@RequestParam(value = "deviceSn", required = false) String deviceSn,
			@RequestParam(value = "syncTarget", required = false) String syncTarget) {
		if (personTemp == null || personTemp.getUserId() == null) {
			return Msg.fail().add("error", "UserId is required.");
		}
		try {
			upsertPersonInClientDb(personTemp, pic);
			boolean syncQueued = queueUserSyncToDevice(personTemp, deviceSn, syncTarget);
			boolean hasFaceTemplate = hasFaceTemplate(personTemp.getUserId());
			boolean hasFaceData = hasFaceData(personTemp.getUserId());
			return Msg.success().add("syncQueued", syncQueued).add("hasFaceTemplate", hasFaceTemplate)
					.add("hasFaceData", hasFaceData);
		} catch (Exception ex) {
			ex.printStackTrace();
			String detail = ex.getMessage();
			if (detail == null || detail.trim().isEmpty()) {
				detail = ex.getClass().getSimpleName();
			}
			return Msg.fail().add("error", detail);
		}
	}

	@ResponseBody
	@RequestMapping(value = "personDetail", method = RequestMethod.GET)
	public Msg personDetail(@RequestParam("enrollId") Long enrollId) {
		if (enrollId == null) {
			return Msg.fail().add("error", "enrollId is required.");
		}
		Person person = personService.selectByPrimaryKey(enrollId);
		if (person == null) {
			return Msg.fail().add("error", "User not found.");
		}
		PersonTemp personTemp = new PersonTemp();
		personTemp.setUserId(person.getId());
		personTemp.setName(person.getName());
		personTemp.setPrivilege(person.getRollId() == null ? 0 : person.getRollId());

		EnrollInfo passwordInfo = enrollInfoService.selectByBackupnum(enrollId, 10);
		if (passwordInfo != null && hasText(passwordInfo.getSignatures())) {
			personTemp.setPassword(truncate(normalizeText(passwordInfo.getSignatures()), PASSWORD_MAX_LENGTH));
		}
		EnrollInfo cardInfo = enrollInfoService.selectByBackupnum(enrollId, 11);
		if (cardInfo != null && hasText(cardInfo.getSignatures())) {
			personTemp.setCardNum(truncate(normalizeText(cardInfo.getSignatures()), CARD_MAX_LENGTH));
		}

		return Msg.success().add("person", personTemp);
	}

	private void upsertPersonInClientDb(PersonTemp personTemp, MultipartFile pic) throws Exception {
		Person person = new Person();
		person.setId(personTemp.getUserId());
		person.setName(personTemp.getName());
		person.setRollId(personTemp.getPrivilege());
		if (personService.selectByPrimaryKey(personTemp.getUserId()) == null) {
			personService.insert(person);
		} else {
			personService.updateByPrimaryKey(person);
		}

		upsertTextBackupWithFallback(personTemp.getPassword(), personTemp.getUserId(), 10,
				new int[] { PASSWORD_MAX_LENGTH, 8, 6, 4, 2, 1 });
		upsertTextBackupWithFallback(personTemp.getCardNum(), personTemp.getUserId(), 11,
				new int[] { CARD_MAX_LENGTH, 16, 12, 10, 8, 6, 4, 2, 1 });
		if (pic != null && hasText(pic.getOriginalFilename())) {
			String base64Photo = savePhotoAsBase64(pic);
			enrollInfoService.updateByEnrollIdAndBackupNum(base64Photo, personTemp.getUserId(), 50);
		}
	}

	private void upsertTextBackupWithFallback(String rawValue, Long userId, int backupNum, int[] maxLengths) {
		String normalized = normalizeText(rawValue);
		if (!hasText(normalized)) {
			return;
		}
		DataIntegrityViolationException lastException = null;
		for (int maxLength : maxLengths) {
			if (maxLength <= 0) {
				continue;
			}
			String attemptValue = truncate(normalized, maxLength);
			if (!hasText(attemptValue)) {
				continue;
			}
			try {
				enrollInfoService.updateByEnrollIdAndBackupNum(attemptValue, userId, backupNum);
				return;
			} catch (DataIntegrityViolationException ex) {
				lastException = ex;
			}
		}
		if (lastException != null) {
			throw lastException;
		}
	}

	private boolean queueUserSyncToDevice(PersonTemp personTemp, String deviceSn, String syncTarget) {
		Person persisted = personService.selectByPrimaryKey(personTemp.getUserId());
		if (persisted == null) {
			return false;
		}
		String name = hasText(persisted.getName()) ? persisted.getName() : String.valueOf(personTemp.getUserId());
		int admin = persisted.getRollId() == null ? 0 : persisted.getRollId();
		int enFlag = normalizeEnableStatus(persisted.getStatus());

		String normalizedTarget = normalizeText(syncTarget);
		if (SYNC_TARGET_ALL.equalsIgnoreCase(normalizedTarget)) {
			List<Device> devices = deviceService.findAllDevice();
			if (devices == null || devices.isEmpty()) {
				return false;
			}
			boolean queued = false;
			for (Device device : devices) {
				if (device == null || !hasText(device.getSerialNum())) {
					continue;
				}
				if (queueUserSyncToSingleDevice(personTemp.getUserId(), name, admin, enFlag, device.getSerialNum().trim())) {
					queued = true;
				}
			}
			return queued;
		}

		if (!hasText(deviceSn)) {
			return false;
		}
		return queueUserSyncToSingleDevice(personTemp.getUserId(), name, admin, enFlag, deviceSn.trim());
	}

	private boolean queueUserSyncToSingleDevice(Long userId, String name, int admin, int enFlag, String deviceSn) {
		// Latest-only sync for device recognition:
		// send name plus available face data for this single user only.
		personService.setUserToDevice(userId, name, -1, admin, "", deviceSn);
		queueFaceToDevice(userId, name, admin, deviceSn);
		queueUserEnableByStatus(userId, enFlag, deviceSn);
		return true;
	}

	private int normalizeEnableStatus(Integer status) {
		return (status != null && status.intValue() == 0) ? 0 : 1;
	}

	private void queueUserEnableByStatus(Long enrollId, int enFlag, String deviceSn) {
		if (!hasText(deviceSn)) {
			return;
		}
		String message = "{\"cmd\":\"enableuser\",\"enrollid\":" + enrollId + ",\"enrolled\":" + enrollId
				+ ",\"enflag\":" + enFlag + "}";
		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message);
		machineCommand.setName("enableuser");
		machineCommand.setStatus(0);
		machineCommand.setSendStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSerial(deviceSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());
		machineComandService.addMachineCommand(machineCommand);
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

	private int queueUserEnableByStatusOnAllDevices(Long enrollId, int enFlag) {
		List<String> serials = getAllKnownDeviceSerials();
		for (String serial : serials) {
			queueUserEnableByStatus(enrollId, enFlag, serial);
		}
		return serials.size();
	}

	private void queueDeleteUserFromDevice(Long enrollId, String deviceSn) {
		if (enrollId == null || !hasText(deviceSn)) {
			return;
		}
		String message = "{\"cmd\":\"deleteuser\",\"enrollid\":" + enrollId + ",\"backupnum\":13}";
		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message);
		machineCommand.setName("deleteuser");
		machineCommand.setStatus(0);
		machineCommand.setSendStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSerial(deviceSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());
		machineComandService.addMachineCommand(machineCommand);
	}

	private int queueUsersStatusSyncOnAllDevices(List<Person> persons, List<String> serials) {
		if (persons == null || serials == null || serials.isEmpty()) {
			return 0;
		}
		int queued = 0;
		List<MachineCommand> batch = new ArrayList<MachineCommand>(BULK_SYNC_INSERT_BATCH_SIZE);
		for (Person person : persons) {
			if (person == null || person.getId() == null) {
				continue;
			}
			int enFlag = normalizeEnableStatus(person.getStatus());
			for (String serial : serials) {
				batch.add(buildEnableUserCommand(person.getId(), enFlag, serial));
				queued++;
				if (batch.size() >= BULK_SYNC_INSERT_BATCH_SIZE) {
					insertMachineCommandsBatch(batch);
					batch.clear();
				}
			}
		}
		if (!batch.isEmpty()) {
			insertMachineCommandsBatch(batch);
		}
		return queued;
	}

	private int queueDeletedUsersRemovalOnAllDevices(List<Long> deletedUserIds, List<String> serials) {
		if (deletedUserIds == null || serials == null || serials.isEmpty()) {
			return 0;
		}
		int queued = 0;
		List<MachineCommand> batch = new ArrayList<MachineCommand>(BULK_SYNC_INSERT_BATCH_SIZE);
		for (Long deletedUserId : deletedUserIds) {
			if (deletedUserId == null) {
				continue;
			}
			for (String serial : serials) {
				batch.add(buildDeleteUserCommand(deletedUserId, serial));
				queued++;
				if (batch.size() >= BULK_SYNC_INSERT_BATCH_SIZE) {
					insertMachineCommandsBatch(batch);
					batch.clear();
				}
			}
		}
		if (!batch.isEmpty()) {
			insertMachineCommandsBatch(batch);
		}
		return queued;
	}

	private MachineCommand buildEnableUserCommand(Long enrollId, int enFlag, String deviceSn) {
		String message = "{\"cmd\":\"enableuser\",\"enrollid\":" + enrollId + ",\"enrolled\":" + enrollId
				+ ",\"enflag\":" + enFlag + "}";
		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message);
		machineCommand.setName("enableuser");
		machineCommand.setStatus(0);
		machineCommand.setSendStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSerial(deviceSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());
		return machineCommand;
	}

	private MachineCommand buildDeleteUserCommand(Long enrollId, String deviceSn) {
		String message = "{\"cmd\":\"deleteuser\",\"enrollid\":" + enrollId + ",\"backupnum\":13}";
		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message);
		machineCommand.setName("deleteuser");
		machineCommand.setStatus(0);
		machineCommand.setSendStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSerial(deviceSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());
		return machineCommand;
	}

	private int insertMachineCommandsBatch(final List<MachineCommand> commands) {
		if (commands == null || commands.isEmpty()) {
			return 0;
		}
		final String sql = "insert into DEVICECMD (SLNO, DC_CMD, DC_DATE, DC_EXECDATE, DC_RES, DC_RESDATE, CMD_DESC, REF_ID, src_sno, DC_cmd_date, IS_DEL_EXECUTED) "
				+ "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		int[] rows = jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps, int i) throws SQLException {
				MachineCommand command = commands.get(i);
				Timestamp createdAt = new Timestamp(
						(command.getGmtCrate() == null ? System.currentTimeMillis() : command.getGmtCrate().getTime()));
				Timestamp modifiedAt = new Timestamp((command.getGmtModified() == null ? createdAt.getTime()
						: command.getGmtModified().getTime()));
				ps.setString(1, command.getSerial());
				ps.setString(2, command.getContent());
				ps.setTimestamp(3, createdAt);
				if (command.getRunTime() == null) {
					ps.setNull(4, Types.TIMESTAMP);
				} else {
					ps.setTimestamp(4, new Timestamp(command.getRunTime().getTime()));
				}
				ps.setString(5, String.valueOf(command.getStatus() == null ? 0 : command.getStatus().intValue()));
				ps.setNull(6, Types.TIMESTAMP);
				ps.setString(7, "JAVA:" + (command.getName() == null ? "command" : command.getName()));
				ps.setInt(8, command.getErrCount() == null ? 0 : command.getErrCount().intValue());
				ps.setNull(9, Types.VARCHAR);
				ps.setTimestamp(10, modifiedAt);
				ps.setInt(11, (command.getSendStatus() != null && command.getSendStatus().intValue() == 1) ? 1 : 0);
			}

			@Override
			public int getBatchSize() {
				return commands.size();
			}
		});
		return rows == null ? 0 : rows.length;
	}

	private static final class DatabaseSyncSummary {
		private int devices;
		private int onlineDevices;
		private int activeUsers;
		private int deletedUsers;
		private int changedStatusUsers;
		private int changedDeletedUsers;
		private int statusCommandsQueued;
		private int deleteCommandsQueued;
		private int totalCommandsQueued;
		private long durationMs;
		private long estimatedDeviceDispatchSeconds;
		private String reason;
	}

	private DatabaseSyncSummary runDatabaseSyncNow() {
		DatabaseUserDeltaSyncService.SyncResult syncResult = databaseUserDeltaSyncService
				.syncChangedUsersToAllDevices("manual");
		if (!syncResult.isSuccess()) {
			throw new IllegalStateException(syncResult.getError());
		}
		DatabaseSyncSummary summary = new DatabaseSyncSummary();
		summary.devices = syncResult.getDevices();
		summary.onlineDevices = syncResult.getOnlineDevices();
		summary.activeUsers = syncResult.getActiveUsers();
		summary.deletedUsers = syncResult.getDeletedUsers();
		summary.changedStatusUsers = syncResult.getChangedStatusUsers();
		summary.changedDeletedUsers = syncResult.getChangedDeletedUsers();
		summary.statusCommandsQueued = syncResult.getStatusCommandsQueued();
		summary.deleteCommandsQueued = syncResult.getDeleteCommandsQueued();
		summary.totalCommandsQueued = syncResult.getTotalCommandsQueued();
		summary.durationMs = syncResult.getDurationMs();
		summary.estimatedDeviceDispatchSeconds = syncResult.getEstimatedDeviceDispatchSeconds();
		summary.reason = syncResult.getReason();
		return summary;
	}

	private Msg getDatabaseSyncStatusMsg() {
		Msg msg = Msg.success().add("running", dbSyncRunning.get()).add("state", dbSyncState).add("message", dbSyncMessage)
				.add("devices", dbSyncDevices).add("onlineDevices", dbSyncOnlineDevices)
				.add("activeUsers", dbSyncActiveUsers).add("deletedUsers", dbSyncDeletedUsers)
				.add("changedStatusUsers", dbSyncChangedStatusUsers).add("changedDeletedUsers", dbSyncChangedDeletedUsers)
				.add("totalCommandsQueued", dbSyncQueuedCommands).add("durationMs", dbSyncDurationMs)
				.add("estimatedDeviceDispatchSeconds", dbSyncEstimatedDeviceDispatchSeconds);
		if (dbSyncStartedAtEpochMs > 0L) {
			msg.add("startedAt", new Date(dbSyncStartedAtEpochMs));
		}
		if (dbSyncFinishedAtEpochMs > 0L) {
			msg.add("finishedAt", new Date(dbSyncFinishedAtEpochMs));
		}
		return msg;
	}

	private boolean triggerDatabaseSyncAsync(final String trigger) {
		if (!dbSyncRunning.compareAndSet(false, true)) {
			log.warn("[DB-DELTA-SYNC] trigger ignored because sync is already running. trigger={}", trigger);
			return false;
		}
		dbSyncState = DB_SYNC_STATE_RUNNING;
		dbSyncMessage = "Sync started by " + trigger;
		dbSyncStartedAtEpochMs = System.currentTimeMillis();
		dbSyncFinishedAtEpochMs = 0L;
		dbSyncDevices = 0;
		dbSyncOnlineDevices = 0;
		dbSyncActiveUsers = 0;
		dbSyncDeletedUsers = 0;
		dbSyncChangedStatusUsers = 0;
		dbSyncChangedDeletedUsers = 0;
		dbSyncQueuedCommands = 0;
		dbSyncDurationMs = 0L;
		dbSyncEstimatedDeviceDispatchSeconds = 0L;
		log.info("[DB-DELTA-SYNC] async trigger accepted. trigger={}", trigger);
		Thread worker = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					log.info("[DB-DELTA-SYNC] worker started. trigger={}", trigger);
					DatabaseSyncSummary summary = runDatabaseSyncNow();
					dbSyncDevices = summary.devices;
					dbSyncOnlineDevices = summary.onlineDevices;
					dbSyncActiveUsers = summary.activeUsers;
					dbSyncDeletedUsers = summary.deletedUsers;
					dbSyncChangedStatusUsers = summary.changedStatusUsers;
					dbSyncChangedDeletedUsers = summary.changedDeletedUsers;
					dbSyncQueuedCommands = summary.totalCommandsQueued;
					dbSyncDurationMs = summary.durationMs;
					dbSyncEstimatedDeviceDispatchSeconds = summary.estimatedDeviceDispatchSeconds;
					dbSyncState = DB_SYNC_STATE_SUCCESS;
					StringBuilder messageBuilder = new StringBuilder();
					messageBuilder.append("Sync completed in ").append(summary.durationMs).append(" ms")
							.append(", changedStatusUsers=").append(summary.changedStatusUsers)
							.append(", changedDeletedUsers=").append(summary.changedDeletedUsers)
							.append(", queuedCommands=").append(summary.totalCommandsQueued);
					if (summary.estimatedDeviceDispatchSeconds > 0L) {
						messageBuilder.append(", estimatedDeviceDispatch=")
								.append(summary.estimatedDeviceDispatchSeconds).append(" sec");
					}
					if (summary.totalCommandsQueued == 0) {
						messageBuilder.append(", reason=No delta found in enable/disable or deleted users.");
					} else if (summary.onlineDevices <= 0) {
						messageBuilder.append(", reason=Commands queued but no websocket device is online.");
					}
					if (hasText(summary.reason)) {
						messageBuilder.append(", detail=").append(summary.reason);
					}
					dbSyncMessage = messageBuilder.toString();
					log.info("[DB-DELTA-SYNC] {}", dbSyncMessage);
				} catch (Exception ex) {
					dbSyncState = DB_SYNC_STATE_FAILED;
					dbSyncMessage = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
					log.error("[DB-DELTA-SYNC] sync failed. trigger={}, message={}", trigger, dbSyncMessage, ex);
				} finally {
					dbSyncFinishedAtEpochMs = System.currentTimeMillis();
					if (dbSyncDurationMs <= 0L && dbSyncStartedAtEpochMs > 0L) {
						dbSyncDurationMs = dbSyncFinishedAtEpochMs - dbSyncStartedAtEpochMs;
					}
					dbSyncRunning.set(false);
					log.info("[DB-DELTA-SYNC] worker finished. trigger={}, state={}, durationMs={}", trigger, dbSyncState,
							dbSyncDurationMs);
				}
			}
		});
		worker.setName("db-user-sync-worker");
		worker.setDaemon(true);
		worker.start();
		return true;
	}

	private boolean queueFaceToDevice(Long userId, String name, int admin, String deviceSn) {
		boolean queued = false;
		for (int backupNum = 20; backupNum <= 27; backupNum++) {
			if (queueBackupToDevice(userId, name, admin, backupNum, deviceSn)) {
				queued = true;
			}
		}
		if (!queued) {
			queued = queueBackupToDevice(userId, name, admin, 50, deviceSn);
		}
		return queued;
	}

	private boolean queueBackupToDevice(Long userId, String name, int admin, int backupNum, String deviceSn) {
		EnrollInfo info = enrollInfoService.selectByBackupnum(userId, backupNum);
		if (info != null && hasText(info.getSignatures())) {
			personService.setUserToDevice(userId, name, backupNum, admin, info.getSignatures(), deviceSn);
			return true;
		}
		return false;
	}

	private boolean hasFaceTemplate(Long userId) {
		int[] faceBackupNums = new int[] { 20, 21, 22, 23, 24, 25, 26, 27 };
		for (int backupNum : faceBackupNums) {
			EnrollInfo info = enrollInfoService.selectByBackupnum(userId, backupNum);
			if (info != null && hasText(info.getSignatures())) {
				return true;
			}
		}
		return false;
	}

	private boolean hasFaceData(Long userId) {
		if (hasFaceTemplate(userId)) {
			return true;
		}
		EnrollInfo photoInfo = enrollInfoService.selectByBackupnum(userId, 50);
		return photoInfo != null && hasText(photoInfo.getSignatures());
	}

	private String savePhotoAsBase64(MultipartFile pic) throws Exception {
		String photoName = pic.getOriginalFilename();
		String extension = "";
		int suffixIndex = photoName.lastIndexOf(".");
		if (suffixIndex >= 0) {
			extension = photoName.substring(suffixIndex);
		}
		String savedFileName = UUID.randomUUID().toString() + extension;
		File photoDir = new File(PERSON_PHOTO_DIR);
		if (!photoDir.exists()) {
			photoDir.mkdirs();
		}
		File photoFile = new File(photoDir, savedFileName);
		pic.transferTo(photoFile);
		return ImageProcess.imageToBase64Str(photoFile.getAbsolutePath());
	}

	private String normalizeText(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private String truncate(String value, int maxLength) {
		if (value == null) {
			return null;
		}
		if (maxLength <= 0 || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}

	private boolean hasText(String value) {
		return value != null && !value.trim().isEmpty();
	}

	@ResponseBody
	@RequestMapping(value = "getUserInfo", method = RequestMethod.GET)
	public Msg getUserInfo(@RequestParam("deviceSn") String deviceSn) {
		System.out.println("è¿›å…¥controller");
		List<Person> person = personService.selectAll();
		List<EnrollInfo> enrollsPrepared = new ArrayList<EnrollInfo>();
		for (int i = 0; i < person.size(); i++) {
			Long enrollId2 = person.get(i).getId();
			List<EnrollInfo> enrollInfos = enrollInfoService.selectByEnrollId(enrollId2);
			for (int j = 0; j < enrollInfos.size(); j++) {
				if (enrollInfos.get(j).getEnrollId() != null && enrollInfos.get(j).getBackupnum() != null) {
					enrollsPrepared.add(enrollInfos.get(j));
				}
			}
		}
		System.out.println("é‡‡é›†ç”¨æˆ·æ•°æ®" + enrollsPrepared);
		personService.getSignature2(enrollsPrepared, deviceSn);

		return Msg.success();
	}

	/* èŽ·å–å•ä¸ªç”¨æˆ· */
	@ResponseBody
	@RequestMapping("sendGetUserInfo")
	public Msg sendGetUserInfo(@RequestParam("enrollId") int enrollId, @RequestParam("backupNum") int backupNum,
			@RequestParam("deviceSn") String deviceSn) {
		if (!isSupportedEnrollBackupNum(backupNum)) {
			return Msg.fail().add("error", "Only face(20-27), password(10), card(11), and photo(50) are supported.");
		}

		List<Device> deviceList = deviceService.findAllDevice();
		System.out.println("è®¾å¤‡ä¿¡æ¯" + deviceList);

		machineComandService.addGetOneUserCommand(enrollId, backupNum, deviceSn);

		return Msg.success();
	}

	/* ä¸‹å‘æ‰€æœ‰ç”¨æˆ·ï¼Œé¢å‘é€‰ä¸­è€ƒå‹¤æœº */
	@ResponseBody
	@RequestMapping(value = "/setPersonToDevice", method = RequestMethod.GET)
	public Msg sendSetUserInfo(@RequestParam("deviceSn") String deviceSn) {
		if (!BACKUP_SYNC_ENABLED) {
			return Msg.fail().add("error", "Backup sync is disabled in relay-only mode.");
		}

		personService.setUserToDevice2(deviceSn);
		return Msg.success();

	}

	@ResponseBody
	@RequestMapping(value = "setUsernameToDevice", method = RequestMethod.GET)
	public Msg setUsernameToDevice(@RequestParam("deviceSn") String deviceSn) {
		personService.setUsernameToDevice(deviceSn);
		return Msg.success();
	}

	@ResponseBody
	@RequestMapping(value = "/getDeviceInfo", method = RequestMethod.GET)
	public Msg getDeviceInfo(@RequestParam("deviceSn") String deviceSn) {
		String message = "{\"cmd\":\"getdevinfo\"}";

		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message);
		machineCommand.setName("getdevinfo");
		machineCommand.setStatus(0);
		machineCommand.setSendStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSerial(deviceSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());

		machineComandService.addMachineCommand(machineCommand);
		return Msg.success();
	}

	/* ä¸‹å‘å•ä¸ªç”¨æˆ·åˆ°æœºå™¨ï¼Œå¯¹é€‰ä¸­è€ƒå‹¤æœº */
	@ResponseBody
	@RequestMapping(value = "/setOneUser", method = RequestMethod.GET)
	public Msg setOneUserTo(@RequestParam("enrollId") Long enrollId, @RequestParam("backupNum") int backupNum,
			@RequestParam("deviceSn") String deviceSn) {
		if (!BACKUP_SYNC_ENABLED && backupNum != -1 && !isFaceBackupNum(backupNum)) {
			return Msg.fail().add("error",
					"Relay-only mode allows single-user face sync only (backup 20-27 or 50).");
		}
		if (!isSupportedSetBackupNum(backupNum)) {
			return Msg.fail().add("error",
					"Only name(-1), face(20-27), password(10), card(11), and photo(50) are supported.");
		}
		Person person = new Person();
		person = personService.selectByPrimaryKey(enrollId);
		EnrollInfo enrollInfo = new EnrollInfo();
		System.out.println("ba" + backupNum);
		enrollInfo = enrollInfoService.selectByBackupnum(enrollId, backupNum);
		int enFlag = normalizeEnableStatus(person.getStatus());
		if (enrollInfo != null) {
			personService.setUserToDevice(enrollId, person.getName(), backupNum, person.getRollId(),
					enrollInfo.getSignatures(), deviceSn);
			queueUserEnableByStatus(enrollId, enFlag, deviceSn);
			return Msg.success();
		} else if (backupNum == -1) {
			personService.setUserToDevice(enrollId, person.getName(), backupNum, 0, "", deviceSn);
			queueUserEnableByStatus(enrollId, enFlag, deviceSn);
			return Msg.success();
		} else {
			return Msg.fail();
		}

	}

	/* ä»Žè€ƒå‹¤æœºåˆ é™¤ç”¨æˆ· */
	@ResponseBody
	@RequestMapping(value = "/deletePersonFromDevice", method = RequestMethod.GET)
	public Msg deleteDeviceUserInfo(@RequestParam("enrollId") Long enrollId,
			@RequestParam("deviceSn") String deviceSn) {

		System.out.println("åˆ é™¤ç”¨æˆ·devicesn===================" + deviceSn);
		personService.deleteUserInfoFromDevice(enrollId, deviceSn);
		// personService.deleteByPrimaryKey(enrollId);
		return Msg.success();
	}

	/* åˆå§‹åŒ–è€ƒå‹¤æœº */
	@ResponseBody
	@RequestMapping(value = "/initSystem", method = RequestMethod.GET)
	public Msg initSystem(@RequestParam("deviceSn") String deviceSn) {
		System.out.println("åˆå§‹åŒ–è¯·æ±‚");
		String message = "{\"cmd\":\"enabledevice\"}";
		String message2 = "{\"cmd\":\"settime\",\"cloudtime\":\"2020-12-23 13:49:30\"}";
		String s4 = "{\"cmd\":\"settime\",\"cloudtime\":\"2016-03-25 13:49:30\"}";
		String s2 = "{\"cmd\":\"setdevinfo\",\"deviceid\":1,\"language\":0,\"volume\":0,\"screensaver\":0,\"verifymode\":0,\"sleep\":0,\"userfpnum\":3,\"loghint\":1000,\"reverifytime\":0}";
		String s3 = "{\"cmd\":\"setdevlock\",\"opendelay\":5,\"doorsensor\":0,\"alarmdelay\":0,\"threat\":0,\"InputAlarm\":0,\"antpass\":0,\"interlock\":0,\"mutiopen\":0,\"tryalarm\":0,\"tamper\":0,\"wgformat\":0,\"wgoutput\":0,\"cardoutput\":0,\"dayzone\":[{\"day\":[{\"section\":\"01:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"}]},{\"day\":[{\"section\":\"02:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"}]},{\"day\":[{\"section\":\"03:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"}]},{\"day\":[{\"section\":\"04:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"}]},{\"day\":[{\"section\":\"05:00~00:0\n"
				+ "0\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"}]},{\"day\":[{\"section\":\"06:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"}]},{\"day\":[{\"section\":\"07:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"}]},{\"day\":[{\"section\":\"08:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"},{\"section\":\"00:00~00:00\"}]}],\"weekzone\":[{\"week\":[{\"day\":0},{\"day\":1},{\"day\":2},{\"day\":3},{\"day\":4},{\"day\":5},{\"day\":6}]},{\"week\":[{\"day\":10},{\"day\":11},{\"day\":12},{\"day\":13},{\"day\":14},{\"day\":15},{\"day\":16}]},{\"week\":[{\"day\":20},{\"day\":21},{\"day\":22},{\"day\":23},{\"day\":24},{\"day\":25},{\"day\":26}]},\n"
				+ "{\"week\":[{\"day\":30},{\"day\":31},{\"day\":32},{\"day\":33},{\"day\":34},{\"day\":35},{\"day\":36}]},{\"week\":[{\"day\":40},{\"day\":41},{\"day\":42},{\"day\":43},{\"day\":44},{\"day\":45},{\"day\":46}]},{\"week\":[{\"day\":50},{\"day\":51},{\"day\":52},{\"day\":53},{\"day\":54},{\"day\":55},{\"day\":56}]},{\"week\":[{\"day\":60},{\"day\":61},{\"day\":62},{\"day\":63},{\"day\":64},{\"day\":65},{\"day\":66}]},{\"week\":[{\"day\":70},{\"day\":71},{\"day\":72},{\"day\":73},{\"day\":74},{\"day\":75},{\"day\":76}]}],\"lockgroup\":[{\"group\":0},{\"group\":1},{\"group\":2},{\"group\":3},{\"group\":4}],\"nopentime\":[{\"day\":0},{\"day\":0},{\"day\":0},{\"day\":0},{\"day\":0},{\"day\":0},{\"day\":0}]}\n"
				+ "";

		String messageTemp = "{\"cmd\":\"setuserlock\",\"count\":1,\"record\":[{\"enrollid\":1,\"weekzone\":1,\"weekzone2\":3,\"group\":1,\"starttime\":\"2010-11-11 00:00:00\",\"endtime\":\"2030-11-11 00:00:00\"}]}";
		String s5 = "{\"cmd\":\"enableuser\",\"enrollid\":1,\"enflag\":0}";
		String s6 = "{\"cmd\":\"getusername\",\"enrollid\":1}";
		String message22 = "{\"cmd\":\"initsys\"}";

		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message22);
		machineCommand.setName("initsys");
		machineCommand.setStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSendStatus(0);
		machineCommand.setSerial(deviceSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());

		machineComandService.addMachineCommand(machineCommand);

		return Msg.success();
	}

	/* é‡‡é›†æ‰€æœ‰çš„è€ƒå‹¤è®°å½•ï¼Œé¢å‘æ‰€æœ‰æœºå™¨ */
	@ResponseBody
	@RequestMapping(value = "/getAllLog", method = RequestMethod.GET)
	public Msg getAllLog(@RequestParam("deviceSn") String deviceSn) {
		String message = "{\"cmd\":\"getalllog\",\"stn\":true}";
		// String
		// messageTemp="{\"cmd\":\"getalllog\",\"stn\":true,\"from\":\"2020-12-03\",\"to\":\"2020-12-30\"}";
		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message);
		machineCommand.setName("getalllog");
		machineCommand.setStatus(0);
		machineCommand.setSendStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSerial(deviceSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());

		machineComandService.addMachineCommand(machineCommand);
		return Msg.success();

	}

	/* é‡‡é›†æ‰€æœ‰çš„è€ƒå‹¤è®°å½•ï¼Œé¢å‘æ‰€æœ‰æœºå™¨ */
	@ResponseBody
	@RequestMapping(value = "/getNewLog", method = RequestMethod.GET)
	public Msg getNewLog(@RequestParam("deviceSn") String deviceSn) {
		String message = "{\"cmd\":\"getnewlog\",\"stn\":true}";
		// String
		// messageTemp="{\"cmd\":\"getalllog\",\"stn\":true,\"from\":\"2020-12-03\",\"to\":\"2020-12-30\"}";
		System.out.println(message);
		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message);
		machineCommand.setName("getnewlog");
		machineCommand.setStatus(0);
		machineCommand.setSendStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSerial(deviceSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());

		machineComandService.addMachineCommand(machineCommand);
		return Msg.success();

	}

	/* æ˜¾ç¤ºå‘˜å·¥åˆ—è¡¨ */
	@RequestMapping(value = "/emps")
	@ResponseBody
	public Msg getAllPersonFromDB(@RequestParam(value = "pn", defaultValue = "1") Integer pn,
			@RequestParam(value = "keyword", required = false) String keyword) {
		int pageNum = (pn == null || pn.intValue() < 1) ? 1 : pn.intValue();
		int pageSize = 8;
		String normalizedKeyword = keyword == null ? "" : keyword.trim();
		PageInfo<UserInfo> page;
		if (normalizedKeyword.isEmpty()) {
			PageHelper.startPage(pageNum, pageSize);
			List<Person> personList = personService.selectAll();
			PageInfo<Person> personPage = new PageInfo<Person>(personList, 5);
			List<UserInfo> emps = new ArrayList<UserInfo>(personList.size());
			for (int i = 0; i < personList.size(); i++) {
				UserInfo userInfo = new UserInfo();
				userInfo.setEnrollId(personList.get(i).getId());
				userInfo.setAdmin(personList.get(i).getRollId());
				userInfo.setName(personList.get(i).getName());
				userInfo.setStatus(personList.get(i).getStatus());
				emps.add(userInfo);
			}
			Page<UserInfo> pagedUsers = new Page<UserInfo>(personPage.getPageNum(), personPage.getPageSize());
			pagedUsers.setTotal(personPage.getTotal());
			pagedUsers.addAll(emps);
			page = new PageInfo<UserInfo>(pagedUsers, 5);
		} else {
			JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
			int offset = (pageNum - 1) * pageSize;
			String countSql = "SELECT COUNT(1) FROM ("
					+ "SELECT ID, NAME, PRI, Verify, ROW_NUMBER() OVER (PARTITION BY ID ORDER BY BU_ID DESC) AS rn "
					+ "FROM BIO_USERMAST WHERE ISNULL(ISDELETED, 0) = 0 AND ISNUMERIC(ID) = 1"
					+ ") u WHERE u.rn = 1 AND (CHARINDEX(?, ISNULL(u.ID, '')) > 0 OR CHARINDEX(?, ISNULL(u.NAME, '')) > 0)";
			Integer totalCount = jdbcTemplate.queryForObject(countSql, new Object[] { normalizedKeyword, normalizedKeyword },
					Integer.class);
			if (totalCount == null) {
				totalCount = Integer.valueOf(0);
			}
			String dataSql = "SELECT CASE WHEN ISNUMERIC(u.ID) = 1 THEN CONVERT(BIGINT, u.ID) ELSE NULL END AS enrollId, "
					+ "u.NAME AS name, "
					+ "ISNULL(CASE WHEN ISNUMERIC(u.PRI) = 1 THEN CONVERT(INT, u.PRI) ELSE NULL END, 0) AS admin, "
					+ "CASE WHEN ISNUMERIC(u.Verify) = 1 AND CONVERT(INT, u.Verify) IN (0,1) THEN CONVERT(INT, u.Verify) ELSE 1 END AS status "
					+ "FROM ("
					+ "SELECT BU_ID, ID, NAME, PRI, Verify, ROW_NUMBER() OVER (PARTITION BY ID ORDER BY BU_ID DESC) AS rn "
					+ "FROM BIO_USERMAST WHERE ISNULL(ISDELETED, 0) = 0 AND ISNUMERIC(ID) = 1"
					+ ") u "
					+ "WHERE u.rn = 1 AND (CHARINDEX(?, ISNULL(u.ID, '')) > 0 OR CHARINDEX(?, ISNULL(u.NAME, '')) > 0) "
					+ "ORDER BY CASE WHEN ISNUMERIC(u.ID) = 1 THEN CONVERT(BIGINT, u.ID) ELSE 0 END "
					+ "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(dataSql,
					new Object[] { normalizedKeyword, normalizedKeyword, Integer.valueOf(offset), Integer.valueOf(pageSize) });
			List<UserInfo> emps = new ArrayList<UserInfo>(rows.size());
			for (int i = 0; i < rows.size(); i++) {
				Map<String, Object> row = rows.get(i);
				UserInfo userInfo = new UserInfo();
				Object enrollIdObj = row.get("enrollId");
				if (enrollIdObj instanceof Number) {
					userInfo.setEnrollId(Long.valueOf(((Number) enrollIdObj).longValue()));
				}
				userInfo.setName(row.get("name") == null ? "" : String.valueOf(row.get("name")));
				Object adminObj = row.get("admin");
				userInfo.setAdmin(adminObj instanceof Number ? ((Number) adminObj).intValue() : 0);
				Object statusObj = row.get("status");
				userInfo.setStatus(statusObj instanceof Number ? ((Number) statusObj).intValue() : 1);
				emps.add(userInfo);
			}
			Page<UserInfo> pagedUsers = new Page<UserInfo>(pageNum, pageSize);
			pagedUsers.setTotal(totalCount.longValue());
			pagedUsers.addAll(emps);
			page = new PageInfo<UserInfo>(pagedUsers, 5);
		}
		return Msg.success().add("pageInfo", page);

	}

	/* æ˜¾ç¤ºæ‰€æœ‰çš„æ‰“å¡è®°å½• */
	@RequestMapping(value = "/records")
	@ResponseBody
	public Msg getAllLogFromDB(@RequestParam(value = "pn", defaultValue = "1") Integer pn) {
		PageHelper.startPage(pn, 8);

		List<Records> records = recordService.selectAllRecords();

		PageInfo page = new PageInfo(records, 5);

		return Msg.success().add("pageInfo", page);

	}

	@RequestMapping(value = "/climsRecords")
	@ResponseBody
	public Msg getAllClimsLogFromDB(@RequestParam(value = "pn", defaultValue = "1") Integer pn,
			@RequestParam(value = "deviceSn", required = false) String deviceSn) {
		int pageSize = 8;
		int pageNum = (pn == null || pn.intValue() < 1) ? 1 : pn.intValue();
		int offset = (pageNum - 1) * pageSize;
		String normalizedSn = deviceSn == null ? "" : deviceSn.trim();

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		String whereClause = "";
		List<Object> whereParams = new ArrayList<Object>();
		Integer mappedDeviceId = null;
		boolean deviceFilterApplied = false;
		boolean fallbackToAllDevices = false;
		boolean mappingMissing = false;
		if (!normalizedSn.isEmpty()) {
			List<Integer> mappedIds = jdbcTemplate.queryForList(
					"SELECT TOP 1 ID FROM NetWork WHERE LTRIM(RTRIM(SLNO)) = LTRIM(RTRIM(?)) ORDER BY ID DESC",
					new Object[] { normalizedSn }, Integer.class);
			if (!mappedIds.isEmpty()) {
				mappedDeviceId = mappedIds.get(0);
			}
			if (mappedDeviceId != null) {
				whereClause = " WHERE DEVICEID = ?";
				whereParams.add(mappedDeviceId);
				deviceFilterApplied = true;
			} else {
				// Do not return mixed all-device data when a specific serial was requested.
				mappingMissing = true;
				whereClause = " WHERE 1 = 0";
			}
		}
		String countSql = "SELECT COUNT(1) FROM CLIMSVIEW" + whereClause;
		Integer totalCount;
		if (whereParams.isEmpty()) {
			totalCount = jdbcTemplate.queryForObject(countSql, Integer.class);
		} else {
			totalCount = jdbcTemplate.queryForObject(countSql, whereParams.toArray(), Integer.class);
		}
		if (totalCount == null) {
			totalCount = Integer.valueOf(0);
		}
		String dataSql = "SELECT " + "CONVERT(BIGINT, DEVICELOGID) AS deviceLogId, "
				+ "CONVERT(VARCHAR(19), DOWNLOADDATE, 120) AS downloadDate, " + "PROJECTID AS projectId, "
				+ "CASE WHEN USERID IS NULL THEN NULL ELSE CONVERT(BIGINT, USERID) END AS userId, "
				+ "CONVERT(VARCHAR(19), LOGDATE, 120) AS logDate, " + "DIRECTION AS direction, "
				+ "CASE WHEN DEVICEID IS NULL THEN NULL ELSE CONVERT(BIGINT, DEVICEID) END AS deviceId, "
				+ "ISNULL((SELECT TOP 1 LTRIM(RTRIM(SLNO)) FROM NetWork WHERE ID = DEVICEID), '') AS deviceSerialNum "
				+ "FROM CLIMSVIEW" + whereClause
				+ " ORDER BY LOGDATE DESC, DOWNLOADDATE DESC, DEVICELOGID DESC "
				+ " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
		List<Object> dataParams = new ArrayList<Object>(whereParams);
		dataParams.add(Integer.valueOf(offset));
		dataParams.add(Integer.valueOf(pageSize));
		List<Map<String, Object>> records = jdbcTemplate.queryForList(dataSql, dataParams.toArray());

		Page<Map<String, Object>> pageRows = new Page<Map<String, Object>>(pageNum, pageSize);
		pageRows.setTotal(totalCount.longValue());
		pageRows.addAll(records);
		PageInfo page = new PageInfo(pageRows, 5);
		return Msg.success().add("pageInfo", page).add("source", "IDSL_NTPC_CLIMS.dbo.CLIMSVIEW")
				.add("deviceSn", normalizedSn).add("deviceMappedId", mappedDeviceId)
				.add("deviceFilterApplied", Boolean.valueOf(deviceFilterApplied))
				.add("fallbackToAllDevices", Boolean.valueOf(fallbackToAllDevices))
				.add("mappingMissing", Boolean.valueOf(mappingMissing));
	}


	@RequestMapping(value = "/openDoor", method = RequestMethod.GET)
	@ResponseBody
	public Msg openDoor(@RequestParam("doorNum") int doorNum, @RequestParam("deviceSn") String deviceSn) {
		String message = "{\"cmd\":\"opendoor\"" + ",\"doornum\":" + doorNum + "}";
		String normalizedSn = deviceSn == null ? "" : deviceSn.trim();
		try {
			if (WebSocketPool.getDeviceSocketBySn(normalizedSn) != null) {
				WebSocketPool.sendMessageToDeviceStatus(normalizedSn, message);
				return Msg.success().add("sentDirect", true).add("queued", false).add("deviceSn", normalizedSn);
			}
			if (WebSocketPool.wsDevice.size() == 1) {
				String fallbackSn = WebSocketPool.wsDevice.keySet().iterator().next();
				WebSocketPool.sendMessageToDeviceStatus(fallbackSn, message);
				return Msg.success().add("sentDirect", true).add("queued", false).add("deviceSn", fallbackSn)
						.add("fallbackSingleDevice", true);
			}
		} catch (Exception ex) {
			// Return explicit failure below.
		}
		return Msg.fail().add("error", "Device is not online on websocket, opendoor was not sent.")
				.add("sentDirect", false).add("queued", false).add("deviceSn", normalizedSn);
	}

	@RequestMapping(value = "/getDevLock", method = RequestMethod.GET)
	@ResponseBody
	public Msg getDevLock(@RequestParam("deviceSn") String deviceSn) {
		String message = "{\"cmd\":\"getdevlock\"}";

		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message);
		machineCommand.setName("getdevlock");
		machineCommand.setStatus(0);
		machineCommand.setSendStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSerial(deviceSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());

		machineComandService.addMachineCommand(machineCommand);
		return Msg.success();

	}

	@RequestMapping(value = "/geUSerLock", method = RequestMethod.GET)
	@ResponseBody
	public Msg getUserLock(@RequestParam("enrollId") Integer enrollId, @RequestParam("deviceSn") String deviceSn) {

		String message = "{\"cmd\":\"getuserlock\",\"enrollid\":" + enrollId + "}";
		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message);
		machineCommand.setName("getuserlock");
		machineCommand.setStatus(0);
		machineCommand.setSendStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSerial(deviceSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());

		machineComandService.addMachineCommand(machineCommand);

		return Msg.success();
	}

	@RequestMapping(value = "/cleanAdmin", method = RequestMethod.GET)
	@ResponseBody
	public Msg cleanAdmin(@RequestParam("deviceSn") String deviceSn) {
		String message = "{\"cmd\":\"cleanadmin\"}";

		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message);
		machineCommand.setName("cleanadmin");
		machineCommand.setStatus(0);
		machineCommand.setSendStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSerial(deviceSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());

		machineComandService.addMachineCommand(machineCommand);
		return Msg.success();

	}

	@RequestMapping(value = "/setUserEnable", method = RequestMethod.GET)
	@ResponseBody
	public Msg setUserEnable(@RequestParam("enrollId") Long enrollId, @RequestParam("enFlag") Integer enFlag,
			@RequestParam(value = "deviceSn", required = false) String deviceSn) {
		if (enrollId == null) {
			return Msg.fail().add("error", "enrollId is required.");
		}
		if (enFlag == null || (enFlag.intValue() != 0 && enFlag.intValue() != 1)) {
			return Msg.fail().add("error", "enFlag must be 0 or 1.");
		}
		Person person = personService.selectByPrimaryKey(enrollId);
		if (person == null) {
			return Msg.fail().add("error", "User not found in database.");
		}

		int updatedRows = personService.updateUserEnableFlag(enrollId, enFlag.intValue());
		if (updatedRows <= 0) {
			return Msg.fail().add("error", "User status update failed in database.");
		}
		int commandCount = queueUserEnableByStatusOnAllDevices(enrollId, enFlag.intValue());
		if (commandCount == 0 && hasText(deviceSn)) {
			queueUserEnableByStatus(enrollId, enFlag.intValue(), deviceSn.trim());
			commandCount = 1;
		}
		return Msg.success().add("enrollId", enrollId).add("enFlag", enFlag).add("commandsQueued", commandCount);
	}

	@RequestMapping(value = "/syncUserByDatabaseStatus", method = RequestMethod.GET)
	@ResponseBody
	public Msg syncUserByDatabaseStatus(@RequestParam("enrollId") Long enrollId) {
		if (enrollId == null) {
			return Msg.fail().add("error", "enrollId is required.");
		}
		Person person = personService.selectByPrimaryKey(enrollId);
		if (person == null) {
			return Msg.fail().add("error", "User not found in database.");
		}
		List<String> serials = getAllKnownDeviceSerials();
		if (serials.isEmpty()) {
			return Msg.fail().add("error", "No devices found in database.");
		}
		int enFlag = normalizeEnableStatus(person.getStatus());
		int queued = 0;
		for (String serial : serials) {
			queueUserEnableByStatus(enrollId, enFlag, serial);
			queued++;
		}
		return Msg.success().add("enrollId", enrollId).add("dbStatus", enFlag).add("commandsQueued", queued);
	}

	@ResponseBody
	@RequestMapping(value = "/syncUsersByDatabaseAllDevices", method = RequestMethod.GET)
	public Msg syncUsersByDatabaseAllDevices() {
		if (!triggerDatabaseSyncAsync("manual")) {
			return getDatabaseSyncStatusMsg().add("accepted", false);
		}
		return getDatabaseSyncStatusMsg().add("accepted", true);
	}

	@ResponseBody
	@RequestMapping(value = "/syncUsersByDatabaseAllDevicesStatus", method = RequestMethod.GET)
	public Msg syncUsersByDatabaseAllDevicesStatus() {
		return getDatabaseSyncStatusMsg();
	}

	@ResponseBody
	@RequestMapping(value = "/syncInactiveUsersDisableAllDevices", method = RequestMethod.GET)
	public Msg syncInactiveUsersDisableAllDevices() {
		List<String> serials = getAllKnownDeviceSerials();
		if (serials.isEmpty()) {
			return Msg.fail().add("error", "No devices found in database.");
		}
		List<Person> persons = personService.selectAll();
		int inactiveUsers = 0;
		for (Person person : persons) {
			if (person != null && person.getId() != null && normalizeEnableStatus(person.getStatus()) == 0) {
				inactiveUsers++;
			}
		}
		int queued = 0;
		for (Person person : persons) {
			if (person == null || person.getId() == null || normalizeEnableStatus(person.getStatus()) != 0) {
				continue;
			}
			for (String serial : serials) {
				queueUserEnableByStatus(person.getId(), 0, serial);
				queued++;
			}
		}
		return Msg.success().add("devices", serials.size()).add("inactiveUsers", inactiveUsers)
				.add("commandsQueued", queued);
	}

	@RequestMapping(value = "/synchronizeTime", method = RequestMethod.GET)
	@ResponseBody
	public Msg synchronizeTime(@RequestParam("deviceSn") String deviceSn) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String timeStr = sdf.format(new Date());
		String message = "{\"cmd\":\"settime\",\"cloudtime\":\"" + timeStr + "\"}";
		if (WebSocketPool.wsDevice.get(deviceSn) != null) {
			WebSocketPool.sendMessageToDeviceStatus(deviceSn, message);
			System.out.println("Send command to machine:" + message);
			return Msg.success();
		}
		return Msg.fail();

	}

	private boolean isSupportedSetBackupNum(int backupNum) {
		return backupNum == -1 || isSupportedEnrollBackupNum(backupNum);
	}

	private boolean isFaceBackupNum(int backupNum) {
		return backupNum == 50 || (backupNum >= 20 && backupNum <= 27);
	}

	private boolean isSupportedEnrollBackupNum(int backupNum) {
		return backupNum == 10 || backupNum == 11 || backupNum == 50 || (backupNum >= 20 && backupNum <= 27);
	}

}


