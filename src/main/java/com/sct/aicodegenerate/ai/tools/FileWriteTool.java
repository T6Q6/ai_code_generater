package com.sct.aicodegenerate.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import com.sct.aicodegenerate.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Slf4j
@Component
public class FileWriteTool extends BaseTool{
    @Tool("写入文件到指定路径")
    public String writeFile(@P("文件的相对路径") String relativeFilePath,
                            @P("要写入文件的内容") String content,
                            @ToolMemoryId Long appId){
        // 具体实现
        try {
            Path path = Paths.get(relativeFilePath);
            if (!path.isAbsolute()){
                // 相对路径处理，创建基于 appId 的项目目录
                String projectDirName = "vue_project_" + appId;
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                path = projectRoot.resolve(path);
            }
            // 创建父目录（如果不存在）
            Path parentDir = path.getParent();
            if (parentDir!=null){
                Files.createDirectories(parentDir);
            }
            // 写入文件内容
            Files.write(path, content.getBytes(),
                    //如果文件不存在，则创建文件
                    StandardOpenOption.CREATE,
                    //如果文件已存在，则清空文件后上传新内容
                    StandardOpenOption.TRUNCATE_EXISTING);
            log.info("文件写入成功：{}", path.toAbsolutePath());
            // 返回需要相对路径
            return "文件写入成功：" + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "文件写入失败：" + relativeFilePath + "，错误：" + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "writFile";
    }

    @Override
    public String getDisplayName() {
        return "写入文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String suffix = FileUtil.getSuffix(relativeFilePath);
        String content = arguments.getStr("content");
        return String.format("""
                [工具调用] %s %s
                ```%s
                %s
                ```
                """,getToolName(),relativeFilePath,suffix,content);
    }
}
