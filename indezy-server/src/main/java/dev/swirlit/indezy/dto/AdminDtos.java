package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.FeatureFlag;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** What the admin console reads and changes. */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** Accounts, those disabled, sign-ups of the last 30 days, and workspaces using each feature. */
    public record Overview(long accounts, long disabledAccounts, long signupsLast30Days, Map<String, Long> featureWorkspaces) {

        public Overview {
            featureWorkspaces = Map.copyOf(featureWorkspaces);
        }
    }

    public record Account(Long id, String email, String firstName, String lastName, LocalDateTime createdAt,
                          LocalDateTime disabledAt, boolean deleted, boolean admin) {

        // Spring MVC logs bodies through toString at DEBUG: the id only (see docs/security.md).
        @Override
        public String toString() {
            return "Account[id=" + id + "]";
        }
    }

    public record AccountPage(List<Account> accounts, long total, int page, int size) {

        public AccountPage {
            accounts = List.copyOf(accounts);
        }
    }

    public record FeatureState(FeatureFlag flag, boolean enabled) {
    }

    public record FeatureUpdate(boolean enabled) {
    }

    /** What the signed-in account may open: the admin console, and which features are on. */
    public record Capabilities(boolean admin, Map<FeatureFlag, Boolean> features) {

        public Capabilities {
            features = Map.copyOf(features);
        }
    }
}
