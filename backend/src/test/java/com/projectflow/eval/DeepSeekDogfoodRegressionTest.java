package com.projectflow.eval;

import org.junit.jupiter.api.Test;

class DeepSeekDogfoodRegressionTest {
    @Test
    void realProfileKeepsOriginalProjectFlowDogfoodQualified() throws Exception {
        RealDogfoodArtifactAssertions.assertQualified("OPENAI_CHAT_COMPLETIONS");
    }
}
