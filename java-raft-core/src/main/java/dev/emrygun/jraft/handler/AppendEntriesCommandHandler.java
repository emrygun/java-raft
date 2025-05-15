package dev.emrygun.jraft.handler;

import dev.emrygun.jraft.log.RaftLogEntry;
import dev.emrygun.jraft.RaftNodeImpl;
import dev.emrygun.jraft.command.AppendEntries;
import dev.emrygun.jraft.command.AppendEntriesResponse;
import dev.emrygun.jraft.event.RaftEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the AppendEntries command in the Raft consensus algorithm.
 */
public class AppendEntriesCommandHandler extends CommandHandler<AppendEntries, AppendEntriesResponse> {
    private static final Logger LOG = LoggerFactory.getLogger(AppendEntriesCommandHandler.class);
    public AppendEntriesCommandHandler(RaftNodeImpl node) {
        super(node);
    }

    @Override
    public AppendEntriesResponse handle(AppendEntries cmd) {
        LOG.trace("Handling AppendEntries command: {}", cmd);
        if (cmd.term() > node.getCurrentTerm()) {
            LOG.trace("Stepping down. term {} > {}", cmd.term(), node.getCurrentTerm());
            // node.stepDown(cmd.term(), String.format("AE term %d > current term %d", cmd.term(), node.getCurrentTerm()));
            node.stepDownEvent(cmd.term());
        }

        // 1. Reply false if term < currentTerm (§5.1)
        if (cmd.term() < node.getCurrentTerm()) {
            return new AppendEntriesResponse(node.getCurrentTerm(), false);
        }

        // 2. Reply false if log doesn’t contain an entry at prevLogIndex whose term matches prevLogTerm (§5.3)
        RaftLogEntry prevLogEntry = node.getRaftLog().getLatest();

        if (prevLogEntry == null) {
            if (cmd.prevLogIndex() != 0 || cmd.prevLogTerm() != 0) {
                return new AppendEntriesResponse(node.getCurrentTerm(), false);
            }
        } else if (prevLogEntry.getTerm() != cmd.prevLogTerm()) {
            return new AppendEntriesResponse(node.getCurrentTerm(), false);
        }

        // 3. If an existing entry conflicts with a new one (same index but different terms),
        // delete the existing entry and all that follow it (§5.3)
        for (RaftLogEntry entry : cmd.entries()) {
            RaftLogEntry existingEntry = node.getRaftLog().getEntry(entry.getIndex());
            if (existingEntry != null && existingEntry.getTerm() != entry.getTerm()) {
                node.getRaftLog().deleteEntriesFrom(entry.getIndex());
                break;
            }
        }

        // 4. Append any new entries not already in the log
        node.getRaftLog().appendEntries(cmd.entries());

        // 5. If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of last new entry)
        if (cmd.leaderCommit() > node.getCommitIndex()) {
            node.updateCommitIndex(cmd.leaderCommit());
        }

        // RPC is successful
        node.setLeader(cmd.leaderId());
        node.newEntryEvent();
        return new AppendEntriesResponse(node.getCurrentTerm(), true);
    }
}
