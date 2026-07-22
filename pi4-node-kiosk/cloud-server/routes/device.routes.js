const express = require("express");
const { listDevices } = require("../controllers/device.controller");

module.exports = function deviceRoutes(deps) {
  const router = express.Router();
  router.get("/", listDevices(deps));
  return router;
};
