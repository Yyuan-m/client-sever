package com.car.customer.module.upload.service;

import com.car.customer.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class UploadService {

    @Value("${upload.path}")
    private String uploadPath;

    /** 图片扩展名白名单 */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg");
    /** 最大文件大小 10MB */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    @PostConstruct
    public void init() {
        File dir = new File(uploadPath);
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn("上传目录创建失败: {}", uploadPath);
        }
    }

    /**
     * 上传文件，返回 {url: "/uploads/yyyyMM/xxx.jpg"}
     * 只返回相对路径，由前端根据图片来源拼接对应服务的 baseURL（8088 或 8089）。
     * 使用 file.getBytes() + FileOutputStream 写入，规避 Spring transferTo 将相对路径
     * 解析到 tomcat work 目录的问题。
     */
    public Map<String, Object> upload(MultipartFile file) {
        validateFile(file);

        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            throw new BusinessException("文件名非法");
        }
        String ext = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf(".") + 1)
                : "jpg";

        // 按年月分目录
        String monthDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        File destDir = new File(uploadPath, monthDir);
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new BusinessException("创建目录失败: " + destDir.getAbsolutePath());
        }

        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        File dest = new File(destDir, fileName);
        try {
            byte[] bytes = file.getBytes();
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                fos.write(bytes);
            }
        } catch (IOException e) {
            log.error("上传文件失败: class={}, msg={}, path={}, dirExists={}, dirWritable={}",
                    e.getClass().getName(), e.getMessage(), dest.getAbsolutePath(),
                    destDir.exists(), destDir.canWrite(), e);
            throw new BusinessException("上传文件失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        // 只返回相对路径，前端负责拼接完整 URL
        Map<String, Object> result = new HashMap<>();
        result.put("url", "/uploads/" + monthDir + "/" + fileName);
        return result;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小不能超过10MB");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new BusinessException("文件名不能为空");
        }
        String ext = originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf(".") + 1)
                : "";
        if (ext.isEmpty() || !IMAGE_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new BusinessException("不支持的文件类型，仅支持: " + String.join(", ", IMAGE_EXTENSIONS));
        }
    }
}
