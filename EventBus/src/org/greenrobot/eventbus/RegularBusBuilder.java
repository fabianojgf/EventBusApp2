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

import org.greenrobot.eventbus.meta.SubscriberInfoIndex;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates RegularBus instances with custom parameters and also allows to install a custom default RegularBus instance.
 */
@SuppressWarnings("unused")
public class RegularBusBuilder {
    EventBusBuilder eventBusBuilder;

    boolean logSubscriberExceptions = true;
    boolean logNoSubscriberMessages = true;
    boolean sendSubscriberExceptionEvent = true;
    boolean sendNoSubscriberEvent = true;
    boolean throwSubscriberException;
    boolean eventInheritance = true;

    List<SubscriberInfoIndex> subscriberInfoIndexes;

    RegularBusBuilder(EventBusBuilder eventBusBuilder) {
        this.eventBusBuilder = eventBusBuilder;
    }

    /** Default: true */
    public RegularBusBuilder logSubscriberExceptions(boolean logSubscriberExceptions) {
        this.logSubscriberExceptions = logSubscriberExceptions;
        return this;
    }

    /** Default: true */
    public RegularBusBuilder logNoSubscriberMessages(boolean logNoSubscriberMessages) {
        this.logNoSubscriberMessages = logNoSubscriberMessages;
        return this;
    }

    /** Default: true */
    public RegularBusBuilder sendSubscriberExceptionEvent(boolean sendSubscriberExceptionEvent) {
        this.sendSubscriberExceptionEvent = sendSubscriberExceptionEvent;
        return this;
    }

    /** Default: true */
    public RegularBusBuilder sendNoSubscriberEvent(boolean sendNoSubscriberEvent) {
        this.sendNoSubscriberEvent = sendNoSubscriberEvent;
        return this;
    }

    /**
     * Fails if an subscriber throws an exception (default: false).
     * <p>
     * Tip: Use this with BuildConfig.DEBUG to let the app crash in DEBUG mode (only). This way, you won't miss
     * exceptions during development.
     */
    public RegularBusBuilder throwSubscriberException(boolean throwSubscriberException) {
        this.throwSubscriberException = throwSubscriberException;
        return this;
    }

    /**
     * By default, EventBus considers the event class hierarchy (subscribers to super classes will be notified).
     * Switching this feature off will improve posting of events. For simple event classes extending Object directly,
     * we measured a speed up of 20% for event posting. For more complex event hierarchies, the speed up should be
     * greater than 20%.
     * <p>
     * However, keep in mind that event posting usually consumes just a small proportion of CPU time inside an app,
     * unless it is posting at high rates, e.g. hundreds/thousands of events per second.
     */
    public RegularBusBuilder eventInheritance(boolean eventInheritance) {
        this.eventInheritance = eventInheritance;
        return this;
    }

    /** Adds an index generated by RegularBus' annotation preprocessor. */
    public RegularBusBuilder addIndex(SubscriberInfoIndex index) {
        if (subscriberInfoIndexes == null) {
            subscriberInfoIndexes = new ArrayList<>();
        }
        subscriberInfoIndexes.add(index);
        return this;
    }
}