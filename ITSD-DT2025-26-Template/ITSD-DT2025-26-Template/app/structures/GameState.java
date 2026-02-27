package structures;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import structures.basic.BetterUnit;
import structures.basic.Player;
import structures.basic.Tile;


/**
 * This class can be used to hold information about the on-going game.
 * Its created with the GameActor.
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class GameState {

	public static final int OWNER_HUMAN = 1;
	public static final int OWNER_AI = 2;
	
	public boolean gameInitalised = false;
	
	public boolean something = false;
	
	// SC05: Turn Initialization - Turn 1 starts
	public int turnNumber = 1;
	// SC08: End Turn Processing - Active player
	public Player activePlayer;

	// Grid of array to store `Tile` objects.
	public Tile[][] board = new Tile[10][6]; // Creating board from index 1 (for both rows and cols)

	// Both Player objects
	public Player humanPlayer;
	public Player aiPlayer;

	// SC10-SC15: Runtime board-unit index for fast occupancy and target lookups.
	public Map<Integer, BetterUnit> unitsById = new HashMap<Integer, BetterUnit>();
	public Map<String, Integer> unitIdByTile = new HashMap<String, Integer>();

	// SC10-SC12: Selection and highlight state.
	public Integer selectedUnitId = null;
	public Set<String> moveHighlightTiles = new HashSet<String>();
	public Set<String> attackHighlightTiles = new HashSet<String>();
	// SC14: For each red highlighted enemy tile, store the approach tile key.
	public Map<String, String> approachTileByEnemyTile = new HashMap<String, String>();

	// SC13-SC14: Multi-step action continuation state (move -> optional follow-up attack).
	public boolean actionLocked = false;
	public Integer pendingMoveUnitId = null;
	public Integer pendingAttackTargetUnitId = null;

	// SC21-SC29: selected hand card state for card highlight/targeting flow.
	public Integer selectedCardHandPosition = null; // 1-based hand index [1..6]
	public String selectedCardTargetMode = null;

	// SC23/SC28: runtime id generator for newly summoned units.
	public int nextUnitId = 2;
	
}
