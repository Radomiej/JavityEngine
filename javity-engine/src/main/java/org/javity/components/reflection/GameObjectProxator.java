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

import pl.silver.reflection.ReflectionBean;
import pl.silver.reflection.SilverField;
import pl.silver.reflection.SilverReflectionUtills;

public class GameObjectProxator {
	private Map<String, JGameObjectImpl> orginalObjects = new HashMap<String, JGameObjectImpl>();
	
	public void proxy(JGameObject masterGameObject, CustomScene scene) {
		for (Component component : masterGameObject.getAllComponents()) {
			ReflectionBean componentBean = SilverReflectionUtills.createReflectionBean(component.getClass());
			for (SilverField field : componentBean.getPublicAccesFields()) {
				if (field.getFieldName().equals("gameObject"))
					continue;
				if (field.getFieldValueClass().equals(JGameObject.class)) {
					JGameObject gameObject = field.getFieldValue(component, JGameObject.class);
					if (gameObject == null)
						continue;

					orginalObjects.put(gameObject.getObjectId(), (JGameObjectImpl) gameObject);
					GameObjectProxy proxyGameObject = new GameObjectProxy(gameObject);
					field.setFieldValue(component, proxyGameObject);

				}
			}
		}
	}

	public void unproxy(JGameObject masterGameObject, CustomScene scene) {
		for (Component component : masterGameObject.getAllComponents()) {
			ReflectionBean componentBean = SilverReflectionUtills.createReflectionBean(component.getClass());
			for (SilverField field : componentBean.getPublicAccesFields()) {
				if (field.getFieldName().equals("gameObject"))
					continue;
				if (field.getFieldValueClass().equals(JGameObject.class)) {
					JGameObject gameObject = field.getFieldValue(component, JGameObject.class);
					if (gameObject == null)
						continue;
					UUID uuid = UUID.fromString(gameObject.getObjectId());
					if (orginalObjects.containsKey(gameObject.getObjectId())) {

						JGameObject existGameObject = orginalObjects.get(gameObject.getObjectId());
//						System.out.println("orginal object: " + existGameObject);
						field.setFieldValue(component, existGameObject);
//						gameObject = field.getFieldValue(component, JGameObjectImpl.class);
					} else {
						Gdx.app.error("GameObjectProxator", "Proxy to non exist object! uuid: " + uuid);
					}
				}
			}
		}
	}
}
