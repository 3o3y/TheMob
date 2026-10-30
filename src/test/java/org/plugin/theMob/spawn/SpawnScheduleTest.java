package org.plugin.theMob.spawn;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SpawnScheduleTest {
  @Test
  void respectsThrottleAndPopulationMultipliers() {
    assertEquals(200, SpawnSchedule.intervalTicks(10, 1));
    assertEquals(350, SpawnSchedule.intervalTicks(10, 1.75));
    assertEquals(400, SpawnSchedule.intervalTicks(10, 2));
  }

  @Test
  void boundsPreventOverflowOrZeroTickLoops() {
    assertTrue(SpawnSchedule.intervalTicks(Integer.MAX_VALUE, Double.POSITIVE_INFINITY) > 0);
    assertEquals(20, SpawnSchedule.intervalTicks(0, 1));
  }
}
