package com.pzhu.eduadmin;

import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.file.service.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("文件存储服务")
class FileStorageServiceTest {

    @TempDir Path tempDir;

    @Test
    @DisplayName("保存允许的文件并返回鉴权访问地址")
    void storeAllowedFile() throws Exception {
        FileStorageService service = new FileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "certificate.pdf", "application/pdf", "%PDF-1.7\n".getBytes());

        String url = service.store(file);

        assertThat(url).startsWith("/api/files/").endsWith(".pdf");
        assertThat(Files.list(tempDir)).hasSize(1);
        assertThat(service.resolveForRead(url.substring(url.lastIndexOf('/') + 1))).exists();
    }

    @Test
    @DisplayName("Rejects executable content disguised as an allowed extension")
    void rejectExecutableContentWithAllowedExtension() {
        FileStorageService service = new FileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "certificate.pdf", "application/pdf", new byte[]{'M', 'Z', 0, 0});

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件内容");
    }

    @Test
    @DisplayName("Rejects a Content-Type that does not match the extension")
    void rejectDisguisedContentType() {
        FileStorageService service = new FileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "certificate.pdf", "image/jpeg", "%PDF-1.7\n".getBytes());

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Content-Type");
    }

    @ParameterizedTest(name = "stores valid {0}")
    @MethodSource("allowedFileTypes")
    @DisplayName("Stores every currently allowed file type")
    void storeEveryCurrentlyAllowedFileType(
            String extension,
            String contentType,
            byte[] content
    ) throws Exception {
        FileStorageService service = new FileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample." + extension, contentType, content);

        String url = service.store(file);

        assertThat(url).endsWith("." + extension);
        assertThat(service.resolveForRead(url.substring(url.lastIndexOf('/') + 1))).exists();
    }

    @ParameterizedTest(name = "rejects corrupt {0}")
    @MethodSource("allowedExtensionsAndContentTypes")
    @DisplayName("Rejects corrupt content for every allowed file type")
    void rejectCorruptContentForEveryAllowedFileType(String extension, String contentType) {
        FileStorageService service = new FileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample." + extension,
                contentType,
                "not-a-valid-file".getBytes(StandardCharsets.US_ASCII)
        );

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件内容");
    }

    @Test
    @DisplayName("Rejects a generic ZIP archive disguised as DOCX")
    void rejectGenericZipDisguisedAsDocx() {
        FileStorageService service = new FileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                genericZip()
        );

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件内容");
    }

    @ParameterizedTest(name = "rejects mismatched Content-Type for {0}")
    @MethodSource("allowedFileTypes")
    @DisplayName("Rejects mismatched Content-Type for every allowed file type")
    void rejectMismatchedContentTypeForEveryAllowedFileType(
            String extension,
            String ignoredContentType,
            byte[] content
    ) {
        FileStorageService service = new FileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample." + extension, "application/octet-stream", content);

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Content-Type");
    }

    @Test
    @DisplayName("Rejects a missing Content-Type")
    void rejectMissingContentType() {
        FileStorageService service = new FileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "sample.png", null, png());

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Content-Type");
    }

    @Test
    @DisplayName("Rejects an empty allowed file")
    void rejectEmptyAllowedFile() {
        FileStorageService service = new FileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("拒绝可执行文件")
    void rejectExecutableFile() {
        FileStorageService service = new FileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "danger.exe", "application/octet-stream", "bad".getBytes());

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件类型");
    }

    @Test
    @DisplayName("拒绝目录穿越和不存在的文件")
    void rejectInvalidReadPath() {
        FileStorageService service = new FileStorageService(tempDir.toString());

        assertThatThrownBy(() -> service.resolveForRead("../secret.pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("路径");
        assertThatThrownBy(() -> service.resolveForRead("00000000000000000000000000000000.pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    private static Stream<Arguments> allowedFileTypes() {
        return Stream.of(
                Arguments.of("jpg", "image/jpeg", jpeg()),
                Arguments.of("jpeg", "image/jpeg", jpeg()),
                Arguments.of("png", "image/png", png()),
                Arguments.of("gif", "image/gif", "GIF89a-content".getBytes(StandardCharsets.US_ASCII)),
                Arguments.of("webp", "image/webp", "RIFF\u0004\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1)),
                Arguments.of("pdf", "application/pdf", "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII)),
                Arguments.of("doc", "application/msword", doc()),
                Arguments.of("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx())
        );
    }

    private static Stream<Arguments> allowedExtensionsAndContentTypes() {
        return Stream.of(
                Arguments.of("jpg", "image/jpeg"),
                Arguments.of("jpeg", "image/jpeg"),
                Arguments.of("png", "image/png"),
                Arguments.of("gif", "image/gif"),
                Arguments.of("webp", "image/webp"),
                Arguments.of("pdf", "application/pdf"),
                Arguments.of("doc", "application/msword"),
                Arguments.of("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        );
    }

    private static byte[] jpeg() {
        return new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 0};
    }

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    }

    private static byte[] doc() {
        return new byte[]{
                (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
                (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1
        };
    }

    private static byte[] docx() {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                addZipEntry(zip, "[Content_Types].xml", "<Types/>");
                addZipEntry(zip, "word/document.xml", "<document/>");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] genericZip() {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                addZipEntry(zip, "readme.txt", "not a Word document");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void addZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
