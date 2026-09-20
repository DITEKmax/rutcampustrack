import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.time.LocalTime;

public class InspectLocalTime {
    public static void main(String[] args) {
        Schema<?> primitive = io.swagger.v3.core.util.PrimitiveType.createProperty(LocalTime.class);
        ResolvedSchema rs = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(LocalTime.class));
        System.out.println("primitive=" + primitive);
        System.out.println("resolved=" + rs.schema);
        System.out.println("referenced=" + rs.referencedSchemas);
    }
}
