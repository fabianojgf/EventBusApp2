package org.greenrobot.eventbus.parametric_scope;

import br.ufc.dc.eval.Assignment;

public class EvalObservedScopeData extends ObservedScopeData {
    protected Assignment assignment;

    public EvalObservedScopeData(Assignment assignment) {
        this.assignment = assignment;
    }

    public Assignment getAssignment() {
        return assignment;
    }

    public void setAssignment(Assignment assignment) {
        this.assignment = assignment;
    }
}
