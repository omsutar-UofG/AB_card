package game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.basic.BetterUnit;
import structures.basic.Card;
import structures.basic.EffectAnimation;
import structures.basic.Player;
import structures.basic.Tile;
import utils.BasicObjectBuilders;
import utils.StaticConfFiles;

/**
 * Shared card-play logic for SC21-SC29.
 *
 * Rule priority used in this class:
 * 1) 2024-GameRules
 * 2) 2025-26 deck card specification
 * 3) story-card generic wording
 */
public final class SimpleCardLogic {

	public static final String TARGET_SUMMON_TILE = "SUMMON_TILE";
	public static final String TARGET_ALLY_UNIT = "ALLY_UNIT";
	public static final String TARGET_ENEMY_UNIT = "ENEMY_UNIT";
	public static final String TARGET_ENEMY_NON_AVATAR = "ENEMY_NON_AVATAR";
	public static final String TARGET_NONE = "NONE";

	private static final int CARD_MODE_NORMAL = 0;
	private static final int CARD_MODE_SELECTED = 1;
	private static final int DEFAULT_NOTIFICATION_SECONDS = 2;
	private static final int DEFAULT_EFFECT_WAIT_MS = 250;
	private static final int MAX_SPELL_SUMMON_COUNT = 3;

	private SimpleCardLogic() {}

	/**
	 * SC21: true when a hand card is currently selected for targeting/playing.
	 */
	public static boolean hasSelectedCard(GameState gameState) {
		return gameState.selectedCardHandPosition != null;
	}

	/**
	 * SC21/SC12:
	 * Clears selected-card state and redraws hand to non-highlight mode.
	 */
	public static void clearCardSelectionAndHandHighlight(ActorRef out, GameState gameState) {
		if (gameState.selectedCardHandPosition == null && gameState.selectedCardTargetMode == null) {
			return;
		}
		gameState.selectedCardHandPosition = null;
		gameState.selectedCardTargetMode = null;
		redrawHumanHand(out, gameState, null);
	}

	/**
	 * SC21 + SC22:
	 * Handles card click, highlights selected card, validates mana, and highlights valid targets.
	 */
	public static void handleCardClicked(ActorRef out, GameState gameState, int handPosition) {
		Card card = getCardByHandPosition(gameState.humanPlayer, handPosition);
		if (card == null) {
			clearCardSelectionAndHandHighlight(out, gameState);
			return;
		}

		// Toggle off when user clicks the currently selected card again.
		if (gameState.selectedCardHandPosition != null && gameState.selectedCardHandPosition == handPosition) {
			SimpleBoardLogic.clearHighlights(out, gameState);
			clearCardSelectionAndHandHighlight(out, gameState);
			return;
		}

		// SC22: deny selection/play flow when mana is insufficient.
		if (gameState.humanPlayer.getMana() < card.getManacost()) {
			BasicCommands.addPlayer1Notification(out, "Not enough mana", DEFAULT_NOTIFICATION_SECONDS);
			SimpleBoardLogic.clearHighlights(out, gameState);
			clearCardSelectionAndHandHighlight(out, gameState);
			return;
		}

		String targetMode = resolveTargetMode(card);
		if (TARGET_NONE.equals(targetMode)) {
			BasicCommands.addPlayer1Notification(out, "Card not in Sprint 4 scope", DEFAULT_NOTIFICATION_SECONDS);
			SimpleBoardLogic.clearHighlights(out, gameState);
			clearCardSelectionAndHandHighlight(out, gameState);
			return;
		}

		gameState.selectedCardHandPosition = handPosition;
		gameState.selectedCardTargetMode = targetMode;
		redrawHumanHand(out, gameState, handPosition);

		SimpleBoardLogic.HighlightPlan plan = buildTargetHighlightPlan(gameState, card, targetMode);
		SimpleBoardLogic.applyHighlightPlan(out, gameState, plan);

		if (plan.moveTileKeys.isEmpty() && plan.attackTileKeys.isEmpty()) {
			BasicCommands.addPlayer1Notification(out, "No valid targets", DEFAULT_NOTIFICATION_SECONDS);
			clearCardSelectionAndHandHighlight(out, gameState);
		}
	}

	/**
	 * SC23-SC29:
	 * Resolves tile click when a card is selected. Returns true if click was consumed by card flow.
	 */
	public static boolean resolveSelectedCardOnTile(
			ActorRef out,
			GameState gameState,
			Tile clickedTile,
			BetterUnit clickedUnit) {
		if (!hasSelectedCard(gameState)) {
			return false;
		}

		Card selectedCard = getCardByHandPosition(gameState.humanPlayer, gameState.selectedCardHandPosition);
		if (selectedCard == null) {
			SimpleBoardLogic.clearHighlights(out, gameState);
			clearCardSelectionAndHandHighlight(out, gameState);
			return true;
		}

		if (gameState.humanPlayer.getMana() < selectedCard.getManacost()) {
			BasicCommands.addPlayer1Notification(out, "Not enough mana", DEFAULT_NOTIFICATION_SECONDS);
			SimpleBoardLogic.clearHighlights(out, gameState);
			clearCardSelectionAndHandHighlight(out, gameState);
			return true;
		}

		if (!isValidTargetSelection(gameState, clickedTile, clickedUnit)) {
			// SC12 behavior for card flow: clear on unrelated tile click.
			SimpleBoardLogic.clearHighlights(out, gameState);
			clearCardSelectionAndHandHighlight(out, gameState);
			return true;
		}

		// 2024 GameRules card-order:
		// 1) spend mana, 2) apply card effect, 3) remove card from hand.
		int manaBefore = gameState.humanPlayer.getMana();
		int manaAfter = manaBefore - selectedCard.getManacost();
		gameState.humanPlayer.setMana(manaAfter);
		BasicCommands.setPlayer1Mana(out, gameState.humanPlayer);

		boolean effectApplied = executeSelectedCardEffect(out, gameState, selectedCard, clickedTile, clickedUnit);
		if (!effectApplied) {
			// Defensive rollback: if effect cannot resolve, restore spent mana.
			gameState.humanPlayer.setMana(manaBefore);
			BasicCommands.setPlayer1Mana(out, gameState.humanPlayer);
			BasicCommands.addPlayer1Notification(out, "Action denied", DEFAULT_NOTIFICATION_SECONDS);
			SimpleBoardLogic.clearHighlights(out, gameState);
			clearCardSelectionAndHandHighlight(out, gameState);
			return true;
		}

		discardCardAndReorderHand(out, gameState.humanPlayer, gameState.selectedCardHandPosition);
		SimpleBoardLogic.clearHighlights(out, gameState);
		gameState.selectedCardHandPosition = null;
		gameState.selectedCardTargetMode = null;
		return true;
	}

	/**
	 * SC21 + SC29: redraw full human hand and optionally mark one card selected.
	 */
	private static void redrawHumanHand(ActorRef out, GameState gameState, Integer selectedPosition) {
		for (int i = 1; i <= 6; i++) {
			BasicCommands.deleteCard(out, i);
		}

		List<Card> hand = gameState.humanPlayer.getHand();
		for (int i = 0; i < hand.size() && i < 6; i++) {
			int position = i + 1;
			int mode = (selectedPosition != null && selectedPosition == position) ? CARD_MODE_SELECTED : CARD_MODE_NORMAL;
			BasicCommands.drawCard(out, hand.get(i), position, mode);
		}
	}

	/**
	 * SC22/SC23/SC24/SC25/SC26/SC27/SC28:
	 * Builds tile highlight plan for selected card based on card type and target rules.
	 */
	private static SimpleBoardLogic.HighlightPlan buildTargetHighlightPlan(GameState gameState, Card card, String targetMode) {
		SimpleBoardLogic.HighlightPlan plan = new SimpleBoardLogic.HighlightPlan();
		int owner = GameState.OWNER_HUMAN;

		if (TARGET_SUMMON_TILE.equals(targetMode)) {
			plan.moveTileKeys.addAll(SimpleBoardLogic.computeAdjacentUnoccupiedTilesForOwner(gameState, owner));
			return plan;
		}

		for (BetterUnit unit : gameState.unitsById.values()) {
			if (unit.getHealth() <= 0) {
				continue;
			}
			String key = SimpleBoardLogic.tileKey(unit.getPosition().getTilex(), unit.getPosition().getTiley());

			if (TARGET_ENEMY_UNIT.equals(targetMode) && unit.getOwner() != owner) {
				plan.attackTileKeys.add(key);
			}
			if (TARGET_ALLY_UNIT.equals(targetMode) && unit.getOwner() == owner) {
				plan.moveTileKeys.add(key);
			}
			if (TARGET_ENEMY_NON_AVATAR.equals(targetMode) && unit.getOwner() != owner && !unit.isAvatar()) {
				plan.attackTileKeys.add(key);
			}
		}
		return plan;
	}

	/**
	 * SC23-SC28: validates whether clicked tile/unit matches active card target constraints.
	 */
	private static boolean isValidTargetSelection(GameState gameState, Tile clickedTile, BetterUnit clickedUnit) {
		String key = SimpleBoardLogic.tileKey(clickedTile.getTilex(), clickedTile.getTiley());
		String mode = gameState.selectedCardTargetMode;

		if (TARGET_SUMMON_TILE.equals(mode)) {
			return gameState.moveHighlightTiles.contains(key) && clickedUnit == null;
		}
		if (TARGET_ALLY_UNIT.equals(mode)) {
			return gameState.moveHighlightTiles.contains(key)
					&& clickedUnit != null
					&& clickedUnit.getOwner() == GameState.OWNER_HUMAN;
		}
		if (TARGET_ENEMY_UNIT.equals(mode)) {
			return gameState.attackHighlightTiles.contains(key)
					&& clickedUnit != null
					&& clickedUnit.getOwner() == GameState.OWNER_AI;
		}
		if (TARGET_ENEMY_NON_AVATAR.equals(mode)) {
			return gameState.attackHighlightTiles.contains(key)
					&& clickedUnit != null
					&& clickedUnit.getOwner() == GameState.OWNER_AI
					&& !clickedUnit.isAvatar();
		}
		return false;
	}

	/**
	 * SC23-SC28:
	 * Executes effect body only. Cost deduction and hand discard are handled by caller.
	 */
	private static boolean executeSelectedCardEffect(
			ActorRef out,
			GameState gameState,
			Card selectedCard,
			Tile clickedTile,
			BetterUnit clickedUnit) {

		if (selectedCard.isCreature()) {
			return summonCreatureFromCard(out, gameState, selectedCard, clickedTile, GameState.OWNER_HUMAN);
		}

		String name = normalizeCardName(selectedCard);
		if ("truestrike".equals(name)) {
			return castTruestrike(out, gameState, clickedTile, clickedUnit);
		}
		if ("sundrop elixir".equals(name)) {
			return castSundropElixir(out, gameState, clickedTile, clickedUnit);
		}
		if ("dark terminus".equals(name)) {
			return castDarkTerminus(out, gameState, clickedTile, clickedUnit);
		}
		if ("beamshock".equals(name)) {
			return castBeamshock(out, gameState, clickedTile, clickedUnit);
		}
		if ("wraithling swarm".equals(name)) {
			return castWraithlingSwarm(out, gameState, clickedTile);
		}

		return false;
	}

	/**
	 * SC23:
	 * Summon creature card onto selected legal tile.
	 */
	private static boolean summonCreatureFromCard(
			ActorRef out,
			GameState gameState,
			Card card,
			Tile targetTile,
			int owner) {
		if (card.getUnitConfig() == null) {
			return false;
		}
		String occupiedKey = SimpleBoardLogic.tileKey(targetTile.getTilex(), targetTile.getTiley());
		if (gameState.unitIdByTile.containsKey(occupiedKey)) {
			return false;
		}

		BetterUnit summoned = (BetterUnit) BasicObjectBuilders.loadUnit(card.getUnitConfig(), gameState.nextUnitId, BetterUnit.class);
		if (summoned == null) {
			return false;
		}
		gameState.nextUnitId++;

		int summonAttack = Math.max(0, card.getBigCard().getAttack());
		int summonHealth = Math.max(1, card.getBigCard().getHealth());

		summoned.setOwner(owner);
		summoned.setAvatar(false);
		summoned.setAttack(summonAttack);
		summoned.setHealth(summonHealth);
		summoned.setMaxHealth(summonHealth);
		summoned.setMoveRange(2);
		summoned.setAttackRange(1);
		summoned.setHasMoved(true);
		summoned.setHasAttacked(true);
		summoned.setStunTurnsRemaining(0);
		summoned.setPositionByTile(targetTile);

		playEffectAndWait(out, StaticConfFiles.f1_summon, targetTile);
		BasicCommands.drawUnit(out, summoned, targetTile);
		BasicCommands.setUnitAttack(out, summoned, summoned.getAttack());
		BasicCommands.setUnitHealth(out, summoned, summoned.getHealth());
		SimpleBoardLogic.registerUnit(gameState, summoned, targetTile);
		return true;
	}

	/**
	 * SC24:
	 * Truestrike -> deal fixed damage to an enemy unit target.
	 */
	private static boolean castTruestrike(ActorRef out, GameState gameState, Tile targetTile, BetterUnit target) {
		if (target == null || target.getOwner() != GameState.OWNER_AI) {
			return false;
		}
		playEffectAndWait(out, StaticConfFiles.f1_inmolation, targetTile);
		SimpleBoardLogic.applySpellDamage(out, gameState, target, 2);
		return true;
	}

	/**
	 * SC25:
	 * Sundrop Elixir -> heal allied target by 5 without exceeding max health.
	 */
	private static boolean castSundropElixir(ActorRef out, GameState gameState, Tile targetTile, BetterUnit target) {
		if (target == null || target.getOwner() != GameState.OWNER_HUMAN) {
			return false;
		}
		playEffectAndWait(out, StaticConfFiles.f1_buff, targetTile);
		SimpleBoardLogic.applyHeal(out, gameState, target, 5);
		return true;
	}

	/**
	 * SC26 + 2025-26 Deck spec:
	 * Dark Terminus -> destroy enemy non-avatar unit, then summon Wraithling on that tile.
	 */
	private static boolean castDarkTerminus(ActorRef out, GameState gameState, Tile targetTile, BetterUnit target) {
		if (target == null || target.getOwner() != GameState.OWNER_AI || target.isAvatar()) {
			return false;
		}

		int targetTileX = target.getPosition().getTilex();
		int targetTileY = target.getPosition().getTiley();
		Tile replacementTile = gameState.board[targetTileX][targetTileY];

		playEffectAndWait(out, StaticConfFiles.f1_martyrdom, targetTile);
		SimpleBoardLogic.applySpellDamage(out, gameState, target, target.getHealth());

		if (replacementTile != null) {
			summonWraithling(out, gameState, replacementTile, GameState.OWNER_HUMAN);
		}
		return true;
	}

	/**
	 * SC27 + 2025-26 Deck spec:
	 * Beamshock -> stun enemy non-avatar unit for its next turn only.
	 */
	private static boolean castBeamshock(ActorRef out, GameState gameState, Tile targetTile, BetterUnit target) {
		if (target == null || target.getOwner() != GameState.OWNER_AI || target.isAvatar()) {
			return false;
		}
		playEffectAndWait(out, StaticConfFiles.f1_inmolation, targetTile);
		target.setStunTurnsRemaining(1);
		return true;
	}

	/**
	 * SC28 + 2025-26 Deck spec:
	 * Wraithling Swarm -> summon 3 Wraithlings in sequence (up to available legal tiles).
	 */
	private static boolean castWraithlingSwarm(ActorRef out, GameState gameState, Tile selectedTile) {
		boolean spawnedAny = summonWraithling(out, gameState, selectedTile, GameState.OWNER_HUMAN);
		int spawned = spawnedAny ? 1 : 0;

		while (spawned < MAX_SPELL_SUMMON_COUNT) {
			List<String> sortedCandidates = sortedTileKeys(SimpleBoardLogic.computeAdjacentUnoccupiedTilesForOwner(gameState, GameState.OWNER_HUMAN));
			if (sortedCandidates.isEmpty()) {
				break;
			}
			Tile nextTile = SimpleBoardLogic.getTileByKey(gameState, sortedCandidates.get(0));
			if (nextTile == null) {
				break;
			}
			if (!summonWraithling(out, gameState, nextTile, GameState.OWNER_HUMAN)) {
				break;
			}
			spawned++;
		}
		return spawnedAny;
	}

	/**
	 * SC28 helper:
	 * Summons one Wraithling token with 1/1 stats.
	 */
	private static boolean summonWraithling(ActorRef out, GameState gameState, Tile tile, int owner) {
		String key = SimpleBoardLogic.tileKey(tile.getTilex(), tile.getTiley());
		if (gameState.unitIdByTile.containsKey(key)) {
			return false;
		}

		BetterUnit wraithling = (BetterUnit) BasicObjectBuilders.loadUnit(StaticConfFiles.wraithling, gameState.nextUnitId, BetterUnit.class);
		if (wraithling == null) {
			return false;
		}
		gameState.nextUnitId++;

		wraithling.setOwner(owner);
		wraithling.setAvatar(false);
		wraithling.setAttack(1);
		wraithling.setHealth(1);
		wraithling.setMaxHealth(1);
		wraithling.setMoveRange(2);
		wraithling.setAttackRange(1);
		wraithling.setHasMoved(true);
		wraithling.setHasAttacked(true);
		wraithling.setStunTurnsRemaining(0);
		wraithling.setPositionByTile(tile);

		playEffectAndWait(out, StaticConfFiles.f1_summon, tile);
		BasicCommands.drawUnit(out, wraithling, tile);
		BasicCommands.setUnitAttack(out, wraithling, wraithling.getAttack());
		BasicCommands.setUnitHealth(out, wraithling, wraithling.getHealth());
		SimpleBoardLogic.registerUnit(gameState, wraithling, tile);
		return true;
	}

	/**
	 * SC29:
	 * Remove used card and redraw hand positions so indices stay contiguous.
	 */
	private static void discardCardAndReorderHand(ActorRef out, Player player, int handPosition) {
		int index = handPosition - 1;
		if (index < 0 || index >= player.getHand().size()) {
			return;
		}
		player.getHand().remove(index);

		for (int i = 1; i <= 6; i++) {
			BasicCommands.deleteCard(out, i);
		}
		for (int i = 0; i < player.getHand().size() && i < 6; i++) {
			BasicCommands.drawCard(out, player.getHand().get(i), i + 1, CARD_MODE_NORMAL);
		}
	}

	private static Card getCardByHandPosition(Player player, Integer handPosition) {
		if (player == null || handPosition == null) {
			return null;
		}
		int index = handPosition - 1;
		if (index < 0 || index >= player.getHand().size()) {
			return null;
		}
		return player.getHand().get(index);
	}

	private static String resolveTargetMode(Card card) {
		if (card.isCreature()) {
			return TARGET_SUMMON_TILE;
		}
		String name = normalizeCardName(card);
		if ("truestrike".equals(name)) {
			return TARGET_ENEMY_UNIT;
		}
		if ("sundrop elixir".equals(name)) {
			return TARGET_ALLY_UNIT;
		}
		if ("dark terminus".equals(name)) {
			return TARGET_ENEMY_NON_AVATAR;
		}
		if ("beamshock".equals(name)) {
			return TARGET_ENEMY_NON_AVATAR;
		}
		if ("wraithling swarm".equals(name)) {
			return TARGET_SUMMON_TILE;
		}
		return TARGET_NONE;
	}

	private static String normalizeCardName(Card card) {
		if (card == null || card.getCardname() == null) {
			return "";
		}
		return card.getCardname().trim().toLowerCase();
	}

	private static void playEffectAndWait(ActorRef out, String effectConfPath, Tile tile) {
		EffectAnimation effect = BasicObjectBuilders.loadEffect(effectConfPath);
		if (effect == null || tile == null) {
			return;
		}
		int estimatedMs = BasicCommands.playEffectAnimation(out, effect, tile);
		int waitMs = estimatedMs > 0 ? estimatedMs : DEFAULT_EFFECT_WAIT_MS;
		try {
			Thread.sleep(waitMs);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static List<String> sortedTileKeys(Iterable<String> tileKeys) {
		List<String> list = new ArrayList<String>();
		for (String key : tileKeys) {
			list.add(key);
		}
		Collections.sort(list, new Comparator<String>() {
			@Override
			public int compare(String a, String b) {
				int[] pa = parseTileKey(a);
				int[] pb = parseTileKey(b);
				if (pa[1] != pb[1]) {
					return Integer.compare(pa[1], pb[1]);
				}
				return Integer.compare(pa[0], pb[0]);
			}
		});
		return list;
	}

	private static int[] parseTileKey(String key) {
		String[] split = key.split("-");
		if (split.length != 2) {
			return new int[] {0, 0};
		}
		try {
			return new int[] {Integer.parseInt(split[0]), Integer.parseInt(split[1])};
		} catch (NumberFormatException e) {
			return new int[] {0, 0};
		}
	}
}
