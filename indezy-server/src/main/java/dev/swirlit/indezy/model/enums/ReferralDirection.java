package dev.swirlit.indezy.model.enums;

/** Who pays a co-optation bonus: the freelancer to who referred the mission, or someone to the freelancer for a referral. */
public enum ReferralDirection {
    /** The freelancer pays who referred the mission to them. */
    I_PAY,
    /** The freelancer is paid for referring someone (by the ESN, the client or the member placed). */
    I_RECEIVE
}
