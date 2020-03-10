import java.io.Serializable;

public class PlayerInfo implements Serializable, Comparable{
	/**
	 * 
	 */
	private static final long serialVersionUID = -743137576920037941L;
	public String m_ip;
	public int m_port;
	public String m_id;
	
	public PlayerInfo(String ip, int port, String id)
	{
		m_ip = ip;
		m_port = port;
		m_id = id;
	}
	
	@Override
	public boolean equals(Object obj){
		if (obj == null)
			return false;
		
		PlayerInfo playerObj = (PlayerInfo) obj;
		return m_id.equals(playerObj.m_id);
	}
	
	@Override
	public int hashCode() {
		return m_id.hashCode();
	}

	@Override
	public int compareTo(Object arg0) {
		PlayerInfo playerObj = (PlayerInfo) arg0;
		return m_id.compareTo(playerObj.m_id);
	}
}
