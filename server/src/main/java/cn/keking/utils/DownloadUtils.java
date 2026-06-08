package cn.keking.utils;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.model.ReturnResponse;
import io.mola.galimatias.GalimatiasParseException;
import org.apache.commons.io.FileUtils;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import static cn.keking.utils.KkFileUtils.isFileUrl;
import static cn.keking.utils.KkFileUtils.isFtpUrl;
import static cn.keking.utils.KkFileUtils.isHttpUrl;
import static cn.keking.utils.KkFileUtils.isWindows;

/**
 * @author yudian-it
 */
public class DownloadUtils {

    private final static Logger logger = LoggerFactory.getLogger(DownloadUtils.class);
    private static final String fileDir = ConfigConstants.getFileDir();
    private static final String URL_PARAM_FTP_USERNAME = "ftp.username";
    private static final String URL_PARAM_FTP_PASSWORD = "ftp.password";
    private static final String URL_PARAM_FTP_CONTROL_ENCODING = "ftp.control.encoding";
    private static final String URL_PARAM_FTP_PORT = "ftp.control.port";

    /**
     * 下载远程文件到本地。
     *
     * 纯 MD5 内容去重方案：总是先下载到 UUID 临时文件，下载成功后计算 MD5，按
     * "md5前缀_原文件名" 重命名；若相同 MD5 文件已存在则复用缓存并删除临时文件。
     * 不同 URL 但内容相同的文件将共享缓存。
     *
     * 同时保留 upstream 5.0.0 的改进：基于 CloseableHttpClient 的下载、MIME 类型
     * 校验、FTP 端口参数、file:// 协议支持等。
     *
     * @param fileAttribute fileAttribute
     * @param fileName      文件名
     * @return 本地文件绝对路径
     */
    public static ReturnResponse<String> downLoad(FileAttribute fileAttribute, String fileName) {

        String urlStr = null;
        try {
            urlStr = fileAttribute.getUrl();
        } catch (Exception e) {
            logger.error("处理URL异常:", e);
        }
        ReturnResponse<String> response = new ReturnResponse<>(0, "下载成功!!!", "");
        String realPath = getRelFilePath(fileName, fileAttribute);
        final String fileSuffix = fileAttribute.getSuffix();

        if (KkFileUtils.isIllegalFileName(realPath)) {
            response.setCode(1);
            response.setContent(null);
            response.setMsg("下载失败:文件名不合法!" + urlStr);
            return response;
        }
        if (!KkFileUtils.isAllowedUpload(realPath)) {
            response.setCode(1);
            response.setContent(null);
            response.setMsg("下载失败:不支持的类型!" + urlStr);
            return response;
        }
        if (fileAttribute.isCompressFile()) {
            response.setContent(fileDir + fileName);
            response.setMsg(fileName);
            return response;
        }
        // 如果文件是否已经存在、且不强制更新，则直接返回文件路径
        if (KkFileUtils.isExist(realPath) && !fileAttribute.forceUpdatedCache()) {
            response.setContent(realPath);
            response.setMsg(fileName);
            return response;
        }
        try {
            URL url = WebUtils.normalizedURL(urlStr);

            // 生成临时文件路径，使用 UUID 避免并发下载冲突
            String tempFileName = UUID.randomUUID().toString() + "_temp_" + fileName;
            String tempFilePath = fileDir + tempFileName;
            File tempFile = new File(tempFilePath);
            File parentDir = tempFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            if (!fileAttribute.getSkipDownLoad()) {
                if (isHttpUrl(url)) {
                    CloseableHttpClient httpClient = HttpRequestUtils.createConfiguredHttpClient();
                    String finalUrlStr = urlStr;

                    final boolean[] hasMimeError = {false};
                    final String[] mimeErrorMessage = {null};

                    HttpRequestUtils.executeHttpRequest(url, httpClient, fileAttribute, responseWrapper -> {
                        String contentType = responseWrapper.getContentType();
                        if (WebUtils.isMimeCheckRequired(fileSuffix)) {
                            if (!WebUtils.isValidMimeType(contentType, fileSuffix)) {
                                logger.error("文件类型错误，期望二进制文件但接收到文本类型，url: {}, Content-Type: {}",
                                        finalUrlStr, contentType);
                                hasMimeError[0] = true;
                                mimeErrorMessage[0] = "期望二进制文件但接收到文本类型，Content-Type: " + contentType;
                                return;
                            }
                        }
                        FileUtils.copyToFile(responseWrapper.getInputStream(), tempFile);
                    });

                    if (hasMimeError[0]) {
                        response.setCode(1);
                        response.setContent(null);
                        response.setMsg(mimeErrorMessage[0]);
                        return response;
                    }

                } else if (isFtpUrl(url)) {
                    String ftpUsername = WebUtils.getUrlParameterReg(fileAttribute.getUrl(), URL_PARAM_FTP_USERNAME);
                    String ftpPassword = WebUtils.getUrlParameterReg(fileAttribute.getUrl(), URL_PARAM_FTP_PASSWORD);
                    String ftpControlEncoding = WebUtils.getUrlParameterReg(fileAttribute.getUrl(), URL_PARAM_FTP_CONTROL_ENCODING);
                    String ftpport = WebUtils.getUrlParameterReg(realPath, URL_PARAM_FTP_PORT);
                    FtpUtils.download(fileAttribute.getUrl(), ftpport, tempFilePath, ftpUsername, ftpPassword, ftpControlEncoding);
                } else if (isFileUrl(url)) {
                    handleFileProtocol(url, tempFilePath);
                } else {
                    response.setCode(1);
                    response.setMsg("url不能识别url" + urlStr);
                    return response;
                }
            } else {
                // SkipDownLoad=true 时直接返回原始路径（压缩包等场景）
                response.setContent(realPath);
                response.setMsg(fileName);
                return response;
            }

            // 下载完成后，计算临时文件的 MD5，按 md5前缀_原文件名 重命名/复用缓存
            if (tempFile.exists() && tempFile.isFile()) {
                String fileMD5 = calculateFileMD5(tempFilePath);
                if (fileMD5 != null) {
                    String md5Prefix = fileMD5.substring(0, Math.min(8, fileMD5.length()));
                    String newFileName = md5Prefix + "_" + fileName;
                    String newFilePath = fileDir + newFileName;

                    File md5File = new File(newFilePath);
                    if (md5File.exists()) {
                        // 相同内容，直接复用缓存，删除临时文件
                        logger.info("文件MD5已存在，复用缓存：{} (MD5: {})", newFilePath, md5Prefix);
                        tempFile.delete();
                    } else {
                        try {
                            Files.move(tempFile.toPath(), md5File.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            logger.info("文件下载并重命名: {} -> {} (MD5: {})", fileName, newFileName, md5Prefix);
                        } catch (IOException e) {
                            logger.error("重命名文件失败: {} -> {}", tempFilePath, newFilePath, e);
                            response.setContent(tempFilePath);
                            response.setMsg(tempFileName);
                            return response;
                        }
                    }
                    response.setContent(newFilePath);
                    response.setMsg(newFileName);
                    return response;
                } else {
                    // MD5 计算失败，返回临时文件
                    logger.error("计算文件MD5失败，使用临时文件：{}", tempFilePath);
                    response.setContent(tempFilePath);
                    response.setMsg(tempFileName);
                    return response;
                }
            }

            // 文件不存在（不应该发生）
            response.setCode(1);
            response.setContent(null);
            response.setMsg("文件下载失败：文件不存在");
            return response;

        } catch (HttpClientErrorException e) {
            logger.error("HTTP请求失败，状态码：{}，url：{}", e.getStatusCode(), urlStr);
            response.setCode(1);
            response.setContent(null);
            if (e.getStatusCode().is4xxClientError()) {
                response.setMsg("文件不存在或无法访问 (HTTP " + e.getStatusCode() + ")");
            } else {
                response.setMsg("下载失败: " + e.getMessage());
            }
            return response;
        } catch (IOException | GalimatiasParseException e) {
            logger.error("文件下载失败，url：{}", urlStr);
            response.setCode(1);
            response.setContent(null);
            if (e instanceof FileNotFoundException) {
                response.setMsg("文件不存在!!!");
            } else {
                response.setMsg(e.getMessage());
            }
            return response;
        } catch (Exception e) {
            logger.error("下载文件时发生未知异常，url：{}", urlStr, e);
            response.setCode(1);
            response.setContent(null);
            response.setMsg("下载失败: " + e.getMessage());
            return response;
        }
    }

    // 处理 file 协议的文件下载
    private static void handleFileProtocol(URL url, String targetPath) throws IOException {
        File sourceFile = new File(url.getPath());
        if (!sourceFile.exists()) {
            throw new FileNotFoundException("本地文件不存在: " + url.getPath());
        }
        if (!sourceFile.isFile()) {
            throw new IOException("路径不是文件: " + url.getPath());
        }

        File targetFile = new File(targetPath);

        // 防止自身复制覆盖
        if (isSameFile(sourceFile, targetFile)) {
            logger.info("源文件和目标文件相同，跳过复制: {}", sourceFile.getAbsolutePath());
            return;
        }

        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 判断两个文件是否是同一个文件
     * 通过比较规范化路径来避免符号链接、相对路径等问题
     */
    private static boolean isSameFile(File file1, File file2) {
        try {
            String canonicalPath1 = file1.getCanonicalPath();
            String canonicalPath2 = file2.getCanonicalPath();
            if (isWindows()) {
                return canonicalPath1.equalsIgnoreCase(canonicalPath2);
            }
            return canonicalPath1.equals(canonicalPath2);
        } catch (IOException e) {
            logger.warn("无法获取文件的规范化路径，使用绝对路径比较: {}, {}", file1.getAbsolutePath(), file2.getAbsolutePath());
            String absolutePath1 = file1.getAbsolutePath();
            String absolutePath2 = file2.getAbsolutePath();
            if (isWindows()) {
                return absolutePath1.equalsIgnoreCase(absolutePath2);
            }
            return absolutePath1.equals(absolutePath2);
        }
    }

    /**
     * 获取真实文件绝对路径
     *
     * @param fileName 文件名
     * @return 文件路径
     */
    private static String getRelFilePath(String fileName, FileAttribute fileAttribute) {
        String type = fileAttribute.getSuffix();
        if (null == fileName) {
            UUID uuid = UUID.randomUUID();
            fileName = uuid + "." + type;
        } else { // 文件后缀不一致时，以 type 为准(针对 simText【将类 txt 文件转为 txt】)
            fileName = fileName.replace(fileName.substring(fileName.lastIndexOf(".") + 1), type);
        }

        String realPath = fileDir + fileName;
        File dirFile = new File(fileDir);
        if (!dirFile.exists() && !dirFile.mkdirs()) {
            logger.error("创建目录【{}】失败,可能是权限不够，请检查", fileDir);
        }
        return realPath;
    }

    /**
     * 计算文件的 MD5 值
     *
     * @param filePath 文件路径
     * @return MD5 哈希值（32 位 16 进制），如果计算失败返回 null
     */
    private static String calculateFileMD5(String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            byte[] hashBytes = md.digest();

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            logger.error("计算文件MD5失败: {}", filePath, e);
            return null;
        }
    }
}
