package dev.emrygun.jraft.command;

import dev.emrygun.jraft.RaftNodeImpl;
import dev.emrygun.jraft.RaftNodeEndpoint;

public record RequestVote(
        long term,
        RaftNodeEndpoint candidateId,
        long lastLogIndex,
        long lastLogTerm
) implements Command {
    public static RequestVote of(RaftNodeImpl raftNode) {
        return new RequestVote(
                raftNode.getCurrentTerm(),
                raftNode.getSelf(),
                raftNode.getRaftLog().getLatest().getIndex(),
                raftNode.getRaftLog().getLatest().getTerm()
        );
    }
}
