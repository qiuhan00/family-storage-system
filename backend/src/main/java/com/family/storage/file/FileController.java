package com.family.storage.file;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件端点（spec §5.6，/api/files）。需登录（SecurityConfig 默认拦截）。
 * POST multipart 字段名 {@code file} → {@code {url}}；
 * DELETE 接受相对路径（{@code {*name}} 捕获多段，如 202601/abc.jpg）。
 * 上传超过 Spring multipart 上限时统一回 400 FILE_TOO_LARGE。
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) {
        return Map.of("url", fileStorageService.store(file));
    }

    @DeleteMapping("/{*name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("name") String name) {
        fileStorageService.delete(name);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleTooLarge() {
        return Map.of("code", "FILE_TOO_LARGE", "message", "文件大小不能超过 5MB");
    }
}