package org.plugin.theMob.api;

public record IntegrationStatus(String plugin, State state, String detail) {
  public enum State {
    ACTIVE,
    ABSENT,
    DISABLED,
    INCOMPATIBLE
  }
}
