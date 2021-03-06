const process = require("process");
const { JSDOM } = require("jsdom");
const path = require("path");
const http = require("http");
const static = require('node-static');

const { dir, base: filename } = path.parse(process.argv[2]);

console.log(`serving: ${dir}`);

serveDirectory(dir).then(server => {
  const url = makeURL(server.address(), filename);

  console.log(`loading ${url}`);

  JSDOM.fromURL(url, {
    resources: "usable",
    runScripts: "dangerously",
  }).then(waitComplete)
    .finally(() => server.close());
}).catch(err => {
  console.error(err);
  process.exit(1);
});

function waitComplete(dom) {
  const boxes = dom.window.document.getElementsByClassName("test-box-header");

  const errors = [];
  let completed = 0;
  for (const box of boxes) {
    const cls = box.className;
    if (cls === "test-box-header error") {
      errors.push(box.innerHTML);
    } else if (cls === "test-box-header success") {
      completed++;
    } else if (cls === "test-box-header") {
      // pending
    } else {
      return Promise.reject(
          new Error(`unknown header class ${cls}. element:\n${box.innerHTML}`));
    }
  }

  if (errors.length !== 0) {
    errors.shift("tests failed:");
    return Promise.reject(new Error(errors.join("\n")));
  }

  const total = boxes.length;

  console.log(`${completed}/${total} tests`);

  if (total > 0 && completed === total) {
    return Promise.resolve(void 0);
  }

  return new Promise(res => setTimeout(res, 100))
    .then(() => waitComplete(dom));
}

function serveDirectory(dir) {
  const fileServer = new static.Server(dir);
  const server = http.createServer((req, res) => fileServer.serve(req, res));

  return new Promise((res, rej) => {
    server.listen(() => res(server));
    server.on("error", rej);
  });
}

function makeURL(serverAddress, filename) {
  const { port, address, family } = serverAddress;

  if (family === "IPv4")
    return `http://${address}:${port}/${filename}`

  if (family === "IPv6")
    return `http://[${address}]:${port}/${filename}`

  throw new Error(`do not know how to construct URL for address family ${family}`);
}
