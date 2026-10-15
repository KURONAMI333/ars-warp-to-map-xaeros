package com.kuronami.arswarptomapx.client;

import com.kuronami.arswarptomapx.ArsWarpToMap;

import net.minecraft.client.Minecraft;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * クライアント側の配線（1.21.1 / NeoForge）。このクラスはクライアント dist でしか
 * ロードされない（{@link ArsWarpToMap} の dist 分岐からのみ参照される）。
 *
 * <p>役割: インベントリの走査と Xaero's Minimap への反映を一定間隔で回すだけ。
 * 巻物の読み取りは {@link InventoryScrollReader}、反映は {@link SyncManager} が担う。</p>
 */
public final class AwtmClient {

    /**
     * 走査と反映の間隔（tick）。ワールド参加直後・次元移動後もこの周期で必ず一度は
     * 反映される（1 秒以内）。巻物の出し入れは人間の操作速度なのでこれで十分追従する。
     */
    private static final int SYNC_INTERVAL_TICKS = 20;

    private AwtmClient() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(AwtmClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(AwtmClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(AwtmClient::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(AwtmClient::onLoggingOut);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        SyncManager.resetSession();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SyncManager.resetSession();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
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
