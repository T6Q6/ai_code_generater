package com.sct.aicodegenerate.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sct.aicodegenerate.ai.guardrail.PromptSafetyInputGuardrail;
import com.sct.aicodegenerate.ai.guardrail.RetryOutputGuardrail;
import com.sct.aicodegenerate.ai.tools.*;
import com.sct.aicodegenerate.exception.BusinessException;
import com.sct.aicodegenerate.exception.ErrorCode;
import com.sct.aicodegenerate.util.SpringContextUtil;
import com.sct.aicodegenerate.model.enums.CodeGenTypeEnum;
import com.sct.aicodegenerate.service.ChatHistoryService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;
    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private ToolManager toolManager;

    /**
     * AI 服务实例缓存
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener(((key, value, cause) -> {
                log.debug("AI 服务实例被移除，appId：{}，原因：{}",key,cause);
            })).build();

    /**
     * 根据 appId 获取服务（带缓存）
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType){
        String chachKey = buildChacheKey(appId,codeGenType);
        return serviceCache.get(chachKey,key-> createAiCodeGeneratorService(appId,codeGenType));
    }

    private String buildChacheKey(long appId, CodeGenTypeEnum codeGenType) {
        return appId + "_" +codeGenType.getValue();
    }

    /**
     * 创建新的 AI 服务实例
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        // 从数据库加载历史对话到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId,chatMemory,20);
        // 根据代码生成类型选择不同的模型配置
        return switch (codeGenType){
            // Vue 项目生成
            case VUE_PROJECT -> {
                // 使用多例模式的 StreamingChatModel 解决并发问题
                StreamingChatModel reasoningStreamingChatModelPrototype = SpringContextUtil.getBean("reasoningStreamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .streamingChatModel(reasoningStreamingChatModelPrototype)
                        .chatMemoryProvider(memoryId -> chatMemory)
                        // 限制 AI 调用工具上限
                        .maxSequentialToolsInvocations(20)
                        .tools(toolManager.getAllTools())
                        // 添加输入护轨
                        .inputGuardrails(new PromptSafetyInputGuardrail())
                        // 添加输出护轨
                        .outputGuardrails(new RetryOutputGuardrail())
                        .hallucinatedToolNameStrategy(toolExecutionRequest ->
                                ToolExecutionResultMessage.from(toolExecutionRequest,
                                        "Error: there is no tool called" + toolExecutionRequest.name()))
                        .build();
            }
            // 原生 HTML 和多文件模式使用默认模式
            case HTML , MULTI_FILE -> {
                // 使用多例模式的 StreamingChatModel 解决并发问题
                StreamingChatModel openAiStreamingChatModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .chatModel(chatModel)
                        .streamingChatModel(openAiStreamingChatModel)
                        .chatMemory(chatMemory)
                        // 添加输入护轨
                        .inputGuardrails(new PromptSafetyInputGuardrail())
                        // 添加输出护轨
                        .outputGuardrails(new RetryOutputGuardrail())
                        // 添加输出护轨来设置最大重试次数
//                        .outputGuardrailsConfig(outputGuardrailsConfig)
                        .build();
            }
                default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型"+codeGenType.getValue());

            };
    }

//    OutputGuardrailsConfig outputGuardrailsConfig = OutputGuardrailsConfig.builder()
//            .maxRetries(3)
//            .build();


}

