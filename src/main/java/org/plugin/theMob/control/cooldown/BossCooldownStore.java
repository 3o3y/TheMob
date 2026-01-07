package org.plugin.theMob.control.cooldown;

import java.util.Set;

public interface BossCooldownStore {

    void load();
    void save();

    long getNextSpawnEpochSeconds(String bossId);
    void setNextSpawnEpochSeconds(String bossId, long epochSeconds);

    Set<String> getAllBossIds();

    void close();
}
