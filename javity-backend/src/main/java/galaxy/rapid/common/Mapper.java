package galaxy.rapid.common;

import java.util.HashSet;
import java.util.Set;

import com.artemis.Entity;
import com.artemis.World;
import com.artemis.utils.IntBag;

public class Mapper
{
	public static Set<Entity> getEntitiesFromBag(IntBag ids, World world){
		Set<Entity> entities = new HashSet<Entity>();
		for (int i = 0; i < ids.size(); ++i) {
			int entityId = ids.get(i);
			Entity entity = world.getEntity(entityId);
			entities.add(entity);
		}
		return entities;
	}
}