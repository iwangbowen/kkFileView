package cn.keking.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang3.StringUtils;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.local.LocalConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sun.star.document.UpdateDocMode;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;

/**
 * @author yudian-it
 */
@Component
public class OfficeToPdfService {

    private static final Logger logger = LoggerFactory.getLogger(OfficeToPdfService.class);
    private static final Pattern COMMENT_RANGE_START_PATTERN = Pattern.compile("<w:commentRangeStart[^>]*/>");
    private static final Pattern COMMENT_RANGE_END_PATTERN = Pattern.compile("<w:commentRangeEnd[^>]*/>");
    private static final Pattern COMMENT_REFERENCE_PATTERN = Pattern.compile("<w:commentReference[^>]*/>");
    private static final Pattern ANNOTATION_REFERENCE_PATTERN = Pattern.compile("<w:annotationRef[^>]*/>");
    private static final Pattern COMMENT_RELATIONSHIP_PATTERN = Pattern.compile("<Relationship[^>]*Target=\"[^\"]*comments[^\"]*\"[^>]*/>", Pattern.CASE_INSENSITIVE);
    private static final int BUFFER_SIZE = 8192;

    public void openOfficeToPDF(String inputFilePath, String outputFilePath, FileAttribute fileAttribute) throws OfficeException {
        office2pdf(inputFilePath, outputFilePath, fileAttribute);
    }


    public static void converterFile(File inputFile, String outputFilePathEnd, FileAttribute fileAttribute) throws OfficeException {
        File outputFile = new File(outputFilePathEnd);
        // 假如目标路径不存在,则新建该路径
        if (!outputFile.getParentFile().exists() && !outputFile.getParentFile().mkdirs()) {
            OfficeToPdfService.logger.error("创建目录【{}】失败，请检查目录权限！", outputFilePathEnd);
        }
        try {
            OfficeToPdfService.convertInternal(inputFile, outputFile, fileAttribute, true);
        } catch (OfficeException primaryException) {
            // LibreOffice 24.x may crash when exporting annotations in some documents; retry without them.
            if (Boolean.TRUE.equals(ConfigConstants.getOfficeExportNotes()) && OfficeToPdfService.shouldRetryWithoutNotes(fileAttribute)) {
                OfficeToPdfService.logger.warn("Primary conversion attempt for {} failed when exporting notes. Retrying without notes.", inputFile.getAbsolutePath(), primaryException);
                try {
                    OfficeToPdfService.convertInternal(inputFile, outputFile, fileAttribute, false);
                    OfficeToPdfService.logger.warn("Converted {} without exporting notes because LibreOffice crashed while handling document comments.", inputFile.getAbsolutePath());
                } catch (OfficeException retryException) {
                    boolean sanitizedSuccess = false;
                    File sanitizedCopy = null;
                    try {
                        sanitizedCopy = OfficeToPdfService.tryCreateCommentFreeCopy(inputFile, fileAttribute);
                        if (sanitizedCopy != null) {
                            OfficeToPdfService.logger.warn("Retrying {} conversion after stripping annotations to prevent LibreOffice crashes.", inputFile.getAbsolutePath());
                            OfficeToPdfService.convertInternal(sanitizedCopy, outputFile, fileAttribute, false);
                            OfficeToPdfService.logger.warn("Converted {} by stripping document annotations; preview will omit comments.", inputFile.getAbsolutePath());
                            sanitizedSuccess = true;
                        }
                    } catch (IOException sanitizeException) {
                        retryException.addSuppressed(sanitizeException);
                    } finally {
                        if (sanitizedCopy != null) {
                            try {
                                Files.deleteIfExists(sanitizedCopy.toPath());
                            } catch (IOException deleteException) {
                                retryException.addSuppressed(deleteException);
                            }
                        }
                    }
                    if (!sanitizedSuccess) {
                        retryException.addSuppressed(primaryException);
                        throw retryException;
                    }
                }
            } else {
                throw primaryException;
            }
        }
    }


    public void office2pdf(String inputFilePath, String outputFilePath, FileAttribute fileAttribute) throws OfficeException {
        if (null != inputFilePath) {
            File inputFile = new File(inputFilePath);
            // 判断目标文件路径是否为空
            if (null == outputFilePath) {
                // 转换后的文件路径
                String outputFilePathEnd = OfficeToPdfService.getOutputFilePath(inputFilePath);
                if (inputFile.exists()) {
                    // 找不到源文件, 则返回
                    OfficeToPdfService.converterFile(inputFile, outputFilePathEnd, fileAttribute);
                }
            } else {
                if (inputFile.exists()) {
                    // 找不到源文件, 则返回
                    OfficeToPdfService.converterFile(inputFile, outputFilePath, fileAttribute);
                }
            }
        }
    }

    public static String getOutputFilePath(String inputFilePath) {
        return inputFilePath.replaceAll("."+ OfficeToPdfService.getPostfix(inputFilePath), ".pdf");
    }

    public static String getPostfix(String inputFilePath) {
        return inputFilePath.substring(inputFilePath.lastIndexOf(".") + 1);
    }

    private static void convertInternal(File inputFile, File outputFile, FileAttribute fileAttribute, boolean exportNotes) throws OfficeException {
        LocalConverter.Builder builder = LocalConverter.builder();
        Map<String, Object> loadProperties = OfficeToPdfService.buildLoadProperties(fileAttribute);
        if (!loadProperties.isEmpty()) {
            builder = builder.loadProperties(loadProperties);
        }
        builder = builder.storeProperties(OfficeToPdfService.buildStoreProperties(fileAttribute, exportNotes));
        builder.build().convert(inputFile).to(outputFile).execute();
    }

    private static Map<String, Object> buildStoreProperties(FileAttribute fileAttribute, boolean exportNotes) {
        Map<String, Object> customProperties = new HashMap<>();
        customProperties.put("FilterData", OfficeToPdfService.buildFilterData(fileAttribute, exportNotes));
        return customProperties;
    }

    private static Map<String, Object> buildLoadProperties(FileAttribute fileAttribute) {
        Map<String, Object> loadProperties = new HashMap<>();
        if (fileAttribute != null && StringUtils.isNotBlank(fileAttribute.getFilePassword())) {
            loadProperties.put("Hidden", true);
            loadProperties.put("ReadOnly", true);
            loadProperties.put("UpdateDocMode", UpdateDocMode.NO_UPDATE);
            loadProperties.put("Password", fileAttribute.getFilePassword());
        }
        return loadProperties;
    }

    private static Map<String, Object> buildFilterData(FileAttribute fileAttribute, boolean exportNotes) {
        Map<String, Object> filterData = new HashMap<>();
        filterData.put("Quality", ConfigConstants.getOfficeQuality());
        filterData.put("MaxImageResolution", ConfigConstants.getOfficeMaxImageResolution());
        if (!"false".equals(ConfigConstants.getOfficePageRange())) {
            filterData.put("PageRange", ConfigConstants.getOfficePageRange());
        }
        if (!"false".equals(ConfigConstants.getOfficeWatermark())) {
            filterData.put("Watermark", ConfigConstants.getOfficeWatermark());
        }
        if (Boolean.TRUE.equals(ConfigConstants.getOfficeExportBookmarks())) {
            filterData.put("ExportBookmarks", true);
        }
        if (Boolean.TRUE.equals(ConfigConstants.getOfficeExportNotes()) && exportNotes) {
            filterData.put("ExportNotes", true);
        }
        String pdfPassword = fileAttribute != null ? fileAttribute.getFilePassword() : null;
        boolean hasPdfPassword = StringUtils.isNotBlank(pdfPassword);
        if (Boolean.TRUE.equals(ConfigConstants.getOfficeDocumentOpenPasswords())) {
            filterData.put("EncryptFile", hasPdfPassword);
            if (hasPdfPassword) {
                filterData.put("DocumentOpenPassword", pdfPassword);
            }
        }
        return filterData;
    }

    private static boolean shouldRetryWithoutNotes(FileAttribute fileAttribute) {
        if (fileAttribute == null || StringUtils.isBlank(fileAttribute.getSuffix())) {
            return false;
        }
        String suffix = fileAttribute.getSuffix().toLowerCase(Locale.ROOT);
        return "doc".equals(suffix) || "docx".equals(suffix) || "odt".equals(suffix) || "rtf".equals(suffix);
    }

    private static File tryCreateCommentFreeCopy(File original, FileAttribute fileAttribute) throws IOException {
        if (fileAttribute == null || StringUtils.isBlank(fileAttribute.getSuffix())) {
            return null;
        }
        if (StringUtils.isNotBlank(fileAttribute.getFilePassword())) {
            return null;
        }
        String suffix = fileAttribute.getSuffix().toLowerCase(Locale.ROOT);
        if (!"docx".equals(suffix)) {
            return null;
        }
        return OfficeToPdfService.removeDocxComments(original);
    }

    private static File removeDocxComments(File sourceFile) throws IOException {
        Path tempTarget = Files.createTempFile("kkfileview-clean-", ".docx");
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(sourceFile)));
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tempTarget.toFile())))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                boolean shouldCopy = entryName != null && !(entryName.startsWith("word/") && entryName.contains("comments"));
                if (shouldCopy) {
                    OfficeToPdfService.copyEntryWithoutComments(entry, entryName, zis, zos);
                }
                zis.closeEntry();
            }
        }
        return tempTarget.toFile();
    }

    private static void copyEntryWithoutComments(ZipEntry entry, String entryName, ZipInputStream zis, ZipOutputStream zos) throws IOException {
        ZipEntry newEntry = new ZipEntry(entryName);
        newEntry.setTime(entry.getTime());
        zos.putNextEntry(newEntry);
        if (!entry.isDirectory()) {
            byte[] data = OfficeToPdfService.readZipEntry(zis);
            if (OfficeToPdfService.shouldStripCommentMarkers(entryName)) {
                data = OfficeToPdfService.stripCommentMarkers(entryName, data);
            }
            zos.write(data);
        }
        zos.closeEntry();
    }

    private static boolean shouldStripCommentMarkers(String entryName) {
        if (!entryName.startsWith("word/")) {
            return false;
        }
        return entryName.endsWith(".xml") || entryName.endsWith(".rels");
    }

    private static byte[] stripCommentMarkers(String entryName, byte[] data) {
        String xml = new String(data, StandardCharsets.UTF_8);
        String cleaned = OfficeToPdfService.COMMENT_RANGE_START_PATTERN.matcher(xml).replaceAll("");
        cleaned = OfficeToPdfService.COMMENT_RANGE_END_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = OfficeToPdfService.COMMENT_REFERENCE_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = OfficeToPdfService.ANNOTATION_REFERENCE_PATTERN.matcher(cleaned).replaceAll("");
        if (entryName.endsWith(".rels")) {
            cleaned = OfficeToPdfService.COMMENT_RELATIONSHIP_PATTERN.matcher(cleaned).replaceAll("");
        }
        return cleaned.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] readZipEntry(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[OfficeToPdfService.BUFFER_SIZE];
        int read;
        while ((read = zis.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, read);
        }
        return buffer.toByteArray();
    }

}
