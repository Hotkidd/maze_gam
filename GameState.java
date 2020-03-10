import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;

public class GameState implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = -1823956769791987198L;
	public static final String TREASURE_VALUE = "*";
	public static final String EMPTY_VALUE = "";
	public HashMap<PlayerInfo, Integer> playerScoreMap;
	public String[][] mazeBoard;
	private int N, K;
	
	public GameState(int N, int K){
		this.N=N;
		this.K=K;
		playerScoreMap = new HashMap<PlayerInfo, Integer>();
		mazeBoard= new String[N][N];
		for (String[] row : mazeBoard) {
			Arrays.fill(row, EMPTY_VALUE);
		}
	}
	
	public void addPlayer(PlayerInfo player, Position pos) {
		playerScoreMap.put(player, 0);
		mazeBoard[pos.row][pos.col] = player.m_id;
	}
	
	public void removePlayer(PlayerInfo player) {
		System.err.println("Removing player " + player.m_id);
		playerScoreMap.remove(player);
		Position pos = getPlayerPosition(player.m_id);
		if (pos != null)
			mazeBoard[pos.row][pos.col] = EMPTY_VALUE;
	}
	
	public Position getPlayerPosition(String playerID){
		for(int i=0; i<N; i++){
			for(int j=0; j<N; j++){
				if(mazeBoard[i][j].equals(playerID)){
					return new Position(i, j);
				}				
			} 
		}
		return null;
	}
}


