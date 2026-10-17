package org.javity.components.reflection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.javity.engine.Component;
import org.javity.engine.CustomScene;
import org.javity.engine.GameObjectProxy;
import org.javity.engine.JGameObject;
import org.javity.engine.JGameObjectImpl;
import org.javity.engine.JScene;

import com.badlogic.gdx.Gdx;

import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.Field;


public class GameObjectProxator {
	private Map<String, JGameObjectImpl> orginalObjects = new HashMap<String, JGameObjectImpl>();
	
	public void proxy(JGameObject masterGameObject, CustomScene scene) {
		for (Component component : masterGameObject.getAllComponents()) {
			for (Field field : ClassReflection.getFields(component.getClass())) {
				if (field.getName().equals("gameObject"))
					continue;
				if (field.getType().equals(JGameObject.class)) {
					JGameObject gameObject = ReferenceFields.read(field, component);
					if (gameObject == null)
						continue;

					orginalObjects.put(gameObject.getObjectId(), (JGameObjectImpl) gameObject);
					GameObjectProxy proxyGameObject = new GameObjectProxy(gameObject);
					ReferenceFields.write(field, component, proxyGameObject);

				}
			}
		}
	}

	public void unproxy(JGameObject masterGameObject, CustomScene scene) {
		for (Component component : masterGameObject.getAllComponents()) {
			for (Field field : ClassReflection.getFields(component.getClass())) {
				if (field.getName().equals("gameObject"))
					continue;
				if (field.getType().equals(JGameObject.class)) {
					JGameObject gameObject = ReferenceFields.read(field, component);
					if (gameObject == null)
						continue;
					UUID uuid = UUID.fromString(gameObject.getObjectId());
					if (orginalObjects.containsKey(gameObject.getObjectId())) {

						JGameObject existGameObject = orginalObjects.get(gameObject.getObjectId());
//						System.out.println("orginal object: " + existGameObject);
						ReferenceFields.write(field, component, existGameObject);
//						gameObject = field.getFieldValue(component, JGameObjectImpl.class);
					} else {
						Gdx.app.error("GameObjectProxator", "Proxy to non exist object! uuid: " + uuid);
					}
				}
			}
		}
	}
}
