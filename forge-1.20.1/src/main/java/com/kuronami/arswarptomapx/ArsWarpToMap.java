package com.kuronami.arswarptomapx;

import com.kuronami.arswarptomapx.client.AwtmClient;
import com.mojang.logging.LogUtils;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import org.slf4j.Logger;

/**
 * Ars Warp to Map — Ars Nouveau のワープ巻物（通常・安定）が指す行き先を
 * Xaero's Minimap のウェイポイントとして自動表示する、クライアント専用のブリッジ
 * （Forge 1.20.1 / Ars Nouveau 4.x）。
 *
 * <p>構成は 1.21.1 セルと同型。差分は巻物データの読み方だけ（1.20.1 はスタック NBT
 * {@code an_warp_scroll} を読む）。サーバー側の処理もネットワーク通信も持たない。</p>
 *
 * <p>設計方針（v1.0）: config 無しの固定挙動。常時同期・永続ウェイポイント・片方向。</p>
 */
@Mod(ArsWarpToMap.MODID)
public final class ArsWarpToMap {

    public static final String MODID = "arswarptomapx";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArsWarpToMap() {
        if (FMLEnvironment.dist.isClient()) {
            // クライアント専用クラスへの参照はこの分岐の中だけで行う
            AwtmClient.init();
        }
    }
}
