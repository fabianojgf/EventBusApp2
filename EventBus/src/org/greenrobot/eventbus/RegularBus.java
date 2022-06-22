package org.greenrobot.eventbus;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

public class RegularBus extends AbstractBus {
    /** Log tag, apps may override it. */
    public static String TAG = "RegularBus";

    static volatile RegularBus defaultInstance;

    private static final Map<Class<?>, List<Class<?>>> eventTypesCache = new HashMap<>();

    private Map<Class<?>, CopyOnWriteArrayList<SubscriberClass>> mappedSubscriberClassesByEventType;
    private Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;
    private Map<Object, List<Class<?>>> typesBySubscriber;
    private Map<Class<?>, Object> stickyEvents;

    private final RegularBusQuerier querier = new RegularBusQuerier(this);

    /**
     * Current Immediate Thread State.
     * For events sent immediately to objects already instantiated.
     */
    private final ThreadLocal<PostingThreadState> currentImmediatePostingThreadState = new ThreadLocal<PostingThreadState>() {
        @Override
        protected PostingThreadState initialValue() {
            return new PostingThreadState(false);
        }
    };

    /**
     * Current Late Thread State.
     * For events sent late for objects that are yet to be instantiated.
     */
    private final ThreadLocal<PostingThreadState> currentLatePostingThreadState = new ThreadLocal<PostingThreadState>() {
        @Override
        protected PostingThreadState initialValue() {
            return new PostingThreadState(true);
        }
    };

    private Poster mainThreadPoster;
    private BackgroundPoster backgroundPoster;
    private AsyncPoster asyncPoster;
    private SubscriberMethodFinder subscriberMethodFinder;

    private boolean throwSubscriberException;
    private boolean logSubscriberExceptions;
    private boolean logNoSubscriberMessages;
    private boolean sendSubscriberExceptionEvent;
    private boolean sendNoSubscriberEvent;
    private boolean eventInheritance;

    private int indexCount;

    /**
     * Creates a new EventBus instance; each instance is a separate scope in which events are delivered.
     *
     * @param eventbus
     */
    public RegularBus(EventBus eventbus, RegularBusBuilder builder) {
        super(eventbus);

        mappedSubscriberClassesByEventType = new HashMap<>();
        subscriptionsByEventType = new HashMap<>();
        typesBySubscriber = new HashMap<>();
        stickyEvents = new ConcurrentHashMap<>();

        MainThreadSupport mainThreadSupport = builder.eventBusBuilder.getMainThreadSupport();

        /** Post/Subcribers */
        mainThreadPoster = mainThreadSupport != null
                ? mainThreadSupport.createPoster(getEventBus()) : null;
        backgroundPoster = new BackgroundPoster(getEventBus());
        asyncPoster = new AsyncPoster(getEventBus());

        indexCount = builder.subscriberInfoIndexes != null
                ? builder.subscriberInfoIndexes.size() : 0;

        subscriberMethodFinder = new SubscriberMethodFinder(builder.subscriberInfoIndexes,
                builder.eventBusBuilder.strictMethodVerification,
                builder.eventBusBuilder.ignoreGeneratedIndex);

        logSubscriberExceptions = builder.logSubscriberExceptions;
        logNoSubscriberMessages = builder.logNoSubscriberMessages;
        sendSubscriberExceptionEvent = builder.sendSubscriberExceptionEvent;
        sendNoSubscriberEvent = builder.sendNoSubscriberEvent;
        throwSubscriberException = builder.throwSubscriberException;
        eventInheritance = builder.eventInheritance;
    }

    /**
     * For unit test primarily.
     */
    public static void clearCaches() {
        /** Subcribers */
        SubscriberMethodFinder.clearCaches();
        eventTypesCache.clear();
    }

    /**
     * Registers the given subscriber to receive events. Subscribers must call {@link #unregisterSubscriber(Object)} once they
     * are no longer interested in receiving events.
     * <p/>
     * Subscribers have event handling methods that must be annotated by {@link Subscribe}.
     * The {@link Subscribe} annotation also allows configuration like {@link
     * ThreadMode} and priority.
     *
     * @param subscriber
     */
    public void register(Object subscriber) {
        Class<?> subscriberClass = subscriber.getClass();
        List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(subscriberClass);
        synchronized (this) {
            for (SubscriberMethod subscriberMethod : subscriberMethods) {
                subscribe(subscriber, subscriberMethod);
            }
        }

        if(isStartMechanismEnabled() && querier.isSubscriberMappedForActionMode(
                subscriberClass, ActionMode.LAZY_SUBSCRIBE)) {
            //Processes the thread that sends the messages that are in the late queue.
            PostingThreadState latePostingState = currentLatePostingThreadState.get();
            processPostingThread(subscriber, latePostingState);
        }
    }

    /**
     * Registers a subscription, which consists of an association between a subscriber object and a subscriber method,
     * which will be invoked to handle a given event.
     *
     * Important: Must be called in synchronized block.
     *
     * @param subscriber
     * @param subscriberMethod
     */
    private void subscribe(Object subscriber, SubscriberMethod subscriberMethod) {
        Class<?> eventType = subscriberMethod.eventType;
        Subscription newSubscription = new Subscription(subscriber, subscriberMethod);
        CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions == null) {
            subscriptions = new CopyOnWriteArrayList<>();
            subscriptionsByEventType.put(eventType, subscriptions);
        } else {
            if (subscriptions.contains(newSubscription)) {
                throw new EventBusException("Subscriber " + subscriber.getClass() + " already registered to event "
                        + eventType);
            }
        }

        int size = subscriptions.size();
        for (int i = 0; i <= size; i++) {
            if (i == size || subscriberMethod.priority > subscriptions.get(i).subscriberMethod.priority) {
                subscriptions.add(i, newSubscription);
                break;
            }
        }

        List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<>();
            typesBySubscriber.put(subscriber, subscribedEvents);
        }
        subscribedEvents.add(eventType);

        if (subscriberMethod.sticky) {
            if (eventInheritance) {
                // Existing sticky events of all subclasses of eventType have to be considered.
                // Note: Iterating over all events may be inefficient with lots of sticky events,
                // thus data structure should be changed to allow a more efficient lookup
                // (e.g. an additional map storing sub classes of super classes: Class -> List<Class>).
                Set<Map.Entry<Class<?>, Object>> entries = stickyEvents.entrySet();
                for (Map.Entry<Class<?>, Object> entry : entries) {
                    Class<?> candidateEventType = entry.getKey();
                    if (eventType.isAssignableFrom(candidateEventType)) {
                        Object stickyEvent = entry.getValue();
                        checkPostStickyEventToSubscription(newSubscription, stickyEvent);
                    }
                }
            } else {
                Object stickyEvent = stickyEvents.get(eventType);
                checkPostStickyEventToSubscription(newSubscription, stickyEvent);
            }
        }
    }

    /**
     * Registers a class subscription, which consists of an association between a class,
     * which has subscriber methods mapped, and one of these subscriber methods.
     *
     * Important: Must be called in synchronized block.
     *
     * @param subscriberClassType
     * @param subscriberMethod
     */
    private void subscribeClass(Class<?> subscriberClassType, SubscriberMethod subscriberMethod) {
        if(!ActionMode.isTypeEnableFor(subscriberClassType, subscriberMethod.actionMode)) {
            throw new EventBusException("Type " + subscriberClassType
                    + " is not an eligible type to use the actionMode "
                    + subscriberMethod.actionMode + " in one of its subscriber methods.");
        }

        Class<?> eventType = subscriberMethod.eventType;
        SubscriberClass newSubscriberClass = new SubscriberClass(subscriberClassType, subscriberMethod);
        CopyOnWriteArrayList<SubscriberClass> subscriberClasses = mappedSubscriberClassesByEventType.get(eventType);
        if (subscriberClasses == null) {
            subscriberClasses = new CopyOnWriteArrayList<>();
            mappedSubscriberClassesByEventType.put(eventType, subscriberClasses);
        } else {
            if (subscriberClasses.contains(newSubscriberClass)) {
                throw new EventBusException("Subscriber " + subscriberClassType + " already registered as 'Subscriber Class' to event "
                        + eventType);
            }
        }

        int size = subscriberClasses.size();
        for (int i = 0; i <= size; i++) {
            if (i == size || subscriberMethod.priority > subscriberClasses.get(i).subscriberMethod.priority) {
                subscriberClasses.add(i, newSubscriberClass);
                break;
            }
        }
    }

    /**
     * Checks if there is a stick event to be posted.
     *
     * @param newSubscription
     * @param stickyEvent
     */
    private void checkPostStickyEventToSubscription(Subscription newSubscription, Object stickyEvent) {
        if (stickyEvent != null) {
            // If the subscriber is trying to abort the event, it will fail (event is not tracked in posting state)
            // --> Strange corner case, which we don't take care of here.
            postToSubscription(newSubscription, stickyEvent, isMainThread());
        }
    }

    /**
     * Checks if the subscriber object is registered.
     *
     * @param subscriber
     * @return
     */
    public synchronized boolean isRegistered(Object subscriber) {
        return typesBySubscriber.containsKey(subscriber);
    }

    /**
     * Unregisters the given subcriber object from the event type.
     * Important: Only updates subscriptionsByEventType, not typesBySubscriber! Caller must update typesBySubscriber.
     *
     * @param subscriber
     * @param eventType
     */
    private void unsubscribeByEventType(Object subscriber, Class<?> eventType) {
        List<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions != null) {
            int size = subscriptions.size();
            for (int i = 0; i < size; i++) {
                Subscription subscription = subscriptions.get(i);
                if (subscription.subscriber == subscriber) {
                    subscription.active = false;
                    subscriptions.remove(i);
                    i--;
                    size--;
                }
            }
        }
    }

    /**
     * Unregisters the given subscriber from all event classes.
     *
     * @param subscriber
     */
    public synchronized void unregisterSubscriber(Object subscriber) {
        List<Class<?>> subscribedTypes = typesBySubscriber.get(subscriber);
        if (subscribedTypes != null) {
            for (Class<?> eventType : subscribedTypes) {
                unsubscribeByEventType(subscriber, eventType);
            }
            typesBySubscriber.remove(subscriber);
        } else {
            getLogger().log(Level.WARNING, "Subscriber to unregister was not registered before: " + subscriber.getClass());
        }
    }

    /**
     * Posts the given event to the event bus.
     *
     * @param event
     */
    public void post(Object event) {
        synchronized (event) {
            if(isStartMechanismEnabled()) {
                //Register classes with methods mapped as subscribe or handle.
                try {
                    eventbus.registerMappedClasses();
                } catch (NoClassDefFoundError e) {
                    //At the moment, do nothing.
                }
            }

            //Put exceptional events in immediate queue.
            PostingThreadState immediatePostingState = currentImmediatePostingThreadState.get();
            putEventInPostingQueue(immediatePostingState, event);

            //Processes the thread that sends the messages that are in the immediate queue.
            processPostingThread(immediatePostingState);

            if(isStartMechanismEnabled() && querier.isEventMappedForActionMode(
                    event, ActionMode.LAZY_SUBSCRIBE)) {
                //Put events in late queue.
                PostingThreadState latePostingState = currentLatePostingThreadState.get();
                putEventInPostingQueue(latePostingState, event);

                //Prepare to start the activities that will receive the events of the late queue.
                prepareLatePostingEvent(event);
            }
        }
    }

    public void putEventInPostingQueue(PostingThreadState postingThreadState, Object event) {
        if(postingThreadState.isLate) {
            HashMap<Class<?>, ArrayList<Object>> lateEventSubscriberQueue = postingThreadState.eventSubscriberQueue;
            Set<Class<?>> subscriberClasses = getMappedSubscriberClassForEvent(event);
            Iterator<Class<?>> it = subscriberClasses.iterator();
            while(it.hasNext()) {
                Class<?> subscriberClass = it.next();

                if(querier.isRegisteredSubscriberClassForEvent(subscriberClass, event))
                    continue;

                if(lateEventSubscriberQueue.containsKey(subscriberClass)) {
                    ArrayList<Object> eventList = lateEventSubscriberQueue.get(subscriberClass);
                    eventList.add(event);
                }
                else {
                    ArrayList<Object> eventList = new ArrayList<Object>();
                    eventList.add(event);
                    lateEventSubscriberQueue.put(subscriberClass, eventList);
                }
            }
        }
        else {
            List<Object> immediateEventQueue = postingThreadState.eventQueue;
            immediateEventQueue.add(event);
        }
    }

    /**
     * Analyzes and stores data from a specific class that have mapped methods to perform
     * the processing of common events or exceptional events.
     */
    public void registerMappedClass(Class<?> classToMap) {
        boolean hasSubscriberMethods = subscriberMethodFinder.hasSubscriberMethods(classToMap);

        //Register classes that contains methods mapped with the @Subscribe annotation.
        if(hasSubscriberMethods) {
            List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(classToMap);
            synchronized (this) {
                for (SubscriberMethod subscriberMethod : subscriberMethods) {
                    subscribeClass(classToMap, subscriberMethod);
                }
            }
        }
    }

    /**
     * Called from a subscriber's event handling method, further event delivery will be canceled. Subsequent
     * subscribers won't receive the event. Events are usually canceled by higher priority subscribers (see
     * {@link Subscribe#priority()}). Canceling is restricted to event handling methods running in posting thread
     * {@link ThreadMode#POSTING}.
     *
     * @param event
     */
    public void cancelEventDelivery(Object event) {
        PostingThreadState postingState = currentImmediatePostingThreadState.get();
        if (!postingState.isPosting) {
            throw new EventBusException(
                    "This method may only be called from inside event handling methods on the posting thread");
        } else if (event == null) {
            throw new EventBusException("Event may not be null");
        } else if (postingState.event != event) {
            throw new EventBusException("Only the currently handled event may be aborted");
        } else if (postingState.subscription.subscriberMethod.threadMode != ThreadMode.POSTING) {
            throw new EventBusException(" event handlers may only abort the incoming event");
        }

        postingState.canceled = true;
    }

    /**
     * Posts the given event to the event bus and holds on to the event (because it is sticky). The most recent sticky
     * event of an event's type is kept in memory for future access by subscribers using {@link Subscribe#sticky()}.
     *
     * @param event
     */
    public void postSticky(Object event) {
        synchronized (stickyEvents) {
            stickyEvents.put(event.getClass(), event);
        }
        // Should be posted after it is putted, in case the subscriber wants to remove immediately
        post(event);
    }

    /**
     * Gets the most recent sticky event for the given type.
     *
     * @see #postSticky(Object)
     *
     * @param eventType
     * @param <T>
     * @return
     */
    public <T> T getStickyEvent(Class<T> eventType) {
        synchronized (stickyEvents) {
            return eventType.cast(stickyEvents.get(eventType));
        }
    }

    /**
     * Remove and gets the recent sticky event for the given event type.
     *
     * @see #postSticky(Object)
     *
     * @param eventType
     * @param <T>
     * @return
     */
    public <T> T removeStickyEvent(Class<T> eventType) {
        synchronized (stickyEvents) {
            return eventType.cast(stickyEvents.remove(eventType));
        }
    }

    /**
     * Removes the sticky event if it equals to the given event.
     * Returns true if the events matched and the sticky event was removed.
     *
     * @param event
     * @return
     */
    public boolean removeStickyEvent(Object event) {
        synchronized (stickyEvents) {
            Class<?> eventType = event.getClass();
            Object existingEvent = stickyEvents.get(eventType);
            if (event.equals(existingEvent)) {
                stickyEvents.remove(eventType);
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Removes all sticky events.
     */
    public void removeAllStickyEvents() {
        synchronized (stickyEvents) {
            stickyEvents.clear();
        }
    }

    /**
     * Performs post processing of all events that are in the queue.
     *
     * @param postingState
     */
    private void processPostingThread(PostingThreadState postingState) {
        processPostingThread(null, postingState);
    }

    /**
     * Performs post processing of all events in which the subscriber object is registered.
     *
     * @param subscriber
     * @param postingState
     */
    private void processPostingThread(Object subscriber, PostingThreadState postingState) {
        List<Object> eventQueue = postingState.eventQueue;
        HashMap<Class<?>, ArrayList<Object>> eventSubscriberQueue = postingState.eventSubscriberQueue;

        if (!postingState.isPosting) {
            postingState.isMainThread = isMainThread();
            postingState.isPosting = true;
            if (postingState.canceled) {
                throw new EventBusException("Internal error. Abort state was not reset");
            }
            try {
                if (subscriber != null && postingState.isLate) {
                    ArrayList<Object> eventList = eventSubscriberQueue.get(subscriber.getClass());
                    if(eventList != null) {
                        while (!eventList.isEmpty()) {
                            postSingleEvent(eventList.remove(0), subscriber, postingState);
                        }
                    }
                }
                else {
                    while (!eventQueue.isEmpty()) {
                        postSingleEvent(eventQueue.remove(0), postingState);
                    }
                }
            } finally {
                postingState.isPosting = false;
                postingState.isMainThread = false;
            }
        }
    }

    /**
     * Post a specific event for all registered subscriber objects.
     *
     * @param event
     * @param postingState
     * @throws Error
     */
    private void postSingleEvent(Object event, PostingThreadState postingState) throws Error {
        postSingleEvent(event, null, postingState);
    }

    /**
     * Post a specific event for a specific subscriber object.
     *
     * @param event
     * @param subscribe
     * @param postingState
     * @throws Error
     */
    private void postSingleEvent(Object event, Object subscribe, PostingThreadState postingState) throws Error {
        Class<?> eventClass = event.getClass();
        boolean subscriptionFound = false;
        if (eventInheritance) {
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                subscriptionFound |= postSingleEventForEventType(event, subscribe, postingState, clazz);
            }
        } else {
            subscriptionFound = postSingleEventForEventType(event, subscribe, postingState, eventClass);
        }
        if (!subscriptionFound) {
            if (logNoSubscriberMessages) {
                getLogger().log(Level.FINE, "No subscribers registered for event " + eventClass);
            }
            if (sendNoSubscriberEvent && eventClass != NoSubscriberEvent.class &&
                    eventClass != SubscriberExceptionEvent.class) {
                post(new NoSubscriberEvent(getEventBus(), event));
            }
        }
    }

    /**
     *
     * @param event
     * @param postingState
     * @param eventClass
     * @return
     */
    private boolean postSingleEventForEventType(Object event, Object subscriber, PostingThreadState postingState, Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            subscriptions = subscriptionsByEventType.get(eventClass);
        }
        if (subscriptions != null && !subscriptions.isEmpty()) {
            for (Subscription subscription : subscriptions) {
                if(postingState.isLate && subscriber != null && !subscription.subscriber.equals(subscriber))
                    continue;

                postingState.event = event;
                postingState.subscription = subscription;
                boolean aborted;
                try {
                    postToSubscription(subscription, event, postingState.isMainThread);
                    aborted = postingState.canceled;
                } finally {
                    postingState.event = null;
                    postingState.subscription = null;
                    postingState.canceled = false;
                }
                if (aborted) {
                    break;
                }
            }
            return true;
        }
        return false;
    }

    private Set<Class<?>> getMappedSubscriberClassForEvent(Object event) {
        Set<Class<?>> subscriberClassesSet = new HashSet<Class<?>>();
        Class<?> eventClass = event.getClass();
        if (eventInheritance) {
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                subscriberClassesSet.addAll(getMappedSubscriberClassForEventType(clazz));
            }
        }
        else {
            subscriberClassesSet.addAll(getMappedSubscriberClassForEventType(eventClass));
        }
        return subscriberClassesSet;
    }

    private Set<Class<?>> getMappedSubscriberClassForEventType(Class<?> eventClass) {
        Set<Class<?>> subscriberClassesSet = new HashSet<Class<?>>();
        CopyOnWriteArrayList<SubscriberClass> subscriberClasses;
        synchronized (this) {
            subscriberClasses = mappedSubscriberClassesByEventType.get(eventClass);
            if(subscriberClasses != null && !subscriberClasses.isEmpty()) {
                for(SubscriberClass subscriberClass : subscriberClasses) {
                    subscriberClassesSet.add(subscriberClass.subscriberClass);
                }
            }
            return subscriberClassesSet;
        }
    }

    /**
     *
     * @param subscription
     * @param event
     * @param isMainThread
     */
    private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
        switch (subscription.subscriberMethod.threadMode) {
            case POSTING:
                invokeSubscriber(subscription, event);
                break;
            case MAIN:
                if (isMainThread) {
                    invokeSubscriber(subscription, event);
                } else {
                    mainThreadPoster.enqueue(subscription, event);
                }
                break;
            case MAIN_ORDERED:
                if (mainThreadPoster != null) {
                    mainThreadPoster.enqueue(subscription, event);
                } else {
                    // temporary: technically not correct as poster not decoupled from subscriber
                    invokeSubscriber(subscription, event);
                }
                break;
            case BACKGROUND:
                if (isMainThread) {
                    backgroundPoster.enqueue(subscription, event);
                } else {
                    invokeSubscriber(subscription, event);
                }
                break;
            case ASYNC:
                asyncPoster.enqueue(subscription, event);
                break;
            default:
                throw new IllegalStateException("Unknown thread mode: " + subscription.subscriberMethod.threadMode);
        }
    }

    /**
     * Prepare the event to be sent to the subscribers who will receive it through the late send queue.
     *
     * @param event
     * @throws Error
     */
    private void prepareLatePostingEvent(Object event) throws Error {
        Class<?> eventClass = event.getClass();
        boolean subscriberClassFound = false;
        if (eventInheritance) {
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                subscriberClassFound |= prepareLatePostingEventForEventType(event, clazz);
            }
        } else {
            subscriberClassFound = prepareLatePostingEventForEventType(event, eventClass);
        }
    }

    /**
     * Prepares the event to be sent to subscribers who will receive it via the late send queue,
     * requiring the type of event to be specified.
     *
     * This method aims to ensure that, if the subscriber object is activity, they will be started so that they receive the events.
     * For this, it is necessary that the subscriber method has the {@link Subscribe#actionMode} parameter of the  {@link Subscribe} annotation
     * with the {@link ActionMode#LAZY_SUBSCRIBE} value.
     *
     * @param event
     * @param eventClass
     * @return
     */
    private boolean prepareLatePostingEventForEventType(Object event, Class<?> eventClass) {
        CopyOnWriteArrayList<SubscriberClass> subscriberClasses;
        synchronized (this) {
            subscriberClasses = mappedSubscriberClassesByEventType.get(eventClass);
        }
        if (subscriberClasses != null && !subscriberClasses.isEmpty()) {
            for (SubscriberClass subscriberClass : subscriberClasses) {
                if(subscriberClass.subscriberMethod.actionMode == ActionMode.LAZY_SUBSCRIBE) {
                    Class<?> subscriberClassType = subscriberClass.subscriberClass;
                    if(ActionMode.isTypeEnableFor(subscriberClassType, ActionMode.LAZY_SUBSCRIBE)) {
                        if(Activity.class.isAssignableFrom(subscriberClassType)) {
                            Intent intent = new Intent(getContext(), subscriberClassType);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            getContext().startActivity(intent);
                        }
                        else if(Service.class.isAssignableFrom(subscriberClassType)) {
                            Intent intent = new Intent(getContext(), subscriberClassType);
                            getContext().startService(intent);
                        }
                    }
                    else {
                        throw new EventBusException("The type of this subscriber is not enabled for the 'start and subscribe' action mode.");
                    }
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Looks up all Class objects including super classes and interfaces. Should also work for interfaces.
     *
     * @param eventClass
     * @return
     */
    static List<Class<?>> lookupAllEventTypes(Class<?> eventClass) {
        synchronized (eventTypesCache) {
            List<Class<?>> eventTypes = eventTypesCache.get(eventClass);
            if (eventTypes == null) {
                eventTypes = new ArrayList<>();
                Class<?> clazz = eventClass;
                while (clazz != null) {
                    eventTypes.add(clazz);
                    addInterfaces(eventTypes, clazz.getInterfaces());
                    clazz = clazz.getSuperclass();
                }
                eventTypesCache.put(eventClass, eventTypes);
            }
            return eventTypes;
        }
    }

    /**
     * Invokes the subscriber if the subscriptions is still active. Skipping subscriptions prevents race conditions
     * between {@link #unregisterSubscriber(Object)} and event delivery. Otherwise the event might be delivered after the
     * subscriber unregistered. This is particularly important for main thread delivery and registrations bound to the
     * live cycle of an Activity or Fragment.
     *
     * @param pendingPost
     */
    void invokeSubscriber(PendingPost pendingPost) {
        Object event = pendingPost.event;
        Subscription subscription = pendingPost.subscription;
        PendingPost.releasePendingPost(pendingPost);
        if (subscription.active) {
            invokeSubscriber(subscription, event);
        }
    }

    /**
     * Invokes the subscriber to perform some processing on the event.
     *
     * @param subscription
     * @param event
     */
    void invokeSubscriber(Subscription subscription, Object event) {
        try {
            subscription.subscriberMethod.method.invoke(subscription.subscriber, event);
        } catch (InvocationTargetException e) {
            handleSubscriberException(subscription, event, e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    /**
     * Process exception caught during invocation of the subscriber.
     *
     * @param subscription
     * @param event
     * @param cause
     */
    private void handleSubscriberException(Subscription subscription, Object event, Throwable cause) {
        if (event instanceof SubscriberExceptionEvent) {
            if (logSubscriberExceptions) {
                // Don't send another SubscriberExceptionEvent to avoid infinite event recursion, just log
                getLogger().log(Level.SEVERE, "SubscriberExceptionEvent subscriber " + subscription.subscriber.getClass()
                        + " threw an exception", cause);
                SubscriberExceptionEvent exEvent = (SubscriberExceptionEvent) event;
                getLogger().log(Level.SEVERE, "Initial event " + exEvent.causingEvent + " caused exception in "
                        + exEvent.causingSubscriber, exEvent.throwable);
            }
        } else {
            if (throwSubscriberException) {
                throw new EventBusException("Invoking subscriber failed", cause);
            }
            if (logSubscriberExceptions) {
                getLogger().log(Level.SEVERE, "Could not dispatch event: " + event.getClass() + " to subscribing class "
                        + subscription.subscriber.getClass(), cause);
            }
            if (sendSubscriberExceptionEvent) {
                SubscriberExceptionEvent exEvent = new SubscriberExceptionEvent(getEventBus(), cause, event,
                        subscription.subscriber);
                post(exEvent);
            }
        }
    }

    /**
     * For ThreadLocal, much faster to set (and get multiple values).
     */
    final static class PostingThreadState {
        final List<Object> eventQueue = new ArrayList<>();
        final HashMap<Class<?>, ArrayList<Object>> eventSubscriberQueue = new HashMap<Class<?>, ArrayList<Object>>();
        boolean isPosting;
        boolean isMainThread;
        boolean isLate;
        Subscription subscription;
        Object event;
        boolean canceled;

        public PostingThreadState() {
            super();
        }

        public PostingThreadState(boolean isLate) {
            super();
            this.isLate = isLate;
        }
    }

    public SubscriberMethodFinder getSubscriberMethodFinder() {
        return subscriberMethodFinder;
    }

    public boolean isEventInheritance() {
        return eventInheritance;
    }

    public Map<Class<?>, CopyOnWriteArrayList<Subscription>> getSubscriptionsByEventType() {
        return subscriptionsByEventType;
    }

    public Map<Class<?>, CopyOnWriteArrayList<SubscriberClass>> getMappedSubscriberClassesByEventType() {
        return mappedSubscriberClassesByEventType;
    }

    public RegularBusQuerier getQuerier() {
        return querier;
    }

    public int getIndexCount() {
        return indexCount;
    }

    /**
     * Just an idea: we could provide a callback to post() to be notified, an alternative would be events, of course...
     */
    /* public */ interface PostCallback {
        void onPostCompleted(List<SubscriberExceptionEvent> exceptionEvents);
    }

    @Override
    public String toString() {
        return "RegularBus[indexCountSubscriber=" + indexCount
                + ", eventInheritance=" + eventInheritance + "]";
    }
}
