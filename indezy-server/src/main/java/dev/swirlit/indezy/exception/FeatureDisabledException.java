package dev.swirlit.indezy.exception;

import dev.swirlit.indezy.model.enums.FeatureFlag;

/** A request reached a feature an administrator turned off. */
public class FeatureDisabledException extends RuntimeException {

    private final FeatureFlag flag;

    public FeatureDisabledException(FeatureFlag flag) {
        super("Feature turned off: " + flag);
        this.flag = flag;
    }

    public FeatureFlag getFlag() {
        return flag;
    }
}
