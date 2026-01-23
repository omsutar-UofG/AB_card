package commands;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface DummyTell {

	/**
	 * This is a method that can be used to replace the normal tell logic
	 * when sending commands to avoid issues when building unit tests where
	 * ActorRef is null
	 * 
	 * If writing a unit test you can add a class that implements this interface
	 * and set BasicCommands.altTell = <your class> to avoid calling ActorRef.tell
	 * and call altTell.tell instead.
	 * @param message
	 */
	public void tell(ObjectNode message);
	
}
