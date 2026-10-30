package org.plugin.theMob.control.debug;

import java.util.concurrent.atomic.AtomicLong;

public final class SpawnStatistics {
  private final AtomicLong attempts = new AtomicLong();
  private final AtomicLong success = new AtomicLong();
  private final AtomicLong blockedBudget = new AtomicLong();
  private final AtomicLong blockedThrottle = new AtomicLong();
  private final AtomicLong blockedCooldown = new AtomicLong();

  public void incAttempt() {
    this.attempts.incrementAndGet();
  }

  public void incSuccess() {
    this.success.incrementAndGet();
  }

  public void incBlockedBudget() {
    this.blockedBudget.incrementAndGet();
  }

  public void incBlockedThrottle() {
    this.blockedThrottle.incrementAndGet();
  }

  public void incBlockedCooldown() {
    this.blockedCooldown.incrementAndGet();
  }

  public long getAttempts() {
    return this.attempts.get();
  }

  public long getSuccess() {
    return this.success.get();
  }

  public long getBlockedBudget() {
    return this.blockedBudget.get();
  }

  public long getBlockedThrottle() {
    return this.blockedThrottle.get();
  }

  public long getBlockedCooldown() {
    return this.blockedCooldown.get();
  }

  public void reset() {
    this.attempts.set(0L);
    this.success.set(0L);
    this.blockedBudget.set(0L);
    this.blockedThrottle.set(0L);
    this.blockedCooldown.set(0L);
  }
}
