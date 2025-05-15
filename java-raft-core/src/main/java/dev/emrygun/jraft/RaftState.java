package dev.emrygun.jraft;

import dev.emrygun.jraft.event.RaftEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public abstract class RaftState {
    private static final Logger LOG = LoggerFactory.getLogger(RaftState.class);

    private final RaftNodeImpl node;

    protected RaftState(RaftNodeImpl node) {
        this.node = node;
    }

    protected RaftEvent pollEventWithTimeout(Duration timeout) {
        return node.pollEvent(timeout);
    }

    void start() {
        act();
        onTerminate();
    }

    /**
     * Act loop of the state.
     */
    public abstract void act();

    public abstract void onTerminate();

    protected RaftNodeImpl getNode() {
        return node;
    }
}
