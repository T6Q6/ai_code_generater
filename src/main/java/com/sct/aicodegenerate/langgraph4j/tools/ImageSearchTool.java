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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pexels 图片搜索工具类（LangChain4j 可调用）
 * 遵循 Pexels API 规范：需标注图片来源为 Pexels，禁止商用未授权使用
 * API 文档参考：https://www.pexels.com/api/documentation/#photos-search
 */
@Slf4j
@Component
public class ImageSearchTool {

    // 1. 配置 Pexels 关键参数（需替换为你的有效 API Key）
    /**
     * Pexels API Key：从 https://www.pexels.com/api/ 注册获取
     * 注意：免费账号有调用频率限制（每小时 200 次请求）
     */
    private static final String PEXELS_API_KEY = "iRQJfW96S3FsobylMbGn79hFsNpeZRkMyEN2HLjKihWf2gV23LvoM6fB"; // 替换为你的真实 API Key
    /**
     * Pexels 图片搜索接口地址
     */
    private static final String PEXELS_PHOTO_SEARCH_URL = "https://api.pexels.com/v1/search";
    /**
     * 默认每页返回数量（API 限制：默认 15，最大 80）
     */
    private static final int DEFAULT_PER_PAGE = 15;
    /**
     * 默认请求超时时间（毫秒）
     */
    private static final int HTTP_TIMEOUT = 8000;


    /**
     * LangChain4j 可调用的 Pexels 图片搜索工具方法
     * 支持按关键词、方向、尺寸、颜色、地区等条件筛选，返回结构化的图片资源列表
     *
     * @param query       搜索关键词（必填，如 "nature"、"Group of people working"）
     * @return 结构化的图片资源列表（包含 URL、作者、尺寸、描述等信息）
     */
    @Tool(
        " 根据传入搜索关键词（query）搜索图片'；"
    )
    public List<ImageResource> searchPexelsPhotos(
            @Parameter(description = "必填，搜索关键词，如 'nature'、'Group of people working'") String query
    ) {
        // 校验必填参数
        if (Objects.isNull(query) || query.trim().isEmpty()) {
            log.error("Pexels 搜索失败：必填参数 query（搜索关键词）不能为空");
            return new ArrayList<>();
        }

        // 2. 构建请求 URL（拼接查询参数）
        StringBuilder requestUrl = new StringBuilder(PEXELS_PHOTO_SEARCH_URL);
        requestUrl.append("?query=").append(query.trim()); // 关键词（Hutool 会自动 URL 编码）

        // 处理页码（默认 1）
        int currentPage = 1;
        requestUrl.append("&page=").append(currentPage);
        // 处理每页数量（默认 15，最大 80）
        int currentPerPage = 12;
        requestUrl.append("&per_page=").append(currentPerPage);

        // 3. 使用 Hutool 发起 HTTP 请求（带 Pexels 认证头）
        List<ImageResource> imageResources = new ArrayList<>();
        try (HttpResponse response = HttpRequest.get(requestUrl.toString())
                // Pexels 认证：必须在 Header 中携带 Authorization
                .header("Authorization", PEXELS_API_KEY)
                // 设置超时时间（连接超时 + 读取超时）
                .timeout(HTTP_TIMEOUT)
                // 执行请求
                .execute()) {

            // 4. 处理响应结果
            int statusCode = response.getStatus();
            String responseBody = response.body();
            log.debug("Pexels 响应状态码：{}，响应内容：{}", statusCode, responseBody);

            // 4.1 成功响应（200 OK）
            if (statusCode == 200) {
                imageResources = parsePexelsResponse(responseBody);
            }
            // 4.2 认证失败（401）：API Key 无效或过期
            else if (statusCode == 401) {
                log.error("Pexels 认证失败：API Key 无效或已过期，响应：{}", responseBody);
            }
            // 4.3 频率超限（429）：免费账号每小时 200 次请求上限
            else if (statusCode == 429) {
                log.error("Pexels 频率超限：每小时请求次数已达上限（免费账号 200 次/小时），响应：{}", responseBody);
            }
            // 4.4 其他错误（如参数错误 400、服务器错误 500 等）
            else {
                log.error("Pexels 搜索失败：状态码 {}，响应：{}", statusCode, responseBody);
            }

        } catch (Exception e) {
            // 捕获网络异常、JSON 解析异常等
            log.error("Pexels 搜索请求异常：", e);
        }

        return imageResources;
    }


    /**
     * 解析 Pexels API 响应体，转换为 LangChain4j 所需的 ImageResource 列表
     *
     * @param responseBody Pexels API 返回的 JSON 字符串
     * @return 结构化的 ImageResource 列表
     */
    private List<ImageResource> parsePexelsResponse(String responseBody) {
        List<ImageResource> imageResources = new ArrayList<>();
        if (Objects.isNull(responseBody) || responseBody.isEmpty()) {
            log.warn("Pexels 响应体为空，无法解析");
            return imageResources;
        }

        // 1. 解析顶层 JSON 对象
        JSONObject responseJson = JSONUtil.parseObj(responseBody);
        // 2. 获取图片数组（"photos" 字段）
        JSONArray photosArray = responseJson.getJSONArray("photos");
        if (Objects.isNull(photosArray) || photosArray.isEmpty()) {
            log.info("Pexels 未搜索到符合条件的图片");
            return imageResources;
        }

        // 3. 遍历图片数组，转换为 ImageResource
        for (Object photoObj : photosArray) {
            JSONObject photoJson = (JSONObject) photoObj;
            ImageResource resource = new ImageResource();

            // 3.1 基础信息：图片 ID、描述、来源
            resource.setDescription(photoJson.getStr("alt")); // 图片描述（alt 字段）

            // 3.2 图片 URL：优先使用 medium 尺寸（平衡清晰度和加载速度），同时保存原图 URL
            JSONObject srcJson = photoJson.getJSONObject("src");
            if (Objects.nonNull(srcJson)) {
                resource.setUrl(srcJson.getStr("medium")); // 中等尺寸 URL（默认展示用）
            }

            // 3.5 图片分类（按业务枚举设置，可根据关键词动态调整）
            resource.setCategory(ImageCategoryEnum.CONTENT);

            imageResources.add(resource);
        }

        log.info("Pexels 解析完成：共获取 {} 张图片", imageResources.size());
        return imageResources;
    }
}