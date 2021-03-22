package org.greenrobot.eventbus.util;

public class ArrayUtil {
    public static int indexOf(Object[] array, Object object) {
        if(array == null || object == null)
            return -1;
        for(int i = 0; i < array.length; i++) {
            if(object.equals(array[i]))
                return i;
        }
        return -1;
    }

    public static boolean contains(Object[] array, Object object) {
        return indexOf(array, object) >= 0;
    }
}
