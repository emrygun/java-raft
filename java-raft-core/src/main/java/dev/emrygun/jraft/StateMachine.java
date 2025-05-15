package dev.emrygun.jraft;

import dev.emrygun.jraft.log.RaftLogEntry;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface StateMachine {
    CompletableFuture<Boolean> apply(List<RaftLogEntry> entry);
}
