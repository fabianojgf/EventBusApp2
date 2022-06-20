package org.greenrobot.eventbus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBusQuerier {
    private EventBus eventBus;

    public EventBusQuerier(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Checks if there is any registered subscriber object to be invoked to process the event.
     *
     * @param event
     * @return
     */
    public boolean hasSubscriberForEvent(Object event) {
        return (event == null) ? false : hasSubscriberForEventType(event.getClass());
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
     * Checks if there is any registered subscriber object to be invoked to process this type of event.
     *
     * @param eventClass
     * @return
     */
    public boolean hasSubscriberForEventType(Class<?> eventClass) {
        if (eventBus.isEventInheritance()) {
            List<Class<?>> eventTypes = EventBus.lookupAllEventTypes(eventClass);
            if (eventTypes != null) {
                int countTypes = eventTypes.size();
                for (int h = 0; h < countTypes; h++) {
                    Class<?> clazz = eventTypes.get(h);
                    if (hasSubscriptionForEventType(clazz)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return hasSubscriptionForEventType(eventClass);
    }

    /**
     * Checks if there is any registered handler object to be invoked to process this type of exceptional event.
     *
     * @param exceptionalEventClass
     * @return
     */
    public boolean hasHandlerForExceptionalEventType(Class<?> exceptionalEventClass) {
        if (eventBus.isExceptionalEventInheritance()) {
            List<Class<?>> exceptionalEventTypes = EventBus.lookupAllExceptionalEventTypes(exceptionalEventClass);
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
     * Checks if there is any subscription associated with this type of event.
     *
     * @param eventClass
     * @return
     */
    public boolean hasSubscriptionForEventType(Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions = null;
        synchronized (this) {
            subscriptions = eventBus.getSubscriptionsByEventType().get(eventClass);
            return subscriptions != null && !subscriptions.isEmpty();
        }
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
            handlements = eventBus.getHandlementsByExceptionalEventType().get(exceptionalEventClass);
            return handlements != null && !handlements.isEmpty();
        }
    }

    /**
     * Checks if there is any class, which does not necessarily have any instance registered as a subscriber,
     * that has a mapped method to be invoked to process the event.
     *
     * @param event
     * @return
     */
    public boolean hasMappedSubscriberClassForEvent(Object event) {
        return (event == null) ? false : hasMappedSubscriberClassForEventType(event.getClass());
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
     * Checks if there is any class, which does not necessarily have any instance registered as a subscriber,
     * that has a mapped method to be invoked to process this type of event.
     *
     * @param eventClass
     * @return
     */
    public boolean hasMappedSubscriberClassForEventType(Class<?> eventClass) {
        if (eventBus.isEventInheritance()) {
            List<Class<?>> eventTypes = EventBus.lookupAllEventTypes(eventClass);
            if (eventTypes != null) {
                int countTypes = eventTypes.size();
                for (int h = 0; h < countTypes; h++) {
                    Class<?> clazz = eventTypes.get(h);
                    if (hasMappedClassSubscriptionForEventType(clazz)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return hasMappedClassSubscriptionForEventType(eventClass);
    }

    /**
     * Checks if there is any class, which does not necessarily have any instance registered as a handler,
     * that has a mapped method to be invoked to process this type of exceptional event.
     *
     * @param exceptionalEventClass
     * @return
     */
    public boolean hasMappedHandlerClassForExceptionalEventType(Class<?> exceptionalEventClass) {
        if (eventBus.isExceptionalEventInheritance()) {
            List<Class<?>> exceptionalEventTypes = EventBus.lookupAllExceptionalEventTypes(exceptionalEventClass);
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
     * Checks if there is any 'subscription associated with this type of event.
     *
     * @param eventClass
     * @return
     */
    public boolean hasMappedClassSubscriptionForEventType(Class<?> eventClass) {
        CopyOnWriteArrayList<SubscriberClass> mappedSubscriberClasses;
        synchronized (this) {
            mappedSubscriberClasses = eventBus.getMappedSubscriberClassesByEventType().get(eventClass);
            return mappedSubscriberClasses != null && !mappedSubscriberClasses.isEmpty();
        }
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
            mappedHandlerClasses = eventBus.getMappedHandlerClassesByExceptionalEventType().get(exceptionalEventClass);
            return mappedHandlerClasses != null && !mappedHandlerClasses.isEmpty();
        }
    }

    /**
     * Checks if the subscriber object is registered for the event.
     *
     * @param subscriber
     * @param event
     * @return
     */
    public boolean isSubscriberForEvent(Object subscriber, Object event) {
        Class<?> eventClass = event.getClass();
        Class<?> subscriberClass = subscriber.getClass();
        if (eventBus.isEventInheritance()) {
            List<Class<?>> eventTypes = EventBus.lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                if(isSubscriberForEventType(subscriber, clazz))
                    return true;
            }
            return false;
        }
        return isSubscriberForEventType(subscriber, eventClass);
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
        if (eventBus.isExceptionalEventInheritance()) {
            List<Class<?>> exceptionalEventTypes = EventBus.lookupAllExceptionalEventTypes(exceptionalEventClass);
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
     * Checks if the subscriber object is registered for this type of event.
     *
     * @param subscriber
     * @param eventClass
     * @return
     */
    public boolean isSubscriberForEventType(Object subscriber, Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            subscriptions = eventBus.getSubscriptionsByEventType().get(eventClass);
            if(subscriptions != null && !subscriptions.isEmpty()) {
                for(Subscription subscription : subscriptions) {
                    if(subscription.subscriber.equals(subscriber))
                        return true;
                }
            }
            return false;
        }
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
            handlements = eventBus.getHandlementsByExceptionalEventType().get(exceptionalEventClass);
            if(handlements != null && !handlements.isEmpty()) {
                for(Handlement handlement : handlements) {
                    if(handlement.handler.equals(handler))
                        return true;
                }
            }
            return false;
        }
    }


    public boolean isRegisteredSubscriberClassForEvent(Class<?> subscriberClass, Object event) {
        Class<?> eventClass = event.getClass();
        if (eventBus.isEventInheritance()) {
            List<Class<?>> eventTypes = EventBus.lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                if(isRegisteredSubscriberClassForEventType(subscriberClass, clazz))
                    return true;
            }
            return false;
        }
        return isRegisteredSubscriberClassForEventType(subscriberClass, eventClass);
    }

    public boolean isRegisteredSubscriberClassForEventType(Class<?> subscriberClassType, Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            subscriptions = eventBus.getSubscriptionsByEventType().get(eventClass);
            if(subscriptions != null && !subscriptions.isEmpty()) {
                for(Subscription subscription : subscriptions) {
                    if(subscription.subscriber.getClass().equals(subscriberClassType))
                        return true;
                }
            }
            return false;
        }
    }

    public boolean isRegisteredHandlerClassForExceptionalEvent(Class<?> handlerClass, Object exceptionalEvent) {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        if (eventBus.isExceptionalEventInheritance()) {
            List<Class<?>> exceptionalEventTypes = EventBus.lookupAllExceptionalEventTypes(exceptionalEventClass);
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
            handlements = eventBus.getHandlementsByExceptionalEventType().get(exceptionalEventClass);
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
     * Checks if the class is registered as 'subscriber class' for the event.
     * Subscriber class is a class which has methods mapped as subscriber for the event.
     *
     * @param subscriberClass
     * @param event
     * @return
     */
    public boolean isMappedSubscriberClassForEvent(Class<?> subscriberClass, Object event) {
        Class<?> eventClass = event.getClass();
        if (eventBus.isEventInheritance()) {
            List<Class<?>> eventTypes = EventBus.lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                if(isMappedSubscriberClassForEventType(subscriberClass, clazz))
                    return true;
            }
            return false;
        }
        return isMappedSubscriberClassForEventType(subscriberClass, eventClass);
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
        if (eventBus.isExceptionalEventInheritance()) {
            List<Class<?>> exceptionalEventTypes = EventBus.lookupAllExceptionalEventTypes(exceptionalEventClass);
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
     * Checks if the class is registered as 'subscriber class' for this type of event.
     *
     * @param subscriberClassType
     * @param eventClass
     * @return
     */
    public boolean isMappedSubscriberClassForEventType(Class<?> subscriberClassType, Class<?> eventClass) {
        CopyOnWriteArrayList<SubscriberClass> subscriberClasses;
        synchronized (this) {
            subscriberClasses = eventBus.getMappedSubscriberClassesByEventType().get(eventClass);
            if(subscriberClasses != null && !subscriberClasses.isEmpty()) {
                for(SubscriberClass subscriberClass : subscriberClasses) {
                    if(eventBus.isEventInheritance()) {
                        if(subscriberClass.subscriberClass.isAssignableFrom(subscriberClassType))
                            return true;
                    }
                    else {
                        if(subscriberClass.subscriberClass.equals(subscriberClassType))
                            return true;
                    }
                }
            }
            return false;
        }
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
            handlerClasses = eventBus.getMappedHandlerClassesByExceptionalEventType().get(exceptionalEventClass);
            if(handlerClasses != null && !handlerClasses.isEmpty()) {
                for(HandlerClass handlerClass : handlerClasses) {
                    if(eventBus.isExceptionalEventInheritance()) {
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

    public boolean isSubscriberMappedForActionMode(Class<?> subscriberClass, ActionMode actionMode) {
        if(!ActionMode.isTypeEnableFor(subscriberClass, actionMode))
            return false;

        List<SubscriberMethod> subscriberMethods = eventBus.getSubscriberMethodFinder().findSubscriberMethods(subscriberClass);
        for(SubscriberMethod m : subscriberMethods) {
            if(m.actionMode.equals(actionMode))
                return true;
        }
        return false;
    }

    public boolean isHandlerMappedForExceptionalActionMode(Class<?> handlerClass, ExceptionalActionMode exceptionalActionMode) {
        if(!ExceptionalActionMode.isTypeEnableFor(handlerClass, exceptionalActionMode))
            return false;

        List<HandlerMethod> handlerMethods = eventBus.getHandlerMethodFinder().findHandlerMethods(handlerClass);
        for(HandlerMethod h : handlerMethods) {
            if(h.actionMode.equals(exceptionalActionMode))
                return true;
        }
        return false;
    }

    public boolean isEventMappedForActionMode(Object event, ActionMode actionMode) {
        Class<?> eventClass = event.getClass();
        boolean eventTypeMapped = false;
        if (eventBus.isEventInheritance()) {
            List<Class<?>> eventTypes = EventBus.lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes && !eventTypeMapped; h++) {
                Class<?> clazz = eventTypes.get(h);
                eventTypeMapped |= isEventTypeMappedForActionMode(clazz, actionMode);
            }
        } else {
            eventTypeMapped = isEventTypeMappedForActionMode(eventClass, actionMode);
        }

        return eventTypeMapped;
    }

    public boolean isExceptionalEventMappedForExceptionalActionMode(Object exceptionalEvent, ExceptionalActionMode exceptionalActionMode) {
        Class<?> exceptionalEventClass = exceptionalEvent.getClass();
        boolean exceptionalEventTypeMapped = false;
        if (eventBus.isExceptionalEventInheritance()) {
            List<Class<?>> exceptionalEventTypes = EventBus.lookupAllExceptionalEventTypes(exceptionalEventClass);
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

    public boolean isEventTypeMappedForActionMode(Class<?> eventClass, ActionMode actionMode) {
        CopyOnWriteArrayList<SubscriberClass> subscriberClasses =
                eventBus.getMappedSubscriberClassesByEventType().get(eventClass);
        if (subscriberClasses != null && !subscriberClasses.isEmpty()) {
            for (SubscriberClass subscriberClass : subscriberClasses) {
                if (ActionMode.isTypeEnableFor(subscriberClass.subscriberClass, actionMode)
                        && subscriberClass.subscriberMethod.actionMode == actionMode) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isExceptionalEventTypeMappedForExceptionalActionMode(Class<?> exceptionalEventClass, ExceptionalActionMode exceptionalActionMode) {
        CopyOnWriteArrayList<HandlerClass> handlerClasses =
                eventBus.getMappedHandlerClassesByExceptionalEventType().get(exceptionalEventClass);
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
