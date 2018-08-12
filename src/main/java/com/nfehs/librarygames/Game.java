package com.nfehs.librarygames;

import java.awt.Container;
import java.awt.Dimension;
import java.net.UnknownHostException;

import javax.swing.JFrame;

import com.nfehs.librarygames.net.GameClient;
import com.nfehs.librarygames.net.Security;
import com.nfehs.librarygames.net.packets.*;
import com.nfehs.librarygames.screens.*;

/**
 * This class hosts the game flow
 * @author Patrick Kenney, Syed Quadri
 * @date 6/13/2018
 */

public class Game {
	public static GameFrame gameFrame;
	public static JFrame window;
	public static Container mainWindow;
	public static Dimension screenSize;
	
	private static Screen screen;
	
	private static GameClient client;
	private static Player player;
	
	public static final byte[] SERVER_IP_ADDRESS = {108, (byte) 205, (byte) 143, 97};
	
	public static final int LOGIN = 0;
	public static final int CREATE_ACCOUNT = 1;
	public static final int ACTIVE_GAMES = 2;
	public static final int CREATE_GAME = 3;
	public static final int OVER = 10;
	
	public static int gameState = Game.LOGIN;
	public static boolean gamePlaying = true;

	public Game() throws UnknownHostException {
		client = new GameClient(this, SERVER_IP_ADDRESS);
		client.start();
	}
	
	/**
	 * This method logs in the user online
	 * @param username
	 * @param password
	 */
	public static void login(String user, char[] pass) {
		// verify that valid data is given
		if (user == null || user.length() < 1) {
			// TODO error user empty
			return;
		} else if (pass == null || pass.length < 1) {
			// TODO error pass empty
			return;
		} else if (user.length() > 20) {
			// TODO error user too long
			return;
		} else if (pass.length > 20) {
			// TODO error pass too long
			return;
		}
		
		// encrypt username and password
		String username = Security.encrypt(user);
		String password = "";
		for (char c : pass)
			password += (c + 15);
		password = Security.encrypt(password);
		
		// send login info to server
		new Packet00Login(username, password).writeData(client);
	}

	/**
	 * This method attempts to create a new account
	 * @param username
	 * @param email
	 * @param password
	 * @param password2
	 */
	public static void createAccount(String user, String email, char[] pass, char[] pass2) {
		// verify that data given is valid
		if (user == null || user.length() < 1) {
			// TODO error user empty
			return;
		} else if (pass == null || pass.length < 1) {
			// TODO error pass empty
			return;
		} else if (user.length() > 20) {
			// TODO error user too long
			return;
		} else if (pass.length > 20) {
			// TODO error pass too long
			return;
		} else if (pass.length != pass2.length) {
			// TODO error passwords do not match
			return;
		} else if (email.contains(":")) {
			// TODO error invalid email
			return;
		}
		
		// encrypt username and password
		String username = Security.encrypt(user);
		String password = "";
		for (int i = 0; i < pass.length; i++) {
			if (pass[i] != pass2[i]) {
				// TODO error passwords do not match
				return;
			}
			password += (pass[i] + 15);
		}
		password = Security.encrypt(password);
		
		// send create account data to server
		new Packet01CreateAcc(email, username, password).writeData(client);
	}
	
	/**
	 * This method attempts to log the user out of the server
	 */
	public static void logout() {
		// send logout package to client
		new Packet03Logout().writeData(client);
	}

	/**
	 * This method closes the screen for the user
	 */
	public static void exitCurrentScreen() {
		if (screen != null)
			screen.exit();
	}

	/**
	 * This method opens up the login screen for the user
	 */
	public static void openLoginScreen() {
		exitCurrentScreen();
		screen = new LoginScreen();
		gameState = LOGIN;
	}

	/**
	 * This method opens up the create account screen for the user
	 */
	public static void openCreateAccountScreen() {
		exitCurrentScreen();
		screen = new CreateAccountScreen();
		gameState = CREATE_ACCOUNT;
	}

	/**
	 * This method opens up the active games screen for the user
	 */
	public static void openActiveGamesScreen() {
		exitCurrentScreen();
		screen = new ActiveGamesScreen();
		gameState = ACTIVE_GAMES;
	}

	/**
	 * This method opens up the create game screen for the user
	 */
	public static void openCreateGameScreen() {
		exitCurrentScreen();
		screen = new CreateGameScreen();
		gameState = CREATE_GAME;
	}
	
	/**
	 * This method updates the active games list on the ActiveGamesScreen
	 */
	public static void updateActiveGamesList() {
		if (!(screen instanceof ActiveGamesScreen)) {
			System.out.println("GAMESTATE ERROR update active games list called on wrong screen");
			return;
		}
		((ActiveGamesScreen) screen).loadActiveGames();
	}

	/**
	 * @return the player
	 */
	public static Player getPlayer() {
		return player;
	}

	/**
	 * @param player the player to set
	 */
	public static void setPlayer(Player player) {
		Game.player = player;
	}
}
