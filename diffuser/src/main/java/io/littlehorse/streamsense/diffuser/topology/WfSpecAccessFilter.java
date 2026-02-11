package io.littlehorse.streamsense.diffuser.topology;

import io.littlehorse.sdk.common.proto.ThreadVarDef;
import io.littlehorse.sdk.common.proto.WfRunVariableAccessLevel;
import io.littlehorse.sdk.common.proto.WfSpec;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Extracts PUBLIC_VAR variable names from a WfSpec and builds metadata store keys.
 */
public class WfSpecAccessFilter {

    /**
     * Returns the set of variable names with {@code PUBLIC_VAR} access level,
     * scanning both {@code frozenVariables} and all {@code ThreadSpec.variableDefs}.
     */
    public static Set<String> publicVariableNames(WfSpec wfSpec) {
        Stream<ThreadVarDef> frozen = wfSpec.getFrozenVariablesList().stream();
        Stream<ThreadVarDef> threadVars = wfSpec.getThreadSpecsMap().values().stream()
                .flatMap(ts -> ts.getVariableDefsList().stream());

        Set<String> result = new HashSet<>();
        Stream.concat(frozen, threadVars)
                .filter(tvd -> tvd.getAccessLevel() == WfRunVariableAccessLevel.PUBLIC_VAR)
                .forEach(tvd -> result.add(tvd.getVarDef().getName()));
        return result;
    }

    /**
     * Builds a metadata store key matching the format used by {@link MetadataProcessor}:
     * {@code {tenant}/WF_SPEC/{name}/{%05d major}/{%05d revision}}.
     */
    public static String metadataStoreKey(String tenant, String name, int majorVersion, int revision) {
        return "%s/WF_SPEC/%s/%05d/%05d".formatted(tenant, name, majorVersion, revision);
    }
}
