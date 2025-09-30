# kkFileView AI 编码助手指南

## 项目概述
**kkFileView** 是基于 Spring Boot 2.4.2 和 Java 8 构建的文档在线预览服务。它提供 REST API 接口，支持预览 40+ 种文件格式，包括 Office 文档、PDF、CAD 文件、图片、视频、3D 模型和压缩包。

**核心技术栈：**
- Spring Boot 2.4.2 + Jetty（非 Tomcat）
- JodConverter 4.4.6 + LibreOffice 7.x 用于 Office 文件转换
- Freemarker 模板引擎用于服务端渲染
- 可选的 Redis/RocksDB/JDK 缓存实现
- Maven 多模块项目

## 架构原理

### 文件处理器的策略模式
核心架构通过 `FilePreviewFactory` 使用 **Spring 管理的策略模式**：

```java
// FilePreviewFactory.java - 通过 Spring bean 名称获取处理器
public FilePreview get(FileAttribute fileAttribute) {
    return context.getBean(fileAttribute.getType().getInstanceName(), FilePreview.class);
}
```

**添加新文件类型支持：**
1. 在 `FileType` 枚举中添加文件扩展名和 bean 名称（例如：`NEWTYPE("newTypeFilePreviewImpl")`）
2. 创建实现 `FilePreview` 接口的 `@Service` 类
3. Bean 名称必须与枚举中的 `instanceName` 匹配
4. 实现 `filePreviewHandle(String url, Model model, FileAttribute fileAttribute)` 方法
5. 返回 `src/main/resources/web/` 目录下的 Freemarker 模板名称

**实现示例：**
- `OfficeFilePreviewImpl.java` - 通过 LibreOffice 转换为 PDF/图片
- `CompressFilePreviewImpl.java` - 解压并列出压缩包内容
- `PdfFilePreviewImpl.java` - 使用 pdf.js 前端库渲染

### 请求流程
```
用户 URL → OnlinePreviewController.onlinePreview()
  ↓
FileHandlerService.getFileAttribute() - 解析 URL，检测文件类型
  ↓
FilePreviewFactory.get() - 根据 FileType 获取处理器
  ↓
XxxFilePreviewImpl.filePreviewHandle() - 处理文件并返回模板
  ↓
Freemarker 渲染预览 UI
```

### 缓存管理
- 三种实现方式：JDK（默认）、Redis、RocksDB
- 通过 `application.properties` 中的 `cache.type` 控制
- 缓存键存储转换后的文件和预览元数据
- 通过定时任务清理：`cache.clean.cron = 0 0 3 * * ?`（每天凌晨 3 点）

## 关键配置

### 环境变量覆盖
**所有**配置项都支持通过 `KK_` 前缀的环境变量覆盖：
```properties
server.port = ${KK_SERVER_PORT:8012}
cache.enabled = ${KK_CACHE_ENABLED:true}
office.home = ${KK_OFFICE_HOME:default}
```

### LibreOffice 检测
启动脚本（`bin/startup.sh`）会在以下路径自动检测 LibreOffice：
```bash
DIR_HOME=("/opt/libreoffice7.5" "/opt/libreoffice7.6" "/usr/lib/libreoffice" ...)
```
如果未找到，会通过 `install.sh` 自动安装。也可通过 `office.home` 配置自定义路径。

### 文件处理限制
```properties
# PDF 转换超时设置（根据页数）
pdf.timeout=90      # <50 页
pdf.timeout80=180   # 50-200 页
pdf.timeout200=300  # >200 页

# Office 转换限制
office.plugin.task.timeout = 5m
office.pagerange = false  # 设置为 "1-5" 可限制页数
```

## 开发工作流程

### 本地开发
```bash
# 在 IDE 中运行
./server/src/main/java/cn/keking/ServerMain.java

# 或通过 Maven 运行
cd server && mvn spring-boot:run

# 访问：http://localhost:8012
```

### 构建
```bash
# 根目录 pom.xml 协调多模块构建
mvn clean package

# 生成两个发行版：
# - server/target/kkFileView-4.4.1-win32.zip（包含 LibreOffice Portable）
# - server/target/kkFileView-4.4.1-linux.tar.gz（自动安装 LibreOffice）
```

### Docker
```bash
# 基础镜像：keking/kkfileview-base:4.4.0（包含 LibreOffice）
docker build -t kkfileview:latest .

# 通过环境变量控制所有设置
docker run -e KK_SERVER_PORT=8080 -e KK_CACHE_ENABLED=false kkfileview:latest
```

### 测试
```bash
# 运行所有测试
mvn test

# 测试特定文件类型预览
curl "http://localhost:8012/onlinePreview?url=$(echo 'http://example.com/test.pdf' | base64 | jq -sRr @uri)"
```

## 项目约定

### URL 编码标准
**重要：**所有文件 URL 使用**双重编码**（Base64 + URL 编码）：
```java
// 编码（客户端）
String encodedUrl = URLEncoder.encode(Base64.encodeBase64String(rawUrl.getBytes()));

// 解码（服务端通过 WebUtils.decodeUrl）
String rawUrl = new String(Base64.decodeBase64(URLDecoder.decode(encodedUrl)));
```

### 安全控制
- **可信主机过滤器：**通过 `trust.host` 配置白名单文件源域名
- **文件上传限制：**通过 `prohibit = exe,dll,dat` 阻止危险扩展名
- **XSS 防护：**所有用户输入通过 `KkFileUtils.htmlEscape()` 转义
- **路径遍历保护：**在 `CompressFileReader` 中对压缩包进行验证

### 日志规范
```java
private static final Logger logger = LoggerFactory.getLogger(ClassName.class);

// 标准格式
logger.info("预览文件url：{}，previewType：{}", fileUrl, fileAttribute.getType());
logger.error("转换失败，url：{}", url, exception);
```

### 异步处理
Office/CAD/TIFF 转换使用线程池避免阻塞：
```java
// FileConvertQueueTask.java 管理转换队列
@Service
public class FileConvertQueueTask {
    @Async
    public void addTask(String cacheKey, String url) { ... }
}
```

## 常见陷阱

1. **Bean 命名不匹配：**确保 `@Service("beanName")` 与 `FileType` 枚举的 `instanceName` 匹配
2. **URL 编码错误：**对所有 URL 参数使用 `WebUtils.decodeUrl()`，而不是直接使用 `URLDecoder`
3. **Office 进程死锁：**设置 `office.plugin.task.maxtasksperprocess = 200` 以自动重启 LibreOffice
4. **CORS 问题：**外部域名的文件通过 `/getCorsFile` 代理端点加载
5. **缓存未清理：**验证 `delete.source.file = true` 以在预览后删除转换后的文件

## 文件位置

**关键目录：**
- `server/src/main/java/cn/keking/service/impl/` - 文件预览实现
- `server/src/main/resources/web/` - Freemarker 模板（pdf.ftl、office.ftl 等）
- `server/src/main/config/application.properties` - 主配置文件（合并到 JAR）
- `server/src/main/bin/` - 启动脚本（startup.sh、startup.bat）
- `server/LibreOfficePortable/` - Windows 下捆绑的 LibreOffice

**运行时路径（来自 `file.dir` 配置）：**
- `${file.dir}/` - 转换文件缓存（PDF、图片）
- `${file.dir}/demo/` - 上传的演示文件

## 集成点

### REST API
```
GET /onlinePreview?url={base64_encoded_url}
GET /picturesPreview?urls={base64_encoded_urls_pipe_separated}
GET /addTask?url={base64_encoded_url} - 异步转换队列
GET /getCorsFile?urlPath={base64_encoded_url} - CORS 代理
```

### 添加前端库
放置在 `server/src/main/resources/static/{library}/` - 通过 `/static/{library}/` 访问
示例：`/static/pdfjs/`、`/static/bpmn/`、`/static/drawio/`

### 外部依赖
- **Aspose CAD（商业版）：**用于 DWG/DXF 预览 - 生产环境需要许可证
- **ByteDeco JavaCV：**通过 FFmpeg/OpenCV 进行视频转码
- **SevenZipJBinding：**压缩包提取（支持 RAR5、7Z）

## 版本兼容性
- **Java：**1.8+（代码库针对 JDK 8）
- **LibreOffice：**7.3 - 7.6 已测试（Linux/Docker），7.5.3 Portable（Windows）
- **Spring Boot：**2.4.2（内嵌 Jetty，非 Tomcat）
- **Maven：**3.6+

## 实用命令
```bash
# 检查 LibreOffice 安装
ls -la /opt/libreoffice*/program/soffice.bin

# 监控转换日志
tail -f server/target/kkFileView-4.4.1/log/kkFileView.log

# 强制重建缓存
curl "http://localhost:8012/onlinePreview?url={url}&forceUpdatedCache=true"

# 查看运行进程
ps aux | grep soffice  # LibreOffice 进程
ps aux | grep kkFileView  # 应用进程
```
