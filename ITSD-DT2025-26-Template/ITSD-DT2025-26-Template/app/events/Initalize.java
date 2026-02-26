package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import demo.CommandDemo;
import demo.Loaders_2024_Check;
import structures.GameState;
import structures.basic.ImageCorrection;
import structures.basic.Player;
import structures.basic.Position;
import structures.basic.Tile;
import structures.basic.Unit;
import utils.BasicObjectBuilders;
import utils.StaticConfFiles;

/**
 * Indicates that both the core game loop in the browser is starting, meaning
 * that it is ready to recieve commands from the back-end.
 * 
 * { 
 *   messageType = “initalize”
 * }
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class Initalize implements EventProcessor{

	private static final int boardRows = 5;
	private static final int boardCols = 9;
	private static final int maxHealth = 20;

	private void drawTiles(ActorRef out, GameState gameState, JsonNode message) {
		// NOTIFICATION: Drawing tiles.
		BasicCommands.addPlayer1Notification(out, "Drawing tiles", 2);
		try {Thread.sleep(200);} catch (InterruptedException e) {e.printStackTrace();}
		
		// Drawing tiles (horizontally) (to draw tiles vertially swap for loops of x and y)
		for (int y = 0 ; y < boardRows ; ++y) {
			for (int x = 0 ; x < boardCols ; ++x) {

				// Creating Tile object
				Tile tile = BasicObjectBuilders.loadTile(x, y);

				// Saving `tile` object in `board` array grid
				gameState.board[x][y] = tile;

				// Drawing tile
				BasicCommands.drawTile(out, tile, 0);
				try {Thread.sleep(10);} catch (InterruptedException e) {e.printStackTrace();}
			}
		}
	}

	private void setPlayersStats(ActorRef out, GameState gameState, JsonNode message) {
		// NOTIFICATION: Setting player 1 health and mana
		BasicCommands.addPlayer1Notification(out, "Setting player 1 health and mana", 2);
		try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

		// Loading player 1 health=20
		gameState.humanPlayer = new Player(maxHealth, 0);
		BasicCommands.setPlayer1Health(out, gameState.humanPlayer);
		System.out.println("Added health to player 1");
		try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

		// Loading player 1 Mana
		for (int m = 1; m <= 9; m++) {
			// NOTIFICATION: setPlayer1Mana
			BasicCommands.addPlayer1Notification(out, "setPlayer1Mana ("+m+")", 1);
			gameState.humanPlayer.setMana(m);
			BasicCommands.setPlayer1Mana(out, gameState.humanPlayer);
			try {Thread.sleep(100);} catch (InterruptedException e) {e.printStackTrace();}
		}

		// Loading player 2 health=20
		// NOTIFICATION: setPlayer2Health
		BasicCommands.addPlayer1Notification(out, "setPlayer2Health", 2);
		gameState.aiPlayer = new Player(maxHealth, 0);
		BasicCommands.setPlayer2Health(out, gameState.aiPlayer);
		try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

		// Loading player 2 Mana
		for (int m = 1; m <= 9; m++) {
			// NOTIFICATION: setPlayer2Mana
			BasicCommands.addPlayer1Notification(out, "setPlayer2Mana ("+m+")", 1);
			gameState.aiPlayer.setMana(m);
			BasicCommands.setPlayer2Mana(out, gameState.aiPlayer);
			try {Thread.sleep(100);} catch (InterruptedException e) {e.printStackTrace();}
		}
	}

	private void drawAvatarUnits(ActorRef out, GameState gameState, JsonNode message, Tile humanUnitTile, Tile aiUnitTile) {
		// NOTIFICATION: Draw Human Unit
		BasicCommands.addPlayer1Notification(out, "Draw Human Unit", 2);
		Unit humanUnit = BasicObjectBuilders.loadUnit(StaticConfFiles.humanAvatar, 0, Unit.class);
		humanUnit.setPositionByTile(humanUnitTile); 
		BasicCommands.drawUnit(out, humanUnit, humanUnitTile);
		try {Thread.sleep(200);} catch (InterruptedException e) {e.printStackTrace();}

		// NOTIFICATION: Draw AI Unit
		BasicCommands.addPlayer1Notification(out, "Draw AI Unit", 2);
		Unit aiUnit = BasicObjectBuilders.loadUnit(StaticConfFiles.aiAvatar, 0, Unit.class);
		aiUnit.setPositionByTile(aiUnitTile);
		BasicCommands.drawUnit(out, aiUnit, aiUnitTile);
		try {Thread.sleep(200);} catch (InterruptedException e) {e.printStackTrace();}

	}

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {
		
		gameState.gameInitalised = true;
		gameState.something = true;
		
		//CommandDemo.executeDemo(out); // this executes the command demo, comment out this when implementing your solution
		//Loaders_2024_Check.test(out);

		// Draw tiles
		drawTiles(out, gameState, message);
		
		// Setting players stats
		setPlayersStats(out, gameState, message);
		
		// Draw avatar
		drawAvatarUnits(out, gameState, message, gameState.board[1][2], gameState.board[6][2]);

		// testing changes
	}

}


