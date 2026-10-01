package com.example.stub;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigModel {
    public int serverPort;
    public String apiPath;
    public String responseFilePath;
    public String imagePath;
    public StorageConfig storage;
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StorageConfig {
        public String bucket;
        public String region;
        public String endpoint;
        public String username;
        public String password;
        public String certificatePath;
        public String certificatePassword;
    }
}
