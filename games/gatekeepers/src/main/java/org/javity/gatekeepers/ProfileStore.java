package org.javity.gatekeepers;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Explicit, bounded save schema. Broken profiles are reported, never silently overwritten. */
public final class ProfileStore {
  private ProfileStore() {}

  public static PlayerProfile load(Path path) throws IOException {
    if (!Files.exists(path)) return new PlayerProfile();
    if (Files.size(path) > 65536) throw new IOException("Zapis jest zbyt duży");
    Properties p = new Properties();
    try {
      try (var input = Files.newInputStream(path)) {
        p.load(input);
      }
      boolean v2 = "2".equals(p.getProperty("version"));
      if (!v2 && !"1".equals(p.getProperty("version")))
        throw new IllegalArgumentException("Wersja zapisu");
      return PlayerProfile.restore(
          new PlayerProfile.Snapshot(
              number(p, "hp"),
              number(p, "hunger"),
              number(p, "coins"),
              number(p, "xp"),
              number(p, "bread"),
              number(p, "apples"),
              number(p, "huntKills"),
              bool(p, "bossDefeated"),
              bool(p, "bossClaimed"),
              Float.parseFloat(p.getProperty("strength")),
              v2 ? number(p, "weaponLevel") : 1,
              v2 ? number(p, "arenaWins") : 0));
    } catch (IllegalArgumentException | NullPointerException bad) {
      throw new IOException("Nieprawidłowy zapis postaci", bad);
    }
  }

  private static int number(Properties p, String key) {
    return Integer.parseInt(p.getProperty(key));
  }

  private static boolean bool(Properties p, String key) {
    String value = p.getProperty(key);
    if (!"true".equals(value) && !"false".equals(value)) throw new IllegalArgumentException(key);
    return Boolean.parseBoolean(value);
  }

  public static void save(Path path, PlayerProfile profile) throws IOException {
    Path target = path.toAbsolutePath();
    Files.createDirectories(target.getParent());
    var s = profile.snapshot();
    Properties p = new Properties();
    p.setProperty("version", "2");
    p.setProperty("hp", "" + s.hp());
    p.setProperty("hunger", "" + s.hunger());
    p.setProperty("coins", "" + s.coins());
    p.setProperty("xp", "" + s.xp());
    p.setProperty("bread", "" + s.bread());
    p.setProperty("apples", "" + s.apples());
    p.setProperty("huntKills", "" + s.huntKills());
    p.setProperty("bossDefeated", "" + s.bossDefeated());
    p.setProperty("bossClaimed", "" + s.bossClaimed());
    p.setProperty("strength", "" + s.strengthSeconds());
    p.setProperty("weaponLevel", "" + s.weaponLevel());
    p.setProperty("arenaWins", "" + s.arenaWins());
    Path temp = Files.createTempFile(target.getParent(), "profile-", ".tmp");
    try {
      try (var output = Files.newOutputStream(temp)) {
        p.store(output, "Gatekeepers profile v2 (loads v1)");
      }
      Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }
}
