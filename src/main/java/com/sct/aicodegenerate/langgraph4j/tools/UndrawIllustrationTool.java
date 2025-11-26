package com.sct.aicodegenerate.langgraph4j.tools;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.sct.aicodegenerate.langgraph4j.model.enums.ImageCategoryEnum;
import com.sct.aicodegenerate.langgraph4j.state.ImageResource;
import dev.langchain4j.agent.tool.Tool;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pixabay API 工具（支持图片/视频搜索）
 * 遵循 Pixabay 规范：需标注图片来源，禁止永久热链，缓存请求24小时
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "pexels")
public class UndrawIllustrationTool {

    // 替换为你的 Pixabay API Key
    private String apiKey;
    // 图片搜索接口地址
    private static final String IMAGE_SEARCH_URL = "https://pixabay.com/api/";

    /**
     * 搜索 Pixabay 图片
     * @param query 搜索关键词（如 "yellow+flower"，无需手动URL编码，Hutool自动处理）
     * @return 图片资源列表，包含预览URL、作者、尺寸等信息
     */
    @Tool("搜索 Pixabay 上的免费图片，返回图片预览 URL、作者、尺寸等信息。注意：使用时需在页面标注图片来源为 Pixabay，禁止永久热链（需下载到自有服务器）")
    public List<ImageResource> searchImages(
            @Parameter(description = "搜索关键词，如 'yellow flower'（无需 URL 编码）") String query) {

        List<ImageResource> imageResources = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        params.put("key", apiKey);
        params.put("q", query);
        params.put("perPage", 1);
        params.put("safesearch", "true");

        try (HttpResponse response = HttpRequest.get(IMAGE_SEARCH_URL)
                .form(params)
                .timeout(5000)
                .execute()) {

            int statusCode = response.getStatus();
            String responseBody = response.body();

            if (statusCode == 200) {
                JSONObject resultJson = JSONUtil.parseObj(responseBody);
                JSONArray hits = resultJson.getJSONArray("hits");

                if (hits != null && !hits.isEmpty()) {
                    for (Object hit : hits) {
                        JSONObject imageJson = (JSONObject) hit;
                        ImageResource resource = new ImageResource();

                        // 填充图片资源信息
                        resource.setUrl(imageJson.getStr("webformatURL"));
                        // 设置图片描述（使用标签拼接作为描述）
                        resource.setDescription(buildImageDescription(imageJson));
                        // 设置图片分类
                        resource.setCategory(ImageCategoryEnum.ILLUSTRATION);

                        imageResources.add(resource);
                    }
                }
            } else if (statusCode == 429) {
                log.error("Pixabay API 频率超限：{}", responseBody);
            } else {
                log.error("Pixabay 图片搜索失败，状态码：{}，响应：{}", statusCode, responseBody);
            }
        } catch (Exception e) {
            log.error("Pixabay 图片搜索异常", e);
        }

        return imageResources;
    }

    /**
     * 构建图片描述信息
     */
    private String buildImageDescription(JSONObject imageJson) {
        // 从API返回中提取标签作为描述信息
        String tags = imageJson.getStr("tags");
        return tags != null ? tags : "图片搜索结果: " + imageJson.getStr("webformatURL");
    }
}
