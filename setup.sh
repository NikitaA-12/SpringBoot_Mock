#!/bin/bash
# Скрипт создает структуру проекта Spring Boot
# Использование: chmod +x setup.sh && ./setup.sh
PROJECT_NAME="stub"
BASE_PATH="src/main/java/com/example/stub"
echo "Создание структуры папок..."
mkdir -p "$BASE_PATH/controller"
mkdir -p "$BASE_PATH/service"
mkdir -p src/main/resources/META-INF/spring
echo "Создание pom.xml..."
cat <<EOF > pom.xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>$PROJECT_NAME</artifactId>
    <version>1.0.0</version>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.1.0</version>
    </parent>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>s3</artifactId>
            <version>2.20.100</version>
        </dependency>
    </dependencies>
</project>
EOF
echo "Создание Java классов (заготовки)..."
# StubApplication.java
cat <<EOF > "$BASE_PATH/StubApplication.java"
package com.example.stub;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class StubApplication {
    public static void main(String[] args) {
        SpringApplication.run(StubApplication.class, args);
    }
}
EOF
# ConfigLoader.java
cat <<EOF > "$BASE_PATH/ConfigLoader.java"
package com.example.stub;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
public class ConfigLoader 
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        // TODO: Добавить логику загрузки config.json
    }
}
EOF
# ConfigService.java
cat <<EOF > "$BASE_PATH/ConfigService.java"
package com.example.stub;
import org.springframework.stereotype.Service;
@Service
public class ConfigService {
    // TODO: Добавить поля и методы для доступа к конфигурации
}
EOF
# StubService.java
cat <<EOF > "$BASE_PATH/service/StubService.java"
package com.example.stub.service;
import com.example.stub.ConfigService;
import org.springframework.stereotype.Service;
@Service
public class StubService {
    public StubService(ConfigService configService) {
        // TODO: Инициализация S3 клиента
    }
    // TODO: Добавить методы загрузки и чтения
}
EOF
# ImageController.java
cat <<EOF > "$BASE_PATH/controller/ImageController.java"
package com.example.stub.controller;
import com.example.stub.ConfigService;
import com.example.stub.service.StubService;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class ImageController {
    public ImageController(StubService stubService, ConfigService configService) {
        // TODO: Инициализация контроллера
    }
    // TODO: Добавить эндпоинты
}
EOF
echo "Создание файлов ресурсов..."
# application.properties
cat <<EOF > src/main/resources/application.properties
server.port=8080
spring.main.web-application-type=servlet
EOF
# META-INF initializer config
cat <<EOF > src/main/resources/META-INF/spring/\
org.springframework.context.ApplicationContextInitializer.imports
com.example.stub.ConfigLoader
EOF
# Внешний config.json (шаблон)
cat <<EOF > config.json
{
  "serverPort": 8080,
  "apiPath": "/aaa/bbb",
  "responseFilePath": "./resp.json",
  "imagePath": "./image.jpeg",
  "storage": {
    "bucket": "img-bckt",
    "region": "us-east-1",
    "endpoint": "http://localhost:9000",
    "username": "YOUR_LOGIN",
    "password": "YOUR_PASSWORD"
  }
}
EOF
# Внешний resp.json (шаблон)
cat <<EOF > resp.json
{
  "data": [
    {
      "b64_json": "content_here"
    }
  ]
}
EOF
echo "Структура проекта создана успешно!"
echo "Не забудьте добавить файл image.jpeg в корень проекта."