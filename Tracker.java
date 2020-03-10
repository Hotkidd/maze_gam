import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*; 

public class Tracker implements TrackerInterface{
	
	//well-known port number
	public static String portNumber = "";
	
	//list of current players, N, & K
	public static ArrayList<PlayerInfo> listOfPlayers = new ArrayList<PlayerInfo>();
	static int N, K;
	
	public static void main(String[] args) throws RemoteException, AlreadyBoundException{
		if(args.length != 3){
			System.out.println("Wrong number of parameters...exiting");
			System.exit(0);
		}
		portNumber = args[0];
		N = Integer.valueOf(args[1]);
		K = Integer.valueOf(args[2]);
		
		System.out.println("The grid size is " + N + " * " + N);
		System.out.println("The total number of treasures is " + K);
		//System.out.println(getPlayerList());
		
		if (!Utilities.startRMIRegistry()) {
			System.err.println("Failed to start RMI registry!");
			return;
		}
		
		Tracker tracker = new Tracker();
		TrackerInterface stub = null;
		Registry registry = null;
		
		try {
			stub = (TrackerInterface) UnicastRemoteObject.exportObject(tracker, 0);
			registry = LocateRegistry.getRegistry();
			registry.bind(TrackerInterface.RMI_NAME, stub);
		} catch (Exception e) {
		    try {
		    	registry.unbind(TrackerInterface.RMI_NAME);
		    	registry.bind(TrackerInterface.RMI_NAME, stub);
			}catch(Exception ee){
				System.err.println("Tracker binding exception: " + ee.toString());
			 	ee.printStackTrace();
			 	System.exit(1);
			}
		}
	}
	
	@Override
	public int getGridSize(){
		return N;
	}
	
	@Override
	public int getNoOfTreasures(){
		return K;
	}
	
	@Override
	public synchronized ArrayList<PlayerInfo> getPlayerList(PlayerInfo player) throws RemoteException {
		synchronized (listOfPlayers) {
			listOfPlayers.add(player);
			return listOfPlayers;
		}
	}

	@Override
	public synchronized void removePlayers(ArrayList<PlayerInfo> playerlist) throws RemoteException {
		synchronized (listOfPlayers) {
			listOfPlayers.removeAll(playerlist);
		}
	}

}
