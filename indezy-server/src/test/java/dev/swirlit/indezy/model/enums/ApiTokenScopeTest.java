package dev.swirlit.indezy.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiTokenScopeTest {

    @Test
    void findsThePartOfTheWorkspaceAPathReads() {
        assertThat(ApiTokenScope.of("/projects/4/activity-reports/2026/11")).contains(ApiTokenScope.MISSIONS);
        assertThat(ApiTokenScope.of("/projects/4/purchase-orders")).contains(ApiTokenScope.MISSIONS);
        assertThat(ApiTokenScope.of("/purchase-orders/9")).contains(ApiTokenScope.MISSIONS);
        assertThat(ApiTokenScope.of("/projects/4")).contains(ApiTokenScope.OPPORTUNITIES);
        assertThat(ApiTokenScope.of("/projects/kanban/1")).contains(ApiTokenScope.OPPORTUNITIES);
        assertThat(ApiTokenScope.of("/interview-steps/by-freelance/1")).contains(ApiTokenScope.OPPORTUNITIES);
        assertThat(ApiTokenScope.of("/contacts")).contains(ApiTokenScope.CONTACTS);
        assertThat(ApiTokenScope.of("/invoices/export/2026")).contains(ApiTokenScope.INVOICES);
        assertThat(ApiTokenScope.of("/compliance-documents/3/content")).contains(ApiTokenScope.DOCUMENTS);
        assertThat(ApiTokenScope.of("/offers/feed")).contains(ApiTokenScope.OFFERS);
        assertThat(ApiTokenScope.of("/projectsx")).isEmpty();
        assertThat(ApiTokenScope.of("/search")).isEmpty();
    }
}
