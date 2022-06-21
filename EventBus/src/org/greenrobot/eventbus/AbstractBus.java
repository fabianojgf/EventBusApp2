package org.greenrobot.eventbus;

import android.content.Context;

import java.util.List;
import java.util.concurrent.ExecutorService;

public abstract class AbstractBus {
    protected EventBus eventbus;

    public AbstractBus() {
    }

    public AbstractBus(EventBus eventbus) {
        this.eventbus = eventbus;
    }

    public EventBus getEventBus() {
        return eventbus;
    }

    public ExecutorService getExecutorService() {
        return eventbus.getExecutorService();
    }

    public Logger getLogger() {
        return eventbus.getLogger();
    }

    public Context getContext() {
        return eventbus.getContext();
    }

    public boolean isMainThread() {
        return eventbus.isMainThread();
    }

    public boolean isStartMechanismEnabled() {
        return eventbus.isStartMechanismEnabled();
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
}
