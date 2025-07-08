package dev.byteworks.indezy.repository;

import dev.byteworks.indezy.model.UserSecurityQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
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
