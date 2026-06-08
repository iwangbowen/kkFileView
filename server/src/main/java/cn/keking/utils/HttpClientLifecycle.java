package cn.keking.utils;

import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;

@Component
public class HttpClientLifecycle {

    @PreDestroy
    public void destroy() {
        System.out.println("Spring 容器关闭，释放 HTTP 连接池资源...");
        HttpRequestUtils.shutdown();
    }
}