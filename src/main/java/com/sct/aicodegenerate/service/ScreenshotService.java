package com.sct.aicodegenerate.service;

public interface ScreenshotService {

    /**
     * 生成网页截图并上传到对象存储
     *
     * @param webUrl 网页 URL
     * @return 访问的 URL
     */
    String generateAndUploadScreenshot(String webUrl);
}
