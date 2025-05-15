package dev.emrygun.jraft;

import java.rmi.RemoteException;
import java.util.List;

public class RaftNodeFactory {

    public static RaftNode create(String address, StateMachine stateMachine, List<String> peers) throws RemoteException {
        List<RaftNodeEndpoint> peerEndpoints = peers.stream()
                .map(RaftNodeEndpoint::new)
                .toList();

        return new RaftNodeImpl(new RaftNodeEndpoint(address), peerEndpoints, stateMachine);
    }
}
