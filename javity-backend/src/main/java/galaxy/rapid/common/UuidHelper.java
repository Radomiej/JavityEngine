package galaxy.rapid.common;

import java.util.UUID;

import com.artemis.Entity;
import com.artemis.World;
import com.artemis.managers.UuidEntityManager;

public class UuidHelper {
	public static Entity getEntityFromUuid(UUID uuid, World world){
		UuidEntityManager uuidManager = world.getSystem(UuidEntityManager.class);
		Entity entity = uuidManager.getEntity(uuid);
		return entity;
	}

	public static UUID getUuidFromEntity(Entity entity) {
		UuidEntityManager uuidManager = entity.getWorld().getSystem(UuidEntityManager.class);
		return uuidManager.getUuid(entity);
	}
}
