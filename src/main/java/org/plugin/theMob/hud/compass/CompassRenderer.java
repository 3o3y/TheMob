package org.plugin.theMob.hud.compass;

public final class CompassRenderer {
  private static final int STEPS = 32;
  private static final int WIDTH = 59;
  private static final String[] RING = new String[32];

  public String render(float yaw) {
    float deg = (yaw + 180.0F) % 360.0F;
    if (deg < 0.0F) {
      deg += 360.0F;
    }

    int center = (int) Math.floor(deg / 360.0F * 32.0F) % 32;
    int half = 29;
    StringBuilder sb = new StringBuilder(128);
    sb.append("§1⟮ ");

    for (int offset = -half; offset <= half; offset++) {
      if (offset == 0) {
        sb.append("§f§l▲");
      } else {
        int idx = (center + offset) % 32;
        if (idx < 0) {
          idx += 32;
        }

        sb.append(this.color(offset)).append(RING[idx]);
      }
    }

    sb.append(" §1⟯");
    return sb.toString();
  }

  private String color(int offset) {
    int d = Math.abs(offset);
    if (d <= 1) {
      return "§f";
    } else if (d <= 3) {
      return "§b";
    } else if (d <= 6) {
      return "§9";
    } else {
      return d <= 10 ? "§1" : "§0";
    }
  }

  static {
    for (int i = 0; i < 32; i++) {
      RING[i] = "·";
    }

    RING[0] = "N";
    RING[8] = "E";
    RING[16] = "S";
    RING[24] = "W";
  }
}
