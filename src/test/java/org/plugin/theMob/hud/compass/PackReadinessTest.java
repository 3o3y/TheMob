package org.plugin.theMob.hud.compass;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PackReadinessTest {
  private final UUID player = UUID.randomUUID();
  private final UUID pack = UUID.randomUUID();
  private final PackReadiness readiness = new PackReadiness();

  @Test
  void acceptanceAndDownloadDoNotShowTheHudBeforeThePackIsApplied() {
    readiness.expect(player, pack, false);
    assertFalse(readiness.loaded(player));
    readiness.update(player, pack, PackReadiness.Phase.ACCEPTED);
    assertFalse(readiness.loaded(player));
    readiness.update(player, pack, PackReadiness.Phase.DOWNLOADED);
    assertFalse(readiness.loaded(player));
    readiness.update(player, pack, PackReadiness.Phase.LOADED);
    assertTrue(readiness.loaded(player));
  }

  @Test
  void anotherPluginsSuccessfulPackCannotUnlockTheWallet() {
    readiness.expect(player, pack, false);
    assertFalse(readiness.update(player, UUID.randomUUID(), PackReadiness.Phase.LOADED));
    assertFalse(readiness.loaded(player));
  }

  @Test
  void preservesANativePackThatFinishedBeforeTheJoinEvent() {
    readiness.expect(player, pack, true);
    readiness.update(player, pack, PackReadiness.Phase.LOADED);
    readiness.expect(player, pack, true);
    assertTrue(readiness.loaded(player));
  }

  @Test
  void changedPacksAndFailedReloadsHideTheHud() {
    readiness.expect(player, pack, false);
    readiness.update(player, pack, PackReadiness.Phase.LOADED);
    readiness.update(player, pack, PackReadiness.Phase.FAILED);
    assertFalse(readiness.loaded(player));
    readiness.update(player, pack, PackReadiness.Phase.LOADED);
    readiness.expect(player, UUID.randomUUID(), true);
    assertFalse(readiness.loaded(player));
    assertFalse(readiness.update(player, pack, PackReadiness.Phase.LOADED));
  }

  @Test
  void declineAndDisconnectNeverPersistAsALoadedPack() {
    readiness.expect(player, pack, false);
    readiness.update(player, pack, PackReadiness.Phase.DECLINED);
    assertFalse(readiness.loaded(player));
    readiness.update(player, pack, PackReadiness.Phase.LOADED);
    readiness.forget(player);
    assertFalse(readiness.loaded(player));
    readiness.expect(player, pack, true);
    assertFalse(readiness.loaded(player));
  }
}
