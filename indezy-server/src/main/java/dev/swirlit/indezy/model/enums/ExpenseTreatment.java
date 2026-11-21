package dev.swirlit.indezy.model.enums;

/** What a workspace's expenses change in its net income, by its legal status. */
public enum ExpenseTreatment {
    /** A micro-entreprise: not deductible, the flat allowance on the turnover already counts them. */
    FLAT_ALLOWANCE,
    /** An EI au réel or a company (EURL, SASU, SARL, SAS): they lower the taxable profit. */
    DEDUCTIBLE,
    /** Portage: the portage company reimburses them as professional expenses, out of the turnover before charges. */
    REIMBURSED,
    /** No status set: only recorded. */
    RECORDED;

    public static ExpenseTreatment of(LegalForm form) {
        if (form == null || form == LegalForm.OTHER) {
            return RECORDED;
        }
        return switch (form) {
            case MICRO_ENTREPRISE -> FLAT_ALLOWANCE;
            case PORTAGE -> REIMBURSED;
            default -> DEDUCTIBLE;
        };
    }
}
