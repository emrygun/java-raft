package dev.emrygun.jraft.handler;

import dev.emrygun.jraft.RaftNodeImpl;
import dev.emrygun.jraft.command.Command;
import dev.emrygun.jraft.command.CommandResponse;

/**
 * Java RMI command handler
 */
public abstract class CommandHandler<RPC extends Command, RPCResponse extends CommandResponse> {
    protected final RaftNodeImpl node;

    protected CommandHandler(RaftNodeImpl node) {
        this.node = node;
    }

    public abstract CommandResponse handle(RPC cmd);
}
