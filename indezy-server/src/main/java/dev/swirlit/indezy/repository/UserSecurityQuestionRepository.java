package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.UserSecurityQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserSecurityQuestionRepository extends JpaRepository<UserSecurityQuestion, Long> {

    /**
     * Find all security questions for a user
     */
    List<UserSecurityQuestion> findByUserId(Long userId);

    /**
     * Delete all security questions for a user
     */
    void deleteByUserId(Long userId);
}
