package org.javity.audio;

import static org.junit.Assert.*;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import java.lang.reflect.Proxy;
import java.util.*;
import org.javity.core.Game;
import org.junit.Test;

public class SoundManagerTest {
  private static final class RecordingAudio {
    long nextId = 100;
    final List<String> calls = new ArrayList<>();
    final Map<Long, Float> volumes = new HashMap<>();
    final Map<String, Map<Long, Deque<RuntimeException>>> idFailures = new HashMap<>();
    final Map<String, Deque<RuntimeException>> nextFailures = new HashMap<>();
    final Sound sound =
        (Sound)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] {Sound.class},
                (proxy, method, args) -> {
                  String name = method.getName();
                  if (name.equals("play") || name.equals("loop")) {
                    RuntimeException failure = takeFailure(name, args);
                    if (failure != null) throw failure;
                    long id = ++nextId;
                    calls.add(name + ":" + id);
                    volumes.put(id, args == null || args.length == 0 ? 1f : (Float) args[0]);
                    return id;
                  }
                  if (name.equals("stop") && args != null && args.length == 1) {
                    calls.add("stop:" + args[0]);
                    RuntimeException failure = takeFailure(name, args);
                    if (failure != null) throw failure;
                    return null;
                  }
                  if (name.equals("stop")) calls.add("stop-all");
                  if ((name.equals("pause") || name.equals("resume")) && args.length == 1)
                    calls.add(name + ":" + args[0]);
                  if (name.equals("setVolume") && args.length == 2)
                    volumes.put((Long) args[0], (Float) args[1]);
                  if (name.equals("setPitch") && args.length == 2)
                    calls.add("pitch:" + args[0] + ":" + args[1]);
                  if (name.equals("setPan") && args.length == 3)
                    calls.add("pan:" + args[0] + ":" + args[1] + ":" + args[2]);
                  if (name.equals("setLooping") && args.length == 2)
                    calls.add("looping:" + args[0] + ":" + args[1]);
                  RuntimeException failure = takeFailure(name, args);
                  if (failure != null) throw failure;
                  return null;
                });

    void failNext(String method, RuntimeException failure) {
      nextFailures.computeIfAbsent(method, ignored -> new ArrayDeque<>()).add(failure);
    }

    void failNext(String method, long id, RuntimeException failure) {
      idFailures
          .computeIfAbsent(method, ignored -> new HashMap<>())
          .computeIfAbsent(id, ignored -> new ArrayDeque<>())
          .add(failure);
    }

    private RuntimeException takeFailure(String method, Object[] args) {
      if (args != null && args.length > 0 && args[0] instanceof Long id) {
        Deque<RuntimeException> failures = idFailures.getOrDefault(method, Map.of()).get(id);
        if (failures != null && !failures.isEmpty()) return failures.removeFirst();
      }
      Deque<RuntimeException> failures = nextFailures.get(method);
      return failures == null || failures.isEmpty() ? null : failures.removeFirst();
    }
  }

  private static final class RecordingMusic {
    final List<String> calls = new ArrayList<>();
    final Map<String, Deque<RuntimeException>> failures = new HashMap<>();
    final Music music =
        (Music)
            Proxy.newProxyInstance(
                getClassLoader(),
                new Class[] {Music.class},
                (proxy, method, args) -> {
                  String name = method.getName();
                  calls.add(name);
                  Deque<RuntimeException> queue = failures.get(name);
                  if (queue != null && !queue.isEmpty()) throw queue.removeFirst();
                  Class<?> result = method.getReturnType();
                  if (result == boolean.class) return false;
                  if (result == int.class) return 0;
                  if (result == long.class) return 0L;
                  if (result == float.class) return 0f;
                  if (result == double.class) return 0d;
                  return null;
                });

    void failNext(String method, RuntimeException failure) {
      failures.computeIfAbsent(method, ignored -> new ArrayDeque<>()).add(failure);
    }
  }

  private static ClassLoader getClassLoader() {
    return SoundManagerTest.class.getClassLoader();
  }

  @Test
  public void voicesUseOneNativeIdAndNeverStopAnotherSoundVoice() {
    RecordingAudio audio = new RecordingAudio();
    long callerId = audio.sound.play();
    assertEquals(101, callerId);
    Game game = new Game();
    SoundManager manager = game.addSystem(new SoundManager());
    manager.setMasterVolume(.5f);
    manager.setBusVolume(AudioBus.SFX, .4f);
    VoiceHandle voice = manager.play(audio.sound, AudioBus.SFX, true, .5f, 1.25f, -.25f);
    assertEquals(.1f, audio.volumes.get(102L), .0001f);
    voice.pause();
    voice.resume();
    voice.setPan(.75f);
    voice.setPitch(.8f);
    voice.close();
    assertEquals(
        List.of("play:101", "loop:102", "pause:102", "resume:102", "pitch:102:0.8", "stop:102"),
        audio.calls.stream().filter(call -> !call.startsWith("pan:")).toList());
    assertFalse(audio.calls.contains("stop-all"));
    manager.dispose();
    game.close();
  }

  @Test
  public void capacityRetiresOldestLeaseAndStaleHandleCannotStopReplacement() {
    RecordingAudio audio = new RecordingAudio();
    try (Game game = new Game()) {
      SoundManager manager = game.addSystem(new SoundManager());
      manager.setMaxVoices(1);
      VoiceHandle first = manager.play(audio.sound, AudioBus.UI);
      VoiceHandle second = manager.play(audio.sound, AudioBus.UI);
      assertFalse(first.isActive());
      assertTrue(second.isActive());
      first.cancel();
      assertEquals(List.of("play:101", "stop:101", "play:102"), audio.calls);
      game.start();
      game.close();
      assertEquals(List.of("play:101", "stop:101", "play:102", "stop:102"), audio.calls);
      assertFalse(second.isActive());
    }
  }

  @Test
  public void pauseGatesBusWithoutLosingPerVoicePauseAndUnscaledDuckFade() {
    RecordingAudio audio = new RecordingAudio();
    try (Game game = new Game()) {
      SoundManager manager = game.addSystem(new SoundManager());
      VoiceHandle voice = manager.play(audio.sound, AudioBus.MUSIC);
      voice.pause();
      manager.pauseBus(AudioBus.MUSIC);
      voice.resume();
      manager.resumeBus(AudioBus.MUSIC);
      assertEquals(List.of("play:101", "pause:101", "resume:101"), audio.calls);

      manager.setBusVolume(AudioBus.MUSIC, 1);
      manager.fadeBus(AudioBus.MUSIC, .5f, .4);
      DuckHandle quiet = manager.duck(AudioBus.MUSIC, .25f, .2);
      DuckHandle quieter = manager.duck(AudioBus.MUSIC, .1f, .3);
      assertEquals(.1f, manager.getEffectiveBusVolume(AudioBus.MUSIC), .0001f);
      game.start();
      game.pause();
      game.step(.1f);
      game.step(.1f);
      assertEquals(.75f, manager.getBusVolume(AudioBus.MUSIC), .0001f);
      assertEquals(.075f, manager.getEffectiveBusVolume(AudioBus.MUSIC), .0001f);
      assertFalse(quiet.isActive());
      assertTrue(quieter.isActive());
      assertTrue(quieter.isActive());
      assertEquals(.075f, manager.getEffectiveBusVolume(AudioBus.MUSIC), .0001f);
      quieter.close();
      assertEquals(.75f, manager.getEffectiveBusVolume(AudioBus.MUSIC), .0001f);
    }
  }

  @Test
  public void musicPitchIsExplicitlyUnsupportedAndBadValuesFailBeforeNativePlay() {
    List<String> calls = new ArrayList<>();
    Music music =
        (Music)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] {Music.class},
                (proxy, method, args) -> {
                  calls.add(method.getName());
                  return null;
                });
    RecordingAudio audio = new RecordingAudio();
    try (Game game = new Game()) {
      SoundManager manager = game.addSystem(new SoundManager());
      assertThrows(
          IllegalArgumentException.class,
          () -> manager.play(audio.sound, AudioBus.SFX, false, 1, Float.NaN, 0));
      assertTrue(audio.calls.isEmpty());
      VoiceHandle stream = manager.play(music, AudioBus.MUSIC);
      assertThrows(IllegalArgumentException.class, () -> stream.setPitch(Float.NaN));
      assertThrows(UnsupportedOperationException.class, () -> stream.setPitch(1.2f));
      assertTrue(stream.isActive());
      stream.close();
      assertTrue(calls.contains("stop"));
    }
  }

  @Test
  public void separateMusicStreamsCoexistButReplayingSameStreamInvalidatesOnlyItsOldLease() {
    List<String> firstCalls = new ArrayList<>(), secondCalls = new ArrayList<>();
    Music firstMusic = recordingMusic(firstCalls);
    Music secondMusic = recordingMusic(secondCalls);
    try (Game game = new Game()) {
      SoundManager manager = game.addSystem(new SoundManager());
      VoiceHandle first = manager.play(firstMusic, AudioBus.MUSIC);
      VoiceHandle second = manager.play(secondMusic, AudioBus.MUSIC);
      assertTrue(first.isActive());
      assertTrue(second.isActive());
      VoiceHandle replacement = manager.play(firstMusic, AudioBus.MUSIC);
      assertFalse(first.isActive());
      assertTrue(second.isActive());
      assertTrue(replacement.isActive());
      assertEquals(1, firstCalls.stream().filter("stop"::equals).count());
      assertEquals(0, secondCalls.stream().filter("stop"::equals).count());
    }
  }

  @Test
  public void duckCollectionIsBoundedAndCleanupAttemptsEveryNativeVoice() {
    RecordingAudio audio = new RecordingAudio();
    try (Game game = new Game()) {
      SoundManager manager = game.addSystem(new SoundManager());
      manager.setMaxDucks(1);
      DuckHandle older = manager.duck(AudioBus.SFX, .4f, 10);
      DuckHandle newer = manager.duck(AudioBus.UI, .2f, 10);
      assertFalse(older.isActive());
      assertTrue(newer.isActive());
      assertEquals(1, manager.getManagedDuckCount());

      manager.play(audio.sound, AudioBus.SFX);
      manager.play(audio.sound, AudioBus.SFX);
      RuntimeException first = new IllegalStateException("stop one");
      RuntimeException second = new IllegalArgumentException("stop two");
      audio.failNext("stop", 101, first);
      audio.failNext("stop", 102, second);
      IllegalStateException failure = assertThrows(IllegalStateException.class, manager::dispose);
      assertSame(first, failure);
      assertArrayEquals(new Throwable[] {second}, failure.getSuppressed());
      assertEquals(List.of("play:101", "play:102", "stop:101", "stop:102"), audio.calls);
      assertEquals("Failed native releases remain tracked", 2, manager.getManagedVoiceCount());
      assertFalse(newer.isActive());
    }
  }

  @Test
  public void pausedSoundAcquisitionRollsBackOnlyItsReturnedId() {
    RecordingAudio audio = new RecordingAudio();
    long callerId = audio.sound.play();
    assertEquals(101, callerId);
    try (Game game = new Game()) {
      SoundManager manager = game.addSystem(new SoundManager());
      manager.pauseBus(AudioBus.SFX);
      IllegalStateException pauseFailure = new IllegalStateException("pause failed");
      audio.failNext("pause", 102, pauseFailure);
      IllegalStateException observed =
          assertThrows(IllegalStateException.class, () -> manager.play(audio.sound, AudioBus.SFX));
      assertSame(pauseFailure, observed);
      assertEquals(List.of("play:101", "play:102", "pause:102", "stop:102"), audio.calls);
      assertEquals(0, manager.getManagedVoiceCount());
      assertFalse(audio.calls.contains("stop-all"));
    }
  }

  @Test
  public void failedRollbackPreservesPrimaryAndSceneCloseRetriesPendingId() {
    RecordingAudio audio = new RecordingAudio();
    Game game = new Game();
    SoundManager manager = game.addSystem(new SoundManager());
    manager.pauseBus(AudioBus.SFX);
    IllegalStateException pauseFailure = new IllegalStateException("pause failed");
    IllegalArgumentException rollbackFailure = new IllegalArgumentException("rollback failed");
    audio.failNext("pause", 101, pauseFailure);
    audio.failNext("stop", 101, rollbackFailure);
    IllegalStateException observed =
        assertThrows(IllegalStateException.class, () -> manager.play(audio.sound, AudioBus.SFX));
    assertSame(pauseFailure, observed);
    assertArrayEquals(new Throwable[] {rollbackFailure}, observed.getSuppressed());
    assertEquals(1, manager.getManagedVoiceCount());
    assertEquals(List.of("play:101", "pause:101", "stop:101"), audio.calls);
    game.close();
    assertEquals(List.of("play:101", "pause:101", "stop:101", "stop:101"), audio.calls);
    assertEquals(0, manager.getManagedVoiceCount());
  }

  @Test
  public void manualStopFailureDeactivatesHandleAndCloseCanRetryItsNativeId() {
    RecordingAudio audio = new RecordingAudio();
    try (Game game = new Game()) {
      SoundManager manager = game.addSystem(new SoundManager());
      VoiceHandle voice = manager.play(audio.sound, AudioBus.UI);
      IllegalStateException stopFailure = new IllegalStateException("manual stop failed");
      audio.failNext("stop", 101, stopFailure);
      assertSame(stopFailure, assertThrows(IllegalStateException.class, voice::stop));
      assertFalse(voice.isActive());
      assertEquals(1, manager.getManagedVoiceCount());
      assertThrows(IllegalStateException.class, () -> voice.setVolume(.5f));
      voice.close();
      assertEquals(0, manager.getManagedVoiceCount());
      assertEquals(List.of("play:101", "stop:101", "stop:101"), audio.calls);
    }
  }

  @Test
  public void failedCapacityRetirementKeepsOldIdTrackedAndDoesNotAcquireReplacement() {
    RecordingAudio audio = new RecordingAudio();
    try (Game game = new Game()) {
      SoundManager manager = game.addSystem(new SoundManager());
      manager.setMaxVoices(1);
      VoiceHandle oldest = manager.play(audio.sound, AudioBus.SFX);
      IllegalStateException stopFailure = new IllegalStateException("retirement failed");
      audio.failNext("stop", 101, stopFailure);
      assertSame(
          stopFailure,
          assertThrows(IllegalStateException.class, () -> manager.play(audio.sound, AudioBus.SFX)));
      assertFalse(oldest.isActive());
      assertEquals(1, manager.getManagedVoiceCount());
      assertEquals(List.of("play:101", "stop:101"), audio.calls);
      oldest.close();
      VoiceHandle replacement = manager.play(audio.sound, AudioBus.SFX);
      assertTrue(replacement.isActive());
      assertEquals(List.of("play:101", "stop:101", "stop:101", "play:102"), audio.calls);
    }
  }

  @Test
  public void musicSetupPlayAndPausedAcquisitionFailuresStopOnlyTheFailedStream() {
    RecordingMusic setupFailure = new RecordingMusic();
    RecordingMusic playFailure = new RecordingMusic();
    RecordingMusic pauseFailure = new RecordingMusic();
    RecordingMusic peer = new RecordingMusic();
    try (Game game = new Game()) {
      SoundManager manager = game.addSystem(new SoundManager());
      VoiceHandle peerHandle = manager.play(peer.music, AudioBus.MUSIC);
      IllegalStateException setup = new IllegalStateException("setup");
      setupFailure.failNext("setLooping", setup);
      assertSame(
          setup,
          assertThrows(
              IllegalStateException.class, () -> manager.play(setupFailure.music, AudioBus.MUSIC)));
      assertEquals(List.of("setLooping", "stop"), setupFailure.calls);

      IllegalArgumentException play = new IllegalArgumentException("play");
      playFailure.failNext("play", play);
      assertSame(
          play,
          assertThrows(
              IllegalArgumentException.class,
              () -> manager.play(playFailure.music, AudioBus.MUSIC)));
      assertEquals(List.of("setLooping", "setPan", "play", "stop"), playFailure.calls);

      manager.pauseBus(AudioBus.MUSIC);
      IllegalStateException pause = new IllegalStateException("pause");
      pauseFailure.failNext("pause", pause);
      assertSame(
          pause,
          assertThrows(
              IllegalStateException.class, () -> manager.play(pauseFailure.music, AudioBus.MUSIC)));
      assertEquals(List.of("setLooping", "setPan", "play", "pause", "stop"), pauseFailure.calls);
      assertTrue(peerHandle.isActive());
      assertFalse(peer.calls.contains("stop"));
      assertEquals(1, manager.getManagedVoiceCount());
    }
  }

  @Test
  public void musicRollbackStopFailureRetainsStreamForSceneCloseRetry() {
    RecordingMusic music = new RecordingMusic();
    Game game = new Game();
    SoundManager manager = game.addSystem(new SoundManager());
    manager.pauseBus(AudioBus.MUSIC);
    IllegalStateException pauseFailure = new IllegalStateException("pause");
    IllegalArgumentException stopFailure = new IllegalArgumentException("stop");
    music.failNext("pause", pauseFailure);
    music.failNext("stop", stopFailure);
    assertSame(
        pauseFailure,
        assertThrows(IllegalStateException.class, () -> manager.play(music.music, AudioBus.MUSIC)));
    assertArrayEquals(new Throwable[] {stopFailure}, pauseFailure.getSuppressed());
    assertEquals(1, manager.getManagedVoiceCount());
    game.close();
    assertEquals(List.of("setLooping", "setPan", "play", "pause", "stop", "stop"), music.calls);
    assertEquals(0, manager.getManagedVoiceCount());
  }

  @Test
  public void soundPlayFailureWithoutIdNeverUsesGlobalStop() {
    RecordingAudio audio = new RecordingAudio();
    try (Game game = new Game()) {
      SoundManager manager = game.addSystem(new SoundManager());
      IllegalStateException failure = new IllegalStateException("play before id");
      audio.failNext("play", failure);
      assertSame(
          failure,
          assertThrows(IllegalStateException.class, () -> manager.play(audio.sound, AudioBus.SFX)));
      assertEquals(0, manager.getManagedVoiceCount());
      assertTrue(audio.calls.isEmpty());
    }
  }

  private static Music recordingMusic(List<String> calls) {
    return (Music)
        Proxy.newProxyInstance(
            SoundManagerTest.class.getClassLoader(),
            new Class[] {Music.class},
            (proxy, method, args) -> {
              calls.add(method.getName());
              return null;
            });
  }
}
