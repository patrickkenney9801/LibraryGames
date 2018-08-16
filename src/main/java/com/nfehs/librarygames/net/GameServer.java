package com.nfehs.librarygames.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.UUID;

import com.nfehs.librarygames.Player;
import com.nfehs.librarygames.net.packets.*;
import com.nfehs.librarygames.net.packets.Packet.PacketTypes;

/**
 * This class handles receiving packets from and sending to the client
 * @author Patrick Kenney, Syed Quadri
 * @date 6/13/2018
 */

public class GameServer extends Thread {

	private DatagramSocket socket;
	private Connection database;
	private static final int PORT = 19602;
	private final String DATABASE_USER = "root";
	private final String DATABASE_PASS = "98011089";
	
	private ArrayList<Player> onlinePlayers;
	
	public GameServer() {
		try {
			this.database = getConnection();
			System.out.println("Connected to database");
			this.socket = new DatagramSocket(PORT);
			System.out.println("Connected to UDP");
			this.onlinePlayers = new ArrayList<Player>();
		} catch (SocketException e) {
			e.printStackTrace();
	} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Returns a database connection to library games server
	 * @return
	 * @throws SQLException
	 */
	private Connection getConnection() throws SQLException {
		try {
			String driver = "com.mysql.cj.jdbc.Driver";
			Class.forName(driver);
			
			return DriverManager.getConnection("jdbc:mysql://localhost:3306/library_games", DATABASE_USER, DATABASE_PASS);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Receives incoming packets from clients
	 */
	public void run() {
		while (true) {
			byte[] data = new byte[1024];
			DatagramPacket packet = new DatagramPacket(data, data.length);
			try {
				socket.receive(packet);
			} catch (IOException e) {
				e.printStackTrace();
			}
			parsePacket(packet.getData(), packet.getAddress(), packet.getPort());
			
			System.out.println("CLIENT > " + new String(packet.getData()));
		}
	}
	
	/**
	 * Sends data to server
	 * @param data
	 */
	public void sendData(byte[] data, InetAddress ipAddress, int port) {
		DatagramPacket packet = new DatagramPacket(data, data.length, ipAddress, port);
		try {
			socket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Handles packets sent to the server
	 * @param data
	 * @param address
	 * @param port
	 */
	private void parsePacket(byte[] data, InetAddress address, int port) {
		String message = new String(data).trim();
		PacketTypes type = Packet.lookupPacket(message.substring(0, 2));
		
		switch (type) {
			case INVALID:				break;
			case LOGIN:					loginUser(data, address, port);
										break;
			case CREATEACCOUNT:			createAccount(data, address, port);
										break;
			case ERROR:					// TODO, probably will not be used by server
										break;
			case LOGOUT:				logout(data, address, port);
										break;
			case GETPLAYERS:			getPlayers(data, address, port);
										break;
			case ADDFRIEND:				addFriend(data, address, port);
										break;
			case CREATEGAME:			createGame(data, address, port);
										break;
			case GETGAMES:				getGames(data, address, port);
										break;
			case GETBOARD:				getBoard(data, address, port);
										break;
			default:					break;
		}
	}

	/**
	 * Logs the user into the database and returns the user's UUID or error message
	 * @param data
	 * @param address
	 * @param port
	 */
	private void loginUser(byte[] data, InetAddress address, int port) {
		try {
			Packet00Login packet = new Packet00Login(data);
			
			// search for account with the given username and password
			PreparedStatement statement = database.prepareStatement("SELECT * FROM users WHERE username = '"
																	+ packet.getUsername() + "' AND password = '"
																	+ packet.getPassword() + "';");
			ResultSet result = statement.executeQuery();
			
			// if there is not a result, send error incorrect credentials
			if (!result.next()) {
				// TODO error wrong account credentials
				System.out.println("Incorrect credentials with username: " + Security.decrypt(packet.getUsername()));
				return;
			}

			// add player to online players list
			onlinePlayers.add(new Player(packet.getUsername(), result.getString("user_key"), address, port));
			
			// send login packet back to client (include username and key)
			Packet00Login returnPacket = new Packet00Login(	packet.getUuidKey(), packet.getUsername(), 
																	result.getString("user_key"), true);
			returnPacket.writeData(this, address, port);
			System.out.println("RETURN LOGIN PACKET SENT");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Creates a new account for the user on the database and returns UUID or error message
	 * @param data
	 * @param address
	 * @param port
	 */
	private void createAccount(byte[] data, InetAddress address, int port) {
		try {
			Packet01CreateAcc packet = new Packet01CreateAcc(data);
			
			// verify that the username is not already in use
			PreparedStatement statement = database.prepareStatement("SELECT * FROM users WHERE username = '" + packet.getUsername() + "';");
			ResultSet result = statement.executeQuery();
			
			// if the username is already in use, do not create new account and send back error package
			if (result.next()) {
				// TODO error username already in use
				System.out.println("user: " + result.getString("username") + " already in use");
				return;
			}
			
			// if it is not, create the account
			String userKey = "" + UUID.randomUUID();
			PreparedStatement createUser = database.prepareStatement("INSERT INTO users VALUES ("
										+ "'" + packet.getUsername() + "', " + "'" +  packet.getPassword() 	+ "', " 
										+ "'" + packet.getEmail()	 + "', " + "'" +  userKey			 	+ "');");
			createUser.executeUpdate();
			
			System.out.println("ACCOUNT CREATED");
			
			// send login packet back to client (include username and key)
			Packet01CreateAcc returnPacket = new Packet01CreateAcc(packet.getUuidKey(), packet.getUsername(), userKey, true);
			returnPacket.writeData(this, address, port);
			System.out.println("RETURN CREATE ACCOUNT PACKET SENT");
			
			// send 04 getPlayers packets to all logged users in case they are on CreateGameScreen
			Packet04GetPlayers temp;
			for (Player p : onlinePlayers) {
				temp = new Packet04GetPlayers(p.getUser_key());
				temp.setUuidKey(packet.getUuidKey());
				getPlayers(temp.getData(), p.getIpAddress(), p.getPort());
			}
			
			// add player to online players list
			onlinePlayers.add(new Player(packet.getUsername(), userKey, address, port));
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Logs the user out of the server
	 * @param data
	 * @param address
	 * @param port
	 */
	private void logout(byte[] data, InetAddress address, int port) {
		for (int i = 0; i < onlinePlayers.size(); i++)
			if (onlinePlayers.get(i).getIpAddress().equals(address))
				onlinePlayers.remove(i);
	}

	/**
	 * Retrieves list of friends and other players
	 * @param data
	 * @param address
	 * @param port
	 */
	private void getPlayers(byte[] data, InetAddress address, int port) {
		try {
			Packet04GetPlayers packet = new Packet04GetPlayers(data);
			
			// find friends list
			PreparedStatement statement = database.prepareStatement("SELECT * FROM users RIGHT JOIN friends ON users.user_key = friends.userkey2"
																+ " WHERE userkey1 = '" + packet.getUserKey() + "';");
			ResultSet result = statement.executeQuery();
			
			// get friends usernames and put into array
			ArrayList<String> friendsList = new ArrayList<String>();
			while (result.next())
				friendsList.add(Security.decrypt(result.getString("username")));
			
			// find all other users
			statement = database.prepareStatement("SELECT * FROM users WHERE user_key != '" + packet.getUserKey() + "';");
			result = statement.executeQuery();
			
			// get other usernames and put into array
			ArrayList<String> othersList = new ArrayList<String>();
			while (result.next())
				if (!friendsList.contains(Security.decrypt(result.getString("username"))))
					othersList.add(Security.decrypt(result.getString("username")));
			
			// build Strings for friends and other users
			String friends = "";
			for (int i = 0; i < friendsList.size(); i++) {
				friends += friendsList.get(i);
				if (i != friendsList.size()-1)
					friends += ",";
			}
			String others = "";
			for (int i = 0; i < othersList.size(); i++) {
				others += othersList.get(i);
				if (i != othersList.size()-1)
					others += ",";
			}
			System.out.println("Friends List: " + friends);
			System.out.println("Others  List: " + others);
			
			// send users to client
			Packet04GetPlayers returnPacket = new Packet04GetPlayers(packet.getUuidKey(), friends, others, true);
			returnPacket.writeData(this, address, port);
			System.out.println("RETURN GET PLAYERS SENT");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Attempts to add a user as a friend to the client
	 * Refreshes the CreateAccountScreen by returning a 04 packet
	 * @param data
	 * @param address
	 * @param port
	 */
	private void addFriend(byte[] data, InetAddress address, int port) {
		try {
			Packet05AddFriend packet = new Packet05AddFriend(data);
			
			// get other user's key
			PreparedStatement getKey = database.prepareStatement("SELECT * FROM users WHERE username = '"
																+ Security.encrypt(packet.getFriendName()) + "';");
			ResultSet result = getKey.executeQuery();

			// verify that other user exists
			if (!result.next()) {
				// TODO error other user does not exist
				return;
			}
			String otherKey = result.getString("user_key");
			
			// verify that player is not already friends with other
			PreparedStatement statement = database.prepareStatement("SELECT * FROM friends WHERE userkey1 = '" + packet.getUserKey()
																	+ "' AND userkey2 = '" + otherKey + "';");
			result = statement.executeQuery();
			
			// if friend set already exists, send error and exit
			if (result.next()) {
				// TODO handles duplicate
				System.out.println("DUPLICATE FRIEND REQUEST ERROR");
				return;
			}

			// add friend set to database
			PreparedStatement add = database.prepareStatement("INSERT INTO friends VALUES ('" + packet.getUserKey() 
															+ "', '" + otherKey + "' , null);");
			add.executeUpdate();
			
			System.out.println("FRIEND CONNECTION CREATED");
			// refresh players by sending a 04 packet
			Packet04GetPlayers temp = new Packet04GetPlayers(packet.getUserKey());
			temp.setUuidKey(packet.getUuidKey());
			getPlayers(temp.getData(), address, port);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Attempts to create a new game
	 * If successful returns a 06 packet with new game key
	 * Also sends notification to opponent if online via 07 packet
	 * @param data
	 * @param address
	 * @param port
	 */
	private void createGame(byte[] data, InetAddress address, int port) {
		try {
			Packet06CreateGame packet = new Packet06CreateGame(data);
			
			/*
			 *  build game board from game type
			 */
			String board = "";
			switch (packet.getGameType()) {
				case 0:							// handle game type Go 9x9
												for (int i = 0; i < 9*9; i++)
													board += "0";
												break;
				case 1:							// handle game type Go 13x13
												for (int i = 0; i < 13*13; i++)
													board += "0";
												break;
				case 2:							// handle game type Go 19x19
												for (int i = 0; i < 19*19; i++)
													board += "0";
												break;
				default:						// error no valid game type, warn and exit
												System.out.println("ERROR gameType: " + packet.getGameType());
												return;
			}
			
			/*
			 *  get other user's key
			 */
			PreparedStatement getKey = database.prepareStatement("SELECT * FROM users WHERE username = '"
																+ Security.encrypt(packet.getOtherUser()) + "';");
			ResultSet result = getKey.executeQuery();

			// verify that other user exists
			if (!result.next()) {
				// TODO error other user does not exist
				return;
			}
			String opponentKey = result.getString("user_key");
			
			// set player1Key and player2Key based on who goes first
			String player1Key = packet.getUserKey();
			String player2Key = opponentKey;
			
			if (!packet.getCreatorGoesFirst()) {
				String temp = player1Key;
				player1Key = player2Key;
				player2Key = temp;
			}
			
			// create the new game
			String gameKey = "" + UUID.randomUUID();
			PreparedStatement createGame = database.prepareStatement("INSERT INTO games VALUES ('" 
										+ gameKey + "', '" +  player1Key + "', '" + player2Key 
										+ "', " + packet.getGameType() + ", true, 0, '"
										+ board + "');");
			createGame.executeUpdate();
			
			System.out.println("GAME CREATED");
			
			// Send new game data to creator via 8 packet so opens game screen
			Packet08GetBoard temp = new Packet08GetBoard(packet.getUserKey(), packet.getUsername(), gameKey);
			temp.setUuidKey(packet.getUuidKey());		// preserve packet id
			getBoard(temp.getData(), address, port);
			
			// attempt to send data to opponent via 8 packet so sends notification TODO
			for (Player p : onlinePlayers)
				if (p.getUser_key().equals(opponentKey))
					getBoard(temp.getData(), p.getIpAddress(), p.getPort());
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Retrieves list of game data
	 * @param data
	 * @param address
	 * @param port
	 */
	private void getGames(byte[] data, InetAddress address, int port) {
		try {
			Packet07GetGames packet = new Packet07GetGames(data);
			
			// find games list
			PreparedStatement statement = database.prepareStatement("SELECT * FROM users RIGHT JOIN games ON users.user_key = games.player2_key"
																+ " WHERE games.player1_key = '" + packet.getUserKey() + "';");
			ResultSet result = statement.executeQuery();
			
			// get game info and put into ArrayList
			ArrayList<String> gameInfo = new ArrayList<String>();
			while (result.next()) {
				String info = "";
				info += result.getString("game_key") + ",";
				info += result.getString("game_type") + ",";
				info += result.getString("username") + ",";
				info += Security.encrypt(packet.getUsername()) + ",";
				info += result.getBoolean("player1_turn");
				gameInfo.add(info);
			}
			
			statement = database.prepareStatement("SELECT * FROM users RIGHT JOIN games ON users.user_key = games.player1_key"
												+ " WHERE games.player2_key = '" + packet.getUserKey() + "';");
			result = statement.executeQuery();

			// get game info and put into ArrayList
			while (result.next()) {
				String info = "";
				info += result.getString("game_key") + ",";
				info += result.getString("game_type") + ",";
				info += Security.encrypt(packet.getUsername()) + ",";
				info += result.getString("username") + ",";
				info += result.getBoolean("player1_turn");
				gameInfo.add(info);
			}
			
			// convert ArrayList into array
			String[] gameInformation = new String[gameInfo.size()];
			for (int i = 0; i < gameInformation.length; i++)
				gameInformation[i] = gameInfo.get(i);
			
			// send games to client
			Packet07GetGames returnPacket = new Packet07GetGames(packet.getUuidKey(), packet.getUserKey(), gameInformation, true);
			returnPacket.writeData(this, address, port);
			
			System.out.println("ACTIVE GAMES SENT");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Retrieves a specific game
	 * @param data
	 * @param address
	 * @param port
	 */
	private void getBoard(byte[] data, InetAddress address, int port) {
		try {
			Packet08GetBoard packet = new Packet08GetBoard(data);
			
			// find game
			PreparedStatement statement = database.prepareStatement("SELECT * FROM games WHERE game_key = '" + packet.getGameKey() + "';");
			ResultSet result = statement.executeQuery();
			
			/*
			 *  get game info
			 */
			// if there are no results, exit print error and send error wrong game key
			if (!result.next()) {
				// TODO send error to user
				System.out.println("ERROR IMPROPER GAME KEY SENT");
				return;
			}
			int gameType;
			int lastMove;
			
			gameType = result.getInt("game_type");
			boolean player1Turn = result.getBoolean("player1_turn");
			lastMove = result.getInt("last_move");
			String board = result.getString("board");
			
			// get player usernames in proper order
			String player1 = null;
			String player2 = null;
			
			if (packet.getUserKey().equals(result.getString("player1_key"))) {
				player1 = Security.encrypt(packet.getUsername());
				
				// get opponent's username
				PreparedStatement findUsername = database.prepareStatement("SELECT * FROM users WHERE user_key = '" 
																			+ result.getString("player2_key") + "';");
				ResultSet opponentUser = findUsername.executeQuery();
				opponentUser.next();
				player2 = opponentUser.getString("username");
			} else  {
				player2 = Security.encrypt(packet.getUsername());
				
				// get opponent's username
				PreparedStatement findUsername = database.prepareStatement("SELECT * FROM users WHERE user_key = '" 
																			+ result.getString("player1_key") + "';");
				ResultSet opponentUser = findUsername.executeQuery();
				opponentUser.next();
				player1 = opponentUser.getString("username");
			}
			
			// send requested game to client
			Packet08GetBoard returnPacket = new Packet08GetBoard(packet.getUuidKey(), packet.getUserKey(), packet.getGameKey(), gameType, 
																 player1, player2, player1Turn, lastMove, board, true);
			returnPacket.writeData(this, address, port);
			
			System.out.println("GAME SENT");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
