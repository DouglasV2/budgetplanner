package ai.budgetspace.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// Sprint 10.111: the LLM sometimes returns scalar intent fields as arrays. These guard that such a response
// no longer breaks the whole parse (which silently disabled AI), taking the first value instead.
class PlannerIntentAnalysisDtoDeserializeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void styleAsArrayTakesFirstValue() throws Exception {
        PlannerIntentAnalysisDto dto = mapper.readValue(
                "{\"style\":[\"warm\",\"modern\"],\"roomType\":\"bedroom\",\"budget\":1800}",
                PlannerIntentAnalysisDto.class);
        assertEquals("warm", dto.style());
        assertEquals("bedroom", dto.roomType());
        assertEquals(1800, dto.budget());
    }

    @Test
    void scalarStringsStillWork() throws Exception {
        PlannerIntentAnalysisDto dto = mapper.readValue(
                "{\"style\":\"minimal\",\"roomType\":\"living-room\",\"qualityPreference\":\"premium\",\"urgency\":\"soon\",\"currency\":\"EUR\"}",
                PlannerIntentAnalysisDto.class);
        assertEquals("minimal", dto.style());
        assertEquals("living-room", dto.roomType());
        assertEquals("premium", dto.qualityPreference());
        assertEquals("soon", dto.urgency());
        assertEquals("EUR", dto.currency());
    }

    @Test
    void allArrayTolerantFieldsAcceptArrays() throws Exception {
        PlannerIntentAnalysisDto dto = mapper.readValue(
                "{\"roomType\":[\"kitchen\"],\"style\":[\"industrial\"],\"qualityPreference\":[\"budget\"],\"urgency\":[\"flexible\"],\"currency\":[\"EUR\"]}",
                PlannerIntentAnalysisDto.class);
        assertEquals("kitchen", dto.roomType());
        assertEquals("industrial", dto.style());
        assertEquals("budget", dto.qualityPreference());
        assertEquals("flexible", dto.urgency());
        assertEquals("EUR", dto.currency());
    }

    @Test
    void emptyOrNullArrayBecomesNull() throws Exception {
        PlannerIntentAnalysisDto dto = mapper.readValue("{\"style\":[],\"roomType\":null}", PlannerIntentAnalysisDto.class);
        assertNull(dto.style());
        assertNull(dto.roomType());
    }
}
