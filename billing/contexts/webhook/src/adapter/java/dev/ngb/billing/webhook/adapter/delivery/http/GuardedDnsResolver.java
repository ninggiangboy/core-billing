package dev.ngb.billing.webhook.adapter.delivery.http;

import dev.ngb.billing.webhook.domain.endpoint.AddressPolicy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;

/**
 * Resolves a host and refuses it when any of its addresses is not public. The HTTP client connects to the
 * addresses this resolver returns, so there is no gap between the check and the connection in which a DNS
 * answer could change (DNS rebinding).
 */
final class GuardedDnsResolver implements DnsResolver {

    private final boolean allowNonPublic;

    GuardedDnsResolver(boolean allowNonPublic) {
        this.allowNonPublic = allowNonPublic;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = SystemDefaultDnsResolver.INSTANCE.resolve(host);
        if (!allowNonPublic) {
            for (InetAddress address : addresses) {
                if (!AddressPolicy.isPublic(address)) {
                    throw new BlockedDestinationException(host);
                }
            }
        }
        return addresses;
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(host);
    }
}
