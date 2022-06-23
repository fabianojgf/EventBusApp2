package org.greenrobot.eventbus.parametric_scope;

public final class NoExpectedScopeData extends ExpectedScopeData<NoObservedScopeData>{
    @Override
    public final boolean matched(NoObservedScopeData scopeData) {
        return false;
    }
}