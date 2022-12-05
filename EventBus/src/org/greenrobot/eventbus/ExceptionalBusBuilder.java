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

import org.greenrobot.eventbus.meta.HandlerInfoIndex;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates EventBus instances with custom parameters and also allows to install a custom default EventBus instance.
 * Create a new builder using {@link EventBus#builder()}.
 */
@SuppressWarnings("unused")
public class ExceptionalBusBuilder {
    EventBusBuilder eventBusBuilder;

    boolean logHandlerExceptions = true;
    boolean logNoHandlerMessages = true;
    boolean sendHandlerExceptionExceptionalEvent = true;
    boolean sendNoHandlerExceptionalEvent = true;
    boolean throwHandlerException;
    boolean exceptionalEventInheritance = true;

    List<HandlerInfoIndex> handlerInfoIndexes;

    ExceptionalBusBuilder(EventBusBuilder eventBusBuilder) {
        this.eventBusBuilder = eventBusBuilder;
    }

    /** Default: true */
    public ExceptionalBusBuilder logHandlerExceptions(boolean logHandlerExceptions) {
        this.logHandlerExceptions = logHandlerExceptions;
        return this;
    }

    /** Default: true */
    public ExceptionalBusBuilder logNoHandlerMessages(boolean logNoHandlerMessages) {
        this.logNoHandlerMessages = logNoHandlerMessages;
        return this;
    }

    /** Default: true */
    public ExceptionalBusBuilder sendHandlerExceptionExceptionalEvent(boolean sendHandlerExceptionExceptionalEvent) {
        this.sendHandlerExceptionExceptionalEvent = sendHandlerExceptionExceptionalEvent;
        return this;
    }

    /** Default: true */
    public ExceptionalBusBuilder sendNoHandlerExceptionalEvent(boolean sendNoHandlerExceptionalEvent) {
        this.sendNoHandlerExceptionalEvent = sendNoHandlerExceptionalEvent;
        return this;
    }

    /**
     * Fails if an handler throws an exception (default: false).
     * <p>
     * Tip: Use this with BuildConfig.DEBUG to let the app crash in DEBUG mode (only). This way, you won't miss
     * exceptions during development.
     */
    public ExceptionalBusBuilder throwHandlerException(boolean throwHandlerException) {
        this.throwHandlerException = throwHandlerException;
        return this;
    }

    /**
     * By default, EventBus considers the exceptional event class hierarchy (handlers to super classes will be notified).
     * Switching this feature off will improve throwing of exceptional events. For simple exceptional event classes extending Object directly,
     * we measured a speed up of 20% for exceptional event throwing. For more complex event hierarchies, the speed up should be
     * greater than 20%.
     * <p>
     * However, keep in mind that exceptional event throwing usually consumes just a small proportion of CPU time inside an app,
     * unless it is throwing at high rates, e.g. hundreds/thousands of exceptional events per second.
     */
    public ExceptionalBusBuilder exceptionalEventInheritance(boolean exceptionalEventInheritance) {
        this.exceptionalEventInheritance = exceptionalEventInheritance;
        return this;
    }

    /** Adds an index generated by EventBus' annotation preprocessor. */
    public ExceptionalBusBuilder addIndex(HandlerInfoIndex index) {
        if (handlerInfoIndexes == null) {
            handlerInfoIndexes = new ArrayList<>();
        }
        handlerInfoIndexes.add(index);
        return this;
    }
}