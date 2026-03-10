import { MCPServer } from "mcp-use/server";
import { z } from "zod";
import { object, error } from "mcp-use/server";

// Create MCP server instance
const server = new MCPServer({
  name: "bored-api-mcp",
  title: "Bored API MCP Server",
  version: "1.0.0",
  description: "MCP server for the Bored API - find random activities to cure boredom",
  baseUrl: process.env.MCP_URL || "http://localhost:3000",
  favicon: "favicon.ico",
  websiteUrl: "https://mcp-use.com",
  icons: [
    {
      src: "icon.svg",
      mimeType: "image/svg+xml",
      sizes: ["512x512"],
    },
  ],
});

// Bored API base URL
const BORED_API_BASE = "https://bored-api.appbrewery.com";
const HTTP_TIMEOUT = 10000; // 10 seconds

// Activity type enum
const ActivityType = z.enum([
  "education",
  "recreational",
  "social",
  "charity",
  "cooking",
  "relaxation",
  "busywork",
]);

// Helper function to fetch with timeout
async function fetchWithTimeout(url: string, timeout: number): Promise<Response> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeout);
  
  try {
    const response = await fetch(url, {
      signal: controller.signal,
    });
    return response;
  } finally {
    clearTimeout(timeoutId);
  }
}

/**
 * Tool: get-random-activity
 * Fetches a random activity from the Bored API
 */
server.tool(
  {
    name: "get-random-activity",
    description: "Get a random activity to cure boredom. Returns an activity with details like type, participants, price, and accessibility.",
    schema: z.object({}),
  },
  async () => {
    try {
      const response = await fetchWithTimeout(
        `${BORED_API_BASE}/random`,
        HTTP_TIMEOUT
      );

      if (!response.ok) {
        return error(`Failed to fetch random activity: HTTP ${response.status}`);
      }

      const data = await response.json();
      return object(data);
    } catch (err) {
      if (err instanceof Error) {
        if (err.name === "AbortError") {
          return error("Request timed out while fetching random activity");
        }
        return error(`Failed to fetch random activity: ${err.message}`);
      }
      return error("Failed to fetch random activity: Unknown error");
    }
  }
);

/**
 * Tool: find-activity
 * Finds an activity based on optional filters
 */
server.tool(
  {
    name: "find-activity",
    description: "Find an activity matching specific criteria like type, number of participants, price range, and accessibility level.",
    schema: z.object({
      type: ActivityType.optional()
        .describe("The type of activity: education, recreational, social, charity, cooking, relaxation, or busywork"),
      participants: z.number().int().min(1).max(8).optional()
        .describe("Number of participants (1, 2, 3, 4, 5, 6, or 8)"),
      minPrice: z.number().min(0).max(1).optional()
        .describe("Minimum price level from 0 (free) to 1 (expensive)"),
      maxPrice: z.number().min(0).max(1).optional()
        .describe("Maximum price level from 0 (free) to 1 (expensive)"),
      minAccessibility: z.number().min(0).max(1).optional()
        .describe("Minimum accessibility level from 0 (most accessible) to 1 (least accessible)"),
      maxAccessibility: z.number().min(0).max(1).optional()
        .describe("Maximum accessibility level from 0 (most accessible) to 1 (least accessible)"),
    }),
  },
  async ({ type, participants, minPrice, maxPrice, minAccessibility, maxAccessibility }) => {
    try {
      // Build query parameters for API (only type and participants are supported by the API)
      const params = new URLSearchParams();
      if (type) {
        params.append("type", type);
      }
      if (participants !== undefined) {
        params.append("participants", participants.toString());
      }

      const queryString = params.toString();
      const url = `${BORED_API_BASE}/filter${queryString ? `?${queryString}` : ""}`;

      const response = await fetchWithTimeout(url, HTTP_TIMEOUT);

      if (!response.ok) {
        return error(`Failed to fetch activities: HTTP ${response.status}`);
      }

      const activities = await response.json();

      // Validate we received an array
      if (!Array.isArray(activities)) {
        return error("Unexpected response format from Bored API");
      }

      // Filter by price range if specified
      let filtered = activities.filter((activity: any) => {
        if (minPrice !== undefined && activity.price < minPrice) {
          return false;
        }
        if (maxPrice !== undefined && activity.price > maxPrice) {
          return false;
        }
        return true;
      });

      // Filter by accessibility range if specified
      // Note: API returns accessibility as a numeric value (0 to 1)
      filtered = filtered.filter((activity: any) => {
        const accessibility = parseFloat(activity.accessibility);
        if (isNaN(accessibility)) {
          return true; // Include if accessibility is not a valid number
        }
        if (minAccessibility !== undefined && accessibility < minAccessibility) {
          return false;
        }
        if (maxAccessibility !== undefined && accessibility > maxAccessibility) {
          return false;
        }
        return true;
      });

      // Return a random activity from the filtered results
      if (filtered.length === 0) {
        return error("No activity found for the given filters");
      }

      const randomIndex = Math.floor(Math.random() * filtered.length);
      return object(filtered[randomIndex]);
    } catch (err) {
      if (err instanceof Error) {
        if (err.name === "AbortError") {
          return error("Request timed out while finding activity");
        }
        return error(`Failed to find activity: ${err.message}`);
      }
      return error("Failed to find activity: Unknown error");
    }
  }
);

const PORT = process.env.PORT ? parseInt(process.env.PORT) : 3000;
console.log(`Server running on port ${PORT}`);
// Start the server
server.listen(PORT);
