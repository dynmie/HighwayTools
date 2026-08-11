package me.dynmie.highway.highwaytools.block;

public enum TaskState {
    BROKEN(1000, 1000),
    PLACED(1000, 1000),
    LIQUID(100, 100),
    BREAKING(100, 100),
    BREAK(20, 20),
    PLACE(20, 20),
    PENDING_BREAK(100, 100),
    PENDING_PLACE(100, 100),
    IMPOSSIBLE_PLACE(20, 20),
    DONE(72727, 1);

    private final int stuckThreshold;
    private final int stuckTimeout;

    TaskState(int stuckThreshold, int stuckTimeout) {
        this.stuckThreshold = stuckThreshold;
        this.stuckTimeout = stuckTimeout;
    }

    public int getStuckThreshold() {
        return stuckThreshold;
    }

    public int getStuckTimeout() {
        return stuckTimeout;
    }
}
