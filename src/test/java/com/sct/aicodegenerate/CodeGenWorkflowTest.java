package com.sct.aicodegenerate;

import com.sct.aicodegenerate.langgraph4j.CodeGenConcurrentWorkflow;
import com.sct.aicodegenerate.langgraph4j.WorkflowApp;
import com.sct.aicodegenerate.langgraph4j.state.WorkflowContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CodeGenWorkflowTest {

    @Test
    void testSimpleHtmlWorkflow() {
        WorkflowContext result = new CodeGenConcurrentWorkflow().executeWorkflow("创建一个简单的个人主页");
        Assertions.assertNotNull(result);
        System.out.println("生成类型: " + result.getGenerationType());
        System.out.println("生成的代码目录: " + result.getGeneratedCodeDir());
        System.out.println("构建结果目录: " + result.getBuildResultDir());
    }
}
