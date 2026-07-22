const express = require("express");
const { getHealth } = require("../controllers/health.controller");

module.exports = function healthRoutes(deps) {
  const router = express.Router();
  router.get("/", getHealth(deps));
  return router;
};
