package org.javity.gatekeepers;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.*;
import org.junit.Test;

public class ProfileStoreTest {
  @Test
  public void oldProfileMigratesAndNewProgressPersists() throws Exception {
    Path file = Files.createTempFile("gatekeepers-v1", ".properties");
    try {
      var p = new PlayerProfile();
      ProfileStore.save(file, p);
      String old =
          Files.readString(file)
              .replace("version=2", "version=1")
              .replaceAll("(?m)^weaponLevel=.*\\R?", "")
              .replaceAll("(?m)^arenaWins=.*\\R?", "");
      Files.writeString(file, old);
      var restored = ProfileStore.load(file);
      assertEquals(1, restored.weaponLevel());
      restored.rewardArena();
      restored.upgradeWeapon();
      ProfileStore.save(file, restored);
      assertEquals(restored.snapshot(), ProfileStore.load(file).snapshot());
    } finally {
      Files.delete(file);
    }
  }

  @Test
  public void roundtripRetainsQuestsInventoryAndUnlocks() throws Exception {
    Path dir = Files.createTempDirectory("gatekeepers-save");
    Path file = dir.resolve("profile.properties");
    try {
      var p = new PlayerProfile();
      p.rewardMob(false);
      p.rewardMob(true);
      p.claimBoss();
      p.damage(7);
      ProfileStore.save(file, p);
      var q = ProfileStore.load(file);
      assertEquals(p.snapshot(), q.snapshot());
    } finally {
      Files.deleteIfExists(file);
      Files.deleteIfExists(dir);
    }
  }

  @Test
  public void missingSaveStartsNewProfile() throws Exception {
    Path dir = Files.createTempDirectory("gatekeepers-missing");
    try {
      assertEquals(30, ProfileStore.load(dir.resolve("missing")).coins());
    } finally {
      Files.delete(dir);
    }
  }

  @Test
  public void corruptSaveIsRejectedAndRetained() throws Exception {
    Path file = Files.createTempFile("gatekeepers-corrupt", ".properties");
    try {
      Files.writeString(file, "version=999\nhp=broken\n");
      assertThrows(IOException.class, () -> ProfileStore.load(file));
      assertTrue(Files.readString(file).contains("broken"));
    } finally {
      Files.delete(file);
    }
  }

  @Test
  public void malformedUnicodeIsReportedAsLoadFailure() throws Exception {
    Path file = Files.createTempFile("gatekeepers-unicode", ".properties");
    try {
      Files.writeString(file, "version=1\nhp=" + "\\" + "uNOTHEX\n");
      assertThrows(IOException.class, () -> ProfileStore.load(file));
    } finally {
      Files.delete(file);
    }
  }
}
