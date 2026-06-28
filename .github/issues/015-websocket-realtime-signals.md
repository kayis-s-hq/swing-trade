# feat(api): add WebSocket for real-time signal updates

**Labels:** `enhancement` `tier-3-infra` `api` `websocket`
**Estimated effort:** 2-3 days

## Problem

The frontend must poll the REST API to check for new signals. There is no real-time push mechanism. Users see stale data until they manually refresh.

## Proposed Solution

Add WebSocket support to push new signals to connected clients in real-time.

## Architecture

```
SignalService.generateSignal()
    └── SignalEvent published to ApplicationEventMulticaster
        └── SignalWebSocketHandler (event listener)
            └── WebSocket session.send() → connected clients
```

## WebSocket Configuration

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins("*")
            .withSockJS();
    }
}
```

## Message Protocol

### STOMP Frames

```
// Client subscribes to signals
SUBSCRIBE /topic/signals
Id: sub-0

// Server pushes new signal
SEND /app/signal.new
Content-Type: application/json

{
  "symbol": "RELIANCE",
  "signalType": "BUY",
  "confidence": 82.5,
  "reasoning": "RSI divergence + volume spike",
  "timestamp": "2026-05-15T10:30:00"
}

// Server pushes scan complete
SEND /app/scan.complete
Content-Type: application/json

{
  "signalsFound": 3,
  "scanDuration": 4523,
  "timestamp": "2026-05-15T10:30:05"
}
```

## Server-Side Implementation

### SignalEvent

```java
public record SignalEvent(
    String symbol,
    SignalType signalType,
    Double confidence,
    String reasoning,
    LocalDateTime timestamp
) {}
```

### SignalWebSocketHandler

```java
@Component
public class SignalWebSocketHandler {

    private final ApplicationEventMulticaster eventMulticaster;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void onNewSignal(SignalEvent event) {
        messagingTemplate.convertAndSend("/topic/signals", event);
    }

    @EventListener
    public void onScanComplete(ScanCompleteEvent event) {
        messagingTemplate.convertAndSend("/app/scan.complete", event);
    }
}
```

## Client-Side Implementation

### useSignalSocket.ts (composable)

```typescript
import SockJS from 'sockjs-client'
import { Stomp } from '@stomp/stompjs'

export function useSignalSocket() {
  const stompClient = ref<any>(null)
  const signals = ref<any[]>([])
  const isConnected = ref(false)

  function connect() {
    stompClient.value = Stomp.overSockJS(() => new SockJS('/ws'))
    stompClient.value.connect({}, () => {
      isConnected.value = true
      stompClient.value.subscribe('/topic/signals', (message) => {
        const signal = JSON.parse(message.body)
        signals.value.push(signal)
      })
    })
  }

  function disconnect() {
    stompClient.value?.disconnect()
    isConnected.value = false
  }

  onUnmounted(disconnect)
  return { signals, isConnected, connect }
}
```

### package.json additions

```bash
npm install @stomp/stompjs sockjs-client
npm install -D @types/sockjs-client  # if needed
```

## Files to Create

- `api/src/main/java/com/swingtrade/api/config/WebSocketConfig.java`
- `api/src/main/java/com/swingtrade/api/handler/SignalWebSocketHandler.java`
- `api/src/main/java/com/swingtrade/api/event/SignalEvent.java`
- `api/src/main/java/com/swingtrade/api/event/ScanCompleteEvent.java`
- `swing-trade-dashboard/src/composables/useSignalSocket.ts`

## Files to Modify

- `api/pom.xml` - Add `spring-boot-starter-websocket`
- `swing-trade-dashboard/package.json` - Add stompjs + sockjs
- `api/src/main/java/com/swingtrade/api/SignalService.java` - Publish SignalEvent
- `api/src/main/java/com/swingtrade/api/ScanService.java` - Publish ScanCompleteEvent
- `src/views/SignalsView.vue` - Connect WebSocket for live updates

## Acceptance Criteria

- [ ] WebSocket endpoint available at `/ws`
- [ ] STOMP protocol with SockJS fallback
- [ ] New signals pushed to `/topic/signals` in real-time
- [ ] Scan complete events pushed to `/app/scan.complete`
- [ ] Frontend `useSignalSocket` composable connects and receives messages
- [ ] Signals appear in SignalsView without page refresh
- [ ] Connection status indicator in UI
- [ ] Auto-reconnect on connection loss
- [ ] WebSocket disabled in test profile
- [ ] Unit tests for event publishing
- [ ] Code coverage >= 80%

## Notes

- SockJS fallback ensures compatibility with strict firewalls
- The WebSocket connection should reuse the same auth as HTTP (JWT in query param or header)
- Consider adding heartbeat (ping/pong) to keep connections alive
- For production, use native WebSocket (`/ws`) instead of SockJS for better performance
