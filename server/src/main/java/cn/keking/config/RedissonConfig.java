package cn.keking.config;

import org.apache.commons.lang3.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

/**
 * Redisson 客户端配置（完善版）
 * 支持 single / cluster / master-slave / sentinel 四种模式，配置完整，统一参数。
 */
@ConditionalOnExpression("'${cache.type:default}'.equals('redis')")
@ConfigurationProperties(prefix = "spring.redisson")
@Configuration
public class RedissonConfig {

    // ========================== 连接配置 ==========================
    private String address;
    private String password;
    private String clientName;
    private int database = 0;
    private String mode = "single";
    private String masterName = "kkfile";

    // ========================== 超时配置 ==========================
    private int idleConnectionTimeout = 10000;
    private int connectTimeout = 10000;
    private int timeout = 3000;

    // ========================== 重试配置 ==========================
    private int retryAttempts = 3;
    private int retryInterval = 1500;

    // ========================== 连接池配置 ==========================
    private int connectionMinimumIdleSize = 10;
    private int connectionPoolSize = 64;
    private int subscriptionsPerConnection = 5;
    private int subscriptionConnectionMinimumIdleSize = 1;
    private int subscriptionConnectionPoolSize = 50;

    // ========================== 集群专用配置 ==========================
    private int scanInterval = 2000;

    // ========================== 其他配置 ==========================
    private int dnsMonitoringInterval = 5000;
    private int threads; // 默认为0，表示使用 CPU 核数 * 2
    private String codec = "org.redisson.codec.JsonJacksonCodec";

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 密码处理：空字符串转为 null
        String pwd = StringUtils.isBlank(password) ? null : password;

        // 根据模式构建配置
        switch (mode.toLowerCase()) {
            case "cluster":
                configureClusterMode(config, pwd);
                break;
            case "master-slave":
                configureMasterSlaveMode(config, pwd);
                break;
            case "sentinel":
                configureSentinelMode(config, pwd);
                break;
            default:
                configureSingleMode(config, pwd);
                break;
        }

        // 公共配置：编码器、线程数
        applyCommonConfig(config);
        return Redisson.create(config);
    }

    // ========================== 配置方法 ==========================

    private void configureSingleMode(Config config, String pwd) {
        String normalizedAddress = normalizeAddress(address);
        config.useSingleServer()
                .setAddress(normalizedAddress)
                .setConnectionMinimumIdleSize(connectionMinimumIdleSize)
                .setConnectionPoolSize(connectionPoolSize)
                .setDatabase(database)
                .setDnsMonitoringInterval(dnsMonitoringInterval)
                .setSubscriptionConnectionMinimumIdleSize(subscriptionConnectionMinimumIdleSize)
                .setSubscriptionConnectionPoolSize(subscriptionConnectionPoolSize)
                .setSubscriptionsPerConnection(subscriptionsPerConnection)
                .setClientName(clientName)
                .setRetryAttempts(retryAttempts)
                .setRetryInterval(retryInterval)
                .setTimeout(timeout)
                .setConnectTimeout(connectTimeout)
                .setIdleConnectionTimeout(idleConnectionTimeout)
                .setPassword(pwd);
    }

    private void configureClusterMode(Config config, String pwd) {
        String[] nodeAddresses = normalizeAddresses(address.split(","));
        config.useClusterServers()
                .setScanInterval(scanInterval)
                .addNodeAddress(nodeAddresses)
                .setPassword(pwd)
                .setRetryAttempts(retryAttempts)
                .setRetryInterval(retryInterval)
                .setTimeout(timeout)
                .setConnectTimeout(connectTimeout)
                .setIdleConnectionTimeout(idleConnectionTimeout)
                .setMasterConnectionPoolSize(connectionPoolSize)
                .setSlaveConnectionPoolSize(connectionPoolSize)
                .setSubscriptionConnectionPoolSize(subscriptionConnectionPoolSize)
                .setSubscriptionConnectionMinimumIdleSize(subscriptionConnectionMinimumIdleSize)
                .setSubscriptionsPerConnection(subscriptionsPerConnection)
                .setClientName(clientName);
    }

    private void configureMasterSlaveMode(Config config, String pwd) {
        String[] addresses = address.split(",");
        validateMasterSlaveAddresses(addresses);
        String[] normalizedAddresses = normalizeAddresses(addresses);
        String masterAddress = normalizedAddresses[0];
        String[] slaveAddresses = new String[normalizedAddresses.length - 1];
        System.arraycopy(normalizedAddresses, 1, slaveAddresses, 0, slaveAddresses.length);

        config.useMasterSlaveServers()
                .setDatabase(database)
                .setPassword(pwd)
                .setMasterAddress(masterAddress)
                .addSlaveAddress(slaveAddresses)
                .setRetryAttempts(retryAttempts)
                .setRetryInterval(retryInterval)
                .setTimeout(timeout)
                .setConnectTimeout(connectTimeout)
                .setIdleConnectionTimeout(idleConnectionTimeout)
                .setMasterConnectionPoolSize(connectionPoolSize)
                .setSlaveConnectionPoolSize(connectionPoolSize)
                .setSubscriptionConnectionPoolSize(subscriptionConnectionPoolSize)
                .setSubscriptionConnectionMinimumIdleSize(subscriptionConnectionMinimumIdleSize)
                .setSubscriptionsPerConnection(subscriptionsPerConnection)
                .setClientName(clientName);
    }

    private void configureSentinelMode(Config config, String pwd) {
        String[] sentinelAddresses = normalizeAddresses(address.split(","));
        config.useSentinelServers()
                .setDatabase(database)
                .setPassword(pwd)
                .setMasterName(masterName)
                .addSentinelAddress(sentinelAddresses)
                .setRetryAttempts(retryAttempts)
                .setRetryInterval(retryInterval)
                .setTimeout(timeout)
                .setConnectTimeout(connectTimeout)
                .setIdleConnectionTimeout(idleConnectionTimeout)
                .setMasterConnectionPoolSize(connectionPoolSize)
                .setSlaveConnectionPoolSize(connectionPoolSize)
                .setSubscriptionConnectionPoolSize(subscriptionConnectionPoolSize)
                .setSubscriptionConnectionMinimumIdleSize(subscriptionConnectionMinimumIdleSize)
                .setSubscriptionsPerConnection(subscriptionsPerConnection)
                .setClientName(clientName);
    }

    private void applyCommonConfig(Config config) {
        // 设置编码器
        if (StringUtils.isNotBlank(codec)) {
            try {
                Class<?> codecClass = ClassUtils.forName(codec, ClassUtils.getDefaultClassLoader());
                Codec codecInstance = (Codec) codecClass.getDeclaredConstructor().newInstance();
                config.setCodec(codecInstance);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create Redisson codec: " + codec, e);
            }
        }
        // 设置线程数（大于0时生效，否则Redisson使用默认值：CPU核数*2）
        if (threads > 0) {
            config.setThreads(threads);
        }
    }

    // ========================== 辅助方法 ==========================

    /**
     * 自动补齐 Redis 地址协议前缀（redis:// 或 rediss://）
     */
    private String normalizeAddress(String addr) {
        if (addr == null) {
            return null;
        }
        addr = addr.trim();
        if (!addr.startsWith("redis://") && !addr.startsWith("rediss://")) {
            addr = "redis://" + addr;
        }
        return addr;
    }

    private String[] normalizeAddresses(String[] addresses) {
        String[] normalized = new String[addresses.length];
        for (int i = 0; i < addresses.length; i++) {
            normalized[i] = normalizeAddress(addresses[i]);
        }
        return normalized;
    }

    private void validateMasterSlaveAddresses(String[] addresses) {
        if (addresses.length < 2) {
            throw new IllegalArgumentException(
                    "Master-slave mode requires at least 2 addresses: master and at least one slave. " +
                            "Current addresses: " + String.join(",", addresses));
        }
    }

    // ========================== Getter / Setter（供 Spring 绑定配置） ==========================
    // 以下所有字段都需要提供 getter/setter，示例中只列出关键字段，实际使用时请补全所有字段。
    // 建议使用 Lombok @Data 或 IDE 自动生成。这里只展示部分，避免篇幅过长。

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public int getDatabase() { return database; }
    public void setDatabase(int database) { this.database = database; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getMasterName() { return masterName; }
    public void setMasterName(String masterName) { this.masterName = masterName; }

    public int getIdleConnectionTimeout() { return idleConnectionTimeout; }
    public void setIdleConnectionTimeout(int idleConnectionTimeout) { this.idleConnectionTimeout = idleConnectionTimeout; }

    public int getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }

    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }

    public int getRetryAttempts() { return retryAttempts; }
    public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }

    public int getRetryInterval() { return retryInterval; }
    public void setRetryInterval(int retryInterval) { this.retryInterval = retryInterval; }

    public int getConnectionMinimumIdleSize() { return connectionMinimumIdleSize; }
    public void setConnectionMinimumIdleSize(int connectionMinimumIdleSize) { this.connectionMinimumIdleSize = connectionMinimumIdleSize; }

    public int getConnectionPoolSize() { return connectionPoolSize; }
    public void setConnectionPoolSize(int connectionPoolSize) { this.connectionPoolSize = connectionPoolSize; }

    public int getSubscriptionsPerConnection() { return subscriptionsPerConnection; }
    public void setSubscriptionsPerConnection(int subscriptionsPerConnection) { this.subscriptionsPerConnection = subscriptionsPerConnection; }

    public int getSubscriptionConnectionMinimumIdleSize() { return subscriptionConnectionMinimumIdleSize; }
    public void setSubscriptionConnectionMinimumIdleSize(int subscriptionConnectionMinimumIdleSize) { this.subscriptionConnectionMinimumIdleSize = subscriptionConnectionMinimumIdleSize; }

    public int getSubscriptionConnectionPoolSize() { return subscriptionConnectionPoolSize; }
    public void setSubscriptionConnectionPoolSize(int subscriptionConnectionPoolSize) { this.subscriptionConnectionPoolSize = subscriptionConnectionPoolSize; }

    public int getScanInterval() { return scanInterval; }
    public void setScanInterval(int scanInterval) { this.scanInterval = scanInterval; }

    public int getDnsMonitoringInterval() { return dnsMonitoringInterval; }
    public void setDnsMonitoringInterval(int dnsMonitoringInterval) { this.dnsMonitoringInterval = dnsMonitoringInterval; }

    public int getThreads() { return threads; }
    public void setThreads(int threads) { this.threads = threads; }

    public String getCodec() { return codec; }
    public void setCodec(String codec) { this.codec = codec; }
}