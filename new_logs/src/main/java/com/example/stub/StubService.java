package com.example.stub;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.SdkHttpClient;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.ssl.SSLContextBuilder;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Collections;
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
        SdkHttpClient httpClient = createSecureHttpClient(storage);
        return S3Client.builder()
                .region(Region.of(storage.region))
                .endpointOverride(URI.create(storage.endpoint))
                .credentialsProvider(
                        StaticCredentialsProvider.create(credentials)
                )
                .httpClient(httpClient)
                .forcePathStyle(true)
                .build();
    }
    private SdkHttpClient createSecureHttpClient(
            ConfigModel.StorageConfig storage
    ) {
        try {
            KeyStore trustStore = KeyStore.getInstance(
                    KeyStore.getDefaultType()
            );
            trustStore.load(null, null);
            File certFile = getCertificateFile(storage.certificatePath);
            try (InputStream certStream = new FileInputStream(certFile)) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                Certificate cert = cf.generateCertificate(certStream);
                trustStore.setCertificateEntry("s3-cert", cert);
            }
            SSLContext sslContext = new SSLContextBuilder()
                    .loadTrustMaterial(trustStore, null)
                    .build();
            SSLConnectionSocketFactory socketFactory = 
                    new SSLConnectionSocketFactory(sslContext);
            return ApacheHttpClient.builder()
                    .socketFactory(socketFactory)
                    .build();
        } catch (KeyStoreException | NoSuchAlgorithmException | 
                 KeyManagementException | CertificateException | 
                 IOException e) {
            throw new RuntimeException(
                    "Failed to create secure HTTP client: " + e.getMessage(),
                    e
            );
        }
    }
    private File getCertificateFile(String certPath) {
        File certFile = new File(certPath);
        if (!certFile.exists()) {
            try {
                String jarPath = new File(
                        StubService.class.getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI()
                ).getParent();
                certFile = new File(jarPath, certPath);
            } catch (Exception e) {
                System.err.println("Warning: Could not resolve JAR path");
            }
        }
        if (!certFile.exists()) {
            throw new RuntimeException(
                    "Certificate file not found: " + certPath
            );
        }
        return certFile;
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
        // Получаем ответ от MinIO
        PutObjectResponse response = s3Client.putObject(putRequest, file.toPath());
        // Извлекаем ETag из заголовков (универсальный способ)
        List<String> etagList = response.sdkHttpResponse()
                .headers()
                .getOrDefault("etag", Collections.emptyList());
        String etag = etagList.isEmpty() ? "N/A" : etagList.get(0);
        // Извлекаем VersionId из заголовков
        List<String> versionList = response.sdkHttpResponse()
                .headers()
                .getOrDefault("x-amz-version-id", Collections.emptyList());
        String versionId = versionList.isEmpty() ? "N/A" : versionList.get(0);
        // Логируем данные ответа
        System.out.println("=== MinIO Upload Response ===");
        System.out.println("ETag: " + etag);
        System.out.println("VersionId: " + versionId);
        System.out.println("Status Code: " + 
                response.sdkHttpResponse().statusCode());
        System.out.println("=============================");
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
