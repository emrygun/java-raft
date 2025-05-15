package dev.emrygun.jraft.rpc;

import dev.emrygun.jraft.RaftNodeEndpoint;
import dev.emrygun.jraft.handler.AppendEntriesCommandHandler;
import dev.emrygun.jraft.handler.RequestVoteCommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RaftRPCServerFactory {
    private static final Logger LOG = LoggerFactory.getLogger(RaftRPCServerFactory.class);

    public static RaftRPCServer create(AppendEntriesCommandHandler appendEntriesCommandHandler,
                                       RequestVoteCommandHandler requestVoteCommandHandler) {
        try {
            return new RaftRPCServerImpl(appendEntriesCommandHandler, requestVoteCommandHandler);
        } catch (RemoteException e) {
            throw new RuntimeException("Failed to create RaftRPCServer", e);
        }
    }

   public static RaftRPCServer getRemoteNode(RaftNodeEndpoint peer) {
       while (true) {
           try {
               // Connect to the RMI registry on the peer's host
               Registry registry = LocateRegistry.getRegistry(peer.getHost(), peer.getPort());

               // Look up the remote RaftNodeImpl object
               Object remoteObject = registry.lookup("raft");

               // Cast and return the remote RaftNodeImpl instance
               LOG.info("Connected to remote node: {}:{}", peer.getHost(), peer.getPort());
               return (RaftRPCServer) remoteObject;
           } catch (RemoteException | NotBoundException e) {
               LOG.error("Failed to connect to remote node. Retrying in 3 seconds...");
               try {
                   Thread.sleep(3000); // Wait for 3 seconds before retrying
               } catch (InterruptedException interruptedException) {
                   throw new RuntimeException("Retry interrupted", interruptedException);
               }
           }
       }
   }
}
