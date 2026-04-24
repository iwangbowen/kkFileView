package cn.keking.service;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.utils.FileConvertStatusManager;
import cn.keking.utils.RemoveSvgAdSimple;
import com.aspose.cad.*;
import com.aspose.cad.fileformats.cad.CadDrawTypeMode;
import com.aspose.cad.fileformats.tiff.enums.TiffExpectedFormat;
import com.aspose.cad.imageoptions.*;
import jakarta.annotation.PreDestroy;
import org.jodconverter.core.util.OSUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * CAD文件转换服务 - 精简版
 * 支持实时状态跟踪和状态锁定机制
 */
@Component
public class CadToPdfService {
    private static final Logger logger = LoggerFactory.getLogger(CadToPdfService.class);

    // 使用虚拟线程执行器
    private final ExecutorService virtualThreadExecutor;
    // 存储正在运行的转换任务
    private final ConcurrentHashMap<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
    // 存储任务的完成状态
    private final ConcurrentHashMap<String, AtomicBoolean> taskCompletionStatus = new ConcurrentHashMap<>();
    // 并发控制信号量
    private final Semaphore concurrentLimit;
    // 转换超时时间（秒）
    private final long conversionTimeout;

    public CadToPdfService() {
        int maxConcurrent = getConcurrentLimit();
        this.concurrentLimit = new Semaphore(maxConcurrent);
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.conversionTimeout = getConversionTimeout();

        logger.info("CAD转换服务初始化完成，最大并发数: {}，转换超时: {}秒", maxConcurrent, conversionTimeout);
    }

    /**
     * CAD文件转换 - 异步版本
     */
    public CompletableFuture<Boolean> cadToPdfAsync(String inputFilePath, String outputFilePath, String cacheName,
                                                    String cadPreviewType, FileAttribute fileAttribute) {
        return submitConversionTask(cacheName, outputFilePath,
                () -> performCadConversion(inputFilePath, outputFilePath, cacheName, cadPreviewType, fileAttribute),
                true);
    }

    /**
     * cadViewer转换
     */
    public CompletableFuture<Boolean> cadViewerConvert(String sDwgFile, String outFilePath, String cachefilepath,
                                                       String cadPreviewType, String cacheName) {
        return submitConversionTask(cacheName, outFilePath + "/" + cacheName,
                () -> executeCadViewerConversion(sDwgFile, outFilePath, cachefilepath, cadPreviewType, cacheName),
                false);
    }

    /**
     * 通用的转换任务提交方法
     */
    private CompletableFuture<Boolean> submitConversionTask(String cacheName, String outputFilePath,
                                                            Supplier<Boolean> conversionSupplier,
                                                            boolean deleteOnTimeout) {
        FileConvertStatusManager.startConvert(cacheName);
        CompletableFuture<Boolean> taskFuture = new CompletableFuture<>();
        taskCompletionStatus.put(cacheName, new AtomicBoolean(false));

        Future<?> future = virtualThreadExecutor.submit(() -> {
            try {
                FileConvertStatusManager.updateProgress(cacheName, "正在启动转换任务", 5);
                boolean result = conversionSupplier.get();
                if (result) {
                    taskFuture.complete(true);
                    taskCompletionStatus.get(cacheName).set(true);
                } else {
                    taskFuture.complete(false);
                }
            } catch (Exception e) {
                logger.error("CAD转换任务执行失败: {}", cacheName, e);
                FileConvertStatusManager.markError(cacheName, "转换过程异常: " + e.getMessage());
                taskFuture.completeExceptionally(e);
            } finally {
                runningTasks.remove(cacheName);
                taskCompletionStatus.remove(cacheName);
            }
        });

        runningTasks.put(cacheName, future);
        scheduleTimeoutCheck(cacheName, taskFuture, future, outputFilePath, deleteOnTimeout);

        return taskFuture;
    }

    /**
     * 调度超时检查
     */
    private void scheduleTimeoutCheck(String fileName, CompletableFuture<Boolean> taskFuture,
                                      Future<?> future, String outputFilePath, boolean deleteOnTimeout) {
        virtualThreadExecutor.submit(() -> {
            try {
                taskFuture.get(conversionTimeout, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                handleConversionTimeout(fileName, taskFuture, future, outputFilePath, deleteOnTimeout);
            } catch (Exception e) {
                handleConversionException(fileName, taskFuture, e);
            }
        });
    }

    /**
     * 处理转换超时
     */
    private void handleConversionTimeout(String fileName, CompletableFuture<Boolean> taskFuture,
                                         Future<?> future, String outputFilePath, boolean deleteOnTimeout) {
        logger.error("CAD转换超时，取消任务: {}, 超时时间: {}秒", fileName, conversionTimeout);
        FileConvertStatusManager.markTimeout(fileName);
        cancelRunningTask(fileName, future);
        if (deleteOnTimeout) {
            deleteIncompleteFile(outputFilePath);
        }
        taskFuture.complete(false);
    }

    /**
     * 处理转换异常
     */
    private void handleConversionException(String fileName, CompletableFuture<Boolean> taskFuture, Exception e) {
        logger.error("CAD转换异常: {}", fileName, e);
        FileConvertStatusManager.markError(fileName, "转换任务异常: " + e.getMessage());
        taskFuture.complete(false);
    }

    /**
     * 执行实际的CAD转换逻辑
     */
    private boolean performCadConversion(String inputFilePath, String outputFilePath, String cacheName,
                                         String cadPreviewType, FileAttribute fileAttribute) {
        return executeWithConcurrencyControl(cacheName, () -> {
            long totalStartTime = System.currentTimeMillis();
            try {
                if (!validateInputParameters(inputFilePath, outputFilePath, cadPreviewType)) {
                    FileConvertStatusManager.markError(cacheName, "文件参数验证失败");
                    return false;
                }

                FileConvertStatusManager.updateProgress(cacheName, "正在准备输出目录", 30);
                createOutputDirectoryIfNeeded(outputFilePath, fileAttribute.isCompressFile());

                FileConvertStatusManager.updateProgress(cacheName, "正在加载CAD文件", 40);
                try (Image cadImage = Image.load(inputFilePath, createLoadOptions())) {
                    FileConvertStatusManager.updateProgress(cacheName, "CAD文件加载完成，开始渲染", 50);

                    CadRasterizationOptions rasterizationOptions = createRasterizationOptions(cadImage);
                    Object options = createConversionOptions(cadPreviewType, rasterizationOptions);

                    FileConvertStatusManager.updateProgress(cacheName, "正在生成输出文件", 80);
                    saveConvertedFile(outputFilePath, cadImage, options);

                    FileConvertStatusManager.updateProgress(cacheName, "文件转换完成", 90);

                    if ("svg".equalsIgnoreCase(cadPreviewType) && ConfigConstants.getCadwatermark()) {
                       // postProcessSvgFile(outputFilePath);
                    }

                    FileConvertStatusManager.updateProgress(cacheName, "转换成功", 100);
                    FileConvertStatusManager.convertSuccess(cacheName);
                    return true;
                }
            } catch (Exception e) {
                logger.error("CAD转换执行失败: {}", cacheName, e);
                FileConvertStatusManager.markError(cacheName, "转换失败: " + e.getMessage());
                deleteIncompleteFile(outputFilePath);
                return false;
            }
        }, "正在启动转换", "已获取转换资源，开始转换");
    }

    /**
     * 执行外部CAD转换器转换
     */
    private boolean executeCadViewerConversion(String sDwgFile, String outFilePath, String cachefilepath,
                                               String cadPreviewType, String cacheName) {
        return executeWithConcurrencyControl(cacheName, () -> {
            try {
                if (!validateInputParameters(sDwgFile, outFilePath + "/" + cacheName, cadPreviewType)) {
                    FileConvertStatusManager.markError(cacheName, "文件参数验证失败");
                    return false;
                }

                FileConvertStatusManager.updateProgress(cacheName, "正在准备输出目录", 30);
                createOutputDirectoryForExternal(outFilePath, cacheName);

                FileConvertStatusManager.updateProgress(cacheName, "正在启动外部CAD转换器", 40);
                String result = executeExternalCadViewer(sDwgFile, outFilePath, cachefilepath, cadPreviewType, cacheName);

                if (result != null && result.contains("Elapsed Time")) {
                    FileConvertStatusManager.updateProgress(cacheName, "转换成功", 100);
                    FileConvertStatusManager.convertSuccess(cacheName);
                    return true;
                } else {
                    FileConvertStatusManager.markError(cacheName, "外部转换失败: " + result);
                    return false;
                }
            } catch (Exception e) {
                logger.error("外部CAD转换执行失败: {}", cacheName, e);
                FileConvertStatusManager.markError(cacheName, "外部转换失败: " + e.getMessage());
                return false;
            }
        }, "正在启动外部CAD转换", "已获取转换资源，开始外部转换");
    }

    /**
     * 带并发控制的执行方法
     */
    private boolean executeWithConcurrencyControl(String cacheName, Supplier<Boolean> task,
                                                  String startMessage, String acquiredMessage) {
        try {
            if (!concurrentLimit.tryAcquire(30, TimeUnit.SECONDS)) {
                FileConvertStatusManager.updateProgress(cacheName, "系统繁忙，等待资源中...", 15);
                throw new TimeoutException("系统繁忙，请稍后重试");
            }

            FileConvertStatusManager.updateProgress(cacheName, acquiredMessage, 20);
            return task.get();
        } catch (TimeoutException e) {
            FileConvertStatusManager.markError(cacheName, "获取转换资源超时");
            return false;
        } catch (Exception e) {
            FileConvertStatusManager.markError(cacheName, "转换过程异常: " + e.getMessage());
            return false;
        } finally {
            concurrentLimit.release();
        }
    }

    /**
     * 创建转换选项
     */
    private Object createConversionOptions(String cadPreviewType, CadRasterizationOptions rasterizationOptions) {
        return switch (cadPreviewType.toLowerCase()) {
            case "svg" -> createSvgOptions(rasterizationOptions);
            case "pdf" -> createPdfOptions(rasterizationOptions);
            case "tif", "tiff" -> createTiffOptions(rasterizationOptions);
            default -> throw new IllegalArgumentException("不支持的预览类型: " + cadPreviewType);
        };
    }

    /**
     * 执行外部CAD转换器
     */
    private String executeExternalCadViewer(String sDwgFile, String outFilePath, String cachefilepath,
                                            String cadPreviewType, String cacheName) throws Exception {
        boolean isWindows = OSUtils.IS_OS_WINDOWS;
        String encoding = isWindows ? "gbk" : "utf-8";
        outFilePath = outFilePath.replace(cacheName, "");

        List<String> command = buildExternalCommand(sDwgFile, outFilePath, cachefilepath, cadPreviewType, cacheName, isWindows);

        FileConvertStatusManager.updateProgress(cacheName, "正在执行外部转换器", 60);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<String> conversionFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return executeProcessWithVirtualThreads(command, cachefilepath, encoding);
                } catch (Exception e) {
                    throw new CompletionException("外部CAD转换失败", e);
                }
            }, executor);

            FileConvertStatusManager.updateProgress(cacheName, "正在等待转换完成", 80);
            return conversionFuture.get(Integer.parseInt(ConfigConstants.getCadTimeout()), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new Exception("外部转换超时，已终止任务", e);
        }
    }

    /**
     * 构建外部命令
     */
    private List<String> buildExternalCommand(String sDwgFile, String outFilePath, String cachefilepath,
                                              String cadPreviewType, String cacheName, boolean isWindows) {
        List<String> command = new ArrayList<>();
        command.add(isWindows ? cachefilepath + "cadviewer.exe" : "./cadviewer");
        command.add("-i=\"" + sDwgFile + "\"");
        command.add("-o=\"" + outFilePath + "/" + cacheName + "\"");
        command.add("-f=\"" + cadPreviewType + "\"");
        command.add("-basic");
        command.add("-lpath=\"" + cachefilepath + "\"");
        command.add("-xpath=\"" + cachefilepath + "files/\"");
        command.add("-fpath=\"" + cachefilepath + "fonts/\"");
        return command;
    }

    // ============== 工具方法 ==============

    private boolean validateInputParameters(String inputFilePath, String outputFilePath, String cadPreviewType) {
        if (inputFilePath == null || inputFilePath.trim().isEmpty() || outputFilePath == null || outputFilePath.trim().isEmpty()) {
            return false;
        }
        File inputFile = new File(inputFilePath);
        return inputFile.exists() && isSupportedPreviewType(cadPreviewType);
    }

    private boolean isSupportedPreviewType(String previewType) {
        return switch (previewType.toLowerCase()) {
            case "svg", "pdf", "tif", "tiff" -> true;
            default -> false;
        };
    }

    private void createOutputDirectoryIfNeeded(String outputFilePath, boolean isCompressFile) {
        if (isCompressFile) {
            createDirectory(new File(outputFilePath).getParentFile());
        }
    }

    private void createOutputDirectoryForExternal(String outputFilePath, String cacheName) {
        createDirectory(new File(outputFilePath.replace(cacheName, "")));
    }

    private void createDirectory(File dir) {
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("无法创建输出目录: " + dir.getAbsolutePath());
        }
    }

    private LoadOptions createLoadOptions() {
        LoadOptions opts = new LoadOptions();
        opts.setSpecifiedEncoding(CodePages.SimpChinese);
        return opts;
    }

    private CadRasterizationOptions createRasterizationOptions(Image cadImage) {
        RasterizationQuality quality = new RasterizationQuality();
        RasterizationQualityValue highQuality = RasterizationQualityValue.High;
        quality.setArc(highQuality);
        quality.setHatch(highQuality);
        quality.setText(highQuality);
        quality.setOle(highQuality);
        quality.setObjectsPrecision(highQuality);
        quality.setTextThicknessNormalization(true);

        CadRasterizationOptions options = new CadRasterizationOptions();
        options.setBackgroundColor(Color.getWhite());
        options.setPageWidth(cadImage.getWidth());
        options.setPageHeight(cadImage.getHeight());
        options.setUnitType(cadImage.getUnitType());
        options.setAutomaticLayoutsScaling(false);
        options.setNoScaling(false);
        options.setQuality(quality);
        options.setDrawType(CadDrawTypeMode.UseObjectColor);
        options.setExportAllLayoutContent(true);
        options.setVisibilityMode(VisibilityMode.AsScreen);
        return options;
    }

    private SvgOptions createSvgOptions(CadRasterizationOptions rasterizationOptions) {
        SvgOptions options = new SvgOptions();
        options.setVectorRasterizationOptions(rasterizationOptions);
        return options;
    }

    private PdfOptions createPdfOptions(CadRasterizationOptions rasterizationOptions) {
        PdfOptions options = new PdfOptions();
        options.setVectorRasterizationOptions(rasterizationOptions);
        return options;
    }

    private TiffOptions createTiffOptions(CadRasterizationOptions rasterizationOptions) {
        return new TiffOptions(TiffExpectedFormat.TiffJpegRgb);
    }

    private void saveConvertedFile(String outputFilePath, Image cadImage, Object options) throws IOException {
        try (OutputStream outputStream = new FileOutputStream(outputFilePath)) {
            switch (options) {
                case SvgOptions svgOptions -> cadImage.save(outputStream, svgOptions);
                case PdfOptions pdfOptions -> cadImage.save(outputStream, pdfOptions);
                case TiffOptions tiffOptions -> cadImage.save(outputStream, tiffOptions);
                default -> throw new IllegalArgumentException("不支持的选项类型");
            }
        }
    }

    private int getConcurrentLimit() {
        try {
            int threadCount = ConfigConstants.getCadThread();
            if (threadCount <= 0) {
                return Math.max(1, Runtime.getRuntime().availableProcessors());
            }
            return Math.min(threadCount, Runtime.getRuntime().availableProcessors() * 4);
        } catch (Exception e) {
            return Math.max(1, Runtime.getRuntime().availableProcessors());
        }
    }

    private long getConversionTimeout() {
        try {
            long timeout = Long.parseLong(ConfigConstants.getCadTimeout());
            return timeout > 0 ? timeout : 300L;
        } catch (NumberFormatException e) {
            return 300L;
        }
    }

    private void postProcessSvgFile(String outputFilePath) {
        try {
            RemoveSvgAdSimple.removeSvgAdFromFile(outputFilePath);
        } catch (Exception e) {
            logger.warn("SVG文件后处理失败: {}", outputFilePath, e);
        }
    }

    private void cancelRunningTask(String fileName, Future<?> future) {
        if (future != null && future.cancel(true)) {
            logger.info("成功取消转换任务: {}", fileName);
        }
    }

    private void deleteIncompleteFile(String filePath) {
        File file = new File(filePath);
        if (file.exists() && !file.delete()) {
            logger.warn("无法删除不完整文件: {}", filePath);
        }
    }

    // ============== 外部进程执行相关方法 ==============

    private static String executeProcessWithVirtualThreads(List<String> command, String workingDir, String encoding) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (!OSUtils.IS_OS_WINDOWS && !"false".equals(workingDir)) {
            processBuilder.directory(new File(workingDir));
        }
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        try {
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return readProcessOutput(process, encoding);
                } catch (IOException e) {
                    throw new CompletionException("读取进程输出失败", e);
                }
            });

            process.waitFor();
            return outputFuture.get();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static String readProcessOutput(Process process, String encoding) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), encoding))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }
        return output.toString();
    }

    // ============== 公共方法 ==============

    public void cancelConversion(String fileName) {
        Future<?> future = runningTasks.get(fileName);
        if (future != null && future.cancel(true)) {
            logger.info("成功取消转换任务: {}", fileName);
            runningTasks.remove(fileName);
            FileConvertStatusManager.markError(fileName, "转换已取消");
        }
    }



    @PreDestroy
    public void shutdown() {
        logger.info("开始关闭CAD转换服务，正在运行的任务数: {}", runningTasks.size());
        runningTasks.keySet().forEach(this::cancelConversion);

        if (virtualThreadExecutor != null && !virtualThreadExecutor.isShutdown()) {
            virtualThreadExecutor.shutdown();
            try {
                if (!virtualThreadExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    virtualThreadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                virtualThreadExecutor.shutdownNow();
            }
        }
    }
}