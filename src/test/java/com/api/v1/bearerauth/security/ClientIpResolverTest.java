package com.api.v1.bearerauth.security;

import com.api.v1.bearerauth.config.AppProperty;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private ClientIpResolver resolver(String... trusted) {
        AppProperty property = new AppProperty();
        property.getActivityLog().setTrustedProxies(List.of(trusted));
        return new ClientIpResolver(property);
    }

    private MockHttpServletRequest request(String remote, String... forwarded) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        for (String value : forwarded) {
            request.addHeader("X-Forwarded-For", value);
        }
        return request;
    }

    @Test
    void withoutTrustedProxies_theHeaderIsIgnored() {
        assertThat(resolver().resolve(request("203.0.113.5", "1.2.3.4"))).isEqualTo("203.0.113.5");
    }

    @Test
    void fromAnUntrustedConnection_theHeaderIsIgnored_soItCannotBeForged() {
        assertThat(resolver("10.0.0.0/8").resolve(request("203.0.113.5", "1.2.3.4"))).isEqualTo("203.0.113.5");
    }

    @Test
    void fromATrustedProxy_theClientIsTheFirstUntrustedAddressFromTheRight() {
        ClientIpResolver resolver = resolver("10.0.0.0/8", "192.168.1.7");

        assertThat(resolver.resolve(request("10.0.0.2", "198.51.100.9"))).isEqualTo("198.51.100.9");
        // a client wrote 6.6.6.6 itself; the proxy appended the real address
        assertThat(resolver.resolve(request("10.0.0.2", "6.6.6.6, 198.51.100.9, 10.0.0.1"))).isEqualTo("198.51.100.9");
        assertThat(resolver.resolve(request("10.0.0.2", "6.6.6.6", "198.51.100.9, 192.168.1.7"))).isEqualTo("198.51.100.9");
    }

    @Test
    void aMalformedHeader_isNotBelieved() {
        ClientIpResolver resolver = resolver("10.0.0.0/8");

        assertThat(resolver.resolve(request("10.0.0.2", "<script>alert(1)</script>"))).isEqualTo("10.0.0.2");
        assertThat(resolver.resolve(request("10.0.0.2", "198.51.100.9, not-an-ip"))).isEqualTo("10.0.0.2");
        assertThat(resolver.resolve(request("10.0.0.2", ""))).isEqualTo("10.0.0.2");
    }

    @Test
    void everyHopTrusted_orNoHeader_givesTheConnection() {
        ClientIpResolver resolver = resolver("10.0.0.0/8");

        assertThat(resolver.resolve(request("10.0.0.2", "10.0.0.9"))).isEqualTo("10.0.0.2");
        assertThat(resolver.resolve(request("10.0.0.2"))).isEqualTo("10.0.0.2");
    }

    @Test
    void ipv6_works_andBadConfigurationIsIgnored() {
        ClientIpResolver resolver = resolver("fd00::/8", "not a range", " ");

        assertThat(resolver.resolve(request("fd00::1", "2001:db8::5"))).isEqualTo("2001:db8::5");
    }

    @Test
    void noRequest_noAddress() {
        assertThat(resolver().resolve(null)).isNull();
    }
}
