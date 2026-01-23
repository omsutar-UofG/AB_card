package commands;

import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class CheckMessageIsNotNullOnTell implements DummyTell {

	@Override
	public void tell(ObjectNode message) {
		assertNotNull(message);
	}

}
