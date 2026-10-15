package com.kuronami.arswarptomapx.compat.ars;

import com.hollingsworth.arsnouveau.common.items.WarpScroll;
import com.kuronami.arswarptomapx.sync.ScrollDestination;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.fml.ModList;

/**
 * Ars Nouveau のワープ巻物（1.20.1 = スタック NBT {@code an_warp_scroll}）から
 * 行き先を読み出す。
 *
 * <p>Ars 不在時に {@code NoClassDefFoundError} を起こさないよう、Ars の型への参照は
 * すべて {@link Inner} に閉じ込める。外側クラスは Ars の型を signature・フィールドに
 * 一切持たず、{@link #isArsLoaded()} が true のときだけ Inner を呼ぶ。</p>
 *
 * <p>アイテム判定は登録 ID 文字列で行う（{@code stable_warp_scroll} は {@code WarpScroll}
 * を継承しない別クラスなので、クラスの instanceof では取りこぼすため）。</p>
 */
public final class ArsScrollReader {

    private static final String ARS_MOD_ID = "ars_nouveau";
    static final String WARP_SCROLL = "ars_nouveau:warp_scroll";
    static final String STABLE_WARP_SCROLL = "ars_nouveau:stable_warp_scroll";

    private ArsScrollReader() {
    }

    public static boolean isArsLoaded() {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(ARS_MOD_ID);
    }

    /**
     * 巻物なら行き先を、そうでない・行き先が無い・壊れているなら null を返す。
     * 決して例外を投げない。
     */
    public static ScrollDestination read(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isArsLoaded() || !isWarpScroll(stack)) {
            return null;
        }
        try {
            return Inner.read(stack);
        } catch (Throwable t) {
            // Ars のデータ形式が変わった等。その巻物だけ諦める（クラッシュさせない）
            return null;
        }
    }

    private static boolean isWarpScroll(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String key = id == null ? "" : id.toString();
        return key.equals(WARP_SCROLL) || key.equals(STABLE_WARP_SCROLL);
    }

    private static final class Inner {

        static ScrollDestination read(ItemStack stack) {
            // Ars の get() は ItemstackData 経由で stack.getOrCreateTag() を呼ぶため、
            // 実スタックを渡すとクライアント側の ItemStack を書き換えてしまう。
            // copy() は NBT を深く複製するので、複製に対して読む。
            WarpScroll.WarpScrollData data = WarpScroll.WarpScrollData.get(stack.copy());
            if (data == null || !data.isValid()) {
                return null;
            }
            BlockPos pos = data.getPos();
            String dimension = data.getDimension();
            if (pos == null || dimension == null || dimension.isEmpty()) {
                return null;
            }
            return new ScrollDestination(
                    stack.getHoverName().getString(), pos.getX(), pos.getY(), pos.getZ(), dimension);
        }
    }
}
