package com.shiroha.mmdskin.player.render;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryRenderScopeTest {
    @AfterEach
    void clearScope() {
        while (InventoryRenderScope.isActive()) {
            InventoryRenderScope.exit();
        }
    }

    @Test
    void shouldTrackNestedInventoryRenderCalls() {
        InventoryRenderScope.enter();
        InventoryRenderScope.enter();

        InventoryRenderScope.exit();
        assertTrue(InventoryRenderScope.isActive());

        InventoryRenderScope.exit();
        assertFalse(InventoryRenderScope.isActive());
    }

    @Test
    void extraExitShouldKeepScopeInactive() {
        InventoryRenderScope.exit();

        assertFalse(InventoryRenderScope.isActive());
    }
}
