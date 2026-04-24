package cn.keking.service;

import cn.keking.config.ConfigConstants;
import cn.keking.utils.FileConvertStatusManager;
import cn.keking.utils.WebUtils;
import cn.keking.web.filter.BaseUrlFilter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.imaging.Imaging;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * TIF文件转换服务 - 虚拟线程版本 (JDK 21+)
 */
@Component
public class TifToPdfService {

    private static final Logger logger = LoggerFactory.getLogger(TifToPdfService.class);
    private static final String FILE_DIR = ConfigConstants.getFileDir();

    // 虚拟线程执行器
    private ExecutorService virtualThreadExecutor;

    @PostConstruct
    public void init() {
        try {
            // 创建虚拟线程执行器
            this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
            logger.info("TIF转换虚拟线程执行器初始化完成");
        } catch (Exception e) {
            logger.error("虚拟线程执行器初始化失败", e);
            // 降级为固定线程池
            this.virtualThreadExecutor = Executors.newFixedThreadPool(
                    Math.min(getMaxConcurrentConversions(), Runtime.getRuntime().availableProcessors() * 2)
            );
            logger.warn("使用固定线程池作为降级方案");
        }
    }

    /**
     * TIF转JPG - 虚拟线程版本
     */
    public List<String> convertTif2Jpg(String strInputFile, String strOutputFile,String fileName,
                                       String cacheName,
                                       boolean forceUpdatedCache) throws Exception {
        Instant startTime = Instant.now();
        try {
            List<String> result = performTifToJpgConversionVirtual(
                    strInputFile, strOutputFile, forceUpdatedCache,fileName,cacheName
            );

            Duration elapsedTime = Duration.between(startTime, Instant.now());
            boolean success = result != null && !result.isEmpty();

            logger.info("TIF转换{} - 文件: {}, 耗时: {}ms, 页数: {}",
                    success ? "成功" : "失败", fileName, elapsedTime.toMillis(),
                    result != null ? result.size() : 0);

            return result != null ? result : Collections.emptyList();

        } catch (Exception e) {
            logger.error("TIF转JPG失败: {}, 耗时: {}ms", fileName,
                    Duration.between(startTime, Instant.now()).toMillis(), e);
            throw e;
        }
    }

    /**
     * 虚拟线程执行TIF转JPG转换
     */
    private List<String> performTifToJpgConversionVirtual(String strInputFile, String strOutputFile,
                                                          boolean forceUpdatedCache,String fileName,
                                                          String cacheName) throws Exception {
        Instant totalStart = Instant.now();

        String baseUrl = BaseUrlFilter.getBaseUrl();
        String outputDirPath = strOutputFile.substring(0, strOutputFile.lastIndexOf('.'));

        File tiffFile = new File(strInputFile);
        if (!tiffFile.exists()) {
            throw new FileNotFoundException("文件不存在: " + strInputFile);
        }

        File outputDir = new File(outputDirPath);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("创建目录失败: " + outputDirPath);
        }
        FileConvertStatusManager.updateProgress(cacheName, "正在转换TIF为PDF", 30);
        // 加载所有图片
        List<BufferedImage> images;
        try {
            images = Imaging.getAllBufferedImages(tiffFile);
           // logger.info("TIF文件加载完成，共{}页，文件: {}",images.size(), strInputFile);
        } catch (IOException e) {
            handleImagingException(e, strInputFile);
            throw e;
        }

        int pageCount = images.size();
        if (pageCount == 0) {
            logger.warn("TIF文件没有可转换的页面: {}", strInputFile);
            return Collections.emptyList();
        }
        FileConvertStatusManager.updateProgress(cacheName, "正在转换TIF为PDF", 50);
        List<String> result = convertPagesVirtualThreads(images, outputDirPath, baseUrl, forceUpdatedCache);

        Duration totalTime = Duration.between(totalStart, Instant.now());
        logger.info("TIF转换PNG完成，总页数: {}, 总耗时: {}ms", pageCount, totalTime.toMillis());
        FileConvertStatusManager.updateProgress(cacheName, "正在转换TIF为PDF", 100);
        return result;
    }

    /**
     * 使用虚拟线程并行转换页面
     */
    private List<String> convertPagesVirtualThreads(List<BufferedImage> images, String outputDirPath,
                                                    String baseUrl, boolean forceUpdatedCache) {
        int pageCount = images.size();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // 用于收集结果的并发列表
        List<String> imageUrls = Collections.synchronizedList(new ArrayList<>(pageCount));

        Instant startTime = Instant.now();

        try {
            // 使用虚拟线程并行处理所有页面
            List<CompletableFuture<Void>> futures = IntStream.range(0, pageCount)
                    .mapToObj(pageIndex -> CompletableFuture.runAsync(() -> {
                        try {
                            BufferedImage image = images.get(pageIndex);

                            // 使用PNG格式，质量更好
                            String fileName = outputDirPath + File.separator + pageIndex + ".png";
                            File outputFile = new File(fileName);

                            if (forceUpdatedCache || !outputFile.exists()) {
                                // 创建目录
                                File parentDir = outputFile.getParentFile();
                                if (!parentDir.exists()) {
                                    parentDir.mkdirs();
                                }

                                boolean writeSuccess = ImageIO.write(image, "png", outputFile);
                                if (!writeSuccess) {
                                    throw new IOException("无法写入PNG格式");
                                }
                                successCount.incrementAndGet();
                            } else {
                                skipCount.incrementAndGet();
                            }

                            // 构建URL
                            String relativePath = fileName.replace(FILE_DIR, "");
                            String url = baseUrl + WebUtils.encodeFileName(relativePath);
                            imageUrls.add(url);

                        } catch (Exception e) {
                            logger.error("转换页 {} 失败: {}", pageIndex, e.getMessage());
                            errorCount.incrementAndGet();
                        }
                    }, virtualThreadExecutor))
                    .toList();

            // 等待所有任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .completeOnTimeout(null, getConversionTimeout(), TimeUnit.SECONDS)
                    .join();

        } catch (Exception e) {
            logger.error("虚拟线程并行转换异常", e);
        }

        Duration elapsedTime = Duration.between(startTime, Instant.now());

        logger.info("TIF虚拟线程转换统计: 成功={}, 跳过={}, 失败={}, 总页数={}, 耗时={}ms",successCount.get(), skipCount.get(), errorCount.get(), pageCount,
                elapsedTime.toMillis());

        // 按页码排序
        return imageUrls.stream()
                .sorted(Comparator.comparing(url -> {
                    // 从URL中提取页码进行排序
                    String fileName = url.substring(url.lastIndexOf('/') + 1);
                    return Integer.parseInt(fileName.substring(0, fileName.lastIndexOf('.')));
                }))
                .collect(Collectors.toList());
    }

    /**
     * TIF转PDF - 虚拟线程版本
     */
    public void convertTif2Pdf(String strJpgFile, String strPdfFile,String fileName,
                               String cacheName,
                               boolean forceUpdatedCache) throws Exception {
        Instant startTime = Instant.now();

        try {
            File pdfFile = new File(strPdfFile);

            // 检查缓存
            if (!forceUpdatedCache && pdfFile.exists()) {
                logger.info("PDF文件已存在，跳过转换: {}", strPdfFile);
                return;
            }
            FileConvertStatusManager.updateProgress(cacheName, "正在转换TIF为PDF", 30);
            boolean result = performTifToPdfConversionVirtual(strJpgFile, strPdfFile,fileName,cacheName);
            Duration elapsedTime = Duration.between(startTime, Instant.now());

            logger.info("TIF转PDF{} - 文件: {}, 耗时: {}ms",
                    result ? "成功" : "失败", fileName, elapsedTime.toMillis());

            if (!result) {
                throw new Exception("TIF转PDF失败");
            }
        } catch (Exception e) {
            logger.error("TIF转PDF失败: {}, 耗时: {}ms", fileName,
                    Duration.between(startTime, Instant.now()).toMillis(), e);
            throw e;
        }
    }

    /**
     * 虚拟线程执行TIF转PDF转换（保持顺序）
     */
    private boolean performTifToPdfConversionVirtual(String strJpgFile, String strPdfFile,String fileName,String cacheName) throws Exception {
        Instant totalStart = Instant.now();

        File tiffFile = new File(strJpgFile);

        try (PDDocument document = new PDDocument()) {
            // 直接使用Imaging获取所有图像
            List<BufferedImage> images = Imaging.getAllBufferedImages(tiffFile);

            if (images.isEmpty()) {
                logger.warn("TIFF文件没有可转换的页面: {}", strJpgFile);
                return false;
            }
            FileConvertStatusManager.updateProgress(cacheName, "正在转换TIF为PDF", 30);
            int pageCount = images.size();
            AtomicInteger processedCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            // 创建页面处理结果的列表
            List<CompletableFuture<ProcessedPageResult>> futures = new ArrayList<>(pageCount);
            FileConvertStatusManager.updateProgress(cacheName, "正在转换TIF为PDF", 50);
            // 为每个页面创建处理任务
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                final int currentPageIndex = pageIndex;
                BufferedImage originalImage = images.get(pageIndex);

                CompletableFuture<ProcessedPageResult> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        // 处理图像（耗时的操作）
                        BufferedImage processedImage = processImageVirtualOptimized(originalImage);

                        // 返回处理结果，包含页码和处理后的图像
                        return new ProcessedPageResult(currentPageIndex, processedImage);
                    } catch (Exception e) {
                        logger.error("异步处理页 {} 失败", currentPageIndex + 1, e);
                        errorCount.incrementAndGet();
                        return null;
                    }
                }, virtualThreadExecutor);

                futures.add(future);
            }
            FileConvertStatusManager.updateProgress(cacheName, "正在转换TIF为PDF", 70);
            // 等待所有任务完成
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );

            // 设置超时
            allFutures.completeOnTimeout(null, getConversionTimeout(), TimeUnit.SECONDS).join();

            // 按顺序收集处理结果
            List<ProcessedPageResult> results = new ArrayList<>(pageCount);
            for (CompletableFuture<ProcessedPageResult> future : futures) {
                ProcessedPageResult result = future.get();
                if (result != null) {
                    results.add(result);
                }
            }

            // 按页码排序（确保顺序）
            results.sort(Comparator.comparingInt(ProcessedPageResult::pageIndex));

            // 按顺序添加到PDF文档
            for (ProcessedPageResult result : results) {
                try {
                    // 创建页面
                    PDPage page = new PDPage(PDRectangle.A4);
                    document.addPage(page);

                    // 转换为PDImageXObject
                    PDImageXObject pdImage = LosslessFactory.createFromImage(document, result.processedImage());

                    // 计算位置并绘制图像
                    float[] position = calculateImagePositionOptimized(page, pdImage);
                    try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                        contentStream.drawImage(pdImage, position[0], position[1], position[2], position[3]);
                    }

                    // 释放资源
                    result.processedImage().flush();
                    processedCount.incrementAndGet();

                } catch (Exception e) {
                    logger.error("添加页 {} 到PDF失败", result.pageIndex() + 1, e);
                    errorCount.incrementAndGet();
                }
            }
            FileConvertStatusManager.updateProgress(cacheName, "正在转换TIF为PDF", 100);
            // 保存PDF
            document.save(strPdfFile);

          //  Duration totalTime = Duration.between(totalStart, Instant.now());
            //logger.info("PDF异步转换完成: {}, 总页数: {}, 成功: {}, 失败: {}, 总耗时: {}ms",  strPdfFile, pageCount, processedCount.get(), errorCount.get(), totalTime.toMillis());

            return processedCount.get() > 0;
        }
    }

    /**
         * 页面处理结果类
         */
        private record ProcessedPageResult(int pageIndex, BufferedImage processedImage) {
    }

    /**
     * 优化的图像处理方法
     */
    private BufferedImage processImageVirtualOptimized(BufferedImage original) {
        int targetDPI = 150;
        float a4WidthInch = 8.27f;
        float a4HeightInch = 11.69f;

        int maxWidth = (int) (a4WidthInch * targetDPI);
        int maxHeight = (int) (a4HeightInch * targetDPI);

        if (original.getWidth() <= maxWidth && original.getHeight() <= maxHeight) {
            return original;
        }

        double scaleX = (double) maxWidth / original.getWidth();
        double scaleY = (double) maxHeight / original.getHeight();
        double scale = Math.min(scaleX, scaleY);

        int newWidth = (int) (original.getWidth() * scale);
        int newHeight = (int) (original.getHeight() * scale);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        g.drawImage(original, 0, 0, newWidth, newHeight, null);
        g.dispose();

        return resized;
    }

    /**
     * 优化的位置计算方法
     */
    private float[] calculateImagePositionOptimized(PDPage page, PDImageXObject pdImage) {
        float margin = 5;
        float pageWidth = page.getMediaBox().getWidth() - 2 * margin;
        float pageHeight = page.getMediaBox().getHeight() - 2 * margin;

        float imageWidth = pdImage.getWidth();
        float imageHeight = pdImage.getHeight();

        float scale = Math.min(pageWidth / imageWidth, pageHeight / imageHeight);
        float scaledWidth = imageWidth * scale;
        float scaledHeight = imageHeight * scale;

        float x = (pageWidth - scaledWidth) / 2 + margin;
        float y = (pageHeight - scaledHeight) / 2 + margin;

        return new float[]{x, y, scaledWidth, scaledHeight};
    }

    /**
     * 异常处理
     */
    private void handleImagingException(IOException e, String filePath) {
        String message = e.getMessage();
        if (message != null && message.contains("Only sequential, baseline JPEGs are supported at the moment")) {
            logger.warn("不支持的非基线JPEG格式，文件：{}", filePath, e);
        } else {
            logger.error("TIF转JPG异常，文件路径：{}", filePath, e);
        }
    }

    /**
     * 获取转换超时时间
     */
    private long getConversionTimeout() {
        try {
            String timeoutStr = ConfigConstants.getTifTimeout();
            if (timeoutStr != null && !timeoutStr.trim().isEmpty()) {
                long timeout = Long.parseLong(timeoutStr);
                if (timeout > 0) {
                    return Math.min(timeout, 600L);
                }
            }
        } catch (NumberFormatException e) {
            logger.warn("解析TIF转换超时时间失败，使用默认值300秒", e);
        }
        return 300L;
    }

    /**
     * 获取最大并发数
     */
    private int getMaxConcurrentConversions() {
        try {
            int maxConcurrent = ConfigConstants.getTifThread();
            if (maxConcurrent > 0) {
                return Math.min(maxConcurrent, 50);
            }
        } catch (Exception e) {
            logger.error("获取并发数配置失败，使用默认值", e);
        }
        return 4;
    }

    @PreDestroy
    public void shutdown() {
        logger.info("开始关闭TIF转换服务...");

        if (virtualThreadExecutor != null && !virtualThreadExecutor.isShutdown()) {
            try {
                virtualThreadExecutor.shutdown();
                if (!virtualThreadExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    virtualThreadExecutor.shutdownNow();
                }
                logger.info("虚拟线程执行器已关闭");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                virtualThreadExecutor.shutdownNow();
            }
        }

        logger.info("TIF转换服务已关闭");
    }
}