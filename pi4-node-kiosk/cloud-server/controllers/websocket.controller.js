const { addWebSocketRoute } = require("../utils/websocket");

function createDeviceWebSocketController({ config, repository, clients }) {
  async function handleDeviceMessage(connection, message) {
    if (message.type === "device.hello") {
      const serial = String(message.serial || "").trim();
      if (!serial) {
        throw new Error("device.hello requires serial");
      }
      connection.serial = serial;
      clients.set(serial, connection);
      await repository.registerDevice({
        serial,
        ip: message.ip,
        mac: message.mac,
        agentVersion: message.agentVersion,
        inventory: message.inventory || {}
      });
      connection.send({
        type: "server.hello",
        companyName: config.companyName,
        serverTime: new Date().toISOString()
      });
      await sendSnapshotAndCommands(connection, serial);
      return;
    }

    if (!connection.serial) {
      throw new Error("Device must send device.hello first");
    }

    if (message.type === "heartbeat") {
      await repository.updateDeviceStatus(connection.serial, true);
      connection.send({ type: "heartbeat.ack", serverTime: new Date().toISOString() });
      return;
    }

    if (message.type === "sync.request") {
      await sendSnapshotAndCommands(connection, connection.serial);
      return;
    }

    if (message.type === "scan.event") {
      const result = await repository.validateQr(message.code, connection.serial);
      const event = {
        scanId: message.scanId,
        serial: connection.serial,
        code: message.code,
        rawCode: message.rawCode,
        localResult: message.localResult || null,
        result,
        inout: message.inout || "IN",
        scannedAt: message.scannedAt || new Date().toISOString()
      };
      await repository.saveScanEvent(event);
      connection.send({
        type: "scan.result",
        scanId: message.scanId,
        result
      });
      return;
    }

    if (message.type === "scan.batch") {
      let saved = 0;
      for (const event of message.events || []) {
        const result = event.result && event.result.valid
          ? event.result
          : await repository.validateQr(event.code, connection.serial);
        await repository.saveScanEvent({
          ...event,
          serial: connection.serial,
          result
        });
        saved += 1;
      }
      connection.send({
        type: "scan.batch.ack",
        scanIds: (message.events || []).map((event) => event.scanId),
        saved
      });
      return;
    }

    if (message.type === "command.ack") {
      await repository.acknowledgeCommand(connection.serial, message.commandId, message.result !== false);
      return;
    }

    throw new Error(`Unknown message type: ${message.type}`);
  }

  async function sendSnapshotAndCommands(connection, serial) {
    const snapshot = await repository.getSnapshot(serial);
    connection.send({
      type: "sync.snapshot",
      ...snapshot
    });
    if (snapshot.commands && snapshot.commands.length > 0) {
      connection.send({
        type: "command.batch",
        commands: snapshot.commands
      });
      await repository.markCommandsSent(snapshot.commands);
    }
  }

  function attach(server) {
    addWebSocketRoute(server, config.wsPath, (connection) => {
      connection.on("message", async (raw) => {
        try {
          const message = JSON.parse(raw);
          await handleDeviceMessage(connection, message);
        } catch (error) {
          connection.send({
            type: "error",
            message: error.message
          });
        }
      });

      connection.on("close", async () => {
        if (connection.serial) {
          clients.delete(connection.serial);
          try {
            await repository.updateDeviceStatus(connection.serial, false);
          } catch (error) {
            console.error("Failed to mark device offline:", error.message);
          }
        }
      });
    });
  }

  return {
    attach,
    sendSnapshotAndCommands
  };
}

module.exports = {
  createDeviceWebSocketController
};
