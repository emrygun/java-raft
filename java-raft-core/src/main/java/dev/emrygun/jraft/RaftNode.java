package dev.emrygun.jraft;

import dev.emrygun.jraft.command.AppendEntries;
import dev.emrygun.jraft.command.AppendEntriesResponse;
import dev.emrygun.jraft.command.RequestVote;
import dev.emrygun.jraft.command.RequestVoteResponse;
import dev.emrygun.jraft.log.RaftLogEntry;

import java.util.concurrent.CompletableFuture;

public interface RaftNode {
    void start();

    RaftNodeEndpoint getSelf();
    RaftNodeEndpoint getLeader();
    boolean isLeader();

    AppendEntriesResponse appendEntries(AppendEntries cmd);
    RequestVoteResponse requestVote(RequestVote cmd);

    CompletableFuture<RaftLogEntry> handleNewLogEntry(byte[] command);
}
