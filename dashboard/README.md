# SwingTrade dashboard

Vue 3 + TypeScript dashboard for watchlists, market data, signals, backtests, paper trading, monitoring, and settings.

Run from `dashboard/`:

```bash
yarn
yarn dev          # http://localhost:3003 when launched by dev-stack.sh
yarn typecheck
yarn test:run
yarn build
```

Use `./dev-stack.sh start` from the repository root to launch the dashboard together with the API and development infrastructure. Do not commit local `.env` files.
