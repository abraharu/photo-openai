package dev.photoopenai.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.telegram")
public record TelegramProperties(
        @NotBlank String token,
        boolean enabled,
        Duration longPollTimeout
) {
    public TelegramProperties {
        longPollTimeout = longPollTimeout == null ? Duration.ofSeconds(30) : longPollTimeout;
    }
}
