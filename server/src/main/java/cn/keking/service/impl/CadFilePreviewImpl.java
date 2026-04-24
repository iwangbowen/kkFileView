package cn.keking.service.impl;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.model.ReturnResponse;
import cn.keking.service.CadToPdfService;
import cn.keking.service.FileHandlerService;
import cn.keking.service.FilePreview;
import cn.keking.utils.*;
import cn.keking.web.filter.BaseUrlFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author chenjh
 * @since 2019/11/21 14:28
 */
@Service
public class CadFilePreviewImpl implements FilePreview {

    private static final Logger logger = LoggerFactory.getLogger(CadFilePreviewImpl.class);
    private static final String OFFICE_PREVIEW_TYPE_IMAGE = "image";
    private static final String OFFICE_PREVIEW_TYPE_ALL_IMAGES = "allImages";

    private final FileHandlerService fileHandlerService;
    private final CadToPdfService cadtopdfservice;
    private final OtherFilePreviewImpl otherFilePreview;
    private final OfficeFilePreviewImpl officefilepreviewimpl;

    // 用于处理回调的线程池
    private static final ExecutorService callbackExecutor = Executors.newFixedThreadPool(3);

    public CadFilePreviewImpl(FileHandlerService fileHandlerService,
                              OtherFilePreviewImpl otherFilePreview,
                              CadToPdfService cadtopdfservice,
                              OfficeFilePreviewImpl officefilepreviewimpl) {
        this.fileHandlerService = fileHandlerService;
        this.otherFilePreview = otherFilePreview;
        this.cadtopdfservice = cadtopdfservice;
        this.officefilepreviewimpl = officefilepreviewimpl;
    }

    @Override
    public String filePreviewHandle(String url, Model model, FileAttribute fileAttribute) {
        // 预览Type，参数传了就取参数的，没传取系统默认
        String officePreviewType = fileAttribute.getOfficePreviewType() == null ?
                ConfigConstants.getOfficePreviewType() : fileAttribute.getOfficePreviewType();
        boolean forceUpdatedCache = fileAttribute.forceUpdatedCache();
        String fileName = fileAttribute.getName();
        String cadPreviewType = ConfigConstants.getCadPreviewType();
        String cacheName = fileAttribute.getCacheName();
        String outFilePath = fileAttribute.getOutFilePath();

        // 查询转换状态
        String statusResult = officefilepreviewimpl.checkAndHandleConvertStatus(model, fileName, cacheName, fileAttribute);
        if (statusResult != null) {
            return statusResult;
        }

        // 判断之前是否已转换过，如果转换过，直接返回，否则执行转换
        if (forceUpdatedCache || !fileHandlerService.listConvertedFiles().containsKey(cacheName)
                || !ConfigConstants.isCacheEnabled()) {

            // 检查是否已在转换中
            ReturnResponse<String> response = DownloadUtils.downLoad(fileAttribute, fileName);
            if (response.isFailure()) {
                return otherFilePreview.notSupportedFile(model, fileAttribute, response.getMsg());
            }

            String filePath = response.getContent();
			 int refreshSchedule = ConfigConstants.getTime();
            if (StringUtils.hasText(outFilePath)) {
                try {
                    // 启动异步转换，并添加回调处理
                    startAsyncConversion(filePath, outFilePath, cacheName, fileAttribute);
                    // 返回等待页面
                    model.addAttribute("fileName", fileName);
					model.addAttribute("time", refreshSchedule);
                    model.addAttribute("message", "文件正在转换中，请稍候...");
                    return WAITING_FILE_PREVIEW_PAGE;
                } catch (Exception e) {
                    logger.error("Failed to start CAD conversion: {}", filePath, e);
                    return otherFilePreview.notSupportedFile(model, fileAttribute, "CAD转换异常，请联系管理员");
                }
            }
        }
        // 如果已有缓存，直接渲染预览
        return renderPreview(model, cacheName, outFilePath, officePreviewType, cadPreviewType, fileAttribute);
    }

    /**
     * 启动异步转换，并在转换完成后处理后续操作
     */
    private void startAsyncConversion(String filePath, String outFilePath,
                                      String cacheName, FileAttribute fileAttribute) {

        //获取cad转换路径
        String cadConverterPath = ConfigConstants.getCadConverterPath();
        int conversionModule= ConfigConstants.getConversionModule();
        if(cadConverterPath.equals("false")){
            //默认aspose-cad
            conversionModule = 1;
       }
        CompletableFuture<Boolean> conversionFuture;
        // 启动异步转换
        if(conversionModule==2){
            conversionFuture = cadtopdfservice.cadViewerConvert(
                    filePath,
                    outFilePath,
                    cadConverterPath,
                    ConfigConstants.getCadPreviewType(),
                    cacheName
            );
        }else {
            conversionFuture = cadtopdfservice.cadToPdfAsync(
                    filePath,
                    outFilePath,
                    cacheName,
                    ConfigConstants.getCadPreviewType(),
                    fileAttribute
            );
        }

        // 添加转换完成后的回调
        conversionFuture.whenCompleteAsync((success, throwable) -> {
            if (success != null && success) {
                try {
                    // 1. 是否保留CAD源文件（只在转换成功后才删除）
                    if (!fileAttribute.isCompressFile() && ConfigConstants.getDeleteSourceFile()) {
                        KkFileUtils.deleteFileByPath(filePath);
                    }
                    // 2. 加入缓存（只在转换成功后才添加）
                    if (ConfigConstants.isCacheEnabled()) {
                        fileHandlerService.addConvertedFile(cacheName,
                                fileHandlerService.getRelativePath(outFilePath));
                    }
                } catch (Exception e) {
                    logger.error("CAD转换后续处理失败: {}", filePath, e);
                }
            } else {
                // 转换失败，保留源文件供排查问题
                logger.error("CAD转换失败，保留源文件: {}", filePath);
                if (throwable != null) {
                    logger.error("转换失败原因: ", throwable);
                }
            }
        }, callbackExecutor);
    }

    /**
     * 渲染预览页面
     */
    private String renderPreview(Model model, String cacheName, String outFilePath,
                                 String officePreviewType, String cadPreviewType,
                                 FileAttribute fileAttribute) {
        cacheName = WebUtils.encodeFileName(cacheName);
        String baseUrl = BaseUrlFilter.getBaseUrl();
        int conversionModule= ConfigConstants.getConversionModule();
        if ("tif".equalsIgnoreCase(cadPreviewType)) {
            model.addAttribute("currentUrl", cacheName);
            return TIFF_FILE_PREVIEW_PAGE;
        } else if ("svg".equalsIgnoreCase(cadPreviewType)) {
            model.addAttribute("currentUrl", cacheName);
            if(conversionModule==2){
                return CADVIEWER_FILE_PREVIEW_PAGE;
            }else {
                return SVG_FILE_PREVIEW_PAGE;
            }

        }

        if (baseUrl != null && (OFFICE_PREVIEW_TYPE_IMAGE.equals(officePreviewType) ||
                OFFICE_PREVIEW_TYPE_ALL_IMAGES.equals(officePreviewType))) {
            return officefilepreviewimpl.getPreviewType(model, fileAttribute, officePreviewType, cacheName, outFilePath);
        }
        model.addAttribute("pdfUrl", cacheName);
        return PDF_FILE_PREVIEW_PAGE;
    }
}