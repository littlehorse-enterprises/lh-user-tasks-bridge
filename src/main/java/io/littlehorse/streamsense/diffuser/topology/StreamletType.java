package io.littlehorse.streamsense.diffuser.topology;

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
