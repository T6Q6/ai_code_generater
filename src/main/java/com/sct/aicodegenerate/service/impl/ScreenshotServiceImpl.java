package com.sct.aicodegenerate.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.sct.aicodegenerate.exception.ErrorCode;
import com.sct.aicodegenerate.exception.ThrowUtils;
import com.sct.aicodegenerate.manager.CosManager;
import com.sct.aicodegenerate.service.ScreenshotService;
import com.sct.aicodegenerate.util.WebScreenshotUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class ScreenshotServiceImpl implements ScreenshotService {

    @Resource
    private CosManager cosManager;

    /**
     * 生成网页截图并上传到 COS
     * @param webUrl
     * @return
     */
    @Override
    public String generateAndUploadScreenshot(String webUrl){
        ThrowUtils.throwIf(StrUtil.isBlank(webUrl), ErrorCode.PARAMS_ERROR,"网页URL不能为空");
        log.info("开始截图：{}", webUrl);
        // 1. 生成本地截图（压缩后的）
        String localScreenshotPath = WebScreenshotUtils.saveWebPageScreenshot(webUrl);
        ThrowUtils.throwIf(StrUtil.isBlank(localScreenshotPath),ErrorCode.OPERATION_ERROR,"本地截图生成失败");
        try {
            // 2. 上传到 COS
            String cosUrl = uploadScreenshotToCos(localScreenshotPath);
            ThrowUtils.throwIf(StrUtil.isBlank(cosUrl),ErrorCode.OPERATION_ERROR,"上传截图到 COS 失败");
            log.info("截图上传成功，访问的 URL 为：{}", cosUrl);
            return cosUrl;
        } finally {
            // 3. 清理本地文件
            cleanupLocalFile(localScreenshotPath);
        }
    }

    /**
     * 清理本地文件
     * @param localScreenshotPath
     */
    private void cleanupLocalFile(String localScreenshotPath) {
        File localFile = new File(localScreenshotPath);
        if (localFile.exists()){
            File parentDir = localFile.getParentFile();
            FileUtil.del(parentDir);
            log.info("清理本地文件成功：{}", parentDir.getAbsolutePath());
        }
    }

    /**
     * 上传截图到对象存储
     * @param localScreenshotPath
     * @return
     */
    private String uploadScreenshotToCos(String localScreenshotPath) {
        if (StrUtil.isBlank(localScreenshotPath)){
            return null;
        }
        File screenshotFile = new File(localScreenshotPath);
        if (!screenshotFile.exists()){
            log.error("本地截图文件不存在：{}", localScreenshotPath);
            return null;
        }
        // 生成 COS 对象键
        String fileName = UUID.randomUUID().toString().substring(0,8)+"_compressed.jpg";
        String cosKey = generateScreenshotKey(fileName);
        return cosManager.uploadFile(cosKey, screenshotFile);
    }

    /**
     * 生成截图的对象存储键
     * @param fileName
     * @return
     */
    private String generateScreenshotKey(String fileName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("/screenshots/%s/%s",datePath,fileName);
    }
}
