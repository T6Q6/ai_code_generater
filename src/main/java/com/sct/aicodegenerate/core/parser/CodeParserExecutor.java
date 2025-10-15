package com.sct.aicodegenerate.core.parser;

import com.sct.aicodegenerate.model.enums.CodeGenTypeEnum;

/**
 * 代码解析执行器
 */
public class CodeParserExecutor {
    private static final HtmlCodeParser htmlCodeParser = new HtmlCodeParser();
    private static final MultiFileCodeParser multiFileCodeParser = new MultiFileCodeParser();

    /**
     * 执行代码解析
     */
    public static Object executeParser(String codeContent, CodeGenTypeEnum codeGenType) {
        return switch (codeGenType) {
            case HTML -> htmlCodeParser.parseCode(codeContent);
            case MULTI_FILE -> multiFileCodeParser.parseCode(codeContent);
            default -> throw new IllegalArgumentException("不支持的生成类型: " + codeGenType.getValue());
        };
    }
}
