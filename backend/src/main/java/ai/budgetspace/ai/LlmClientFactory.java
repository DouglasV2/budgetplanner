package ai.budgetspace.ai;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sprint 10.10 — picks the active {@link LlmClient} from configuration. Returns empty when AI is
 * disabled, the provider is {@code OFF}, or the selected provider has no API key — callers then use
 * the deterministic rule-based path.
 */
@Component
public class LlmClientFactory {
    private final LlmProperties properties;
    private final Map<LlmProvider, LlmClient> clientsByProvider = new EnumMap<>(LlmProvider.class);

    public LlmClientFactory(LlmProperties properties, List<LlmClient> clients) {
        this.properties = properties;
        for (LlmClient client : clients) {
            clientsByProvider.put(client.provider(), client);
        }
    }

    public Optional<LlmClient> activeClient() {
        if (!properties.aiUsable()) return Optional.empty();
        return Optional.ofNullable(clientsByProvider.get(properties.provider()));
    }
}
