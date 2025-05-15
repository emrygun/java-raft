package dev.emrygun.jraft.rpc;

import dev.emrygun.jraft.command.AppendEntries;
import dev.emrygun.jraft.command.AppendEntriesResponse;
import dev.emrygun.jraft.command.RequestVote;
import dev.emrygun.jraft.command.RequestVoteResponse;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RaftRPCServer extends Remote {
    AppendEntriesResponse appendEntries(AppendEntries request) throws RemoteException;
    RequestVoteResponse requestVote(RequestVote request) throws RemoteException;
}
