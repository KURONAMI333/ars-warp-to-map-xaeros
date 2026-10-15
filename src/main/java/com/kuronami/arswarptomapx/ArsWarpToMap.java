package com.kuronami.arswarptomapx;

import com.kuronami.arswarptomapx.client.AwtmClient;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

import org.slf4j.Logger;

/**
 * Ars Warp to Map — Ars Nouveau のワープ巻物（通常・安定）が指す行き先を
 * Xaero's Minimap のウェイポイントとして自動表示する、クライアント専用のブリッジ。
 *
 * <p>構成: クライアントのインベントリ（メイン・ホットバー・オフハンド）から巻物の
 * 行き先を読み、あるべきウェイポイント集合として Xaero's Minimap へ同期する。
 * サーバー側の処理もネットワーク通信も持たない（サーバーに入れても何もしない）。</p>
 *
 * <p>設計方針（v1.0）: config 無しの固定挙動。常時同期・永続ウェイポイント・片方向。</p>
 */
@Mod(ArsWarpToMap.MODID)
public final class ArsWarpToMap {

    public static final String MODID = "arswarptomapx";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArsWarpToMap(IEventBus modEventBus, ModContainer modContainer) {
        if (FMLEnvironment.dist.isClient()) {
            // クライアント専用クラスへの参照はこの分岐の中だけで行う
            AwtmClient.init(modEventBus);
        }
    }
}
