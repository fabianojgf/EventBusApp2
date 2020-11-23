/*
 * Copyright (C) 2012-2016 Markus Junginger, greenrobot (http://greenrobot.org)
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
package org.greenrobot.eventbus.util;

import org.greenrobot.eventbus.EventBus;

import java.lang.reflect.Constructor;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Executes an {@link RunnableEx} using a thread pool. Thrown exceptions are propagated by posting failure events of any
 * given type (default is {@link ThrowableFailureEvent}).
 * 
 * @author Markus
 */
public class ExceptionalEventAsyncExecutor {

    public static class Builder {
        private Executor threadPool;
        private Class<?> failureExceptionalEventType;
        private EventBus eventBus;

        private Builder() {
        }

        public Builder threadPool(Executor threadPool) {
            this.threadPool = threadPool;
            return this;
        }

        public Builder failureEventType(Class<?> failureExceptionalEventType) {
            this.failureExceptionalEventType = failureExceptionalEventType;
            return this;
        }

        public Builder eventBus(EventBus eventBus) {
            this.eventBus = eventBus;
            return this;
        }

        public ExceptionalEventAsyncExecutor build() {
            return buildForScope(null);
        }

        public ExceptionalEventAsyncExecutor buildForScope(Object executionContext) {
            if (eventBus == null) {
                eventBus = EventBus.getDefault();
            }
            if (threadPool == null) {
                threadPool = Executors.newCachedThreadPool();
            }
            if (failureExceptionalEventType == null) {
                failureExceptionalEventType = ThrowableFailureEvent.class;
            }
            return new ExceptionalEventAsyncExecutor(threadPool, eventBus, failureExceptionalEventType, executionContext);
        }
    }

    /** Like {@link Runnable}, but the run method may throw an exception. */
    public interface RunnableEx {
        void run() throws Exception;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ExceptionalEventAsyncExecutor create() {
        return new Builder().build();
    }

    private final Executor threadPool;
    private final Constructor<?> failureExceptionalEventConstructor;
    private final EventBus eventBus;
    private final Object scope;

    private ExceptionalEventAsyncExecutor(Executor threadPool, EventBus eventBus, Class<?> failureExceptionalEventType, Object scope) {
        this.threadPool = threadPool;
        this.eventBus = eventBus;
        this.scope = scope;
        try {
            failureExceptionalEventConstructor = failureExceptionalEventType.getConstructor(Throwable.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(
                    "Failure exceptional event class must have a constructor with one parameter of type Throwable", e);
        }
    }

    /** Posts an failure event if the given {@link RunnableEx} throws an Exception. */
    public void execute(final RunnableEx runnable) {
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } catch (Exception e) {
                    Object exceptionalEvent;
                    try {
                        exceptionalEvent = failureExceptionalEventConstructor.newInstance(e);
                    } catch (Exception e1) {
                        eventBus.getLogger().log(Level.SEVERE, "Original exception:", e);
                        throw new RuntimeException("Could not create failure exceptional event", e1);
                    }
                    if (exceptionalEvent instanceof HasExecutionScope) {
                        ((HasExecutionScope) exceptionalEvent).setExecutionScope(scope);
                    }
                    eventBus.throwException(exceptionalEvent);
                }
            }
        });
    }

}
