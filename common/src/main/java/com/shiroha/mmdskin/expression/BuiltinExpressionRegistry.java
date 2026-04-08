package com.shiroha.mmdskin.expression;

import com.shiroha.mmdskin.ui.config.MorphWheelConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BuiltinExpressionRegistry {
    private static final Map<String, BuiltinExpressionPreset> PRESETS = new LinkedHashMap<>();

    static {
        register(new BuiltinExpressionPreset("smile", "gui.mmdskin.expression.smile",
                List.of(target(0.85f, aliases("にこり", "happy", "joy", "smile", "笑顔", "笑い"))),
                List.of(target(0.20f, aliases("え", "ee", "e")))));

        register(new BuiltinExpressionPreset("grin", "gui.mmdskin.expression.grin",
                List.of(target(0.90f, aliases("にやり", "grin", "smirk", "にこり", "happy", "joy"))),
                List.of(target(0.35f, aliases("あ", "aa", "a", "口開き", "mouthopen", "openmouth")))));

        register(new BuiltinExpressionPreset("laugh", "gui.mmdskin.expression.laugh",
                List.of(target(0.95f, aliases("笑い", "laugh", "laughter", "にこり", "happy", "joy"))),
                List.of(target(0.45f, aliases("あ", "aa", "a", "口開き", "mouthopen", "openmouth")))));

        register(new BuiltinExpressionPreset("angry", "gui.mmdskin.expression.angry",
                List.of(target(0.85f, aliases("怒り", "angry", "ikari"))),
                List.of(target(0.45f, aliasesWithTokens(
                        List.of("怒り眉", "angrybrow", "browangry"),
                        List.of(List.of("brow", "angry"), List.of("eyebrow", "angry"), List.of("眉", "怒")))))));

        register(new BuiltinExpressionPreset("sad", "gui.mmdskin.expression.sad",
                List.of(target(0.85f, aliases("悲しい", "sad", "sorrow", "悲しみ"))),
                List.of(target(0.30f, aliasesWithTokens(
                        List.of("困る", "sadbrow", "browsad"),
                        List.of(List.of("brow", "sad"), List.of("eyebrow", "sad"), List.of("眉", "悲"), List.of("困")))))));

        register(new BuiltinExpressionPreset("cry", "gui.mmdskin.expression.cry",
                List.of(target(0.95f, aliasesWithTokens(
                        List.of("悲しい", "sad", "sorrow", "悲しみ", "泣き", "泣き顔", "cry", "crying", "涙", "tear", "tears", "困る"),
                        List.of(
                                List.of("悲"),
                                List.of("sad"),
                                List.of("sorrow"),
                                List.of("泣"),
                                List.of("cry"),
                                List.of("涙"),
                                List.of("tear"),
                                List.of("困")
                        )))),
                List.of(
                        target(0.55f, aliases("涙", "tears", "tear")),
                        target(0.40f, aliasesWithTokens(
                                List.of("困る", "sadbrow", "browsad", "泣き眉", "crybrow"),
                                List.of(
                                        List.of("brow", "sad"),
                                        List.of("eyebrow", "sad"),
                                        List.of("眉", "悲"),
                                        List.of("眉", "泣"),
                                        List.of("困")
                                ))),
                        target(0.30f, aliases("まばたき", "blink", "眨眼"))
                )));

        register(new BuiltinExpressionPreset("surprised", "gui.mmdskin.expression.surprised",
                List.of(target(0.95f, aliasesWithTokens(
                        List.of("びっくり", "surprised", "surprise", "fun", "驚き", "瞳小", "小瞳", "pupilsmall", "smallpupil", "smalliris", "irissmall"),
                        List.of(
                                List.of("pupil", "small"),
                                List.of("iris", "small"),
                                List.of("瞳", "小"),
                                List.of("目", "小"),
                                List.of("surprise", "pupil")
                        )))),
                List.of(
                        target(0.90f, aliasesWithTokens(
                                List.of("お", "oh", "o", "丸口", "roundmouth", "mouthround", "びっくり口", "surprisedmouth"),
                                List.of(
                                        List.of("mouth", "round"),
                                        List.of("口", "丸"),
                                        List.of("surprise", "mouth"),
                                        List.of("びっくり", "口")
                                ))),
                        target(0.80f, aliasesWithTokens(
                                List.of("口開き", "mouthopen", "openmouth", "あ", "aa", "a"),
                                List.of(
                                        List.of("mouth", "open"),
                                        List.of("口", "開")
                                ))),
                        target(0.65f, aliasesWithTokens(
                                List.of("瞳小", "小瞳", "pupilsmall", "smallpupil", "smalliris", "irissmall"),
                                List.of(
                                        List.of("pupil", "small"),
                                        List.of("iris", "small"),
                                        List.of("瞳", "小"),
                                        List.of("目", "小")
                                ))),
                        target(0.35f, aliasesWithTokens(
                                List.of("上眉", "raisebrow", "surprisebrow", "驚き眉"),
                                List.of(
                                        List.of("brow", "raise"),
                                        List.of("eyebrow", "raise"),
                                        List.of("眉", "上"),
                                        List.of("眉", "驚")
                                )))
                )));

        register(new BuiltinExpressionPreset("blink", "gui.mmdskin.expression.blink",
                List.of(target(1.0f, aliases("まばたき", "blink", "blinkboth", "眨眼", "まばたき両目"))),
                List.of()));

        register(new BuiltinExpressionPreset("wink_left", "gui.mmdskin.expression.wink_left",
                List.of(target(1.0f, aliases("ウィンク", "wink", "blinkleft", "blink_l"))),
                List.of()));

        register(new BuiltinExpressionPreset("wink_right", "gui.mmdskin.expression.wink_right",
                List.of(target(1.0f, aliases("ウィンク右", "winkright", "blinkright", "blink_r"))),
                List.of()));

        register(new BuiltinExpressionPreset("mouth_open", "gui.mmdskin.expression.mouth_open",
                List.of(target(0.75f, aliasesWithTokens(
                        List.of("あ", "aa", "a", "口開き", "mouthopen", "openmouth"),
                        List.of(List.of("mouth", "open"), List.of("口", "開"))))),
                List.of(target(0.35f, aliases("お", "oh", "o")))));
    }

    private BuiltinExpressionRegistry() {
    }

    public static List<BuiltinExpressionPreset> all() {
        return List.copyOf(PRESETS.values());
    }

    public static BuiltinExpressionPreset find(String id) {
        return PRESETS.get(id);
    }

    public static List<MorphWheelConfig.MorphEntry> createConfigEntries() {
        return all().stream()
                .map(preset -> MorphWheelConfig.MorphEntry.fromPreset(preset.id(), preset.displayName()))
                .toList();
    }

    private static void register(BuiltinExpressionPreset preset) {
        PRESETS.put(preset.id(), preset);
    }

    private static BuiltinExpressionPreset.MorphTarget target(float weight, ExpressionMatchRule rule) {
        return new BuiltinExpressionPreset.MorphTarget(rule, weight);
    }

    private static ExpressionMatchRule aliases(String... aliases) {
        return ExpressionMatchRule.of(List.of(aliases), List.of());
    }

    private static ExpressionMatchRule aliasesWithTokens(List<String> aliases, List<List<String>> tokenGroups) {
        return ExpressionMatchRule.of(aliases, tokenGroups);
    }
}
