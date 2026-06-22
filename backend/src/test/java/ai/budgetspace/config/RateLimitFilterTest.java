package ai.budgetspace.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10.85 — the per-IP rate-limit backstop on /api/plans/*: allows a burst up to the limit, 429s beyond it,
 * counts per IP, and never touches other paths/methods or the CORS preflight.
 */
class RateLimitFilterTest {

    private static MockHttpServletRequest post(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/plans/generate-fast");
        request.setRemoteAddr(ip);
        return request;
    }

    @Test
    void allowsUpToTheLimitThenReturns429() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 3, 10, "http://localhost:5180");

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(post("1.2.3.4"), response, chain);
            assertThat(chain.getRequest()).as("request %s passes through", i).isNotNull();
            assertThat(response.getStatus()).isEqualTo(200);
        }

        // The 4th request in the same window is throttled.
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(post("1.2.3.4"), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotNull();
        assertThat(chain.getRequest()).as("blocked request does not reach the chain").isNull();
    }

    @Test
    void limitsAreCountedPerIp() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 1, 10, "http://localhost:5180");
        // IP A spends its single token.
        filter.doFilter(post("10.0.0.1"), new MockHttpServletResponse(), new MockFilterChain());

        // IP B still has its own allowance.
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(post("10.0.0.2"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotLimitOtherPathsOrMethods() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 1, 10, "http://localhost:5180");

        // A GET to a plan path is not a write and is not limited.
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/plans/generate-fast");
        get.setRemoteAddr("9.9.9.9");
        MockFilterChain getChain = new MockFilterChain();
        filter.doFilter(get, new MockHttpServletResponse(), getChain);
        assertThat(getChain.getRequest()).isNotNull();

        // A POST to a non-plan path is not limited.
        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/billing/checkout");
        other.setRemoteAddr("9.9.9.9");
        MockFilterChain otherChain = new MockFilterChain();
        filter.doFilter(other, new MockHttpServletResponse(), otherChain);
        assertThat(otherChain.getRequest()).isNotNull();

        // No buckets were created for those.
        assertThat(filter.activeBucketCount()).isZero();
    }

    @Test
    void disabledFilterLetsEverythingThrough() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(false, 1, 10, "http://localhost:5180");
        for (int i = 0; i < 5; i++) {
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(post("1.1.1.1"), new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).isNotNull();
        }
    }
}
