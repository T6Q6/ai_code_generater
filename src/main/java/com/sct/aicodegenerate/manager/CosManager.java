package com.sct.aicodegenerate.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.sct.aicodegenerate.config.CosClientConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * COS 对象存储管理器
 */
@Component
@Slf4j
public class CosManager {
    @Resource
    private CosClientConfig cosClientConfig;
    @Resource
    private COSClient cosClient;

    /**
     * 上传对象
     */
    public PutObjectResult putObject(String key,File file){
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 上传文件到 COS 并返回访问 URL
     */
    public String uploadFile(String key,File file){
        // 上传文件
        PutObjectResult result = putObject(key, file);
        if (result != null){
            // 构建访问URL
            String url = String.format("%s%s",cosClientConfig.getHost(),key);
            log.info("上传文件成功，访问的 URL 为：{}",url);
            return url;
        }else {
            log.error("上传文件失败");
            return null;
        }
    }
}






