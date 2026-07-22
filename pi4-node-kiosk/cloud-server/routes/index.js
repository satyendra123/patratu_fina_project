const healthRoutes = require("./health.routes");
const deviceRoutes = require("./device.routes");
const commandRoutes = require("./command.routes");

function registerRoutes(app, deps) {
  app.use("/api/health", healthRoutes(deps));
  app.use("/api/devices", deviceRoutes(deps));
  app.use("/api/commands", commandRoutes(deps));
}

module.exports = registerRoutes;
