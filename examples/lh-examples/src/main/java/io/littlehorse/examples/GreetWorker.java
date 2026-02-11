package io.littlehorse.examples;

import io.littlehorse.sdk.worker.LHTaskMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GreetWorker {

    private static final Logger log = LoggerFactory.getLogger(GreetWorker.class);

    @LHTaskMethod("greet")
    public String greeting(String name) {
        log.debug("Executing task greet");
        return "hello there, " + name;
    }

    @LHTaskMethod("ask-for-name")
    public String askForName() {
        log.debug("Executing ask-for-name");
        return "Hi what's your name?";
    }
}
