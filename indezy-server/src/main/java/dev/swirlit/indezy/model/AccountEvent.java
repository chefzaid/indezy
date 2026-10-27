package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.AccountEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Something that happened to an account (a sign-in, a password change...), kept a year for its owner to read. */
@Entity
@Table(name = "account_events", indexes = @Index(name = "idx_account_events_user", columnList = "user_id, created_at"))
@Getter
@Setter
public class AccountEvent extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private AccountEventType type;

    /** What qualifies the event: the sign-in method, why a sign-in was refused, how many sessions ended. */
    @Column(name = "detail", length = 40)
    private String detail;

    @Column(name = "device", length = 80)
    private String device;
}
