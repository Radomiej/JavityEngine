package org.javity.components.reflection;

import java.util.UUID;

import org.javity.engine.Component;
import org.javity.engine.CustomScene;
import org.javity.engine.GameObjectProxy;
import org.javity.engine.JGameObject;
import org.javity.engine.JGameObjectImpl;
import org.javity.engine.Scene;

import com.badlogic.gdx.Gdx;

import pl.silver.reflection.ReflectionBean;
import pl.silver.reflection.SilverField;
import pl.silver.reflection.SilverReflectionUtills;

public class GameObjectsMonoReference {
	private CustomScene scene;

	public GameObjectsMonoReference(CustomScene scene) {
		this.scene = scene;
	}

	public void procces() {
		scene.getLoadSceneObjects().clear();
		for (JGameObject gameObject : scene.getGameObjects()) {
			UUID uuid = UUID.fromString(gameObject.getObjectId());
			scene.getLoadSceneObjects().put(uuid, gameObject);
		}
		for (JGameObject go : scene.getGameObjects()) {
			for (Component component : go.getAllComponents()) {
				ReflectionBean componentBean = SilverReflectionUtills.createReflectionBean(component.getClass());
				for (SilverField field : componentBean.getPublicAccesFields()) {
					if (field.getFieldName().equals("gameObject"))
						continue;
					if (field.getFieldValueClass().equals(JGameObject.class)) {
						JGameObject gameObject = field.getFieldValue(component, JGameObject.class);
						if (gameObject == null)
							continue;
						UUID uuid = UUID.fromString(gameObject.getObjectId());
						if (scene.getLoadSceneObjects().containsKey(uuid)) {

							JGameObject existGameObject = scene.getLoadSceneObjects().get(uuid);
							field.setFieldValue(component, existGameObject);
//							gameObject = field.getFieldValue(component, JGameObjectImpl.class);
						} else {
							// scene.getLoadSceneObjects().put(uuid,
							// gameObject);
							Gdx.app.error("GameObjectsMonoReference", "Proxy to non exist object! " + gameObject);
						}
					}
				}
			}
		}
	}

	public void proxyProcces() {
		for (JGameObject go : scene.getGameObjects()) {
			for (Component component : go.getAllComponents()) {
				ReflectionBean componentBean = SilverReflectionUtills.createReflectionBean(component.getClass());
				for (SilverField field : componentBean.getPublicAccesFields()) {
					if (field.getFieldName().equals("gameObject"))
						continue;
					if (field.getFieldValueClass().equals(JGameObject.class)) {
						JGameObject gameObject = field.getFieldValue(component, JGameObject.class);
						if (gameObject == null)
							continue;

						GameObjectProxy proxyGameObject = new GameObjectProxy(gameObject);
						field.setFieldValue(component, proxyGameObject);

					}
				}
			}
		}
	}
}
