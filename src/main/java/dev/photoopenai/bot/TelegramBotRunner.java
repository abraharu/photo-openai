package dev.photoopenai.bot;

import dev.photoopenai.config.TelegramProperties;
import dev.photoopenai.openai.OpenAiImageClient;
import dev.photoopenai.telegram.TelegramClient;
import dev.photoopenai.telegram.TelegramModels.Message;
import dev.photoopenai.telegram.TelegramModels.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TelegramBotRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TelegramBotRunner.class);
    private static final String HELP = """
            PixelWish редактирует фото по твоему описанию.

            Как пользоваться:
            1. Отправь фото.
            2. В подписи к фото напиши, что изменить.

            Можно иначе:
            1. Отправь фото без подписи.
            2. Следующим сообщением отправь задачу.

            Примеры задач:
            Убери фон и сделай студийный свет.
            Сделай фото в стиле делового портрета.
            Замени фон на город ночью.

            Команды:
            /help - показать эту инструкцию
            /clear - забыть последнее фото
            """;
    private static final String WAITING_FOR_PROMPT = "Фото получил. Теперь отправь текстом, что нужно изменить.";
    private static final String WORKING = "Фото принято. Начал обработку, это может занять несколько минут. Результат пришлю сюда.";
    private static final String UPLOAD_PHOTO_ACTION = "upload_photo";

    private final TelegramProperties telegramProperties;
    private final TelegramClient telegramClient;
    private final OpenAiImageClient openAiImageClient;
    private final PhotoSessionStore photoSessionStore;
    private final AtomicLong offset = new AtomicLong(0);

    public TelegramBotRunner(
            TelegramProperties telegramProperties,
            TelegramClient telegramClient,
            OpenAiImageClient openAiImageClient,
            PhotoSessionStore photoSessionStore
    ) {
        this.telegramProperties = telegramProperties;
        this.telegramClient = telegramClient;
        this.openAiImageClient = openAiImageClient;
        this.photoSessionStore = photoSessionStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        pollOnce()
                .repeat()
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2)).maxBackoff(Duration.ofSeconds(30)))
                .subscribe(
                        ignored -> {
                        },
                        error -> log.error("Telegram polling stopped", error)
                );

        log.info("Telegram bot long polling started with timeout {}", telegramProperties.longPollTimeout());
    }

    private Mono<Void> pollOnce() {
        return Mono.defer(() -> telegramClient.getUpdates(offset.get())
                        .flatMap(updates -> processUpdates(updates).thenReturn(updates))
                        .doOnNext(this::advanceOffset)
                        .then())
                .onErrorResume(error -> {
                    log.warn("Telegram polling iteration failed: {}", error.getMessage());
                    return Mono.delay(Duration.ofSeconds(3)).then();
                });
    }

    private Mono<Void> processUpdates(List<Update> updates) {
        return Mono.when(updates.stream()
                .map(this::processUpdate)
                .toList());
    }

    private Mono<Void> processUpdate(Update update) {
        Message message = update.message();
        if (message == null || message.chat() == null) {
            return Mono.empty();
        }

        long chatId = message.chat().id();
        if (message.photo() != null && !message.photo().isEmpty()) {
            return handlePhotoMessage(chatId, message);
        }
        if (message.text() != null && !message.text().isBlank()) {
            return handleTextMessage(chatId, message.text());
        }
        return Mono.empty();
    }

    private Mono<Void> handlePhotoMessage(long chatId, Message message) {
        return photoSessionStore.saveLargestPhoto(chatId, message.photo())
                .map(photo -> {
                    String prompt = normalize(message.caption());
                    if (prompt.isBlank()) {
                        return telegramClient.sendMessage(chatId, WAITING_FOR_PROMPT);
                    }
                    return editAndReply(chatId, photo.fileId(), prompt);
                })
                .orElseGet(() -> telegramClient.sendMessage(chatId, "Не смог найти фото в сообщении."));
    }

    private Mono<Void> handleTextMessage(long chatId, String text) {
        String prompt = normalize(text);
        if (prompt.equals("/start") || prompt.equals("/help")) {
            return telegramClient.sendMessage(chatId, HELP);
        }
        if (prompt.equals("/clear")) {
            photoSessionStore.clear(chatId);
            return telegramClient.sendMessage(chatId, "Готово, последнее фото забыто.");
        }
        if (prompt.startsWith("/")) {
            return telegramClient.sendMessage(chatId, "Не знаю такую команду. Отправь /help, чтобы посмотреть инструкцию.");
        }

        return photoSessionStore.takeFresh(chatId)
                .map(photo -> editAndReply(chatId, photo.fileId(), prompt))
                .orElseGet(Mono::empty);
    }

    private Mono<Void> editAndReply(long chatId, String fileId, String prompt) {
        return telegramClient.sendMessage(chatId, WORKING)
                .then(withUploadProgress(chatId, telegramClient.getFile(fileId)
                        .flatMap(file -> telegramClient.downloadFile(file.filePath())
                                .flatMap(bytes -> openAiImageClient.editImage(bytes, contentTypeFrom(file.filePath()), prompt)))))
                .flatMap(result -> telegramClient.sendPhoto(chatId, result, "edited.png", "Готово"))
                .onErrorResume(error -> {
                    log.warn("Image edit failed for chat {}: {}", chatId, error.getMessage(), error);
                    return telegramClient.sendMessage(chatId, "Не получилось отредактировать фото: " + userSafeMessage(error));
                });
    }

    private <T> Mono<T> withUploadProgress(long chatId, Mono<T> work) {
        Mono<T> sharedWork = work.cache();
        Mono<Void> progress = Flux.interval(Duration.ZERO, Duration.ofSeconds(4))
                .flatMap(tick -> telegramClient.sendChatAction(chatId, UPLOAD_PHOTO_ACTION)
                        .onErrorResume(error -> {
                            log.debug("Could not send Telegram chat action for chat {}: {}", chatId, error.getMessage());
                            return Mono.empty();
                        }))
                .takeUntilOther(sharedWork.then())
                .then();

        return Mono.when(progress, sharedWork).then(sharedWork);
    }

    private void advanceOffset(List<Update> updates) {
        updates.stream()
                .mapToLong(Update::updateId)
                .max()
                .ifPresent(lastUpdateId -> offset.set(lastUpdateId + 1));
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    private String contentTypeFrom(String filePath) {
        String lower = filePath == null ? "" : filePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }

    private String userSafeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "проверь токены и попробуй еще раз.";
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
