package com.example.stub;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.io.File;
import java.io.IOException;
@SpringBootApplication
public class StubApplication {
    public static void main(String[] args) {
        ConfigModel config = loadStartupConfig();
        // Приоритет: Переменная окружения SERVER_PORT > config.json
        if (System.getenv("SERVER_PORT") == null) {
            System.setProperty("server.port", 
                    String.valueOf(config.serverPort));
        }
        System.setProperty("app.api.path", config.apiPath);
        SpringApplication.run(StubApplication.class, args);
    }
    private static ConfigModel loadStartupConfig() {
        ObjectMapper mapper = new ObjectMapper();
        File configFile = new File("./config.json");
        if (!configFile.exists()) {
            throw new IllegalStateException(
                    "Config file not found: ./config.json"
            );
        }
        try {
            ConfigModel model = mapper.readValue(
                    configFile, 
                    ConfigModel.class
            );
            if (model.apiPath == null) {
                throw new IllegalStateException(
                        "Missing apiPath in config"
                );
            }
            return model;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read config", 
                    e
            );
        }
    }
}
