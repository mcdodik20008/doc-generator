import { test as base, expect } from '@playwright/test';
import * as http from 'http';
import * as fs from 'fs';
import * as path from 'path';

const PROJECT_ROOT = path.resolve(__dirname, '..', '..');

const TEMPLATES_DIR = path.join(PROJECT_ROOT, 'src', 'main', 'resources', 'templates');
const STATIC_DIR = path.join(PROJECT_ROOT, 'src', 'main', 'resources', 'static');

const MIME_TYPES: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.json': 'application/json',
};

/** Map URL paths to template files */
const PAGE_ROUTES: Record<string, string> = {
  '/': 'dashboard.html',
  '/ingest': 'ingest.html',
  '/graph': 'graph.html',
  '/chat': 'chat.html',
  '/health': 'health.html',
};

function createServer(): http.Server {
  return http.createServer((req, res) => {
    const url = new URL(req.url || '/', `http://${req.headers.host}`);
    const pathname = url.pathname;

    // Serve page routes from templates
    const templateFile = PAGE_ROUTES[pathname];
    if (templateFile) {
      const filePath = path.join(TEMPLATES_DIR, templateFile);
      return serveFile(filePath, '.html', res);
    }

    // Serve static assets: /css/*, /js/*, /favicon.svg
    if (pathname.startsWith('/css/') || pathname.startsWith('/js/') || pathname === '/favicon.svg') {
      const filePath = path.join(STATIC_DIR, pathname);
      const ext = path.extname(filePath);
      return serveFile(filePath, ext, res);
    }

    // Everything else (including /api/*) returns 404 — API is mocked via page.route()
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('Not found');
  });
}

function serveFile(filePath: string, ext: string, res: http.ServerResponse) {
  try {
    const content = fs.readFileSync(filePath);
    const mime = MIME_TYPES[ext] || 'application/octet-stream';
    res.writeHead(200, { 'Content-Type': mime });
    res.end(content);
  } catch {
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('Not found');
  }
}

type ServeAppFixture = {
  appURL: string;
};

export const test = base.extend<ServeAppFixture>({
  appURL: [async ({}, use) => {
    const server = createServer();

    await new Promise<void>((resolve) => {
      server.listen(0, '127.0.0.1', () => resolve());
    });

    const addr = server.address() as { port: number };
    const url = `http://127.0.0.1:${addr.port}`;

    await use(url);

    server.close();
  }, { scope: 'worker' }],
});

export { expect };
