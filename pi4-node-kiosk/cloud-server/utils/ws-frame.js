const crypto = require("crypto");

function encodeFrame(data, options = {}) {
  const payload = Buffer.isBuffer(data) ? data : Buffer.from(String(data), "utf8");
  const opcode = options.opcode || 0x1;
  const frame = Buffer.alloc(payload.length < 126 ? 2 + payload.length : 4 + payload.length);
  let offset = 0;

  frame[offset++] = 0x80 | opcode;
  if (payload.length < 126) {
    frame[offset++] = payload.length;
  } else {
    frame[offset++] = 126;
    frame.writeUInt16BE(payload.length, offset);
    offset += 2;
  }
  payload.copy(frame, offset);
  return frame;
}

function decodeFrames(buffer) {
  const frames = [];
  let offset = 0;

  while (offset + 2 <= buffer.length) {
    const first = buffer[offset];
    const second = buffer[offset + 1];
    const opcode = first & 0x0f;
    const masked = (second & 0x80) === 0x80;
    let payloadLength = second & 0x7f;
    let headerLength = 2;

    if (payloadLength === 126) {
      if (offset + 4 > buffer.length) {
        break;
      }
      payloadLength = buffer.readUInt16BE(offset + 2);
      headerLength += 2;
    } else if (payloadLength === 127) {
      if (offset + 10 > buffer.length) {
        break;
      }
      const high = buffer.readUInt32BE(offset + 2);
      const low = buffer.readUInt32BE(offset + 6);
      if (high !== 0) {
        throw new Error("WebSocket payload too large.");
      }
      payloadLength = low;
      headerLength += 8;
    }

    const maskLength = masked ? 4 : 0;
    const frameLength = headerLength + maskLength + payloadLength;
    if (offset + frameLength > buffer.length) {
      break;
    }

    let payload = Buffer.from(buffer.subarray(offset + headerLength + maskLength, offset + frameLength));
    if (masked) {
      const maskKey = buffer.subarray(offset + headerLength, offset + headerLength + 4);
      for (let index = 0; index < payload.length; index += 1) {
        payload[index] = payload[index] ^ maskKey[index % 4];
      }
    }

    frames.push({
      opcode,
      text: payload.toString("utf8"),
      payload
    });
    offset += frameLength;
  }

  return {
    frames,
    remaining: buffer.subarray(offset)
  };
}

function createAcceptKey(clientKey) {
  return crypto
    .createHash("sha1")
    .update(`${clientKey}258EAFA5-E914-47DA-95CA-C5AB0DC85B11`)
    .digest("base64");
}

module.exports = {
  createAcceptKey,
  decodeFrames,
  encodeFrame
};
