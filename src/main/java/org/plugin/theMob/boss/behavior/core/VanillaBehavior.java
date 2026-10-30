package org.plugin.theMob.boss.behavior.core;

import org.plugin.theMob.boss.behavior.BossBehavior;

/** Explicitly retains native flight, ranged and special boss AI. */
public final class VanillaBehavior implements BossBehavior {
  public String id() {
    return "vanilla";
  }
}
