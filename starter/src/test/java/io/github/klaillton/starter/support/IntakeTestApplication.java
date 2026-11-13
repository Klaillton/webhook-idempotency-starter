package io.github.klaillton.starter.support;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(IntakeFixtures.class)
public class IntakeTestApplication {}
