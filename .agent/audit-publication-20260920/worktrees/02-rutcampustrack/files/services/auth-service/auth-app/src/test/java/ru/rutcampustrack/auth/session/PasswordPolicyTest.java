package ru.rutcampustrack.auth.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsTwelveUnicodeScalarsWithNdAndPSpecial() {
        PasswordPolicy.Validation result = policy.validate("abcdefghij١!");

        assertThat(result.valid()).isTrue();
        assertThat(result.scalarCount()).isEqualTo(12);
        assertThat(result.utf8Bytes()).isEqualTo(13);
    }

    @Test
    void symbolAloneSatisfiesSpecialCategoryWithoutPunctuation() {
        PasswordPolicy.Validation result = policy.validate("abcdefghij1😀");

        assertThat(result.valid()).isTrue();
        assertThat(result.scalarCount()).isEqualTo(12);
        assertThat(result.utf8Bytes()).isEqualTo(15);
    }

    @Test
    void supplementaryScalarsCountAsOneAndUnicodeNdIsAccepted() {
        PasswordPolicy.Validation result = policy.validate("😀😀😀😀😀😀😀😀😀😀😀١!");

        assertThat(result.valid()).isTrue();
        assertThat(result.scalarCount()).isEqualTo(13);
    }

    @Test
    void rejectsMissingDigitOrSpecialAndDoesNotTreatSpaceOrCombiningMarkAsSpecial() {
        assertThat(policy.validate("abcdefghijkl!").code())
                .isEqualTo(PasswordPolicy.Code.MISSING_DECIMAL_DIGIT);
        assertThat(policy.validate("abcdefghijk1").code())
                .isEqualTo(PasswordPolicy.Code.MISSING_SPECIAL);
        assertThat(policy.validate("abcdefghijk1 ").code())
                .isEqualTo(PasswordPolicy.Code.MISSING_SPECIAL);
        assertThat(policy.validate("e\u0301e\u0301e\u0301e\u0301e\u0301e\u0301e\u0301e\u0301e\u0301e\u03011").code())
                .isEqualTo(PasswordPolicy.Code.MISSING_SPECIAL);
    }

    @Test
    void enforcesScalarMinimumAndUtf8ByteMaximumWithoutTruncation() {
        assertThat(policy.validate("abcdefghi1!").code()).isEqualTo(PasswordPolicy.Code.TOO_SHORT);
        String seventyTwoBytes = "😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀ab1!";
        assertThat(policy.validate(seventyTwoBytes).valid()).isTrue();
        String seventyThreeBytes = "😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀abc1!";
        PasswordPolicy.Validation result = policy.validate(seventyThreeBytes);
        assertThat(result.scalarCount()).isEqualTo(22);
        assertThat(result.utf8Bytes()).isEqualTo(73);
        assertThat(result.code()).isEqualTo(PasswordPolicy.Code.TOO_MANY_UTF8_BYTES);
    }

    @Test
    void rejectsUnpairedSurrogatesInsteadOfReplacingThem() {
        String unpaired = "abcdefghij1!" + '\uD800';
        String unpairedLow = "abcdefghij1!" + '\uDC00';

        PasswordPolicy.Validation result = policy.validate(unpaired);
        PasswordPolicy.Validation lowResult = policy.validate(unpairedLow);

        assertThat(result.code()).isEqualTo(PasswordPolicy.Code.UNPAIRED_SURROGATE);
        assertThat(result.valid()).isFalse();
        assertThat(lowResult.code()).isEqualTo(PasswordPolicy.Code.UNPAIRED_SURROGATE);
        assertThat(lowResult.valid()).isFalse();
    }
}
