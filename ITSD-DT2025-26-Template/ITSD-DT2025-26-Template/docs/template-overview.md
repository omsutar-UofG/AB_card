# Template Structure Overview

The provided project template follows a modular structure.

## Application Packages

- actors: Contains core in-game entities such as players or units.
- assets: Stores static resources used by the application.
- commands: Defines command objects sent from game logic to the UI.
- controllers: Coordinates events and invokes appropriate game logic.
- demo: Provides example or demonstration code.
- events: Represents events triggered during gameplay.
- structures: Contains data structures representing the game state.
- utils: Provides shared helper and utility functions.
- views: Handles presentation and displays the game state to the user.

## High-Level Control Flow

The system follows an event-driven architecture. User actions generate events, which are processed by controllers. The game state is updated accordingly, and commands are sent to the views to reflect these changes.
