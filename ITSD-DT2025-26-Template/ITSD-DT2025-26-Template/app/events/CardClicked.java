package events;


import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import game.SimpleBoardLogic;
import game.SimpleCardLogic;
import structures.GameState;

/**
 * Indicates that the user has clicked an object on the game canvas, in this case a card.
 * The event returns the position in the player's hand the card resides within.
 * 
 * { 
 *   messageType = “cardClicked”
 *   position = <hand index position [1-6]>
 * }
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class CardClicked implements EventProcessor{

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {
		// SC21-SC29 + SC40: only allow card interactions while game is active.
		if (!SimpleBoardLogic.isGameActive(gameState)) {
			return;
		}
		// Card play is human-driven in this sprint scope.
		if (!SimpleBoardLogic.isHumanTurn(gameState)) {
			return;
		}
		// Do not interleave card flow with ongoing movement/attack animation chain.
		if (gameState.actionLocked) {
			return;
		}

		int handPosition = message.get("position").asInt();

		// SC12 compatibility: selecting a card exits current unit-selection mode.
		SimpleBoardLogic.clearSelectionAndHighlights(out, gameState);
		SimpleBoardLogic.clearPendingAction(gameState);

		// SC21 + SC22 (+ SC23-SC29 target prep):
		// highlight selected card, validate mana, and highlight legal targets.
		SimpleCardLogic.handleCardClicked(out, gameState, handPosition);
	}

}
