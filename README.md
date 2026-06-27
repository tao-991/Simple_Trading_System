# Simple Trading System

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square)
![Maven](https://img.shields.io/badge/Build-Maven-blue?style=flat-square)
![Status](https://img.shields.io/badge/Status-In_Progress-yellow?style=flat-square)

A production-quality algorithmic trading system built in Java for self-learning,
modelled after Goldman Sachs Equities Desk Technology standards.

> [!IMPORTANT]
> Built as interview preparation for Trading IT roles at investment banks.
> Each module mirrors real systems used at firms like Goldman Sachs.

---

## Modules completed

### ✅ Module 3 — Order Management System (OMS)
Full order lifecycle management with thread-safe state transitions.

- `Order` — immutable identity fields, volatile mutable state, business methods
  (`applyFill`, `cancel`, `reject`) with no public setters
- `OrderStore` — dual-store design: active and archived orders with atomic
  `remove(key, value)` to prevent race conditions on archival
- Enums: `OrderSide`, `OrderType`, `OrderStatus`, `TimeInForce`

### ✅ Module 4 — Limit Order Book & Matching Engine
Price-Time Priority matching engine with Single Writer concurrency model.

- `LimitOrderBook` — `TreeMap<Long, PriceLevel>` for bids (reverse order) and asks
  (natural order); prices stored as `long` ticks to avoid floating-point precision issues
- `PriceLevel` — `ArrayDeque<Order>` for FIFO time priority within a price level
- `MatchingEngine` — per-symbol `SingleThreadExecutor`; no locks needed inside
  `LimitOrderBook` (Single Writer Principle)
- `Trade` / `MatchResult` — immutable output of each matching cycle
- `OrderManager` — bridges matching engine and OMS; applies fills and archives
  completed orders

### Module 7 — Kafka Message Bus
Decouples the matching engine from downstream consumers via a persistent,
replayable event log. The matching engine no longer calls consumers directly;
it publishes to Kafka and any number of independent consumers subscribe.

- `KafkaConfig` — central configuration: broker address, topic names,
  producer/consumer properties
- `TradeEventProducer` — publishes `Trade` events to the `executions` topic.
  Keyed by `incomingOrderId` so all events for one order land in the same
  partition (ordering guarantee). Configured with `acks=all` (no message loss)
  and `enable.idempotence=true` (broker dedups retries via PID + sequence number)
- `PositionConsumer` — standalone consumer that subscribes to `executions`,
  deserializes trades, and maintains net position per symbol. Zero compile-time
  dependency on the matching engine
- Manual offset commit (`enable.auto.commit=false`, `commitSync` after each
  batch) for at-least-once delivery
- Idempotent consumption via a `tradeId` set: redelivered messages are skipped
  before they affect position. In-memory for now; Redis-backed persistence
  is the next step
- `Trade` has a dedicated 7-arg `@JsonCreator` constructor so Jackson
  reconstructs trades with their ORIGINAL `tradeId` and `tradeTime`
  (round-trip consistency — essential for tradeId-based idempotency)
- Single-broker Kafka in KRaft mode via Docker Compose

---

## Architecture

```
Trader
  └─► Order ──► OrderStore (OMS)
                    │
                    │ submitOrder()
                    ▼
            MatchingEngine
              └─ LimitOrderBook.match()
                    │ [Single Writer per symbol]
                    │ produces
                    ▼
                  Trade
                    │
                    │ tradeHandler callback
                    ▼
            OrderManager.onTrades()
              ├─ Order.applyFill()
              └─ OrderStore.archive()  ← if FILLED
```

---

## Key design decisions

> [!NOTE]
> These decisions reflect production standards at investment banks.
> Each one has a specific rationale worth knowing for interviews.

| Decision | Rationale |
|---|---|
| Price as `long` ticks | Avoids floating-point precision errors in financial calculations |
| Single Writer Principle | Per-symbol executor eliminates locks inside `LimitOrderBook` |
| Trade price = resting order's price | Price maker wins; standard exchange behaviour |
| Business methods over setters | `applyFill()`, `cancel()`, `reject()` enforce valid state transitions |
| `match()` produces Trades only | `OrderManager` owns state updates; clean separation of concerns || Kafka key = `incomingOrderId` | Same order's events route to one partition → ordering preserved |
| At-least-once + tradeId idempotency | Never lose a trade; redelivered trades are deduped, not double-counted |
| Two `Trade` constructors | 5-arg auto-generates `tradeId`/`tradeTime` (matching engine); 7-arg `@JsonCreator` preserves them (Kafka deserialization) |

---

## Tech stack

| Layer | Technology |
|---|---|
| Core language | Java 17 |
| Build | Maven |
| High-performance queue | LMAX Disruptor *(upcoming)* |
| Messaging | Apache Kafka  |
| FIX protocol | QuickFIX/J *(upcoming)* |
| Cache | Redis *(upcoming)* |
| Database | PostgreSQL + TimescaleDB *(upcoming)* |
| Monitoring | Prometheus + Grafana *(upcoming)* |
| Infrastructure | Docker Compose  |

---

## Roadmap

- [ ] Module 1 — Market Data Handler (UDP/NIO/FAST)
- [ ] Module 2 — FIX Gateway (QuickFIX/J)
- [x] Module 3 — Order Management System (OMS)
- [x] Module 4 — Limit Order Book & Matching Engine
- [ ] Module 5 — Risk Engine (Pre-trade, Greeks, VaR)
- [ ] Module 6 — Position Manager
- [x] Module 7 — Kafka Message Bus
- [ ] Module 8 — PnL Engine
- [ ] Module 9 — Dashboard & Monitoring
- [ ] Module 10 — Execution Algorithms (TWAP, VWAP, POV, IS, Iceberg)

> [!WARNING]
> Modules 1 and 2 are planned but not yet started.
> Development follows core engine first, then external protocols.