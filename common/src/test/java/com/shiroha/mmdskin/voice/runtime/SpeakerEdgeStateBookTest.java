package com.shiroha.mmdskin.voice.runtime;

import com.shiroha.mmdskin.voice.VoiceEventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpeakerEdgeStateBookTest {
    @Test
    void shouldOnlyEmitOnRisingEdges() {
        SpeakerEdgeStateBook stateBook = new SpeakerEdgeStateBook();

        assertEquals(List.of(VoiceEventType.HURT), stateBook.update("maid:1", true, false));
        assertEquals(List.of(), stateBook.update("maid:1", true, false));
        assertEquals(List.of(), stateBook.update("maid:1", false, false));
        assertEquals(List.of(VoiceEventType.DEATH), stateBook.update("maid:1", false, true));
        assertEquals(List.of(), stateBook.update("maid:1", false, true));
    }

    @Test
    void shouldEvictOnlyStaleSpeakersWithoutBreakingEdgeSemantics() {
        SpeakerEdgeStateBook stateBook = new SpeakerEdgeStateBook();

        assertEquals(List.of(VoiceEventType.HURT), stateBook.update("maid:stale", true, false, 1_000L));
        assertEquals(List.of(VoiceEventType.HURT), stateBook.update("maid:fresh", true, false, 5_500L));

        stateBook.evictStaleSpeakers(61_000L, 60_000L);

        assertEquals(List.of(VoiceEventType.HURT), stateBook.update("maid:stale", true, false, 61_001L));
        assertEquals(List.of(), stateBook.update("maid:fresh", true, false, 61_001L));
    }
}
