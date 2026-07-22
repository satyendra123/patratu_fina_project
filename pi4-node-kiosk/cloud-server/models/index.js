const { JsonRepository } = require("./json.model");
const { MySqlRepository } = require("./mysql.model");

function createRepository(config) {
  const driver = String(config.database.driver || "json").toLowerCase();
  if (driver === "mysql") {
    return new MySqlRepository(config.database.mysql);
  }
  return new JsonRepository(config.database.jsonFile);
}

module.exports = {
  createRepository
};
