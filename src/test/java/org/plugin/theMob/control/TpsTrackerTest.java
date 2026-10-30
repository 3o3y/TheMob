package org.plugin.theMob.control;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TpsTrackerTest {
  @Test
  void clockAdvancesOncePerServerTickAndResetsOnStop() {
    var clock = new AtomicLong();
    var tracker = new TpsTracker(clock::get);
    for (int i = 0; i < 1200; i++) {
      clock.addAndGet(50_000_000);
      tracker.tick();
    }
    assertEquals(1200, tracker.currentTick());
    assertEquals(20, tracker.tps1m(), .0001);
    assertEquals(TpsTracker.Status.OK, tracker.statusEnum());
    tracker.stop();
    assertEquals(0, tracker.currentTick());
    assertEquals(20, tracker.tps1m(), .0001);
  }

  @Test
  void lagAndLongServerStallsReduceMeasuredTps() {
    var clock = new AtomicLong();
    var tracker = new TpsTracker(clock::get);
    clock.addAndGet(5_000_000_000L);
    tracker.tick();
    assertEquals(20, tracker.tps1s(), .0001); // Startup delay precedes the first server tick.
    for (int i = 0; i < 20; i++) {
      clock.addAndGet(100_000_000);
      tracker.tick();
    }
    assertEquals(10, tracker.tps1s(), .0001);
    assertTrue(tracker.isDropping());
    clock.addAndGet(5_000_000_000L);
    tracker.tick();
    assertTrue(tracker.tps1s() < 3);
    assertEquals(5000, tracker.mspt(), .0001);
  }

  @Test
  void catchUpTicksNeverReportMoreThanTwentyTps() {
    var tracker = new TpsTracker(() -> 0L);
    tracker.tick();
    assertEquals(20, tracker.tps1s(), .0001);
  }
}
