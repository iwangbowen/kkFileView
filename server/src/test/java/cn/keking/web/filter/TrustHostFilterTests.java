package cn.keking.web.filter;

import cn.keking.config.ConfigConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class TrustHostFilterTests {

    private final TrustHostFilter trustHostFilter = new TrustHostFilter();

    @AfterEach
    void tearDown() {
        ConfigConstants.setTrustHostValue("default");
        ConfigConstants.setNotTrustHostValue("default");
    }

    @Test
    void shouldBlockWildcardNotTrustHostPattern() {
        ConfigConstants.setTrustHostValue("*");
        ConfigConstants.setNotTrustHostValue("192.168.*");

        assert trustHostFilter.isNotTrustHost("192.168.1.10");
        assert !trustHostFilter.isNotTrustHost("8.8.8.8");
        assert !trustHostFilter.isNotTrustHost("192.168.evil.com");
    }

    @Test
    void shouldBlockCidrNotTrustHostPattern() {
        ConfigConstants.setTrustHostValue("*");
        ConfigConstants.setNotTrustHostValue("10.0.0.0/8");

        assert trustHostFilter.isNotTrustHost("10.1.2.3");
        assert !trustHostFilter.isNotTrustHost("11.1.2.3");
        // Ensure hostnames are not matched by CIDR-based not-trust rules (no DNS resolution)
        assert !trustHostFilter.isNotTrustHost("localhost");
    }

    @Test
    void shouldSupportHighBitIpv4InCidrMatching() {
        ConfigConstants.setTrustHostValue("*");
        ConfigConstants.setNotTrustHostValue("200.0.0.0/8");

        assert trustHostFilter.isNotTrustHost("200.1.2.3");
        assert !trustHostFilter.isNotTrustHost("199.1.2.3");
    }

    @Test
    void shouldSupportIpv4UpperBoundaryCidrMatching() {
        ConfigConstants.setTrustHostValue("*");
        ConfigConstants.setNotTrustHostValue("255.255.255.255/32");

        assert trustHostFilter.isNotTrustHost("255.255.255.255");
        assert !trustHostFilter.isNotTrustHost("255.255.255.254");
    }

    @Test
    void shouldDenyWhenHostIsBlankOrNull() {
        ConfigConstants.setTrustHostValue("*");
        ConfigConstants.setNotTrustHostValue("default");

        assert trustHostFilter.isNotTrustHost(null);
        assert trustHostFilter.isNotTrustHost(" ");
    }

    @Test
    void shouldAllowWildcardTrustHostPattern() {
        ConfigConstants.setTrustHostValue("*.trusted.com");
        ConfigConstants.setNotTrustHostValue("default");

        assert !trustHostFilter.isNotTrustHost("api.trusted.com");
        assert trustHostFilter.isNotTrustHost("api.evil.com");
    }

    @Test
    void shouldKeepBlacklistHigherPriorityThanWhitelist() {
        ConfigConstants.setTrustHostValue("*");
        ConfigConstants.setNotTrustHostValue("127.0.0.1,10.*");

        assert trustHostFilter.isNotTrustHost("127.0.0.1");
        assert trustHostFilter.isNotTrustHost("10.1.2.3");
        assert !trustHostFilter.isNotTrustHost("8.8.8.8");
    }

    @Test
    void shouldStillEnforceWhitelistWhenBlacklistConfigured() {
        ConfigConstants.setTrustHostValue("internal.example.com");
        ConfigConstants.setNotTrustHostValue("127.0.0.1");

        assert !trustHostFilter.isNotTrustHost("internal.example.com");
        assert trustHostFilter.isNotTrustHost("8.8.8.8");
    }
}
