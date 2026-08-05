package com.dqc.compare.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 解析请求：封装待解析文件及其前缀嗅探内容（前 1KB），供路由与解析器做格式判定。
 */
public class ParseRequest {

    private final Path path;
    private final String contentSnippet;

    public ParseRequest(Path path) {
        this.path = path;
        this.contentSnippet = sniff(path);
    }

    private static String sniff(Path path) {
        try {
            byte[] buf = new byte[1024];
            int n;
            try (var in = Files.newInputStream(path)) {
                n = in.read(buf);
            }
            if (n <= 0) {
                return "";
            }
            return new String(buf, 0, n, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    public Path getPath() {
        return path;
    }

    public String getContentSnippet() {
        return contentSnippet;
    }

    public String getFileName() {
        return path.getFileName() == null ? "" : path.getFileName().toString();
    }
}
