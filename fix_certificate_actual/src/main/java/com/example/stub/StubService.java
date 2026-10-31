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
    private final S3Client s3Client;
    public StubService(ConfigLoader configLoader) {
        this.configLoader = configLoader;
        this.s3Client = createS3Client();
        ensureBucketExists();
    }
    private S3Client createS3Client() {
        ConfigModel.StorageConfig storage = configLoader.getConfig().storage;
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                storage.username,
                storage.password
        );
        // Создаем HTTP клиент без проверки SSL
        SdkHttpClient httpClient = ApacheHttpClient.builder()
                .connectionTimeout(java.time.Duration.ofSeconds(5))
                .socketTimeout(java.time.Duration.ofSeconds(10))
                .build();
        return S3Client.builder()
                .region(Region.of(storage.region))
                .endpointOverride(URI.create(storage.endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .httpClient(httpClient)
                .forcePathStyle(true)
                .build();
    }
    private void ensureBucketExists() {
        String bucket = configLoader.getConfig().storage.bucket;
        try {
            // Проверяем существование бакета
            s3Client.headBucket(
                    HeadBucketRequest.builder().bucket(bucket).build()
            );
            System.out.println("Bucket exists: " + bucket);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                System.out.println("Bucket not found. Creating: " + bucket);
                try {
                    s3Client.createBucket(CreateBucketRequest.builder()
                            .bucket(bucket)
                            .build());
                    System.out.println("Bucket created: " + bucket);
                } catch (S3Exception createException) {
                    // Если бакет не может быть создан, просто логируем ошибку
                    // но продолжаем работу - возможно бакет уже существует
                    System.err.println("Warning: Could not create bucket: " + 
                        createException.getMessage());
                    System.err.println("Continuing with existing bucket or will try on upload...");
                }
            } else {
                // Для других ошибок, просто логируем и продолжаем
                // Это может быть проблема с endpoint или авторизацией
                System.err.println("Warning: Error checking bucket: " + e.getMessage());
                System.err.println("Will try to continue without bucket check...");
            }
        } catch (Exception e) {
            // Любые другие ошибки - продолжаем работу
            System.err.println("Warning: Unexpected error checking bucket: " + e.getMessage());
            System.err.println("Will try to continue without bucket check...");
        }
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
        ConfigModel cfg = configLoader.getConfig();
        String imagePath = cfg.imagePath;
        String bucket = cfg.storage.bucket;
        File file = new File(imagePath);
        System.out.println(
                "Searching image at: " + file.getAbsolutePath()
        );
        if (!file.exists()) {
            throw new IllegalStateException(
                    "Image file not found: " + file.getAbsolutePath()
            );
        }
        if (!file.canRead()) {
            throw new IllegalStateException(
                    "Image file is not readable: " + file.getAbsolutePath()
            );
        }
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
        try {
            s3Client.putObject(putRequest, file.toPath());
            System.out.println("Upload successful!");
        } catch (S3Exception e) {
            System.err.println("Upload failed: " + e.getMessage());
            System.err.println("Status code: " + e.statusCode());
            System.err.println("Error code: " + e.awsErrorDetails().errorCode());
            throw e;
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

