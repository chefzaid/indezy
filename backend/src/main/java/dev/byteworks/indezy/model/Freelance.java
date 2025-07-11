package dev.byteworks.indezy.model;

import dev.byteworks.indezy.model.enums.EmploymentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "freelances")
@Getter
@Setter
public class Freelance extends BaseEntity {

    @NotBlank
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @NotBlank
    @Column(name = "last_name", nullable = false)
    private String lastName;

    @NotBlank
    @Email
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "address")
    private String address;

    @Column(name = "city")
    private String city;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EmploymentStatus status;

    @Column(name = "notice_period_in_days")
    private Integer noticePeriodInDays;

    @Column(name = "availability_date")
    private LocalDate availabilityDate;

    @Column(name = "reversion_rate")
    private Double reversionRate;

    @Column(name = "cv_file_path")
    private String cvFilePath;

    @Column(name = "password_hash")
    private String passwordHash;

    @OneToMany(mappedBy = "freelance", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Project> projects = new ArrayList<>();

    @OneToMany(mappedBy = "freelance", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Client> clients = new ArrayList<>();

    @OneToMany(mappedBy = "freelance", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Contact> contacts = new ArrayList<>();

    @OneToMany(mappedBy = "freelance", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Source> sources = new ArrayList<>();

    // Custom getters and setters for collections to prevent EI_EXPOSE_REP
    public List<Project> getProjects() {
        return projects != null ? new ArrayList<>(projects) : new ArrayList<>();
    }

    public void setProjects(final List<Project> projects) {
        this.projects = projects != null ? new ArrayList<>(projects) : new ArrayList<>();
    }

    public List<Client> getClients() {
        return clients != null ? new ArrayList<>(clients) : new ArrayList<>();
    }

    public void setClients(final List<Client> clients) {
        this.clients = clients != null ? new ArrayList<>(clients) : new ArrayList<>();
    }

    public List<Contact> getContacts() {
        return contacts != null ? new ArrayList<>(contacts) : new ArrayList<>();
    }

    public void setContacts(final List<Contact> contacts) {
        this.contacts = contacts != null ? new ArrayList<>(contacts) : new ArrayList<>();
    }

    public List<Source> getSources() {
        return sources != null ? new ArrayList<>(sources) : new ArrayList<>();
    }

    public void setSources(final List<Source> sources) {
        this.sources = sources != null ? new ArrayList<>(sources) : new ArrayList<>();
    }

    // Helper methods
    public String getFullName() {
        return firstName + " " + lastName;
    }
}
