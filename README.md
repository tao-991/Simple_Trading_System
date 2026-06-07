# Simple Trading System

A production-quality algorithmic trading system built in Java for self-learning,
modelled after Goldman Sachs Equities Desk Technology standards.

## Modules completed

### Module 3 — Order Management System (OMS)
Full order lifecycle management with thread-safe state transitions.

- `Order` — immutable identity fields, volatile mutable state, business methods
  (`applyFill`, `cancel`, `reject`) with no public setters
- `OrderStore` — dual-store design: active and archived orders with atomic
  `remove(key, value)` to prevent race conditions on archival
- Enums: `OrderSide`, `OrderType`, `OrderStatus`, `TimeInForce`

### Module 4 — Limit Order Book & Matching Engine
Price-Time Priority matching engine with Single Writer concurrency model.

- `LimitOrderBook` — `TreeMap<Long, PriceLevel>` for bids (reverse order) and asks
  (natural order); prices stored as `long` ticks to avoid floating-point precision issues
- `PriceLevel` — `ArrayDeque<Order>` for FIFO time priority within a price level
- `MatchingEngine` — per-symbol `SingleThreadExecutor`; no locks needed inside
  `LimitOrderBook` (Single Writer Principle)
- `Trade` / `MatchResult` — immutable output of each matching cycle
- `OrderManager` — bridges matching engine and OMS; applies fills and archives
  completed orders

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

## Key design decisions

| Decision | Rationale |
|---|---|
| Price as `long` ticks | Avoids floating-point precision errors in financial calculations |
| Single Writer Principle | Per-symbol executor eliminates locks inside LimitOrderBook |
| Trade price = resting order's price | Price maker wins; standard exchange behaviour |
| Business methods over setters | `applyFill()`, `cancel()`, `reject()` enforce valid state transitions |
| `match()` produces Trades only | OrderManager owns state updates; clean separation of concerns |

## Tech stack

- Java 17
- Maven
- *(Upcoming)* LMAX Disruptor, Apache Kafka, QuickFIX/J, Redis, PostgreSQL,
  TimescaleDB, Prometheus/Grafana, Docker Compose

## Roadmap

- [ ] Module 1 — Market Data Handler (UDP/NIO/FAST)
- [ ] Module 2 — FIX Gateway (QuickFIX/J)
- [x] Module 3 — Order Management System (OMS)
- [x] Module 4 — Limit Order Book & Matching Engine
- [ ] Module 5 — Risk Engine (Pre-trade, Greeks, VaR)
- [ ] Module 6 — Position Manager
- [ ] Module 7 — Kafka Message Bus
- [ ] Module 8 — PnL Engine
- [ ] Module 9 — Dashboard & Monitoring
