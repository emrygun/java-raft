package dev.emrygun.jraft.rpc;

import dev.emrygun.jraft.command.AppendEntries;
import dev.emrygun.jraft.command.AppendEntriesResponse;
import dev.emrygun.jraft.command.RequestVote;
import dev.emrygun.jraft.command.RequestVoteResponse;
import dev.emrygun.jraft.handler.AppendEntriesCommandHandler;
import dev.emrygun.jraft.handler.RequestVoteCommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class RaftRPCServerImpl extends UnicastRemoteObject implements RaftRPCServer {
    private static final Logger LOG = LoggerFactory.getLogger(RaftRPCServerImpl.class);
    private final AppendEntriesCommandHandler appendEntriesCommandHandler;
    private final RequestVoteCommandHandler requestVoteCommandHandler;

    protected RaftRPCServerImpl(AppendEntriesCommandHandler appendEntriesCommandHandler,
                                RequestVoteCommandHandler requestVoteCommandHandler) throws RemoteException {
        this.appendEntriesCommandHandler = appendEntriesCommandHandler;
        this.requestVoteCommandHandler = requestVoteCommandHandler;
    }

    @Override
    public AppendEntriesResponse appendEntries(AppendEntries request) {
        LOG.trace("Append entries request: {}", request);
        return appendEntriesCommandHandler.handle(request);
    }

    @Override
    public RequestVoteResponse requestVote(RequestVote request) throws RemoteException {
        LOG.trace("Request vote request: {}", request);
        return requestVoteCommandHandler.handle(request);
    }
}
