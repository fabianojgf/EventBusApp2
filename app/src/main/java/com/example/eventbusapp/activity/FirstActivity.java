package com.example.eventbusapp.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.example.eventbusapp.R;
import com.example.eventbusapp.exception.UserClickException;
import com.example.eventbusapp.notification.UserHealthProblemExceptionEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.parametric_scope.EvalObservedScopeData;

import br.ufc.dc.eval.Assignment;

public class FirstActivity extends AppCompatActivity {
    Button buttonException;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);

        buttonException = findViewById(R.id.buttonException);
        buttonException.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                executeAction(v);
            }
        });

        Log.println(Log.VERBOSE, "EventBusTest", "FirstActivity: onCreate: ---");
    }

    public void executeAction(View view) {
        try {
            methodToThrowException();
        } catch(UserClickException e) {
            Assignment assignment = new Assignment();
            assignment.assign("userName", "Fabiano");
            assignment.assign("userLocationLat", -3.759054);
            assignment.assign("userLocationLong", -38.538155);

            assignment.assign("userHomeLocationLat", -3.759011);
            assignment.assign("userHomeLocationLong", -38.538085);

            assignment.assign("userWorkLocationLat", -3.7452654);
            assignment.assign("userWorkLocationLong", -38.5758069);

            EventBus.getDefault(this).throwException(
                    new UserHealthProblemExceptionEvent(e,
                            new EvalObservedScopeData(assignment)));
        }
    }

    private void methodToThrowException() throws UserClickException {
        throw new UserClickException();
    }
}