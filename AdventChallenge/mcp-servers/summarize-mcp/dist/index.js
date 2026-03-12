import { createInterface } from "node:readline";
import { stdin, stdout, stderr } from "node:process";
const TOOLS = [
    {
        name: "summarize",
        description: "Summarize a given text by extracting key sentences. Returns a condensed version of the input text.",
        inputSchema: {
            type: "object",
            properties: {
                text: {
                    type: "string",
                    description: "The text to summarize",
                },
                maxSentences: {
                    type: "integer",
                    description: "Maximum number of sentences in the summary (default: 3)",
                    minimum: 1,
                    maximum: 20,
                },
            },
            required: ["text"],
            additionalProperties: false,
        },
        annotations: {
            readOnlyHint: true,
            openWorldHint: false,
            destructiveHint: false,
        },
    },
];
function writeJson(message) {
    stdout.write(`${JSON.stringify(message)}\n`);
}
function logError(message) {
    stderr.write(`[summarize-mcp] ${message}\n`);
}
function sendResult(id, result) {
    writeJson({ jsonrpc: "2.0", id, result });
}
function sendError(id, code, message) {
    writeJson({ jsonrpc: "2.0", id, error: { code, message } });
}
function errorContent(text) {
    return { content: [{ type: "text", text }], isError: true };
}
function successContent(text) {
    return { content: [{ type: "text", text }] };
}
function splitSentences(text) {
    const raw = text.match(/[^.!?]*[.!?]+/g);
    if (!raw || raw.length === 0) {
        return text.trim() ? [text.trim()] : [];
    }
    return raw.map((s) => s.trim()).filter((s) => s.length > 0);
}
function callSummarize(rawArguments) {
    const args = (rawArguments ?? {});
    const text = typeof args.text === "string" ? args.text.trim() : "";
    const maxSentences = typeof args.maxSentences === "number" ? args.maxSentences : 3;
    if (!text) {
        return errorContent("Text parameter is required and must be non-empty");
    }
    const sentences = splitSentences(text);
    const selected = sentences.slice(0, Math.max(1, maxSentences));
    const summary = selected.join(" ");
    return successContent(summary);
}
async function handleToolsCall(id, params) {
    const p = (params ?? {});
    const name = typeof p.name === "string" ? p.name : "";
    if (name === "summarize") {
        const result = callSummarize(p.arguments);
        sendResult(id, result);
        return;
    }
    sendError(id, -32601, `Unknown tool: ${name}`);
}
async function handleRequest(request) {
    const method = request.method;
    const hasId = Object.prototype.hasOwnProperty.call(request, "id");
    const id = hasId ? (request.id ?? null) : null;
    if (!method) {
        if (hasId)
            sendError(id, -32600, "Invalid Request: method is required");
        return;
    }
    if (method === "notifications/initialized")
        return;
    if (!hasId)
        return;
    if (method === "initialize") {
        sendResult(id, {
            protocolVersion: "2024-11-05",
            capabilities: { tools: { listChanged: false } },
            serverInfo: {
                name: "summarize-mcp",
                version: "1.0.0",
                description: "MCP server for summarizing text",
                title: "Summarize MCP Server",
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
    if (!trimmed)
        return;
    let parsed;
    try {
        parsed = JSON.parse(trimmed);
    }
    catch {
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
//# sourceMappingURL=index.js.map