package cn.keking.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 文件转换状态管理器（增强版）
 * 支持实时状态跟踪和状态锁定机制
 */
public class FileConvertStatusManager {

    // 存储转换状态：key=文件名，value=转换状态对象
    private static final ConcurrentMap<String, ConvertState> STATUS_MAP = new ConcurrentHashMap<>();

    // 记录最终状态（超时或异常），防止重复转换
    private static final ConcurrentMap<String, Status> FINAL_STATUS_MAP = new ConcurrentHashMap<>();

    /**
     * 开始转换，创建初始状态
     * @param fileName 文件名
     */
    public static void startConvert(String fileName) {
        STATUS_MAP.putIfAbsent(fileName, new ConvertState(Status.CONVERTING, "等待转换", 0));
        // 清除可能存在的最终状态，因为要开始新的转换
        FINAL_STATUS_MAP.remove(fileName);
    }

    /**
     * 更新转换进度
     * @param fileName 文件名
     * @param message 状态消息
     * @param progress 进度百分比(0-100)
     */
    public static void updateProgress(String fileName, String message, int progress) {
        STATUS_MAP.computeIfPresent(fileName, (key, state) -> {
            state.update(message, progress);
            logger.debug("更新转换进度: {} -> {} ({}%)", fileName, message, progress);
            return state;
        });
    }

    /**
     * 标记转换超时 - 记录为最终状态
     * @param fileName 文件名
     */
    public static void markTimeout(String fileName) {
        STATUS_MAP.put(fileName, new ConvertState(Status.TIMEOUT, "转换超时，请重试", 0));
        // 记录为最终状态
        FINAL_STATUS_MAP.put(fileName, Status.TIMEOUT);
        logger.warn("标记文件转换超时: {}", fileName);
    }

    /**
     * 标记转换失败 - 记录为最终状态
     * @param fileName 文件名
     * @param errorMessage 错误信息
     */
    public static void markError(String fileName, String errorMessage) {
        STATUS_MAP.put(fileName, new ConvertState(Status.FAILED, errorMessage, 0));
        // 记录为最终状态
        FINAL_STATUS_MAP.put(fileName, Status.FAILED);
        logger.warn("标记文件转换失败: {}, 错误: {}", fileName, errorMessage);
    }

    /**
     * 查询文件转换状态
     * @param fileName 文件名
     * @return 转换状态对象，如果不存在返回null
     */
    public static ConvertStatus getConvertStatus(String fileName) {
        // 先检查是否有最终状态
        Status finalStatus = FINAL_STATUS_MAP.get(fileName);
        if ((finalStatus == Status.TIMEOUT || finalStatus == Status.FAILED)) {
            ConvertState state = STATUS_MAP.get(fileName);
            if (state == null) {
                // 如果STATUS_MAP中没有，创建一个最终状态
                if (finalStatus == Status.TIMEOUT) {
                    return new ConvertStatus(Status.TIMEOUT, "转换超时，请重试", 0, 0);
                } else {
                    return new ConvertStatus(Status.FAILED, "转换失败", 0, 0);
                }
            }
            // 返回最终状态
            return new ConvertStatus(state.status, state.message, state.progress, 0);
        }

        ConvertState state = STATUS_MAP.get(fileName);
        if (state == null) {
            return null;
        }

        // 如果是转换中状态，计算已等待时间
        long waitingSeconds = 0;
        if (state.status == Status.CONVERTING) {
            waitingSeconds = (System.currentTimeMillis() - state.startTime) / 1000;
        }

        return new ConvertStatus(
                state.status,
                state.message,
                state.progress,
                waitingSeconds
        );
    }

    /**
     * 转换成功
     * @param fileName 文件名
     */
    public static void convertSuccess(String fileName) {
        STATUS_MAP.remove(fileName);
        // 清除最终状态，允许重新转换
        FINAL_STATUS_MAP.remove(fileName);
    }

    /**
     * 清理状态（强制重置，允许重新转换）
     * @param fileName 文件名
     * @return true: 清理成功; false: 清理失败
     */
    public static boolean clearStatus(String fileName) {
        boolean removed1 = STATUS_MAP.remove(fileName) != null;
        boolean removed2 = FINAL_STATUS_MAP.remove(fileName) != null;
        logger.info("清理文件状态: {}, STATUS_MAP: {}, FINAL_STATUS_MAP: {}",
                fileName, removed1, removed2);
        return removed1 || removed2;
    }



    /**
     * 清理过期状态（长时间未清理的状态）
     * @param expireHours 过期时间（小时）
     * @return 清理的数量
     */
    public static int cleanupExpiredStatus(int expireHours) {
        long expireMillis = expireHours * 3600 * 1000L;
        long currentTime = System.currentTimeMillis();

        // 清理STATUS_MAP中的过期状态
        int count1 = (int) STATUS_MAP.entrySet().stream()
                .filter(entry -> {
                    ConvertState state = entry.getValue();
                    if (state.status == Status.CONVERTING) {
                        return false; // 转换中的不清理
                    }
                    long elapsed = currentTime - state.startTime;
                    return elapsed > expireMillis;
                })
                .count();

        // 清理FINAL_STATUS_MAP中的过期状态
        // 注意：FINAL_STATUS_MAP没有时间戳，无法基于时间清理
        // 如果需要清理，可以设置一个独立的过期机制

        logger.info("清理了 {} 个过期的转换状态", count1);
        return count1;
    }

    /**
     * 转换状态枚举
     */
    public enum Status {
        CONVERTING("转换中"),
        FAILED("转换失败"),
        TIMEOUT("转换超时"),
        QUEUED("排队中");

        private final String description;

        Status(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 内部状态存储类
     */
    private static class ConvertState {
        private final Status status;
        private String message;
        private int progress; // 0-100
        private final long startTime;

        public ConvertState(Status status, String message, int progress) {
            this.status = status;
            this.message = message;
            this.progress = Math.max(0, Math.min(100, progress));
            this.startTime = System.currentTimeMillis();
        }

        public void update(String message, int progress) {
            this.message = message;
            this.progress = Math.max(0, Math.min(100, progress));
        }
    }

    /**
     * 对外暴露的转换状态封装类
     */
    public static class ConvertStatus {
        private final Status status;
        private final String message;
        private final int progress;
        private final long waitingSeconds;
        private final long timestamp;

        public ConvertStatus(Status status, String message, int progress, long waitingSeconds) {
            this.status = status;
            this.message = message;
            this.progress = progress;
            this.waitingSeconds = waitingSeconds;
            this.timestamp = System.currentTimeMillis();
        }


        // 获取实时状态信息
        public String getRealTimeMessage() {
            if (status == Status.CONVERTING) {
                if (progress > 0) {
                    return String.format("%s: %s (进度: %d%%，已等待 %d 秒)",
                            status.getDescription(), message, progress, waitingSeconds);
                } else {
                    return String.format("%s: %s，已等待 %d 秒",
                            status.getDescription(), message, waitingSeconds);
                }
            }
            return message;
        }

        // Getters
        public Status getStatus() { return status; }
        public String getMessage() { return message; }
        public int getProgress() { return progress; }

        public long getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return "ConvertStatus{" +
                    "status=" + status +
                    ", message='" + message + '\'' +
                    ", progress=" + progress +
                    ", waitingSeconds=" + waitingSeconds +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    // 日志记录器
    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(FileConvertStatusManager.class);
}