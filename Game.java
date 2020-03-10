import java.awt.EventQueue;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSplitPane;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.net.InetAddress;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.JPanel;
import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableModel;

public class Game implements ClientInterface {
	private final static HashSet<Integer> VALID_DIRECTIONS = new HashSet<Integer>(Arrays.asList(0, 1, 2, 3, 4, 9));
	private final static int RECHECK_SLEEP_TIME = 200;
	private JFrame m_frame;
	private DefaultTableModel m_scoreTable;
	JLabel[][] m_mazeLabels; // holds the text label in maze grid, update the text here will update the UI automatically
	ServerInterface m_serverStub = null;
	GameState m_gameState = null;
	int m_N = 0;
	int m_K = 0;
	public static String m_trackerIpAddress = "";
	public static int m_trackerPortNumber = 0;
	public static String m_playerId = "";
	
	public PlayerInfo m_thisPlayer = null;
	public PlayerInfo m_primaryServer = null;
	public PlayerInfo m_backupServer = null;
	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		if (args.length != 3) {
			System.err.println("Expecting exactly 3 arguments: IP Port PlayerId");
			return;
		}

		try {
			m_trackerPortNumber = Integer.parseInt(args[1]);
		} catch (NumberFormatException e) {
			System.err.println("Expecting the second argument to be an integer!");
			return;
		}
		
		m_trackerIpAddress = args[0];
		m_playerId = args[2];
		
		String rmiTimeout = "500";
		System.getProperties().setProperty("sun.rmi.transport.proxy.connectTimeout", rmiTimeout); 
		System.getProperties().setProperty("sun.rmi.transport.tcp.responseTimeout", rmiTimeout); 
		
		if (!Utilities.startRMIRegistry()) {
			System.err.println("Failed to start RMI registry!");
			return;
		}
		
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					new Game();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public Game() {
		initialize();
		refreshUI();
		startPeerChecker();
		new Thread(new ScanInputRunnable()).start();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		// setup UI
		m_frame = new JFrame();
		m_frame.setTitle(m_playerId);
		m_frame.setBounds(100, 100, 800, 600);
		m_frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);	
		
		// data initialization
     	if (!initializeData()) {
     		System.err.println("Failed to initialize data!");
   			System.exit(1);
   		}
		
		// left panel, current score of all the players
		JPanel scorePanel = new JPanel();
		Vector<String> columnNames = new Vector<String>();
		columnNames.addElement("Player");
		columnNames.addElement("Score");
		m_scoreTable = new DefaultTableModel(new Vector<Object>(), columnNames);
		JTable table = new JTable(m_scoreTable);
		table.setEnabled(false);
		scorePanel.setLayout(new BorderLayout());
		scorePanel.add(table.getTableHeader(), BorderLayout.PAGE_START);
		scorePanel.add(table, BorderLayout.CENTER);
		// right panel, the maze grid showing players and treasure location
		JPanel mazePanel = new JPanel();
		mazePanel.setLayout(new GridLayout(m_N, m_N, 0, 0));
		Border blackline = BorderFactory.createLineBorder(Color.black);
		m_mazeLabels = new JLabel[m_N][m_N];
        for (int i = 0; i < m_N; i++)
        {
            for (int j = 0; j < m_N; j++)
            {
            	m_mazeLabels[i][j] = new JLabel("", SwingConstants.CENTER);
                m_mazeLabels[i][j].setBorder(blackline);
                mazePanel.add(m_mazeLabels[i][j]);
            }
        }
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scorePanel, mazePanel);
        m_frame.getContentPane().add(splitPane);
        m_frame.setVisible(true);
	}
	
	private synchronized void handlePrimaryServerLostConnection(Collection<PlayerInfo> playerList) {
		
		if (Utilities.checkLiveness(m_primaryServer)) {
			try {
				System.out.println("Server busy! Retrying...");
				Thread.sleep(RECHECK_SLEEP_TIME);
			} catch (InterruptedException e) {
				System.err.println("Sleep interrupted?...");
			}
			
			return;
		}
		
		while (Utilities.checkLiveness(m_backupServer)) {
			try {
				Registry serverRegistry = LocateRegistry.getRegistry(m_backupServer.m_ip);
				ServerInterface serverStub = (ServerInterface) serverRegistry.lookup(Server.BACKUP_SERVER_RMI_NAME);
				m_primaryServer = serverStub.getPrimaryServer();
				// wait for server to be restored
				while (Server.RESTORING_SERVER.equals(m_primaryServer)) {
					Thread.sleep(RECHECK_SLEEP_TIME);
					m_primaryServer = serverStub.getPrimaryServer();
				}
				
				return;
			}  catch (NotBoundException nbe) {
				break;
			} catch (Exception e) {
				try {
					System.out.println("Backup server busy! Retrying...");
					Thread.sleep(RECHECK_SLEEP_TIME);
				} catch (InterruptedException ee) {
					System.err.println("Sleep interrupted?...");
				}
			}
		}
		

		if (!updateServersFromPeer(playerList)) {
			System.err.println("Cannot contact server! Quiting...");
		    System.exit(1);
		}
	}
	
	//players move function
	public void scanInput() {
        Scanner input = new Scanner(System.in);
		
		try {
			int direction = 0;
			while (direction != 9) {
				direction = input.nextInt();
				if (VALID_DIRECTIONS.contains(direction)) {
					boolean handled = false;
					while (!handled) {
						try {
							// ask primary server to handle move
							Registry serverRegistry = LocateRegistry.getRegistry(m_primaryServer.m_ip);
							ServerInterface serverStub = (ServerInterface) serverRegistry.lookup(Server.PRIMARY_SERVER_RMI_NAME);
							m_gameState = serverStub.move(m_thisPlayer, direction);
							
							if (m_gameState == null) {
								System.err.println("Cannot find player " + m_thisPlayer.m_id + " in server! Quiting...");
								System.exit(1);
							}
							
							refreshUI();
							handled = true;
						}  catch (Exception e) {
							
							if (m_gameState == null) {
								System.err.println("Cannot handle server lost! Game State Null! Quiting...");
							    System.exit(1);
							}
							
							handlePrimaryServerLostConnection(m_gameState.playerScoreMap.keySet());
						}
					}
				}
			}
			
			System.exit(0);
			
		} catch (Exception e) {
			System.err.println("Move exception: " + e.toString());
		    e.printStackTrace();
		    System.exit(1);
		}
	}

	@Override
	public boolean promoteToServer(boolean isPrimary, GameState gameState, PlayerInfo theOtherServer) throws RemoteException {
		
		String serverBindName = isPrimary ? Server.PRIMARY_SERVER_RMI_NAME : Server.BACKUP_SERVER_RMI_NAME;
		Registry serverRegistry = null;
		try {
			if (m_serverStub == null)
				m_serverStub = (ServerInterface) UnicastRemoteObject.exportObject(new Server(m_N, m_K, isPrimary, m_thisPlayer, theOtherServer, gameState), 0);
			
			serverRegistry = LocateRegistry.getRegistry();
			serverRegistry.bind(serverBindName, m_serverStub);
		} catch (Exception e) {
		    try {
		    	serverRegistry.unbind(serverBindName);
		    	serverRegistry.bind(serverBindName, m_serverStub);
			}catch(Exception ee){
				System.err.println("Server binding exception: " + ee.toString());
			 	return false;
			}
		}
		
		if (isPrimary)
			m_primaryServer = m_thisPlayer;
		else
			m_backupServer = m_thisPlayer;
		
		m_frame.setTitle(m_playerId + " - " + (isPrimary ? "Primary Server" : "Backup Server"));
		System.out.println("Client promoted to " + serverBindName);
		return true;
	}

	@Override
	public PlayerInfo getPrimaryServer() throws RemoteException {
		return m_primaryServer;
	}

	@Override
	public PlayerInfo getBackupServer() throws RemoteException {
		return m_backupServer;
	}
	
	private void refreshUI() {
		Vector playerScore = m_scoreTable.getDataVector();
		playerScore.clear();
		for (Map.Entry<PlayerInfo, Integer> entry : m_gameState.playerScoreMap.entrySet()) {
			Vector<Object> row = new Vector<Object>();
			row.addElement(entry.getKey().m_id);
			row.addElement(entry.getValue());
			playerScore.addElement(row);
		}
		m_scoreTable.fireTableDataChanged();
		
		for (int i = 0; i < m_N; i++)
        {
            for (int j = 0; j < m_N; j++)
            {
            	m_mazeLabels[i][j].setText(m_gameState.mazeBoard[i][j]);
            }
        }
	}
	
	private boolean initializeData() {
		try {
			m_thisPlayer = new PlayerInfo(InetAddress.getLocalHost().getHostAddress(), Registry.REGISTRY_PORT, m_playerId);
			// register this player to its own registry
			ClientInterface thisClientStub = null;
			Registry thisClientRegistry = null;
			boolean isThisClientBinded = false;
			try {
				thisClientStub = (ClientInterface) UnicastRemoteObject.exportObject(this, 0);
				thisClientRegistry = LocateRegistry.getRegistry();
				thisClientRegistry.bind(m_thisPlayer.m_id, thisClientStub);
				isThisClientBinded = true;
			} catch (Exception e) {
			    try {
			    	thisClientRegistry.unbind(m_thisPlayer.m_id);
			    	thisClientRegistry.bind(m_thisPlayer.m_id, thisClientStub);
			    	isThisClientBinded = true;
				}catch(Exception ee){
					System.err.println("Client binding exception: " + ee.toString());
				 	ee.printStackTrace();
				}
			}			
			
			if (!isThisClientBinded)
				return false;
			
			Registry registry = LocateRegistry.getRegistry(m_trackerIpAddress);
			TrackerInterface trackerStub = (TrackerInterface) registry.lookup(TrackerInterface.RMI_NAME);
			m_N = trackerStub.getGridSize();
			m_K = trackerStub.getNoOfTreasures();
			ArrayList<PlayerInfo> playerList = trackerStub.getPlayerList(m_thisPlayer);
			playerList.remove(m_thisPlayer);
			System.out.print(m_thisPlayer.m_id + ": Plaeyrs in trackers are:");
			for (PlayerInfo player : playerList) {
				System.out.print(" " + player.m_id);
			}
			System.out.println();
			// initialize server if needed, retrieve server information
			if (playerList.size() == 0) {
				if (!promoteToServer(true, null, null))
					return false;
			}
			else {
				if (playerList.size() == 1) 
					m_primaryServer = playerList.get(0);
				
				if (!updateServersFromPeer(playerList))
					if (!promoteToServer(true, null, null))
						return false;
			}
			// register player to server
			do {
				try {
					Registry serverRegistry = LocateRegistry.getRegistry(m_primaryServer.m_ip);
					ServerInterface serverStub = (ServerInterface) serverRegistry.lookup(Server.PRIMARY_SERVER_RMI_NAME);
					m_gameState = serverStub.joinGame(m_thisPlayer);
					
					if (m_gameState == null) {
						return false;
					}
					else if (!m_primaryServer.equals(m_thisPlayer) && (m_backupServer == null || m_backupServer.equals(m_primaryServer))) {
						if (promoteToServer(false, m_gameState, m_primaryServer)) {
							try {
								serverStub.updateServer(m_thisPlayer);
							} catch(Exception e) {
								System.err.println("Update server exception: " + e.toString());
							    e.printStackTrace();
							}
						}
					}
				} catch (Exception e) {
					System.err.println("Join game exception: " + e.toString());
				}
				
				if (m_gameState == null) {
					Thread.sleep(RECHECK_SLEEP_TIME);
					handlePrimaryServerLostConnection(playerList);
				}
			} while (m_gameState == null);
			
			//get players that are no longer in the game
			playerList.removeAll(m_gameState.playerScoreMap.keySet());
			//remove them from tracker
			trackerStub.removePlayers(playerList);
			System.out.print(m_thisPlayer.m_id + ": Plaeyrs removed from trackers are:");
			for (PlayerInfo player : playerList) {
				System.out.print(" " + player.m_id);
			}
			System.out.println();
			
		} catch (Exception e) {
			System.err.println("Tracker exception: " + e.toString());
		    e.printStackTrace();
		    return false;
		}
		
		return true;
	}
	
	private boolean getBackupServerFromPrimaryServer() {
		while (Utilities.checkLiveness(m_primaryServer)) {
			try {
				Registry serverRegistry = LocateRegistry.getRegistry(m_primaryServer.m_ip);
				ServerInterface serverStub = (ServerInterface) serverRegistry.lookup(Server.PRIMARY_SERVER_RMI_NAME);
				m_backupServer = serverStub.getBackupServer();

				while (Server.RESTORING_SERVER.equals(m_backupServer)) {
					Thread.sleep(RECHECK_SLEEP_TIME);
					m_backupServer = serverStub.getBackupServer();
				}
				
				return true;
			} catch (NotBoundException nbe) {
				return false;
			} catch (Exception e) {
				System.out.println("Server busy! Retrying...");
				try {
					Thread.sleep(RECHECK_SLEEP_TIME);
				} catch (InterruptedException e1) {
					System.err.println("Sleep interrupted?...");
				}
			}
		}
		
		return false;
	}
	
	private boolean updateServersFromPeer(Collection<PlayerInfo> playerList) {
		
		if (getBackupServerFromPrimaryServer())
			return true;
		
		System.out.println("Cannot contact primary server " + (m_primaryServer == null ? "" : m_primaryServer.m_id) + "! Try to get server information from other peers!");
		m_primaryServer = null;
		
		boolean hasAlivePeer = false;
		do {
			hasAlivePeer = false;
			for (PlayerInfo peer : playerList) {
				
				if (peer.equals(m_thisPlayer))
					continue;
				
				try {
					Registry peerRegistry = LocateRegistry.getRegistry(peer.m_ip);
					ClientInterface clientStub = (ClientInterface) peerRegistry.lookup(peer.m_id);
					m_primaryServer = clientStub.getPrimaryServer();
					hasAlivePeer = true;
					// trying to get backup server from primary server. at the mean time test the health of the primary server
					if (getBackupServerFromPrimaryServer())
						return true;
					else {
						m_primaryServer = null;
						System.err.println("Failed to get server information from peer " + peer.m_id + "! Contacting next peer...");
					}
				} catch(Exception e) {
					m_primaryServer = null;
					System.err.println("Failed to get server information from peer " + peer.m_id + "! Contacting next peer...");
				}
			}
		} while (hasAlivePeer);
			
		
		return false;
	}
	
	private void handlePlayerCrash(PlayerInfo failedPlayer) {
		
		if (failedPlayer.equals(m_primaryServer) || !Utilities.checkLiveness(m_primaryServer)) {
			if (m_gameState == null) {
				System.err.println("Cannot handle server lost! Game State Null! Quiting...");
			    System.exit(1);
			}
			
			handlePrimaryServerLostConnection(m_gameState.playerScoreMap.keySet());
		}
		
		try {
			Registry serverRegistry = LocateRegistry.getRegistry(m_primaryServer.m_ip);
			ServerInterface serverStub = (ServerInterface) serverRegistry.lookup(Server.PRIMARY_SERVER_RMI_NAME);
			m_gameState = serverStub.clientFailed(failedPlayer);
		} catch (Exception e) {
			System.err.println("Update server failed client exception: " + e.toString());
		    e.printStackTrace();
		}
	}
	
	
	private void startPeerChecker() {
		final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
		System.out.println("start peer checker");
		scheduler.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				PlayerInfo[] playerList = m_gameState.playerScoreMap.keySet().toArray(new PlayerInfo[0]);
				Arrays.sort(playerList);
				int playerIndex = Arrays.binarySearch(playerList, m_thisPlayer);
				
				if (playerIndex < 0)
					return;
				
				PlayerInfo nextPlayer = playerList[(playerIndex == playerList.length - 1 ? 0 : playerIndex + 1)];

				if (!Utilities.checkLiveness(nextPlayer)) {
					handlePlayerCrash(nextPlayer);
				}
			}
			
		}, 2, 2, TimeUnit.SECONDS);
	}
	
	public class ScanInputRunnable implements Runnable {
		public void run() {
			scanInput();
		}
	}

	@Override
	public void checkLiveness() throws RemoteException {
		return;
	}
}
