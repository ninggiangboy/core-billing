package dev.ngb.billing.webhook.domain.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AddressPolicyTest {

    private static InetAddress ip(String literal) throws UnknownHostException {
        return InetAddress.getByName(literal);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "127.0.0.1", "127.8.9.10", "10.0.0.1", "172.16.0.1", "172.31.255.255", "192.168.1.1", "169.254.169.254", "0.0.0.0", "0.1.2.3",
        "100.64.0.1", "100.127.255.255", "192.0.0.5", "192.0.2.1", "198.18.0.1", "198.51.100.7", "203.0.113.9", "224.0.0.1", "240.0.0.1",
        "255.255.255.255", "::1", "::", "fe80::1", "fc00::1", "fd12:3456:789a::1", "ff02::1", "2001:db8::1", "::ffff:10.0.0.1", "::ffff:127.0.0.1",
        "64:ff9b::a00:1", "2002:7f00:1::1", "::10.0.0.1"})
    void refusesAddressesOutsideThePublicInternet(String literal) throws Exception {
        assertThat(AddressPolicy.isPublic(ip(literal))).as(literal).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "93.184.216.34", "172.15.0.1", "172.32.0.1", "100.63.255.255", "100.128.0.1", "2606:4700:4700::1111", "2001:4860:4860::8888"})
    void allowsPublicAddresses(String literal) throws Exception {
        assertThat(AddressPolicy.isPublic(ip(literal))).as(literal).isTrue();
    }
}
