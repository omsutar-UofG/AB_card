package events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import akka.actor.ActorRef;
import commands.BasicCommands;
import game.SimpleBoardLogic;
import game.SimpleCardLogic;
import structures.GameState;
import structures.basic.BetterUnit;
import structures.basic.Card;
import structures.basic.Player;
import structures.basic.Tile;

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
		if (gameState.gameOver) {
			return;
		}

		// SC37: AI routine starts automatically when AI becomes active player.
		if (gameState.activePlayer != gameState.aiPlayer) {
			return;
		}

		executeAiTurn(out, gameState);
		if (!gameState.gameOver) {
			endAiTurnAndStartHumanTurn(out, gameState);
		}
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
	 * SC37/SC38/SC39:
	 * AI turn routine: for each AI unit, attack if possible; otherwise move toward human avatar and try attack again.
	 */
	private void executeAiTurn(ActorRef out, GameState gameState) {
		SimpleBoardLogic.clearSelectionAndHighlights(out, gameState);
		SimpleBoardLogic.clearPendingAction(gameState);
		SimpleCardLogic.clearCardSelectionAndHandHighlight(out, gameState);

		// SC37 extension:
		// AI card phase (play at most one legal card) before unit move/attack phase.
		SimpleCardLogic.aiTryPlayOneCard(out, gameState);
		if (gameState.gameOver) {
			return;
		}

		List<BetterUnit> aiUnits = snapshotAiUnits(gameState);
		for (BetterUnit snapshotUnit : aiUnits) {
			if (gameState.gameOver) {
				return;
			}
			BetterUnit aiUnit = gameState.unitsById.get(snapshotUnit.getId());
			if (aiUnit == null || aiUnit.getHealth() <= 0 || aiUnit.getOwner() != GameState.OWNER_AI) {
				continue;
			}
			if (aiUnit.isHasMoved() && aiUnit.isHasAttacked()) {
				continue;
			}

			// SC39: attack first when already in range.
			BetterUnit attackTarget = chooseAiAttackTarget(gameState, aiUnit);
			if (attackTarget != null && !aiUnit.isHasAttacked()) {
				performAiAttack(out, gameState, aiUnit, attackTarget);
				continue;
			}

			// SC38: otherwise move toward human avatar using shortest-distance destination this turn.
			if (!aiUnit.isHasMoved()
					&& !aiUnit.isHasAttacked()
					&& !SimpleBoardLogic.isMovementLockedByProvoke(gameState, aiUnit)) {
				Tile moveTile = chooseBestMoveTowardHumanAvatar(gameState, aiUnit);
				if (moveTile != null) {
					// SC38 visual-path fix: choose axis order that avoids occupied intermediate crossings.
					boolean yFirst = SimpleBoardLogic.shouldMoveYFirstForVisualPath(gameState, aiUnit, moveTile);
					BasicCommands.moveUnitToTile(out, aiUnit, moveTile, yFirst);
					// SC39 timing fix: wait until movement animation is expected to finish before attacking.
					waitMs(estimateAiMoveAnimationMs(gameState, aiUnit, moveTile));
					SimpleBoardLogic.moveUnitStateToTile(gameState, aiUnit, moveTile);
					aiUnit.setHasMoved(true);
				}
			}

			if (gameState.gameOver) {
				return;
			}

			// SC39: after movement, attack if any legal target enters range.
			attackTarget = chooseAiAttackTarget(gameState, aiUnit);
			if (attackTarget != null && !aiUnit.isHasAttacked()) {
				performAiAttack(out, gameState, aiUnit, attackTarget);
			}
		}
	}

	/**
	 * SC39:
	 * Executes one validated AI attack exchange and applies post-attack action lock semantics.
	 */
	private void performAiAttack(ActorRef out, GameState gameState, BetterUnit attacker, BetterUnit defender) {
		if (attacker == null || defender == null || gameState.gameOver) {
			return;
		}
		if (!SimpleBoardLogic.canAttackTargetRespectingProvoke(gameState, attacker, defender)) {
			return;
		}
		SimpleBoardLogic.resolveCombatExchange(out, gameState, attacker, defender);
		BetterUnit refreshed = gameState.unitsById.get(attacker.getId());
		if (refreshed == null || refreshed.getHealth() <= 0) {
			return;
		}
		// 2024 GameRules: attacking before moving forfeits movement.
		if (!refreshed.isHasMoved()) {
			refreshed.setHasMoved(true);
		}
		refreshed.setHasAttacked(true);
	}

	/**
	 * SC39:
	 * Target selection policy: if legal, prioritize human avatar, then lowest-health enemy.
	 */
	private BetterUnit chooseAiAttackTarget(GameState gameState, BetterUnit attacker) {
		if (attacker == null || attacker.getHealth() <= 0) {
			return null;
		}
		List<BetterUnit> candidates = new ArrayList<BetterUnit>();

		List<BetterUnit> forcedProvokeTargets = SimpleBoardLogic.findAdjacentEnemyProvokeUnits(gameState, attacker);
		if (!forcedProvokeTargets.isEmpty()) {
			for (BetterUnit target : forcedProvokeTargets) {
				if (target.getHealth() > 0 && SimpleBoardLogic.isInAttackRange(attacker, target)) {
					candidates.add(target);
				}
			}
		} else {
			for (BetterUnit unit : gameState.unitsById.values()) {
				if (unit.getOwner() == attacker.getOwner() || unit.getHealth() <= 0) {
					continue;
				}
				if (SimpleBoardLogic.isInAttackRange(attacker, unit)
						&& SimpleBoardLogic.canAttackTargetRespectingProvoke(gameState, attacker, unit)) {
					candidates.add(unit);
				}
			}
		}
		if (candidates.isEmpty()) {
			return null;
		}

		Collections.sort(candidates, (a, b) -> {
			if (a.isAvatar() && !b.isAvatar()) {
				return -1;
			}
			if (!a.isAvatar() && b.isAvatar()) {
				return 1;
			}
			if (a.getHealth() != b.getHealth()) {
				return Integer.compare(a.getHealth(), b.getHealth());
			}
			return Integer.compare(a.getId(), b.getId());
		});
		return candidates.get(0);
	}

	/**
	 * SC38:
	 * Chooses legal destination minimizing Manhattan distance to the human avatar.
	 */
	private Tile chooseBestMoveTowardHumanAvatar(GameState gameState, BetterUnit unit) {
		BetterUnit humanAvatar = SimpleBoardLogic.getAvatarUnitForOwner(gameState, GameState.OWNER_HUMAN);
		if (humanAvatar == null) {
			return null;
		}
		SimpleBoardLogic.HighlightPlan plan = SimpleBoardLogic.buildHighlightPlan(gameState, unit);
		if (plan.moveTileKeys.isEmpty()) {
			return null;
		}

		String bestKey = null;
		int bestDistance = Integer.MAX_VALUE;
		for (String key : plan.moveTileKeys) {
			int[] xy = SimpleBoardLogic.parseTileKey(key);
			if (xy == null) {
				continue;
			}
			int distance = Math.abs(xy[0] - humanAvatar.getPosition().getTilex())
					+ Math.abs(xy[1] - humanAvatar.getPosition().getTiley());
			if (distance < bestDistance) {
				bestDistance = distance;
				bestKey = key;
			}
		}
		if (bestKey == null) {
			return null;
		}
		return SimpleBoardLogic.getTileByKey(gameState, bestKey);
	}

	/**
	 * SC37/SC38/SC39 helper:
	 * Snapshot alive AI units in stable id order to avoid iterator issues during combat removals.
	 */
	private List<BetterUnit> snapshotAiUnits(GameState gameState) {
		List<BetterUnit> units = new ArrayList<BetterUnit>();
		for (BetterUnit unit : gameState.unitsById.values()) {
			if (unit.getOwner() == GameState.OWNER_AI && unit.getHealth() > 0) {
				units.add(unit);
			}
		}
		Collections.sort(units, (a, b) -> Integer.compare(a.getId(), b.getId()));
		return units;
	}

	/**
	 * Small pacing helper to keep AI movement/animation sequence readable and deterministic.
	 */
	private void waitMs(int ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * SC39 timing fix:
	 * Estimates AI move animation duration from board pixel distance before chaining attack logic.
	 */
	private int estimateAiMoveAnimationMs(GameState gameState, BetterUnit unit, Tile destination) {
		if (gameState == null || unit == null || unit.getPosition() == null || destination == null) {
			return 600;
		}
		Tile start = gameState.board[unit.getPosition().getTilex()][unit.getPosition().getTiley()];
		if (start == null) {
			return 600;
		}

		// Front-end configuration in gamescreen.scala.html:
		// moveVelocity = 2 px/frame, fps = 60.
		final double moveVelocityPxPerFrame = 2.0d;
		final double fps = 60.0d;

		int pixelDistance = Math.abs(destination.getXpos() - start.getXpos())
				+ Math.abs(destination.getYpos() - start.getYpos());
		double frames = pixelDistance / moveVelocityPxPerFrame;
		int estimatedMs = (int) Math.ceil(frames * (1000.0d / fps));

		// Add buffer for command queue + animation settling.
		int withBuffer = estimatedMs + 250;
		if (withBuffer < 320) {
			return 320;
		}
		if (withBuffer > 7000) {
			return 7000;
		}
		return withBuffer;
	}

	/**
	 * Implements AI auto-pass for SC08 continuity:
	 * 1) drain AI mana, 2) draw for AI at end-turn, 3) swap active player to human.
	 */
	private void endAiTurnAndStartHumanTurn(ActorRef out, GameState gameState) {
		if (gameState.gameOver) {
			return;
		}
		// SC07 equivalent for AI: clear remaining mana at end of its turn.
		gameState.aiPlayer.setMana(0);
		BasicCommands.setPlayer2Mana(out, gameState.aiPlayer);

		// SC12: make sure no stale selection/highlight state leaks across turns.
		SimpleBoardLogic.clearSelectionAndHighlights(out, gameState);
		SimpleBoardLogic.clearPendingAction(gameState);
		// SC21/SC12: clear card-selection highlight when control changes.
		SimpleCardLogic.clearCardSelectionAndHandHighlight(out, gameState);

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
		// SC27 UX:
		// tell player when one or more of their units are stun-locked this turn.
		if (!gameState.stunnedThisTurnUnitIds.isEmpty()) {
			BasicCommands.addPlayer1Notification(out, "You are stunned this turn", 2);
		}
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
