package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import game.SimpleBoardLogic;
import structures.GameState;
import structures.basic.Card;
import structures.basic.Player;

/**
 * In the user’s browser, the game is running in an infinite loop, where there is around a 1 second delay 
 * between each loop. Its during each loop that the UI acts on the commands that have been sent to it. A 
 * heartbeat event is fired at the end of each loop iteration. As with all events this is received by the Game 
 * Actor, which you can use to trigger game logic.
 * 
 * { 
 *   String messageType = “heartbeat”
 * }
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class Heartbeat implements EventProcessor{

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {
		// Only run turn logic once the game has finished initialize pipeline.
		if (!isTurnSystemReady(gameState)) {
			return;
		}

		// Minimal AI flow for early story cards:
		// when it is AI turn, auto-end it on heartbeat and pass control back to human.
		if (gameState.activePlayer != gameState.aiPlayer) {
			return;
		}

		endAiTurnAndStartHumanTurn(out, gameState);
	}

	/**
	 * Ensures turn-driven events are not processed before players and active turn exist.
	 */
	private boolean isTurnSystemReady(GameState gameState) {
		return gameState != null
				&& gameState.gameInitalised
				&& gameState.humanPlayer != null
				&& gameState.aiPlayer != null
				&& gameState.activePlayer != null;
	}

	/**
	 * Implements AI auto-pass for SC08 continuity:
	 * 1) drain AI mana, 2) draw for AI at end-turn, 3) swap active player to human.
	 */
	private void endAiTurnAndStartHumanTurn(ActorRef out, GameState gameState) {
		// SC07 equivalent for AI: clear remaining mana at end of its turn.
		gameState.aiPlayer.setMana(0);
		BasicCommands.setPlayer2Mana(out, gameState.aiPlayer);

		// SC12: make sure no stale selection/highlight state leaks across turns.
		SimpleBoardLogic.clearSelectionAndHighlights(out, gameState);
		SimpleBoardLogic.clearPendingAction(gameState);

		// SC05 + 2024-GameRules alignment:
		// draw happens at END of AI turn (not at human turn start).
		drawCardForPlayer(out, gameState, gameState.aiPlayer);

		// SC08: hand control back to human and advance round counter.
		gameState.activePlayer = gameState.humanPlayer;
		gameState.turnNumber++;
		BasicCommands.addPlayer1Notification(out, "Your Turn", 2);

		// SC05: start-of-turn mana for human player (with agreed 9-mana cap).
		int newMana = gameState.turnNumber + 1;
		if (newMana > 9) {
			newMana = 9;
		}
		gameState.humanPlayer.setMana(newMana);
		BasicCommands.setPlayer1Mana(out, gameState.humanPlayer);

		// SC15: refresh action limits for the side that just became active.
		SimpleBoardLogic.resetActionFlagsForOwner(gameState, GameState.OWNER_HUMAN);
	}

	/**
	 * Draw one card for the player ending turn and apply SC06 overdraw rule.
	 */
	private void drawCardForPlayer(ActorRef out, GameState gameState, Player player) {
		if (player.getDeck().isEmpty()) {
			return;
		}

		Card card = player.getDeck().get(0);
		player.removeCardFromDeck(card);

		if (player.getHand().size() >= 6) {
			// Only show burn message to human player.
			if (player == gameState.humanPlayer) {
				BasicCommands.addPlayer1Notification(out, "Hand full! Card burned.", 2);
			}
			return;
		}

		player.addCardToHand(card);

		// Only human hand is visible in UI.
		if (player == gameState.humanPlayer) {
			BasicCommands.drawCard(out, card, player.getHand().indexOf(card) + 1, 0);
		}
	}

}
