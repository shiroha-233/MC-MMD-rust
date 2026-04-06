package com.shiroha.mmdskin.renderer.integration.player;

final class PlayerRenderSelection {

    private final PlayerRenderAction terminalAction;
    private final String selectedModel;
    private final String playerCacheKey;
    private final boolean localPlayer;
    private final boolean skipSceneModel;

    private PlayerRenderSelection(PlayerRenderAction terminalAction,
                                  String selectedModel,
                                  String playerCacheKey,
                                  boolean localPlayer,
                                  boolean skipSceneModel) {
        this.terminalAction = terminalAction;
        this.selectedModel = selectedModel;
        this.playerCacheKey = playerCacheKey;
        this.localPlayer = localPlayer;
        this.skipSceneModel = skipSceneModel;
    }

    static PlayerRenderSelection terminal(PlayerRenderAction action) {
        return terminal(action, false);
    }

    static PlayerRenderSelection terminal(PlayerRenderAction action, boolean skipSceneModel) {
        return new PlayerRenderSelection(action, null, null, false, skipSceneModel);
    }

    static PlayerRenderSelection render(String selectedModel, String playerCacheKey, boolean localPlayer) {
        return new PlayerRenderSelection(null, selectedModel, playerCacheKey, localPlayer, false);
    }

    boolean hasTerminalAction() {
        return terminalAction != null;
    }

    PlayerRenderAction terminalAction() {
        return terminalAction;
    }

    String selectedModel() {
        return selectedModel;
    }

    String playerCacheKey() {
        return playerCacheKey;
    }

    boolean isLocalPlayer() {
        return localPlayer;
    }

    boolean shouldSkipSceneModel() {
        return skipSceneModel;
    }
}
