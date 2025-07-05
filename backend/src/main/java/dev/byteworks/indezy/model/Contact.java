package dev.byteworks.indezy.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "contacts")
@Getter
@Setter
public class Contact extends BaseEntity {

    @NotBlank
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Email
    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelance_id", nullable = false)
    private Freelance freelance;

    // Helper method
    public String getFullName() {
        if (lastName != null && !lastName.trim().isEmpty()) {
            return firstName + " " + lastName;
        }
        return firstName;
    }
}
