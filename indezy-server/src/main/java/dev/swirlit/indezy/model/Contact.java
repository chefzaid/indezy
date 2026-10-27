package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.ContactLegalBasis;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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

    /** Why the freelancer keeps the contact's data; a null column reads as legitimate interest. */
    @Enumerated(EnumType.STRING)
    @Column(name = "legal_basis", length = 30)
    private ContactLegalBasis legalBasis = ContactLegalBasis.LEGITIMATE_INTEREST;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelance_id", nullable = false)
    private Freelance freelance;

    /** Whom the contact reports to, at the same company or another; the link goes when the manager is deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Contact manager;

    // Helper method
    public String getFullName() {
        if (lastName != null && !lastName.trim().isEmpty()) {
            return firstName + " " + lastName;
        }
        return firstName;
    }

    public ContactLegalBasis getLegalBasis() {
        return legalBasis == null ? ContactLegalBasis.LEGITIMATE_INTEREST : legalBasis;
    }
}
