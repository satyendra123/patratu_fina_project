package com.timmy.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DeadlockLoserDataAccessException;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timmy.entity.Device;
import com.timmy.entity.DeviceStatus;
import com.timmy.entity.EnrollInfo;
import com.timmy.entity.MachineCommand;
import com.timmy.entity.Person;
import com.timmy.entity.Records;
import com.timmy.entity.UserTemp;
import com.timmy.mapper.MachineCommandMapper;
import com.timmy.mapper.NetWorkMapper;
import com.timmy.service.DeviceService;
import com.timmy.service.EnrollInfoService;
import com.timmy.service.PersonService;
import com.timmy.service.RecordsService;

public class WSServer extends WebSocketServer {

	@Autowired
	DeviceService deviceService;

	@Autowired
	RecordsService recordsService;

	@Autowired
	PersonService personService;

	@Autowired
	EnrollInfoService enrollInfoService;

	@Autowired
	MachineCommandMapper machineCommandMapper;

	@Autowired
	NetWorkMapper netWorkMapper;

	int j = 0;
	int h = 0;
	int e = 0;
	static int l;
	Long timeStamp = 0L;
	Long timeStamp2 = 0L;
	public static boolean setUserResult;
	public static Logger logger = LoggerFactory.getLogger(WSServer.class);
	private static final AtomicBoolean SERVER_STARTED = new AtomicBoolean(false);
	private static final String DEVICE_USER_EXPORT_DIR = "device-user-export";
	private static final String DEVICE_USER_FULL_EXPORT_FILE = "device-user-full-export.jsonl";
	private static final String FIXED_REGISTRATION_DEVICE_SN = "AXTI11107153";
	private static final String MASTER_SYNC_CONFIG_FILE = "master-device-sync.properties";
	private static final String MASTER_SYNC_LOG_FILE = "master-device-sync.log";
	private static final boolean AUTO_DB_RELAY_ANY_DEVICE = true;
	private static final boolean ALLOW_DEVICE_ADMIN_STORAGE = false;
	private static final int DEVICECMD_DEADLOCK_MAX_RETRY = 4;
	private static final long DEVICECMD_DEADLOCK_RETRY_BASE_MS = 35L;
	private static final Object DEVICE_USER_EXPORT_LOCK = new Object();
	private static final Object MASTER_SYNC_LOCK = new Object();
	private static volatile boolean masterSyncConfigLoaded = false;
	private static volatile boolean masterSyncEnabled = false;
	private static volatile String masterSyncSourceSn = "";
	private static volatile long masterSyncRelayedRecords = 0L;
	private static volatile long masterSyncQueuedCommands = 0L;
	private static volatile String masterSyncLastUpdated = "";
	private static volatile String masterSyncLastMessage = "Master sync disabled.";
	private static volatile String masterSyncLastRecord = "";
	private static volatile boolean projectCodeDirResolved = false;
	private static volatile Path projectCodeDir = null;
	private final int configuredPort;
	private final ObjectMapper commandObjectMapper = new ObjectMapper();

	// private Timer timer;
	public WSServer(InetSocketAddress address) {
		super(address);
		this.configuredPort = address.getPort();
		setConnectionLostTimeout(120);
		logger.info("ÃƒÂ¥Ã…â€œÃ‚Â°ÃƒÂ¥Ã‚ÂÃ¢â€šÂ¬" + address);

	}

	public WSServer(int port) throws UnknownHostException {
		super(new InetSocketAddress(port));
		this.configuredPort = port;
		setConnectionLostTimeout(120);
		logger.info("ÃƒÂ§Ã‚Â«Ã‚Â¯ÃƒÂ¥Ã‚ÂÃ‚Â£" + port);
	}

	/**
	 * Start websocket server only once to avoid duplicate bind attempts.
	 */
	public synchronized boolean startSafely() {
		if (!SERVER_STARTED.compareAndSet(false, true)) {
			logger.info("WebSocket server already started; skipping duplicate startup.");
			return false;
		}
		if (!isPortAvailable(configuredPort)) {
			SERVER_STARTED.set(false);
			logger.warn("WebSocket port {} is already in use; skipping startup.", configuredPort);
			return false;
		}
		super.start();
		return true;
	}

	public synchronized void stopSafely() throws InterruptedException {
		if (!SERVER_STARTED.compareAndSet(true, false)) {
			return;
		}
		super.stop();
	}

	public static String getDeviceUserExportDirPath() {
		return resolveExportBaseDir().toString();
	}

	public static String getMasterDeviceSyncConfigPath() {
		return resolveExportBaseDir().resolve(MASTER_SYNC_CONFIG_FILE).toString();
	}

	public static String getMasterDeviceSyncLogPath() {
		return resolveExportBaseDir().resolve(MASTER_SYNC_LOG_FILE).toString();
	}

	public static String getDeviceUserFullExportFilePath() {
		return resolveExportBaseDir().resolve(DEVICE_USER_FULL_EXPORT_FILE).toString();
	}

	public static void configureMasterDeviceSync(String masterSn, boolean enabled) {
		ensureMasterSyncConfigLoaded();
		String normalizedSn = normalizeSerial(masterSn);
		synchronized (MASTER_SYNC_LOCK) {
			if (enabled && normalizedSn == null) {
				throw new IllegalArgumentException("masterSn is required when enabling master sync.");
			}
			if (normalizedSn != null) {
				masterSyncSourceSn = normalizedSn;
			}
			masterSyncEnabled = enabled;
			masterSyncLastUpdated = nowTextStatic();
			masterSyncLastMessage = enabled
					? ("Master sync enabled for source device " + safeForStatus(masterSyncSourceSn) + ".")
					: "Master sync disabled.";
			saveMasterSyncConfigLocked();
			appendMasterSyncLogLocked(masterSyncLastUpdated + " | " + masterSyncLastMessage);
		}
	}

	public static Map<String, Object> getMasterDeviceSyncStatus() {
		ensureMasterSyncConfigLoaded();
		Map<String, Object> status = new LinkedHashMap<String, Object>();
		synchronized (MASTER_SYNC_LOCK) {
			status.put("masterSyncEnabled", masterSyncEnabled);
			status.put("masterSyncSourceSn", masterSyncSourceSn);
			status.put("masterSyncRelayedRecords", masterSyncRelayedRecords);
			status.put("masterSyncQueuedCommands", masterSyncQueuedCommands);
			status.put("masterSyncLastUpdated", masterSyncLastUpdated);
			status.put("masterSyncLastMessage", masterSyncLastMessage);
			status.put("masterSyncLastRecord", masterSyncLastRecord);
			status.put("masterSyncConfigFile", getMasterDeviceSyncConfigPath());
			status.put("masterSyncLogFile", getMasterDeviceSyncLogPath());
		}
		return status;
	}

	private boolean isPortAvailable(int port) {
		try (ServerSocket socket = new ServerSocket()) {
			socket.setReuseAddress(true);
			socket.bind(new InetSocketAddress("0.0.0.0", port));
			return true;
		} catch (IOException ex) {
			return false;
		}
	}
	@Override
	public void onOpen(org.java_websocket.WebSocket conn, ClientHandshake handshake) {
		// TODO Auto-generated method stub
		// deviceService=(DeviceService)ContextLoader.getCurrentWebApplicationContext().getBean(DeviceService.class);
		System.out.println("ÃƒÂ¦Ã…â€œÃ¢â‚¬Â°ÃƒÂ¤Ã‚ÂºÃ‚ÂºÃƒÂ¨Ã‚Â¿Ã…Â¾ÃƒÂ¦Ã…Â½Ã‚Â¥Socket conn:" + conn);
		// l++;
		logger.info("WebSocket open remote:{} resource:{} connection:{} upgrade:{} protocol:{} ua:{}",
				conn == null ? null : conn.getRemoteSocketAddress(),
				handshake == null ? null : handshake.getResourceDescriptor(),
				handshake == null ? null : handshake.getFieldValue("Connection"),
				handshake == null ? null : handshake.getFieldValue("Upgrade"),
				handshake == null ? null : handshake.getFieldValue("Sec-WebSocket-Protocol"),
				handshake == null ? null : handshake.getFieldValue("User-Agent"));
		bindSingleKnownDeviceIfPossible(conn);
		l++;

	}

	@Override
	public void onClose(org.java_websocket.WebSocket conn, int code, String reason, boolean remote) {
		String sn = null;
		try {
			sn = WebSocketPool.removeDeviceByWebsocket(conn);
		} catch (Exception ex) {
			logger.warn("Failed removing websocket from pool on close. conn:{}", conn, ex);
		}
		if (sn != null && deviceService != null) {
			Device d1 = deviceService.selectDeviceBySerialNum(sn);
			if (d1 != null) {
				deviceService.updateStatusByPrimaryKey(d1.getId(), 0);
			}
		}
		Object remoteAddress = null;
		if (conn != null) {
			try {
				remoteAddress = conn.getRemoteSocketAddress();
			} catch (Exception ignore) {
				remoteAddress = null;
			}
		}
		logger.info("onClose remote:{} code:{} reason:{} remoteClosed:{} sn:{} wsSize:{}",
				remoteAddress, code, reason, remote, sn, WebSocketPool.wsDevice == null ? 0 : WebSocketPool.wsDevice.size());
	}

	@Override
	public void onMessage(org.java_websocket.WebSocket conn, String message) {
		System.out.println("ÃƒÂ¤Ã‚Â¸Ã…Â ÃƒÂ¤Ã‚Â¼Ã‚Â ÃƒÂ¤Ã‚ÂºÃ‚ÂºÃƒÂ¥Ã¢â‚¬ËœÃ‹Å“ÃƒÂ¤Ã‚Â¿Ã‚Â¡ÃƒÂ¦Ã‚ÂÃ‚Â¯-----------------" + message);
		ObjectMapper objectMapper = new ObjectMapper();
		String ret;

		try {
			// Thread.sleep(7000);
			String msg = message.replaceAll(",]", "]");

			JsonNode jsonNode = (JsonNode) objectMapper.readValue(msg, JsonNode.class);
			// System.out.println("ÃƒÂ¦Ã¢â‚¬Â¢Ã‚Â°ÃƒÂ¦Ã‚ÂÃ‚Â®"+jsonNode);
			if (jsonNode.has("cmd")) {
				ret = jsonNode.get("cmd").asText();
				if ("reg".equals(ret)) {
					System.out.println("ÃƒÂ¨Ã‚Â®Ã‚Â¾ÃƒÂ¥Ã‚Â¤Ã¢â‚¬Â¡ÃƒÂ¤Ã‚Â¿Ã‚Â¡ÃƒÂ¦Ã‚ÂÃ‚Â¯" + jsonNode);
					try {

						this.getDeviceInfo(jsonNode, conn);

					} catch (Exception e) {
						// TODO Auto-generated catch block
						conn.send("{\"ret\":\"reg\",\"result\":false,\"reason\":1}");
						e.printStackTrace();
					}
				} else if ("sendlog".equals(ret)) {
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss");
					try {
						this.getAttandence(jsonNode, conn);

					} catch (Exception e) {
						// TODO Auto-generated catch block
						conn.send("{\"ret\":\"sendlog\",\"result\":false,\"reason\":1}");
						e.printStackTrace();
					}
				} else if ("sendqrcode".equals(ret)) {
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss");
					try {						
						StringBuilder sb = new StringBuilder();
						sb.append("{\"ret\":\"sendqrcode\",\"result\":true");
						sb.append(",\"access\":1");
						sb.append(",\"enrollid\":123");
						sb.append(",\"username\":\"QR code\"");
						sb.append(",\"message\":\"QR code flashing successful\"");
						sb.append(",\"voice\":\"QR code flashing successful\"}");								
						conn.send(sb.toString());
					    System.out.println("QR code-----------------"+sb.toString());
					} catch (Exception e) {
						// TODO Auto-generated catch block
						conn.send("{\"ret\":\"sendqrcode\",\"result\":false,\"reason\":1}");
						e.printStackTrace();
					}
				} else if ("senduser".equals(ret)) {
					System.out.println(jsonNode);

					try {
						if (jsonNode.has("backupnum")) {
							int backupnum = jsonNode.get("backupnum").asInt();
							if (!isSupportedBackupNum(backupnum)) {
								SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
								String sn = jsonNode.has("sn") ? jsonNode.get("sn").asText() : null;
								conn.send("{\"ret\":\"senduser\",\"result\":true,\"cloudtime\":\"" + sdf.format(new Date())
										+ "\"}");
								if (sn != null) {
									DeviceStatus deviceStatus = new DeviceStatus();
									deviceStatus.setWebSocket(conn);
									deviceStatus.setStatus(1);
									deviceStatus.setDeviceSn(sn);
									updateDevice(sn, deviceStatus);
								}
								timeStamp2 = System.currentTimeMillis();
								return;
							}
						}
						this.getEnrollInfo(jsonNode, conn);

					} catch (Exception e) {
						// TODO Auto-generated catch block
						conn.send("{\"ret\":\"senduser\",\"result\":false,\"reason\":1}");
						e.printStackTrace();
					}
				}

			} else if (jsonNode.has("ret")) {
				ret = jsonNode.get("ret").asText();
				// boolean result;
				if ("getuserlist".equals(ret)) {
					// System.out.println(jsonNode);
					// System.out.println("ÃƒÂ¦Ã¢â‚¬Â¢Ã‚Â°ÃƒÂ¦Ã‚ÂÃ‚Â®"+message);
					this.getUserList(jsonNode, conn);

				} else if ("getuserinfo".equals(ret)) {
					// System.out.println("jsonÃƒÂ¦Ã¢â‚¬Â¢Ã‚Â°ÃƒÂ¦Ã‚ÂÃ‚Â®"+jsonNode);
					this.getUserInfo(jsonNode, conn);
					String sn = jsonNode.get("sn").asText();
					DeviceStatus deviceStatus = new DeviceStatus();
					deviceStatus.setWebSocket(conn);
					deviceStatus.setDeviceSn(sn);
					deviceStatus.setStatus(1);
					updateDevice(sn, deviceStatus);
				} else if ("setuserinfo".equals(ret)) {
					Boolean result = jsonNode.get("result").asBoolean();
					// WebSocketPool.setUserResult(result);
					// setUserResult=result;
					// System.out.println();
					String sn = jsonNode.get("sn").asText();
					DeviceStatus deviceStatus = new DeviceStatus();
					deviceStatus.setWebSocket(conn);
					deviceStatus.setDeviceSn(sn);
					deviceStatus.setStatus(1);
					updateDevice(sn, deviceStatus);
					updateCommandStatus(sn, "setuserinfo", jsonNode);
					System.out.println("ÃƒÂ¤Ã‚Â¸Ã¢â‚¬Â¹ÃƒÂ¥Ã‚ÂÃ¢â‚¬ËœÃƒÂ¦Ã¢â‚¬Â¢Ã‚Â°ÃƒÂ¦Ã‚ÂÃ‚Â®" + jsonNode);
				} else if ("getalllog".equals(ret)) {

					System.out.println("ÃƒÂ¨Ã…Â½Ã‚Â·ÃƒÂ¥Ã‚ÂÃ¢â‚¬â€œÃƒÂ¦Ã¢â‚¬Â°Ã¢â€šÂ¬ÃƒÂ¦Ã…â€œÃ¢â‚¬Â°ÃƒÂ¦Ã¢â‚¬Â°Ã¢â‚¬Å“ÃƒÂ¥Ã‚ÂÃ‚Â¡ÃƒÂ¨Ã‚Â®Ã‚Â°ÃƒÂ¥Ã‚Â½Ã¢â‚¬Â¢" + jsonNode);
					try {
						this.getAllLog(jsonNode, conn);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();

					}

				} else if ("getnewlog".equals(ret)) {

					System.out.println("ÃƒÂ¨Ã…Â½Ã‚Â·ÃƒÂ¥Ã‚ÂÃ¢â‚¬â€œÃƒÂ¦Ã¢â‚¬Â°Ã¢â€šÂ¬ÃƒÂ¦Ã…â€œÃ¢â‚¬Â°ÃƒÂ¦Ã¢â‚¬Â°Ã¢â‚¬Å“ÃƒÂ¥Ã‚ÂÃ‚Â¡ÃƒÂ¨Ã‚Â®Ã‚Â°ÃƒÂ¥Ã‚Â½Ã¢â‚¬Â¢" + jsonNode);
					try {
						this.getnewLog(jsonNode, conn);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();

					}

				} else if ("deleteuser".equals(ret)) {
					String sn = jsonNode.get("sn").asText();
					DeviceStatus deviceStatus = new DeviceStatus();
					deviceStatus.setWebSocket(conn);
					deviceStatus.setDeviceSn(sn);
					deviceStatus.setStatus(1);
					updateDevice(sn, deviceStatus);
					System.out.println("ÃƒÂ¥Ã‹â€ Ã‚Â ÃƒÂ©Ã¢â€žÂ¢Ã‚Â¤ÃƒÂ¤Ã‚ÂºÃ‚ÂºÃƒÂ¥Ã¢â‚¬ËœÃ‹Å“" + jsonNode);
					updateCommandStatus(sn, "deleteuser");
				} else if ("initsys".equals(ret)) {
					String sn = jsonNode.get("sn").asText();
					DeviceStatus deviceStatus = new DeviceStatus();
					deviceStatus.setWebSocket(conn);
					deviceStatus.setDeviceSn(sn);
					deviceStatus.setStatus(1);
					updateDevice(sn, deviceStatus);
					System.out.println("ÃƒÂ¥Ã‹â€ Ã‚ÂÃƒÂ¥Ã‚Â§Ã¢â‚¬Â¹ÃƒÂ¥Ã…â€™Ã¢â‚¬â€œÃƒÂ§Ã‚Â³Ã‚Â»ÃƒÂ§Ã‚Â»Ã…Â¸" + jsonNode);
					updateCommandStatus(sn, "initsys");
				} else if ("setdevlock".equals(ret)) {
					String sn = jsonNode.get("sn").asText();
					DeviceStatus deviceStatus = new DeviceStatus();
					deviceStatus.setWebSocket(conn);
					deviceStatus.setDeviceSn(sn);
					deviceStatus.setStatus(1);
					updateDevice(sn, deviceStatus);
					System.out.println("ÃƒÂ¨Ã‚Â®Ã‚Â¾ÃƒÂ§Ã‚Â½Ã‚Â®ÃƒÂ¥Ã‚Â¤Ã‚Â©ÃƒÂ¦Ã¢â‚¬â€Ã‚Â¶ÃƒÂ©Ã¢â‚¬â€Ã‚Â´ÃƒÂ¦Ã‚Â®Ã‚Âµ" + jsonNode);
					updateCommandStatus(sn, "setdevlock");
				} else if ("setuserlock".equals(ret)) {
					String sn = jsonNode.get("sn").asText();
					DeviceStatus deviceStatus = new DeviceStatus();
					deviceStatus.setWebSocket(conn);
					deviceStatus.setDeviceSn(sn);
					deviceStatus.setStatus(1);
					updateDevice(sn, deviceStatus);
					System.out.println("ÃƒÂ©Ã¢â‚¬â€Ã‚Â¨ÃƒÂ§Ã‚Â¦Ã‚ÂÃƒÂ¦Ã…Â½Ã‹â€ ÃƒÂ¦Ã‚ÂÃ†â€™" + jsonNode);
					updateCommandStatus(sn, "setuserlock");
				} else if ("getdevinfo".equals(ret)) {
					String sn = jsonNode.get("sn").asText();
					DeviceStatus deviceStatus = new DeviceStatus();
					deviceStatus.setWebSocket(conn);
					deviceStatus.setDeviceSn(sn);
					deviceStatus.setStatus(1);
					updateDevice(sn, deviceStatus);
					System.out.println(new Date() + "ÃƒÂ¨Ã‚Â®Ã‚Â¾ÃƒÂ¥Ã‚Â¤Ã¢â‚¬Â¡ÃƒÂ¤Ã‚Â¿Ã‚Â¡ÃƒÂ¦Ã‚ÂÃ‚Â¯" + jsonNode);
					updateCommandStatus(sn, "getdevinfo");
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

					JSONObject jObj = new JSONObject();
					jObj.put("SN", sn);
					jObj.put("currentTime", System.currentTimeMillis());
					// RestTemplateUtil.postDeviceInfo(jObj);
				} else if ("setusername".equals(ret)) {
					String sn = jsonNode.get("sn").asText();
					DeviceStatus deviceStatus = new DeviceStatus();
					deviceStatus.setWebSocket(conn);
					deviceStatus.setDeviceSn(sn);
					deviceStatus.setStatus(1);
					updateDevice(sn, deviceStatus);
					System.out.println(new Date() + "ÃƒÂ¤Ã‚Â¸Ã¢â‚¬Â¹ÃƒÂ¥Ã‚ÂÃ¢â‚¬ËœÃƒÂ¥Ã‚Â§Ã¢â‚¬Å“ÃƒÂ¥Ã‚ÂÃ‚Â" + jsonNode);
					updateCommandStatus(sn, "setusername");
				} else if ("reboot".equals(ret)) {
					String sn = jsonNode.get("sn").asText();
					DeviceStatus deviceStatus = new DeviceStatus();
					deviceStatus.setWebSocket(conn);
					deviceStatus.setDeviceSn(sn);
					deviceStatus.setStatus(1);
					updateDevice(sn, deviceStatus);
					updateCommandStatus(sn, "reboot");
				} else if ("getdevlock".equals(ret)) {
					String sn = jsonNode.get("sn").asText();
					DeviceStatus deviceStatus = new DeviceStatus();
					deviceStatus.setWebSocket(conn);
					deviceStatus.setDeviceSn(sn);
					deviceStatus.setStatus(1);
					updateDevice(sn, deviceStatus);
					updateCommandStatus(sn, "getdevlock");
				} else if ("getuserlock".equals(ret)) {
					String sn = jsonNode.get("sn").asText();
					DeviceStatus deviceStatus = new DeviceStatus();
					deviceStatus.setWebSocket(conn);
					deviceStatus.setDeviceSn(sn);
					deviceStatus.setStatus(1);
					updateDevice(sn, deviceStatus);
					updateCommandStatus(sn, "getuserlock");
				} else if ("enableuser".equals(ret)) {
					String sn = jsonNode.get("sn").asText();
					DeviceStatus deviceStatus = new DeviceStatus();
					deviceStatus.setWebSocket(conn);
					deviceStatus.setDeviceSn(sn);
					deviceStatus.setStatus(1);
					updateDevice(sn, deviceStatus);
					MachineCommand matchedEnableCommand = updateCommandStatusAndReturnMatched(sn, "enableuser", jsonNode);
					boolean commandResult = jsonNode.has("result") && jsonNode.get("result").asBoolean();
					String requestPayload = matchedEnableCommand == null ? null : matchedEnableCommand.getContent();
					Long payloadEnrollId = extractEnrollIdFromEnablePayload(requestPayload);
					Integer payloadEnFlag = extractEnFlagFromEnablePayload(requestPayload);
					Long responseEnrollId = jsonNode.has("enrollid") ? jsonNode.get("enrollid").asLong() : null;
					Long effectiveEnrollId = payloadEnrollId != null ? payloadEnrollId : responseEnrollId;
					String effectiveCmd = buildEnableUserCmdForLog(effectiveEnrollId, payloadEnFlag);
					if (commandResult) {
						logger.info("Device enableuser success sn:{} response:{} requestPayload:{} effectiveCmd:{}",
								sn, jsonNode, requestPayload, effectiveCmd);
					} else {
						int reason = jsonNode.has("reason") ? jsonNode.get("reason").asInt() : -1;
						logger.warn("Device enableuser failed sn:{} reason:{} response:{} requestPayload:{} effectiveCmd:{}",
								sn, reason, jsonNode, requestPayload, effectiveCmd);
					}
				} else {
					String sn = jsonNode.get("sn").asText();
					DeviceStatus deviceStatus = new DeviceStatus();
					deviceStatus.setWebSocket(conn);
					deviceStatus.setDeviceSn(sn);
					deviceStatus.setStatus(1);
					updateDevice(sn, deviceStatus);
					updateCommandStatus(sn, ret);
				}

			}

			// Thread.sleep(40000);
			// conn.close();

			/* if(System.currentTimeMillis()) */

		} catch (Exception e) {
			// TODO Auto-generated catch block
			logger.error("Failed to process websocket message from {} payload: {}", conn.getRemoteSocketAddress(), message,
					e);
			e.printStackTrace();
		}

	}

	@Override
	public void onMessage(WebSocket conn, ByteBuffer message) {
		if (message == null) {
			return;
		}
		try {
			ByteBuffer copy = message.asReadOnlyBuffer();
			byte[] payload = new byte[copy.remaining()];
			copy.get(payload);
			String text = new String(payload, StandardCharsets.UTF_8).trim();
			if (text.startsWith("{")) {
				onMessage(conn, text);
				return;
			}
			logger.warn("Received non-json binary frame from {} ({} bytes).",
					conn == null ? null : conn.getRemoteSocketAddress(), payload.length);
		} catch (Exception ex) {
			logger.error("Failed to decode binary websocket frame from {}",
					conn == null ? null : conn.getRemoteSocketAddress(), ex);
		}
	}

	/* websocketÃƒÂ©Ã¢â‚¬Å“Ã‚Â¾ÃƒÂ¦Ã…Â½Ã‚Â¥ÃƒÂ¥Ã‚ÂÃ¢â‚¬ËœÃƒÂ§Ã¢â‚¬ÂÃ…Â¸ÃƒÂ©Ã¢â‚¬ÂÃ¢â€žÂ¢ÃƒÂ¨Ã‚Â¯Ã‚Â¯ÃƒÂ§Ã…Â¡Ã¢â‚¬Å¾ÃƒÂ¦Ã¢â‚¬â€Ã‚Â¶ÃƒÂ¥Ã¢â€šÂ¬Ã¢â€žÂ¢ */
	@Override
	public void onError(org.java_websocket.WebSocket conn, Exception ex) {
		if (ex instanceof BindException) {
			SERVER_STARTED.set(false);
		}
		// TODO Auto-generated method stub
		logger.error("WebSocket error conn:{}", conn, ex);
		ex.printStackTrace();
		if (conn != null) {
			conn.close();
			WebSocketPool.removeDeviceByWebsocket(conn);
		}
		// System.out.println("socketÃƒÂ¦Ã¢â‚¬â€œÃ‚Â­ÃƒÂ¥Ã‚Â¼Ã¢â€šÂ¬ÃƒÂ¤Ã‚ÂºÃ¢â‚¬Â ");
		System.out.println("socketÃƒÂ¨Ã‚Â¿Ã…Â¾ÃƒÂ¦Ã…Â½Ã‚Â¥ÃƒÂ¦Ã¢â‚¬â€œÃ‚Â­ÃƒÂ¥Ã‚Â¼Ã¢â€šÂ¬ÃƒÂ¤Ã‚ÂºÃ¢â‚¬Â " + conn);
	}

	public void onStart() {

		logger.info("WebSocket server started on 0.0.0.0:{}", configuredPort);

	}

	public void updateCommandStatus(String serial, String commandType) {
		updateCommandStatus(serial, commandType, null);
	}

	private void bindSingleKnownDeviceIfPossible(WebSocket conn) {
		if (conn == null || deviceService == null) {
			return;
		}
		try {
			List<Device> devices = deviceService.findAllDevice();
			if (devices == null || devices.size() != 1) {
				return;
			}
			Device onlyDevice = devices.get(0);
			if (onlyDevice == null || onlyDevice.getSerialNum() == null || onlyDevice.getSerialNum().trim().isEmpty()) {
				return;
			}
			String sn = onlyDevice.getSerialNum().trim();
			DeviceStatus current = WebSocketPool.getDeviceStatus(sn);
			if (current != null && current.getWebSocket() != null && current.getWebSocket() != conn) {
				return;
			}
			DeviceStatus deviceStatus = new DeviceStatus();
			deviceStatus.setWebSocket(conn);
			deviceStatus.setDeviceSn(sn);
			deviceStatus.setStatus(1);
			updateDevice(sn, deviceStatus);
			if (onlyDevice.getId() != null) {
				deviceService.updateStatusByPrimaryKey(onlyDevice.getId(), 1);
			}
			logger.warn("Bound websocket {} to single known device SN {} before reg.",
					conn.getRemoteSocketAddress(), sn);
		} catch (Exception ex) {
			logger.debug("Single-device pre-bind skipped for {}", conn.getRemoteSocketAddress(), ex);
		}
	}

	public void updateCommandStatus(String serial, String commandType, JsonNode response) {
		updateCommandStatusAndReturnMatched(serial, commandType, response);
	}

	private MachineCommand updateCommandStatusAndReturnMatched(String serial, String commandType, JsonNode response) {
		List<MachineCommand> pending = findPendingCommandWithRetry(1, serial, commandType);
		if (pending == null || pending.isEmpty()) {
			return null;
		}
		MachineCommand matched = findMatchingPendingCommand(pending, commandType, response);
		if (matched == null) {
			return null;
		}
		int status = 1;
		if (response != null && response.has("result") && !response.get("result").asBoolean()) {
			status = 2;
		}
		updateCommandStatusWithRetry(status, 0, new Date(), matched.getId(), serial, commandType);
		if (status != 1) {
			logger.warn("Device returned failure for command {} serial:{} cmdId:{} payload:{}",
					commandType, serial, matched.getId(), response);
		}
		return matched;
	}

	private List<MachineCommand> findPendingCommandWithRetry(int sendStatus, String serial, String commandType) {
		if (machineCommandMapper == null) {
			return new ArrayList<MachineCommand>();
		}
		int attempt = 0;
		while (true) {
			try {
				List<MachineCommand> pending = machineCommandMapper.findPendingCommand(sendStatus, serial);
				return pending == null ? new ArrayList<MachineCommand>() : pending;
			} catch (RuntimeException ex) {
				if (!isDeadlockException(ex) || attempt >= DEVICECMD_DEADLOCK_MAX_RETRY - 1) {
					throw ex;
				}
				attempt++;
				logger.warn("Deadlock while reading DEVICECMD pending rows. serial:{} sendStatus:{} commandType:{} attempt:{}/{}",
						serial, sendStatus, commandType, attempt, DEVICECMD_DEADLOCK_MAX_RETRY);
				sleepDeadlockBackoff(attempt);
			}
		}
	}

	private void updateCommandStatusWithRetry(int status, int sendStatus, Date runTime, int id, String serial, String commandType) {
		if (machineCommandMapper == null) {
			return;
		}
		int attempt = 0;
		while (true) {
			try {
				machineCommandMapper.updateCommandStatus(status, sendStatus, runTime, id);
				return;
			} catch (RuntimeException ex) {
				if (!isDeadlockException(ex) || attempt >= DEVICECMD_DEADLOCK_MAX_RETRY - 1) {
					throw ex;
				}
				attempt++;
				logger.warn("Deadlock while updating DEVICECMD status. cmdId:{} serial:{} commandType:{} attempt:{}/{}",
						id, serial, commandType, attempt, DEVICECMD_DEADLOCK_MAX_RETRY);
				sleepDeadlockBackoff(attempt);
			}
		}
	}

	private boolean isDeadlockException(Throwable ex) {
		Throwable cursor = ex;
		while (cursor != null) {
			if (cursor instanceof DeadlockLoserDataAccessException) {
				return true;
			}
			String message = cursor.getMessage();
			if (message != null && message.toLowerCase().contains("deadlock")) {
				return true;
			}
			cursor = cursor.getCause();
		}
		return false;
	}

	private void sleepDeadlockBackoff(int attempt) {
		long sleepMs = DEVICECMD_DEADLOCK_RETRY_BASE_MS * Math.max(1, attempt);
		try {
			Thread.sleep(sleepMs);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	private MachineCommand findMatchingPendingCommand(List<MachineCommand> pending, String commandType, JsonNode response) {
		if (pending == null || commandType == null) {
			return null;
		}
		for (MachineCommand command : pending) {
			if (command == null || !commandType.equals(command.getName())) {
				continue;
			}
			if ("setuserinfo".equals(commandType) && response != null && response.has("enrollid")) {
				if (isMatchingSetUserInfo(command.getContent(), response)) {
					return command;
				}
				continue;
			}
			return command;
		}
		return null;
	}

	private boolean isMatchingSetUserInfo(String commandContent, JsonNode response) {
		if (commandContent == null || response == null || !response.has("enrollid")) {
			return false;
		}
		try {
			JsonNode cmdNode = commandObjectMapper.readTree(commandContent);
			if (!cmdNode.has("enrollid")) {
				return false;
			}
			long cmdEnrollId = cmdNode.get("enrollid").asLong();
			long rspEnrollId = response.get("enrollid").asLong();
			if (cmdEnrollId != rspEnrollId) {
				return false;
			}
			if (response.has("backupnum") && cmdNode.has("backupnum")) {
				return cmdNode.get("backupnum").asInt() == response.get("backupnum").asInt();
			}
			return true;
		} catch (Exception ex) {
			logger.debug("Failed to parse setuserinfo command content for matching: {}", commandContent, ex);
			String normalized = commandContent.replace(" ", "");
			String enrollToken = "\"enrollid\":" + response.get("enrollid").asLong();
			if (!normalized.contains(enrollToken)) {
				return false;
			}
			if (response.has("backupnum")) {
				String backupToken = "\"backupnum\":" + response.get("backupnum").asInt();
				return normalized.contains(backupToken);
			}
			return true;
		}
	}

	private Long extractEnrollIdFromEnablePayload(String payload) {
		if (payload == null || payload.trim().isEmpty()) {
			return null;
		}
		try {
			JsonNode node = commandObjectMapper.readTree(payload);
			if (node != null && node.has("enrollid")) {
				return Long.valueOf(node.get("enrollid").asLong());
			}
		} catch (Exception ignore) {
		}
		return null;
	}

	private Integer extractEnFlagFromEnablePayload(String payload) {
		if (payload == null || payload.trim().isEmpty()) {
			return null;
		}
		try {
			JsonNode node = commandObjectMapper.readTree(payload);
			if (node != null && node.has("enflag")) {
				return Integer.valueOf(node.get("enflag").asInt());
			}
		} catch (Exception ignore) {
		}
		return null;
	}

	private String buildEnableUserCmdForLog(Long enrollId, Integer enFlag) {
		StringBuilder sb = new StringBuilder();
		sb.append("{\"cmd\":\"enableuser\"");
		if (enrollId != null) {
			sb.append(",\"enrollid\":").append(enrollId.longValue());
			sb.append(",\"enrolled\":").append(enrollId.longValue());
		}
		if (enFlag != null) {
			sb.append(",\"enflag\":").append(enFlag.intValue());
		}
		sb.append("}");
		return sb.toString();
	}

	// ÃƒÂ¨Ã…Â½Ã‚Â·ÃƒÂ¥Ã‚Â¾Ã¢â‚¬â€ÃƒÂ¨Ã‚Â¿Ã…Â¾ÃƒÂ¦Ã…Â½Ã‚Â¥ÃƒÂ¨Ã‚Â®Ã‚Â¾ÃƒÂ¥Ã‚Â¤Ã¢â‚¬Â¡ÃƒÂ¤Ã‚Â¿Ã‚Â¡ÃƒÂ¦Ã‚ÂÃ‚Â¯
	public void getDeviceInfo(JsonNode jsonNode, org.java_websocket.WebSocket args1) {
		String sn = jsonNode.get("sn").asText();
		System.out.println("ÃƒÂ¥Ã‚ÂºÃ‚ÂÃƒÂ¥Ã‹â€ Ã¢â‚¬â€ÃƒÂ¥Ã‚ÂÃ‚Â·" + sn);
		DeviceStatus deviceStatus = new DeviceStatus();
		if (sn != null) {
			ensureNetworkMapping(sn);

			Device d1 = deviceService.selectDeviceBySerialNum(sn);

			if (d1 == null) {
				int i = deviceService.insert(sn, 1);
				System.out.println(i);
			} else {
				// deviceService.updateByPrimaryKey()
				deviceService.updateStatusByPrimaryKey(d1.getId(), 1);
			}

			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			// long l=System.currentTimeMillis();

			args1.send("{\"ret\":\"reg\",\"result\":true,\"cloudtime\":\"" + sdf.format(new Date()) + "\"}");
			// ",\"cloudtime\":\""
			// args1.send("{\"ret\":\"reg\",\"result\":true,\"cloudtime\":\"" +
			// sdf.format(new Date())+"\",\"nosenduser\":" +true+ "}");
			// System.out.println("{\"ret\":\"reg\",\"result\":true,\"cloudtime\":\"" +
			// sdf.format(new Date())+"\",\"nosenduser\":" +true+ "}");
			deviceStatus.setWebSocket(args1);
			deviceStatus.setStatus(1);
			deviceStatus.setDeviceSn(sn);
			updateDevice(sn, deviceStatus);
			System.out.println(WebSocketPool.getDeviceStatus(sn));
			JSONObject jObj = new JSONObject();
			jObj.put("SN", sn);
			jObj.put("currentTime", System.currentTimeMillis());
			// RestTemplateUtil.postDeviceInfo(jObj);

		} else {
			args1.send("{\"ret\":\"reg\",\"result\":false,\"reason\":1}");
			deviceStatus.setWebSocket(args1);
			deviceStatus.setDeviceSn(sn);
			deviceStatus.setStatus(1);
			updateDevice(sn, deviceStatus);
		}

		timeStamp = System.currentTimeMillis();
		timeStamp2 = timeStamp;
	}

	public void updateDevice(String sn, DeviceStatus deviceStatus) {
		if (WebSocketPool.getDeviceStatus(sn) != null) {
			// WebSocketPool.removeDeviceStatus(sn);
			WebSocketPool.addDeviceAndStatus(sn, deviceStatus);
		} else {
			WebSocketPool.addDeviceAndStatus(sn, deviceStatus);
		}
	}

	private void relayMasterRegistrationIfNeeded(String sourceSn, Long enrollId, String name, int admin, int backupnum,
			String record) {
		if (!isMasterSyncSource(sourceSn)) {
			return;
		}
		if (enrollId == null || !isSupportedBackupNum(backupnum)) {
			return;
		}
		List<Device> devices = deviceService == null ? null : deviceService.findAllDevice();
		if (devices == null || devices.isEmpty()) {
			updateMasterSyncStats(sourceSn, enrollId, backupnum, 0, "Master record received but no devices are registered.");
			return;
		}

		String payload = buildSetUserInfoRelayPayload(enrollId, name, backupnum, admin, record);
		if (payload == null || payload.trim().isEmpty()) {
			updateMasterSyncStats(sourceSn, enrollId, backupnum, 0, "Master record skipped due to empty relay payload.");
			return;
		}

		int queued = 0;
		String normalizedSource = normalizeSerial(sourceSn);
		for (Device device : devices) {
			if (device == null) {
				continue;
			}
			String targetSn = normalizeSerial(device.getSerialNum());
			if (targetSn == null) {
				continue;
			}
			if (normalizedSource != null && normalizedSource.equalsIgnoreCase(targetSn)) {
				continue;
			}
			try {
				queueMachineCommand(targetSn, "setuserinfo", payload);
				queued++;
			} catch (Exception ex) {
				logger.warn("Failed queueing master relay command. sourceSn={} targetSn={} enrollId={} backupnum={}",
						sourceSn, targetSn, enrollId, backupnum, ex);
			}
		}

		String relayMessage = queued > 0
				? ("Relayed master registration to " + queued + " devices.")
				: "Master registration received but no target device was eligible.";
		updateMasterSyncStats(sourceSn, enrollId, backupnum, queued, relayMessage);
	}

	private boolean shouldRelayRegistrationFromSource(String sourceSn) {
		if (AUTO_DB_RELAY_ANY_DEVICE) {
			return true;
		}
		return isMasterSyncSource(sourceSn);
	}

	private void relayRegistrationFromDatabaseIfNeeded(String sourceSn, Long enrollId, String fallbackName,
			int fallbackAdmin, int backupnum) {
		if (!shouldRelayRegistrationFromSource(sourceSn)) {
			return;
		}
		if (enrollId == null || !isSupportedBackupNum(backupnum)) {
			return;
		}
		if (deviceService == null || enrollInfoService == null || machineCommandMapper == null) {
			return;
		}

		EnrollInfo dbInfo = enrollInfoService.selectByBackupnum(enrollId, backupnum);
		if (dbInfo == null) {
			logger.debug("Skip DB relay: no backup data. sourceSn={} enrollId={} backupnum={}", sourceSn, enrollId,
					backupnum);
			return;
		}
		String relayRecord = dbInfo.getSignatures();
		if (relayRecord == null || relayRecord.trim().isEmpty()) {
			logger.debug("Skip DB relay: empty backup record. sourceSn={} enrollId={} backupnum={}", sourceSn, enrollId,
					backupnum);
			return;
		}

		String relayName = fallbackName == null ? "" : fallbackName;
		int relayAdmin = fallbackAdmin;
		if (personService != null) {
			Person dbPerson = personService.selectByPrimaryKey(enrollId);
			if (dbPerson != null) {
				if (dbPerson.getName() != null && !dbPerson.getName().trim().isEmpty()) {
					relayName = dbPerson.getName();
				}
				if (dbPerson.getRollId() != null) {
					relayAdmin = dbPerson.getRollId().intValue();
				}
			}
		}

		String payload = buildSetUserInfoRelayPayload(enrollId, relayName, backupnum, relayAdmin, relayRecord);
		if (payload == null || payload.trim().isEmpty()) {
			return;
		}

		List<Device> devices = deviceService.findAllDevice();
		if (devices == null || devices.isEmpty()) {
			return;
		}
		String normalizedSource = normalizeSerial(sourceSn);
		int queued = 0;
		for (Device device : devices) {
			if (device == null) {
				continue;
			}
			String targetSn = normalizeSerial(device.getSerialNum());
			if (targetSn == null) {
				continue;
			}
			if (normalizedSource != null && normalizedSource.equalsIgnoreCase(targetSn)) {
				continue;
			}
			try {
				queueMachineCommand(targetSn, "setuserinfo", payload);
				queued++;
			} catch (Exception ex) {
				logger.warn("Failed queueing registration DB relay. sourceSn={} targetSn={} enrollId={} backupnum={}",
						sourceSn, targetSn, enrollId, backupnum, ex);
			}
		}

		if (queued > 0) {
			logger.info("Registration DB relay queued. sourceSn={} enrollId={} backupnum={} queuedTargets={}", sourceSn,
					enrollId, backupnum, queued);
		}
		if (isMasterSyncSource(sourceSn)) {
			String relayMessage = queued > 0
					? ("Relayed master registration to " + queued + " devices.")
					: "Master registration received but no target device was eligible.";
			updateMasterSyncStats(sourceSn, enrollId, backupnum, queued, relayMessage);
		}
	}

	private boolean isMasterSyncSource(String sourceSn) {
		ensureMasterSyncConfigLoaded();
		String normalizedSource = normalizeSerial(sourceSn);
		synchronized (MASTER_SYNC_LOCK) {
			if (!masterSyncEnabled) {
				return false;
			}
			if (normalizedSource == null || masterSyncSourceSn == null || masterSyncSourceSn.trim().isEmpty()) {
				return false;
			}
			return masterSyncSourceSn.equalsIgnoreCase(normalizedSource);
		}
	}

	private String buildSetUserInfoRelayPayload(Long enrollId, String name, int backupnum, int admin, String record) {
		if (enrollId == null) {
			return null;
		}
		String normalizedName = name == null ? "" : name;
		String normalizedRecord = record == null ? "" : record;
		StringBuilder sb = new StringBuilder();
		sb.append("{\"cmd\":\"setuserinfo\",\"enrollid\":").append(enrollId.longValue());
		sb.append(",\"name\":\"").append(escapeJson(normalizedName)).append("\"");
		sb.append(",\"backupnum\":").append(backupnum);
		sb.append(",\"admin\":").append(admin);
		sb.append(",\"record\":");
		if ((backupnum == 10 || backupnum == 11) && isJsonNumber(normalizedRecord)) {
			sb.append(normalizedRecord.trim());
		} else {
			sb.append("\"").append(escapeJson(normalizedRecord)).append("\"");
		}
		sb.append("}");
		return sb.toString();
	}

	private boolean isJsonNumber(String value) {
		if (value == null) {
			return false;
		}
		String normalized = value.trim();
		if (normalized.isEmpty()) {
			return false;
		}
		return normalized.matches("-?\\d+(\\.\\d+)?");
	}

	private String escapeJson(String value) {
		if (value == null) {
			return "";
		}
		String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
		escaped = escaped.replace("\r", "\\r").replace("\n", "\\n");
		return escaped;
	}

	private void queueMachineCommand(String serial, String commandName, String content) {
		if (serial == null || commandName == null || content == null) {
			return;
		}
		MachineCommand machineCommand = new MachineCommand();
		machineCommand.setSerial(serial);
		machineCommand.setName(commandName);
		machineCommand.setContent(content);
		machineCommand.setStatus(0);
		machineCommand.setSendStatus(0);
		machineCommand.setErrCount(0);
		machineCommand.setGmtCrate(new Date());
		machineCommand.setGmtModified(new Date());
		machineCommandMapper.insert(machineCommand);
	}

	private void updateMasterSyncStats(String sourceSn, Long enrollId, int backupnum, int queuedTargets, String message) {
		ensureMasterSyncConfigLoaded();
		synchronized (MASTER_SYNC_LOCK) {
			masterSyncRelayedRecords++;
			masterSyncQueuedCommands += Math.max(0, queuedTargets);
			masterSyncLastUpdated = nowTextStatic();
			masterSyncLastRecord = "source=" + safeForStatus(sourceSn)
					+ ", enrollId=" + (enrollId == null ? "" : enrollId.longValue())
					+ ", backupnum=" + backupnum
					+ ", queuedTargets=" + Math.max(0, queuedTargets);
			masterSyncLastMessage = safeForStatus(message);
			saveMasterSyncConfigLocked();
			appendMasterSyncLogLocked(masterSyncLastUpdated + " | " + masterSyncLastMessage + " | " + masterSyncLastRecord);
		}
	}

	// ÃƒÂ¨Ã…Â½Ã‚Â·ÃƒÂ¥Ã‚Â¾Ã¢â‚¬â€ÃƒÂ¦Ã¢â‚¬Â°Ã¢â‚¬Å“ÃƒÂ¥Ã‚ÂÃ‚Â¡ÃƒÂ¨Ã‚Â®Ã‚Â°ÃƒÂ¥Ã‚Â½Ã¢â‚¬Â¢ÃƒÂ¯Ã‚Â¼Ã…â€™ÃƒÂ¥Ã…â€™Ã¢â‚¬Â¦ÃƒÂ¦Ã¢â‚¬Â¹Ã‚Â¬ÃƒÂ¦Ã…â€œÃ‚ÂºÃƒÂ¥Ã¢â€žÂ¢Ã‚Â¨ÃƒÂ¥Ã‚ÂÃ‚Â·
	private void getAttandence(JsonNode jsonNode, org.java_websocket.WebSocket conn) {
		// TODO Auto-generated method stub
		// System.out.println("ÃƒÂ¦Ã¢â‚¬Â°Ã¢â‚¬Å“ÃƒÂ¥Ã‚ÂÃ‚Â¡ÃƒÂ¨Ã‚Â®Ã‚Â°ÃƒÂ¥Ã‚Â½Ã¢â‚¬Â¢-----------"+jsonNode);
		String sn = jsonNode.get("sn").asText();
		ensureNetworkMapping(sn);
		Integer gateInOutOverride = resolveGateInOutOverride(sn);
		int count = jsonNode.get("count").asInt();
		// int logindex=jsonNode.get("logindex").asInt();
		int logindex = -1;
		if (jsonNode.get("logindex") != null) {
			logindex = jsonNode.get("logindex").asInt();
		}
		// int logindex=jsonNode.get("logindex").asInt();
		List<Records> recordAll = new ArrayList<Records>();
		// System.out.println(jsonNode);
		JsonNode records = jsonNode.get("record");
		DeviceStatus deviceStatus = new DeviceStatus();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		if (count > 0) {
			Iterator iterator = records.elements();
			while (iterator.hasNext()) {
				JsonNode type = (JsonNode) iterator.next();
				JSONObject obj = new JSONObject();
					Long enrollId = type.get("enrollid").asLong();
					String timeStr = type.get("time").asText();
					int mode = type.get("mode").asInt();
					int inOut = type.get("inout").asInt();
					if (gateInOutOverride != null) {
						inOut = gateInOutOverride.intValue();
					}
					int event = type.get("event").asInt();
				Double temperature = (double) 0;
				if (type.get("temp") != null) {
					temperature = type.get("temp").asDouble();
					temperature = temperature / 10;
					temperature = (double) Math.round(temperature * 10) / 10;
					System.out.println("ÃƒÂ¦Ã‚Â¸Ã‚Â©ÃƒÂ¥Ã‚ÂºÃ‚Â¦ÃƒÂ¥Ã¢â€šÂ¬Ã‚Â¼" + temperature);
					obj.put("temperature", String.valueOf(temperature));
				}
				Records record = new Records();
				record.setDeviceSerialNum(sn);
				record.setEnrollId(enrollId);
				record.setEvent(event);
				record.setIntout(inOut);
				record.setMode(mode);
				record.setRecordsTime(timeStr);
				record.setTemperature(temperature);
				if (enrollId == 99999999) {
					obj.put("resultStatus", 0);
				} else {
					obj.put("resultStatus", 1);
				}
				obj.put("IdentifyType", "0");
				obj.put("Mac_addr", "");
				obj.put("SN", sn);
				obj.put("address", "");
				obj.put("birthday", "");
				obj.put("depart", "");
				obj.put("devicename", "");
				obj.put("employee_number", "");

				if (type.get("image") != null) {
					obj.put("face_base64", type.get("image").asText());
				}
				recordAll.add(record);
				obj.put("icNum", "");
				obj.put("id", sn);
				obj.put("idNum", "");
				obj.put("idissue", "");
				obj.put("inout", inOut);
				obj.put("location", "");
				obj.put("name", "");
				obj.put("nation", "");

				obj.put("sex", "");
				obj.put("telephone", "");
				obj.put("templatePhoto", "");
				obj.put("time", timeStr);
				obj.put("userid", String.valueOf(enrollId));
				obj.put("validEnd", "");
				obj.put("validStart", "");

				// RestTemplateUtil.postLog(obj);
			}

			if (logindex >= 0) {
				conn.send("{\"ret\":\"sendlog\",\"result\":true" + ",\"count\":" + count + ",\"logindex\":" + logindex
						+ ",\"cloudtime\":\"" + sdf.format(new Date()) + "\"}");
			} else if (logindex < 0) {
				conn.send(
						"{\"ret\":\"sendlog\",\"result\":true" + ",\"cloudtime\":\"" + sdf.format(new Date()) + "\"}");
			}
			/*
			 * conn.send("{\"ret\":\"sendlog\",\"result\":true"+",\"count\":"+count+
			 * ",\"logindex\":"+logindex+",\"cloudtime\":\"" + sdf.format(new Date()) +
			 * "\"}");
			 */
			deviceStatus.setWebSocket(conn);
			deviceStatus.setStatus(1);
			deviceStatus.setDeviceSn(sn);
			updateDevice(sn, deviceStatus);

		} else if (count == 0) {
			conn.send("{\"ret\":\"\"sendlog\"\",\"result\":false,\"reason\":1}");
			deviceStatus.setWebSocket(conn);
			deviceStatus.setStatus(1);
			deviceStatus.setDeviceSn(sn);
			updateDevice(sn, deviceStatus);
		}

		System.out.println(recordAll);
		for (int i = 0; i < recordAll.size(); i++) {
			Records recordsTemp = recordAll.get(i);
			recordsService.insert(recordsTemp);
		}

		timeStamp2 = System.currentTimeMillis();

	}

	// ÃƒÂ¨Ã…Â½Ã‚Â·ÃƒÂ¥Ã‚ÂÃ¢â‚¬â€œÃƒÂ¦Ã…â€œÃ‚ÂºÃƒÂ¥Ã¢â€žÂ¢Ã‚Â¨ÃƒÂ¦Ã…Â½Ã‚Â¨ÃƒÂ©Ã¢â€šÂ¬Ã‚ÂÃƒÂ¦Ã‚Â³Ã‚Â¨ÃƒÂ¥Ã¢â‚¬Â Ã…â€™ÃƒÂ¤Ã‚Â¿Ã‚Â¡ÃƒÂ¦Ã‚ÂÃ‚Â¯
	private void getEnrollInfo(JsonNode jsonNode, org.java_websocket.WebSocket conn) {
		// TODO Auto-generated method stub
		// System.out.println("??????????????????????????????????????????????????????????????????????????????"+(conn.getData()).getClass());
		// int enrollId=jsonNode.get("enrollid").asInt();
		// System.out.println("json"+json);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String sn = jsonNode.get("sn").asText();
		exportRawPayload(sn, "senduser", jsonNode);
		String signatures1 = jsonNode.get("record").asText();
		DeviceStatus deviceStatus = new DeviceStatus();
		if (signatures1 == null) {
			conn.send("{\"ret\":\"senduser\",\"result\":false,\"reason\":1}");
			deviceStatus.setWebSocket(conn);
			deviceStatus.setStatus(1);
			deviceStatus.setDeviceSn(sn);
			updateDevice(sn, deviceStatus);
		} else {
			int backupnum = jsonNode.get("backupnum").asInt();
			if (!isSupportedBackupNum(backupnum)) {
				conn.send("{\"ret\":\"senduser\",\"result\":true,\"cloudtime\":\"" + sdf.format(new Date()) + "\"}");
				deviceStatus.setWebSocket(conn);
				deviceStatus.setStatus(1);
				deviceStatus.setDeviceSn(sn);
				updateDevice(sn, deviceStatus);
				timeStamp2 = System.currentTimeMillis();
				return;
			}
			// if(backupnum!=10&&backupnum!=11){
			Long enrollId = jsonNode.get("enrollid").asLong();
			String name = jsonNode.get("name").asText();
			int rollId = sanitizeAdminForDb(jsonNode.get("admin").asInt());
			String signatures = jsonNode.get("record").asText();
			Person person = new Person();
			person.setId(enrollId);
			person.setName(name);
			person.setRollId(rollId);
			Person existingPerson = personService.selectByPrimaryKey(enrollId);
			if (existingPerson == null) {
				personService.insert(person);
			} else {
				existingPerson.setName(name);
				existingPerson.setRollId(rollId);
				personService.updateByPrimaryKeySelective(existingPerson);
			}
			EnrollInfo enrollInfo = new EnrollInfo();
			enrollInfo.setEnrollId(enrollId);
			enrollInfo.setBackupnum(backupnum);
			enrollInfo.setSignatures(signatures);
			EnrollInfo existingInfo = enrollInfoService.selectByBackupnum(enrollId, backupnum);
			String exportedImagePath = null;
			if (backupnum == 50) {
				logger.info("Dynamic face local image write disabled for senduser. sn={} enrollId={} backupnum={}",
						sn, enrollId, backupnum);
			}
			if (existingInfo == null) {
				enrollInfoService.insertSelective(enrollInfo);
			} else {
				existingInfo.setSignatures(signatures);
				enrollInfoService.updateByPrimaryKeyWithBLOBs(existingInfo);
			}
			exportUserInfoRecord(sn, enrollId, name, rollId, backupnum, signatures, exportedImagePath, "senduser");
			try {
				relayRegistrationFromDatabaseIfNeeded(sn, enrollId, name, rollId, backupnum);
			} catch (Exception relayEx) {
				logger.warn("Registration DB relay failed. sourceSn={} enrollId={} backupnum={}",
						sn, enrollId, backupnum, relayEx);
			}
			conn.send("{\"ret\":\"senduser\",\"result\":true,\"cloudtime\":\"" + sdf.format(new Date()) + "\"}");
			deviceStatus.setWebSocket(conn);
			deviceStatus.setStatus(1);
			deviceStatus.setDeviceSn(sn);
			updateDevice(sn, deviceStatus);
			/*
			 * }else{ System.out.println("????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????"+jsonNode);
			 * 
			 * conn.send("{\"ret\":\"senduser\",\"result\":true,\"cloudtime\":\"" +
			 * sdf.format(new Date()) + "\"}"); deviceStatus.setWebSocket(conn);
			 * deviceStatus.setStatus(1); deviceStatus.setDeviceSn(sn); updateDevice(sn,
			 * deviceStatus); }
			 */
		}
		timeStamp2 = System.currentTimeMillis();
	}
	// ???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
	private void getUserList(JsonNode jsonNode, org.java_websocket.WebSocket conn) {
		List<UserTemp> userTemps = new ArrayList<UserTemp>();
		boolean result = jsonNode.get("result").asBoolean();
		int count;
		JsonNode records = jsonNode.get("record");
		// System.out.println("?????????????????????????????????????????????????????????"+records);
		String sn = jsonNode.get("sn").asText();
		exportRawPayload(sn, "getuserlist", jsonNode);
		DeviceStatus deviceStatus = new DeviceStatus();
		if (result) {
			count = jsonNode.get("count").asInt();
			// System.out.println("???????????????????????????????????????????????????????"+count);
			if (count > 0) {
				Iterator iterator = records.elements();
				while (iterator.hasNext()) {
					JsonNode type = (JsonNode) iterator.next();
					Long enrollId = type.get("enrollid").asLong();
					// int enrollId=Integer.valueOf(enrollId1);
					int admin = sanitizeAdminForDb(type.get("admin").asInt());
					int backupnum = type.get("backupnum").asInt();
					UserTemp userTemp = new UserTemp();
					userTemp.setEnrollId(enrollId);
					userTemp.setBackupnum(backupnum);
					userTemp.setAdmin(admin);
					userTemps.add(userTemp);
					exportUserListRecord(sn, enrollId, admin, backupnum);
				}
				conn.send("{\"cmd\":\"getuserlist\",\"stn\":false}");
				// DeviceStatus deviceStatus=new DeviceStatus();
				deviceStatus.setWebSocket(conn);
				deviceStatus.setStatus(1);
				deviceStatus.setDeviceSn(sn);
				updateDevice(sn, deviceStatus);
			}
		}
		for (int i = 0; i < userTemps.size(); i++) {
			UserTemp uTemp = new UserTemp();
			uTemp = userTemps.get(i);
			if (personService.selectByPrimaryKey(uTemp.getEnrollId()) == null) {
				Person personTemp = new Person();
				personTemp.setId(uTemp.getEnrollId());
				personTemp.setName("");
				personTemp.setRollId(uTemp.getAdmin());
				personService.insert(personTemp);
			}
		}
		queueGetUserInfoCommandsFromUserList(sn, userTemps);
		updateCommandStatus(sn, "getuserlist");
	}

	private void queueGetUserInfoCommandsFromUserList(String sn, List<UserTemp> userTemps) {
		if (!isFixedRegistrationSource(sn) || machineCommandMapper == null || userTemps == null || userTemps.isEmpty()) {
			return;
		}
		Set<String> seen = new LinkedHashSet<String>();
		int queued = 0;
		for (int i = 0; i < userTemps.size(); i++) {
			UserTemp userTemp = userTemps.get(i);
			if (userTemp == null || userTemp.getEnrollId() == null) {
				continue;
			}
			int backupnum = userTemp.getBackupnum();
			if (!isSupportedBackupNum(backupnum)) {
				continue;
			}
			String key = userTemp.getEnrollId().longValue() + "|" + backupnum;
			if (!seen.add(key)) {
				continue;
			}
			String payload = "{\"cmd\":\"getuserinfo\",\"enrollid\":" + userTemp.getEnrollId().longValue()
					+ ",\"backupnum\":" + backupnum + "}";
			MachineCommand command = new MachineCommand();
			command.setSerial(sn);
			command.setName("getuserinfo");
			command.setContent(payload);
			command.setStatus(0);
			command.setSendStatus(0);
			command.setErrCount(0);
			command.setGmtCrate(new Date());
			command.setGmtModified(new Date());
			machineCommandMapper.insert(command);
			queued++;
		}
		if (queued > 0) {
			logger.info("Queued {} getuserinfo commands from getuserlist for fixed source device {}", queued, sn);
		}
	}

	private boolean isFixedRegistrationSource(String sn) {
		if (sn == null) {
			return false;
		}
		String normalized = sn.trim();
		if (normalized.isEmpty()) {
			return false;
		}
		return FIXED_REGISTRATION_DEVICE_SN.equalsIgnoreCase(normalized);
	}
//     	???????????????????????????????????????????????????????????????????????????????????????????????????????????
	private void getUserInfo(JsonNode jsonNode, org.java_websocket.WebSocket conn) {
		// TODO Auto-generated method stub
		System.out.println(jsonNode);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		// System.out.println("??????????????????????????????????????????????????????????????????????????????????"+jsonNode);
		Boolean result = jsonNode.get("result").asBoolean();
		String sn = jsonNode.get("sn").asText();
		exportRawPayload(sn, "getuserinfo", jsonNode);
		// System.out.println("sn???????????????????????????"+jsonNode);
		System.out.println(jsonNode);
		// DeviceStatus deviceStatus=new DeviceStatus();
		if (result) {
			int backupnum = jsonNode.get("backupnum").asInt();
			if (!isSupportedBackupNum(backupnum)) {
				updateCommandStatus(sn, "getuserinfo");
				return;
			}
			Long enrollId = jsonNode.get("enrollid").asLong();
			String name = jsonNode.get("name").asText();
			int admin = sanitizeAdminForDb(jsonNode.get("admin").asInt());
			String signatures = jsonNode.get("record").asText();
			EnrollInfo enrollInfo = enrollInfoService.selectByBackupnum(enrollId, backupnum);
			String exportedImagePath = null;
			Person person = new Person();
			person.setId(enrollId);
			person.setName(name);
			person.setRollId(admin);
			if (personService.selectByPrimaryKey(enrollId) == null) {
				personService.insert(person);
			} else if (personService.selectByPrimaryKey(enrollId) != null) {
				personService.updateByPrimaryKey(person);
			}
			if (backupnum == 50) {
				logger.info("Dynamic face local image write disabled for getuserinfo. sn={} enrollId={} backupnum={}",
						sn, enrollId, backupnum);
			}
			if (enrollInfo == null) {
				EnrollInfo insertEnrollInfo = new EnrollInfo();
				insertEnrollInfo.setEnrollId(enrollId);
				insertEnrollInfo.setBackupnum(backupnum);
				insertEnrollInfo.setSignatures(signatures);
				enrollInfoService.insertSelective(insertEnrollInfo);
			} else {
				enrollInfo.setSignatures(signatures);
				enrollInfoService.updateByPrimaryKeyWithBLOBs(enrollInfo);
			}
			exportUserInfoRecord(sn, enrollId, name, admin, backupnum, signatures, exportedImagePath, "getuserinfo");
		}
		// }
		updateCommandStatus(sn, "getuserinfo");
	}
	// ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????
	// Supported user payload types for full registration sync.
	private boolean isSupportedBackupNum(int backupnum) {
		return backupnum == 10 || backupnum == 11 || backupnum == 50 || (backupnum >= 20 && backupnum <= 27);
	}

	private int sanitizeAdminForDb(int admin) {
		if (!ALLOW_DEVICE_ADMIN_STORAGE) {
			return 0;
		}
		return Math.max(0, admin);
	}

	private void exportRawPayload(String sn, String source, JsonNode payload) {
		if (payload == null) {
			return;
		}
		Path filePath = resolveDeviceExportFilePath(sn, "raw.jsonl");
		if (filePath == null) {
			return;
		}
		StringBuilder line = new StringBuilder();
		line.append("{\"time\":\"").append(nowText()).append("\"");
		line.append(",\"source\":\"").append(source == null ? "" : source).append("\"");
		line.append(",\"payload\":").append(payload.toString()).append("}");
		appendTextLine(filePath, line.toString());
	}

	private void exportUserListRecord(String sn, Long enrollId, int admin, int backupnum) {
		Path csvPath = resolveDeviceExportFilePath(sn, "userlist.csv");
		if (csvPath == null) {
			return;
		}
		String header = "time,device_sn,enrollid,admin,backupnum";
		StringBuilder row = new StringBuilder();
		row.append(csvCell(nowText())).append(",");
		row.append(csvCell(sn)).append(",");
		row.append(csvCell(enrollId == null ? "" : String.valueOf(enrollId.longValue()))).append(",");
		row.append(csvCell(String.valueOf(admin))).append(",");
		row.append(csvCell(String.valueOf(backupnum)));
		appendCsvLine(csvPath, header, row.toString());
	}

	private void exportUserInfoRecord(String sn, Long enrollId, String name, int admin, int backupnum, String record,
			String imagePath, String source) {
		Path csvPath = resolveDeviceExportFilePath(sn, "userinfo.csv");
		if (csvPath == null) {
			return;
		}
		String header = "time,source,device_sn,enrollid,name,admin,backupnum,record_size,image_path,record";
		String normalizedRecord = record == null ? "" : record;
		StringBuilder row = new StringBuilder();
		row.append(csvCell(nowText())).append(",");
		row.append(csvCell(source)).append(",");
		row.append(csvCell(sn)).append(",");
		row.append(csvCell(enrollId == null ? "" : String.valueOf(enrollId.longValue()))).append(",");
		row.append(csvCell(name)).append(",");
		row.append(csvCell(String.valueOf(admin))).append(",");
		row.append(csvCell(String.valueOf(backupnum))).append(",");
		row.append(csvCell(String.valueOf(normalizedRecord.length()))).append(",");
		row.append(csvCell(imagePath)).append(",");
		row.append(csvCell(normalizedRecord));
		appendCsvLine(csvPath, header, row.toString());

		Path txtPath = resolveDeviceExportFilePath(sn, "userinfo.txt");
		if (txtPath != null) {
			StringBuilder line = new StringBuilder();
			line.append("time=").append(nowText());
			line.append(", source=").append(source == null ? "" : source);
			line.append(", device=").append(sn == null ? "" : sn);
			line.append(", enrollid=").append(enrollId == null ? "" : enrollId.longValue());
			line.append(", name=").append(name == null ? "" : name);
			line.append(", admin=").append(admin);
			line.append(", backupnum=").append(backupnum);
			line.append(", record_size=").append(normalizedRecord.length());
			line.append(", image_path=").append(imagePath == null ? "" : imagePath);
			appendTextLine(txtPath, line.toString());
		}

		exportUnifiedUserRecord(sn, enrollId, name, admin, backupnum, normalizedRecord, imagePath, source);
	}

	private void exportUnifiedUserRecord(String sn, Long enrollId, String name, int admin, int backupnum, String record,
			String imagePath, String source) {
		Path bundlePath = resolveExportBaseDir().resolve(DEVICE_USER_FULL_EXPORT_FILE);
		StringBuilder json = new StringBuilder();
		json.append("{\"time\":\"").append(escapeJson(nowText())).append("\"");
		json.append(",\"source\":\"").append(escapeJson(source == null ? "" : source)).append("\"");
		json.append(",\"deviceSn\":\"").append(escapeJson(sn == null ? "" : sn)).append("\"");
		json.append(",\"enrollid\":").append(enrollId == null ? "null" : String.valueOf(enrollId.longValue()));
		json.append(",\"name\":\"").append(escapeJson(name == null ? "" : name)).append("\"");
		json.append(",\"admin\":").append(admin);
		json.append(",\"backupnum\":").append(backupnum);
		json.append(",\"recordSize\":").append(record == null ? 0 : record.length());
		json.append(",\"imagePath\":\"").append(escapeJson(imagePath == null ? "" : imagePath)).append("\"");
		json.append(",\"record\":\"").append(escapeJson(record == null ? "" : record)).append("\"");
		json.append("}");
		appendTextLine(bundlePath, json.toString());
	}

	private Path resolveDeviceExportFilePath(String sn, String fileName) {
		if (fileName == null || fileName.trim().isEmpty()) {
			return null;
		}
		String deviceToken = sanitizeFileToken(sn);
		if (deviceToken.isEmpty()) {
			deviceToken = "UNKNOWN";
		}
		return resolveExportBaseDir().resolve(deviceToken + "_" + fileName);
	}

	private static Path resolveExportBaseDir() {
		String explicit = System.getProperty("idsl.export.dir");
		if (explicit != null && !explicit.trim().isEmpty()) {
			return Paths.get(explicit.trim());
		}

		Path projectDir = resolveProjectCodeDir();
		if (projectDir != null) {
			return projectDir.resolve(DEVICE_USER_EXPORT_DIR);
		}

		String userDir = System.getProperty("user.dir");
		if (userDir != null && !userDir.trim().isEmpty()) {
			return Paths.get(userDir.trim(), DEVICE_USER_EXPORT_DIR);
		}

		String wtpDeploy = System.getProperty("wtp.deploy");
		if (wtpDeploy != null && !wtpDeploy.trim().isEmpty()) {
			Path deployRoot = Paths.get(wtpDeploy.trim());
			Path appDir = deployRoot.resolve("FingerprintDeviceDemo");
			if (Files.exists(appDir)) {
				return appDir.resolve(DEVICE_USER_EXPORT_DIR);
			}
			return deployRoot.resolve(DEVICE_USER_EXPORT_DIR);
		}

		String catalinaBase = System.getProperty("catalina.base");
		if (catalinaBase != null && !catalinaBase.trim().isEmpty()) {
			return Paths.get(catalinaBase.trim(), "webapps", "FingerprintDeviceDemo", DEVICE_USER_EXPORT_DIR);
		}

		return Paths.get(DEVICE_USER_EXPORT_DIR);
	}

	private static Path resolveProjectCodeDir() {
		if (projectCodeDirResolved) {
			return projectCodeDir;
		}
		synchronized (MASTER_SYNC_LOCK) {
			if (projectCodeDirResolved) {
				return projectCodeDir;
			}
			List<Path> candidates = new ArrayList<Path>();
			addProjectCandidate(candidates, System.getProperty("idsl.project.code.dir"));

			String userHome = System.getProperty("user.home");
			if (userHome != null && !userHome.trim().isEmpty()) {
				addProjectCandidate(candidates,
						Paths.get(userHome, "Desktop", "JAVA_2512_working", "JAVA_2512", "demo").toString());
				addProjectCandidate(candidates,
						Paths.get(userHome, "Desktop", "JAVA_2512", "JAVA_2512", "demo").toString());
				addProjectCandidate(candidates,
						Paths.get(userHome, "eclipse-workspace", "JAVA_2512", "demo").toString());
			}
			addProjectCandidate(candidates, System.getProperty("user.dir"));

			for (Path candidate : candidates) {
				if (isLikelyProjectCodeDir(candidate)) {
					projectCodeDir = candidate;
					break;
				}
			}
			projectCodeDirResolved = true;
			return projectCodeDir;
		}
	}

	private static void addProjectCandidate(List<Path> candidates, String rawPath) {
		if (rawPath == null || rawPath.trim().isEmpty()) {
			return;
		}
		try {
			Path normalized = Paths.get(rawPath.trim()).normalize();
			if (!candidates.contains(normalized)) {
				candidates.add(normalized);
			}
		} catch (Exception ignore) {
		}
	}

	private static boolean isLikelyProjectCodeDir(Path dir) {
		if (dir == null || !Files.isDirectory(dir)) {
			return false;
		}
		try {
			Path pom = dir.resolve("pom.xml");
			Path wsServer = dir.resolve(Paths.get("src", "main", "java", "com", "timmy", "websocket", "WSServer.java"));
			return Files.exists(pom) && Files.exists(wsServer);
		} catch (Exception ex) {
			return false;
		}
	}

	private static void ensureMasterSyncConfigLoaded() {
		synchronized (MASTER_SYNC_LOCK) {
			if (masterSyncConfigLoaded) {
				return;
			}
			Path configPath = resolveExportBaseDir().resolve(MASTER_SYNC_CONFIG_FILE);
			if (Files.exists(configPath)) {
				Properties props = new Properties();
				try (InputStream in = Files.newInputStream(configPath)) {
					props.load(in);
					masterSyncEnabled = Boolean.parseBoolean(props.getProperty("enabled", "false"));
					masterSyncSourceSn = safePropertiesText(props.getProperty("masterSn", ""));
					masterSyncRelayedRecords = parseLongOrZero(props.getProperty("relayedRecords"));
					masterSyncQueuedCommands = parseLongOrZero(props.getProperty("queuedCommands"));
					masterSyncLastUpdated = safePropertiesText(props.getProperty("lastUpdated", ""));
					masterSyncLastMessage = safePropertiesText(props.getProperty("lastMessage", "Master sync disabled."));
					masterSyncLastRecord = safePropertiesText(props.getProperty("lastRecord", ""));
				} catch (Exception ex) {
					logger.warn("Failed loading master sync config {}", configPath, ex);
				}
			}
			masterSyncConfigLoaded = true;
		}
	}

	private static void saveMasterSyncConfigLocked() {
		Path configPath = resolveExportBaseDir().resolve(MASTER_SYNC_CONFIG_FILE);
		try {
			Path parent = configPath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Properties props = new Properties();
			props.setProperty("enabled", String.valueOf(masterSyncEnabled));
			props.setProperty("masterSn", safeForStatus(masterSyncSourceSn));
			props.setProperty("relayedRecords", String.valueOf(masterSyncRelayedRecords));
			props.setProperty("queuedCommands", String.valueOf(masterSyncQueuedCommands));
			props.setProperty("lastUpdated", safeForStatus(masterSyncLastUpdated));
			props.setProperty("lastMessage", safeForStatus(masterSyncLastMessage));
			props.setProperty("lastRecord", safeForStatus(masterSyncLastRecord));
			try (OutputStream out = Files.newOutputStream(configPath,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				props.store(out, "Master device registration sync status");
			}
		} catch (Exception ex) {
			logger.warn("Failed writing master sync config {}", configPath, ex);
		}
	}

	private static void appendMasterSyncLogLocked(String line) {
		if (line == null || line.trim().isEmpty()) {
			return;
		}
		Path logPath = resolveExportBaseDir().resolve(MASTER_SYNC_LOG_FILE);
		try {
			Path parent = logPath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.write(logPath, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (Exception ex) {
			logger.warn("Failed writing master sync log {}", logPath, ex);
		}
	}

	private static String safePropertiesText(String value) {
		if (value == null) {
			return "";
		}
		return value.trim();
	}

	private static long parseLongOrZero(String value) {
		if (value == null || value.trim().isEmpty()) {
			return 0L;
		}
		try {
			return Long.parseLong(value.trim());
		} catch (Exception ex) {
			return 0L;
		}
	}

	private static String normalizeSerial(String sn) {
		if (sn == null) {
			return null;
		}
		String normalized = sn.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private static String nowTextStatic() {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
	}

	private static String safeForStatus(String text) {
		return text == null ? "" : text.trim();
	}

	private String sanitizeFileToken(String value) {
		if (value == null) {
			return "";
		}
		String normalized = value.trim();
		if (normalized.isEmpty()) {
			return "";
		}
		return normalized.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private void appendCsvLine(Path path, String header, String line) {
		if (path == null || line == null) {
			return;
		}
		synchronized (DEVICE_USER_EXPORT_LOCK) {
			try {
				Path parent = path.getParent();
				if (parent != null) {
					Files.createDirectories(parent);
				}
				boolean exists = Files.exists(path);
				if (!exists && header != null && !header.isEmpty()) {
					Files.write(path, (header + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
							StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				}
				Files.write(path, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
						StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			} catch (Exception ex) {
				logger.warn("Failed to write device user CSV export file {}", path, ex);
			}
		}
	}

	private void appendTextLine(Path path, String line) {
		if (path == null || line == null) {
			return;
		}
		synchronized (DEVICE_USER_EXPORT_LOCK) {
			try {
				Path parent = path.getParent();
				if (parent != null) {
					Files.createDirectories(parent);
				}
				Files.write(path, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
						StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			} catch (Exception ex) {
				logger.warn("Failed to write device user text export file {}", path, ex);
			}
		}
	}

	private String csvCell(String value) {
		String normalized = value == null ? "" : value;
		String escaped = normalized.replace("\"", "\"\"");
		if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
			return "\"" + escaped + "\"";
		}
		return escaped;
	}

	private String nowText() {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
	}

	private void getAllLog(JsonNode jsonNode, WebSocket conn) {

		Boolean result = jsonNode.get("result").asBoolean();
		List<Records> recordAll = new ArrayList<Records>();
		// System.out.println("ÃƒÂ¨Ã‚Â®Ã‚Â°ÃƒÂ¥Ã‚Â½Ã¢â‚¬Â¢"+jsonNode);
			String sn = jsonNode.get("sn").asText();
			ensureNetworkMapping(sn);
			Integer gateInOutOverride = resolveGateInOutOverride(sn);
			JsonNode records = jsonNode.get("record");
		DeviceStatus deviceStatus = new DeviceStatus();
		int count;
		boolean flag = false;
		if (result) {
			count = jsonNode.get("count").asInt();
			if (count > 0) {
					Iterator iterator = records.elements();
					while (iterator.hasNext()) {
						JsonNode type = (JsonNode) iterator.next();
						Long enrollId = type.get("enrollid").asLong();
						String timeStr = type.get("time").asText();
						int mode = type.get("mode").asInt();
						int inOut = type.get("inout").asInt();
						if (gateInOutOverride != null) {
							inOut = gateInOutOverride.intValue();
						}
						int event = type.get("event").asInt();
					Double temperature = (double) 0;
					if (type.get("temp") != null) {
						temperature = type.get("temp").asDouble();
						temperature = temperature / 100;
						temperature = (double) Math.round(temperature * 10) / 10;
						System.out.println("ÃƒÂ¦Ã‚Â¸Ã‚Â©ÃƒÂ¥Ã‚ÂºÃ‚Â¦ÃƒÂ¥Ã¢â€šÂ¬Ã‚Â¼" + temperature);
					}
					Records record = new Records();
					// record.setDeviceSerialNum(sn);
					record.setEnrollId(enrollId);
					record.setEvent(event);
					record.setIntout(inOut);
					record.setMode(mode);
					record.setRecordsTime(timeStr);
					record.setDeviceSerialNum(sn);
					record.setTemperature(temperature);

					recordAll.add(record);
				}
				conn.send("{\"cmd\":\"getalllog\",\"stn\":false}");
				deviceStatus.setWebSocket(conn);
				deviceStatus.setStatus(1);
				deviceStatus.setDeviceSn(sn);
				updateDevice(sn, deviceStatus);
			}
		}
		// System.out.println(recordAll);
		for (int i = 0; i < recordAll.size(); i++) {
			Records recordsTemp = recordAll.get(i);
			recordsService.insert(recordsTemp);
		}
		updateCommandStatus(sn, "getalllog");

	}

	// ÃƒÂ¨Ã…Â½Ã‚Â·ÃƒÂ¥Ã‚ÂÃ¢â‚¬â€œÃƒÂ¥Ã¢â‚¬Â¦Ã‚Â¨ÃƒÂ©Ã†â€™Ã‚Â¨ÃƒÂ¦Ã¢â‚¬Â°Ã¢â‚¬Å“ÃƒÂ¥Ã‚ÂÃ‚Â¡ÃƒÂ¨Ã‚Â®Ã‚Â°ÃƒÂ¥Ã‚Â½Ã¢â‚¬Â¢
	private void getnewLog(JsonNode jsonNode, WebSocket conn) {

		Boolean result = jsonNode.get("result").asBoolean();
		List<Records> recordAll = new ArrayList<Records>();
		System.out.println("ÃƒÂ¨Ã‚Â®Ã‚Â°ÃƒÂ¥Ã‚Â½Ã¢â‚¬Â¢" + jsonNode);
		String sn = jsonNode.get("sn").asText();
		ensureNetworkMapping(sn);
		Integer gateInOutOverride = resolveGateInOutOverride(sn);
		JsonNode records = jsonNode.get("record");
		DeviceStatus deviceStatus = new DeviceStatus();
		boolean flag = false;
		int count;
		if (result) {
			count = jsonNode.get("count").asInt();
			if (count > 0) {
				Iterator iterator = records.elements();
				while (iterator.hasNext()) {
					JsonNode type = (JsonNode) iterator.next();
						Long enrollId = type.get("enrollid").asLong();
						String timeStr = type.get("time").asText();
						int mode = type.get("mode").asInt();
						int inOut = type.get("inout").asInt();
						if (gateInOutOverride != null) {
							inOut = gateInOutOverride.intValue();
						}
						int event = type.get("event").asInt();
					Double temperature = (double) 0;
					if (type.get("temp") != null) {
						temperature = type.get("temp").asDouble();
						temperature = temperature / 100;
						temperature = (double) Math.round(temperature * 10) / 10;
						System.out.println("ÃƒÂ¦Ã‚Â¸Ã‚Â©ÃƒÂ¥Ã‚ÂºÃ‚Â¦ÃƒÂ¥Ã¢â€šÂ¬Ã‚Â¼" + temperature);
					}
					Records record = new Records();
					// record.setDeviceSerialNum(sn);
					record.setEnrollId(enrollId);
					record.setEvent(event);
					record.setIntout(inOut);
					record.setMode(mode);
					record.setRecordsTime(timeStr);
					record.setDeviceSerialNum(sn);
					record.setTemperature(temperature);
					recordAll.add(record);
				}
				conn.send("{\"cmd\":\"getnewlog\",\"stn\":false}");
				deviceStatus.setWebSocket(conn);
				deviceStatus.setStatus(1);
				deviceStatus.setDeviceSn(sn);
				updateDevice(sn, deviceStatus);
			}
		}
		// System.out.println(recordAll);
		for (int i = 0; i < recordAll.size(); i++) {
			Records recordsTemp = recordAll.get(i);
			recordsService.insert(recordsTemp);
		}
		updateCommandStatus(sn, "getnewlog");

	}

	private Integer ensureNetworkMapping(String sn) {
		if (sn == null) {
			return null;
		}
		String normalizedSn = sn.trim();
		if (normalizedSn.isEmpty() || netWorkMapper == null) {
			return null;
		}
		Integer networkId = netWorkMapper.selectNetworkIdBySlno(normalizedSn);
		if (networkId != null) {
			return networkId;
		}
		try {
			netWorkMapper.insertNetworkPlaceholder(normalizedSn);
		} catch (Exception ex) {
			logger.warn("Failed to insert NetWork placeholder for device sn {}", normalizedSn, ex);
		}
		return netWorkMapper.selectNetworkIdBySlno(normalizedSn);
	}

	private Integer resolveGateInOutOverride(String sn) {
		if (sn == null || netWorkMapper == null) {
			return null;
		}
		String normalizedSn = sn.trim();
		if (normalizedSn.isEmpty()) {
			return null;
		}
		try {
			String gate = netWorkMapper.selectGateBySlno(normalizedSn);
			return toInOutByGate(gate);
		} catch (Exception ex) {
			logger.warn("Failed to resolve gate mode for device sn {}", normalizedSn, ex);
			return null;
		}
	}

	private Integer toInOutByGate(String gate) {
		if (gate == null) {
			return null;
		}
		String normalized = gate.trim();
		if (normalized.isEmpty()) {
			return null;
		}
		String upper = normalized.toUpperCase();
		if ("IN".equals(upper) || "ENTRY".equals(upper) || "0".equals(upper)) {
			return Integer.valueOf(0);
		}
		if ("OUT".equals(upper) || "EXIT".equals(upper) || "1".equals(upper)) {
			return Integer.valueOf(1);
		}
		return null;
	}
}








