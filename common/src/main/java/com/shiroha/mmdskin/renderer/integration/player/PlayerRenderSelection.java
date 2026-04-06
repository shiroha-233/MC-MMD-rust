package com.shiroha.mmdskin.renderer.integration.player;

final class PlayerRenderSelection {

    private final PlayerRenderAction terminalAction;
    private final String selectedModel;
    private final String playerCacheKey;
    private final boolean localPlayer;

    private PlayerRenderSelection(PlayerRenderAction terminalAction,
                                  String selectedModel,
                                  String playerCacheKey,
                                  boolean localPlayer) {
        this.terminalAction = terminalAction;
        this.selectedModel = selectedModel;
        this.playerCacheKey = playerCacheKey;
        this.localPlayer = localPlayer;
    }

    static PlayerRenderSelection terminal(PlayerRenderAction action) {
        return new PlayerRenderSelection(action, null, null, false);
    }

    static PlayerRenderSelection render(String selectedModel, String playerCacheKey, boolean localPlayer) {
        return new PlayerRenderSelection(null, selectedModel, playerCacheKey, localPlayer);
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
}
