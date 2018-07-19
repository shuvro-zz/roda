package org.roda.core.events.akka;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.roda.core.RodaCoreFactory;
import org.roda.core.common.akka.Messages.EventGroupCreated;
import org.roda.core.common.akka.Messages.EventGroupDeleted;
import org.roda.core.common.akka.Messages.EventGroupUpdated;
import org.roda.core.common.akka.Messages.EventUserCreated;
import org.roda.core.common.akka.Messages.EventUserDeleted;
import org.roda.core.common.akka.Messages.EventUserUpdated;
import org.roda.core.data.v2.user.Group;
import org.roda.core.data.v2.user.User;
import org.roda.core.events.EventsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.cluster.Cluster;
import akka.cluster.ddata.DistributedData;
import akka.cluster.ddata.GSet;
import akka.cluster.ddata.GSetKey;
import akka.cluster.ddata.Key;
import akka.cluster.ddata.ORMap;
import akka.cluster.ddata.Replicator.Changed;
import akka.cluster.ddata.Replicator.Subscribe;
import akka.cluster.ddata.Replicator.Update;
import akka.cluster.ddata.Replicator.UpdateFailure;
import akka.cluster.ddata.Replicator.UpdateSuccess;
import akka.cluster.ddata.Replicator.WriteAll;
import akka.cluster.ddata.Replicator.WriteConsistency;
import akka.cluster.ddata.Replicator.WriteMajority;
import scala.Option;
import scala.concurrent.duration.Duration;

public class AkkaEventsHandlerAndNotifierActor extends AbstractActor {
  private static final Logger LOGGER = LoggerFactory.getLogger(AkkaEventsHandlerAndNotifierActor.class);

  private static final String CACHE_PREFIX = "cache-";
  private static final String USER_KEY_PREFIX = "user-";
  private static final String GROUP_KEY_PREFIX = "group-";

  private final ActorRef replicator = DistributedData.get(context().system()).replicator();
  private final Cluster cluster = Cluster.get(context().system());

  private EventsHandler eventsHandler;
  private String instanceSenderId;

  private final Key<GSet<ObjectKey>> objectKeysKey = GSetKey.create("objectKeys");
  private Set<ObjectKey> objectKeys = new HashSet<>();

  private final WriteConsistency writeConsistency;

  public AkkaEventsHandlerAndNotifierActor(final EventsHandler eventsHandler, final String writeConsistency,
    final int writeConsistencyTimeoutInSeconds) {
    this.eventsHandler = eventsHandler;
    this.instanceSenderId = self().toString();
    this.writeConsistency = instantiateWriteConsistency(writeConsistency, writeConsistencyTimeoutInSeconds);
  }

  private WriteConsistency instantiateWriteConsistency(String writeConsistency,
    final int writeConsistencyTimeoutInSeconds) {
    if ("WriteAll".equalsIgnoreCase(writeConsistency)) {
      return new WriteAll(Duration.create(writeConsistencyTimeoutInSeconds, TimeUnit.SECONDS));
    } else {
      return new WriteMajority(Duration.create(writeConsistencyTimeoutInSeconds, TimeUnit.SECONDS));
    }

  }

  @Override
  public void preStart() {
    Subscribe<GSet<ObjectKey>> subscribe = new Subscribe<>(objectKeysKey, getSelf());
    replicator.tell(subscribe, ActorRef.noSender());
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder().match(EventUserCreated.class, e -> handleUserCreated(e))
      .match(EventUserUpdated.class, e -> handleUserUpdated(e)).match(EventUserDeleted.class, e -> handleUserDeleted(e))
      .match(EventGroupCreated.class, e -> handleGroupCreated(e))
      .match(EventGroupUpdated.class, e -> handleGroupUpdated(e))
      .match(EventGroupDeleted.class, e -> handleGroupDeleted(e)).match(Changed.class, c -> handleChanged(c))
      .match(UpdateSuccess.class, e -> handleUpdateSuccess(e)).match(UpdateFailure.class, e -> handleUpdateFailure(e))
      .matchAny(msg -> {
        LOGGER.warn("Received unknown message '{}'", msg);
      }).build();
  }

  private void handleUpdateSuccess(UpdateSuccess e) {
    LOGGER.info("handleUpdateSuccess '{}'", e);
    // FIXME 20180925 hsilva: do nothing???
  }

  private void handleUpdateFailure(UpdateFailure e) {
    LOGGER.info("handleUpdateFailure '{}'", e);
    // FIXME 20180925 hsilva: what to do???
  }

  private void handleChanged(Changed<?> e) {
    if (e.key().equals(objectKeysKey)) {
      handleObjectKeysChanged((Changed<GSet<ObjectKey>>) e);
    } else if (e.key() instanceof ObjectKey) {
      handleObjectChanged((Changed<ORMap<String, CRDTWrapper>>) e);
    }
  }

  private void handleObjectKeysChanged(Changed<GSet<ObjectKey>> e) {
    Set<ObjectKey> newKeys = e.dataValue().getElements();
    Set<ObjectKey> keysToSubscribe = new HashSet<>(newKeys);
    keysToSubscribe.removeAll(objectKeys);
    keysToSubscribe.forEach(keyToSubscribe -> {
      // subscribe to get notifications of when objects with this name are
      // added or removed
      replicator.tell(new Subscribe<>(keyToSubscribe, self()), self());
    });
    objectKeys = newKeys;

    // 20180925 hsilva: try to improve GC
    keysToSubscribe = null;
  }

  private void handleObjectChanged(Changed<ORMap<String, CRDTWrapper>> e) {
    String objectId = e.key().id().replaceFirst(CACHE_PREFIX, "");
    Option<CRDTWrapper> option = e.dataValue().get(objectId);
    if (option.isDefined()) {
      CRDTWrapper wrapper = (CRDTWrapper) option.get();
      if (!wrapper.getInstanceId().equals(instanceSenderId)) {
        if (objectId.startsWith(USER_KEY_PREFIX)) {
          if (!wrapper.isUpdate()) {
            // FIXME 20180814 hsilva: still missing passwords
            eventsHandler.handleUserCreated(RodaCoreFactory.getModelService(), (User) wrapper.getRodaObject(), null);
          } else {
            // FIXME 20180814 hsilva: still missing my user update & passwords
            eventsHandler.handleUserUpdated(RodaCoreFactory.getModelService(), (User) wrapper.getRodaObject(), null);
          }
        } else if (objectId.startsWith(GROUP_KEY_PREFIX)) {
          if (!wrapper.isUpdate()) {
            eventsHandler.handleGroupCreated(RodaCoreFactory.getModelService(), (Group) wrapper.getRodaObject());
          } else {
            eventsHandler.handleGroupUpdated(RodaCoreFactory.getModelService(), (Group) wrapper.getRodaObject());
          }
        }
      }
    } else {
      // this is a deletion
      if (objectId.startsWith(USER_KEY_PREFIX)) {
        eventsHandler.handleUserDeleted(RodaCoreFactory.getModelService(), objectId.replaceFirst(USER_KEY_PREFIX, ""));
      } else if (objectId.startsWith(GROUP_KEY_PREFIX)) {
        eventsHandler.handleGroupDeleted(RodaCoreFactory.getModelService(),
          objectId.replaceFirst(GROUP_KEY_PREFIX, ""));
      }
    }
  }

  private void handleUserCreated(EventUserCreated e) {
    // FIXME 20180925 hsilva: the following line is to be removed
    LOGGER.info("handleUserCreated '{}'", e);
    String key = USER_KEY_PREFIX + e.getUser().getId();
    putObjectInCache(key, new CRDTWrapper(e.getUser(), false, instanceSenderId, new Date().getTime()));
  }

  private void handleUserUpdated(EventUserUpdated e) {
    String key = USER_KEY_PREFIX + e.getUser().getId();
    putObjectInCache(key, new CRDTWrapper(e.getUser(), true, instanceSenderId, new Date().getTime()));
  }

  private void handleUserDeleted(EventUserDeleted e) {
    String key = USER_KEY_PREFIX + e.getId();
    evictObjectFromCache(key);
  }

  private void handleGroupCreated(EventGroupCreated e) {
    String key = GROUP_KEY_PREFIX + e.getGroup().getId();
    putObjectInCache(key, new CRDTWrapper(e.getGroup(), false, instanceSenderId, new Date().getTime()));
  }

  private void handleGroupUpdated(EventGroupUpdated e) {
    String key = GROUP_KEY_PREFIX + e.getGroup().getId();
    putObjectInCache(key, new CRDTWrapper(e.getGroup(), true, instanceSenderId, new Date().getTime()));
  }

  private void handleGroupDeleted(EventGroupDeleted e) {
    String key = GROUP_KEY_PREFIX + e.getId();
    evictObjectFromCache(key);
  }

  private void putObjectInCache(String key, CRDTWrapper value) {
    ObjectKey objectKey = dataKey(key);
    if (!objectKeys.contains(objectKey)) {
      Update<GSet<ObjectKey>> update1 = new Update<>(objectKeysKey, GSet.create(), writeConsistency,
        curr -> curr.add(objectKey));
      replicator.tell(update1, self());
    }

    Update<ORMap<String, CRDTWrapper>> update = new Update<ORMap<String, CRDTWrapper>>(dataKey(key), ORMap.create(),
      writeConsistency, curr -> curr.put(cluster, key, value));
    replicator.tell(update, self());
  }

  private void evictObjectFromCache(String key) {
    ObjectKey objectKey = dataKey(key);
    if (!objectKeys.contains(objectKey)) {
      Update<GSet<ObjectKey>> update1 = new Update<>(objectKeysKey, GSet.create(), writeConsistency,
        curr -> curr.add(objectKey));
      replicator.tell(update1, self());
    }

    Update<ORMap<String, CRDTWrapper>> update = new Update<>(objectKey, ORMap.create(), writeConsistency,
      curr -> curr.remove(cluster, key));
    replicator.tell(update, self());
  }

  private ObjectKey dataKey(String entryKey) {
    return new ObjectKey(CACHE_PREFIX + entryKey);
  }

  public static class ObjectKey extends Key<ORMap<String, CRDTWrapper>> {
    private static final long serialVersionUID = 4859356839497209682L;

    public ObjectKey(String eventKey) {
      super(eventKey);
    }
  }
}