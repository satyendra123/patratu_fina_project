function normalizeQrCode(raw) {
  const value = String(raw || "").trim();
  if (!value) {
    return "";
  }
  try {
    const parsed = JSON.parse(value);
    if (typeof parsed === "string" || typeof parsed === "number") {
      return String(parsed).trim();
    }
    return String(parsed.enrollId || parsed.enrollid || parsed.id || parsed.userCode || parsed.code || "").trim();
  } catch (error) {
    return value.replace(/^ID[:=]/i, "").trim();
  }
}

module.exports = {
  normalizeQrCode
};
