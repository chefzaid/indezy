package dev.byteworks.indezy.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_sessions")
@Getter
@Setter
public class UserSession extends BaseEntity {

    @NotBlank
    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;

    @Column(name = "device")
    private String device;

    @Column(name = "browser")
    private String browser;

    @Column(name = "location")
    private String location;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "last_active")
    private LocalDateTime lastActive;

    @Column(name = "is_current")
    private Boolean isCurrent = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Helper methods
    public boolean isActive() {
        return lastActive != null && lastActive.isAfter(LocalDateTime.now().minusHours(24));
    }
}
