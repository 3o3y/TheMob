package org.plugin.theMob.hud.compass;

public final class CompassRenderer {

    private static final int STEPS = 32;
    private static final int WIDTH = 59; // ungerade
    private static final String[] RING = new String[STEPS];

    static {
        for (int i = 0; i < STEPS; i++) {
            RING[i] = "·";
        }
        RING[0]  = "N";
        RING[8]  = "E";
        RING[16] = "S";
        RING[24] = "W";
    }

    public String render(float yaw) {

        float deg = (yaw + 180f) % 360f;
        if (deg < 0f) deg += 360f;
        int center = Math.round((deg / 360f) * STEPS) % STEPS;
        int half = WIDTH / 2;
        StringBuilder sb = new StringBuilder(128);
        sb.append("§1⟮ ");
        for (int offset = -half; offset <= half; offset++) {
            if (offset == 0) {
                sb.append("§f§l▲");
                continue;
            }
            int idx = (center + offset + STEPS) % STEPS;
            sb.append(color(offset)).append(RING[idx]);
        }
        sb.append(" §1⟯");
        return sb.toString();
    }
    private String color(int offset) {
        int d = Math.abs(offset);
        if (d == 0) return "§f§l";
        if (d <= 1) return "§f";
        if (d <= 3) return "§b";
        if (d <= 6) return "§9";
        if (d <= 10) return "§1";
        return "§0";
    }
}
