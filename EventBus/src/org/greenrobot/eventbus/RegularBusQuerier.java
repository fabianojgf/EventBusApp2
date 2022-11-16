package org.greenrobot.eventbus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RegularBusQuerier {
    private RegularBus regularBus;

    public RegularBusQuerier(RegularBus regularBus) {
        this.regularBus = regularBus;
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
     * Checks if there is any registered subscriber object to be invoked to process this type of event.
     *
     * @param eventClass
     * @return
     */
    public boolean hasSubscriberForEventType(Class<?> eventClass) {
        if (regularBus.isEventInheritance()) {
            List<Class<?>> eventTypes = RegularBus.lookupAllEventTypes(eventClass);
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
     * Checks if there is any subscription associated with this type of event.
     *
     * @param eventClass
     * @return
     */
    public boolean hasSubscriptionForEventType(Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions = null;
        synchronized (this) {
            subscriptions = regularBus.getEagerSubscriptionsByEventType().get(eventClass);
            return subscriptions != null && !subscriptions.isEmpty();
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
     * Checks if there is any class, which does not necessarily have any instance registered as a subscriber,
     * that has a mapped method to be invoked to process this type of event.
     *
     * @param eventClass
     * @return
     */
    public boolean hasMappedSubscriberClassForEventType(Class<?> eventClass) {
        if (regularBus.isEventInheritance()) {
            List<Class<?>> eventTypes = RegularBus.lookupAllEventTypes(eventClass);
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
     * Checks if there is any 'subscription associated with this type of event.
     *
     * @param eventClass
     * @return
     */
    public boolean hasMappedClassSubscriptionForEventType(Class<?> eventClass) {
        CopyOnWriteArrayList<LazySubscription> mappedSubscriberClasses;
        synchronized (this) {
            mappedSubscriberClasses = regularBus.getLazySubscriptionsByEventType().get(eventClass);
            return mappedSubscriberClasses != null && !mappedSubscriberClasses.isEmpty();
        }
    }

    /**
     * Checks if the subscriber object is registered for the event.
     *
     * @param subscriber
     * @param event
     * @return
     */
    public boolean isEagerSubscriberForEvent(Object subscriber, Object event) {
        Class<?> eventClass = event.getClass();
        Class<?> subscriberClass = subscriber.getClass();
        if (regularBus.isEventInheritance()) {
            List<Class<?>> eventTypes = RegularBus.lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                if(isEagerSubscriberForEventType(subscriber, clazz))
                    return true;
            }
            return false;
        }
        return isEagerSubscriberForEventType(subscriber, eventClass);
    }

    /**
     * Checks if the subscriber object is registered for this type of event.
     *
     * @param subscriber
     * @param eventClass
     * @return
     */
    public boolean isEagerSubscriberForEventType(Object subscriber, Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            subscriptions = regularBus.getEagerSubscriptionsByEventType().get(eventClass);
            if(subscriptions != null && !subscriptions.isEmpty()) {
                for(Subscription subscription : subscriptions) {
                    if(subscription.subscriber.equals(subscriber))
                        return true;
                }
            }
            return false;
        }
    }

    public boolean isEagerSubscriberClassForEvent(Class<?> subscriberClass, Object event) {
        Class<?> eventClass = event.getClass();
        if (regularBus.isEventInheritance()) {
            List<Class<?>> eventTypes = RegularBus.lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                if(isEagerSubscriberClassForEventType(subscriberClass, clazz))
                    return true;
            }
            return false;
        }
        return isEagerSubscriberClassForEventType(subscriberClass, eventClass);
    }

    public boolean isEagerSubscriberClassForEventType(Class<?> subscriberClassType, Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            subscriptions = regularBus.getEagerSubscriptionsByEventType().get(eventClass);
            if(subscriptions != null && !subscriptions.isEmpty()) {
                for(Subscription subscription : subscriptions) {
                    if(subscription.subscriber.getClass().equals(subscriberClassType))
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
        if (regularBus.isEventInheritance()) {
            List<Class<?>> eventTypes = RegularBus.lookupAllEventTypes(eventClass);
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
     * Checks if the class is registered as 'subscriber class' for this type of event.
     *
     * @param subscriberClassType
     * @param eventClass
     * @return
     */
    public boolean isMappedSubscriberClassForEventType(Class<?> subscriberClassType, Class<?> eventClass) {
        CopyOnWriteArrayList<LazySubscription> subscriberClasses;
        synchronized (this) {
            subscriberClasses = regularBus.getLazySubscriptionsByEventType().get(eventClass);
            if(subscriberClasses != null && !subscriberClasses.isEmpty()) {
                for(LazySubscription subscriberClass : subscriberClasses) {
                    if(regularBus.isEventInheritance()) {
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

    public boolean isSubscriberMappedForActionMode(Class<?> subscriberClass, ActionMode actionMode) {
        if(!ActionMode.isTypeEnableFor(subscriberClass, actionMode))
            return false;

        List<SubscriberMethod> subscriberMethods = regularBus.getSubscriberMethodFinder().findSubscriberMethods(subscriberClass);
        for(SubscriberMethod m : subscriberMethods) {
            if(m.actionMode.equals(actionMode))
                return true;
        }
        return false;
    }

    public boolean isEventMappedForActionMode(Object event, ActionMode actionMode) {
        Class<?> eventClass = event.getClass();
        boolean eventTypeMapped = false;
        if (regularBus.isEventInheritance()) {
            List<Class<?>> eventTypes = RegularBus.lookupAllEventTypes(eventClass);
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

    public boolean isEventTypeMappedForActionMode(Class<?> eventClass, ActionMode actionMode) {
        CopyOnWriteArrayList<LazySubscription> subscriberClasses =
                regularBus.getLazySubscriptionsByEventType().get(eventClass);
        if (subscriberClasses != null && !subscriberClasses.isEmpty()) {
            for (LazySubscription subscriberClass : subscriberClasses) {
                if (ActionMode.isTypeEnableFor(subscriberClass.subscriberClass, actionMode)
                        && subscriberClass.subscriberMethod.actionMode == actionMode) {
                    return true;
                }
            }
        }
        return false;
    }
}
