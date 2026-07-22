const path = require("path");
const { loadEnvFile } = require("../utils/env");

loadEnvFile(path.join(__dirname, "..", "..", ".env"));

function loadCloudConfig() {
  return {
    companyName: process.env.COMPANY_NAME || "YAMUNA FINA PROJECT",
    port: Number(process.env.CLOUD_HTTP_PORT || 8088),
    wsPath: process.env.CLOUD_WS_PATH || "/ws/device",
    database: {
      driver: process.env.DB_DRIVER || "mysql",
      jsonFile: path.resolve(process.env.JSON_DB_FILE || path.join(__dirname, "..", "data", "cloud-db.json")),
      mysql: {
        host: process.env.MYSQL_HOST || "127.0.0.1",
        port: Number(process.env.MYSQL_PORT || 3306),
        database: process.env.MYSQL_DATABASE || "pi4_kiosk",
        user: process.env.MYSQL_USER || "pi4_user",
        password: process.env.MYSQL_PASSWORD || "",
        waitForConnections: true,
        connectionLimit: Number(process.env.MYSQL_CONNECTION_LIMIT || 10),
        namedPlaceholders: true,
        dateStrings: false
      }
    }
  };
}

module.exports = {
  loadCloudConfig
};
