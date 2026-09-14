package com.coupleoutfit.storage.infrastructure;

import com.coupleoutfit.storage.application.SourceAssetStorage;
import com.coupleoutfit.source.application.SourceException;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.io.IOException;

@RestController
@Profile("(local | test) & !staging & !production")
public class LocalUploadController {
    private final SourceAssetStorage storage;
    public LocalUploadController(SourceAssetStorage storage) { this.storage=storage; }
    private LocalSourceAssetStorageAdapter local() {
        if(storage instanceof LocalSourceAssetStorageAdapter adapter) return adapter;
        throw new SourceException("SOURCE_STORAGE_UNAVAILABLE");
    }
    @PutMapping("/internal/local-upload") public ResponseEntity<Void> upload(@RequestParam String token,HttpServletRequest request) throws IOException {
        local().upload(token,request.getContentType(),request.getInputStream());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @GetMapping("/internal/local-read") public void read(@RequestParam String token,HttpServletResponse response) throws IOException {
        var metadata=local().readMetadata(token);
        response.setContentType(metadata.mediaType()); response.setContentLengthLong(metadata.byteSize());
        response.setHeader("Cache-Control","no-store"); response.setHeader("Content-Disposition","attachment");
        try(var input=local().readAuthorized(token)) { input.transferTo(response.getOutputStream()); }
    }
}
