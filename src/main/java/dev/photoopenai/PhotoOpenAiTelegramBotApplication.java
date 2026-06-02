package dev.photoopenai;

import dev.photoopenai.config.OpenAiProperties;
import dev.photoopenai.config.TelegramProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({TelegramProperties.class, OpenAiProperties.class})
public class PhotoOpenAiTelegramBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(PhotoOpenAiTelegramBotApplication.class, args);
    }
}
