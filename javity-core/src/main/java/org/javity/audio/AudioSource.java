package org.javity.audio;

import com.badlogic.gdx.audio.*;
import java.util.*;
import org.javity.core.Component;

/** Sound voices or an exclusive Music stream. Asset ownership explicit; pause/stop are explicit. */
public final class AudioSource extends Component {
  private final Sound sound;
  private final Music music;
  private final boolean owned;
  private final Deque<Long> voices = new ArrayDeque<>();
  private boolean closed, loop;
  private float volume = 1, pitch = 1, pan;
  private int maxVoices = 16;

  public AudioSource(Sound sound, boolean owned) {
    this.sound = Objects.requireNonNull(sound);
    music = null;
    this.owned = owned;
  }

  public AudioSource(Music music, boolean owned) {
    this.music = Objects.requireNonNull(music);
    sound = null;
    this.owned = owned;
  }

  private void live() {
    if (closed) throw new IllegalStateException("Audio closed");
  }

  public void setMaxVoices(int count) {
    live();
    if (count < 1) throw new IllegalArgumentException("Positive voice limit required");
    maxVoices = count;
    trim();
  }

  private void trim() {
    while (voices.size() > maxVoices) sound.stop(voices.removeFirst());
  }

  public void setVolume(float value) {
    live();
    if (!Float.isFinite(value) || value < 0 || value > 1)
      throw new IllegalArgumentException("Volume 0..1");
    volume = value;
    if (music != null) music.setVolume(value);
    else for (long id : voices) sound.setVolume(id, value);
  }

  public void setPitch(float value) {
    live();
    if (!Float.isFinite(value) || value <= 0)
      throw new IllegalArgumentException("Positive pitch required");
    pitch = value;
    if (sound != null) for (long id : voices) sound.setPitch(id, value);
  }

  public void setLooping(boolean value) {
    live();
    loop = value;
    if (music != null) music.setLooping(value);
    else for (long id : voices) sound.setLooping(id, value);
  }

  public void setPan(float value) {
    live();
    if (!Float.isFinite(value) || value < -1 || value > 1)
      throw new IllegalArgumentException("Pan -1..1");
    pan = value;
    if (music != null) music.setPan(pan, volume);
    else for (long id : voices) sound.setPan(id, pan, volume);
  }

  public void play() {
    live();
    if (music != null) {
      music.setLooping(loop);
      music.setPan(pan, volume);
      music.play();
    } else {
      long id = loop ? sound.loop(volume, pitch, pan) : sound.play(volume, pitch, pan);
      if (id >= 0) {
        voices.addLast(id);
        trim();
      }
    }
  }

  public void stop() {
    live();
    stopVoices();
  }

  private void stopVoices() {
    if (music != null) music.stop();
    else {
      for (long id : voices) sound.stop(id);
      voices.clear();
    }
  }

  public void pause() {
    live();
    if (music != null) music.pause();
    else for (long id : voices) sound.pause(id);
  }

  public void resume() {
    live();
    if (music != null) music.play();
    else for (long id : voices) sound.resume(id);
  }

  protected void onRelease() {
    if (closed) return;
    closed = true;
    try {
      stopVoices();
    } finally {
      if (owned) {
        if (sound != null) sound.dispose();
        else music.dispose();
      }
    }
  }
}
