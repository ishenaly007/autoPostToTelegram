package com.example.autoposttotelegram.controller;

import com.example.autoposttotelegram.model.Channel;
import com.example.autoposttotelegram.model.User;
import com.example.autoposttotelegram.repository.ChannelRepository;
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
@RequestMapping("/api/channels")
@CrossOrigin(origins = "*")
@Tag(name = "Channels", description = "API для управления каналами Telegram")
public class ChannelController {

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private UserRepository userRepository;

    @Operation(summary = "Получить канал пользователя", description = "Возвращает канал, связанный с пользователем, или null, если канала нет.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Канал найден или null")
    })
    @GetMapping("/{userId}")
    public ResponseEntity<?> getChannel(@PathVariable Long userId) {
        Optional<Channel> channel = channelRepository.findByUserId(userId);
        return channel.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(null));
    }

    @Operation(summary = "Добавить канал", description = "Добавляет новый канал для пользователя, заменяя существующий, если он есть.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Канал успешно добавлен"),
            @ApiResponse(responseCode = "400", description = "Пользователь не найден")
    })
    @PostMapping("/{userId}")
    public ResponseEntity<?> addChannel(@PathVariable Long userId, @RequestBody ChannelRequest channelRequest) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) {
            return ResponseEntity.badRequest().body("Пользователь не найден");
        }

        Optional<Channel> existingChannel = channelRepository.findByUserId(userId);
        existingChannel.ifPresent(channelRepository::delete);

        Channel channel = new Channel();
        channel.setChannelId(channelRequest.getChannelId());
        channel.setUser(user.get());
        channelRepository.save(channel);
        return ResponseEntity.ok(channel);
    }

    @Operation(summary = "Удалить канал", description = "Удаляет канал пользователя, если он существует.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Канал успешно удалён"),
            @ApiResponse(responseCode = "400", description = "Канал не найден")
    })
    @DeleteMapping("/{userId}")
    public ResponseEntity<?> deleteChannel(@PathVariable Long userId) {
        Optional<Channel> channel = channelRepository.findByUserId(userId);
        if (channel.isPresent()) {
            channelRepository.delete(channel.get());
            return ResponseEntity.ok("Канал удалён");
        } else {
            return ResponseEntity.badRequest().body("Канал не найден");
        }
    }
}

class ChannelRequest {
    private String channelId;

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }
}