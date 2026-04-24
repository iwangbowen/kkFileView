package cn.keking.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RemoveSvgAdSimple {


    /**
     * 更可靠的版本：使用DOM解析思路，但用正则实现
     * @param svgContent SVG内容字符串
     * @return 清理后的SVG内容
     */
    public static String removeSvgAdReliable(String svgContent) {
        StringBuilder cleaned = new StringBuilder();

        // 找到XML声明
        if (svgContent.contains("<?xml")) {
            int xmlEnd = svgContent.indexOf("?>") + 2;
            cleaned.append(svgContent, 0, xmlEnd).append("\n");
        }

        // 找到SVG开始标签
        int svgStart = svgContent.indexOf("<svg");
        if (svgStart == -1) return svgContent;

        int svgEnd = svgContent.indexOf(">", svgStart) + 1;
        cleaned.append(svgContent, svgStart, svgEnd).append("\n");

        // 解析剩余内容
        String remaining = svgContent.substring(svgEnd);
        int pos = 0;

        while (pos < remaining.length()) {
            // 查找下一个<g>标签
            int gStart = remaining.indexOf("<g", pos);
            if (gStart == -1) break;

            // 检查这个<g>标签是否包含transform属性
            int gTagEnd = remaining.indexOf(">", gStart);
            String gTag = remaining.substring(gStart, gTagEnd + 1);

            if (gTag.contains("transform")) {
                // 找到对应的</g>标签
                int depth = 1;
                int searchPos = gTagEnd + 1;
                int gClose = -1;

                while (searchPos < remaining.length()) {
                    int nextOpen = remaining.indexOf("<g", searchPos);
                    int nextClose = remaining.indexOf("</g>", searchPos);

                    if (nextClose != -1 && (nextClose < nextOpen || nextOpen == -1)) {
                        depth--;
                        if (depth == 0) {
                            gClose = nextClose + 4; // 包括</g>的4个字符
                            break;
                        }
                        searchPos = nextClose + 4;
                    } else if (nextOpen != -1 && nextOpen < nextClose) {
                        depth++;
                        searchPos = nextOpen + 2;
                    } else {
                        break;
                    }
                }

                if (gClose != -1) {
                    cleaned.append(remaining, gStart, gClose).append("\n");
                    pos = gClose;
                } else {
                    pos = gTagEnd + 1;
                }
            } else {
                // 跳过这个<g>元素及其内容
                pos = gTagEnd + 1;
            }
        }

        cleaned.append("</svg>");
        return cleaned.toString();
    }

    /**
     * 从文件路径读取SVG内容，清理广告，然后替换原文件
     * @param filePath SVG文件路径
     * @throws IOException 文件读写异常
     */
    public static void removeSvgAdFromFile(String filePath) throws IOException {
        removeSvgAdFromFile(filePath, filePath);
    }

    /**
     * 从文件路径读取SVG内容，清理广告，然后写入目标文件
     * @param sourceFilePath 源SVG文件路径
     * @param targetFilePath 目标SVG文件路径
     * @throws IOException 文件读写异常
     */
    public static void removeSvgAdFromFile(String sourceFilePath, String targetFilePath) throws IOException {
        // 读取文件内容
        Path sourcePath = Paths.get(sourceFilePath);
        String svgContent = Files.readString(sourcePath);

        // 清理SVG广告
        String cleanedContent = removeSvgAdReliable(svgContent);

        // 写入目标文件
        Path targetPath = Paths.get(targetFilePath);
        Files.writeString(targetPath, cleanedContent);
        System.out.println("SVG广告清理完成！");
    }

}