package com.example.eventbusapp;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;

import androidx.test.annotation.UiThreadTest;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.core.internal.deps.guava.collect.Iterables;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.example.eventbusapp.activity.FirstActivity;
import com.example.eventbusapp.activity.MainActivity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Iterator;
import java.util.List;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
//@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    @Rule
    public ActivityTestRule activityTestRule =
            new ActivityTestRule<FirstActivity>(FirstActivity.class);

    @Test
    @UiThreadTest
    public void useAppContext() throws Throwable {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        //assertEquals("com.example.eventbusapp", appContext.getPackageName());

        activityTestRule.getActivity().findViewById(
                R.id.activityFirstButtonThrowExceptionB).performClick();

        ActivityManager manager = (ActivityManager) activityTestRule
                .getActivity().getSystemService(Context.ACTIVITY_SERVICE);

        List<ActivityManager.AppTask> runningTaskInfoList =
                manager.getAppTasks();
        do {
            runningTaskInfoList = manager.getAppTasks();
            System.out.println("[TASKS]: " + runningTaskInfoList.size());
        } while(runningTaskInfoList.size() <= 1);

        System.out.println("[TASKS]: " + runningTaskInfoList.size());
        Iterator<ActivityManager.AppTask> itr = runningTaskInfoList.iterator();

        while (itr.hasNext()) {
            ActivityManager.AppTask runningTaskInfo =
                    (ActivityManager.AppTask) itr.next();
            System.out.println("[ACTIVITY]: " + runningTaskInfo.getTaskInfo().toString());
        }
    }

    public static  <T extends Activity> T getCurrentActivity(ActivityTestRule activityTestRule) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        final Activity[] activity = new Activity[1];
        try {
            activityTestRule.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    java.util.Collection<Activity> activites = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED);
                    activity[0] = Iterables.getOnlyElement(activites);
                }});
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        //noinspection unchecked
        return (T) activity[0];
    }
}