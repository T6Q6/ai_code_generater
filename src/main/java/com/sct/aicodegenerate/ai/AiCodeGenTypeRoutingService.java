package com.sct.aicodegenerate.ai;

import com.sct.aicodegenerate.model.enums.CodeGenTypeEnum;
import dev.langchain4j.service.SystemMessage;

/**
 * AI代码生成类型路由服务
 */
public interface AiCodeGenTypeRoutingService {

    /**
     * 根据用户需求只能选择代码生成类型
     */
    @SystemMessage(fromResource = "prompt/codegen-routing-system-prompt.txt")
    CodeGenTypeEnum routeCodeGenType(String userPrompt);
}
