package ai.budgetspace.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Sprint 10.183 (Move-In QoL): the new optional fields on MoveInRequestDto / MoveInRoomDto must be
// back-compatible — an old client (or an old saved payload) that omits them still deserializes, reading
// the missing fields as EMPTY, never null. These guard that contract so old and new callers coexist.
class MoveInDtoBackCompatTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void legacyRequestJsonWithoutRoomPriorityReadsEmptyMap() throws Exception {
        MoveInRequestDto dto = mapper.readValue(
                "{\"base\":null,\"rooms\":[\"living-room\",\"bedroom\"],\"totalBudget\":5000}",
                MoveInRequestDto.class);
        assertEquals(List.of("living-room", "bedroom"), dto.rooms());
        assertEquals(5000, dto.totalBudget());
        assertNotNull(dto.roomPriority());
        assertTrue(dto.roomPriority().isEmpty());
    }

    @Test
    void newRequestJsonReadsRoomPriority() throws Exception {
        MoveInRequestDto dto = mapper.readValue(
                "{\"base\":null,\"rooms\":[\"bedroom\"],\"totalBudget\":3000,\"roomPriority\":{\"bedroom\":\"now\"}}",
                MoveInRequestDto.class);
        assertEquals("now", dto.roomPriority().get("bedroom"));
    }

    @Test
    void legacyRoomJsonWithoutBucketsReadsEmptyLists() throws Exception {
        MoveInRoomDto dto = mapper.readValue(
                "{\"roomType\":\"bedroom\",\"allocatedBudget\":1200,\"plans\":[],\"partial\":false}",
                MoveInRoomDto.class);
        assertEquals("bedroom", dto.roomType());
        assertTrue(dto.missingEssential().isEmpty());
        assertTrue(dto.niceToHave().isEmpty());
        assertTrue(dto.unavailableInMarket().isEmpty());
    }

    @Test
    void newRoomJsonReadsMissingBuckets() throws Exception {
        MoveInRoomDto dto = mapper.readValue(
                "{\"roomType\":\"bathroom\",\"allocatedBudget\":800,\"plans\":[],\"partial\":true,"
                        + "\"missingEssential\":[\"washbasin\"],\"niceToHave\":[\"textiles\"],"
                        + "\"unavailableInMarket\":[\"bath-shower\"]}",
                MoveInRoomDto.class);
        assertEquals(List.of("washbasin"), dto.missingEssential());
        assertEquals(List.of("textiles"), dto.niceToHave());
        assertEquals(List.of("bath-shower"), dto.unavailableInMarket());
    }

    @Test
    void legacyConstructorsFillEmptyDefaults() {
        MoveInRoomDto room = new MoveInRoomDto("kitchen", 900, List.of(), false);
        assertTrue(room.missingEssential().isEmpty());
        assertTrue(room.niceToHave().isEmpty());
        assertTrue(room.unavailableInMarket().isEmpty());

        MoveInRequestDto req = new MoveInRequestDto(null, List.of("kitchen"), 900);
        assertTrue(req.roomPriority().isEmpty());
    }

    @Test
    void nullsAreNormalizedToEmpty() {
        MoveInRoomDto room = new MoveInRoomDto("hallway", 300, null, false, null, null, null);
        assertTrue(room.plans().isEmpty());
        assertTrue(room.missingEssential().isEmpty());

        MoveInRequestDto req = new MoveInRequestDto(null, null, 300, null);
        assertTrue(req.rooms().isEmpty());
        assertTrue(req.roomPriority().isEmpty());
    }
}
