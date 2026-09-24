package com.family.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.family.storage.common.ApiException;
import com.family.storage.file.FileStorageService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 图片上传/删除单元测试（Task 7，spec §5.6）：
 * 类型白名单、大小上限、上传 URL 格式、目录穿越防护与删除；
 * code-review M5：新增文件头魔数校验（扩展名/Content-Type 之外的内容验真）。
 */
class FileStorageServiceTest {
    @TempDir
    Path tempDir;

    private static final Pattern URL_PATTERN =
            Pattern.compile("^/uploads/\\d{6}/[0-9a-fA-F-]{36}\\.(jpg|jpeg|png|webp)$");

    /** 合法 PNG 文件头（8 字节魔数 + 若干数据字节）。 */
    private static final byte[] VALID_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    /** 合法 JPEG 文件头（FF D8 FF）。 */
    private static final byte[] VALID_JPEG =
            {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 1, 2};
    /** 合法 WebP 文件头（RIFF....WEBP）。 */
    private static final byte[] VALID_WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 1, 2};
    /** 伪装成图片的 HTML 内容（M5 应拒绝）。 */
    private static final byte[] HTML_POLYGLOT =
            "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

    @Test
    void nonImageTypeRejected() {
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        var file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        ApiException ex = assertThrows(ApiException.class, () -> svc.store(file));
        assertEquals("INVALID_FILE_TYPE", ex.getCode());
    }

    @Test
    void imageContentTypeWithNonImageExtensionRejected() {
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        var file = new MockMultipartFile("file", "a.bmp", "image/bmp", new byte[] {1, 2, 3});
        ApiException ex = assertThrows(ApiException.class, () -> svc.store(file));
        assertEquals("INVALID_FILE_TYPE", ex.getCode());
    }

    @Test
    void nonImageContentTypeRejected() {
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        var file = new MockMultipartFile("file", "a.png", "application/octet-stream", new byte[] {1, 2, 3});
        ApiException ex = assertThrows(ApiException.class, () -> svc.store(file));
        assertEquals("INVALID_FILE_TYPE", ex.getCode());
    }

    @Test
    void oversizedFileRejected() {
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        byte[] big = new byte[6 * 1024 * 1024];
        var file = new MockMultipartFile("file", "a.png", "image/png", big);
        ApiException ex = assertThrows(ApiException.class, () -> svc.store(file));
        assertEquals("FILE_TOO_LARGE", ex.getCode());
    }

    @Test
    void emptyFileRejected() {
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        var file = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        assertThrows(ApiException.class, () -> svc.store(file));
    }

    // ---- M5：魔数校验（内容验真） ----

    @Test
    void htmlDisguisedAsJpegRejectedByMagic() {
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        var file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", HTML_POLYGLOT);
        ApiException ex = assertThrows(ApiException.class, () -> svc.store(file));
        assertEquals("INVALID_FILE_TYPE", ex.getCode());
    }

    @Test
    void htmlDisguisedAsPngRejectedByMagic() {
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        var file = new MockMultipartFile("file", "photo.png", "image/png", HTML_POLYGLOT);
        ApiException ex = assertThrows(ApiException.class, () -> svc.store(file));
        assertEquals("INVALID_FILE_TYPE", ex.getCode());
    }

    @Test
    void blocksMismatchedMagicBetweenFormats() {
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        // 扩展名/Content-Type 声明为 png，内容却是合法 JPEG：应拒绝
        var file = new MockMultipartFile("file", "sneaky.png", "image/png", VALID_JPEG);
        ApiException ex = assertThrows(ApiException.class, () -> svc.store(file));
        assertEquals("INVALID_FILE_TYPE", ex.getCode());
    }

    @Test
    void validJpegAccepted() {
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        var file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", VALID_JPEG);
        String url = svc.store(file);
        assertTrue(URL_PATTERN.matcher(url).matches(), url);
    }

    @Test
    void validPngAccepted() {
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        var file = new MockMultipartFile("file", "photo.png", "image/png", VALID_PNG);
        String url = svc.store(file);
        assertTrue(URL_PATTERN.matcher(url).matches(), url);
    }

    @Test
    void validWebpAccepted() {
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        var file = new MockMultipartFile("file", "photo.webp", "image/webp", VALID_WEBP);
        String url = svc.store(file);
        assertTrue(URL_PATTERN.matcher(url).matches(), url);
    }

    @Test
    void storeReturnsUrlAndWritesFile() throws Exception {
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        var file = new MockMultipartFile("file", "photo.png", "image/png", VALID_PNG);
        String url = svc.store(file);
        assertTrue(URL_PATTERN.matcher(url).matches(), "url 格式应为 /uploads/{yyyyMM}/{uuid}.{ext}: " + url);
        String relative = url.substring("/uploads/".length());
        assertTrue(Files.exists(tempDir.resolve(relative)), "上传文件应落盘: " + relative);
    }

    @Test
    void storeSanitizesPathLikeOriginalFilename() {
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        var file = new MockMultipartFile("file", "../../evil.png", "image/png", VALID_PNG);
        String url = svc.store(file); // 不抛异常：原始文件名仅取 basename 校验，落盘名始终为 UUID
        assertTrue(URL_PATTERN.matcher(url).matches(), "落盘名必须为 UUID: " + url);
    }

    @Test
    void deleteRemovesStoredFile() throws Exception {
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        var file = new MockMultipartFile("file", "photo.png", "image/png", VALID_PNG);
        String url = svc.store(file);
        String relative = url.substring("/uploads/".length());
        assertTrue(Files.exists(tempDir.resolve(relative)));

        svc.delete(relative);
        assertFalse(Files.exists(tempDir.resolve(relative)), "删除后文件不应存在");
    }

    @Test
    void deleteRejectsTraversalPath() {
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        ApiException ex = assertThrows(ApiException.class,
                () -> svc.delete("../../outside.txt"));
        assertEquals("INVALID_FILE_PATH", ex.getCode());
    }

    @Test
    void deleteWithLeadingSlashStillDeletesStoredFile() throws Exception {
        // D1 回归：@PathVariable {*name} 可能带前导斜杠（如 /202601/abc.png），必须仍能删除
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        var file = new MockMultipartFile("file", "photo.png", "image/png", VALID_PNG);
        String url = svc.store(file);
        String relative = url.substring("/uploads/".length());
        assertTrue(Files.exists(tempDir.resolve(relative)));

        svc.delete("/" + relative);
        assertFalse(Files.exists(tempDir.resolve(relative)), "带前导斜杠的删除应生效");
    }

    @Test
    void deleteWithBackslashNotAllowedToEscapeBase() {
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        // Windows 下反斜杠剥除后重解析，穿越类输入仍必须被拒绝
        ApiException ex = assertThrows(ApiException.class,
                () -> svc.delete("\\..\\..\\outside.txt"));
        assertEquals("INVALID_FILE_PATH", ex.getCode());
    }

    @Test
    void deleteOutsideBaseIsRejected() throws Exception {
        Path outside = Files.writeString(tempDir.resolveSibling("outside.txt"), "secret");
        var svc = new FileStorageService(tempDir.toString(), "/uploads");
        assertThrows(ApiException.class, () -> svc.delete("../outside.txt"));
        assertTrue(Files.exists(outside), "目录穿越不应删除 uploadDir 之外的文件");
    }
}