import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const SERVER_DIR = join(__dirname, '..');

function createMcpClient(serverDir) {
  const proc = spawn('node', ['dist/index.js'], {
    cwd: serverDir,
    stdio: ['pipe', 'pipe', 'pipe']
  });
  let buffer = '';
  const responses = [];
  let resolveNext = null;

  proc.stdout.on('data', (data) => {
    buffer += data.toString();
    const lines = buffer.split('\n');
    buffer = lines.pop() || '';
    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed) continue;
      try {
        const parsed = JSON.parse(trimmed);
        if (resolveNext) {
          const r = resolveNext;
          resolveNext = null;
          r(parsed);
        } else {
          responses.push(parsed);
        }
      } catch {}
    }
  });

  return {
    send(msg) {
      proc.stdin.write(JSON.stringify(msg) + '\n');
    },
    async waitResponse() {
      if (responses.length > 0) return responses.shift();
      return new Promise((resolve) => {
        resolveNext = resolve;
      });
    },
    async initialize() {
      this.send({
        jsonrpc: '2.0',
        id: 1,
        method: 'initialize',
        params: {
          protocolVersion: '2024-11-05',
          clientInfo: { name: 'test', version: '1.0.0' },
          capabilities: {}
        }
      });
      const resp = await this.waitResponse();
      this.send({ jsonrpc: '2.0', method: 'notifications/initialized', params: {} });
      return resp;
    },
    close() {
      proc.stdin.end();
      proc.kill();
    }
  };
}

describe('search-mcp', { timeout: 15000 }, () => {
  let client;

  it('tools/list returns search tool with correct schema', async () => {
    client = createMcpClient(SERVER_DIR);
    try {
      await client.initialize();
      client.send({ jsonrpc: '2.0', id: 2, method: 'tools/list', params: {} });
      const resp = await client.waitResponse();
      assert.ok(resp.result, 'Response should have result');
      assert.ok(Array.isArray(resp.result.tools), 'tools should be an array');
      const searchTool = resp.result.tools.find((t) => t.name === 'search');
      assert.ok(searchTool, 'search tool should exist');
      assert.ok(
        searchTool.inputSchema?.properties?.query,
        'inputSchema should have query property'
      );
    } finally {
      client.close();
    }
  });

  it('initialize returns correct server info', async () => {
    client = createMcpClient(SERVER_DIR);
    try {
      const resp = await client.initialize();
      assert.strictEqual(resp.result?.serverInfo?.name, 'search-mcp');
    } finally {
      client.close();
    }
  });

  it('search with valid query returns content', async () => {
    client = createMcpClient(SERVER_DIR);
    try {
      await client.initialize();
      client.send({
        jsonrpc: '2.0',
        id: 3,
        method: 'tools/call',
        params: { name: 'search', arguments: { query: 'Python' } }
      });
      const resp = await client.waitResponse();
      assert.ok(resp.result, 'Response should have result');
      assert.ok(Array.isArray(resp.result.content), 'content should be an array');
      assert.ok(
        resp.result.content.some((c) => c.type === 'text' && c.text),
        'content should have text'
      );
      assert.ok(!resp.result.isError, 'should not be error');
    } finally {
      client.close();
    }
  });

  it('search with empty query returns error', async () => {
    client = createMcpClient(SERVER_DIR);
    try {
      await client.initialize();
      client.send({
        jsonrpc: '2.0',
        id: 4,
        method: 'tools/call',
        params: { name: 'search', arguments: { query: '' } }
      });
      const resp = await client.waitResponse();
      assert.ok(resp.result, 'Response should have result');
      assert.strictEqual(resp.result.isError, true, 'isError should be true');
    } finally {
      client.close();
    }
  });
});
