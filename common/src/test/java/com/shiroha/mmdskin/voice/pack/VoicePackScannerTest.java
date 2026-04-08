package com.shiroha.mmdskin.voice.pack;

import com.shiroha.mmdskin.voice.VoiceEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoicePackScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldScanManifestAndAudioFolders() throws Exception {
        Path packDir = Files.createDirectories(tempDir.resolve("paimon_cn"));
        Files.writeString(packDir.resolve("voice_pack.json"), """
                {
                  "id": "paimon_cn",
                  "displayName": "Paimon CN",
                  "events": {
                    "attack": {"folder": "attack", "cooldownTicks": 12, "priority": 60},
                    "hurt": {"folder": "hurt", "cooldownTicks": 20, "priority": 80}
                  }
                }
                """, StandardCharsets.UTF_8);
        Files.createDirectories(packDir.resolve("attack"));
        Files.createDirectories(packDir.resolve("hurt"));
        Files.write(packDir.resolve("attack/01.ogg"), new byte[]{1, 2, 3});
        Files.write(packDir.resolve("attack/02.wav"), new byte[]{1, 2, 3});
        Files.write(packDir.resolve("hurt/01.ogg"), new byte[]{1, 2, 3});

        List<VoicePackDefinition> packs = VoicePackScanner.scan(tempDir.toFile());

        assertEquals(1, packs.size());
        VoicePackDefinition definition = packs.get(0);
        assertEquals("paimon_cn", definition.getId());
        assertEquals("Paimon CN", definition.getDisplayName());
        assertNotNull(definition.getBinding(VoiceEventType.ATTACK));
        assertEquals(2, definition.getBinding(VoiceEventType.ATTACK).clips().size());
        assertEquals(12, definition.getBinding(VoiceEventType.ATTACK).cooldownTicks());
        assertEquals(80, definition.getBinding(VoiceEventType.HURT).priority());
    }

    @Test
    void shouldScanDetailVariantFolders() throws Exception {
        Path packDir = Files.createDirectories(tempDir.resolve("detail_pack"));
        Files.writeString(packDir.resolve("voice_pack.json"), "{\"id\":\"detail_pack\"}", StandardCharsets.UTF_8);
        Files.createDirectories(packDir.resolve("death/death.attack.arrow.item"));
        Files.createDirectories(packDir.resolve("biome_enter/minecraft/plains"));
        Files.write(packDir.resolve("death/death.attack.arrow.item/01.ogg"), new byte[]{1, 2, 3});
        Files.write(packDir.resolve("biome_enter/minecraft/plains/01.ogg"), new byte[]{1, 2, 3});

        List<VoicePackDefinition> packs = VoicePackScanner.scan(tempDir.toFile());

        VoicePackDefinition definition = packs.get(0);
        assertEquals(1, definition.getBinding(VoiceEventType.DEATH).detailClips().get("death.attack.arrow.item").size());
        assertEquals(1, definition.getBinding(VoiceEventType.BIOME_ENTER).detailClips().get("minecraft/plains").size());
    }

    @Test
    void shouldSkipDuplicatePackIds() throws Exception {
        createPack("pack_a", "shared_id");
        createPack("pack_b", "shared_id");

        List<VoicePackDefinition> packs = VoicePackScanner.scan(tempDir.toFile());

        assertEquals(1, packs.size());
        assertEquals("shared_id", packs.get(0).getId());
    }

    @Test
    void shouldIgnorePackWithoutManifest() throws Exception {
        Files.createDirectories(tempDir.resolve("broken_pack").resolve("attack"));

        List<VoicePackDefinition> packs = VoicePackScanner.scan(tempDir.toFile());

        assertTrue(packs.isEmpty());
    }

    private void createPack(String folderName, String packId) throws Exception {
        Path packDir = Files.createDirectories(tempDir.resolve(folderName));
        Files.writeString(packDir.resolve("voice_pack.json"), "{\"id\":\"" + packId + "\"}", StandardCharsets.UTF_8);
        Files.createDirectories(packDir.resolve("attack"));
        Files.write(packDir.resolve("attack/01.ogg"), new byte[]{1, 2, 3});
    }
}
