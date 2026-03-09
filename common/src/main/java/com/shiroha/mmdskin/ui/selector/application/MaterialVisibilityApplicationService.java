package com.shiroha.mmdskin.ui.selector.application;

import com.shiroha.mmdskin.ui.selector.port.MaterialVisibilityGateway;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class MaterialVisibilityApplicationService {
    private final MaterialVisibilityGateway gateway;

    public MaterialVisibilityApplicationService(MaterialVisibilityGateway gateway) {
        this.gateway = gateway;
    }

    public Optional<MaterialScreenContext> createPlayerContext() {
        return gateway.createPlayerContext();
    }

    public Optional<MaterialScreenContext> createMaidContext(UUID maidUuid, String maidName) {
        return gateway.createMaidContext(maidUuid, maidName);
    }

    public List<MaterialEntryState> loadMaterials(MaterialScreenContext context) {
        return new ArrayList<>(gateway.loadMaterials(context.modelHandle()));
    }

    public void setAllVisible(MaterialScreenContext context, List<MaterialEntryState> materials, boolean visible) {
        gateway.setAllVisible(context.modelHandle(), visible);
        for (MaterialEntryState material : materials) {
            material.setVisible(visible);
        }
    }

    public void invertSelection(MaterialScreenContext context, List<MaterialEntryState> materials) {
        for (MaterialEntryState material : materials) {
            boolean visible = !material.visible();
            material.setVisible(visible);
            gateway.setMaterialVisible(context.modelHandle(), material.index(), visible);
        }
    }

    public void toggleMaterial(MaterialScreenContext context, List<MaterialEntryState> materials, int listIndex) {
        if (listIndex < 0 || listIndex >= materials.size()) {
            return;
        }

        MaterialEntryState material = materials.get(listIndex);
        boolean visible = !material.visible();
        material.setVisible(visible);
        gateway.setMaterialVisible(context.modelHandle(), material.index(), visible);
    }

    public void save(MaterialScreenContext context, List<MaterialEntryState> materials) {
        if (context.configModelName() == null || context.configModelName().isEmpty()) {
            return;
        }

        Set<Integer> hiddenMaterials = new HashSet<>();
        for (MaterialEntryState material : materials) {
            if (!material.visible()) {
                hiddenMaterials.add(material.index());
            }
        }
        gateway.saveHiddenMaterials(context.configModelName(), hiddenMaterials);
    }

    public record MaterialScreenContext(long modelHandle, String modelName, String configModelName) {
    }

    public static final class MaterialEntryState {
        private final int index;
        private final String name;
        private boolean visible;

        public MaterialEntryState(int index, String name, boolean visible) {
            this.index = index;
            this.name = name;
            this.visible = visible;
        }

        public int index() {
            return index;
        }

        public String name() {
            return name;
        }

        public boolean visible() {
            return visible;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
        }
    }
}
