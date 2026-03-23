package edu.wisc.game.gemini;

import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import java.util.List;
import java.util.Map;

class GenaiResponseSchema {

    static Schema makeRootSchema() {

// 1. Define the deepest level: The individual Move Object
Schema moveObjectSchema = Schema.builder()
    .type(Type.Known.OBJECT)
    .description("Here you should describe one proposed move")
    .properties(Map.of(
        "id", Schema.builder()
            .type(Type.Known.INTEGER)
            .description("The object ID of the object you want to move")
            .build(),
        "bucketId", Schema.builder()
            .type(Type.Known.INTEGER)
            .description("The ID of the bucket into which you want to move the object")
            .build()
    ))
    .required(List.of("id", "bucketId"))
    .build();

// 2. Define the middle level: The Array of Moves (per Episode)
Schema movesPerEpisodeSchema = Schema.builder()
    .type(Type.Known.ARRAY)
    .description("Proposed moves for one future episode. Length should equal the number of objects on the board.")
    .items(moveObjectSchema)
    .build();

// 3. Define the Top Level: The Full Response Object
Schema rootSchema = Schema.builder()
    .type(Type.Known.OBJECT)
    .properties(Map.of(
        "inferredRules", Schema.builder()
            .type(Type.Known.STRING)
            .description("Please describe here the hidden rules that best explain all already played episodes shown to you")
            .build(),
        "proposedMoves", Schema.builder()
            .type(Type.Known.ARRAY)
            .description("Proposed moves for all future episodes. Each element corresponds to one future episode.")
            .items(movesPerEpisodeSchema) // Nest the array of moves here
            .build()
    ))
    .required(List.of("inferredRules", "proposedMoves"))
    .build();

return rootSchema;
    }

}
