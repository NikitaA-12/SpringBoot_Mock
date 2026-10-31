package com.example.stub;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.SdkHttpClient;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
@Service
public class StubService {
    private final ConfigLoader configLoader;
    private S3Client s3Client;
    private boolean s3Enabled = true;
    public StubService(ConfigLoader configLoader) {
        this.configLoader = configLoader;
        try {
            this.s3Client = createS3Client();
            System.out.println("S3 client created successfully");
        } catch (Exception e) {
            System.err.println("Warning: Failed to create S3 client: " + e.getMessage());
            System.err.println("S3 will be disabled, using stub responses only");
            this.s3Client = null;
            this.s3Enabled = false;
        }
    }
    private S3Client createS3Client() {
        ConfigModel.StorageConfig storage = configLoader.getConfig().storage;
        // Проверяем наличие необходимых параметров
        if (storage.endpoint == null || storage.endpoint.isEmpty()) {
            throw new IllegalArgumentException("Storage endpoint is not configured");
        }
        if (storage.username == null || storage.password == null) {
            throw new IllegalArgumentException("Storage credentials are not configured");
        }
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                storage.username,
                storage.password
        );
        // Создаем HTTP клиент с таймаутами
        SdkHttpClient httpClient = ApacheHttpClient.builder()
                .connectionTimeout(java.time.Duration.ofSeconds(3))
                .socketTimeout(java.time.Duration.ofSeconds(5))
                .build();
        return S3Client.builder()
                .region(Region.of(storage.region != null ? storage.region : "us-east-1"))
                .endpointOverride(URI.create(storage.endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .httpClient(httpClient)
                .forcePathStyle(true)
                .build();
    }
    public String getStubResponse() {
        String path = configLoader.getConfig().responseFilePath;
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(path));
            return new String(bytes);
        } catch (IOException e) {
            System.err.println(
                    "Error reading response file: " + e.getMessage()
            );
            return "{\"error\": \"Response file not found\"}";
        }
    }
    public void uploadImage() {
        // Всегда логируем попытку загрузки
        System.out.println("=== Upload attempt ===");
        if (!s3Enabled || s3Client == null) {
            System.out.println("S3 is disabled, skipping upload");
            System.out.println("Returning stub response");
            return;
        }
        ConfigModel cfg = configLoader.getConfig();
        String imagePath = cfg.imagePath;
        String bucket = cfg.storage.bucket;
        // Проверяем наличие изображения
        File file = new File(imagePath);
        if (!file.exists() || !file.canRead()) {
            System.out.println("Warning: Image file not found or not readable: " + imagePath);
            System.out.println("Continuing with stub response");
            return;
        }
        try {
            // Проверяем существование бакета ensureBucketExists();
            String objectKey = file.getName();
            String contentType = determineContentType(file.getName());
            System.out.println("Uploading to bucket: " + bucket);
            System.out.println("Object key: " + objectKey);
            System.out.println("Content-Type: " + contentType);
            System.out.println("File size: " + file.length() + " bytes");
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(file.length())
                    .build();
            s3Client.putObject(putRequest, file.toPath());
            System.out.println("Upload successful!");
        } catch (S3Exception e) {
            System.err.println("S3 Error during upload: " + e.getMessage());
            System.err.println("Status code: " + e.statusCode());
            System.err.println("Will continue with stub response");
        } catch (Exception e) {
            System.err.println("Unexpected error during upload: " + e.getMessage());
            System.err.println("Will continue with stub response");
        }
        System.out.println("=== Upload attempt complete ===");
    }
    private void ensureBucketExists() {
        if (!s3Enabled || s3Client == null) {
            return;
        }
        String bucket = configLoader.getConfig().storage.bucket;
        try {
            s3Client.headBucket(
                    HeadBucketRequest.builder().bucket(bucket).build()
            );
            System.out.println("Bucket exists: " + bucket);
        } catch (S3Exception e) {
            // Бакет не существует или ошибка - просто логируем и продолжаем
            System.err.println("Warning: Could not check bucket: " + e.getMessage());
            System.err.println("Will try to upload anyway");
        } catch (Exception e) {
            System.err.println("Warning: Unexpected error checking bucket: " + e.getMessage());
            System.err.println("Will try to upload anyway");
        }
    }
    private String determineContentType(String fileName) {
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".gif")) {
            return "image/gif";
        } else if (fileName.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (fileName.endsWith(".webp")) {
            return "image/webp";
        } else if (fileName.endsWith(".bmp")) {
            return "image/bmp";
        } else {
            return "application/octet-stream";
        }
    }
}

