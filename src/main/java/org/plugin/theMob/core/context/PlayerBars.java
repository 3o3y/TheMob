package org.plugin.theMob.core.context;

import org.bukkit.boss.BossBar;

public final class PlayerBars {

    private BossBar hudBar;
    private BossBar bossBar;
    public void setHudBar(BossBar hudBar) {
        this.hudBar = hudBar;
    }
    public void setBossBar(BossBar bossBar) {
        this.bossBar = bossBar;
    }
    public void showBoss() {
        if (hudBar != null) hudBar.setVisible(false);
        if (bossBar != null) bossBar.setVisible(true);
    }
    public void hideBoss() {
        if (bossBar != null) bossBar.setVisible(false);
        if (hudBar != null) hudBar.setVisible(true);
    }
    public BossBar hudBar() { return hudBar; }
    public BossBar bossBar() { return bossBar; }
}
