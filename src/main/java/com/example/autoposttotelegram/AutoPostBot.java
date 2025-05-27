package com.example.autoposttotelegram;

import com.example.autoposttotelegram.model.Channel;
import com.example.autoposttotelegram.model.PostMessage;
import com.example.autoposttotelegram.model.User;
import com.example.autoposttotelegram.repository.ChannelRepository;
import com.example.autoposttotelegram.repository.PostMessageRepository;
import com.example.autoposttotelegram.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatAdministrators;
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Component
@EnableScheduling
public class AutoPostBot extends TelegramLongPollingBot {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostMessageRepository postMessageRepository;

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Override
    public String getBotUsername() {
        return "AutoPostBot";
    }

    @Override
    public String getBotToken() {
        return System.getenv("TELEGRAM_BOT_TOKEN");
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();
            String telegramUsername = update.getMessage().getFrom().getUserName();

            if (telegramUsername == null) {
                try {
                    sendMessage(chatId, "Ошибка: ваш Telegram username не установлен. Установите username в настройках Telegram.");
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
                return;
            }

            telegramUsername = telegramUsername.replace("@", "");

            try {
                if (messageText.startsWith("/start")) {
                    sendMessage(chatId, "Привет! Я бот для автоматизации постинга. Используйте команды:\n" +
                                        "/addchannel @ChannelName - добавить канал\n" +
                                        "/del - удалить канал\n" +
                                        "/createpost <текст> dd.MM.yyyy HH:mm - создать текстовый пост\n" +
                                        "/password <пароль> - установить пароль для входа на сайт\n" +
                                        "Для медиа: отправьте медиа с подписью <текст> dd.MM.yyyy HH:mm");
                } else if (messageText.startsWith("/addchannel")) {
                    handleAddChannel(update, telegramUsername);
                } else if (messageText.startsWith("/del")) {
                    handleDeleteChannel(update, telegramUsername);
                } else if (messageText.startsWith("/createpost")) {
                    handleCreatePost(update, telegramUsername);
                } else if (messageText.startsWith("/password")) {
                    handleSetPassword(update, telegramUsername);
                }
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        } else if (update.hasMessage()) {
            String telegramUsername = update.getMessage().getFrom().getUserName();
            if (telegramUsername == null) {
                try {
                    sendMessage(update.getMessage().getChatId(), "Ошибка: ваш Telegram username не установлен. Установите username в настройках Telegram.");
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
                return;
            }
            telegramUsername = telegramUsername.replace("@", "");
            if (update.getMessage().hasPhoto()) {
                handleMediaMessage(update, "photo", telegramUsername);
            } else if (update.getMessage().hasVideo()) {
                handleMediaMessage(update, "video", telegramUsername);
            } else if (update.getMessage().hasSticker()) {
                handleMediaMessage(update, "sticker", telegramUsername);
            } else if (update.getMessage().hasAudio()) {
                handleMediaMessage(update, "audio", telegramUsername);
            } else if (update.getMessage().hasDocument()) {
                handleMediaMessage(update, "document", telegramUsername);
            } else if (update.getMessage().hasAnimation()) {
                handleMediaMessage(update, "animation", telegramUsername);
            } else if (update.getMessage().hasVoice()) {
                handleMediaMessage(update, "voice", telegramUsername);
            } else if (update.getMessage().hasVideoNote()) {
                handleMediaMessage(update, "video_note", telegramUsername);
            }
        }
    }

    private void handleAddChannel(Update update, String telegramUsername) throws TelegramApiException {
        String[] parts = update.getMessage().getText().split(" ");
        Long chatId = update.getMessage().getChatId();
        Long telegramId = update.getMessage().getFrom().getId();

        if (parts.length < 2) {
            sendMessage(chatId, "Пожалуйста, укажите ID канала. Формат: /addchannel @ChannelName");
            return;
        }

        String channelName = parts[1];

        List<ChatMember> admins = getChatAdministrators(channelName);
        boolean isAdmin = admins.stream()
                .anyMatch(admin -> admin.getUser().getId().equals(telegramId));

        if (!isAdmin) {
            sendMessage(chatId, "Вы должны быть администратором канала!");
            return;
        }

        User user = userRepository.findByTelegramUsername(telegramUsername)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setTelegramUsername(telegramUsername);
                    return userRepository.save(newUser);
                });

        Optional<Channel> existingChannel = channelRepository.findByUserId(user.getId());
        existingChannel.ifPresent(channelRepository::delete);

        Channel channel = new Channel();
        channel.setChannelId(channelName);
        channel.setUser(user);
        channelRepository.save(channel);
        sendMessage(chatId, "Канал " + channelName + " успешно добавлен! Предыдущий канал (если был) удалён.");
    }

    private void handleDeleteChannel(Update update, String telegramUsername) throws TelegramApiException {
        Long chatId = update.getMessage().getChatId();

        User user = userRepository.findByTelegramUsername(telegramUsername)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден!"));

        Optional<Channel> channel = channelRepository.findByUserId(user.getId());
        if (channel.isPresent()) {
            channelRepository.delete(channel.get());
            sendMessage(chatId, "Канал успешно удалён!");
        } else {
            sendMessage(chatId, "У вас нет зарегистрированного канала!");
        }
    }

    private void handleSetPassword(Update update, String telegramUsername) throws TelegramApiException {
        Long chatId = update.getMessage().getChatId();
        String[] parts = update.getMessage().getText().split(" ", 2);

        if (parts.length < 2) {
            sendMessage(chatId, "Пожалуйста, укажите пароль. Формат: /password <пароль>");
            return;
        }

        String password = parts[1];

        User user = userRepository.findByTelegramUsername(telegramUsername)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setTelegramUsername(telegramUsername);
                    return userRepository.save(newUser);
                });

        user.setPassword(password);
        userRepository.save(user);
        sendMessage(chatId, "Пароль успешно установлен! Используйте его для входа на сайт.");
    }

    private void handleCreatePost(Update update, String telegramUsername) throws TelegramApiException {
        Long chatId = update.getMessage().getChatId();
        String messageText = update.getMessage().getText();

        if (messageText.length() < 16) {
            sendMessage(chatId, "Формат: /createpost <текст> dd.MM.yyyy HH:mm");
            return;
        }

        String dateTimeStr = messageText.substring(messageText.length() - 16);
        String content = messageText.substring("/createpost".length(), messageText.length() - 16).trim();

        try {
            LocalDateTime publishTime = LocalDateTime.parse(dateTimeStr, formatter);

            User user = userRepository.findByTelegramUsername(telegramUsername)
                    .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден!"));

            Optional<Channel> channel = channelRepository.findByUserId(user.getId());
            if (channel.isEmpty()) {
                sendMessage(chatId, "У вас нет зарегистрированного канала! Добавьте канал с помощью /addchannel @ChannelName");
                return;
            }

            PostMessage post = new PostMessage();
            post.setChannel(channel.get());
            post.setContent(content);
            post.setPublishTime(publishTime);
            postMessageRepository.save(post);

            sendMessage(chatId, "Пост успешно создан и будет опубликован " + dateTimeStr);
        } catch (DateTimeParseException e) {
            sendMessage(chatId, "Неверный формат даты! Используйте: dd.MM.yyyy HH:mm в конце текста");
        }
    }

    private void handleMediaMessage(Update update, String mediaType, String telegramUsername) {
        Long chatId = update.getMessage().getChatId();
        String fileId;
        String caption = update.getMessage().getCaption() != null ? update.getMessage().getCaption() : "";

        if (caption.length() < 16) {
            try {
                sendMessage(chatId, "Подпись должна заканчиваться датой и временем в формате: dd.MM.yyyy HH:mm");
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            return;
        }

        String dateTimeStr = caption.substring(caption.length() - 16);
        String content = caption.substring(0, caption.length() - 16).trim();

        try {
            LocalDateTime publishTime = LocalDateTime.parse(dateTimeStr, formatter);

            User user = userRepository.findByTelegramUsername(telegramUsername)
                    .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден!"));

            Optional<Channel> channel = channelRepository.findByUserId(user.getId());
            if (channel.isEmpty()) {
                try {
                    sendMessage(chatId, "У вас нет зарегистрированного канала! Добавьте канал с помощью /addchannel @ChannelName");
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
                return;
            }

            switch (mediaType) {
                case "photo":
                    fileId = update.getMessage().getPhoto().get(update.getMessage().getPhoto().size() - 1).getFileId();
                    break;
                case "video":
                    fileId = update.getMessage().getVideo().getFileId();
                    break;
                case "sticker":
                    fileId = update.getMessage().getSticker().getFileId();
                    break;
                case "audio":
                    fileId = update.getMessage().getAudio().getFileId();
                    break;
                case "document":
                    fileId = update.getMessage().getDocument().getFileId();
                    break;
                case "animation":
                    fileId = update.getMessage().getAnimation().getFileId();
                    break;
                case "voice":
                    fileId = update.getMessage().getVoice().getFileId();
                    break;
                case "video_note":
                    fileId = update.getMessage().getVideoNote().getFileId();
                    break;
                default:
                    return;
            }

            PostMessage post = new PostMessage();
            post.setChannel(channel.get());
            post.setContent(content);
            post.setPublishTime(publishTime);
            post.setMediaId(fileId);
            post.setMediaType(mediaType);
            postMessageRepository.save(post);

            try {
                sendMessage(chatId, "Медиа-пост (" + mediaType + ") успешно создан и будет опубликован " + dateTimeStr);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        } catch (DateTimeParseException e) {
            try {
                sendMessage(chatId, "Неверный формат даты! Подпись должна заканчиваться: dd.MM.yyyy HH:mm");
            } catch (TelegramApiException e1) {
                e1.printStackTrace();
            }
        }
    }

    @Scheduled(fixedRate = 60000)
    public void publishScheduledPosts() {
        LocalDateTime now = LocalDateTime.now();
        List<PostMessage> posts = postMessageRepository.findByPublishTimeBeforeAndPublishedFalse(now);

        for (PostMessage post : posts) {
            try {
                if (post.getMediaId() != null) {
                    switch (post.getMediaType()) {
                        case "photo":
                            SendPhoto sendPhoto = new SendPhoto();
                            sendPhoto.setChatId(post.getChannel().getChannelId());
                            sendPhoto.setPhoto(new InputFile(post.getMediaId()));
                            sendPhoto.setCaption(post.getContent());
                            execute(sendPhoto);
                            break;
                        case "video":
                            SendVideo sendVideo = new SendVideo();
                            sendVideo.setChatId(post.getChannel().getChannelId());
                            sendVideo.setVideo(new InputFile(post.getMediaId()));
                            sendVideo.setCaption(post.getContent());
                            execute(sendVideo);
                            break;
                        case "sticker":
                            SendSticker sendSticker = new SendSticker();
                            sendSticker.setChatId(post.getChannel().getChannelId());
                            sendSticker.setSticker(new InputFile(post.getMediaId()));
                            execute(sendSticker);
                            break;
                        case "audio":
                            SendAudio sendAudio = new SendAudio();
                            sendAudio.setChatId(post.getChannel().getChannelId());
                            sendAudio.setAudio(new InputFile(post.getMediaId()));
                            sendAudio.setCaption(post.getContent());
                            execute(sendAudio);
                            break;
                        case "document":
                            SendDocument sendDocument = new SendDocument();
                            sendDocument.setChatId(post.getChannel().getChannelId());
                            sendDocument.setDocument(new InputFile(post.getMediaId()));
                            sendDocument.setCaption(post.getContent());
                            execute(sendDocument);
                            break;
                        case "animation":
                            SendAnimation sendAnimation = new SendAnimation();
                            sendAnimation.setChatId(post.getChannel().getChannelId());
                            sendAnimation.setAnimation(new InputFile(post.getMediaId()));
                            sendAnimation.setCaption(post.getContent());
                            execute(sendAnimation);
                            break;
                        case "voice":
                            SendVoice sendVoice = new SendVoice();
                            sendVoice.setChatId(post.getChannel().getChannelId());
                            sendVoice.setVoice(new InputFile(post.getMediaId()));
                            sendVoice.setCaption(post.getContent());
                            execute(sendVoice);
                            break;
                        case "video_note":
                            SendVideoNote sendVideoNote = new SendVideoNote();
                            sendVideoNote.setChatId(post.getChannel().getChannelId());
                            sendVideoNote.setVideoNote(new InputFile(post.getMediaId()));
                            execute(sendVideoNote);
                            break;
                    }
                } else {
                    SendMessage sendMessage = new SendMessage();
                    sendMessage.setChatId(post.getChannel().getChannelId());
                    sendMessage.setText(post.getContent());
                    execute(sendMessage);
                }
                post.setPublished(true);
                postMessageRepository.save(post);
            }  catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    private List<ChatMember> getChatAdministrators(String chatId) throws TelegramApiException {
        GetChatAdministrators getChatAdministrators = new GetChatAdministrators();
        getChatAdministrators.setChatId(chatId);
        return execute(getChatAdministrators);
    }

    private void sendMessage(Long chatId, String text) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        execute(message);
    }
}