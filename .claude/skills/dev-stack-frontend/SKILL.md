# dev-stack-frontend

Manage the Vue dev server independently of the full stack.

## Commands

```bash
./dev-stack.sh frontend start   # Start Vue dev server on localhost:3003
./dev-stack.sh frontend stop    # Stop Vue dev server
./dev-stack.sh frontend logs    # View Vue dev server logs
```

## Use Cases

- Start Vue server after stopping the full stack but keeping infra running
- Restart frontend after config changes without restarting backend
- View frontend logs for debugging