package structures;

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

	
	public boolean gameInitalised = false;
	
	public boolean something = false;

	// Grid of array to store `Tile` objects.
	public Tile[][] board = new Tile[10][6]; // Creating board from index 1 (for both rows and cols)

	// Both Player objects
	public Player humanPlayer;
	public Player aiPlayer;
	
}
