import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.*;

public interface TrackerInterface extends Remote{
	int getGridSize() throws RemoteException;
	int getNoOfTreasures() throws RemoteException;
	public static final String RMI_NAME = "TRACKER";
	public ArrayList<PlayerInfo> getPlayerList(PlayerInfo player) throws RemoteException;
	public void removePlayers(ArrayList<PlayerInfo> playerlist) throws RemoteException;
}
