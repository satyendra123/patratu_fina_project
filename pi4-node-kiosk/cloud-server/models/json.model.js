const { JsonStore } = require("../utils/json-store");
const { normalizeQrCode } = require("./qr-code");

function nowIso() {
  return new Date().toISOString();
}

class JsonRepository {
  constructor(file) {
    this.store = new JsonStore(file, {
      devices: {},
      users: [
        {
          id: "1001",
          name: "Demo Manager",
          role: "manager",
          status: 1,
          updatedAt: nowIso(),
          version: 1
        },
        {
          id: "1002",
          name: "Demo Staff",
          role: "user",
          status: 1,
          updatedAt: nowIso(),
          version: 2
        }
      ],
      deletedUserIds: [],
      scans: [],
      commands: []
    });
  }

  async registerDevice(device) {
    const timestamp = nowIso();
    this.store.update((db) => {
      db.devices[device.serial] = {
        ...(db.devices[device.serial] || {}),
        ...device,
        online: true,
        lastSeenAt: timestamp
      };
    });
  }

  async updateDeviceStatus(serial, online) {
    this.store.update((db) => {
      db.devices[serial] = {
        ...(db.devices[serial] || { serial }),
        online,
        lastSeenAt: nowIso()
      };
    });
  }

  async getSnapshot(serial) {
    const db = this.store.get();
    return {
      full: true,
      serverCursor: Date.now(),
      users: db.users.filter((user) => !db.deletedUserIds.includes(String(user.id))),
      deletedUserIds: db.deletedUserIds,
      commands: db.commands.filter((command) => command.serial === serial && command.status === "queued")
    };
  }

  async validateQr(rawCode, serial) {
    const code = normalizeQrCode(rawCode);
    const db = this.store.get();
    const user = db.users.find((entry) => String(entry.id) === code);
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
            role: user.role || "user"
          }
        : null,
      message: valid ? "Valid access" : "Invalid or disabled QR",
      source: "cloud-json"
    };
  }

  async saveScanEvent(event) {
    this.store.update((db) => {
      db.scans.unshift({
        ...event,
        savedAt: nowIso()
      });
      db.scans = db.scans.slice(0, 10000);
    });
  }

  async markCommandsSent(commands) {
    const ids = new Set((commands || []).map((command) => command.id));
    if (ids.size === 0) {
      return;
    }
    this.store.update((db) => {
      for (const command of db.commands) {
        if (ids.has(command.id)) {
          command.status = "sent";
          command.sentAt = nowIso();
        }
      }
    });
  }

  async acknowledgeCommand(serial, commandId, result) {
    this.store.update((db) => {
      const command = db.commands.find((entry) => entry.serial === serial && entry.id === commandId);
      if (command) {
        command.status = result ? "done" : "failed";
        command.ackAt = nowIso();
      }
    });
  }

  async enqueueCommand(serial, name, payload) {
    const id = `cmd-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    this.store.update((db) => {
      db.commands.push({
        id,
        serial,
        name,
        payload,
        status: "queued",
        createdAt: nowIso()
      });
    });
    return id;
  }

  async listDevices() {
    return Object.values(this.store.get().devices);
  }
}

module.exports = {
  JsonRepository
};
