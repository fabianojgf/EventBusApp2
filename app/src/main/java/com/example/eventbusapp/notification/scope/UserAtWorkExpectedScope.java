package com.example.eventbusapp.notification.scope;

import static br.ufc.dc.eval.Expression.and;
import static br.ufc.dc.eval.Expression.gteq;
import static br.ufc.dc.eval.Expression.lteq;

import android.location.Location;

import org.greenrobot.eventbus.parametric_scope.EvalExpectedScopeData;
import org.greenrobot.eventbus.parametric_scope.EvalObservedScopeData;

import br.ufc.dc.eval.EvaluationException;
import br.ufc.dc.eval.Expression;
import br.ufc.dc.eval.var.NumberConstant;
import br.ufc.dc.eval.var.NumberVariable;
import br.ufc.dc.eval.var.StringVariable;

public class UserAtWorkExpectedScope extends EvalExpectedScopeData {
    @Override
    protected Expression matchExpression(EvalObservedScopeData scopeData) {
        StringVariable userName = new StringVariable("userName");
        NumberVariable userLocationLat = new NumberVariable("userLocationLat");
        NumberVariable userLocationLong = new NumberVariable("userLocationLat");
        NumberVariable userWorkLocationLat = new NumberVariable("userWorkLocationLat");
        NumberVariable userWorkLocationLong = new NumberVariable("userWorkLocationLong");

        double radius = 10.0; //10 Meters.

        Expression exprUserLocation = null;

        try {
            Location userLocation = new Location("");
            userLocation.setLatitude(userLocationLat.getValuation(
                    scopeData.getAssignment()).doubleValue());
            userLocation.setLongitude(userLocationLong.getValuation(
                    scopeData.getAssignment()).doubleValue());

            Location userWorkLocation = new Location("");
            userWorkLocation.setLatitude(userWorkLocationLat.getValuation(
                    scopeData.getAssignment()).doubleValue());
            userWorkLocation.setLongitude(userWorkLocationLong.getValuation(
                    scopeData.getAssignment()).doubleValue());

            double distance = userLocation.distanceTo(userWorkLocation);

            exprUserLocation = Expression.lteq(
                    new NumberConstant(distance),
                    new NumberConstant(radius));
        } catch (EvaluationException e) {
            e.printStackTrace();
        }

        return exprUserLocation;
    }
}
