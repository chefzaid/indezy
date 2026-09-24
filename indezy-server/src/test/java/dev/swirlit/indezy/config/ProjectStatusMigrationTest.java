package dev.swirlit.indezy.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectStatusMigrationTest {

    @Test
    void constraintWithRetiredStatusesIsOutdated() {
        String legacy = "CHECK (((status)::text = ANY ((ARRAY['IDENTIFIED'::character varying, "
            + "'APPLIED'::character varying, 'INTERVIEW'::character varying, 'OFFER'::character varying, "
            + "'WON'::character varying, 'LOST'::character varying])::text[])))";

        assertThat(ProjectStatusMigration.isUpToDate(legacy)).isFalse();
    }

    @Test
    void constraintMissingAStatusIsOutdated() {
        assertThat(ProjectStatusMigration.isUpToDate("CHECK (status IN ('INTERVIEW', 'OFFER', 'WON', 'LOST'))"))
            .isFalse();
    }

    @Test
    void rebuiltConstraintIsUpToDate() {
        String rebuilt = "CHECK (((status)::text = ANY ((ARRAY["
            + ProjectStatusMigration.allowedValues() + "])::text[])))";

        assertThat(ProjectStatusMigration.isUpToDate(rebuilt)).isTrue();
        assertThat(ProjectStatusMigration.allowedValues())
            .isEqualTo("'CONTACT', 'INTERVIEW', 'OFFER', 'WON', 'LOST'");
    }
}
