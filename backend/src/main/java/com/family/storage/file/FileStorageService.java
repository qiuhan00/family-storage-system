package com.family.storage.file;

import com.family.storage.common.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片文件存储（spec §5.6 / Task 7）：
 * 上传写入 {@code {uploadDir}/{yyyyMM}/{uuid}.{ext}}，返回 /uploads 前缀 URL；
 * 扩大扩展名与 Content-Type 白名单、5MB 大小上限；删除时做路径规范化，
 * 以 uploadDir 为前缀校验，防止目录穿越。启动时创建上传根目录。
 */
@Service
public class FileStorageService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final Path uploadDir;
    private final String urlPrefix;

    public FileStorageService(@Value("${app.upload-dir:./uploads}") String uploadDir,
                              @Value("${app.url-prefix:/uploads}") String urlPrefix) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
        this.urlPrefix = urlPrefix;
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + uploadDir, e);
        }
    }

    /** 保存图片，返回可访问 URL，如 /uploads/202609/uuid.jpg。 */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "文件内容为空");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE",
                    "仅支持 jpg/jpeg/png/webp 图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE",
                    "文件 Content-Type 必须为图片类型");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "文件大小不能超过 5MB");
        }

        String month = LocalDate.now().format(MONTH);
        Path dir = uploadDir.resolve(month);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID() + "." + ext);
            file.transferTo(target);
            return urlPrefix + "/" + month + "/" + target.getFileName();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORE_FAILED", "文件保存失败");
        }
    }

    /** 删除已上传的相对路径文件（如 202601/abc.jpg）；路径穿越与目录删除一律拒绝。 */
    public void delete(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_PATH", "文件路径不能为空");
        }
        // @PathVariable 捕获的路径可能带前导斜杠（如 /202601/abc.png），剥除后仍是相对路径；
        // 反斜杠同样处理，避免 resolve 将之解释为文件系统根路径。
        String relative = fileName.replaceFirst("^[/\\\\]+", "");
        if (relative.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_PATH", "非法文件路径");
        }
        Path target = uploadDir.resolve(relative).normalize();
        if (!target.startsWith(uploadDir) || target.equals(uploadDir)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_PATH", "非法文件路径");
        }
        if (Files.isDirectory(target)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_PATH", "不允许删除目录");
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_DELETE_FAILED", "文件删除失败");
        }
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        // 含路径分隔符的原始文件名一律视为非法（上传名仅允许纯文件名）
        Path name = Path.of(filename).getFileName();
        if (name == null) {
            return null;
        }
        String simple = name.toString();
        int dot = simple.lastIndexOf('.');
        if (dot < 0 || dot == simple.length() - 1) {
            return null;
        }
        return simple.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}