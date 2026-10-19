package dev.swirlit.indezy.scraping;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsRulesTest {

    @Test
    void appliesTheWildcardGroupWithTheLongestMatchingRule() {
        RobotsRules rules = RobotsRules.parse("""
            # comment
            User-agent: Googlebot
            Disallow: /

            User-agent: *
            Disallow: /private
            Allow: /private/jobs
            Disallow: */?id*
            Disallow: /*.pdf$
            Sitemap: https://example.com/sitemap.xml
            """, "IndezyBot");

        assertThat(rules.allows("/jobs?page=2")).isTrue();
        assertThat(rules.allows("/private/account")).isFalse();
        assertThat(rules.allows("/private/jobs/42")).isTrue();
        assertThat(rules.allows("/annonce/?id=4")).isFalse();
        assertThat(rules.allows("/doc.pdf")).isFalse();
        assertThat(rules.allows("/doc.pdf?v=1")).isTrue();
        assertThat(rules.allows("")).isTrue();
    }

    @Test
    void prefersTheGroupNamingIndezy() {
        RobotsRules rules = RobotsRules.parse("""
            User-agent: *
            Disallow: /

            User-agent: indezybot
            User-agent: OtherBot
            Disallow: /admin
            """, "IndezyBot");

        assertThat(rules.allows("/missions")).isTrue();
        assertThat(rules.allows("/admin/users")).isFalse();
    }

    @Test
    void letsAllowWinATie() {
        RobotsRules rules = RobotsRules.parse("User-agent: *\nDisallow: /page\nAllow: /page\n", "IndezyBot");

        assertThat(rules.allows("/page")).isTrue();
    }

    @Test
    void ignoresEmptyDisallowAndRulesOutsideAGroup() {
        RobotsRules rules = RobotsRules.parse("Disallow: /orphan\nUser-agent: *\nCrawl-delay: 5\nDisallow:\n", "IndezyBot");

        assertThat(rules.allows("/orphan")).isTrue();
    }

    @Test
    void allowsOrRefusesEverything() {
        assertThat(RobotsRules.allowAll().allows("/anything")).isTrue();
        assertThat(RobotsRules.disallowAll().allows("/anything")).isFalse();
    }
}
