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

import android.os.Looper;

import org.greenrobot.eventbus.android.AndroidLogger;
import org.greenrobot.eventbus.meta.HandlerInfoIndex;
import org.greenrobot.eventbus.meta.SubscriberInfoIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Creates EventBus instances with custom parameters and also allows to install a custom default EventBus instance.
 * Create a new builder using {@link EventBus#builder()}.
 */
@SuppressWarnings("unused")
public class EventBusBuilder {
    private final static ExecutorService DEFAULT_EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    RegularBusBuilder regularBusBuilder = new RegularBusBuilder(this);
    ExceptionalBusBuilder exceptionalBusBuilder = new ExceptionalBusBuilder(this);

    boolean mappedClassesRegistrationPerformed = false;

    boolean startMechanismEnabled = true;
    boolean ignoreGeneratedIndex;
    boolean strictMethodVerification;
    ExecutorService executorService = DEFAULT_EXECUTOR_SERVICE;
    List<Class<?>> skipMethodVerificationForClasses;
    Logger logger;
    MainThreadSupport mainThreadSupport;

    EventBusBuilder() {
    }

    /**
     * By default, EventBus considers that the registration of classes with methods for subscribe  or handle was not carried out.
     * <p>
     * Registration will be performed during the first execution of {@link EventBus#post(Object)} or {@link EventBus#throwException(Object)} methods.
     */
    public EventBusBuilder mappedClassesRegistrationPerformed(boolean mappedClassesRegistrationPerformed) {
        this.mappedClassesRegistrationPerformed = mappedClassesRegistrationPerformed;
        return this;
    }

    /**
     * Provide a custom thread pool to EventBus used for async and background event delivery. This is an advanced
     * setting to that can break things: ensure the given ExecutorService won't get stuck to avoid undefined behavior.
     */
    public EventBusBuilder executorService(ExecutorService executorService) {
        this.executorService = executorService;
        return this;
    }

    /**
     * Method name verification is done for methods starting with onEvent to avoid typos; using this method you can
     * exclude subscriber classes from this check. Also disables checks for method modifiers (public, not static nor
     * abstract).
     */
    public EventBusBuilder skipMethodVerificationFor(Class<?> clazz) {
        if (skipMethodVerificationForClasses == null) {
            skipMethodVerificationForClasses = new ArrayList<>();
        }
        skipMethodVerificationForClasses.add(clazz);
        return this;
    }

    /** the start mechanism for services and activities (default: true). */
    public EventBusBuilder startMechanismEnabled(boolean startMechanismEnabled) {
        this.startMechanismEnabled = startMechanismEnabled;
        return this;
    }

    /** Forces the use of reflection even if there's a generated index (default: false). */
    public EventBusBuilder ignoreGeneratedIndex(boolean ignoreGeneratedIndex) {
        this.ignoreGeneratedIndex = ignoreGeneratedIndex;
        return this;
    }

    /** Enables strict method verification (default: false). */
    public EventBusBuilder strictMethodVerification(boolean strictMethodVerification) {
        this.strictMethodVerification = strictMethodVerification;
        return this;
    }

    /** RegularBusBuilder */

    public EventBusBuilder logSubscriberExceptions(boolean logSubscriberExceptions) {
        regularBusBuilder.logSubscriberExceptions(logSubscriberExceptions);
        return this;
    }

    public EventBusBuilder logNoSubscriberMessages(boolean logNoSubscriberMessages) {
        regularBusBuilder.logNoSubscriberMessages(logNoSubscriberMessages);
        return this;
    }

    public EventBusBuilder sendSubscriberExceptionEvent(boolean sendSubscriberExceptionEvent) {
        regularBusBuilder.sendSubscriberExceptionEvent(sendSubscriberExceptionEvent);
        return this;
    }

    public EventBusBuilder sendNoSubscriberEvent(boolean sendNoSubscriberEvent) {
        regularBusBuilder.sendNoSubscriberEvent(sendNoSubscriberEvent);
        return this;
    }

    public EventBusBuilder throwSubscriberException(boolean throwSubscriberException) {
        regularBusBuilder.throwSubscriberException(throwSubscriberException);
        return this;
    }

    public EventBusBuilder eventInheritance(boolean eventInheritance) {
        regularBusBuilder.eventInheritance(eventInheritance);
        return this;
    }

    public EventBusBuilder addIndex(SubscriberInfoIndex index) {
        regularBusBuilder.addIndex(index);
        return this;
    }

    /** ExceptionalBusBuilder */

    public EventBusBuilder logHandlerExceptions(boolean logHandlerExceptions) {
        exceptionalBusBuilder.logHandlerExceptions(logHandlerExceptions);
        return this;
    }

    public EventBusBuilder logNoHandlerMessages(boolean logNoHandlerMessages) {
        exceptionalBusBuilder.logNoHandlerMessages(logNoHandlerMessages);
        return this;
    }

    public EventBusBuilder sendHandlerExceptionExceptionalEvent(boolean sendHandlerExceptionExceptionalEvent) {
        exceptionalBusBuilder.sendHandlerExceptionExceptionalEvent(sendHandlerExceptionExceptionalEvent);
        return this;
    }

    public EventBusBuilder sendNoHandlerExceptionalEvent(boolean sendNoHandlerExceptionalEvent) {
        exceptionalBusBuilder.sendNoHandlerExceptionalEvent(sendNoHandlerExceptionalEvent);
        return this;
    }

    public EventBusBuilder throwHandlerException(boolean throwHandlerException) {
        exceptionalBusBuilder.throwHandlerException(throwHandlerException);
        return this;
    }

    public EventBusBuilder exceptionalEventInheritance(boolean exceptionalEventInheritance) {
        exceptionalBusBuilder.exceptionalEventInheritance(exceptionalEventInheritance);
        return this;
    }

    public EventBusBuilder addIndex(HandlerInfoIndex index) {
        exceptionalBusBuilder.addIndex(index);
        return this;
    }

    /**
     * Set a specific log handler for all EventBus logging.
     * <p>
     * By default all logging is via {@link android.util.Log} but if you want to use EventBus
     * outside the Android environment then you will need to provide another log target.
     */
    public EventBusBuilder logger(Logger logger) {
        this.logger = logger;
        return this;
    }

    Logger getLogger() {
        if (logger != null) {
            return logger;
        } else {
            return Logger.Default.get();
        }
    }

    MainThreadSupport getMainThreadSupport() {
        if (mainThreadSupport != null) {
            return mainThreadSupport;
        } else if (AndroidLogger.isAndroidLogAvailable()) {
            Object looperOrNull = getAndroidMainLooperOrNull();
            return looperOrNull == null ? null :
                    new MainThreadSupport.AndroidHandlerMainThreadSupport((Looper) looperOrNull);
        } else {
            return null;
        }
    }

    static Object getAndroidMainLooperOrNull() {
        try {
            return Looper.getMainLooper();
        } catch (RuntimeException e) {
            // Not really a functional Android (e.g. "Stub!" maven dependencies)
            return null;
        }
    }

    /**
     * Installs the default EventBus returned by {@link EventBus#getDefault()} using this builders' values. Must be
     * done only once before the first usage of the default EventBus.
     *
     * @throws EventBusException if there's already a default EventBus instance in place
     */
    public EventBus installDefaultEventBus() {
        synchronized (EventBus.class) {
            if (EventBus.defaultInstance != null) {
                throw new EventBusException("Default instance already exists." +
                        " It may be only set once before it's used the first time to ensure consistent behavior.");
            }
            EventBus.defaultInstance = build();
            return EventBus.defaultInstance;
        }
    }

    public RegularBusBuilder getRegularBusBuilder() {
        return regularBusBuilder;
    }

    public ExceptionalBusBuilder getExceptionalBusBuilder() {
        return exceptionalBusBuilder;
    }

    /** Builds an EventBus based on the current configuration. */
    public EventBus build() {
        return new EventBus(this);
    }
}