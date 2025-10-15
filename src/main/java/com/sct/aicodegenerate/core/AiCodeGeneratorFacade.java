package com.sct.aicodegenerate.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.sct.aicodegenerate.ai.AiCodeGeneratorService;
import com.sct.aicodegenerate.ai.AiCodeGeneratorServiceFactory;
import com.sct.aicodegenerate.ai.model.message.AiResponseMessage;
import com.sct.aicodegenerate.ai.model.message.ToolExecutedMessage;
import com.sct.aicodegenerate.ai.model.message.ToolRequestMessage;
import com.sct.aicodegenerate.constant.AppConstant;
import com.sct.aicodegenerate.core.builder.VueProjectBuilder;
import com.sct.aicodegenerate.exception.BusinessException;
import com.sct.aicodegenerate.exception.ErrorCode;
import com.sct.aicodegenerate.exception.ThrowUtils;
import com.sct.aicodegenerate.model.entity.HtmlCodeResult;
import com.sct.aicodegenerate.model.entity.MultiFileCodeResult;
import com.sct.aicodegenerate.model.enums.CodeGenTypeEnum;
import com.sct.aicodegenerate.core.parser.CodeParserExecutor;
import com.sct.aicodegenerate.core.saver.CodeFileSaverExecutor;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * AI 代码生成外观类，组合生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private VueProjectBuilder vueProjectBuilder;
    /**
     * 统一入口：根据类型生成并保存代码（流式）
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenType, Long appId) {
        ThrowUtils.throwIf(codeGenType == null,
                new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空"));
        // 根据 appId 获取对应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenType);
        return switch (codeGenType) {
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, codeGenType, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, codeGenType, appId);
            }
            case VUE_PROJECT -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream,appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型: " + codeGenType.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 通用流式代码处理方法
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream
                .doOnNext(chunk -> {
                    //实时收集代码片段
                    codeBuilder.append(chunk);
                })
                .doOnComplete(() -> {
                    //流式返回完成后保存代码
                    try {
                        String completeCode = codeBuilder.toString();
                        //使用执行器解析代码
                        Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                        //使用执行器保存代码
                        File saveDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                        System.out.println("代码保存成功：" + saveDir.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("代码保存失败: {}", e.getMessage());
                    }
                });
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     */
    private Flux<String> processTokenStream(TokenStream tokenStream,Long appId){
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) -> {
                AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                sink.next(JSONUtil.toJsonStr(aiResponseMessage));
            })
            .onPartialToolExecutionRequest((index,toolExecutionRequest)-> {
                ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                sink.next(JSONUtil.toJsonStr(toolRequestMessage));
            })
            .onToolExecuted((ToolExecution toolExecution)-> {
                ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
            })
            .onCompleteResponse((ChatResponse completeResponse)-> {
                // 执行 Vue 项目构建（同步执行，确保预览时项目已就绪）
                String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "vue_project_" + appId;
                vueProjectBuilder.buildVueProject(projectPath);
                sink.complete();
            })
            .onError((Throwable error) -> {
                error.printStackTrace();
                sink.error(error);
            })
            .start();
        });
    }

//    /**
//      * 统一入口：根据类型生成并保存代码
//      */
//    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenType, Long appId) {
//        ThrowUtils.throwIf(codeGenType == null,
//                new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空"));
//        // 根据 appId 获取对应的 AI 服务实例
//        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenType);
//        return switch (codeGenType) {
//            case HTML -> {
//                HtmlCodeResult htmlCodeResult = aiCodeGeneratorService.generateHtmlCode(userMessage);
//                yield CodeFileSaverExecutor.executeSaver(htmlCodeResult, codeGenType, appId);
//            }
//            case MULTI_FILE -> {
//                MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorService.generateMultiFileCode(userMessage);
//                yield CodeFileSaverExecutor.executeSaver(multiFileCodeResult, codeGenType, appId);
//            }
//            default -> {
//                String errorMessage = "不支持的生成类型: " + codeGenType.getValue();
//                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
//            }
//        };
//    }

}