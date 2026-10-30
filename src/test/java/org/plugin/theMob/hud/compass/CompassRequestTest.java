package org.plugin.theMob.hud.compass;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CompassRequestTest {
  @Test
  void noArgumentsTogglesSelfWhileScopeOnlyRequestsReadOnlyStatus() {
    assertEquals(CompassRequest.Scope.SELF, CompassRequest.parse(new String[0]).scope());
    assertEquals(CompassRequest.Operation.TOGGLE, CompassRequest.parse(new String[0]).operation());
    assertEquals(
        CompassRequest.Operation.STATUS, CompassRequest.parse(new String[] {"server"}).operation());
    assertEquals(
        CompassRequest.Operation.STATUS,
        CompassRequest.parse(new String[] {"spieler", "Alice"}).operation());
  }

  @Test
  void parsesSelfPlayerAndServerActionsWithoutConfusingPlayerNamesWithScopes() {
    assertEquals(
        CompassRequest.Operation.ON, CompassRequest.parse(new String[] {"AN"}).operation());
    CompassRequest player = CompassRequest.parse(new String[] {"spieler", "server", "aus"});
    assertEquals(CompassRequest.Scope.PLAYER, player.scope());
    assertEquals("server", player.target());
    assertEquals(CompassPreferences.Action.OFF, player.operation().action());
    CompassRequest server = CompassRequest.parse(new String[] {"server", "on"});
    assertEquals(CompassRequest.Scope.SERVER, server.scope());
    assertEquals(CompassPreferences.Action.ON, server.operation().action());
  }

  @Test
  void rejectsMistypedOrExtraArgumentsInsteadOfChangingGlobalState() {
    String[][] invalid = {
      {"alle"},
      {"server", "falsch"},
      {"server", "an", "extra"},
      {"spieler"},
      {"spieler", "Alice", "aus", "extra"},
      {"spieler", "", "an"},
      {"an", "Alice"}
    };
    for (String[] arguments : invalid) {
      assertThrows(IllegalArgumentException.class, () -> CompassRequest.parse(arguments));
    }
    assertThrows(IllegalStateException.class, () -> CompassRequest.Operation.STATUS.action());
  }
}
