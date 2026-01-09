package cn.keking.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.model.ReturnResponse;
import io.mola.galimatias.GalimatiasParseException;

/**
 * @author yudian-it
 */
public class DownloadUtils {

    private final static Logger logger = LoggerFactory.getLogger(DownloadUtils.class);
    private static final String fileDir = ConfigConstants.getFileDir();
    private static final String URL_PARAM_FTP_USERNAME = "ftp.username";
    private static final String URL_PARAM_FTP_PASSWORD = "ftp.password";
    private static final String URL_PARAM_FTP_CONTROL_ENCODING = "ftp.control.encoding";
    private static final RestTemplate restTemplate = new RestTemplate();
    private static  final HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
    private static final ObjectMapper mapper = new ObjectMapper();


    /**
     * @param fileAttribute fileAttribute
     * @param fileName      文件名
     * @return 本地文件绝对路径
     */
    public static ReturnResponse<String> downLoad(FileAttribute fileAttribute, String fileName) {
        // 忽略ssl证书
        String urlStr = null;
        try {
            SslUtils.ignoreSsl();
            urlStr = fileAttribute.getUrl().replaceAll("\\+", "%20").replaceAll(" ", "%20");
        } catch (Exception e) {
            DownloadUtils.logger.error("忽略SSL证书异常:", e);
        }
        ReturnResponse<String> response = new ReturnResponse<>(0, "下载成功!!!", "");
        String realPath = DownloadUtils.getRelFilePath(fileName, fileAttribute);

        // 判断是否非法地址
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
        if (fileAttribute.isCompressFile()) { //压缩包文件 直接赋予路径 不予下载
            response.setContent(DownloadUtils.fileDir + fileName);
            response.setMsg(fileName);
            return response;
        }

        // 纯MD5方案：总是下载文件，然后计算MD5
        // 如果MD5文件已存在，则删除刚下载的文件并返回MD5文件
        // 注意：forceUpdatedCache参数仍然有效，但不再用于检查文件是否存在

        try {
            URL url = WebUtils.normalizedURL(urlStr);

            // 生成临时文件路径，使用UUID避免并发下载冲突
            String tempFileName = UUID.randomUUID().toString() + "_temp_" + fileName;
            String tempFilePath = DownloadUtils.fileDir + tempFileName;

            if (!fileAttribute.getSkipDownLoad()) {
                if (KkFileUtils.isHttpUrl(url)) {
                    File tempFile = new File(tempFilePath);
                    DownloadUtils.factory.setConnectionRequestTimeout(2000);  //设置超时时间
                    DownloadUtils.factory.setConnectTimeout(10000);
                    DownloadUtils.factory.setReadTimeout(72000);
                    HttpClient httpClient = HttpClientBuilder.create().setRedirectStrategy(new DefaultRedirectStrategy()).build();
                    DownloadUtils.factory.setHttpClient(httpClient);  //加入重定向方法
                    DownloadUtils.restTemplate.setRequestFactory(DownloadUtils.factory);
                    RequestCallback requestCallback = request -> {
                        request.getHeaders().setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL));
                        String proxyAuthorization = fileAttribute.getKkProxyAuthorization();
                        if(StringUtils.hasText(proxyAuthorization)){
                            Map<String,String>  proxyAuthorizationMap = DownloadUtils.mapper.readValue(proxyAuthorization, Map.class);
                            proxyAuthorizationMap.forEach((key, value) -> request.getHeaders().set(key, value));
                        }
                    };
                    try {
                        DownloadUtils.restTemplate.execute(url.toURI(), HttpMethod.GET, requestCallback, fileResponse -> {
                            FileUtils.copyToFile(fileResponse.getBody(), tempFile);
                            return null;
                        });
                    }  catch (Exception e) {
                            response.setCode(1);
                            response.setContent(null);
                            response.setMsg("下载失败:" + e);
                            // 清理临时文件
                            if (tempFile.exists()) {
                                tempFile.delete();
                            }
                            return response;
                    }
                } else if (KkFileUtils.isFtpUrl(url)) {
                    String ftpUsername = WebUtils.getUrlParameterReg(fileAttribute.getUrl(), DownloadUtils.URL_PARAM_FTP_USERNAME);
                    String ftpPassword = WebUtils.getUrlParameterReg(fileAttribute.getUrl(), DownloadUtils.URL_PARAM_FTP_PASSWORD);
                    String ftpControlEncoding = WebUtils.getUrlParameterReg(fileAttribute.getUrl(), DownloadUtils.URL_PARAM_FTP_CONTROL_ENCODING);
                    FtpUtils.download(fileAttribute.getUrl(), tempFilePath, ftpUsername, ftpPassword, ftpControlEncoding);
                } else {
                    response.setCode(1);
                    response.setMsg("url不能识别url" + urlStr);
                    return response;
                }
            } else {
                // SkipDownLoad=true时，直接返回原始路径（压缩包等场景）
                response.setContent(realPath);
                response.setMsg(fileName);
                return response;
            }

            // 下载完成后，计算临时文件的MD5并重命名为MD5文件
            // 不同内容的文件有不同MD5，相同内容的文件共享缓存
            File tempFile = new File(tempFilePath);
            if (tempFile.exists() && tempFile.isFile()) {
                String fileMD5 = DownloadUtils.calculateFileMD5(tempFilePath);
                if (fileMD5 != null) {
                    // 生成新文件名：MD5前8位_原始文件名
                    String md5Prefix = fileMD5.substring(0, Math.min(8, fileMD5.length()));
                    String newFileName = md5Prefix + "_" + fileName;
                    String newFilePath = DownloadUtils.fileDir + newFileName;

                    // 如果基于MD5的文件已存在，说明是相同内容的文件，直接复用
                    File md5File = new File(newFilePath);
                    if (md5File.exists()) {
                        DownloadUtils.logger.info("文件MD5已存在，复用缓存：{} (MD5: {})", newFilePath, md5Prefix);
                        // 删除刚下载的临时文件
                        if (!tempFile.delete()) {
                            DownloadUtils.logger.warn("删除临时文件失败：{}", tempFilePath);
                        }
                    } else {
                        // 重命名临时文件为基于MD5的文件名
                        try {
                            Files.move(tempFile.toPath(), md5File.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            DownloadUtils.logger.info("文件下载并重命名: {} -> {} (MD5: {})", fileName, newFileName, md5Prefix);
                        } catch (IOException e) {
                            DownloadUtils.logger.error("文件重命名失败: {} -> {}", tempFilePath, newFilePath, e);
                            // 如果重命名失败，仍然使用临时文件
                            response.setContent(tempFilePath);
                            response.setMsg(tempFileName);
                            return response;
                        }
                    }
                    // 返回新文件路径和名称
                    response.setContent(newFilePath);
                    response.setMsg(newFileName);
                    return response;
                } else {
                    // MD5计算失败，返回临时文件
                    DownloadUtils.logger.error("计算文件MD5失败，使用临时文件：{}", tempFilePath);
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
        } catch (IOException | GalimatiasParseException e) {
            DownloadUtils.logger.error("文件下载失败，url：{}", urlStr);
            response.setCode(1);
            response.setContent(null);
            if (e instanceof FileNotFoundException) {
                response.setMsg("文件不存在!!!");
            } else {
                response.setMsg(e.getMessage());
            }
            return response;
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
        } else { // 文件后缀不一致时，以type为准(针对simText【将类txt文件转为txt】)
            fileName = fileName.replace(fileName.substring(fileName.lastIndexOf(".") + 1), type);
        }

        String realPath = DownloadUtils.fileDir + fileName;
        File dirFile = new File(DownloadUtils.fileDir);
        if (!dirFile.exists() && !dirFile.mkdirs()) {
            DownloadUtils.logger.error("创建目录【{}】失败,可能是权限不够，请检查", DownloadUtils.fileDir);
        }
        return realPath;
    }

    /**
     * 计算文件的MD5值
     *
     * @param filePath 文件路径
     * @return MD5哈希值（32位16进制），如果计算失败返回null
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

            // 转换为32位16进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            DownloadUtils.logger.error("计算文件MD5失败: {}", filePath, e);
            return null;
        }
    }

}
