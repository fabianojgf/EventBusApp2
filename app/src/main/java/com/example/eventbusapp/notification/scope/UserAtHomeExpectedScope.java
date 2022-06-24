package com.example.eventbusapp.notification.scope;

import static br.ufc.dc.eval.Expression.*;

import android.location.Location;

import org.greenrobot.eventbus.parametric_scope.EvalExpectedScopeData;
import org.greenrobot.eventbus.parametric_scope.EvalObservedScopeData;

import br.ufc.dc.eval.EvaluationException;
import br.ufc.dc.eval.Expression;
import br.ufc.dc.eval.var.NumberConstant;
import br.ufc.dc.eval.var.NumberVariable;
import br.ufc.dc.eval.var.StringVariable;

public class UserAtHomeExpectedScope extends EvalExpectedScopeData {
    @Override
    protected Expression matchExpression(EvalObservedScopeData scopeData) {
        StringVariable userName = new StringVariable("userName");
        NumberVariable userLocationLat = new NumberVariable("userLocationLat");
        NumberVariable userLocationLong = new NumberVariable("userLocationLong");
        NumberVariable userHomeLocationLat = new NumberVariable("userHomeLocationLat");
        NumberVariable userHomeLocationLong = new NumberVariable("userHomeLocationLong");

        double radius = 10.0; //10 Meters.

        Expression exprUserLocation = null;

        try {
            Location userLocation = new Location("userLocation");
            userLocation.setLatitude(userLocationLat.getValuation(
                    scopeData.getAssignment()).doubleValue());
            userLocation.setLongitude(userLocationLong.getValuation(
                    scopeData.getAssignment()).doubleValue());

            Location userHomeLocation = new Location("userHomeLocation");
            userHomeLocation.setLatitude(userHomeLocationLat.getValuation(
                    scopeData.getAssignment()).doubleValue());
            userHomeLocation.setLongitude(userHomeLocationLong.getValuation(
                    scopeData.getAssignment()).doubleValue());

            double distance = userLocation.distanceTo(userHomeLocation);

            exprUserLocation = Expression.lteq(
                    new NumberConstant(distance),
                    new NumberConstant(radius));
        } catch (EvaluationException e) {
            e.printStackTrace();
        }

        return exprUserLocation;
    }
}
