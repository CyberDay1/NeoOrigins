package net.minecraft.gametest.framework;

public interface GameTestListener {
    void testStructureLoaded(GameTestInfo p_127651_);

    void testPassed(GameTestInfo p_177494_, GameTestRunner p_320110_);

    void testFailed(GameTestInfo p_127652_, GameTestRunner p_320238_);

    void testAddedForRerun(GameTestInfo p_320937_, GameTestInfo p_320294_, GameTestRunner p_320147_);
}
