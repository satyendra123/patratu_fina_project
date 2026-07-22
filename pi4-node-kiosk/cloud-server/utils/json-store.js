const fs = require("fs");
const path = require("path");

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function readJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch (error) {
    if (error && error.code === "ENOENT") {
      return clone(fallback);
    }
    throw error;
  }
}

function writeJsonAtomic(file, value) {
  ensureDir(path.dirname(file));
  const tmp = `${file}.${process.pid}.tmp`;
  fs.writeFileSync(tmp, `${JSON.stringify(value, null, 2)}\n`, "utf8");
  fs.renameSync(tmp, file);
}

class JsonStore {
  constructor(file, defaults) {
    this.file = path.resolve(file);
    this.defaults = clone(defaults);
    this.data = readJson(this.file, this.defaults);
  }

  get() {
    return this.data;
  }

  save() {
    writeJsonAtomic(this.file, this.data);
  }

  update(mutator) {
    const result = mutator(this.data);
    this.save();
    return result;
  }
}

module.exports = {
  JsonStore
};
