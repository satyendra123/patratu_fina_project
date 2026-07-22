const { normalizeQrCode } = require("./qr-code");

function roleName(roleId, fallback) {
  if (fallback) {
    return fallback;
  }
  if (Number(roleId) >= 14) {
    return "superadmin";
  }
  if (Number(roleId) >= 10) {
    return "admin";
  }
  if (Number(roleId) > 0) {
    return "manager";
  }
  return "user";
}

class MySqlRepository {
  constructor(config) {
    this.config = config;
    this.pool = null;
  }

  async getPool() {
    if (this.pool) {
      return this.pool;
    }
    let mysql;
    try {
      mysql = require("mysql2/promise");
    } catch (error) {
      throw new Error("Install dependencies first: npm install");
    }
    this.pool = mysql.createPool(this.config);
    return this.pool;
  }

  async query(sql, params) {
    const pool = await this.getPool();
    const [rows] = await pool.execute(sql, params);
    return rows;
  }

  async ensureDevice(serial) {
    await this.query(
      `INSERT INTO devices (serial, online)
      VALUES (?, 0)
      ON DUPLICATE KEY UPDATE serial = VALUES(serial)`,
      [serial]
    );
  }

  async registerDevice(device) {
    await this.query(
      `INSERT INTO devices (
        serial, ip, mac, agent_version, inventory_json, online, last_seen_at
      ) VALUES (?, ?, ?, ?, ?, 1, NOW())
      ON DUPLICATE KEY UPDATE
        ip = VALUES(ip),
        mac = VALUES(mac),
        agent_version = VALUES(agent_version),
        inventory_json = VALUES(inventory_json),
        online = 1,
        last_seen_at = NOW()`,
      [
        device.serial,
        device.ip || null,
        device.mac || null,
        device.agentVersion || null,
        JSON.stringify(device.inventory || {})
      ]
    );
  }

  async updateDeviceStatus(serial, online) {
    await this.query(
      `INSERT INTO devices (serial, online, last_seen_at)
      VALUES (?, ?, NOW())
      ON DUPLICATE KEY UPDATE online = VALUES(online), last_seen_at = NOW()`,
      [serial, online ? 1 : 0]
    );
  }

  async getSnapshot(serial) {
    const users = await this.query(
      `SELECT id, name, role, role_id AS roleId, status, updated_at AS updatedAt, version
      FROM users
      WHERE deleted_at IS NULL
      ORDER BY id`,
      []
    );
    const deletedRows = await this.query(
      `SELECT id FROM users WHERE deleted_at IS NOT NULL ORDER BY deleted_at`,
      []
    );
    const commands = await this.query(
      `SELECT id, serial, name, payload_json AS payload
      FROM device_commands
      WHERE serial = ? AND status = 'queued'
      ORDER BY id
      LIMIT 1000`,
      [serial]
    );

    return {
      full: true,
      serverCursor: Date.now(),
      users: users.map((row) => ({
        id: String(row.id),
        name: row.name || "",
        role: roleName(row.roleId, row.role),
        roleId: row.roleId,
        status: Number(row.status),
        updatedAt: row.updatedAt,
        version: row.version
      })),
      deletedUserIds: deletedRows.map((row) => String(row.id)),
      commands: commands.map((command) => ({
        ...command,
        payload: parseJsonColumn(command.payload)
      }))
    };
  }

  async validateQr(rawCode, serial) {
    const code = normalizeQrCode(rawCode);
    const users = await this.query(
      `SELECT id, name, role, role_id AS roleId, status
      FROM users
      WHERE id = ? AND deleted_at IS NULL
      LIMIT 1`,
      [code]
    );
    const user = users[0] || null;
    const valid = Boolean(user && Number(user.status) === 1);
    return {
      code,
      serial,
      valid,
      access: valid ? 1 : 0,
      openGate: valid,
      user: user
        ? {
            id: String(user.id),
            name: user.name,
            role: roleName(user.roleId, user.role),
            roleId: user.roleId
          }
        : null,
      message: valid ? "Valid access" : "Invalid or disabled QR",
      source: "mysql"
    };
  }

  async saveScanEvent(event) {
    const result = event.result || {};
    const user = result.user || {};
    await this.ensureDevice(event.serial);
    await this.query(
      `INSERT INTO scan_events (
        scan_id, serial, code, raw_code, user_id, valid, inout,
        local_result_json, result_json, scanned_at, saved_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
      ON DUPLICATE KEY UPDATE
        result_json = VALUES(result_json),
        saved_at = NOW()`,
      [
        event.scanId,
        event.serial,
        result.code || event.code || null,
        event.rawCode || event.code || null,
        user.id || null,
        result.valid ? 1 : 0,
        event.inout || "IN",
        JSON.stringify(event.localResult || null),
        JSON.stringify(result),
        new Date(event.scannedAt || Date.now())
      ]
    );
  }

  async markCommandsSent(commands) {
    const ids = (commands || []).map((command) => command.id).filter(Boolean);
    if (ids.length === 0) {
      return;
    }
    const placeholders = ids.map(() => "?").join(",");
    await this.query(
      `UPDATE device_commands
      SET status = 'sent', sent_at = NOW()
      WHERE id IN (${placeholders}) AND status = 'queued'`,
      ids
    );
  }

  async acknowledgeCommand(serial, commandId, result) {
    await this.query(
      `UPDATE device_commands
      SET status = ?, ack_at = NOW()
      WHERE id = ? AND serial = ?`,
      [result ? "done" : "failed", Number(commandId), serial]
    );
  }

  async enqueueCommand(serial, name, payload) {
    await this.ensureDevice(serial);
    const rows = await this.query(
      `INSERT INTO device_commands (serial, name, payload_json, status, created_at)
      VALUES (?, ?, ?, 'queued', NOW())`,
      [serial, name || "command", JSON.stringify(payload || {})]
    );
    return rows.insertId;
  }

  async listDevices() {
    return this.query(
      `SELECT serial, ip, mac, agent_version AS agentVersion, online, location_name AS locationName,
        last_seen_at AS lastSeenAt
      FROM devices
      ORDER BY serial`,
      []
    );
  }
}

function parseJsonColumn(value) {
  if (!value || typeof value === "object") {
    return value || {};
  }
  try {
    return JSON.parse(value);
  } catch (error) {
    return { raw: value };
  }
}

module.exports = {
  MySqlRepository
};
