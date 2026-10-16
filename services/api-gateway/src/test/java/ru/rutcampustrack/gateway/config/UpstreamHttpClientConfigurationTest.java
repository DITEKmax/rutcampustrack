package ru.rutcampustrack.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.netty.channel.EventLoop;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.resolver.dns.DefaultDnsCache;
import io.netty.util.concurrent.ScheduledFuture;
import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.NameResolverProvider;

class UpstreamHttpClientConfigurationTest {

    @Test
    void positiveEntryExpiresWithinFiveSecondsAndCanResolveTheReplacementAddress()
            throws Exception {
        HttpClient existing = HttpClient.create().responseTimeout(Duration.ofSeconds(11));
        HttpClient configured =
                new UpstreamHttpClientConfiguration().upstreamDnsCachePolicy().customize(existing);
        NameResolverProvider provider = configured.configuration().getNameResolverProvider();
        if (provider == null) provider = NameResolverProvider.builder().build();
        var cache =
                new DefaultDnsCache(
                        (int) provider.cacheMinTimeToLive().toSeconds(),
                        (int) provider.cacheMaxTimeToLive().toSeconds(),
                        (int) provider.cacheNegativeTimeToLive().toSeconds());
        EventLoop loop = mock(EventLoop.class);
        AtomicReference<Runnable> eviction = new AtomicReference<>();
        AtomicLong evictionDelayNanos = new AtomicLong(Long.MAX_VALUE);
        doAnswer(
                        invocation -> {
                            eviction.set(invocation.getArgument(0));
                            evictionDelayNanos.set(
                                    invocation
                                            .getArgument(2, TimeUnit.class)
                                            .toNanos(invocation.getArgument(1, Long.class)));
                            return mock(ScheduledFuture.class);
                        })
                .when(loop)
                .schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        DnsRecord[] records = new DnsRecord[0];
        InetAddress oldAddress = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        InetAddress newAddress = InetAddress.getByAddress(new byte[] {127, 0, 0, 2});

        cache.cache("owned.invalid", records, oldAddress, 600, loop);
        assertThat(cache.get("owned.invalid", records).getFirst().address()).isEqualTo(oldAddress);
        // Advance only the controlled scheduler: callbacks beyond five seconds remain pending.
        if (evictionDelayNanos.get() <= TimeUnit.SECONDS.toNanos(5)) eviction.get().run();
        assertThat(cache.get("owned.invalid", records))
                .as("The stale address must be evicted within five seconds")
                .isNullOrEmpty();
        cache.cache("owned.invalid", records, newAddress, 600, loop);
        assertThat(cache.get("owned.invalid", records).getFirst().address()).isEqualTo(newAddress);
        assertThat(provider.cacheMinTimeToLive()).isEqualTo(Duration.ZERO);
        assertThat(provider.cacheNegativeTimeToLive()).isEqualTo(Duration.ZERO);
        assertThat(configured.configuration().responseTimeout()).isEqualTo(Duration.ofSeconds(11));
    }
}
