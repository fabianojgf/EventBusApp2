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
                    if (hasHandlementForExceptionalEventType(clazz)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return hasHandlementForExceptionalEventType(exceptionalEventClass);
    }

    /**
     * Checks if there is any handlement associated with this type of exceptional event.
     *
     * @param exceptionalEventClass
     * @return
     */
    public boolean hasHandlementForExceptionalEventType(Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<Handlement> handlements = null;
        synchronized (this) {
            handlements = exceptionalBus.getHandlementsByExceptionalEventType().get(exceptionalEventClass);
            return handlements != null && !handlements.isEmpty();
        }
    }

    /**
     * Checks if there is any class, which does not necessarily have any instance registered as a handler,
     * that has a mapped method to be invoked to process the exceptional event.
     *
     * @param exceptionalEvent
     * @return
     */
    public boolean hasMappedHandlerClassForExceptionalEvent(Object exceptionalEvent) {
        return (exceptionalEvent == null) ? false : hasMappedHandlerClassForExceptionalEventType(exceptionalEvent.getClass());
    }

    /**
     * Checks if there is any class, which does not necessarily have any instance registered as a handler,
     * that has a mapped method to be invoked to process this type of exceptional event.
     *
     * @param exceptionalEventClass
     * @return
     */
    public boolean hasMappedHandlerClassForExceptionalEventType(Class<?> exceptionalEventClass) {
        if (exceptionalBus.isExceptionalEventInheritance()) {
            List<Class<?>> exceptionalEventTypes = ExceptionalBus.lookupAllExceptionalEventTypes(exceptionalEventClass);
            if (exceptionalEventTypes != null) {
                int countTypes = exceptionalEventTypes.size();
                for (int h = 0; h < countTypes; h++) {
                    Class<?> clazz = exceptionalEventTypes.get(h);
                    if (hasMappedClassHandlementForExceptionalEventType(clazz)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return hasMappedClassHandlementForExceptionalEventType(exceptionalEventClass);
    }

    /**
     * Checks if there is any handlement associated with this type of exceptional event.
     *
     * @param exceptionalEventClass
     * @return
     */
    public boolean hasMappedClassHandlementForExceptionalEventType(Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<HandlerClass> mappedHandlerClasses;
        synchronized (this) {
            mappedHandlerClasses = exceptionalBus.getMappedHandlerClassesByExceptionalEventType().get(exceptionalEventClass);
            return mappedHandlerClasses != null && !mappedHandlerClasses.isEmpty();
        }
    }

    /**
     * Checks if the handler object is registered for the exceptional event.
     *
     * @param handler
     * @param exceptionalEvent
     * @return
     */
    public boolean isHandlerForExceptionalEvent(Object handler, Object exceptionalEvent) {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        Class<?> handlerClass = handler.getClass();
        if (exceptionalBus.isExceptionalEventInheritance()) {
            List<Class<?>> exceptionalEventTypes = ExceptionalBus.lookupAllExceptionalEventTypes(exceptionalEventClass);
            int countTypes = exceptionalEventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = exceptionalEventTypes.get(h);
                if(isHandlerForExceptionalEventType(handler, clazz))
                    return true;
            }
            return false;
        }
        return isHandlerForExceptionalEventType(handler, exceptionalEventClass);
    }

    /**
     * Checks if the handler object is registered for this type of exceptional event.
     *
     * @param handler
     * @param exceptionalEventClass
     * @return
     */
    public boolean isHandlerForExceptionalEventType(Object handler, Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<Handlement> handlements;
        synchronized (this) {
            handlements = exceptionalBus.getHandlementsByExceptionalEventType().get(exceptionalEventClass);
            if(handlements != null && !handlements.isEmpty()) {
                for(Handlement handlement : handlements) {
                    if(handlement.handler.equals(handler))
                        return true;
                }
            }
            return false;
        }
    }

    public boolean isRegisteredHandlerClassForExceptionalEvent(Class<?> handlerClass, Object exceptionalEvent) {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        if (exceptionalBus.isExceptionalEventInheritance()) {
            List<Class<?>> exceptionalEventTypes = ExceptionalBus.lookupAllExceptionalEventTypes(exceptionalEventClass);
            int countTypes = exceptionalEventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = exceptionalEventTypes.get(h);
                if(isRegisteredHandlerClassForExceptionalEventType(handlerClass, clazz))
                    return true;
            }
            return false;
        }
        return isRegisteredHandlerClassForExceptionalEventType(handlerClass, exceptionalEventClass);
    }

    public boolean isRegisteredHandlerClassForExceptionalEventType(Class<?> handlerClassType, Class<?> exceptionalEventClass) {
        CopyOnWriteArrayList<Handlement> handlements;
        synchronized (this) {
            handlements = exceptionalBus.getHandlementsByExceptionalEventType().get(exceptionalEventClass);
            if(handlements != null && !handlements.isEmpty()) {
                for(Handlement handlement : handlements) {
                    if(handlement.handler.getClass().equals(handlerClassType))
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
        CopyOnWriteArrayList<HandlerClass> handlerClasses;
        synchronized (this) {
            handlerClasses = exceptionalBus.getMappedHandlerClassesByExceptionalEventType().get(exceptionalEventClass);
            if(handlerClasses != null && !handlerClasses.isEmpty()) {
                for(HandlerClass handlerClass : handlerClasses) {
                    if(exceptionalBus.isExceptionalEventInheritance()) {
                        if(handlerClass.handlerClass.isAssignableFrom(handlerClassType))
                            return true;
                    }
                    else {
                        if(handlerClass.handlerClass.equals(handlerClassType))
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
        CopyOnWriteArrayList<HandlerClass> handlerClasses =
                exceptionalBus.getMappedHandlerClassesByExceptionalEventType().get(exceptionalEventClass);
        if (handlerClasses != null && !handlerClasses.isEmpty()) {
            for (HandlerClass handlerClass : handlerClasses) {
                if (ExceptionalActionMode.isTypeEnableFor(handlerClass.handlerClass, exceptionalActionMode)
                        && handlerClass.handlerMethod.actionMode == exceptionalActionMode) {
                    return true;
                }
            }
        }
        return false;
    }
}