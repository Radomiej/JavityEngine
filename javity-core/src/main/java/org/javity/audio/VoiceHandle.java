package org.javity.audio;

/** A lease on one manager-owned native voice. Closing it stops only that voice. */
public final class VoiceHandle implements AutoCloseable {
  final SoundManager manager;
  final SoundManager.Voice voice;

  VoiceHandle(SoundManager manager, SoundManager.Voice voice) {
    this.manager = manager;
    this.voice = voice;
  }

  public boolean isActive() {
    return manager.isActive(voice);
  }

  public void setVolume(float volume) {
    manager.setVoiceVolume(voice, volume);
  }

  public void setPan(float pan) {
    manager.setVoicePan(voice, pan);
  }

  public void setPitch(float pitch) {
    manager.setVoicePitch(voice, pitch);
  }

  public void pause() {
    manager.pauseVoice(voice);
  }

  public void resume() {
    manager.resumeVoice(voice);
  }

  public void stop() {
    manager.stopVoice(voice);
  }

  /** Ends this lease using its own native voice identifier. */
  public void cancel() {
    stop();
  }

  @Override
  public void close() {
    stop();
  }
}
