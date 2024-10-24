package io.micronaut.tracing.util;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public record Foo(@GeneratedValue @Id Integer id, String name) {
}
