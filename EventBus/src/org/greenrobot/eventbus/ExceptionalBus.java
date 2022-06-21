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
    private static final Map<Class<?>, List<Class<?>>> exceptionalEventTypesCache = new HashMap<>();

    private Map<Class<?>, CopyOnWriteArrayList<HandlerClass>> mappedHandlerClassesByExceptionalEventType;
    private Map<Class<?>, CopyOnWriteArrayList<Handlement>> handlementsByExceptionalEventType;
    private Map<Object, List<Class<?>>> typesByHandler;
    private Map<Class<?>, Object> stickyExceptionalEvents;

    private final ExceptionalBusQuerier querier = new ExceptionalBusQuerier(this);
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
     * For exceptional events sent late for objects that are yet to be instantiated.
     */
    private final ThreadLocal<ThrowingThreadState> currentLateThrowingThreadState = new ThreadLocal<ThrowingThreadState>() {
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

    private int indexCountHandler;

    /**
     * Creates a new EventBus instance; each instance is a separate scope in which events are delivered.
     *
     * @param eventbus
     */
    public ExceptionalBus(EventBus eventbus, ExceptionalBusBuilder builder) {
        super(eventbus);

        mappedHandlerClassesByExceptionalEventType = new HashMap<>();
        handlementsByExceptionalEventType = new HashMap<>();
        typesByHandler = new HashMap<>();
        stickyExceptionalEvents = new ConcurrentHashMap<>();

        MainThreadSupport mainThreadSupport = builder.eventBusBuilder.getMainThreadSupport();

        mainThreadThrower = mainThreadSupport != null
                ? mainThreadSupport.createThrower(getEventBus()) : null;
        backgroundThrower = new BackgroundThrower(getEventBus());
        asyncThrower = new AsyncThrower(getEventBus());

        indexCountHandler = builder.handlerInfoIndexes != null
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
     * Registers the given handler to receive exceptional events. Handlers must call {@link #unregisterHandler(Object)} once they
     * are no longer interested in receiving exceptional events.
     * <p/>
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

        if(isStartMechanismEnabled() && querier.isHandlerMappedForExceptionalActionMode(
                handlerClass, ExceptionalActionMode.LAZY_HANDLE)) {
            //Processes the thread that sends the messages that are in the late queue.
            ThrowingThreadState lateThrowingState = currentLateThrowingThreadState.get();
            processThrowingThread(handler, lateThrowingState);
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
            if(isStartMechanismEnabled()) {
                //Register classes with methods mapped as subscribe or handle.
                try {
                    getEventBus().registerMappedClasses();
                } catch (NoClassDefFoundError e) {
                    //At the moment, do nothing.
                }
            }

            //Put exceptional events in immediate queue.
            ThrowingThreadState immediateThrowingState = currentImmediateThrowingThreadState.get();
            putExceptionalEventInThrowingQueue(immediateThrowingState, exceptionalEvent);

            //Processes the thread that sends the messages that are in the immediate queue.
            processThrowingThread(immediateThrowingState);

            if(isStartMechanismEnabled() && querier.isExceptionalEventMappedForExceptionalActionMode(
                    exceptionalEvent, ExceptionalActionMode.LAZY_HANDLE)) {
                //Put exceptional events in late queue.
                ThrowingThreadState lateThrowingState = currentLateThrowingThreadState.get();
                putExceptionalEventInThrowingQueue(lateThrowingState, exceptionalEvent);

                //Prepare to start the activities that will receive the exceptional events of the late queue.
                prepareLateThrowingExceptionalEvent(exceptionalEvent);
            }
        }
    }

    public void putExceptionalEventInThrowingQueue(ThrowingThreadState throwingThreadState, Object exceptionalEvent) {
        if(throwingThreadState.isLate) {
            HashMap<Class<?>, ArrayList<Object>> lateExceptionalEventHandlerQueue = throwingThreadState.exceptionalEventHandlerQueue;
            Set<Class<?>> handlerClasses = getMappedHandlerClassForExceptionalEvent(exceptionalEvent);
            Iterator<Class<?>> it = handlerClasses.iterator();
            while(it.hasNext()) {
                Class<?> handlerClass = it.next();

                if(querier.isRegisteredHandlerClassForExceptionalEvent(handlerClass, exceptionalEvent))
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
                    handleClass(classToMap, handlerMethod);
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
                            Intent intent = new Intent(getContext(), handlerClassType);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            getContext().startActivity(intent);
                        }
                        else if(Service.class.isAssignableFrom(handlerClassType)) {
                            Intent intent = new Intent(getContext(), handlerClassType);
                            getContext().startService(intent);
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
                getLogger().log(Level.SEVERE, "HandlerExceptionExceptionalEvent handler " + handlement.handler.getClass()
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
                        + handlement.handler.getClass(), cause);
            }
            if (sendHandlerExceptionExceptionalEvent) {
                HandlerExceptionExceptionalEvent exExceptionalEvent = new HandlerExceptionExceptionalEvent(getEventBus(), cause, exceptionalEvent,
                        handlement.handler);
                throwException(exExceptionalEvent);
            }
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

    public HandlerMethodFinder getHandlerMethodFinder() {
        return handlerMethodFinder;
    }

    public boolean isExceptionalEventInheritance() {
        return exceptionalEventInheritance;
    }

    public Map<Class<?>, CopyOnWriteArrayList<Handlement>> getHandlementsByExceptionalEventType() {
        return handlementsByExceptionalEventType;
    }

    public Map<Class<?>, CopyOnWriteArrayList<HandlerClass>> getMappedHandlerClassesByExceptionalEventType() {
        return mappedHandlerClassesByExceptionalEventType;
    }

    /**
     * Just an idea: we could provide a callback to throws() to be notified, an alternative would be exceptional events, of course...
     */
    /* public */ interface ThrowsCallback {
        void onThrowsCompleted(List<HandlerExceptionExceptionalEvent> exceptionExceptionalEvents);
    }

    @Override
    public String toString() {
        return "ExceptionalBus[indexCountHandler=" + indexCountHandler
                + ", exceptionalEventInheritance=" + exceptionalEventInheritance + "]";
    }
}
