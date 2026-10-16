package com.kuronami.arswarptomapx.sync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 書き込み段階の判定（{@link SyncWriteStage}）のテスト。
 * 最初の書き込み前の例外は再試行でよく、書き込み開始後の失敗だけが記録を不健全に落とす対象になる。
 */
class SyncWriteStageTest {

    @Test
    void beforeFirstWriteDoesNotCorruptRecord() {
        SyncWriteStage stage = new SyncWriteStage();
        assertTrue(stage.beforeFirstWrite());
        assertFalse(stage.failureCorruptsRecord()); // 状態を読むだけの段階の例外 → 再試行
    }

    @Test
    void afterFirstWriteFailureCorruptsRecord() {
        SyncWriteStage stage = new SyncWriteStage();
        stage.beginWrite(); // 最初の削除・作成の直前
        assertFalse(stage.beforeFirstWrite());
        assertTrue(stage.failureCorruptsRecord()); // 部分適用の可能性 → 不健全
    }

    @Test
    void writeStageLatchesStarted() {
        SyncWriteStage stage = new SyncWriteStage();
        stage.beginWrite();
        stage.beginWrite(); // 2件目以降
        assertTrue(stage.failureCorruptsRecord()); // 開始済みのまま戻らない
    }
}
