package org.greenrobot.eventbus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ExceptionalBusQuerier {
    private ExceptionalBus exceptionalBus;

    public ExceptionalBusQuerier(ExceptionalBus exceptionalBus) {
        this.exceptionalBus = exceptionalBus;
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
     * Checks if there is any registered handler object to be invoked to process this type of exceptional event.
     *
     * @param exceptionalEventClass
     * @return
     */
    public boolean hasHandlerForExceptionalEventType(Class<?> exceptionalEventClass) {
        if (exceptionalBus.isExceptionalEventInheritance()) {
            List<Class<?>> exceptionalEventTypes = ExceptionalBus.lookupAllExceptionalEventTypes(exceptionalEventClass);
            if (exceptionalEventTypes != null) {
                int countTypes = exceptionalEventTypes.size();
                for (int h = 0; h < countTypes; h++) {
                    Class<?> clazz = exceptionalEventTypes.get(h);
                    if (hasHandlingForExceptionalEventType(clazz)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return hasHandlingForExceptionalEventType(exceptionalEventClass);
    }

    /**
     * Checks if there is any handling associated with this type of exceptional event.
     *
     * @param exceptionalEventClass
     * @return
     */
    public boolean hasHandlingForExceptionalEventType(Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<Handling> handlings = null;
        synchronized (this) {
            handlings = exceptionalBus.getEagerHandlingsByExceptionalEventType().get(exceptionalEventClass);
            return handlings != null && !handlings.isEmpty();
        }
    }

    /**
     * Checks if there is any class, which does not necessarily have any instance registered as a handler,
     * that has a mapped method to be invoked to process the exceptional event.
     *
     * @param exceptionalEvent
     * @return
     */
    public boolean hasLazyHandlingForExceptionalEvent(Object exceptionalEvent) {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        if (exceptionalBus.isExceptionalEventInheritance()) {
            List<Class<?>> exceptionalEventTypes = ExceptionalBus.lookupAllExceptionalEventTypes(exceptionalEventClass);
            if (exceptionalEventTypes != null) {
                int countTypes = exceptionalEventTypes.size();
                for (int h = 0; h < countTypes; h++) {
                    Class<?> clazz = exceptionalEventTypes.get(h);
                    if (hasLazyHandlingForExceptionalEventType(clazz)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return hasLazyHandlingForExceptionalEventType(exceptionalEventClass);
    }

    /**
     * Checks if there is any handling associated with this type of exceptional event.
     *
     * @param exceptionalEventClass
     * @return
     */
    public boolean hasLazyHandlingForExceptionalEventType(Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<LazyHandling> mappedLazyHandlings;
        synchronized (this) {
            mappedLazyHandlings = exceptionalBus.getLazyHandlingsByExceptionalEventType().get(exceptionalEventClass);
            return mappedLazyHandlings != null && !mappedLazyHandlings.isEmpty();
        }
    }

    /**
     * Checks if the handler object is registered for the exceptional event.
     *
     * @param handler
     * @param exceptionalEvent
     * @return
     */
    public boolean isEagerHandlerForExceptionalEvent(Object handler, Object exceptionalEvent) {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        Class<?> handlerClass = handler.getClass();
        if (exceptionalBus.isExceptionalEventInheritance()) {
            List<Class<?>> exceptionalEventTypes = ExceptionalBus.lookupAllExceptionalEventTypes(exceptionalEventClass);
            int countTypes = exceptionalEventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = exceptionalEventTypes.get(h);
                if(isEagerHandlerForExceptionalEventType(handler, clazz))
                    return true;
            }
            return false;
        }
        return isEagerHandlerForExceptionalEventType(handler, exceptionalEventClass);
    }

    /**
     * Checks if the handler object is registered for this type of exceptional event.
     *
     * @param handler
     * @param exceptionalEventClass
     * @return
     */
    public boolean isEagerHandlerForExceptionalEventType(Object handler, Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<Handling> handlings;
        synchronized (this) {
            handlings = exceptionalBus.getEagerHandlingsByExceptionalEventType().get(exceptionalEventClass);
            if(handlings != null && !handlings.isEmpty()) {
                for(Handling handling : handlings) {
                    if(handling.handler.equals(handler))
                        return true;
                }
            }
            return false;
        }
    }

    public boolean isEagerHandlerClassForExceptionalEvent(Class<?> handlerClass, Object exceptionalEvent) {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        if (exceptionalBus.isExceptionalEventInheritance()) {
            List<Class<?>> exceptionalEventTypes = ExceptionalBus.lookupAllExceptionalEventTypes(exceptionalEventClass);
            int countTypes = exceptionalEventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = exceptionalEventTypes.get(h);
                if(isEagerHandlerClassForExceptionalEventType(handlerClass, clazz))
                    return true;
            }
            return false;
        }
        return isEagerHandlerClassForExceptionalEventType(handlerClass, exceptionalEventClass);
    }

    public boolean isEagerHandlerClassForExceptionalEventType(Class<?> handlerClassType, Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<Handling> handlings;
        synchronized (this) {
            handlings = exceptionalBus.getEagerHandlingsByExceptionalEventType().get(exceptionalEventClass);
            if(handlings != null && !handlings.isEmpty()) {
                for(Handling handling : handlings) {
                    if(handling.handler.getClass().equals(handlerClassType))
                        return true;
                }
            }
            return false;
        }
    }

    /**
     * Checks if the class is registered as 'handler class' for the exceptional event.
     * Handler class is a class which has methods mapped as handler for the exceptional event.
     *
     * @param handlerClass
     * @param exceptionalEvent
     * @return
     */
    public boolean isMappedHandlerClassForExceptionalEvent(Class<?> handlerClass, Object exceptionalEvent) {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        if (exceptionalBus.isExceptionalEventInheritance()) {
            List<Class<?>> exceptionalEventTypes = ExceptionalBus.lookupAllExceptionalEventTypes(exceptionalEventClass);
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
     * Checks if the class is registered as 'handler class' for this type of exceptional event.
     *
     * @param handlerClassType
     * @param exceptionalEventClass
     * @return
     */
    public boolean isMappedHandlerClassForExceptionalEventType(Class<?> handlerClassType, Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<LazyHandling> lazyHandlings;
        synchronized (this) {
            lazyHandlings = exceptionalBus.getLazyHandlingsByExceptionalEventType().get(exceptionalEventClass);
            if(lazyHandlings != null && !lazyHandlings.isEmpty()) {
                for(LazyHandling lazyHandling : lazyHandlings) {
                    if(exceptionalBus.isExceptionalEventInheritance()) {
                        if(lazyHandling.handlerClass.isAssignableFrom(handlerClassType))
                            return true;
                    }
                    else {
                        if(lazyHandling.handlerClass.equals(handlerClassType))
                            return true;
                    }
                }
            }
            return false;
        }
    }

    public boolean isHandlerMappedForExceptionalActionMode(Class<?> handlerClass, ExceptionalActionMode exceptionalActionMode) {
        if(!ExceptionalActionMode.isTypeEnableFor(handlerClass, exceptionalActionMode))
            return false;

        List<HandlerMethod> handlerMethods = exceptionalBus.getHandlerMethodFinder().findHandlerMethods(handlerClass);
        for(HandlerMethod h : handlerMethods) {
            if(h.actionMode.equals(exceptionalActionMode))
                return true;
        }
        return false;
    }

    public boolean isExceptionalEventMappedForExceptionalActionMode(Object exceptionalEvent, ExceptionalActionMode exceptionalActionMode) {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        boolean exceptionalEventTypeMapped = false;
        if (exceptionalBus.isExceptionalEventInheritance()) {
            List<Class<?>> exceptionalEventTypes = ExceptionalBus.lookupAllExceptionalEventTypes(exceptionalEventClass);
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

    public boolean isExceptionalEventTypeMappedForExceptionalActionMode(Class<?> exceptionalEventClass, ExceptionalActionMode exceptionalActionMode) {
        CopyOnWriteArrayList<LazyHandling> lazyHandlings =
                exceptionalBus.getLazyHandlingsByExceptionalEventType().get(exceptionalEventClass);
        if (lazyHandlings != null && !lazyHandlings.isEmpty()) {
            for (LazyHandling lazyHandling : lazyHandlings) {
                if (ExceptionalActionMode.isTypeEnableFor(lazyHandling.handlerClass, exceptionalActionMode)
                        && lazyHandling.handlerMethod.actionMode == exceptionalActionMode) {
                    return true;
                }
            }
        }
        return false;
    }
}