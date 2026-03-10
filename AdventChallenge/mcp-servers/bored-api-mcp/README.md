# Bored API MCP Server

An MCP server for the [Bored API](https://bored-api.appbrewery.com/) - helps you find random activities to cure boredom.

## About the Bored API

The Bored API provides random activities to do when you're bored. It returns activity details including:
- **activity**: The activity description
- **type**: Category (education, recreational, social, charity, cooking, relaxation, busywork)
- **participants**: Number of people needed (1, 2, 3, 4, 5, 6, or 8)
- **price**: Cost level from 0 (free) to 1 (expensive)
- **accessibility**: Accessibility level from 0 (most accessible) to 1 (least accessible)
- **link**: Optional URL with more information
- **key**: Unique identifier for the activity

## Available Tools

### get-random-activity
Get a completely random activity from the Bored API.

### find-activity
Find an activity matching specific criteria:
- `type`: Filter by activity type
- `participants`: Filter by number of participants
- `minPrice` / `maxPrice`: Filter by price range (0-1)
- `minAccessibility` / `maxAccessibility`: Filter by accessibility range (0-1)

## Installation

```bash
npm install
```

## Usage

### Production (Bothub Client host)

The Bothub Client host application launches this server using:

- **Command**: `node dist/index.js`
- **Working directory**: `mcp-servers/bored-api-mcp` (this folder)

**Build is required before first use:**

```bash
npm run build
```

This produces `dist/index.js` which the host expects. Without this file, healthcheck will fail with a process launch error.

After building:
1. In Bothub Client, open **MCP Servers** settings (gear icon in header).
2. Enable **Bored API** in the server list.
3. Click **Check connection** — status should show **Online**.

See `docs/MCP_USER.md` and `docs/MCP_DEVELOPER.md` in the repo root for detailed setup and troubleshooting.

### Development (local testing)

For development with hot-reload (no pre-build needed):

```bash
npm run dev
```

This uses `tsx` to run `index.ts` directly with live reload.

### Standalone production

To run the production build standalone:

```bash
npm run start   # Equivalent to: node dist/index.js
```

## Verification

### With MCP Inspector (development)

1. Start the server: `npm run dev`
2. Open the inspector: [http://localhost:3000/inspector](http://localhost:3000/inspector)
3. Test the tools:
   - Call `get-random-activity` to fetch a random activity
   - Call `find-activity` with filters like `type: "recreational"` or `participants: 2`

### With Bothub Client (production)

1. Build: `npm run build`
2. In the app: open **MCP Servers** → enable **Bored API** → click **Check connection**
3. Status should show **Online**
4. Test routing with a message like:
   - "I'm bored, give me an activity idea"
   - "Чем заняться?" (Russian)

## Troubleshooting

- **Healthcheck fails with "Cannot run program"**: `dist/index.js` is missing. Run `npm run build` in this folder.
- **Server shows Offline**: Check that Node.js is installed and accessible, and that the working directory is correct.
- **No activities returned**: The external Bored API may be unavailable. Check network connectivity.

## Learn More

- [mcp-use Documentation](https://mcp-use.com/docs/typescript/getting-started/quickstart)
- [Bored API Documentation](https://www.boredapi.com/)

## Deploy

```bash
npm run deploy
```
