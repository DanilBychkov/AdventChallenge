import { createInterface } from "node:readline";
import { stdin, stdout, stderr } from "node:process";

const HTTP_TIMEOUT_MS = 15_000;

type JsonRpcId = number | string | null;

type JsonRpcRequest = {
  jsonrpc?: string;
  id?: JsonRpcId;
  method?: string;
  params?: unknown;
};

const TOOLS = [
  {
    name: "search",
    description:
      "Search Wikipedia for information on a topic. Returns article title, summary extract, and URL.",
    inputSchema: {
      type: "object",
      properties: {
        query: {
          type: "string",
          description: "The topic to search for on Wikipedia",
        },
      },
      required: ["query"],
      additionalProperties: false,
    },
    annotations: {
      readOnlyHint: true,
      openWorldHint: true,
      destructiveHint: false,
    },
  },
] as const;

function writeJson(message: Record<string, unknown>) {
  stdout.write(`${JSON.stringify(message)}\n`);
}

function logError(message: string) {
  stderr.write(`[search-mcp] ${message}\n`);
}

function sendResult(id: JsonRpcId, result: Record<string, unknown>) {
  writeJson({ jsonrpc: "2.0", id, result });
}

function sendError(id: JsonRpcId, code: number, message: string) {
  writeJson({ jsonrpc: "2.0", id, error: { code, message } });
}

async function fetchWithTimeout(url: string, timeoutMs: number): Promise<Response> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { signal: controller.signal });
  } finally {
    clearTimeout(timeoutId);
  }
}

function errorContent(text: string) {
  return { content: [{ type: "text", text }], isError: true };
}

function successContent(text: string) {
  return { content: [{ type: "text", text }] };
}

async function callSearch(rawArguments: unknown) {
  const args = (rawArguments ?? {}) as Record<string, unknown>;
  const query = typeof args.query === "string" ? args.query.trim() : "";

  if (!query) {
    return errorContent("Query parameter is required and must be non-empty");
  }

  const encoded = encodeURIComponent(query);

  try {
    const summaryUrl = `https://en.wikipedia.org/api/rest_v1/page/summary/${encoded}`;
    const response = await fetchWithTimeout(summaryUrl, HTTP_TIMEOUT_MS);

    if (response.ok) {
      const data = (await response.json()) as Record<string, unknown>;
      const result = {
        title: data.title ?? query,
        extract: data.extract ?? "",
        url: (data.content_urls as Record<string, Record<string, string>>)?.desktop?.page ??
          `https://en.wikipedia.org/wiki/${encoded}`,
      };
      return successContent(JSON.stringify(result));
    }

    const searchUrl = `https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=${encoded}&format=json&origin=*`;
    const searchResponse = await fetchWithTimeout(searchUrl, HTTP_TIMEOUT_MS);

    if (!searchResponse.ok) {
      return errorContent(`Wikipedia API error: HTTP ${searchResponse.status}`);
    }

    const searchData = (await searchResponse.json()) as Record<string, unknown>;
    const queryResult = searchData.query as Record<string, unknown> | undefined;
    const searchResults = (queryResult?.search ?? []) as Array<Record<string, unknown>>;

    if (searchResults.length === 0) {
      return errorContent(`No Wikipedia articles found for: ${query}`);
    }

    const firstResult = searchResults[0]!;
    const snippet = (firstResult.snippet as string ?? "").replace(/<[^>]*>/g, "");
    const result = {
      title: firstResult.title ?? query,
      extract: snippet,
      url: `https://en.wikipedia.org/wiki/${encodeURIComponent(String(firstResult.title ?? query))}`,
    };
    return successContent(JSON.stringify(result));
  } catch (err) {
    if (err instanceof Error && err.name === "AbortError") {
      return errorContent("Request timed out while searching Wikipedia");
    }
    return errorContent(`Failed to search Wikipedia: ${err instanceof Error ? err.message : "Unknown error"}`);
  }
}

async function handleToolsCall(id: JsonRpcId, params: unknown) {
  const p = (params ?? {}) as Record<string, unknown>;
  const name = typeof p.name === "string" ? p.name : "";

  if (name === "search") {
    const result = await callSearch(p.arguments);
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
        name: "search-mcp",
        version: "1.0.0",
        description: "MCP server for searching Wikipedia",
        title: "Search MCP Server",
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
