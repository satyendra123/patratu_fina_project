function enqueueCommand({ repository, clients, wsController }) {
  return async (request, response, next) => {
    try {
      const body = request.body || {};
      if (!body.serial) {
        response.status(400).json({ ok: false, error: "serial is required" });
        return;
      }
      const commandId = await repository.enqueueCommand(body.serial, body.name, body.payload || {});
      const client = clients.get(body.serial);
      if (client) {
        await wsController.sendSnapshotAndCommands(client, body.serial);
      }
      response.json({ ok: true, commandId });
    } catch (error) {
      next(error);
    }
  };
}

module.exports = {
  enqueueCommand
};
