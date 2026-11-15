const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const workspace = path.resolve(__dirname, '..', '..');
const apps = [
  { name: 'Admin', directory: 'Valor-Admin-Portal', port: 3000 },
  { name: 'Customer', directory: 'Valor-Android-APP', port: 3001 },
  { name: 'Technician', directory: 'Valor-technician', port: 3002 },
];
const types = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.ico': 'image/x-icon',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.txt': 'text/plain; charset=utf-8',
  '.webp': 'image/webp',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
};

for (const app of apps) {
  const dist = path.join(workspace, app.directory, 'dist');
  if (!fs.existsSync(path.join(dist, 'index.html'))) {
    throw new Error(`${app.name} web build is missing. Run build-local-web.ps1 first.`);
  }
  http.createServer((request, response) => {
    let pathname;
    try { pathname = decodeURIComponent(new URL(request.url, 'http://127.0.0.1').pathname); }
    catch { response.writeHead(400).end(); return; }
    const candidate = path.resolve(dist, `.${pathname}`);
    const relative = path.relative(dist, candidate);
    if (relative.startsWith('..') || path.isAbsolute(relative)) {
      response.writeHead(403).end(); return;
    }
    let file = candidate;
    try {
      if (!fs.statSync(file).isFile()) file = path.join(dist, 'index.html');
    } catch {
      if (path.extname(file)) { response.writeHead(404).end(); return; }
      file = path.join(dist, 'index.html');
    }
    response.writeHead(200, {
      'Content-Type': types[path.extname(file).toLowerCase()] || 'application/octet-stream',
      'Cache-Control': file.endsWith('index.html') ? 'no-store' : 'public, max-age=3600',
    });
    fs.createReadStream(file).pipe(response);
  }).listen(app.port, '127.0.0.1', () => console.log(`${app.name}: http://127.0.0.1:${app.port}`));
}
