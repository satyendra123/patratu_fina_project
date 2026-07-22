const http = require("http");
const express = require("express");
const registerRoutes = require("./routes");
const { loadCloudConfig } = require("./config");
const { createRepository } = require("./models");
const { createDeviceWebSocketController } = require("./controllers/websocket.controller");

function createApp(deps) {
  const app = express();

  app.use((request, response, next) => {
    response.setHeader("access-control-allow-origin", "*");
    response.setHeader("access-control-allow-methods", "GET,POST,OPTIONS");
    response.setHeader("access-control-allow-headers", "content-type");
    if (request.method === "OPTIONS") {
      response.status(204).end();
      return;
    }
    next();
  });

  app.use(express.json({ limit: "1mb" }));

  registerRoutes(app, deps);

  app.use((request, response) => {
    response.status(404).json({ ok: false, error: "Not found" });
  });

  app.use((error, request, response, next) => {
    if (response.headersSent) {
      next(error);
      return;
    }
    response.status(500).json({ ok: false, error: error.message });
  });

  return app;
}

const config = loadCloudConfig();
const repository = createRepository(config);
const clients = new Map();
const wsController = createDeviceWebSocketController({ config, repository, clients });
const app = createApp({ config, repository, clients, wsController });
const server = http.createServer(app);

wsController.attach(server);

server.listen(config.port, "0.0.0.0", () => {
  console.log(`Pi4 cloud sync server listening on http://0.0.0.0:${config.port}`);
  console.log(`Device WebSocket path: ${config.wsPath}`);
  console.log(`Database driver: ${config.database.driver}`);
});
