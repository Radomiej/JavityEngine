
package org.javity.engine;

import com.badlogic.gdx.scenes.scene2d.Actor;

public abstract class GUIComponent extends DefaultComponent{

	Actor getScene2dActor() {
		return getActor();
	}

	protected abstract Actor getActor();
}
