package galaxy.rapid.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.artemis.Aspect;
import com.artemis.AspectSubscriptionManager;
import com.artemis.ComponentMapper;
import com.artemis.Entity;
import com.artemis.EntitySubscription;
import com.artemis.Manager;
import com.artemis.utils.IntBag;

import galaxy.rapid.components.RenderComponent;

public class RenderableManager extends Manager {
	private AspectSubscriptionManager asm;
	private EntitySubscription bodySubscription;
	private List<Entity> entities;

	private ComponentMapper<RenderComponent> renderMapper;
	private RenderComparator comparator = new RenderComparator();

	@SuppressWarnings("unchecked")
	@Override
	protected void initialize() {
		super.initialize();
		entities = new ArrayList<Entity>();// new TreeSet<Entity>(new
											// RenderComparator());
		bodySubscription = asm.get(Aspect.all(RenderComponent.class));
	}

	private void getSortedEntities() {
		entities.clear();
		IntBag bag = bodySubscription.getEntities();
		for (int i = 0; i < bag.size(); ++i) {
			int entityId = bag.get(i);
			Entity entity = world.getEntity(entityId);
			entities.add(entity);
		}

		Collections.sort(entities, comparator);
	}

	public List<Entity> getRendererObjects() {
		getSortedEntities();
		return entities;
	}

	private class RenderComparator implements Comparator<Entity> {
		public int compare(Entity o1, Entity o2) {

			RenderComponent render1 = renderMapper.get(o1);
			RenderComponent render2 = renderMapper.get(o2);
			return render1.getOrderZ() - render2.getOrderZ();
		}
	}
}
