package org.javity.audio;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import java.util.*;
import org.javity.core.GameSystem;

/** Scene-owned mixer for caller-owned assets and manager-owned playback leases. */
public final class SoundManager extends GameSystem {
  static final int DEFAULT_MAX_VOICES = 64;
  static final int DEFAULT_MAX_DUCKS = 64;

  static final class Voice {
    final Sound sound;
    final Music music;
    final AudioBus bus;
    final long id;
    final long order;
    float volume, pitch, pan;
    boolean active, pausedByHandle, nativePaused;
    boolean nativeOwned;

    Voice(
        Sound sound,
        Music music,
        AudioBus bus,
        long id,
        long order,
        float volume,
        float pitch,
        float pan,
        boolean active) {
      this.sound = sound;
      this.music = music;
      this.bus = bus;
      this.id = id;
      this.order = order;
      this.volume = volume;
      this.pitch = pitch;
      this.pan = pan;
      this.active = active;
      this.nativeOwned = active;
    }
  }

  static final class Duck {
    final AudioBus bus;
    final float multiplier;
    double remaining;
    boolean active = true;

    Duck(AudioBus bus, float multiplier, double remaining) {
      this.bus = bus;
      this.multiplier = multiplier;
      this.remaining = remaining;
    }
  }

  private static final class Fade {
    final float from, to;
    final double duration;
    double elapsed;

    Fade(float from, float to, double duration) {
      this.from = from;
      this.to = to;
      this.duration = duration;
    }
  }

  private final EnumMap<AudioBus, Float> busVolumes = new EnumMap<>(AudioBus.class);
  private final EnumMap<AudioBus, Boolean> busPaused = new EnumMap<>(AudioBus.class);
  private final EnumMap<AudioBus, Fade> fades = new EnumMap<>(AudioBus.class);
  private final List<Voice> voices = new ArrayList<>();
  private final List<Duck> ducks = new ArrayList<>();
  private float masterVolume = 1;
  private int maxVoices = DEFAULT_MAX_VOICES;
  private int maxDucks = DEFAULT_MAX_DUCKS;
  private long nextOrder;
  private boolean released;

  public SoundManager() {
    for (AudioBus bus : AudioBus.values()) {
      busVolumes.put(bus, 1f);
      busPaused.put(bus, false);
    }
  }

  public VoiceHandle play(Sound sound, AudioBus bus) {
    return play(sound, bus, false, 1, 1, 0);
  }

  public VoiceHandle play(
      Sound sound, AudioBus bus, boolean looping, float volume, float pitch, float pan) {
    live();
    Objects.requireNonNull(sound, "sound");
    Objects.requireNonNull(bus, "bus");
    validateVoice(volume, pitch, pan);
    retireForCapacity();
    long id =
        looping
            ? sound.loop(volume * effectiveGain(bus), pitch, pan)
            : sound.play(volume * effectiveGain(bus), pitch, pan);
    Voice voice = new Voice(sound, null, bus, id, nextOrder++, volume, pitch, pan, id >= 0);
    if (!voice.nativeOwned) return new VoiceHandle(this, voice);
    voices.add(voice);
    if (busPaused.get(bus)) {
      try {
        syncPause(voice);
      } catch (RuntimeException | Error failure) {
        rollbackAcquisition(voice, failure);
        throw failure;
      }
    }
    return new VoiceHandle(this, voice);
  }

  public VoiceHandle play(Music music, AudioBus bus) {
    return play(music, bus, true, 1, 0);
  }

  /** Music is an exclusive borrowed stream while its returned lease is active. */
  public VoiceHandle play(Music music, AudioBus bus, boolean looping, float volume, float pan) {
    live();
    Objects.requireNonNull(music, "music");
    Objects.requireNonNull(bus, "bus");
    validateVoice(volume, 1, pan);
    for (Voice voice : List.copyOf(voices)) if (voice.music == music) stopVoice(voice);
    retireForCapacity();
    Voice voice = new Voice(null, music, bus, 0, nextOrder++, volume, 1, pan, true);
    voices.add(voice);
    try {
      music.setLooping(looping);
      music.setPan(pan, volume * effectiveGain(bus));
      music.play();
      if (busPaused.get(bus)) syncPause(voice);
    } catch (RuntimeException | Error failure) {
      rollbackAcquisition(voice, failure);
      throw failure;
    }
    return new VoiceHandle(this, voice);
  }

  public void setMasterVolume(float volume) {
    live();
    validateGain(volume);
    masterVolume = volume;
    refreshVoices();
  }

  public float getMasterVolume() {
    return masterVolume;
  }

  public void setBusVolume(AudioBus bus, float volume) {
    live();
    Objects.requireNonNull(bus, "bus");
    validateGain(volume);
    fades.remove(bus);
    busVolumes.put(bus, volume);
    refreshVoices();
  }

  public float getBusVolume(AudioBus bus) {
    return busVolumes.get(Objects.requireNonNull(bus, "bus"));
  }

  public float getEffectiveBusVolume(AudioBus bus) {
    return effectiveGain(Objects.requireNonNull(bus, "bus"));
  }

  /** Fades base bus gain on unscaled scene time; active ducks remain multiplicative. */
  public void fadeBus(AudioBus bus, float targetVolume, double unscaledSeconds) {
    live();
    Objects.requireNonNull(bus, "bus");
    validateGain(targetVolume);
    validateDuration(unscaledSeconds);
    float from = busVolumes.get(bus);
    if (unscaledSeconds == 0) {
      fades.remove(bus);
      busVolumes.put(bus, targetVolume);
      refreshVoices();
    } else {
      fades.put(bus, new Fade(from, targetVolume, unscaledSeconds));
    }
  }

  /** Applies the lowest active multiplier, without replacing the base bus gain. */
  public DuckHandle duck(AudioBus bus, float multiplier, double unscaledSeconds) {
    live();
    Objects.requireNonNull(bus, "bus");
    validateGain(multiplier);
    validateDuration(unscaledSeconds);
    Duck duck = new Duck(bus, multiplier, unscaledSeconds);
    if (unscaledSeconds > 0) {
      if (ducks.size() >= maxDucks) {
        Duck oldest = ducks.getFirst();
        oldest.active = false;
        ducks.removeFirst();
      }
      ducks.add(duck);
      refreshVoices();
    } else duck.active = false;
    return new DuckHandle(this, duck);
  }

  public void pauseBus(AudioBus bus) {
    live();
    Objects.requireNonNull(bus, "bus");
    if (busPaused.put(bus, true)) return;
    for (Voice voice : voices) if (voice.bus == bus) syncPause(voice);
  }

  public void resumeBus(AudioBus bus) {
    live();
    Objects.requireNonNull(bus, "bus");
    if (!busPaused.put(bus, false)) return;
    for (Voice voice : voices) if (voice.bus == bus) syncPause(voice);
  }

  public boolean isBusPaused(AudioBus bus) {
    return busPaused.get(Objects.requireNonNull(bus, "bus"));
  }

  public void setMaxVoices(int maxVoices) {
    live();
    if (maxVoices < 1) throw new IllegalArgumentException("Voice limit must be positive");
    this.maxVoices = maxVoices;
    while (voices.size() > maxVoices) stopVoice(voices.getFirst());
  }

  public int getMaxVoices() {
    return maxVoices;
  }

  public int getManagedVoiceCount() {
    return voices.size();
  }

  public void setMaxDucks(int maxDucks) {
    live();
    if (maxDucks < 1) throw new IllegalArgumentException("Duck limit must be positive");
    this.maxDucks = maxDucks;
    while (ducks.size() > maxDucks) {
      Duck oldest = ducks.removeFirst();
      oldest.active = false;
    }
    refreshVoices();
  }

  public int getMaxDucks() {
    return maxDucks;
  }

  public int getManagedDuckCount() {
    return ducks.size();
  }

  @Override
  public void onUiUpdate(float unscaledDelta) {
    if (released || unscaledDelta <= 0) return;
    boolean changed = false;
    for (AudioBus bus : AudioBus.values()) {
      Fade fade = fades.get(bus);
      if (fade == null) continue;
      fade.elapsed = Math.min(fade.duration, fade.elapsed + unscaledDelta);
      float progress = (float) (fade.elapsed / fade.duration);
      busVolumes.put(bus, fade.from + (fade.to - fade.from) * progress);
      if (fade.elapsed >= fade.duration) {
        busVolumes.put(bus, fade.to);
        fades.remove(bus);
      }
      changed = true;
    }
    for (Duck duck : List.copyOf(ducks)) {
      duck.remaining -= unscaledDelta;
      if (duck.remaining <= 0) {
        cancelDuck(duck);
        changed = true;
      }
    }
    if (changed) refreshVoices();
  }

  boolean isActive(Voice voice) {
    return voice != null && voice.active && !released && voices.contains(voice);
  }

  void setVoiceVolume(Voice voice, float volume) {
    requireVoice(voice);
    validateGain(volume);
    voice.volume = volume;
    updateNativeVolume(voice);
  }

  void setVoicePan(Voice voice, float pan) {
    requireVoice(voice);
    if (!Float.isFinite(pan) || pan < -1 || pan > 1)
      throw new IllegalArgumentException("Pan must be in [-1, 1]");
    voice.pan = pan;
    if (voice.sound != null)
      voice.sound.setPan(voice.id, pan, voice.volume * effectiveGain(voice.bus));
    else voice.music.setPan(pan, voice.volume * effectiveGain(voice.bus));
  }

  void setVoicePitch(Voice voice, float pitch) {
    requireVoice(voice);
    if (!Float.isFinite(pitch) || pitch <= 0)
      throw new IllegalArgumentException("Pitch must be finite and positive");
    if (voice.sound == null)
      throw new UnsupportedOperationException("Music pitch is not supported");
    voice.pitch = pitch;
    voice.sound.setPitch(voice.id, pitch);
  }

  void pauseVoice(Voice voice) {
    requireVoice(voice);
    if (voice.pausedByHandle) return;
    voice.pausedByHandle = true;
    syncPause(voice);
  }

  void resumeVoice(Voice voice) {
    requireVoice(voice);
    if (!voice.pausedByHandle) return;
    voice.pausedByHandle = false;
    syncPause(voice);
  }

  void stopVoice(Voice voice) {
    if (voice == null || !voices.contains(voice)) return;
    voice.active = false;
    stopNative(voice);
  }

  boolean isDuckActive(Duck duck) {
    return duck != null && duck.active && !released && ducks.contains(duck);
  }

  void cancelDuck(Duck duck) {
    if (!isDuckActive(duck)) return;
    duck.active = false;
    ducks.remove(duck);
    refreshVoices();
  }

  private void nativePause(Voice voice) {
    if (voice.sound != null) voice.sound.pause(voice.id);
    else voice.music.pause();
  }

  private void nativeResume(Voice voice) {
    if (voice.sound != null) voice.sound.resume(voice.id);
    else voice.music.play();
  }

  private void syncPause(Voice voice) {
    boolean shouldPause = voice.pausedByHandle || busPaused.get(voice.bus);
    if (shouldPause == voice.nativePaused) return;
    if (shouldPause) nativePause(voice);
    else nativeResume(voice);
    voice.nativePaused = shouldPause;
  }

  private void requireVoice(Voice voice) {
    live();
    if (!isActive(voice)) throw new IllegalStateException("Voice lease is no longer active");
  }

  private void retireForCapacity() {
    while (voices.size() >= maxVoices) stopVoice(voices.getFirst());
  }

  private float effectiveGain(AudioBus bus) {
    float duck = 1;
    for (Duck active : ducks)
      if (active.active && active.bus == bus) duck = Math.min(duck, active.multiplier);
    return masterVolume * busVolumes.get(bus) * duck;
  }

  private void refreshVoices() {
    for (Voice voice : List.copyOf(voices)) updateNativeVolume(voice);
  }

  private void updateNativeVolume(Voice voice) {
    float volume = voice.volume * effectiveGain(voice.bus);
    if (voice.sound != null) {
      voice.sound.setVolume(voice.id, volume);
      voice.sound.setPan(voice.id, voice.pan, volume);
    } else {
      voice.music.setVolume(volume);
      voice.music.setPan(voice.pan, volume);
    }
  }

  private void live() {
    if (released || isDisposed()) throw new IllegalStateException("SoundManager disposed");
  }

  @Override
  protected void onRelease() {
    released = true;
    Throwable failure = null;
    for (Voice voice : List.copyOf(voices)) {
      voice.active = false;
      try {
        stopNative(voice);
      } catch (RuntimeException | Error error) {
        if (failure == null) failure = error;
        else if (failure != error) failure.addSuppressed(error);
      }
    }
    for (Duck duck : ducks) duck.active = false;
    ducks.clear();
    fades.clear();
    if (failure instanceof RuntimeException error) throw error;
    if (failure instanceof Error error) throw error;
  }

  private void rollbackAcquisition(Voice voice, Throwable failure) {
    voice.active = false;
    try {
      stopNative(voice);
    } catch (RuntimeException | Error cleanupFailure) {
      if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
    }
  }

  private void stopNative(Voice voice) {
    if (!voice.nativeOwned) {
      voices.remove(voice);
      return;
    }
    if (voice.sound != null) voice.sound.stop(voice.id);
    else voice.music.stop();
    voice.nativeOwned = false;
    voice.active = false;
    voices.remove(voice);
  }

  private static void validateVoice(float volume, float pitch, float pan) {
    validateGain(volume);
    if (!Float.isFinite(pitch) || pitch <= 0)
      throw new IllegalArgumentException("Positive pitch required");
    if (!Float.isFinite(pan) || pan < -1 || pan > 1)
      throw new IllegalArgumentException("Pan must be in [-1, 1]");
  }

  private static void validateGain(float volume) {
    if (!Float.isFinite(volume) || volume < 0 || volume > 1)
      throw new IllegalArgumentException("Gain must be finite and in [0, 1]");
  }

  private static void validateDuration(double seconds) {
    if (!Double.isFinite(seconds) || seconds < 0)
      throw new IllegalArgumentException("Duration must be finite and nonnegative");
  }
}
