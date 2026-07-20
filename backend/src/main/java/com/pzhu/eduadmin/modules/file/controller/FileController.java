package com.pzhu.eduadmin.modules.file.controller;

import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.file.service.FileStorageService;
import com.pzhu.eduadmin.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping("/api/files/upload")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN", "TEACHER"})
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String url = fileStorageService.store(file);
        return Result.success(Map.of(
                "url", url,
                "originalName", file.getOriginalFilename() == null ? "" : file.getOriginalFilename()));
    }

    @GetMapping("/api/files/{fileName:.+}")
    public ResponseEntity<Resource> download(@PathVariable String fileName) throws IOException {
        Path path = fileStorageService.resolveForRead(fileName);
        String contentType = Files.probeContentType(path);
        MediaType mediaType = contentType == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(path));
    }
}
