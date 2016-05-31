package org.javity.components.reflection;

import java.util.UUID;

import org.javity.engine.Component;
import org.javity.engine.CustomScene;
import org.javity.engine.GameObject;
import org.javity.engine.Scene;

import pl.silver.reflection.ReflectionBean;
import pl.silver.reflection.SilverField;
import pl.silver.reflection.SilverReflectionUtills;

public class GameObjectsMonoReference {
	private CustomScene scene;

	public GameObjectsMonoReference(CustomScene scene) {
		this.scene = scene;
	}
	
	public void procces(){
		scene.getLoadSceneObjects().clear();
		for(GameObject gameObject : scene.getGameObjects()){
			UUID uuid = UUID.fromString(gameObject.getPrefabId());
			scene.getLoadSceneObjects().put(uuid, gameObject);
		}
		for(GameObject go : scene.getGameObjects()){
			for(Component component : go.getAllComponents()){
				ReflectionBean componentBean = SilverReflectionUtills.createReflectionBean(component.getClass());
				for(SilverField field : componentBean.getPublicAccesFields()){
					if(field.getFieldName().equals("gameObject")) continue;
					if(field.getFieldValueClass().equals(GameObject.class)){
						GameObject gameObject = field.getFieldValue(component, GameObject.class);
						if(gameObject == null) continue;
						UUID uuid = UUID.fromString(gameObject.getPrefabId());
						if(scene.getLoadSceneObjects().containsKey(uuid)){
							
							GameObject existGameObject = scene.getLoadSceneObjects().get(uuid);
							field.setFieldValue(component, existGameObject);
							gameObject = field.getFieldValue(component, GameObject.class);
						}else{
							scene.getLoadSceneObjects().put(uuid, gameObject);
						}
					}
				}
			}
		}
	}
}
