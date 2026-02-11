package io.littlehorse.streamsense.diffuser.topology;

import io.littlehorse.sdk.common.proto.*;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WfSpecAccessFilterTest {

    @Test
    void mixedAccessLevelsReturnsOnlyPublic() {
        WfSpec wfSpec = WfSpec.newBuilder()
                .putThreadSpecs("entrypoint", ThreadSpec.newBuilder()
                        .addVariableDefs(threadVarDef("public-var", WfRunVariableAccessLevel.PUBLIC_VAR))
                        .addVariableDefs(threadVarDef("private-var", WfRunVariableAccessLevel.PRIVATE_VAR))
                        .addVariableDefs(threadVarDef("inherited-var", WfRunVariableAccessLevel.INHERITED_VAR))
                        .build())
                .build();

        Set<String> result = WfSpecAccessFilter.publicVariableNames(wfSpec);

        assertEquals(Set.of("public-var"), result);
    }

    @Test
    void allPrivateReturnsEmpty() {
        WfSpec wfSpec = WfSpec.newBuilder()
                .putThreadSpecs("entrypoint", ThreadSpec.newBuilder()
                        .addVariableDefs(threadVarDef("secret1", WfRunVariableAccessLevel.PRIVATE_VAR))
                        .addVariableDefs(threadVarDef("secret2", WfRunVariableAccessLevel.PRIVATE_VAR))
                        .build())
                .build();

        Set<String> result = WfSpecAccessFilter.publicVariableNames(wfSpec);

        assertTrue(result.isEmpty());
    }

    @Test
    void frozenAndThreadSpecVariablesAreCombined() {
        WfSpec wfSpec = WfSpec.newBuilder()
                .addFrozenVariables(threadVarDef("frozen-public", WfRunVariableAccessLevel.PUBLIC_VAR))
                .addFrozenVariables(threadVarDef("frozen-private", WfRunVariableAccessLevel.PRIVATE_VAR))
                .putThreadSpecs("entrypoint", ThreadSpec.newBuilder()
                        .addVariableDefs(threadVarDef("thread-public", WfRunVariableAccessLevel.PUBLIC_VAR))
                        .build())
                .putThreadSpecs("child-thread", ThreadSpec.newBuilder()
                        .addVariableDefs(threadVarDef("child-public", WfRunVariableAccessLevel.PUBLIC_VAR))
                        .addVariableDefs(threadVarDef("child-private", WfRunVariableAccessLevel.PRIVATE_VAR))
                        .build())
                .build();

        Set<String> result = WfSpecAccessFilter.publicVariableNames(wfSpec);

        assertEquals(Set.of("frozen-public", "thread-public", "child-public"), result);
    }

    @Test
    void defaultAccessLevelIsTreatedAsPublic() {
        // PUBLIC_VAR = 0 is the proto default, so unset access_level defaults to public
        WfSpec wfSpec = WfSpec.newBuilder()
                .putThreadSpecs("entrypoint", ThreadSpec.newBuilder()
                        .addVariableDefs(ThreadVarDef.newBuilder()
                                .setVarDef(VariableDef.newBuilder().setName("default-var"))
                                .build())
                        .build())
                .build();

        Set<String> result = WfSpecAccessFilter.publicVariableNames(wfSpec);

        assertEquals(Set.of("default-var"), result);
    }

    @Test
    void metadataStoreKeyFormat() {
        assertEquals(
                "default/WF_SPEC/output-topic/00000/00000",
                WfSpecAccessFilter.metadataStoreKey("default", "output-topic", 0, 0));
        assertEquals(
                "tenant1/WF_SPEC/my-workflow/00003/00012",
                WfSpecAccessFilter.metadataStoreKey("tenant1", "my-workflow", 3, 12));
    }

    private static ThreadVarDef threadVarDef(String name, WfRunVariableAccessLevel accessLevel) {
        return ThreadVarDef.newBuilder()
                .setVarDef(VariableDef.newBuilder().setName(name))
                .setAccessLevel(accessLevel)
                .build();
    }
}
