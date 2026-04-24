package cn.keking.service.impl;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.model.FileType;
import cn.keking.model.ReturnResponse;
import cn.keking.service.FileHandlerService;
import cn.keking.service.FilePreview;
import cn.keking.service.Mediatomp4Service;
import cn.keking.utils.DownloadUtils;
import cn.keking.utils.FileConvertStatusManager;
import cn.keking.utils.KkFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author : kl
 * @authorboke : kailing.pub
 * @create : 2018-03-25 上午11:58
 * @description: 异步视频文件预览实现
 **/
@Service
public class MediaFilePreviewImpl implements FilePreview {

    private static final Logger logger = LoggerFactory.getLogger(MediaFilePreviewImpl.class);
    private final FileHandlerService fileHandlerService;
    private final OtherFilePreviewImpl otherFilePreview;
    private final Mediatomp4Service mediatomp4Service;
    private final OfficeFilePreviewImpl officefilepreviewimpl;

    // 用于处理回调的线程池
    private static final ExecutorService callbackExecutor = Executors.newFixedThreadPool(3);

    public MediaFilePreviewImpl(FileHandlerService fileHandlerService,
                                OtherFilePreviewImpl otherFilePreview,
                                Mediatomp4Service mediatomp4Service,
                                OfficeFilePreviewImpl officefilepreviewimpl) {
        this.fileHandlerService = fileHandlerService;
        this.otherFilePreview = otherFilePreview;
        this.mediatomp4Service = mediatomp4Service;
        this.officefilepreviewimpl = officefilepreviewimpl;
    }

    @Override
    public String filePreviewHandle(String url, Model model, FileAttribute fileAttribute) {
        String fileName = fileAttribute.getName();
        String suffix = fileAttribute.getSuffix();
        String cacheName = fileAttribute.getCacheName();
        String outFilePath = fileAttribute.getOutFilePath();
        boolean forceUpdatedCache = fileAttribute.forceUpdatedCache();
        FileType type = fileAttribute.getType();

        // 检查是否是需要转换的视频格式
        boolean mediaTypes = false;
        String[] mediaTypesConvert = FileType.MEDIA_CONVERT_TYPES;
        for (String temp : mediaTypesConvert) {
            if (suffix.equalsIgnoreCase(temp)) {
                mediaTypes = true;
                break;
            }
        }
        // 查询转换状态
        String statusResult = officefilepreviewimpl.checkAndHandleConvertStatus(model, fileName, cacheName, fileAttribute);
        if (statusResult != null) {
            return statusResult;
        }
        // 非HTTP协议或需要转换的文件
        if (!url.toLowerCase().startsWith("http") || checkNeedConvert(mediaTypes)) {
            // 检查缓存
            File outputFile = new File(outFilePath);
            if (outputFile.exists() && !forceUpdatedCache && ConfigConstants.isCacheEnabled()) {
                String relativePath = fileHandlerService.getRelativePath(outFilePath);
                if (fileHandlerService.listConvertedMedias().containsKey(cacheName)) {
                    model.addAttribute("mediaUrl", relativePath);
                    return MEDIA_FILE_PREVIEW_PAGE;
                }
            }
            // 下载文件
            ReturnResponse<String> response = DownloadUtils.downLoad(fileAttribute, fileName);
            if (response.isFailure()) {
                return otherFilePreview.notSupportedFile(model, fileAttribute, response.getMsg());
            }
            String filePath = response.getContent();
            if (mediaTypes) {
                // 检查文件大小限制
                if (isFileSizeExceeded(filePath)) {
                    return otherFilePreview.notSupportedFile(model, fileAttribute,
                            "视频文件大小超过" + ConfigConstants.getMediaConvertMaxSize() + "MB限制，禁止转换");
                }
                try {
                    // 启动异步转换，并添加回调处理
                    startAsyncConversion(filePath, outFilePath, cacheName, fileAttribute);
					int refreshSchedule = ConfigConstants.getTime();
                    // 返回等待页面
                    model.addAttribute("fileName", fileName);
					model.addAttribute("time", refreshSchedule);
                    model.addAttribute("message", "视频文件正在转换中，请稍候...");
                    return WAITING_FILE_PREVIEW_PAGE;
                } catch (Exception e) {
                    logger.error("Failed to start video conversion: {}", filePath, e);
                    return otherFilePreview.notSupportedFile(model, fileAttribute, "视频转换异常，请联系管理员");
                }
            } else {
                // 不需要转换的文件，直接返回
                model.addAttribute("mediaUrl", fileHandlerService.getRelativePath(outFilePath));
                return MEDIA_FILE_PREVIEW_PAGE;
            }
        }
        // HTTP协议的媒体文件，直接播放
        if (type.equals(FileType.MEDIA)) {
            model.addAttribute("mediaUrl", url);
            return MEDIA_FILE_PREVIEW_PAGE;
        }

        return otherFilePreview.notSupportedFile(model, fileAttribute, "系统还不支持该格式文件的在线预览");
    }

    /**
     * 启动异步转换，并在转换完成后处理后续操作
     */
    private void startAsyncConversion(String filePath, String outFilePath,
                                      String cacheName, FileAttribute fileAttribute) {
        // 启动异步转换
        CompletableFuture<Boolean> conversionFuture = mediatomp4Service.convertToMp4Async(
                filePath,
                outFilePath,
                cacheName,
                fileAttribute
        );

        // 添加转换完成后的回调
        conversionFuture.whenCompleteAsync((success, throwable) -> {
            if (success != null && success) {
                try {
                    // 1. 是否保留源文件（只在转换成功后才删除）
                    if (!fileAttribute.isCompressFile() && ConfigConstants.getDeleteSourceFile()) {
                        KkFileUtils.deleteFileByPath(filePath);
                    }
                    // 2. 加入视频缓存（只在转换成功后才添加）
                    if (ConfigConstants.isCacheEnabled()) {
                        fileHandlerService.addConvertedMedias(cacheName,
                                fileHandlerService.getRelativePath(outFilePath));
                    }
                } catch (Exception e) {
                    logger.error("视频转换后续处理失败: {}", filePath, e);
                }
            } else {
                // 转换失败，保留源文件供排查问题
                logger.error("视频转换失败，保留源文件: {}", filePath);
                if (throwable != null) {
                    logger.error("视频转换失败原因: ", throwable);
                }
            }
        }, callbackExecutor);
    }

    /**
     * 检查文件大小是否超过限制
     */
    private boolean isFileSizeExceeded(String filePath) {
        try {
            File inputFile = new File(filePath);
            if (inputFile.exists()) {
                long fileSizeMB = inputFile.length() / (1024 * 1024);
                int maxSizeMB = ConfigConstants.getMediaConvertMaxSize();

                if (fileSizeMB > maxSizeMB) {
                    logger.warn("视频文件大小超过限制: {}MB > {}MB", fileSizeMB, maxSizeMB);
                    return true;
                }
            }
        } catch (Exception e) {
            logger.error("检查文件大小时出错: {}", filePath, e);
        }
        return false;
    }

    /**
     * 检查是否需要转换
     */
    private boolean checkNeedConvert(boolean mediaTypes) {
        // 检查转换开关是否开启
        if ("true".equals(ConfigConstants.getMediaConvertDisable())) {
            return mediaTypes;
        }
        return false;
    }
}