package com.artemis;

import com.artemis.annotations.SkipWire;
import com.artemis.utils.Bag;
import com.artemis.utils.ImmutableBag;
import com.artemis.utils.IntBag;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import static com.artemis.Aspect.all;
import static com.artemis.utils.ConverterUtil.toIntBag;

/**
 * Manages all instances of {@link EntitySubscription}.
 *
 * Entity subscriptions are automatically updated during {@link com.artemis.World#process()}.
 * Any {@link com.artemis.EntitySubscription.SubscriptionListener | listeners}
 * are informed when entities are added or removed.
 *
 * @see EntitySubscription
 */
@SkipWire
public class AspectSubscriptionManager extends BaseSystem {

	private final Map<Aspect.Builder, EntitySubscription> subscriptionMap;
	private final Bag<EntitySubscription> subscriptions = new Bag(EntitySubscription.class);

	private final IntBag changed = new IntBag();
	private final IntBag deleted = new IntBag();

	protected AspectSubscriptionManager() {
		subscriptionMap = new HashMap<Aspect.Builder, EntitySubscription>();
	}

	@Override
	protected void processSystem() {}

	@Override
	protected void initialize() {
		// making sure subscription 1 matches all entities
		get(all());
	}

	/**
	 * Get subscription to all entities matching {@link Aspect}.
	 *
	 * Will create a new subscription if not yet available for
	 * given {@link Aspect} match.
	 *
	 * @param builder Aspect to match.
	 * @return {@link EntitySubscription} for aspect.
	 */
	public EntitySubscription get(Aspect.Builder builder) {
		EntitySubscription subscription = subscriptionMap.get(builder);
		return (subscription != null) ? subscription : createSubscription(builder);
	}

	private EntitySubscription createSubscription(Aspect.Builder builder) {
		EntitySubscription entitySubscription = new EntitySubscription(world, builder);
		subscriptionMap.put(builder, entitySubscription);
		subscriptions.add(entitySubscription);

		world.getComponentManager().synchronize(entitySubscription);
		return entitySubscription;
	}

	/**
	 * Informs all listeners of added, changedBits and deletedBits changes.
	 *
	 * Two types of listeners:
	 * {@see EntityObserver} implementations are guaranteed to be called back in order of system registration.
	 * {@see com.artemis.EntitySubscription.SubscriptionListener}, where order can vary (typically ordinal, except
	 * for subscrip1tions created in process, initialize instead of setWorld).
     *
	 * {@link com.artemis.EntitySubscription.SubscriptionListener#inserted(IntBag)}
	 * {@link com.artemis.EntitySubscription.SubscriptionListener#removed(IntBag)}
	 *
	 * Observers are called before Subscriptions, which means managerial tasks get artificial priority.
	 *
	 * @param changedBits Entities with changedBits composition or state.
	 * @param deletedBits Entities removed from world.
	 */
	void process(BitSet changedBits, BitSet deletedBits) {
		toEntityIntBags(changedBits, deletedBits);

		// note: processAll != process
		subscriptions.get(0).processAll(changed, deleted);

		for (int i = 1, s = subscriptions.size(); s > i; i++) {
			subscriptions.get(i).process(changed, deleted);
		}
	}

	private void toEntityIntBags(BitSet changed, BitSet deleted) {
		toPaddedIntBag(changed, this.changed);
		toIntBag(deleted, this.deleted);

		changed.clear();
		deleted.clear();
	}

	void processComponentIdentity(int id, BitSet componentBits) {
		for (int i = 0, s = subscriptions.size(); s > i; i++) {
			subscriptions.get(i).processComponentIdentity(id, componentBits);
		}
	}

	/**
	 * Gets the active list of all current entity subscriptions. Meant to assist
	 * in tooling/debugging.
	 *
	 * @return All active subscriptions.
	 */
	public ImmutableBag<EntitySubscription> getSubscriptions() {
		return subscriptions;
	}

	private IntBag toPaddedIntBag(BitSet bs, IntBag out) {
		if (bs.isEmpty()) {
			out.setSize(0);
			return out;
		}

		int size = 2 * bs.cardinality();
		out.setSize(size);
		out.ensureCapacity(size);

		ComponentManager cm = world.getComponentManager();
		int[] activesArray = out.getData();
		for (int i = bs.nextSetBit(0), index = 0; i >= 0; i = bs.nextSetBit(i + 1)) {
			activesArray[index] = i;
			activesArray[index + 1] = cm.getIdentity(i);
			index += 2;
		}

		return out;
	}
}
