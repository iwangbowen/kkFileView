package cn.keking.service.impl;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.model.ReturnResponse;
import cn.keking.service.FileHandlerService;
import cn.keking.service.FilePreview;
import cn.keking.service.PdfToJpgService;
import cn.keking.utils.DownloadUtils;
import cn.keking.utils.FileConvertStatusManager;
import cn.keking.utils.WebUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.poi.EncryptedDocumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by kl on 2018/1/17.
 * Content :处理pdf文件
 */
@Service
public class PdfFilePreviewImpl implements FilePreview {

    private static final Logger logger = LoggerFactory.getLogger(PdfFilePreviewImpl.class);
    private static final String PDF_PASSWORD_MSG = "password";
    private final FileHandlerService fileHandlerService;
    private final OtherFilePreviewImpl otherFilePreview;
    private final PdfToJpgService pdftojpgservice;
    private final OfficeFilePreviewImpl officefilepreviewimpl;

    // 用于处理回调的线程池
    private static final ExecutorService callbackExecutor = Executors.newFixedThreadPool(3);

    public PdfFilePreviewImpl(FileHandlerService fileHandlerService,
                              OtherFilePreviewImpl otherFilePreview,
                              OfficeFilePreviewImpl officefilepreviewimpl,
                              PdfToJpgService pdftojpgservice) {
        this.fileHandlerService = fileHandlerService;
        this.otherFilePreview = otherFilePreview;
        this.pdftojpgservice = pdftojpgservice;
        this.officefilepreviewimpl = officefilepreviewimpl;
    }

    @Override
    public String filePreviewHandle(String url, Model model, FileAttribute fileAttribute) {
        String pdfName = fileAttribute.getName();  //获取原始文件名
        String officePreviewType = fileAttribute.getOfficePreviewType(); //转换类型
        boolean forceUpdatedCache = fileAttribute.forceUpdatedCache();  //是否启用强制更新命令
        String outFilePath = fileAttribute.getOutFilePath();  //生成的文件路径
        String originFilePath;  //原始文件路径
        String cacheName = pdfName+officePreviewType;
        String filePassword = fileAttribute.getFilePassword();  // 获取密码
        if("demo.pdf".equals(pdfName)){
            return otherFilePreview.notSupportedFile(model, fileAttribute, "不能使用该文件名，请更换其他文件名在进行转换");
        }
        // 查询转换状态
        String statusResult = officefilepreviewimpl.checkAndHandleConvertStatus(model, pdfName, cacheName, fileAttribute);
        if (statusResult != null) {
            return statusResult;
        }

        boolean jiami=false;
        if(!ObjectUtils.isEmpty(filePassword)){
            jiami=pdftojpgservice.hasEncryptedPdfCacheSimple(outFilePath);
        }
        if (OfficeFilePreviewImpl.OFFICE_PREVIEW_TYPE_IMAGE.equals(officePreviewType) ||
                OfficeFilePreviewImpl.OFFICE_PREVIEW_TYPE_ALL_IMAGES.equals(officePreviewType)) {
            // 判断之前是否已转换过，如果转换过，直接返回，否则执行转换
            if (forceUpdatedCache || !fileHandlerService.listConvertedFiles().containsKey(cacheName) || !ConfigConstants.isCacheEnabled()) {
                if(jiami){
                    return renderPreview(model, cacheName, outFilePath,
                            officePreviewType, fileAttribute);
                }
                // 当文件不存在时，就去下载
                ReturnResponse<String> response = DownloadUtils.downLoad(fileAttribute, pdfName);
                if (response.isFailure()) {
                    return otherFilePreview.notSupportedFile(model, fileAttribute, response.getMsg());
                }
                originFilePath = response.getContent();
                // 检查文件是否需要密码，但不启动转换
                if (filePassword == null || filePassword.trim().isEmpty()) {
                    // 没有提供密码，先检查文件是否需要密码
                    if (checkIfPdfNeedsPassword(originFilePath, cacheName, pdfName)) {
                        model.addAttribute("needFilePassword", true);
                        model.addAttribute("fileName", pdfName);
                        model.addAttribute("cacheName", pdfName);
                        return EXEL_FILE_PREVIEW_PAGE;
                    }
                }
                try {
                    // 启动异步转换
                    startAsyncPdfConversion(originFilePath, outFilePath, cacheName, pdfName, fileAttribute);
					int refreshSchedule = ConfigConstants.getTime();
                    // 返回等待页面
                    model.addAttribute("fileName", pdfName);
					model.addAttribute("time", refreshSchedule);
                    model.addAttribute("message", "文件正在转换中，请稍候...");
                    return WAITING_FILE_PREVIEW_PAGE;
                } catch (Exception e) {
                    logger.error("Failed to start PDF conversion: {}", originFilePath, e);
                    return otherFilePreview.notSupportedFile(model, fileAttribute, "PDF转换异常，请联系管理员");
                }
            } else {
                // 如果已有缓存，直接渲染预览
                return renderPreview(model, cacheName, outFilePath,
                        officePreviewType, fileAttribute);
            }
        } else {
            // 处理普通PDF预览（非图片转换）
            return handleRegularPdfPreview(url, model, fileAttribute, pdfName, forceUpdatedCache, outFilePath);
        }
    }

    /**
     * 检查PDF文件是否需要密码（不进行实际转换）
     */
    private boolean checkIfPdfNeedsPassword(String originFilePath, String cacheName, String pdfName) {
        try {
            // 尝试用空密码加载PDF，检查是否需要密码
            File pdfFile = new File(originFilePath);
            if (!pdfFile.exists()) {
                return false;
            }

            // 使用try-with-resources确保资源释放
            try (org.apache.pdfbox.pdmodel.PDDocument tempDoc = org.apache.pdfbox.Loader.loadPDF(pdfFile, "")) {
                // 如果能加载成功，说明不需要密码
                int pageCount = tempDoc.getNumberOfPages();
                logger.info("PDF文件不需要密码，总页数: {}，文件: {}", pageCount, originFilePath);
                return false;
            } catch (Exception e) {
                Throwable[] throwableArray = ExceptionUtils.getThrowables(e);
                for (Throwable throwable : throwableArray) {
                    if (throwable instanceof IOException || throwable instanceof EncryptedDocumentException) {
                        if (e.getMessage().toLowerCase().contains(PDF_PASSWORD_MSG)) {
                            FileConvertStatusManager.convertSuccess(cacheName);
                            logger.info("PDF文件需要密码: {}", originFilePath);
                            return true;
                        }
                    }
                }
                logger.warn("PDF文件检查异常: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            logger.error("检查PDF密码状态失败: {}", originFilePath, e);
            return false;
        }
    }

    /**
     * 启动异步PDF转换
     */
    private void startAsyncPdfConversion(String originFilePath, String outFilePath,
                                         String cacheName, String pdfName,
                                         FileAttribute fileAttribute) {
        // 启动异步转换
        CompletableFuture<List<String>> conversionFuture = CompletableFuture.supplyAsync(() -> {
            try {
                // 更新状态
                FileConvertStatusManager.startConvert(cacheName);
                FileConvertStatusManager.updateProgress(cacheName, "正在启动PDF转换", 10);

                List<String> imageUrls = pdftojpgservice.pdf2jpg(originFilePath, outFilePath,
                        fileAttribute);

                if (imageUrls != null && !imageUrls.isEmpty()) {
                    boolean usePasswordCache = fileAttribute.getUsePasswordCache();
                    String filePassword = fileAttribute.getFilePassword();
                    if (ConfigConstants.isCacheEnabled() && (ObjectUtils.isEmpty(filePassword) || usePasswordCache)) {
                        fileHandlerService.addConvertedFile(cacheName, fileHandlerService.getRelativePath(outFilePath));
                    }
                    FileConvertStatusManager.updateProgress(cacheName, "转换完成", 100);
                    // 短暂延迟后清理状态
                    FileConvertStatusManager.convertSuccess(cacheName);
                    return imageUrls;
                } else {
                    FileConvertStatusManager.markError(cacheName, "PDF转换失败，未生成图片");
                    return null;
                }
            } catch (Exception e) {
                Throwable[] throwableArray = ExceptionUtils.getThrowables(e);
                for (Throwable throwable : throwableArray) {
                    if (throwable instanceof IOException || throwable instanceof EncryptedDocumentException) {
                        if (e.getMessage().toLowerCase().contains(PDF_PASSWORD_MSG)) {
                            // 标记为需要密码的状态
                            return null;
                        }
                    }
                }
                logger.error("PDF转换执行失败: {}", cacheName, e);

                // 检查是否已经标记为超时
                FileConvertStatusManager.ConvertStatus status = FileConvertStatusManager.getConvertStatus(cacheName);
                if (status == null || status.getStatus() != FileConvertStatusManager.Status.TIMEOUT) {
                    FileConvertStatusManager.markError(cacheName, "转换失败: " + e.getMessage());
                }
                return null;
            }
        });

        // 添加转换完成后的回调
        conversionFuture.whenCompleteAsync((imageUrls, throwable) -> {
            if (imageUrls == null || imageUrls.isEmpty()) {
                logger.error("PDF转换失败，保留源文件: {}", originFilePath);
                if (throwable != null) {
                    logger.error("转换失败原因: ", throwable);
                }
            }
        }, callbackExecutor);
    }

    /**
     * 渲染预览页面
     */
    private String renderPreview(Model model, String cacheName,
                                 String outFilePath, String officePreviewType,
                                 FileAttribute fileAttribute) {
        try {
            List<String> imageUrls;
            if(pdftojpgservice.hasEncryptedPdfCacheSimple(outFilePath)){
                  imageUrls = pdftojpgservice.getEncryptedPdfCache(outFilePath);
            }else {
                imageUrls = fileHandlerService.loadPdf2jpgCache(outFilePath);
            }
            if (imageUrls == null || imageUrls.isEmpty()) {
                return otherFilePreview.notSupportedFile(model, fileAttribute, "PDF转换缓存异常，请联系管理员");
            }

            model.addAttribute("imgUrls", imageUrls);
            model.addAttribute("currentUrl", imageUrls.getFirst());

            if (OfficeFilePreviewImpl.OFFICE_PREVIEW_TYPE_IMAGE.equals(officePreviewType)) {
                return OFFICE_PICTURE_FILE_PREVIEW_PAGE;
            } else {
                return PICTURE_FILE_PREVIEW_PAGE;
            }
        } catch (Exception e) {
            logger.error("渲染PDF预览页面失败: {}", cacheName, e);
            return otherFilePreview.notSupportedFile(model, fileAttribute, "渲染预览页面异常，请联系管理员");
        }
    }

    /**
     * 处理普通PDF预览（非图片转换）
     */
    private String handleRegularPdfPreview(String url, Model model, FileAttribute fileAttribute,
                                           String pdfName, boolean forceUpdatedCache,
                                           String outFilePath) {
        // 不是http开头，浏览器不能直接访问，需下载到本地
        if (url != null && !url.toLowerCase().startsWith("http")) {
            if (forceUpdatedCache || !fileHandlerService.listConvertedFiles().containsKey(pdfName) || !ConfigConstants.isCacheEnabled()) {
                ReturnResponse<String> response = DownloadUtils.downLoad(fileAttribute, pdfName);
                if (response.isFailure()) {
                    return otherFilePreview.notSupportedFile(model, fileAttribute, response.getMsg());
                }
                model.addAttribute("pdfUrl", fileHandlerService.getRelativePath(response.getContent()));
                if (ConfigConstants.isCacheEnabled()) {
                    // 加入缓存
                    fileHandlerService.addConvertedFile(pdfName, fileHandlerService.getRelativePath(outFilePath));
                }
            } else {
                model.addAttribute("pdfUrl", WebUtils.encodeFileName(pdfName));
            }
        } else {
            model.addAttribute("pdfUrl", url);
        }
        return PDF_FILE_PREVIEW_PAGE;
    }

}