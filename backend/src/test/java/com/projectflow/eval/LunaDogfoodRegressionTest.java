package com.projectflow.eval;

import org.junit.jupiter.api.Test;

class LunaDogfoodRegressionTest {
    @Test
    void realProfileKeepsOriginalProjectFlowDogfoodQualified() throws Exception {
        RealDogfoodArtifactAssertions.assertQualified("OPENAI_RESPONSES");
    }
}
