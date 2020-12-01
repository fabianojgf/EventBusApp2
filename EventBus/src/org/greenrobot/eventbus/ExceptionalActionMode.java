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

import android.app.Activity;
import android.app.Service;

/**
 * Each handler method has a exceptional action mode, which determines what
 * type of action will be taken to execute the method.
 *
 * @author Fabiano Gadelha
 */
@SuppressWarnings("JavaDoc")
public enum ExceptionalActionMode {
    /**
     * This is default action, in which the method will receive the invocation and will be executed.
     * However, the method will only be executed if the class instance is registered.
     */
    HANDLE,
    /**
     * This action causes the method to receive the invocation to be executed, even if there is no instance
     * of the registered class. This action ensures that the class is initialized so that the method is executed.
     */
    START_AND_HANDLE;

    /**
     * Checks if the type is enabled to use the informed action mode.
     *
     * @param type
     * @param actionMode
     * @return
     */
    public static boolean isTypeEnableFor(Class<?> type, ExceptionalActionMode actionMode) {
        if(actionMode.equals(HANDLE))
            return true;
        else if(actionMode.equals(START_AND_HANDLE)) {
            return Activity.class.isAssignableFrom(type)
                    || Service.class.isAssignableFrom(type);
        }
        return false;
    }
}