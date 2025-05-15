package dev.emrygun.jraft;

import dev.emrygun.jraft.event.RaftEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class FollowerState extends RaftState {
    private static final Logger LOG = LoggerFactory.getLogger(FollowerState.class);

    FollowerState(RaftNodeImpl node) {
        super(node);
    }

    @Override
    public synchronized void act() {
        while (true) {
            RaftEvent event = pollEventWithTimeout(getElectionTimeout());
            switch (event) {
                case RaftEvent.NewEntryEvent newEntryEvent -> {
                    continue;
                }
                case null -> {
                    LOG.trace("Election timeout expired. Transitioning to candidate");
                    getNode().toCandidate();
                    return;
                }
                default -> throw new IllegalStateException("Unexpected value: " + event);
            }
        }
    }

    @Override
    public void onTerminate() {
        // Nothing to do here
    }

    // Random election timeout between 150 300
    private Duration getElectionTimeout() {
        return Duration.ofMillis(300 + (long) (Math.random() * 150));
    }
}
