package org.greenrobot.eventbus.parametric_scope;

import br.ufc.dc.eval.EvaluationException;
import br.ufc.dc.eval.Evaluator;
import br.ufc.dc.eval.Expression;

public abstract class EvalExpectedScopeData extends ExpectedScopeData<EvalObservedScopeData>{
    protected abstract Expression matchExpression();

    @Override
    public final boolean matched(EvalObservedScopeData scopeData) {
        try {
            return Evaluator.eval(scopeData.getAssignment(), matchExpression());
        } catch (EvaluationException e) {
            e.printStackTrace();
        }
        return false;
    }
}