package com.pzhu.eduadmin.modules.file.service;

import com.pzhu.eduadmin.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FileStorageService {

    private static final long MAX_SIZE = 10 * 1024 * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx");
    private static final Map<String, Set<String>> ALLOWED_CONTENT_TYPES = Map.of(
            "jpg", Set.of("image/jpeg"),
            "jpeg", Set.of("image/jpeg"),
            "png", Set.of("image/png"),
            "gif", Set.of("image/gif"),
            "webp", Set.of("image/webp"),
            "pdf", Set.of("application/pdf"),
            "doc", Set.of("application/msword"),
            "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    );
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
        validateContentType(file, extension);
        validateContentSignature(file, extension);

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

    private void validateContentType(MultipartFile file, String extension) {
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new BusinessException(400, "Content-Type 与文件扩展名不匹配");
        }
        String normalizedContentType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.getOrDefault(extension, Set.of()).contains(normalizedContentType)) {
            throw new BusinessException(400, "Content-Type 与文件扩展名不匹配");
        }
    }

    private void validateContentSignature(MultipartFile file, String extension) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            if (!hasExpectedSignature(header, extension)) {
                throw new BusinessException(400, "文件内容与文件类型不匹配");
            }
        } catch (IOException exception) {
            throw new BusinessException(500, "文件读取失败");
        }
        if ("docx".equals(extension) && !isWordDocumentPackage(file)) {
            throw new BusinessException(400, "文件内容与文件类型不匹配");
        }
    }

    private boolean isWordDocumentPackage(MultipartFile file) {
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            boolean hasContentTypes = false;
            boolean hasDocument = false;
            ZipEntry entry;
            int inspectedEntries = 0;
            while (inspectedEntries < 128 && (entry = zip.getNextEntry()) != null) {
                inspectedEntries++;
                if ("[Content_Types].xml".equals(entry.getName())) hasContentTypes = true;
                if ("word/document.xml".equals(entry.getName())) hasDocument = true;
                if (hasContentTypes && hasDocument) return true;
            }
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean hasExpectedSignature(byte[] header, String extension) {
        return switch (extension) {
            case "jpg", "jpeg" -> startsWith(header, new byte[]{
                    (byte) 0xff, (byte) 0xd8, (byte) 0xff
            });
            case "png" -> startsWith(header, new byte[]{
                    (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
            });
            case "gif" -> startsWith(header, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                    || startsWith(header, "GIF89a".getBytes(StandardCharsets.US_ASCII));
            case "webp" -> startsWith(header, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && matchesAt(header, 8, "WEBP".getBytes(StandardCharsets.US_ASCII));
            case "pdf" -> startsWith(header, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "doc" -> startsWith(header, new byte[]{
                    (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
                    (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1
            });
            case "docx" -> startsWith(header, new byte[]{0x50, 0x4b, 0x03, 0x04});
            default -> false;
        };
    }

    private boolean startsWith(byte[] content, byte[] prefix) {
        return matchesAt(content, 0, prefix);
    }

    private boolean matchesAt(byte[] content, int offset, byte[] expected) {
        if (content.length < offset + expected.length) return false;
        return Arrays.mismatch(content, offset, offset + expected.length,
                expected, 0, expected.length) == -1;
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
