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
package org.greenrobot.eventbus;

/**
 * @author ---
 */
final class Handling {
    final Object handler;
    final HandlerMethod handlerMethod;
    /**
     * Becomes false as soon as {@link EventBus#unregisterHandler(Object)} is called, which is checked by queued exceptional event delivery
     * {@link ExceptionalBus#invokeHandler(PendingThrow)} to prevent race conditions.
     */
    volatile boolean active;

    Handling(Object handler, HandlerMethod handlerMethod) {
        this.handler = handler;
        this.handlerMethod = handlerMethod;
        active = true;
    }

    public Object getHandler() {
        return handler;
    }

    public HandlerMethod getHandlerMethod() {
        return handlerMethod;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Handling) {
            Handling otherHandling = (Handling) other;
            return handler == otherHandling.handler
                    && handlerMethod.equals(otherHandling.handlerMethod);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return handler.hashCode() + handlerMethod.methodString.hashCode();
    }
}