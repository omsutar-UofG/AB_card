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
		
		// SC05: Turn Initialization - Turn 1 starts with 3 cards drawn
		// SC05: Turn Initialization - Mana scales correctly (Turn 1 + 1 = 2 mana)
		
		// NOTIFICATION: Setting player 1 health and mana
		BasicCommands.addPlayer1Notification(out, "Setting player 1 health and mana", 2);
		try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

		// Loading player 1 health=20
		gameState.humanPlayer = new Player(maxHealth, 0);
		// Initialize Deck for Human Player
		gameState.humanPlayer.setDeck(utils.OrderedCardLoader.getPlayer1Cards(1));
		BasicCommands.setPlayer1Health(out, gameState.humanPlayer);
		System.out.println("Added health to player 1");
		try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

		// SC05: Set Player 1 Mana to 2 (Turn 1 + 1)
		gameState.humanPlayer.setMana(2);
		BasicCommands.setPlayer1Mana(out, gameState.humanPlayer);
		
		// SC05: Draw 3 cards at the start of the game
		for (int i = 0; i < 3; i++) {
			drawCard(out, gameState, gameState.humanPlayer);
			try {Thread.sleep(500);} catch (InterruptedException e) {e.printStackTrace();}
		}

		// Loading player 2 health=20
		// NOTIFICATION: setPlayer2Health
		BasicCommands.addPlayer1Notification(out, "setPlayer2Health", 2);
		gameState.aiPlayer = new Player(maxHealth, 0);
		// Initialize Deck for AI Player
		gameState.aiPlayer.setDeck(utils.OrderedCardLoader.getPlayer2Cards(1));
		BasicCommands.setPlayer2Health(out, gameState.aiPlayer);
		try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}

		// Set AI Mana to 2 (Mirroring Human for fairness/demo, though SC05 implies active player logic)
		gameState.aiPlayer.setMana(2);
		BasicCommands.setPlayer2Mana(out, gameState.aiPlayer);
		
		// Draw 3 cards for AI (Logic mirroring Human)
		for (int i = 0; i < 3; i++) {
			// AI drawing logic - usually just backend update, maybe no UI update if hidden
			// For now, let's just add to hand without UI drawing for simplicity unless required
			if (gameState.aiPlayer.getDeck().size() > 0) {
				structures.basic.Card card = gameState.aiPlayer.getDeck().remove(0);
				gameState.aiPlayer.addCardToHand(card);
			}
		}
		
		// Initialize Game State Turn Info
		gameState.turnNumber = 1;
		gameState.activePlayer = gameState.humanPlayer;
	}

	/**
	 * Helper method to draw a card for a player.
	 * Implements SC06: Overdraw Rule.
	 */
	private void drawCard(ActorRef out, GameState gameState, Player player) {
		// Check if deck is empty
		if (player.getDeck().isEmpty()) {
			BasicCommands.addPlayer1Notification(out, "Deck is empty!", 2);
			return;
		}
		
		// Get the top card
		structures.basic.Card card = player.getDeck().get(0);
		
		// SC06: Overdraw Rule
		// Acceptance Criteria: If hand size >= 6, drawn card is removed from deck without entering hand.
		if (player.getHand().size() >= 6) {
			// Burn the card (Remove from deck but don't add to hand)
			player.removeCardFromDeck(card);
			BasicCommands.addPlayer1Notification(out, "Hand full! Card burned.", 2);
			// Optional: Visualization of burning card could go here
		} else {
			// Normal Draw
			player.removeCardFromDeck(card);
			player.addCardToHand(card);
			// Draw card on UI (only for Human player usually)
			if (player == gameState.humanPlayer) {
				BasicCommands.drawCard(out, card, player.getHand().indexOf(card) + 1, 0);
			}
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
		
		// Keep the game in non-initialized state until the full setup pipeline
		// (board, players, decks, avatars, active player) has completed.
		gameState.gameInitalised = false;
		gameState.something = true;
		
		//CommandDemo.executeDemo(out); // this executes the command demo, comment out this when implementing your solution
		//Loaders_2024_Check.test(out);

		// Draw tiles
		drawTiles(out, gameState, message);
		
		// Setting players stats
		setPlayersStats(out, gameState, message);
		
		// Draw avatar
		drawAvatarUnits(out, gameState, message, gameState.board[1][2], gameState.board[6][2]);

		// Initialization is complete; turn-related events are now safe to process.
		gameState.gameInitalised = true;

		// testing changes
	}

}


