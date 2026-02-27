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
	 * 1) drain AI mana, 2) swap active player to human, 3) trigger human turn-start logic.
	 */
	private void endAiTurnAndStartHumanTurn(ActorRef out, GameState gameState) {
		// SC07 equivalent for AI: clear remaining mana at end of its turn.
		gameState.aiPlayer.setMana(0);
		BasicCommands.setPlayer2Mana(out, gameState.aiPlayer);

		// SC12: make sure no stale selection/highlight state leaks across turns.
		SimpleBoardLogic.clearSelectionAndHighlights(out, gameState);
		SimpleBoardLogic.clearPendingAction(gameState);

		// SC08: hand control back to human and advance round counter.
		gameState.activePlayer = gameState.humanPlayer;
		gameState.turnNumber++;
		BasicCommands.addPlayer1Notification(out, "Your Turn", 2);

		// SC05: start-of-turn resources for human player (with agreed 9-mana cap).
		int newMana = gameState.turnNumber + 1;
		if (newMana > 9) {
			newMana = 9;
		}
		gameState.humanPlayer.setMana(newMana);
		BasicCommands.setPlayer1Mana(out, gameState.humanPlayer);

		// SC05 + SC06: human draws one card, overdraw burns when hand is full.
		drawCardForHuman(out, gameState.humanPlayer);

		// SC15: refresh action limits for the side that just became active.
		SimpleBoardLogic.resetActionFlagsForOwner(gameState, GameState.OWNER_HUMAN);
	}

	/**
	 * Draw one card for human hand and apply the overdraw rule at hand size 6.
	 */
	private void drawCardForHuman(ActorRef out, Player humanPlayer) {
		if (humanPlayer.getDeck().isEmpty()) {
			return;
		}

		Card card = humanPlayer.getDeck().get(0);
		humanPlayer.removeCardFromDeck(card);

		if (humanPlayer.getHand().size() >= 6) {
			BasicCommands.addPlayer1Notification(out, "Hand full! Card burned.", 2);
			return;
		}

		humanPlayer.addCardToHand(card);
		BasicCommands.drawCard(out, card, humanPlayer.getHand().indexOf(card) + 1, 0);
	}

}
