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

    static final EventBusBuilder DEFAULT_BUILDER = new EventBusBuilder();

    private final MainThreadSupport mainThreadSupport;
    private final ExecutorService executorService;
    private final Logger logger;
    private final int indexCount;
    private final RegularBus regularBus;
    private final ExceptionalBus exceptionalBus;

    private boolean mappedClassesRegistrationPerformed;
    private boolean startMechanismEnabled;
    private Context context;

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
        RegularBus.clearCaches();
        /** Handlers */
        ExceptionalBus.clearCaches();
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
        mainThreadSupport = builder.getMainThreadSupport();
        executorService = builder.executorService;
        logger = builder.getLogger();

        regularBus = new RegularBus(this, builder.regularBusBuilder);
        exceptionalBus = new ExceptionalBus(this, builder.exceptionalBusBuilder);
        indexCount = 0;

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
        regularBus.register(subscriber);
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
        exceptionalBus.register(handler);
    }

    /**
     * Checks if the current thread is running in the main thread.
     * If there is no main thread support (e.g. non-Android), "true" is always returned.
     * In that case MAIN thread subscribers are always called in posting thread,
     * and BACKGROUND subscribers are always called from a background poster.
     *
     * @return
     */
    public boolean isMainThread() {
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
        return regularBus.isRegistered(subscriber);
    }

    /**
     * Checks if the handler object is registered.
     *
     * @param handler
     * @return
     */
    public synchronized boolean isRegisteredHandler(Object handler) {
        return exceptionalBus.isRegistered(handler);
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
        regularBus.unregisterSubscriber(subscriber);
    }

    /**
     * Unregisters the given handler from all exceptional event classes.
     *
     * @param handler
     */
    public synchronized void unregisterHandler(Object handler) {
        exceptionalBus.unregisterHandler(handler);
    }

    /**
     * Posts the given event to the event bus.
     *
     * @param event
     */
    public void post(Object event) {
        regularBus.post(event);
    }

    /**
     * Posts the given exceptional event to the event bus.
     *
     * @param exceptionalEvent
     */
    public void throwException(Object exceptionalEvent) {
        exceptionalBus.throwException(exceptionalEvent);
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
        regularBus.registerMappedClass(classToMap);
        exceptionalBus.registerMappedClass(classToMap);
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
        regularBus.cancelEventDelivery(event);
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
        exceptionalBus.cancelExceptionalEventDelivery(exceptionalEvent);
    }

    /**
     * Posts the given event to the event bus and holds on to the event (because it is sticky). The most recent sticky
     * event of an event's type is kept in memory for future access by subscribers using {@link Subscribe#sticky()}.
     *
     * @param event
     */
    public void postSticky(Object event) {
        regularBus.postSticky(event);
    }

    /**
     * Posts the given exceptional event to the event bus and holds on to the exceptional event (because it is sticky). The most recent sticky
     * exceptional event of an exceptional event's type is kept in memory for future access by handlers using {@link Handle#sticky()}.
     *
     * @param exceptionalEvent
     */
    public void throwSticky(Object exceptionalEvent) {
        exceptionalBus.throwSticky(exceptionalEvent);
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
        return regularBus.getStickyEvent(eventType);
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
        return exceptionalBus.getStickyExceptionalEvent(exceptionalEventType);
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
        return regularBus.removeStickyEvent(eventType);
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
        return exceptionalBus.removeStickyExceptionalEvent(exceptionalEventType);
    }

    /**
     * Removes the sticky event if it equals to the given event.
     * Returns true if the events matched and the sticky event was removed.
     *
     * @param event
     * @return
     */
    public boolean removeStickyEvent(Object event) {
        return regularBus.removeStickyEvent(event);
    }

    /**
     * Removes the sticky exceptional event if it equals to the given exceptional event.
     * Returns true if the events matched and the sticky exceptional event was removed.
     *
     * @param exceptionalEvent
     * @return
     */
    public boolean removeStickyExceptionalEvent(Object exceptionalEvent) {
        return exceptionalBus.removeStickyExceptionalEvent(exceptionalEvent);
    }

    /**
     * Removes all sticky events.
     */
    public void removeAllStickyEvents() {
        regularBus.removeAllStickyEvents();
    }

    /**
     * Removes all exceptional sticky events.
     */
    public void removeAllStickyExceptionalEvents() {
        exceptionalBus.removeAllStickyExceptionalEvents();
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

    public Context getContext() {
        return context;
    }

    public RegularBus getRegularBus() {
        return regularBus;
    }

    public ExceptionalBus getExceptionalBus() {
        return exceptionalBus;
    }

    public boolean isStartMechanismEnabled() {
        return startMechanismEnabled;
    }

    @Override
    public String toString() {
        return "EventBus[indexCount=" + indexCount
                + ", regularBus=" + regularBus
                + ", exceptionalBus=" + exceptionalBus + "]";
    }
}