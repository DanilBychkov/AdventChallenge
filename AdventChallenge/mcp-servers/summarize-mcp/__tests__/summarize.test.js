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

describe('summarize-mcp', { timeout: 15000 }, () => {
  let client;

  it('tools/list returns summarize tool with correct schema', async () => {
    client = createMcpClient(SERVER_DIR);
    try {
      await client.initialize();
      client.send({ jsonrpc: '2.0', id: 2, method: 'tools/list', params: {} });
      const resp = await client.waitResponse();
      assert.ok(resp.result, 'Response should have result');
      assert.ok(Array.isArray(resp.result.tools), 'tools should be an array');
      const summarizeTool = resp.result.tools.find((t) => t.name === 'summarize');
      assert.ok(summarizeTool, 'summarize tool should exist');
      assert.ok(
        summarizeTool.inputSchema?.properties?.text,
        'inputSchema should have text property'
      );
      assert.ok(
        summarizeTool.inputSchema?.properties?.maxSentences,
        'inputSchema should have maxSentences property'
      );
    } finally {
      client.close();
    }
  });

  it('initialize returns correct server info', async () => {
    client = createMcpClient(SERVER_DIR);
    try {
      const resp = await client.initialize();
      assert.strictEqual(resp.result?.serverInfo?.name, 'summarize-mcp');
    } finally {
      client.close();
    }
  });

  it('summarize extracts first 3 sentences by default', async () => {
    client = createMcpClient(SERVER_DIR);
    try {
      await client.initialize();
      const text =
        'Sentence one. Sentence two. Sentence three. Sentence four.';
      client.send({
        jsonrpc: '2.0',
        id: 3,
        method: 'tools/call',
        params: { name: 'summarize', arguments: { text } }
      });
      const resp = await client.waitResponse();
      assert.ok(resp.result, 'Response should have result');
      assert.ok(!resp.result.isError, 'should not be error');
      const textContent = resp.result.content?.find((c) => c.type === 'text')?.text;
      assert.ok(textContent, 'should have text content');
      const sentences = textContent.split(/[.!?]+/).filter((s) => s.trim());
      assert.ok(sentences.length <= 3, 'should have at most 3 sentences');
    } finally {
      client.close();
    }
  });

  it('summarize with custom maxSentences', async () => {
    client = createMcpClient(SERVER_DIR);
    try {
      await client.initialize();
      const text =
        'First sentence. Second sentence. Third sentence. Fourth sentence.';
      client.send({
        jsonrpc: '2.0',
        id: 4,
        method: 'tools/call',
        params: {
          name: 'summarize',
          arguments: { text, maxSentences: 2 }
        }
      });
      const resp = await client.waitResponse();
      assert.ok(resp.result, 'Response should have result');
      assert.ok(!resp.result.isError, 'should not be error');
      const textContent = resp.result.content?.find((c) => c.type === 'text')?.text;
      assert.ok(textContent, 'should have text content');
      const sentences = textContent.split(/[.!?]+/).filter((s) => s.trim());
      assert.ok(sentences.length <= 2, 'should have at most 2 sentences');
    } finally {
      client.close();
    }
  });

  it('summarize with empty text returns error', async () => {
    client = createMcpClient(SERVER_DIR);
    try {
      await client.initialize();
      client.send({
        jsonrpc: '2.0',
        id: 5,
        method: 'tools/call',
        params: { name: 'summarize', arguments: { text: '' } }
      });
      const resp = await client.waitResponse();
      assert.ok(resp.result, 'Response should have result');
      assert.strictEqual(resp.result.isError, true, 'isError should be true');
    } finally {
      client.close();
    }
  });

  it('summarize handles text without sentence boundaries', async () => {
    client = createMcpClient(SERVER_DIR);
    try {
      await client.initialize();
      const text = 'No periods here just words';
      client.send({
        jsonrpc: '2.0',
        id: 6,
        method: 'tools/call',
        params: { name: 'summarize', arguments: { text } }
      });
      const resp = await client.waitResponse();
      assert.ok(resp.result, 'Response should have result');
      assert.ok(!resp.result.isError, 'should not be error');
      const textContent = resp.result.content?.find((c) => c.type === 'text')?.text;
      assert.ok(textContent !== undefined, 'should return something');
    } finally {
      client.close();
    }
  });
});
