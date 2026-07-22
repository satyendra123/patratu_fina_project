const express = require("express");
const { enqueueCommand } = require("../controllers/command.controller");

module.exports = function commandRoutes(deps) {
  const router = express.Router();
  router.post("/", enqueueCommand(deps));
  return router;
};
