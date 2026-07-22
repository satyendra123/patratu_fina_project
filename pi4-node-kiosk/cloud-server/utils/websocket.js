const { EventEmitter } = require("events");
const { URL } = require("url");
const { createAcceptKey, decodeFrames, encodeFrame } = require("./ws-frame");

function createConnection(socket) {
  const connection = new EventEmitter();
  connection.socket = socket;
  connection.isOpen = true;
  connection.remoteAddress = socket.remoteAddress;
  connection.buffer = Buffer.alloc(0);

  connection.send = (message) => {
    if (!connection.isOpen) {
      return false;
    }
    const payload = typeof message === "string" ? message : JSON.stringify(message);
    socket.write(encodeFrame(payload));
    return true;
  };

  connection.close = () => {
    if (!connection.isOpen) {
      return;
    }
    connection.isOpen = false;
    try {
      socket.write(encodeFrame("", { opcode: 0x8 }));
    } catch (error) {
      // Socket may already be closed.
    }
    socket.end();
  };

  connection._handleData = (chunk) => {
    connection.buffer = Buffer.concat([connection.buffer, chunk]);
    const decoded = decodeFrames(connection.buffer);
    connection.buffer = decoded.remaining;

    for (const frame of decoded.frames) {
      if (frame.opcode === 0x1) {
        connection.emit("message", frame.text);
      } else if (frame.opcode === 0x8) {
        connection.close();
      } else if (frame.opcode === 0x9) {
        socket.write(encodeFrame(frame.payload, { opcode: 0xA }));
      } else if (frame.opcode === 0xA) {
        connection.emit("pong");
      }
    }
  };

  socket.on("data", connection._handleData);
  socket.on("close", () => {
    if (connection.isOpen) {
      connection.isOpen = false;
      connection.emit("close");
    }
  });
  socket.on("error", (error) => connection.emit("error", error));
  return connection;
}

function addWebSocketRoute(server, routePath, onConnection) {
  server.on("upgrade", (request, socket) => {
    const pathname = new URL(request.url, "http://localhost").pathname;
    if (pathname !== routePath) {
      socket.destroy();
      return;
    }

    const key = request.headers["sec-websocket-key"];
    if (!key) {
      socket.destroy();
      return;
    }

    socket.write([
      "HTTP/1.1 101 Switching Protocols",
      "Upgrade: websocket",
      "Connection: Upgrade",
      `Sec-WebSocket-Accept: ${createAcceptKey(key)}`,
      "",
      ""
    ].join("\r\n"));

    onConnection(createConnection(socket), request);
  });
}

module.exports = {
  addWebSocketRoute
};
