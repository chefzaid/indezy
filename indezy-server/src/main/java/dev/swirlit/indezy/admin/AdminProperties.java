package dev.swirlit.indezy.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Locale;

/**
 * Who administers Indezy ({@code indezy.admin.emails}, from {@code INDEZY_ADMIN_EMAILS}): the accounts with these
 * emails, case aside. None by default, so the admin console stays closed until the deployment names someone.
 */
@ConfigurationProperties(prefix = "indezy.admin")
public record AdminProperties(@DefaultValue List<String> emails) {

    public AdminProperties {
        emails = emails == null ? List.of() : List.copyOf(emails.stream().filter(email -> email != null && !email.isBlank())
            .map(email -> email.strip().toLowerCase(Locale.ROOT)).toList());
    }

    public boolean isAdmin(String email) {
        return email != null && emails.contains(email.strip().toLowerCase(Locale.ROOT));
    }
}
