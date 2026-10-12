package ru.rutcampustrack.academic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.format.support.DefaultFormattingConversionService;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;

@ResourceLock("java.util.Locale.default")
class WebConfigEnumConversionTest {

    @Test
    void queryEnumsUseCanonicalCaseUnderTurkishLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var conversions = new DefaultFormattingConversionService();
            new WebConfig().addFormatters(conversions);
            assertThat(conversions.convert(" active ", AccountStatus.class))
                    .isEqualTo(AccountStatus.ACTIVE);
            assertThat(conversions.convert("archived", AccountStatus.class))
                    .isEqualTo(AccountStatus.ARCHIVED);
            assertThat(conversions.convert(" ", AccountStatus.class)).isNull();
            assertThatThrownBy(() -> conversions.convert("unknown", AccountStatus.class))
                    .isInstanceOf(org.springframework.core.convert.ConversionFailedException.class);
        } finally {
            Locale.setDefault(previous);
        }
    }
}
