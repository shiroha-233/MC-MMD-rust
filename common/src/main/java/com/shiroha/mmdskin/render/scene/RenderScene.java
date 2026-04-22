package com.shiroha.mmdskin.render.scene;

/** 文件职责：描述一次模型渲染发生的场景上下文。 */
public final class RenderScene {

    public enum SceneType {
        WORLD,
        INVENTORY,
        ITEM,
        GUI,
        OTHER
    }

    public static final RenderScene WORLD = new Builder().sceneType(SceneType.WORLD).build();
    public static final RenderScene INVENTORY = new Builder().sceneType(SceneType.INVENTORY).mirror(true).build();
    public static final RenderScene FIRST_PERSON = new Builder().sceneType(SceneType.WORLD).firstPerson(true).build();
    public static final RenderScene ITEM = new Builder().sceneType(SceneType.ITEM).build();

    private final SceneType sceneType;
    private final boolean firstPerson;
    private final boolean mirror;

    private RenderScene(Builder builder) {
        this.sceneType = builder.sceneType;
        this.firstPerson = builder.firstPerson;
        this.mirror = builder.mirror;
    }

    public SceneType sceneType() {
        return sceneType;
    }

    public boolean isFirstPerson() {
        return firstPerson;
    }

    public boolean isMirror() {
        return mirror;
    }

    public boolean isWorldScene() {
        return sceneType == SceneType.WORLD;
    }

    public boolean isInventoryScene() {
        return sceneType == SceneType.INVENTORY;
    }

    public static final class Builder {
        private SceneType sceneType = SceneType.WORLD;
        private boolean firstPerson;
        private boolean mirror;

        public Builder sceneType(SceneType sceneType) {
            this.sceneType = sceneType;
            return this;
        }

        public Builder firstPerson(boolean firstPerson) {
            this.firstPerson = firstPerson;
            return this;
        }

        public Builder mirror(boolean mirror) {
            this.mirror = mirror;
            return this;
        }

        public RenderScene build() {
            return new RenderScene(this);
        }
    }
}
