package com.projectflow.eval;

import org.junit.jupiter.api.Test;

class GLMDogfoodRegressionTest {
    @Test
    void realProfileKeepsOriginalProjectFlowDogfoodQualified() throws Exception {
        RealDogfoodArtifactAssertions.assertQualified("OPENAI_RESPONSES");
    }
}
