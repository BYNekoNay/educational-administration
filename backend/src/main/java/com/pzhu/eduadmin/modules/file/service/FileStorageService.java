package com.pzhu.eduadmin.modules.file.service;

import com.pzhu.eduadmin.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class FileStorageService {

    private static final long MAX_SIZE = 10 * 1024 * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx");
    private static final Pattern STORED_NAME_PATTERN = Pattern.compile(
            "^[0-9a-f]{32}\\.(jpg|jpeg|png|gif|webp|pdf|doc|docx)$");

    private final Path uploadRoot;

    public FileStorageService(@Value("${file.upload-dir:./uploads}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(400, "上传文件不能为空");
        if (file.getSize() > MAX_SIZE) throw new BusinessException(400, "文件大小不能超过10MB");

        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        extension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(400, "不支持的文件类型");
        }

        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = uploadRoot.resolve(storedName).normalize();
        if (!target.startsWith(uploadRoot)) throw new BusinessException(400, "非法文件路径");

        try {
            Files.createDirectories(uploadRoot);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException(500, "文件保存失败");
        }
        return "/api/files/" + storedName;
    }

    public Path resolveForRead(String fileName) {
        if (fileName == null || !STORED_NAME_PATTERN.matcher(fileName).matches()) {
            throw new BusinessException(400, "非法文件路径");
        }
        Path target = uploadRoot.resolve(fileName).normalize();
        if (!target.startsWith(uploadRoot) || !Files.isRegularFile(target)) {
            throw new BusinessException(404, "文件不存在");
        }
        return target;
    }
}
