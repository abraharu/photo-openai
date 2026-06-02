package dev.photoopenai.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.openai")
public record OpenAiProperties(
        @NotBlank String apiKey,
        String imageModel,
        String imageSize,
        String inputFidelity
) {
    public OpenAiProperties {
        imageModel = blankToDefault(imageModel, "gpt-image-1.5");
        imageSize = blankToDefault(imageSize, "auto");
        inputFidelity = blankToDefault(inputFidelity, "high");
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
