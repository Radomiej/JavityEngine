package org.javity.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.javity.components.RectangleCollider;
import org.javity.components.Transform;
import org.javity.engine.gui.JCanvas;

import com.artemis.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.OrderedMap;

public class GameObjectProxy extends JGameObject{

	private transient JGameObject orginalObject;
	public GameObjectProxy(JGameObject gameObject) {
		this.orginalObject = gameObject;
		objectId = gameObject.objectId;
	}
	
	public GameObjectProxy() {
	}

	private void initOrginalObject(){
		
	}
	
	@Override
	public void start() {
		Gdx.app.error("GameObjectProxy", "nothing start() mehtod is execute!"); 
	}

	@Override
	public void awake() {
		Gdx.app.error("GameObjectProxy", "nothing awake() mehtod is execute!"); 
	}

	@Override
	public <T extends Component> T getComponentInParent(Class<T> class1) {
		return null;
	}

	@Override
	public <T extends Component> Iterable<T> getComponentsInParent(Class<T> class1) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T extends Component> Iterable<T> getComponents(Class<T> componentType) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T extends Component> T getComponent(Class<T> componentType) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isStarted() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Transform getTransform() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<Component> getAllComponents() {
		// TODO Auto-generated method stub
		return null;
	}
}
