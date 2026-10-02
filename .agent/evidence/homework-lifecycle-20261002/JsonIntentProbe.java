import java.time.LocalDate;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import ru.rutcampustrack.academic.homework.HomeworkCreateIntent;
import ru.rutcampustrack.academic.contract.enums.HomeworkBindingMode;

class JsonIntentProbe {
    public static void main(String[] args) {
        var intent = new HomeworkCreateIntent(1L, 2L, 3L, "legacy", null, null,
                HomeworkBindingMode.LESSON, LocalDate.of(2201, 5, 13), 1);
        System.out.println("Hibernate JSON: " + new JacksonJsonFormatMapper().toString(intent, HomeworkCreateIntent.class));
        System.out.println("SQL expected lessonDate: \"2201-05-13\"");
    }
}
