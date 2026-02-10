package com.shiroha.mmdskin.renderer.core;

/**
 * 渲染上下文 (SRP - 单一职责原则)
 * 
 * 封装渲染时的上下文信息，替代使用调用栈检测的方式。
 * 提供显式的、类型安全的上下文传递机制。
 */
public class RenderContext {
    
    /**
     * 渲染场景类型
     */
    public enum SceneType {
        /** 世界渲染（正常游戏视角） */
        WORLD,
        /** 物品栏/背包界面 */
        INVENTORY,
        /** 物品渲染 */
        ITEM,
        /** GUI 界面 */
        GUI,
        /** 其他 */
        OTHER
    }
    
    private final SceneType sceneType;
    private final boolean isFirstPerson;
    private final boolean isMirror;
    
    private RenderContext(Builder builder) {
        this.sceneType = builder.sceneType;
        this.isFirstPerson = builder.isFirstPerson;
        this.isMirror = builder.isMirror;
    }
    
    public SceneType getSceneType() {
        return sceneType;
    }
    
    public boolean isFirstPerson() {
        return isFirstPerson;
    }
    
    public boolean isMirror() {
        return isMirror;
    }
    
    /**
     * 是否在物品栏场景中（需要镜像头部角度）
     */
    public boolean isInventoryScene() {
        return sceneType == SceneType.INVENTORY;
    }
    
    /**
     * 是否在世界渲染场景中
     */
    public boolean isWorldScene() {
        return sceneType == SceneType.WORLD;
    }
    
    // ==================== 预定义上下文 ====================
    
    /** 世界渲染上下文 */
    public static final RenderContext WORLD = new Builder()
            .sceneType(SceneType.WORLD)
            .build();
    
    /** 物品栏渲染上下文 */
    public static final RenderContext INVENTORY = new Builder()
            .sceneType(SceneType.INVENTORY)
            .mirror(true)
            .build();

    /** 第一人称世界渲染上下文 */
    public static final RenderContext FIRST_PERSON = new Builder()
            .sceneType(SceneType.WORLD)
            .firstPerson(true)
            .build();
    
    /** 物品渲染上下文 */
    public static final RenderContext ITEM = new Builder()
            .sceneType(SceneType.ITEM)
            .build();
    
    // ==================== Builder ====================
    
    public static class Builder {
        private SceneType sceneType = SceneType.WORLD;
        private boolean isFirstPerson = false;
        private boolean isMirror = false;
        
        public Builder sceneType(SceneType sceneType) {
            this.sceneType = sceneType;
            return this;
        }
        
        public Builder firstPerson(boolean isFirstPerson) {
            this.isFirstPerson = isFirstPerson;
            return this;
        }
        
        public Builder mirror(boolean isMirror) {
            this.isMirror = isMirror;
            return this;
        }
        
        public RenderContext build() {
            return new RenderContext(this);
        }
    }
}
