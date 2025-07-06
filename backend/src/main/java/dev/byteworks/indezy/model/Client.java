package dev.byteworks.indezy.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clients")
@Getter
@Setter
public class Client extends BaseEntity {

    @NotBlank
    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "address")
    private String address;

    @NotBlank
    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "domain")
    private String domain;

    @NotNull
    @Column(name = "is_final", nullable = false)
    private Boolean isFinal;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelance_id", nullable = false)
    private Freelance freelance;

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Project> projects = new ArrayList<>();

    @OneToMany(mappedBy = "middleman", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Project> middlemanProjects = new ArrayList<>();

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Contact> contacts = new ArrayList<>();

    // Custom getters and setters for collections to prevent EI_EXPOSE_REP
    public List<Project> getProjects() {
        return projects != null ? new ArrayList<>(projects) : new ArrayList<>();
    }

    public void setProjects(final List<Project> projects) {
        this.projects = projects != null ? new ArrayList<>(projects) : new ArrayList<>();
    }

    public List<Project> getMiddlemanProjects() {
        return middlemanProjects != null ? new ArrayList<>(middlemanProjects) : new ArrayList<>();
    }

    public void setMiddlemanProjects(final List<Project> middlemanProjects) {
        this.middlemanProjects = middlemanProjects != null ? new ArrayList<>(middlemanProjects) : new ArrayList<>();
    }

    public List<Contact> getContacts() {
        return contacts != null ? new ArrayList<>(contacts) : new ArrayList<>();
    }

    public void setContacts(final List<Contact> contacts) {
        this.contacts = contacts != null ? new ArrayList<>(contacts) : new ArrayList<>();
    }
}
