package org.plugin.theMob.control.debug;

import java.util.concurrent.atomic.AtomicLong;

public final class SpawnStatistics {

    private final AtomicLong attempts = new AtomicLong();
    private final AtomicLong success = new AtomicLong();
    private final AtomicLong blockedBudget = new AtomicLong();
    private final AtomicLong blockedThrottle = new AtomicLong();
    private final AtomicLong blockedCooldown = new AtomicLong();

    // =========================
    // WRITE
    // =========================
    public void incAttempt() {
        attempts.incrementAndGet();
    }

    public void incSuccess() {
        success.incrementAndGet();
    }

    public void incBlockedBudget() {
        blockedBudget.incrementAndGet();
    }

    public void incBlockedThrottle() {
        blockedThrottle.incrementAndGet();
    }

    public void incBlockedCooldown() {
        blockedCooldown.incrementAndGet();
    }

    // =========================
    // READ
    // =========================
    public long getAttempts() {
        return attempts.get();
    }

    public long getSuccess() {
        return success.get();
    }

    public long getBlockedBudget() {
        return blockedBudget.get();
    }

    public long getBlockedThrottle() {
        return blockedThrottle.get();
    }

    public long getBlockedCooldown() {
        return blockedCooldown.get();
    }

    public void reset() {
        attempts.set(0);
        success.set(0);
        blockedBudget.set(0);
        blockedThrottle.set(0);
        blockedCooldown.set(0);
    }
}
