package cn.keking.service.impl;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.model.ReturnResponse;
import cn.keking.service.FileHandlerService;
import cn.keking.service.FilePreview;
import cn.keking.service.TifToPdfService;
import cn.keking.utils.DownloadUtils;
import cn.keking.utils.FileConvertStatusManager;
import cn.keking.utils.KkFileUtils;
import cn.keking.utils.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * tiff 图片文件处理
 * @author kl (http://kailing.pub)
 * @since 2021/2/8
 */
@Service
public class TiffFilePreviewImpl implements FilePreview {

    private static final Logger logger = LoggerFactory.getLogger(TiffFilePreviewImpl.class);

    // 用于处理回调的线程池
    private static final ExecutorService callbackExecutor = Executors.newFixedThreadPool(3);

    private final FileHandlerService fileHandlerService;
    private final OtherFilePreviewImpl otherFilePreview;
    private final TifToPdfService tiftoservice;
    private final OfficeFilePreviewImpl officefilepreviewimpl;

    public TiffFilePreviewImpl(FileHandlerService fileHandlerService, OtherFilePreviewImpl otherFilePreview, TifToPdfService tiftoservice, OfficeFilePreviewImpl officefilepreviewimpl) {
        this.fileHandlerService = fileHandlerService;
        this.otherFilePreview = otherFilePreview;
        this.tiftoservice = tiftoservice;
        this.officefilepreviewimpl = officefilepreviewimpl;
    }

    @Override
    public String filePreviewHandle(String url, Model model, FileAttribute fileAttribute) {
        String fileName = fileAttribute.getName();
        String tifPreviewType = ConfigConstants.getTifPreviewType();
        String cacheName = fileAttribute.getCacheName();
        String outFilePath = fileAttribute.getOutFilePath();
        boolean forceUpdatedCache = fileAttribute.forceUpdatedCache();

        // 查询转换状态
        String statusResult = officefilepreviewimpl.checkAndHandleConvertStatus(model, fileName, cacheName, fileAttribute);
        if (statusResult != null) {
            return statusResult;
        }

        if ("jpg".equalsIgnoreCase(tifPreviewType) || "pdf".equalsIgnoreCase(tifPreviewType)) {
            if (forceUpdatedCache || !fileHandlerService.listConvertedFiles().containsKey(cacheName) || !ConfigConstants.isCacheEnabled()) {
                ReturnResponse<String> response = DownloadUtils.downLoad(fileAttribute, fileName);
                if (response.isFailure()) {
                    return otherFilePreview.notSupportedFile(model, fileAttribute, response.getMsg());
                }
                String filePath = response.getContent();

                try {
                    // 启动异步转换
                    startAsyncTiffConversion(filePath, outFilePath, cacheName, fileName, fileAttribute, tifPreviewType, forceUpdatedCache);
					int refreshSchedule = ConfigConstants.getTime();
                    // 返回等待页面
                    model.addAttribute("fileName", fileName);
					model.addAttribute("time", refreshSchedule);
                    model.addAttribute("message", "文件正在转换中，请稍候...");
                    return WAITING_FILE_PREVIEW_PAGE;
                } catch (Exception e) {
                    logger.error("Failed to start TIF conversion: {}", filePath, e);
                    return otherFilePreview.notSupportedFile(model, fileAttribute, "TIF转换异常，请联系系统管理员!");
                }
            } else {
                // 如果已有缓存，直接渲染预览
                if ("pdf".equalsIgnoreCase(tifPreviewType)) {
                    model.addAttribute("pdfUrl", WebUtils.encodeFileName(cacheName));
                    return PDF_FILE_PREVIEW_PAGE;
                } else if ("jpg".equalsIgnoreCase(tifPreviewType)) {
                    List<String> imgCache = fileHandlerService.getImgCache(cacheName);
                    if (imgCache == null || imgCache.isEmpty()) {
                        return otherFilePreview.notSupportedFile(model, fileAttribute, "TIF转换缓存异常，请联系系统管理员!");
                    }
                    model.addAttribute("imgUrls", imgCache);
                    model.addAttribute("currentUrl", imgCache.getFirst());
                    return PICTURE_FILE_PREVIEW_PAGE;
                }
            }
        }

        // 处理普通TIF预览（不进行转换）
        return handleRegularTiffPreview(url, model, fileAttribute, fileName, forceUpdatedCache, outFilePath);
    }

    /**
     * 启动异步TIF转换
     */
    private void startAsyncTiffConversion(String filePath, String outFilePath, String cacheName,
                                          String fileName, FileAttribute fileAttribute,
                                          String tifPreviewType, boolean forceUpdatedCache) {
        // 启动异步转换
        CompletableFuture<Void> conversionFuture = CompletableFuture.supplyAsync(() -> {
            try {
                // 更新状态
                FileConvertStatusManager.startConvert(cacheName);
                FileConvertStatusManager.updateProgress(cacheName, "正在启动TIF转换", 10);

                if ("pdf".equalsIgnoreCase(tifPreviewType)) {
                    tiftoservice.convertTif2Pdf(filePath, outFilePath,fileName,cacheName, forceUpdatedCache);

                    // 转换成功，更新缓存
                    if (ConfigConstants.isCacheEnabled()) {
                        fileHandlerService.addConvertedFile(cacheName, fileHandlerService.getRelativePath(outFilePath));
                    }
                } else {
                    List<String> listPic2Jpg = tiftoservice.convertTif2Jpg(filePath, outFilePath,fileName,cacheName, forceUpdatedCache);
                    // 转换成功，更新缓存
                    if (ConfigConstants.isCacheEnabled()) {
                        fileHandlerService.putImgCache(cacheName, listPic2Jpg);
                        fileHandlerService.addConvertedFile(cacheName, fileHandlerService.getRelativePath(outFilePath));
                    }
                }
                FileConvertStatusManager.convertSuccess(cacheName);
                return null;
            } catch (Exception e) {
                // 检查是否为Bad endianness tag异常
                if (e.getMessage() != null && e.getMessage().contains("Bad endianness tag (not 0x4949 or 0x4d4d)")) {
                    // 特殊处理：对于这种异常，我们不标记为转换失败，而是记录日志
                    logger.warn("TIF文件格式异常（Bad endianness tag），将尝试直接预览: {}", filePath);
                    FileConvertStatusManager.convertSuccess(cacheName);
                    return null;
                } else {
                    logger.error("TIF转换执行失败: {}", cacheName, e);

                    // 检查是否已经标记为超时
                    FileConvertStatusManager.ConvertStatus status = FileConvertStatusManager.getConvertStatus(cacheName);
                    if (status == null || status.getStatus() != FileConvertStatusManager.Status.TIMEOUT) {
                        FileConvertStatusManager.markError(cacheName, "转换失败: " + e.getMessage());
                    }
                    throw new RuntimeException(e);
                }
            }
        });

        // 添加转换完成后的回调
        conversionFuture.thenRunAsync(() -> {
            try {
                // 是否保留源文件（只在转换成功后才删除）
                if (!fileAttribute.isCompressFile() && ConfigConstants.getDeleteSourceFile()) {
                    KkFileUtils.deleteFileByPath(filePath);
                }
            } catch (Exception e) {
                logger.error("TIF转换后续处理失败: {}", filePath, e);
            }
        }, callbackExecutor).exceptionally(throwable -> {
            // 转换失败，记录日志但不删除源文件
            logger.error("TIF转换失败，保留源文件供排查: {}", filePath, throwable);
            return null;
        });
    }

    /**
     * 处理普通TIF预览（不进行转换）
     */
    private String handleRegularTiffPreview(String url, Model model, FileAttribute fileAttribute,
                                            String fileName, boolean forceUpdatedCache, String outFilePath) {
        // 不是http开头，浏览器不能直接访问，需下载到本地
        if (url != null && !url.toLowerCase().startsWith("http")) {
            if (forceUpdatedCache || !fileHandlerService.listConvertedFiles().containsKey(fileName) || !ConfigConstants.isCacheEnabled()) {
                ReturnResponse<String> response = DownloadUtils.downLoad(fileAttribute, fileName);
                if (response.isFailure()) {
                    return otherFilePreview.notSupportedFile(model, fileAttribute, response.getMsg());
                }
                model.addAttribute("currentUrl", fileHandlerService.getRelativePath(response.getContent()));
                if (ConfigConstants.isCacheEnabled()) {
                    // 加入缓存
                    fileHandlerService.addConvertedFile(fileName, fileHandlerService.getRelativePath(outFilePath));
                }
            } else {
                model.addAttribute("currentUrl", WebUtils.encodeFileName(fileName));
            }
            return TIFF_FILE_PREVIEW_PAGE;
        }
        model.addAttribute("currentUrl", url);
        return TIFF_FILE_PREVIEW_PAGE;
    }
}