package org.greenrobot.eventbus.parametric_scope;

import br.ufc.dc.eval.EvaluationException;
import br.ufc.dc.eval.Evaluator;
import br.ufc.dc.eval.Expression;

public abstract class ExpectedScopeData<T extends ObservedScopeData> {
    public abstract boolean matched(T scopeData);
}