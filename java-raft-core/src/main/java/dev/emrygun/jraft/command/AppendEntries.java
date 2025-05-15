package dev.emrygun.jraft.command;

import dev.emrygun.jraft.RaftNodeImpl;
import dev.emrygun.jraft.log.RaftLogEntry;
import dev.emrygun.jraft.RaftNodeEndpoint;

import java.util.List;

public record AppendEntries(
    long term,
    RaftNodeEndpoint leaderId,
    long prevLogIndex,
    long prevLogTerm,
    List<RaftLogEntry> entries,
    long leaderCommit
) implements Command {
    public static AppendEntries of(RaftNodeImpl raftNode, RaftNodeEndpoint nodeEndpoint) {
        long nextIndex = raftNode.getNextIndex(nodeEndpoint);
        RaftLogEntry prevLog = raftNode.getRaftLog().getLatest(nextIndex - 1);

        return new AppendEntries(
                raftNode.getCurrentTerm(),
                raftNode.getSelf(),
                prevLog.getIndex(),
                prevLog.getTerm(),
                raftNode.getRaftLog().getEntriesFrom(nextIndex),
                raftNode.getCommitIndex()
        );
    }
}