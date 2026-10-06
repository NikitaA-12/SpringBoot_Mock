package com.example.stub;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
@RestController
@RequestMapping("${app.api.path}")
public class ImageController {
    private final StubService stubService;
    public ImageController(StubService stubService) {
        this.stubService = stubService;
    }
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleStubRequest(
            @RequestBody(required = false) Map<String, Object> requestBody
    ) {
        // Логируем входные данные
        System.out.println("Incoming request body: " + requestBody);
        // Проверяем, есть ли fileGuid во входном запросе
        if (requestBody != null && requestBody.containsKey("fileGuid")) {
            String guid = (String) requestBody.get("fileGuid");
            System.out.println("Found fileGuid in request: " + guid);
            String jsonResponse = "{\"data\": [{\"fileGuid\": \"" 
                    + guid + "\"}]}";
            return ResponseEntity.ok(jsonResponse);
        }
        // Если нет, выполняем стандартную логику
        stubService.uploadImage();
        return ResponseEntity.ok(stubService.getStubResponse());
    }
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }
}
