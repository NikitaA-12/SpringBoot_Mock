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
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
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
            // Получаем файл сертификата
            File certFile = getCertificateFile(storage.certificatePath);
            System.out.println("Loading certificate from: " + certFile.getAbsolutePath());
            // Проверяем, что файл существует и доступен для чтения
            if (!certFile.exists()) {
                throw new RuntimeException("Certificate file does not exist: " + certFile.getAbsolutePath());
            }
            if (!certFile.canRead()) {
                throw new RuntimeException("Certificate file is not readable: " + certFile.getAbsolutePath());
            }
            // Создаем KeyStore с нашим сертификатом
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            // Добавляем сертификат в trustStore
            try (InputStream certStream = new FileInputStream(certFile)) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                Certificate cert = cf.generateCertificate(certStream);
                trustStore.setCertificateEntry("s3-cert", cert);
                System.out.println("Certificate loaded successfully");
            }
            // Создаем TrustManager на основе нашего trustStore
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm()
            );
            tmf.init(trustStore);
            // Создаем Apache HTTP Client с правильной конфигурацией TLS
            return ApacheHttpClient.builder()
                    .tlsTrustManagersProvider(() -> tmf.getTrustManagers())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to create secure HTTP client: " + e.getMessage(),
                    e
            );
        }
    }
    private File getCertificateFile(String certPath) {
        // Если путь абсолютный, используем его напрямую
        File certFile = new File(certPath);
        if (certFile.isAbsolute() && certFile.exists()) {
            return certFile;
        }
        // Определяем базовую директорию приложения
        String baseDir = getApplicationDirectory();
        System.out.println("Application directory: " + baseDir);
        // Очищаем путь от "./" в начале
        String cleanedPath = certPath.replaceFirst("^\\./", "");
        // Пробуем загрузить относительно директории приложения
        certFile = new File(baseDir, cleanedPath);
        System.out.println("Trying path: " + certFile.getAbsolutePath());
        if (!certFile.exists()) {
            // Пробуем загрузить относительно текущей рабочей директории
            certFile = new File(System.getProperty("user.dir"), cleanedPath);
            System.out.println("Trying path: " + certFile.getAbsolutePath());
        }
        if (!certFile.exists()) {
            // Пробуем загрузить из classpath
            try (InputStream is = getClass().getClassLoader()
                    .getResourceAsStream(cleanedPath)) {
                if (is != null) {
                    // Создаем временный файл из ресурса
                    certFile = File.createTempFile("cert", ".crt");
                    certFile.deleteOnExit();
                    Files.copy(is, certFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Loaded from classpath to: " + 
                            certFile.getAbsolutePath());
                }
            } catch (IOException e) {
                System.err.println("Warning: Could not load certificate from classpath");
            }
        }
        if (!certFile.exists()) {
            throw new RuntimeException(
                    "Certificate file not found. Looked at: " + 
                    certFile.getAbsolutePath()
            );
        }
        return certFile;
    }
    private String getApplicationDirectory() {
        try {
            // Получаем путь к JAR файлу
            java.net.URL location = StubService.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation();
            if (location != null) {
                File jarFile = new File(location.toURI());
                String jarDir = jarFile.getParent();
                System.out.println("JAR location: " + jarFile.getAbsolutePath());
                System.out.println("JAR directory: " + jarDir);
                return jarDir;
            }
        } catch (URISyntaxException e) {
            System.err.println("Warning: Could not resolve JAR path");
        }
        // Если не удалось получить путь из JAR, используем текущую директорию
        String userDir = System.getProperty("user.dir");
        System.out.println("Using user.dir: " + userDir);
        return userDir;
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
        File responseFile = new File(path);
// Если файл не найден по прямому пути, ищем в директории приложения
        if (!responseFile.exists()) {
            String baseDir = getApplicationDirectory();
            String cleanedPath = path.replaceFirst("^\\./", "");
            responseFile = new File(baseDir, cleanedPath);
        }
        try {
            byte[] bytes = Files.readAllBytes(responseFile.toPath());
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
        System.out.println("Uploading to bucket: " + bucket);
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
        // Формируем ключ объекта (без префикса)
        String objectKey = file.getName();
        String contentType = determineContentType(file.getName());
        System.out.println("Object key: " + objectKey);
        System.out.println("Content-Type: " + contentType);
        System.out.println("File size: " + file.length() + " bytes");
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .contentLength(file.length())
                .build();
        // Получаем ответ от S3
        PutObjectResponse response = s3Client.putObject(putRequest, file.toPath());
        // Извлекаем ETag из заголовков
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
        System.out.println("=== S3 Upload Response ===");
        System.out.println("Object Key: " + objectKey);
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
