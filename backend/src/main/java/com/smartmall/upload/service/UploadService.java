package com.smartmall.upload.service;

import com.smartmall.auth.AuthContext;
import com.smartmall.auth.CurrentUser;
import com.smartmall.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 本地磁盘图片上传。只允许卖家和管理员调用，返回可直接访问的相对 URL。
 * 生产环境建议换成对象存储，这里保持零外部依赖，方便毕设演示。
 */
@Slf4j
@Service
public class UploadService {

    /** 上传根目录，可通过 app.upload.dir 覆盖 */
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /** 单个文件大小上限（字节） */
    @Value("${app.upload.max-size:5242880}")
    private long maxSize;

    private static final List<String> ALLOWED_EXT = List.of("jpg", "jpeg", "png", "webp", "gif");

    public String saveImage(MultipartFile file) {
        CurrentUser user = AuthContext.require();
        if (!user.canManageProducts()) {
            throw new BizException(403, "只有卖家或管理员可以上传图片");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的图片");
        }
        if (file.getSize() > maxSize) {
            throw new BizException("图片不能超过 " + (maxSize / 1024 / 1024) + "MB");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException("只支持 jpg / png / webp / gif 格式");
        }
        // 按用户 + 日期分目录，避免单目录文件过多
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path targetDir = Paths.get(uploadDir, user.id(), datePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(filename);
            file.transferTo(target.toFile());
            String url = "/uploads/" + user.id() + "/" + datePath + "/" + filename;
            log.info("用户 {} 上传图片成功：{}", user.username(), url);
            return url;
        } catch (IOException e) {
            log.warn("图片上传失败: {}", e.getMessage());
            throw new BizException("图片保存失败，请重试");
        }
    }

    /** 从原文件名取扩展名，并做白名单校验 */
    private String extensionOf(String original) {
        if (!StringUtils.hasText(original)) {
            return "";
        }
        int index = original.lastIndexOf('.');
        if (index < 0 || index == original.length() - 1) {
            return "";
        }
        return original.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    public Path rootPath() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }
}