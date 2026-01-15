package org.me.retrocoder.model;

import org.junit.jupiter.api.Test;
import org.me.retrocoder.model.Feature;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FeatureTest {

    @Test
    void defaultValues() {
        Feature feature = Feature.builder()
            .category("test")
            .name("test feature")
            .description("test description")
            .build();

        assertEquals(999, feature.getPriority());
        assertFalse(feature.getPasses());
        assertFalse(feature.getInProgress());
    }

    @Test
    void getStepsListEmpty() {
        Feature feature = new Feature();
        feature.setSteps(null);

        List<String> steps = feature.getStepsList();
        assertNotNull(steps);
        assertTrue(steps.isEmpty());
    }

    @Test
    void getStepsListValid() {
        Feature feature = new Feature();
        feature.setSteps("[\"step1\",\"step2\",\"step3\"]");

        List<String> steps = feature.getStepsList();
        assertEquals(3, steps.size());
        assertEquals("step1", steps.get(0));
        assertEquals("step2", steps.get(1));
        assertEquals("step3", steps.get(2));
    }

    @Test
    void setStepsListValid() {
        Feature feature = new Feature();
        feature.setStepsList(List.of("step1", "step2"));

        String json = feature.getSteps();
        assertNotNull(json);
        assertTrue(json.contains("step1"));
        assertTrue(json.contains("step2"));
    }

    @Test
    void setStepsListNull() {
        Feature feature = new Feature();
        feature.setStepsList(null);

        assertEquals("[]", feature.getSteps());
    }

    @Test
    void setStepsListEmpty() {
        Feature feature = new Feature();
        feature.setStepsList(List.of());

        assertEquals("[]", feature.getSteps());
    }
}
