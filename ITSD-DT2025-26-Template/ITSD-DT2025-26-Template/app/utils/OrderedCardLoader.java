package utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import structures.basic.Card;

/**
 * This is a utility class that provides methods for loading the decks for each
 * player, as the deck ordering is fixed. 
 * @author Richard
 *
 */
public class OrderedCardLoader {

	public static String cardsDIR = "conf/gameconfs/cards/";
	
	/**
	 * Returns all of the cards in the human player's deck in order
	 * @return
	 */
	public static List<Card> getPlayer1Cards(int copies) {
	
		List<Card> cardsInDeck = new ArrayList<Card>(20);
		
		int cardID = 1;
		for (int i =0; i<copies; i++) {
			for (String filename : new File(cardsDIR).list()) {
				if (filename.startsWith("1_")) {
					// this is a deck 1 card
					cardsInDeck.add(BasicObjectBuilders.loadCard(cardsDIR+filename, cardID, Card.class));
				}
			}
		}
		
		
		return cardsInDeck;
	}
	
	
	/**
	 * Returns all of the cards in the human player's deck in order
	 * @return
	 */
	public static List<Card> getPlayer2Cards(int copies) {
	
		List<Card> cardsInDeck = new ArrayList<Card>(20);
		
		int cardID = 1;
		for (int i =0; i<copies; i++) {
			for (String filename : new File(cardsDIR).list()) {
				if (filename.startsWith("2_")) {
					// this is a deck 2 card
					cardsInDeck.add(BasicObjectBuilders.loadCard(cardsDIR+filename, cardID, Card.class));
				}
			}
		}
		
		return cardsInDeck;
	}
	
}
