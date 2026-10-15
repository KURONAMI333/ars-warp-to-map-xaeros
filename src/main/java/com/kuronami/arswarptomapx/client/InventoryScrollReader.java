package com.kuronami.arswarptomapx.client;

import com.kuronami.arswarptomapx.compat.ars.ArsScrollReader;
import com.kuronami.arswarptomapx.sync.ScrollDestination;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * プレイヤーのインベントリ（メイン・ホットバー・オフハンド）からワープ巻物の行き先を
 * 読み出す。チェスト・エンダーチェスト・シュルカーボックスは見ない（仕様どおり
 * 「今持っている巻物」だけが対象）。
 *
 * <p>Ars Nouveau が無い環境では空を返す。1 スロットの読み取り失敗はその巻物だけを
 * 捨て、全体は止めない。</p>
 */
public final class InventoryScrollReader {

    private InventoryScrollReader() {
    }

    public static List<ScrollDestination> scan(Player player) {
        List<ScrollDestination> out = new ArrayList<>();
        if (player == null || !ArsScrollReader.isArsLoaded()) {
            return out;
        }
        // メイン 36 スロット（ホットバー含む）+ オフハンド 1 スロット
        for (ItemStack stack : player.getInventory().items) {
            add(out, stack);
        }
        for (ItemStack stack : player.getInventory().offhand) {
            add(out, stack);
        }
        return out;
    }

    private static void add(List<ScrollDestination> out, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ScrollDestination destination = ArsScrollReader.read(stack);
        if (destination != null) {
            out.add(destination);
        }
    }
}
