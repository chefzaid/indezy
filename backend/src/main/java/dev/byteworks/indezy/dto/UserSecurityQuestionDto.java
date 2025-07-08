package dev.byteworks.indezy.dto;

import lombok.Data;

@Data
public class UserSecurityQuestionDto {
    private Long id;
    private String question;
    private String answer; // Only used for input, never returned
}
