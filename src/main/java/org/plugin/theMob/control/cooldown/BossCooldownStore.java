package org.plugin.theMob.control.cooldown;

import java.util.Set;

public interface BossCooldownStore {
  void load();

  void save();

  long getNextSpawnEpochSeconds(String var1);

  void setNextSpawnEpochSeconds(String var1, long var2);

  Set<String> getAllBossIds();

  void close();
}
