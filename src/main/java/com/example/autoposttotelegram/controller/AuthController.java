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

    @Operation(summary = "Авторизация пользователя", description = "Авторизует пользователя по Telegram username и паролю.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешная авторизация"),
            @ApiResponse(responseCode = "400", description = "Неверный формат Telegram username"),
            @ApiResponse(responseCode = "401", description = "Неверный Telegram username или пароль")
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        String telegramUsername = loginRequest.getUsername().replace("@", "");
        if (telegramUsername.isEmpty()) {
            return ResponseEntity.badRequest().body("Неверный формат Telegram username. Используйте @<username>, например @ExampleUser");
        }

        Optional<User> user = userRepository.findByTelegramUsernameAndPassword(telegramUsername, loginRequest.getPassword());
        if (user.isPresent()) {
            return ResponseEntity.ok(new LoginResponse(user.get().getId(), telegramUsername));
        } else {
            return ResponseEntity.status(401).body("Неверный Telegram username или пароль");
        }
    }
}

class LoginResponse {
    private Long userId;
    private String telegramUsername;

    public LoginResponse(Long userId, String telegramUsername) {
        this.userId = userId;
        this.telegramUsername = telegramUsername;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTelegramUsername() {
        return telegramUsername;
    }

    public void setTelegramUsername(String telegramUsername) {
        this.telegramUsername = telegramUsername;
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
