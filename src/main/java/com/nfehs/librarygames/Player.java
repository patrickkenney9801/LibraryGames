package com.nfehs.librarygames;

import java.net.InetAddress;
import java.util.ArrayList;

import com.nfehs.librarygames.games.BoardGame;

/**
 * This class holds data for players
 * Used by both client player and by server to hold data
 * @author Patrick Kenney and Syed Quadri
 * @date 8/10/2018
 */

public class Player {
	private String username;
	private String user_key;
	private InetAddress ipAddress;
	private int port;
	public ArrayList<BoardGame> boardGames;
	private String[] friends;
	private String[] otherPlayers;
	
	/**
	 * For use by client
	 * @param user
	 * @param key
	 * @param games
	 * TODO
	 */
	public Player(String user, String key) {
		setUsername(user);
		setUser_key(key);
		boardGames = new ArrayList<BoardGame>();
	}
	
	/**
	 * For use by server
	 * @param user
	 * @param key
	 * @param ip
	 * @param p
	 */
	public Player(String user, String key, InetAddress ip, int p) {
		setUsername(user);
		setUser_key(key);
		setIpAddress(ip);
		setPort(p);
	}
	
	public void updateGamesList(String games) {
		// TODO add code to add games to list
		
		if (Game.gameState == Game.ACTIVE_GAMES)
			Game.updateActiveGamesList();
	}
	
	/**
	 * @return the username
	 */
	public String getUsername() {
		return username;
	}
	/**
	 * @param username the username to set
	 */
	public void setUsername(String username) {
		this.username = username;
	}
	/**
	 * @return the user_key
	 */
	public String getUser_key() {
		return user_key;
	}
	/**
	 * @param user_key the user_key to set
	 */
	public void setUser_key(String user_key) {
		this.user_key = user_key;
	}
	/**
	 * @return the ipAddress
	 */
	public InetAddress getIpAddress() {
		return ipAddress;
	}
	/**
	 * @param ipAddress the ipAddress to set
	 */
	public void setIpAddress(InetAddress ipAddress) {
		this.ipAddress = ipAddress;
	}
	/**
	 * @return the port
	 */
	public int getPort() {
		return port;
	}
	/**
	 * @param port the port to set
	 */
	public void setPort(int port) {
		this.port = port;
	}

	public String[] getFriends() {
		return friends;
	}

	public void setFriends(String friends) {
		if (friends == null || friends.length() < 1) {
			this.friends = new String[0];
			return;
		}
		this.friends = friends.split(",");
	}

	public String[] getOtherPlayers() {
		return otherPlayers;
	}

	public void setOtherPlayers(String otherPlayers) {
		this.otherPlayers = otherPlayers.split(",");
	}
}
