# SwingTrade Dashboard

Vue 3 monitoring application for the SwingTrade automated trading system. Real-time positions, signals, and portfolio monitoring with dark mode support and responsive design.

## Features

- Real-time market overview with system metrics
- Positions management and monitoring with filtering
- Signal generation and filtering by type/confidence
- Portfolio performance tracking with trade history
- Dark mode support with localStorage persistence
- Responsive design (mobile, tablet, desktop)

## Tech Stack

- Vue 3 + TypeScript
- Vite build tool
- Tailwind CSS
- Vue Router (hash history)
- Pinia state management
- Axios HTTP client
- Vitest for unit testing

## Development

### Prerequisites

- Node.js 18+
- npm 9+

### Installation

```bash
cd swing-trade-dashboard
npm install
```

### Development Server

```bash
npm run dev
```

The application will start at `http://localhost:5173`

### Build

```bash
npm run build
```

The build output will be in the `dist/` directory.

### Preview Production Build

```bash
npm run preview
```

The production preview will be available at `http://localhost:4173`

### Run Tests

```bash
npx vitest run
```

### Lint

```bash
npm run lint
```

## Environment Variables

Create a `.env` file from `.env.example` and update with your values:

```bash
cp .env.example .env
```

### Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `VITE_API_BASE_URL` | `http://localhost:8080/api` | Backend API endpoint |
| `VITE_API_TIMEOUT` | `30000` | Request timeout in milliseconds |
| `VITE_ENABLE_DEBUG` | `false` | Enable debug mode |
| `VITE_ENABLE_ANALYTICS` | `false` | Enable analytics |

## Production Deployment

1. Build the application: `npm run build`
2. The `dist/` directory contains the minified assets
3. Serve the `dist/` directory with any static file server (nginx, Apache, etc.)
4. Router uses hash history, no server reconfiguration needed

## Browser Support

- Chrome/Edge (latest)
- Firefox (latest)
- Safari (latest)

## License

MIT or proprietary - see project root for details.
