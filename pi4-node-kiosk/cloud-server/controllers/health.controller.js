function getHealth({ config, clients }) {
  return (request, response) => {
    response.json({
      ok: true,
      companyName: config.companyName,
      connectedDevices: clients.size,
      databaseDriver: config.database.driver
    });
  };
}

module.exports = {
  getHealth
};
