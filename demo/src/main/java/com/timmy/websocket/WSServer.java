package com.timmy.websocket;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

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
import com.timmy.util.ImageProcess;

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
	private final int configuredPort;
	private final ObjectMapper commandObjectMapper = new ObjectMapper();

	// private Timer timer;
	public WSServer(InetSocketAddress address) {
		super(address);
		this.configuredPort = address.getPort();
		setConnectionLostTimeout(120);
		logger.info("Ã¥Å“Â°Ã¥Ââ‚¬" + address);

	}

	public WSServer(int port) throws UnknownHostException {
		super(new InetSocketAddress(port));
		this.configuredPort = port;
		setConnectionLostTimeout(120);
		logger.info("Ã§Â«Â¯Ã¥ÂÂ£" + port);
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
		System.out.println("Ã¦Å“â€°Ã¤ÂºÂºÃ¨Â¿Å¾Ã¦Å½Â¥Socket conn:" + conn);
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
		System.out.println("Ã¤Â¸Å Ã¤Â¼Â Ã¤ÂºÂºÃ¥â€˜ËœÃ¤Â¿Â¡Ã¦ÂÂ¯-----------------" + message);
		ObjectMapper objectMapper = new ObjectMapper();
		String ret;

		try {
			// Thread.sleep(7000);
			String msg = message.replaceAll(",]", "]");

			JsonNode jsonNode = (JsonNode) objectMapper.readValue(msg, JsonNode.class);
			// System.out.println("Ã¦â€¢Â°Ã¦ÂÂ®"+jsonNode);
			if (jsonNode.has("cmd")) {
				ret = jsonNode.get("cmd").asText();
				if ("reg".equals(ret)) {
					System.out.println("Ã¨Â®Â¾Ã¥Â¤â€¡Ã¤Â¿Â¡Ã¦ÂÂ¯" + jsonNode);
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
					// System.out.println("Ã¦â€¢Â°Ã¦ÂÂ®"+message);
					this.getUserList(jsonNode, conn);

				} else if ("getuserinfo".equals(ret)) {
					// System.out.println("jsonÃ¦â€¢Â°Ã¦ÂÂ®"+jsonNode);
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
					System.out.println("Ã¤Â¸â€¹Ã¥Ââ€˜Ã¦â€¢Â°Ã¦ÂÂ®" + jsonNode);
				} else if ("getalllog".equals(ret)) {

					System.out.println("Ã¨Å½Â·Ã¥Ââ€“Ã¦â€°â‚¬Ã¦Å“â€°Ã¦â€°â€œÃ¥ÂÂ¡Ã¨Â®Â°Ã¥Â½â€¢" + jsonNode);
					try {
						this.getAllLog(jsonNode, conn);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();

					}

				} else if ("getnewlog".equals(ret)) {

					System.out.println("Ã¨Å½Â·Ã¥Ââ€“Ã¦â€°â‚¬Ã¦Å“â€°Ã¦â€°â€œÃ¥ÂÂ¡Ã¨Â®Â°Ã¥Â½â€¢" + jsonNode);
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
					System.out.println("Ã¥Ë†Â Ã©â„¢Â¤Ã¤ÂºÂºÃ¥â€˜Ëœ" + jsonNode);
					updateCommandStatus(sn, "deleteuser");
				} else if ("initsys".equals(ret)) {
					String sn = jsonNode.get("sn").asText();
					DeviceStatus deviceStatus = new DeviceStatus();
					deviceStatus.setWebSocket(conn);
					deviceStatus.setDeviceSn(sn);
					deviceStatus.setStatus(1);
					updateDevice(sn, deviceStatus);
					System.out.println("Ã¥Ë†ÂÃ¥Â§â€¹Ã¥Å’â€“Ã§Â³Â»Ã§Â»Å¸" + jsonNode);
					updateCommandStatus(sn, "initsys");
				} else if ("setdevlock".equals(ret)) {
					String sn = jsonNode.get("sn").asText();
					DeviceStatus deviceStatus = new DeviceStatus();
					deviceStatus.setWebSocket(conn);
					deviceStatus.setDeviceSn(sn);
					deviceStatus.setStatus(1);
					updateDevice(sn, deviceStatus);
					System.out.println("Ã¨Â®Â¾Ã§Â½Â®Ã¥Â¤Â©Ã¦â€”Â¶Ã©â€”Â´Ã¦Â®Âµ" + jsonNode);
					updateCommandStatus(sn, "setdevlock");
				} else if ("setuserlock".equals(ret)) {
					String sn = jsonNode.get("sn").asText();
					DeviceStatus deviceStatus = new DeviceStatus();
					deviceStatus.setWebSocket(conn);
					deviceStatus.setDeviceSn(sn);
					deviceStatus.setStatus(1);
					updateDevice(sn, deviceStatus);
					System.out.println("Ã©â€”Â¨Ã§Â¦ÂÃ¦Å½Ë†Ã¦ÂÆ’" + jsonNode);
					updateCommandStatus(sn, "setuserlock");
				} else if ("getdevinfo".equals(ret)) {
					String sn = jsonNode.get("sn").asText();
					DeviceStatus deviceStatus = new DeviceStatus();
					deviceStatus.setWebSocket(conn);
					deviceStatus.setDeviceSn(sn);
					deviceStatus.setStatus(1);
					updateDevice(sn, deviceStatus);
					System.out.println(new Date() + "Ã¨Â®Â¾Ã¥Â¤â€¡Ã¤Â¿Â¡Ã¦ÂÂ¯" + jsonNode);
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
					System.out.println(new Date() + "Ã¤Â¸â€¹Ã¥Ââ€˜Ã¥Â§â€œÃ¥ÂÂ" + jsonNode);
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

	/* websocketÃ©â€œÂ¾Ã¦Å½Â¥Ã¥Ââ€˜Ã§â€Å¸Ã©â€â„¢Ã¨Â¯Â¯Ã§Å¡â€žÃ¦â€”Â¶Ã¥â‚¬â„¢ */
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
		// System.out.println("socketÃ¦â€“Â­Ã¥Â¼â‚¬Ã¤Âºâ€ ");
		System.out.println("socketÃ¨Â¿Å¾Ã¦Å½Â¥Ã¦â€“Â­Ã¥Â¼â‚¬Ã¤Âºâ€ " + conn);
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
		List<MachineCommand> pending = machineCommandMapper.findPendingCommand(1, serial);
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
		machineCommandMapper.updateCommandStatus(status, 0, new Date(), matched.getId());
		if (status != 1) {
			logger.warn("Device returned failure for command {} serial:{} cmdId:{} payload:{}",
					commandType, serial, matched.getId(), response);
		}
		return matched;
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

	// Ã¨Å½Â·Ã¥Â¾â€”Ã¨Â¿Å¾Ã¦Å½Â¥Ã¨Â®Â¾Ã¥Â¤â€¡Ã¤Â¿Â¡Ã¦ÂÂ¯
	public void getDeviceInfo(JsonNode jsonNode, org.java_websocket.WebSocket args1) {
		String sn = jsonNode.get("sn").asText();
		System.out.println("Ã¥ÂºÂÃ¥Ë†â€”Ã¥ÂÂ·" + sn);
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

	// Ã¨Å½Â·Ã¥Â¾â€”Ã¦â€°â€œÃ¥ÂÂ¡Ã¨Â®Â°Ã¥Â½â€¢Ã¯Â¼Å’Ã¥Å’â€¦Ã¦â€¹Â¬Ã¦Å“ÂºÃ¥â„¢Â¨Ã¥ÂÂ·
	private void getAttandence(JsonNode jsonNode, org.java_websocket.WebSocket conn) {
		// TODO Auto-generated method stub
		// System.out.println("Ã¦â€°â€œÃ¥ÂÂ¡Ã¨Â®Â°Ã¥Â½â€¢-----------"+jsonNode);
		String sn = jsonNode.get("sn").asText();
		ensureNetworkMapping(sn);
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
		Boolean flag = false;
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
				int event = type.get("event").asInt();
				Double temperature = (double) 0;
				if (type.get("temp") != null) {
					temperature = type.get("temp").asDouble();
					temperature = temperature / 10;
					temperature = (double) Math.round(temperature * 10) / 10;
					System.out.println("Ã¦Â¸Â©Ã¥ÂºÂ¦Ã¥â‚¬Â¼" + temperature);
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
					String picName = UUID.randomUUID().toString();
					obj.put("face_base64", type.get("image").asText());
					flag = ImageProcess.base64toImage(type.get("image").asText(), picName);
					if (flag) {
						record.setImage(picName + ".jpg");
					}
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

	// Ã¨Å½Â·Ã¥Ââ€“Ã¦Å“ÂºÃ¥â„¢Â¨Ã¦Å½Â¨Ã©â‚¬ÂÃ¦Â³Â¨Ã¥â€ Å’Ã¤Â¿Â¡Ã¦ÂÂ¯
	private void getEnrollInfo(JsonNode jsonNode, org.java_websocket.WebSocket conn) {
		// TODO Auto-generated method stub
		// System.out.println("Ã¨Â¿Å¾Ã¦Å½Â¥Ã¦â€¢Â°Ã¦ÂÂ®Ã§Â±Â»Ã¥Å¾â€¹"+(conn.getData()).getClass());
		// int enrollId=jsonNode.get("enrollid").asInt();
		// System.out.println("json"+json);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String sn = jsonNode.get("sn").asText();
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
			int rollId = jsonNode.get("admin").asInt();
			String signatures = jsonNode.get("record").asText();
			Person person = new Person();
			person.setId(enrollId);
			person.setName(name);
			person.setRollId(rollId);
			// System.out.println("Ã¥â€˜ËœÃ¥Â·Â¥Ã¥ÂÂ·"+enrollId);
			personService.selectByPrimaryKey(enrollId);
			// System.out.println("Ã¤ÂºÂºÃ¥â€˜ËœÃ¤Â¿Â¡Ã¦ÂÂ¯"+personService.selectByPrimaryKey(enrollId));
			System.out.println("Ã¨Å½Â·Ã¥Ââ€“Ã¤ÂºÂºÃ¥â€˜ËœÃ¦â€¢Â°Ã¦ÂÂ®" + personService.selectByPrimaryKey(enrollId));
			if (personService.selectByPrimaryKey(enrollId) == null) {
				personService.insert(person);
			}
			EnrollInfo enrollInfo = new EnrollInfo();
			enrollInfo.setEnrollId(enrollId);
			enrollInfo.setBackupnum(backupnum);
			enrollInfo.setSignatures(signatures);
			if (backupnum == 50) {

				String picName = UUID.randomUUID().toString();
				ImageProcess.base64toImage(jsonNode.get("record").asText(), picName);
				enrollInfo.setImagePath(picName + ".jpg");
			}
			EnrollInfo existingInfo = enrollInfoService.selectByBackupnum(enrollId, backupnum);
			if (existingInfo == null) {
				enrollInfoService.insertSelective(enrollInfo);
			} else {
				existingInfo.setSignatures(signatures);
				if (backupnum == 50) {
					existingInfo.setImagePath(enrollInfo.getImagePath());
				}
				enrollInfoService.updateByPrimaryKeyWithBLOBs(existingInfo);
			}

			conn.send("{\"ret\":\"senduser\",\"result\":true,\"cloudtime\":\"" + sdf.format(new Date()) + "\"}");
			deviceStatus.setWebSocket(conn);
			deviceStatus.setStatus(1);
			deviceStatus.setDeviceSn(sn);
			updateDevice(sn, deviceStatus);
			/*
			 * }else{ System.out.println("Ã¥ÂÂ¡Ã¥ÂÂ·Ã¦Ë†â€“Ã¨â‚¬â€¦Ã¥Â¯â€ Ã§Â ÂÃ§Å¡â€žÃ¦Æ’â€¦Ã¥â€ Âµ"+jsonNode);
			 * 
			 * conn.send("{\"ret\":\"senduser\",\"result\":true,\"cloudtime\":\"" +
			 * sdf.format(new Date()) + "\"}"); deviceStatus.setWebSocket(conn);
			 * deviceStatus.setStatus(1); deviceStatus.setDeviceSn(sn); updateDevice(sn,
			 * deviceStatus); }
			 */
		}
		timeStamp2 = System.currentTimeMillis();
	}

	// Ã¨Å½Â·Ã¥Ââ€“Ã§â€Â¨Ã¦Ë†Â·Ã¥Ë†â€”Ã¨Â¡Â¨Ã¯Â¼Å’Ã¦Å“ÂÃ¥Å Â¡Ã¥â„¢Â¨Ã¤Â¸Â»Ã¥Å Â¨Ã¥Ââ€˜Ã¥â€¡ÂºÃ¨Â¯Â·Ã¦Â±â€š
	private void getUserList(JsonNode jsonNode, org.java_websocket.WebSocket conn) {
		List<UserTemp> userTemps = new ArrayList<UserTemp>();
		boolean result = jsonNode.get("result").asBoolean();

		int count;
		JsonNode records = jsonNode.get("record");
		// System.out.println("Ã§â€Â¨Ã¦Ë†Â·Ã¥Ë†â€”Ã¨Â¡Â¨"+records);
		String sn = jsonNode.get("sn").asText();
		DeviceStatus deviceStatus = new DeviceStatus();
		if (result) {
			count = jsonNode.get("count").asInt();
			// System.out.println("Ã§â€Â¨Ã¦Ë†Â·Ã¦â€¢Â°Ã¯Â¼Å¡"+count);
			if (count > 0) {
				Iterator iterator = records.elements();
				while (iterator.hasNext()) {
					JsonNode type = (JsonNode) iterator.next();
					Long enrollId = type.get("enrollid").asLong();
					// int enrollId=Integer.valueOf(enrollId1);
					int admin = type.get("admin").asInt();
					int backupnum = type.get("backupnum").asInt();
					UserTemp userTemp = new UserTemp();
					userTemp.setEnrollId(enrollId);
					userTemp.setBackupnum(backupnum);
					userTemp.setAdmin(admin);
					userTemps.add(userTemp);

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

		updateCommandStatus(sn, "getuserlist");

	}

//     	Ã¨Å½Â·Ã¥Â¾â€”Ã§â€Â¨Ã¦Ë†Â·Ã¨Â¯Â¦Ã§Â»â€ Ã¤Â¿Â¡Ã¦ÂÂ¯
	private void getUserInfo(JsonNode jsonNode, org.java_websocket.WebSocket conn) {
		// TODO Auto-generated method stub
		System.out.println(jsonNode);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		// System.out.println("Ã§â€Â¨Ã¦Ë†Â·Ã¥â€¦Â·Ã¤Â½â€œÃ¤Â¿Â¡Ã¦ÂÂ¯"+jsonNode);
		Boolean result = jsonNode.get("result").asBoolean();

		String sn = jsonNode.get("sn").asText();
		// System.out.println("snÃ¦â€¢Â°Ã¦ÂÂ®"+jsonNode);

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
			int admin = jsonNode.get("admin").asInt();
			String signatures = jsonNode.get("record").asText();

			Person person = new Person();
			person.setId(enrollId);
			person.setName(name);
			person.setRollId(admin);
			EnrollInfo enrollInfo = enrollInfoService.selectByBackupnum(enrollId, backupnum);
			if (personService.selectByPrimaryKey(enrollId) == null) {
				personService.insert(person);
			} else if (personService.selectByPrimaryKey(enrollId) != null) {
				personService.updateByPrimaryKey(person);
			}
			if (enrollInfo == null) {
				EnrollInfo insertEnrollInfo = new EnrollInfo();
				insertEnrollInfo.setEnrollId(enrollId);
				insertEnrollInfo.setBackupnum(backupnum);
				insertEnrollInfo.setSignatures(signatures);
				if (backupnum == 50) {
					String picName = UUID.randomUUID().toString();
					ImageProcess.base64toImage(jsonNode.get("record").asText(), picName);
					insertEnrollInfo.setImagePath(picName + ".jpg");
				}
				enrollInfoService.insert(insertEnrollInfo.getEnrollId(), insertEnrollInfo.getBackupnum(),
						insertEnrollInfo.getImagePath(), insertEnrollInfo.getSignatures());
			} else {
				if (backupnum == 50) {
					String picName = UUID.randomUUID().toString();
					ImageProcess.base64toImage(jsonNode.get("record").asText(), picName);
					enrollInfo.setImagePath(picName + ".jpg");
				}
				enrollInfo.setSignatures(signatures);
				enrollInfoService.updateByPrimaryKeyWithBLOBs(enrollInfo);
			}

		}
		// }
		updateCommandStatus(sn, "getuserinfo");

	}

	// Ã¨Å½Â·Ã¥Ââ€“Ã¥â€¦Â¨Ã©Æ’Â¨Ã¦â€°â€œÃ¥ÂÂ¡Ã¨Â®Â°Ã¥Â½â€¢
	// Only keep backup types supported by IDBS face workflow.
	private boolean isSupportedBackupNum(int backupnum) {
		// Face workflow: process face templates (20-27) and face photo (50) only.
		return backupnum == 50 || (backupnum >= 20 && backupnum <= 27);
	}

	private Long extractEnrollIdFromEnablePayload(String payload) {
		if (payload == null || payload.trim().isEmpty()) {
			return null;
		}
		try {
			JsonNode cmdNode = commandObjectMapper.readTree(payload);
			if (cmdNode.has("enrollid")) {
				return Long.valueOf(cmdNode.get("enrollid").asLong());
			}
		} catch (Exception ex) {
			logger.debug("Failed to parse enrollid from enableuser payload: {}", payload, ex);
		}
		return null;
	}

	private Integer extractEnFlagFromEnablePayload(String payload) {
		if (payload == null || payload.trim().isEmpty()) {
			return null;
		}
		try {
			JsonNode cmdNode = commandObjectMapper.readTree(payload);
			if (cmdNode.has("enflag")) {
				int enFlag = cmdNode.get("enflag").asInt();
				return Integer.valueOf(enFlag == 0 ? 0 : 1);
			}
		} catch (Exception ex) {
			logger.debug("Failed to parse enflag from enableuser payload: {}", payload, ex);
		}
		return null;
	}

	private String buildEnableUserCmdForLog(Long enrollId, Integer enFlag) {
		if (enrollId == null || enFlag == null) {
			return null;
		}
		int normalizedEnFlag = enFlag.intValue() == 0 ? 0 : 1;
		return "{\"cmd\":\"enableuser\",\"enrollid\":" + enrollId + ",\"enflag\":" + normalizedEnFlag + "}";
	}

	private void getAllLog(JsonNode jsonNode, WebSocket conn) {

		Boolean result = jsonNode.get("result").asBoolean();
		List<Records> recordAll = new ArrayList<Records>();
		// System.out.println("Ã¨Â®Â°Ã¥Â½â€¢"+jsonNode);
		String sn = jsonNode.get("sn").asText();
		ensureNetworkMapping(sn);
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
					int event = type.get("event").asInt();
					Double temperature = (double) 0;
					if (type.get("temp") != null) {
						temperature = type.get("temp").asDouble();
						temperature = temperature / 100;
						temperature = (double) Math.round(temperature * 10) / 10;
						System.out.println("Ã¦Â¸Â©Ã¥ÂºÂ¦Ã¥â‚¬Â¼" + temperature);
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

	// Ã¨Å½Â·Ã¥Ââ€“Ã¥â€¦Â¨Ã©Æ’Â¨Ã¦â€°â€œÃ¥ÂÂ¡Ã¨Â®Â°Ã¥Â½â€¢
	private void getnewLog(JsonNode jsonNode, WebSocket conn) {

		Boolean result = jsonNode.get("result").asBoolean();
		List<Records> recordAll = new ArrayList<Records>();
		System.out.println("Ã¨Â®Â°Ã¥Â½â€¢" + jsonNode);
		String sn = jsonNode.get("sn").asText();
		ensureNetworkMapping(sn);
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
					int event = type.get("event").asInt();
					Double temperature = (double) 0;
					if (type.get("temp") != null) {
						temperature = type.get("temp").asDouble();
						temperature = temperature / 100;
						temperature = (double) Math.round(temperature * 10) / 10;
						System.out.println("Ã¦Â¸Â©Ã¥ÂºÂ¦Ã¥â‚¬Â¼" + temperature);
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
}






