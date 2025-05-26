package com.example.autoposttotelegram.controller;

import com.example.autoposttotelegram.model.Channel;
import com.example.autoposttotelegram.model.PostMessage;
import com.example.autoposttotelegram.repository.ChannelRepository;
import com.example.autoposttotelegram.repository.PostMessageRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/posts")
@CrossOrigin(origins = "*")
@Tag(name = "Posts", description = "API для управления постами")
public class PostController {

    @Autowired
    private PostMessageRepository postMessageRepository;

    @Autowired
    private ChannelRepository channelRepository;

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Operation(summary = "Получить посты пользователя", description = "Возвращает список неопубликованных постов для канала пользователя.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список постов или пустой список, если канала нет")
    })
    @GetMapping("/{userId}")
    public ResponseEntity<List<PostMessage>> getPosts(@PathVariable Long userId) {
        Optional<Channel> channel = channelRepository.findByUserId(userId);
        if (channel.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<PostMessage> posts = postMessageRepository.findByChannelIdAndPublishedFalse(channel.get().getId());
        return ResponseEntity.ok(posts);
    }

    @Operation(summary = "Создать пост", description = "Создаёт новый текстовый пост для канала пользователя.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Пост успешно создан"),
            @ApiResponse(responseCode = "400", description = "Канал не найден или неверный формат даты")
    })
    @PostMapping("/{userId}")
    public ResponseEntity<?> createPost(@PathVariable Long userId, @RequestBody PostRequest postRequest) {
        Optional<Channel> channel = channelRepository.findByUserId(userId);
        if (channel.isEmpty()) {
            return ResponseEntity.badRequest().body("Канал не найден");
        }

        try {
            LocalDateTime publishTime = LocalDateTime.parse(postRequest.getPublishTime(), formatter);

            PostMessage post = new PostMessage();
            post.setChannel(channel.get());
            post.setContent(postRequest.getContent());
            post.setPublishTime(publishTime);
            postMessageRepository.save(post);
            return ResponseEntity.ok(post);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body("Неверный формат даты! Используйте: dd.MM.yyyy HH:mm");
        }
    }

    @Operation(summary = "Удалить пост", description = "Удаляет неопубликованный пост пользователя.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Пост успешно удалён"),
            @ApiResponse(responseCode = "400", description = "Пост не найден, уже опубликован или канал не найден")
    })
    @DeleteMapping("/{userId}/{postId}")
    public ResponseEntity<?> deletePost(@PathVariable Long userId, @PathVariable Long postId) {
        Optional<Channel> channel = channelRepository.findByUserId(userId);
        if (channel.isEmpty()) {
            return ResponseEntity.badRequest().body("Канал не найден");
        }

        Optional<PostMessage> post = postMessageRepository.findById(postId);
        if (post.isPresent() && post.get().getChannel().getId().equals(channel.get().getId()) && !post.get().isPublished()) {
            postMessageRepository.delete(post.get());
            return ResponseEntity.ok("Пост удалён");
        } else {
            return ResponseEntity.badRequest().body("Пост не найден или уже опубликован");
        }
    }
}

class PostRequest {
    private String content;
    private String publishTime;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(String publishTime) {
        this.publishTime = publishTime;
    }
}