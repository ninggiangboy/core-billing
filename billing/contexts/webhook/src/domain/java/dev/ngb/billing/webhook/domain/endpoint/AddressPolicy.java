package dev.ngb.billing.webhook.domain.endpoint;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Which addresses a webhook may be sent to. Anything that is not on the public internet is refused, so a
 * Tenant cannot make Billing call its own network: loopback, link-local (including the cloud metadata
 * address), private ranges, carrier-grade NAT, multicast, reserved space and IPv6 forms that embed an IPv4
 * address are all blocked.
 */
public final class AddressPolicy {

    private AddressPolicy() {}

    public static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            return publicV4(b[0] & 0xff, b[1] & 0xff, b[2] & 0xff);
        }
        if (address instanceof Inet6Address) {
            return publicV6(b);
        }
        return false;
    }

    private static boolean publicV4(int a, int b, int c) {
        if (a == 0 || a >= 240) {
            return false; // this network, reserved, broadcast
        }
        // Also checked here, not only through InetAddress, because IPv6 forms embed an IPv4 address as raw bytes.
        if (a == 10 || a == 127 || a == 172 && b >= 16 && b <= 31 || a == 192 && b == 168 || a == 169 && b == 254) {
            return false; // private, loopback, link-local
        }
        if (a == 100 && b >= 64 && b <= 127) {
            return false; // carrier-grade NAT
        }
        if (a == 192 && b == 0 && c == 0) {
            return false; // IETF protocol assignments
        }
        if (a == 192 && b == 0 && c == 2 || a == 198 && b == 51 && c == 100 || a == 203 && b == 0 && c == 113) {
            return false; // documentation ranges
        }
        return !(a == 198 && (b == 18 || b == 19)); // benchmarking
    }

    private static boolean publicV6(byte[] b) {
        if ((b[0] & 0xfe) == 0xfc) {
            return false; // unique local fc00::/7
        }
        if (b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0d && b[3] == (byte) 0xb8) {
            return false; // documentation 2001:db8::/32
        }
        boolean zeroPrefix = true;
        for (int i = 0; i < 12; i++) {
            zeroPrefix &= b[i] == 0;
        }
        boolean nat64 = b[0] == 0x00 && b[1] == 0x64 && b[2] == (byte) 0xff && b[3] == (byte) 0x9b && allZero(b, 4, 12);
        if (zeroPrefix || nat64) {
            return publicV4(b[12] & 0xff, b[13] & 0xff, b[14] & 0xff); // IPv4-compatible and NAT64
        }
        if (b[0] == 0x20 && b[1] == 0x02) {
            return publicV4(b[2] & 0xff, b[3] & 0xff, b[4] & 0xff); // 6to4
        }
        return true;
    }

    private static boolean allZero(byte[] b, int from, int to) {
        for (int i = from; i < to; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
