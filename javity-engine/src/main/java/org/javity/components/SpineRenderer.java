package org.javity.components;

import org.javity.engine.JTime;
import org.javity.engine.NativeComponent;
import org.javity.engine.resources.SpineResource;
import org.javity.engine.resources.SpriteAtlasResource;
import org.javity.engine.resources.SpriteResource;

import com.badlogic.gdx.math.Vector2;

import galaxy.rapid.asset.RapidAsset;
import galaxy.rapid.asset.SpineAssetModel;
import galaxy.rapid.components.RenderComponent;
import galaxy.rapid.components.SpineComponent;
import galaxy.rapid.components.SpriteComponent;

public class SpineRenderer extends NativeComponent {
	private SpineResource spine;
	private transient RenderComponent renderComponent;
	private transient SpineComponent spineComponent;
	private Vector2 offset;

	public SpineRenderer() {
	}

	public SpineRenderer(String spinePath) {
		this(spinePath, new Vector2());
	}

	public SpineRenderer(SpineResource spineResource) {
		spine = spineResource;
	}

	public SpineRenderer(String spinePath, Vector2 offset) {
		String[] spinePathParts = spinePath.split("#");
		spine = new SpineResource(spinePathParts[0], spinePathParts[1] != null ? spinePathParts[1] : "default");
		this.offset = offset;
	}

	@Override
	public void awake() {
		renderComponent = new RenderComponent();
		addNativeComponent(renderComponent);

		RapidAsset.INSTANCE.loadSpine(spine.getResourcePath());
		SpineAssetModel spineAssetModel = RapidAsset.INSTANCE.getSpine(spine.getResourcePath(), spine.getSkinName());
		spineComponent = new SpineComponent(spineAssetModel);
		spineComponent.setOffset(offset);
		addNativeComponent(spineComponent);
	}

	@Override
	public void update() {
		renderComponent.setOrderZ(getTransform().getOrderZ());
	}

	public void play(String animationName, boolean loop) {
		spineComponent.getAnimationState().setAnimation(0, animationName, loop);
	}

	public void play(int index, String animationName, boolean loop) {
		spineComponent.getAnimationState().setAnimation(index, animationName, loop);
	}

	/**
	 * Play this animation clip after end current animation.
	 * 
	 * @param index
	 * @param animationName
	 *            the name of next animation
	 * @param loop
	 * @param delay
	 *            delay between starting next animation
	 */
	public void playNext(int index, String animationName, boolean loop, float delay) {
		spineComponent.getAnimationState().addAnimation(index, animationName, loop, delay);
	}

	public SpineResource getSpine() {
		return spine;
	}

	@Override
	public void onEnabled() {
		renderComponent.setRender(true);
	}

	@Override
	public void onDisable() {
		renderComponent.setRender(false);
	}
}
