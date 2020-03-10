import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class Server implements ServerInterface{
	public final static PlayerInfo RESTORING_SERVER = new PlayerInfo("RESTORING", 0, "RESTORING");
	private int N, K;
	private GameState gameState;
	private PlayerInfo thisServer;
	private PlayerInfo theOtherServer;
	private boolean isPrimaryServer;
	private boolean isRestoringServer = false;
	
	public Server(int N, int K, boolean isPrimaryServer, PlayerInfo thisServer, PlayerInfo theOtherServer, GameState gameState) throws RemoteException{
		this.N=N;
		this.K=K;
		this.isPrimaryServer = isPrimaryServer;
		this.thisServer = thisServer;
		this.theOtherServer = theOtherServer;
		
		if (gameState == null) // detect new game or recover
			startGame();
		else
			updateGameState(gameState);
		
		startServerChecker();
	}
	
	public void updateGameState(GameState gameState) throws RemoteException{
		this.gameState = gameState;
	}
	
	private void syncGameStateToBackupServer() {
		if (!isPrimaryServer || isRestoringServer) {
			System.err.println("Unexpected syncGameStateToBackupServer call at backup server");
			return;
		}
		
		if (theOtherServer != null && !thisServer.equals(theOtherServer)) {
			PlayerInfo usedTheOtherServer = new PlayerInfo(theOtherServer.m_ip, theOtherServer.m_port, theOtherServer.m_id);
			try {
				Registry serverRegistry = LocateRegistry.getRegistry(usedTheOtherServer.m_ip);
				ServerInterface serverStub = (ServerInterface) serverRegistry.lookup(Server.BACKUP_SERVER_RMI_NAME);
				serverStub.updateGameState(gameState);
			} catch (Exception e) {
				System.err.println("Failed to sync game state with bakcup server. Restore server...");
				try {
					gameState.removePlayer(usedTheOtherServer);
					restoreCrashedServer(false, usedTheOtherServer);
				} catch (Exception e1) {
					System.err.println("Failed to restore failed server.");
				}
			}
		}
	}
	
	@Override
	public void updateServer(PlayerInfo theOtherServer) throws RemoteException {
		this.theOtherServer = theOtherServer;
	}
	@Override
	public synchronized GameState joinGame(PlayerInfo client) throws RemoteException{
		synchronized (gameState) {
			//check if the players are more than maze size
			if(gameState.playerScoreMap.size()>=N*N){
				System.out.println("Number of players exceeds the maze size!");
				return null;
			}
			
			gameState.addPlayer(client, getRandomUnoccupiedPosition());
			syncGameStateToBackupServer();
			return gameState;
		}
	}
	@Override
	public synchronized GameState move(PlayerInfo client, int direction) throws RemoteException{
		synchronized (gameState) {
			// "9" Exit
			if (direction == 9) {
				System.out.println("MOVE: 9 pressed for player " + client.m_id);
				gameState.removePlayer(client);
				if (client.equals(thisServer))  {
					restoreCrashedServer(true, client);
				}
				else if (client.equals(theOtherServer)){
					restoreCrashedServer(false, client);
				}
				return gameState;
			}
			// "0" Refresh
			else if (direction == 0) {
				return gameState;
			}
			
			Position pos = gameState.getPlayerPosition(client.m_id);
			if (pos == null) {
				System.err.println("MOVE: could not find player " + client.m_id);
				return gameState;
			}
	
			Position newPos = new Position(pos.row, pos.col);
			// "1" move West
			if (direction == 1 && pos.col > 0 ){
				newPos.col -= 1;
			}
			// "2" move South
			else if (direction == 2 && pos.row < N-1 ){
				newPos.row += 1;
			}
			// "3" move East
			else if (direction == 3 && pos.col<N-1 ){
				newPos.col += 1;
			}
			// "4" move North
			else if (direction == 4 && pos.row > 0 ){
				newPos.row -= 1;
			}
			// other cases, do nothing
			else{
				return gameState;
			}
	
			if (gameState.mazeBoard[newPos.row][newPos.col].equals(GameState.TREASURE_VALUE)) {
				generateOneTreasure();
				gameState.playerScoreMap.put(client, gameState.playerScoreMap.get(client) + 1);
			}
			else if (!gameState.mazeBoard[newPos.row][newPos.col].equals(GameState.EMPTY_VALUE))
				return gameState;
			
			gameState.mazeBoard[pos.row][pos.col] = GameState.EMPTY_VALUE;
			gameState.mazeBoard[newPos.row][newPos.col] = client.m_id;
			
			System.out.println("Player " + client.m_id + " moved from (" + pos.row + ", " + pos.col + ") to (" + newPos.row + ", " + newPos.col + ")!");
			syncGameStateToBackupServer();
			
			return gameState;
		}
	}
	
	private void startGame() {
		this.gameState = new GameState(N, K);
		
		for (int i = 0; i < K; i++) {
			generateOneTreasure();
		}
	}
	
	private synchronized void restoreCrashedServer(boolean isThisServerCrashed, PlayerInfo crashedServer){
		
		if (gameState.playerScoreMap.keySet().isEmpty())
			return;

		PlayerInfo supposeCrashedServer = isThisServerCrashed ? thisServer : theOtherServer;
		
		if (!crashedServer.equals(supposeCrashedServer))
			return;
		
		isRestoringServer = true;
		try {
			System.out.println("Restoring " + ((isPrimaryServer && isThisServerCrashed) || (!isPrimaryServer && !isThisServerCrashed) ? Server.PRIMARY_SERVER_RMI_NAME : Server.BACKUP_SERVER_RMI_NAME));
			//find a free player who is not server
			PlayerInfo leftOverServer = isThisServerCrashed ? theOtherServer : thisServer;
			
			if (gameState.playerScoreMap.keySet().size() == 1) {
				if (isPrimaryServer) {
					this.theOtherServer = null;
					return;
				}
				
				try{
					Registry registry = LocateRegistry.getRegistry(leftOverServer.m_ip);
					ClientInterface stub = (ClientInterface) registry.lookup(leftOverServer.m_id);
					stub.promoteToServer(true, this.gameState, leftOverServer);
					this.isPrimaryServer = true;
					this.theOtherServer = leftOverServer;
				}catch(Exception e) {
					System.err.println("Restore backup server to primary server exception at player " + leftOverServer.m_id + e.toString());
				    e.printStackTrace();
				}
			}
			else {
				for(PlayerInfo player : gameState.playerScoreMap.keySet()){
					if(!player.equals(leftOverServer)){
						//promote the freePlayer to become server
						try{
							Registry registry = LocateRegistry.getRegistry(player.m_ip);
							ClientInterface stub = (ClientInterface) registry.lookup(player.m_id);
							stub.promoteToServer(isThisServerCrashed ? isPrimaryServer : !isPrimaryServer, this.gameState, leftOverServer);
							this.theOtherServer = player;
							break;
						}catch(Exception e) {
							System.err.println("Restore server exception at player " + player.m_id + ": " + e.toString());
						}
					}
				}
			}
		} catch(Exception e) {
			System.err.println("Restore server exception: " + e.toString());
		    e.printStackTrace();
		} finally {
			isRestoringServer = false;
		}
		
		System.out.println("Server restored: " + theOtherServer.m_id);
		
	}
	
	private void generateOneTreasure() {
		Position randomPosition = getRandomUnoccupiedPosition();
		gameState.mazeBoard[randomPosition.row][randomPosition.col] = GameState.TREASURE_VALUE;
	}
	
	public Position getRandomUnoccupiedPosition() {
		int x = -1;
		int y = -1;
		Random rand = new Random();
		
		while (true) {
			x = rand.nextInt(this.N);
			y = rand.nextInt(this.N);
			if (gameState.mazeBoard[x][y].equals(GameState.EMPTY_VALUE)) {
				break;
			}
		}
		
		return new Position(x, y);
	}

	@Override
	public synchronized GameState clientFailed(PlayerInfo failedClient) throws RemoteException {
		synchronized (gameState) {
			gameState.removePlayer(failedClient);
			syncGameStateToBackupServer();
			
			return gameState;
		}
	}

	@Override
	public PlayerInfo getBackupServer() throws RemoteException {
		return isPrimaryServer ? (isRestoringServer ? RESTORING_SERVER : theOtherServer) : thisServer;
	}

	@Override
	public PlayerInfo getPrimaryServer() throws RemoteException {
		return isPrimaryServer ? thisServer : (isRestoringServer ? RESTORING_SERVER : theOtherServer);
	}
	
	private void startServerChecker() {
		final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
		System.out.println("start server checker");
		scheduler.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				if (theOtherServer != null && !isRestoringServer) {
					PlayerInfo usedTheOtherServer = new PlayerInfo(theOtherServer.m_ip, theOtherServer.m_port, theOtherServer.m_id);
					if (!Utilities.checkLiveness(usedTheOtherServer) && usedTheOtherServer.equals(theOtherServer)) {
						System.out.println("Server checker: the other server " + theOtherServer.m_id +  " failed");
						synchronized (gameState) {
							gameState.removePlayer(usedTheOtherServer);
							restoreCrashedServer(false, usedTheOtherServer);
						}
					}
				}
			}
			
		}, 1, 1, TimeUnit.SECONDS);
	}
}
