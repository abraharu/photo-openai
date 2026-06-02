package dev.photoopenai.bot;

import dev.photoopenai.telegram.TelegramModels.PhotoSize;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PhotoSessionStore {
    private static final Duration TIME_TO_LIVE = Duration.ofMinutes(10);

    private final Clock clock;
    private final Map<Long, StoredPhoto> photos = new ConcurrentHashMap<>();

    public PhotoSessionStore() {
        this(Clock.systemUTC());
    }

    PhotoSessionStore(Clock clock) {
        this.clock = clock;
    }

    public Optional<StoredPhoto> saveLargestPhoto(long chatId, List<PhotoSize> photoSizes) {
        return photoSizes.stream()
                .max(Comparator.comparingInt(photo -> photo.width() * photo.height()))
                .map(photo -> {
                    StoredPhoto stored = new StoredPhoto(photo.fileId(), Instant.now(clock));
                    photos.put(chatId, stored);
                    return stored;
                });
    }

    public Optional<StoredPhoto> takeFresh(long chatId) {
        StoredPhoto stored = photos.get(chatId);
        if (stored == null) {
            return Optional.empty();
        }
        if (stored.savedAt().plus(TIME_TO_LIVE).isBefore(Instant.now(clock))) {
            photos.remove(chatId);
            return Optional.empty();
        }
        photos.remove(chatId);
        return Optional.of(stored);
    }

    public void clear(long chatId) {
        photos.remove(chatId);
    }

    public record StoredPhoto(String fileId, Instant savedAt) {
    }
}
