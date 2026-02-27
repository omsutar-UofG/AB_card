package game;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.BetterUnit;
import structures.basic.Tile;
import structures.basic.UnitAnimationType;

/**
 * Shared board and combat helpers for SC10-SC15.
 *
 * This class centralizes:
 * - tile highlighting rules
 * - occupancy bookkeeping
 * - movement reachability checks
 * - immediate attack resolution
 */
public final class SimpleBoardLogic {

	public static final int TILE_MODE_NORMAL = 0;
	public static final int TILE_MODE_MOVE_HIGHLIGHT = 1;
	public static final int TILE_MODE_ATTACK_HIGHLIGHT = 2;

	private SimpleBoardLogic() {}

	/**
	 * SC10/SC11/SC12: Stable tile key format used by highlight and occupancy sets.
	 */
	public static String tileKey(int tilex, int tiley) {
		return tilex + "-" + tiley;
	}

	/**
	 * Basic readiness check for event processing that depends on turn and players.
	 */
	public static boolean isTurnSystemReady(GameState gameState) {
		return gameState != null
				&& gameState.gameInitalised
				&& gameState.humanPlayer != null
				&& gameState.aiPlayer != null
				&& gameState.activePlayer != null;
	}

	public static int ownerForActivePlayer(GameState gameState) {
		return gameState.activePlayer == gameState.humanPlayer ? GameState.OWNER_HUMAN : GameState.OWNER_AI;
	}

	public static boolean isHumanTurn(GameState gameState) {
		return gameState.activePlayer == gameState.humanPlayer;
	}

	/**
	 * SC10-SC15: Register a unit in both id-index and tile-occupancy index.
	 */
	public static void registerUnit(GameState gameState, BetterUnit unit, Tile tile) {
		unit.setPositionByTile(tile);
		gameState.unitsById.put(unit.getId(), unit);
		gameState.unitIdByTile.put(tileKey(tile.getTilex(), tile.getTiley()), unit.getId());
	}

	public static BetterUnit getUnitAt(GameState gameState, int tilex, int tiley) {
		Integer unitId = gameState.unitIdByTile.get(tileKey(tilex, tiley));
		if (unitId == null) {
			return null;
		}
		return gameState.unitsById.get(unitId);
	}

	/**
	 * SC13/SC14: Back-end position update after move animation has completed.
	 */
	public static void moveUnitStateToTile(GameState gameState, BetterUnit unit, Tile destinationTile) {
		String oldTile = tileKey(unit.getPosition().getTilex(), unit.getPosition().getTiley());
		gameState.unitIdByTile.remove(oldTile);
		unit.setPositionByTile(destinationTile);
		gameState.unitIdByTile.put(tileKey(destinationTile.getTilex(), destinationTile.getTiley()), unit.getId());
	}

	public static boolean unitCanTakeAction(BetterUnit unit) {
		return unit != null && unit.getHealth() > 0 && !(unit.isHasMoved() && unit.isHasAttacked());
	}

	/**
	 * SC12: Clear current selection markers without touching board visuals.
	 */
	public static void clearSelection(GameState gameState) {
		gameState.selectedUnitId = null;
		gameState.approachTileByEnemyTile.clear();
	}

	/**
	 * SC12: Clear all tile highlights and restore normal tile mode.
	 */
	public static void clearHighlights(ActorRef out, GameState gameState) {
		Set<String> allHighlighted = new HashSet<String>();
		allHighlighted.addAll(gameState.moveHighlightTiles);
		allHighlighted.addAll(gameState.attackHighlightTiles);
		for (String key : allHighlighted) {
			Tile tile = getTileByKey(gameState, key);
			if (tile != null) {
				BasicCommands.drawTile(out, tile, TILE_MODE_NORMAL);
			}
		}
		gameState.moveHighlightTiles.clear();
		gameState.attackHighlightTiles.clear();
	}

	public static void clearSelectionAndHighlights(ActorRef out, GameState gameState) {
		clearHighlights(out, gameState);
		clearSelection(gameState);
	}

	/**
	 * SC13/SC14: Clear in-flight action continuation state.
	 */
	public static void clearPendingAction(GameState gameState) {
		gameState.pendingMoveUnitId = null;
		gameState.pendingAttackTargetUnitId = null;
		gameState.actionLocked = false;
	}

	/**
	 * SC15: Reset action flags for units belonging to the current active side.
	 */
	public static void resetActionFlagsForOwner(GameState gameState, int owner) {
		for (BetterUnit unit : gameState.unitsById.values()) {
			if (unit.getOwner() == owner) {
				unit.setHasMoved(false);
				unit.setHasAttacked(false);
			}
		}
	}

	/**
	 * SC10 + SC11:
	 * Build movement and attack highlight sets for one selected unit.
	 */
	public static HighlightPlan buildHighlightPlan(GameState gameState, BetterUnit selectedUnit) {
		HighlightPlan plan = new HighlightPlan();

		// SC10 + 2024 GameRules:
		// if a unit already attacked, it forfeits moving this turn.
		if (!selectedUnit.isHasMoved() && !selectedUnit.isHasAttacked()) {
			plan.moveTileKeys.addAll(computeReachableUnoccupiedTiles(gameState, selectedUnit));
		}

		// SC11: show red enemy targets if attack is still available.
		if (!selectedUnit.isHasAttacked()) {
			for (BetterUnit candidate : gameState.unitsById.values()) {
				if (candidate.getOwner() == selectedUnit.getOwner() || candidate.getHealth() <= 0) {
					continue;
				}
				String enemyKey = tileKey(candidate.getPosition().getTilex(), candidate.getPosition().getTiley());
				if (isInAttackRange(selectedUnit, candidate)) {
					plan.attackTileKeys.add(enemyKey);
					continue;
				}

				// SC14 support: mark reachable non-adjacent enemies in red if we can
				// move to an attack-adjacent tile this turn.
				if (!selectedUnit.isHasMoved()) {
					String approachTile = findApproachTileForEnemy(gameState, selectedUnit, candidate, plan.moveTileKeys);
					if (approachTile != null) {
						plan.attackTileKeys.add(enemyKey);
						plan.approachByEnemyTile.put(enemyKey, approachTile);
					}
				}
			}
		}

		return plan;
	}

	public static void applyHighlightPlan(ActorRef out, GameState gameState, HighlightPlan plan) {
		clearHighlights(out, gameState);
		gameState.moveHighlightTiles.addAll(plan.moveTileKeys);
		gameState.attackHighlightTiles.addAll(plan.attackTileKeys);
		gameState.approachTileByEnemyTile.clear();
		gameState.approachTileByEnemyTile.putAll(plan.approachByEnemyTile);

		for (String moveTile : gameState.moveHighlightTiles) {
			Tile tile = getTileByKey(gameState, moveTile);
			if (tile != null) {
				BasicCommands.drawTile(out, tile, TILE_MODE_MOVE_HIGHLIGHT);
			}
		}
		for (String attackTile : gameState.attackHighlightTiles) {
			Tile tile = getTileByKey(gameState, attackTile);
			if (tile != null) {
				BasicCommands.drawTile(out, tile, TILE_MODE_ATTACK_HIGHLIGHT);
			}
		}
	}

	public static boolean isInAttackRange(BetterUnit attacker, BetterUnit defender) {
		int dx = Math.abs(attacker.getPosition().getTilex() - defender.getPosition().getTilex());
		int dy = Math.abs(attacker.getPosition().getTiley() - defender.getPosition().getTiley());
		// 2024 GameRules: default attack supports adjacent diagonals.
		return isTileInAttackRange(dx, dy, attacker.getAttackRange());
	}

	/**
	 * SC14: Resolve one attack sequence and apply HP changes.
	 */
	public static void executeAttack(ActorRef out, GameState gameState, BetterUnit attacker, BetterUnit defender) {
		BasicCommands.playUnitAnimation(out, attacker, UnitAnimationType.attack);

		int damage = Math.max(0, attacker.getAttack());
		int nextHealth = defender.getHealth() - damage;
		defender.setHealth(nextHealth);

		if (nextHealth > 0) {
			BasicCommands.playUnitAnimation(out, defender, UnitAnimationType.hit);
			BasicCommands.setUnitHealth(out, defender, nextHealth);
		} else {
			BasicCommands.setUnitHealth(out, defender, 0);
			BasicCommands.playUnitAnimation(out, defender, UnitAnimationType.death);
			BasicCommands.deleteUnit(out, defender);
			gameState.unitIdByTile.remove(tileKey(defender.getPosition().getTilex(), defender.getPosition().getTiley()));
			gameState.unitsById.remove(defender.getId());
		}

		// Keep player-health card synced for avatar units.
		if (defender.isAvatar()) {
			int hp = Math.max(defender.getHealth(), 0);
			if (defender.getOwner() == GameState.OWNER_HUMAN) {
				gameState.humanPlayer.setHealth(hp);
				BasicCommands.setPlayer1Health(out, gameState.humanPlayer);
			} else {
				gameState.aiPlayer.setHealth(hp);
				BasicCommands.setPlayer2Health(out, gameState.aiPlayer);
			}
		}
	}

	public static Tile getTileByKey(GameState gameState, String key) {
		int[] xy = parseKey(key);
		if (xy == null || !inBoard(gameState, xy[0], xy[1])) {
			return null;
		}
		return gameState.board[xy[0]][xy[1]];
	}

	private static Set<String> computeReachableUnoccupiedTiles(GameState gameState, BetterUnit unit) {
		Set<String> reachable = new HashSet<String>();
		int startX = unit.getPosition().getTilex();
		int startY = unit.getPosition().getTiley();
		int maxStraightSteps = Math.max(0, unit.getMoveRange());

		// 2024 GameRules: default movement is up to two straight tiles (cardinal).
		int[][] cardinal = new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] dir : cardinal) {
			for (int step = 1; step <= maxStraightSteps; step++) {
				int nx = startX + (dir[0] * step);
				int ny = startY + (dir[1] * step);
				if (!inBoard(gameState, nx, ny)) {
					break;
				}
				String key = tileKey(nx, ny);
				// Cannot pass through or land on occupied tiles.
				if (gameState.unitIdByTile.containsKey(key) && !Objects.equals(gameState.unitIdByTile.get(key), unit.getId())) {
					break;
				}
				reachable.add(key);
			}
		}

		// 2024 GameRules: default movement also allows one diagonal tile.
		if (maxStraightSteps >= 1) {
			int[][] diagonal = new int[][] {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
			for (int[] dir : diagonal) {
				int nx = startX + dir[0];
				int ny = startY + dir[1];
				if (!inBoard(gameState, nx, ny)) {
					continue;
				}
				String key = tileKey(nx, ny);
				if (!gameState.unitIdByTile.containsKey(key) || Objects.equals(gameState.unitIdByTile.get(key), unit.getId())) {
					reachable.add(key);
				}
			}
		}

		reachable.remove(tileKey(startX, startY));
		return reachable;
	}

	private static String findApproachTileForEnemy(
			GameState gameState,
			BetterUnit attacker,
			BetterUnit enemy,
			Set<String> reachableMoves) {

		int ex = enemy.getPosition().getTilex();
		int ey = enemy.getPosition().getTiley();
		int ax = attacker.getPosition().getTilex();
		int ay = attacker.getPosition().getTiley();
		int attackRange = Math.max(1, attacker.getAttackRange());

		String best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (String candidate : reachableMoves) {
			int[] xy = parseKey(candidate);
			if (xy == null || !inBoard(gameState, xy[0], xy[1])) {
				continue;
			}
			int dx = Math.abs(xy[0] - ex);
			int dy = Math.abs(xy[1] - ey);
			if (!isTileInAttackRange(dx, dy, attackRange)) {
				continue;
			}
			int distance = Math.abs(xy[0] - ax) + Math.abs(xy[1] - ay);
			if (distance < bestDistance) {
				best = candidate;
				bestDistance = distance;
			}
		}
		return best;
	}

	private static boolean isTileInAttackRange(int dx, int dy, int range) {
		return !(dx == 0 && dy == 0) && dx <= range && dy <= range;
	}

	private static int[] parseKey(String key) {
		String[] split = key.split("-");
		if (split.length != 2) {
			return null;
		}
		try {
			return new int[] {Integer.parseInt(split[0]), Integer.parseInt(split[1])};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static boolean inBoard(GameState gameState, int x, int y) {
		return x >= 0
				&& y >= 0
				&& x < gameState.board.length
				&& y < gameState.board[0].length
				&& gameState.board[x][y] != null;
	}

	public static class HighlightPlan {
		public Set<String> moveTileKeys = new HashSet<String>();
		public Set<String> attackTileKeys = new HashSet<String>();
		public Map<String, String> approachByEnemyTile = new HashMap<String, String>();
	}
}
