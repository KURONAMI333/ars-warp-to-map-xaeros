package com.kuronami.arswarptomapx.client;

import com.kuronami.arswarptomapx.ArsWarpToMap;

import net.minecraft.client.Minecraft;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * クライアント側の配線（Forge 1.20.1）。このクラスはクライアント dist でしか
 * ロードされない（{@link ArsWarpToMap} の dist 分岐からのみ参照される）。
 *
 * <p>役割: インベントリの走査と Xaero's Minimap への反映を一定間隔で回すだけ。
 * 巻物の読み取りは {@link InventoryScrollReader}、反映は {@link SyncManager} が担う。</p>
 */
public final class AwtmClient {

    /**
     * 走査と反映の間隔（tick）。ワールド参加直後・次元移動後もこの周期で必ず一度は
     * 反映される（1 秒以内）。
     */
    private static final int SYNC_INTERVAL_TICKS = 20;

    private AwtmClient() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(AwtmClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(AwtmClient::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(AwtmClient::onLoggingOut);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        SyncManager.resetSession();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SyncManager.resetSession();
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if ((mc.player.tickCount % SYNC_INTERVAL_TICKS) != 0) {
            return;
        }
        try {
            SyncManager.refresh();
            SyncManager.flushPending();
        } catch (Throwable t) {
            ArsWarpToMap.LOGGER.warn("Ars Warp to Map sync cycle failed: {}", t.toString());
        }
    }
}
