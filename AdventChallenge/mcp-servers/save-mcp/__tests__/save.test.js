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

describe('save-mcp', { timeout: 15000 }, () => {
  let client;

  it('tools/list returns save-to-file tool with correct schema', async () => {
    client = createMcpClient(SERVER_DIR);
    try {
      await client.initialize();
      client.send({ jsonrpc: '2.0', id: 2, method: 'tools/list', params: {} });
      const resp = await client.waitResponse();
      assert.ok(resp.result, 'Response should have result');
      assert.ok(Array.isArray(resp.result.tools), 'tools should be an array');
      const saveTool = resp.result.tools.find((t) => t.name === 'save-to-file');
      assert.ok(saveTool, 'save-to-file tool should exist');
      assert.ok(
        saveTool.inputSchema?.properties?.content,
        'inputSchema should have content property'
      );
      assert.ok(
        saveTool.inputSchema?.properties?.filename,
        'inputSchema should have filename property'
      );
    } finally {
      client.close();
    }
  });

  it('initialize returns correct server info', async () => {
    client = createMcpClient(SERVER_DIR);
    try {
      const resp = await client.initialize();
      assert.strictEqual(resp.result?.serverInfo?.name, 'save-mcp');
    } finally {
      client.close();
    }
  });

  it('save-to-file creates file with content', async () => {
    client = createMcpClient(SERVER_DIR);
    try {
      await client.initialize();
      client.send({
        jsonrpc: '2.0',
        id: 3,
        method: 'tools/call',
        params: {
          name: 'save-to-file',
          arguments: {
            content: 'Hello test content',
            filename: 'test-save-output.txt'
          }
        }
      });
      const resp = await client.waitResponse();
      assert.ok(resp.result, 'Response should have result');
      assert.ok(!resp.result.isError, 'should not be error');
      const textContent = resp.result.content?.find((c) => c.type === 'text')?.text;
      assert.ok(textContent, 'should have text content');
      const parsed = JSON.parse(textContent);
      assert.ok(parsed.savedTo, 'result should have savedTo');
      assert.ok(typeof parsed.sizeBytes === 'number', 'result should have sizeBytes');
    } finally {
      client.close();
    }
  });

  it('save-to-file with empty content returns error', async () => {
    client = createMcpClient(SERVER_DIR);
    try {
      await client.initialize();
      client.send({
        jsonrpc: '2.0',
        id: 4,
        method: 'tools/call',
        params: {
          name: 'save-to-file',
          arguments: { content: '' }
        }
      });
      const resp = await client.waitResponse();
      assert.ok(resp.result, 'Response should have result');
      assert.strictEqual(resp.result.isError, true, 'isError should be true');
    } finally {
      client.close();
    }
  });

  it('save-to-file generates filename when not provided', async () => {
    client = createMcpClient(SERVER_DIR);
    try {
      await client.initialize();
      client.send({
        jsonrpc: '2.0',
        id: 5,
        method: 'tools/call',
        params: {
          name: 'save-to-file',
          arguments: { content: 'Auto filename test' }
        }
      });
      const resp = await client.waitResponse();
      assert.ok(resp.result, 'Response should have result');
      assert.ok(!resp.result.isError, 'should not be error');
      const textContent = resp.result.content?.find((c) => c.type === 'text')?.text;
      const parsed = JSON.parse(textContent);
      assert.ok(parsed.savedTo, 'should have savedTo path');
      assert.ok(
        /result-\d+\.txt$/.test(parsed.savedTo),
        'savedTo should have auto-generated result-NNNNNNNNNN.txt filename'
      );
    } finally {
      client.close();
    }
  });

  it('save-to-file uses provided filename', async () => {
    client = createMcpClient(SERVER_DIR);
    try {
      await client.initialize();
      const customFilename = 'my-custom-file-123.txt';
      client.send({
        jsonrpc: '2.0',
        id: 6,
        method: 'tools/call',
        params: {
          name: 'save-to-file',
          arguments: {
            content: 'Custom filename content',
            filename: customFilename
          }
        }
      });
      const resp = await client.waitResponse();
      assert.ok(resp.result, 'Response should have result');
      assert.ok(!resp.result.isError, 'should not be error');
      const textContent = resp.result.content?.find((c) => c.type === 'text')?.text;
      const parsed = JSON.parse(textContent);
      assert.ok(parsed.savedTo.endsWith(customFilename), 'savedTo should end with provided filename');
    } finally {
      client.close();
    }
  });
});
