package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A turn of the AI conversation about an opportunity: the freelancer's question or the AI's answer. */
@Entity
@Table(name = "project_chat_messages", indexes = @Index(name = "idx_project_chat_project", columnList = "project_id"))
@Getter
@Setter
public class ProjectChatMessage extends BaseEntity {

    public static final int TEXT_MAX = 8_000;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** The freelancer's turn; else the AI's. */
    @Column(name = "from_member", nullable = false)
    private boolean fromMember;

    @Column(name = "text", nullable = false, length = TEXT_MAX)
    private String text;
}
