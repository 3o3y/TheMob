package org.plugin.theMob.control.debug;

public final class SpawnStatistics {

    private long attempts;
    private long success;
    private long blockedBudget;
    private long blockedThrottle;
    private long blockedCooldown;

    // =========================
    // WRITE
    // =========================
    public void incAttempt() {
        attempts++;
    }

    public void incSuccess() {
        success++;
    }

    public void incBlockedBudget() {
        blockedBudget++;
    }

    public void incBlockedThrottle() {
        blockedThrottle++;
    }

    public void incBlockedCooldown() {
        blockedCooldown++;
    }

    // =========================
    // READ
    // =========================
    public long getAttempts() {
        return attempts;
    }

    public long getSuccess() {
        return success;
    }

    public long getBlockedBudget() {
        return blockedBudget;
    }

    public long getBlockedThrottle() {
        return blockedThrottle;
    }

    public long getBlockedCooldown() {
        return blockedCooldown;
    }

    public void reset() {
        attempts = success = blockedBudget = blockedThrottle = blockedCooldown = 0;
    }
}
