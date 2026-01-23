package utils;

import java.io.File;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;

import structures.basic.Card;
import structures.basic.EffectAnimation;
import structures.basic.Tile;
import structures.basic.Unit;

/**
 * This class contains methods for producing basic objects from configuration files
 * 
 * @author Dr. Richard McCreadie
 *
 */
public class BasicObjectBuilders {

	@JsonIgnore
	protected static ObjectMapper mapper = new ObjectMapper(); // Jackson Java Object Serializer, is used to read java objects from a file

	/**
	 * This class produces a Card object (or anything that extends Card) given a configuration
	 * file. Configuration files can be found in the conf/gameconfs directory. The card should
	 * be given a unique id number. The classtype field specifies the type of Card to be
	 * constructed, e.g. Card.class will create a default card object, but if you had a class
	 * extending card, e.g. MyAwesomeCard that extends Card, you could also specify
	 * MyAwesomeCard.class here. If using an extending class you will need to manually set any
	 * new data fields. 
	 * @param configurationFile
	 * @param id
	 * @param classtype
	 * @return
	 */
	public static Card loadCard(String configurationFile, int id, Class<? extends Card> classtype) {
		try {
			Card card = mapper.readValue(new File(configurationFile), classtype);

			// If the card is a creature, add its idle animation as the card animation
			if (card.isCreature()) {
				Unit unit = loadUnit(card.getUnitConfig(), -1, Unit.class);
				List<String> idleAnimation = unit.getAnimations().getAllFrames().subList(unit.getAnimations().getIdle().getFrameStartEndIndices()[0], unit.getAnimations().getIdle().getFrameStartEndIndices()[1]);
				card.getMiniCard().setAnimationFrames(idleAnimation.toArray(new String[idleAnimation.size()]));
			}

			card.setId(id);
			return card;
		} catch (Exception e) {
			e.printStackTrace();

		}
		return null;
	}

	/**
	 * This class produces a EffectAnimation object given a configuration
	 * file. Configuration files can be found in the conf/gameconfs directory.
	 * @param configurationFile
	 * @return
	 */
	public static EffectAnimation loadEffect(String configurationFile) {
		try {
			EffectAnimation effect = mapper.readValue(new File(configurationFile), EffectAnimation.class);
			return effect;
		} catch (Exception e) {
			e.printStackTrace();

		}
		return null;
	}

	/**
	 * Loads a unit from a configuration file. Configuration files can be found 
	 * in the conf/gameconfs directory. The unit needs to be given a unique identifier
	 * (id). This method requires a classtype argument that specifies what type of
	 * unit to create. 
	 * @param configFile
	 * @return
	 */
	public static Unit loadUnit(String configFile, int id,  Class<? extends Unit> classType) {

		try {
			Unit unit = mapper.readValue(new File(configFile), classType);

			// identify start and end frames automatically based on file names
			// IDLE
			{
				int startframe = 0; int endframe = 0; int index = 0; boolean inAnimation = false;
				for (String framename: unit.getAnimations().getAllFrames()) {
					if (framename.contains("_idle_")) {
						if (startframe==0) { startframe=index; inAnimation=true;}
					} else {
						if (inAnimation) { endframe=index-1; break;}
					}
					index++;
				}
				if (endframe==0) endframe=index;
				int[] frameIndexes = {startframe, endframe};
				if (inAnimation) unit.getAnimations().getIdle().setFrameStartEndIndices(frameIndexes);
			}

			// DEATH
			{
				int startframe = 0; int endframe = 0; int index = 0; boolean inAnimation = false;
				for (String framename: unit.getAnimations().getAllFrames()) {
					if (framename.contains("_death_")) {
						if (startframe==0) { startframe=index; inAnimation=true;}
					} else {
						if (inAnimation) { endframe=index-1; break;}
					}
					index++;
				}
				if (endframe==0) endframe=index;
				int[] frameIndexes = {startframe, endframe};
				if (inAnimation) unit.getAnimations().getDeath().setFrameStartEndIndices(frameIndexes);
			}

			// ATTACK
			{
				int startframe = 0; int endframe = 0; int index = 0; boolean inAnimation = false;
				for (String framename: unit.getAnimations().getAllFrames()) {
					if (framename.contains("_attack_")) {
						if (startframe==0) { startframe=index; inAnimation=true;}
					} else {
						if (inAnimation) { endframe=index-1; break;}
					}
					index++;
				}
				if (endframe==0) endframe=index;
				int[] frameIndexes = {startframe, endframe};
				if (inAnimation) unit.getAnimations().getAttack().setFrameStartEndIndices(frameIndexes);
			}

			// MOVE
			{
				int startframe = 0; int endframe = 0; int index = 0; boolean inAnimation = false;
				for (String framename: unit.getAnimations().getAllFrames()) {
					if (framename.contains("_run_")) {
						if (startframe==0) { startframe=index; inAnimation=true;}
					} else {
						if (inAnimation) { endframe=index-1; break;}
					}
					index++;
				}
				if (endframe==0) endframe=index;
				int[] frameIndexes = {startframe, endframe};
				if (inAnimation) unit.getAnimations().getMove().setFrameStartEndIndices(frameIndexes);
			}

			// CHANNEL
			{
				int startframe = 0; int endframe = 0; int index = 0; boolean inAnimation = false;
				for (String framename: unit.getAnimations().getAllFrames()) {
					if (framename.contains("_castloop_")) {
						if (startframe==0) { startframe=index; inAnimation=true;}
					} else {
						if (inAnimation) { endframe=index-1; break;}
					}
					index++;
				}
				if (endframe==0) endframe=index;
				int[] frameIndexes = {startframe, endframe};
				if (inAnimation) unit.getAnimations().getChannel().setFrameStartEndIndices(frameIndexes);
			}

			// HIT
			{
				int startframe = 0; int endframe = 0; int index = 0; boolean inAnimation = false;
				for (String framename: unit.getAnimations().getAllFrames()) {
					if (framename.contains("_hit_")) {
						if (startframe==0) { startframe=index; inAnimation=true;}
					} else {
						if (inAnimation) { endframe=index-1; break;}
					}
					index++;
				}
				if (endframe==0) endframe=index;
				int[] frameIndexes = {startframe, endframe};
				if (inAnimation) unit.getAnimations().getChannel().setFrameStartEndIndices(frameIndexes);
			}

			// add full address to animation frames
			for (int i =0; i<unit.getAnimations().getAllFrames().size(); i++) {
				unit.getAnimations().getAllFrames().set(i, unit.getAnimations().getFrameDIR()+unit.getAnimations().getAllFrames().get(i));
			}



			unit.setId(id);
			return unit;
		} catch (Exception e) {
			e.printStackTrace();

		}
		return null;

	}

	/**
	 * Generates a tile object with x and y indices
	 * @param x
	 * @param y
	 * @return
	 */
	public static Tile loadTile(int x, int y) {
		int gridmargin = 5;
		int gridTopLeftx = 410;
		int gridTopLefty = 280;

		Tile tile = Tile.constructTile(StaticConfFiles.tileConf);
		tile.setXpos((tile.getWidth()*x)+(gridmargin*x)+gridTopLeftx);
		tile.setYpos((tile.getHeight()*y)+(gridmargin*y)+gridTopLefty);
		tile.setTilex(x);
		tile.setTiley(y);

		return tile;

	}

}
