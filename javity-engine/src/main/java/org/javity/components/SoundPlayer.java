package org.javity.components;

import org.javity.engine.Component;
import org.javity.engine.JComponent;
import org.javity.engine.resources.MusicResource;
import org.javity.engine.resources.SoundResource;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.LongArray;

import galaxy.rapid.asset.RapidAsset;

public class SoundPlayer extends JComponent {

	private boolean backgroundMusic;
	private SoundResource sound;
	private MusicResource music;
	private boolean loop;
	private LongArray soundIds = new LongArray(2);

	public SoundPlayer(String path) {
		this(path, false);
	}

	public SoundPlayer(String path, boolean isBackgroundMusic) {
		this.backgroundMusic = isBackgroundMusic;
		if (backgroundMusic) {
			music = new MusicResource(path);
		} else {
			sound = new SoundResource(path);
		}
	}

	public void play() {
		if (backgroundMusic) {
			Music musicAsset = RapidAsset.INSTANCE.getMusic(music.getResourcePath());
			musicAsset.setLooping(loop);
			musicAsset.play();
		} else {
			Sound soundAsset = RapidAsset.INSTANCE.getSound(sound.getResourcePath());
			long id = soundAsset.play();
			soundIds.add(id);
			soundAsset.setLooping(id, loop);
		}
	}

	public void stop() {
		if (backgroundMusic) {
			Music musicAsset = RapidAsset.INSTANCE.getMusic(music.getResourcePath());
			musicAsset.stop();
		} else {
			Sound soundAsset = RapidAsset.INSTANCE.getSound(sound.getResourcePath());
			if (loop) {
				soundAsset.stop();

			} else {
				soundAsset.play();
			}
		}
	}

	public SoundPlayer setLooping(boolean loop) {
		this.loop = loop;
		if (!loop) {
			stopLooping();
		}
		return this;
	}

	private void stopLooping() {
		if (backgroundMusic) {
			Music musicAsset = RapidAsset.INSTANCE.getMusic(music.getResourcePath());
			musicAsset.setLooping(false);
			return;
		}

		Sound soundAsset = RapidAsset.INSTANCE.getSound(sound.getResourcePath());
		for (long id : soundIds.items) {
			soundAsset.setLooping(id, false);
		}
	}
}
