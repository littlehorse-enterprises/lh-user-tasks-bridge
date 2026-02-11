package io.littlehorse.streamsense.diffuser.topology;

/**
 * The four types of streamlets produced by the diffuser. Each type maps to a
 * topic name prefix segment (e.g. {@code workflow}, {@code usertask}).
 *
 * @see TopicNameResolver
 */
public enum StreamletType {
    WORKFLOW("workflow"),
    USERTASK("usertask"),
    EXTERNALEVENT("externalevent"),
    WORKFLOWEVENT("workflowevent");

    private final String prefix;

    StreamletType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
