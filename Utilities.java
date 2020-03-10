import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;

public class Utilities {
	public static boolean startRMIRegistry() {
		try {
		    java.rmi.registry.LocateRegistry.createRegistry(java.rmi.registry.Registry.REGISTRY_PORT);
		    System.out.println("RMI registry ready.");
		    return true;
		} catch (ExportException ee) {
			System.out.println("RMI registry is already running.");
			return true;
		} catch (Exception e) {
		    System.err.println("Exception starting RMI registry:");
		    e.printStackTrace();
		    return false;
		}
	}
	
	public static boolean checkLiveness(PlayerInfo player) {
		if (player == null)
			return false;
		
		try {
			Registry registry = LocateRegistry.getRegistry(player.m_ip);
			ClientInterface remote = (ClientInterface)registry.lookup(player.m_id);
			remote.checkLiveness();
			return true;
		} catch (Exception e) {
			return false;
		}
		
	}
}
