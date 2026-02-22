package com.polarbookshop.catalogservice.domain;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;


class BookValidationTest {

    private static Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator  = factory.getValidator();
        }
    }

    @Test
    void whenAllFieldsCorrectThenValidationSucceeds() {
        Book book = new Book("1234567890", "Title", "Author", 9.90);
        Set<ConstraintViolation<Book>> validations = validator.validate(book);
        assertThat(validations).isEmpty();
    }

    @Test
    void whenIsbnNotValidThenValidationFails() {
        Book book = new Book("a234567890", "Title", "Author", 9.90);
        Set<ConstraintViolation<Book>> validations = validator.validate(book);
        assertThat(validations).hasSize(1);
        assertThat(validations.iterator().next().getMessage())
                .isEqualTo("The ISBN format must be valid.");
    }
}