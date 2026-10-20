package org.javity.wasm;

import static org.junit.Assert.*;

import org.javity.platform.PlatformProvider;
import org.junit.Test;

public class OrbitPlatformProviderTest {
  @Test
  public void mapsCurrentPlayerAndSaveShapes() {
    PlatformProvider.Player player =
        OrbitPlatformProvider.parsePlayer("{\"id\":\"p-1\",\"nickname\":\"N\"}");
    assertEquals("p-1", player.id());
    assertEquals("N", player.displayName());
    PlatformProvider.Profile profile =
        OrbitPlatformProvider.parseProfile("{\"id\":\"p-1\",\"nickname\":\"N\"}");
    assertEquals("N", profile.displayName());
    assertEquals("", profile.avatarUrl());
    assertNull(OrbitPlatformProvider.parseSave("main", "null"));
    PlatformProvider.Save save =
        OrbitPlatformProvider.parseSave("main", "{\"data\":{\"level\":2},\"revision\":7}");
    assertEquals("main", save.slot());
    assertEquals("{\"level\":2}", save.payload());
    assertEquals(7, save.revision());
  }

  @Test
  public void mapsCurrentRoomPageCreatedAndJoinTicketShapes() {
    PlatformProvider.RoomList page =
        OrbitPlatformProvider.parseRoomList(
            "{\"items\":[{\"id\":\"r\",\"gameId\":\"g\",\"version\":\"1\",\"ownerId\":\"p\",\"status\":\"ready\",\"mode\":\"lobby\",\"private\":true,\"ranked\":false,\"maxPlayers\":4}]}");
    assertEquals(1, page.items().size());
    PlatformProvider.Room room = page.items().get(0);
    assertEquals("r", room.id());
    assertEquals("lobby", room.mode());
    assertTrue(room.isPrivate());
    assertEquals(4, room.maxPlayers());
    PlatformProvider.Room created =
        OrbitPlatformProvider.parseRoom(
            "{\"id\":\"r\",\"gameId\":\"g\",\"version\":\"1\",\"ownerId\":\"p\",\"status\":\"ready\",\"mode\":\"lobby\",\"private\":true,\"ranked\":false,\"maxPlayers\":4,\"inviteCode\":\"secret-invite\"}");
    assertEquals("secret-invite", created.inviteCode());
    PlatformProvider.RoomTicket ticket =
        OrbitPlatformProvider.parseRoomTicket(
            "{\"endpoint\":\"wss://room.example\",\"externalRoomId\":\"external\",\"ticket\":\"secret-ticket\",\"expiresIn\":60}");
    assertEquals("wss://room.example", ticket.endpoint());
    assertEquals("external", ticket.externalRoomId());
    assertEquals("secret-ticket", ticket.ticket());
    assertEquals(60, ticket.expiresIn());
    assertTrue(OrbitPlatformProvider.parseRoomCloseResult("{\"ok\":true}").ok());
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsPlayerWithoutRequiredId() {
    OrbitPlatformProvider.parsePlayer("{\"nickname\":\"N\"}");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsRoomWithoutRequiredId() {
    OrbitPlatformProvider.parseRoom(
        "{\"gameId\":\"g\",\"version\":\"1\",\"ownerId\":\"p\",\"status\":\"ready\",\"mode\":\"lobby\",\"private\":false,\"ranked\":false,\"maxPlayers\":2}");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsSaveWithoutValidRevision() {
    OrbitPlatformProvider.parseSave("main", "{\"data\":{},\"revision\":0}");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsTicketWithoutEndpoint() {
    OrbitPlatformProvider.parseRoomTicket(
        "{\"externalRoomId\":\"external\",\"ticket\":\"secret\",\"expiresIn\":60}");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsTicketWithoutSecretTicket() {
    OrbitPlatformProvider.parseRoomTicket(
        "{\"endpoint\":\"wss://room.example\",\"externalRoomId\":\"external\",\"expiresIn\":60}");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsTicketWithoutExternalRoomId() {
    OrbitPlatformProvider.parseRoomTicket(
        "{\"endpoint\":\"wss://room.example\",\"ticket\":\"secret\",\"expiresIn\":60}");
  }
}
