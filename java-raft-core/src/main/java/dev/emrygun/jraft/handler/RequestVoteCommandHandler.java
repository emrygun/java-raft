package dev.emrygun.jraft.handler;

import dev.emrygun.jraft.RaftNodeImpl;
import dev.emrygun.jraft.command.RequestVote;
import dev.emrygun.jraft.command.RequestVoteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestVoteCommandHandler extends CommandHandler<RequestVote, RequestVoteResponse> {
    private static final Logger LOG = LoggerFactory.getLogger(RequestVoteCommandHandler.class);

    public RequestVoteCommandHandler(RaftNodeImpl node) {
        super(node);
    }

    @Override
    public RequestVoteResponse handle(RequestVote cmd) {
        synchronized (this.node) {
            // FIXME: IT NEEDS TO BE COMMON
            if (cmd.term() > node.getCurrentTerm()) {
                LOG.trace("Stepping down. term {} > {}", cmd.term(), node.getCurrentTerm());
                // node.stepDown(cmd.term(), String.format("RV term %d > current term %d", cmd.term(), node.getCurrentTerm()));
                node.stepDownEvent(cmd.term());
            }

            // Rule 1: Reply false if term < currentTerm
            if (cmd.term() < node.getCurrentTerm()) {
                LOG.trace("false for candidate candidate: {}, currentTerm: {}, cmdDterm: {}", cmd.candidateId(), node.getCurrentTerm(), cmd.term());
                return new RequestVoteResponse(node.getCurrentTerm(), false);
            }

            // Rule 2: Check if vote can be granted
            if ((node.getVotedFor() == null || node.getVotedFor() == cmd.candidateId()) &&
                isLogUpToDate(cmd.lastLogIndex(), cmd.lastLogTerm())) {
                node.setVotedFor(cmd.candidateId());
                LOG.trace("true for candidate {}, term: {}", cmd.candidateId(), node.getCurrentTerm());
                return new RequestVoteResponse(node.getCurrentTerm(), true);
            }

            LOG.trace("false for candidate {}, term: {}, votedFor: {}", cmd.candidateId(), node.getCurrentTerm(), node.getVotedFor());
            return new RequestVoteResponse(node.getCurrentTerm(), false);
        }
    }

    private boolean isLogUpToDate(long candidateLastLogIndex, long candidateLastLogTerm) {
        var lastLog = node.getRaftLog().getLatest();
        if (lastLog == null) {
            return true; // If the node has no logs, it is considered up-to-date
        }

        long lastLogIndex = lastLog.getIndex();
        long lastLogTerm = lastLog.getTerm();

        // Candidate's log is more up-to-date if its term is higher, or if terms are equal and its index is greater
        return candidateLastLogTerm > lastLogTerm ||
               (candidateLastLogTerm == lastLogTerm && candidateLastLogIndex >= lastLogIndex);
    }
}
