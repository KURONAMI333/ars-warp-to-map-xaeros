package com.kuronami.arswarptomapx.sync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 記録の健全性のセッション内遷移（{@link RecordHealth}）のテスト。
 * 同期の失敗・保存の失敗で不健全に落ち、そのセッション中は戻らないことを守る。
 */
class RecordHealthTest {

    @Test
    void syncFailureDropsAndLatches() {
        assertFalse(RecordHealth.afterSync(true, true));   // 例外で中断 → 不健全
        assertFalse(RecordHealth.afterSync(false, false)); // 一度落ちたら戻らない
        assertTrue(RecordHealth.afterSync(true, false));   // 正常終了なら維持
    }

    @Test
    void saveFailureDropsAndLatches() {
        assertFalse(RecordHealth.afterSave(true, false));  // 保存失敗 → 不健全
        assertFalse(RecordHealth.afterSave(false, true));  // 一度落ちたら戻らない
        assertTrue(RecordHealth.afterSave(true, true));    // 保存成功なら維持
    }
}
