package com.shiroha.mmdskin.ui.selector.application;

import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService.MaterialEntryState;
import com.shiroha.mmdskin.ui.selector.application.MaterialVisibilityApplicationService.MaterialScreenContext;
import com.shiroha.mmdskin.ui.selector.port.MaterialVisibilityGateway;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialVisibilityApplicationServiceTest {

    @Test
    void shouldToggleSingleMaterial() {
        FakeMaterialGateway gateway = new FakeMaterialGateway();
        MaterialVisibilityApplicationService service = new MaterialVisibilityApplicationService(gateway);
        MaterialScreenContext context = new MaterialScreenContext(9L, "alice", "alice");
        List<MaterialEntryState> materials = gateway.materials;

        service.toggleMaterial(context, materials, 0);

        assertFalse(materials.get(0).visible());
        assertEquals(0, gateway.lastMaterialIndex);
        assertFalse(gateway.lastVisible);
    }

    @Test
    void shouldSetAllVisible() {
        FakeMaterialGateway gateway = new FakeMaterialGateway();
        MaterialVisibilityApplicationService service = new MaterialVisibilityApplicationService(gateway);
        MaterialScreenContext context = new MaterialScreenContext(9L, "alice", "alice");

        service.setAllVisible(context, gateway.materials, true);

        assertTrue(gateway.setAllVisibleCalled);
        assertTrue(gateway.materials.stream().allMatch(MaterialEntryState::visible));
    }

    @Test
    void shouldSaveHiddenMaterialIndexes() {
        FakeMaterialGateway gateway = new FakeMaterialGateway();
        MaterialVisibilityApplicationService service = new MaterialVisibilityApplicationService(gateway);
        MaterialScreenContext context = new MaterialScreenContext(9L, "alice", "alice");

        service.save(context, gateway.materials);

        assertEquals(Set.of(1), gateway.savedHiddenMaterials);
    }

    private static final class FakeMaterialGateway implements MaterialVisibilityGateway {
        private final List<MaterialEntryState> materials = List.of(
                new MaterialEntryState(0, "body", true),
                new MaterialEntryState(1, "hat", false)
        );
        private boolean setAllVisibleCalled;
        private int lastMaterialIndex = -1;
        private boolean lastVisible;
        private Set<Integer> savedHiddenMaterials = Set.of();

        @Override
        public Optional<MaterialScreenContext> createPlayerContext() {
            return Optional.empty();
        }

        @Override
        public Optional<MaterialScreenContext> createMaidContext(UUID maidUuid, String maidName) {
            return Optional.empty();
        }

        @Override
        public List<MaterialEntryState> loadMaterials(long modelHandle) {
            return materials;
        }

        @Override
        public void setAllVisible(long modelHandle, boolean visible) {
            setAllVisibleCalled = true;
        }

        @Override
        public void setMaterialVisible(long modelHandle, int materialIndex, boolean visible) {
            lastMaterialIndex = materialIndex;
            lastVisible = visible;
        }

        @Override
        public void saveHiddenMaterials(String configModelName, Set<Integer> hiddenMaterials) {
            savedHiddenMaterials = hiddenMaterials;
        }
    }
}
