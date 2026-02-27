package events;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import game.SimpleBoardLogic;
import structures.GameState;

/**
 * Indicates that a unit instance has started a move. 
 * The event reports the unique id of the unit.
 * 
 * { 
 *   messageType = “unitMoving”
 *   id = <unit id>
 * }
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class UnitMoving implements EventProcessor{

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {
		int unitid = message.get("id").asInt();

		if (!SimpleBoardLogic.isTurnSystemReady(gameState)) {
			return;
		}
		// SC13/SC14: lock interaction while movement animation is running.
		if (gameState.pendingMoveUnitId != null && gameState.pendingMoveUnitId == unitid) {
			gameState.actionLocked = true;
		}
	}

}
