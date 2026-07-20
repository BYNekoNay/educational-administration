package com.pzhu.eduadmin;

import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.file.service.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

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
                "file", "certificate.pdf", "application/pdf", "pdf-data".getBytes());

        String url = service.store(file);

        assertThat(url).startsWith("/api/files/").endsWith(".pdf");
        assertThat(Files.list(tempDir)).hasSize(1);
        assertThat(service.resolveForRead(url.substring(url.lastIndexOf('/') + 1))).exists();
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
}
