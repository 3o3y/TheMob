package org.plugin.theMob.hud.compass;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.plugin.theMob.TheMob;

class CoinOverlayPackTest {
  private static final String HASH = "a".repeat(40);

  @Test
  void matchesCoinOverlayPackIdExactly() {
    var offer =
        CoinOverlayPack.offer("https://example.org/coins.zip", HASH.toUpperCase(), "Laden?", false);
    assertEquals(
        UUID.nameUUIDFromBytes(
            ("CoinOverlay:HUD:https://example.org/coins.zip:" + HASH)
                .getBytes(StandardCharsets.UTF_8)),
        offer.id());
    assertEquals(HASH, offer.sha1());
    assertEquals(20, offer.hashBytes().length);
  }

  @Test
  void observesOnlyCoinOverlayPackWithoutSendingAnotherJoinOffer() throws Exception {
    var server = MockBukkit.mock();
    try {
      var coins = MockBukkit.createMockPlugin("CoinOverlay");
      coins.getConfig().set("resource-pack.url", "https://example.org/coins.zip");
      coins.getConfig().set("resource-pack.sha1", HASH);
      var plugin = MockBukkit.load(TheMob.class);
      plugin.getConfig().set("navigation-hud.resource-pack.url", "invalid-old-TheMob-url");
      var owner = mock(CompassService.class);
      when(owner.plugin()).thenReturn(plugin);
      when(owner.sharedCoinOverlayPack()).thenReturn(true);
      var delivery = new ResourcePackDelivery(owner, () -> null);
      Player player = spy(server.addPlayer());
      delivery.joined(player);
      assertFalse(delivery.ready(player));
      delivery.onPackStatus(
          new PlayerResourcePackStatusEvent(
              player, UUID.randomUUID(), PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
      assertFalse(delivery.ready(player));
      var id = CoinOverlayPack.offer("https://example.org/coins.zip", HASH, "", false).id();
      delivery.onPackStatus(
          new PlayerResourcePackStatusEvent(
              player, id, PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
      assertTrue(delivery.ready(player));
      delivery.joined(player);
      assertTrue(delivery.ready(player));
      var afterReload = new ResourcePackDelivery(owner, () -> null);
      afterReload.joined(player);
      assertTrue(
          afterReload.ready(player), "TheMob reload must keep the shared pack's loaded state");
      server.getScheduler().performTicks(40);
      verify(player, never()).addResourcePack(any(), anyString(), any(), anyString(), anyBoolean());
      assertTrue(delivery.status().contains("CoinOverlay"));
      coins.getConfig().set("resource-pack.sha1", "b".repeat(40));
      var changedPack = new ResourcePackDelivery(owner, () -> null);
      changedPack.joined(player);
      assertFalse(
          changedPack.ready(player), "A changed published hash requires its own successful load");
      coins.getConfig().set("resource-pack.url", "");
      var nativePack = mock(org.bukkit.packs.ResourcePack.class);
      UUID nativeId = UUID.randomUUID();
      when(nativePack.getId()).thenReturn(nativeId);
      when(nativePack.getUrl()).thenReturn("https://example.org/server.zip");
      when(nativePack.getHash()).thenReturn(HASH);
      when(nativePack.getPrompt()).thenReturn("");
      var nativeDelivery = new ResourcePackDelivery(owner, () -> nativePack);
      nativeDelivery.joined(player);
      assertFalse(nativeDelivery.ready(player));
      nativeDelivery.onPackStatus(
          new PlayerResourcePackStatusEvent(
              player, nativeId, PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
      assertTrue(nativeDelivery.ready(player));
      coins.getConfig().set("resource-pack.offer-on-join", false);
      var manual = new ResourcePackDelivery(owner, () -> nativePack);
      manual.quit(player);
      manual.joined(player);
      assertFalse(
          manual.ready(player), "Disabling automatic offers must not assume an installed pack");
      manual.onPackStatus(
          new PlayerResourcePackStatusEvent(
              player, nativeId, PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
      assertTrue(manual.ready(player), "Native load status is observed even without plugin offers");
      manual.onPackStatus(
          new PlayerResourcePackStatusEvent(
              player, nativeId, PlayerResourcePackStatusEvent.Status.DISCARDED));
      assertFalse(manual.ready(player));
    } finally {
      MockBukkit.unmock();
    }
  }
}
