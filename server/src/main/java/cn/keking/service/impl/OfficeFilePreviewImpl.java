package cn.keking.service.impl;

import java.io.IOException;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.poi.EncryptedDocumentException;
import org.jodconverter.core.office.OfficeException;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.model.ReturnResponse;
import cn.keking.service.FileHandlerService;
import cn.keking.service.FilePreview;
import cn.keking.service.OfficeToPdfService;
import cn.keking.utils.DownloadUtils;
import cn.keking.utils.KkFileUtils;
import cn.keking.utils.OfficeUtils;
import cn.keking.utils.WebUtils;
import cn.keking.web.filter.BaseUrlFilter;

/**
 * Created by kl on 2018/1/17.
 * Content :处理office文件
 */
@Service
public class OfficeFilePreviewImpl implements FilePreview {

    public static final String OFFICE_PREVIEW_TYPE_IMAGE = "image";
    public static final String OFFICE_PREVIEW_TYPE_ALL_IMAGES = "allImages";
    private static final String OFFICE_PASSWORD_MSG = "password";

    private final FileHandlerService fileHandlerService;
    private final OfficeToPdfService officeToPdfService;
    private final OtherFilePreviewImpl otherFilePreview;

    public OfficeFilePreviewImpl(FileHandlerService fileHandlerService, OfficeToPdfService officeToPdfService, OtherFilePreviewImpl otherFilePreview) {
        this.fileHandlerService = fileHandlerService;
        this.officeToPdfService = officeToPdfService;
        this.otherFilePreview = otherFilePreview;
    }

    @Override
    public String filePreviewHandle(String url, Model model, FileAttribute fileAttribute) {
        // 预览Type，参数传了就取参数的，没传取系统默认
        String officePreviewType = fileAttribute.getOfficePreviewType();
        boolean userToken = fileAttribute.getUsePasswordCache();
        String baseUrl = BaseUrlFilter.getBaseUrl();
        String suffix = fileAttribute.getSuffix();  //获取文件后缀
        String fileName = fileAttribute.getName(); //获取文件原始名称
        String filePassword = fileAttribute.getFilePassword(); //获取密码
        boolean forceUpdatedCache=fileAttribute.forceUpdatedCache();  //是否启用强制更新命令
        boolean isHtmlView = fileAttribute.isHtmlView();  //xlsx  转换成html
        String cacheName = fileAttribute.getCacheName();  //转换后的文件名
        String outFilePath = fileAttribute.getOutFilePath();  //转换后生成文件的路径
        if (!"html".equalsIgnoreCase(officePreviewType)) {
            if ("web" .equalsIgnoreCase(ConfigConstants.getOfficeTypeWeb())) {
                if ("xlsx".equalsIgnoreCase(suffix)) {
                    model.addAttribute("pdfUrl", KkFileUtils.htmlEscape(url)); //特殊符号处理
                    return FilePreview.XLSX_FILE_PREVIEW_PAGE;
                }
                if ("csv".equalsIgnoreCase(suffix)) {
                    model.addAttribute("csvUrl", KkFileUtils.htmlEscape(url));
                    return FilePreview.CSV_FILE_PREVIEW_PAGE;
                }
            }
        }
        if (forceUpdatedCache|| !fileHandlerService.listConvertedFiles().containsKey(cacheName) || !ConfigConstants.isCacheEnabled()) {
        // 下载远程文件到本地，总是下载并返回MD5前缀的文件名
        ReturnResponse<String> response = DownloadUtils.downLoad(fileAttribute, fileName);
        if (response.isFailure()) {
            return otherFilePreview.notSupportedFile(model, fileAttribute, response.getMsg());
        }
            String filePath = response.getContent();
            String actualFileName = response.getMsg();  // 下载后的实际文件名（包含MD5前缀）

            // 根据实际下载的文件名重新生成cacheName和outFilePath
            // 这样不同内容的文件会有不同的cache和output文件
            String actualCacheFilePrefixName = actualFileName.substring(0, actualFileName.lastIndexOf(".")) + suffix + ".";
            String actualCacheName = actualCacheFilePrefixName + (isHtmlView ? "html" : "pdf");
            String actualOutFilePath = ConfigConstants.getFileDir() + actualCacheName;

            // 检查基于MD5的缓存是否已存在
            if (!fileHandlerService.listConvertedFiles().containsKey(actualCacheName)) {
                boolean  isPwdProtectedOffice =  OfficeUtils.isPwdProtected(filePath);    // 判断是否加密文件
                if (isPwdProtectedOffice && !StringUtils.hasLength(filePassword)) {
                    // 加密文件需要密码
                    model.addAttribute("needFilePassword", true);
                    return FilePreview.EXEL_FILE_PREVIEW_PAGE;
                } else {
                    if (StringUtils.hasText(actualOutFilePath)) {
                        try {
                            officeToPdfService.openOfficeToPDF(filePath, actualOutFilePath, fileAttribute);
                        } catch (OfficeException e) {
                            if (isPwdProtectedOffice && !OfficeUtils.isCompatible(filePath, filePassword)) {
                                // 加密文件密码错误，提示重新输入
                                model.addAttribute("needFilePassword", true);
                                model.addAttribute("filePasswordError", true);
                                return FilePreview.EXEL_FILE_PREVIEW_PAGE;
                            }
                            return otherFilePreview.notSupportedFile(model, fileAttribute, "抱歉，该文件版本不兼容，文件版本错误。");
                        }
                        if (isHtmlView) {
                            // 对转换后的文件进行操作(改变编码方式)
                            fileHandlerService.doActionConvertedFile(actualOutFilePath);
                        }
                        //是否保留OFFICE源文件
                        if (!fileAttribute.isCompressFile() && ConfigConstants.getDeleteSourceFile()) {
                            KkFileUtils.deleteFileByPath(filePath);
                        }
                        if (userToken || !isPwdProtectedOffice) {
                            // 加入缓存
                            fileHandlerService.addConvertedFile(actualCacheName, fileHandlerService.getRelativePath(actualOutFilePath));
                        }
                    }
                }
            }

            // 使用实际的cacheName和outFilePath更新fileAttribute和model
            cacheName = actualCacheName;
            outFilePath = actualOutFilePath;
        }
        if (!isHtmlView && baseUrl != null && (OfficeFilePreviewImpl.OFFICE_PREVIEW_TYPE_IMAGE.equals(officePreviewType) || OfficeFilePreviewImpl.OFFICE_PREVIEW_TYPE_ALL_IMAGES.equals(officePreviewType))) {
            return OfficeFilePreviewImpl.getPreviewType(model, fileAttribute, officePreviewType, cacheName, outFilePath, fileHandlerService, OfficeFilePreviewImpl.OFFICE_PREVIEW_TYPE_IMAGE, otherFilePreview);
        }
        model.addAttribute("pdfUrl", WebUtils.encodeFileName(cacheName));  //输出转义文件名 方便url识别
        return isHtmlView ? FilePreview.EXEL_FILE_PREVIEW_PAGE : FilePreview.PDF_FILE_PREVIEW_PAGE;
    }

    static String getPreviewType(Model model, FileAttribute fileAttribute, String officePreviewType, String pdfName, String outFilePath, FileHandlerService fileHandlerService, String officePreviewTypeImage, OtherFilePreviewImpl otherFilePreview) {
        String suffix = fileAttribute.getSuffix();
        boolean isPPT = "ppt".equalsIgnoreCase(suffix) || "pptx".equalsIgnoreCase(suffix);
        List<String> imageUrls = null;
        try {
            imageUrls =  fileHandlerService.pdf2jpg(outFilePath,outFilePath, pdfName, fileAttribute);
        } catch (Exception e) {
            Throwable[] throwableArray = ExceptionUtils.getThrowables(e);
            for (Throwable throwable : throwableArray) {
                if (throwable instanceof IOException || throwable instanceof EncryptedDocumentException) {
                    if (e.getMessage().toLowerCase().contains(OfficeFilePreviewImpl.OFFICE_PASSWORD_MSG)) {
                        model.addAttribute("needFilePassword", true);
                        return FilePreview.EXEL_FILE_PREVIEW_PAGE;
                    }
                }
            }
        }
        if (imageUrls == null || imageUrls.size() < 1) {
            return otherFilePreview.notSupportedFile(model, fileAttribute, "office转图片异常，请联系管理员");
        }
        model.addAttribute("imgUrls", imageUrls);
        model.addAttribute("currentUrl", imageUrls.get(0));
        if (officePreviewTypeImage.equals(officePreviewType)) {
            // PPT 图片模式使用专用预览页面
            return (isPPT ? FilePreview.PPT_FILE_PREVIEW_PAGE : FilePreview.OFFICE_PICTURE_FILE_PREVIEW_PAGE);
        } else {
            return FilePreview.PICTURE_FILE_PREVIEW_PAGE;
        }
    }

}
