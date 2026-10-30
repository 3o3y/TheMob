# Performance

TheMob uses a managed entity registry, hot/cold hysteresis, bounded work queues, shared radar chunk scans, spawn budgets and measured tick pacing. Distant mobs retain their original AI state when woken. Missing chunks are not synchronously loaded merely for an arena ticket or claim query.

Dungeon planning runs in the background; terrain writes use a bounded block/time budget with one chunk at a time. Generation is still substantial work and clears the reserved build area. Use diagnostics and a test world to set capacity; no universal mob count or measured TPS improvement is claimed.

[Complete configuration and examples](../reference/PERFORMANCE.md). The detailed operator references currently use German; the public API developer guide is English.
