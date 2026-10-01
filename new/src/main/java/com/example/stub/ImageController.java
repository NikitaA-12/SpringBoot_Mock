package com.example.stub;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("${app.api.path}")
public class ImageController {
    private final StubService stubService;
    public ImageController(StubService stubService) {
        this.stubService = stubService;
    }
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleStubRequest() {
        stubService.uploadImage();
        return ResponseEntity.ok(stubService.getStubResponse());
    }
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }
}
