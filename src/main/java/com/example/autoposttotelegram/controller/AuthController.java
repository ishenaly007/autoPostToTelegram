package com.example.autoposttotelegram.controller;

import com.example.autoposttotelegram.model.User;
import com.example.autoposttotelegram.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@Tag(name = "Authentication", description = "API для авторизации пользователей")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Operation(summary = "Авторизация пользователя", description = "Авторизует пользователя по Telegram ID и паролю, заданному через команду /password в боте.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешная авторизация"),
            @ApiResponse(responseCode = "400", description = "Неверный формат Telegram ID"),
            @ApiResponse(responseCode = "401", description = "Неверный Telegram ID или пароль")
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        String telegramUsername = loginRequest.getUsername().replace("@", "");
        Long telegramId;
        try {
            telegramId = Long.parseLong(telegramUsername);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body("Неверный формат Telegram ID. Используйте @<число>, например @123456789");
        }

        Optional<User> user = userRepository.findByTelegramIdAndPassword(telegramId, loginRequest.getPassword());
        if (user.isPresent()) {
            return ResponseEntity.ok(new LoginResponse(user.get().getId(), user.get().getTelegramId()));
        } else {
            return ResponseEntity.status(401).body("Неверный Telegram ID или пароль");
        }
    }
}

class LoginRequest {
    private String username;
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

class LoginResponse {
    private Long userId;
    private Long telegramId;

    public LoginResponse(Long userId, Long telegramId) {
        this.userId = userId;
        this.telegramId = telegramId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTelegramId() {
        return telegramId;
    }

    public void setTelegramId(Long telegramId) {
        this.telegramId = telegramId;
    }
}