function listDevices({ repository }) {
  return async (request, response, next) => {
    try {
      response.json({
        devices: await repository.listDevices()
      });
    } catch (error) {
      next(error);
    }
  };
}

module.exports = {
  listDevices
};
