package com.example.stub;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;
@Component
public class ConfigLoader {
    private final ConfigModel config;
    private final ObjectMapper objectMapper;
    public ConfigLoader() {
        this.objectMapper = new ObjectMapper();
        this.config = loadConfigFromFile();
    }
    private ConfigModel loadConfigFromFile() {
        File configFile = new File("./config.json");
        if (!configFile.exists()) {
            throw new IllegalStateException(
                    "Config file not found: ./config.json"
            );
        }
        try {
            return objectMapper.readValue(
                    configFile, 
                    ConfigModel.class
            );
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read config file", 
                    e
            );
        }
    }
    public ConfigModel getConfig() {
        return config;
    }
}
