package com.projectflow.eval;

import org.junit.jupiter.api.Test;

class QwenDogfoodRegressionTest {
    @Test
    void realProfileKeepsOriginalProjectFlowDogfoodQualified() throws Exception {
        RealDogfoodArtifactAssertions.assertQualified("ANTHROPIC_MESSAGES");
    }
}
