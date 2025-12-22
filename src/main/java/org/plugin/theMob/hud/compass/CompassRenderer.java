package org.plugin.theMob.hud.compass;

public final class CompassRenderer {

    private static final int STEPS = 32;
    private static final int WINDOW = 21; // MUSS ungerade sein
    private static final String[] RING = new String[STEPS];
    static {
        for (int i = 0; i < STEPS; i++) {
            RING[i] = "-";
        }
        RING[0]  = "N";
        RING[4]  = "NO";
        RING[8]  = "O";
        RING[12] = "SO";
        RING[16] = "S";
        RING[20] = "SW";
        RING[24] = "W";
        RING[28] = "NW";
    }
    public String render(float yaw) {
        float compassDeg = (yaw + 180f) % 360f; // 0(S) -> 180, 180(N)->0, -90(E)->90, 90(W)->270
        if (compassDeg < 0f) compassDeg += 360f;
        int center = Math.round((compassDeg / 360f) * STEPS) % STEPS;
        int half = WINDOW / 2;
        StringBuilder sb = new StringBuilder(128);
        for (int offset = -half; offset <= half; offset++) {
            int idx = (center + offset + STEPS) % STEPS;
            if (offset == 0) {
                sb.append("§f§l^");
                continue;
            }
            sb.append(gradientColor(offset)).append(RING[idx]);
        }
        return sb.toString();
    }
    private String gradientColor(int offset) {
        int d = Math.abs(offset);
        if (d <= 1) return "§f";
        if (d <= 2) return "§b";
        if (d <= 3) return "§3";
        if (d <= 5) return "§9";
        return "§1";
    }
}
