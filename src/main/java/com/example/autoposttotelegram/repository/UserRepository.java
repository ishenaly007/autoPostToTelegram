package com.example.autoposttotelegram.repository;

import com.example.autoposttotelegram.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByTelegramId(Long telegramId);
    Optional<User> findByTelegramIdAndPassword(Long telegramId, String password);
}