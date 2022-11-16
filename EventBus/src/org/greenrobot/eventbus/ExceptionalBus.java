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

public class ExceptionalBus extends AbstractBus {
    /** Log tag, apps may override it. */
    public static String TAG = "ExceptionalBus";

    static volatile ExceptionalBus defaultInstance;

    private static final Map<Class<?>, List<Class<?>>> exceptionalEventTypesCache = new HashMap<>();

    private Map<Class<?>, CopyOnWriteArrayList<LazyHandling>> mappedHandlingsByExceptionalEventType;
    private Map<Class<?>, CopyOnWriteArrayList<LazyHandling>> lazyHandlingsByExceptionalEventType;
    private Map<Class<?>, CopyOnWriteArrayList<Handling>> eagerHandlingsByExceptionalEventType;

    private Map<Object, List<Class<?>>> typesByHandler;
    private Map<Class<?>, Object> stickyExceptionalEvents;

    private final ExceptionalBusQuerier querier = new ExceptionalBusQuerier(this);
    /**
     * Current Eager Thread State.
     * For exceptional events sent eagerly to objects already instantiated.
     */
    private final ThreadLocal<ThrowingThreadState> currentEagerThrowingThreadState = new ThreadLocal<ThrowingThreadState>() {
        @Override
        protected ThrowingThreadState initialValue() {
            return new ThrowingThreadState(false);
        }
    };

    /**
     * Current Lazy Thread State.
     * For exceptional events sent lazy for objects that are yet to be instantiated.
     */
    private final ThreadLocal<ThrowingThreadState> currentLazyThrowingThreadState = new ThreadLocal<ThrowingThreadState>() {
        @Override
        protected ThrowingThreadState initialValue() {
            return new ThrowingThreadState(true);
        }
    };

    private Thrower mainThreadThrower;
    private BackgroundThrower backgroundThrower;
    private AsyncThrower asyncThrower;
    private HandlerMethodFinder handlerMethodFinder;

    private boolean throwHandlerException;
    private boolean logHandlerExceptions;
    private boolean logNoHandlerMessages;
    private boolean sendHandlerExceptionExceptionalEvent;
    private boolean sendNoHandlerExceptionalEvent;
    private boolean exceptionalEventInheritance;

    private int indexCount;

    /**
     * Creates a new EventBus instance; each instance is a separate scope in which events are delivered.
     *
     * @param eventbus
     */
    public ExceptionalBus(EventBus eventbus, ExceptionalBusBuilder builder) {
        super(eventbus);

        mappedHandlingsByExceptionalEventType = new HashMap<>();
        lazyHandlingsByExceptionalEventType = new HashMap<>();
        eagerHandlingsByExceptionalEventType = new HashMap<>();

        typesByHandler = new HashMap<>();
        stickyExceptionalEvents = new ConcurrentHashMap<>();

        MainThreadSupport mainThreadSupport = builder.eventBusBuilder.getMainThreadSupport();

        /** Throw/Handlers */
        mainThreadThrower = mainThreadSupport != null
                ? mainThreadSupport.createThrower(getEventBus()) : null;
        backgroundThrower = new BackgroundThrower(getEventBus());
        asyncThrower = new AsyncThrower(getEventBus());

        indexCount = builder.handlerInfoIndexes != null
                ? builder.handlerInfoIndexes.size() : 0;

        handlerMethodFinder = new HandlerMethodFinder(builder.handlerInfoIndexes,
                builder.eventBusBuilder.strictMethodVerification,
                builder.eventBusBuilder.ignoreGeneratedIndex);

        logHandlerExceptions = builder.logHandlerExceptions;
        logNoHandlerMessages = builder.logNoHandlerMessages;
        sendHandlerExceptionExceptionalEvent = builder.sendHandlerExceptionExceptionalEvent;
        sendNoHandlerExceptionalEvent= builder.sendNoHandlerExceptionalEvent;
        throwHandlerException = builder.throwHandlerException;
        exceptionalEventInheritance = builder.exceptionalEventInheritance;
    }

    /**
     * For unit test primarily.
     */
    public static void clearCaches() {
        /** Handlers */
        HandlerMethodFinder.clearCaches();
        exceptionalEventTypesCache.clear();
    }

    /**
     * Registers the given handler to receive exceptional events. Handlers must call {@link #unregister(Object)} once they
     * are no longer interested in receiving exceptional events.
     * <p>
     * Handlers have exceptional event handling methods that must be annotated by {@link Handle}.
     * The {@link Handle} annotation also allows configuration like {@link
     * ExceptionalThreadMode} and priority.
     *
     * @param handler
     */
    public void register(Object handler) {
        Class<?> handlerClass = handler.getClass();
        List<HandlerMethod> handlerMethods = handlerMethodFinder.findHandlerMethods(handlerClass);
        synchronized (this) {
            for (HandlerMethod handlerMethod : handlerMethods) {
                handle(handler, handlerMethod);
            }
        }

        if(isLazyMechanismEnabled() && querier.isHandlerMappedForExceptionalActionMode(
                handlerClass, ExceptionalActionMode.LAZY_HANDLE)) {
            //Processes the thread that sends the messages that are in the lazy queue.
            ThrowingThreadState lazyThrowingState = currentLazyThrowingThreadState.get();
            processThrowingThread(handler, lazyThrowingState);
        }
    }

    /**
     * Registers a handling, which consists of an association between a handler object and a handler method,
     * which will be invoked to handle a given exceptional event.
     *
     * Important: Must be called in synchronized block.
     *
     * @param handler
     * @param handlerMethod
     */
    private void handle(Object handler, HandlerMethod handlerMethod) {
        Class<?> exceptionalEventType = handlerMethod.exceptionalEventType;
        Handling newHandling = new Handling(handler, handlerMethod);
        CopyOnWriteArrayList<Handling> handlings = eagerHandlingsByExceptionalEventType.get(exceptionalEventType);
        if (handlings == null) {
            handlings = new CopyOnWriteArrayList<>();
            eagerHandlingsByExceptionalEventType.put(exceptionalEventType, handlings);
        } else {
            if (handlings.contains(newHandling)) {
                throw new EventBusException("Handler " + handler.getClass() + " already registered to exceptional event "
                        + exceptionalEventType);
            }
        }

        int size = handlings.size();
        for (int i = 0; i <= size; i++) {
            if (i == size || handlerMethod.priority > handlings.get(i).handlerMethod.priority) {
                handlings.add(i, newHandling);
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
                        checkThrowsExceptionalStickyEventToHandling(newHandling, stickyExceptionalEvent);
                    }
                }
            } else {
                Object stickyExceptionalEvent = stickyExceptionalEvents.get(exceptionalEventType);
                checkThrowsExceptionalStickyEventToHandling(newHandling, stickyExceptionalEvent);
            }
        }
    }

    /**
     * Registers a lazy handling, which consists of an association between a handler class and a handler method,
     * which has handler methods mapped with actionMode LAZY_HANDLE, and one of these methods.
     *
     * Important: Must be called in synchronized block.
     *
     * @param handlerClass
     * @param handlerMethod
     */
    private void lazyHandle(Class<?> handlerClass, HandlerMethod handlerMethod) {
        if(handlerMethod.actionMode != ExceptionalActionMode.LAZY_HANDLE) {
            throw new EventBusException("The method " + handlerClass
                    + "::" + handlerMethod.methodString + " is not marked with actionMode LAZY_HANDLE.");
        }

        Class<?> exceptionalEventType = handlerMethod.exceptionalEventType;
        LazyHandling newLazyHandling = new LazyHandling(handlerClass, handlerMethod);

        //Handlings mapped on source code and actionMode LAZY.
        CopyOnWriteArrayList<LazyHandling> lazyHandlings = lazyHandlingsByExceptionalEventType.get(exceptionalEventType);
        if (lazyHandlings == null) {
            lazyHandlings = new CopyOnWriteArrayList<>();
            lazyHandlingsByExceptionalEventType.put(exceptionalEventType, lazyHandlings);
        } else {
            if (lazyHandlings.contains(newLazyHandling)) {
                throw new EventBusException("Handler " + handlerClass + " already registered as 'LazyHandling' to exceptional event "
                        + exceptionalEventType);
            }
        }

        int size = lazyHandlings.size();
        for (int i = 0; i <= size; i++) {
            if (i == size || handlerMethod.priority > lazyHandlings.get(i).handlerMethod.priority) {
                lazyHandlings.add(i, newLazyHandling);
                break;
            }
        }
    }

    /**
     * Registers a mapped handling, which consists of an association between a handler class and a handler method,
     * which has handler methods mapped, and one of these handler methods.
     *
     * Important: Must be called in synchronized block.
     *
     * @param handlerClass
     * @param handlerMethod
     */
    private void mapHandle(Class<?> handlerClass, HandlerMethod handlerMethod) {
        if(!ExceptionalActionMode.isTypeEnableFor(handlerClass, handlerMethod.actionMode)) {
            throw new EventBusException("Type " + handlerClass
                    + " is not an eligible type to use the actionMode "
                    + handlerMethod.actionMode + " in one of its handler methods.");
        }

        Class<?> exceptionalEventType = handlerMethod.exceptionalEventType;
        LazyHandling newMappedHandling = new LazyHandling(handlerClass, handlerMethod);

        //Handlings just mapped on source code.
        CopyOnWriteArrayList<LazyHandling> mappedHandlings = mappedHandlingsByExceptionalEventType.get(exceptionalEventType);
        if (mappedHandlings == null) {
            mappedHandlings = new CopyOnWriteArrayList<>();
            mappedHandlingsByExceptionalEventType.put(exceptionalEventType, mappedHandlings);
        } else {
            if (mappedHandlings.contains(newMappedHandling)) {
                throw new EventBusException("Handler " + handlerClass + " already registered as 'Mapped Handling' to exceptional event "
                        + exceptionalEventType);
            }
        }

        int size = mappedHandlings.size();
        for (int i = 0; i <= size; i++) {
            if (i == size || handlerMethod.priority > mappedHandlings.get(i).handlerMethod.priority) {
                mappedHandlings.add(i, newMappedHandling);
                break;
            }
        }

        if(handlerMethod.actionMode == ExceptionalActionMode.LAZY_HANDLE) {
            lazyHandle(handlerClass, handlerMethod);
        }
    }

    /**
     * Checks if there is a exceptional stick event to be throwed.
     *
     * @param newHandling
     * @param exceptionalStickyEvent
     */
    private void checkThrowsExceptionalStickyEventToHandling(Handling newHandling, Object exceptionalStickyEvent) {
        if (exceptionalStickyEvent != null) {
            // If the handler is trying to abort the exceptional event, it will fail (exceptional event is not tracked in throwing state)
            // --> Strange corner case, which we don't take care of here.
            throwsToHandling(newHandling, exceptionalStickyEvent, isMainThread());
        }
    }

    /**
     * Checks if the handler object is registered.
     *
     * @param handler
     * @return
     */
    public synchronized boolean isRegistered(Object handler) {
        return typesByHandler.containsKey(handler);
    }

    /**
     * Unregisters the given handler object from the exceptional event type.
     * Important: Only updates handlingsByExceptionalEventType, not typesByHandler! Caller must update typesByHandler.
     *
     * @param handler
     * @param exceptionalEventType
     */
    private void unhandleByExceptionalEventType(Object handler, Class<?> exceptionalEventType) {
        List<Handling> handlings = eagerHandlingsByExceptionalEventType.get(exceptionalEventType);
        if (handlings != null) {
            int size = handlings.size();
            for (int i = 0; i < size; i++) {
                Handling handling = handlings.get(i);
                if (handling.handler == handler) {
                    handling.active = false;
                    handlings.remove(i);
                    i--;
                    size--;
                }
            }
        }
    }

    /**
     * Unregisters the given handler from all exceptional event classes.
     *
     * @param handler
     */
    public synchronized void unregister(Object handler) {
        List<Class<?>> handledTypes = typesByHandler.get(handler);
        if (handledTypes != null) {
            for (Class<?> exceptionalEventType : handledTypes) {
                unhandleByExceptionalEventType(handler, exceptionalEventType);
            }
            typesByHandler.remove(handler);
        } else {
            getLogger().log(Level.WARNING, "Handler to unregister was not registered before: " + handler.getClass());
        }
    }

    /**
     * Posts the given exceptional event to the event bus.
     *
     * @param exceptionalEvent
     */
    public void throwException(Object exceptionalEvent) {
        synchronized (exceptionalEvent) {
            if(isLazyMechanismEnabled()) {
                //Register classes with methods mapped as subscribe or handle.
                try {
                    eventbus.registerMappedClasses();
                } catch (NoClassDefFoundError e) {
                    //At the moment, do nothing.
                }
            }

            //Put exceptional events in eager queue.
            ThrowingThreadState eagerThrowingState = currentEagerThrowingThreadState.get();
            putExceptionalEventInThrowingQueue(eagerThrowingState, exceptionalEvent);

            //Processes the thread that sends the messages that are in the eager queue.
            processThrowingThread(eagerThrowingState);

            if(isLazyMechanismEnabled() && querier.isExceptionalEventMappedForExceptionalActionMode(
                    exceptionalEvent, ExceptionalActionMode.LAZY_HANDLE)) {
                //Put exceptional events in lazy queue.
                ThrowingThreadState lazyThrowingState = currentLazyThrowingThreadState.get();
                putExceptionalEventInThrowingQueue(lazyThrowingState, exceptionalEvent);
            }
        }
    }

    public void putExceptionalEventInThrowingQueue(ThrowingThreadState throwingThreadState, Object exceptionalEvent) {
        if(throwingThreadState.isLazy) {
            HashMap<Class<?>, ArrayList<Object>> lazyHandlingExceptionalEventQueue = throwingThreadState.lazyExceptionalEventQueue;
            Set<Class<?>> handlerClasses = getLazyHandlingClassesForExceptionalEvent(exceptionalEvent);
            Iterator<Class<?>> it = handlerClasses.iterator();
            while(it.hasNext()) {
                Class<?> handlerClass = it.next();

                if(querier.isEagerHandlerClassForExceptionalEvent(handlerClass, exceptionalEvent)) {
                    it.remove();
                    continue;
                }

                if(lazyHandlingExceptionalEventQueue.containsKey(handlerClass)) {
                    ArrayList<Object> exceptionalEventList = lazyHandlingExceptionalEventQueue.get(handlerClass);
                    exceptionalEventList.add(exceptionalEvent);
                }
                else {
                    ArrayList<Object> exceptionalEventList = new ArrayList<Object>();
                    exceptionalEventList.add(exceptionalEvent);
                    lazyHandlingExceptionalEventQueue.put(handlerClass, exceptionalEventList);
                }
            }

            //Prepare to start the activities/services that will receive the exceptional events of the lazy queue.
            prepareLazyThrowingExceptionalEvent(handlerClasses);
        }
        else {
            List<Object> eagerExceptionalEventQueue = throwingThreadState.eagerExceptionalEventQueue;
            eagerExceptionalEventQueue.add(exceptionalEvent);
        }
    }

    /**
     * Analyzes and stores data from a specific class that have mapped methods to perform
     * the processing of common events or exceptional events.
     */
    public void registerMappedClass(Class<?> classToMap) {
        boolean hasHandlerMethods = handlerMethodFinder.hasHandlerMethods(classToMap);

        //Register classes that contains methods mapped with the @Handle annotation.
        if(hasHandlerMethods) {
            List<HandlerMethod> handlerMethods = handlerMethodFinder.findHandlerMethods(classToMap);
            synchronized (this) {
                for (HandlerMethod handlerMethod : handlerMethods) {
                    mapHandle(classToMap, handlerMethod);
                }
            }
        }
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
        ThrowingThreadState throwingState = currentEagerThrowingThreadState.get();
        if (!throwingState.isThrowing) {
            throw new EventBusException(
                    "This method may only be called from inside exceptional event handling methods on the throwing thread");
        } else if (exceptionalEvent == null) {
            throw new EventBusException("Exceptional event may not be null");
        } else if (throwingState.exceptionalEvent != exceptionalEvent) {
            throw new EventBusException("Only the currently handled exceptional event may be aborted");
        } else if (throwingState.handling.handlerMethod.threadMode != ExceptionalThreadMode.THROWING) {
            throw new EventBusException(" exceptional event handlers may only abort the incoming exceptional event");
        }

        throwingState.canceled = true;
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
        // Should be throwed after it is putted, in case the handler wants to remove eagerly
        throwException(exceptionalEvent);
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
     * Removes all exceptional sticky events.
     */
    public void removeAllStickyExceptionalEvents() {
        synchronized (stickyExceptionalEvents) {
            stickyExceptionalEvents.clear();
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
        List<Object> exceptionalEventQueue = throwingState.eagerExceptionalEventQueue;
        HashMap<Class<?>, ArrayList<Object>> lazyExceptionalEventQueue = throwingState.lazyExceptionalEventQueue;

        if (!throwingState.isThrowing) {
            throwingState.isMainThread = isMainThread();
            throwingState.isThrowing = true;
            if (throwingState.canceled) {
                throw new EventBusException("Internal error. Abort state was not reset");
            }
            try {
                if (handler != null && throwingState.isLazy) {
                    ArrayList<Object> exceptionalEventList = lazyExceptionalEventQueue.get(handler.getClass());
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
        boolean handlingFound = false;
        if (exceptionalEventInheritance) {
            List<Class<?>> exceptionalEventTypes = lookupAllExceptionalEventTypes(exceptionalEventClass);
            int countTypes = exceptionalEventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = exceptionalEventTypes.get(h);
                handlingFound |= throwsSingleExceptionalEventForExceptionalEventType(exceptionalEvent, handler, throwingState, clazz);
            }
        } else {
            handlingFound = throwsSingleExceptionalEventForExceptionalEventType(exceptionalEvent, handler, throwingState, exceptionalEventClass);
        }
        if (!handlingFound) {
            if (logNoHandlerMessages) {
                getLogger().log(Level.FINE, "No handlers registered for exceptional event " + exceptionalEventClass);
            }
            if (sendNoHandlerExceptionalEvent && exceptionalEventClass != NoHandlerExceptionalEvent.class &&
                    exceptionalEventClass != HandlerExceptionExceptionalEvent.class) {
                throwException(new NoHandlerExceptionalEvent(getEventBus(), exceptionalEvent));
            }
        }
    }

    /**
     *
     * @param exceptionalEvent
     * @param throwingState
     * @param exceptionalEventClass
     * @return
     */
    private boolean throwsSingleExceptionalEventForExceptionalEventType(Object exceptionalEvent, Object handler, ThrowingThreadState throwingState, Class<?> exceptionalEventClass) {

        CopyOnWriteArrayList<Handling> handlings = getHandlingsForThrowingExceptionalEvent(
                exceptionalEvent,exceptionalEventClass);

        if (handlings != null && !handlings.isEmpty()) {
            for (Handling handling : handlings) {
                if(throwingState.isLazy && handler != null && !handling.handler.equals(handler))
                    continue;

                throwingState.exceptionalEvent = exceptionalEvent;
                throwingState.handling = handling;
                boolean aborted;
                try {
                    throwsToHandling(handling, exceptionalEvent, throwingState.isMainThread);
                    aborted = throwingState.canceled;
                } finally {
                    throwingState.exceptionalEvent = null;
                    throwingState.handling = null;
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
     *
     * @param exceptionalEvent
     * @param exceptionalEventClass
     * @return
     */
    private CopyOnWriteArrayList<Handling> getHandlingsForThrowingExceptionalEvent(
            Object exceptionalEvent, Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<Handling> handlings;
        synchronized (this) {
            handlings = eagerHandlingsByExceptionalEventType.get(exceptionalEventClass);
            if(handlings == null)
                return null;
        }
        return handlings;
    }

    private Set<Class<?>> getLazyHandlingClassesForExceptionalEvent(Object exceptionalEvent) {
        Set<Class<?>> handlerClassesSet = new HashSet<Class<?>>();
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        if (exceptionalEventInheritance) {
            List<Class<?>> exceptionalEventTypes = lookupAllExceptionalEventTypes(exceptionalEventClass);
            int countTypes = exceptionalEventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = exceptionalEventTypes.get(h);
                handlerClassesSet.addAll(getLazyHandlingClassesForExceptionalEventType(clazz));
            }
        }
        else {
            handlerClassesSet.addAll(getLazyHandlingClassesForExceptionalEventType(exceptionalEventClass));
        }
        return handlerClassesSet;
    }

    private Set<Class<?>> getLazyHandlingClassesForExceptionalEventType(Class<?> exceptionalEventClass) {
        Set<Class<?>> handlerClassesSet = new HashSet<Class<?>>();
        CopyOnWriteArrayList<LazyHandling> lazyHandlings;
        synchronized (this) {
            lazyHandlings = lazyHandlingsByExceptionalEventType.get(exceptionalEventClass);
            if(lazyHandlings != null && !lazyHandlings.isEmpty()) {
                for(LazyHandling lazyHandling : lazyHandlings) {
                    handlerClassesSet.add(lazyHandling.handlerClass);
                }
            }
            return handlerClassesSet;
        }
    }

    /**
     *
     * @param handling
     * @param exceptionalEvent
     * @param isMainThread
     */
    private void throwsToHandling(Handling handling, Object exceptionalEvent, boolean isMainThread) {
        switch (handling.handlerMethod.threadMode) {
            case THROWING:
                invokeHandler(handling, exceptionalEvent);
                break;
            case MAIN:
                if (isMainThread) {
                    invokeHandler(handling, exceptionalEvent);
                } else {
                    mainThreadThrower.enqueue(handling, exceptionalEvent);
                }
                break;
            case MAIN_ORDERED:
                if (mainThreadThrower != null) {
                    mainThreadThrower.enqueue(handling, exceptionalEvent);
                } else {
                    // temporary: technically not correct as poster not decoupled from subscriber
                    invokeHandler(handling, exceptionalEvent);
                }
                break;
            case BACKGROUND:
                if (isMainThread) {
                    backgroundThrower.enqueue(handling, exceptionalEvent);
                } else {
                    invokeHandler(handling, exceptionalEvent);
                }
                break;
            case ASYNC:
                asyncThrower.enqueue(handling, exceptionalEvent);
                break;
            default:
                throw new IllegalStateException("Unknown thread mode: " + handling.handlerMethod.threadMode);
        }
    }

    /**
     * Prepares the exceptional event to be sent to the handlers who will receive it through the lazy send queue.
     *
     * @param handlerClasses
     * @throws Error
     */
    private void prepareLazyThrowingExceptionalEvent(Set<Class<?>> handlerClasses) throws Error {
        for (Class<?> handlerClass : handlerClasses) {
            if(ExceptionalActionMode.isTypeEnableFor(handlerClass, ExceptionalActionMode.LAZY_HANDLE)) {
                if(Activity.class.isAssignableFrom(handlerClass)) {
                    Intent intent = new Intent(getContext(), handlerClass);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(intent);
                }
                else if(Service.class.isAssignableFrom(handlerClass)) {
                    Intent intent = new Intent(getContext(), handlerClass);
                    getContext().startService(intent);
                }
            }
            else {
                throw new EventBusException("The type of this handler is not enabled for the 'start and handle' action mode.");
            }
        }
    }

    /**
     * Looks up all Class objects including super classes and interfaces. Should also work for interfaces.
     *
     * @param exceptionalEventClass
     * @return
     */
    static List<Class<?>> lookupAllExceptionalEventTypes(Class<?> exceptionalEventClass) {
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
     * Invokes the handler if the handlings is still active. Skipping handlings prevents race conditions
     * between {@link #unregister(Object)} and exceptional event delivery. Otherwise the exceptional event might be delivered after the
     * handler unregistered. This is particularly important for main thread delivery and registrations bound to the
     * live cycle of an Activity or Fragment.
     *
     * @param pendingThrow
     */
    void invokeHandler(PendingThrow pendingThrow) {
        Object exceptionalEvent = pendingThrow.exceptionalEvent;
        Handling handling = pendingThrow.handling;
        PendingThrow.releasePendingThrow(pendingThrow);
        if (handling.active) {
            invokeHandler(handling, exceptionalEvent);
        }
    }

    /**
     * Invokes the handler to perform some processing on the exceptional event.
     *
     * @param handling
     * @param exceptionalEvent
     */
    void invokeHandler(Handling handling, Object exceptionalEvent) {
        try {
            handling.handlerMethod.method.invoke(handling.handler, exceptionalEvent);
        } catch (InvocationTargetException e) {
            handleHandlerException(handling, exceptionalEvent, e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    /**
     * Process exception caught during invocation of the handler.
     *
     * @param handling
     * @param exceptionalEvent
     * @param cause
     */
    private void handleHandlerException(Handling handling, Object exceptionalEvent, Throwable cause) {
        if (exceptionalEvent instanceof HandlerExceptionExceptionalEvent) {
            if (logHandlerExceptions) {
                // Don't send another HandlerExceptionExceptionalEvent to avoid infinite exceptional event recursion, just log.
                getLogger().log(Level.SEVERE, "HandlerExceptionExceptionalEvent handler " + handling.handler.getClass()
                        + " threw an exception", cause);
                HandlerExceptionExceptionalEvent exExceptionalEvent = (HandlerExceptionExceptionalEvent) exceptionalEvent;
                getLogger().log(Level.SEVERE, "Initial exceptional event " + exExceptionalEvent.causingExceptionalEvent + " caused exception in "
                        + exExceptionalEvent.causingHandler, exExceptionalEvent.throwable);
            }
        } else {
            if (throwHandlerException) {
                throw new EventBusException("Invoking handler failed", cause);
            }
            if (logHandlerExceptions) {
                getLogger().log(Level.SEVERE, "Could not dispatch exceptional event: " + exceptionalEvent.getClass() + " to handling class "
                        + handling.handler.getClass(), cause);
            }
            if (sendHandlerExceptionExceptionalEvent) {
                HandlerExceptionExceptionalEvent exExceptionalEvent = new HandlerExceptionExceptionalEvent(getEventBus(), cause, exceptionalEvent,
                        handling.handler);
                throwException(exExceptionalEvent);
            }
        }
    }

    /**
     * For ThreadLocal, much faster to set (and get multiple values).
     */
    final static class ThrowingThreadState {
        final List<Object> eagerExceptionalEventQueue = new ArrayList<>();
        final HashMap<Class<?>, ArrayList<Object>> lazyExceptionalEventQueue = new HashMap<Class<?>, ArrayList<Object>>();
        boolean isThrowing;
        boolean isMainThread;
        boolean isLazy;
        Handling handling;
        Object exceptionalEvent;
        boolean canceled;

        public ThrowingThreadState() {
            super();
        }

        public ThrowingThreadState(boolean isLazy) {
            super();
            this.isLazy = isLazy;
        }
    }

    public HandlerMethodFinder getHandlerMethodFinder() {
        return handlerMethodFinder;
    }

    public boolean isExceptionalEventInheritance() {
        return exceptionalEventInheritance;
    }

    public Map<Class<?>, CopyOnWriteArrayList<Handling>> getEagerHandlingsByExceptionalEventType() {
        return eagerHandlingsByExceptionalEventType;
    }

    public Map<Class<?>, CopyOnWriteArrayList<LazyHandling>> getLazyHandlingsByExceptionalEventType() {
        return lazyHandlingsByExceptionalEventType;
    }

    public ExceptionalBusQuerier getQuerier() {
        return querier;
    }

    public int getIndexCount() {
        return indexCount;
    }

    /**
     * Just an idea: we could provide a callback to throws() to be notified, an alternative would be exceptional events, of course...
     */
    /* public */ interface ThrowsCallback {
        void onThrowsCompleted(List<HandlerExceptionExceptionalEvent> exceptionExceptionalEvents);
    }

    @Override
    public String toString() {
        return "ExceptionalBus[indexCountHandler=" + indexCount
                + ", exceptionalEventInheritance=" + exceptionalEventInheritance + "]";
    }
}
