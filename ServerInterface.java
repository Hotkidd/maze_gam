import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;


public interface ServerInterface extends Remote{
	//game server registry names
	public static final String PRIMARY_SERVER_RMI_NAME = "primaryServer";
	public static final String BACKUP_SERVER_RMI_NAME = "backupServer";
	//player join
	GameState joinGame(PlayerInfo client) throws RemoteException;
	//player move
	GameState move(PlayerInfo client, int direction) throws RemoteException;
	//update game state, used for primary server to update backup server
	public void updateGameState(GameState gameState) throws RemoteException;
	//use for one server to info the other server, especially during setup, 2nd Player Join..
	public void updateServer(PlayerInfo theOtherServer) throws RemoteException;
	//use to update failed client with server
	GameState clientFailed(PlayerInfo failedClient) throws RemoteException;
	PlayerInfo getBackupServer() throws RemoteException;
	PlayerInfo getPrimaryServer() throws RemoteException;
}
