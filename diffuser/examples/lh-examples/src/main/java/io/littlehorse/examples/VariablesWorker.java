package io.littlehorse.examples;

import io.littlehorse.sdk.worker.LHTaskMethod;
import io.littlehorse.sdk.worker.LHType;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VariablesWorker {

    private static final Logger log = LoggerFactory.getLogger(VariablesWorker.class);

    @LHTaskMethod("sentiment-analysis")
    public Double sentimentAnalysis(@LHType(masked = true) String text) {
        log.debug("Executing task sentiment-analysis vars ({})", text);
        return RandomUtils.nextDouble(0.0, 100.0);
    }

    @LHTaskMethod("process-text")
    @LHType(masked = true)
    public ProcessedText processText(
            @LHType(masked = true) String text, Double sentimentScore, Boolean addLength, Integer userId) {
        log.debug("Executing task process-text vars ({}, {}, {}, {})", text, sentimentScore, addLength, userId);
        ProcessedText processedText = new ProcessedText();
        processedText.text = text;
        processedText.addLength = addLength;
        processedText.userId = userId;
        processedText.sentimentScore = sentimentScore;
        return processedText;
    }

    @LHTaskMethod("send")
    public String send(@LHType(masked = true) ProcessedText processedText) {
        log.debug("Executing task send vars ({})", processedText);
        return "";
    }
}

class ProcessedText {

    public String text;
    public Double sentimentScore;
    public Boolean addLength;
    public Integer userId;

    @Override
    public String toString() {
        return "ProcessedText{"
                + "text='" + text + '\''
                + ", sentimentScore=" + sentimentScore
                + ", addLength=" + addLength
                + ", userId=" + userId
                + '}';
    }
}
