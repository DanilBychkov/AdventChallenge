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

## Getting Started

### Install dependencies

```bash
npm install
```

### Development mode

```bash
npm run dev
```

### Build for production

```bash
npm run build
```

### Start production server

```bash
npm run start
```

## Verification

1. Start the server: `npm run dev`
2. Open the inspector: [http://localhost:3000/inspector](http://localhost:3000/inspector)
3. Test the tools:
   - Call `get-random-activity` to fetch a random activity
   - Call `find-activity` with filters like `type: "recreational"` or `participants: 2`

## Learn More

- [mcp-use Documentation](https://mcp-use.com/docs/typescript/getting-started/quickstart)
- [Bored API Documentation](https://www.boredapi.com/)

## Deploy

```bash
npm run deploy
```
