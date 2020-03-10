import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClientInterface extends Remote {
	boolean promoteToServer(boolean isPrimary, GameState gameState, PlayerInfo theOtherServer) throws RemoteException;
	PlayerInfo getPrimaryServer() throws RemoteException;
	PlayerInfo getBackupServer() throws RemoteException;
	void checkLiveness() throws RemoteException;
}