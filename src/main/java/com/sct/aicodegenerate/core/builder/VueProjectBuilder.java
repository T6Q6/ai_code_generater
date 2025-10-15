package com.sct.aicodegenerate.core.builder;

import cn.hutool.core.util.RuntimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class VueProjectBuilder {

    /**
     * 异步构建项目（不阻塞主流程）
     */
    public void buildProjectAsync(String projectPath){
        // 在单独的线程中执行构建，避免阻塞主流程
        Thread.ofVirtual().name("vue-builder-"+System.currentTimeMillis())
                .start(()->{
                    try {
                        buildVueProject(projectPath);
                    } catch (Exception e) {
                        log.error("异步构建 Vue 项目时发生异常：{}",e.getMessage());
                    }
                });
    }

    /**
     * 构建 Vue 项目
     */
    public boolean buildVueProject(String projectPath){
        File projectDir = new File(projectPath);
        if(!projectDir.exists() || !projectDir.isDirectory()){
            log.error("项目目录不存在或者不是目录：{}",projectPath);
            return false;
        }
        // 检查 package.json 是否存在
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()){
            log.error("项目目录下不存在 package.json 文件：{}",projectPath);
            return false;
        }
        log.info("开始构建 Vue 项目：{}",projectPath);
        // 执行 npm run build
        if (!executeNpmInstall(projectDir)){
            log.error("npm install 执行失败，请检查项目目录：{}",projectPath);
            return false;
        }
        // 执行 npm run build
        if (!executeNpmRunBuild(projectDir)){
            log.error("npm run build 执行失败，请检查项目目录：{}",projectPath);
            return false;
        }
        //验证 dist 目录是否生成
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists()){
            log.error("项目目录下不存在 dist 目录，请检查项目目录：{}",projectPath);
            return false;
        }
        log.info("构建 Vue 项目成功，dist 目录：{}",distDir.getAbsolutePath());
        return true;
    }


    /**
     * 执行 npm install 命令
     */
    private boolean executeNpmInstall(File projectDir){
        log.info("执行 npm install...");
        String command = String.format("%s install",buildCommand("npm"));
        // 5分钟超时
        return executeCommand(projectDir,command,300);
    }

    /**
     * 执行 npm run build 命令
     */
    private boolean executeNpmRunBuild(File projectDir){
        log.info("执行 npm run build...");
        String command = String.format("%s run build",buildCommand("npm"));
        // 3分钟超时
        return executeCommand(projectDir,command,180);
    }

    /**
     * 判断是不是 Windows 系统
     */
    private boolean isWindows(){
        String osName = System.getProperty("os.name");
        return osName.toLowerCase().contains("windows");
    }

    /**
     * 根据操作系统构造命令
     */
    private String buildCommand(String baseCommand){
        if (isWindows()){
            return baseCommand+".cmd";
        }
        return baseCommand;
    }


    /**
     * 执行命令
     */
    private boolean executeCommand(File workingDir,String command,int timeoutSeconds){
        try {
            log.info("在目录 {} 中执行命令：{}",workingDir.getAbsolutePath(),command);
            Process process = RuntimeUtil.exec(
                    null,
                    workingDir,
                    // 命令分割为数组
                    command.split("\\s+"));
            // 等待进程完成，设置超时
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished){
                log.error("命令执行超时（{}秒），强制终止进程",timeoutSeconds);
                process.destroyForcibly();
                return false;
            }
            int exitCode = process.exitValue();
            if (exitCode == 0){
                log.info("命令执行成功: {}",command);
                return true;
            }else {
                log.error("命令执行失败，退出码: {}",exitCode);
                return false;
            }
        } catch (InterruptedException e) {
            log.error("执行命令失败：{}，错误信息：{}",command,e.getMessage());
            return false;
        }
    }
}
