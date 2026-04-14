package com.timmy.controller;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
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
import com.timmy.mapper.NetWorkMapper;
import com.timmy.serviceImpl.DatabaseUserDeltaSyncService;
import com.timmy.serviceImpl.UsernameDeltaSyncService;
import com.timmy.util.ControllerBase;
import com.timmy.websocket.WSServer;
import com.timmy.websocket.WebSocketPool;

@Controller

public class AllController extends ControllerBase {
	private static final Logger log = LoggerFactory.getLogger(AllController.class);

	@Autowired
	private DataSource dataSource;

	@Autowired
	private NetWorkMapper netWorkMapper;

	@Autowired
	private UsernameDeltaSyncService usernameDeltaSyncService;

	@Autowired
	private DatabaseUserDeltaSyncService databaseUserDeltaSyncService;

	/*
	 * @Autowired EnrollInfoService enrollInfoService;
	 */
	private static final int PASSWORD_MAX_LENGTH = 10;
	private static final int CARD_MAX_LENGTH = 20;
	private static final String FIXED_REGISTRATION_DEVICE_SN = "AXTI11107153";
	private static final String VERIFY_SCHEDULER_AUDIT_TABLE = "JAVA_VERIFY_SCHEDULER_AUDIT";
	private static final String DEVICE_USER_SYNC_STATE_TABLE = "JAVA_DEVICE_USER_SYNC_STATE";
	private static final String SYNC_TARGET_ALL = "all";
	private static final boolean AUTO_SYNC_REGISTRATION_TO_ALL_DEVICES = true;
	private static final int BULK_SYNC_INSERT_BATCH_SIZE = 500;
	private static final String DB_SYNC_STATE_IDLE = "IDLE";
	private static final String DB_SYNC_STATE_RUNNING = "RUNNING";
	private static final String DB_SYNC_STATE_SUCCESS = "SUCCESS";
	private static final String DB_SYNC_STATE_FAILED = "FAILED";
	private static final long DB_SYNC_WORKER_ATTACH_GRACE_MS = 5000L;
	private static final boolean ALLOW_DEVICE_ADMIN_SYNC = false;
	// Full sync mode: allow backup/user sync APIs (face/password/card/photo/name).
	private static final boolean BACKUP_SYNC_ENABLED = true;
	private static final double DEFAULT_SETUSERINFO_RATE_PER_SECOND = 0.25d;
	private static final int SETUSERINFO_RATE_LOOKBACK_MINUTES = 20;
	private static final int MIN_SETUSERINFO_RATE_SAMPLE = 20;
	private static final double MIN_SETUSERINFO_RATE_PER_SECOND = 0.05d;
	private static final double MAX_SETUSERINFO_RATE_PER_SECOND = 5.0d;
	private final AtomicBoolean dbSyncRunning = new AtomicBoolean(false);
	private volatile String dbSyncState = DB_SYNC_STATE_IDLE;
	private volatile String dbSyncMessage = "Not started.";
	private volatile long dbSyncStartedAtEpochMs = 0L;
	private volatile long dbSyncFinishedAtEpochMs = 0L;
	private volatile int dbSyncDevices = 0;
	private volatile int dbSyncOnlineDevices = 0;
	private volatile int dbSyncActiveUsers = 0;
	private volatile int dbSyncDeletedUsers = 0;
	private volatile int dbSyncEnabledUsers = 0;
	private volatile int dbSyncDisabledUsers = 0;
	private volatile int dbSyncChangedStatusUsers = 0;
	private volatile int dbSyncChangedDeletedUsers = 0;
	private volatile int dbSyncTotalEnrollRecords = 0;
	private volatile int dbSyncSetuserinfoCommandsQueued = 0;
	private volatile int dbSyncCleanAdminCommandsQueued = 0;
	private volatile int dbSyncQueuedCommands = 0;
	private volatile long dbSyncDurationMs = 0L;
	private volatile long dbSyncEstimatedDeviceDispatchSeconds = 0L;
	private volatile List<DatabaseSyncDeviceDetail> dbSyncDeviceDetails = new ArrayList<DatabaseSyncDeviceDetail>();
	private volatile Thread dbSyncWorkerThread;
	private volatile boolean deviceUserSyncStateTableEnsured;

	private final AtomicBoolean usernameDeltaSyncRunning = new AtomicBoolean(false);
	private volatile String usernameDeltaSyncState = DB_SYNC_STATE_IDLE;
	private volatile String usernameDeltaSyncMessage = "Not started.";
	private volatile long usernameDeltaSyncStartedAtEpochMs = 0L;
	private volatile long usernameDeltaSyncFinishedAtEpochMs = 0L;
	private volatile int usernameDeltaSyncDevices = 0;
	private volatile int usernameDeltaSyncOnlineDevices = 0;
	private volatile int usernameDeltaSyncTotalUsers = 0;
	private volatile int usernameDeltaSyncChangedUsers = 0;
	private volatile int usernameDeltaSyncChangedStatuses = 0;
	private volatile int usernameDeltaSyncUsernameCommands = 0;
	private volatile int usernameDeltaSyncStatusCommands = 0;
	private volatile int usernameDeltaSyncQueuedCommands = 0;
	private volatile long usernameDeltaSyncDurationMs = 0L;
	private volatile long usernameDeltaSyncEstimatedDeviceDispatchSeconds = 0L;
	private volatile String usernameDeltaSyncSnapshotFile = "";
	private volatile String usernameDeltaSyncReportFile = "";
	private volatile String usernameDeltaSyncReason = "";
	private volatile List<UsernameDeltaSyncService.DeviceSyncDetail> usernameDeltaSyncDeviceDetails = new ArrayList<UsernameDeltaSyncService.DeviceSyncDetail>();

	@RequestMapping("/hello1")
	public String hello() {
		return "hello";
	}

	@RequestMapping(value = "/climsRecordsPage", method = RequestMethod.GET)
	public String climsRecordsPage() {
		return "climsRecords";
	}

	@RequestMapping(value = "/schedulerStatusPage", method = RequestMethod.GET)
	public String schedulerStatusPage() {
		return "schedulerStatus";
	}

	@RequestMapping(value = "/updateDataPage", method = RequestMethod.GET)
	public String updateDataPage() {
		return "updateData";
	}

	@RequestMapping(value = "/schedulerDailyStatus", method = RequestMethod.GET)
	@ResponseBody
	public Msg schedulerDailyStatus(@RequestParam(value = "days", defaultValue = "30") Integer days,
			@RequestParam(value = "pn", defaultValue = "1") Integer pn,
			@RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
		int safeDays = 30;
		if (days != null && days.intValue() > 0) {
			safeDays = days.intValue();
		}
		if (safeDays > 365) {
			safeDays = 365;
		}
		int safePn = (pn == null || pn.intValue() <= 0) ? 1 : pn.intValue();
		int safePageSize = (pageSize == null || pageSize.intValue() <= 0) ? 10 : pageSize.intValue();
		if (safePageSize > 100) {
			safePageSize = 100;
		}
		try {
			JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
			ensureSchedulerAuditTable(jdbcTemplate);
			String countSql = "SELECT COUNT(1) FROM ("
					+ " SELECT CAST(STARTED_AT AS date) AS started_day "
					+ " FROM " + VERIFY_SCHEDULER_AUDIT_TABLE + " "
					+ " WHERE STARTED_AT >= DATEADD(day, -(? - 1), CAST(GETDATE() AS date)) "
					+ " GROUP BY CAST(STARTED_AT AS date) "
					+ ") d";
			Integer totalObj = jdbcTemplate.queryForObject(countSql, Integer.class, Integer.valueOf(safeDays));
			int totalRecords = totalObj == null ? 0 : totalObj.intValue();
			int totalPages = totalRecords <= 0 ? 0 : ((totalRecords - 1) / safePageSize) + 1;
			if (totalPages > 0 && safePn > totalPages) {
				safePn = totalPages;
			}
			if (totalPages <= 0) {
				safePn = 1;
			}
			int offset = (safePn - 1) * safePageSize;
			String sql = "WITH base AS ("
					+ " SELECT "
					+ " ID, STATUS, STARTED_AT, FINISHED_AT, "
					+ " ISNULL(ACTIVE_USER, 0) AS ACTIVE_USER, "
					+ " ISNULL(DISABLE_USER, 0) AS DISABLE_USER, "
					+ " ISNULL(DELETED_USER, 0) AS DELETED_USER, "
					+ " CAST(STARTED_AT AS date) AS started_day "
					+ " FROM " + VERIFY_SCHEDULER_AUDIT_TABLE + " "
					+ " WHERE STARTED_AT >= DATEADD(day, -(? - 1), CAST(GETDATE() AS date)) "
					+ "), daily AS ("
					+ " SELECT started_day, "
					+ " COUNT(1) AS runCount, "
					+ " SUM(CASE WHEN STATUS = 'SUCCESS' THEN 1 ELSE 0 END) AS successCount, "
					+ " SUM(CASE WHEN STATUS = 'FAILED' THEN 1 ELSE 0 END) AS failedCount, "
					+ " MIN(STARTED_AT) AS firstStartedAt, "
					+ " MAX(FINISHED_AT) AS lastFinishedAt "
					+ " FROM base "
					+ " GROUP BY started_day "
					+ "), latest AS ("
					+ " SELECT started_day, STATUS, ACTIVE_USER, DISABLE_USER, DELETED_USER, "
					+ " ROW_NUMBER() OVER (PARTITION BY started_day ORDER BY STARTED_AT DESC, ID DESC) AS rn "
					+ " FROM base "
					+ ") "
					+ "SELECT CONVERT(VARCHAR(10), d.started_day, 120) AS runDate, "
					+ " d.runCount AS runCount, "
					+ " d.successCount AS successCount, "
					+ " d.failedCount AS failedCount, "
					+ " CONVERT(VARCHAR(19), d.firstStartedAt, 120) AS firstStartedAt, "
					+ " CONVERT(VARCHAR(19), d.lastFinishedAt, 120) AS lastFinishedAt, "
					+ " l.ACTIVE_USER AS enabledUsers, "
					+ " l.DISABLE_USER AS disabledUsers, "
					+ " l.DELETED_USER AS deletedUsers, "
					+ " CASE "
					+ " WHEN l.STATUS = 'SUCCESS' THEN 'SUCCESS' "
					+ " ELSE 'FAILED' END AS dailyStatus "
					+ "FROM daily d "
					+ "LEFT JOIN latest l ON l.started_day = d.started_day AND l.rn = 1 "
					+ "ORDER BY d.started_day DESC "
					+ "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, Integer.valueOf(safeDays),
					Integer.valueOf(offset), Integer.valueOf(safePageSize));
			return Msg.success().add("rows", rows).add("days", Integer.valueOf(safeDays))
					.add("pn", Integer.valueOf(safePn)).add("pageSize", Integer.valueOf(safePageSize))
					.add("totalRecords", Integer.valueOf(totalRecords)).add("totalPages", Integer.valueOf(totalPages))
					.add("tableName", VERIFY_SCHEDULER_AUDIT_TABLE);
		} catch (Exception ex) {
			log.error("Failed to load scheduler daily status.", ex);
			return Msg.fail().add("error", ex.getMessage());
		}
	}

	/* èŽ·å–æ‰€æœ‰è€ƒå‹¤æœº */
	@ResponseBody
	@RequestMapping(value = "/device", method = RequestMethod.GET)
	public Msg getAllDevice() {
		List<Device> deviceList = deviceService.findAllDevice();
		return Msg.success().add("device", deviceList);
	}

	@ResponseBody
	@RequestMapping(value = "/setDeviceInOutMode", method = RequestMethod.GET)
	public Msg setDeviceInOutMode(@RequestParam("deviceSn") String deviceSn, @RequestParam("mode") String mode,
			@RequestParam(value = "applyToDevice", required = false) Boolean applyToDevice) {
		String normalizedSn = normalizeText(deviceSn);
		String normalizedMode = normalizeInOutMode(mode);
		boolean shouldApplyToDevice = applyToDevice == null ? true : applyToDevice.booleanValue();
		if (!hasText(normalizedSn)) {
			return Msg.fail().add("error", "deviceSn is required.");
		}
		if (normalizedMode == null) {
			return Msg.fail().add("error", "mode must be one of AUTO, IN, OUT.");
		}
		if (netWorkMapper == null) {
			return Msg.fail().add("error", "NetWork mapper is unavailable.");
		}
		try {
			netWorkMapper.insertNetworkPlaceholder(normalizedSn);
			netWorkMapper.upsertGateBySlno(normalizedSn, "AUTO".equals(normalizedMode) ? null : normalizedMode);
			String savedGate = normalizeInOutMode(netWorkMapper.selectGateBySlno(normalizedSn));
			String effectiveMode = savedGate == null ? "AUTO" : savedGate;
			boolean commandQueued = false;
			if (shouldApplyToDevice) {
				MachineCommand command = buildSetQuestionnaireCommand(normalizedSn, effectiveMode);
				if (command != null) {
					machineComandService.addMachineCommand(command);
					commandQueued = true;
				}
			}
			return Msg.success().add("deviceSn", normalizedSn).add("mode", effectiveMode)
					.add("applyToDeviceRequested", Boolean.valueOf(shouldApplyToDevice))
					.add("commandQueued", Boolean.valueOf(commandQueued));
		} catch (Exception ex) {
			log.error("Failed to set in/out mode for device {}", normalizedSn, ex);
			return Msg.fail().add("error", ex.getMessage());
		}
	}

	@ResponseBody
	@RequestMapping(value = "/getDeviceInOutMode", method = RequestMethod.GET)
	public Msg getDeviceInOutMode(@RequestParam("deviceSn") String deviceSn) {
		String normalizedSn = normalizeText(deviceSn);
		if (!hasText(normalizedSn)) {
			return Msg.fail().add("error", "deviceSn is required.");
		}
		String normalizedMode = normalizeInOutMode(netWorkMapper == null ? null : netWorkMapper.selectGateBySlno(normalizedSn));
		return Msg.success().add("deviceSn", normalizedSn).add("mode", normalizedMode == null ? "AUTO" : normalizedMode);
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
	public Msg sendWs(@RequestParam(value = "deviceSn", required = false) String deviceSn) {
		if (!BACKUP_SYNC_ENABLED) {
			return Msg.fail().add("error", "Backup sync is disabled in relay-only mode.");
		}
		String message = "{\"cmd\":\"getuserlist\",\"stn\":true}";
		String targetDeviceSn = FIXED_REGISTRATION_DEVICE_SN;

		System.out.println("Fixed getuserlist source device: " + targetDeviceSn + ", requested=" + deviceSn);

		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message);
		machineCommand.setName("getuserlist");
		machineCommand.setStatus(0);
		machineCommand.setSendStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSerial(targetDeviceSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());
		machineCommand.setContent(message);
		machineComandService.addMachineCommand(machineCommand);
		return Msg.success().add("exportDir", WSServer.getDeviceUserExportDirPath())
				.add("bundleFile", WSServer.getDeviceUserFullExportFilePath())
				.add("sourceDeviceSn", targetDeviceSn)
				.add("note", "getuserinfo commands are auto-queued from this device to fetch full details (id/name/privilege/image).");
	}

	@ResponseBody
	@RequestMapping(value = "addPerson", method = RequestMethod.POST)
	public Msg addPerson(PersonTemp personTemp, @RequestParam(value = "pic", required = false) MultipartFile pic) throws Exception {
		if (personTemp != null && personTemp.getUserId() != null) {
			boolean isNewRegistration = personService.selectByPrimaryKey(personTemp.getUserId()) == null;
			upsertPersonInClientDb(personTemp, pic);
			boolean syncQueued = queueRegistrationSync(personTemp, isNewRegistration);
			return Msg.success().add("syncQueued", syncQueued);
		}

		System.out.println("Dynamic face local folder write is disabled.");
		System.out.println("æ–°å¢žäººå‘˜ä¿¡æ¯===================" + personTemp);
		String base64Str = "";
		if (pic != null && pic.getOriginalFilename() != null && !("").equals(pic.getOriginalFilename())) {
			base64Str = savePhotoAsBase64(pic);
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

		if (hasText(base64Str)) {
			EnrollInfo enrollInfoTemp = new EnrollInfo();
			enrollInfoTemp.setBackupnum(50);
			enrollInfoTemp.setEnrollId(personTemp.getUserId());
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
			Person existingBeforeUpsert = personService.selectByPrimaryKey(personTemp.getUserId());
			boolean isNewRegistration = existingBeforeUpsert == null;
			upsertPersonInClientDb(personTemp, pic);
			String effectiveDeviceSn = deviceSn;
			String effectiveSyncTarget = syncTarget;
			if (AUTO_SYNC_REGISTRATION_TO_ALL_DEVICES && isNewRegistration) {
				effectiveDeviceSn = null;
				effectiveSyncTarget = SYNC_TARGET_ALL;
			}
			boolean syncQueued = queueUserSyncToDevice(personTemp, effectiveDeviceSn, effectiveSyncTarget);
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

	private boolean queueRegistrationSync(PersonTemp personTemp, boolean isNewRegistration) {
		if (personTemp == null || personTemp.getUserId() == null) {
			return false;
		}
		if (!AUTO_SYNC_REGISTRATION_TO_ALL_DEVICES || !isNewRegistration) {
			return false;
		}
		return queueUserSyncToDevice(personTemp, null, SYNC_TARGET_ALL);
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

	private List<Long> parseEnrollIdsCsv(String enrollIdsText) {
		Set<Long> ids = new LinkedHashSet<Long>();
		if (!hasText(enrollIdsText)) {
			return new ArrayList<Long>();
		}
		String[] tokens = enrollIdsText.split("[,\\s]+");
		for (String token : tokens) {
			if (!hasText(token)) {
				continue;
			}
			String normalized = token.trim();
			if (!normalized.matches("\\d+")) {
				continue;
			}
			try {
				long value = Long.parseLong(normalized);
				if (value > 0L) {
					ids.add(Long.valueOf(value));
				}
			} catch (NumberFormatException ex) {
				// ignore invalid token
			}
		}
		return new ArrayList<Long>(ids);
	}

	private List<String> parseSerialsCsv(String serialsText) {
		Set<String> serials = new LinkedHashSet<String>();
		if (!hasText(serialsText)) {
			return new ArrayList<String>();
		}
		String[] tokens = serialsText.split("[,\\s]+");
		for (String token : tokens) {
			if (hasText(token)) {
				serials.add(token.trim());
			}
		}
		return new ArrayList<String>(serials);
	}

	private List<UserInfo> buildUserInfoRecordsForDirectSend(Long enrollId, Person person, List<EnrollInfo> enrollInfos) {
		List<UserInfo> records = new ArrayList<UserInfo>();
		if (enrollId == null || person == null || enrollInfos == null || enrollInfos.isEmpty()) {
			return records;
		}
		for (EnrollInfo enrollInfo : enrollInfos) {
			if (enrollInfo == null || enrollInfo.getBackupnum() == null || !isSupportedSetBackupNum(enrollInfo.getBackupnum())
					|| !hasText(enrollInfo.getSignatures())) {
				continue;
			}
			UserInfo info = new UserInfo();
			info.setEnrollId(enrollId);
			info.setName(person.getName() == null ? "" : person.getName());
			info.setAdmin(person.getRollId() == null ? 0 : person.getRollId());
			info.setBackupnum(enrollInfo.getBackupnum().intValue());
			info.setRecord(enrollInfo.getSignatures());
			info.setSourceId(enrollInfo.getId() == null ? null : Long.valueOf(enrollInfo.getId().longValue()));
			records.add(info);
		}
		java.util.Collections.sort(records, new java.util.Comparator<UserInfo>() {
			@Override
			public int compare(UserInfo left, UserInfo right) {
				long leftId = left == null || left.getSourceId() == null ? Long.MAX_VALUE : left.getSourceId().longValue();
				long rightId = right == null || right.getSourceId() == null ? Long.MAX_VALUE : right.getSourceId().longValue();
				return Long.compare(leftId, rightId);
			}
		});
		return records;
	}

	private int directSendSetUserInfoRecords(List<UserInfo> records, List<String> onlineSerials) {
		if (records == null || records.isEmpty() || onlineSerials == null || onlineSerials.isEmpty()) {
			return 0;
		}
		int sent = 0;
		List<String> activeSerials = new ArrayList<String>(onlineSerials);
		for (int start = 0; start < records.size(); start += 50) {
			int end = Math.min(start + 50, records.size());
			List<UserInfo> batch = records.subList(start, end);
			for (int i = 0; i < activeSerials.size(); i++) {
				String serial = activeSerials.get(i);
				if (!hasText(serial)) {
					continue;
				}
				boolean keepDeviceActive = true;
				for (UserInfo info : batch) {
					if (info == null || info.getEnrollId() == null) {
						continue;
					}
					String payload = buildSetUserInfoPayload(info.getEnrollId(), info.getName(), info.getBackupnum(),
							sanitizeAdminForDevice(info.getEnrollId(), Integer.valueOf(info.getAdmin())), info.getRecord());
					if (!WebSocketPool.sendMessageToDeviceStatus(serial, payload)) {
						activeSerials.remove(i);
						i--;
						keepDeviceActive = false;
						break;
					}
					sent++;
				}
				if (!keepDeviceActive && activeSerials.isEmpty()) {
					return sent;
				}
			}
			if (end < records.size()) {
				try {
					Thread.sleep(20L);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					return sent;
				}
			}
		}
		return sent;
	}

	private int directSendEnableCommands(List<Long> enrollIds, Map<Long, Integer> enableByUserId, List<String> onlineSerials) {
		if (enrollIds == null || enrollIds.isEmpty() || enableByUserId == null || enableByUserId.isEmpty()
				|| onlineSerials == null || onlineSerials.isEmpty()) {
			return 0;
		}
		int sent = 0;
		List<String> activeSerials = new ArrayList<String>(onlineSerials);
		for (int start = 0; start < enrollIds.size(); start += 50) {
			int end = Math.min(start + 50, enrollIds.size());
			List<Long> batch = enrollIds.subList(start, end);
			for (int i = 0; i < activeSerials.size(); i++) {
				String serial = activeSerials.get(i);
				if (!hasText(serial)) {
					continue;
				}
				boolean keepDeviceActive = true;
				for (Long enrollId : batch) {
					if (enrollId == null) {
						continue;
					}
					Integer enFlag = enableByUserId.get(enrollId);
					String payload = "{\"cmd\":\"enableuser\",\"enrollid\":" + enrollId + ",\"enrolled\":" + enrollId
							+ ",\"enflag\":" + (enFlag == null ? 0 : enFlag.intValue()) + "}";
					if (!WebSocketPool.sendMessageToDeviceStatus(serial, payload)) {
						activeSerials.remove(i);
						i--;
						keepDeviceActive = false;
						break;
					}
					sent++;
				}
				if (!keepDeviceActive && activeSerials.isEmpty()) {
					return sent;
				}
			}
			if (end < enrollIds.size()) {
				try {
					Thread.sleep(20L);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					return sent;
				}
			}
		}
		return sent;
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
		int queued = 0;
		for (int i = 0; i < commands.size(); i++) {
			MachineCommand command = commands.get(i);
			if (command == null) {
				continue;
			}
			machineComandService.addMachineCommand(command);
			queued++;
		}
		return queued;
	}

	private static final class DatabaseSyncSummary {
		private int devices;
		private int onlineDevices;
		private int activeUsers;
		private int totalEnrollRecords;
		private int setuserinfoCommandsQueued;
		private int cleanAdminCommandsQueued;
		private int totalCommandsQueued;
		private int successDevices;
		private int failedDevices;
		private long durationMs;
		private long estimatedDeviceDispatchSeconds;
		private String reason;
		private List<DatabaseSyncDeviceDetail> deviceDetails;
	}

	private DatabaseSyncSummary runDatabaseSyncNow() {
		return runDatabaseSyncNow(true);
	}

	private DatabaseSyncSummary runDatabaseSyncNow(boolean fullSync) {
		return runDatabaseSyncNow(null, fullSync);
	}

	private DatabaseSyncSummary runDatabaseSyncNow(List<String> targetSerials) {
		return runDatabaseSyncNow(targetSerials, true);
	}

	private DatabaseSyncSummary runDatabaseSyncNow(List<String> targetSerials, boolean fullSync) {
		long startedAt = System.currentTimeMillis();
		Set<String> serialSet = new LinkedHashSet<String>();
		List<String> candidateSerials = targetSerials;
		if (candidateSerials == null || candidateSerials.isEmpty()) {
			candidateSerials = getAllKnownDeviceSerials();
		}
		if (candidateSerials != null) {
			for (String serial : candidateSerials) {
				if (hasText(serial)) {
					serialSet.add(serial.trim());
				}
			}
		}
		List<String> serials = new ArrayList<String>(serialSet);
		if (serials == null || serials.isEmpty()) {
			throw new IllegalStateException("No devices found in database.");
		}
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		ensureDeviceUserSyncStateTable(jdbcTemplate);
		DatabaseSyncSummary summary = new DatabaseSyncSummary();
		summary.devices = serials.size();
		summary.onlineDevices = countOnlineDevices(serials);
		Map<Long, List<UserInfo>> recordsByUser = buildEnrollRecordsByUser();
		summary.activeUsers = recordsByUser.size();
		summary.totalEnrollRecords = countTotalEnrollRecords(recordsByUser);
		summary.deviceDetails = new ArrayList<DatabaseSyncDeviceDetail>();
		Map<String, DeviceSyncState> stateBySerial = loadDeviceSyncStateBySerial(jdbcTemplate, serials);

		int totalSetuserinfoQueued = 0;
		int totalCleanAdminQueued = 0;
		long maxEstimatedDispatchSeconds = 0L;
		for (String serial : serials) {
			if (!hasText(serial)) {
				continue;
			}
			String normalizedSerial = serial.trim();
			DeviceSyncState existingState = stateBySerial.get(normalizedSerial.toUpperCase());
			if (existingState == null) {
				existingState = stateBySerial.get(normalizedSerial);
			}
			DatabaseSyncDeviceDetail detail = fullSync
					? syncDeviceFullByUserId(jdbcTemplate, normalizedSerial, existingState, recordsByUser)
					: syncDeviceIncrementalByUserId(jdbcTemplate, normalizedSerial, existingState, recordsByUser);
			summary.deviceDetails.add(detail);
			totalSetuserinfoQueued += detail.getQueuedSetuserinfo();
			totalCleanAdminQueued += detail.getQueuedCleanAdmin();
			if (detail.getEstimatedDispatchSeconds() > maxEstimatedDispatchSeconds) {
				maxEstimatedDispatchSeconds = detail.getEstimatedDispatchSeconds();
			}
			if (DB_SYNC_STATE_SUCCESS.equals(detail.getSyncStatus())) {
				summary.successDevices++;
			} else if (DB_SYNC_STATE_FAILED.equals(detail.getSyncStatus())) {
				summary.failedDevices++;
			}
		}

		summary.setuserinfoCommandsQueued = totalSetuserinfoQueued;
		summary.cleanAdminCommandsQueued = totalCleanAdminQueued;
		summary.totalCommandsQueued = totalSetuserinfoQueued + totalCleanAdminQueued;
		summary.estimatedDeviceDispatchSeconds = maxEstimatedDispatchSeconds;
		summary.durationMs = System.currentTimeMillis() - startedAt;
		if (summary.failedDevices > 0) {
			summary.reason = fullSync ? "Some devices failed during full DB sync."
					: "Some devices failed during incremental sync.";
		} else if (summary.totalCommandsQueued == 0) {
			summary.reason = fullSync ? "No registration records found for target devices."
					: "No incremental records found for target devices.";
		} else if (summary.onlineDevices <= 0) {
			summary.reason = "Commands queued in DEVICECMD, but no websocket device is online right now.";
		}
		return summary;
	}

	private Msg getDatabaseSyncStatusMsg() {
		reconcileDatabaseSyncWorkerState();
		boolean running = dbSyncRunning.get();
		long durationMs = dbSyncDurationMs;
		if (running && dbSyncStartedAtEpochMs > 0L) {
			long elapsed = System.currentTimeMillis() - dbSyncStartedAtEpochMs;
			if (elapsed > durationMs) {
				durationMs = elapsed;
			}
		}
		Msg msg = Msg.success().add("running", running).add("state", dbSyncState).add("message", dbSyncMessage)
				.add("devices", dbSyncDevices).add("onlineDevices", dbSyncOnlineDevices)
				.add("activeUsers", dbSyncActiveUsers).add("deletedUsers", dbSyncDeletedUsers)
				.add("enabledUsers", dbSyncEnabledUsers).add("disabledUsers", dbSyncDisabledUsers)
				.add("changedStatusUsers", dbSyncChangedStatusUsers).add("changedDeletedUsers", dbSyncChangedDeletedUsers)
				.add("totalEnrollRecords", dbSyncTotalEnrollRecords)
				.add("setuserinfoCommandsQueued", dbSyncSetuserinfoCommandsQueued)
				.add("cleanAdminCommandsQueued", dbSyncCleanAdminCommandsQueued)
				.add("totalCommandsQueued", dbSyncQueuedCommands).add("durationMs", durationMs)
				.add("estimatedDeviceDispatchSeconds", dbSyncEstimatedDeviceDispatchSeconds)
				.add("deviceDetails", dbSyncDeviceDetails)
				.add("deviceSyncStateRows", loadDeviceSyncStateRows())
				.add("wsConnectedDevices", WebSocketPool.wsDevice.size())
				.add("wsConnectedSerials", new ArrayList<String>(WebSocketPool.wsDevice.keySet()));
		if (dbSyncStartedAtEpochMs > 0L) {
			msg.add("startedAt", new Date(dbSyncStartedAtEpochMs));
		}
		if (dbSyncFinishedAtEpochMs > 0L) {
			msg.add("finishedAt", new Date(dbSyncFinishedAtEpochMs));
		}
		return msg;
	}

	private void reconcileDatabaseSyncWorkerState() {
		if (!dbSyncRunning.get()) {
			return;
		}
		Thread worker = dbSyncWorkerThread;
		if (worker != null && worker.isAlive()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (dbSyncStartedAtEpochMs > 0L && now - dbSyncStartedAtEpochMs < DB_SYNC_WORKER_ATTACH_GRACE_MS) {
			return;
		}
		dbSyncState = DB_SYNC_STATE_FAILED;
		dbSyncMessage = "Sync worker is not active. Status auto-reset; please start sync again.";
		dbSyncFinishedAtEpochMs = now;
		if (dbSyncDurationMs <= 0L && dbSyncStartedAtEpochMs > 0L) {
			dbSyncDurationMs = now - dbSyncStartedAtEpochMs;
		}
		dbSyncRunning.set(false);
		log.warn("[DB-FULL-SYNC] auto-reset stale running state. startedAt={}, finishedAt={}",
				dbSyncStartedAtEpochMs, dbSyncFinishedAtEpochMs);
	}

	private boolean triggerDatabaseSyncAsync(final String trigger) {
		return triggerDatabaseSyncAsync(trigger, true);
	}

	private boolean triggerDatabaseSyncAsync(final String trigger, final boolean fullSync) {
		if (!dbSyncRunning.compareAndSet(false, true)) {
			log.warn("[DB-FULL-SYNC] trigger ignored because sync is already running. trigger={}", trigger);
			return false;
		}
		String modeLabel = fullSync ? "Full image sync" : "Incremental sync";
		dbSyncState = DB_SYNC_STATE_RUNNING;
		dbSyncMessage = modeLabel + " started by " + trigger;
		dbSyncStartedAtEpochMs = System.currentTimeMillis();
		dbSyncFinishedAtEpochMs = 0L;
		dbSyncDevices = 0;
		dbSyncOnlineDevices = 0;
		dbSyncActiveUsers = 0;
		dbSyncDeletedUsers = 0;
		dbSyncEnabledUsers = 0;
		dbSyncDisabledUsers = 0;
		dbSyncChangedStatusUsers = 0;
		dbSyncChangedDeletedUsers = 0;
		dbSyncTotalEnrollRecords = 0;
		dbSyncSetuserinfoCommandsQueued = 0;
		dbSyncCleanAdminCommandsQueued = 0;
		dbSyncQueuedCommands = 0;
		dbSyncDurationMs = 0L;
		dbSyncEstimatedDeviceDispatchSeconds = 0L;
		dbSyncDeviceDetails = new ArrayList<DatabaseSyncDeviceDetail>();
		dbSyncWorkerThread = null;
		log.info("[DB-FULL-SYNC] async trigger accepted. trigger={}", trigger);
		Thread worker = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					log.info("[DB-FULL-SYNC] worker started. trigger={}", trigger);
					DatabaseSyncSummary summary = runDatabaseSyncNow(fullSync);
					dbSyncDevices = summary.devices;
					dbSyncOnlineDevices = summary.onlineDevices;
					dbSyncActiveUsers = summary.activeUsers;
					dbSyncDeletedUsers = 0;
					dbSyncEnabledUsers = 0;
					dbSyncDisabledUsers = 0;
					dbSyncChangedStatusUsers = 0;
					dbSyncChangedDeletedUsers = 0;
					dbSyncTotalEnrollRecords = summary.totalEnrollRecords;
					dbSyncSetuserinfoCommandsQueued = summary.setuserinfoCommandsQueued;
					dbSyncCleanAdminCommandsQueued = summary.cleanAdminCommandsQueued;
					dbSyncQueuedCommands = summary.totalCommandsQueued;
					dbSyncDurationMs = summary.durationMs;
					dbSyncEstimatedDeviceDispatchSeconds = summary.estimatedDeviceDispatchSeconds;
					dbSyncDeviceDetails = summary.deviceDetails == null
							? new ArrayList<DatabaseSyncDeviceDetail>()
							: new ArrayList<DatabaseSyncDeviceDetail>(summary.deviceDetails);
					dbSyncState = DB_SYNC_STATE_SUCCESS;
					StringBuilder messageBuilder = new StringBuilder();
					messageBuilder.append(modeLabel).append(" completed in ").append(summary.durationMs).append(" ms")
							.append(", successDevices=").append(summary.successDevices)
							.append(", failedDevices=").append(summary.failedDevices)
							.append(", activeUsers=").append(summary.activeUsers)
							.append(", enrollRecords=").append(summary.totalEnrollRecords)
							.append(", queued(setuserinfo=").append(summary.setuserinfoCommandsQueued)
							.append(", cleanadmin=").append(summary.cleanAdminCommandsQueued)
							.append(", total=").append(summary.totalCommandsQueued).append(")");
					if (summary.estimatedDeviceDispatchSeconds > 0L) {
						messageBuilder.append(", estimatedDeviceDispatch=")
								.append(summary.estimatedDeviceDispatchSeconds).append(" sec");
					}
					if (summary.onlineDevices <= 0 && summary.totalCommandsQueued > 0) {
						messageBuilder.append(", reason=Commands queued but no websocket device is online.");
					}
					if (hasText(summary.reason)) {
						messageBuilder.append(", detail=").append(summary.reason);
					}
					dbSyncMessage = messageBuilder.toString();
					log.info("[DB-FULL-SYNC] {}", dbSyncMessage);
				} catch (Exception ex) {
					dbSyncState = DB_SYNC_STATE_FAILED;
					dbSyncMessage = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
					log.error("[DB-FULL-SYNC] sync failed. trigger={}, message={}", trigger, dbSyncMessage, ex);
				} finally {
					dbSyncFinishedAtEpochMs = System.currentTimeMillis();
					if (dbSyncDurationMs <= 0L && dbSyncStartedAtEpochMs > 0L) {
						dbSyncDurationMs = dbSyncFinishedAtEpochMs - dbSyncStartedAtEpochMs;
					}
					if (dbSyncWorkerThread == Thread.currentThread()) {
						dbSyncWorkerThread = null;
					}
					dbSyncRunning.set(false);
					log.info("[DB-FULL-SYNC] worker finished. trigger={}, state={}, durationMs={}", trigger, dbSyncState,
							dbSyncDurationMs);
				}
			}
		});
		worker.setName("db-user-sync-worker");
		worker.setDaemon(true);
		dbSyncWorkerThread = worker;
		try {
			worker.start();
		} catch (Throwable th) {
			dbSyncWorkerThread = null;
			dbSyncState = DB_SYNC_STATE_FAILED;
			dbSyncMessage = "Unable to start sync worker: "
					+ (th.getMessage() == null ? th.getClass().getSimpleName() : th.getMessage());
			dbSyncFinishedAtEpochMs = System.currentTimeMillis();
			if (dbSyncStartedAtEpochMs > 0L) {
				dbSyncDurationMs = dbSyncFinishedAtEpochMs - dbSyncStartedAtEpochMs;
			}
			dbSyncRunning.set(false);
			log.error("[DB-FULL-SYNC] worker start failed. trigger={}, message={}", trigger, dbSyncMessage, th);
			return false;
		}
		return true;
	}

	private int countPendingSetUserInfoCommands(JdbcTemplate jdbcTemplate, String serial) {
		if (jdbcTemplate == null || !hasText(serial)) {
			return 0;
		}
		try {
			Integer count = jdbcTemplate.queryForObject(
					"SELECT COUNT(1) FROM DEVICECMD "
							+ "WHERE SLNO = ? AND CMD_DESC = 'JAVA:setuserinfo' "
							+ "AND ISNULL(CASE WHEN ISNUMERIC(DC_RES)=1 THEN CONVERT(INT, DC_RES) ELSE NULL END, 0) = 0 "
							+ "AND ISNULL(CONVERT(INT, IS_DEL_EXECUTED), 0) = 0",
					new Object[] { serial }, Integer.class);
			return count == null ? 0 : count.intValue();
		} catch (Exception ex) {
			return 0;
		}
	}

	private int clearPendingSetUserInfoCommands(JdbcTemplate jdbcTemplate, String serial) {
		if (jdbcTemplate == null || !hasText(serial)) {
			return 0;
		}
		try {
			return jdbcTemplate.update("DELETE FROM DEVICECMD "
					+ "WHERE SLNO = ? AND CMD_DESC = 'JAVA:setuserinfo' "
					+ "AND ISNULL(CASE WHEN ISNUMERIC(DC_RES)=1 THEN CONVERT(INT, DC_RES) ELSE NULL END, 0) = 0 "
					+ "AND ISNULL(CONVERT(INT, IS_DEL_EXECUTED), 0) = 0", serial);
		} catch (Exception ex) {
			return 0;
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

	private Map<Long, List<UserInfo>> buildEnrollRecordsByUser() {
		Map<Long, List<UserInfo>> grouped = new java.util.TreeMap<Long, List<UserInfo>>();
		List<UserInfo> usersToSend = enrollInfoService.usersToSendDevice();
		for (UserInfo info : usersToSend) {
			if (info == null || info.getEnrollId() == null || info.getSourceId() == null
					|| info.getSourceId().longValue() <= 0L) {
				continue;
			}
			if (!isSupportedEnrollBackupNum(info.getBackupnum())) {
				continue;
			}
			if (!hasText(info.getRecord())) {
				continue;
			}
			Long userId = info.getEnrollId();
			List<UserInfo> records = grouped.get(userId);
			if (records == null) {
				records = new ArrayList<UserInfo>();
				grouped.put(userId, records);
			}
			records.add(info);
		}
		return new LinkedHashMap<Long, List<UserInfo>>(grouped);
	}

	private int countTotalEnrollRecords(Map<Long, List<UserInfo>> recordsByUser) {
		if (recordsByUser == null || recordsByUser.isEmpty()) {
			return 0;
		}
		int total = 0;
		for (Map.Entry<Long, List<UserInfo>> entry : recordsByUser.entrySet()) {
			List<UserInfo> records = entry.getValue();
			total += records == null ? 0 : records.size();
		}
		return total;
	}

	private DatabaseSyncDeviceDetail syncDeviceIncrementalByUserId(JdbcTemplate jdbcTemplate, String serial,
			DeviceSyncState existingState, Map<Long, List<UserInfo>> recordsByUser) {
		return syncDeviceByUserId(jdbcTemplate, serial, existingState, recordsByUser, false);
	}

	private DatabaseSyncDeviceDetail syncDeviceFullByUserId(JdbcTemplate jdbcTemplate, String serial,
			DeviceSyncState existingState, Map<Long, List<UserInfo>> recordsByUser) {
		return syncDeviceByUserId(jdbcTemplate, serial, existingState, recordsByUser, true);
	}

	private DatabaseSyncDeviceDetail syncDeviceByUserId(JdbcTemplate jdbcTemplate, String serial,
			DeviceSyncState existingState, Map<Long, List<UserInfo>> recordsByUser, boolean fullSync) {
		DatabaseSyncDeviceDetail detail = new DatabaseSyncDeviceDetail();
		detail.setSerial(serial);
		detail.setOnline(isDeviceOnline(serial));
		long lastSyncedUserId = existingState == null ? 0L : Math.max(0L, existingState.lastSyncUserId);
		long syncCursorBefore = fullSync ? 0L : lastSyncedUserId;
		detail.setLastSyncedUserIdBefore(syncCursorBefore);
		Date startedAt = new Date();
		markDeviceSyncRunning(jdbcTemplate, serial, syncCursorBefore, startedAt, fullSync);
		try {
			int pendingBefore = countPendingSetUserInfoCommands(jdbcTemplate, serial);
			detail.setPendingBefore(pendingBefore);
			detail.setClearedPending(0);

			List<UserInfo> recordsToQueue = fullSync ? collectAllEnrollRecords(recordsByUser)
					: collectDeltaEnrollRecords(recordsByUser, syncCursorBefore);
			int queuedUsers = countDistinctUsers(recordsToQueue);
			detail.setDeltaUserCount(queuedUsers);

			int queuedCleanAdmin = 0;
			int queuedSetuserinfo = queueSetUserInfoRecordsToDevice(recordsToQueue, serial);
			long lastSyncedUserIdAfter = syncCursorBefore;
			detail.setLastSyncedUserIdAfter(lastSyncedUserIdAfter);
			detail.setQueuedSetuserinfo(queuedSetuserinfo);
			detail.setQueuedCleanAdmin(queuedCleanAdmin);

			int pendingAfter = countPendingSetUserInfoCommands(jdbcTemplate, serial);
			detail.setPendingAfter(pendingAfter);
			double dispatchRatePerSecond = resolveSetUserInfoRatePerSecond(jdbcTemplate, serial);
			detail.setDispatchRatePerSecond(toOneDecimal(dispatchRatePerSecond));
			long estimatedDispatchSeconds = estimateDispatchSeconds(pendingAfter, dispatchRatePerSecond);
			detail.setEstimatedDispatchSeconds(estimatedDispatchSeconds);
			detail.setSyncStatus(DB_SYNC_STATE_SUCCESS);
			Date finishedAt = new Date();
			detail.setLastSyncAt(formatDateTime(finishedAt));
			String message;
			if (queuedSetuserinfo == 0) {
				message = fullSync ? "No registration records found." : "No incremental records found.";
			} else {
				message = "Queued " + queuedSetuserinfo + " setuserinfo commands for " + queuedUsers + " users.";
				if (fullSync) {
					message += " (full DB sync).";
				}
			}
			detail.setSyncMessage(message);
			markDeviceSyncFinished(jdbcTemplate, serial, true, message, null, lastSyncedUserIdAfter, queuedSetuserinfo,
					queuedCleanAdmin, startedAt, finishedAt);
		} catch (Exception ex) {
			String errorMessage = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			detail.setSyncStatus(DB_SYNC_STATE_FAILED);
			detail.setSyncMessage(errorMessage);
			detail.setLastSyncedUserIdAfter(syncCursorBefore);
			Date finishedAt = new Date();
			detail.setLastSyncAt(formatDateTime(finishedAt));
			markDeviceSyncFinished(jdbcTemplate, serial, false, fullSync ? "Full sync failed." : "Incremental sync failed.",
					errorMessage, syncCursorBefore, detail.getQueuedSetuserinfo(), detail.getQueuedCleanAdmin(), startedAt,
					finishedAt);
		}
		return detail;
	}

	private List<UserInfo> collectAllEnrollRecords(Map<Long, List<UserInfo>> recordsByUser) {
		List<UserInfo> all = new ArrayList<UserInfo>();
		if (recordsByUser == null || recordsByUser.isEmpty()) {
			return all;
		}
		for (Map.Entry<Long, List<UserInfo>> entry : recordsByUser.entrySet()) {
			List<UserInfo> records = entry.getValue();
			if (records == null || records.isEmpty()) {
				continue;
			}
			all.addAll(records);
		}
		sortRecordsBySourceId(all);
		return all;
	}

	private List<UserInfo> collectDeltaEnrollRecords(Map<Long, List<UserInfo>> recordsByUser, long lastSyncedUserId) {
		List<UserInfo> delta = new ArrayList<UserInfo>();
		if (recordsByUser == null || recordsByUser.isEmpty()) {
			return delta;
		}
		for (Map.Entry<Long, List<UserInfo>> entry : recordsByUser.entrySet()) {
			List<UserInfo> records = entry.getValue();
			if (records == null || records.isEmpty()) {
				continue;
			}
			for (UserInfo record : records) {
				if (record == null || record.getSourceId() == null
						|| record.getSourceId().longValue() <= lastSyncedUserId) {
					continue;
				}
				delta.add(record);
			}
		}
		sortRecordsBySourceId(delta);
		return delta;
	}

	private long resolveMaxEnrollId(List<UserInfo> records, long defaultValue) {
		long maxUserId = defaultValue;
		if (records == null || records.isEmpty()) {
			return maxUserId;
		}
		for (UserInfo record : records) {
			if (record != null && record.getSourceId() != null
					&& record.getSourceId().longValue() > maxUserId) {
				maxUserId = record.getSourceId().longValue();
			}
		}
		return maxUserId;
	}

	private void sortRecordsBySourceId(List<UserInfo> records) {
		if (records == null || records.size() <= 1) {
			return;
		}
		java.util.Collections.sort(records, new java.util.Comparator<UserInfo>() {
			@Override
			public int compare(UserInfo left, UserInfo right) {
				long leftId = left == null || left.getSourceId() == null ? Long.MAX_VALUE : left.getSourceId().longValue();
				long rightId = right == null || right.getSourceId() == null ? Long.MAX_VALUE : right.getSourceId().longValue();
				return Long.compare(leftId, rightId);
			}
		});
	}

	private boolean isFirstRunForDevice(DeviceSyncState existingState) {
		return existingState == null || !hasText(existingState.lastSyncStatus)
				|| "NEVER".equalsIgnoreCase(existingState.lastSyncStatus);
	}

	private int countDistinctUsers(List<UserInfo> records) {
		if (records == null || records.isEmpty()) {
			return 0;
		}
		Set<Long> users = new LinkedHashSet<Long>();
		for (UserInfo info : records) {
			if (info != null && info.getEnrollId() != null) {
				users.add(info.getEnrollId());
			}
		}
		return users.size();
	}

	private int queueSetUserInfoRecordsToDevice(List<UserInfo> records, String deviceSn) {
		if (!hasText(deviceSn) || records == null || records.isEmpty()) {
			return 0;
		}
		int queued = 0;
		List<MachineCommand> batch = new ArrayList<MachineCommand>(BULK_SYNC_INSERT_BATCH_SIZE);
		for (UserInfo info : records) {
			MachineCommand command = buildSetUserInfoCommand(info, deviceSn);
			if (command == null) {
				continue;
			}
			batch.add(command);
			if (batch.size() >= BULK_SYNC_INSERT_BATCH_SIZE) {
				queued += insertMachineCommandsBatch(batch);
				batch.clear();
			}
		}
		if (!batch.isEmpty()) {
			queued += insertMachineCommandsBatch(batch);
		}
		return queued;
	}

	private MachineCommand buildSetUserInfoCommand(UserInfo info, String deviceSn) {
		if (info == null || info.getEnrollId() == null || info.getSourceId() == null || !hasText(deviceSn)) {
			return null;
		}
		int backupNum = info.getBackupnum();
		if (!isSupportedEnrollBackupNum(backupNum) || !hasText(info.getRecord())) {
			return null;
		}
		MachineCommand command = new MachineCommand();
		command.setName("setuserinfo");
		command.setStatus(0);
		command.setSendStatus(0);
		command.setErrCount(0);
		command.setSerial(deviceSn);
		command.setGmtCrate(new Date());
		command.setGmtModified(new Date());
		int safeAdmin = sanitizeAdminForDevice(info.getEnrollId(), Integer.valueOf(info.getAdmin()));
		String payload = buildSetUserInfoPayload(info.getEnrollId(), info.getName(), backupNum, safeAdmin,
				info.getRecord());
		command.setContent(buildSetUserInfoCommandEnvelope(info.getSourceId().longValue(), payload));
		return command;
	}

	private String buildSetUserInfoCommandEnvelope(long sourceId, String payload) {
		return "{\"meta\":{\"sourceRowId\":" + sourceId + "},\"payload\":" + payload + "}";
	}

	private String buildSetUserInfoPayload(Long enrollId, String name, int backupNum, int admin, String record) {
		String safeName = escapeJson(name == null ? "" : name);
		String safeRecord = record == null ? "" : record.trim();
		if (backupNum == 10 || backupNum == 11) {
			if (safeRecord.matches("-?\\d+")) {
				return "{\"cmd\":\"setuserinfo\",\"enrollid\":" + enrollId + ",\"name\":\"" + safeName
						+ "\",\"backupnum\":" + backupNum + ",\"admin\":" + admin + ",\"record\":" + safeRecord + "}";
			}
			return "{\"cmd\":\"setuserinfo\",\"enrollid\":" + enrollId + ",\"name\":\"" + safeName
					+ "\",\"backupnum\":" + backupNum + ",\"admin\":" + admin + ",\"record\":\""
					+ escapeJson(safeRecord) + "\"}";
		}
		return "{\"cmd\":\"setuserinfo\",\"enrollid\":" + enrollId + ",\"name\":\"" + safeName
				+ "\",\"backupnum\":" + backupNum + ",\"admin\":" + admin + ",\"record\":\""
				+ escapeJson(safeRecord) + "\"}";
	}

	private int sanitizeAdminForDevice(Long enrollId, Integer admin) {
		if (!ALLOW_DEVICE_ADMIN_SYNC) {
			return 0;
		}
		return admin == null ? 0 : Math.max(0, admin.intValue());
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

	private String safeDeviceSyncMessage(String message) {
		return truncate(message, 1800);
	}

	private void markDeviceSyncRunning(JdbcTemplate jdbcTemplate, String serial, long lastSyncedUserId, Date startedAt,
			boolean fullSync) {
		ensureDeviceUserSyncStateTable(jdbcTemplate);
		String status = DB_SYNC_STATE_RUNNING;
		String message = safeDeviceSyncMessage(fullSync ? "Full DB sync running." : "Incremental sync running.");
		int updated = jdbcTemplate.update(
				"UPDATE " + DEVICE_USER_SYNC_STATE_TABLE
						+ " SET LAST_SYNC_STATUS = ?, LAST_SYNC_STARTED_AT = ?, LAST_SYNC_MESSAGE = ?, UPDATED_AT = SYSDATETIME() "
						+ " WHERE DEVICE_SN = ?",
				status, toTimestamp(startedAt), message, serial);
		if (updated > 0) {
			return;
		}
		jdbcTemplate.update(
				"INSERT INTO " + DEVICE_USER_SYNC_STATE_TABLE + " (DEVICE_SN, LAST_SYNC_STATUS, LAST_SYNC_AT, LAST_SYNC_STARTED_AT, "
						+ "LAST_SYNC_FINISHED_AT, LAST_SYNC_MESSAGE, LAST_SYNC_USER_ID, LAST_QUEUED_SETUSERINFO, LAST_QUEUED_CLEANADMIN, "
						+ "LAST_ERROR_MESSAGE, TOTAL_RUNS, UPDATED_AT) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, SYSDATETIME())",
				serial, status, null, toTimestamp(startedAt), null, message, Long.valueOf(Math.max(0L, lastSyncedUserId)),
				Integer.valueOf(0), Integer.valueOf(0), null, Long.valueOf(0L));
	}

	private void markDeviceSyncFinished(JdbcTemplate jdbcTemplate, String serial, boolean success, String message,
			String errorMessage, long lastSyncedUserId, int queuedSetuserinfo, int queuedCleanAdmin, Date startedAt,
			Date finishedAt) {
		ensureDeviceUserSyncStateTable(jdbcTemplate);
		String status = success ? DB_SYNC_STATE_SUCCESS : DB_SYNC_STATE_FAILED;
		String syncMessage = safeDeviceSyncMessage(message);
		String safeError = safeDeviceSyncMessage(errorMessage);
		int updated = jdbcTemplate.update(
				"UPDATE " + DEVICE_USER_SYNC_STATE_TABLE
						+ " SET LAST_SYNC_STATUS = ?, LAST_SYNC_AT = ?, LAST_SYNC_STARTED_AT = ?, LAST_SYNC_FINISHED_AT = ?, "
						+ "LAST_SYNC_MESSAGE = ?, LAST_ERROR_MESSAGE = ?, LAST_SYNC_USER_ID = ?, LAST_QUEUED_SETUSERINFO = ?, "
						+ "LAST_QUEUED_CLEANADMIN = ?, TOTAL_RUNS = ISNULL(TOTAL_RUNS, 0) + 1, UPDATED_AT = SYSDATETIME() "
						+ "WHERE DEVICE_SN = ?",
				status, toTimestamp(finishedAt), toTimestamp(startedAt), toTimestamp(finishedAt), syncMessage, safeError,
				Long.valueOf(Math.max(0L, lastSyncedUserId)), Integer.valueOf(Math.max(0, queuedSetuserinfo)),
				Integer.valueOf(Math.max(0, queuedCleanAdmin)), serial);
		if (updated > 0) {
			return;
		}
		jdbcTemplate.update(
				"INSERT INTO " + DEVICE_USER_SYNC_STATE_TABLE + " (DEVICE_SN, LAST_SYNC_STATUS, LAST_SYNC_AT, LAST_SYNC_STARTED_AT, "
						+ "LAST_SYNC_FINISHED_AT, LAST_SYNC_MESSAGE, LAST_SYNC_USER_ID, LAST_QUEUED_SETUSERINFO, LAST_QUEUED_CLEANADMIN, "
						+ "LAST_ERROR_MESSAGE, TOTAL_RUNS, UPDATED_AT) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, SYSDATETIME())",
				serial, status, toTimestamp(finishedAt), toTimestamp(startedAt), toTimestamp(finishedAt), syncMessage,
				Long.valueOf(Math.max(0L, lastSyncedUserId)), Integer.valueOf(Math.max(0, queuedSetuserinfo)),
				Integer.valueOf(Math.max(0, queuedCleanAdmin)), safeError, Long.valueOf(1L));
	}

	private void ensureDeviceUserSyncStateTable(JdbcTemplate jdbcTemplate) {
		if (deviceUserSyncStateTableEnsured || jdbcTemplate == null) {
			return;
		}
		String ddl = "IF OBJECT_ID('dbo." + DEVICE_USER_SYNC_STATE_TABLE + "', 'U') IS NULL "
				+ "BEGIN "
				+ "CREATE TABLE dbo." + DEVICE_USER_SYNC_STATE_TABLE + " ("
				+ "DEVICE_SN VARCHAR(100) NOT NULL PRIMARY KEY, "
				+ "LAST_SYNC_STATUS VARCHAR(20) NOT NULL DEFAULT 'NEVER', "
				+ "LAST_SYNC_AT DATETIME2(0) NULL, "
				+ "LAST_SYNC_STARTED_AT DATETIME2(0) NULL, "
				+ "LAST_SYNC_FINISHED_AT DATETIME2(0) NULL, "
				+ "LAST_SYNC_MESSAGE NVARCHAR(2000) NULL, "
				+ "LAST_SYNC_USER_ID BIGINT NOT NULL DEFAULT 0, "
				+ "LAST_QUEUED_SETUSERINFO INT NOT NULL DEFAULT 0, "
				+ "LAST_QUEUED_CLEANADMIN INT NOT NULL DEFAULT 0, "
				+ "LAST_ERROR_MESSAGE NVARCHAR(2000) NULL, "
				+ "TOTAL_RUNS BIGINT NOT NULL DEFAULT 0, "
				+ "UPDATED_AT DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()"
				+ "); "
				+ "END; "
				+ "IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_" + DEVICE_USER_SYNC_STATE_TABLE
				+ "_UPDATED_AT' AND object_id = OBJECT_ID('dbo." + DEVICE_USER_SYNC_STATE_TABLE + "')) "
				+ "BEGIN "
				+ "CREATE INDEX IX_" + DEVICE_USER_SYNC_STATE_TABLE + "_UPDATED_AT ON dbo."
				+ DEVICE_USER_SYNC_STATE_TABLE + " (UPDATED_AT DESC); "
				+ "END;";
		jdbcTemplate.execute(ddl);
		deviceUserSyncStateTableEnsured = true;
	}

	private Map<String, DeviceSyncState> loadDeviceSyncStateBySerial(JdbcTemplate jdbcTemplate, List<String> serials) {
		Map<String, DeviceSyncState> stateBySerial = new LinkedHashMap<String, DeviceSyncState>();
		if (jdbcTemplate == null || serials == null || serials.isEmpty()) {
			return stateBySerial;
		}
		ensureDeviceUserSyncStateTable(jdbcTemplate);
		String sql = "SELECT DEVICE_SN, LAST_SYNC_STATUS, LAST_SYNC_AT, LAST_SYNC_STARTED_AT, LAST_SYNC_FINISHED_AT, "
				+ "LAST_SYNC_MESSAGE, LAST_SYNC_USER_ID, LAST_QUEUED_SETUSERINFO, LAST_QUEUED_CLEANADMIN, "
				+ "TOTAL_RUNS, LAST_ERROR_MESSAGE FROM " + DEVICE_USER_SYNC_STATE_TABLE
				+ " WHERE DEVICE_SN IN (" + buildSqlPlaceholders(serials.size()) + ")";
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, serials.toArray());
		for (Map<String, Object> row : rows) {
			String serial = toStringSafe(row.get("DEVICE_SN"));
			if (!hasText(serial)) {
				continue;
			}
			DeviceSyncState state = new DeviceSyncState();
			state.serial = serial.trim();
			state.lastSyncStatus = toStringSafe(row.get("LAST_SYNC_STATUS"));
			state.lastSyncAt = toStringSafe(row.get("LAST_SYNC_AT"));
			state.lastSyncStartedAt = toStringSafe(row.get("LAST_SYNC_STARTED_AT"));
			state.lastSyncFinishedAt = toStringSafe(row.get("LAST_SYNC_FINISHED_AT"));
			state.lastSyncMessage = toStringSafe(row.get("LAST_SYNC_MESSAGE"));
			state.lastSyncUserId = toLong(row.get("LAST_SYNC_USER_ID"), 0L);
			state.lastQueuedSetuserinfo = (int) toLong(row.get("LAST_QUEUED_SETUSERINFO"), 0L);
			state.lastQueuedCleanadmin = (int) toLong(row.get("LAST_QUEUED_CLEANADMIN"), 0L);
			state.totalRuns = toLong(row.get("TOTAL_RUNS"), 0L);
			state.lastErrorMessage = toStringSafe(row.get("LAST_ERROR_MESSAGE"));
			stateBySerial.put(state.serial.toUpperCase(), state);
		}
		return stateBySerial;
	}

	private List<Map<String, Object>> loadDeviceSyncStateRows() {
		try {
			JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
			ensureDeviceUserSyncStateTable(jdbcTemplate);
			String sql = "WITH devices AS ("
					+ " SELECT DISTINCT LTRIM(RTRIM(SLNO)) AS serial FROM DEVICEINFO "
					+ " WHERE LTRIM(RTRIM(ISNULL(SLNO, ''))) <> ''"
					+ ") "
					+ "SELECT d.serial AS serial, "
					+ "ISNULL(s.LAST_SYNC_STATUS, 'NEVER') AS lastSyncStatus, "
					+ "CONVERT(VARCHAR(19), s.LAST_SYNC_AT, 120) AS lastSyncAt, "
					+ "CONVERT(VARCHAR(19), s.LAST_SYNC_STARTED_AT, 120) AS lastSyncStartedAt, "
					+ "CONVERT(VARCHAR(19), s.LAST_SYNC_FINISHED_AT, 120) AS lastSyncFinishedAt, "
					+ "ISNULL(s.LAST_SYNC_USER_ID, 0) AS lastSyncUserId, "
					+ "ISNULL(s.LAST_QUEUED_SETUSERINFO, 0) AS lastQueuedSetuserinfo, "
					+ "ISNULL(s.LAST_QUEUED_CLEANADMIN, 0) AS lastQueuedCleanadmin, "
					+ "ISNULL((SELECT COUNT(1) FROM DEVICECMD dc "
					+ " WHERE LTRIM(RTRIM(ISNULL(dc.SLNO, ''))) = d.serial "
					+ " AND dc.CMD_DESC = 'JAVA:setuserinfo' "
					+ " AND ISNUMERIC(dc.DC_RES) = 1 AND CONVERT(INT, dc.DC_RES) = 1), 0) AS deliveredSetuserinfoCount, "
					+ "ISNULL(s.TOTAL_RUNS, 0) AS totalRuns, "
					+ "ISNULL(s.LAST_SYNC_MESSAGE, '') AS lastSyncMessage, "
					+ "ISNULL(s.LAST_ERROR_MESSAGE, '') AS lastErrorMessage "
					+ "FROM devices d "
					+ "LEFT JOIN " + DEVICE_USER_SYNC_STATE_TABLE + " s ON LTRIM(RTRIM(s.DEVICE_SN)) = d.serial "
					+ "ORDER BY d.serial";
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
			List<Map<String, Object>> enriched = new ArrayList<Map<String, Object>>(rows.size());
			for (Map<String, Object> row : rows) {
				Map<String, Object> copy = new LinkedHashMap<String, Object>(row);
				String serial = toStringSafe(copy.get("serial"));
				copy.put("online", Boolean.valueOf(isDeviceOnline(serial)));
				enriched.add(copy);
			}
			return enriched;
		} catch (Exception ex) {
			return new ArrayList<Map<String, Object>>();
		}
	}

	private String buildSqlPlaceholders(int size) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < size; i++) {
			if (i > 0) {
				sb.append(",");
			}
			sb.append("?");
		}
		return sb.toString();
	}

	private long toLong(Object value, long defaultValue) {
		if (value == null) {
			return defaultValue;
		}
		try {
			if (value instanceof Number) {
				return ((Number) value).longValue();
			}
			String text = String.valueOf(value).trim();
			if (text.isEmpty()) {
				return defaultValue;
			}
			return Long.parseLong(text);
		} catch (Exception ex) {
			return defaultValue;
		}
	}

	private String toStringSafe(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private String formatDateTime(Date value) {
		if (value == null) {
			return "";
		}
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value);
	}

	private Timestamp toTimestamp(Date value) {
		return value == null ? null : new Timestamp(value.getTime());
	}

	private static final class DeviceSyncState {
		private String serial;
		private String lastSyncStatus;
		private String lastSyncAt;
		private String lastSyncStartedAt;
		private String lastSyncFinishedAt;
		private String lastSyncMessage;
		private String lastErrorMessage;
		private long lastSyncUserId;
		private int lastQueuedSetuserinfo;
		private int lastQueuedCleanadmin;
		private long totalRuns;
	}

	public static class DatabaseSyncDeviceDetail {
		private String serial;
		private boolean online;
		private int pendingBefore;
		private int pendingAfter;
		private int clearedPending;
		private int queuedSetuserinfo;
		private int queuedCleanAdmin;
		private int deltaUserCount;
		private long lastSyncedUserIdBefore;
		private long lastSyncedUserIdAfter;
		private String syncStatus;
		private String syncMessage;
		private String lastSyncAt;
		private long estimatedDispatchSeconds;
		private double dispatchRatePerSecond;

		public String getSerial() {
			return serial;
		}

		public void setSerial(String serial) {
			this.serial = serial;
		}

		public boolean isOnline() {
			return online;
		}

		public void setOnline(boolean online) {
			this.online = online;
		}

		public int getPendingBefore() {
			return pendingBefore;
		}

		public void setPendingBefore(int pendingBefore) {
			this.pendingBefore = pendingBefore;
		}

		public int getClearedPending() {
			return clearedPending;
		}

		public void setClearedPending(int clearedPending) {
			this.clearedPending = clearedPending;
		}

		public int getPendingAfter() {
			return pendingAfter;
		}

		public void setPendingAfter(int pendingAfter) {
			this.pendingAfter = pendingAfter;
		}

		public int getQueuedSetuserinfo() {
			return queuedSetuserinfo;
		}

		public void setQueuedSetuserinfo(int queuedSetuserinfo) {
			this.queuedSetuserinfo = queuedSetuserinfo;
		}

		public int getQueuedCleanAdmin() {
			return queuedCleanAdmin;
		}

		public void setQueuedCleanAdmin(int queuedCleanAdmin) {
			this.queuedCleanAdmin = queuedCleanAdmin;
		}

		public int getDeltaUserCount() {
			return deltaUserCount;
		}

		public void setDeltaUserCount(int deltaUserCount) {
			this.deltaUserCount = deltaUserCount;
		}

		public long getLastSyncedUserIdBefore() {
			return lastSyncedUserIdBefore;
		}

		public void setLastSyncedUserIdBefore(long lastSyncedUserIdBefore) {
			this.lastSyncedUserIdBefore = lastSyncedUserIdBefore;
		}

		public long getLastSyncedUserIdAfter() {
			return lastSyncedUserIdAfter;
		}

		public void setLastSyncedUserIdAfter(long lastSyncedUserIdAfter) {
			this.lastSyncedUserIdAfter = lastSyncedUserIdAfter;
		}

		public String getSyncStatus() {
			return syncStatus;
		}

		public void setSyncStatus(String syncStatus) {
			this.syncStatus = syncStatus;
		}

		public String getSyncMessage() {
			return syncMessage;
		}

		public void setSyncMessage(String syncMessage) {
			this.syncMessage = syncMessage;
		}

		public String getLastSyncAt() {
			return lastSyncAt;
		}

		public void setLastSyncAt(String lastSyncAt) {
			this.lastSyncAt = lastSyncAt;
		}

		public long getEstimatedDispatchSeconds() {
			return estimatedDispatchSeconds;
		}

		public void setEstimatedDispatchSeconds(long estimatedDispatchSeconds) {
			this.estimatedDispatchSeconds = estimatedDispatchSeconds;
		}

		public double getDispatchRatePerSecond() {
			return dispatchRatePerSecond;
		}

		public void setDispatchRatePerSecond(double dispatchRatePerSecond) {
			this.dispatchRatePerSecond = dispatchRatePerSecond;
		}
	}

	private static final class UsernameDeltaSyncSummary {
		private int devices;
		private int onlineDevices;
		private int totalUsers;
		private int changedUsers;
		private int changedStatuses;
		private int usernameCommandsQueued;
		private int statusCommandsQueued;
		private int totalCommandsQueued;
		private long durationMs;
		private long estimatedDeviceDispatchSeconds;
		private String snapshotFile;
		private String reportFile;
		private String reason;
		private List<UsernameDeltaSyncService.DeviceSyncDetail> deviceDetails;
	}

	private UsernameDeltaSyncSummary runUsernameDeltaSyncNow() {
		UsernameDeltaSyncService.SyncResult syncResult = usernameDeltaSyncService.syncChangedUsersToAllDevices("manual");
		if (!syncResult.isSuccess()) {
			throw new IllegalStateException(syncResult.getError());
		}
		UsernameDeltaSyncSummary summary = new UsernameDeltaSyncSummary();
		summary.devices = syncResult.getDevices();
		summary.onlineDevices = syncResult.getOnlineDevices();
		summary.totalUsers = syncResult.getTotalUsers();
		summary.changedUsers = syncResult.getUsernameDeltaUsers();
		summary.changedStatuses = syncResult.getStatusDeltaUsers();
		summary.usernameCommandsQueued = syncResult.getUsernameCommandsQueued();
		summary.statusCommandsQueued = syncResult.getStatusCommandsQueued();
		summary.totalCommandsQueued = syncResult.getTotalCommandsQueued();
		summary.durationMs = syncResult.getDurationMs();
		summary.estimatedDeviceDispatchSeconds = syncResult.getEstimatedDeviceDispatchSeconds();
		summary.snapshotFile = syncResult.getSnapshotFile();
		summary.reportFile = syncResult.getReportFile();
		summary.reason = syncResult.getReason();
		summary.deviceDetails = syncResult.getDeviceDetails();
		return summary;
	}

	private Msg getUsernameDeltaSyncStatusMsg() {
		Msg msg = Msg.success().add("running", usernameDeltaSyncRunning.get())
				.add("state", usernameDeltaSyncState)
				.add("message", usernameDeltaSyncMessage)
				.add("devices", usernameDeltaSyncDevices)
				.add("onlineDevices", usernameDeltaSyncOnlineDevices)
				.add("totalUsers", usernameDeltaSyncTotalUsers)
				.add("changedUsers", usernameDeltaSyncChangedUsers)
				.add("changedStatuses", usernameDeltaSyncChangedStatuses)
				.add("usernameCommandsQueued", usernameDeltaSyncUsernameCommands)
				.add("statusCommandsQueued", usernameDeltaSyncStatusCommands)
				.add("totalCommandsQueued", usernameDeltaSyncQueuedCommands)
				.add("durationMs", usernameDeltaSyncDurationMs)
				.add("estimatedDeviceDispatchSeconds", usernameDeltaSyncEstimatedDeviceDispatchSeconds)
				.add("snapshotFile", usernameDeltaSyncSnapshotFile)
				.add("reportFile", usernameDeltaSyncReportFile)
				.add("reason", usernameDeltaSyncReason)
				.add("deviceDetails", usernameDeltaSyncDeviceDetails)
				.add("wsConnectedDevices", WebSocketPool.wsDevice.size())
				.add("wsConnectedSerials", new ArrayList<String>(WebSocketPool.wsDevice.keySet()));
		if (usernameDeltaSyncStartedAtEpochMs > 0L) {
			msg.add("startedAt", new Date(usernameDeltaSyncStartedAtEpochMs));
		}
		if (usernameDeltaSyncFinishedAtEpochMs > 0L) {
			msg.add("finishedAt", new Date(usernameDeltaSyncFinishedAtEpochMs));
		}
		return msg;
	}

	private boolean triggerUsernameDeltaSyncAsync(final String trigger) {
		if (!usernameDeltaSyncRunning.compareAndSet(false, true)) {
			log.warn("[USERNAME-DELTA-SYNC] trigger ignored because sync is already running. trigger={}", trigger);
			return false;
		}
		usernameDeltaSyncState = DB_SYNC_STATE_RUNNING;
		usernameDeltaSyncMessage = "Username delta sync started by " + trigger;
		usernameDeltaSyncStartedAtEpochMs = System.currentTimeMillis();
		usernameDeltaSyncFinishedAtEpochMs = 0L;
		usernameDeltaSyncDevices = 0;
		usernameDeltaSyncOnlineDevices = 0;
		usernameDeltaSyncTotalUsers = 0;
		usernameDeltaSyncChangedUsers = 0;
		usernameDeltaSyncChangedStatuses = 0;
		usernameDeltaSyncUsernameCommands = 0;
		usernameDeltaSyncStatusCommands = 0;
		usernameDeltaSyncQueuedCommands = 0;
		usernameDeltaSyncDurationMs = 0L;
		usernameDeltaSyncEstimatedDeviceDispatchSeconds = 0L;
		usernameDeltaSyncSnapshotFile = "";
		usernameDeltaSyncReportFile = "";
		usernameDeltaSyncReason = "";
		usernameDeltaSyncDeviceDetails = new ArrayList<UsernameDeltaSyncService.DeviceSyncDetail>();

		Thread worker = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					UsernameDeltaSyncSummary summary = runUsernameDeltaSyncNow();
					usernameDeltaSyncDevices = summary.devices;
					usernameDeltaSyncOnlineDevices = summary.onlineDevices;
					usernameDeltaSyncTotalUsers = summary.totalUsers;
					usernameDeltaSyncChangedUsers = summary.changedUsers;
					usernameDeltaSyncChangedStatuses = summary.changedStatuses;
					usernameDeltaSyncUsernameCommands = summary.usernameCommandsQueued;
					usernameDeltaSyncStatusCommands = summary.statusCommandsQueued;
					usernameDeltaSyncQueuedCommands = summary.totalCommandsQueued;
					usernameDeltaSyncDurationMs = summary.durationMs;
					usernameDeltaSyncEstimatedDeviceDispatchSeconds = summary.estimatedDeviceDispatchSeconds;
					usernameDeltaSyncSnapshotFile = summary.snapshotFile == null ? "" : summary.snapshotFile;
					usernameDeltaSyncReportFile = summary.reportFile == null ? "" : summary.reportFile;
					usernameDeltaSyncReason = summary.reason == null ? "" : summary.reason;
					usernameDeltaSyncDeviceDetails = summary.deviceDetails == null
							? new ArrayList<UsernameDeltaSyncService.DeviceSyncDetail>()
							: new ArrayList<UsernameDeltaSyncService.DeviceSyncDetail>(summary.deviceDetails);
					usernameDeltaSyncState = DB_SYNC_STATE_SUCCESS;

					StringBuilder messageBuilder = new StringBuilder();
					messageBuilder.append("Username delta sync completed in ").append(summary.durationMs).append(" ms")
							.append(", changedUsers=").append(summary.changedUsers)
							.append(", changedStatuses=").append(summary.changedStatuses)
							.append(", usernameCommands=").append(summary.usernameCommandsQueued)
							.append(", statusCommands=").append(summary.statusCommandsQueued)
							.append(", totalCommands=").append(summary.totalCommandsQueued);
					if (summary.estimatedDeviceDispatchSeconds > 0L) {
						messageBuilder.append(", estimatedDeviceDispatch=")
								.append(summary.estimatedDeviceDispatchSeconds).append(" sec");
					}
					if (summary.totalCommandsQueued == 0) {
						messageBuilder.append(", reason=No delta found.");
					} else if (summary.onlineDevices <= 0) {
						messageBuilder.append(", reason=Commands queued but no websocket device is online.");
					}
					if (hasText(summary.reason)) {
						messageBuilder.append(", detail=").append(summary.reason);
					}
					if (hasText(summary.reportFile)) {
						messageBuilder.append(", reportFile=").append(summary.reportFile);
					}
					usernameDeltaSyncMessage = messageBuilder.toString();
					log.info("[USERNAME-DELTA-SYNC] {}", usernameDeltaSyncMessage);
				} catch (Exception ex) {
					usernameDeltaSyncState = DB_SYNC_STATE_FAILED;
					usernameDeltaSyncMessage = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
					log.error("[USERNAME-DELTA-SYNC] sync failed. trigger={}, message={}", trigger,
							usernameDeltaSyncMessage, ex);
				} finally {
					usernameDeltaSyncFinishedAtEpochMs = System.currentTimeMillis();
					if (usernameDeltaSyncDurationMs <= 0L && usernameDeltaSyncStartedAtEpochMs > 0L) {
						usernameDeltaSyncDurationMs = usernameDeltaSyncFinishedAtEpochMs - usernameDeltaSyncStartedAtEpochMs;
					}
					usernameDeltaSyncRunning.set(false);
				}
			}
		});
		worker.setName("username-delta-sync-worker");
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
		if (pic == null || pic.isEmpty()) {
			return "";
		}
		return Base64.getEncoder().encodeToString(pic.getBytes());
	}

	private String normalizeText(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private String normalizeInOutMode(String mode) {
		String normalized = normalizeText(mode);
		if (normalized == null) {
			return null;
		}
		String upper = normalized.toUpperCase();
		if ("IN".equals(upper) || "0".equals(upper) || "ENTRY".equals(upper)) {
			return "IN";
		}
		if ("OUT".equals(upper) || "1".equals(upper) || "EXIT".equals(upper)) {
			return "OUT";
		}
		if ("AUTO".equals(upper) || "DEFAULT".equals(upper)) {
			return "AUTO";
		}
		return null;
	}

	private void addMapEntries(Msg msg, Map<String, Object> values) {
		if (msg == null || values == null || values.isEmpty()) {
			return;
		}
		for (Map.Entry<String, Object> entry : values.entrySet()) {
			msg.add(entry.getKey(), entry.getValue());
		}
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

	private double resolveSetUserInfoRatePerSecond(JdbcTemplate jdbcTemplate, String serial) {
		if (jdbcTemplate == null || !hasText(serial)) {
			return DEFAULT_SETUSERINFO_RATE_PER_SECOND;
		}
		try {
			Map<String, Object> row = jdbcTemplate.queryForMap(
					"SELECT COUNT(1) AS success_count, MIN(DC_EXECDATE) AS first_exec, MAX(DC_EXECDATE) AS last_exec "
							+ "FROM DEVICECMD "
							+ "WHERE SLNO = ? AND CMD_DESC = 'JAVA:setuserinfo' "
							+ "AND ISNUMERIC(DC_RES) = 1 AND CONVERT(INT, DC_RES) = 1 "
							+ "AND DC_EXECDATE IS NOT NULL "
							+ "AND DC_EXECDATE >= DATEADD(MINUTE, ?, GETDATE())",
					new Object[] { serial, -SETUSERINFO_RATE_LOOKBACK_MINUTES });
			Number successCountNumber = (Number) row.get("success_count");
			int successCount = successCountNumber == null ? 0 : successCountNumber.intValue();
			if (successCount < MIN_SETUSERINFO_RATE_SAMPLE) {
				return DEFAULT_SETUSERINFO_RATE_PER_SECOND;
			}
			Timestamp firstExec = (Timestamp) row.get("first_exec");
			Timestamp lastExec = (Timestamp) row.get("last_exec");
			if (firstExec == null || lastExec == null) {
				return DEFAULT_SETUSERINFO_RATE_PER_SECOND;
			}
			long durationMs = lastExec.getTime() - firstExec.getTime();
			if (durationMs <= 0L) {
				return DEFAULT_SETUSERINFO_RATE_PER_SECOND;
			}
			double ratePerSecond = successCount / (durationMs / 1000.0d);
			if (ratePerSecond < MIN_SETUSERINFO_RATE_PER_SECOND || ratePerSecond > MAX_SETUSERINFO_RATE_PER_SECOND) {
				return DEFAULT_SETUSERINFO_RATE_PER_SECOND;
			}
			return ratePerSecond;
		} catch (Exception ex) {
			return DEFAULT_SETUSERINFO_RATE_PER_SECOND;
		}
	}

	private long estimateDispatchSeconds(int pendingCount, double ratePerSecond) {
		if (pendingCount <= 0) {
			return 0L;
		}
		double safeRate = ratePerSecond <= 0.0d ? DEFAULT_SETUSERINFO_RATE_PER_SECOND : ratePerSecond;
		return (long) Math.ceil(pendingCount / safeRate);
	}

	private double toOneDecimal(double value) {
		return Math.round(value * 10.0d) / 10.0d;
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
		return Msg.success().add("exportDir", WSServer.getDeviceUserExportDirPath())
				.add("bundleFile", WSServer.getDeviceUserFullExportFilePath())
				.add("note", "Device user export files are being written during websocket responses.");
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
		String normalizedSn = normalizeText(deviceSn);
		if (!hasText(normalizedSn)) {
			return Msg.fail().add("error", "deviceSn is required.");
		}
		if (deviceService.selectDeviceBySerialNum(normalizedSn) == null) {
			return Msg.fail().add("error", "Selected device not found in DEVICEINFO: " + normalizedSn);
		}
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		ensureDeviceUserSyncStateTable(jdbcTemplate);
		Map<Long, List<UserInfo>> recordsByUser = buildEnrollRecordsByUser();
		Map<String, DeviceSyncState> stateBySerial = loadDeviceSyncStateBySerial(jdbcTemplate,
				java.util.Collections.singletonList(normalizedSn));
		DeviceSyncState existingState = stateBySerial.get(normalizedSn.toUpperCase());
		if (existingState == null) {
			existingState = stateBySerial.get(normalizedSn);
		}
		boolean fullSync = existingState == null || existingState.lastSyncUserId <= 0L;
		long resumeFromUserId = existingState == null ? 0L : Math.max(0L, existingState.lastSyncUserId);
		int pendingBefore = countPendingSetUserInfoCommands(jdbcTemplate, normalizedSn);
		if (pendingBefore > 0) {
			return Msg.fail()
					.add("error",
							"Pending setuserinfo commands already exist for selected device. Clear or wait before starting another sync.")
					.add("deviceSn", normalizedSn)
					.add("pendingBefore", pendingBefore)
					.add("syncMode", fullSync ? "FULL" : "INCREMENTAL")
					.add("resumeFromUserId", Long.valueOf(resumeFromUserId))
					.add("resumeFromRowId", Long.valueOf(resumeFromUserId));
		}
		List<UserInfo> recordsToQueue = fullSync ? collectAllEnrollRecords(recordsByUser)
				: collectDeltaEnrollRecords(recordsByUser, resumeFromUserId);
		int queuedUsers = countDistinctUsers(recordsToQueue);
		int totalRecords = countTotalEnrollRecords(recordsByUser);

		int faceRecords = 0;
		int imageRecords = 0;
		int passwordRecords = 0;
		int cardRecords = 0;
		for (int i = 0; i < recordsToQueue.size(); i++) {
			UserInfo enrollInfo = recordsToQueue.get(i);
			if (enrollInfo == null) {
				continue;
			}
			int backupNum = enrollInfo.getBackupnum();
			if (backupNum >= 20 && backupNum <= 27) {
				faceRecords++;
			} else if (backupNum == 50) {
				imageRecords++;
			} else if (backupNum == 10) {
				passwordRecords++;
			} else if (backupNum == 11) {
				cardRecords++;
			}
		}

		DatabaseSyncDeviceDetail detail = fullSync
				? syncDeviceFullByUserId(jdbcTemplate, normalizedSn, existingState, recordsByUser)
				: syncDeviceIncrementalByUserId(jdbcTemplate, normalizedSn, existingState, recordsByUser);
		int activeUsers = recordsByUser.size();
		long estimatedSeconds = detail.getEstimatedDispatchSeconds();
		long estimatedMinutes = (long) Math.ceil(estimatedSeconds / 60.0d);
		Map<String, Object> deviceSyncState = null;
		List<Map<String, Object>> deviceStateRows = loadDeviceSyncStateRows();
		for (Map<String, Object> row : deviceStateRows) {
			String serial = normalizeText(toStringSafe(row.get("serial")));
			if (serial != null && serial.equalsIgnoreCase(normalizedSn)) {
				deviceSyncState = new LinkedHashMap<String, Object>(row);
				break;
			}
		}
		Msg response = DB_SYNC_STATE_FAILED.equals(detail.getSyncStatus()) ? Msg.fail() : Msg.success();
		response.add("deviceSn", normalizedSn).add("activeUsers", activeUsers).add("totalRecords", totalRecords)
				.add("queuedUsers", queuedUsers).add("queuedRecords", detail.getQueuedSetuserinfo())
				.add("queuedCount", recordsToQueue.size())
				.add("imageRecords", imageRecords).add("faceRecords", faceRecords)
				.add("passwordRecords", passwordRecords).add("cardRecords", cardRecords)
				.add("pendingBefore", detail.getPendingBefore()).add("clearedPending", detail.getClearedPending())
				.add("pendingAfter", detail.getPendingAfter())
				.add("cleanAdminQueued", detail.getQueuedCleanAdmin() > 0)
				.add("syncStatus", detail.getSyncStatus()).add("syncMessage", detail.getSyncMessage())
				.add("lastSyncAt", detail.getLastSyncAt())
				.add("syncMode", fullSync ? "FULL" : "INCREMENTAL")
				.add("resumeFromUserId", Long.valueOf(resumeFromUserId))
				.add("resumeFromRowId", Long.valueOf(resumeFromUserId))
				.add("estimatedDispatchSeconds", estimatedSeconds).add("estimatedDispatchMinutes", estimatedMinutes)
				.add("dispatchRatePerSecond", detail.getDispatchRatePerSecond())
				.add("dispatchRatePerMinute", toOneDecimal(detail.getDispatchRatePerSecond() * 60.0d))
				.add("note", fullSync ? "Selected device full DB registration sync queued."
						: "Selected device incremental registration sync queued from next record id.");
		if (deviceSyncState != null) {
			response.add("deviceSyncState", deviceSyncState);
		}
		return response;

	}

	@ResponseBody
	@RequestMapping(value = "setUsernameToDevice", method = RequestMethod.GET)
	public Msg setUsernameToDevice(@RequestParam("deviceSn") String deviceSn) {
		personService.setUsernameToDevice(deviceSn);
		return Msg.success();
	}

	@ResponseBody
	@RequestMapping(value = "/configureMasterDeviceSync", method = RequestMethod.GET)
	public Msg configureMasterDeviceSync(@RequestParam(value = "masterSn", required = false) String masterSn,
			@RequestParam(value = "enabled", required = false, defaultValue = "true") boolean enabled) {
		String normalizedMasterSn = normalizeText(masterSn);
		if (enabled && !hasText(normalizedMasterSn)) {
			return Msg.fail().add("error", "masterSn is required when enabling master sync.");
		}
		if (hasText(normalizedMasterSn) && deviceService.selectDeviceBySerialNum(normalizedMasterSn) == null) {
			return Msg.fail().add("error", "Master device serial not found in DEVICEINFO: " + normalizedMasterSn);
		}
		try {
			WSServer.configureMasterDeviceSync(normalizedMasterSn, enabled);
		} catch (IllegalArgumentException ex) {
			return Msg.fail().add("error", ex.getMessage());
		}
		Msg msg = Msg.success();
		addMapEntries(msg, WSServer.getMasterDeviceSyncStatus());
		return msg;
	}

	@ResponseBody
	@RequestMapping(value = "/masterDeviceSyncStatus", method = RequestMethod.GET)
	public Msg masterDeviceSyncStatus() {
		Msg msg = Msg.success();
		addMapEntries(msg, WSServer.getMasterDeviceSyncStatus());
		return msg;
	}

	@ResponseBody
	@RequestMapping(value = "/syncMasterDeviceFullToAll", method = RequestMethod.GET)
	public Msg syncMasterDeviceFullToAll(@RequestParam("masterSn") String masterSn,
			@RequestParam(value = "includeMaster", required = false, defaultValue = "false") boolean includeMaster) {
		String normalizedMasterSn = normalizeText(masterSn);
		if (!hasText(normalizedMasterSn)) {
			return Msg.fail().add("error", "masterSn is required.");
		}
		if (deviceService.selectDeviceBySerialNum(normalizedMasterSn) == null) {
			return Msg.fail().add("error", "Master device serial not found in DEVICEINFO: " + normalizedMasterSn);
		}

		List<Device> devices = deviceService.findAllDevice();
		if (devices == null || devices.isEmpty()) {
			return Msg.fail().add("error", "No devices available.");
		}
		int totalDevices = 0;
		int targetDevices = 0;
		for (int i = 0; i < devices.size(); i++) {
			Device device = devices.get(i);
			if (device == null || !hasText(device.getSerialNum())) {
				continue;
			}
			totalDevices++;
			String targetSn = device.getSerialNum().trim();
			if (!includeMaster && targetSn.equalsIgnoreCase(normalizedMasterSn)) {
				continue;
			}
			personService.setUserToDevice2(targetSn);
			targetDevices++;
		}
		return Msg.success().add("masterSn", normalizedMasterSn).add("includeMaster", includeMaster)
				.add("totalDevices", totalDevices).add("targetDevices", targetDevices)
				.add("note", "Queued full DB registration sync to target devices.");
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
		System.out.println("ba" + backupNum);
		EnrollInfo enrollInfo = enrollInfoService.selectByBackupnum(enrollId, backupNum);
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
	public Msg getAllLogFromDB(@RequestParam(value = "pn", defaultValue = "1") Integer pn,
			@RequestParam(value = "keyword", required = false) String keyword,
			@RequestParam(value = "fetchAll", required = false) Boolean fetchAll) {
		int pageNum = (pn == null || pn.intValue() < 1) ? 1 : pn.intValue();
		String normalizedKeyword = keyword == null ? "" : keyword.trim();
		List<Records> records;
		if (Boolean.TRUE.equals(fetchAll)) {
			if (normalizedKeyword.isEmpty()) {
				records = recordService.selectAllRecords();
			} else {
				records = recordService.selectRecordsByKeyword(normalizedKeyword);
			}
		} else {
			PageHelper.startPage(pageNum, 8);
			if (normalizedKeyword.isEmpty()) {
				records = recordService.selectAllRecords();
			} else {
				records = recordService.selectRecordsByKeyword(normalizedKeyword);
			}
		}

		PageInfo<Records> page = new PageInfo<Records>(records, 5);

		return Msg.success().add("pageInfo", page);

	}

	@RequestMapping(value = "/recordsLatest")
	@ResponseBody
	public Msg getLatestLogFromDB(@RequestParam(value = "limit", defaultValue = "30") Integer limit,
			@RequestParam(value = "deviceSn", required = false) String deviceSn,
			@RequestParam(value = "keyword", required = false) String keyword,
			@RequestParam(value = "fromTime", required = false) String fromTime,
			@RequestParam(value = "toTime", required = false) String toTime,
			@RequestParam(value = "pn", required = false) Integer pn,
			@RequestParam(value = "pageSize", required = false) Integer pageSize,
			@RequestParam(value = "applyFilter", required = false) Boolean applyFilter) {
		int safeLimit = 30;
		if (limit != null && limit.intValue() > 0) {
			safeLimit = Math.min(limit.intValue(), 200);
		}
		int safePageNum = (pn == null || pn.intValue() < 1) ? 1 : pn.intValue();
		int safePageSize = 30;
		if (pageSize != null && pageSize.intValue() > 0) {
			safePageSize = Math.min(pageSize.intValue(), 200);
		}
		boolean filterMode = Boolean.TRUE.equals(applyFilter);
		String normalizedSn = deviceSn == null ? "" : deviceSn.trim();
		String normalizedKeyword = keyword == null ? "" : keyword.trim();
		String normalizedFromTime = normalizeDateTimeForSql(fromTime);
		String normalizedToTime = normalizeDateTimeForSql(toTime);
		boolean invalidDateInput = (hasText(fromTime) && normalizedFromTime == null)
				|| (hasText(toTime) && normalizedToTime == null);
		if (invalidDateInput) {
			return Msg.fail().add("error", "Invalid datetime format. Use yyyy-MM-dd HH:mm:ss.");
		}
		if (normalizedFromTime != null && normalizedToTime != null
				&& normalizedFromTime.compareTo(normalizedToTime) > 0) {
			return Msg.fail().add("error", "From datetime must be before To datetime.");
		}

		boolean hasActiveFilters = hasActiveLatestRecordFilters(normalizedSn, normalizedKeyword, normalizedFromTime,
				normalizedToTime);
		boolean requestedFilterMode = filterMode;
		filterMode = requestedFilterMode && hasActiveFilters;

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		try {
			if (filterMode) {
				String fromClause = " FROM ATTLOG A " + "LEFT JOIN ("
						+ "SELECT ID, NAME, ROW_NUMBER() OVER (PARTITION BY ID ORDER BY BU_ID DESC) AS rn "
						+ "FROM BIO_USERMAST WHERE ISNULL(ISDELETED, 0) = 0"
						+ ") U ON U.rn = 1 AND LTRIM(RTRIM(U.ID)) = LTRIM(RTRIM(A.USER_CODE))";
				String whereClause = " WHERE 1 = 1";
				List<Object> whereParams = new ArrayList<Object>();
				if (!normalizedSn.isEmpty()) {
					whereClause += " AND LTRIM(RTRIM(ISNULL(A.SLNO, ''))) = LTRIM(RTRIM(?))";
					whereParams.add(normalizedSn);
				}
				if (!normalizedKeyword.isEmpty()) {
					if (isExactUserIdKeyword(normalizedKeyword)) {
						whereClause += " AND LTRIM(RTRIM(ISNULL(A.USER_CODE, ''))) = LTRIM(RTRIM(?))";
						whereParams.add(normalizedKeyword);
					} else {
						whereClause += " AND (CHARINDEX(?, ISNULL(A.USER_CODE, '')) > 0 OR CHARINDEX(?, ISNULL(U.NAME, '')) > 0)";
						whereParams.add(normalizedKeyword);
						whereParams.add(normalizedKeyword);
					}
				}
				if (normalizedFromTime != null) {
					whereClause += " AND A.ATT_DATETIME >= CONVERT(DATETIME, ?, 120)";
					whereParams.add(normalizedFromTime);
				}
				if (normalizedToTime != null) {
					whereClause += " AND A.ATT_DATETIME <= CONVERT(DATETIME, ?, 120)";
					whereParams.add(normalizedToTime);
				}

				int effectivePageNum = safePageNum;
				List<Map<String, Object>> rows = queryFilteredLatestRecordRows(jdbcTemplate, fromClause, whereClause,
						whereParams, effectivePageNum, safePageSize);
				int totalMatched = extractTotalMatched(rows);
				if (rows.isEmpty() && effectivePageNum > 1) {
					Integer totalMatchedObj = jdbcTemplate.queryForObject("SELECT COUNT(1) " + fromClause + whereClause,
							whereParams.toArray(), Integer.class);
					totalMatched = totalMatchedObj == null ? 0 : totalMatchedObj.intValue();
					int totalPages = totalMatched <= 0 ? 0 : ((totalMatched + safePageSize - 1) / safePageSize);
					if (totalPages > 0 && effectivePageNum > totalPages) {
						effectivePageNum = totalPages;
						rows = queryFilteredLatestRecordRows(jdbcTemplate, fromClause, whereClause, whereParams,
								effectivePageNum, safePageSize);
						int refreshedTotalMatched = extractTotalMatched(rows);
						if (refreshedTotalMatched > 0) {
							totalMatched = refreshedTotalMatched;
						}
					}
				}

				int totalPages = totalMatched <= 0 ? 0 : ((totalMatched + safePageSize - 1) / safePageSize);
				if (totalPages > 0 && effectivePageNum > totalPages) {
					effectivePageNum = totalPages;
				}
				if (effectivePageNum < 1) {
					effectivePageNum = 1;
				}

				List<Records> records = mapLatestRecordRows(rows);
				applyProfileImageFallback(records, jdbcTemplate);
				Integer latestRecordId = records.isEmpty() ? null : Integer.valueOf(records.get(0).getId());
				return Msg.success().add("records", records).add("count", Integer.valueOf(records.size()))
						.add("pn", Integer.valueOf(effectivePageNum)).add("pageSize", Integer.valueOf(safePageSize))
						.add("totalMatched", Integer.valueOf(totalMatched)).add("totalPages", Integer.valueOf(totalPages))
						.add("applyFilter", Boolean.TRUE).add("limit", Integer.valueOf(safeLimit))
						.add("latestRecordId", latestRecordId).add("deviceSn", normalizedSn).add("keyword", normalizedKeyword)
						.add("fromTime", normalizedFromTime).add("toTime", normalizedToTime);
			}

			List<Map<String, Object>> rows;
			if (normalizedKeyword.isEmpty()) {
				String whereClause = " WHERE 1 = 1";
				List<Object> whereParams = new ArrayList<Object>();
				if (!normalizedSn.isEmpty()) {
					whereClause += " AND LTRIM(RTRIM(ISNULL(A.SLNO, ''))) = LTRIM(RTRIM(?))";
					whereParams.add(normalizedSn);
				}
				if (normalizedFromTime != null) {
					whereClause += " AND A.ATT_DATETIME >= CONVERT(DATETIME, ?, 120)";
					whereParams.add(normalizedFromTime);
				}
				if (normalizedToTime != null) {
					whereClause += " AND A.ATT_DATETIME <= CONVERT(DATETIME, ?, 120)";
					whereParams.add(normalizedToTime);
				}

				// Fast path: fetch only latest rows first.
				String dataSql = "WITH LatestLogs AS (" + "SELECT TOP (?) "
						+ "A.ATT_ID, A.USER_CODE, LTRIM(RTRIM(ISNULL(A.USER_CODE, ''))) AS USER_CODE_TRIM, "
						+ "A.ATT_DATETIME, A.COL2, A.INOUT_ID, A.COL3, A.SLNO, A.COL5, A.COL6 "
						+ "FROM ATTLOG A " + whereClause + " ORDER BY A.ATT_DATETIME DESC, A.ATT_ID DESC" + ") " + "SELECT "
						+ "CONVERT(INT, L.ATT_ID) AS id, "
						+ "ISNULL(L.USER_CODE, '') AS userCodeRaw, "
						+ "ISNULL(L.USER_CODE_TRIM, '') AS userCodeTrim, "
						+ "CASE WHEN ISNUMERIC(L.USER_CODE) = 1 THEN CONVERT(BIGINT, L.USER_CODE) ELSE NULL END AS enrollId, "
						+ "CONVERT(VARCHAR(19), L.ATT_DATETIME, 120) AS recordsTime, "
						+ "ISNULL(CASE WHEN ISNUMERIC(L.COL2) = 1 THEN CONVERT(INT, L.COL2) ELSE NULL END, 0) AS mode, "
						+ "ISNULL(CASE WHEN ISNUMERIC(L.INOUT_ID) = 1 THEN CONVERT(INT, L.INOUT_ID) ELSE NULL END, 0) AS intout, "
						+ "ISNULL(CASE WHEN ISNUMERIC(L.COL3) = 1 THEN CONVERT(INT, L.COL3) ELSE NULL END, 0) AS event, "
						+ "ISNULL(L.SLNO, '') AS deviceSerialNum, "
						+ "ISNULL(NULLIF(LTRIM(RTRIM((SELECT TOP 1 n.NET_AREA FROM NetWork n WHERE LTRIM(RTRIM(n.SLNO)) = LTRIM(RTRIM(L.SLNO)) ORDER BY n.ID DESC))), ''), 'Unmapped') AS locationName, "
						+ "ISNULL(CASE WHEN ISNUMERIC(L.COL5) = 1 THEN CONVERT(FLOAT, L.COL5) ELSE NULL END, 0) AS temperature, "
						+ "ISNULL(L.COL6, '') AS image " + "FROM LatestLogs L "
						+ "ORDER BY L.ATT_DATETIME DESC, L.ATT_ID DESC";
				List<Object> dataParams = new ArrayList<Object>();
				dataParams.add(Integer.valueOf(safeLimit));
				dataParams.addAll(whereParams);
				rows = jdbcTemplate.queryForList(dataSql, dataParams.toArray());
				Set<String> userIds = new LinkedHashSet<String>();
				for (int i = 0; i < rows.size(); i++) {
					Map<String, Object> row = rows.get(i);
					String rawCode = normalizeText(
							row.get("userCodeRaw") == null ? "" : String.valueOf(row.get("userCodeRaw")));
					String trimmedCode = normalizeText(
							row.get("userCodeTrim") == null ? "" : String.valueOf(row.get("userCodeTrim")));
					if (hasText(rawCode)) {
						userIds.add(rawCode);
					}
					if (hasText(trimmedCode)) {
						userIds.add(trimmedCode);
					}
				}
				Map<String, String> userNameById = loadLatestUserNamesById(jdbcTemplate, userIds);
				List<Records> records = mapLatestRecordRows(rows, userNameById);
				applyProfileImageFallback(records, jdbcTemplate);
				Integer latestRecordId = records.isEmpty() ? null : Integer.valueOf(records.get(0).getId());
				return Msg.success().add("records", records).add("count", Integer.valueOf(records.size()))
						.add("limit", Integer.valueOf(safeLimit)).add("latestRecordId", latestRecordId)
						.add("deviceSn", normalizedSn).add("keyword", normalizedKeyword).add("fromTime", normalizedFromTime)
						.add("toTime", normalizedToTime);
			}

			// Keyword path (User ID/Name search) keeps exact matching behavior.
			String fromClause = " FROM ATTLOG A " + "LEFT JOIN ("
					+ "SELECT ID, NAME, ROW_NUMBER() OVER (PARTITION BY ID ORDER BY BU_ID DESC) AS rn "
					+ "FROM BIO_USERMAST WHERE ISNULL(ISDELETED, 0) = 0"
					+ ") U ON U.rn = 1 AND LTRIM(RTRIM(U.ID)) = LTRIM(RTRIM(A.USER_CODE))";
			String whereClause = " WHERE 1 = 1";
			List<Object> whereParams = new ArrayList<Object>();
			if (!normalizedSn.isEmpty()) {
				whereClause += " AND LTRIM(RTRIM(ISNULL(A.SLNO, ''))) = LTRIM(RTRIM(?))";
				whereParams.add(normalizedSn);
			}
			if (isExactUserIdKeyword(normalizedKeyword)) {
				whereClause += " AND LTRIM(RTRIM(ISNULL(A.USER_CODE, ''))) = LTRIM(RTRIM(?))";
				whereParams.add(normalizedKeyword);
			} else {
				whereClause += " AND (CHARINDEX(?, ISNULL(A.USER_CODE, '')) > 0 OR CHARINDEX(?, ISNULL(U.NAME, '')) > 0)";
				whereParams.add(normalizedKeyword);
				whereParams.add(normalizedKeyword);
			}
			if (normalizedFromTime != null) {
				whereClause += " AND A.ATT_DATETIME >= CONVERT(DATETIME, ?, 120)";
				whereParams.add(normalizedFromTime);
			}
			if (normalizedToTime != null) {
				whereClause += " AND A.ATT_DATETIME <= CONVERT(DATETIME, ?, 120)";
				whereParams.add(normalizedToTime);
			}
			String dataSql = "SELECT " + "CONVERT(INT, A.ATT_ID) AS id, "
					+ "CASE WHEN ISNUMERIC(A.USER_CODE) = 1 THEN CONVERT(BIGINT, A.USER_CODE) ELSE NULL END AS enrollId, "
					+ "CONVERT(VARCHAR(19), A.ATT_DATETIME, 120) AS recordsTime, "
					+ "ISNULL(CASE WHEN ISNUMERIC(A.COL2) = 1 THEN CONVERT(INT, A.COL2) ELSE NULL END, 0) AS mode, "
					+ "ISNULL(CASE WHEN ISNUMERIC(A.INOUT_ID) = 1 THEN CONVERT(INT, A.INOUT_ID) ELSE NULL END, 0) AS intout, "
					+ "ISNULL(CASE WHEN ISNUMERIC(A.COL3) = 1 THEN CONVERT(INT, A.COL3) ELSE NULL END, 0) AS event, "
					+ "ISNULL(U.NAME, '') AS userName, " + "ISNULL(A.SLNO, '') AS deviceSerialNum, "
					+ "ISNULL(NULLIF(LTRIM(RTRIM((SELECT TOP 1 n.NET_AREA FROM NetWork n WHERE LTRIM(RTRIM(n.SLNO)) = LTRIM(RTRIM(A.SLNO)) ORDER BY n.ID DESC))), ''), 'Unmapped') AS locationName, "
					+ "ISNULL(CASE WHEN ISNUMERIC(A.COL5) = 1 THEN CONVERT(FLOAT, A.COL5) ELSE NULL END, 0) AS temperature, "
					+ "ISNULL(A.COL6, '') AS image " + fromClause + whereClause
					+ " ORDER BY A.ATT_DATETIME DESC, A.ATT_ID DESC OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";
			List<Object> dataParams = new ArrayList<Object>(whereParams);
			dataParams.add(Integer.valueOf(safeLimit));
			rows = jdbcTemplate.queryForList(dataSql, dataParams.toArray());
			List<Records> records = mapLatestRecordRows(rows);
			applyProfileImageFallback(records, jdbcTemplate);
			Integer latestRecordId = records.isEmpty() ? null : Integer.valueOf(records.get(0).getId());
			return Msg.success().add("records", records).add("count", Integer.valueOf(records.size()))
					.add("limit", Integer.valueOf(safeLimit)).add("applyFilter", Boolean.FALSE)
					.add("pn", Integer.valueOf(1)).add("pageSize", Integer.valueOf(safeLimit))
					.add("totalPages", Integer.valueOf(1)).add("totalMatched", Integer.valueOf(records.size()))
					.add("latestRecordId", latestRecordId).add("deviceSn", normalizedSn).add("keyword", normalizedKeyword)
					.add("fromTime", normalizedFromTime).add("toTime", normalizedToTime);
		} catch (DataAccessException ex) {
			log.error(
					"[RECORDS-LATEST] Failed loading latest records. requestedFilterMode={} effectiveFilterMode={} deviceSn={} keyword={} fromTime={} toTime={} pn={} pageSize={} limit={}",
					Boolean.valueOf(requestedFilterMode), Boolean.valueOf(filterMode), normalizedSn, normalizedKeyword,
					normalizedFromTime, normalizedToTime, Integer.valueOf(safePageNum), Integer.valueOf(safePageSize),
					Integer.valueOf(safeLimit), ex);
			String errorMessage = isDatabaseBusy(ex) ? "Database is busy. Please retry in a few seconds."
					: "Unable to load latest records right now.";
			return Msg.fail().add("error", errorMessage).add("applyFilter", Boolean.valueOf(filterMode))
					.add("deviceSn", normalizedSn).add("keyword", normalizedKeyword).add("fromTime", normalizedFromTime)
					.add("toTime", normalizedToTime);
		}
	}

	private boolean hasActiveLatestRecordFilters(String normalizedSn, String normalizedKeyword, String normalizedFromTime,
			String normalizedToTime) {
		return hasText(normalizedSn) || hasText(normalizedKeyword) || normalizedFromTime != null
				|| normalizedToTime != null;
	}

	private boolean isExactUserIdKeyword(String keyword) {
		return hasText(keyword) && keyword.matches("\\d+");
	}

	private List<Map<String, Object>> queryFilteredLatestRecordRows(JdbcTemplate jdbcTemplate, String fromClause,
			String whereClause, List<Object> whereParams, int pageNum, int pageSize) {
		int effectivePageNum = pageNum < 1 ? 1 : pageNum;
		int offset = (effectivePageNum - 1) * pageSize;
		String dataSql = "SELECT * FROM (" + "SELECT " + "CONVERT(INT, A.ATT_ID) AS id, "
				+ "CASE WHEN ISNUMERIC(A.USER_CODE) = 1 THEN CONVERT(BIGINT, A.USER_CODE) ELSE NULL END AS enrollId, "
				+ "CONVERT(VARCHAR(19), A.ATT_DATETIME, 120) AS recordsTime, "
				+ "ISNULL(CASE WHEN ISNUMERIC(A.COL2) = 1 THEN CONVERT(INT, A.COL2) ELSE NULL END, 0) AS mode, "
				+ "ISNULL(CASE WHEN ISNUMERIC(A.INOUT_ID) = 1 THEN CONVERT(INT, A.INOUT_ID) ELSE NULL END, 0) AS intout, "
				+ "ISNULL(CASE WHEN ISNUMERIC(A.COL3) = 1 THEN CONVERT(INT, A.COL3) ELSE NULL END, 0) AS event, "
				+ "ISNULL(U.NAME, '') AS userName, " + "ISNULL(A.SLNO, '') AS deviceSerialNum, "
				+ "ISNULL(NULLIF(LTRIM(RTRIM((SELECT TOP 1 n.NET_AREA FROM NetWork n WHERE LTRIM(RTRIM(n.SLNO)) = LTRIM(RTRIM(A.SLNO)) ORDER BY n.ID DESC))), ''), 'Unmapped') AS locationName, "
				+ "ISNULL(CASE WHEN ISNUMERIC(A.COL5) = 1 THEN CONVERT(FLOAT, A.COL5) ELSE NULL END, 0) AS temperature, "
				+ "ISNULL(A.COL6, '') AS image, " + "COUNT(1) OVER() AS totalMatched " + fromClause + whereClause + ") X "
				+ "ORDER BY X.recordsTime DESC, X.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
		List<Object> dataParams = new ArrayList<Object>(whereParams);
		dataParams.add(Integer.valueOf(offset));
		dataParams.add(Integer.valueOf(pageSize));
		return jdbcTemplate.queryForList(dataSql, dataParams.toArray());
	}

	private int extractTotalMatched(List<Map<String, Object>> rows) {
		if (rows == null || rows.isEmpty()) {
			return 0;
		}
		Map<String, Object> row = rows.get(0);
		if (row == null || row.isEmpty()) {
			return 0;
		}
		Object value = row.containsKey("totalMatched") ? row.get("totalMatched") : row.get("TOTALMATCHED");
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		if (value == null) {
			return 0;
		}
		try {
			return Integer.parseInt(String.valueOf(value));
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	private boolean isDatabaseBusy(Throwable ex) {
		Throwable cursor = ex;
		while (cursor != null) {
			String message = cursor.getMessage();
			if (message != null) {
				String lowerMessage = message.toLowerCase(Locale.ENGLISH);
				if (lowerMessage.contains("timeout waiting for idle object")
						|| lowerMessage.contains("cannot get jdbc connection")
						|| lowerMessage.contains("cannot get a connection, pool error")
						|| lowerMessage.contains("unable to acquire jdbc connection")
						|| lowerMessage.contains("borrow object failed")) {
					return true;
				}
			}
			cursor = cursor.getCause();
		}
		return false;
	}

	private List<Records> mapLatestRecordRows(List<Map<String, Object>> rows) {
		return mapLatestRecordRows(rows, null);
	}

	private List<Records> mapLatestRecordRows(List<Map<String, Object>> rows, Map<String, String> userNameById) {
		List<Records> records = new ArrayList<Records>();
		if (rows == null || rows.isEmpty()) {
			return records;
		}
		records = new ArrayList<Records>(rows.size());
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			Records record = new Records();
			Object idObj = row.get("id");
			record.setId(idObj instanceof Number ? ((Number) idObj).intValue() : 0);
			Object enrollIdObj = row.get("enrollId");
			record.setEnrollId(enrollIdObj instanceof Number ? Long.valueOf(((Number) enrollIdObj).longValue()) : null);
			record.setRecordsTime(row.get("recordsTime") == null ? "" : String.valueOf(row.get("recordsTime")));
			Object modeObj = row.get("mode");
			record.setMode(modeObj instanceof Number ? ((Number) modeObj).intValue() : 0);
			Object inoutObj = row.get("intout");
			record.setIntout(inoutObj instanceof Number ? ((Number) inoutObj).intValue() : 0);
			Object eventObj = row.get("event");
			record.setEvent(eventObj instanceof Number ? ((Number) eventObj).intValue() : 0);
			String userName = row.get("userName") == null ? "" : String.valueOf(row.get("userName"));
			if (!hasText(userName) && userNameById != null && !userNameById.isEmpty()) {
				String rawCode = normalizeText(row.get("userCodeRaw") == null ? "" : String.valueOf(row.get("userCodeRaw")));
				String trimmedCode = normalizeText(
						row.get("userCodeTrim") == null ? "" : String.valueOf(row.get("userCodeTrim")));
				if (hasText(trimmedCode)) {
					userName = userNameById.get(trimmedCode);
				}
				if (!hasText(userName) && hasText(rawCode)) {
					userName = userNameById.get(rawCode);
				}
			}
			record.setUserName(hasText(userName) ? userName : "");
			record.setDeviceSerialNum(row.get("deviceSerialNum") == null ? "" : String.valueOf(row.get("deviceSerialNum")));
			record.setLocationName(row.get("locationName") == null ? "" : String.valueOf(row.get("locationName")));
			Object tempObj = row.get("temperature");
			record.setTemperature(tempObj instanceof Number ? ((Number) tempObj).doubleValue() : 0.0d);
			record.setImage(row.get("image") == null ? "" : String.valueOf(row.get("image")));
			record.setImageBase64("");
			records.add(record);
		}
		return records;
	}

	private Map<String, String> loadLatestUserNamesById(JdbcTemplate jdbcTemplate, Set<String> userIds) {
		Map<String, String> userNameById = new LinkedHashMap<String, String>();
		if (jdbcTemplate == null || userIds == null || userIds.isEmpty()) {
			return userNameById;
		}
		List<String> normalizedIds = new ArrayList<String>(userIds.size());
		for (String userId : userIds) {
			String normalized = normalizeText(userId);
			if (hasText(normalized)) {
				normalizedIds.add(normalized);
			}
		}
		if (normalizedIds.isEmpty()) {
			return userNameById;
		}
		StringBuilder placeholders = new StringBuilder();
		for (int i = 0; i < normalizedIds.size(); i++) {
			if (i > 0) {
				placeholders.append(",");
			}
			placeholders.append("?");
		}
		String sql = "SELECT X.ID, X.NAME FROM (" + "SELECT LTRIM(RTRIM(ID)) AS ID, NAME, "
				+ "ROW_NUMBER() OVER (PARTITION BY LTRIM(RTRIM(ID)) ORDER BY BU_ID DESC) AS rn "
				+ "FROM BIO_USERMAST WHERE ISNULL(ISDELETED, 0) = 0 "
				+ "AND LTRIM(RTRIM(ID)) IN (" + placeholders + ")" + ") X WHERE X.rn = 1";
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, normalizedIds.toArray());
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			String id = normalizeText(row.get("ID") == null ? "" : String.valueOf(row.get("ID")));
			String name = row.get("NAME") == null ? "" : String.valueOf(row.get("NAME"));
			if (hasText(id)) {
				userNameById.put(id, hasText(name) ? name : "");
			}
		}
		return userNameById;
	}

	private void applyProfileImageFallback(List<Records> records, JdbcTemplate jdbcTemplate) {
		if (records == null || records.isEmpty() || jdbcTemplate == null) {
			return;
		}
		Set<Long> enrollIds = new LinkedHashSet<Long>();
		for (int i = 0; i < records.size(); i++) {
			Records record = records.get(i);
			if (record == null) {
				continue;
			}
			if (hasText(record.getImage())) {
				continue;
			}
			Long enrollId = record.getEnrollId();
			if (enrollId != null && enrollId.longValue() > 0L) {
				enrollIds.add(enrollId);
			}
		}
		if (enrollIds.isEmpty()) {
			return;
		}
		Map<Long, ProfileImagePayload> imagePayloadByEnrollId = loadProfileImagePayloadByEnrollId(jdbcTemplate, enrollIds);
		if (imagePayloadByEnrollId == null || imagePayloadByEnrollId.isEmpty()) {
			return;
		}
		for (int i = 0; i < records.size(); i++) {
			Records record = records.get(i);
			if (record == null || record.getEnrollId() == null) {
				continue;
			}
			ProfileImagePayload payload = imagePayloadByEnrollId.get(record.getEnrollId());
			if (payload == null) {
				continue;
			}
			if (!hasText(record.getImage()) && hasText(payload.fileName)) {
				record.setImage(payload.fileName);
			}
			if (hasText(payload.base64Data)) {
				record.setImageBase64(payload.base64Data);
			}
		}
	}

	private static final class ProfileImagePayload {
		private String fileName;
		private String base64Data;
	}

	private Map<Long, ProfileImagePayload> loadProfileImagePayloadByEnrollId(JdbcTemplate jdbcTemplate, Set<Long> enrollIds) {
		Map<Long, ProfileImagePayload> payloadByEnrollId = new LinkedHashMap<Long, ProfileImagePayload>();
		if (jdbcTemplate == null || enrollIds == null || enrollIds.isEmpty()) {
			return payloadByEnrollId;
		}
		List<String> idTokens = new ArrayList<String>(enrollIds.size());
		for (Long enrollId : enrollIds) {
			if (enrollId == null || enrollId.longValue() <= 0L) {
				continue;
			}
			idTokens.add(String.valueOf(enrollId.longValue()));
		}
		if (idTokens.isEmpty()) {
			return payloadByEnrollId;
		}

		StringBuilder placeholders = new StringBuilder();
		for (int i = 0; i < idTokens.size(); i++) {
			if (i > 0) {
				placeholders.append(",");
			}
			placeholders.append("?");
		}
		String sql = "SELECT X.ID, CAST(X.IMAGE AS NVARCHAR(MAX)) AS IMAGE_DATA FROM (" + "SELECT "
				+ "LTRIM(RTRIM(ID)) AS ID, IMAGE, "
				+ "ROW_NUMBER() OVER (PARTITION BY LTRIM(RTRIM(ID)) ORDER BY "
				+ "CASE WHEN UPD_DATE IS NULL THEN 1 ELSE 0 END, UPD_DATE DESC, "
				+ "CASE WHEN BP_DATE IS NULL THEN 1 ELSE 0 END, BP_DATE DESC) AS rn "
				+ "FROM BIO_PICDATA WHERE LTRIM(RTRIM(ID)) IN (" + placeholders + ")" + ") X WHERE X.rn = 1";
		List<Map<String, Object>> rows;
		try {
			rows = jdbcTemplate.queryForList(sql, idTokens.toArray());
		} catch (Exception ex) {
			log.debug("[LOG-IMAGE] Unable to load profile image fallback from BIO_PICDATA: {}", ex.getMessage());
			return payloadByEnrollId;
		}
		for (int i = 0; i < rows.size(); i++) {
			Map<String, Object> row = rows.get(i);
			String idText = normalizeText(row.get("ID") == null ? "" : String.valueOf(row.get("ID")));
			if (!hasText(idText)) {
				continue;
			}
			Long enrollId;
			try {
				enrollId = Long.valueOf(idText);
			} catch (NumberFormatException ex) {
				continue;
			}
			String imageBase64Raw = row.get("IMAGE_DATA") == null ? "" : String.valueOf(row.get("IMAGE_DATA"));
			String imageBase64 = normalizeBase64ImageData(imageBase64Raw);
			if (!hasText(imageBase64)) {
				continue;
			}
			ProfileImagePayload payload = new ProfileImagePayload();
			payload.base64Data = imageBase64;
			payload.fileName = ensureProfileImageFile(enrollId, imageBase64);
			if (hasText(payload.fileName) || hasText(payload.base64Data)) {
				payloadByEnrollId.put(enrollId, payload);
			}
		}
		return payloadByEnrollId;
	}

	private String ensureProfileImageFile(Long enrollId, String imageBase64) {
		return "";
	}

	private String normalizeBase64ImageData(String rawBase64) {
		if (!hasText(rawBase64)) {
			return "";
		}
		String normalized = rawBase64.trim();
		if (normalized.startsWith("data:")) {
			int commaIndex = normalized.indexOf(',');
			if (commaIndex >= 0 && commaIndex + 1 < normalized.length()) {
				normalized = normalized.substring(commaIndex + 1);
			}
		}
		normalized = normalized.replaceAll("\\s+", "");
		return hasText(normalized) ? normalized : "";
	}

	private String normalizeDateTimeForSql(String value) {
		String normalized = normalizeText(value);
		if (normalized == null) {
			return null;
		}
		normalized = normalized.replace('T', ' ');
		if (normalized.length() == 16) {
			normalized = normalized + ":00";
		}
		if (normalized.length() != 19) {
			return null;
		}
		try {
			Timestamp.valueOf(normalized);
			return normalized;
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private void ensureSchedulerAuditTable(JdbcTemplate jdbcTemplate) {
		String ddl = "IF OBJECT_ID('dbo." + VERIFY_SCHEDULER_AUDIT_TABLE + "', 'U') IS NULL "
				+ "BEGIN "
				+ "CREATE TABLE dbo." + VERIFY_SCHEDULER_AUDIT_TABLE + " ("
				+ "ID BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY, "
				+ "ACTIVE_USER INT NOT NULL DEFAULT 0, "
				+ "DISABLE_USER INT NOT NULL DEFAULT 0, "
				+ "DELETED_USER INT NOT NULL DEFAULT 0, "
				+ "STATUS VARCHAR(20) NOT NULL, "
				+ "STARTED_AT DATETIME2(0) NOT NULL, "
				+ "FINISHED_AT DATETIME2(0) NULL "
				+ "); "
				+ "END; "
				+ "IF COL_LENGTH('dbo." + VERIFY_SCHEDULER_AUDIT_TABLE + "', 'DELETED_USER') IS NULL "
				+ "BEGIN "
				+ "ALTER TABLE dbo." + VERIFY_SCHEDULER_AUDIT_TABLE + " ADD DELETED_USER INT NOT NULL CONSTRAINT DF_"
				+ VERIFY_SCHEDULER_AUDIT_TABLE + "_DELETED_USER DEFAULT 0 WITH VALUES; "
				+ "END; "
				+ "IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_" + VERIFY_SCHEDULER_AUDIT_TABLE
				+ "_STARTED_AT' AND object_id = OBJECT_ID('dbo." + VERIFY_SCHEDULER_AUDIT_TABLE + "')) "
				+ "BEGIN "
				+ "CREATE INDEX IX_" + VERIFY_SCHEDULER_AUDIT_TABLE + "_STARTED_AT ON dbo."
				+ VERIFY_SCHEDULER_AUDIT_TABLE + " (STARTED_AT DESC); "
				+ "END;";
		jdbcTemplate.execute(ddl);
	}

	@RequestMapping(value = "/climsRecords")
	@ResponseBody
	public Msg getAllClimsLogFromDB(@RequestParam(value = "pn", defaultValue = "1") Integer pn,
			@RequestParam(value = "deviceSn", required = false) String deviceSn,
			@RequestParam(value = "keyword", required = false) String keyword,
			@RequestParam(value = "fetchAll", required = false) Boolean fetchAll) {
		int pageSize = 8;
		int pageNum = (pn == null || pn.intValue() < 1) ? 1 : pn.intValue();
		int offset = (pageNum - 1) * pageSize;
		String normalizedSn = deviceSn == null ? "" : deviceSn.trim();
		String normalizedKeyword = keyword == null ? "" : keyword.trim();
		boolean shouldFetchAll = Boolean.TRUE.equals(fetchAll);

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		String fromClause = " FROM CLIMSVIEW C " + "LEFT JOIN ("
				+ "SELECT ID, NAME, ROW_NUMBER() OVER (PARTITION BY ID ORDER BY BU_ID DESC) AS rn "
				+ "FROM BIO_USERMAST WHERE ISNULL(ISDELETED, 0) = 0"
				+ ") U ON U.rn = 1 AND LTRIM(RTRIM(U.ID)) = LTRIM(RTRIM(CONVERT(VARCHAR(50), C.USERID)))";
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
				whereClause = " WHERE C.DEVICEID = ?";
				whereParams.add(mappedDeviceId);
				deviceFilterApplied = true;
			} else {
				// Do not return mixed all-device data when a specific serial was requested.
				mappingMissing = true;
				whereClause = " WHERE 1 = 0";
			}
		}
		if (!normalizedKeyword.isEmpty()) {
			if (whereClause.isEmpty()) {
				whereClause = " WHERE ";
			} else {
				whereClause += " AND ";
			}
			whereClause += "(CHARINDEX(?, ISNULL(CONVERT(VARCHAR(50), C.USERID), '')) > 0 "
					+ "OR CHARINDEX(?, ISNULL(U.NAME, '')) > 0)";
			whereParams.add(normalizedKeyword);
			whereParams.add(normalizedKeyword);
		}
		String countSql = "SELECT COUNT(1)" + fromClause + whereClause;
		Integer totalCount;
		if (whereParams.isEmpty()) {
			totalCount = jdbcTemplate.queryForObject(countSql, Integer.class);
		} else {
			totalCount = jdbcTemplate.queryForObject(countSql, whereParams.toArray(), Integer.class);
		}
		if (totalCount == null) {
			totalCount = Integer.valueOf(0);
		}
		String dataSql = "SELECT " + "CONVERT(BIGINT, C.DEVICELOGID) AS deviceLogId, "
				+ "CONVERT(VARCHAR(19), C.DOWNLOADDATE, 120) AS downloadDate, " + "C.PROJECTID AS projectId, "
				+ "CASE WHEN C.USERID IS NULL THEN NULL ELSE CONVERT(BIGINT, C.USERID) END AS userId, "
				+ "ISNULL(U.NAME, '') AS userName, " + "CONVERT(VARCHAR(19), C.LOGDATE, 120) AS logDate, "
				+ "C.DIRECTION AS direction, "
				+ "CASE WHEN C.DEVICEID IS NULL THEN NULL ELSE CONVERT(BIGINT, C.DEVICEID) END AS deviceId, "
				+ "ISNULL((SELECT TOP 1 LTRIM(RTRIM(SLNO)) FROM NetWork WHERE ID = C.DEVICEID), '') AS deviceSerialNum "
				+ fromClause + whereClause + " ORDER BY C.LOGDATE DESC, C.DOWNLOADDATE DESC, C.DEVICELOGID DESC ";
		List<Object> dataParams = new ArrayList<Object>(whereParams);
		if (!shouldFetchAll) {
			dataSql += " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
			dataParams.add(Integer.valueOf(offset));
			dataParams.add(Integer.valueOf(pageSize));
		}
		List<Map<String, Object>> records = jdbcTemplate.queryForList(dataSql, dataParams.toArray());

		PageInfo page;
		if (shouldFetchAll) {
			page = new PageInfo(records, 5);
		} else {
			Page<Map<String, Object>> pageRows = new Page<Map<String, Object>>(pageNum, pageSize);
			pageRows.setTotal(totalCount.longValue());
			pageRows.addAll(records);
			page = new PageInfo(pageRows, 5);
		}
		return Msg.success().add("pageInfo", page).add("source", "IDSL_NTPC_CLIMS.dbo.CLIMSVIEW")
				.add("deviceSn", normalizedSn).add("deviceMappedId", mappedDeviceId)
				.add("keyword", normalizedKeyword)
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
		queueCleanAdminCommand(deviceSn);
		return Msg.success();

	}

	private void queueCleanAdminCommand(String deviceSn) {
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
	}

	private MachineCommand buildSetQuestionnaireCommand(String deviceSn, String mode) {
		String normalizedSn = normalizeText(deviceSn);
		String normalizedMode = normalizeInOutMode(mode);
		if (!hasText(normalizedSn) || normalizedMode == null) {
			return null;
		}
		String message = buildSetQuestionnairePayload(normalizedMode);
		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setContent(message);
		machineCommand.setName("setquestionnaire");
		machineCommand.setStatus(0);
		machineCommand.setSendStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setSerial(normalizedSn);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());
		return machineCommand;
	}

	private String buildSetQuestionnairePayload(String mode) {
		String normalizedMode = normalizeInOutMode(mode);
		if (normalizedMode == null) {
			normalizedMode = "AUTO";
		}
		// Device-side in/out popup must stay disabled. We infer IN/OUT on server side
		// from selected device gate mode (AUTO/IN/OUT) while processing incoming logs.
		return "{\"cmd\":\"setquestionnaire\""
				+ ",\"title\":\"inout event\""
				+ ",\"voice\":\"please select\""
				+ ",\"errmsg\":\"please select\""
				+ ",\"radio\":true"
				+ ",\"optionflag\":0"
				+ ",\"usequestion\":false"
				+ ",\"useschedule\":false"
				+ ",\"card\":0"
				+ ",\"items\":[\"in\",\"out\"]"
				+ ",\"schedules\":["
				+ "\"00:00-00:00*0\","
				+ "\"00:00-00:00*0\","
				+ "\"00:00-00:00*0\","
				+ "\"00:00-00:00*0\","
				+ "\"00:00-00:00*0\","
				+ "\"00:00-00:00*0\","
				+ "\"00:00-00:00*0\","
				+ "\"00:00-00:00*0\""
				+ "]"
				+ "}";
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
	@RequestMapping(value = "/setUserToAllDevice", method = RequestMethod.GET)
	public Msg setUserToAllDevice() {
		if (!triggerDatabaseSyncAsync("manual-setUserToAllDevice")) {
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
	@RequestMapping(value = "/setUserToAllDeviceStatus", method = RequestMethod.GET)
	public Msg setUserToAllDeviceStatus() {
		return getDatabaseSyncStatusMsg();
	}

	@ResponseBody
	@RequestMapping(value = "/syncUsernameDeltaAllDevices", method = RequestMethod.GET)
	public Msg syncUsernameDeltaAllDevices() {
		if (!triggerUsernameDeltaSyncAsync("manual")) {
			return getUsernameDeltaSyncStatusMsg().add("accepted", false);
		}
		return getUsernameDeltaSyncStatusMsg().add("accepted", true);
	}

	@ResponseBody
	@RequestMapping(value = "/syncUsernameDeltaAllDevicesStatus", method = RequestMethod.GET)
	public Msg syncUsernameDeltaAllDevicesStatus() {
		return getUsernameDeltaSyncStatusMsg();
	}

	@ResponseBody
	@RequestMapping(value = "/syncUsersStatusAllDevices", method = RequestMethod.GET)
	public Msg syncUsersStatusAllDevices() {
		List<String> serials = getAllKnownDeviceSerials();
		if (serials.isEmpty()) {
			return Msg.fail().add("error", "No devices found in database.");
		}
		List<Person> persons = personService.selectAll();
		if (persons == null || persons.isEmpty()) {
			return Msg.success().add("devices", serials.size()).add("users", 0).add("inactiveUsers", 0)
					.add("commandsQueued", 0);
		}
		int inactiveUsers = 0;
		for (Person person : persons) {
			if (person != null && person.getId() != null && normalizeEnableStatus(person.getStatus()) == 0) {
				inactiveUsers++;
			}
		}
		int queued = queueUsersStatusSyncOnAllDevices(persons, serials);
		return Msg.success().add("devices", serials.size()).add("users", persons.size()).add("inactiveUsers", inactiveUsers)
				.add("activeUsers", persons.size() - inactiveUsers).add("commandsQueued", queued);
	}

	@ResponseBody
	@RequestMapping(value = "/syncUsersStatusByUpdDateAllDevices", method = RequestMethod.GET)
	public Msg syncUsersStatusByUpdDateAllDevices(@RequestParam("syncDate") String syncDate) {
		if (!hasText(syncDate)) {
			return Msg.fail().add("error", "syncDate is required.");
		}
		DatabaseUserDeltaSyncService.SyncResult result = databaseUserDeltaSyncService
				.directSendUsersUpdatedByUpdDate("manual-upd-date-page", syncDate.trim());
		long plannedForOnlineDevices = (long) result.getChangedStatusUsers() * (long) result.getOnlineDevices();
		int offlineDevices = Math.max(0, result.getDevices() - result.getOnlineDevices());
		if (!result.isSuccess()) {
			String error = hasText(result.getError()) ? result.getError() : result.getReason();
			return Msg.fail().add("error", error).add("syncDate", syncDate.trim()).add("devices", result.getDevices())
					.add("onlineDevices", result.getOnlineDevices()).add("users", result.getChangedStatusUsers())
					.add("enabledUsers", result.getEnabledUsers()).add("disabledUsers", result.getDisabledUsers())
					.add("offlineDevices", offlineDevices).add("sendMode", "direct-online")
					.add("commandsSent", result.getTotalCommandsQueued())
					.add("enableCommandsSent", result.getEnableCommandsQueued())
					.add("disableCommandsSent", result.getDisableCommandsQueued())
					.add("commandsPlannedForOnlineDevices", plannedForOnlineDevices)
					.add("commandsQueued", result.getTotalCommandsQueued())
					.add("estimatedDeviceDispatchSeconds", result.getEstimatedDeviceDispatchSeconds())
					.add("reason", result.getReason());
		}
		return Msg.success().add("syncDate", syncDate.trim()).add("devices", result.getDevices())
				.add("onlineDevices", result.getOnlineDevices()).add("users", result.getChangedStatusUsers())
				.add("enabledUsers", result.getEnabledUsers()).add("disabledUsers", result.getDisabledUsers())
				.add("offlineDevices", offlineDevices).add("sendMode", "direct-online")
				.add("commandsSent", result.getTotalCommandsQueued())
				.add("enableCommandsSent", result.getEnableCommandsQueued())
				.add("disableCommandsSent", result.getDisableCommandsQueued())
				.add("commandsPlannedForOnlineDevices", plannedForOnlineDevices)
				.add("commandsQueued", result.getTotalCommandsQueued())
				.add("estimatedDeviceDispatchSeconds", result.getEstimatedDeviceDispatchSeconds())
				.add("reason", result.getReason());
	}

	@ResponseBody
	@RequestMapping(value = "/syncUsersDeleteByDelDateAllDevices", method = RequestMethod.GET)
	public Msg syncUsersDeleteByDelDateAllDevices(@RequestParam("syncDate") String syncDate) {
		if (!hasText(syncDate)) {
			return Msg.fail().add("error", "syncDate is required.");
		}
		DatabaseUserDeltaSyncService.SyncResult result = databaseUserDeltaSyncService
				.directSendUsersDeletedByDelDate("manual-del-date-page", syncDate.trim());
		long plannedForOnlineDevices = (long) result.getChangedDeletedUsers() * (long) result.getOnlineDevices();
		int offlineDevices = Math.max(0, result.getDevices() - result.getOnlineDevices());
		if (!result.isSuccess()) {
			String error = hasText(result.getError()) ? result.getError() : result.getReason();
			return Msg.fail().add("error", error).add("syncDate", syncDate.trim()).add("devices", result.getDevices())
					.add("onlineDevices", result.getOnlineDevices()).add("deletedUsers", result.getChangedDeletedUsers())
					.add("offlineDevices", offlineDevices).add("sendMode", "direct-online")
					.add("commandsSent", result.getDeleteCommandsQueued())
					.add("commandsPlannedForOnlineDevices", plannedForOnlineDevices)
					.add("commandsQueued", result.getDeleteCommandsQueued())
					.add("estimatedDeviceDispatchSeconds", result.getEstimatedDeviceDispatchSeconds())
					.add("reason", result.getReason());
		}
		return Msg.success().add("syncDate", syncDate.trim()).add("devices", result.getDevices())
				.add("onlineDevices", result.getOnlineDevices()).add("deletedUsers", result.getChangedDeletedUsers())
				.add("offlineDevices", offlineDevices).add("sendMode", "direct-online")
				.add("commandsSent", result.getDeleteCommandsQueued())
				.add("commandsPlannedForOnlineDevices", plannedForOnlineDevices)
				.add("commandsQueued", result.getDeleteCommandsQueued())
				.add("estimatedDeviceDispatchSeconds", result.getEstimatedDeviceDispatchSeconds())
				.add("reason", result.getReason());
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

	@ResponseBody
	@RequestMapping(value = "/deleteUserFromAllDevices", method = RequestMethod.GET)
	public Msg deleteUserFromAllDevices(@RequestParam("enrollId") Long enrollId) {
		if (enrollId == null || enrollId.longValue() <= 0L) {
			return Msg.fail().add("error", "Valid enrollId is required.");
		}
		List<String> serials = getAllKnownDeviceSerials();
		if (serials.isEmpty()) {
			return Msg.fail().add("error", "No devices found in database.").add("enrollId", enrollId);
		}
		String message = "{\"cmd\":\"deleteuser\",\"enrollid\":" + enrollId + ",\"backupnum\":13}";
		int onlineDevices = 0;
		int sent = 0;
		for (String serial : serials) {
			if (!isDeviceOnline(serial)) {
				continue;
			}
			onlineDevices++;
			if (WebSocketPool.sendMessageToDeviceStatus(serial, message)) {
				sent++;
			}
		}
		int offlineDevices = Math.max(0, serials.size() - onlineDevices);
		if (onlineDevices <= 0) {
			return Msg.fail().add("error", "No online devices found for direct delete send.").add("enrollId", enrollId)
					.add("devices", serials.size()).add("onlineDevices", 0).add("offlineDevices", offlineDevices)
					.add("commandsSent", 0);
		}
		if (sent <= 0) {
			return Msg.fail().add("error", "Delete command could not be sent to online devices.").add("enrollId", enrollId)
					.add("devices", serials.size()).add("onlineDevices", onlineDevices)
					.add("offlineDevices", offlineDevices).add("commandsSent", 0);
		}
		return Msg.success().add("enrollId", enrollId).add("devices", serials.size()).add("onlineDevices", onlineDevices)
				.add("offlineDevices", offlineDevices).add("commandsSent", sent);
	}

	@ResponseBody
	@RequestMapping(value = "/onlineDevicesForDirectUserSend", method = RequestMethod.GET)
	public Msg onlineDevicesForDirectUserSend() {
		List<Device> devices = deviceService.findAllDevice();
		List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
		for (Device device : devices) {
			if (device == null || !hasText(device.getSerialNum())) {
				continue;
			}
			String serial = device.getSerialNum().trim();
			if (!isDeviceOnline(serial)) {
				continue;
			}
			Map<String, Object> row = new LinkedHashMap<String, Object>();
			row.put("serialNum", serial);
			row.put("online", Boolean.TRUE);
			rows.add(row);
		}
		return Msg.success().add("devices", rows).add("count", rows.size());
	}

	@ResponseBody
	@RequestMapping(value = "/directSendUsersToSelectedDevices", method = RequestMethod.GET)
	public Msg directSendUsersToSelectedDevices(@RequestParam("enrollIds") String enrollIdsText,
			@RequestParam("deviceSns") String deviceSnsText) {
		List<Long> enrollIds = parseEnrollIdsCsv(enrollIdsText);
		if (enrollIds.isEmpty()) {
			return Msg.fail().add("error", "Please enter valid numeric IDs separated by comma.");
		}
		List<String> requestedSerials = parseSerialsCsv(deviceSnsText);
		if (requestedSerials.isEmpty()) {
			return Msg.fail().add("error", "Please select at least one online device.");
		}
		List<String> onlineSerials = new ArrayList<String>();
		for (String serial : requestedSerials) {
			if (hasText(serial) && isDeviceOnline(serial)) {
				onlineSerials.add(serial.trim());
			}
		}
		if (onlineSerials.isEmpty()) {
			return Msg.fail().add("error", "Selected devices are not online.").add("requestedDevices", requestedSerials.size())
					.add("onlineDevices", 0).add("offlineDevices", requestedSerials.size());
		}
		int usersFound = 0;
		int missingUsers = 0;
		int imageRecordsPlanned = 0;
		List<Long> missingIds = new ArrayList<Long>();
		List<Long> foundUserIds = new ArrayList<Long>();
		Map<Long, Integer> enableByUserId = new LinkedHashMap<Long, Integer>();
		List<UserInfo> allRecordsToSend = new ArrayList<UserInfo>();
		for (Long enrollId : enrollIds) {
			if (enrollId == null) {
				continue;
			}
			Person person = personService.selectByPrimaryKey(enrollId);
			if (person == null) {
				missingUsers++;
				missingIds.add(enrollId);
				continue;
			}
			List<EnrollInfo> enrollInfos = enrollInfoService.selectByEnrollId(enrollId);
			List<UserInfo> userRecords = buildUserInfoRecordsForDirectSend(enrollId, person, enrollInfos);
			if (userRecords.isEmpty()) {
				missingUsers++;
				missingIds.add(enrollId);
				continue;
			}
			usersFound++;
			foundUserIds.add(enrollId);
			enableByUserId.put(enrollId, Integer.valueOf(normalizeEnableStatus(person.getStatus())));
			allRecordsToSend.addAll(userRecords);
			for (UserInfo info : userRecords) {
				if (info != null && info.getBackupnum() == 50) {
					imageRecordsPlanned += onlineSerials.size();
				}
			}
		}
		if (usersFound <= 0) {
			return Msg.fail().add("error", "No matching users with registration data found.")
					.add("requestedIds", enrollIds.size()).add("missingUsers", missingUsers).add("missingIds", missingIds)
					.add("devices", requestedSerials.size()).add("onlineDevices", onlineSerials.size())
					.add("offlineDevices", Math.max(0, requestedSerials.size() - onlineSerials.size()))
					.add("commandsSent", 0);
		}
		int setuserinfoSent = directSendSetUserInfoRecords(allRecordsToSend, onlineSerials);
		int enableSent = directSendEnableCommands(foundUserIds, enableByUserId, onlineSerials);
		return Msg.success().add("requestedIds", enrollIds.size()).add("usersFound", usersFound)
				.add("missingUsers", missingUsers).add("missingIds", missingIds)
				.add("devices", requestedSerials.size()).add("onlineDevices", onlineSerials.size())
				.add("offlineDevices", Math.max(0, requestedSerials.size() - onlineSerials.size()))
				.add("selectedDevices", onlineSerials).add("setuserinfoSent", setuserinfoSent)
				.add("imageRecordsPlanned", imageRecordsPlanned).add("enableCommandsSent", enableSent)
				.add("commandsSent", setuserinfoSent + enableSent);
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




