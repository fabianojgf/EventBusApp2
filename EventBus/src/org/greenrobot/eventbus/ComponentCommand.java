package org.greenrobot.eventbus;

import android.app.Activity;
import android.app.Fragment;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.greenrobot.eventbus.util.ArrayUtil;

public enum ComponentCommand {
    /** Command Default for All Components */
    DEFAULT,
    /** Commands for Activity Components */
    START_ACTIVITY,
    START_ACTIVITY_FOR_RESULT,
    START_ACTIVITY_FROM_CHILD,
    START_ACTIVITY_FROM_FRAGMENT,
    START_ACTIVITY_IF_NEEDED,
    START_ACTIVITIES,
    /** Commands for Service Components */
    START_SERVICE,
    START_FOREGROUND_SERVICE;

    public static ComponentCommand[] getActivityCommands() {
        return new ComponentCommand[] {
                DEFAULT,
                START_ACTIVITY,
                START_ACTIVITY_FOR_RESULT,
                START_ACTIVITY_FROM_CHILD,
                START_ACTIVITY_FROM_FRAGMENT,
                START_ACTIVITY_IF_NEEDED,
                START_ACTIVITIES
        };
    }

    public static ComponentCommand[] getServiceCommands() {
        return new ComponentCommand[] {
                DEFAULT,
                START_SERVICE,
                START_FOREGROUND_SERVICE
        };
    }

    public static boolean isTypeEnableFor(Class<?> type, ComponentCommand componentCommand) {
        if(type == null || componentCommand == null)
            return false;

        return ((Activity.class.isAssignableFrom(type)
                    && ArrayUtil.contains(getActivityCommands(), componentCommand))
                || (Service.class.isAssignableFrom(type)
                    && ArrayUtil.contains(getServiceCommands(), componentCommand)));
    }

    public static void executeCommand(Context context, Intent intent, ComponentCommand componentCommand) throws Exception {
        executeCommand(context, intent, 0, null, componentCommand);
    }

    public static void executeCommand(Context context, Intent intent, int requestCode,
                                      Bundle options, ComponentCommand componentCommand) throws Exception {
        executeCommand(context, intent, requestCode, options, null, null, componentCommand);
    }

    public static void executeCommand(Context context, Intent intent, int requestCode,
                                      Bundle options, Activity activity, Fragment fragment, ComponentCommand componentCommand) throws Exception {
        /** Default */

        if(componentCommand.equals(DEFAULT)) {
            if(Activity.class.isAssignableFrom(context.getClass())) {
                context.startActivity(intent);
            }
            if(Service.class.isAssignableFrom(context.getClass())) {
                context.startService(intent);
            }
        }

        /** Activity */

        else if(componentCommand.equals(START_ACTIVITY)) {
            context.startActivity(intent, options);
        }
        else if(componentCommand.equals(START_ACTIVITY_FOR_RESULT)) {
            if(Activity.class.isAssignableFrom(context.getClass())) {
                ((Activity) context).startActivityForResult(intent, requestCode, options);
            }
            else {
                throw new Exception("Class " + context.getClass() + " doesn't support method 'startActivityForResult'.");
            }
        }
        else if(componentCommand.equals(START_ACTIVITY_FROM_CHILD)) {
            if(Activity.class.isAssignableFrom(context.getClass())) {
                ((Activity) context).startActivityFromChild(activity, intent, requestCode, options);
            }
            else {
                throw new Exception("Class " + context.getClass() + " doesn't support method 'startActivityFromChild'.");
            }
        }
        else if(componentCommand.equals(START_ACTIVITY_FROM_FRAGMENT)) {
            if(Activity.class.isAssignableFrom(context.getClass())) {
                ((Activity) context).startActivityFromFragment(fragment, intent, requestCode, options);
            }
            else {
                throw new Exception("Class " + context.getClass() + " doesn't support method 'startActivityFromFragment'.");
            }
        }
        else if(componentCommand.equals(START_ACTIVITY_IF_NEEDED)) {
            if(Activity.class.isAssignableFrom(context.getClass())) {
                ((Activity) context).startActivityIfNeeded(intent, requestCode, options);
            }
            else {
                throw new Exception("Class " + context.getClass() + " doesn't support method 'startActivityIfNeeded'.");
            }
        }
        else if(componentCommand.equals(START_ACTIVITIES)) {
            context.startActivities(new Intent[]{intent}, options);
        }

        /** Service */

        else if(componentCommand.equals(START_SERVICE)) {
            context.startService(intent);
        }
        else if(componentCommand.equals(START_FOREGROUND_SERVICE)) {
            //As bibliotecas Android (4.1.1.4) usadas no Eventbus não suporta.
            //context.startForegroundService(intent);
            context.startService(intent);
        }
    }
}
