/*
 * Copyright (C) 2012-2020 Markus Junginger, greenrobot (http://greenrobot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.greenrobot.eventbus;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

import dalvik.system.DexFile;

/**
 * EventBus is a central publish/subscribe event system for Java and Android.
 * Events are posted ({@link #post(Object)}) to the bus, which delivers it to subscribers that have a matching handler
 * method for the event type.
 * To receive events, subscribers must register themselves to the bus using {@link #registerSubscriber(Object)}.
 * Once registered, subscribers receive events until {@link #unregisterSubscriber(Object)} is called.
 * Event handling methods must be annotated by {@link Subscribe}, must be public, return nothing (void),
 * and have exactly one parameter (the event).
 *
 * @author Markus Junginger, greenrobot
 */
@SuppressWarnings("ALL")
public class EventBus {

    /** Log tag, apps may override it. */
    public static String TAG = "EventBus";

    static volatile EventBus defaultInstance;

    private static final EventBusBuilder DEFAULT_BUILDER = new EventBusBuilder();
    private static final Map<Class<?>, List<Class<?>>> eventTypesCache = new HashMap<>();
    private static final Map<Class<?>, List<Class<?>>> exceptionalEventTypesCache = new HashMap<>();

    private final Map<Class<?>, CopyOnWriteArrayList<SubscriberClass>> mappedSubscriberClassesByEventType;
    private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;
    private final Map<Object, List<Class<?>>> typesBySubscriber;
    private final Map<Class<?>, Object> stickyEvents;

    private final Map<Class<?>, CopyOnWriteArrayList<HandlerClass>> mappedHandlerClassesByExceptionalEventType;
    private final Map<Class<?>, CopyOnWriteArrayList<Handlement>> handlementsByExceptionalEventType;
    private final Map<Object, List<Class<?>>> typesByHandler;
    private final Map<Class<?>, Object> stickyExceptionalEvents;

    private Context context;

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
     * Current Immediate Thread State.
     * For exceptional events sent immediately to objects already instantiated.
     */
    private final ThreadLocal<ThrowingThreadState> currentImmediateThrowingThreadState = new ThreadLocal<ThrowingThreadState>() {
        @Override
        protected ThrowingThreadState initialValue() {
            return new ThrowingThreadState(false);
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

    /**
     * Current Late Thread State.
     * For exceptional events sent late for objects that are yet to be instantiated.
     */
    private final ThreadLocal<ThrowingThreadState> currentLateThrowingThreadState = new ThreadLocal<ThrowingThreadState>() {
        @Override
        protected ThrowingThreadState initialValue() {
            return new ThrowingThreadState(true);
        }
    };

    // @Nullable
    private final MainThreadSupport mainThreadSupport;
    // @Nullable
    private final Poster mainThreadPoster;
    private final BackgroundPoster backgroundPoster;
    private final AsyncPoster asyncPoster;
    private final SubscriberMethodFinder subscriberMethodFinder;
    private final Thrower mainThreadThrower;
    private final BackgroundThrower backgroundThrower;
    private final AsyncThrower asyncThrower;
    private final HandlerMethodFinder handlerMethodFinder;
    private final ExecutorService executorService;

    private final boolean throwSubscriberException;
    private final boolean logSubscriberExceptions;
    private final boolean logNoSubscriberMessages;
    private final boolean sendSubscriberExceptionEvent;
    private final boolean sendNoSubscriberEvent;
    private final boolean eventInheritance;

    private final boolean throwHandlerException;
    private final boolean logHandlerExceptions;
    private final boolean logNoHandlerMessages;
    private final boolean sendHandlerExceptionExceptionalEvent;
    private final boolean sendNoHandlerExceptionalEvent;
    private final boolean exceptionalEventInheritance;

    private boolean mappedClassesRegistrationPerformed;
    private boolean startMechanismEnabled;

    private final int indexCount;
    private final int indexCountSubscriber;
    private final int indexCountHandler;
    private final Logger logger;

    /**
     * Convenience singleton for apps using a process-wide EventBus instance.
     *
     * @return EventBus
     */
    public static EventBus getDefault() {
        return getDefault((Context) null);
    }

    /**
     * Convenience singleton for apps using a process-wide EventBus instance.
     *
     * @param context
     * @return EventBus
     */
    public static EventBus getDefault(Context context) {
        EventBus instance = defaultInstance;
        if (instance == null) {
            synchronized (EventBus.class) {
                instance = EventBus.defaultInstance;
                if (instance == null) {
                    instance = EventBus.defaultInstance = new EventBus((Context) context);
                }
            }
        }
        return instance;
    }

    /**
     *
     * @return EventBusBuilder
     */
    public static EventBusBuilder builder() {
        return new EventBusBuilder();
    }

    /**
     * For unit test primarily.
     */
    public static void clearCaches() {
        /** Subcribers */
        SubscriberMethodFinder.clearCaches();
        eventTypesCache.clear();
        /** Handlers */
        HandlerMethodFinder.clearCaches();
        exceptionalEventTypesCache.clear();
    }

    /**
     * Creates a new EventBus instance; each instance is a separate scope in which events are delivered.
     * To use a central bus, consider {@link #getDefault()}.
     */
    public EventBus() {
        this((Context) null);
    }

    /**
     * Creates a new EventBus instance; each instance is a separate scope in which events are delivered.
     * To use a central bus, consider {@link #getDefault(Context)}.
     *
     * @param context
     */
    public EventBus(Context context) {
        this(DEFAULT_BUILDER);
        this.context = context;
    }

    /**
     *
     * @param builder
     */
    EventBus(EventBusBuilder builder) {
        logger = builder.getLogger();

        /** Post/Subcribers */
        mappedSubscriberClassesByEventType = new HashMap<>();
        subscriptionsByEventType = new HashMap<>();
        typesBySubscriber = new HashMap<>();
        stickyEvents = new ConcurrentHashMap<>();
        /** Throwers/Handlers */
        mappedHandlerClassesByExceptionalEventType = new HashMap<>();
        handlementsByExceptionalEventType = new HashMap<>();
        typesByHandler = new HashMap<>();
        stickyExceptionalEvents = new ConcurrentHashMap<>();

        mainThreadSupport = builder.getMainThreadSupport();

        /** Post/Subcribers */
        mainThreadPoster = mainThreadSupport != null ? mainThreadSupport.createPoster(this) : null;
        backgroundPoster = new BackgroundPoster(this);
        asyncPoster = new AsyncPoster(this);
        /** Throwers/Handlers */
        mainThreadThrower = mainThreadSupport != null ? mainThreadSupport.createThrower(this) : null;
        backgroundThrower = new BackgroundThrower(this);
        asyncThrower = new AsyncThrower(this);

        indexCountSubscriber = builder.subscriberInfoIndexes != null ? builder.subscriberInfoIndexes.size() : 0;
        indexCountHandler = builder.handlerInfoIndexes != null ? builder.handlerInfoIndexes.size() : 0;
        indexCount = indexCountSubscriber + indexCountHandler;

        subscriberMethodFinder = new SubscriberMethodFinder(builder.subscriberInfoIndexes,
                builder.strictMethodVerification, builder.ignoreGeneratedIndex);
        handlerMethodFinder = new HandlerMethodFinder(builder.handlerInfoIndexes,
                builder.strictMethodVerification, builder.ignoreGeneratedIndex);

        executorService = builder.executorService;
        /** Post/Subcribers */
        logSubscriberExceptions = builder.logSubscriberExceptions;
        logNoSubscriberMessages = builder.logNoSubscriberMessages;
        sendSubscriberExceptionEvent = builder.sendSubscriberExceptionEvent;
        sendNoSubscriberEvent = builder.sendNoSubscriberEvent;
        throwSubscriberException = builder.throwSubscriberException;
        eventInheritance = builder.eventInheritance;

        /** Throwers/Handlers */
        logHandlerExceptions = builder.logHandlerExceptions;
        logNoHandlerMessages = builder.logNoHandlerMessages;
        sendHandlerExceptionExceptionalEvent = builder.sendHandlerExceptionExceptionalEvent;
        sendNoHandlerExceptionalEvent = builder.sendNoHandlerExceptionalEvent;
        throwHandlerException = builder.throwHandlerException;
        exceptionalEventInheritance = builder.exceptionalEventInheritance;

        mappedClassesRegistrationPerformed = builder.mappedClassesRegistrationPerformed;
        startMechanismEnabled = builder.startMechanismEnabled;
    }

    /**
     * Registers the given object to receive both events and exceptional events. Registered objects must call {@link #unregister(Object)} once they
     * are no longer interested in receiving events and exceptional events.
     *
     * @param object
     */
    public void register(Object object) {
        registerSubscriber(object);
        registerHandler(object);
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
    public void registerSubscriber(Object subscriber) {
        Class<?> subscriberClass = subscriber.getClass();
        List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(subscriberClass);
        synchronized (this) {
            for (SubscriberMethod subscriberMethod : subscriberMethods) {
                subscribe(subscriber, subscriberMethod);
            }
        }

        if(startMechanismEnabled && isSubscriberMappedForActionMode(
                subscriberClass, ActionMode.LAZY_SUBSCRIBE)) {
            //Processes the thread that sends the messages that are in the late queue.
            PostingThreadState latePostingState = currentLatePostingThreadState.get();
            processPostingThread(subscriber, latePostingState);
        }
    }

    /**
     * Registers the given handler to receive exceptional events. Handlers must call {@link #unregisterHandler(Object)} once they
     * are no longer interested in receiving exceptional events.
     * <p/>
     * Handlers have exceptional event handling methods that must be annotated by {@link Handle}.
     * The {@link Handle} annotation also allows configuration like {@link
     * ExceptionalThreadMode} and priority.
     *
     * @param handler
     */
    public void registerHandler(Object handler) {
        Class<?> handlerClass = handler.getClass();
        List<HandlerMethod> handlerMethods = handlerMethodFinder.findHandlerMethods(handlerClass);
        synchronized (this) {
            for (HandlerMethod handlerMethod : handlerMethods) {
                handle(handler, handlerMethod);
            }
        }

        if(startMechanismEnabled && isHandlerMappedForExceptionalActionMode(
                handlerClass, ExceptionalActionMode.LAZY_HANDLE)) {
            //Processes the thread that sends the messages that are in the late queue.
            ThrowingThreadState lateThrowingState = currentLateThrowingThreadState.get();
            processThrowingThread(handler, lateThrowingState);
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
     * Registers a handlement, which consists of an association between a handler object and a handler method,
     * which will be invoked to handle a given exceptional event.
     *
     * Important: Must be called in synchronized block.
     *
     * @param handler
     * @param handlerMethod
     */
    private void handle(Object handler, HandlerMethod handlerMethod) {
        Class<?> exceptionalEventType = handlerMethod.exceptionalEventType;
        Handlement newHandlement = new Handlement(handler, handlerMethod);
        CopyOnWriteArrayList<Handlement> handlements = handlementsByExceptionalEventType.get(exceptionalEventType);
        if (handlements == null) {
            handlements = new CopyOnWriteArrayList<>();
            handlementsByExceptionalEventType.put(exceptionalEventType, handlements);
        } else {
            if (handlements.contains(newHandlement)) {
                throw new EventBusException("Handler " + handler.getClass() + " already registered to exceptional event "
                        + exceptionalEventType);
            }
        }

        int size = handlements.size();
        for (int i = 0; i <= size; i++) {
            if (i == size || handlerMethod.priority > handlements.get(i).handlerMethod.priority) {
                handlements.add(i, newHandlement);
                break;
            }
        }

        List<Class<?>> handledExceptionalEvents = typesByHandler.get(handler);
        if (handledExceptionalEvents == null) {
            handledExceptionalEvents = new ArrayList<>();
            typesByHandler.put(handler, handledExceptionalEvents);
        }
        handledExceptionalEvents.add(exceptionalEventType);

        if (handlerMethod.sticky) {
            if (exceptionalEventInheritance) {
                // Existing sticky exceptional events of all subclasses of exceptionalEventType have to be considered.
                // Note: Iterating over all exceptional events may be inefficient with lots of sticky exceptional events,
                // thus data structure should be changed to allow a more efficient lookup
                // (e.g. an additional map storing sub classes of super classes: Class -> List<Class>).
                Set<Map.Entry<Class<?>, Object>> entries = stickyExceptionalEvents.entrySet();
                for (Map.Entry<Class<?>, Object> entry : entries) {
                    Class<?> candidateExceptionalEventType = entry.getKey();
                    if (exceptionalEventType.isAssignableFrom(candidateExceptionalEventType)) {
                        Object stickyExceptionalEvent = entry.getValue();
                        checkThrowsExceptionalStickyEventToHandlement(newHandlement, stickyExceptionalEvent);
                    }
                }
            } else {
                Object stickyExceptionalEvent = stickyExceptionalEvents.get(exceptionalEventType);
                checkThrowsExceptionalStickyEventToHandlement(newHandlement, stickyExceptionalEvent);
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
     * Registers a class handlement, which consists of an association between a class,
     * which has handler methods mapped, and one of these handler methods.
     *
     * Important: Must be called in synchronized block.
     *
     * @param handlerClassType
     * @param handlerMethod
     */
    private void handleClass(Class<?> handlerClassType, HandlerMethod handlerMethod) {
        if(!ExceptionalActionMode.isTypeEnableFor(handlerClassType, handlerMethod.actionMode)) {
            throw new EventBusException("Type " + handlerClassType
                    + " is not an eligible type to use the actionMode "
                    + handlerMethod.actionMode + " in one of its handler methods.");
        }

        Class<?> exceptionalEventType = handlerMethod.exceptionalEventType;
        HandlerClass newHandlerClass = new HandlerClass(handlerClassType, handlerMethod);
        CopyOnWriteArrayList<HandlerClass> handlerClasses = mappedHandlerClassesByExceptionalEventType.get(exceptionalEventType);
        if (handlerClasses == null) {
            handlerClasses = new CopyOnWriteArrayList<>();
            mappedHandlerClassesByExceptionalEventType.put(exceptionalEventType, handlerClasses);
        } else {
            if (handlerClasses.contains(newHandlerClass)) {
                throw new EventBusException("Handler " + handlerClassType + " already registered as 'Hendler Class' to exceptional event "
                        + exceptionalEventType);
            }
        }

        int size = handlerClasses.size();
        for (int i = 0; i <= size; i++) {
            if (i == size || handlerMethod.priority > handlerClasses.get(i).handlerMethod.priority) {
                handlerClasses.add(i, newHandlerClass);
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
     * Checks if there is a exceptional stick event to be throwed.
     *
     * @param newHandlement
     * @param exceptionalStickyEvent
     */
    private void checkThrowsExceptionalStickyEventToHandlement(Handlement newHandlement, Object exceptionalStickyEvent) {
        if (exceptionalStickyEvent != null) {
            // If the handler is trying to abort the exceptional event, it will fail (exceptional event is not tracked in throwing state)
            // --> Strange corner case, which we don't take care of here.
            throwsToHandlement(newHandlement, exceptionalStickyEvent, isMainThread());
        }
    }

    /**
     * Checks if the current thread is running in the main thread.
     * If there is no main thread support (e.g. non-Android), "true" is always returned.
     * In that case MAIN thread subscribers are always called in posting thread,
     * and BACKGROUND subscribers are always called from a background poster.
     *
     * @return
     */
    private boolean isMainThread() {
        return mainThreadSupport == null || mainThreadSupport.isMainThread();
    }

    /**
     * Checks if the object is registered as subscriber and handler.
     *
     * @param object
     * @return
     */
    public synchronized boolean isRegistered(Object object) {
        return isRegisteredSubscriber(object)
                && isRegisteredHandler(object);
    }

    /**
     * Checks if the subscriber object is registered.
     *
     * @param subscriber
     * @return
     */
    public synchronized boolean isRegisteredSubscriber(Object subscriber) {
        return typesBySubscriber.containsKey(subscriber);
    }

    /**
     * Checks if the handler object is registered.
     *
     * @param handler
     * @return
     */
    public synchronized boolean isRegisteredHandler(Object handler) {
        return typesByHandler.containsKey(handler);
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
     * Unregisters the given handler object from the exceptional event type.
     * Important: Only updates handlementsByExceptionalEventType, not typesByHandler! Caller must update typesByHandler.
     *
     * @param handler
     * @param exceptionalEventType
     */
    private void unhandleByExceptionalEventType(Object handler, Class<?> exceptionalEventType) {
        List<Handlement> handlements = handlementsByExceptionalEventType.get(exceptionalEventType);
        if (handlements != null) {
            int size = handlements.size();
            for (int i = 0; i < size; i++) {
                Handlement handlement = handlements.get(i);
                if (handlement.handler == handler) {
                    handlement.active = false;
                    handlements.remove(i);
                    i--;
                    size--;
                }
            }
        }
    }

    /**
     * Unregisters the given object from all event and exceptional event classes.
     *
     * @param object
     */
    public synchronized void unregister(Object object) {
        unregisterSubscriber(object);
        unregisterHandler(object);
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
            logger.log(Level.WARNING, "Subscriber to unregister was not registered before: " + subscriber.getClass());
        }
    }

    /**
     * Unregisters the given handler from all exceptional event classes.
     *
     * @param handler
     */
    public synchronized void unregisterHandler(Object handler) {
        List<Class<?>> handledTypes = typesByHandler.get(handler);
        if (handledTypes != null) {
            for (Class<?> exceptionalEventType : handledTypes) {
                unhandleByExceptionalEventType(handler, exceptionalEventType);
            }
            typesByHandler.remove(handler);
        } else {
            logger.log(Level.WARNING, "Handler to unregister was not registered before: " + handler.getClass());
        }
    }

    /**
     * Posts the given event to the event bus.
     *
     * @param event
     */
    public void post(Object event) {
        synchronized (event) {
            if(startMechanismEnabled) {
                //Register classes with methods mapped as subscribe or handle.
                try {
                    registerMappedClasses();
                } catch (NoClassDefFoundError e) {
                    //At the moment, do nothing.
                }
            }

            //Put exceptional events in immediate queue.
            PostingThreadState immediatePostingState = currentImmediatePostingThreadState.get();
            putEventInPostingQueue(immediatePostingState, event);

            //Processes the thread that sends the messages that are in the immediate queue.
            processPostingThread(immediatePostingState);

            if(startMechanismEnabled && isEventMappedForActionMode(
                    event, ActionMode.LAZY_SUBSCRIBE)) {
                //Put events in late queue.
                PostingThreadState latePostingState = currentLatePostingThreadState.get();
                putEventInPostingQueue(latePostingState, event);

                //Prepare to start the activities that will receive the events of the late queue.
                prepareLatePostingEvent(event);
            }
        }
    }

    /**
     * Posts the given exceptional event to the event bus.
     *
     * @param exceptionalEvent
     */
    public void throwException(Object exceptionalEvent) {
        synchronized (exceptionalEvent) {
            if(startMechanismEnabled) {
                //Register classes with methods mapped as subscribe or handle.
                try {
                    registerMappedClasses();
                } catch (NoClassDefFoundError e) {
                    //At the moment, do nothing.
                }
            }

            //Put exceptional events in immediate queue.
            ThrowingThreadState immediateThrowingState = currentImmediateThrowingThreadState.get();
            putExceptionalEventInThrowingQueue(immediateThrowingState, exceptionalEvent);

            //Processes the thread that sends the messages that are in the immediate queue.
            processThrowingThread(immediateThrowingState);

            if(startMechanismEnabled && isExceptionalEventMappedForExceptionalActionMode(
                    exceptionalEvent, ExceptionalActionMode.LAZY_HANDLE)) {
                //Put exceptional events in late queue.
                ThrowingThreadState lateThrowingState = currentLateThrowingThreadState.get();
                putExceptionalEventInThrowingQueue(lateThrowingState, exceptionalEvent);

                //Prepare to start the activities that will receive the exceptional events of the late queue.
                prepareLateThrowingExceptionalEvent(exceptionalEvent);
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

                if(isRegisteredSubscriberClassForEvent(subscriberClass, event))
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

    public void putExceptionalEventInThrowingQueue(ThrowingThreadState throwingThreadState, Object exceptionalEvent) {
        if(throwingThreadState.isLate) {
            HashMap<Class<?>, ArrayList<Object>> lateExceptionalEventHandlerQueue = throwingThreadState.exceptionalEventHandlerQueue;
            Set<Class<?>> handlerClasses = getMappedHandlerClassForExceptionalEvent(exceptionalEvent);
            Iterator<Class<?>> it = handlerClasses.iterator();
            while(it.hasNext()) {
                Class<?> handlerClass = it.next();

                if(isRegisteredHandlerClassForExceptionalEvent(handlerClass, exceptionalEvent))
                    continue;

                if(lateExceptionalEventHandlerQueue.containsKey(handlerClass)) {
                    ArrayList<Object> exceptionalEventList = lateExceptionalEventHandlerQueue.get(handlerClass);
                    exceptionalEventList.add(exceptionalEvent);
                }
                else {
                    ArrayList<Object> exceptionalEventList = new ArrayList<Object>();
                    exceptionalEventList.add(exceptionalEvent);
                    lateExceptionalEventHandlerQueue.put(handlerClass, exceptionalEventList);
                }
            }
        }
        else {
            List<Object> immediateExceptionalEventQueue = throwingThreadState.exceptionalEventQueue;
            immediateExceptionalEventQueue.add(exceptionalEvent);
        }
    }

    /**
     * Identifies and stores data from classes that have mapped methods to perform
     * the processing of common events or exceptional events.
     */
    public void registerMappedClasses() {
        if(mappedClassesRegistrationPerformed)
            return;

        mappedClassesRegistrationPerformed = true;

        if(context == null)
            return;

        try {
            @SuppressLint({"NewApi", "LocalSuppress"})
            DexFile df = new DexFile(context.getPackageCodePath());

            for (Enumeration<String> iter = df.entries(); iter.hasMoreElements(); ) {
                String s = iter.nextElement();
                if (s.contains(context.getPackageName())) {
                    //System.out.println("ClasseDex: " + s);
                    try {
                        Class<?> classInPackage = Class.forName(s);
                        registerMappedClass(classInPackage);
                    } catch (ClassNotFoundException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    /**
     * Analyzes and stores data from a specific class that have mapped methods to perform
     * the processing of common events or exceptional events.
     */
    public void registerMappedClass(Class<?> classToMap) {
        boolean hasSubscriberMethods = subscriberMethodFinder.hasSubscriberMethods(classToMap);
        boolean hasHandlerMethods = handlerMethodFinder.hasHandlerMethods(classToMap);

        //Register classes that contains methods mapped with the @Subscribe annotation.
        if(hasSubscriberMethods) {
            List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(classToMap);
            synchronized (this) {
                for (SubscriberMethod subscriberMethod : subscriberMethods) {
                    subscribeClass(classToMap, subscriberMethod);
                }
            }
        }

        //Register classes that contains methods mapped with the @Handle annotation.
        if(hasHandlerMethods) {
            List<HandlerMethod> handlerMethods = handlerMethodFinder.findHandlerMethods(classToMap);
            synchronized (this) {
                for (HandlerMethod handlerMethod : handlerMethods) {
                    handleClass(classToMap, handlerMethod);
                }
            }
        }
        /*
        if(hasSubscriberMethods || hasHandlerMethods) {
            System.out.println("REGISTERED: MappedClass [ " + classInPackage.getName() + "]");
        }
        */
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
     * Called from a handler's exceptional event handling method, further exceptional event delivery will be canceled. Subsequent
     * handlers won't receive the exceptional event. Exceptional events are usually canceled by higher priority handlers (see
     * {@link Handle#priority()}). Canceling is restricted to exceptional event handling methods running in throwing thread
     * {@link ExceptionalThreadMode#THROWING}.
     *
     * @param exceptionalEvent
     */
    public void cancelExceptionalEventDelivery(Object exceptionalEvent) {
        ThrowingThreadState throwingState = currentImmediateThrowingThreadState.get();
        if (!throwingState.isThrowing) {
            throw new EventBusException(
                    "This method may only be called from inside exceptional event handling methods on the throwing thread");
        } else if (exceptionalEvent == null) {
            throw new EventBusException("Exceptional event may not be null");
        } else if (throwingState.exceptionalEvent != exceptionalEvent) {
            throw new EventBusException("Only the currently handled exceptional event may be aborted");
        } else if (throwingState.handlement.handlerMethod.threadMode != ExceptionalThreadMode.THROWING) {
            throw new EventBusException(" exceptional event handlers may only abort the incoming exceptional event");
        }

        throwingState.canceled = true;
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
     * Posts the given exceptional event to the event bus and holds on to the exceptional event (because it is sticky). The most recent sticky
     * exceptional event of an exceptional event's type is kept in memory for future access by handlers using {@link Handle#sticky()}.
     *
     * @param exceptionalEvent
     */
    public void throwSticky(Object exceptionalEvent) {
        synchronized (stickyExceptionalEvents) {
            stickyExceptionalEvents.put(exceptionalEvent.getClass(), exceptionalEvent);
        }
        // Should be throwed after it is putted, in case the handler wants to remove immediately
        throwException(exceptionalEvent);
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
     * Gets the most recent sticky exceptional event for the given type.
     *
     * @see #throwSticky(Object)
     *
     * @param exceptionalEventType
     * @param <T>
     * @return
     */
    public <T> T getStickyExceptionalEvent(Class<T> exceptionalEventType) {
        synchronized (stickyExceptionalEvents) {
            return exceptionalEventType.cast(stickyExceptionalEvents.get(exceptionalEventType));
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
     * Remove and gets the recent sticky exceptional event for the given exceptional event type.
     *
     * @see #throwSticky(Object)
     *
     * @param exceptionalEventType
     * @param <T>
     * @return
     */
    public <T> T removeStickyExceptionalEvent(Class<T> exceptionalEventType) {
        synchronized (stickyExceptionalEvents) {
            return exceptionalEventType.cast(stickyExceptionalEvents.remove(exceptionalEventType));
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
     * Removes the sticky exceptional event if it equals to the given exceptional event.
     * Returns true if the events matched and the sticky exceptional event was removed.
     *
     * @param exceptionalEvent
     * @return
     */
    public boolean removeStickyExceptionalEvent(Object exceptionalEvent) {
        synchronized (stickyExceptionalEvents) {
            Class<?> exceptionalEventType = exceptionalEvent.getClass();
            Object existingExceptionalEvent = stickyExceptionalEvents.get(exceptionalEventType);
            if (exceptionalEvent.equals(existingExceptionalEvent)) {
                stickyExceptionalEvents.remove(exceptionalEventType);
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
     * Removes all exceptional sticky events.
     */
    public void removeAllStickyExceptionalEvents() {
        synchronized (stickyExceptionalEvents) {
            stickyExceptionalEvents.clear();
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
     * Performs post (throw) processing of all exceptional events that are in the queue.
     *
     * @param throwingState
     */
    private void processThrowingThread(ThrowingThreadState throwingState) {
        processThrowingThread(null, throwingState);
    }

    /**
     * Performs post (throw) processing of all exceptional events in which the handler object is registered.
     *
     * @param handler
     * @param throwingState
     */
    private void processThrowingThread(Object handler, ThrowingThreadState throwingState) {
        List<Object> exceptionalEventQueue = throwingState.exceptionalEventQueue;
        HashMap<Class<?>, ArrayList<Object>> exceptionalEventHandlerQueue = throwingState.exceptionalEventHandlerQueue;

        if (!throwingState.isThrowing) {
            throwingState.isMainThread = isMainThread();
            throwingState.isThrowing = true;
            if (throwingState.canceled) {
                throw new EventBusException("Internal error. Abort state was not reset");
            }
            try {
                if (handler != null && throwingState.isLate) {
                    ArrayList<Object> exceptionalEventList = exceptionalEventHandlerQueue.get(handler.getClass());
                    if(exceptionalEventList != null) {
                        while (!exceptionalEventList.isEmpty()) {
                            throwSingleExceptionalEvent(exceptionalEventList.remove(0), handler, throwingState);
                        }
                    }
                }
                else {
                    while (!exceptionalEventQueue.isEmpty()) {
                        throwSingleExceptionalEvent(exceptionalEventQueue.remove(0), throwingState);
                    }
                }
            } finally {
                throwingState.isThrowing = false;
                throwingState.isMainThread = false;
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
                logger.log(Level.FINE, "No subscribers registered for event " + eventClass);
            }
            if (sendNoSubscriberEvent && eventClass != NoSubscriberEvent.class &&
                    eventClass != SubscriberExceptionEvent.class) {
                post(new NoSubscriberEvent(this, event));
            }
        }
    }

    /**
     * Post a specific exceptional event for all registered handler objects.
     *
     * @param exceptionalEvent
     * @param throwingState
     * @throws Error
     */
    private void throwSingleExceptionalEvent(Object exceptionalEvent, ThrowingThreadState throwingState) throws Error {
        throwSingleExceptionalEvent(exceptionalEvent, null, throwingState);
    }

    /**
     * Post a specific exceptional event for a specific registered handler object.
     *
     * @param exceptionalEvent
     * @param handler
     * @param throwingState
     * @throws Error
     */
    private void throwSingleExceptionalEvent(Object exceptionalEvent, Object handler, ThrowingThreadState throwingState) throws Error {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        boolean handlementFound = false;
        if (exceptionalEventInheritance) {
            List<Class<?>> exceptionalEventTypes = lookupAllExceptionalEventTypes(exceptionalEventClass);
            int countTypes = exceptionalEventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = exceptionalEventTypes.get(h);
                handlementFound |= throwsSingleExceptionalEventForExceptionalEventType(exceptionalEvent, handler, throwingState, clazz);
            }
        } else {
            handlementFound = throwsSingleExceptionalEventForExceptionalEventType(exceptionalEvent, handler, throwingState, exceptionalEventClass);
        }
        if (!handlementFound) {
            if (logNoHandlerMessages) {
                logger.log(Level.FINE, "No handlers registered for exceptional event " + exceptionalEventClass);
            }
            if (sendNoHandlerExceptionalEvent && exceptionalEventClass != NoHandlerExceptionalEvent.class &&
                    exceptionalEventClass != HandlerExceptionExceptionalEvent.class) {
                throwException(new NoHandlerExceptionalEvent(this, exceptionalEvent));
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

    /**
     *
     * @param exceptionalEvent
     * @param throwingState
     * @param exceptionalEventClass
     * @return
     */
    private boolean throwsSingleExceptionalEventForExceptionalEventType(Object exceptionalEvent, Object handler, ThrowingThreadState throwingState, Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<Handlement> handlements;
        synchronized (this) {
            handlements = handlementsByExceptionalEventType.get(exceptionalEventClass);
        }
        if (handlements != null && !handlements.isEmpty()) {
            for (Handlement handlement : handlements) {
                if(throwingState.isLate && handler != null && !handlement.handler.equals(handler))
                    continue;

                throwingState.exceptionalEvent = exceptionalEvent;
                throwingState.handlement = handlement;
                boolean aborted;
                try {
                    throwsToHandlement(handlement, exceptionalEvent, throwingState.isMainThread);
                    aborted = throwingState.canceled;
                } finally {
                    throwingState.exceptionalEvent = null;
                    throwingState.handlement = null;
                    throwingState.canceled = false;
                }
                if (aborted) {
                    break;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Checks if there is any registered subscriber object to be invoked to process the event.
     *
     * @param event
     * @return
     */
    public boolean hasSubscriberForEvent(Object event) {
        return (event == null) ? false : hasSubscriberForEventType(event.getClass());
    }

    /**
     * Checks if there is any registered handler object to be invoked to process the exceptional event.
     *
     * @param exceptionalEvent
     * @return
     */
    public boolean hasHandlerForExceptionalEvent(Object exceptionalEvent) {
        return (exceptionalEvent == null) ? false : hasHandlerForExceptionalEventType(exceptionalEvent.getClass());
    }

    /**
     * Checks if there is any registered subscriber object to be invoked to process this type of event.
     *
     * @param eventClass
     * @return
     */
    public boolean hasSubscriberForEventType(Class<?> eventClass) {
        if (eventInheritance) {
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            if (eventTypes != null) {
                int countTypes = eventTypes.size();
                for (int h = 0; h < countTypes; h++) {
                    Class<?> clazz = eventTypes.get(h);
                    if (hasSubscriptionForEventType(clazz)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return hasSubscriptionForEventType(eventClass);
    }

    /**
     * Checks if there is any registered handler object to be invoked to process this type of exceptional event.
     *
     * @param exceptionalEventClass
     * @return
     */
    public boolean hasHandlerForExceptionalEventType(Class<?> exceptionalEventClass) {
        if (exceptionalEventInheritance) {
            List<Class<?>> exceptionalEventTypes = lookupAllExceptionalEventTypes(exceptionalEventClass);
            if (exceptionalEventTypes != null) {
                int countTypes = exceptionalEventTypes.size();
                for (int h = 0; h < countTypes; h++) {
                    Class<?> clazz = exceptionalEventTypes.get(h);
                    if (hasHandlementForExceptionalEventType(clazz)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return hasHandlementForExceptionalEventType(exceptionalEventClass);
    }

    /**
     * Checks if there is any subscription associated with this type of event.
     *
     * @param eventClass
     * @return
     */
    private boolean hasSubscriptionForEventType(Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions = null;
        synchronized (this) {
            subscriptions = subscriptionsByEventType.get(eventClass);
            return subscriptions != null && !subscriptions.isEmpty();
        }
    }

    /**
     * Checks if there is any handlement associated with this type of exceptional event.
     *
     * @param exceptionalEventClass
     * @return
     */
    private boolean hasHandlementForExceptionalEventType(Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<Handlement> handlements = null;
        synchronized (this) {
            handlements = handlementsByExceptionalEventType.get(exceptionalEventClass);
            return handlements != null && !handlements.isEmpty();
        }
    }

    /**
     * Checks if there is any class, which does not necessarily have any instance registered as a subscriber,
     * that has a mapped method to be invoked to process the event.
     *
     * @param event
     * @return
     */
    public boolean hasMappedSubscriberClassForEvent(Object event) {
        return (event == null) ? false : hasMappedSubscriberClassForEventType(event.getClass());
    }

    /**
     * Checks if there is any class, which does not necessarily have any instance registered as a handler,
     * that has a mapped method to be invoked to process the exceptional event.
     *
     * @param exceptionalEvent
     * @return
     */
    public boolean hasMappedHandlerClassForExceptionalEvent(Object exceptionalEvent) {
        return (exceptionalEvent == null) ? false : hasMappedHandlerClassForExceptionalEventType(exceptionalEvent.getClass());
    }

    /**
     * Checks if there is any class, which does not necessarily have any instance registered as a subscriber,
     * that has a mapped method to be invoked to process this type of event.
     *
     * @param eventClass
     * @return
     */
    public boolean hasMappedSubscriberClassForEventType(Class<?> eventClass) {
        if (eventInheritance) {
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            if (eventTypes != null) {
                int countTypes = eventTypes.size();
                for (int h = 0; h < countTypes; h++) {
                    Class<?> clazz = eventTypes.get(h);
                    if (hasMappedClassSubscriptionForEventType(clazz)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return hasMappedClassSubscriptionForEventType(eventClass);
    }

    /**
     * Checks if there is any class, which does not necessarily have any instance registered as a handler,
     * that has a mapped method to be invoked to process this type of exceptional event.
     *
     * @param exceptionalEventClass
     * @return
     */
    public boolean hasMappedHandlerClassForExceptionalEventType(Class<?> exceptionalEventClass) {
        if (exceptionalEventInheritance) {
            List<Class<?>> exceptionalEventTypes = lookupAllExceptionalEventTypes(exceptionalEventClass);
            if (exceptionalEventTypes != null) {
                int countTypes = exceptionalEventTypes.size();
                for (int h = 0; h < countTypes; h++) {
                    Class<?> clazz = exceptionalEventTypes.get(h);
                    if (hasMappedClassHandlementForExceptionalEventType(clazz)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return hasMappedClassHandlementForExceptionalEventType(exceptionalEventClass);
    }

    /**
     * Checks if there is any 'subscription associated with this type of event.
     *
     * @param eventClass
     * @return
     */
    private boolean hasMappedClassSubscriptionForEventType(Class<?> eventClass) {
        CopyOnWriteArrayList<SubscriberClass> mappedSubscriberClasses;
        synchronized (this) {
            mappedSubscriberClasses = mappedSubscriberClassesByEventType.get(eventClass);
            return mappedSubscriberClasses != null && !mappedSubscriberClasses.isEmpty();
        }
    }

    /**
     * Checks if there is any handlement associated with this type of exceptional event.
     *
     * @param exceptionalEventClass
     * @return
     */
    private boolean hasMappedClassHandlementForExceptionalEventType(Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<HandlerClass> mappedHandlerClasses;
        synchronized (this) {
            mappedHandlerClasses = mappedHandlerClassesByExceptionalEventType.get(exceptionalEventClass);
            return mappedHandlerClasses != null && !mappedHandlerClasses.isEmpty();
        }
    }

    /**
     * Checks if the subscriber object is registered for the event.
     *
     * @param subscriber
     * @param event
     * @return
     */
    private boolean isSubscriberForEvent(Object subscriber, Object event) {
        Class<?> eventClass = event.getClass();
        Class<?> subscriberClass = subscriber.getClass();
        if (eventInheritance) {
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                if(isSubscriberForEventType(subscriber, clazz))
                    return true;
            }
            return false;
        }
        return isSubscriberForEventType(subscriber, eventClass);
    }

    /**
     * Checks if the handler object is registered for the exceptional event.
     *
     * @param handler
     * @param exceptionalEvent
     * @return
     */
    private boolean isHandlerForExceptionalEvent(Object handler, Object exceptionalEvent) {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        Class<?> handlerClass = handler.getClass();
        if (exceptionalEventInheritance) {
            List<Class<?>> exceptionalEventTypes = lookupAllExceptionalEventTypes(exceptionalEventClass);
            int countTypes = exceptionalEventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = exceptionalEventTypes.get(h);
                if(isHandlerForExceptionalEventType(handler, clazz))
                    return true;
            }
            return false;
        }
        return isHandlerForExceptionalEventType(handler, exceptionalEventClass);
    }

    /**
     * Checks if the subscriber object is registered for this type of event.
     *
     * @param subscriber
     * @param eventClass
     * @return
     */
    private boolean isSubscriberForEventType(Object subscriber, Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            subscriptions = subscriptionsByEventType.get(eventClass);
            if(subscriptions != null && !subscriptions.isEmpty()) {
                for(Subscription subscription : subscriptions) {
                    if(subscription.subscriber.equals(subscriber))
                        return true;
                }
            }
            return false;
        }
    }

    /**
     * Checks if the handler object is registered for this type of exceptional event.
     *
     * @param handler
     * @param exceptionalEventClass
     * @return
     */
    private boolean isHandlerForExceptionalEventType(Object handler, Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<Handlement> handlements;
        synchronized (this) {
            handlements = handlementsByExceptionalEventType.get(exceptionalEventClass);
            if(handlements != null && !handlements.isEmpty()) {
                for(Handlement handlement : handlements) {
                    if(handlement.handler.equals(handler))
                        return true;
                }
            }
            return false;
        }
    }

    private boolean isRegisteredSubscriberClassForEvent(Class<?> subscriberClass, Object event) {
        Class<?> eventClass = event.getClass();
        if (eventInheritance) {
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                if(isRegisteredSubscriberClassForEventType(subscriberClass, clazz))
                    return true;
            }
            return false;
        }
        return isRegisteredSubscriberClassForEventType(subscriberClass, eventClass);
    }

    private boolean isRegisteredSubscriberClassForEventType(Class<?> subscriberClassType, Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            subscriptions = subscriptionsByEventType.get(eventClass);
            if(subscriptions != null && !subscriptions.isEmpty()) {
                for(Subscription subscription : subscriptions) {
                    if(subscription.subscriber.getClass().equals(subscriberClassType))
                        return true;
                }
            }
            return false;
        }
    }

    private boolean isRegisteredHandlerClassForExceptionalEvent(Class<?> handlerClass, Object exceptionalEvent) {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        if (exceptionalEventInheritance) {
            List<Class<?>> exceptionalEventTypes = lookupAllExceptionalEventTypes(exceptionalEventClass);
            int countTypes = exceptionalEventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = exceptionalEventTypes.get(h);
                if(isRegisteredHandlerClassForExceptionalEventType(handlerClass, clazz))
                    return true;
            }
            return false;
        }
        return isRegisteredHandlerClassForExceptionalEventType(handlerClass, exceptionalEventClass);
    }

    private boolean isRegisteredHandlerClassForExceptionalEventType(Class<?> handlerClassType, Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<Handlement> handlements;
        synchronized (this) {
            handlements = handlementsByExceptionalEventType.get(exceptionalEventClass);
            if(handlements != null && !handlements.isEmpty()) {
                for(Handlement handlement : handlements) {
                    if(handlement.handler.getClass().equals(handlerClassType))
                        return true;
                }
            }
            return false;
        }
    }

    /**
     * Checks if the class is registered as 'subscriber class' for the event.
     * Subscriber class is a class which has methods mapped as subscriber for the event.
     *
     * @param subscriberClass
     * @param event
     * @return
     */
    private boolean isMappedSubscriberClassForEvent(Class<?> subscriberClass, Object event) {
        Class<?> eventClass = event.getClass();
        if (eventInheritance) {
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                if(isMappedSubscriberClassForEventType(subscriberClass, clazz))
                    return true;
            }
            return false;
        }
        return isMappedSubscriberClassForEventType(subscriberClass, eventClass);
    }

    /**
     * Checks if the class is registered as 'handler class' for the exceptional event.
     * Handler class is a class which has methods mapped as handler for the exceptional event.
     *
     * @param handlerClass
     * @param exceptionalEvent
     * @return
     */
    private boolean isMappedHandlerClassForExceptionalEvent(Class<?> handlerClass, Object exceptionalEvent) {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        if (exceptionalEventInheritance) {
            List<Class<?>> exceptionalEventTypes = lookupAllExceptionalEventTypes(exceptionalEventClass);
            int countTypes = exceptionalEventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = exceptionalEventTypes.get(h);
                if(isMappedHandlerClassForExceptionalEventType(handlerClass, clazz))
                    return true;
            }
            return false;
        }
        return isMappedHandlerClassForExceptionalEventType(handlerClass, exceptionalEventClass);
    }

    /**
     * Checks if the class is registered as 'subscriber class' for this type of event.
     *
     * @param subscriberClassType
     * @param eventClass
     * @return
     */
    private boolean isMappedSubscriberClassForEventType(Class<?> subscriberClassType, Class<?> eventClass) {
        CopyOnWriteArrayList<SubscriberClass> subscriberClasses;
        synchronized (this) {
            subscriberClasses = mappedSubscriberClassesByEventType.get(eventClass);
            if(subscriberClasses != null && !subscriberClasses.isEmpty()) {
                for(SubscriberClass subscriberClass : subscriberClasses) {
                    if(eventInheritance) {
                        if(subscriberClass.subscriberClass.isAssignableFrom(subscriberClassType))
                            return true;
                    }
                    else {
                        if(subscriberClass.subscriberClass.equals(subscriberClassType))
                            return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * Checks if the class is registered as 'handler class' for this type of exceptional event.
     *
     * @param handlerClassType
     * @param exceptionalEventClass
     * @return
     */
    private boolean isMappedHandlerClassForExceptionalEventType(Class<?> handlerClassType, Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<HandlerClass> handlerClasses;
        synchronized (this) {
            handlerClasses = mappedHandlerClassesByExceptionalEventType.get(exceptionalEventClass);
            if(handlerClasses != null && !handlerClasses.isEmpty()) {
                for(HandlerClass handlerClass : handlerClasses) {
                    if(exceptionalEventInheritance) {
                        if(handlerClass.handlerClass.isAssignableFrom(handlerClassType))
                            return true;
                    }
                    else {
                        if(handlerClass.handlerClass.equals(handlerClassType))
                            return true;
                    }
                }
            }
            return false;
        }
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

    private Set<Class<?>> getMappedHandlerClassForExceptionalEvent(Object exceptionalEvent) {
        Set<Class<?>> handlerClassesSet = new HashSet<Class<?>>();
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        if (exceptionalEventInheritance) {
            List<Class<?>> exceptionalEventTypes = lookupAllExceptionalEventTypes(exceptionalEventClass);
            int countTypes = exceptionalEventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = exceptionalEventTypes.get(h);
                handlerClassesSet.addAll(getMappedHandlerClassForExceptionalEventType(clazz));
            }
        }
        else {
            handlerClassesSet.addAll(getMappedHandlerClassForExceptionalEventType(exceptionalEventClass));
        }
        return handlerClassesSet;
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

    private Set<Class<?>> getMappedHandlerClassForExceptionalEventType(Class<?> exceptionalEventClass) {
        Set<Class<?>> handlerClassesSet = new HashSet<Class<?>>();
        CopyOnWriteArrayList<HandlerClass> handlerClasses;
        synchronized (this) {
            handlerClasses = mappedHandlerClassesByExceptionalEventType.get(exceptionalEventClass);
            if(handlerClasses != null && !handlerClasses.isEmpty()) {
                for(HandlerClass handlerClass : handlerClasses) {
                    handlerClassesSet.add(handlerClass.handlerClass);
                }
            }
            return handlerClassesSet;
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
     *
     * @param handlement
     * @param exceptionalEvent
     * @param isMainThread
     */
    private void throwsToHandlement(Handlement handlement, Object exceptionalEvent, boolean isMainThread) {
        switch (handlement.handlerMethod.threadMode) {
            case THROWING:
                invokeHandler(handlement, exceptionalEvent);
                break;
            case MAIN:
                if (isMainThread) {
                    invokeHandler(handlement, exceptionalEvent);
                } else {
                    mainThreadThrower.enqueue(handlement, exceptionalEvent);
                }
                break;
            case MAIN_ORDERED:
                if (mainThreadThrower != null) {
                    mainThreadThrower.enqueue(handlement, exceptionalEvent);
                } else {
                    // temporary: technically not correct as poster not decoupled from subscriber
                    invokeHandler(handlement, exceptionalEvent);
                }
                break;
            case BACKGROUND:
                if (isMainThread) {
                    backgroundThrower.enqueue(handlement, exceptionalEvent);
                } else {
                    invokeHandler(handlement, exceptionalEvent);
                }
                break;
            case ASYNC:
                asyncThrower.enqueue(handlement, exceptionalEvent);
                break;
            default:
                throw new IllegalStateException("Unknown thread mode: " + handlement.handlerMethod.threadMode);
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
     * Prepares the exceptional event to be sent to the handlers who will receive it through the late send queue.
     *
     * @param exceptionalEvent
     * @throws Error
     */
    private void prepareLateThrowingExceptionalEvent(Object exceptionalEvent) throws Error {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        boolean handlerClassFound = false;
        if (exceptionalEventInheritance) {
            List<Class<?>> exceptionalEventTypes = lookupAllExceptionalEventTypes(exceptionalEventClass);
            int countTypes = exceptionalEventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = exceptionalEventTypes.get(h);
                handlerClassFound |= prepareLateThrowingExceptionalEventForExceptionalEventType(exceptionalEvent, clazz);
            }
        } else {
            handlerClassFound = prepareLateThrowingExceptionalEventForExceptionalEventType(exceptionalEvent, exceptionalEventClass);
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
                            Intent intent = new Intent(context, subscriberClassType);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                        }
                        else if(Service.class.isAssignableFrom(subscriberClassType)) {
                            Intent intent = new Intent(context, subscriberClassType);
                            context.startService(intent);
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
     * Prepares the exceptional event to be sent to handlers who will receive it via the late send queue,
     * requiring the type of event to be specified.
     *
     * This method aims to ensure that, if the handler object is activity, they will be started so that they receive the exceptional events.
     * For this, it is necessary that the handler method has the {@link Handle#actionMode} parameter of the  {@link Handle} annotation
     * with the {@link ExceptionalActionMode#LAZY_HANDLE} value.
     *
     * @param exceptionalEvent
     * @param exceptionalEventClass
     * @return
     */
    private boolean prepareLateThrowingExceptionalEventForExceptionalEventType(Object exceptionalEvent, Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<HandlerClass> handlerClasses;
        synchronized (this) {
            handlerClasses = mappedHandlerClassesByExceptionalEventType.get(exceptionalEventClass);
        }
        if (handlerClasses != null && !handlerClasses.isEmpty()) {
            for (HandlerClass handlerClass : handlerClasses) {
                if(handlerClass.handlerMethod.actionMode == ExceptionalActionMode.LAZY_HANDLE) {
                    Class<?> handlerClassType = handlerClass.handlerClass;
                    if(ExceptionalActionMode.isTypeEnableFor(handlerClassType, ExceptionalActionMode.LAZY_HANDLE)) {
                        if(Activity.class.isAssignableFrom(handlerClassType)) {
                            Intent intent = new Intent(context, handlerClassType);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                        }
                        else if(Service.class.isAssignableFrom(handlerClassType)) {
                            Intent intent = new Intent(context, handlerClassType);
                            context.startService(intent);
                        }
                    }
                    else {
                        throw new EventBusException("The type of this handler is not enabled for the 'start and handle' action mode.");
                    }
                }
            }
            return true;
        }
        return false;
    }

    private boolean isSubscriberMappedForActionMode(Class<?> subscriberClass, ActionMode actionMode) {
        if(!ActionMode.isTypeEnableFor(subscriberClass, actionMode))
            return false;

        List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(subscriberClass);
        for(SubscriberMethod m : subscriberMethods) {
            if(m.actionMode.equals(actionMode))
                return true;
        }
        return false;
    }

    private boolean isHandlerMappedForExceptionalActionMode(Class<?> handlerClass, ExceptionalActionMode exceptionalActionMode) {
        if(!ExceptionalActionMode.isTypeEnableFor(handlerClass, exceptionalActionMode))
            return false;

        List<HandlerMethod> handlerMethods = handlerMethodFinder.findHandlerMethods(handlerClass);
        for(HandlerMethod h : handlerMethods) {
            if(h.actionMode.equals(exceptionalActionMode))
                return true;
        }
        return false;
    }

    private boolean isEventMappedForActionMode(Object event, ActionMode actionMode) {
        Class<?> eventClass = event.getClass();
        boolean eventTypeMapped = false;
        if (eventInheritance) {
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes && !eventTypeMapped; h++) {
                Class<?> clazz = eventTypes.get(h);
                eventTypeMapped |= isEventTypeMappedForActionMode(clazz, actionMode);
            }
        } else {
            eventTypeMapped = isEventTypeMappedForActionMode(eventClass, actionMode);
        }

        return eventTypeMapped;
    }

    private boolean isExceptionalEventMappedForExceptionalActionMode(Object exceptionalEvent, ExceptionalActionMode exceptionalActionMode) {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        boolean exceptionalEventTypeMapped = false;
        if (exceptionalEventInheritance) {
            List<Class<?>> exceptionalEventTypes = lookupAllExceptionalEventTypes(exceptionalEventClass);
            int countTypes = exceptionalEventTypes.size();
            for (int h = 0; h < countTypes && !exceptionalEventTypeMapped; h++) {
                Class<?> clazz = exceptionalEventTypes.get(h);
                exceptionalEventTypeMapped |= isExceptionalEventTypeMappedForExceptionalActionMode(clazz, exceptionalActionMode);
            }
        } else {
            exceptionalEventTypeMapped = isExceptionalEventTypeMappedForExceptionalActionMode(exceptionalEventClass, exceptionalActionMode);
        }

        return exceptionalEventTypeMapped;
    }

    private boolean isEventTypeMappedForActionMode(Class<?> eventClass, ActionMode actionMode) {
        CopyOnWriteArrayList<SubscriberClass> subscriberClasses =
                mappedSubscriberClassesByEventType.get(eventClass);
        if (subscriberClasses != null && !subscriberClasses.isEmpty()) {
            for (SubscriberClass subscriberClass : subscriberClasses) {
                if (ActionMode.isTypeEnableFor(subscriberClass.subscriberClass, actionMode)
                        && subscriberClass.subscriberMethod.actionMode == actionMode) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isExceptionalEventTypeMappedForExceptionalActionMode(Class<?> exceptionalEventClass, ExceptionalActionMode exceptionalActionMode) {
        CopyOnWriteArrayList<HandlerClass> handlerClasses =
                mappedHandlerClassesByExceptionalEventType.get(exceptionalEventClass);
        if (handlerClasses != null && !handlerClasses.isEmpty()) {
            for (HandlerClass handlerClass : handlerClasses) {
                if (ExceptionalActionMode.isTypeEnableFor(handlerClass.handlerClass, exceptionalActionMode)
                        && handlerClass.handlerMethod.actionMode == exceptionalActionMode) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Looks up all Class objects including super classes and interfaces. Should also work for interfaces.
     *
     * @param eventClass
     * @return
     */
    private static List<Class<?>> lookupAllEventTypes(Class<?> eventClass) {
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
     * Looks up all Class objects including super classes and interfaces. Should also work for interfaces.
     *
     * @param exceptionalEventClass
     * @return
     */
    private static List<Class<?>> lookupAllExceptionalEventTypes(Class<?> exceptionalEventClass) {
        synchronized (exceptionalEventTypesCache) {
            List<Class<?>> exceptionalEventTypes = exceptionalEventTypesCache.get(exceptionalEventClass);
            if (exceptionalEventTypes == null) {
                exceptionalEventTypes = new ArrayList<>();
                Class<?> clazz = exceptionalEventClass;
                while (clazz != null) {
                    exceptionalEventTypes.add(clazz);
                    addInterfaces(exceptionalEventTypes, clazz.getInterfaces());
                    clazz = clazz.getSuperclass();
                }
                exceptionalEventTypesCache.put(exceptionalEventClass, exceptionalEventTypes);
            }
            return exceptionalEventTypes;
        }
    }

    /**
     * Recurses through super interfaces.
     *
     * @param eventTypes
     * @param interfaces
     */
    static void addInterfaces(List<Class<?>> eventTypes, Class<?>[] interfaces) {
        for (Class<?> interfaceClass : interfaces) {
            if (!eventTypes.contains(interfaceClass)) {
                eventTypes.add(interfaceClass);
                addInterfaces(eventTypes, interfaceClass.getInterfaces());
            }
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
     * Invokes the handler if the handlements is still active. Skipping handlements prevents race conditions
     * between {@link #unregisterHandler(Object)} and exceptional event delivery. Otherwise the exceptional event might be delivered after the
     * handler unregistered. This is particularly important for main thread delivery and registrations bound to the
     * live cycle of an Activity or Fragment.
     *
     * @param pendingThrow
     */
    void invokeHandler(PendingThrow pendingThrow) {
        Object exceptionalEvent = pendingThrow.exceptionalEvent;
        Handlement handlement = pendingThrow.handlement;
        PendingThrow.releasePendingThrow(pendingThrow);
        if (handlement.active) {
            invokeHandler(handlement, exceptionalEvent);
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
     * Invokes the handler to perform some processing on the exceptional event.
     *
     * @param handlement
     * @param exceptionalEvent
     */
    void invokeHandler(Handlement handlement, Object exceptionalEvent) {
        try {
            handlement.handlerMethod.method.invoke(handlement.handler, exceptionalEvent);
        } catch (InvocationTargetException e) {
            handleHandlerException(handlement, exceptionalEvent, e.getCause());
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
                logger.log(Level.SEVERE, "SubscriberExceptionEvent subscriber " + subscription.subscriber.getClass()
                        + " threw an exception", cause);
                SubscriberExceptionEvent exEvent = (SubscriberExceptionEvent) event;
                logger.log(Level.SEVERE, "Initial event " + exEvent.causingEvent + " caused exception in "
                        + exEvent.causingSubscriber, exEvent.throwable);
            }
        } else {
            if (throwSubscriberException) {
                throw new EventBusException("Invoking subscriber failed", cause);
            }
            if (logSubscriberExceptions) {
                logger.log(Level.SEVERE, "Could not dispatch event: " + event.getClass() + " to subscribing class "
                        + subscription.subscriber.getClass(), cause);
            }
            if (sendSubscriberExceptionEvent) {
                SubscriberExceptionEvent exEvent = new SubscriberExceptionEvent(this, cause, event,
                        subscription.subscriber);
                post(exEvent);
            }
        }
    }

    /**
     * Process exception caught during invocation of the handler.
     *
     * @param handlement
     * @param exceptionalEvent
     * @param cause
     */
    private void handleHandlerException(Handlement handlement, Object exceptionalEvent, Throwable cause) {
        if (exceptionalEvent instanceof HandlerExceptionExceptionalEvent) {
            if (logHandlerExceptions) {
                // Don't send another HandlerExceptionExceptionalEvent to avoid infinite exceptional event recursion, just log.
                logger.log(Level.SEVERE, "HandlerExceptionExceptionalEvent handler " + handlement.handler.getClass()
                        + " threw an exception", cause);
                HandlerExceptionExceptionalEvent exExceptionalEvent = (HandlerExceptionExceptionalEvent) exceptionalEvent;
                logger.log(Level.SEVERE, "Initial exceptional event " + exExceptionalEvent.causingExceptionalEvent + " caused exception in "
                        + exExceptionalEvent.causingHandler, exExceptionalEvent.throwable);
            }
        } else {
            if (throwHandlerException) {
                throw new EventBusException("Invoking handler failed", cause);
            }
            if (logHandlerExceptions) {
                logger.log(Level.SEVERE, "Could not dispatch exceptional event: " + exceptionalEvent.getClass() + " to handling class "
                        + handlement.handler.getClass(), cause);
            }
            if (sendHandlerExceptionExceptionalEvent) {
                HandlerExceptionExceptionalEvent exExceptionalEvent = new HandlerExceptionExceptionalEvent(this, cause, exceptionalEvent,
                        handlement.handler);
                throwException(exExceptionalEvent);
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

    /**
     * For ThreadLocal, much faster to set (and get multiple values).
     */
    final static class ThrowingThreadState {
        final List<Object> exceptionalEventQueue = new ArrayList<>();
        final HashMap<Class<?>, ArrayList<Object>> exceptionalEventHandlerQueue = new HashMap<Class<?>, ArrayList<Object>>();
        boolean isThrowing;
        boolean isMainThread;
        boolean isLate;
        Handlement handlement;
        Object exceptionalEvent;
        boolean canceled;

        public ThrowingThreadState() {
            super();
        }

        public ThrowingThreadState(boolean isLate) {
            super();
            this.isLate = isLate;
        }
    }

    /**
     * Get method for executorService.
     *
     * @return
     */
    ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * Get method for logger.
     * For internal use only.
     *
     * @return
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * Just an idea: we could provide a callback to post() to be notified, an alternative would be events, of course...
     */
    /* public */ interface PostCallback {
        void onPostCompleted(List<SubscriberExceptionEvent> exceptionEvents);
    }

    /**
     * Just an idea: we could provide a callback to throws() to be notified, an alternative would be exceptional events, of course...
     */
    /* public */ interface ThrowsCallback {
        void onThrowsCompleted(List<HandlerExceptionExceptionalEvent> exceptionExceptionalEvents);
    }

    @Override
    public String toString() {
        return "EventBus[indexCount=" + indexCount
                + ", indexCountSubscriber=" + indexCountSubscriber
                + ", indexCountHandler=" + indexCountHandler
                + ", eventInheritance=" + eventInheritance
                + ", exceptionalEventInheritance=" + exceptionalEventInheritance + "]";
    }
}