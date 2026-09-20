import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import ru.rutcampustrack.gateway.filter.LoginBodyExtractionFilter;

class GatewayDispatchProbe {
    public static void main(String[] args) {
        var request = MockServerHttpRequest.post("/api/auth/login")
                .header("Content-Type", "application/json")
                .body("{\"login\":\"probe-student\"}");
        var calls = new AtomicInteger();
        var logins = new ArrayList<String>();
        GatewayFilterChain chain = exchange -> Mono.defer(() -> {
            calls.incrementAndGet();
            logins.add(exchange.getRequest().getHeaders().getFirst("X-Login"));
            return Mono.empty();
        });
        new LoginBodyExtractionFilter(new ObjectMapper())
                .filter(MockServerWebExchange.from(request), chain).block();
        System.out.println("DOWNSTREAM_SUBSCRIPTIONS=" + calls.get());
        System.out.println("LOGIN_HEADERS=" + logins);
        if (calls.get() != 1) {
            throw new AssertionError("One incoming login must invoke downstream once; actual=" + calls.get());
        }
    }
}
