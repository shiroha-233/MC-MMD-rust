// 负责按已安装模组组裁剪 NeoForge 侧可选 Mixin。
package com.shiroha.mmdskin.mixin.neoforge.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class MmdNeoForgeMixinPlugin implements IMixinConfigPlugin {
    private static final String VIVECRAFT_MIXIN_SUFFIX = ".VivecraftVRArmHelperMixin";
    /** Vivecraft 那个可选 mixin 的目标类。 */
    private static final String VIVECRAFT_TARGET_CLASS = "org.vivecraft.client_vr.render.helpers.VRArmHelper";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.endsWith(VIVECRAFT_MIXIN_SUFFIX)) {
            return true;
        }
        // 这里**不能**用 ModList.get().isLoaded("vivecraft")：
        // Mixin 的 PREPARE 阶段早于 FML 初始化 ModList，那时 ModList.get() 返回
        // null，直接 NPE 会让整个模组加载失败（"Error loading mods"）。
        // 改成直接探测目标类在不在：类在就应用，不在就跳过。
        return vivecraftTargetPresent();
    }

    /**
     * 目标类是否可见。
     *
     * <p>用类探测而不是 {@code ModList}：ModList 的初始化时机晚于 Mixin 配置求值，
     * 而且按 mod id 判断也不如看类准（类才是 mixin 真正需要的东西）。
     * 探测不出来（类加载器隔离等意外）时返回 true，交给 mixin 自身的
     * {@code @Pseudo} 兜底 —— 目标类不存在时 {@code @Pseudo} 会自动跳过。
     */
    private static boolean vivecraftTargetPresent() {
        try {
            Class.forName(VIVECRAFT_TARGET_CLASS, false, MmdNeoForgeMixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        } catch (Throwable unknown) {
            return true;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
