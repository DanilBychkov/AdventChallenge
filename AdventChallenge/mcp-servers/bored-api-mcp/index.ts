import { createInterface } from "node:readline";
import { stdin, stdout, stderr } from "node:process";
import { z } from "zod";

const BORED_API_BASE = "https://bored-api.appbrewery.com";
const HTTP_TIMEOUT_MS = 10_000;

const ActivityType = z.enum([
  "education",
  "recreational",
  "social",
  "charity",
  "cooking",
  "relaxation",
  "busywork",
]);

const ParticipantsSchema = z.union([
  z.literal(1),
  z.literal(2),
  z.literal(3),
  z.literal(4),
  z.literal(5),
  z.literal(6),
  z.literal(8),
]);

const FindActivitySchema = z.object({
  type: ActivityType.optional(),
  participants: ParticipantsSchema.optional(),
  minPrice: z.number().min(0).max(1).optional(),
  maxPrice: z.number().min(0).max(1).optional(),
  minAccessibility: z.number().min(0).max(1).optional(),
  maxAccessibility: z.number().min(0).max(1).optional(),
});

type JsonRpcId = number | string | null;

type JsonRpcRequest = {
  jsonrpc?: string;
  id?: JsonRpcId;
  method?: string;
  params?: unknown;
};

type BoredActivity = {
  activity?: string;
  type?: string;
  participants?: number;
  price?: number;
  accessibility?: number | string;
  link?: string;
  key?: string;
};

const TOOLS = [
  {
    name: "get-random-activity",
    description:
      "Get a random activity to cure boredom. Returns an activity with details like type, participants, price, and accessibility.",
    inputSchema: {
      type: "object",
      properties: {},
      additionalProperties: false,
    },
    annotations: {
      readOnlyHint: true,
      openWorldHint: true,
      destructiveHint: false,
    },
  },
  {
    name: "find-activity",
    description:
      "Find an activity matching specific criteria like type, number of participants, price range, and accessibility level.",
    inputSchema: {
      type: "object",
      properties: {
        type: {
          type: "string",
          enum: ["education", "recreational", "social", "charity", "cooking", "relaxation", "busywork"],
          description:
            "The type of activity: education, recreational, social, charity, cooking, relaxation, or busywork",
        },
        participants: {
          type: "integer",
          enum: [1, 2, 3, 4, 5, 6, 8],
          description: "Number of participants (1, 2, 3, 4, 5, 6, or 8)",
        },
        minPrice: {
          type: "number",
          minimum: 0,
          maximum: 1,
          description: "Minimum price level from 0 (free) to 1 (expensive)",
        },
        maxPrice: {
          type: "number",
          minimum: 0,
          maximum: 1,
          description: "Maximum price level from 0 (free) to 1 (expensive)",
        },
        minAccessibility: {
          type: "number",
          minimum: 0,
          maximum: 1,
          description: "Minimum accessibility level from 0 (most accessible) to 1 (least accessible)",
        },
        maxAccessibility: {
          type: "number",
          minimum: 0,
          maximum: 1,
          description: "Maximum accessibility level from 0 (most accessible) to 1 (least accessible)",
        },
      },
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
  stderr.write(`[bored-api-mcp] ${message}\n`);
}

function sendResult(id: JsonRpcId, result: Record<string, unknown>) {
  writeJson({ jsonrpc: "2.0", id, result });
}

function sendError(id: JsonRpcId, code: number, message: string) {
  writeJson({
    jsonrpc: "2.0",
    id,
    error: {
      code,
      message,
    },
  });
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
  return {
    content: [{ type: "text", text }],
    isError: true,
  };
}

function successContent(activity: BoredActivity) {
  return {
    content: [
      {
        type: "text",
        text: JSON.stringify(activity),
      },
    ],
    activity,
  };
}

async function callGetRandomActivity() {
  try {
    const response = await fetchWithTimeout(`${BORED_API_BASE}/random`, HTTP_TIMEOUT_MS);
    if (!response.ok) {
      return errorContent(`Failed to fetch random activity: HTTP ${response.status}`);
    }

    const data = (await response.json()) as BoredActivity;
    return successContent(data);
  } catch (err) {
    if (err instanceof Error && err.name === "AbortError") {
      return errorContent("Request timed out while fetching random activity");
    }
    return errorContent(`Failed to fetch random activity: ${err instanceof Error ? err.message : "Unknown error"}`);
  }
}

async function callFindActivity(rawArguments: unknown) {
  const parsed = FindActivitySchema.safeParse(rawArguments ?? {});
  if (!parsed.success) {
    const details = parsed.error.issues.map((issue) => `${issue.path.join(".")}: ${issue.message}`).join("; ");
    return errorContent(`Invalid arguments for tool find-activity: ${details}`);
  }

  const { type, participants, minPrice, maxPrice, minAccessibility, maxAccessibility } = parsed.data;

  if (minPrice !== undefined && maxPrice !== undefined && minPrice > maxPrice) {
    return errorContent("minPrice must be less than or equal to maxPrice");
  }

  if (
    minAccessibility !== undefined &&
    maxAccessibility !== undefined &&
    minAccessibility > maxAccessibility
  ) {
    return errorContent("minAccessibility must be less than or equal to maxAccessibility");
  }

  const params = new URLSearchParams();
  if (type) {
    params.append("type", type);
  }
  if (participants !== undefined) {
    params.append("participants", participants.toString());
  }

  const query = params.toString();
  const url = `${BORED_API_BASE}/filter${query ? `?${query}` : ""}`;

  try {
    const response = await fetchWithTimeout(url, HTTP_TIMEOUT_MS);
    if (!response.ok) {
      return errorContent(`Failed to fetch activities: HTTP ${response.status}`);
    }

    const activities = (await response.json()) as BoredActivity[];
    if (!Array.isArray(activities)) {
      return errorContent("Unexpected response format from Bored API");
    }

    let filtered = activities.filter((activity) => {
      if (minPrice !== undefined && typeof activity.price === "number" && activity.price < minPrice) return false;
      if (maxPrice !== undefined && typeof activity.price === "number" && activity.price > maxPrice) return false;
      return true;
    });

    filtered = filtered.filter((activity) => {
      const accessibility = Number.parseFloat(String(activity.accessibility));
      if (Number.isNaN(accessibility)) return true;
      if (minAccessibility !== undefined && accessibility < minAccessibility) return false;
      if (maxAccessibility !== undefined && accessibility > maxAccessibility) return false;
      return true;
    });

    if (filtered.length === 0) {
      return errorContent("No activity found for the given filters");
    }

    const randomIndex = Math.floor(Math.random() * filtered.length);
    return successContent(filtered[randomIndex] as BoredActivity);
  } catch (err) {
    if (err instanceof Error && err.name === "AbortError") {
      return errorContent("Request timed out while finding activity");
    }
    return errorContent(`Failed to find activity: ${err instanceof Error ? err.message : "Unknown error"}`);
  }
}

async function handleToolsCall(id: JsonRpcId, params: unknown) {
  const call = z
    .object({
      name: z.string(),
      arguments: z.unknown().optional(),
    })
    .safeParse(params ?? {});

  if (!call.success) {
    sendError(id, -32602, "Invalid params for tools/call");
    return;
  }

  const { name, arguments: toolArgs } = call.data;

  if (name === "get-random-activity") {
    const result = await callGetRandomActivity();
    sendResult(id, result);
    return;
  }

  if (name === "find-activity") {
    const result = await callFindActivity(toolArgs);
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

  if (method === "notifications/initialized") {
    return;
  }

  if (!hasId) {
    return;
  }

  if (method === "initialize") {
    sendResult(id, {
      protocolVersion: "2024-11-05",
      capabilities: {
        tools: {
          listChanged: false,
        },
      },
      serverInfo: {
        name: "bored-api-mcp",
        version: "1.0.0",
        description: "MCP server for the Bored API - find random activities to cure boredom",
        title: "Bored API MCP Server",
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
  if (!trimmed) {
    return;
  }

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
