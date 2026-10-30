package org.plugin.theMob.hud.compass;

import java.util.Locale;

/** Parsing has no server or storage side effects. */
record CompassRequest(Scope scope, String target, Operation operation) {
  enum Scope {
    SELF,
    PLAYER,
    SERVER
  }

  enum Operation {
    ON,
    OFF,
    TOGGLE,
    STATUS;

    CompassPreferences.Action action() {
      return switch (this) {
        case ON -> CompassPreferences.Action.ON;
        case OFF -> CompassPreferences.Action.OFF;
        case TOGGLE -> CompassPreferences.Action.TOGGLE;
        case STATUS -> throw new IllegalStateException("Status aendert keine Einstellung.");
      };
    }
  }

  static CompassRequest parse(String[] arguments) {
    if (arguments.length == 0) {
      return new CompassRequest(Scope.SELF, null, Operation.TOGGLE);
    }
    String first = arguments[0].toLowerCase(Locale.ROOT);
    if (first.equals("server")) {
      if (arguments.length > 2) {
        throw usage();
      }
      return new CompassRequest(
          Scope.SERVER, null, arguments.length == 1 ? Operation.STATUS : operation(arguments[1]));
    }
    if (first.equals("spieler") || first.equals("player")) {
      if (arguments.length < 2 || arguments.length > 3 || arguments[1].isBlank()) {
        throw usage();
      }
      return new CompassRequest(
          Scope.PLAYER,
          arguments[1],
          arguments.length == 2 ? Operation.STATUS : operation(arguments[2]));
    }
    if (arguments.length == 1) {
      return new CompassRequest(Scope.SELF, null, operation(first));
    }
    throw usage();
  }

  private static Operation operation(String argument) {
    return switch (argument.toLowerCase(Locale.ROOT)) {
      case "an", "on" -> Operation.ON;
      case "aus", "off" -> Operation.OFF;
      case "toggle", "umschalten" -> Operation.TOGGLE;
      case "status" -> Operation.STATUS;
      default -> throw usage();
    };
  }

  private static IllegalArgumentException usage() {
    return new IllegalArgumentException(
        "Verwendung: /kompass [an|aus|status], "
            + "/kompass spieler <Name> <an|aus|status> oder /kompass server <an|aus|status>");
  }
}
