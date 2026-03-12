import { createInterface } from "node:readline";
import { stdin, stdout, stderr } from "node:process";
import { mkdirSync, writeFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { homedir } from "node:os";

type JsonRpcId = number | string | null;

type JsonRpcRequest = {
  jsonrpc?: string;
  id?: JsonRpcId;
  method?: string;
  params?: unknown;
};

const RESULTS_DIR = join(homedir(), ".bothubclient", "pipeline-results");

const TOOLS = [
  {
    name: "save-to-file",
    description:
      "Save text content to a local file. Returns the absolute path and file size.",
    inputSchema: {
      type: "object",
      properties: {
        content: {
          type: "string",
          description: "The text content to save to file",
        },
        filename: {
          type: "string",
          description: "Optional filename. If not provided, a timestamped name is generated.",
        },
      },
      required: ["content"],
      additionalProperties: false,
    },
    annotations: {
      readOnlyHint: false,
      openWorldHint: false,
      destructiveHint: false,
    },
  },
] as const;

function writeJson(message: Record<string, unknown>) {
  stdout.write(`${JSON.stringify(message)}\n`);
}

function logError(message: string) {
  stderr.write(`[save-mcp] ${message}\n`);
}

function sendResult(id: JsonRpcId, result: Record<string, unknown>) {
  writeJson({ jsonrpc: "2.0", id, result });
}

function sendError(id: JsonRpcId, code: number, message: string) {
  writeJson({ jsonrpc: "2.0", id, error: { code, message } });
}

function errorContent(text: string) {
  return { content: [{ type: "text", text }], isError: true };
}

function successContent(text: string) {
  return { content: [{ type: "text", text }] };
}

function callSaveToFile(rawArguments: unknown) {
  const args = (rawArguments ?? {}) as Record<string, unknown>;
  const content = typeof args.content === "string" ? args.content : "";
  const filename = typeof args.filename === "string" && args.filename.trim()
    ? args.filename.trim()
    : `result-${Date.now()}.txt`;

  if (!content) {
    return errorContent("Content parameter is required and must be non-empty");
  }

  try {
    mkdirSync(RESULTS_DIR, { recursive: true });
    const filePath = join(RESULTS_DIR, filename);
    writeFileSync(filePath, content, "utf-8");
    const stats = statSync(filePath);

    const result = {
      savedTo: filePath,
      sizeBytes: stats.size,
    };
    return successContent(JSON.stringify(result));
  } catch (err) {
    return errorContent(`Failed to save file: ${err instanceof Error ? err.message : "Unknown error"}`);
  }
}

async function handleToolsCall(id: JsonRpcId, params: unknown) {
  const p = (params ?? {}) as Record<string, unknown>;
  const name = typeof p.name === "string" ? p.name : "";

  if (name === "save-to-file") {
    const result = callSaveToFile(p.arguments);
    sendResult(id, result);
    return;
  }

  sendError(id, -32601, `Unknown tool: ${name}`);
}

async function handleRequest(request: JsonRpcRequest) {
  const method = request.method;
  const hasId = Object.prototype.hasOwnProperty.call(request, "id");
  const id = hasId ? (request.id ?? null) : null;

  if (!method) {
    if (hasId) sendError(id, -32600, "Invalid Request: method is required");
    return;
  }

  if (method === "notifications/initialized") return;
  if (!hasId) return;

  if (method === "initialize") {
    sendResult(id, {
      protocolVersion: "2024-11-05",
      capabilities: { tools: { listChanged: false } },
      serverInfo: {
        name: "save-mcp",
        version: "1.0.0",
        description: "MCP server for saving text to files",
        title: "Save MCP Server",
      },
    });
    return;
  }

  if (method === "tools/list") {
    sendResult(id, { tools: TOOLS });
    return;
  }

  if (method === "tools/call") {
    await handleToolsCall(id, request.params);
    return;
  }

  sendError(id, -32601, `Method not found: ${method}`);
}

const rl = createInterface({ input: stdin, crlfDelay: Infinity });

rl.on("line", (line) => {
  const trimmed = line.trim();
  if (!trimmed) return;

  let parsed: JsonRpcRequest;
  try {
    parsed = JSON.parse(trimmed) as JsonRpcRequest;
  } catch {
    logError(`Invalid JSON received: ${trimmed.slice(0, 120)}`);
    return;
  }

  handleRequest(parsed).catch((err) => {
    const id = Object.prototype.hasOwnProperty.call(parsed, "id") ? (parsed.id ?? null) : null;
    sendError(id, -32603, `Internal error: ${err instanceof Error ? err.message : "unknown"}`);
  });
});

rl.on("close", () => {
  process.exit(0);
});

stdin.resume();
