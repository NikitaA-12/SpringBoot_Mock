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
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
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
        SdkHttpClient httpClient = createInsecureHttpClient();
        return S3Client.builder()
                .region(Region.of(storage.region))
                .endpointOverride(URI.create(storage.endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .httpClient(httpClient)
                .forcePathStyle(true)
                .build();
    }
    private SdkHttpClient createInsecureHttpClient() {
        try {
            // Создаем SSLContext, который доверяет всем сертификатам
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // Не проверяем
                    }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // Не проверяем
                    }
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Отключаем проверку хоста (hostname verification)
            System.setProperty("com.amazonaws.sdk.disableCertChecking", "true");
            return ApacheHttpClient.builder()
                    .tlsTrustManagersProvider(() -> trustAllCerts)
                    .build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(
                    "Failed to create insecure HTTP client: " + e.getMessage(),
                    e
            );
        }
    }
    private void ensureBucketExists() {
        String bucket = configLoader.getConfig().storage.bucket;
        try {
            s3Client.headBucket(
                    HeadBucketRequest.builder().bucket(bucket).build()
            );
            System.out.println("Bucket exists: " + bucket);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                System.out.println("Bucket not found. Creating: " + bucket);
                s3Client.createBucket(CreateBucketRequest.builder()
                        .bucket(bucket)
                        .build());
                System.out.println("Bucket created: " + bucket);
            } else {
                throw e;
            }
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
        s3Client.putObject(putRequest, file.toPath());
        System.out.println("Upload successful!");
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

