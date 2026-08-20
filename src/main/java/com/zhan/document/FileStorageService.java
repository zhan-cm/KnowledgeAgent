package com.zhan.document;

import com.zhan.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "txt", "md", "markdown", "xlsx", "pptx");

    private final Path cwd;
    private final Path baseDir;

    public FileStorageService(@Value("${app.storage.dir}") String storageDir) {
        this.cwd = Paths.get("").toAbsolutePath().normalize();
        this.baseDir = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建文件存储目录: " + baseDir, e);
        }
    }

    public StoredFile store(MultipartFile file) {
        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename());
        String ext = StringUtils.getFilenameExtension(originalName);
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw BusinessException.badRequest("不支持的文件类型，仅支持 PDF/Word/TXT");
        }
        String storedName = UUID.randomUUID() + "." + ext.toLowerCase();
        Path target = baseDir.resolve(storedName);
        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new BusinessException(500, "文件保存失败: " + e.getMessage());
        }
        String relativePath = cwd.relativize(target).toString().replace('\\', '/');
        return new StoredFile(originalName, ext.toLowerCase(), relativePath);
    }

    public Path resolve(String relativePath) {
        Path path = cwd.resolve(relativePath).normalize();
        if (!path.startsWith(baseDir)) {
            throw BusinessException.badRequest("非法文件路径");
        }
        return path;
    }

    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException ignored) {
            // 删除失败不影响主流程
        }
    }

    public record StoredFile(String originalName, String fileType, String relativePath) {
    }
}
